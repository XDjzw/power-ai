import request from './request'

/** 分页查电站。size 传大值可当"全量下拉数据源"用 */
export function pageStations(params) {
  return request.get('/station/page', { params })
}

export function createStation(data) {
  return request.post('/station', data)
}

export function updateStation(id, data) {
  return request.put(`/station/${id}`, data)
}

export function deleteStation(id) {
  return request.delete(`/station/${id}`)
}
