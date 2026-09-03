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

const moduleOptions = ['', 'SYSTEM', 'USER', 'ROLE', 'ABILITY_TAG', 'EXTEND_FIELD', 'EMPLOYEE', 'POST', 'MATCHING']

const columns = [
  { prop: 'id', label: 'ID', width: '70px' },
  { prop: 'userId', label: '用户ID', width: '80px' },
  { prop: 'realName', label: '操作人', width: '100px' },
  { prop: 'operationModule', label: '操作模块', width: '120px' },
  { prop: 'operationType', label: '操作类型', width: '100px' },
  { prop: 'operationDesc', label: '操作描述', minWidth: '160px' },
  { prop: 'requestUrl', label: '请求URL', minWidth: '160px' },
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
          <el-option label="系统管理" value="SYSTEM" />
          <el-option label="用户管理" value="USER" />
          <el-option label="角色管理" value="ROLE" />
          <el-option label="能力标签" value="ABILITY_TAG" />
          <el-option label="扩展字段" value="EXTEND_FIELD" />
          <el-option label="员工管理" value="EMPLOYEE" />
          <el-option label="岗位管理" value="POST" />
          <el-option label="匹配管理" value="MATCHING" />
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
          <template v-if="col.prop === 'costTime'" #default="{ row }">
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
