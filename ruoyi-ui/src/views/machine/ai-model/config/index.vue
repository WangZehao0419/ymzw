<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" label-width="70px">
      <el-form-item label="模型名称" prop="modelName"><el-input v-model="queryParams.modelName" clearable placeholder="请输入模型名称" @keyup.enter.native="handleQuery" /></el-form-item>
      <el-form-item label="模型类型" prop="modelType"><el-select v-model="queryParams.modelType" clearable placeholder="请选择"><el-option v-for="item in modelTypes" :key="item" :label="typeLabel(item)" :value="item" /></el-select></el-form-item>
      <el-form-item label="状态" prop="status"><el-select v-model="queryParams.status" clearable placeholder="请选择"><el-option label="启用" :value="1" /><el-option label="禁用" :value="0" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button><el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8"><el-col :span="1.5"><el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增模型</el-button></el-col><right-toolbar :showSearch.sync="showSearch" @queryTable="getList" /></el-row>
    <el-table v-loading="loading" :data="modelList">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="模型名称" prop="modelName" min-width="160" />
      <el-table-column label="标识" prop="modelIdentifier" min-width="150" />
      <el-table-column label="类型" prop="modelType" width="110"><template slot-scope="scope"><el-tag>{{ typeLabel(scope.row.modelType) }}</el-tag></template></el-table-column>
      <el-table-column label="API 地址" prop="apiEndpoint" min-width="220" show-overflow-tooltip />
      <el-table-column label="Max Tokens" prop="maxTokens" width="110" align="center" />
      <el-table-column label="温度" prop="temperature" width="80" align="center" />
      <el-table-column label="状态" width="100" align="center"><template slot-scope="scope"><el-switch :value="Number(scope.row.status) === 1" @change="val => handleStatus(scope.row, val)" /></template></el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="150" fixed="right" align="center"><template slot-scope="scope"><el-button type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button><el-button type="text" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.page" :limit.sync="queryParams.pageSize" @pagination="getList" />
    <el-dialog :title="title" :visible.sync="open" width="650px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="105px">
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="模型名称" prop="modelName"><el-input v-model="form.modelName" /></el-form-item></el-col><el-col :span="12"><el-form-item label="模型类型" prop="modelType"><el-select v-model="form.modelType" style="width:100%"><el-option v-for="item in modelTypes" :key="item" :label="typeLabel(item)" :value="item" /></el-select></el-form-item></el-col></el-row>
        <el-form-item label="模型标识" prop="modelIdentifier"><el-input v-model="form.modelIdentifier" /></el-form-item>
        <el-form-item label="API 地址" prop="apiEndpoint"><el-input v-model="form.apiEndpoint" /></el-form-item>
        <el-form-item label="API 密钥" prop="apiKey"><el-input v-model="form.apiKey" show-password placeholder="编辑时留空表示不修改" /></el-form-item>
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="Max Tokens"><el-input-number v-model="form.maxTokens" :min="1" :max="200000" /></el-form-item></el-col><el-col :span="12"><el-form-item label="温度"><el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" /></el-form-item></el-col></el-row>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button type="primary" @click="submitForm">确 定</el-button><el-button @click="open=false">取 消</el-button></div>
    </el-dialog>
  </div>
</template>
<script>
import { listModels, getModel, addModel, updateModel, delModel, changeModelStatus, getModelTypes } from '@/api/business/ai'
export default {
  name: 'BusinessAiModel',
  data() { return { loading: false, showSearch: true, total: 0, modelList: [], modelTypes: ['LLM', 'EMBEDDING', 'IMAGE', 'SPEECH'], open: false, title: '', queryParams: { page: 1, pageSize: 10, modelName: '', modelType: '', status: '' }, form: {}, rules: { modelName: [{ required: true, message: '模型名称不能为空', trigger: 'blur' }], modelType: [{ required: true, message: '模型类型不能为空', trigger: 'change' }], modelIdentifier: [{ required: true, message: '模型标识不能为空', trigger: 'blur' }], apiEndpoint: [{ required: true, message: 'API 地址不能为空', trigger: 'blur' }] } } },
  created() { this.getList(); getModelTypes().then(data => { if (Array.isArray(data) && data.length) this.modelTypes = data }).catch(() => {}) },
  methods: {
    typeLabel(type) { return { LLM: '大语言模型', EMBEDDING: '向量嵌入', IMAGE: '图像模型', SPEECH: '语音模型' }[type] || type },
    getList() { this.loading = true; listModels(this.queryParams).then(res => { this.modelList = res.rows; this.total = res.total }).finally(() => { this.loading = false }) },
    reset() { this.form = { id: undefined, modelName: '', modelType: 'LLM', modelIdentifier: '', apiEndpoint: '', apiKey: '', maxTokens: 4096, temperature: 0.7, status: 1, description: '' }; this.resetForm('form') },
    handleQuery() { this.queryParams.page = 1; this.getList() }, resetQuery() { this.resetForm('queryForm'); this.handleQuery() }, handleAdd() { this.reset(); this.title = '新增 AI 模型'; this.open = true },
    handleUpdate(row) { this.reset(); getModel(row.id).then(data => { this.form = { ...data, apiKey: '' }; this.title = '编辑 AI 模型'; this.open = true }) },
    submitForm() { this.$refs.form.validate(valid => { if (!valid) return; const action = this.form.id ? updateModel : addModel; const data = { ...this.form }; if (!data.apiKey) delete data.apiKey; action(data).then(() => { this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功'); this.open = false; this.getList() }) }) },
    handleDelete(row) { this.$modal.confirm(`是否确认删除模型“${row.modelName}”？`).then(() => delModel(row.id)).then(() => { this.$modal.msgSuccess('删除成功'); this.getList() }).catch(() => {}) },
    handleStatus(row, enabled) { const old = row.status; row.status = enabled ? 1 : 0; changeModelStatus(row.id, row.status).then(() => this.$modal.msgSuccess('状态更新成功')).catch(() => { row.status = old }) }
  }
}
</script>
