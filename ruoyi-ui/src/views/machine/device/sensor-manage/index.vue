<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="82px">
      <el-form-item label="传感器编号" prop="sensorCode"><el-input v-model="queryParams.sensorCode" placeholder="请输入编号" clearable @keyup.enter.native="handleQuery" /></el-form-item>
      <el-form-item label="传感器名称" prop="sensorName"><el-input v-model="queryParams.sensorName" placeholder="请输入名称" clearable @keyup.enter.native="handleQuery" /></el-form-item>
      <el-form-item label="状态" prop="sensorStatus"><el-select v-model="queryParams.sensorStatus" clearable placeholder="请选择"><el-option label="启用" :value="1" /><el-option label="禁用" :value="0" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button><el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8"><el-col :span="1.5"><el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增传感器</el-button></el-col><right-toolbar :showSearch.sync="showSearch" @queryTable="getList" /></el-row>
    <el-table v-loading="loading" :data="sensorList">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="传感器编号" prop="sensorCode" min-width="130" />
      <el-table-column label="传感器名称" prop="sensorName" min-width="150" />
      <el-table-column label="单位" prop="sensorUnit" width="90" />
      <el-table-column label="绑定设备" prop="equipmentName" min-width="160" />
      <el-table-column label="状态" prop="sensorStatus" width="90" align="center"><template slot-scope="scope"><el-tag :type="Number(scope.row.sensorStatus) === 1 ? 'success' : 'info'">{{ Number(scope.row.sensorStatus) === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" align="center" width="150" fixed="right"><template slot-scope="scope"><el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button><el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
    <el-dialog :title="title" :visible.sync="open" width="520px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="传感器编号" prop="sensorCode"><el-input v-model="form.sensorCode" /></el-form-item>
        <el-form-item label="传感器名称" prop="sensorName"><el-input v-model="form.sensorName" /></el-form-item>
        <el-form-item label="测量单位" prop="sensorUnit"><el-input v-model="form.sensorUnit" /></el-form-item>
        <el-form-item label="绑定设备" prop="equipmentId">
          <el-select v-model="form.equipmentId" filterable clearable placeholder="请选择设备" style="width:100%" @change="handleEquipmentChange">
            <el-option v-for="item in equipmentList" :key="item.id" :label="item.equipmentNo ? `${item.equipmentName}（${item.equipmentNo}）` : item.equipmentName" :value="item.id" />
            <!-- 兜底：编辑回显时设备不在前100条内，显示友好文案 -->
            <el-option v-if="form.equipmentId && !equipmentList.some(item => Number(item.id) === Number(form.equipmentId))" :key="form.equipmentId" :label="'设备 #' + form.equipmentId" :value="form.equipmentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="sensorStatus"><el-radio-group v-model="form.sensorStatus"><el-radio :label="1">启用</el-radio><el-radio :label="0">禁用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <div slot="footer"><el-button type="primary" @click="submitForm">确 定</el-button><el-button @click="open = false">取 消</el-button></div>
    </el-dialog>
  </div>
</template>
<script>
import { listSensor, addSensor, updateSensor, delSensor, listEquipment } from '@/api/business/machine'
export default {
  name: 'BusinessSensor',
  data() { return { loading: false, showSearch: true, total: 0, sensorList: [], equipmentList: [], open: false, title: '', queryParams: { pageNum: 1, pageSize: 10, sensorCode: '', sensorName: '', sensorStatus: '' }, form: {}, rules: { sensorCode: [{ required: true, message: '传感器编号不能为空', trigger: 'blur' }], sensorName: [{ required: true, message: '传感器名称不能为空', trigger: 'blur' }], sensorUnit: [{ required: true, message: '测量单位不能为空', trigger: 'blur' }], equipmentId: [{ required: true, message: '绑定设备不能为空', trigger: 'change' }] } } },
  created() { this.getList() },
  methods: {
    getList() { this.loading = true; listSensor(this.queryParams).then(res => { this.sensorList = res.rows; this.total = res.total }).finally(() => { this.loading = false }) },
    reset() { this.form = { id: undefined, sensorCode: '', sensorName: '', sensorUnit: '', sensorStatus: 1, equipmentId: undefined, equipmentName: '' }; this.resetForm('form') },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() }, resetQuery() { this.resetForm('queryForm'); this.handleQuery() },
    // 加载设备下拉列表（已加载则跳过，避免重复请求）
    loadEquipmentList() { if (this.equipmentList.length) return; listEquipment({ pageNum: 1, pageSize: 100 }).then(res => { this.equipmentList = res.rows || [] }).catch(() => {}) },
    // 选择设备后同步设备名称，供提交时携带 equipmentName
    handleEquipmentChange(val) { if (!val) { this.form.equipmentName = ''; return } const eq = this.equipmentList.find(item => Number(item.id) === Number(val)); if (eq) this.form.equipmentName = eq.equipmentName },
    handleAdd() { this.reset(); this.loadEquipmentList(); this.title = '新增传感器'; this.open = true }, handleUpdate(row) { this.form = { ...row }; this.loadEquipmentList(); this.title = '编辑传感器'; this.open = true },
    submitForm() { this.$refs.form.validate(valid => { if (!valid) return; const action = this.form.id ? updateSensor : addSensor; action(this.form).then(() => { this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功'); this.open = false; this.getList() }) }) },
    handleDelete(row) { this.$modal.confirm(`是否确认删除传感器“${row.sensorName}”？`).then(() => delSensor(row.id)).then(() => { this.$modal.msgSuccess('删除成功'); this.getList() }).catch(() => {}) }
  }
}
</script>
