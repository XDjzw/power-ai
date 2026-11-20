<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 菜单 = 路由配置的单点来源：加一个页面只需要在 router 里加一条 + 这里加一项。
// 暂不做按角色隐藏菜单——四个页面所有人可见，按钮级权限在页面内部用 v-if 控制
const menus = [
  { path: '/station', title: '电站管理', icon: 'OfficeBuilding' },
  { path: '/stats', title: '日统计查询', icon: 'DataLine' },
  { path: '/order', title: '工单管理', icon: 'Tickets' },
  { path: '/assessment', title: '考核结果', icon: 'TrendCharts' },
]

// 菜单高亮跟随当前路由（刷新后不会丢）
const activeMenu = computed(() => route.path)

const roleTagType = { ADMIN: 'danger', MANAGER: 'warning', OPERATOR: '' }
const roleLabel = { ADMIN: '管理员', MANAGER: '电站负责人', OPERATOR: '运营专员' }

async function handleLogout() {
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon :size="22"><Lightning /></el-icon>
        <span>power-ai 运营平台</span>
      </div>
      <el-menu :default-active="activeMenu" router background-color="#001529" text-color="#a6adb4" active-text-color="#fff">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="page-title">{{ route.meta.title }}</div>
        <div class="user-area">
          <el-tag :type="roleTagType[userStore.role]" effect="dark" size="small">
            {{ roleLabel[userStore.role] || userStore.role }}
          </el-tag>
          <span class="user-name">{{ userStore.realName || userStore.username }}</span>
          <el-button link type="danger" @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background-color: #001529;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-weight: 600;
  font-size: 16px;
}

/* 让菜单背景融入侧边栏深色 */
.aside :deep(.el-menu) {
  border-right: none;
}

.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-name {
  color: #303133;
}

.main {
  padding: 16px;
}
</style>
