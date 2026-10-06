<script setup lang="ts">
/**
 * 证据准备（可折叠）。
 *
 * 为什么把它从「主 Tab」降级成折叠区：
 * 演化页原先有 5 个并列 Tab，其中「资料输入」是整屏 5 张互不相关的上传卡
 * （市场 JD 采集 / 行业白皮书 / 公司内部资料 / 云知识库 / 知乎采集）。
 * 但其中两件事在别处已有**更完整**的实现：
 *   · 权威材料上传 → 「岗位趋势发现」的材料库（支持拖拽多选、材料类别、
 *     「仅试算」不落库、上传后能看见能删能重建索引）；
 *   · 市场 JD 采集 → 「市场 JD 采集」页（命令下发 + 批次台账 + JD 池 + 按批次解析）。
 * 同一件事有两套界面、两套体验，用户不知道该用哪个 —— 这才是「混乱」的来源。
 *
 * 现在这里只保留：
 *   1. 指向那两个权威入口的**跳转**（不再重复实现表单）；
 *   2. 云知识库同步 —— 它**没有**其它入口，只能留在这里；
 *      未配置时（`/api/rag/cloud/status` 的 `usable=false`）表单换成说明，
 *      因为后端此时只会空跑并回 `syncedCount=0`，而界面会误报「同步完成」。
 *
 * 「外部趋势（知乎）」也移出本组件：它的检索词由目标岗位决定，而目标岗位是在
 * 「运行演化」里选的，放在这里会出现「按钮永远灰着、提示去选岗位」的死角。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { syncEvolutionCloudKnowledge } from '@/api/evolution'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import { useCloudKnowledge } from './use-cloud-knowledge'

const router = useRouter()
const expanded = ref(false)
const syncing = ref(false)
const cloudSyncForm = ref({ knowledgeBaseCode: '', businessDomain: '' })

/**
 * 云知识库可用性。
 *
 * 这一块原来是「填编码 → 点同步」，而**未配置时后端只会空跑**（返回 `syncedCount=0`），
 * 界面却提示「同步完成」——用户会以为证据已经进来了。所以未配置时直接把表单换成说明。
 */
const {
  usable: cloudKbUsable,
  ensureLoaded: ensureCloudKbLoaded,
  notConfiguredHint: cloudKbHint,
} = useCloudKnowledge()

onMounted(() => {
  void ensureCloudKbLoaded()
})

function toggle() {
  expanded.value = !expanded.value
}

async function handleCloudSync() {
  if (!cloudSyncForm.value.knowledgeBaseCode.trim()) {
    ElMessage.warning('请填写知识库编码')
    return
  }
  syncing.value = true
  try {
    const res = await syncEvolutionCloudKnowledge(cloudSyncForm.value)
    ElMessage.success(`同步完成，共同步 ${res.data?.syncedCount ?? 0} 个文档`)
  } catch (error) {
    // 同步失败只影响这一个来源，不能把整页弹成错误态
    ElMessage.error(resolveApiErrorMessage(error, '云知识库同步失败'))
  } finally {
    syncing.value = false
  }
}
</script>

<template>
  <section class="evo-card">
    <button type="button" class="evo-prep__head" @click="toggle">
      <span class="evo-prep__head-main">
        <span class="evo-prep__title">证据准备</span>
        <span class="evo-prep__sub">
          演化分析需要证据。上传权威材料、采集市场 JD、同步云知识库都在各自专页完成，这里只做入口。
        </span>
      </span>
      <span class="evo-prep__chevron">{{ expanded ? '收起 ▲' : '展开 ▼' }}</span>
    </button>

    <div v-show="expanded" class="evo-prep__body">
      <div class="evo-prep__item">
        <span class="evo-prep__item-text">
          <span class="evo-prep__item-title">权威材料</span>
          <span class="evo-prep__item-desc">
            政府文件、政策、行业报告、白皮书。支持拖拽批量上传、材料类别标注，
            以及「仅试算」模式（不落库、解析后自动清理）。
          </span>
        </span>
        <el-button type="primary" plain @click="router.push('/post/trend-discovery')">去上传材料</el-button>
      </div>

      <div class="evo-prep__item">
        <span class="evo-prep__item-text">
          <span class="evo-prep__item-title">市场 JD</span>
          <span class="evo-prep__item-desc">
            查看已接收的市场 JD 批次、按批次解析、浏览 JD 池并做数据治理（单条/批量删除）。
          </span>
        </span>
        <el-button type="primary" plain @click="router.push('/post/crawler-jd')">去市场 JD 采集</el-button>
      </div>

      <div class="evo-prep__item">
        <span class="evo-prep__item-text">
          <span class="evo-prep__item-title">
            云知识库
            <el-tag v-if="!cloudKbUsable" size="small" type="info" effect="plain">未配置</el-tag>
          </span>
          <span class="evo-prep__item-desc">
            从已配置的云端知识库同步文档。这是本页独有的来源，没有其它入口。
          </span>
        </span>
      </div>
      <div v-if="!cloudKbUsable" class="evo-inline-fields">
        <el-alert type="info" :closable="false" show-icon :title="cloudKbHint" />
      </div>
      <div v-else class="evo-inline-fields">
        <div class="evo-form-row" style="flex:1">
          <label class="evo-label">知识库编码</label>
          <el-input v-model="cloudSyncForm.knowledgeBaseCode" placeholder="company-post-kb" />
        </div>
        <div class="evo-form-row" style="flex:1">
          <label class="evo-label">业务领域</label>
          <el-input v-model="cloudSyncForm.businessDomain" placeholder="如：智能制造" />
        </div>
        <div class="evo-form-row" style="justify-content:flex-end">
          <label class="evo-label">&nbsp;</label>
          <el-button type="primary" :loading="syncing" @click="handleCloudSync">同步</el-button>
        </div>
      </div>
    </div>
  </section>
</template>
