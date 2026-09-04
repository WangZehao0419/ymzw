import request from '@/utils/request'

function pageResult(res, pageNum, pageSize) {
  const data = res.data || res
  return {
    rows: data.records || data.rows || [],
    total: Number(data.total || 0),
    pageNum: Number(data.current || data.page || pageNum || 1),
    pageSize: Number(data.size || data.pageSize || pageSize || 10)
  }
}

export function listEquipment(query) {
  const params = {
    equipmentName: query.equipmentName || undefined,
    equipmentNo: query.equipmentNo || undefined,
    equipmentStatus: query.equipmentStatus === '' ? undefined : query.equipmentStatus,
    workshopId: query.workshopId || undefined,
    page: query.pageNum || 1,
    pageSize: query.pageSize || 10
  }
  return request({ url: '/api/equipment/page', method: 'get', params }).then(res =>
    pageResult(res, params.page, params.pageSize)
  )
}

export function getEquipment(id) {
  return request({ url: `/api/equipment/${id}`, method: 'get' }).then(res => res.data || res)
}

export function addEquipment(data) {
  return request({ url: '/api/equipment', method: 'post', data })
}

export function updateEquipment(data) {
  return request({ url: '/api/equipment', method: 'put', data })
}

export function delEquipment(id) {
  return request({ url: `/api/equipment/${id}`, method: 'delete' })
}

export function changeEquipmentStatus(id, status) {
  return request({
    url: `/api/equipment/status/${id}`,
    method: 'put',
    params: { status }
  })
}

export function listWorkshop(query) {
  const params = {
    workshopName: query.workshopName || undefined,
    workshopStatus: query.workshopStatus === '' ? undefined : query.workshopStatus,
    page: query.pageNum || 1,
    pageSize: query.pageSize || 10
  }
  return request({ url: '/api/equipment/workshop/page', method: 'get', params }).then(res =>
    pageResult(res, params.page, params.pageSize)
  )
}

export function getWorkshop(id) {
  return request({ url: `/api/equipment/workshop/${id}`, method: 'get' }).then(res => res.data || res)
}

export function addWorkshop(data) {
  return request({ url: '/api/equipment/workshop', method: 'post', data })
}

export function updateWorkshop(data) {
  return request({ url: '/api/equipment/workshop', method: 'put', data })
}

export function delWorkshop(id) {
  return request({ url: `/api/equipment/workshop/${id}`, method: 'delete' })
}

// 批量保存车间设备孪生布局（layoutX/layoutY 为 null 表示移回清单）
export function saveWorkshopLayout(workshopId, data) {
  return request({ url: `/api/equipment/workshop/${workshopId}/layout`, method: 'put', data })
}

export function listSensor(query) {
  const params = {
    sensorName: query.sensorName || undefined,
    sensorCode: query.sensorCode || undefined,
    equipmentId: query.equipmentId || undefined,
    sensorStatus: query.sensorStatus === '' ? undefined : query.sensorStatus,
    page: query.pageNum || 1,
    pageSize: query.pageSize || 10
  }
  return request({ url: '/api/equipment/sensor/page', method: 'get', params }).then(res =>
    pageResult(res, params.page, params.pageSize)
  )
}

export function addSensor(data) {
  return request({ url: '/api/equipment/sensor', method: 'post', data })
}

export function updateSensor(data) {
  return request({ url: '/api/equipment/sensor', method: 'put', data })
}

export function delSensor(id) {
  return request({ url: `/api/equipment/sensor/${id}`, method: 'delete' })
}

export function changeSensorStatus(id, status) {
  return request({
    url: `/api/equipment/sensor/status/${id}`,
    method: 'put',
    params: { status }
  })
}

export function listParts(query) {
  return request({
    url: '/api/part/inspection/page',
    method: 'get',
    params: {
      inspectionFlag: query.inspectionFlag,
      isQualified: query.isQualified === '' ? undefined : query.isQualified,
      current: query.pageNum || 1,
      size: query.pageSize || 10
    },
    timeout: 30000
  }).then(res => {
    const result = pageResult(res, query.pageNum, query.pageSize)
    const keyword = (query.keyword || '').trim()
    if (keyword) {
      result.rows = result.rows.filter(item =>
        String(item.partName || '').includes(keyword) || String(item.partCode || '').includes(keyword)
      )
      result.total = result.rows.length
    }
    return result
  })
}

export function inspectPart(data) {
  return request({
    url: '/api/part/inspection/ai-inspect',
    method: 'post',
    data,
    timeout: 90000
  })
}

// 查询传感器监测历史数据（分页，按时间倒序返回，返回结构 { rows, total }）
export function fetchMonitorHistory(params) {
  return request({
    url: '/api/equipment/monitor/history',
    method: 'get',
    params: {
      sensorId: params.sensorId || undefined,
      equipmentId: params.equipmentId || undefined,
      page: params.page || 1,
      pageSize: params.pageSize || 10,
      startTime: params.startTime || undefined,
      endTime: params.endTime || undefined
    }
  })
}
