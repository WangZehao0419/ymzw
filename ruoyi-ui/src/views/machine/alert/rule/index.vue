<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增规则</el-button></el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="ruleList">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="传感器编号" prop="sensorCode" min-width="140" />
      <el-table-column label="传感器名称" prop="sensorName" min-width="140" show-overflow-tooltip>
        <template slot-scope="scope">{{ scope.row.sensorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="上限阈值" prop="upperLimit" width="110" align="center">
        <template slot-scope="scope">{{ scope.row.upperLimit === null || scope.row.upperLimit === undefined ? '-' : scope.row.upperLimit }}</template>
      </el-table-column>
      <el-table-column label="下限阈值" prop="lowerLimit" width="110" align="center">
        <template slot-scope="scope">{{ scope.row.lowerLimit === null || scope.row.lowerLimit === undefined ? '-' : scope.row.lowerLimit }}</template>
      </el-table-column>
      <el-table-column label="持续点数" prop="sustainPoints" width="100" align="center" />
      <el-table-column label="告警级别" prop="level" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="levelMeta(scope.row.level).type" :effect="levelMeta(scope.row.level).effect">{{ levelMeta(scope.row.level).label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="启用状态" prop="enabled" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="Number(scope.row.enabled) === 1 ? 'success' : 'info'">{{ Number(scope.row.enabled) === 1 ? '启用' : '禁用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" :visible.sync="open" width="520px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="传感器" prop="sensorId">
          <el-select v-model="form.sensorId" placeholder="请选择传感器" style="width:100%">
            <el-option v-for="item in sensorOptions" :key="item.id" :label="item.sensorCode + (item.sensorName ? '（' + item.sensorName + '）' : '')" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上限阈值" prop="upperLimit"><el-input-number v-model="form.upperLimit" :precision="2" controls-position="right" placeholder="留空表示不限制" style="width:100%" /></el-form-item>
        <el-form-item label="下限阈值" prop="lowerLimit"><el-input-number v-model="form.lowerLimit" :precision="2" controls-position="right" placeholder="留空表示不限制" style="width:100%" /></el-form-item>
        <el-form-item label="持续点数" prop="sustainPoints"><el-input-number v-model="form.sustainPoints" :min="1" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="告警级别" prop="level">
          <el-select v-model="form.level" style="width:100%">
            <el-option label="预警" value="WARNING" />
            <el-option label="重要" value="IMPORTANT" />
            <el-option label="严重" value="SEVERE" />
            <el-option label="危急" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listAlertRules, addAlertRule, updateAlertRule, deleteAlertRule } from '@/api/business/alert'
import { listSensor } from '@/api/business/machine'

export default {
  name: 'BusinessAlertRule',
  data() {
    return {
      loading: false,
      showSearch: true,
      total: 0,
      ruleList: [],
      open: false,
      title: '',
      queryParams: { pageNum: 1, pageSize: 10 },
      form: {},
      // 传感器下拉选项(进入页面加载一次,新增/编辑对话框共用)
      sensorOptions: [],
      rules: {
        sensorId: [{ required: true, message: '请选择传感器', trigger: 'change' }]
      }
    }
  },
  created() {
    this.getList()
    this.getSensorOptions()
  },
  methods: {
    getList() {
      this.loading = true
      listAlertRules(this.queryParams).then(res => {
        this.ruleList = res.rows
        this.total = res.total
      }).finally(() => {
        this.loading = false
      })
    },
    // 进页面加载一次传感器下拉数据,避免每次打开对话框重复请求;pageSize 放大以覆盖全部传感器
    getSensorOptions() {
      listSensor({ pageNum: 1, pageSize: 1000 }).then(res => {
        this.sensorOptions = res.rows
      })
    },
    reset() {
      this.form = { id: undefined, sensorId: undefined, upperLimit: undefined, lowerLimit: undefined, sustainPoints: 1, level: 'WARNING', enabled: 1 }
      this.resetForm('form')
    },
    handleAdd() {
      this.reset()
      this.title = '新增告警规则'
      this.open = true
    },
    handleUpdate(row) {
      this.reset()
      this.form = { ...row, enabled: Number(row.enabled) }
      this.title = '编辑告警规则'
      this.open = true
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const action = this.form.id ? updateAlertRule(this.form.id, this.form) : addAlertRule(this.form)
        action.then(() => {
          this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功')
          this.open = false
          this.getList()
        })
      })
    },
    handleDelete(row) {
      this.$modal.confirm(`是否确认删除传感器“${row.sensorCode}”的告警规则？`).then(() => deleteAlertRule(row.id)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    },
    // 四级等级标签映射:同色系内 dark 实底强于 light 浅底区分相邻档(重要>预警、危急>严重),红色系整体重于橙色系区分高低档
    levelMeta(level) {
      return {
        WARNING: { label: '预警', type: 'warning', effect: 'light' },
        IMPORTANT: { label: '重要', type: 'warning', effect: 'dark' },
        SEVERE: { label: '严重', type: 'danger', effect: 'light' },
        CRITICAL: { label: '危急', type: 'danger', effect: 'dark' }
      }[level] || { label: level || '-', type: 'info', effect: 'light' }
    }
  }
}
</script>
