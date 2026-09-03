import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  batchGenerateTagVectors,
  deleteTag,
  getTagById,
  getTagTree,
  getTagTreeByCategory,
  saveTag,
  updateTag,
} from '@/api'
import type { AbilityTag, AbilityTagSaveDTO, AbilityTagTreeVO } from '@/api'
import { searchTags } from '@/utils/tagSearch'
import { countAllNodes, countTreeNodes } from '@/utils/tagTree'
import { countDirectChildren } from '@/views/system/ability-tag/tag-directory-panel'

export const categoryTabs = [
  { label: '全部', value: '' },
  { label: '技术', value: 'TECHNICAL' },
  { label: '软技能', value: 'SOFT' },
  { label: '业务', value: 'BUSINESS' },
]

export function useTagDirectory() {
  const loading = ref(false)
  const treeData = ref<AbilityTagTreeVO[]>([])
  const filterText = ref('')
  const activeCategory = ref('')
  const selectedNode = ref<AbilityTagTreeVO | null>(null)
  const detailLoading = ref(false)
  const isEditMode = ref(false)
  const saveLoading = ref(false)
  const generatingVectors = ref(false)

  const form = reactive<AbilityTagSaveDTO>({
    tagCode: '',
    tagName: '',
    parentId: undefined,
    tagCategory: '',
    tagLevel: 0,
    description: '',
    sortOrder: 0,
  })

  const rules: FormRules = {
    tagName: [{ required: true, message: '请输入标签名称', trigger: 'blur' }],
    tagCategory: [{ required: true, message: '请选择标签分类', trigger: 'change' }],
  }

  const categoryStats = ref({ TECHNICAL: 0, SOFT: 0, BUSINESS: 0 })

  const activeCategoryLabel = computed(() => {
    const match = categoryTabs.find(tab => tab.value === activeCategory.value)
    return match?.label || '全部'
  })

  const searchResults = computed(() => {
    return searchTags(treeData.value, filterText.value.trim())
  })

  const totalTagCount = computed(() => countAllNodes(treeData.value))

  const categoryDistribution = computed(() => {
    const c = categoryStats.value
    return [
      { label: '技术能力', value: c.TECHNICAL, color: '#2563eb' },
      { label: '软技能', value: c.SOFT, color: '#059669' },
      { label: '业务能力', value: c.BUSINESS, color: '#d97706' },
    ]
  })

  const categoryDistributionTotal = computed(() => {
    return categoryDistribution.value.reduce((sum, item) => sum + item.value, 0)
  })

  function getNodeMeta(data: AbilityTagTreeVO): string {
    const childCount = countDirectChildren(data as any)
    if (childCount > 0) return `${childCount} 个子标签`
    return '末级标签'
  }

  function fillFormFromTag(tag: AbilityTag) {
    Object.assign(form, {
      id: tag.id,
      tagCode: tag.tagCode,
      tagName: tag.tagName,
      parentId: tag.parentId,
      tagCategory: tag.tagCategory,
      tagLevel: tag.tagLevel,
      description: tag.description,
      sortOrder: tag.sortOrder,
    })
  }

  async function loadTree(category?: string) {
    loading.value = true
    try {
      if (category) {
        const res = await getTagTreeByCategory(category)
        treeData.value = res.data
      } else {
        const res = await getTagTree()
        treeData.value = res.data
        categoryStats.value = countTreeNodes(treeData.value) as any
      }
    } catch {
      treeData.value = []
    } finally {
      loading.value = false
    }
  }

  function handleCategoryChange(category: string) {
    activeCategory.value = category
    filterText.value = ''
    loadTree(category || undefined)
  }

  async function handleNodeClick(data: AbilityTagTreeVO) {
    selectedNode.value = data
    isEditMode.value = false
    detailLoading.value = true
    try {
      const res = await getTagById(data.id)
      fillFormFromTag(res.data)
    } finally {
      detailLoading.value = false
    }
  }

  async function handleSearchResultSelect(tagId: number) {
    const res = await getTagById(tagId)
    fillFormFromTag(res.data)
    selectedNode.value = {
      id: res.data.id,
      tagCode: res.data.tagCode,
      tagName: res.data.tagName,
      tagCategory: res.data.tagCategory,
      tagLevel: res.data.tagLevel,
      children: [],
    }
    isEditMode.value = false
    filterText.value = ''
  }

  function handleAdd(formInstance?: FormInstance) {
    isEditMode.value = true
    selectedNode.value = null
    Object.assign(form, {
      id: undefined,
      tagCode: '',
      tagName: '',
      parentId: 0,
      tagCategory: activeCategory.value || '',
      tagLevel: 0,
      description: '',
      sortOrder: 0,
    })
    formInstance?.resetFields()
  }

  function handleEdit(formInstance?: FormInstance) {
    if (!form.id) return
    isEditMode.value = true
    formInstance?.resetFields()
  }

  function handleCancelEdit() {
    isEditMode.value = false
    if (selectedNode.value) handleNodeClick(selectedNode.value)
  }

  async function handleSave(formInstance?: FormInstance) {
    if (formInstance) {
      const valid = await formInstance.validate().catch(() => false)
      if (!valid) return
    }

    saveLoading.value = true
    try {
      if (form.id) {
        await updateTag(form.id, form)
        ElMessage.success('编辑成功')
      } else {
        const { tagCode: _tagCode, ...createPayload } = form
        await saveTag(createPayload)
        ElMessage.success('新增成功')
      }
      isEditMode.value = false
      loadTree(activeCategory.value || undefined)
      if (form.id) {
        const res = await getTagById(form.id)
        fillFormFromTag(res.data)
        selectedNode.value = {
          id: res.data.id,
          tagCode: res.data.tagCode,
          tagName: res.data.tagName,
          tagCategory: res.data.tagCategory,
          tagLevel: res.data.tagLevel,
          children: [],
        }
      }
    } finally {
      saveLoading.value = false
    }
  }

  async function handleDelete() {
    if (!form.id) return
    try {
      const cascadeHint = form.tagLevel === 0
        ? '该最高节点及其下属能力域、技能标签都会被停用，但历史引用会保留。'
        : '该标签会被停用，历史引用会保留。'
      await ElMessageBox.confirm(`确定要删除标签「${form.tagName}」吗？\n${cascadeHint}`, '确认删除', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
      await deleteTag(form.id)
      ElMessage.success('删除成功')
      selectedNode.value = null
      Object.assign(form, {
        id: undefined,
        tagCode: '',
        tagName: '',
        parentId: undefined,
        tagCategory: '',
        tagLevel: 0,
        description: '',
        sortOrder: 0,
      })
      loadTree(activeCategory.value || undefined)
    } catch (e: any) {
      if (e !== 'cancel') {
        ElMessage.error(e?.message || '删除失败')
      }
    }
  }

  async function handleGenerateVectors() {
    generatingVectors.value = true
    try {
      const res = await batchGenerateTagVectors()
      ElMessage.success(`向量生成完成，处理了 ${res.data} 个标签`)
    } catch {
      ElMessage.error('向量生成失败')
    } finally {
      generatingVectors.value = false
    }
  }

  return {
    loading,
    treeData,
    filterText,
    activeCategory,
    selectedNode,
    detailLoading,
    isEditMode,
    saveLoading,
    generatingVectors,
    form,
    rules,
    categoryStats,
    activeCategoryLabel,
    searchResults,
    totalTagCount,
    categoryDistribution,
    categoryDistributionTotal,
    getNodeMeta,
    loadTree,
    handleCategoryChange,
    handleNodeClick,
    handleSearchResultSelect,
    handleAdd,
    handleEdit,
    handleCancelEdit,
    handleSave,
    handleDelete,
    handleGenerateVectors,
  }
}
