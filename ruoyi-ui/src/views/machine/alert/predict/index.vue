<template>
  <div class="app-container">
    <!-- ===== 设备层（默认视图）：设备大卡网格，无外框，点击进入传感器层 ===== -->
    <div v-if="currentView === 'device'">
      <div class="view-toolbar">
        <span class="view-toolbar-title">设备健康总览</span>
        <el-button size="mini" icon="el-icon-refresh" circle @click="refreshDeviceView" />
      </div>
      <el-row v-if="equipmentGroups.length" :gutter="12">
        <el-col v-for="group in equipmentGroups" :key="group.equipmentId" :xs="24" :sm="12" :md="8">
          <!-- 设备大卡：整卡可点击进入该设备的传感器趋势视图 -->
          <div class="equip-card" @click="enterSensorView(group)">
            <div class="equip-card-head">
              <span class="equip-name">{{ group.equipmentName }}</span>
              <div class="equip-head-right">
                <span class="equip-sensor-count">{{ group.sensors.length }} 传感器</span>
                <el-tag size="mini" :type="statusMeta(group.aggregateStatus).type">{{ statusMeta(group.aggregateStatus).label }}</el-tag>
                <!-- 采集基线：.stop 阻断设备卡点击(否则会误入传感器层) -->
                <el-button size="mini" plain icon="el-icon-data-line" :loading="!!baselineLoading[group.equipmentId]" @click.stop="collectBaseline(group)">采集基线</el-button>
              </div>
            </div>
            <!-- 仪表盘 ref 用动态名：v-for 内 ref 注册为数组，取 [0]；健康分取 overview 均值 -->
            <div :ref="'gauge-' + group.equipmentId" class="equip-gauge" />
            <div class="equip-rul" :class="{ risk: overviewOf(group) && overviewOf(group).minRulPoint != null }">
              <template v-if="overviewOf(group) && overviewOf(group).minRulPoint != null">
                <span class="equip-rul-sensor">{{ sensorNameOf(overviewOf(group).minRulSensorCode) }}</span>
                <span class="equip-rul-text">{{ rulText(overviewOf(group).minRulPoint) }}</span>
              </template>
              <span v-else class="equip-rul-none">暂无风险</span>
            </div>
            <!-- 三态计数：组内传感器状态统计 -->
            <div class="equip-counts">
              <span class="count-normal">正常 {{ group.counts.normal }}</span>
              <span class="count-degrading">劣化中 {{ group.counts.degrading }}</span>
              <span class="count-breached">已越界 {{ group.counts.breached }}</span>
            </div>
          </div>
        </el-col>
      </el-row>
      <div v-else class="overview-empty">暂无设备预测数据</div>
    </div>

    <!-- ===== 传感器层（点击设备进入）：该设备传感器一行一个全宽趋势卡 ===== -->
    <div v-else>
      <!-- 顶部工具行：返回 + 设备摘要（聚合状态/组健康分/最早越界倒计时）+ 刷新趋势 -->
      <div class="view-toolbar sensor-toolbar">
        <div class="sensor-toolbar-left">
          <el-button size="mini" icon="el-icon-back" @click="exitSensorView">返回设备列表</el-button>
          <span v-if="currentGroup" class="equipment-name">{{ currentGroup.equipmentName }}</span>
          <el-tag v-if="currentGroup" size="mini" :type="statusMeta(currentGroup.aggregateStatus).type">{{ statusMeta(currentGroup.aggregateStatus).label }}</el-tag>
        </div>
        <div v-if="currentGroup" class="sensor-toolbar-right">
          <span class="group-health">
            <span class="group-health-label">健康分</span>
            <span class="group-health-score" :style="{ color: healthColor(currentGroup.minHealthScore) }">{{ healthText(currentGroup.minHealthScore) }}</span>
          </span>
          <span v-if="currentGroup.earliestBreachTimeMs != null" class="group-breach">
            最早越界 {{ formatMs(currentGroup.earliestBreachTimeMs) }}（剩余 {{ countdown(currentGroup.earliestBreachTimeMs) }}）
          </span>
          <span v-else class="group-breach">最早越界 -</span>
          <el-button size="mini" icon="el-icon-refresh" circle @click="loadDetailsForEquipment(currentEquipmentId)" />
        </div>
      </div>
      <div v-for="s in (currentGroup ? currentGroup.sensors : [])" :key="s.sensorCode" class="trend-card">
        <!-- 点击整卡打开放大对话框：完整大图 + AI 诊断入口 -->
        <div class="trend-card-head">
          <span class="trend-card-name">{{ s.sensorName || s.sensorCode }}</span>
          <!-- 健康分徽标：三态底色（≥80 绿 / 60-80 橙 / <60 红），任务未跑过无分显示 '-' -->
          <span class="health-badge" :style="healthBadgeStyle(s.healthScore)">{{ s.healthScore != null ? healthText(s.healthScore) : '-' }}</span>
          <el-tag size="mini" :type="statusMeta(s.status).type">{{ statusMeta(s.status).label }}</el-tag>
        </div>
        <div v-loading="detailInFlight[s.sensorCode]" class="trend-mini-wrap" element-loading-text="趋势加载中…">
          <div :ref="'trend-' + s.sensorCode" class="trend-mini" />
          <!-- 单传感器请求失败仅该卡空态，不影响其他卡片 -->
          <div v-if="!detailsMap[s.sensorCode] && !detailInFlight[s.sensorCode]" class="trend-empty">暂无趋势数据</div>
        </div>
        <div class="trend-card-foot">
          <div class="trend-card-item">
            <span class="label">异常评分</span>
            <span class="value">{{ s.anomalyScore != null ? s.anomalyScore : '-' }}</span>
          </div>
          <div class="trend-card-item">
            <span class="label">预计越界</span>
            <!-- 后端 NORMAL 态已显式清空 predicted_breach_time，无值即无风险 -->
            <span class="value" :class="{ danger: hasForecast(s) }">{{ hasForecast(s) ? formatMs(s.predictedBreachTimeMs) : '-' }}</span>
          </div>
          <div class="trend-card-item">
            <span class="label">剩余时间</span>
            <span class="value" :class="{ danger: hasForecast(s) }">{{ hasForecast(s) ? countdown(s.predictedBreachTimeMs) : '-' }}</span>
          </div>
        </div>
      </div>
      <div v-if="currentGroup && currentGroup.sensors.length === 0" class="overview-empty">该设备暂无传感器</div>
    </div>

    <!-- 趋势放大对话框：完整大图（tooltip/legend/dataZoom）+ AI 诊断入口 -->
    <el-dialog :visible.sync="bigChartVisible" width="80%" append-to-body @closed="disposeBigChart">
      <template slot="title">{{ bigChartTitle }}</template>
      <div class="big-chart-toolbar">
        <el-tag v-if="bigChartAi && bigChartAi.isAnomaly != null" size="small" :type="bigChartAi.isAnomaly ? 'danger' : 'success'">{{ bigChartAi.isAnomaly ? '模型判定异常' : '模型判定正常' }}</el-tag>
        <el-tag v-if="bigChartAi && bigChartAi.rulPoint != null" type="danger" size="small">RUL 点估计 {{ bigChartAi.rulPoint }} 分钟</el-tag>
        <el-button size="mini" type="primary" plain icon="el-icon-magic-stick" :loading="diagLoading" :disabled="!bigChartSensor" @click="diagnoseFromBigChart">AI 诊断</el-button>
      </div>
      <div ref="bigChart" class="big-chart" v-loading="bigChartLoading" element-loading-text="趋势数据加载中…" />
      <div class="chart-legend-tips">
        <span><i class="dot raw" />原始数据：传感器上报原始值</span>
        <span><i class="dot q50" />q50 中位预测：模型对未来各时刻的最可能取值（每点间隔 1 分钟）</span>
        <span><i class="dot band" />q10-q90 分位带：该时刻实际值落在此区间的概率约 80%</span>
        <span><i class="dot rul" />RUL 标注：竖线为剩余寿命点估计时刻，浅红区间为最早-最晚失效范围（单位：分钟）</span>
      </div>
    </el-dialog>

    <!-- AI 诊断抽屉：报告（markdown 源文展示）+ 历史诊断列表 -->
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
import { listPredictSensors, fetchPredictDetail, fetchPredictOverview, diagnoseSensor, listDiagnosisRecords } from '@/api/business/predict'
import { collectSensorBaseline } from '@/api/business/machine'

// 状态严重度映射：排序用（BREACHED=2 / DEGRADING=1 / NORMAL=0）
const SEVERITY = { BREACHED: 2, DEGRADING: 1, NORMAL: 0 }
// 模型预测步长（分钟）：q10/q50/q90 每点间隔，与后端 predict.model.horizon 的分钟口径一致
const FORECAST_STEP_MS = 60 * 1000

export default {
  name: 'MachinePredict',
  data() {
    return {
      // 分层级视图：device=设备大卡网格（默认），sensor=某设备的传感器趋势列表
      currentView: 'device',
      currentEquipmentId: null,
      sensors: [],
      refreshTimer: null,
      // 剩余时间倒计时文本的缓存（每 30s 轮询刷新数据，倒计时文本每秒本地推进）
      tickTimer: null,
      tickFlag: 0,
      // 设备健康总览数据（/predict/overview，gauge 均值健康分与最紧迫 RUL 来源）
      overviewList: [],
      // 设备健康仪表盘实例：key=equipmentId，避免 v-for 重渲染时重复 init
      gauges: {},
      // 当前设备各传感器趋势详情：key=sensorCode，渐进式填充（先返回先渲染）
      detailsMap: {},
      // 每传感器趋势请求 in-flight 标记：轮询周期内未返回的传感器跳过本轮，防重复请求堆积
      detailInFlight: {},
      // 趋势图 echarts 实例：key=sensorCode（参照 gauges 模式 init/dispose/resize）
      trendCharts: {},
      // 趋势放大对话框状态
      bigChartVisible: false,
      bigChartSensor: null,
      bigChartInstance: null,
      // 设备基线采集按钮 loading：key=equipmentId（按设备独立）
      baselineLoading: {},
      // AI 诊断抽屉状态
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
    // 传感器按设备分组聚合：设备层大卡与传感器层列表共用（组间严重度降序，组内状态降序）
    equipmentGroups() {
      const groups = []
      const idxOf = {}
      this.sensors.forEach(s => {
        // equipmentId 统一转字符串作分组键，避免数字/字符串类型差异导致同设备拆组
        const key = String(s.equipmentId)
        if (idxOf[key] === undefined) {
          idxOf[key] = groups.length
          groups.push({ equipmentId: s.equipmentId, equipmentName: '', sensors: [], aggregateStatus: 'NORMAL', minHealthScore: 100, earliestBreachTimeMs: null, counts: { normal: 0, degrading: 0, breached: 0 } })
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
        // 三态计数（status 缺省按 NORMAL）：设备层大卡展示
        g.sensors.forEach(s => {
          const st = s.status || 'NORMAL'
          if (st === 'BREACHED') g.counts.breached++
          else if (st === 'DEGRADING') g.counts.degrading++
          else g.counts.normal++
        })
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
    // 传感器层当前设备组：按 currentEquipmentId 匹配
    currentGroup() {
      if (this.currentEquipmentId == null) return null
      return this.equipmentGroups.find(g => String(g.equipmentId) === String(this.currentEquipmentId)) || null
    },
    // 放大对话框标题：设备 / 传感器 维度
    bigChartTitle() {
      if (!this.bigChartSensor) return '趋势预测'
      const s = this.bigChartSensor
      return ((s.equipmentName || '设备#' + s.equipmentId) + ' / ' + (s.sensorName || s.sensorCode)) + ' — 趋势预测'
    },
    // 放大对话框本轮推理产物（取 detailsMap 中该传感器已加载详情）
    bigChartAi() {
      const code = this.bigChartSensor && this.bigChartSensor.sensorCode
      const d = code ? this.detailsMap[code] : null
      return (d && d.ai) || null
    },
    // 放大对话框 loading：选中了传感器但其趋势详情尚未返回
    bigChartLoading() {
      const code = this.bigChartSensor && this.bigChartSensor.sensorCode
      return !!(code && !this.detailsMap[code])
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
    // 30s 轮询：与后端预测任务调度周期(predict.interval-ms)一致；传感器层追加当前设备趋势刷新
    this.refreshTimer = setInterval(() => {
      this.loadSensors()
      this.loadOverview()
      if (this.currentView === 'sensor' && this.currentEquipmentId != null) {
        this.loadDetailsForEquipment(this.currentEquipmentId)
      }
    }, 30000)
    // 倒计时文本每秒推进（只驱动文本，不拉数据）
    this.tickTimer = setInterval(() => { this.tickFlag++ }, 1000)
    window.addEventListener('resize', this.resizeCharts)
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer)
    clearInterval(this.tickTimer)
    window.removeEventListener('resize', this.resizeCharts)
    // 释放全部趋势图实例，防止内存泄漏
    Object.keys(this.trendCharts).forEach(k => {
      this.trendCharts[k].dispose()
      delete this.trendCharts[k]
    })
    // 释放所有设备健康仪表盘实例，防止内存泄漏
    Object.keys(this.gauges).forEach(k => {
      this.gauges[k].dispose()
      delete this.gauges[k]
    })
    this.disposeBigChart()
  },
  methods: {
    init() {
      // 设备层首屏仅拉传感器列表与设备总览，不拉任何趋势数据（进入设备后才加载）
      this.loadSensors()
      this.loadOverview()
    },
    // 设备层刷新（工具行刷新按钮）
    refreshDeviceView() {
      this.loadSensors()
      this.loadOverview()
    },
    // ===== 设备健康基线采集 =====
    // 该设备每个传感器取当前时刻前最近 1024 个数据点存 sensor_baseline（后端逐传感器处理）
    collectBaseline(group) {
      const key = group.equipmentId
      this.$set(this.baselineLoading, key, true)
      collectSensorBaseline(key).then(res => {
        const collected = (res && res.collected) || 0
        const empty = (res && res.empty) || 0
        // 无数据传感器如实提示，便于判断采集窗口是否覆盖完整
        this.$modal.msgSuccess(empty > 0 ? `已采集 ${collected} 个传感器基线（${empty} 个无数据未采集）` : `已采集 ${collected} 个传感器基线`)
      }).finally(() => {
        this.$set(this.baselineLoading, key, false)
      })
    },
    // 加载传感器状态列表
    loadSensors() {
      return listPredictSensors().then(list => {
        this.sensors = (list || []).map(s => ({ ...s, status: s.status || 'NORMAL' }))
      }).catch(() => {
        this.sensors = []
      })
    },
    // 设备健康总览：失败静默保留旧值
    loadOverview() {
      return fetchPredictOverview().then(list => {
        this.overviewList = list || []
        // 等 v-for 渲染出 gauge 容器后再初始化/更新仪表盘（传感器层无 $refs 时遍历自然跳过）
        this.$nextTick(() => this.renderGauges())
      }).catch(() => {})
    },
    // ===== 视图切换（分层级） =====
    // 进入某设备的传感器趋势视图：释放 gauge 实例（v-if 销毁设备层 DOM）并加载该设备趋势
    enterSensorView(group) {
      this.currentView = 'sensor'
      this.currentEquipmentId = group.equipmentId
      Object.keys(this.gauges).forEach(k => {
        this.gauges[k].dispose()
        delete this.gauges[k]
      })
      this.$nextTick(() => this.loadDetailsForEquipment(group.equipmentId))
    },
    // 返回设备层：释放趋势实例并清缓存（重进重新加载，无陈旧数据），重建仪表盘
    exitSensorView() {
      this.currentView = 'device'
      this.currentEquipmentId = null
      Object.keys(this.trendCharts).forEach(k => {
        this.trendCharts[k].dispose()
        delete this.trendCharts[k]
      })
      this.detailsMap = {}
      this.detailInFlight = {}
      this.$nextTick(() => this.renderGauges())
    },
    // 加载指定设备的传感器趋势：逐传感器并行请求现有 detail 接口，先返回先渲染（互不阻塞）
    loadDetailsForEquipment(eqId) {
      const group = this.equipmentGroups.find(g => String(g.equipmentId) === String(eqId))
      if (!group) return
      // 清理已不属于当前设备的趋势图实例（参照 gauges 的 alive 清理模式）
      const alive = {}
      group.sensors.forEach(s => {
        if (s.sensorCode && this.trendCharts[s.sensorCode]) alive[s.sensorCode] = this.trendCharts[s.sensorCode]
      })
      Object.keys(this.trendCharts).forEach(k => {
        if (!alive[k]) {
          this.trendCharts[k].dispose()
          delete this.trendCharts[k]
        }
      })
      this.trendCharts = alive
      group.sensors.forEach(s => {
        const code = s.sensorCode
        if (!code || this.detailInFlight[code]) return
        this.$set(this.detailInFlight, code, true)
        fetchPredictDetail(code).then(detail => {
          this.$set(this.detailsMap, code, detail)
          // 数据到达即渲染该卡图表，不等其他传感器
          this.$nextTick(() => this.renderTrendChart(code, detail))
        }).catch(() => {
          // 单传感器失败：清除旧数据让该卡显示空态，不影响其他卡片
          this.$delete(this.detailsMap, code)
        }).finally(() => {
          this.$set(this.detailInFlight, code, false)
        })
      })
    },
    // 渲染单个传感器趋势图：实例复用（已存在 setOption，不存在 init）
    renderTrendChart(code, detail) {
      const el = (this.$refs['trend-' + code] || [])[0]
      if (!el) return
      let chart = this.trendCharts[code]
      if (!chart) {
        chart = echarts.init(el)
        this.trendCharts[code] = chart
      }
      chart.setOption(this.trendOption(detail, true), true)
    },
    // 趋势图配置：mini=true 传感器层单行卡（含时间轴，无 tooltip/legend/dataZoom，RUL 竖线不带文字），false=放大对话框大图
    trendOption(d, mini) {
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
          lineStyle: { color: '#F56C6C', type: 'solid', width: mini ? 1 : 1.5 },
          // 单行卡空间有限，RUL 文字仅大图显示
          label: mini ? { show: false } : { formatter: `RUL ${ai.rulPoint}分钟`, position: 'start' }
        })
      }
      if (ai.rulEarliest != null && ai.rulLatest != null) {
        markAreas.push([
          {
            xAxis: now + ai.rulEarliest * 60000, name: '失效区间',
            itemStyle: { color: 'rgba(245,108,108,0.06)' },
            label: mini ? { show: false } : { formatter: `失效区间 ${ai.rulEarliest}-${ai.rulLatest}分钟`, position: 'inside' }
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
      // 单行全宽卡空间充足，小图也显示时间轴标签（紧凑格式）
      const option = {
        grid: mini ? { left: 56, right: 16, top: 12, bottom: 26 } : { left: 50, right: 30, top: 34, bottom: 46 },
        xAxis: {
          type: 'time',
          axisLabel: { formatter: v => this.parseTime(new Date(v), '{m}-{d} {h}:{i}') || '' }
        },
        yAxis: { type: 'value', scale: true },
        series
      }
      if (!mini) {
        // 大图独有：悬浮提示/图例/缩放
        option.tooltip = {
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
        }
        option.legend = { top: 0, data: ['原始数据', 'q10 下界', 'q90 上界', 'q50 中位预测'] }
        option.dataZoom = [{ type: 'inside' }, { type: 'slider', height: 16, bottom: 8 }]
      }
      return option
    },
    // ===== 趋势放大对话框 =====
    openBigChart(s) {
      this.bigChartSensor = s
      this.bigChartVisible = true
      this.$nextTick(() => this.renderBigChart())
    },
    renderBigChart() {
      if (!this.$refs.bigChart) return
      if (!this.bigChartInstance) {
        this.bigChartInstance = echarts.init(this.$refs.bigChart)
      }
      const code = this.bigChartSensor && this.bigChartSensor.sensorCode
      const detail = code ? this.detailsMap[code] : null
      if (!detail) {
        this.bigChartInstance.clear()
        return
      }
      this.bigChartInstance.setOption(this.trendOption(detail, false), true)
    },
    disposeBigChart() {
      if (this.bigChartInstance) {
        this.bigChartInstance.dispose()
        this.bigChartInstance = null
      }
    },
    // 对话框内 AI 诊断入口：上下文取当前放大传感器
    diagnoseFromBigChart() {
      const s = this.bigChartSensor
      if (!s) return
      this.openDiagnose({ equipmentId: s.equipmentId, sensorCode: s.sensorCode, sensorName: s.sensorName, equipmentName: s.equipmentName })
    },
    // ===== 设备层仪表盘 =====
    // 设备卡匹配的 overview 数据（gauge 均值健康分与最紧迫 RUL 来源）
    overviewOf(group) {
      return this.overviewList.find(o => String(o.equipmentId) === String(group.equipmentId)) || null
    },
    // 渲染设备健康仪表盘：按设备分组遍历（$refs 仅设备层存在），复用已建实例并清理已消失设备
    renderGauges() {
      const alive = {}
      this.equipmentGroups.forEach(g => {
        const key = String(g.equipmentId)
        const el = (this.$refs['gauge-' + key] || [])[0]
        if (!el) return
        alive[key] = this.gauges[key] || echarts.init(el)
        alive[key].setOption(this.gaugeOption(this.overviewOf(g), g), true)
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
    gaugeOption(eq, group) {
      // 健康分优先 overview 均值，无 overview 数据时兜底组内最低分
      const score = eq != null && eq.healthScore != null ? eq.healthScore : (group ? group.minHealthScore : null)
      const hasScore = score != null
      const color = hasScore ? this.healthColor(score) : '#909399'
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
          data: [{ value: hasScore ? Number(score) : 0, name: '健康分' }]
        }]
      }
    },
    resizeCharts() {
      Object.keys(this.trendCharts).forEach(k => this.trendCharts[k].resize())
      Object.keys(this.gauges).forEach(k => this.gauges[k].resize())
      this.bigChartInstance && this.bigChartInstance.resize()
    },
    // ===== AI 诊断 =====
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
/* 视图工具行（无卡片外框）：标题/返回 + 摘要 + 刷新 */
.view-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  .view-toolbar-title { font-size: 14px; font-weight: 600; color: #303133; }
}
.overview-empty { padding: 24px 0; text-align: center; font-size: 13px; color: #909399; }
/* 设备层：设备大卡（可点击进入） */
.equip-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  &:hover { border-color: #409EFF; box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08); }
  .equip-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    .equip-name { font-size: 14px; font-weight: 600; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .equip-head-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
    .equip-sensor-count { font-size: 12px; color: #909399; white-space: nowrap; }
  }
  .equip-gauge { height: 170px; width: 100%; }
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
  /* 三态计数行 */
  .equip-counts {
    display: flex;
    gap: 14px;
    padding-top: 6px;
    font-size: 12px;
    .count-normal { color: #67C23A; }
    .count-degrading { color: #E6A23C; }
    .count-breached { color: #F56C6C; }
  }
}
/* 传感器层：工具行与一行一个全宽趋势卡 */
.sensor-toolbar {
  .sensor-toolbar-left { display: flex; align-items: center; gap: 12px; .equipment-name { font-size: 15px; font-weight: 600; color: #303133; } }
  .sensor-toolbar-right { display: flex; align-items: center; gap: 20px; }
  .group-health { display: flex; align-items: baseline; gap: 6px; }
  .group-health-label { font-size: 12px; color: #909399; }
  .group-health-score { font-size: 22px; font-weight: 700; line-height: 1; }
  .group-breach { font-size: 12px; color: #909399; }
}
.trend-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  &:hover { border-color: #c6e2ff; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
  .trend-card-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
    .trend-card-name {
      flex: 1;
      font-size: 13px;
      font-weight: 600;
      color: #303133;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
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
  .trend-mini-wrap { position: relative; }
  .trend-mini { height: 260px; width: 100%; }
  .trend-empty {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: #909399;
  }
  .trend-card-foot {
    display: flex;
    gap: 18px;
    margin-top: 6px;
  }
  .trend-card-item {
    display: flex;
    flex-direction: column;
    .label { font-size: 12px; color: #909399; }
    .value { font-size: 13px; color: #303133; font-weight: 500; &.danger { color: #F56C6C; } }
  }
}
/* 放大对话框 */
.big-chart-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.big-chart { height: 420px; width: 100%; }
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
/* AI 诊断抽屉（append-to-body 下仅样式化自有元素，抽屉体自带滚动） */
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
