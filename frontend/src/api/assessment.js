import request from './request'

/** 某月（YYYY-MM）考核结果，带电站编号/名称 */
export function listAssessment(month) {
  return request.get(`/assessment/month/${month}`)
}
