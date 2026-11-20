<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createStation, deleteStation, pageStations, updateStation } from '@/api/station'
import { useUserStore } from '@/stores/user'

/**
 * 电站管理——CRUD 示范页。
 * 后面三个页面（日统计/工单/考核）都是这个骨架的变体：
 *   1. reactive 查询条件 + 分页对象
 *   2. load() 拉数据填表格
 *   3. 新增/编辑共用一个 dialog（form.id 有没有值区分两种模式）
 *   4. 删除必须 ElMessageBox.confirm 二次确认
 * 唯一的新东西是"角色按钮"：v-if="userStore.isAdmin"——
 * 后端同样有 @RequireRole(ADMIN) 兜底，前端隐藏只是体验优化
 */
const userStore = useUserStore()

const loading = ref(false)
const list = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })

// 枚举的中文映射：后端存的是英文枚举名（WIND/ONLINE），展示必须翻译。
// 真实项目这些字典要么后端下发、要么放公共常量，教学项目就地映射
const typeLabel = { WIND: '风电场', SOLAR: '光伏电站' }
const typeTag = { WIND: 'success', SOLAR: 'warning' }
const statusLabel = { ONLINE: '并网运行', BUILDING: '在建', STOPPED: '停运' }
const statusTag = { ONLINE: 'success', BUILDING: 'warning', STOPPED: 'info' }

async function load() {
  loading.value = true
  try {
    // 后端 Page<T> 的 JSON 结构：{ records, total, current, size }
    // total 是 Long → 后端统一序列化成字符串（雪花 ID 精度坑），el-pagination 要数字，Number() 转回
    const page = await pageStations({ current: pagination.current, size: pagination.size })
    list.value = page.records
    pagination.total = Number(page.total)
  } finally {
    loading.value = false
  }
}

// ---- 新增/编辑对话框 ----
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref()
// 空表单模板：每次打开对话框都从它复制一份，避免上次编辑的残留
const emptyForm = () => ({
  id: null,
  stationNo: '',
  name: '',
  stationType: 'WIND',
  capacityKw: null,
  regionCompany: '',
  managerName: '',
  gridConnectDate: '',
  status: 'ONLINE',
})
const form = reactive(emptyForm())

const rules = {
  stationNo: [{ required: true, message: '请输入电站编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入电站名称', trigger: 'blur' }],
  capacityKw: [{ required: true, message: '请输入装机容量', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, emptyForm()) // form 是 reactive，直接赋值整个对象会丢响应性，要逐字段覆盖
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, emptyForm(), row) // 先清残留再覆盖行数据，row 里多余的字段（createdAt 等）不影响
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateStation(form.id, form)
      ElMessage.success('修改成功')
    } else {
      await createStation(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除电站「${row.name}」吗？`, '警告', { type: 'warning' })
  await deleteStation(row.id)
  ElMessage.success('删除成功')
  // 当前页删空后回退一页，避免停在空页
  if (list.value.length === 1 && pagination.current > 1) {
    pagination.current--
  }
  load()
}

onMounted(load)
</script>

<template>
  <el-card>
    <div class="toolbar">
      <span class="hint">电站台账（0-3 数据源）</span>
      <el-button v-if="userStore.isAdmin" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>&nbsp;新增电站
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="stationNo" label="电站编号" width="110" />
      <el-table-column prop="name" label="电站名称" min-width="150" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="typeTag[row.stationType]">{{ typeLabel[row.stationType] || row.stationType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="capacityKw" label="装机容量(kW)" width="120" align="right" />
      <el-table-column prop="regionCompany" label="区域公司" min-width="130" />
      <el-table-column prop="managerName" label="负责人" width="90" />
      <el-table-column prop="gridConnectDate" label="并网日期" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag[row.status]">{{ statusLabel[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <!-- 操作列：非管理员连按钮都看不到（后端还有 403 双保险） -->
      <el-table-column v-if="userStore.isAdmin" label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="pagination.total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pager"
      @current-change="load"
      @size-change="load"
    />

    <!-- 新增/编辑共用对话框：靠 form.id 区分 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑电站' : '新增电站'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="电站编号" prop="stationNo">
          <el-input v-model="form.stationNo" placeholder="如 WF001" />
        </el-form-item>
        <el-form-item label="电站名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.stationType">
            <el-radio value="WIND">风电场</el-radio>
            <el-radio value="SOLAR">光伏电站</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="装机容量(kW)" prop="capacityKw">
          <el-input-number v-model="form.capacityKw" :min="0" :step="1000" style="width: 200px" />
        </el-form-item>
        <el-form-item label="区域公司">
          <el-input v-model="form.regionCompany" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.managerName" />
        </el-form-item>
        <el-form-item label="并网日期">
          <el-date-picker v-model="form.gridConnectDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="并网运行" value="ONLINE" />
            <el-option label="在建" value="BUILDING" />
            <el-option label="停运" value="STOPPED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
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

.hint {
  color: #909399;
  font-size: 13px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
