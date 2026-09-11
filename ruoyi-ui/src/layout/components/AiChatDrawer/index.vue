<template>
  <el-drawer
    :visible.sync="drawerVisible"
    direction="rtl"
    size="520px"
    append-to-body
    :wrapper-closable="true"
    title="云眸智维智能系统助手"
    custom-class="ai-chat-drawer"
  >
    <!-- el-drawer body 默认无 padding 且 flex:1，这里自建 flex column 布局撑满：消息区滚动、输入区固定底部 -->
    <div class="ai-chat-root">
      <div ref="msgList" class="msg-list">
        <!-- 空状态：首次打开无消息时的引导 -->
        <div v-if="messages.length === 0" class="empty-state">
          <i class="el-icon-chat-dot-round empty-icon" />
          <p class="empty-title">您好，我是云眸智维智能系统助手</p>
<!--          <p class="empty-tip">试试对我说：帮我对 CNC-001 的主轴温度传感器创建一张故障维修工单</p>-->
        </div>
        <!-- 消息列表：user 右侧主色气泡，assistant 左侧浅灰气泡 -->
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="msg-row"
          :class="msg.role === 'user' ? 'msg-right' : 'msg-left'"
        >
          <div class="msg-bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-assistant'">
            <!-- 用户消息纯文本插值：输入的 < > ** 等按原文显示，不走 HTML 渲染 -->
            <span v-if="msg.role === 'user'" class="msg-text">{{ msg.content }}</span>
            <!-- assistant 消息 markdown 富文本（html:false 已转义源码 HTML） -->
            <span v-else class="msg-text md-content" v-html="renderMarkdown(msg.content)" />
            <!-- 流式回复中：最后一条 assistant 消息末尾追加闪烁光标，形成"正在输入"效果 -->
            <span v-if="msg.role === 'assistant' && sending && index === messages.length - 1" class="typing-cursor" />
          </div>
        </div>
      </div>
      <!-- 输入区：Enter 发送 / Shift+Enter 换行 -->
      <div class="input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          resize="none"
          :placeholder="recording ? '正在聆听…' : '请输入内容，Enter 发送，Shift+Enter 换行'"
          @keydown.enter.native="handleEnter"
        />
        <div class="input-actions">
          <el-tooltip :content="voiceTooltip" placement="top">
            <span>
              <el-button
                circle
                :type="recording ? 'danger' : 'default'"
                :class="{ 'voice-recording': recording }"
                :icon="recording ? 'el-icon-turn-off-microphone' : 'el-icon-microphone'"
                :disabled="!voiceSupported || sending"
                @click="toggleVoice"
              />
            </span>
          </el-tooltip>
          <el-button type="primary" icon="el-icon-s-promotion" :loading="sending" :disabled="!inputText.trim() || sending" @click="send">发送</el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { getToken } from '@/utils/auth'
import MarkdownIt from 'markdown-it'

// markdown-it 单例：html:false 源码 HTML 一律转义（防注入），breaks 单换行转 <br>，
// linkify 裸 URL 自动成链；实例无状态可全局复用
const md = new MarkdownIt({ html: false, breaks: true, linkify: true })

export default {
  name: 'AiChatDrawer',
  props: {
    // .sync 双向：el-drawer 内部关闭（点遮罩/ESC/右上角叉）走 computed setter 转发父组件
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      messages: [], // 对话消息：[{role:'user'|'assistant', content:''}]
      inputText: '',
      sending: false, // 一轮流式回复进行中：锁定发送，驱动按钮 loading 与"正在输入"光标
      conversationId: '', // 会话 ID：created 时生成一次，后端靠它维持多轮上下文
      voiceSupported: false, // 浏览器是否支持语音识别（created 时探测）
      recording: false // 语音录制中：驱动按钮态/placeholder/动画
    }
  },
  computed: {
    // 本地 computed 桥接 .sync：get 读 prop，set 时 emit update:visible 给父组件
    drawerVisible: {
      get() {
        return this.visible
      },
      set(val) {
        this.$emit('update:visible', val)
      }
    },
    // 麦克风按钮悬浮提示：按支持情况与录音状态区分文案
    voiceTooltip() {
      if (!this.voiceSupported) return '当前浏览器不支持语音输入'
      if (this.recording) return '点击停止录音'
      return '语音输入'
    }
  },
  watch: {
    // 抽屉关闭时若在录音立即停止，避免后台持续占用麦克风
    visible(val) {
      if (!val && this.recording) {
        this.recognition && this.recognition.stop()
      }
    }
  },
  created() {
    // 组件随 Navbar 常驻（不随抽屉关闭销毁、不用 destroy-on-close），会话 ID 因此跨开合复用，
    // 只有刷新页面才换新会话
    this.conversationId = 'chat-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
    // 语音识别能力探测：Chrome/Edge 支持，Firefox 等不支持时按钮禁用
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
    this.voiceSupported = !!SpeechRecognition
  },
  methods: {
    // Enter 快捷发送：Shift+Enter 放行走默认换行；输入法选词阶段的 Enter 不当作发送（否则中文没法打）
    handleEnter(event) {
      if (event.shiftKey) return
      if (event.isComposing) return
      event.preventDefault()
      this.send()
    },
    send() {
      // 录音中不允许发送：识别文本仍在实时写入输入框，此时发送内容不完整
      if (this.recording) return
      // 提交前才 trim：v-model.trim 会在输入阶段吃掉首尾空格，影响正常输入体验
      const text = this.inputText.trim()
      if (!text || this.sending) return
      this.messages.push({ role: 'user', content: text })
      // 先占位一条空 assistant 消息：后续 delta 直接追加其上，渲染层自然呈现逐字输出
      this.messages.push({ role: 'assistant', content: '' })
      this.inputText = ''
      this.sending = true
      this.scrollToBottom()
      this.sendMessage(text)
    },
    // 调后端流式接口：fetch + ReadableStream 逐 chunk 读，NDJSON 按行分帧解析
    async sendMessage(message) {
      try {
        const response = await fetch(process.env.VUE_APP_BASE_API + '/api/ai/chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + getToken()
          },
          body: JSON.stringify({ message: message, conversationId: this.conversationId })
        })
        // 非 2xx（401/404/500 等）统一抛错走 catch 兜底，不暴露技术细节给用户
        if (!response.ok) {
          throw new Error('HTTP ' + response.status)
        }
        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8')
        // NDJSON 分帧缓冲：网络 chunk 不保证按行边界切割，半行必须攒到下一 chunk 再拼
        let buffer = ''
        let finished = false
        while (!finished) {
          const { value, done: streamDone } = await reader.read()
          if (streamDone) break
          // stream:true 告知 decoder 后面还有数据：多字节中文跨 chunk 截断时不产生乱码
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          // 末段可能是半行：弹回缓冲区等下一轮拼接
          buffer = lines.pop()
          for (const line of lines) {
            if (finished) break
            finished = this.handleStreamLine(line)
          }
        }
        // flush decoder 内可能残留的尾字节，并兜底解析缓冲区最后一行（服务端末行未带 \n 的边界情况）
        buffer += decoder.decode()
        if (!finished && buffer.trim()) {
          this.handleStreamLine(buffer)
        }
      } catch (e) {
        // 网络/HTTP 层任何异常收敛为用户可理解的文案
        this.appendAssistantText('服务暂时不可用，请稍后重试')
      } finally {
        this.sending = false
        this.scrollToBottom()
      }
    },
    // 语音输入开关：待机→开始录音；录音→停止。识别文本追加进输入框而非直接发送，
    // 用户保留修改权（识别引擎可能出错，直接发送风险高）
    toggleVoice() {
      if (!this.voiceSupported || this.sending) return
      if (this.recording) {
        this.recognition && this.recognition.stop()
        return
      }
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
      const recognition = new SpeechRecognition()
      recognition.lang = 'zh-CN'
      recognition.continuous = false // 单句模式：静音后自动结束
      recognition.interimResults = true // 中间结果实时上屏
      // 暂存录音前输入框原文；finalVoiceText 累计已落定文本，inputText = 原文 + final 累计 + 当前 interim
      this.textBeforeVoice = this.inputText
      this.finalVoiceText = ''
      this.voiceGotResult = false
      recognition.onresult = (event) => {
        this.voiceGotResult = true
        let finalText = ''
        let interimText = ''
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const r = event.results[i]
          if (r.isFinal) {
            finalText += r[0].transcript
          } else {
            interimText += r[0].transcript
          }
        }
        if (finalText) {
          this.finalVoiceText += finalText
        }
        this.inputText = this.textBeforeVoice + this.finalVoiceText + interimText
      }
      recognition.onend = () => {
        // 无论正常结束/静音自动结束/出错，统一在此复位录音态
        this.recording = false
        this.recognition = null
        if (!this.voiceGotResult) {
          this.$message.info('未识别到语音')
        }
      }
      recognition.onerror = (event) => {
        // not-allowed/service-not-allowed 均为麦克风权限问题
        if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
          this.$message.warning('麦克风权限被拒绝，请在浏览器地址栏允许麦克风访问')
        } else if (event.error !== 'aborted' && event.error !== 'no-speech') {
          this.$message.warning('语音识别失败：' + event.error)
        }
      }
      this.recognition = recognition
      try {
        recognition.start()
        this.recording = true
      } catch (e) {
        // start 抛错（如已启动）直接复位
        this.recording = false
        this.recognition = null
      }
    },
    // assistant 消息按 markdown 渲染；流式 delta 每次追加都会触发重渲染，
    // 未闭合语法（如半截 **）会按原文展示，闭合后自动成文
    renderMarkdown(content) {
      if (!content) return ''
      return md.render(content)
    },
    // 解析一行 NDJSON，返回 true 表示本轮终止（收到 done 或 error）
    handleStreamLine(line) {
      const text = line.trim()
      if (!text) return false
      let data
      try {
        data = JSON.parse(text)
      } catch (e) {
        // 单行解析失败只丢弃该行，不中断整轮流式输出
        return false
      }
      if (data.type === 'delta') {
        // 增量文本追加到最后一条 assistant 消息（send 时已占位保证末位是 assistant）
        const last = this.messages[this.messages.length - 1]
        if (last && last.role === 'assistant') {
          last.content += data.content || ''
        }
        this.scrollToBottom()
      } else if (data.type === 'error') {
        this.appendAssistantText('出错了：' + (data.message || '未知错误'))
        return true
      } else if (data.type === 'done') {
        return true
      }
      return false
    },
    // 错误/兜底文案写入 assistant 消息：占位为空直接写，已有半截回复则换行拼接
    appendAssistantText(text) {
      const last = this.messages[this.messages.length - 1]
      if (last && last.role === 'assistant') {
        last.content = last.content ? last.content + '\n\n' + text : text
      } else {
        this.messages.push({ role: 'assistant', content: text })
      }
    },
    // 滚到底部：delta 每次追加后调用；msgList 在抽屉首次打开后才渲染，故判空
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.msgList
        if (el) {
          el.scrollTop = el.scrollHeight
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
// custom-class 挂在 .el-drawer 上；append-to-body 把 DOM 移到 body 后 scoped 属性仍在
// （父 scope id 打在子组件根元素上），::v-deep 可正常命中（项目既有惯例写法）
::v-deep .ai-chat-drawer .el-drawer__header {
  // 默认 margin-bottom 32px 过大：压缩并加分隔线
  margin-bottom: 0;
  padding: 14px 20px;
  border-bottom: 1px solid #ebeef5;
  color: #303133;
  font-weight: 600;
}

.ai-chat-root {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

// 空状态引导
.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 0 24px;
  .empty-icon {
    font-size: 48px;
    color: #c0c4cc;
    margin-bottom: 16px;
  }
  .empty-title {
    font-size: 15px;
    color: #303133;
    margin: 0 0 8px;
  }
  .empty-tip {
    font-size: 13px;
    color: #909399;
    line-height: 1.7;
    margin: 0;
    background: #f7f8fa;
    border-radius: 8px;
    padding: 10px 14px;
  }
}

.msg-row {
  display: flex;
  margin-bottom: 12px;
  &:last-child {
    margin-bottom: 0;
  }
  &.msg-right {
    justify-content: flex-end;
  }
  &.msg-left {
    justify-content: flex-start;
  }
}

.msg-bubble {
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}
.bubble-user {
  background: #409EFF;
  color: #fff;
  // 用户消息保留原样换行（纯文本路径）
  white-space: pre-wrap;
}
.bubble-assistant {
  background: #f4f4f5;
  color: #303133;
  // markdown 富文本排版（::v-deep 命中 v-html 注入的元素；项目既有惯例写法）
  ::v-deep .md-content {
    // markdown 段落自带间距，首尾不外扩防止气泡撑高
    p {
      margin: 0 0 4px;
      &:last-child {
        margin-bottom: 0;
      }
    }
    ul,
    ol {
      margin: 4px 0;
      padding-left: 18px;
    }
    li {
      margin: 2px 0;
    }
    strong {
      font-weight: 600;
    }
    // 标题字号钳制：气泡内不放大失控
    h1, h2, h3, h4, h5, h6 {
      margin: 6px 0 4px;
      font-weight: 600;
      font-size: 14px;
      &:first-child {
        margin-top: 0;
      }
    }
    code {
      padding: 0 4px;
      border-radius: 3px;
      background: #eceef1;
      font-family: Consolas, Monaco, 'Courier New', monospace;
      font-size: 12px;
    }
    pre {
      margin: 4px 0;
      padding: 8px;
      border-radius: 4px;
      background: #eceef1;
      overflow-x: auto;
      code {
        padding: 0;
        background: transparent;
      }
    }
    a {
      color: #409EFF;
    }
    table {
      border-collapse: collapse;
      margin: 4px 0;
      th,
      td {
        border: 1px solid #dcdfe6;
        padding: 4px 8px;
      }
      th {
        background: #fafafa;
      }
    }
    blockquote {
      margin: 4px 0;
      padding: 2px 10px;
      border-left: 3px solid #dcdfe6;
      color: #909399;
    }
  }
}

// 流式输出"正在输入"光标：闪烁竖线
.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 13px;
  background: #409EFF;
  margin-left: 2px;
  vertical-align: -2px;
  animation: ai-cursor-blink 0.8s step-end infinite;
}
@keyframes ai-cursor-blink {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

// 录音中麦克风按钮：呼吸闪烁，提示正在采集
.voice-recording {
  animation: voice-pulse 1.2s ease-in-out infinite;
}
@keyframes voice-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.5);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(245, 108, 108, 0);
  }
}

// 输入区固定底部，不随消息滚动
.input-area {
  flex-shrink: 0;
  border-top: 1px solid #ebeef5;
  padding: 12px 16px;
  .input-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 8px;
  }
}
</style>
