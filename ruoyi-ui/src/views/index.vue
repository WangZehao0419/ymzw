<template>
  <div class="app-container dashboard">
    <!-- 顶部欢迎条：系统名 + 实时时钟 -->
    <el-card class="welcome-card" shadow="never">
      <div class="welcome-bar">
        <div class="welcome-left">
          <span class="welcome-title">云眸智维监控平台</span>
          <span class="welcome-sub">设备与告警全局态势</span>
        </div>
        <div class="welcome-clock">
          <i class="el-icon-time" />
          <span>{{ nowText }}</span>
        </div>
      </div>
    </el-card>

    <!-- KPI 统计卡片 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-body">
            <div class="kpi-icon kpi-icon--primary"><i class="el-icon-monitor" /></div>
            <div class="kpi-text">
              <div class="kpi-value">{{ equipmentTotal === null ? '-' : equipmentTotal }}</div>
              <div class="kpi-label">设备总数</div>
              <div class="kpi-sub">{{ runningCount === null ? '设备数据获取失败' : '运行中 ' + runningCount + ' 台' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-body">
            <div class="kpi-icon kpi-icon--success"><i class="el-icon-odometer" /></div>
            <div class="kpi-text">
              <div class="kpi-value">{{ sensorTotal === null ? '-' : sensorTotal }}</div>
              <div class="kpi-label">传感器总数</div>
              <div class="kpi-sub">实时采集点位</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-body">
            <div class="kpi-icon kpi-icon--warning"><i class="el-icon-bell" /></div>
            <div class="kpi-text">
              <div class="kpi-value">{{ firingCount === null ? '-' : firingCount }}</div>
              <div class="kpi-label">告警中</div>
              <div class="kpi-sub">FIRING 状态</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-body">
            <div class="kpi-icon kpi-icon--danger"><i class="el-icon-warning" /></div>
            <div class="kpi-text">
              <div class="kpi-value">{{ severeCount === null ? '-' : severeCount }}</div>
              <div class="kpi-label">严重告警</div>
              <div class="kpi-sub">SEVERE + FIRING</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区：设备状态饼图 + 告警级别环形图 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card shadow="never" class="chart-card">
          <div slot="header" class="card-header"><span>设备状态分布</span></div>
          <div ref="statusChart" class="chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card shadow="never" class="chart-card">
          <div slot="header" class="card-header"><span>告警级别分布</span></div>
          <div ref="levelChart" class="chart" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 最新告警列表：实时流驱动 -->
    <el-card shadow="never" class="alert-card">
      <div slot="header" class="card-header">
        <span>最新告警</span>
        <span class="card-header-tip">实时流推送 · 展示最近 10 条</span>
      </div>
      <el-table :data="alertList" row-key="id" :row-class-name="rowClassName">
        <el-table-column label="设备" prop="equipmentName" min-width="140" align="center" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.equipmentName || '设备#' + scope.row.equipmentId }}</template>
        </el-table-column>
        <el-table-column label="传感器" min-width="130" align="center" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.sensorName || scope.row.sensorCode }}</template>
        </el-table-column>
        <el-table-column label="当前数值" prop="sensorValue" width="100" align="center">
          <template slot-scope="scope"><span class="sensor-value">{{ scope.row.sensorValue }}</span></template>
        </el-table-column>
        <el-table-column label="级别" prop="alertLevel" width="90" align="center">
          <template slot-scope="scope"><el-tag :type="levelMeta(scope.row.alertLevel).type">{{ levelMeta(scope.row.alertLevel).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" prop="alertStatus" width="90" align="center">
          <template slot-scope="scope"><el-tag :type="statusMeta(scope.row.alertStatus).type">{{ statusMeta(scope.row.alertStatus).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="时间" prop="triggerTime" width="165" align="center">
          <template slot-scope="scope">{{ formatTime(scope.row.triggerTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { listEquipment, listSensor } from '@/api/business/machine'
import { fetchAlertEvents } from '@/api/business/alert'
import { subscribe } from '@/utils/ndjsonStream'

// 告警流地址：与顶栏铃铛/预警页共用同一 URL，ndjsonStream 内部复用单连接
const ALERT_STREAM_URL = process.env.VUE_APP_BASE_API + '/api/alert-events/stream'

// 设备状态字典：后端值 -> 展示名/主题色
const EQUIPMENT_STATUS = [
  { value: '0', label: '运行中', color: '#67C23A' },
  { value: '1', label: '待机', color: '#909399' },
  { value: '2', label: '维护中', color: '#E6A23C' },
  { value: '3', label: '离线', color: '#C0C4CC' }
]

// 告警级别 -> 标签/主题色（与告警预警页保持一致）
const LEVEL_META = {
  SEVERE: { label: '严重', type: 'danger', color: '#F56C6C' },
  WARNING: { label: '预警', type: 'warning', color: '#E6A23C' },
  NORMAL: { label: '正常', type: 'info', color: '#409EFF' }
}

// 告警状态 -> 标签
const STATUS_META = {
  FIRING: { label: '告警中', type: 'danger' },
  ACKED: { label: '已确认', type: 'warning' },
  RESOLVED: { label: '已恢复', type: 'success' }
}

// 星期中文映射（getDay 0 = 周日）
const WEEK_CN = ['日', '一', '二', '三', '四', '五', '六']

export default {
  name: 'Index',
  data() {
    return {
      // 实时时钟
      now: new Date(),
      clockTimer: null,
      refreshTimer: null,
      // KPI 指标：null 表示对应接口失败，页面显示 '-'
      equipmentTotal: null,
      runningCount: null,
      sensorTotal: null,
      firingCount: null,
      severeCount: null,
      // 告警级别计数（基于全部已拉取告警，流式到达时增量维护）
      levelCounts: { SEVERE: 0, WARNING: 0, NORMAL: 0 },
      // 最新告警列表（最多 10 条）
      alertList: [],
      receivedIds: new Set(), // 已出现过的告警 id，防止流重连重放导致重复插入
      newAlertIds: new Set(), // 流式新到达的告警 id，用于新行高亮
      unsubscribe: null,
      statusChart: null,
      levelChart: null
    }
  },
  computed: {
    nowText() {
      const text = this.parseTime(this.now, '{y}年{m}月{d}日 {h}:{i}:{s}')
      return text + ' 星期' + WEEK_CN[this.now.getDay()]
    }
  },
  mounted() {
    // 图表容器需在 DOM 挂载后初始化，数据到达后统一 setOption
    this.$nextTick(() => {
      this.statusChart = echarts.init(this.$refs.statusChart)
      this.levelChart = echarts.init(this.$refs.levelChart)
      this.loadDashboardData()
    })
    window.addEventListener('resize', this.handleResize)
    // 每秒刷新时钟
    this.clockTimer = setInterval(() => {
      this.now = new Date()
    }, 1000)
    // 每 60 秒刷新 KPI 与图表
    this.refreshTimer = setInterval(() => {
      this.loadDashboardData()
    }, 60 * 1000)
    // 订阅实时告警流：新告警顶部插入并联动 KPI/级别环形图
    this.unsubscribe = subscribe(ALERT_STREAM_URL, {
      onMessage: this.handleStreamAlert
    })
  },
  beforeDestroy() {
    // 离开页面清理全部定时器/监听/流订阅/图表实例，避免内存泄漏
    if (this.clockTimer) {
      clearInterval(this.clockTimer)
      this.clockTimer = null
    }
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer)
      this.refreshTimer = null
    }
    window.removeEventListener('resize', this.handleResize)
    if (this.unsubscribe) {
      this.unsubscribe()
      this.unsubscribe = null
    }
    if (this.statusChart) {
      this.statusChart.dispose()
      this.statusChart = null
    }
    if (this.levelChart) {
      this.levelChart.dispose()
      this.levelChart = null
    }
  },
  methods: {
    // 并行拉取三类数据，任一失败降级为 null：对应 KPI 显示 '-'、图表空数据、列表空
    loadDashboardData() {
      Promise.all([
        listEquipment({ pageNum: 1, pageSize: 100 }).catch(() => null),
        listSensor({ pageNum: 1, pageSize: 1 }).catch(() => null),
        fetchAlertEvents({ pageNum: 1, pageSize: 500 }).catch(() => null)
      ]).then(([equipRes, sensorRes, alertRes]) => {
        if (equipRes) {
          const rows = equipRes.rows || []
          this.equipmentTotal = equipRes.total
          const statusCount = {}
          rows.forEach(item => {
            statusCount[item.equipmentStatus] = (statusCount[item.equipmentStatus] || 0) + 1
          })
          this.runningCount = statusCount['0'] || 0
          this.renderStatusChart(statusCount)
        } else {
          this.equipmentTotal = null
          this.runningCount = null
          this.renderStatusChart(null)
        }
        this.sensorTotal = sensorRes ? sensorRes.total : null
        if (alertRes) {
          const rows = alertRes.rows || []
          let firing = 0
          let severe = 0
          const levels = { SEVERE: 0, WARNING: 0, NORMAL: 0 }
          rows.forEach(item => {
            if (levels[item.alertLevel] !== undefined) {
              levels[item.alertLevel] += 1
            }
            if (item.alertStatus === 'FIRING') {
              firing += 1
              if (item.alertLevel === 'SEVERE') {
                severe += 1
              }
            }
          })
          this.firingCount = firing
          this.severeCount = severe
          this.levelCounts = levels
          this.alertList = rows.slice(0, 10)
          // 记录已展示告警 id，避免流端重放时列表重复插入
          rows.forEach(item => {
            if (item.id !== undefined && item.id !== null) {
              this.receivedIds.add(item.id)
            }
          })
        } else {
          this.firingCount = null
          this.severeCount = null
          this.levelCounts = { SEVERE: 0, WARNING: 0, NORMAL: 0 }
          this.alertList = []
        }
        this.renderLevelChart()
      })
    },
    // 设备状态分布饼图
    renderStatusChart(statusCount) {
      if (!this.statusChart) return
      const data = statusCount
        ? EQUIPMENT_STATUS
          .map(item => ({ name: item.label, value: statusCount[item.value] || 0, itemStyle: { color: item.color } }))
          .filter(item => item.value > 0)
        : []
      this.statusChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} 台 ({d}%)' },
        legend: { bottom: 0 },
        series: [
          {
            type: 'pie',
            radius: '55%',
            center: ['50%', '42%'],
            itemStyle: { borderColor: '#fff', borderWidth: 2 },
            label: { formatter: '{b}: {c} 台' },
            data
          }
        ]
      })
    },
    // 告警级别分布环形图（不用 roseType）
    renderLevelChart() {
      if (!this.levelChart) return
      const data = Object.keys(LEVEL_META)
        .map(key => ({ name: LEVEL_META[key].label, value: this.levelCounts[key] || 0, itemStyle: { color: LEVEL_META[key].color } }))
        .filter(item => item.value > 0)
      this.levelChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} 条 ({d}%)' },
        legend: { bottom: 0 },
        series: [
          {
            type: 'pie',
            radius: ['40%', '62%'],
            center: ['50%', '42%'],
            itemStyle: { borderColor: '#fff', borderWidth: 2 },
            label: { formatter: '{b}: {c} 条' },
            data
          }
        ]
      })
    },
    // 流式新告警：去重后顶部插入并联动 KPI 计数与级别环形图
    handleStreamAlert(alert) {
      if (!alert || alert.id === undefined || alert.id === null) return
      if (this.receivedIds.has(alert.id)) return
      this.receivedIds.add(alert.id)
      this.newAlertIds.add(alert.id)
      setTimeout(() => {
        this.newAlertIds.delete(alert.id)
      }, 5000)
      this.alertList.unshift({ ...alert })
      this.alertList = this.alertList.slice(0, 10)
      if (alert.alertStatus === 'FIRING') {
        // 初始接口失败时计数为 null，按 0 起算保证流式联动仍生效
        this.firingCount = (this.firingCount || 0) + 1
        if (alert.alertLevel === 'SEVERE') {
          this.severeCount = (this.severeCount || 0) + 1
        }
      }
      if (this.levelCounts[alert.alertLevel] !== undefined) {
        this.levelCounts[alert.alertLevel] += 1
        this.renderLevelChart()
      }
    },
    // 新到达的告警行追加高亮 class
    rowClassName({ row }) {
      return this.newAlertIds.has(row.id) ? 'alert-new-row' : ''
    },
    levelMeta(level) {
      return LEVEL_META[level] || { label: level || '-', type: 'info' }
    },
    statusMeta(status) {
      return STATUS_META[status] || { label: status || '-', type: 'info' }
    },
    // 时间格式化：兼容 ISO(带T)/普通字符串/数组(LocalDateTime Jackson 序列化)三种格式
    formatTime(time) {
      if (!time) return '-'
      let normalized = time
      if (typeof time === 'string') {
        normalized = time.replace('T', ' ').replace(/-/g, '/')
      } else if (Array.isArray(time)) {
        // Jackson LocalDateTime 数组 [年,月,日,时,分,秒,纳秒]: 直接 new Date(数组) 是 Invalid Date,
        // 须按字段构造，否则各字段 NaN 会被 parseTime 转成 0，显示成 0-0-0 0:0:0
        normalized = new Date(time[0] || 1970, (time[1] || 1) - 1, time[2] || 1, time[3] || 0, time[4] || 0, time[5] || 0)
      }
      return this.parseTime(new Date(normalized), '{y}-{m}-{d} {h}:{i}:{s}') || '-'
    },
    handleResize() {
      if (this.statusChart) this.statusChart.resize()
      if (this.levelChart) this.levelChart.resize()
    }
  }
}
</script>

<style lang="scss" scoped>
.dashboard {
  .welcome-card {
    margin-bottom: 16px;
  }
  .welcome-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
  }
  .welcome-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
  }
  .welcome-sub {
    margin-left: 12px;
    font-size: 13px;
    color: #909399;
  }
  .welcome-clock {
    display: flex;
    align-items: center;
    font-size: 15px;
    font-weight: 500;
    color: #409eff;

    i {
      margin-right: 6px;
      font-size: 17px;
    }
  }
  .kpi-card {
    margin-bottom: 16px;
  }
  .kpi-body {
    display: flex;
    align-items: center;
  }
  .kpi-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    margin-right: 16px;
    border-radius: 10px;
    font-size: 28px;
    flex-shrink: 0;
  }
  .kpi-icon--primary {
    background: rgba(64, 158, 255, 0.12);
    color: #409eff;
  }
  .kpi-icon--success {
    background: rgba(103, 194, 58, 0.12);
    color: #67c23a;
  }
  .kpi-icon--warning {
    background: rgba(230, 162, 60, 0.12);
    color: #e6a23c;
  }
  .kpi-icon--danger {
    background: rgba(245, 108, 108, 0.12);
    color: #f56c6c;
  }
  .kpi-value {
    font-size: 26px;
    font-weight: 700;
    line-height: 1.2;
    color: #303133;
  }
  .kpi-label {
    margin-top: 2px;
    font-size: 13px;
    color: #909399;
  }
  .kpi-sub {
    margin-top: 4px;
    font-size: 12px;
    color: #c0c4cc;
  }
  .chart-card,
  .alert-card {
    margin-bottom: 16px;
  }
  .chart {
    width: 100%;
    height: 300px;
  }
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
  }
  .card-header-tip {
    font-size: 12px;
    font-weight: 400;
    color: #c0c4cc;
  }
  .sensor-value {
    color: #f56c6c;
    font-weight: 600;
  }
}

// 流式新插入的告警行：5 秒背景渐隐提示
::v-deep .el-table .alert-new-row {
  animation: alert-row-flash 5s ease-out;
}
@keyframes alert-row-flash {
  0% { background-color: #fde2e2; }
  60% { background-color: #fdf6ec; }
  100% { background-color: transparent; }
}
</style>
