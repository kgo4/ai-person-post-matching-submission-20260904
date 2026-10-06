import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { extractTemplate, stripSfcComments } from '../../../utils/sfc-source.ts'

/**
 * 云知识库「未配置」口径的契约测试。
 *
 * 背景：平台里的云知识库（火山引擎 KB）**代码是真的、功能是关的** ——
 * `VOLCENGINE_KB_ENABLED=false` 且 AK/SK/资源 ID 全空，`isUsable()=false`。
 * 但界面原先有四处会误导用户：
 *   1. 岗位演化 / 定时演化的「云知识库」开关**默认为开且可勾**（后端拿到也不会真去检索）；
 *   2. 证据准备的云同步表单可以填写并点「同步」，后端只会空跑并回 `syncedCount=0`；
 *   3. 知识资产页的「配置」按钮 + 配置对话框 —— 后端 `updateConfig` 只改内存不落库，重启回滚；
 *   4. 上述对话框会把 AK/SK 明文提交，而该端点不在操作日志脱敏名单里。
 *
 * 这四条的共同后果都是**静默失效**（操作成功、结果为空、没有报错），
 * 所以这里用源码级断言把它们钉住 —— 这个仓库没有组件挂载测试环境，
 * 版式与交互只能靠「模板文本 + 纯函数」来锁。
 */

const ROOT = resolve(import.meta.dirname, '../../..')
const read = rel => readFileSync(resolve(ROOT, rel), 'utf8')

const EVO = 'views/post/evolution'

/* ============ 1. 唯一判据来自 composable，不许各页自己算 ============ */

const composable = read(`${EVO}/use-cloud-knowledge.ts`)
assert.ok(
  composable.includes("getCloudKnowledgeStatus"),
  '可用性必须取自 /rag/cloud/status（与知识资产页同一个值），否则会出现"那边未配置、这边能勾"',
)
assert.ok(
  composable.includes("usable.value ? '' : `${CLOUD_KB_NOT_CONFIGURED}：${CLOUD_KB_STARTUP_ONLY}`"),
  '未配置提示必须由 composable 统一生成，避免三处入口各写一份文案',
)
assert.ok(
  composable.includes('let inflight') && composable.includes('inflight = refresh()'),
  '三个组件同时 onMounted 必须复用同一次请求（去重），否则进一次演化页会打三次接口',
)

for (const file of ['EvolutionAgentPanel.vue', 'EvolutionSchedulePanel.vue', 'EvolutionEvidencePrep.vue']) {
  const src = stripSfcComments(read(`${EVO}/${file}`))
  assert.ok(src.includes('useCloudKnowledge'), `${file} 必须走统一的可用性判据`)
  assert.ok(
    /cloudKbUsable/.test(src),
    `${file} 必须使用 cloudKbUsable（而不是自己判断 enabled/usable）`,
  )
}

/* ============ 2. 两个开关：置灰 + 未配置标签 + 点击提示 ============ */

for (const file of ['EvolutionAgentPanel.vue', 'EvolutionSchedulePanel.vue']) {
  const raw = read(`${EVO}/${file}`)
  const tpl = extractTemplate(stripSfcComments(raw))

  assert.ok(
    tpl.includes(':disabled="!cloudKbUsable"'),
    `${file}：云知识库开关必须在未配置时置灰（disabled），否则"能勾但不起作用"`,
  )
  assert.ok(
    tpl.includes('evo-switch-guard') && tpl.includes('warnCloudKbNotConfigured'),
    `${file}：disabled 的 el-switch 会吞掉点击，必须在包裹层承接点击给出原因`,
  )
  assert.ok(
    tpl.includes('未配置'),
    `${file}：未配置时要有可见的状态标签，不能只有鼠标悬浮才知道`,
  )
  assert.ok(
    tpl.includes('evo-switch-guard') && tpl.includes('active-text="云知识库"'),
    `${file}：置灰的是云知识库那一项，不能把相邻的「权威材料 / 市场 JD」一起禁用`,
  )
}

// 运行演化：禁用同时必须复位为「不包含」，否则开关停在开=用户以为带了云证据
const agentSrc = stripSfcComments(read(`${EVO}/EvolutionAgentPanel.vue`))
assert.ok(
  /includeCloudKnowledge:\s*false/.test(agentSrc),
  '运行演化：默认值必须是 false（状态未拉到前先按"不包含"算）',
)
assert.ok(
  /watch\(\s*cloudKbUsable[\s\S]{0,160}includeCloudKnowledge = value/.test(agentSrc),
  '运行演化：可用性变化必须同步开关（未配置 → 关），否则灰着的开关仍显示为"开"',
)

// 定时演化：只置灰，不许静默改写用户已保存的配置（那是数据篡改）
const schedSrc = stripSfcComments(read(`${EVO}/EvolutionSchedulePanel.vue`))
assert.ok(
  schedSrc.includes('includeCloudKnowledge: schedule.includeCloudKnowledge'),
  '定时演化：编辑既有配置时必须原样回显，不能按可用性改写用户已保存的值',
)
assert.ok(
  /includeCloudKnowledge:\s*cloudKbUsable\.value \? 1 : 0/.test(schedSrc),
  '定时演化：只有新建时才按可用性取默认值',
)

/* ============ 3. 证据准备：未配置时表单换成说明 ============ */

const prepTpl = extractTemplate(stripSfcComments(read(`${EVO}/EvolutionEvidencePrep.vue`)))
assert.ok(
  prepTpl.includes('v-if="!cloudKbUsable"') && prepTpl.includes('cloudKbHint'),
  '证据准备：未配置时必须把同步表单换成说明（后端只会空跑并回 syncedCount=0，界面却会报"同步完成"）',
)
assert.ok(
  prepTpl.includes('v-else'),
  '证据准备：已配置时仍要保留可用的同步表单，不能把功能删掉',
)

/* ============ 4. 知识资产页：取消运行期配置连接 ============ */

const kbSrc = stripSfcComments(read('views/rag/knowledge/index.vue'))

assert.ok(
  !kbSrc.includes('openCloudConfig') && !kbSrc.includes('saveCloudConfig'),
  '知识资产页：必须移除「配置」入口与其保存逻辑（后端只改内存不落库，重启回滚）',
)
assert.ok(
  !kbSrc.includes('showCloudConfigDialog') && !kbSrc.includes('cloudConfigForm'),
  '知识资产页：配置对话框及其表单状态必须一并移除，避免留一个"看起来能配"的空壳',
)
assert.ok(
  !kbSrc.includes('updateCloudKnowledgeConfig'),
  '知识资产页：不得再调用配置写入接口',
)
assert.ok(
  kbSrc.includes('只在系统启动时由服务端配置提供') && kbSrc.includes('运行期不支持修改'),
  '知识资产页：未配置时要说明"连接信息只在系统启动时配置"，这是用户唯一能做的动作',
)
assert.ok(
  kbSrc.includes('未配置云端知识库'),
  '知识资产页：未配置状态必须明确写出来',
)
// 同步/检索能力保留（可用时仍要能演示）
assert.ok(
  kbSrc.includes('showCloudSyncDialog') && kbSrc.includes('handleCloudSearch'),
  '知识资产页：已配置时仍要保留「同步云端」与「云端检索」',
)

/* ============ 5. API 层：写入函数必须带「不可用」说明 ============ */

const apiSrc = read('api/rag.ts')
// ⚠️ 必须先在**剥掉注释**的源码里找函数体：删掉注释后「当前不可用」这句就没了，
// 契约要拦的是「函数名被去掉、注释还在」或「注释被删、函数还在」这两种半吊子改法。
const apiSrcNoComment = stripSfcComments(apiSrc)
assert.ok(
  /当前不可用[\s\S]{0,600}?updateCloudKnowledgeConfig/.test(apiSrc),
  'api/rag.ts：保留的写配置函数必须标注"当前不可用"，否则后来者会以为它是可用能力',
)
assert.ok(
  apiSrcNoComment.includes("return put('/rag/cloud/config', data)"),
  'api/rag.ts：该函数体应保留（接口契约完整），只是无调用方',
)
assert.ok(
  !kbSrc.includes('updateCloudKnowledgeConfig'),
  'api/rag.ts：标注了不可用，就必须真的没有页面调用它',
)

console.log('cloud-knowledge-config.test.mjs: 全部断言通过')
