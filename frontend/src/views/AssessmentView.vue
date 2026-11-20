<script setup>
import { onMounted, ref } from 'vue'
import { listAssessment } from '@/api/assessment'

/**
 * 考核结果——0-5 跑批的读出口。
 * 数据凌晨 1 点由 MonthAssessmentJob 生成，这里按月查询展示。
 * 考核逻辑一句话：发电进度 < 时间进度 × 0.9 → 欠进度（BEHIND）
 */
const month = ref('')
const rows = ref([])
const loading = ref(false)
const todayMonth = new Date().toISOString().slice(0, 7) // 默认看本月

async function load() {
  if (!month.value) return
  loading.value = true
  try {
    rows.value = await listAssessment(month.value)
  } finally {
    loading.value = false
  }
}

/** 0~1 的小数进度转百分比展示 */
function percent(v) {
  return v == null ? '-' : (Number(v) * 100).toFixed(1) + '%'
}

function statusTag(status) {
  return status === 'BEHIND' ? 'danger' : 'success'
}

function statusLabel(status) {
  return status === 'BEHIND' ? '欠进度' : '正常'
}

onMounted(() => {
  month.value = todayMonth
  load()
})
</script>

<template>
  <el-card>
    <div class="toolbar">
      <el-date-picker
        v-model="month"
        type="month"
        value-format="YYYY-MM"
        placeholder="选择月份"
        :clearable="false"
        @change="load"
      />
      <span class="hint">跑批每天凌晨 1 点执行，考核窗口为月初~昨天</span>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="stationNo" label="电站编号" width="110" />
      <el-table-column prop="stationName" label="电站名称" min-width="150" />
      <el-table-column prop="monthPlanPower" label="月计划(kWh)" width="140" align="right" />
      <el-table-column prop="actualGenPower" label="实际发电(kWh)" width="140" align="right" />
      <el-table-column label="时间进度" width="100" align="right">
        <template #default="{ row }">{{ percent(row.timeProgress) }}</template>
      </el-table-column>
      <el-table-column label="发电进度" min-width="200">
        <template #default="{ row }">
          <!-- 进度条：百分比是发电进度，红色是欠进度（< 时间进度×0.9） -->
          <el-progress
            :percentage="Math.min(Number(row.genProgress) * 100, 100)"
            :status="row.assessStatus === 'BEHIND' ? 'exception' : undefined"
            :format="() => percent(row.genProgress)"
          />
        </template>
      </el-table-column>
      <el-table-column label="考核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.assessStatus)">{{ statusLabel(row.assessStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="assessedAt" label="考核时间" width="160" />
    </el-table>

    <el-empty v-if="!loading && !rows.length" description="该月暂无考核数据（可能月初未到考核起始日，或跑批未执行）" />
  </el-card>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.hint {
  color: #909399;
  font-size: 13px;
}
</style>
