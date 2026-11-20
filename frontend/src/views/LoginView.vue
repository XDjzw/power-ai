<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  // el-form 的 validate：规则全过才走下面，失败直接 return
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.login(form) // 失败时拦截器已弹错误提示，这里只管成功路径
    ElMessage.success(`欢迎，${userStore.realName}`)
    router.push('/')
  } finally {
    loading.value = false
  }
}

/** 演示账号快捷填充：种子数据里三个角色各一个账号，密码都是 123456 */
function fill(username) {
  form.username = username
  form.password = '123456'
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">新能源发电运营平台</h2>
      <p class="subtitle">power-ai 管理台</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>

      <el-divider>演示账号（点击填充）</el-divider>
      <div class="quick-login">
        <el-tag class="quick-tag" type="danger" @click="fill('admin')">admin / 管理员</el-tag>
        <el-tag class="quick-tag" type="warning" @click="fill('manager')">manager / 负责人</el-tag>
        <el-tag class="quick-tag" @click="fill('operator')">operator / 运营</el-tag>
      </div>
      <p class="tip">密码均为 123456</p>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 登录页背景：新能源主题的深蓝渐变 */
  background: linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%);
}

.login-card {
  width: 400px;
  padding: 8px 12px;
}

.title {
  text-align: center;
  color: #303133;
  margin-bottom: 4px;
}

.subtitle {
  text-align: center;
  color: #909399;
  font-size: 13px;
  margin-bottom: 24px;
}

.login-btn {
  width: 100%;
}

.quick-login {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.quick-tag {
  cursor: pointer;
}

.tip {
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 8px;
}
</style>
