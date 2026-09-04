import request from '@/utils/request'

// 分页结果规整：兼容 TableDataInfo(rows/total) 与 MyBatis-Plus(records/total) 两种返回结构
function pageResult(res) {
  const data = res.data || res
  return {
    rows: data.records || data.rows || [],
    total: Number(data.total || 0)
  }
}

// 分页查询维保工单（keyword 匹配 编号/设备/传感器）
export function fetchWorkOrderPage(query) {
  const params = {
    page: query.pageNum || 1,
    size: query.pageSize || 10,
    status: query.status || undefined,
    orderType: query.orderType || undefined,
    keyword: query.keyword || undefined
  }
  return request({ url: '/api/work-orders/page', method: 'get', params }).then(pageResult)
}

// 转派工单 data: {handler, handlerName}
export function assignWorkOrder(id, data) {
  return request({ url: `/api/work-orders/${id}/assign`, method: 'post', data })
}

// 完成工单 data: {handleRemark}；后端联动下发维护复位指令，返回 data: {resetSuccess, resetMessage}
export function completeWorkOrder(id, data) {
  return request({ url: `/api/work-orders/${id}/complete`, method: 'post', data })
}

// 取消工单 data: {reason}
export function cancelWorkOrder(id, data) {
  return request({ url: `/api/work-orders/${id}/cancel`, method: 'post', data })
}

// 工单流转记录（时间升序）→ 后端 AjaxResult.data 为列表
export function fetchWorkOrderLogs(id) {
  return request({ url: `/api/work-orders/${id}/logs`, method: 'get' }).then(res => (res && res.data) || res || [])
}
