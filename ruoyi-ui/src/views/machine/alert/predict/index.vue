<template>
  <div class="app-container">
    <!-- KPI 摘要条：受监控设备/传感器/三态计数/活跃预测告警，纯前端聚合 -->
    <div class="kpi-bar">
      <div class="kpi-item">
        <span class="kpi-label">受监控设备</span>
        <span class="kpi-value">{{ equipmentGroups.length }}</span>
      </div>
      <div class="kpi-item">
        <span class="kpi-label">传感器总数</span>
        <span class="kpi-value">{{ kpiStats.total }}</span>
      </div>
      <div class="kpi-item">
        <span class="kpi-label">正常</span>
        <span class="kpi-value kpi-success">{{ kpiStats.normal }}</span>
      </div>
      <div class="kpi-item">
        <span class="kpi-label">劣化中</span>
        <span class="kpi-value kpi-warning">{{ kpiStats.degrading }}</span>
      </div>
      <div class="kpi-item">
        <span class="kpi-label">已越界</span>
        <span class="kpi-value kpi-danger">{{ kpiStats.breached }}</span>
      </div>
      <div class="kpi-item">
        <span class="kpi-label">活跃预测告警</span>
        <span class="kpi-value kpi-danger">{{ activeAlertCount }}</span>
      </div>
    </div>
    <!-- 顶部：传感器预测状态总览卡片（按设备分组） -->
    <div v-for="group in equipmentGroups" :key="group.equipmentId" class="equipment-group">
      <!-- 组标题：设备名 + 聚合状态徽标（任一越界 > 任一劣化 > 全正常）+ 组健康分（木桶原则取组内最低）+ 最早越界时刻/倒计时 -->
      <div class="equipment-group-title">
        <div class="equipment-title-left">
          <span class="equipment-name">{{ group.equipmentName }}</span>
          <el-tag size="mini" :type="statusMeta(group.aggregateStatus).type">{{ statusMeta(group.aggregateStatus).label }}</el-tag>
        </div>
        <div class="equipment-title-right">
          <span class="group-health">
            <span class="group-health-label">健康分</span>
            <span class="group-health-score" :style="{ color: healthColor(group.minHealthScore) }">{{ healthText(group.minHealthScore) }}</span>
          </span>
          <span v-if="group.earliestBreachTimeMs != null" class="group-breach">
            最早越界 {{ formatMs(group.earliestBreachTimeMs) }}（剩余 {{ countdown(group.earliestBreachTimeMs) }}）
          </span>
          <span v-else class="group-breach">最早越界 -</span>
        </div>
      </div>
      <el-row :gutter="12" class="sensor-cards">
        <el-col v-for="s in group.sensors" :key="s.sensorCode" :xs="24" :sm="12" :md="8">
          <div class="sensor-card" :class="{ active: s.sensorCode === currentSensor }" @click="selectSensor(s.sensorCode)">
            <div class="sensor-card-head">
              <span class="sensor-card-name">{{ s.sensorName || s.sensorCode }}</span>
              <el-tag size="mini" :type="statusMeta(s.status).type">{{ statusMeta(s.status).label }}</el-tag>
            </div>
            <div class="sensor-card-body">
              <div class="sensor-card-item">
                <span class="label">健康分</span>
                <span class="value" :style="{ color: healthColor(s.healthScore) }">{{ healthText(s.healthScore) }}</span>
              </div>
              <div class="sensor-card-item">
                <span class="label">劣化速率</span>
                <span class="value">{{ s.slope != null ? s.slope + ' /点' : '-' }}</span>
              </div>
              <div class="sensor-card-item">
                <span class="label">预计越界</span>
                <!-- NORMAL 态残留旧 t1Points 时显示 '-'，防止幽灵数据误导（配合后端 P3 清理双保险） -->
                <span class="value" :class="{ danger: hasForecast(s) }">{{ hasForecast(s) ? formatMs(s.predictedBreachTimeMs) : '-' }}</span>
              </div>
              <div class="sensor-card-item">
                <span class="label">剩余时间</span>
                <span class="value" :class="{ danger: hasForecast(s) }">{{ hasForecast(s) ? countdown(s.predictedBreachTimeMs) : '-' }}</span>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 中部：传感器详情图 -->
    <el-card shadow="never" class="chart-card">
      <div slot="header" class="chart-header">
        <span class="chart-title">{{ (currentSensorMeta.equipmentName || '设备#' + currentSensorMeta.equipmentId) + ' / ' + (currentSensorMeta.sensorName || currentSensorMeta.sensorCode) }} — 趋势预测</span>
        <div class="chart-toolbar">
          <el-tag v-if="detail && detail.trend && detail.trend.t1Points != null" type="danger" size="small">
            预计 {{ formatMs(detail.trend.predictedBreachTimeMs) }} 越界（剩余 {{ countdown(detail.trend.predictedBreachTimeMs) }}）
          </el-tag>
          <el-tag v-else-if="detail && detail.trend" type="info" size="small">趋势已识别，暂无可预测越界时刻</el-tag>
          <el-tag v-else type="info" size="small">无启用阈值规则，仅展示曲线</el-tag>
          <el-button size="mini" icon="el-icon-refresh" circle @click="loadDetail" />
        </div>
      </div>
      <div ref="chart" class="chart" v-loading="chartLoading" />
      <div class="chart-legend-tips">
        <span><i class="dot raw" />原始数据：传感器上报原始值</span>
        <span><i class="dot smoothed" />平滑线：按正弦周期滑动平均，剔除周期性波动</span>
        <span><i class="dot band" />预测带：回归外推 95% 置信区间</span>
        <span><i class="dot threshold" />阈值线：告警规则上下限</span>
      </div>
    </el-card>

    <!-- 底部：预测告警列表 -->
    <el-card shadow="never" class="alert-card">
      <div slot="header" class="chart-header">
        <span class="chart-title">预测告警</span>
        <el-button size="mini" icon="el-icon-refresh" circle @click="getAlerts" />
      </div>
      <el-table v-loading="alertsLoading" :data="alertList" size="small">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="所属设备" prop="equipmentName" width="150" align="center" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.equipmentName || '设备#' + scope.row.equipmentId }}</template>
        </el-table-column>
        <el-table-column label="传感器" prop="sensorName" min-width="140" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.sensorName || scope.row.sensorCode }}</template>
        </el-table-column>
        <el-table-column label="级别" prop="alertLevel" width="90" align="center">
          <template slot-scope="scope"><el-tag size="mini" :type="levelMeta(scope.row.alertLevel).type" :effect="levelMeta(scope.row.alertLevel).effect">{{ levelMeta(scope.row.alertLevel).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" prop="alertStatus" width="90" align="center">
          <template slot-scope="scope"><el-tag size="mini" :type="alertStatusMeta(scope.row.alertStatus).type">{{ alertStatusMeta(scope.row.alertStatus).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="预计越界时刻" width="165" align="center">
          <template slot-scope="scope">{{ formatMs(toEpochMs(scope.row.predictedBreachTime)) }}</template>
        </el-table-column>
        <el-table-column label="剩余时间" width="110" align="center">
          <template slot-scope="scope">{{ scope.row.predictedBreachTime ? countdown(toEpochMs(scope.row.predictedBreachTime)) : '-' }}</template>
        </el-table-column>
        <el-table-column label="触发时间" width="165" align="center">
          <template slot-scope="scope">{{ formatMs(toEpochMs(scope.row.triggerTime)) }}</template>
        </el-table-column>
        <el-table-column label="维护建议" prop="suggestion" min-width="180" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.suggestion || '-' }}</template>
        </el-table-column>
        <el-table-column label="证据" prop="evidence" min-width="220" show-overflow-tooltip>
          <template slot-scope="scope">{{ evidenceBrief(scope.row.evidence) }}</template>
        </el-table-column>
      </el-table>
      <pagination v-show="alertsTotal > 0" :total="alertsTotal" :page.sync="alertsQuery.pageNum" :limit.sync="alertsQuery.pageSize" @pagination="getAlerts" />
    </el-card>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { listPredictSensors, fetchPredictDetail, fetchPredictAlerts } from '@/api/business/predict'

// 状态严重度映射：排序用（BREACHED=2 / DEGRADING=1 / NORMAL=0）
const SEVERITY = { BREACHED: 2, DEGRADING: 1, NORMAL: 0 }

export default {
  name: 'MachinePredict',
  data() {
    return {
      sensors: [],
      currentSensor: '',
      detail: null,
      chart: null,
      chartLoading: false,
      alertList: [],
      alertsTotal: 0,
      alertsLoading: false,
      alertsQuery: { pageNum: 1, pageSize: 10 },
      // 活跃(FIRING)预测告警数：独立轻量请求，轮询刷新，失败静默保留旧值
      activeAlertCount: 0,
      refreshTimer: null,
      // 剩余时间倒计时文本的缓存（每 30s 轮询刷新数据，倒计时文本每秒本地推进）
      tickTimer: null,
      tickFlag: 0
    }
  },
  computed: {
    // KPI 摘要统计：纯前端聚合传感器状态（status 缺省按 NORMAL）
    kpiStats() {
      const stats = { total: this.sensors.length, normal: 0, degrading: 0, breached: 0 }
      this.sensors.forEach(s => {
        const st = s.status || 'NORMAL'
        if (st === 'BREACHED') stats.breached++
        else if (st === 'DEGRADING') stats.degrading++
        else stats.normal++
      })
      return stats
    },
    // 传感器卡片按设备分组：组间按聚合严重度降序（同级按组最低健康分升序），组内按状态严重度降序（同级按健康分升序）
    equipmentGroups() {
      const groups = []
      const idxOf = {}
      this.sensors.forEach(s => {
        // equipmentId 统一转字符串作分组键，避免数字/字符串类型差异导致同设备拆组
        const key = String(s.equipmentId)
        if (idxOf[key] === undefined) {
          idxOf[key] = groups.length
          groups.push({ equipmentId: s.equipmentId, equipmentName: '', sensors: [], aggregateStatus: 'NORMAL', minHealthScore: 100, earliestBreachTimeMs: null })
        }
        groups[idxOf[key]].sensors.push(s)
      })
      groups.forEach(g => {
        // 设备名取组内第一个非空值，全空兜底 '设备#' + id（与告警列表口径一致）
        g.equipmentName = g.sensors.map(s => s.equipmentName).find(n => n) || ('设备#' + g.equipmentId)
        // 聚合状态：任一 BREACHED > 任一 DEGRADING > 全 NORMAL
        g.aggregateStatus = g.sensors.some(s => s.status === 'BREACHED')
          ? 'BREACHED'
          : (g.sensors.some(s => s.status === 'DEGRADING') ? 'DEGRADING' : 'NORMAL')
        // 组健康分：木桶原则取组内传感器最低分（null 按 100 参与）
        g.minHealthScore = g.sensors.reduce((min, s) => Math.min(min, this.healthScoreOf(s)), 100)
        // 组内最早预计越界时刻：仅有 predictedBreachTimeMs 的传感器参与，无则保持 null（展示 '-'）
        g.earliestBreachTimeMs = g.sensors.reduce((earliest, s) => {
          if (s.predictedBreachTimeMs == null) return earliest
          if (earliest == null || s.predictedBreachTimeMs < earliest) return s.predictedBreachTimeMs
          return earliest
        }, null)
        // 组内排序：状态严重度降序 -> 同级健康分升序 -> 原顺序（map+index 显式次级键保证稳定，不依赖引擎实现）
        g.sensors = g.sensors
          .map((s, i) => ({ s, i }))
          .sort((a, b) => (SEVERITY[b.s.status] || 0) - (SEVERITY[a.s.status] || 0) || this.healthScoreOf(a.s) - this.healthScoreOf(b.s) || a.i - b.i)
          .map(x => x.s)
      })
      // 组间排序：聚合严重度降序 -> 同级组最低健康分升序 -> 原顺序（map+index 显式次级键保证稳定）
      return groups
        .map((g, i) => ({ g, i }))
        .sort((a, b) => (SEVERITY[b.g.aggregateStatus] || 0) - (SEVERITY[a.g.aggregateStatus] || 0) || a.g.minHealthScore - b.g.minHealthScore || a.i - b.i)
        .map(x => x.g)
    },
    // 当前选中传感器元信息（含设备维度），供详情图标题使用
    currentSensorMeta() {
      const s = this.sensors.find(x => x.sensorCode === this.currentSensor)
      // 兜底：未选中或选中项已被移除时给占位值，避免标题渲染出 undefined
      return s || { equipmentName: '', equipmentId: '', sensorCode: this.currentSensor, sensorName: this.currentSensor || '未选择' }
    }
  },
  mounted() {
    this.init()
    // 30s 轮询：与后端预测任务调度周期(predict.interval-ms)一致
    this.refreshTimer = setInterval(() => {
      this.loadSensors(true)
      if (this.currentSensor) this.loadDetail()
      this.getAlerts()
      this.loadActiveAlertCount()
    }, 30000)
    // 倒计时文本每秒推进（只驱动文本，不拉数据）
    this.tickTimer = setInterval(() => { this.tickFlag++ }, 1000)
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer)
    clearInterval(this.tickTimer)
    window.removeEventListener('resize', this.resizeChart)
    if (this.chart) {
      this.chart.dispose()
      this.chart = null
    }
  },
  methods: {
    init() {
      this.loadSensors().then(() => {
        if (!this.currentSensor && this.sensors.length > 0) {
          this.selectSensor(this.sensors[0].sensorCode)
        }
      })
      this.getAlerts()
      this.loadActiveAlertCount()
    },
    // 加载传感器状态列表；silent 模式不重置当前选择（轮询刷新用）
    loadSensors(silent) {
      return listPredictSensors().then(list => {
        this.sensors = (list || []).map(s => ({ ...s, status: s.status || 'NORMAL' }))
        if (!silent && this.sensors.length > 0 && !this.sensors.some(s => s.sensorCode === this.currentSensor)) {
          this.selectSensor(this.sensors[0].sensorCode)
        }
      }).catch(() => {
        this.sensors = []
      })
    },
    selectSensor(code) {
      if (this.currentSensor === code) return
      this.currentSensor = code
      this.loadDetail()
    },
    loadDetail() {
      if (!this.currentSensor) return Promise.resolve()
      this.chartLoading = true
      return fetchPredictDetail(this.currentSensor).then(detail => {
        this.detail = detail
        this.renderChart()
      }).catch(() => {
        this.detail = null
      }).finally(() => {
        this.chartLoading = false
      })
    },
    getAlerts() {
      this.alertsLoading = true
      fetchPredictAlerts(this.alertsQuery).then(res => {
        this.alertList = res.rows
        this.alertsTotal = res.total
      }).finally(() => {
        this.alertsLoading = false
      })
    },
    // 活跃(FIRING)预测告警总数：pageSize=1 轻量请求，仅取 total；失败静默保留旧值
    loadActiveAlertCount() {
      return fetchPredictAlerts({ pageNum: 1, pageSize: 1, alertStatus: 'FIRING' }).then(res => {
        this.activeAlertCount = res.total
      }).catch(() => {})
    },
    // 详情图：原始曲线 / 平滑线 / 预测带 / 阈值线 / t1 标注
    renderChart() {
      if (!this.$refs.chart) return
      if (!this.chart) {
        this.chart = echarts.init(this.$refs.chart)
        window.addEventListener('resize', this.resizeChart)
      }
      const d = this.detail
      if (!d) {
        this.chart.clear()
        return
      }
      const series = [
        {
          name: '原始数据', type: 'line', showSymbol: false, symbolSize: 2,
          data: (d.raw || []).map(p => [p.ts, p.val]),
          lineStyle: { width: 1, opacity: 0.45, color: '#909399' },
          itemStyle: { color: '#909399' }
        },
        {
          name: '平滑线', type: 'line', showSymbol: false, smooth: true,
          data: (d.smoothed || []).map(p => [p.ts, p.val]),
          lineStyle: { width: 2, color: '#409EFF' }, itemStyle: { color: '#409EFF' }
        }
      ]
      // 预测带：low 面（mid−half 与 mid 之间填充）用 stack 技巧：bandLow + bandGap 叠加成带
      const t = d.trend
      if (t && t.band && t.band.length > 0) {
        const bandLow = []
        const bandGap = []
        const midLine = []
        t.band.forEach(b => {
          bandLow.push([b[0], b[1]])
          bandGap.push([b[0], b[2] - b[1]])
          midLine.push([b[0], b[2]])
        })
        series.push(
          {
            name: '预测带下界', type: 'line', stack: 'band', showSymbol: false,
            data: bandLow, lineStyle: { opacity: 0 }, areaStyle: { color: 'rgba(230,162,60,0.12)' },
            tooltip: { show: false }, silent: true
          },
          {
            name: '预测带', type: 'line', stack: 'band', showSymbol: false,
            data: bandGap, lineStyle: { opacity: 0 }, areaStyle: { color: 'rgba(230,162,60,0.25)' },
            tooltip: { show: false }, silent: true
          },
          {
            name: '预测中值', type: 'line', showSymbol: false,
            data: midLine,
            lineStyle: { width: 2, type: 'dashed', color: '#E6A23C' }, itemStyle: { color: '#E6A23C' }
          }
        )
      }
      const markLines = []
      if (t && t.threshold != null) {
        markLines.push({
          yAxis: t.threshold, name: '阈值',
          lineStyle: { color: '#F56C6C', type: 'dashed', width: 1.5 },
          label: { formatter: `阈值 ${t.threshold}`, position: 'insideEndTop' }
        })
      }
      if (t && t.predictedBreachTimeMs != null) {
        markLines.push({
          xAxis: t.predictedBreachTimeMs, name: '预计越界',
          lineStyle: { color: '#F56C6C', type: 'solid', width: 1.5 },
          label: { formatter: '预计越界', position: 'start' }
        })
      }
      series.push({
        name: '阈值/越界标注', type: 'line', markLine: {
          symbol: 'none', silent: true, data: markLines
        },
        data: []
      })
      this.chart.setOption({
        tooltip: {
          trigger: 'axis',
          formatter: params => {
            const ts = params[0] && params[0].value && params[0].value[0]
            const head = ts ? this.formatMsFull(ts) + '<br/>' : ''
            const lines = params.filter(p => p.seriesName !== '预测带下界' && p.seriesName !== '预测带' && (p.value == null || p.value[1] != null)).map(p => {
              const v = p.value && p.value[1] != null ? p.value[1] : '-'
              return `${p.marker}${p.seriesName}：<b>${v}</b>`
            })
            return head + lines.join('<br/>')
          }
        },
        legend: { top: 0, data: ['原始数据', '平滑线', '预测中值'] },
        grid: { left: 50, right: 30, top: 34, bottom: 46 },
        xAxis: {
          type: 'time',
          axisLabel: { formatter: v => this.parseTime(new Date(v), '{m}-{d} {h}:{i}') || '' }
        },
        yAxis: { type: 'value', scale: true },
        dataZoom: [{ type: 'inside' }, { type: 'slider', height: 16, bottom: 8 }],
        series
      }, true)
    },
    resizeChart() {
      this.chart && this.chart.resize()
    },
    // ===== 展示工具 =====
    // 健康分取值：后端字段并行开发中，null 统一按 100，用于聚合与排序
    healthScoreOf(s) {
      return s.healthScore == null ? 100 : Number(s.healthScore)
    },
    // 健康分三态色：≥80 绿 / 60–80 橙 / <60 红（null 记 100）
    healthColor(score) {
      const v = score == null ? 100 : Number(score)
      if (v >= 80) return '#67C23A'
      if (v >= 60) return '#E6A23C'
      return '#F56C6C'
    },
    // 健康分展示文本：四舍五入取整，避免 Double 长小数直接上屏
    healthText(score) {
      const v = score == null ? 100 : Number(score)
      return String(Math.round(v))
    },
    statusMeta(status) {
      return {
        NORMAL: { label: '正常', type: 'success' },
        DEGRADING: { label: '劣化中', type: 'warning' },
        BREACHED: { label: '已越界', type: 'danger' }
      }[status] || { label: status || '正常', type: 'info' }
    },
    // 卡片"预计越界/剩余时间"两列的显示条件：NORMAL 态即使残留旧 t1Points 也视为无预测
    hasForecast(s) {
      return s.status !== 'NORMAL' && s.t1Points != null
    },
    // 防御性补齐四级映射:预测链路目前只产 WARNING/SEVERE,但与其他页面共用同一套等级标签语义,
    // 万一后端放开预测四级,这里兜底防止列表直接显示英文原串;视觉规则与告警页一致
    levelMeta(level) {
      return {
        CRITICAL: { label: '危急', type: 'danger', effect: 'dark' },
        SEVERE: { label: '严重', type: 'danger', effect: 'light' },
        IMPORTANT: { label: '重要', type: 'warning', effect: 'dark' },
        WARNING: { label: '预警', type: 'warning', effect: 'light' },
        NORMAL: { label: '正常', type: 'info', effect: 'light' }
      }[level] || { label: level || '-', type: 'info', effect: 'light' }
    },
    alertStatusMeta(s) {
      return { FIRING: { label: '告警中', type: 'danger' }, ACKED: { label: '已确认', type: 'warning' }, RESOLVED: { label: '已恢复', type: 'success' } }[s] || { label: s || '-', type: 'info' }
    },
    // LocalDateTime 兼容转 epoch ms：REST 返回 ISO 字符串或数组（Jackson 版本差异），统一转毫秒
    toEpochMs(time) {
      if (time == null) return null
      if (typeof time === 'number') return time
      if (typeof time === 'string') return new Date(time.replace('T', ' ').replace(/-/g, '/')).getTime()
      if (Array.isArray(time)) return new Date(time[0] || 1970, (time[1] || 1) - 1, time[2] || 1, time[3] || 0, time[4] || 0, time[5] || 0).getTime()
      return null
    },
    formatMs(ms) {
      if (ms == null) return '-'
      const d = new Date(Number(ms))
      const p = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
    },
    formatMsFull(ms) {
      return this.formatMs(ms)
    },
    // 剩余时间倒计时：依赖 tickFlag 触发响应式刷新
    countdown(ms) {
      void this.tickFlag
      if (ms == null) return '-'
      const diff = Number(ms) - Date.now()
      if (diff <= 0) return '已到/已越界'
      const totalSec = Math.floor(diff / 1000)
      const m = Math.floor(totalSec / 60)
      const s = totalSec % 60
      return m > 0 ? `${m}分${String(s).padStart(2, '0')}秒` : `${s}秒`
    },
    // evidence JSON 摘要：提取 slope/r2/onset/t1 关键字段
    evidenceBrief(evidence) {
      if (!evidence) return '-'
      try {
        const ev = JSON.parse(evidence)
        const parts = []
        if (ev.slope != null) parts.push(`速率 ${ev.slope}/点`)
        if (ev.r2 != null) parts.push(`R² ${ev.r2}`)
        if (ev.t1Points != null) parts.push(`t1 ${ev.t1Points}点`)
        if (ev.onset != null) parts.push(`起点 ${this.formatMs(ev.onset)}`)
        return parts.join('，') || evidence
      } catch (e) {
        return evidence
      }
    }
  }
}
</script>

<style lang="scss" scoped>
/* KPI 摘要条：纯前端聚合统计，一行展示 */
.kpi-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 40px;
  padding: 14px 16px;
  margin-bottom: 16px;
  background: #f5f7fa;
  border-radius: 6px;
  .kpi-item { display: flex; align-items: baseline; gap: 8px; }
  .kpi-label { font-size: 12px; color: #909399; }
  .kpi-value { font-size: 20px; font-weight: 700; line-height: 1; color: #303133; }
  .kpi-success { color: #67C23A; }
  .kpi-warning { color: #E6A23C; }
  .kpi-danger { color: #F56C6C; }
}
.sensor-cards { margin-bottom: 12px; }
/* 设备分组区块：与相邻分组/图表卡的垂直间距 */
.equipment-group { margin-bottom: 16px; }
.equipment-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  .equipment-name { font-size: 14px; font-weight: 600; color: #303133; }
  .equipment-title-left { display: flex; align-items: center; gap: 8px; }
  .equipment-title-right { display: flex; align-items: center; gap: 20px; }
  .group-health { display: flex; align-items: baseline; gap: 6px; }
  .group-health-label { font-size: 12px; color: #909399; }
  .group-health-score { font-size: 22px; font-weight: 700; line-height: 1; }
  .group-breach { font-size: 12px; color: #909399; }
}
.sensor-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  &:hover { border-color: #c6e2ff; }
  &.active { border-color: #409EFF; box-shadow: 0 0 0 1px #409EFF inset; }
  .sensor-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    .sensor-card-name { font-size: 14px; font-weight: 600; color: #303133; }
  }
  .sensor-card-body { display: flex; gap: 18px; }
  .sensor-card-item {
    display: flex;
    flex-direction: column;
    .label { font-size: 12px; color: #909399; }
    .value { font-size: 13px; color: #303133; font-weight: 500; &.danger { color: #F56C6C; } }
  }
}
.chart-card { margin-bottom: 12px; }
.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .chart-title { font-size: 14px; font-weight: 600; }
  .chart-toolbar { display: flex; align-items: center; gap: 8px; }
}
.chart { height: 380px; width: 100%; }
.chart-legend-tips {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding-top: 8px;
  font-size: 12px;
  color: #909399;
  .dot {
    display: inline-block;
    width: 10px;
    height: 3px;
    border-radius: 2px;
    margin-right: 4px;
    vertical-align: middle;
    &.raw { background: #909399; }
    &.smoothed { background: #409EFF; }
    &.band { background: rgba(230,162,60,0.5); }
    &.threshold { background: #F56C6C; }
  }
}
</style>
