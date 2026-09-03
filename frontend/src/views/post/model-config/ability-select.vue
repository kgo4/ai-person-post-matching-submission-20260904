<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTagTree } from '@/api'
import type { AbilityTagTreeVO } from '@/api'

const router = useRouter()
const loading = ref(false)
const selectedAbilities = ref<number[]>([])

// 能力标签树数据
const treeData = ref<AbilityTagTreeVO[]>([])
const tagNameMap = ref<Record<number, string>>({})

onMounted(() => {
  loadTagTree()
})

async function loadTagTree() {
  loading.value = true
  try {
    const res = await getTagTree()
    const map: Record<number, string> = {}
    const walk = (nodes: AbilityTagTreeVO[]) => {
      for (const n of nodes) {
        map[n.id] = n.tagName
        if (n.children?.length) walk(n.children)
      }
    }
    walk(res.data ?? [])
    tagNameMap.value = map
    treeData.value = res.data
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleConfirm() {
  if (selectedAbilities.value.length === 0) {
    ElMessage.warning('请至少选择一项能力')
    return
  }
  ElMessage.success('已选择 ' + selectedAbilities.value.length + ' 项能力')
  router.back()
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover" v-loading="loading">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>自定义能力项选择</span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="12">
          <h4>能力标签树</h4>
          <div class="ability-tree-tip">「能力」为岗位要求锚点，「技能」为其细分项，建议优先选择能力层</div>
          <el-tree
            :data="treeData"
            show-checkbox
            node-key="id"
            default-expand-all
            :props="{ children: 'children', label: 'tagName' }"
            @check="(_data: any, checkInfo: any) => selectedAbilities = checkInfo.checkedKeys"
          >
            <template #default="{ data }">
              <span class="ability-tree-node">
                <span class="ability-tree-node__name">{{ data.tagName }}</span>
                <el-tag
                  :type="data.tagLevel === 2 ? 'success' : 'primary'"
                  size="small"
                  effect="plain"
                >{{ data.tagLevel === 2 ? '技能' : '能力' }}</el-tag>
              </span>
            </template>
          </el-tree>
        </el-col>
        <el-col :span="12">
          <h4>已选能力项 ({{ selectedAbilities.length }})</h4>
          <el-tag
            v-for="id in selectedAbilities"
            :key="id"
            style="margin: 4px;"
          >
            {{ tagNameMap[id] || id }}
          </el-tag>
          <el-empty v-if="selectedAbilities.length === 0" description="暂未选择能力项" :image-size="80" />
        </el-col>
      </el-row>

      <div style="text-align: center; margin-top: 24px;">
        <el-button type="primary" @click="handleConfirm">确认选择</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.ability-tree-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 8px;
}

.ability-tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.ability-tree-node__name {
  font-size: 13px;
  color: #1e293b;
}
</style>
