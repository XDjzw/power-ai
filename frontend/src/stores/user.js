import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as apiLogin } from '@/api/auth'

/**
 * 用户态 store：token + 基本信息 + 角色判断。
 *
 * 持久化设计：token 存 localStorage（拦截器直接读），
 * 用户信息 JSON 也存一份——刷新页面后不用等 /auth/me 就能渲染顶栏。
 * 注意：localStorage 里的 role 只是"显示用"，真正的权限校验在后端
 * （@RequireRole），前端按钮隐藏只是体验优化，不能当安全边界。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref('')
  const realName = ref('')
  const role = ref('')
  const stationId = ref(null)

  // 刷新后从 localStorage 恢复（有 token 但内存里没用户信息的场景）
  const saved = localStorage.getItem('user')
  if (saved) {
    try {
      const u = JSON.parse(saved)
      username.value = u.username || ''
      realName.value = u.realName || ''
      role.value = u.role || ''
      stationId.value = u.stationId ?? null
    } catch {
      localStorage.removeItem('user')
    }
  }

  // 角色判断 getter：模板里 v-if="userStore.isAdmin" 比写 userStore.role === 'ADMIN' 可读
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isManager = computed(() => role.value === 'MANAGER')
  const isOperator = computed(() => role.value === 'OPERATOR')

  async function login(form) {
    const data = await apiLogin(form) // 拦截器已解包，data 就是 LoginResponse
    token.value = data.token
    username.value = data.username
    realName.value = data.realName
    role.value = data.role
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify({ ...data }))
  }

  function logout() {
    token.value = ''
    username.value = ''
    realName.value = ''
    role.value = ''
    stationId.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, username, realName, role, stationId, isAdmin, isManager, isOperator, login, logout }
})
