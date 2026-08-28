import request from '@/utils/request'

// 分页结果规整：兼容 TableDataInfo(rows/total) 与 MyBatis-Plus(records/total) 两种返回结构
function pageResult(res) {
  const data = res.data || res
  return {
    rows: data.records || data.rows || [],
    total: Number(data.total || 0)
  }
}

// 分页查询告警事件（triggerTime 倒序）
export function fetchAlertEvents(query) {
  const params = {
    page: query.pageNum || 1,
    size: query.pageSize || 10,
    sensorCode: query.sensorCode || undefined,
    alertLevel: query.alertLevel || undefined,
    alertStatus: query.alertStatus || undefined
  }
  return request({ url: '/api/alert-events/page', method: 'get', params }).then(pageResult)
}

// 分页查询告警规则
export function listAlertRules(query) {
  const params = {
    page: query.pageNum || 1,
    size: query.pageSize || 10
  }
  return request({ url: '/api/alert-rules/page', method: 'get', params }).then(pageResult)
}

// 新增告警规则
export function addAlertRule(data) {
  return request({ url: '/api/alert-rules', method: 'post', data })
}

// 修改告警规则
export function updateAlertRule(id, data) {
  return request({ url: `/api/alert-rules/${id}`, method: 'put', data })
}

// 删除告警规则
export function deleteAlertRule(id) {
  return request({ url: `/api/alert-rules/${id}`, method: 'delete' })
}
