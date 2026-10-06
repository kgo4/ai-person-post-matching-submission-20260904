import type { RouteRecordRaw } from 'vue-router'
import { Briefcase, Connection, DataAnalysis, DataLine, Document, DocumentCopy, Files, List, MagicStick, SetUp, Share, Upload } from '@element-plus/icons-vue'

const Layout = () => import('@/views/layout/index.vue')

const postRoutes: RouteRecordRaw = {
  path: '/post',
  component: Layout,
  redirect: '/post/list',
  meta: { title: '岗位管理', icon: Briefcase },
  children: [
    {
      path: 'list',
      name: 'PostList',
      component: () => import('@/views/post/list/index.vue'),
      meta: { title: '岗位列表', icon: List },
    },
    {
      path: 'detail/:id',
      name: 'PostDetail',
      component: () => import('@/views/post/detail/index.vue'),
      meta: { title: '岗位详情', hidden: true },
    },
    {
      path: 'model-config',
      name: 'PostModelConfig',
      component: () => import('@/views/post/model-config/index.vue'),
      meta: { title: '岗位能力配置', icon: SetUp },
    },
    {
      path: 'model-config/ability-select',
      name: 'ModelAbilitySelect',
      component: () => import('@/views/post/model-config/ability-select.vue'),
      meta: { title: '能力项选择', hidden: true },
    },
    {
      path: 'model-config/weight',
      name: 'ModelWeightConfig',
      component: () => import('@/views/post/model-config/weight-config.vue'),
      meta: { title: '权重配置', hidden: true },
    },
    {
      path: 'model-config/extend',
      name: 'ModelExtendInfo',
      component: () => import('@/views/post/model-config/extend-info.vue'),
      meta: { title: '隐性要求', hidden: true },
    },
    {
      path: 'panorama',
      name: 'PostPanorama',
      component: () => import('@/views/post/panorama/index.vue'),
      meta: { title: '岗位全景图谱', icon: Share },
    },
    {
      path: 'template',
      name: 'PostTemplate',
      component: () => import('@/views/post/template/index.vue'),
      meta: { title: '岗位能力模板', icon: Document, hidden: true },
    },
    {
      path: 'template/edit',
      name: 'PostTemplateEdit',
      component: () => import('@/views/post/template/edit.vue'),
      meta: { title: '模板编辑', hidden: true },
    },
    {
      path: 'excel-import',
      name: 'PostExcelImport',
      component: () => import('@/views/post/excel-import/index.vue'),
      meta: { title: 'Excel批量导入', icon: Upload },
    },
    /**
     * 岗位趋势发现（权威材料 → 岗位建议）——LLM+RAG 主链路，后端 /api/post/trend/**。
     * 与下面「市场 JD 统计」是两套不能混用的子系统：本页解析出的是**候选**（岗位/能力变更），
     * 必须由岗位管理员审核后才落地；统计页是纯 PMI 共现、零 LLM 的观察视图。
     */
    {
      path: 'trend-discovery',
      name: 'PostTrendDiscovery',
      component: () => import('@/views/post/trend-discovery/index.vue'),
      meta: { title: '岗位趋势发现', icon: MagicStick, requiredPermissions: ['POST:MANAGE'] },
    },
    /**
     * 市场 JD 统计：原「岗位趋势发现」页（`/post/emerging-post`）更名为更贴切的名称并换路径。
     * 它只调 `/api/post/emerging/**` 两个只读 GET，是零 LLM 的统计视图，保留为独立入口。
     */
    {
      path: 'market-jd-stats',
      name: 'PostMarketJdStats',
      component: () => import('@/views/post/emerging-post/index.vue'),
      meta: { title: '市场 JD 统计', icon: DataAnalysis, requiredPermissions: ['POST:MANAGE'] },
    },
    // 旧路径保留为 hidden 重定向，避免已有收藏/深链 404
    {
      path: 'emerging-post',
      name: 'EmergingPost',
      redirect: '/post/market-jd-stats',
      meta: { title: '市场 JD 统计', hidden: true },
    },
    {
      path: 'prototype',
      name: 'PostPrototype',
      component: () => import('@/views/post/prototype/index.vue'),
      meta: { title: '岗位能力模板素材', icon: Files },
    },
    {
      path: 'model-version',
      name: 'PostModelVersion',
      component: () => import('@/views/post/model-version/index.vue'),
      meta: { title: '模型发布记录', icon: DocumentCopy },
    },
    {
      path: 'evolution/dashboard',
      name: 'PostEvolutionDashboard',
      redirect: '/post/evolution',
      meta: { title: '岗位演化', hidden: true },
    },
    {
      path: 'evolution',
      name: 'PostEvolution',
      component: () => import('@/views/post/evolution/index.vue'),
      meta: { title: '能力更新任务', icon: DataLine },
    },
    {
      path: 'evolution/detail/:id',
      name: 'PostEvolutionDetail',
      component: () => import('@/views/post/evolution/detail.vue'),
      meta: { title: '任务详情', hidden: true },
    },
    /**
     * 市场 JD 采集（爬虫系统配合功能）：岗位管理员此前只有「岗位演化 → 资料输入」里的一个抽屉，
     * 只能触发抓取、看不到抓回来的数据。本页把「采集 → 入池浏览 → 按批次解析」拼成闭环。
     * 权限复用 POST:EVOLUTION —— 后端 /api/post/evolution/** 全族（含爬虫代理与市场 JD 池）都用这个码。
     */
    {
      path: 'crawler-jd',
      name: 'PostCrawlerJd',
      component: () => import('@/views/post/crawler-jd/index.vue'),
      meta: { title: '市场 JD 采集', icon: Connection, requiredPermissions: ['POST:EVOLUTION'] },
    },
  ],
}

export default postRoutes
