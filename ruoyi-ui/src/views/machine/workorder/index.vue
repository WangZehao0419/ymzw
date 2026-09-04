<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择工单状态" clearable>
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="工单类型" prop="orderType">
        <el-select v-model="queryParams.orderType" placeholder="请选择工单类型" clearable>
          <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="编号/设备/传感器" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button plain icon="el-icon-refresh" size="mini" @click="getList">刷新</el-button></el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="workOrderList">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="工单编号" prop="orderNo" min-width="170" />
      <el-table-column label="工单类型" prop="orderType" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="orderTypeMeta(scope.row.orderType).type">{{ scope.row.orderType || '-' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="级别" prop="alertLevel" width="90" align="center">
        <template slot-scope="scope"><el-tag :type="levelMeta(scope.row.alertLevel).type">{{ levelMeta(scope.row.alertLevel).label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="设备名称" prop="equipmentName" min-width="140" show-overflow-tooltip>
        <template slot-scope="scope">{{ scope.row.equipmentName || '设备#' + scope.row.equipmentId }}</template>
      </el-table-column>
      <el-table-column label="传感器" prop="sensorName" min-width="130" show-overflow-tooltip>
        <template slot-scope="scope">{{ scope.row.sensorName || scope.row.sensorCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="工单内容" prop="description" min-width="220" show-overflow-tooltip>
        <template slot-scope="scope">{{ scope.row.description || '-' }}</template>
      </el-table-column>
      <el-table-column label="处理人" prop="handlerName" width="100" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.handlerName">{{ scope.row.handlerName }}</span>
          <span v-else class="assignee-empty">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="statusMeta(scope.row.status).type">{{ statusMeta(scope.row.status).label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="165" align="center">
        <template slot-scope="scope">{{ formatTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="240" fixed="right">
        <template slot-scope="scope">
          <template v-if="scope.row.status === 'PENDING'">
            <el-button size="mini" type="text" @click="handleAssign(scope.row)">转派</el-button>
            <el-button size="mini" type="text" @click="handleComplete(scope.row)">完成</el-button>
            <el-button size="mini" type="text" @click="handleCancel(scope.row)">取消</el-button>
          </template>
          <template v-else-if="scope.row.status === 'PROCESSING'">
            <el-button size="mini" type="text" @click="handleAssign(scope.row)">转派</el-button>
            <el-button size="mini" type="text" @click="handleComplete(scope.row)">完成</el-button>
            <el-button size="mini" type="text" @click="handleCancel(scope.row)">取消</el-button>
          </template>
          <!-- 流转按钮置于状态分支之外，所有状态均可见 -->
          <el-button size="mini" type="text" @click="handleFlow(scope.row)">流转</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <!-- 转派弹窗：选择处理人 -->
    <el-dialog :title="assignTitle" :visible.sync="assignOpen" width="480px" append-to-body>
      <el-form ref="assignForm" :model="assignForm" :rules="assignRules" label-width="90px">
        <el-form-item label="工单编号">{{ assignForm.orderNo }}</el-form-item>
        <el-form-item label="处理人" prop="handler">
          <el-select v-model="assignForm.handler" placeholder="请选择处理人" clearable filterable style="width:100%">
            <el-option v-for="item in userOptions" :key="item.userId" :label="item.nickName" :value="item.userId" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitAssign">确 定</el-button>
        <el-button @click="assignOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 完成弹窗：处理结果必填，完成后后端联动解除告警并下发维护复位指令 -->
    <el-dialog title="完成工单" :visible.sync="completeOpen" width="520px" append-to-body>
      <el-alert title="完成后将解除对应告警，并向设备下发维护复位指令使模拟数据回落" type="warning" :closable="false" show-icon class="mb8" />
      <el-form ref="completeForm" :model="completeForm" :rules="completeRules" label-width="90px">
        <el-form-item label="处理结果" prop="handleRemark">
          <el-input v-model="completeForm.handleRemark" type="textarea" :rows="4" placeholder="请填写处理结果" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitComplete">确 定</el-button>
        <el-button @click="completeOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 流转记录弹窗：按时间升序展示工单全生命周期操作 -->
    <el-dialog :title="'流转记录 - ' + flowOrderNo" :visible.sync="flowOpen" width="560px" append-to-body>
      <div v-loading="flowLoading">
        <el-timeline v-if="flowList.length > 0" style="padding-left: 8px">
          <el-timeline-item v-for="(item, index) in flowList" :key="index" :timestamp="formatTime(item.createTime)" placement="top">
            <el-tag :type="actionMeta(item.action).type" size="small">{{ actionMeta(item.action).label }}</el-tag>
            <span style="margin-left: 8px">操作人 {{ item.operator || '-' }}</span>
            <div v-if="item.detail">{{ item.detail }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else-if="!flowLoading" description="暂无流转记录" :image-size="80" />
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { fetchWorkOrderPage, assignWorkOrder, completeWorkOrder, cancelWorkOrder, fetchWorkOrderLogs } from '@/api/business/workorder'
import { listUser } from '@/api/system/user'

export default {
  name: 'BusinessWorkOrder',
  data() {
    return {
      loading: false,
      showSearch: true,
      total: 0,
      workOrderList: [],
      statusOptions: [
        { label: '待处理', value: 'PENDING' },
        { label: '处理中', value: 'PROCESSING' },
        { label: '已完成', value: 'COMPLETED' },
        { label: '已取消', value: 'CANCELLED' }
      ],
      typeOptions: [
        { label: '故障维修', value: '故障维修' },
        { label: '预防维护', value: '预防维护' }
      ],
      queryParams: { pageNum: 1, pageSize: 10, status: '', orderType: '', keyword: '' },
      userOptions: [],
      assignOpen: false,
      assignTitle: '',
      assignForm: { id: undefined, orderNo: '', handler: undefined },
      assignRules: {
        handler: [{ required: true, message: '处理人不能为空', trigger: 'change' }]
      },
      completeOpen: false,
      completeForm: { id: undefined, orderNo: '', handleRemark: '' },
      completeRules: {
        handleRemark: [{ required: true, message: '处理结果不能为空', trigger: 'blur' }]
      },
      // 流转记录弹窗状态：flowList 按时间升序展示
      flowOpen: false,
      flowLoading: false,
      flowList: [],
      flowOrderNo: '',
      refreshTimer: null
    }
  },
  created() {
    this.getList()
  },
  mounted() {
    // 30s 轮询：工单由后端自动生成/流转，轮询保持列表最新（参考预测性维护页）
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
      fetchWorkOrderPage(this.queryParams).then(res => {
        this.workOrderList = res.rows
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
    // 只加载正常状态用户作为处理人候选；每次打开弹窗前重载，保证新增用户可选（与设备台账口径一致）
    loadUserOptions() {
      listUser({ pageNum: 1, pageSize: 100, status: '0' }).then(res => {
        this.userOptions = res.rows.map(u => ({ userId: u.userId, nickName: u.nickName || u.userName }))
      })
    },
    // 转派弹窗：PENDING/PROCESSING 均可转派处理人，调 assign 接口
    handleAssign(row) {
      this.assignForm = { id: row.id, orderNo: row.orderNo, handler: row.handler || undefined }
      this.loadUserOptions()
      this.assignTitle = '转派处理人'
      this.assignOpen = true
      this.$nextTick(() => { this.$refs.assignForm && this.$refs.assignForm.clearValidate() })
    },
    submitAssign() {
      this.$refs.assignForm.validate(valid => {
        if (!valid) return
        // 姓名随 id 冗余提交，后端工单表存 handler_name 用于列表展示
        const user = this.userOptions.find(u => u.userId === this.assignForm.handler)
        assignWorkOrder(this.assignForm.id, { handler: this.assignForm.handler, handlerName: user ? user.nickName : '' }).then(() => {
          this.$modal.msgSuccess('转派处理人成功')
          this.assignOpen = false
          this.getList()
        })
      })
    },
    handleComplete(row) {
      this.completeForm = { id: row.id, orderNo: row.orderNo, handleRemark: '' }
      this.completeOpen = true
      this.$nextTick(() => { this.$refs.completeForm && this.$refs.completeForm.clearValidate() })
    },
    submitComplete() {
      this.$refs.completeForm.validate(valid => {
        if (!valid) return
        completeWorkOrder(this.completeForm.id, { handleRemark: this.completeForm.handleRemark }).then(res => {
          // 兼容 AjaxResult(data 字段) 与裸对象两种返回结构，与 alert.js 的 pageResult 口径一致
          const data = (res && res.data) || res || {}
          if (data.resetSuccess) {
            this.$modal.msgSuccess('工单已完成，告警已解除，复位指令已下发')
          } else {
            this.$modal.msgWarning('工单已完成，但' + (data.resetMessage || '维护复位指令下发失败'))
          }
          this.completeOpen = false
          this.getList()
        })
      })
    },
    handleCancel(row) {
      this.$prompt('请输入取消原因', '取消工单', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入取消原因',
        inputValidator: value => !!(value && value.trim()) || '取消原因不能为空',
        type: 'warning'
      }).then(({ value }) => {
        cancelWorkOrder(row.id, { reason: value.trim() }).then(() => {
          this.$modal.msgSuccess('工单已取消')
          this.getList()
        })
      }).catch(() => {})
    },
    // 打开流转记录弹窗：每次打开重拉，保证记录与后端最新状态一致
    handleFlow(row) {
      this.flowOrderNo = row.orderNo
      this.flowOpen = true
      this.flowLoading = true
      fetchWorkOrderLogs(row.id).then(logs => {
        this.flowList = logs || []
      }).finally(() => {
        this.flowLoading = false
      })
    },
    // 流转动作元信息映射；type 用法与页内既有 statusMeta（含 primary）保持一致
    actionMeta(action) {
      return {
        CREATE: { label: '生成', type: 'info' },
        ASSIGN: { label: '指派', type: 'warning' },
        START: { label: '接单', type: 'primary' },
        COMPLETE: { label: '完成', type: 'success' },
        CANCEL: { label: '取消', type: 'danger' }
      }[action] || { label: action || '-', type: 'info' }
    },
    // 后端 orderType 直接存中文文案（故障维修/预防维护），此处仅映射 tag 颜色
    orderTypeMeta(type) {
      return { '故障维修': { type: 'danger' }, '预防维护': { type: 'warning' } }[type] || { type: 'info' }
    },
    levelMeta(level) {
      return { SEVERE: { label: '严重', type: 'danger' }, WARNING: { label: '预警', type: 'warning' } }[level] || { label: level || '-', type: 'info' }
    },
    statusMeta(status) {
      return {
        PENDING: { label: '待处理', type: 'warning' },
        PROCESSING: { label: '处理中', type: 'primary' },
        COMPLETED: { label: '已完成', type: 'success' },
        CANCELLED: { label: '已取消', type: 'info' }
      }[status] || { label: status || '-', type: 'info' }
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
// 未转派的工单处理人占位：红色高亮提示待转派
.assignee-empty {
  color: #f56c6c;
  font-weight: 600;
}
</style>
