import request from '@/utils/request'

// 分页结果规整：兼容 TableDataInfo(rows/total) 与 MyBatis-Plus(records/total) 两种返回结构
function pageResult(res) {
  const data = res.data || res
  return {
    rows: data.records || data.rows || [],
    total: Number(data.total || 0)
  }
}

// 传感器预测状态列表（选择器 + 状态总览卡片）
export function listPredictSensors() {
  return request({ url: '/api/predict/sensors', method: 'get' }).then(res => res.data || res)
}

// 单传感器详情：原始窗口 + 平滑序列 + 趋势外推（阈值线/预测带/t1）
export function fetchPredictDetail(sensorCode, window) {
  return request({
    url: `/api/predict/detail/${sensorCode}`,
    method: 'get',
    params: window ? { window } : undefined
  }).then(res => res.data || res)
}

// 预测告警列表（独立 predict_alert 表分页，天然仅含 PREDICT 数据，无需再传 alertType）
export function fetchPredictAlerts(query) {
  const params = {
    page: query.pageNum || 1,
    size: query.pageSize || 10,
    alertStatus: query.alertStatus || undefined
  }
  return request({ url: '/api/predict/alerts', method: 'get', params }).then(pageResult)
}
