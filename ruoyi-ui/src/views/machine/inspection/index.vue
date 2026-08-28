<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" @tab-click="handleTabChange">
      <el-tab-pane label="待检测" name="pending" />
      <el-tab-pane label="检测记录" name="finished" />
      <el-tab-pane label="异常处理" name="exception" />
    </el-tabs>
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" label-width="70px">
      <el-form-item label="关键字" prop="keyword"><el-input v-model="queryParams.keyword" placeholder="零件名称或编号" clearable @keyup.enter.native="handleQuery" /></el-form-item>
      <el-form-item><el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button><el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="partList">
      <el-table-column type="expand"><template slot-scope="scope"><pre class="part-data">{{ formatData(scope.row.partData || scope.row.inspectionDetails) }}</pre></template></el-table-column>
      <el-table-column label="零件编号" prop="partCode" min-width="120" />
      <el-table-column label="零件名称" prop="partName" min-width="150" />
      <el-table-column label="零件类型" prop="partType" min-width="120" />
      <el-table-column label="创建时间" prop="createTime" min-width="160" />
      <el-table-column v-if="activeTab !== 'pending'" label="检测结果" width="110" align="center"><template slot-scope="scope"><el-tag :type="resultMeta(scope.row).type">{{ resultMeta(scope.row).label }}</el-tag></template></el-table-column>
      <el-table-column v-if="activeTab === 'pending'" label="操作" width="120" align="center"><template slot-scope="scope"><el-button type="text" icon="el-icon-cpu" :loading="inspectingId === scope.row.id" @click="handleInspect(scope.row)">AI 检测</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
  </div>
</template>
<script>
import { listParts, inspectPart } from '@/api/business/machine'
export default {
  name: 'BusinessInspection',
  data() { return { loading: false, activeTab: 'pending', total: 0, partList: [], inspectingId: null, queryParams: { pageNum: 1, pageSize: 10, keyword: '', inspectionFlag: false, isQualified: '' } } },
  created() { this.getList() },
  methods: {
    getList() { this.loading = true; listParts(this.queryParams).then(res => { this.partList = res.rows; this.total = res.total }).finally(() => { this.loading = false }) },
    handleTabChange() { this.queryParams.pageNum = 1; this.queryParams.inspectionFlag = this.activeTab !== 'pending'; this.queryParams.isQualified = this.activeTab === 'exception' ? false : ''; this.getList() },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() }, resetQuery() { this.resetForm('queryForm'); this.handleQuery() },
    resultMeta(row) { const abnormal = row.inspectionStatus === 2 || row.inspectionStatus === '2'; if (abnormal) return { label: '待处理', type: 'warning' }; const qualified = row.qualifiedFlag === true || row.qualifiedFlag === 1 || row.isQualified === true || row.isQualified === 1; return qualified ? { label: '合格', type: 'success' } : { label: '不合格', type: 'danger' } },
    formatData(value) { if (!value) return '暂无参数数据'; if (typeof value === 'string') { try { return JSON.stringify(JSON.parse(value), null, 2) } catch (_) { return value } } return JSON.stringify(value, null, 2) },
    handleInspect(row) { const payload = { partId: row.id, partType: row.partType || row.partName, partData: row.partData || {}, standardData: row.standardData || {} }; this.$modal.confirm(`确认对零件“${row.partName || row.partCode}”执行 AI 检测？`).then(() => { this.inspectingId = row.id; return inspectPart(payload) }).then(() => { this.$modal.msgSuccess('检测完成'); this.getList() }).catch(() => {}).finally(() => { this.inspectingId = null }) }
  }
}
</script>
<style scoped>.part-data { margin: 0; padding: 12px 20px; white-space: pre-wrap; word-break: break-all; color: #606266; background: #f7f8fa; }</style>
