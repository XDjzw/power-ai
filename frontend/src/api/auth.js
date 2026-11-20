import request from './request'

/** 登录：POST /api/auth/login → { token, username, realName, role } */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 当前登录人（UserContext 的 HTTP 出口），刷新页面时校验 token 用 */
export function getMe() {
  return request.get('/auth/me')
}
