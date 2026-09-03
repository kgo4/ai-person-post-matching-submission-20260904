<template>
  <div class="snapshot-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>图谱快照</span>
          <el-button type="primary" @click="handleCreateSnapshot" :loading="creating">
            创建快照
          </el-button>
        </div>
      </template>

      <el-table :data="snapshotList" v-loading="loading" stripe>
        <el-table-column prop="snapshotCode" label="快照编码" width="200" />
        <el-table-column prop="snapshotName" label="快照名称" />
        <el-table-column prop="snapshotType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ typeLabels[row.snapshotType] || row.snapshotType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nodeCount" label="节点数" width="100" />
        <el-table-column prop="edgeCount" label="边数" width="100" />
        <el-table-column prop="createdTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">查看</el-button>
            <el-button type="success" link @click="handleDownload(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 快照详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="快照详情" size="600px">
      <template v-if="selectedSnapshot">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="快照编码">{{ selectedSnapshot.snapshotCode }}</el-descriptions-item>
          <el-descriptions-item label="快照名称">{{ selectedSnapshot.snapshotName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ selectedSnapshot.snapshotType }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ selectedSnapshot.createdTime }}</el-descriptions-item>
          <el-descriptions-item label="节点数">{{ selectedSnapshot.nodeCount }}</el-descriptions-item>
          <el-descriptions-item label="边数">{{ selectedSnapshot.edgeCount }}</el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h4>快照数据预览</h4>
        <el-input
          type="textarea"
          :rows="15"
          :model-value="previewJson"
          readonly
        />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageSnapshots, createSnapshot, getPanorama } from '@/api/kg'
import type { GraphSnapshot } from '@/api/kg'

const loading = ref(false)
const creating = ref(false)
const drawerVisible = ref(false)
const snapshotList = ref<GraphSnapshot[]>([])
const selectedSnapshot = ref<GraphSnapshot | null>(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const typeLabels: Record<string, string> = {
  FULL: '全景快照',
  POST: '岗位快照',
  EMPLOYEE: '员工快照',
  CONTEST_DEMO: '能力图谱'
}

const previewJson = computed(() => {
  if (!selectedSnapshot.value?.snapshotJson) return ''
  try {
    return JSON.stringify(JSON.parse(selectedSnapshot.value.snapshotJson), null, 2)
  } catch {
    return selectedSnapshot.value.snapshotJson
  }
})

const fetchSnapshots = async () => {
  loading.value = true
  try {
    const res = await pageSnapshots({ page: currentPage.value, size: pageSize.value })
    if (res.code === 200) {
      snapshotList.value = res.data.records
      total.value = res.data.total
    }
  } catch (error) {
    console.error('获取快照列表失败', error)
  } finally {
    loading.value = false
  }
}

const handleCreateSnapshot = async () => {
  try {
    await ElMessageBox.confirm('确定要基于当前图谱创建快照吗？', '确认')
    creating.value = true

    const graphRes = await getPanorama()
    const res = await createSnapshot({
      snapshotType: 'FULL',
      snapshotName: `全景快照-${new Date().toLocaleString()}`,
      graphJson: JSON.stringify(graphRes.data)
    })
    if (res.code === 200) {
      ElMessage.success('快照创建成功')
      await fetchSnapshots()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('快照创建失败')
    }
  } finally {
    creating.value = false
  }
}

const handleView = (row: GraphSnapshot) => {
  selectedSnapshot.value = row
  drawerVisible.value = true
}

const handleDownload = (row: GraphSnapshot) => {
  const blob = new Blob([row.snapshotJson], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${row.snapshotCode}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('下载成功')
}

const handleSizeChange = () => {
  currentPage.value = 1
  fetchSnapshots()
}

const handleCurrentChange = () => {
  fetchSnapshots()
}

onMounted(() => {
  fetchSnapshots()
})
</script>

<style scoped lang="scss">
.snapshot-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
