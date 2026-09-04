<template>
  <div>
    <el-popover ref="alertPopover" placement="bottom-end" width="320" trigger="manual" :value="alertVisible" popper-class="alert-popover">
      <div class="alert-header">
        <span class="alert-title">实时告警</span>
        <span class="alert-clear" @click="clearAlerts">清空</span>
      </div>
      <div v-if="alertList.length === 0" class="alert-empty"><i class="el-icon-inbox"></i><br>暂无告警</div>
      <div v-else>
        <div v-for="item in alertList" :key="item.id" class="alert-item" @click="markRead(item)">
          <el-tag v-if="item.alertType === 'PREDICT'" size="mini" type="warning" effect="dark" class="alert-tag alert-tag-predict">预测</el-tag>
          <el-tag v-else size="mini" :type="levelMeta(item.alertLevel).type" :effect="levelMeta(item.alertLevel).effect" class="alert-tag">{{ levelMeta(item.alertLevel).label }}</el-tag>
          <span class="alert-item-title">{{ item.sensorName || item.sensorCode }}</span>
          <span class="alert-item-value">{{ formatValue(item.sensorValue) }}</span>
          <span class="alert-item-time">{{ formatTime(item.triggerTime) }}</span>
        </div>
      </div>
    </el-popover>

    <div v-popover:alertPopover class="right-menu-item hover-effect alert-trigger" @mouseenter="onAlertEnter" @mouseleave="onAlertLeave">
      <i class="el-icon-warning alert-icon" />
      <span v-if="unreadCount > 0" class="alert-badge">{{ unreadCount }}</span>
    </div>

    <!-- 未读告警期间视口边缘红色呼吸闪烁：fixed 全屏覆盖+pointer-events:none 不挡交互 -->
    <div v-show="unreadCount > 0" class="alert-edge-flash" aria-hidden="true" />
  </div>
</template>

<script>
import { subscribe } from '@/utils/ndjsonStream'

// 告警流地址：与故障预警页共用同一 URL，ndjsonStream 内部复用单连接
const ALERT_STREAM_URL = process.env.VUE_APP_BASE_API + '/api/alert-events/stream'

export default {
  name: 'HeaderAlert',
  data() {
    return {
      alertList: [], // 实时告警列表（最多保留最近 20 条）
      // 未读告警 id 集合：用数组而非对象/Set，Vue2 对数组的 push/splice 有原生响应式拦截，
      // 角标/闪烁/循环播报引擎的未读判断均以它为唯一数据源
      unreadIdList: [],
      alertVisible: false, // 弹出层显示状态
      alertLeaveTimer: null, // 鼠标离开计时器
      unsubscribe: null
    }
  },
  computed: {
    // 未读数改由 unreadIdList 派生：模板既有 unreadCount 引用（角标 v-if、闪烁 v-show）零改动
    unreadCount() {
      return this.unreadIdList.length
    }
  },
  created() {
    // 循环播报引擎状态放非响应式实例属性：引擎自驱动、不参与渲染，
    // 进 data 反而让每次游标推进都触发无意义的依赖收集
    this._speakLoopActive = false // 引擎活跃标志
    this._speakSafetyTimer = null // 保险定时器句柄（防 onend/onerror 均不触发时卡死）
    this._speakCursor = 0 // 轮播游标（对未读 pending 数组取模）
  },
  mounted() {
    // 订阅实时告警流：新告警计入未读并插入列表，严重级别额外右下角通知
    this.unsubscribe = subscribe(ALERT_STREAM_URL, {
      onMessage: this.handleStreamAlert
    })
  },
  beforeDestroy() {
    // 先停播引擎再退订：cancel 残留语音并清保险定时器，防止销毁后定时器仍触发 advance 操作已卸载实例
    this.stopSpeakLoop()
    if (this.unsubscribe) {
      this.unsubscribe()
      this.unsubscribe = null
    }
  },
  methods: {
    // 鼠标移入告警区域
    onAlertEnter() {
      clearTimeout(this.alertLeaveTimer)
      this.alertVisible = true
      this.$nextTick(() => {
        const popper = this.$refs.alertPopover.$refs.popper
        if (popper && !popper._alertBound) {
          popper._alertBound = true
          popper.addEventListener('mouseenter', () => clearTimeout(this.alertLeaveTimer))
          popper.addEventListener('mouseleave', () => {
            this.alertLeaveTimer = setTimeout(() => { this.alertVisible = false }, 100)
          })
        }
      })
    },
    // 鼠标离开告警区域
    onAlertLeave() {
      this.alertLeaveTimer = setTimeout(() => { this.alertVisible = false }, 150)
    },
    // 流式告警：新告警计入未读、顶部插入；同 id 再推为升级推送（后端只升级不重建），就地替换该条
    handleStreamAlert(alert) {
      if (!alert || alert.id === undefined || alert.id === null) return
      // 列表已有同 id：升级推送分支，就地替换等级/数值/时间，避免同传感器重复占两条
      const idx = this.alertList.findIndex(item => item.id === alert.id)
      if (idx > -1) {
        this.alertList.splice(idx, 1, { ...alert })
        // 已读告警升级须重新触达（与后端 ACKED→FIRING 语义一致）；已在未读则不重复入列，防角标虚增
        if (!this.unreadIdList.includes(alert.id)) {
          this.unreadIdList.push(alert.id)
        }
        // 游标归零并(重)启动引擎：下一条即播升级后的告警，buildAlertText 从列表取值自动用新等级"严重告警"
        this._speakCursor = 0
        this.startSpeakLoop()
        // 严重/危急才弹右下角通知:低等级靠铃铛+播报触达,避免通知风暴
        if (alert.alertLevel === 'SEVERE' || alert.alertLevel === 'CRITICAL') {
          // 升级通知与新告警通知文案区分：用户已见过该告警，重点是"级别变了"而非"新告警"
          this.$notify({
            title: '告警升级',
            message: `${alert.sensorName || alert.sensorCode} 数值 ${alert.sensorValue} 超限，级别已升级为${alert.alertLevel === 'CRITICAL' ? '危急' : '严重'}`,
            type: 'error',
            duration: 5000
          })
        }
        return
      }
      this.unreadIdList.push(alert.id)
      this.alertList.unshift({ ...alert })
      if (this.alertList.length > 20) {
        this.alertList = this.alertList.slice(0, 20)
      }
      // 游标归零：新告警 unshift 在 alertList 头部，pending[0] 即最新，
      // 当前条播完后下一条就轮到新告警（引擎未活跃时 startSpeakLoop 内部也会归零，双保险）
      this._speakCursor = 0
      this.startSpeakLoop()
      // 严重/危急才弹右下角通知:低等级靠铃铛+播报触达,避免通知风暴
      if (alert.alertLevel === 'SEVERE' || alert.alertLevel === 'CRITICAL') {
        // PREDICT 的 SEVERE 是"预测即将越界",文案与实测越界区分(预测链路不产 CRITICAL,危急分支恒走实测文案)
        const isPredict = alert.alertType === 'PREDICT'
        this.$notify({
          title: isPredict ? '预测严重告警' : (alert.alertLevel === 'CRITICAL' ? '危急告警' : '严重告警'),
          message: `${alert.sensorName || alert.sensorCode} 数值 ${this.formatValue(alert.sensorValue)}${isPredict ? '，预测即将越界' : ' 超限'}`,
          type: 'error',
          duration: 5000
        })
      }
    },
    // 启动循环播报(已活跃则忽略):未读期间逐条循环,全部已读/清空时停止
    startSpeakLoop() {
      if (this._speakLoopActive) return
      if (!window.speechSynthesis) { console.debug('当前浏览器不支持语音播报,已跳过'); return }
      this._speakLoopActive = true
      this._speakCursor = 0
      this.speakNext()
    },
    // 停止循环:cancel 正在播报的当前条(已读代表用户已处理,播完扰人)并退出引擎
    stopSpeakLoop() {
      this._speakLoopActive = false
      clearTimeout(this._speakSafetyTimer)
      this._speakSafetyTimer = null
      try { window.speechSynthesis && window.speechSynthesis.cancel() } catch (e) { /* 忽略 */ }
    },
    // 播报下一条未读:事件驱动(utterance.onend/onerror 续播)而非 setInterval 轮询,
    // 播报节奏与语音真实结束对齐,长文案不会被打断、短文案也不空等
    speakNext() {
      // 防御:停止条件在取条前先判,防止停止请求与已排队的续播竞争取到条目
      if (!this._speakLoopActive || this.unreadCount === 0) {
        this.stopSpeakLoop()
        return
      }
      const pending = this.alertList.filter(a => this.unreadIdList.includes(a.id))
      if (pending.length === 0) {
        this.stopSpeakLoop()
        return
      }
      const item = pending[this._speakCursor % pending.length]
      this._speakCursor++
      try {
        const utterance = new SpeechSynthesisUtterance(this.buildAlertText(item))
        utterance.lang = 'zh-CN'
        // 语速调快+音调微升,营造告警的急促感(默认 1.0/1.0 过于平缓)
        utterance.rate = 1.4
        utterance.pitch = 1.2
        // onend/onerror 都推进:onerror 时 utterance 被 cancel 属正常停止路径,但引擎若仍活跃需续播
        utterance.onend = () => this.advanceSpeakLoop()
        utterance.onerror = () => this.advanceSpeakLoop()
        // 保险定时器:部分浏览器 onend/onerror 均不触发,10s 强制推进防引擎卡死在一条上
        this._speakSafetyTimer = setTimeout(() => this.advanceSpeakLoop(), 10000)
        // 正在播报先取消:防语音叠死;短窗口重复 speak 由 cancel 兜底(重入可接受)
        if (window.speechSynthesis.speaking) {
          window.speechSynthesis.cancel()
        }
        window.speechSynthesis.speak(utterance)
      } catch (e) {
        // TTS 异常直接停引擎而非续播:防 speakNext 异常风暴下 advance 递归失控,告警主链路不受影响
        console.debug('语音播报异常,已停止循环:', e && e.message)
        this.stopSpeakLoop()
      }
    },
    // 推进循环:onend/onerror/保险定时器三路都汇到这,先清定时器保证一轮只推进一次
    advanceSpeakLoop() {
      clearTimeout(this._speakSafetyTimer)
      this._speakSafetyTimer = null
      if (this._speakLoopActive && this.unreadCount > 0) {
        this.speakNext()
      } else {
        this.stopSpeakLoop()
      }
    },
    // 播报文案:设备+传感器+级别+数值;数值统一两位小数(PREDICT 平滑值带全精度,
    // 原样播会读出一长串数字,听感差且无信息量;传感器原始值本身也是两位小数)
    // PREDICT 类型加"预测"前缀:与实时告警区分,听众能感知"这是将要发生,不是已发生"
    buildAlertText(alert) {
      const levelText = { CRITICAL: '危急', SEVERE: '严重', IMPORTANT: '重要', WARNING: '预警', NORMAL: '正常' }[alert.alertLevel] || alert.alertLevel || ''
      const equipment = alert.equipmentName || (alert.equipmentId ? '设备' + alert.equipmentId : '未知设备')
      const sensor = alert.sensorName || alert.sensorCode || '未知传感器'
      const value = alert.sensorValue == null ? '' : this.formatValue(alert.sensorValue)
      const prefix = alert.alertType === 'PREDICT' ? '预测' : ''
      return `${equipment}，${sensor}，出现${prefix}${levelText}告警，当前数值 ${value}`
    },
    // 数值展示统一两位小数:兜住历史已落库的全精度平滑值(源头已同步修为两位)
    formatValue(v) {
      return v == null ? '-' : Number(v).toFixed(2)
    },
    // 点击单条告警视为已读:从未读集合移除,角标-1,仍保留在列表(已读不是删除,历史可回看);
    // 全部已读后停止循环播报
    markRead(item) {
      const idx = this.unreadIdList.indexOf(item.id)
      if (idx > -1) {
        this.unreadIdList.splice(idx, 1)
      }
      if (this.unreadCount === 0) {
        this.stopSpeakLoop()
      }
      this.alertVisible = false
      // 按告警类型分流:预测告警去预测性维护页,实时告警去故障预警页
      this.$router.push(item.alertType === 'PREDICT' ? '/machine-business/predict' : '/machine-business/warning')
    },
    // 清空列表并重置未读集合（仅前端态，不做已读持久化）：列表没了播报也无从取条，同步停引擎
    clearAlerts() {
      this.stopSpeakLoop()
      this.alertList = []
      this.unreadIdList = []
    },
    // 四级等级标签映射:同色系内 dark 实底强于 light 浅底区分相邻档(重要>预警、危急>严重),红色系整体重于橙色系区分高低档
    levelMeta(level) {
      return {
        CRITICAL: { label: '危急', type: 'danger', effect: 'dark' },
        SEVERE: { label: '严重', type: 'danger', effect: 'light' },
        IMPORTANT: { label: '重要', type: 'warning', effect: 'dark' },
        WARNING: { label: '预警', type: 'warning', effect: 'light' },
        NORMAL: { label: '正常', type: 'info', effect: 'light' }
      }[level] || { label: level || '-', type: 'info', effect: 'light' }
    },
    // 时间格式化（仅时分秒）：兼容 ISO(带T)/普通字符串/数组(LocalDateTime Jackson 序列化)三种格式
    formatTime(time) {
      if (!time) return '-'
      let normalized = time
      if (typeof time === 'string') {
        normalized = time.replace('T', ' ').replace(/-/g, '/')
      } else if (Array.isArray(time)) {
        // Jackson LocalDateTime 数组 [年,月,日,时,分,秒,纳秒]:直接 new Date(数组) 是 Invalid Date,
        // 各字段 NaN 会被 parseTime 的 value||0 转成 0,显示成 0:0:0,须按字段构造
        normalized = new Date(time[0] || 1970, (time[1] || 1) - 1, time[2] || 1, time[3] || 0, time[4] || 0, time[5] || 0)
      }
      return this.parseTime(new Date(normalized), '{h}:{i}:{s}') || '-'
    }
  }
}
</script>

<style lang="scss" scoped>
.alert-trigger {
  position: relative;
  transform: translateX(-6px);
  .alert-icon { font-size: 18px; vertical-align: middle; }
  .alert-badge {
    position: absolute;
    top: 7px;
    right: -3px;
    background: #f56c6c;
    color: #fff;
    border-radius: 10px;
    font-size: 10px;
    height: 16px;
    line-height: 16px;
    padding: 0 4px;
    min-width: 16px;
    text-align: center;
    white-space: nowrap;
    pointer-events: none;
  }
}
.alert-popover {
  padding: 0 !important;
}
.alert-popover .alert-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f7f9fb;
  border-bottom: 1px solid #eee;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}
.alert-popover .alert-clear {
  font-size: 12px;
  color: #409EFF;
  font-weight: normal;
  cursor: pointer;
}
.alert-popover .alert-clear:hover { color: #2b7cc1; }
.alert-popover .alert-empty {
  padding: 24px;
  text-align: center;
  color: #bbb;
  font-size: 12px;
  line-height: 1.8;
}
.alert-popover .alert-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.15s;
}
.alert-popover .alert-item:last-child { border-bottom: none; }
.alert-popover .alert-item:hover { background: #f7f9fb; }
.alert-popover .alert-tag { flex-shrink: 0; }
// PREDICT 预测标签:橙色实底(warning+dark),与红色实时告警一眼区分
.alert-popover .alert-tag-predict {
  background: #e6a23c;
  border-color: #e6a23c;
  color: #fff;
}
.alert-popover .alert-item-title {
  flex: 1;
  font-size: 12px;
  color: #333;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.alert-popover .alert-item-value {
  flex-shrink: 0;
  font-size: 12px;
  color: #f56c6c;
  font-weight: 600;
}
.alert-popover .alert-item-time {
  flex-shrink: 0;
  font-size: 11px;
  color: #bbb;
}
// 告警边缘闪烁层：四边红色光晕呼吸,纯 CSS 动画零定时器
.alert-edge-flash {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 3000; // 高于 ElNotification(~2000),保证提醒可见
  animation: alert-edge-breathe 1.2s ease-in-out infinite;
}
@keyframes alert-edge-breathe {
  0%, 100% { box-shadow: inset 0 0 30px rgba(245, 108, 108, 0.25); }
  50% { box-shadow: inset 0 0 60px rgba(245, 108, 108, 0.6); }
}
</style>
