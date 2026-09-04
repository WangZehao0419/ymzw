<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="82px">
      <el-form-item label="传感器编号" prop="sensorCode">
        <el-input v-model="queryParams.sensorCode" placeholder="请输入传感器编号" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="告警级别" prop="alertLevel">
        <el-select v-model="queryParams.alertLevel" placeholder="请选择告警级别" clearable>
          <el-option v-for="item in levelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
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

    <el-row :gutter="10" class="mb8">
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="alertList" row-key="id" :row-class-name="rowClassName">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="所属设备" prop="equipmentName" width="150" align="center" show-overflow-tooltip>
        <template slot-scope="scope">{{ scope.row.equipmentName || '设备#' + scope.row.equipmentId }}</template>
      </el-table-column>
      <el-table-column label="传感器编号" prop="sensorCode" min-width="130" />
      <el-table-column label="传感器名称" prop="sensorName" min-width="150" show-overflow-tooltip />
      <el-table-column label="当前数值" prop="sensorValue" width="110" align="center">
        <template slot-scope="scope"><span class="sensor-value">{{ scope.row.sensorValue }}</span></template>
      </el-table-column>
      <el-table-column label="告警级别" prop="alertLevel" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="levelMeta(scope.row.alertLevel).type" :effect="levelMeta(scope.row.alertLevel).effect">{{ levelMeta(scope.row.alertLevel).label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="告警状态" prop="alertStatus" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="statusMeta(scope.row.alertStatus).type">{{ statusMeta(scope.row.alertStatus).label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="触发时间" prop="triggerTime" width="165" align="center">
        <template slot-scope="scope">{{ formatTime(scope.row.triggerTime) }}</template>
      </el-table-column>
      <el-table-column label="证据数据" prop="evidence" min-width="220" show-overflow-tooltip>
        <template slot-scope="scope">{{ scope.row.evidence || '-' }}</template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script>
import { fetchAlertEvents } from '@/api/business/alert'
import { subscribe } from '@/utils/ndjsonStream'

// 告警流地址：与顶栏铃铛共用同一 URL，ndjsonStream 内部复用单连接
const ALERT_STREAM_URL = process.env.VUE_APP_BASE_API + '/api/alert-events/stream'

export default {
  name: 'BusinessWarning',
  data() {
    return {
      loading: false,
      showSearch: true,
      total: 0,
      alertList: [],
      // 查询下拉按等级从高到低排列,与运营排查时的关注优先级一致
      levelOptions: [
        { label: '危急', value: 'CRITICAL' },
        { label: '严重', value: 'SEVERE' },
        { label: '重要', value: 'IMPORTANT' },
        { label: '预警', value: 'WARNING' },
        { label: '正常', value: 'NORMAL' }
      ],
      statusOptions: [
        { label: '告警中', value: 'FIRING' },
        { label: '已确认', value: 'ACKED' },
        { label: '已恢复', value: 'RESOLVED' }
      ],
      // 告警记录=已发生的阈值告警，固定只查 RULE；PREDICT 预测告警在预测性维护页展示
      queryParams: { pageNum: 1, pageSize: 10, sensorCode: '', alertLevel: '', alertStatus: '', alertType: 'RULE' },
      newAlertIds: new Set(), // 最近流式到达的告警 id，用于新行高亮
      receivedIds: new Set(), // 已插入过的告警 id，防止流重连重放导致重复插入
      unsubscribe: null
    }
  },
  created() {
    this.getList()
  },
  mounted() {
    // 订阅实时告警流：新告警实时插入列表顶部
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
    getList() {
      this.loading = true
      fetchAlertEvents(this.queryParams).then(res => {
        this.alertList = res.rows
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
    // 流式告警：新告警顶部插入并计数；同 id 再推为升级推送（后端只升级不重建），就地替换该行
    handleStreamAlert(alert) {
      if (!alert || alert.id === undefined || alert.id === null) return
      // 告警记录页只收 RULE 阈值告警：流推送的 PREDICT 预测告警在预测性维护页展示，
      // 此处过滤防其绕过分页查询的 alertType 条件直接插入列表
      if (alert.alertType && alert.alertType !== 'RULE') return
      // id 已收过：必为升级推送 → 就地替换，等级标签/数值/证据列随新对象刷新
      if (this.receivedIds.has(alert.id)) {
        const idx = this.alertList.findIndex(row => row.id === alert.id)
        if (idx > -1) {
          this.alertList.splice(idx, 1, { ...alert })
          // 升级需重新引起关注：复用新增告警的 5 秒高亮生命周期（Set 重复 add 幂等）
          this.newAlertIds.add(alert.id)
          setTimeout(() => { this.newAlertIds.delete(alert.id) }, 5000)
        }
        // 找不到说明该告警不在当前页（已翻页/被筛选），升级推送直接忽略，不影响其他行
        return
      }
      this.receivedIds.add(alert.id)
      this.newAlertIds.add(alert.id)
      setTimeout(() => { this.newAlertIds.delete(alert.id) }, 5000)
      this.alertList.unshift({ ...alert })
      this.total += 1
    },
    // 新到达的告警行追加高亮 class
    rowClassName({ row }) {
      return this.newAlertIds.has(row.id) ? 'alert-new-row' : ''
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
    statusMeta(status) {
      return { FIRING: { label: '告警中', type: 'danger' }, ACKED: { label: '已确认', type: 'warning' }, RESOLVED: { label: '已恢复', type: 'success' } }[status] || { label: status || '-', type: 'info' }
    },
    // 时间格式化：兼容 ISO(带T)/普通字符串/数组(LocalDateTime Jackson 序列化)三种格式
    formatTime(time) {
      if (!time) return '-'
      let normalized = time
      if (typeof time === 'string') {
        normalized = time.replace('T', ' ').replace(/-/g, '/')
      } else if (Array.isArray(time)) {
        // Jackson LocalDateTime 数组 [年,月,日,时,分,秒,纳秒]:直接 new Date(数组) 是 Invalid Date,
        // 各字段 NaN 会被 parseTime 的 value||0 转成 0,显示成 0-0-0 0:0:0,须按字段构造
        normalized = new Date(time[0] || 1970, (time[1] || 1) - 1, time[2] || 1, time[3] || 0, time[4] || 0, time[5] || 0)
      }
      return this.parseTime(new Date(normalized), '{y}-{m}-{d} {h}:{i}:{s}') || '-'
    }
  }
}
</script>

<style lang="scss" scoped>
.sensor-value {
  color: #f56c6c;
  font-weight: 600;
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
