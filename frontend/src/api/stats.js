import request from './request'

/** 分页查日统计（全站维度，跨电站翻页用） */
export function pageStats(params) {
  return request.get('/stats/page', { params })
}

/** 某电站的全部日统计，按日期倒序——看单站发电曲线用 */
export function listStatsByStation(stationId) {
  return request.get(`/stats/station/${stationId}`)
}
