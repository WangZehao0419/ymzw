import { getToken } from '@/utils/auth'

/**
 * NDJSON 流式订阅工具（模块级单例）
 *
 * 后端以 NDJSON 协议持续推送（每行一个 JSON 对象 + 换行符），
 * 同一 URL 的多个订阅者共享一条 fetch 长连接：
 *   - 惰性建连：首个订阅者出现才发起 fetch
 *   - 订阅者归零：主动 abort 连接并清理单例条目
 *   - 断流自动指数退避重连（1s/2s/4s... 上限 30s），重连时重新读取最新 token
 *
 * 接口契约（并行任务依赖，签名不可改）：
 *   subscribe(url, handlers) → unsubscribe 函数
 *   handlers: {
 *     onMessage: (obj) => void,      // 必填；每条非 heartbeat 的 JSON 对象回调
 *     onStatus?: (status) => void,    // 可选；'connected' | 'reconnecting' | 'closed'
 *     onAuthFail?: () => void         // 可选；收到 401 时回调（停止重连）
 *   }
 */

// 首次重连等待时长（ms），之后每次翻倍
const RECONNECT_BASE_DELAY = 1000
// 重连退避上限（ms）
const RECONNECT_MAX_DELAY = 30000
// 心跳标记：type 为 heartbeat 的行不回调 onMessage
const HEARTBEAT_TYPE = 'heartbeat'

/**
 * 单例连接表：Map<url, entry>
 * entry 结构：
 *   subscribers   Set<handlers>          当前订阅者集合
 *   controller    AbortController | null 当前 fetch 的中止控制器
 *   retryTimer    number | null          重连定时器句柄
 *   retryDelay    number                 当前退避等待时长
 *   buffer        string                 NDJSON 分帧缓冲区（保留不完整尾行）
 *   manualClosed  boolean                主动关闭标记：true 时读流中断不触发重连
 *   connecting    boolean                建连进行中标记（防止并发建连）
 */
const connections = new Map()

// 向所有订阅者派发指定回调（未传入的可选回调直接跳过）
function notify(entry, name, ...args) {
  entry.subscribers.forEach(handlers => {
    if (typeof handlers[name] === 'function') {
      handlers[name](...args)
    }
  })
}

// 指数退避重连：先通知 'reconnecting'，再按当前退避时长定时重建连接
function scheduleReconnect(entry, url) {
  if (entry.manualClosed) {
    return
  }
  notify(entry, 'onStatus', 'reconnecting')
  const delay = entry.retryDelay
  // 连接成功时会重置为基准值，只有持续失败才会逐步逼近上限
  entry.retryDelay = Math.min(delay * 2, RECONNECT_MAX_DELAY)
  entry.retryTimer = setTimeout(() => {
    entry.retryTimer = null
    connect(entry, url)
  }, delay)
}

// 主动关闭连接：abort fetch、取消未触发的重连定时器、清理单例条目
function closeConnection(url) {
  const entry = connections.get(url)
  if (!entry) {
    return
  }
  entry.manualClosed = true
  if (entry.retryTimer !== null) {
    clearTimeout(entry.retryTimer)
    entry.retryTimer = null
  }
  if (entry.controller) {
    try {
      entry.controller.abort()
    } catch (e) {
      // abort 已完成的请求可能抛错，静默即可
    }
    entry.controller = null
  }
  connections.delete(url)
}

// 建立连接并进入读流循环；任何异常（含响应非 ok）统一走重连，AbortError 静默
async function connect(entry, url) {
  if (entry.manualClosed || entry.connecting) {
    return
  }
  entry.connecting = true
  entry.controller = new AbortController()
  try {
    // 每次建连都重新读取最新 token，保证重连后凭据不过期
    const res = await fetch(url, {
      headers: { Authorization: 'Bearer ' + getToken() },
      signal: entry.controller.signal
    })
    if (!res.ok) {
      if (res.status === 401) {
        // 认证失效：通知所有订阅者后停止重连并释放连接
        notify(entry, 'onAuthFail')
        closeConnection(url)
        return
      }
      // 其他非 2xx 状态（如网关 502/503）按连接失败处理，进入重连
      throw new Error('NDJSON stream HTTP ' + res.status)
    }
    // 连接成功：重置退避时长，通知订阅者
    entry.retryDelay = RECONNECT_BASE_DELAY
    entry.buffer = ''
    notify(entry, 'onStatus', 'connected')
    await readStream(entry, url, res)
  } catch (err) {
    // 主动关闭触发的 abort 会以 AbortError 形式抛出，静默不重连
    if (entry.manualClosed || (err && err.name === 'AbortError')) {
      return
    }
    scheduleReconnect(entry, url)
  } finally {
    entry.connecting = false
  }
}

// 读流循环：增量解码 + 按 \n 分帧；done 且非主动关闭视为服务端断流，走重连
async function readStream(entry, url, res) {
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  for (;;) {
    const { done, value } = await reader.read()
    if (done) {
      if (!entry.manualClosed) {
        scheduleReconnect(entry, url)
      }
      return
    }
    // stream: true 保证多字节 UTF-8 字符跨 chunk 边界时正确拼接
    entry.buffer += decoder.decode(value, { stream: true })
    dispatchLines(entry)
  }
}

// NDJSON 分帧：buffer 中每次找 \n 切出完整行；不完整的尾行保留到下次 chunk 拼接
function dispatchLines(entry) {
  let idx = entry.buffer.indexOf('\n')
  while (idx !== -1) {
    const line = entry.buffer.slice(0, idx).trim()
    entry.buffer = entry.buffer.slice(idx + 1)
    if (line) {
      try {
        const obj = JSON.parse(line)
        // 心跳行仅用于保活，不向业务层派发
        if (obj && obj.type !== HEARTBEAT_TYPE) {
          notify(entry, 'onMessage', obj)
        }
      } catch (e) {
        // 解析失败的行（如截断/脏数据）静默丢弃，不影响后续帧
        console.warn('[ndjsonStream] 丢弃无法解析的行:', line)
      }
    }
    idx = entry.buffer.indexOf('\n')
  }
}

/**
 * 订阅 NDJSON 流
 * @param {string} url 流端点完整地址（含网关前缀）
 * @param {Object} handlers 回调集合（见文件头注释）
 * @returns {Function} unsubscribe 函数，调用后移除订阅；订阅者归零时自动断开连接
 */
export function subscribe(url, handlers = {}) {
  if (typeof handlers.onMessage !== 'function') {
    throw new TypeError('[ndjsonStream] handlers.onMessage 为必填回调')
  }
  let entry = connections.get(url)
  if (!entry) {
    entry = {
      subscribers: new Set(),
      controller: null,
      retryTimer: null,
      retryDelay: RECONNECT_BASE_DELAY,
      buffer: '',
      manualClosed: false,
      connecting: false
    }
    connections.set(url, entry)
  }
  entry.subscribers.add(handlers)
  // 惰性建连：仅首个订阅者出现时发起连接（重连场景下连接可能已在退避中，无需重复触发）
  if (entry.subscribers.size === 1) {
    connect(entry, url)
  }
  return function unsubscribe() {
    // 条目可能已被归零清理甚至重建，必须以当前 Map 中的条目为准
    const current = connections.get(url)
    if (!current || !current.subscribers.has(handlers)) {
      return
    }
    current.subscribers.delete(handlers)
    if (current.subscribers.size === 0) {
      closeConnection(url)
    }
  }
}
