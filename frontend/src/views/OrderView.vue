<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { acceptOrder, createOrder, finishOrder, pageOrders } from '@/api/order'
import { pageStations } from '@/api/station'
import { useUserStore } from '@/stores/user'

/**
 * 工单管理——按角色分工的典型页面：
 *   OPERATOR（运营）  → 只有"新建工单"按钮
 *   MANAGER（负责人） → 只有行内"接单/完工"动作按钮
 *   ADMIN             → 只读（能看，不动手）
 * 按钮之外，状态机本身在后端（accept 只允许 WAITING→PROCESSING 等），
 * 前端只是按 status 决定显示哪个按钮
 */
const userStore = useUserStore()

const stations = ref([])
const query = reactive({ current: 1, size: 10, total: 0, stationId: null, status: null })
const list = ref([])
const loading = ref(false)

const faultLabel = { PITCH: '变桨系统', GEARBOX: '齿轮箱', INVERTER: '逆变器', LINE: '集电线路', OTHER: '其他' }
const statusLabel = { WAITING: '待接单', PROCESSING: '处理中', DONE: '已完成', TIMEOUT: '已超时', ESCALATED: '已升级' }
const statusTag = { WAITING: 'danger', PROCESSING: 'warning', DONE: 'success', TIMEOUT: 'info', ESCALATED: 'info' }

async function loadStations() {
  const page = await pageStations({ current: 1, size: 100 })
  stations.value = page.records
}

async function load() {
  loading.value = true
  try {
    const page = await pageOrders({
      current: query.current,
      size: query.size,
      stationId: query.stationId ?? undefined,
      status: query.status ?? undefined,
    })
    list.value = page.records
    query.total = Number(page.total) // Long 序列化为字符串，转数字给 el-pagination
  } finally {
    loading.value = false
  }
}

function stationName(id) {
  const s = stations.value.find((x) => x.id === id)
  return s ? `${s.stationNo} ${s.name}` : id
}

// ---- 新建工单（OPERATOR） ----
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const emptyForm = () => ({ stationId: null, deviceName: '', faultType: 'OTHER', description: '' })
const form = reactive(emptyForm())

const rules = {
  stationId: [{ required: true, message: '请选择电站', trigger: 'change' }],
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

async function handleCreate() {
  await formRef.value.validate()
  saving.value = true
  try {
    const order = await createOrder(form) // 返回带 orderNo 的完整实体（单号是后端生成的）
    ElMessage.success(`工单 ${order.orderNo} 创建成功`)
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---- 接单/完工（MANAGER） ----
// ElMessageBox.prompt 弹输入框：接单要填处理人，完工要填备注
async function handleAccept(row) {
  const { value } = await ElMessageBox.prompt('请输入处理人姓名', '接单', {
    inputValue: userStore.realName, // 默认填自己，改起来方便
    inputValidator: (v) => (v && v.trim() ? true : '处理人不能为空'),
  })
  await acceptOrder(row.id, value)
  ElMessage.success('接单成功')
  load()
}

async function handleFinish(row) {
  const { value } = await ElMessageBox.prompt('请填写完工备注', '完工', {
    inputValidator: (v) => (v && v.trim() ? true : '备注不能为空'),
  })
  await finishOrder(row.id, value)
  ElMessage.success('工单已完工')
  load()
}

onMounted(() => {
  loadStations()
  load()
})
</script>

<template>
  <el-card>
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="query.stationId" placeholder="全部电站" clearable style="width: 220px" @change="load">
          <el-option v-for="s in stations" :key="s.id" :label="`${s.stationNo} ${s.name}`" :value="s.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option v-for="(label, key) in statusLabel" :key="key" :label="label" :value="key" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button v-if="userStore.isOperator" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>&nbsp;新建工单
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="orderNo" label="工单号" width="170" />
      <el-table-column label="电站" min-width="150">
        <template #default="{ row }">{{ stationName(row.stationId) }}</template>
      </el-table-column>
      <el-table-column prop="deviceName" label="设备" min-width="130" />
      <el-table-column label="故障类型" width="100">
        <template #default="{ row }">{{ faultLabel[row.faultType] || row.faultType }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag[row.status]">{{ statusLabel[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="故障描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="handlerName" label="处理人" width="90" />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <!-- 动作列：只有负责人可见；按钮随状态流转变化（状态机的前端投影） -->
      <el-table-column v-if="userStore.isManager" label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'WAITING'" link type="primary" @click="handleAccept(row)">接单</el-button>
          <el-button v-if="row.status === 'PROCESSING'" link type="success" @click="handleFinish(row)">完工</el-button>
          <span v-if="['DONE', 'TIMEOUT', 'ESCALATED'].includes(row.status)" class="done">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="query.total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pager"
      @current-change="load"
      @size-change="load"
    />

    <el-dialog v-model="dialogVisible" title="新建工单" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="电站" prop="stationId">
          <el-select v-model="form.stationId" style="width: 100%">
            <el-option v-for="s in stations" :key="s.id" :label="`${s.stationNo} ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="form.deviceName" placeholder="如 7号风机齿轮箱" />
        </el-form-item>
        <el-form-item label="故障类型">
          <el-select v-model="form.faultType">
            <el-option v-for="(label, key) in faultLabel" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.filters {
  display: flex;
  gap: 8px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.done {
  color: #c0c4cc;
}
</style>
