<script setup lang="ts">
/**
 * 材料库：已上传权威材料的管理面板。
 *
 * 解决三个真实问题：
 *   1. **上传后看不见**：原来上传完只剩一个能关掉的 chip，刷新页面就再也找不到这份材料，
 *      也无法删除；重复上传只能靠自己去猜系统里有没有。
 *   2. **索引失败后无路可走**：材料索引失败时页面只有一句报错，材料本身卡在库里，
 *      既不能用也删不掉。这里给出「重建索引」入口，不需要重新上传。
 *   3. **老材料无法复用**：新一次解析若想用已经传过的材料，只能再传一遍。
 *      这里可以直接「加入本次解析」。
 *
 * 「仅试算」材料默认不列出 —— 它们本就只服务于当次试算，且会被自动清理，
 * 列出来只会让管理员以为系统里堆了一堆垃圾材料。需要排查时可勾选显示。
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh, RefreshRight, Search } from '@element-plus/icons-vue'
import {
  MATERIAL_CATEGORIES,
  deleteAuthorityMaterial,
  pageAuthorityMaterials,
  reindexAuthorityMaterial,
} from '@/api/post-trend'
import type { AuthorityMaterial } from '@/api/post-trend'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import {
  formatUploadedTime,
  isAlreadyPicked,
  materialStatusMeta,
  pickBlockReason,
} from './material-library-meta'

const props = withDefaults(
  defineProps<{
    /** 解析进行中时不允许改动材料集合 */
    running?: boolean
    /** 本轮待提交清单里的材料 id，用于把「已加入」的置灰 */
    pendingIds?: number[]
  }>(),
  { running: false, pendingIds: () => [] },
)

const emit = defineEmits<{ (e: 'pick', material: AuthorityMaterial): void }>()

const PAGE_SIZE = 8

const list = ref<AuthorityMaterial[]>([])
const total = ref(0)
const current = ref(1)
const loading = ref(false)
/** 常驻错误：删除/重建索引失败要说清是哪一份材料、为什么失败 */
const errorText = ref('')
const keyword = ref('')
const category = ref('')
const includeEphemeral = ref(false)
/** 正在处理中的材料 id：避免连点两次删除 */
const actingId = ref<number | null>(null)

async function load(page = current.value) {
  loading.value = true
  errorText.value = ''
  try {
    const res = await pageAuthorityMaterials({
      current: page,
      size: PAGE_SIZE,
      keyword: keyword.value.trim() || undefined,
      sourceCategory: category.value || undefined,
      includeEphemeral: includeEphemeral.value || undefined,
    })
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
    current.value = page
  } catch (error) {
    errorText.value = resolveApiErrorMessage(error, '加载材料库失败')
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  keyword.value = ''
  category.value = ''
  includeEphemeral.value = false
  void load(1)
}

async function handleDelete(material: AuthorityMaterial) {
  try {
    await ElMessageBox.confirm(
      `删除后这份材料的检索片段与向量会一并清理，之后无法再用于解析。已经落地到岗位候选里的证据原文是快照，不受影响。确认删除「${material.title}」？`,
      '删除材料',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  actingId.value = material.documentId
  errorText.value = ''
  try {
    const res = await deleteAuthorityMaterial(material.documentId)
    ElMessage.success(res.data?.message ?? '材料已删除')
    // 删掉最后一页的最后一条时往前退一页，否则会停在空白页
    const nextPage = list.value.length === 1 && current.value > 1 ? current.value - 1 : current.value
    await load(nextPage)
  } catch (error) {
    errorText.value = `「${material.title}」删除失败：${resolveApiErrorMessage(error, '请稍后重试')}`
  } finally {
    actingId.value = null
  }
}

async function handleReindex(material: AuthorityMaterial) {
  actingId.value = material.documentId
  errorText.value = ''
  try {
    const res = await reindexAuthorityMaterial(material.documentId)
    ElMessage.success(`「${material.title}」已重建索引，共 ${res.data?.chunkCount ?? 0} 个片段`)
    await load()
  } catch (error) {
    errorText.value = `「${material.title}」重建索引失败：${resolveApiErrorMessage(error, '请稍后重试')}`
  } finally {
    actingId.value = null
  }
}

function handlePick(material: AuthorityMaterial) {
  const reason = pickBlockReason(material)
  if (reason) {
    errorText.value = `「${material.title}」${reason}`
    return
  }
  errorText.value = ''
  emit('pick', material)
}

onMounted(() => void load(1))

defineExpose({ reload: () => load(), resetAndLoad })
</script>

<template>
  <section class="glass-card td-card">
    <div class="td-card__head">
      <div>
        <div class="section-title">材料库</div>
        <div class="section-desc">
          已经上传过的权威材料都在这里，可以删除、重建索引，或直接加入本次解析。<strong>同一份内容重复上传不会重复建索引</strong>，系统会直接复用已有索引。
        </div>
      </div>
      <div class="toolbar-group">
        <el-input
          v-model="keyword"
          class="ml-keyword"
          placeholder="按标题搜索"
          clearable
          :prefix-icon="Search"
          @keyup.enter="load(1)"
          @clear="load(1)"
        />
        <el-select v-model="category" class="ml-category" placeholder="全部类别" clearable @change="load(1)">
          <el-option v-for="item in MATERIAL_CATEGORIES" :key="item.value" :value="item.value" :label="item.label" />
        </el-select>
        <el-checkbox v-model="includeEphemeral" @change="load(1)">含仅试算材料</el-checkbox>
        <el-button :loading="loading" @click="load()">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <div class="td-card__body">
      <el-alert
        v-if="errorText"
        class="td-alert"
        type="error"
        :closable="false"
        show-icon
        :title="errorText"
      />

      <el-table v-loading="loading" :data="list" class="ml-table" :empty-text="'材料库还没有材料'">
        <el-table-column label="材料" min-width="260">
          <template #default="{ row }">
            <div class="ml-title">{{ row.title }}</div>
            <div class="ml-sub">
              {{ row.sourceCategoryLabel || row.sourceCategory }}
              <el-tag v-if="row.ephemeral" class="ml-flag" size="small" type="info" effect="plain">仅试算</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="索引状态" width="130">
          <template #default="{ row }">
            <el-tooltip :content="materialStatusMeta(row.indexStatus, row.readyForAnalysis).hint" placement="top">
              <el-tag
                size="small"
                effect="light"
                :type="materialStatusMeta(row.indexStatus, row.readyForAnalysis).type"
              >
                {{ materialStatusMeta(row.indexStatus, row.readyForAnalysis).label }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="片段" width="80">
          <template #default="{ row }">{{ row.chunkCount ?? 0 }}</template>
        </el-table-column>

        <el-table-column label="上传时间" width="150">
          <template #default="{ row }">{{ formatUploadedTime(row.uploadedTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="250" align="right">
          <template #default="{ row }">
            <el-tooltip
              :disabled="!pickBlockReason(row) && !isAlreadyPicked(row.documentId, props.pendingIds)"
              :content="pickBlockReason(row) || '已在本轮待解析清单里'"
              placement="top"
            >
              <span class="ml-action">
                <el-button
                  size="small"
                  type="primary"
                  plain
                  :disabled="
                    props.running ||
                    !!pickBlockReason(row) ||
                    isAlreadyPicked(row.documentId, props.pendingIds)
                  "
                  @click="handlePick(row)"
                >
                  <el-icon><Plus /></el-icon> 加入本次解析
                </el-button>
              </span>
            </el-tooltip>
            <el-button size="small" :loading="actingId === row.documentId" @click="handleReindex(row)">
              <el-icon><RefreshRight /></el-icon> 重建索引
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="props.running"
              :loading="actingId === row.documentId"
              @click="handleDelete(row)"
            >
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="ml-foot">
        <el-pagination
          v-if="total > PAGE_SIZE"
          layout="total, prev, pager, next"
          :total="total"
          :page-size="PAGE_SIZE"
          :current-page="current"
          @current-change="load"
        />
        <span v-else class="td-hint">共 {{ total }} 份材料</span>
        <el-button v-if="total" text @click="resetAndLoad">重置筛选</el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ml-keyword {
  width: 190px;
}

.ml-category {
  width: 150px;
}

.ml-table {
  width: 100%;
}

.ml-table :deep(.el-table__cell) {
  vertical-align: middle;
}

.ml-title {
  font-weight: 600;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-all;
}

.ml-sub {
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.ml-flag {
  height: 18px;
  padding: 0 6px;
  line-height: 16px;
}

.ml-action {
  margin-right: 8px;
}

.ml-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}
</style>
