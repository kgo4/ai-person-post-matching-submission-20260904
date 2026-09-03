import type { RouteRecordRaw } from 'vue-router'
import { Briefcase, DataLine, Document, DocumentCopy, Files, List, MagicStick, SetUp, Share, Upload } from '@element-plus/icons-vue'

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
    {
      path: 'emerging-post',
      name: 'EmergingPost',
      component: () => import('@/views/post/emerging-post/index.vue'),
      meta: { title: '新兴岗位定义', icon: MagicStick },
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
  ],
}

export default postRoutes
