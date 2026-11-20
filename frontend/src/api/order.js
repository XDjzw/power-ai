import request from './request'

/** 分页查工单，支持 stationId / status 筛选 */
export function pageOrders(params) {
  return request.get('/order/page', { params })
}

/** 创建工单（仅 OPERATOR） */
export function createOrder(data) {
  return request.post('/order', data)
}

/** 接单（仅 MANAGER）。handlerName 走 query 参数——动作端点，状态流转不进 body */
export function acceptOrder(id, handlerName) {
  return request.put(`/order/${id}/accept`, null, { params: { handlerName } })
}

/** 完工（仅 MANAGER） */
export function finishOrder(id, remark) {
  return request.put(`/order/${id}/finish`, null, { params: { remark } })
}
