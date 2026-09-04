<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="车间名称" prop="workshopName">
        <el-input v-model="queryParams.workshopName" placeholder="请输入车间名称" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="workshopStatus">
        <el-select v-model="queryParams.workshopStatus" placeholder="车间状态" clearable>
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增车间</el-button></el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="workshopList">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="车间名称" prop="workshopName" min-width="150" show-overflow-tooltip />
      <el-table-column label="位置" prop="workshopLocation" min-width="120" />
      <el-table-column label="负责人" prop="workshopManager" min-width="100" />
      <el-table-column label="状态" prop="workshopStatus" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="statusMeta(scope.row.workshopStatus).type">{{ statusMeta(scope.row.workshopStatus).label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="备注" prop="workshopRemark" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="230" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
          <el-button size="mini" type="text" icon="el-icon-picture-outline" @click="handleTwin(scope.row)">数字孪生</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" :visible.sync="open" width="560px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="车间名称" prop="workshopName"><el-input v-model="form.workshopName" placeholder="请输入车间名称" /></el-form-item>
        <el-form-item label="位置" prop="workshopLocation"><el-input v-model="form.workshopLocation" placeholder="请输入位置" /></el-form-item>
        <el-form-item label="负责人" prop="workshopManagerId">
          <el-select v-model="form.workshopManagerId" placeholder="请选择负责人" clearable filterable style="width:100%" @change="handleManagerChange">
            <el-option v-for="item in userOptions" :key="item.userId" :label="item.nickName" :value="item.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="workshopStatus"><el-select v-model="form.workshopStatus" style="width:100%"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="备注" prop="workshopRemark"><el-input v-model="form.workshopRemark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer"><el-button type="primary" @click="submitForm">确 定</el-button><el-button @click="cancel">取 消</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { listWorkshop, getWorkshop, addWorkshop, updateWorkshop, delWorkshop } from '@/api/business/machine'
import { listUser } from '@/api/system/user'

export default {
  name: 'BusinessWorkshop',
  data() {
    return {
      loading: false, showSearch: true, total: 0, workshopList: [], open: false, title: '',
      statusOptions: [{ label: '启用', value: '0' }, { label: '停用', value: '1' }],
      userOptions: [],
      queryParams: { pageNum: 1, pageSize: 10, workshopName: '', workshopStatus: '' },
      form: {},
      rules: {
        workshopName: [{ required: true, message: '车间名称不能为空', trigger: 'blur' }]
      }
    }
  },
  created() { this.getList() },
  methods: {
    statusMeta(status) { return { '0': { label: '启用', type: 'success' }, '1': { label: '停用', type: 'info' } }[String(status)] || { label: '未知', type: 'info' } },
    getList() { this.loading = true; listWorkshop(this.queryParams).then(res => { this.workshopList = res.rows; this.total = res.total }).finally(() => { this.loading = false }) },
    // 只加载启用状态的用户作为负责人候选；弹窗打开前重载，保证新建用户及时出现；昵称缺省时回退登录名
    loadUserOptions() { listUser({ pageNum: 1, pageSize: 100, status: '0' }).then(res => { this.userOptions = res.rows.map(u => ({ userId: u.userId, nickName: u.nickName || u.userName })) }) },
    // 选择负责人时同步冗余姓名字段（后端 workshop_manager 存姓名用于列表展示）；清空时一并置空
    handleManagerChange(userId) { const item = this.userOptions.find(u => u.userId === userId); this.form.workshopManager = item ? item.nickName : '' },
    reset() { this.form = { id: undefined, workshopName: '', workshopLocation: '', workshopManagerId: undefined, workshopManager: '', workshopStatus: '0', workshopRemark: '' }; this.resetForm('form') },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() { this.resetForm('queryForm'); this.handleQuery() },
    handleAdd() { this.reset(); this.loadUserOptions(); this.title = '新增车间'; this.open = true },
    handleUpdate(row) { this.reset(); this.loadUserOptions(); getWorkshop(row.id).then(data => { this.form = { ...data }; this.title = '编辑车间'; this.open = true }) },
    cancel() { this.open = false; this.reset() },
    submitForm() { this.$refs.form.validate(valid => { if (!valid) return; const action = this.form.id ? updateWorkshop : addWorkshop; action(this.form).then(() => { this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功'); this.open = false; this.getList() }) }) },
    handleDelete(row) { this.$modal.confirm(`是否确认删除车间“${row.workshopName}”？`).then(() => delWorkshop(row.id)).then(() => { this.$modal.msgSuccess('删除成功'); this.getList() }).catch(() => {}) },
    // 跳转该车间的数字孪生 3D 编辑页（workshopId 经 query 传递，目标页缺失时自动回落）
    handleTwin(row) { this.$router.push({ path: '/machine-business/workshop/twin', query: { workshopId: row.id } }) }
  }
}
</script>
