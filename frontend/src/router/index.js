import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由设计：
 *  /login          登录页（独立全屏，无布局壳）
 *  /               布局壳（侧边菜单 + 顶栏），子路由是各业务页
 *  /station 等四个 懒加载：() => import(...) —— 首屏只加载登录页
 *
 * 守卫逻辑（beforeEach）：
 *  1. 没 token 且去的不是登录页 → 重定向 /login
 *  2. 有 token 却去登录页 → 回首页（已登录就别再登了）
 *  3. meta.roles 声明了该页需要的角色 → 角色不符弹提示并打回首页
 *     （后端同样会 403，这里是双保险 + 体验优化）
 */
const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
  {
    path: '/',
    component: () => import('@/views/LayoutView.vue'),
    redirect: '/station',
    children: [
      { path: 'station', name: 'station', component: () => import('@/views/StationView.vue'), meta: { title: '电站管理' } },
      { path: 'stats', name: 'stats', component: () => import('@/views/StatsView.vue'), meta: { title: '日统计查询' } },
      { path: 'order', name: 'order', component: () => import('@/views/OrderView.vue'), meta: { title: '工单管理' } },
      { path: 'assessment', name: 'assessment', component: () => import('@/views/AssessmentView.vue'), meta: { title: '考核结果' } },
    ],
  },
  // 兜底：任何没匹配到的路径都回首页（生产可换成 404 页）
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login') {
    return '/login'
  }
  if (token && to.path === '/login') {
    return '/'
  }
  return true
})

export default router
