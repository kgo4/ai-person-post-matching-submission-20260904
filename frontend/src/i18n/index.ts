import { createI18n } from 'vue-i18n'

const messages = {
  en: {
    nav: {
      dashboard: 'Overview',
      dashboard_board: 'Dashboard',
      employee: 'Candidates',
      employee_list: 'Directory',
      employee_profile: 'Profiles',
      employee_interview: 'Interviews',
      post: 'Positions',
      post_list: 'Postings',
      post_model: 'Models',
      post_template: 'Templates',
      post_excel_import: 'Excel Import',
      post_emerging: 'Emerging Post',
      post_prototype: 'Prototypes',
      matching: 'Intelligence',
      match_run: 'Run Match',
      match_result: 'Results',
      match_task: 'Tasks',
      match_history: 'History',
      match_rule: 'Rules',
      system: 'Settings',
      sys_tag: 'Tags',
      sys_tag_library: 'Tag Library',
      sys_field: 'Fields',
      sys_user: 'Users',
      sys_role: 'Roles',
      search: 'Search...',
      logout: 'Sign out'
    }
  },
  zh: {
    nav: {
      dashboard: '总览',
      dashboard_board: '数据看板',
      employee: '人员',
      employee_list: '员工列表',
      employee_profile: '能力档案',
      employee_interview: 'AI面试记录',
      post: '岗位',
      post_list: '岗位列表',
      post_model: '能力模型',
      post_template: '模型模板',
      post_excel_import: 'Excel批量导入',
      post_emerging: '新兴岗位定义',
      post_prototype: '岗位原型库',
      matching: '匹配',
      match_run: '发起匹配',
      match_result: '匹配结果',
      match_task: '待办任务',
      match_history: '审批历史',
      match_rule: '规则配置',
      system: '系统',
      sys_tag: '能力标签',
      sys_tag_library: '标签库治理',
      sys_field: '扩展字段',
      sys_user: '用户',
      sys_role: '角色',
      search: '搜索...',
      logout: '退出登录'
    }
  }
}

export const i18n = createI18n({
  legacy: false, // for Vue 3 Composition API
  locale: localStorage.getItem('language') || 'zh', // default to zh
  fallbackLocale: 'en',
  messages,
})

export function toggleLanguage() {
  const newLang = i18n.global.locale.value === 'zh' ? 'en' : 'zh'
  i18n.global.locale.value = newLang
  localStorage.setItem('language', newLang)
  return newLang
}
