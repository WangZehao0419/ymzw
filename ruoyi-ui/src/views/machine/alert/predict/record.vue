<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true">
      <el-form-item label="设备名称" prop="equipmentName">
        <el-input v-model="queryParams.equipmentName" placeholder="请输入设备名称" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="告警状态" prop="alertStatus">
        <el-select v-model="queryParams.alertStatus" placeholder="请选择告警状态" clearable>
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 列表以设备为主语展示：传感器信息移入「证据」（触发前全设备传感器数据） -->
    <el-table v-loading="loading" :data="recordList" size="small">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="所属设备" prop="equipmentName" width="150" align="center" show-overflow-tooltip>
        <template slot-scope="scope">{{ scope.row.equipmentName || '设备#' + scope.row.equipmentId }}</template>
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
      <el-table-column label="证据" width="100" align="center" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="openEvidence(scope.row)">查看证据</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <!-- 预警证据对话框：该设备全部传感器在触发时刻之前的数据曲线（设备维度决策支撑） -->
    <el-dialog :visible.sync="evidenceVisible" width="80%" append-to-body @closed="disposeEvidenceCharts">
      <template slot="title">预警证据 — {{ evidenceTitle }}</template>
      <div v-loading="evidenceLoading" class="evidence-body" element-loading-text="证据数据加载中…">
        <el-row v-if="evidenceList.length" :gutter="12">
          <el-col v-for="ev in evidenceList" :key="ev.sensorCode" :xs="24" :sm="12">
            <div class="evidence-card" :class="{ firing: ev.firing }">
              <div class="evidence-card-head">
                <span class="evidence-card-name">{{ ev.sensorName || ev.sensorCode }}</span>
                <span v-if="ev.unit" class="evidence-card-unit">（{{ ev.unit }}）</span>
                <el-tag v-if="ev.firing" size="mini" type="danger" effect="dark">触发传感器</el-tag>
              </div>
              <div :ref="'evidence-' + ev.sensorCode" class="evidence-chart" />
              <div v-if="!ev.points || ev.points.length === 0" class="evidence-empty">暂无数据</div>
            </div>
          </el-col>
        </el-row>
        <div v-else-if="!evidenceLoading" class="evidence-none">暂无证据数据</div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { fetchPredictAlerts, fetchAlertEvidence } from '@/api/business/predict'

export default {
  name: 'MachinePredictRecord',
  data() {
    return {
      loading: false,
      recordList: [],
      total: 0,
      queryParams: { pageNum: 1, pageSize: 10, equipmentName: '', alertStatus: '' },
      statusOptions: [
        { label: '告警中', value: 'FIRING' },
        { label: '已确认', value: 'ACKED' },
        { label: '已恢复', value: 'RESOLVED' }
      ],
      // 剩余时间倒计时文本的缓存标记（倒计时文本每秒本地推进，不拉数据）
      tickFlag: 0,
      tickTimer: null,
      // 证据对话框状态
      evidenceVisible: false,
      evidenceLoading: false,
      evidenceList: [],
      evidenceTitle: '',
      // 证据曲线 echarts 实例：key=sensorCode（init/setOption/dispose 管理）
      evidenceCharts: {}
    }
  },
  created() {
    this.getList()
  },
  mounted() {
    // 每秒推进 tickFlag 触发 countdown 重算（纯本地文本刷新，无请求）
    this.tickTimer = setInterval(() => { this.tickFlag++ }, 1000)
  },
  beforeDestroy() {
    clearInterval(this.tickTimer)
    this.disposeEvidenceCharts()
  },
  methods: {
    getList() {
      this.loading = true
      fetchPredictAlerts(this.queryParams).then(res => {
        this.recordList = res.rows
        this.total = res.total
      }).finally(() => {
        this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.handleQuery()
    },
    // ===== 证据对话框 =====
    openEvidence(row) {
      this.evidenceTitle = row.equipmentName || ('设备#' + row.equipmentId)
      this.evidenceVisible = true
      this.evidenceLoading = true
      this.evidenceList = []
      fetchAlertEvidence(row.id).then(list => {
        this.evidenceList = list || []
        // 等 v-for 渲染出曲线容器后再初始化图表
        this.$nextTick(() => this.renderEvidenceCharts())
      }).finally(() => {
        this.evidenceLoading = false
      })
    },
    renderEvidenceCharts() {
      this.evidenceList.forEach(ev => {
        const code = ev.sensorCode
        const el = (this.$refs['evidence-' + code] || [])[0]
        if (!el) return
        let chart = this.evidenceCharts[code]
        if (!chart) {
          chart = echarts.init(el)
          this.evidenceCharts[code] = chart
        }
        const points = ev.points || []
        chart.setOption({
          grid: { left: 52, right: 14, top: 12, bottom: 24 },
          xAxis: {
            type: 'time',
            axisLabel: { formatter: v => this.parseTime(new Date(v), '{m}-{d} {h}:{i}') || '' }
          },
          yAxis: { type: 'value', scale: true },
          series: [{
            type: 'line',
            showSymbol: false,
            // 触发传感器曲线加重着色突出，其余传感器为参照（灰）
            data: points.map(p => [p.ts, p.val]),
            lineStyle: { width: ev.firing ? 2 : 1, color: ev.firing ? '#F56C6C' : '#909399', opacity: ev.firing ? 1 : 0.7 },
            itemStyle: { color: ev.firing ? '#F56C6C' : '#909399' }
          }]
        }, true)
      })
    },
    disposeEvidenceCharts() {
      Object.keys(this.evidenceCharts).forEach(k => {
        this.evidenceCharts[k].dispose()
        delete this.evidenceCharts[k]
      })
      this.evidenceList = []
    },
    // ===== 展示工具 =====
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
/* 证据对话框 */
.evidence-body { min-height: 120px; }
.evidence-card {
  position: relative;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 12px;
  /* 触发该预警的传感器高亮 */
  &.firing { border-color: #F56C6C; box-shadow: 0 0 0 1px #F56C6C inset; }
  .evidence-card-head {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 6px;
    .evidence-card-name { font-size: 13px; font-weight: 600; color: #303133; }
    .evidence-card-unit { font-size: 12px; color: #909399; }
  }
  .evidence-chart { height: 240px; width: 100%; }
  .evidence-empty {
    position: absolute;
    top: 34px;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: #909399;
  }
}
.evidence-none { padding: 32px 0; text-align: center; font-size: 13px; color: #909399; }
</style>
