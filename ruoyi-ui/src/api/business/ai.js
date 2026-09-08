import request from '@/utils/request'

const modelBase = '/api/ai/model'
const serviceBase = '/api/ai/services'

// B5 死代码清理：仅保留有后端对应的模型管理 CRUD 与服务健康检查，
// 其余无后端实现的调用（边缘推理/联邦学习/知识抽取等）已随 ai-service 孤儿页面一并删除。
export function listModels(query) {
  return request({ url: `${modelBase}/page`, method: 'get', params: query, timeout: 30000 }).then(res => {
    const data = res.data || res
    return { rows: data.records || data.rows || [], total: Number(data.total || 0) }
  })
}
export function getModel(id) { return request({ url: `${modelBase}/${id}`, method: 'get' }).then(res => res.data || res) }
export function addModel(data) { return request({ url: modelBase, method: 'post', data }) }
export function updateModel(data) { return request({ url: modelBase, method: 'put', data }) }
export function delModel(id) { return request({ url: `${modelBase}/${id}`, method: 'delete' }) }
export function changeModelStatus(id, status) { return request({ url: `${modelBase}/status/${id}`, method: 'put', params: { status } }) }
export function getModelTypes() { return request({ url: `${modelBase}/types`, method: 'get' }).then(res => res.data || res || []) }
export function checkAiHealth() { return request({ url: `${serviceBase}/health`, method: 'get', timeout: 30000 }) }
