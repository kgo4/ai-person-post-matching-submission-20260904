import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  approveRelation,
  createRelation,
  discoverRelations,
  getUsageStats,
  computeUsageStats,
  pageRelations,
  rejectRelation,
} from '@/api/tag-governance'
import type { AbilityTagUsageStat, AbilityTagRelation } from '@/api/tag-governance'

export function useTagRelations() {
  const statsLoading = ref(false)
  const computingStats = ref(false)
  const usageStats = ref<AbilityTagUsageStat[]>([])

  const relationsLoading = ref(false)
  const relations = ref<AbilityTagRelation[]>([])
  const relationSearch = ref({ status: '', relationType: '' })

  const discoveringRelations = ref(false)
  const createRelationDialogVisible = ref(false)
  const createRelationForm = ref({ sourceTagId: '', targetTagId: '', relationType: 'SIMILAR', remark: '' })

  async function loadStats() {
    statsLoading.value = true
    try {
      // 每次进入健康页先刷新一次快照，避免展示旧的空统计。
      await computeUsageStats()
      const res = await getUsageStats(100)
      usageStats.value = res.data
    } catch {
      ElMessage.error('加载统计数据失败')
    } finally {
      statsLoading.value = false
    }
  }

  async function handleComputeStats() {
    computingStats.value = true
    try {
      await computeUsageStats()
      await loadStats()
      ElMessage.success('统计计算完成')
    } catch {
      ElMessage.error('计算失败')
    } finally {
      computingStats.value = false
    }
  }

  async function loadRelations() {
    relationsLoading.value = true
    try {
      const params: any = { pageNum: 1, pageSize: 100 }
      if (relationSearch.value.status) params.status = relationSearch.value.status
      if (relationSearch.value.relationType) params.relationType = relationSearch.value.relationType
      const res = await pageRelations(params)
      relations.value = res.data.records
    } catch {
      ElMessage.error('加载关系数据失败')
    } finally {
      relationsLoading.value = false
    }
  }

  async function handleDiscoverRelations(threshold: number = 0.5) {
    discoveringRelations.value = true
    try {
      const res = await discoverRelations(threshold)
      ElMessage.success(`发现完成，新增 ${res.data} 条关系（阈值=${threshold}）`)
      await loadRelations()
    } catch {
      ElMessage.error('关系发现失败')
    } finally {
      discoveringRelations.value = false
    }
  }

  async function handleCreateRelation() {
    const { sourceTagId, targetTagId, relationType, remark } = createRelationForm.value
    if (!sourceTagId || !targetTagId) {
      ElMessage.warning('请选择源标签和目标标签')
      return
    }
    if (sourceTagId === targetTagId) {
      ElMessage.warning('源标签和目标标签不能相同')
      return
    }
    try {
      await createRelation({
        sourceTagId: Number(sourceTagId),
        targetTagId: Number(targetTagId),
        relationType,
        remark: remark || undefined,
      })
      ElMessage.success('关系创建成功')
      createRelationDialogVisible.value = false
      createRelationForm.value = { sourceTagId: '', targetTagId: '', relationType: 'SIMILAR', remark: '' }
      await loadRelations()
    } catch {
      ElMessage.error('创建关系失败')
    }
  }

  async function handleApproveRelation(id: number) {
    try {
      await approveRelation(id)
      ElMessage.success('审核通过')
      await loadRelations()
    } catch {
      ElMessage.error('审核失败')
    }
  }

  async function handleRejectRelation(id: number) {
    try {
      await rejectRelation(id)
      ElMessage.success('已拒绝')
      await loadRelations()
    } catch {
      ElMessage.error('操作失败')
    }
  }

  async function handleBatchApproveAll() {
    const pending = relations.value.filter(r => r.status === 'PENDING')
    if (pending.length === 0) {
      ElMessage.info('没有待审核的关系')
      return
    }
    let done = 0
    for (const rel of pending) {
      try {
        await approveRelation(rel.id)
        done++
      } catch {
        // continue
      }
    }
    ElMessage.success(`批量通过完成：${done}/${pending.length}`)
    await loadRelations()
  }

  async function handleBatchRejectAll() {
    const pending = relations.value.filter(r => r.status === 'PENDING')
    if (pending.length === 0) {
      ElMessage.info('没有待审核的关系')
      return
    }
    let done = 0
    for (const rel of pending) {
      try {
        await rejectRelation(rel.id)
        done++
      } catch {
        // continue
      }
    }
    ElMessage.success(`批量拒绝完成：${done}/${pending.length}`)
    await loadRelations()
  }

  return {
    statsLoading,
    computingStats,
    usageStats,
    relationsLoading,
    relations,
    relationSearch,
    discoveringRelations,
    createRelationDialogVisible,
    createRelationForm,
    loadStats,
    handleComputeStats,
    loadRelations,
    handleDiscoverRelations,
    handleCreateRelation,
    handleApproveRelation,
    handleRejectRelation,
    handleBatchApproveAll,
    handleBatchRejectAll,
  }
}
