<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listStatsByStation } from '@/api/stats'
import { pageStations } from '@/api/station'

/**
 * 日统计查询——只读页面，比电站管理少一套增删改。
 * 交互：先选电站，再展示该电站全部日统计（按日期倒序）。
 * 数据量：每站每天一条，一年 365 条，前端一次拿全量不设分页（教学场景够用）；
 * 真实系统应该像电站页那样走后端分页 + 日期范围筛选
 */
const stations = ref([])
const stationId = ref(null)
const rows = ref([])
const loading = ref(false)

async function loadStations() {
  // size=100 拿全量当"下拉数据源"：9 个站的教学数据没问题，
  // 生产环境这个模式会退化，应改用专门的"字典接口"
  const page = await pageStations({ current: 1, size: 100 })
  stations.value = page.records
  if (stations.value.length) {
    stationId.value = stations.value[0].id
    await loadStats()
  }
}

async function loadStats() {
  if (!stationId.value) {
    ElMessage.warning('请先选择电站')
    return
  }
  loading.value = true
  try {
    rows.value = await listStatsByStation(stationId.value)
  } finally {
    loading.value = false
  }
}

// 汇总当前选择电站已加载数据的发电量（列表是倒序，全量数据所以就是该站累计）
const totalGen = computed(() =>
  rows.value.reduce((sum, r) => sum + Number(r.dayGenPower || 0), 0),
)

/** 比率字段数据库存的是小数（0.0521），展示要 ×100 加 % */
function percent(v) {
  return v == null ? '-' : (Number(v) * 100).toFixed(2) + '%'
}

// 进页面先拉电站列表，列表就绪后自动查第一个站的日统计
onMounted(loadStations)
</script>

<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="stationId" placeholder="选择电站" style="width: 260px" @change="loadStats">
        <el-option v-for="s in stations" :key="s.id" :label="`${s.stationNo} ${s.name}`" :value="s.id" />
      </el-select>
      <!-- 显式查询按钮：进入页面时若后端不在线，自动查询会失败且不会重试，
           下拉框选"同一个电站"也不触发 change，必须给用户一个手动重查入口 -->
      <el-button type="primary" @click="loadStats">查询</el-button>
      <span class="summary">累计发电量：<b>{{ totalGen.toLocaleString() }}</b> kWh</span>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe max-height="calc(100vh - 260px)">
      <el-table-column prop="statDate" label="日期" width="120" fixed />
      <el-table-column prop="dayGenPower" label="日发电量(kWh)" width="150" align="right" />
      <el-table-column prop="dayNetPower" label="上网电量(kWh)" width="150" align="right" />
      <el-table-column prop="dayOutputPower" label="厂供电量(kWh)" width="150" align="right" />
      <el-table-column prop="plantConsumption" label="厂用电量(kWh)" width="150" align="right" />
      <el-table-column label="厂用电率" width="110" align="right">
        <template #default="{ row }">{{ percent(row.auxiliaryPowerRate) }}</template>
      </el-table-column>
      <el-table-column prop="curtailmentLoss" label="限电损失(kWh)" width="150" align="right" />
      <el-table-column label="限电比例" width="110" align="right">
        <template #default="{ row }">{{ percent(row.curtailmentRatio) }}</template>
      </el-table-column>
      <el-table-column prop="avgUtilHours" label="利用小时数" width="120" align="right" />
    </el-table>

    <p class="tip">共 {{ rows.length }} 条记录 · 数据由 DataSeeder 生成（0-2 入库，0-3 查询）</p>
  </el-card>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.summary {
  color: #606266;
}

.tip {
  margin-top: 10px;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
