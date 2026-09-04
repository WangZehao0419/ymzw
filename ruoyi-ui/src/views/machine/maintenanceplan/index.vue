<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="82px">
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择计划状态" clearable>
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="重复规则" prop="repeatType">
        <el-select v-model="queryParams.repeatType" placeholder="请选择重复规则" clearable>
          <el-option v-for="item in repeatOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="编号/名称/设备" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新建计划</el-button></el-col>
      <el-col :span="1.5"><el-button plain icon="el-icon-refresh" size="mini" @click="getList">刷新</el-button></el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="planList">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="计划编号" prop="planNo" min-width="170" />
      <el-table-column label="计划名称" prop="planName" min-width="150" show-overflow-tooltip />
      <el-table-column label="设备" prop="equipmentName" min-width="140" show-overflow-tooltip>
        <template slot-scope="scope">{{ scope.row.equipmentName || '设备#' + scope.row.equipmentId }}</template>
      </el-table-column>
      <el-table-column label="保养类型" prop="maintenanceType" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="maintenanceTypeMeta(scope.row.maintenanceType).type">{{ scope.row.maintenanceType || '-' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="重复规则" prop="repeatType" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="repeatTypeMeta(scope.row.repeatType).type">{{ repeatTypeMeta(scope.row.repeatType).label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="触发时刻" min-width="170" align="center">
        <template slot-scope="scope">
          <div>{{ formatFireTime(scope.row) }}</div>
          <!-- 法定工作日规则附小字说明，降低理解成本 -->
          <div v-if="scope.row.repeatType === 'LEGAL_WORKDAY'" class="fire-tip">智能跳过节假日</div>
        </template>
      </el-table-column>
      <el-table-column label="下次触发时间" prop="nextFireTime" width="165" align="center">
        <template slot-scope="scope">
          <!-- 仅启用中的计划会继续调度，暂停/已完成显示 '-' -->
          <span v-if="scope.row.status === 'ENABLED' && scope.row.nextFireTime" class="next-fire">{{ formatTime(scope.row.nextFireTime) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="上次触发时间" prop="lastFireTime" width="165" align="center">
        <template slot-scope="scope">{{ formatTime(scope.row.lastFireTime) }}</template>
      </el-table-column>
      <el-table-column label="负责人" prop="assigneeName" width="100" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.assigneeName">{{ scope.row.assigneeName }}</span>
          <span v-else class="assignee-empty">待指派</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template slot-scope="scope"><el-tag :type="statusMeta(scope.row.status).type">{{ statusMeta(scope.row.status).label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="170" fixed="right">
        <template slot-scope="scope">
          <template v-if="scope.row.status === 'ENABLED'">
            <el-button size="mini" type="text" @click="handlePause(scope.row)">暂停</el-button>
            <el-button size="mini" type="text" @click="handleUpdate(scope.row)">编辑</el-button>
          </template>
          <template v-else-if="scope.row.status === 'PAUSED'">
            <el-button size="mini" type="text" @click="handleResume(scope.row)">恢复</el-button>
            <el-button size="mini" type="text" @click="handleUpdate(scope.row)">编辑</el-button>
            <el-button size="mini" type="text" @click="handleDelete(scope.row)">删除</el-button>
          </template>
          <!-- DONE 已完成计划无可执行操作 -->
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <!-- 新建/编辑弹窗：重复规则驱动动态表单（ONCE 补日期、MONTHLY 补几号，其余仅时刻） -->
    <el-dialog :title="title" :visible.sync="open" width="640px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="计划名称" prop="planName">
          <el-input v-model="form.planName" placeholder="请输入计划名称" />
        </el-form-item>
        <el-form-item label="设备" prop="equipmentId">
          <el-select v-model="form.equipmentId" filterable clearable placeholder="请选择设备" style="width:100%" @change="handleEquipmentChange">
            <el-option v-for="item in equipmentOptions" :key="item.id" :label="item.equipmentNo ? `${item.equipmentName}（${item.equipmentNo}）` : item.equipmentName" :value="item.id" />
            <!-- 兜底：编辑回显时设备不在前100条内，显示友好文案 -->
            <el-option v-if="form.equipmentId && !equipmentOptions.some(item => Number(item.id) === Number(form.equipmentId))" :key="form.equipmentId" :label="'设备 #' + form.equipmentId" :value="form.equipmentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="保养类型" prop="maintenanceType">
          <el-select v-model="form.maintenanceType" placeholder="请选择保养类型" style="width:100%">
            <el-option v-for="item in maintenanceTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="重复规则" prop="repeatType">
          <el-select v-model="form.repeatType" placeholder="请选择重复规则" style="width:100%" @change="handleRepeatChange">
            <el-option v-for="item in repeatOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.repeatType === 'ONCE'" label="触发日期" prop="fireDate">
          <el-date-picker v-model="form.fireDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择触发日期" style="width:100%" />
        </el-form-item>
        <el-form-item v-if="form.repeatType === 'MONTHLY'" label="每月几号" prop="fireDay">
          <el-input-number v-model="form.fireDay" :min="1" :max="31" :precision="0" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item label="触发时刻" prop="fireTime">
          <el-time-picker v-model="form.fireTime" value-format="HH:mm" format="HH:mm" placeholder="请选择触发时刻" style="width:100%" />
        </el-form-item>
        <el-form-item label="维护内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请填写维护内容（如清洁、润滑、紧固、校准项）" />
        </el-form-item>
        <el-form-item label="负责人" prop="assigneeId">
          <el-select v-model="form.assigneeId" placeholder="请选择负责人（可不选）" clearable filterable style="width:100%">
            <el-option v-for="item in userOptions" :key="item.userId" :label="item.nickName" :value="item.userId" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { fetchMaintenancePlanPage, addMaintenancePlan, updateMaintenancePlan, pauseMaintenancePlan, resumeMaintenancePlan, delMaintenancePlan } from '@/api/business/maintenanceplan'
import { listEquipment } from '@/api/business/machine'
import { listUser } from '@/api/system/user'

export default {
  name: 'BusinessMaintenancePlan',
  data() {
    return {
      loading: false,
      showSearch: true,
      total: 0,
      planList: [],
      statusOptions: [
        { label: '启用', value: 'ENABLED' },
        { label: '暂停', value: 'PAUSED' },
        { label: '已完成', value: 'DONE' }
      ],
      // 重复规则选项：筛选栏与新建/编辑弹窗共用（文案参考小米闹钟重复规则）
      repeatOptions: [
        { label: '只触发一次', value: 'ONCE' },
        { label: '每天', value: 'DAILY' },
        { label: '周一至周五', value: 'WEEKDAYS' },
        { label: '每月', value: 'MONTHLY' },
        { label: '法定工作日', value: 'LEGAL_WORKDAY' }
      ],
      maintenanceTypeOptions: ['日常保养', '一级保养', '二级保养', '精度校准', '润滑保养'],
      queryParams: { pageNum: 1, pageSize: 10, status: '', repeatType: '', keyword: '' },
      equipmentOptions: [],
      userOptions: [],
      open: false,
      title: '',
      form: {},
      rules: {
        planName: [{ required: true, message: '计划名称不能为空', trigger: 'blur' }],
        equipmentId: [{ required: true, message: '设备不能为空', trigger: 'change' }],
        maintenanceType: [{ required: true, message: '保养类型不能为空', trigger: 'change' }],
        repeatType: [{ required: true, message: '重复规则不能为空', trigger: 'change' }],
        fireDate: [{ required: true, message: '触发日期不能为空', trigger: 'change' }],
        fireDay: [{ required: true, message: '每月几号不能为空', trigger: 'change' }],
        fireTime: [{ required: true, message: '触发时刻不能为空', trigger: 'change' }]
      },
      refreshTimer: null
    }
  },
  created() {
    this.getList()
  },
  mounted() {
    // 30s 轮询：计划的触发/状态流转发生在后端调度器，轮询保持列表最新（与工单页口径一致）
    this.refreshTimer = setInterval(() => {
      this.getList()
    }, 30000)
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer)
    this.refreshTimer = null
  },
  methods: {
    getList() {
      this.loading = true
      fetchMaintenancePlanPage(this.queryParams).then(res => {
        this.planList = res.rows
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
    reset() {
      this.form = {
        id: undefined,
        planName: '',
        equipmentId: undefined,
        equipmentName: '',
        maintenanceType: '',
        content: '',
        repeatType: 'DAILY',
        fireTime: '',
        fireDay: 1,
        fireDate: '',
        assigneeId: undefined,
        assigneeName: '',
        // 编辑时回填保留原状态，新建时留空交由后端默认启用
        status: undefined
      }
      this.resetForm('form')
    },
    // 加载设备下拉（已加载则跳过，避免重复请求）——复用设备台账 listEquipment 接口
    loadEquipmentOptions() {
      if (this.equipmentOptions.length) return
      listEquipment({ pageNum: 1, pageSize: 100 }).then(res => {
        this.equipmentOptions = res.rows || []
      }).catch(() => {})
    },
    // 只加载正常状态用户作为负责人候选；每次打开弹窗前重载，保证新增用户可选（与工单页口径一致）
    loadUserOptions() {
      listUser({ pageNum: 1, pageSize: 100, status: '0' }).then(res => {
        this.userOptions = res.rows.map(u => ({ userId: u.userId, nickName: u.nickName || u.userName }))
      })
    },
    // 选择设备后同步设备名称，提交时冗余携带（后端列表展示用，与传感器管理同口径）
    handleEquipmentChange(val) {
      if (!val) {
        this.form.equipmentName = ''
        return
      }
      const eq = this.equipmentOptions.find(item => Number(item.id) === Number(val))
      if (eq) this.form.equipmentName = eq.equipmentName
    },
    // 切换重复规则时清理不适用字段，避免提交脏数据
    handleRepeatChange(val) {
      if (val !== 'ONCE') this.form.fireDate = ''
      if (val !== 'MONTHLY') this.form.fireDay = 1
    },
    handleAdd() {
      this.reset()
      this.loadEquipmentOptions()
      this.loadUserOptions()
      this.title = '新建维护计划'
      this.open = true
      this.$nextTick(() => { this.$refs.form && this.$refs.form.clearValidate() })
    },
    handleUpdate(row) {
      this.loadEquipmentOptions()
      this.loadUserOptions()
      // 回填：fireTime 规整为 "HH:mm" 适配 el-time-picker 的 value-format
      this.form = {
        id: row.id,
        planName: row.planName || '',
        equipmentId: row.equipmentId,
        equipmentName: row.equipmentName || '',
        maintenanceType: row.maintenanceType || '',
        content: row.content || '',
        repeatType: row.repeatType,
        fireTime: this.normalizeTime(row.fireTime),
        fireDay: row.fireDay || 1,
        fireDate: this.normalizeDate(row.fireDate),
        assigneeId: row.assigneeId || undefined,
        assigneeName: row.assigneeName || '',
        status: row.status
      }
      this.title = '编辑维护计划'
      this.open = true
      this.$nextTick(() => { this.$refs.form && this.$refs.form.clearValidate() })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        // 姓名/设备名随 id 冗余提交，后端列表展示（与工单指派同口径）；fireTime 提交 "HH:mm"（后端 LocalTime 接受）
        const user = this.userOptions.find(u => u.userId === this.form.assigneeId)
        const data = {
          id: this.form.id,
          planName: this.form.planName,
          equipmentId: this.form.equipmentId,
          equipmentName: this.form.equipmentName,
          maintenanceType: this.form.maintenanceType,
          content: this.form.content,
          repeatType: this.form.repeatType,
          fireTime: this.form.fireTime,
          fireDay: this.form.repeatType === 'MONTHLY' ? this.form.fireDay : null,
          fireDate: this.form.repeatType === 'ONCE' ? this.form.fireDate : null,
          assigneeId: this.form.assigneeId || null,
          assigneeName: user ? user.nickName : '',
          status: this.form.status || undefined
        }
        if (data.id) {
          updateMaintenancePlan(data.id, data).then(() => {
            this.$modal.msgSuccess('计划已更新')
            this.open = false
            this.getList()
          })
        } else {
          addMaintenancePlan(data).then(res => {
            const plan = (res && res.data) || res || {}
            // 创建成功即回显下次触发时间，省去回列表确认的一步
            this.$modal.msgSuccess(plan.nextFireTime ? '计划已创建，下次触发：' + this.formatTime(plan.nextFireTime) : '计划已创建')
            this.open = false
            this.getList()
          })
        }
      })
    },
    handlePause(row) {
      pauseMaintenancePlan(row.id).then(() => {
        this.$modal.msgSuccess('计划已暂停，暂停期间不再触发')
        this.getList()
      })
    },
    handleResume(row) {
      resumeMaintenancePlan(row.id).then(() => {
        this.$modal.msgSuccess('计划已恢复，恢复后继续按计划触发')
        this.getList()
      })
    },
    handleDelete(row) {
      this.$modal.confirm(`是否确认删除维护计划“${row.planName || row.planNo}”？删除后不可恢复`).then(() => delMaintenancePlan(row.id)).then(() => {
        this.$modal.msgSuccess('计划已删除')
        this.getList()
      }).catch(() => {})
    },
    // 触发时刻语义化展示：把 fireTime/fireDay/fireDate 翻译成人类可读文案
    formatFireTime(row) {
      const hm = this.normalizeTime(row.fireTime)
      if (!hm) return '-'
      switch (row.repeatType) {
        case 'ONCE': return '一次性 ' + (this.normalizeDate(row.fireDate) || '-') + ' ' + hm
        case 'DAILY': return '每天 ' + hm
        case 'WEEKDAYS': return '周一至周五 ' + hm
        case 'MONTHLY': return '每月 ' + (row.fireDay || '-') + ' 号 ' + hm
        case 'LEGAL_WORKDAY': return '法定工作日 ' + hm
        default: return hm
      }
    },
    // fireTime 规整："HH:mm:ss"/"HH:mm" 字符串或 Jackson LocalTime 数组 [时,分,秒] → "HH:mm"
    normalizeTime(time) {
      if (!time) return ''
      if (Array.isArray(time)) {
        return `${String(time[0] || 0).padStart(2, '0')}:${String(time[1] || 0).padStart(2, '0')}`
      }
      return time.toString().slice(0, 5)
    },
    // fireDate 规整："yyyy-MM-dd" 字符串或 Jackson LocalDate 数组 [年,月,日] → "yyyy-MM-dd"（空返回 ''）
    normalizeDate(date) {
      if (!date) return ''
      if (Array.isArray(date)) {
        return `${date[0]}-${String(date[1] || 1).padStart(2, '0')}-${String(date[2] || 1).padStart(2, '0')}`
      }
      return date.toString().replace('T', ' ').slice(0, 10)
    },
    repeatTypeMeta(type) {
      return {
        ONCE: { label: '只触发一次', type: 'info' },
        DAILY: { label: '每天', type: 'primary' },
        WEEKDAYS: { label: '周一至周五', type: 'success' },
        MONTHLY: { label: '每月', type: 'warning' },
        LEGAL_WORKDAY: { label: '法定工作日', type: 'danger' }
      }[type] || { label: type || '-', type: 'info' }
    },
    maintenanceTypeMeta(type) {
      return { '日常保养': { type: 'info' }, '一级保养': { type: 'primary' }, '精度校准': { type: 'warning' } }[type] || { type: '' }
    },
    statusMeta(status) {
      return {
        ENABLED: { label: '启用', type: 'success' },
        PAUSED: { label: '暂停', type: 'info' },
        DONE: { label: '已完成', type: '' }
      }[status] || { label: status || '-', type: 'info' }
    },
    // 时间格式化：兼容 ISO(带T)/普通字符串/数组(LocalDateTime Jackson 序列化)三种格式
    formatTime(time) {
      if (!time) return '-'
      let normalized = time
      if (typeof time === 'string') {
        normalized = time.replace('T', ' ').replace(/-/g, '/')
      } else if (Array.isArray(time)) {
        // Jackson LocalDateTime 数组 [年,月,日,时,分,秒,纳秒]：直接 new Date(数组) 是 Invalid Date，须按字段构造
        normalized = new Date(time[0] || 1970, (time[1] || 1) - 1, time[2] || 1, time[3] || 0, time[4] || 0, time[5] || 0)
      }
      return this.parseTime(new Date(normalized), '{y}-{m}-{d} {h}:{i}:{s}') || '-'
    }
  }
}
</script>

<style lang="scss" scoped>
// 未指派负责人的占位：红色高亮提示待指派
.assignee-empty {
  color: #f56c6c;
  font-weight: 600;
}

// 启用中计划的下次触发时间：高亮提醒即将执行
.next-fire {
  color: #409eff;
  font-weight: 600;
}

// 法定工作日规则的附加说明小字
.fire-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}
</style>
