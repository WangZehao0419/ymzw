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

    <!-- ① 设备健康总览：每设备一张卡（gauge 健康分 + 聚合状态 + 最紧迫 RUL），数据来自 /predict/overview，随 30s 轮询刷新 -->
    <el-card shadow="never" class="overview-card">
      <div slot="header" class="chart-header">
        <span class="chart-title">① 设备健康总览</span>
        <span class="chart-subtitle">健康分=成员传感器均值；RUL=最紧迫剩余寿命（分钟）</span>
        <el-button size="mini" icon="el-icon-refresh" circle @click="loadOverview" />
      </div>
      <el-row v-if="overviewList.length" :gutter="12">
        <el-col v-for="eq in overviewList" :key="eq.equipmentId" :xs="24" :sm="12" :md="8" :lg="6">
          <div class="equip-card">
            <div class="equip-card-head">
              <span class="equip-name">{{ eq.equipmentName || '设备#' + eq.equipmentId }}</span>
              <el-tag size="mini" :type="statusMeta(eq.status).type">{{ statusMeta(eq.status).label }}</el-tag>
            </div>
            <!-- 仪表盘 ref 用动态名：v-for 内 ref 注册为数组，取 [0] -->
            <div :ref="'gauge-' + eq.equipmentId" class="equip-gauge" />
            <div class="equip-rul" :class="{ risk: eq.minRulPoint != null }">
              <template v-if="eq.minRulPoint != null">
                <span class="equip-rul-sensor">{{ sensorNameOf(eq.minRulSensorCode) }}</span>
                <span class="equip-rul-text">{{ rulText(eq.minRulPoint) }}</span>
              </template>
              <span v-else class="equip-rul-none">暂无风险</span>
            </div>
          </div>
        </el-col>
      </el-row>
      <div v-else class="overview-empty">暂无设备预测数据</div>
    </el-card>

    <!-- ② 传感器列表：按设备分组卡片 -->
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
              <!-- 健康分徽标：三态底色（≥80 绿 / 60-80 橙 / <60 红），任务未跑过无分显示 '-' -->
              <span class="health-badge" :style="healthBadgeStyle(s.healthScore)">{{ s.healthScore != null ? healthText(s.healthScore) : '-' }}</span>
            </div>
            <div class="sensor-card-body">
              <div class="sensor-card-item">
                <span class="label">状态</span>
                <span class="value"><el-tag size="mini" :type="statusMeta(s.status).type">{{ statusMeta(s.status).label }}</el-tag></span>
              </div>
              <div class="sensor-card-item">
                <span class="label">异常评分</span>
                <span class="value">{{ s.anomalyScore != null ? s.anomalyScore : '-' }}</span>
              </div>
              <div class="sensor-card-item">
                <span class="label">预计越界</span>
                <!-- 后端 NORMAL 态已显式清空 predicted_breach_time，无值即无风险 -->
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

    <!-- ③ 趋势详情：原始窗口 + 模型分位带（q10-q90）/q50 中位线 + RUL 标注 -->
    <el-card shadow="never" class="chart-card">
      <div slot="header" class="chart-header">
        <span class="chart-title">{{ (currentSensorMeta.equipmentName || '设备#' + currentSensorMeta.equipmentId) + ' / ' + (currentSensorMeta.sensorName || currentSensorMeta.sensorCode) }} — 趋势预测</span>
        <div class="chart-toolbar">
          <el-tag v-if="detailAi && detailAi.isAnomaly != null" size="small" :type="detailAi.isAnomaly ? 'danger' : 'success'">{{ detailAi.isAnomaly ? '模型判定异常' : '模型判定正常' }}</el-tag>
          <el-tag v-if="detailAi && detailAi.rulPoint != null" type="danger" size="small">RUL 点估计 {{ detailAi.rulPoint }} 分钟</el-tag>
          <el-button size="mini" type="primary" plain icon="el-icon-magic-stick" :loading="diagLoading" :disabled="currentSensorMeta.equipmentId === ''" @click="openDiagnoseFromDetail">AI 诊断</el-button>
          <el-button size="mini" icon="el-icon-refresh" circle @click="loadDetail" />
        </div>
      </div>
      <!-- ai 字段信息条：异常评分/健康分/失效区间/模型版本/快照时间 -->
      <div v-if="detailAi" class="ai-info-bar">
        <span class="ai-info-item">异常评分 <b>{{ detailAi.anomalyScore != null ? detailAi.anomalyScore : '-' }}</b></span>
        <span class="ai-info-item">健康分 <b :style="{ color: healthColor(detailAi.healthScore) }">{{ detailAi.healthScore != null ? healthText(detailAi.healthScore) : '-' }}</b></span>
        <span class="ai-info-item">模型版本 <b>{{ detailAi.modelVersion || '-' }}</b></span>
        <span v-if="detailAi.rulEarliest != null || detailAi.rulLatest != null" class="ai-info-item">
          失效区间 <b>最早 {{ detailAi.rulEarliest != null ? detailAi.rulEarliest + ' 分钟' : '-' }} / 最晚 {{ detailAi.rulLatest != null ? detailAi.rulLatest + ' 分钟' : '-' }}</b>
        </span>
        <span v-if="detail && detail.updateTimeMs" class="ai-info-item">快照时间 <b>{{ formatMs(detail.updateTimeMs) }}</b></span>
      </div>
      <div ref="chart" class="chart" v-loading="chartLoading" />
      <div class="chart-legend-tips">
        <span><i class="dot raw" />原始数据：传感器上报原始值</span>
        <span><i class="dot q50" />q50 中位预测：模型对未来各时刻的最可能取值（每点间隔 1 分钟）</span>
        <span><i class="dot band" />q10-q90 分位带：该时刻实际值落在此区间的概率约 80%</span>
        <span><i class="dot rul" />RUL 标注：竖线为剩余寿命点估计时刻，浅红区间为最早-最晚失效范围（单位：分钟）</span>
      </div>
    </el-card>

    <!-- ③+ AI 诊断抽屉：报告（markdown 源文展示）+ 历史诊断列表 -->
    <el-drawer custom-class="diag-drawer" :title="diagTitle" :visible.sync="diagVisible" direction="rtl" size="640px" append-to-body>
      <div class="diag-body">
        <div v-loading="diagLoading" class="diag-report-wrap" element-loading-text="诊断生成中（含 LLM 调用，约需数十秒）…">
          <div v-if="diagViewingRecordId != null" class="diag-report-meta">正在查看历史记录 #{{ diagViewingRecordId }}</div>
          <div v-if="diagReport" class="diag-report"><pre>{{ diagReport }}</pre></div>
          <div v-else-if="!diagLoading" class="diag-empty">暂无诊断报告</div>
        </div>
        <div v-if="diagContext" class="diag-actions">
          <el-button size="mini" type="primary" icon="el-icon-refresh" :loading="diagLoading" @click="runDiagnose">重新生成</el-button>
        </div>
        <div class="diag-history">
          <div class="diag-history-title">历史诊断记录</div>
          <el-table v-loading="diagRecordsLoading" :data="diagRecords" size="small" highlight-current-row @row-click="viewDiagnosisRecord">
            <el-table-column label="时间" width="160" align="center">
              <!-- B6 实体已定为 createdTime，多字段兜底防契约微调 -->
              <template slot-scope="scope">{{ formatMs(toEpochMs(scope.row.createdTime != null ? scope.row.createdTime : (scope.row.createTime != null ? scope.row.createTime : scope.row.createTimeMs))) }}</template>
            </el-table-column>
            <el-table-column label="传感器" min-width="130" show-overflow-tooltip>
              <template slot-scope="scope">{{ scope.row.sensorName || scope.row.sensorCode || '-' }}</template>
            </el-table-column>
            <el-table-column label="摘要" min-width="180" show-overflow-tooltip>
              <template slot-scope="scope">{{ diagRecordBrief(scope.row) }}</template>
            </el-table-column>
          </el-table>
          <pagination v-show="diagTotal > 0" :total="diagTotal" :page.sync="diagQuery.pageNum" :limit.sync="diagQuery.pageSize" @pagination="loadDiagnosisRecords" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { listPredictSensors, fetchPredictDetail, fetchPredictAlerts, fetchPredictOverview, diagnoseSensor, listDiagnosisRecords } from '@/api/business/predict'

// 状态严重度映射：排序用（BREACHED=2 / DEGRADING=1 / NORMAL=0）
const SEVERITY = { BREACHED: 2, DEGRADING: 1, NORMAL: 0 }
// 模型预测步长（分钟）：q10/q50/q90 每点间隔，与后端 predict.model.horizon 的分钟口径一致
const FORECAST_STEP_MS = 60 * 1000

export default {
  name: 'MachinePredict',
  data() {
    return {
      sensors: [],
      currentSensor: '',
      detail: null,
      chart: null,
      chartLoading: false,
      // 活跃(FIRING)预测告警数：独立轻量请求，轮询刷新，失败静默保留旧值
      activeAlertCount: 0,
      refreshTimer: null,
      // 剩余时间倒计时文本的缓存（每 30s 轮询刷新数据，倒计时文本每秒本地推进）
      tickTimer: null,
      tickFlag: 0,
      // ① 设备健康总览数据（/predict/overview）
      overviewList: [],
      // 设备健康仪表盘实例：key=equipmentId，避免 v-for 重渲染时重复 init
      gauges: {},
      // ③+ AI 诊断抽屉状态
      diagVisible: false,
      diagLoading: false,
      diagContext: null,
      diagReport: '',
      diagViewingRecordId: null,
      diagRecords: [],
      diagTotal: 0,
      diagRecordsLoading: false,
      diagQuery: { pageNum: 1, pageSize: 5, equipmentId: undefined, sensorCode: undefined }
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
    },
    // 本轮详情的模型推理产物（B4 起嵌套在 detail.ai 下，旧回归带字段已删除）
    detailAi() {
      return (this.detail && this.detail.ai) || null
    },
    // 诊断抽屉标题：设备 / 传感器 维度
    diagTitle() {
      if (!this.diagContext) return 'AI 诊断'
      const eq = this.diagContext.equipmentName || '设备#' + this.diagContext.equipmentId
      const se = this.diagContext.sensorName || this.diagContext.sensorCode
      return `AI 诊断 — ${eq} / ${se}`
    }
  },
  mounted() {
    this.init()
    // 30s 轮询：与后端预测任务调度周期(predict.interval-ms)一致
    this.refreshTimer = setInterval(() => {
      this.loadSensors(true)
      this.loadOverview()
      if (this.currentSensor) this.loadDetail()
      this.loadActiveAlertCount()
    }, 30000)
    // 倒计时文本每秒推进（只驱动文本，不拉数据）
    this.tickTimer = setInterval(() => { this.tickFlag++ }, 1000)
    window.addEventListener('resize', this.resizeCharts)
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer)
    clearInterval(this.tickTimer)
    window.removeEventListener('resize', this.resizeCharts)
    if (this.chart) {
      this.chart.dispose()
      this.chart = null
    }
    // 释放所有设备健康仪表盘实例，防止内存泄漏
    Object.keys(this.gauges).forEach(k => {
      this.gauges[k].dispose()
      delete this.gauges[k]
    })
  },
  methods: {
    init() {
      this.loadSensors().then(() => {
        if (!this.currentSensor && this.sensors.length > 0) {
          this.selectSensor(this.sensors[0].sensorCode)
        }
      })
      this.loadOverview()
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
    // ① 设备健康总览：失败静默保留旧值（与活跃告警计数同策略）
    loadOverview() {
      return fetchPredictOverview().then(list => {
        this.overviewList = list || []
        // 等 v-for 渲染出 gauge 容器后再初始化/更新仪表盘
        this.$nextTick(() => this.renderGauges())
      }).catch(() => {})
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
    // 活跃(FIRING)预测告警总数：pageSize=1 轻量请求，仅取 total；失败静默保留旧值
    loadActiveAlertCount() {
      return fetchPredictAlerts({ pageNum: 1, pageSize: 1, alertStatus: 'FIRING' }).then(res => {
        this.activeAlertCount = res.total
      }).catch(() => {})
    },
    // ① 渲染设备健康仪表盘：复用已建实例，清理已消失设备的实例
    renderGauges() {
      const alive = {}
      this.overviewList.forEach(eq => {
        const key = String(eq.equipmentId)
        const el = (this.$refs['gauge-' + key] || [])[0]
        if (!el) return
        alive[key] = this.gauges[key] || echarts.init(el)
        alive[key].setOption(this.gaugeOption(eq), true)
      })
      Object.keys(this.gauges).forEach(k => {
        if (!alive[k]) {
          this.gauges[k].dispose()
          delete this.gauges[k]
        }
      })
      this.gauges = alive
    },
    // 单设备仪表盘配置：三态变色（≥80 绿 / 60-80 橙 / <60 红），无快照数据显示 '-'
    gaugeOption(eq) {
      const hasScore = eq.healthScore != null
      const color = hasScore ? this.healthColor(eq.healthScore) : '#909399'
      return {
        series: [{
          type: 'gauge',
          center: ['50%', '58%'],
          radius: '92%',
          startAngle: 200,
          endAngle: -20,
          min: 0,
          max: 100,
          progress: { show: true, width: 8, itemStyle: { color } },
          pointer: { show: false },
          axisLine: { roundCap: true, lineStyle: { width: 8, color: [[1, '#ebeef5']] } },
          axisTick: { show: false },
          splitLine: { show: false },
          axisLabel: { show: false },
          title: { show: true, offsetCenter: [0, '38%'], fontSize: 11, color: '#909399' },
          detail: {
            valueAnimation: true,
            fontSize: 22,
            fontWeight: 700,
            color,
            offsetCenter: [0, 0],
            formatter: hasScore ? v => String(Math.round(v)) : () => '-'
          },
          data: [{ value: hasScore ? Number(eq.healthScore) : 0, name: '健康分' }]
        }]
      }
    },
    // ③ 详情图：原始窗口 / q10-q90 分位带 / q50 中位线 / RUL 标注
    renderChart() {
      if (!this.$refs.chart) return
      if (!this.chart) {
        this.chart = echarts.init(this.$refs.chart)
      }
      const d = this.detail
      if (!d) {
        this.chart.clear()
        return
      }
      const raw = d.raw || []
      const ai = d.ai || {}
      const series = [
        {
          name: '原始数据', type: 'line', showSymbol: false, symbolSize: 2,
          data: raw.map(p => [p.ts, p.val]),
          lineStyle: { width: 1, opacity: 0.45, color: '#909399' },
          itemStyle: { color: '#909399' }
        }
      ]
      // 分位带：q10 与 q90 形成区间填充（stack 技巧：基底 + 差值叠加成带）
      // 时间映射：q 系列每点间隔 1 分钟，紧接窗口末点之后（与后端 horizon 分钟口径一致）
      const lastTs = raw.length > 0 ? raw[raw.length - 1].ts : Date.now()
      const bandBase = []
      const bandGap = []
      const q10Line = []
      const q50Line = []
      const q90Line = []
      const q10 = ai.q10 || []
      const q50 = ai.q50 || []
      const q90 = ai.q90 || []
      const len = Math.max(q10.length, q50.length, q90.length)
      for (let i = 0; i < len; i++) {
        const ts = lastTs + (i + 1) * FORECAST_STEP_MS
        if (q10[i] != null && q90[i] != null) {
          bandBase.push([ts, q10[i]])
          bandGap.push([ts, q90[i] - q10[i]])
        }
        if (q10[i] != null) q10Line.push([ts, q10[i]])
        if (q50[i] != null) q50Line.push([ts, q50[i]])
        if (q90[i] != null) q90Line.push([ts, q90[i]])
      }
      if (bandBase.length > 0) {
        series.push(
          {
            name: '分位带基底', type: 'line', stack: 'band', showSymbol: false,
            data: bandBase, lineStyle: { opacity: 0 }, areaStyle: { color: 'rgba(124,58,237,0.10)' },
            tooltip: { show: false }, silent: true
          },
          {
            name: '分位带', type: 'line', stack: 'band', showSymbol: false,
            data: bandGap, lineStyle: { opacity: 0 }, areaStyle: { color: 'rgba(124,58,237,0.18)' },
            tooltip: { show: false }, silent: true
          }
        )
      }
      // 分位带上下边界线（浅紫）+ q50 中位线（紫色虚线），与原始灰线视觉区分
      series.push(
        {
          name: 'q10 下界', type: 'line', showSymbol: false,
          data: q10Line, lineStyle: { width: 1, opacity: 0.6, color: '#a78bfa' }, itemStyle: { color: '#a78bfa' }
        },
        {
          name: 'q90 上界', type: 'line', showSymbol: false,
          data: q90Line, lineStyle: { width: 1, opacity: 0.6, color: '#a78bfa' }, itemStyle: { color: '#a78bfa' }
        },
        {
          name: 'q50 中位预测', type: 'line', showSymbol: false, smooth: true,
          data: q50Line, lineStyle: { width: 2, type: 'dashed', color: '#7c3aed' }, itemStyle: { color: '#7c3aed' }
        }
      )
      // RUL 标注：点估计竖线 + 最早-最晚失效浅红区间（分钟数相对推理时刻换算为绝对时刻）
      const markLines = []
      const markAreas = []
      const now = Date.now()
      if (ai.rulPoint != null) {
        markLines.push({
          xAxis: now + ai.rulPoint * 60000, name: 'RUL 点估计',
          lineStyle: { color: '#F56C6C', type: 'solid', width: 1.5 },
          label: { formatter: `RUL ${ai.rulPoint}分钟`, position: 'start' }
        })
      }
      if (ai.rulEarliest != null && ai.rulLatest != null) {
        markAreas.push([
          {
            xAxis: now + ai.rulEarliest * 60000, name: '失效区间',
            itemStyle: { color: 'rgba(245,108,108,0.06)' },
            label: { formatter: `失效区间 ${ai.rulEarliest}-${ai.rulLatest}分钟`, position: 'inside' }
          },
          { xAxis: now + ai.rulLatest * 60000 }
        ])
      }
      series.push({
        name: 'RUL 标注', type: 'line', data: [],
        markLine: { symbol: 'none', silent: true, data: markLines },
        markArea: { silent: true, data: markAreas }
      })
      const unit = d.unit ? ' ' + d.unit : ''
      this.chart.setOption({
        tooltip: {
          trigger: 'axis',
          formatter: params => {
            const ts = params[0] && params[0].value && params[0].value[0]
            const head = ts ? this.formatMsFull(ts) + '<br/>' : ''
            const lines = params.filter(p => p.seriesName !== '分位带基底' && p.seriesName !== '分位带' && p.seriesName !== 'RUL 标注' && (p.value == null || p.value[1] != null)).map(p => {
              const v = p.value && p.value[1] != null ? p.value[1] : '-'
              return `${p.marker}${p.seriesName}：<b>${v}${unit}</b>`
            })
            return head + lines.join('<br/>')
          }
        },
        legend: { top: 0, data: ['原始数据', 'q10 下界', 'q90 上界', 'q50 中位预测'] },
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
    resizeCharts() {
      this.chart && this.chart.resize()
      Object.keys(this.gauges).forEach(k => this.gauges[k].resize())
    },
    // ===== ③+ AI 诊断 =====
    // 打开诊断抽屉：重置上下文 -> 拉历史记录 -> 触发本次诊断
    openDiagnose(ctx) {
      if (this.diagLoading) return // 生成中不允许切换上下文，防止请求与展示错位
      this.diagContext = { ...ctx }
      this.diagVisible = true
      this.diagReport = ''
      this.diagViewingRecordId = null
      this.diagQuery = { pageNum: 1, pageSize: 5, equipmentId: ctx.equipmentId, sensorCode: ctx.sensorCode }
      this.loadDiagnosisRecords()
      this.runDiagnose()
    },
    // 详情区「AI 诊断」按钮入口：取当前选中传感器的设备维度
    openDiagnoseFromDetail() {
      const m = this.currentSensorMeta
      if (!m.sensorCode || m.equipmentId === '') return
      this.openDiagnose({ equipmentId: m.equipmentId, sensorCode: m.sensorCode, sensorName: m.sensorName, equipmentName: m.equipmentName })
    },
    // 触发诊断：loading 态防重复点击，成功后顺带刷新历史列表
    runDiagnose() {
      if (!this.diagContext || this.diagLoading) return
      this.diagLoading = true
      this.diagViewingRecordId = null
      diagnoseSensor(this.diagContext.equipmentId, this.diagContext.sensorCode).then(res => {
        this.diagReport = this.extractReport(res)
        this.loadDiagnosisRecords()
      }).catch(() => {
        this.diagReport = ''
      }).finally(() => {
        this.diagLoading = false
      })
    },
    loadDiagnosisRecords() {
      if (!this.diagContext) return
      this.diagRecordsLoading = true
      listDiagnosisRecords({ ...this.diagQuery }).then(res => {
        this.diagRecords = res.rows
        this.diagTotal = res.total
      }).catch(() => {
        this.diagRecords = []
        this.diagTotal = 0
      }).finally(() => {
        this.diagRecordsLoading = false
      })
    },
    // 点击历史条目：在报告区查看其报告内容
    viewDiagnosisRecord(row) {
      const text = this.extractReport(row)
      if (!text) return
      this.diagReport = text
      this.diagViewingRecordId = row.id != null ? row.id : null
    },
    // 诊断报告字段防御性提取：B6 并行开发中，兼容 report/content/response 等可能字段名与 R/AjaxResult 两种包裹
    extractReport(res) {
      if (res == null) return ''
      const d = res.data != null ? res.data : res
      if (typeof d === 'string') return d
      if (d && typeof d === 'object') return d.report || d.content || d.reportContent || d.response || d.result || ''
      return ''
    },
    // 历史记录摘要：优先 summary 字段，缺省截取报告前 60 字
    diagRecordBrief(row) {
      if (row.summary) return row.summary
      const text = this.extractReport(row)
      if (text) return text.length > 60 ? text.slice(0, 60) + '…' : text
      return '-'
    },
    // ===== 展示工具 =====
    // 健康分取值：任务未跑过为 null，聚合排序时统一按 100
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
    // 传感器卡片健康分徽标样式：三态底色 + 白字（无分灰底，与总览仪表盘口径一致）
    healthBadgeStyle(score) {
      return { background: score == null ? '#909399' : this.healthColor(score), color: '#fff' }
    },
    statusMeta(status) {
      return {
        NORMAL: { label: '正常', type: 'success' },
        DEGRADING: { label: '劣化中', type: 'warning' },
        BREACHED: { label: '已越界', type: 'danger' }
      }[status] || { label: status || '正常', type: 'info' }
    },
    // 传感器编号 -> 名称（总览卡片 RUL 展示用），查不到回退编号本身
    sensorNameOf(code) {
      if (!code) return '-'
      const s = this.sensors.find(x => x.sensorCode === code)
      return (s && s.sensorName) || code
    },
    // 最紧迫 RUL 文案：null 无风险 / 负值已到失效时刻 / 正值 X 分钟后越限
    rulText(minutes) {
      if (minutes == null) return '暂无风险'
      const m = Number(minutes)
      if (m <= 0) return '已到失效时刻'
      return `${m} 分钟后越限`
    },
    // 卡片"预计越界/剩余时间"两列的显示条件：B4 起后端 NORMAL 态显式清空 predicted_breach_time
    hasForecast(s) {
      return s.predictedBreachTimeMs != null
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
/* ① 设备健康总览卡片 */
.overview-card { margin-bottom: 16px; }
.chart-subtitle { font-size: 12px; color: #909399; }
.overview-empty { padding: 24px 0; text-align: center; font-size: 13px; color: #909399; }
.equip-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px 8px;
  margin-bottom: 12px;
  .equip-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    .equip-name { font-size: 13px; font-weight: 600; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  }
  .equip-gauge { height: 150px; width: 100%; }
  .equip-rul {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding-top: 4px;
    border-top: 1px dashed #ebeef5;
    font-size: 12px;
    .equip-rul-sensor { color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .equip-rul-text { color: #303133; white-space: nowrap; }
    .equip-rul-none { color: #67C23A; }
    &.risk .equip-rul-text { color: #F56C6C; font-weight: 600; }
  }
}
.sensor-cards { margin-bottom: 12px; }
/* ② 传感器分组区块：与相邻分组/图表卡的垂直间距 */
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
    /* 健康分徽标：三态底色由内联 style 控制 */
    .health-badge {
      display: inline-block;
      min-width: 40px;
      padding: 1px 8px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 600;
      text-align: center;
      line-height: 18px;
    }
  }
  .sensor-card-body { display: flex; gap: 18px; }
  .sensor-card-item {
    display: flex;
    flex-direction: column;
    .label { font-size: 12px; color: #909399; }
    .value { font-size: 13px; color: #303133; font-weight: 500; &.danger { color: #F56C6C; } }
  }
}
/* ③ 趋势详情卡 */
.chart-card { margin-bottom: 12px; }
.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .chart-title { font-size: 14px; font-weight: 600; }
  .chart-toolbar { display: flex; align-items: center; gap: 8px; }
}
/* ai 字段信息条 */
.ai-info-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
  .ai-info-item b { font-weight: 600; color: #303133; margin-left: 4px; }
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
    &.q50 { background: #7c3aed; }
    &.band { background: rgba(124,58,237,0.35); }
    &.rul { background: #F56C6C; }
  }
}
/* ③+ AI 诊断抽屉（append-to-body 下仅样式化自有元素，抽屉体自带滚动） */
.diag-body { padding: 0 20px 20px; }
.diag-report-wrap { min-height: 160px; }
.diag-report-meta { font-size: 12px; color: #909399; margin-bottom: 6px; }
.diag-report {
  background: #f8f9fb;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 16px;
  pre {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-family: inherit;
    font-size: 13px;
    line-height: 1.7;
    color: #303133;
  }
}
.diag-empty { padding: 32px 0; text-align: center; font-size: 13px; color: #909399; }
.diag-actions { padding: 10px 0; border-bottom: 1px solid #ebeef5; }
.diag-history { padding-top: 10px; }
.diag-history-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
</style>
