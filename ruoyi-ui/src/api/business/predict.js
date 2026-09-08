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
// 支持传感器名称（后端 like 模糊匹配）/告警状态（eq）筛选
export function fetchPredictAlerts(query) {
  const params = {
    page: query.pageNum || 1,
    size: query.pageSize || 10,
    sensorName: query.sensorName || undefined,
    alertStatus: query.alertStatus || undefined
  }
  return request({ url: '/api/predict/alerts', method: 'get', params }).then(pageResult)
}

// 设备级预测总览（B4 新增：按设备聚合健康分均值/聚合状态/最紧迫 RUL 及对应传感器）
export function fetchPredictOverview() {
  return request({ url: '/api/predict/overview', method: 'get' }).then(res => res.data || res)
}

// AI 诊断（B6 契约：入参 {equipmentId, sensorCode}，返回含 report 字段）
// 诊断含 LLM 调用较慢，超时放宽到 60s（默认 10s 会中断生成）
export function diagnoseSensor(equipmentId, sensorCode) {
  return request({
    url: '/api/ai/agent-service/diagnose',
    method: 'post',
    data: { equipmentId, sensorCode },
    timeout: 60000
  }).then(res => res.data || res)
}

// 历史诊断记录分页（B6 契约：equipmentId/sensorCode 过滤）
// 分页参数名按 B6 已落地的 DiagnosisRecordQuery(page/pageSize) 对齐；
// size 为 predict alerts 口径的兜底，B6 controller 未落地前双发兼容（Spring 绑定忽略未知参数）
export function listDiagnosisRecords(query) {
  const params = {
    page: query.pageNum || 1,
    pageSize: query.pageSize || 10,
    size: query.pageSize || 10,
    equipmentId: query.equipmentId !== undefined && query.equipmentId !== '' ? query.equipmentId : undefined,
    sensorCode: query.sensorCode || undefined
  }
  return request({ url: '/api/ai/agent-service/diagnose/records', method: 'get', params }).then(pageResult)
}
