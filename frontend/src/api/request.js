import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一的 axios 实例：全站请求唯一入口。
 *
 * 前后端约定（见后端 Result<T>）：
 *   code = 0          → 成功，data 是业务数据
 *   code = 40100      → 未登录 / token 过期
 *   code = 40300      → 无权限（角色不匹配）
 *   其他非 0          → 业务失败，message 是给用户看的文案
 *
 * 关键设计：拦截器里直接把 Result 解包，只把 data 返回给页面——
 * 页面代码里永远看不到 code/message 的判断，写起来像在调"本地函数"。
 */
const request = axios.create({
  baseURL: '/api', // vite dev 代理到 8080，生产由 nginx 反代，路径写法两处一致
  timeout: 10000,
})

// ---- 请求拦截器：带上 JWT ----
// token 放 localStorage（Pinia 持久化），拦截器不 import store——
// 循环依赖：store 的 action 会调 api，api 再 import store 就绕成圈了
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ---- 响应拦截器：解包 Result + 全局错误处理 ----
request.interceptors.response.use(
  (response) => {
    // 后端把业务失败也放在 HTTP 200 的 Result 里（统一出口），
    // 所以"HTTP 成功"不代表"业务成功"，必须看 code
    const res = response.data
    if (res.code === 0) {
      return res.data
    }
    if (res.code === 40100) {
      // 未登录/过期：清空用户态回登录页。防重复弹窗：只在非登录页才提示
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (!location.pathname.startsWith('/login')) {
        ElMessage.error('登录已过期，请重新登录')
      }
      location.href = '/login'
      return Promise.reject(new Error(res.message))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    // HTTP 层失败：401 是拦截器用 40100 写回但 HTTP 状态码也可能是 401 的兜底
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      location.href = '/login'
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查后端是否启动')
    } else {
      ElMessage.error(error.response?.data?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export default request
