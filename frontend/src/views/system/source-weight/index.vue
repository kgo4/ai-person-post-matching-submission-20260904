<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { listSourceWeights, batchUpdateSourceWeights } from '@/api'
import type { SourceWeightConfig } from '@/api'

const loading = ref(false)
const tableData = ref<SourceWeightConfig[]>([])
const editingRow = ref<string | null>(null)
const editForm = ref<{ id: number; weight: number }>({ id: 0, weight: 0 })
const totalWeight = computed(() => tableData.value.reduce((sum, row) => sum + Number(row.weight || 0), 0))

/**
 * 展示层统一使用归一化后的有效权重，避免历史配置或后端返回的原始值
 * 在页面上出现合计不为 1 的情况。最后一项吸收四舍五入误差，确保显示值
 * 按两位小数相加仍然严格等于 100.00%。
 */
function normalizeDisplayWeights(rows: SourceWeightConfig[]): SourceWeightConfig[] {
  if (!rows.length) return []
  const sourceTotal = rows.reduce((sum, row) => sum + Math.max(0, Number(row.weight || 0)), 0)
  if (sourceTotal <= 0) {
    const equal = Number((100 / rows.length).toFixed(2))
    return rows.map((row, index) => ({
      ...row,
      weight: index === rows.length - 1 ? Number((100 - equal * (rows.length - 1)).toFixed(2)) : equal,
    }))
  }

  let displayedTotal = 0
  return rows.map((row, index) => {
    if (index === rows.length - 1) {
      return { ...row, weight: Number((100 - displayedTotal).toFixed(2)) }
    }
    const weight = Number((Math.max(0, Number(row.weight || 0)) / sourceTotal * 100).toFixed(2))
    displayedTotal += weight
    return { ...row, weight }
  })
}

const columns = [
  { prop: 'sourceLabel', label: '来源名称', width: '140px' },
  { prop: 'sourceType', label: '来源编码', width: '160px' },
  { prop: 'weight', label: '权重', width: '120px' },
  { prop: 'sortOrder', label: '排序', width: '80px' },
  { prop: 'remark', label: '备注' },
]

async function fetchList() {
  loading.value = true
  try {
    const res = await listSourceWeights()
    tableData.value = normalizeDisplayWeights(res.data || [])
  } finally {
    loading.value = false
  }
}

function startEdit(row: SourceWeightConfig) {
  editingRow.value = row.sourceType
  editForm.value = { id: row.id, weight: row.weight }
}

function cancelEdit() {
  editingRow.value = null
}

async function saveEdit(row: SourceWeightConfig) {
  const newWeight = editForm.value.weight
  if (newWeight < 0 || newWeight > 100) {
    ElMessage.warning('权重值必须在 0 ~ 100% 之间')
    return
  }
  try {
    const res = await batchUpdateSourceWeights([{ id: row.id, weight: newWeight }])
    tableData.value = normalizeDisplayWeights(res.data || [])
    const updated = tableData.value.find(item => item.id === row.id)
    ElMessage.success(`"${row.sourceLabel}" 权重已更新为 ${(updated?.weight ?? newWeight).toFixed(2)}%`)
    editingRow.value = null
  } catch {
    // 错误已由拦截器处理
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="source-weight-page">
    <div class="page-header">
      <h2>
        来源证据权重配置
        <el-tooltip placement="top" content="权重只用于人员评估流程中融合简历、AI测试和AI面试证据，影响最终能力等级确认，不影响岗位匹配分或岗位能力模型权重。">
          <el-icon class="title-help"><InfoFilled /></el-icon>
        </el-tooltip>
      </h2>
      <p class="page-desc">
        仅配置人员评估流程的三类证据。简历解析负责能力声明，AI 测试与 AI 面试负责核验；PMS 项目和手动维护不参与此配置。
      </p>
      <el-alert :type="Math.abs(totalWeight - 100) < 0.001 ? 'success' : 'warning'" :closable="false" style="margin-top: 10px">
        当前可配置来源权重合计：{{ totalWeight.toFixed(2) }}%
      </el-alert>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
          <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
          >
        <template #default="{ row }" v-if="col.prop === 'weight'">
          <template v-if="editingRow === row.sourceType">
            <el-input-number
              v-model="editForm.weight"
              :min="0"
              :max="100"
              :step="1"
              :precision="2"
              size="small"
              controls-position="right"
              style="width: 140px"
            />
          </template>
          <template v-else>
            <el-tooltip placement="top" :content="`该来源在人员评估证据融合中的基础影响系数：${Number(row.weight || 0).toFixed(2)}。保存后系统会统一归一化。`">
              <el-tag :type="row.weight >= 25 ? 'danger' : row.weight >= 15 ? 'warning' : 'info'" effect="plain">
              {{ row.weight.toFixed(2) }}%
              </el-tag>
            </el-tooltip>
          </template>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <template v-if="editingRow === row.sourceType">
            <el-button type="primary" size="small" @click="saveEdit(row)">保存</el-button>
            <el-button size="small" @click="cancelEdit">取消</el-button>
          </template>
          <template v-else>
            <el-button type="primary" size="small" link @click="startEdit(row)">编辑</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.source-weight-page {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: var(--el-text-color-primary);
}

.page-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0;
  line-height: 1.6;
}
</style>
