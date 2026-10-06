<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">匹配校准数据</h1>
      <p class="page-desc">人工结构化复核产生的标准化校准数据，可筛选、脱敏、导出；仅展示校准偏差、样本量和建议权重预览，不代表模型训练或模型效果。</p>
    </div>

    <div class="filter-bar">
      <el-form inline>
        <el-form-item label="导出标记">
          <el-select v-model="query.exportEnabled" clearable placeholder="全部" style="width: 140px">
            <el-option label="允许导出" :value="true" />
            <el-option label="不允许导出" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位ID">
          <el-input v-model="query.postId" placeholder="岗位ID" style="width: 120px" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="query.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="query.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load(1)">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="export-bar">
      <el-button type="success" :loading="exporting" @click="doExport('jsonl')">导出 JSONL</el-button>
      <el-button type="success" :loading="exporting" @click="doExport('csv')">导出 CSV</el-button>
      <el-checkbox v-model="query.includeDimensions" style="margin-left: 12px">包含维度修正</el-checkbox>
      <el-checkbox v-model="query.maskPersonalData" style="margin-left: 12px">脱敏员工标识</el-checkbox>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="matchingRecordId" label="匹配记录" width="100" />
      <el-table-column prop="empId" label="员工ID" width="90" />
      <el-table-column prop="postId" label="岗位ID" width="90" />
      <el-table-column prop="aiMatchScore" label="原始AI分" width="100" />
      <el-table-column prop="finalMatchScore" label="人工最终分" width="100" />
      <el-table-column prop="finalMatchStatus" label="最终状态" width="90" />
      <el-table-column prop="calibrationSource" label="校准来源" width="150" />
      <el-table-column prop="exportEnabled" label="允许导出" width="90">
        <template #default="{ row }">
          <el-tag :type="row.exportEnabled === 1 ? 'success' : 'info'">
            {{ row.exportEnabled === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="feedbackComment" label="人工备注" show-overflow-tooltip />
      <el-table-column prop="feedbackTime" label="时间" width="160" />
    </el-table>

    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load()"
      style="margin-top: 12px"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { pageCalibration, exportCalibration } from '@/api/matching'

const loading = ref(false)
const exporting = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 20,
  exportEnabled: undefined as boolean | undefined,
  postId: undefined as number | undefined,
  startTime: undefined as string | undefined,
  endTime: undefined as string | undefined,
  includeDimensions: true,
  maskPersonalData: true,
})

async function load(page?: number) {
  if (page) query.current = page
  loading.value = true
  try {
    const res = await pageCalibration({
      current: query.current,
      size: query.size,
      exportEnabled: query.exportEnabled,
      postId: query.postId,
      startTime: query.startTime,
      endTime: query.endTime,
    })
    rows.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

async function doExport(format: 'jsonl' | 'csv') {
  exporting.value = true
  try {
    const blob = await exportCalibration({
      format,
      includeDimensions: query.includeDimensions,
      maskPersonalData: query.maskPersonalData,
      startTime: query.startTime,
      endTime: query.endTime,
      postId: query.postId,
      exportEnabled: true,
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ext = format === 'csv' ? 'csv' : 'jsonl'
    a.download = `matching-calibration-${new Date().toISOString().slice(0, 10)}.${ext}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出完成')
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(() => load())
</script>

<style scoped>
.page { padding: 20px; }
.page-header { margin-bottom: 16px; }
.page-title { font-size: 20px; margin: 0 0 6px; }
.page-desc { color: #8a93a6; font-size: 13px; margin: 0; }
.filter-bar, .export-bar { margin-bottom: 12px; }
</style>
