<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { pageLogs } from '@/api'
import type { SysOperationLog } from '@/api'

const loading = ref(false)
const tableData = ref<SysOperationLog[]>([])
const total = ref(0)

const searchForm = reactive({
  operationModule: '',
  userId: '',
  dateRange: [] as string[],
  current: 1,
  size: 10,
})

/**
 * 操作模块码 → 中文名。
 *
 * 口径必须与后端 `common/audit/OperationLogClassifier` 的 MODULE_LABELS 一致：
 * 过去这里写死了 8 个选项（SYSTEM/USER/ROLE/ABILITY_TAG/EXTEND_FIELD/EMPLOYEE/POST/MATCHING），
 * 而切面把模块**写死成小写 `system`**、类型写死成 `UNKNOWN` —— 于是「操作类型」整列显示 UNKNOWN，
 * 模块也永远匹配不上这些大写选项。
 *
 * ⚠️ 这里只用于**展示与筛选**，不作为校验：后端新增模块码时，`moduleLabel()` 会原样透出英文码，
 * 不会变成空白或 UNKNOWN。
 */
const MODULE_LABELS: Record<string, string> = {
  SYSTEM: '系统管理',
  USER: '用户管理',
  ROLE: '角色权限',
  ABILITY_TAG: '能力标签',
  EXTEND_FIELD: '扩展字段',
  EMPLOYEE: '人员管理',
  POST: '岗位管理',
  MATCHING: '人岗匹配',
  LEARNING: '学习成长',
  RAG: '知识资产',
  KG: '知识图谱',
  AI_TEST: 'AI 测试',
  GOVERNANCE: 'AI 治理',
  CRAWLER: '数据采集',
  NOTIFICATION: '通知面试',
  DLQ: '死信队列',
  OUTBOX: '事件发件箱',
  SCHEDULER: '定时任务',
  OTHER: '其他',
}

/** 操作类型码 → 中文名；UNKNOWN 是历史行遗留值，展示为「其他」。 */
const TYPE_LABELS: Record<string, string> = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '删除',
  QUERY: '查询',
  EXECUTE: '执行',
  ALERT: '告警',
  UNKNOWN: '其他',
}

const TYPE_TAG_TYPES: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  CREATE: 'success',
  UPDATE: 'primary',
  DELETE: 'danger',
  QUERY: 'info',
  EXECUTE: 'info',
  ALERT: 'warning',
  UNKNOWN: 'info',
}

function normalizeCode(value?: string) {
  return (value || '').trim().toUpperCase()
}

function moduleLabel(value?: string) {
  const code = normalizeCode(value)
  if (!code) return '—'
  // 未知模块码原样显示，不吞掉信息（后端新增模块时前端不会先坏掉）
  return MODULE_LABELS[code] ?? value ?? code
}

function typeLabel(value?: string) {
  const code = normalizeCode(value)
  if (!code) return '—'
  return TYPE_LABELS[code] ?? value ?? code
}

function typeTagType(value?: string) {
  return TYPE_TAG_TYPES[normalizeCode(value)] ?? 'info'
}

const columns = [
  { prop: 'id', label: 'ID', width: '70px' },
  { prop: 'userId', label: '用户ID', width: '80px' },
  { prop: 'realName', label: '操作人', width: '110px' },
  { prop: 'operationModule', label: '操作模块', width: '110px' },
  { prop: 'operationType', label: '操作类型', width: '90px' },
  { prop: 'operationDesc', label: '操作描述', minWidth: '220px' },
  { prop: 'requestUrl', label: '请求URL', minWidth: '200px' },
  { prop: 'operationIp', label: 'IP地址', width: '140px' },
  { prop: 'operationTime', label: '操作时间', width: '170px' },
  { prop: 'costTime', label: '耗时(ms)', width: '90px' },
]

async function fetchList() {
  loading.value = true
  try {
    const params: any = {
      current: searchForm.current,
      size: searchForm.size,
    }
    if (searchForm.operationModule) {
      params.operationModule = searchForm.operationModule
    }
    if (searchForm.userId) {
      params.userId = searchForm.userId
    }
    if (searchForm.dateRange && searchForm.dateRange.length === 2) {
      params.startTime = searchForm.dateRange[0]
      params.endTime = searchForm.dateRange[1]
    }
    const res = await pageLogs(params)
    tableData.value = res.data.records
    total.value = res.data.total
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  searchForm.current = 1
  fetchList()
}

function handleReset() {
  searchForm.operationModule = ''
  searchForm.userId = ''
  searchForm.dateRange = []
  handleSearch()
}

function handleSizeChange(size: number) {
  searchForm.size = size
  fetchList()
}

function handleCurrentChange(current: number) {
  searchForm.current = current
  fetchList()
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <span>操作日志</span>
      </template>

      <!-- 筛选栏 -->
      <div class="search-bar">
        <el-select
          v-model="searchForm.operationModule"
          placeholder="操作模块"
          clearable
          style="width: 160px;"
        >
          <el-option
            v-for="(label, code) in MODULE_LABELS"
            :key="code"
            :label="label"
            :value="code"
          />
        </el-select>

        <el-input
          v-model="searchForm.userId"
          placeholder="用户ID"
          clearable
          style="width: 140px;"
          @keyup.enter="handleSearch"
        />

        <el-date-picker
          v-model="searchForm.dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 380px;"
        />

        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column
          v-for="col in columns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :min-width="col.minWidth"
          show-overflow-tooltip
        >
          <template v-if="col.prop === 'operationModule'" #default="{ row }">
            {{ moduleLabel(row.operationModule) }}
          </template>
          <template v-else-if="col.prop === 'operationType'" #default="{ row }">
            <el-tag :type="typeTagType(row.operationType)" size="small" disable-transitions>
              {{ typeLabel(row.operationType) }}
            </el-tag>
          </template>
          <template v-else-if="col.prop === 'costTime'" #default="{ row }">
            {{ row.costTime }}ms
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="searchForm.current"
          v-model:page-size="searchForm.size"
          :page-sizes="[10, 15, 20, 50]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
