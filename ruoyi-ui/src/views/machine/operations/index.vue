<template>
  <div class="app-container">
    <el-alert title="此模块沿用 ht_admin 的本地演示数据，尚无对应后端接口。" type="info" :closable="false" show-icon class="mb8" />
    <el-form :inline="true" size="small"><el-form-item label="关键字"><el-input v-model="keyword" clearable placeholder="输入编号、设备或内容" /></el-form-item><el-form-item><el-button type="primary" icon="el-icon-search">搜索</el-button><el-button icon="el-icon-refresh" @click="keyword=''">重置</el-button></el-form-item></el-form>
    <el-table :data="filteredRows" border>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" :width="col.width" :min-width="col.minWidth" show-overflow-tooltip>
        <template slot-scope="scope"><el-tag v-if="col.tag" :type="tagType(scope.row[col.prop])">{{ scope.row[col.prop] }}</el-tag><span v-else>{{ scope.row[col.prop] || '-' }}</span></template>
      </el-table-column>
    </el-table>
  </div>
</template>
<script>
import { workOrders, maintenancePlans, inspectionLogs, deviceLogs } from '@/mock/business/operations'
const configs = {
  order: { rows: workOrders, columns: [{ prop: 'orderNo', label: '工单编号', minWidth: 150 }, { prop: 'machineName', label: '机床名称', minWidth: 160 }, { prop: 'orderType', label: '工单类型', width: 110 }, { prop: 'content', label: '工单内容', minWidth: 240 }, { prop: 'assignee', label: '负责人', width: 90 }, { prop: 'status', label: '状态', width: 90, tag: true }, { prop: 'createTime', label: '创建时间', width: 165 }] },
  plan: { rows: maintenancePlans, columns: [{ prop: 'planNo', label: '计划编号', minWidth: 150 }, { prop: 'machineName', label: '机床名称', minWidth: 180 }, { prop: 'maintenanceType', label: '维护类型', width: 120 }, { prop: 'planDate', label: '计划日期', width: 120 }, { prop: 'owner', label: '负责人', width: 90 }, { prop: 'execStatus', label: '执行状态', width: 100, tag: true }] },
  inspectionLog: { rows: inspectionLogs, columns: [{ prop: 'logTime', label: '日志时间', width: 165 }, { prop: 'partCode', label: '零件编号', width: 150 }, { prop: 'partName', label: '零件名称', width: 120 }, { prop: 'action', label: '操作', width: 130 }, { prop: 'operator', label: '操作人', width: 90 }, { prop: 'status', label: '状态', width: 90, tag: true }, { prop: 'detail', label: '详情', minWidth: 260 }] },
  deviceLog: { rows: deviceLogs, columns: [{ prop: 'logTime', label: '日志时间', width: 165 }, { prop: 'deviceCode', label: '设备编号', width: 130 }, { prop: 'deviceName', label: '设备名称', minWidth: 170 }, { prop: 'logType', label: '日志类型', width: 110, tag: true }, { prop: 'logContent', label: '日志内容', minWidth: 260 }, { prop: 'operator', label: '操作人', width: 90 }] }
}
export default {
  name: 'BusinessOperations',
  data() { return { keyword: '' } },
  computed: { type() { return this.$route.meta.businessType || 'order' }, config() { return configs[this.type] }, columns() { return this.config.columns }, filteredRows() { const k = this.keyword.trim(); return k ? this.config.rows.filter(row => JSON.stringify(row).includes(k)) : this.config.rows } },
  methods: { tagType(value) { if (['异常', '告警触发'].includes(value)) return 'danger'; if (['重要', '处理中', '待处理', '待执行'].includes(value)) return 'warning'; if (['已处理', '已完成', '成功'].includes(value)) return 'success'; return 'info' } }
}
</script>
