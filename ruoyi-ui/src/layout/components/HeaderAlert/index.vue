<template>
  <div>
    <el-popover ref="alertPopover" placement="bottom-end" width="320" trigger="manual" :value="alertVisible" popper-class="alert-popover">
      <div class="alert-header">
        <span class="alert-title">实时告警</span>
        <span class="alert-clear" @click="clearAlerts">清空</span>
      </div>
      <div v-if="alertList.length === 0" class="alert-empty"><i class="el-icon-inbox"></i><br>暂无告警</div>
      <div v-else>
        <div v-for="item in alertList" :key="item.id" class="alert-item" @click="goWarningPage">
          <el-tag size="mini" :type="levelMeta(item.alertLevel).type" class="alert-tag">{{ levelMeta(item.alertLevel).label }}</el-tag>
          <span class="alert-item-title">{{ item.sensorName || item.sensorCode }}</span>
          <span class="alert-item-value">{{ item.sensorValue }}</span>
          <span class="alert-item-time">{{ formatTime(item.triggerTime) }}</span>
        </div>
      </div>
    </el-popover>

    <div v-popover:alertPopover class="right-menu-item hover-effect alert-trigger" @mouseenter="onAlertEnter" @mouseleave="onAlertLeave">
      <i class="el-icon-warning alert-icon" />
      <span v-if="unreadCount > 0" class="alert-badge">{{ unreadCount }}</span>
    </div>
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
      unreadCount: 0, // 未读数量
      alertVisible: false, // 弹出层显示状态
      alertLeaveTimer: null, // 鼠标离开计时器
      unsubscribe: null
    }
  },
  mounted() {
    // 订阅实时告警流：新告警计入未读并插入列表，严重级别额外右下角通知
    this.unsubscribe = subscribe(ALERT_STREAM_URL, {
      onMessage: this.handleStreamAlert
    })
  },
  beforeDestroy() {
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
    // 流式新告警：未读+1、顶部插入（超 20 条截断），SEVERE 级别弹通知
    handleStreamAlert(alert) {
      if (!alert || alert.id === undefined || alert.id === null) return
      this.unreadCount += 1
      this.alertList.unshift({ ...alert })
      if (this.alertList.length > 20) {
        this.alertList = this.alertList.slice(0, 20)
      }
      if (alert.alertLevel === 'SEVERE') {
        this.$notify({
          title: '严重告警',
          message: `${alert.sensorName || alert.sensorCode} 数值 ${alert.sensorValue} 超限`,
          type: 'error',
          duration: 5000
        })
      }
    },
    // 点击告警条目：跳转故障预警页并关闭浮层
    goWarningPage() {
      this.alertVisible = false
      this.$router.push('/machine-business/warning')
    },
    // 清空列表并重置未读数（仅前端态，不做已读持久化）
    clearAlerts() {
      this.alertList = []
      this.unreadCount = 0
    },
    levelMeta(level) {
      return { SEVERE: { label: '严重', type: 'danger' }, WARNING: { label: '预警', type: 'warning' }, NORMAL: { label: '正常', type: 'info' } }[level] || { label: level || '-', type: 'info' }
    },
    // 时间格式化（仅时分秒）：兼容 ISO(带T) 与普通字符串两种序列化格式
    formatTime(time) {
      if (!time) return '-'
      const normalized = typeof time === 'string' ? time.replace('T', ' ').replace(/-/g, '/') : time
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
</style>
