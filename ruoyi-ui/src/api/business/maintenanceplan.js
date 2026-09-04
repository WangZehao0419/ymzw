import request from '@/utils/request'

// 分页结果规整：兼容 TableDataInfo(rows/total) 与 MyBatis-Plus(records/total) 两种返回结构
function pageResult(res) {
  const data = res.data || res
  return {
    rows: data.records || data.rows || [],
    total: Number(data.total || 0)
  }
}

// 分页查询维护计划（keyword 匹配 编号/名称/设备）
export function fetchMaintenancePlanPage(query) {
  const params = {
    page: query.pageNum || 1,
    size: query.pageSize || 10,
    status: query.status || undefined,
    repeatType: query.repeatType || undefined,
    keyword: query.keyword || undefined
  }
  return request({ url: '/api/maintenance-plans/page', method: 'get', params }).then(pageResult)
}

// 新建维护计划；返回含 nextFireTime 的计划实体 → 后端 AjaxResult.data 与裸对象两种结构兼容
export function addMaintenancePlan(data) {
  return request({ url: '/api/maintenance-plans', method: 'post', data }).then(res => (res && res.data) || res)
}

// 更新维护计划（body 同实体字段）
export function updateMaintenancePlan(id, data) {
  return request({ url: `/api/maintenance-plans/${id}`, method: 'put', data })
}

// 暂停计划：暂停期间不再触发
export function pauseMaintenancePlan(id) {
  return request({ url: `/api/maintenance-plans/${id}/pause`, method: 'post' })
}

// 恢复计划：恢复后按 nextFireTime 继续调度
export function resumeMaintenancePlan(id) {
  return request({ url: `/api/maintenance-plans/${id}/resume`, method: 'post' })
}

// 删除计划
export function delMaintenancePlan(id) {
  return request({ url: `/api/maintenance-plans/${id}`, method: 'delete' })
}
