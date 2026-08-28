import request from '@/utils/request'

const modelBase = '/api/ai/model'
const serviceBase = '/api/ai/services'

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
export function getAiDevices() { return request({ url: `${serviceBase}/devices`, method: 'get', timeout: 30000 }).then(res => res.data || res || []) }
export function predictAllDevices() { return request({ url: `${serviceBase}/predict/all-devices`, method: 'get', timeout: 60000 }).then(res => res.data || res || []) }
export function predictRul(deviceId, hours) { return request({ url: `${serviceBase}/predict/rul/${deviceId}`, method: 'get', params: { hours: hours || 100 }, timeout: 60000 }).then(res => res.data || res) }
export function getEdgeNodes() { return request({ url: `${serviceBase}/edge/nodes`, method: 'get', timeout: 30000 }).then(res => res.data || res || []) }
export function runEdgeInference(data) { return request({ url: `${serviceBase}/edge/inference`, method: 'post', data, timeout: 60000 }).then(res => res.data || res) }
export function simulateFederated(data) { return request({ url: `${serviceBase}/federated/simulate`, method: 'post', data, timeout: 120000 }).then(res => res.data || res) }
export function extractKnowledge(data) { return request({ url: `${serviceBase}/knowledge/extract`, method: 'post', data, timeout: 60000 }).then(res => res.data || res) }
export function diagnose(data) { return request({ url: `${serviceBase}/react/diagnose`, method: 'post', data, timeout: 60000 }).then(res => res.data || res) }
