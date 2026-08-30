<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="设备编号" prop="equipmentNo">
        <el-input v-model="queryParams.equipmentNo" placeholder="请输入设备编号" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="设备名称" prop="equipmentName">
        <el-input v-model="queryParams.equipmentName" placeholder="请输入设备名称" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="equipmentStatus">
        <el-select v-model="queryParams.equipmentStatus" placeholder="设备状态" clearable>
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属车间" prop="workshopId">
        <el-select v-model="queryParams.workshopId" placeholder="所属车间" clearable>
          <el-option v-for="item in workshopOptions" :key="item.id" :label="item.workshopName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增设备</el-button></el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="equipmentList">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="设备编号" prop="equipmentNo" min-width="120" />
      <el-table-column label="设备名称" prop="equipmentName" min-width="150" show-overflow-tooltip />
      <el-table-column label="设备型号" prop="equipmentModelName" min-width="120" />
      <el-table-column label="所属车间" prop="workshopName" min-width="120" />
      <el-table-column label="负责人" prop="equipmentUserName" min-width="100" />
      <el-table-column label="状态" prop="equipmentStatus" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="statusMeta(scope.row.equipmentStatus).type">{{ statusMeta(scope.row.equipmentStatus).label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="安装日期" prop="equipmentInstallDate" width="120" />
      <el-table-column label="备注" prop="equipmentRemark" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="290" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-monitor" @click="handleMonitor(scope.row)">实时监测</el-button>
          <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(scope.row)">详情</el-button>
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" :visible.sync="open" width="560px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="设备编号" prop="equipmentNo"><el-input v-model="form.equipmentNo" placeholder="请输入设备编号" /></el-form-item>
        <el-form-item label="设备名称" prop="equipmentName"><el-input v-model="form.equipmentName" placeholder="请输入设备名称" /></el-form-item>
        <el-form-item label="设备型号" prop="equipmentModelName"><el-input v-model="form.equipmentModelName" placeholder="请输入设备型号" /></el-form-item>
        <el-form-item label="所属车间" prop="workshopId"><el-select v-model="form.workshopId" placeholder="请选择所属车间" style="width:100%"><el-option v-for="item in workshopOptions" :key="item.id" :label="item.workshopName" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="负责人" prop="equipmentUserId"><el-select v-model="form.equipmentUserId" placeholder="请选择负责人" clearable filterable style="width:100%" @change="handleOwnerChange"><el-option v-for="item in userOptions" :key="item.userId" :label="item.nickName" :value="item.userId" /></el-select></el-form-item>
        <el-form-item label="设备状态" prop="equipmentStatus"><el-select v-model="form.equipmentStatus" style="width:100%"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="安装日期" prop="equipmentInstallDate"><el-date-picker v-model="form.equipmentInstallDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择安装日期" style="width:100%" /></el-form-item>
        <el-form-item label="备注" prop="equipmentRemark"><el-input v-model="form.equipmentRemark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer"><el-button type="primary" @click="submitForm">确 定</el-button><el-button @click="cancel">取 消</el-button></div>
    </el-dialog>

    <el-dialog title="设备详情" :visible.sync="detailOpen" width="560px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="设备编号">{{ detail.equipmentNo }}</el-descriptions-item>
        <el-descriptions-item label="设备名称">{{ detail.equipmentName }}</el-descriptions-item>
        <el-descriptions-item label="设备型号">{{ detail.equipmentModelName }}</el-descriptions-item>
        <el-descriptions-item label="所属车间">{{ detail.workshopName }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ detail.equipmentUserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="安装日期">{{ detail.equipmentInstallDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.equipmentRemark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script>
import { listEquipment, getEquipment, addEquipment, updateEquipment, delEquipment, listWorkshop } from '@/api/business/machine'
import { listUser } from '@/api/system/user'

export default {
  name: 'BusinessDevice',
  data() {
    return {
      loading: false, showSearch: true, total: 0, equipmentList: [], open: false, detailOpen: false, title: '', detail: {}, workshopOptions: [], userOptions: [],
      statusOptions: [{ label: '运行中', value: '0' }, { label: '待机', value: '1' }, { label: '维护中', value: '2' }, { label: '离线', value: '3' }],
      queryParams: { pageNum: 1, pageSize: 10, equipmentNo: '', equipmentName: '', equipmentStatus: '', workshopId: '' },
      form: {},
      rules: {
        equipmentNo: [{ required: true, message: '设备编号不能为空', trigger: 'blur' }],
        equipmentName: [{ required: true, message: '设备名称不能为空', trigger: 'blur' }],
        equipmentModelName: [{ required: true, message: '设备型号不能为空', trigger: 'blur' }],
        workshopId: [{ required: true, message: '所属车间不能为空', trigger: 'change' }]
      }
    }
  },
  created() { this.getList(); this.loadWorkshopOptions() },
  methods: {
    statusMeta(status) { return { '0': { label: '运行中', type: 'success' }, '1': { label: '待机', type: '' }, '2': { label: '维护中', type: 'warning' }, '3': { label: '离线', type: 'info' } }[String(status)] || { label: '未知', type: 'info' } },
    getList() { this.loading = true; listEquipment(this.queryParams).then(res => { this.equipmentList = res.rows; this.total = res.total }).finally(() => { this.loading = false }) },
    // 只加载启用状态的车间；页面 keep-alive 缓存后车间数据可能变化，故每次打开弹窗前重载
    loadWorkshopOptions() { listWorkshop({ page: 1, pageSize: 100, workshopStatus: '0' }).then(res => { this.workshopOptions = res.rows }) },
    // 只加载启用状态的用户作为负责人候选；与车间同理，弹窗打开前重载；昵称缺省时回退登录名
    loadUserOptions() { listUser({ pageNum: 1, pageSize: 100, status: '0' }).then(res => { this.userOptions = res.rows.map(u => ({ userId: u.userId, nickName: u.nickName || u.userName })) }) },
    // 选择负责人时同步冗余姓名字段，后端 DTO 依赖 equipmentUserName 展示；清空时一并置空
    handleOwnerChange(userId) { const item = this.userOptions.find(u => u.userId === userId); this.form.equipmentUserName = item ? item.nickName : '' },
    reset() { this.form = { id: undefined, equipmentNo: '', equipmentName: '', equipmentModelName: '', workshopId: undefined, workshopName: '', equipmentUserId: undefined, equipmentUserName: '', equipmentStatus: '0', equipmentInstallDate: '', equipmentRemark: '' }; this.resetForm('form') },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() { this.resetForm('queryForm'); this.handleQuery() },
    handleAdd() { this.reset(); this.loadWorkshopOptions(); this.loadUserOptions(); this.title = '新增设备'; this.open = true },
    handleUpdate(row) { this.reset(); this.loadWorkshopOptions(); this.loadUserOptions(); getEquipment(row.id).then(data => { this.form = { ...data }; this.title = '编辑设备'; this.open = true }) },
    handleDetail(row) { getEquipment(row.id).then(data => { this.detail = data; this.detailOpen = true }) },
    // 跳转传感器实时监测页，携带设备 ID
    handleMonitor(row) { this.$router.push({ path: '/machine-business/device/sensor-monitor', query: { equipmentId: row.id } }) },
    cancel() { this.open = false; this.reset() },
    submitForm() { this.$refs.form.validate(valid => { if (!valid) return; const ws = this.workshopOptions.find(w => w.id === this.form.workshopId); this.form.workshopName = ws ? ws.workshopName : ''; const action = this.form.id ? updateEquipment : addEquipment; action(this.form).then(() => { this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功'); this.open = false; this.getList() }) }) },
    handleDelete(row) { this.$modal.confirm(`是否确认删除设备“${row.equipmentName}”？`).then(() => delEquipment(row.id)).then(() => { this.$modal.msgSuccess('删除成功'); this.getList() }).catch(() => {}) }
  }
}
</script>
