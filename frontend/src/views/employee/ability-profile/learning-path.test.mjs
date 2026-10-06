import assert from 'node:assert/strict'
import {
  learningPathPriorityMeta,
  normalizeLearningPath,
} from './learning-path.ts'

/* ---------------- 核心回归：结构化对象不能再被当字符串渲染 ---------------- */

const structured = normalizeLearningPath([
  { tagId: 10, abilityName: 'Java 并发', currentLevel: 2, targetLevel: 4, suggestion: '补充 JMM 与锁', priority: 'HIGH' },
])
assert.equal(structured.length, 1)
assert.equal(structured[0].abilityName, 'Java 并发')
assert.equal(structured[0].currentLevel, 2)
assert.equal(structured[0].targetLevel, 4)
assert.equal(structured[0].suggestion, '补充 JMM 与锁')
assert.equal(structured[0].priority, 'HIGH')

/* ---------------- 字符串形态（AI 只给一句话）仍可用 ---------------- */
const plain = normalizeLearningPath(['建议加强分布式事务的实践'])
assert.equal(plain.length, 1)
assert.equal(plain[0].abilityName, null)
assert.equal(plain[0].suggestion, '建议加强分布式事务的实践')
assert.equal(plain[0].currentLevel, null)

/* ---------------- 等级容错：字符串数字可取，取不到是 null 而不是 0 ---------------- */
const looseLevel = normalizeLearningPath([
  { abilityName: 'Redis', currentLevel: '3', targetLevel: null, suggestion: '深入持久化' },
])
assert.equal(looseLevel[0].currentLevel, 3, '字符串型等级要能取到')
assert.equal(looseLevel[0].targetLevel, null, '缺等级必须是 null，回落 0 会被读成 L0')

/* ---------------- 脏数据一律丢弃，不渲染成空行/原始 JSON ---------------- */
assert.deepEqual(normalizeLearningPath(null), [])
assert.deepEqual(normalizeLearningPath(undefined), [])
assert.deepEqual(normalizeLearningPath('{bad json'), [])
assert.deepEqual(normalizeLearningPath({ abilityName: 'Java' }), [], '不是数组时整体忽略')
assert.deepEqual(normalizeLearningPath([null, 0, false, {}, [], { abilityName: '  ' }]), [],
  '空对象、纯空白能力名都算脏数据')
// 标量元素（0 / false）曾经被宽松文本转换转成「0」渲染成一条假建议，必须丢弃
assert.deepEqual(normalizeLearningPath([0, 1, false, true]), [], '数值/布尔标量不是建议正文')
assert.deepEqual(normalizeLearningPath(['  ']), [], '纯空白字符串不产生条目')
// 对象数组元素里的数字字段仍按文本渲染（后端偶尔把等级拼进 suggestion）
assert.equal(normalizeLearningPath([{ abilityName: 'Java', suggestion: 3 }])[0].suggestion, '3')

/* ---------------- 字段别名兼容（AI 可能换 key）+ 部分字段缺失 ---------------- */
const aliased = normalizeLearningPath([
  { ability: 'Kafka', content: '学习分区与消费组' },
  { abilityName: 'MySQL', text: '索引优化', currentLevel: 1 },
])
assert.equal(aliased.length, 2)
assert.equal(aliased[0].abilityName, 'Kafka')
assert.equal(aliased[0].suggestion, '学习分区与消费组')
assert.equal(aliased[1].suggestion, '索引优化')
assert.equal(aliased[1].abilityName, 'MySQL')

/* ---------------- 混合数组（对象 + 字符串）保持顺序 ---------------- */
const mixed = normalizeLearningPath([
  { abilityName: 'Java' },
  '先补基础',
  { abilityName: 'Go', suggestion: '进阶' },
])
assert.deepEqual(mixed.map(e => e.abilityName), ['Java', null, 'Go'])

/* ---------------- 优先级归一化 ---------------- */
assert.deepEqual(learningPathPriorityMeta('HIGH'), { text: '高优先级', tagType: 'danger' })
assert.deepEqual(learningPathPriorityMeta('p0'), { text: '高优先级', tagType: 'danger' })
assert.deepEqual(learningPathPriorityMeta('Medium'), { text: '中优先级', tagType: 'warning' })
assert.deepEqual(learningPathPriorityMeta('低'), { text: '低优先级', tagType: 'info' })
assert.equal(learningPathPriorityMeta(null), null, '没有优先级就不渲染标签')
assert.equal(learningPathPriorityMeta('   '), null)
// 未识别的值回显原文，而不是吞掉（否则排查时看不到后端到底给了什么）
assert.deepEqual(learningPathPriorityMeta('URGENT'), { text: 'URGENT', tagType: 'info' })

console.log('learning path logic tests passed')
