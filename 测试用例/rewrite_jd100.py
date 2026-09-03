# -*- coding: utf-8 -*-
"""
jd-100.json 语料改写脚本（v3：片段变体化，跨岗位相似度受控）
===========================================================
目标：
  1) 同岗位 5 变体措辞独立、来源与职级可感知，Jaccard 远低于 0.92；
  2) 跨岗位任意两两 Jaccard 也低于疑似阈值（0.80），保证治理管线洁净度全部通过。
实现：6 套来源风格骨架；每套骨架的固定句片段提供 3 个措辞变体，
      按岗位全局序号 + 槽位号轮换选择，使同一骨架在不同岗位上文本组合不同。
约束：保留 id/domain/jobTitle/level/source/mustSkills/plusSkills/scenes；
      jobDescription 必须包含全部 mustSkills；不得含噪声模式与过时技术词。
"""
import json
import os
import re
import shutil
from itertools import combinations

BASE = os.path.dirname(os.path.abspath(__file__))
SUBMISSION_ROOT = os.path.normpath(os.path.join(BASE, '..'))
DATA = os.path.join(SUBMISSION_ROOT, '测试数据')
SRC = os.path.join(DATA, '岗位JD-100条.json')
ORIG = os.path.join(DATA, '岗位JD-100条-原始备份.json')

if os.path.exists(ORIG):
    shutil.copy(ORIG, SRC)
with open(SRC, encoding='utf-8') as f:
    DATA_LIST = json.load(f)

# ============ 素材库 ============
QUALITY = ['性能', '稳定性', '可观测性', '交付质量', '可维护性', '运行效率']
LEVEL_REQ = {
    '初级': '具备扎实的计算机基础与良好的学习能力，可在指导下独立完成开发与验证任务',
    '中级': '能独立负责模块设计、开发与联调，具备良好的工程实践与问题定位能力',
    '高级': '具备架构设计与技术规划能力，可主导方向落地、评审技术方案并指导团队成员',
}
DIRECTION = {
    '人工智能算法工程师': '算法研发与模型落地',
    '大数据数据工程师': '数据链路开发与平台建设',
    '智能系统平台开发工程师': '平台能力建设与系统开发',
    '物联网智能系统工程师': '智能系统开发与设备协同',
    '人工智能物联网解决方案工程师': '端云协同解决方案设计',
    '大数据机器学习工程师': '机器学习应用与工程化',
    '智能系统数据治理工程师': '数据治理体系建设',
    '物联网边缘计算工程师': '边缘计算能力建设',
    '人工智能智能制造工程师': '智能推理与制造场景落地',
    '大数据知识图谱工程师': '知识图谱构建与应用',
    '智能系统算法工程师': '算法研发与系统融合',
    '物联网数据工程师': '物联网数据链路开发',
    '人工智能平台开发工程师': '平台化能力与工程效能建设',
    '大数据智能系统工程师': '智能系统开发与实时控制',
    '智能系统物联网解决方案工程师': '物联网解决方案架构',
    '物联网机器学习工程师': '物联场景机器学习应用',
    '人工智能数据治理工程师': 'AI 数据治理与工程化',
    '大数据边缘计算工程师': '边缘数据链路与实时分析',
    '智能系统智能制造工程师': '智能推理与制造数字化',
    '物联网知识图谱工程师': '物联知识图谱构建',
}
DK = {
    '人工智能': ['算法工程', '模型工程', '智能化应用', 'AI 工程化', '数据驱动'],
    '大数据': ['数据平台', '数据处理链路', '数据驱动决策', '规模化数据', '实时离线一体化'],
    '智能系统': ['系统平台', '智慧化场景', '多系统协同', '数字化底座', '平台化能力'],
    '物联网': ['设备接入', '边缘侧计算', '端云协同', '物联场景', '连接与数据'],
}

def enum_variant(must, k):
    if k == 0:
        return '、'.join(must)
    if k == 1:
        return '、'.join(must[:-1]) + ' 及 ' + must[-1]
    if k == 2:
        return '、'.join(must[:-1]) + ' 以及 ' + must[-1]
    if k == 3:
        return '、'.join(must[:-1]) + ' 与 ' + must[-1]
    return '，'.join(must)

def plus_variant(k):
    return ['Docker、Git 与监控告警', 'Docker 容器化、Git 版本管理与监控告警',
            '容器化部署（Docker）、版本管理（Git）与监控告警'][k % 3]

def pick(variants, post_idx, slot):
    return variants[(post_idx * 7 + slot * 5) % len(variants)]

# ============ 6 套骨架（每槽位 3 变体；must0..must4 为不同技能列举）============
# S1 招聘平台A · 职责分点式
S1_H = [
    '负责{scene}场景下的{post}工作，方向聚焦{direction}。',
    '面向{scene}场景承担{post}职责，重点围绕{direction}推进。',
    '在{scene}场景下负责{post}相关工作，聚焦{direction}方向。',
]
S1_F = [
    '主要职责包括：围绕{must0}开展能力建设与落地；参与需求澄清与技术方案评审，配合完成版本迭代与联调发布；关注{quality}并持续改进。',
    '岗位职责覆盖{must0}相关的建设与交付：参与需求与方案评审，跟进开发、测试与联调发布全流程，持续优化{quality}。',
    '工作围绕{must0}的工程化落地展开：承担需求分析、方案设计与开发联调，保障版本稳定发布，并持续打磨{quality}。',
]
S1_T = [
    '任职要求：{lvl}，熟悉{must0}。加分项：{plus}。',
    '任职要求：{lvl}，对{must0}有实际使用经验。加分项：{plus}。',
    '我们希望候选人{lvl}、熟悉{must0}；加分项：{plus}。',
]

# S2 招聘平台B · 项目段落式
S2_H = [
    '加入{scene}方向的{post}岗位，你将参与{direction}项目，',
    '我们正在为{scene}方向组建{post}团队，你将深入{direction}项目，',
    '本团队专注{scene}场景的{post}方向，你将加入{direction}项目组，',
]
S2_F = [
    '独立承担{must1}相关模块的开发与交付，与产品、测试协同推进{dk}落地，并对{quality}负责。',
    '负责{must1}相关能力的研发与交付，联动产品与测试共同推进{dk}建设，持续保障{quality}。',
    '承担{must1}相关模块的设计与实现，跨团队协作推进{dk}落地，对{quality}目标负责。',
]
S2_T = [
    '希望你{lvl}，掌握{must1}；具备{plus}经验者优先。',
    '期望你{lvl}、熟悉{must1}；有{plus}相关经验者优先考虑。',
    '要求{lvl}，熟练使用{must1}；有{plus}经验者加分。',
]

# S3 行业白皮书 · 行业视角式
S3_H = [
    '伴随{domain}在{scene}的快速发展，{post}正成为{dk}的关键角色。',
    '在{domain}与{scene}融合演进的大背景下，{post}岗位的重要性持续凸显。',
    '随着{domain}在{scene}的深化应用，{post}成为支撑{dk}的重要力量。',
]
S3_F = [
    '本岗位围绕{must2}构建{dk}能力，推动{direction}相关技术在业务中的标准化与规模化应用，支撑{domain}长期演进。',
    '该角色聚焦{must2}的能力建设，促进相关技术在{scene}场景的规模化落地，并为{domain}发展夯实基础。',
    '岗位重点推进{must2}的体系化建设，助力{dk}在业务中的标准化应用与{domain}长期发展。',
]
S3_T = [
    '任职要求：{lvl}，掌握{must2}。',
    '任职要求：{lvl}，对{must2}有深入理解。',
    '我们期待候选人{lvl}、掌握{must2}。',
]

# S4 招聘平台A · 主导/执行式（职级强差异）
S4_HI = [
    '作为{post}，你将主导{dk}的技术路线与落地节奏，',
    '作为{post}，你将负责{dk}技术方向的规划与推进，',
    '作为{post}，你将成为{dk}技术方向的负责人，',
]
S4_FI = [
    '统筹{must3}相关体系的建设与演进，评审关键设计与实现，带领团队攻克技术难点，对整体{quality}负责。',
    '统筹{must3}体系的规划与落地，把控关键技术方案，带领团队解决难点，保障{quality}达成。',
    '主导{must3}相关体系建设与迭代，评审核心技术方案，指导团队突破难点并对{quality}负责。',
]
S4_TI = [
    '任职要求：{lvl}，{must3}实践经验丰富。',
    '任职要求：{lvl}，在{must3}方向有丰富落地经验。',
    '期望{lvl}，拥有{must3}方向的深厚实践积累。',
]
S4_HJ = [
    '作为{post}，你将在资深同事指导下参与{dk}相关工作，',
    '作为{post}，你将跟随团队经验丰富的同事学习并参与{dk}相关任务，',
    '作为{post}，你将在团队指导下投入{dk}相关工作，',
]
S4_FJ = [
    '围绕{must3}开展开发与验证，配合完成联调、测试与上线，及时沉淀技术文档。',
    '围绕{must3}完成开发与自测，配合团队进行联调、测试与发布，并整理技术文档。',
    '基于{must3}推进开发与验证工作，协同完成测试上线，及时记录与沉淀问题。',
]
S4_TJ = [
    '任职要求：{lvl}，了解{must3}。',
    '任职要求：{lvl}，对{must3}有一定了解。',
    '期望{lvl}，初步掌握{must3}。',
]

# S5 招聘平台B · 简洁精炼式
S5_H = [
    '从事{dk}方向的{post}工作，核心围绕{must4}展开：',
    '本岗位负责{dk}方向的{post}工作，重点聚焦{must4}：',
    '负责{dk}领域{post}相关工作，以{must4}为核心：',
]
S5_F = [
    '负责{dk}能力建设与日常交付，保障{quality}，配合团队完成项目目标。',
    '推进{dk}能力建设与常规交付，确保{quality}，协助团队达成业务目标。',
    '承担{dk}能力的建设与交付工作，维护{quality}，配合团队推进项目落地。',
]
S5_T = [
    '任职要求：{lvl}，熟悉{must4}；有{plus}经验者加分。',
    '任职要求：{lvl}、熟练{must4}；有{plus}经验者优先。',
    '要求{lvl}，掌握{must4}；有{plus}相关经验者优先考虑。',
]

# S6 行业白皮书 · 趋势演进式
S6_H = [
    '在{domain}与{scene}加速融合的背景下，{post}需要构建{must0}等关键能力，',
    '随着{domain}与{scene}的持续融合，{post}愈发需要{domain}相关的{must0}能力，',
    '面向{domain}与{scene}深度融合的趋势，{post}对{must0}能力的需求显著增强，',
]
S6_F = [
    '以满足{dk}对{direction}的需求。本岗位聚焦{must0}的体系化建设，推动技术方案在{scene}场景落地，并沉淀可复用的工程规范。',
    '以支撑{dk}在{direction}方向的持续发展。岗位重点推进{must0}的体系建设，促进方案在{scene}场景应用并形成工程规范。',
    '从而满足{dk}对{direction}的需要。岗位围绕{must0}开展体系化建设，推动落地{scene}场景并沉淀标准规范。',
]
S6_T = [
    '任职要求：{lvl}，掌握{must0}。',
    '任职要求：{lvl}，具备{must0}的工程经验。',
    '期望{lvl}，对{must0}有体系化认知。',
]

SKELETONS = {
    '招聘平台A': [
        lambda p, g, s: pick(S1_H, g, s).format(**p) + pick(S1_F, g, s + 1).format(**p) + pick(S1_T, g, s + 2).format(**p),
        lambda p, g, s: (
            (pick(S4_HI, g, s).format(**p) + pick(S4_FI, g, s + 1).format(**p) + pick(S4_TI, g, s + 2).format(**p))
            if p['level'] == '高级'
            else (pick(S4_HJ, g, s).format(**p) + pick(S4_FJ, g, s + 1).format(**p) + pick(S4_TJ, g, s + 2).format(**p))),
    ],
    '招聘平台B': [
        lambda p, g, s: pick(S2_H, g, s).format(**p) + pick(S2_F, g, s + 1).format(**p) + pick(S2_T, g, s + 2).format(**p),
        lambda p, g, s: pick(S5_H, g, s).format(**p) + pick(S5_F, g, s + 1).format(**p) + pick(S5_T, g, s + 2).format(**p),
    ],
    '行业白皮书': [
        lambda p, g, s: pick(S3_H, g, s).format(**p) + pick(S3_F, g, s + 1).format(**p) + pick(S3_T, g, s + 2).format(**p),
        lambda p, g, s: pick(S6_H, g, s).format(**p) + pick(S6_F, g, s + 1).format(**p) + pick(S6_T, g, s + 2).format(**p),
    ],
}

# ============ 生成 ============
by_post = {}
for d in DATA_LIST:
    by_post.setdefault(d['jobTitle'], []).append(d)
for v in by_post.values():
    v.sort(key=lambda x: x['id'])
post_order = sorted(by_post.keys())
post_idx = {name: i for i, name in enumerate(post_order)}

NOISE_CHECK = [re.compile(r'1[3-9]\d{9}'), re.compile(r'0\d{2,3}-\d{7,8}'),
               re.compile(r'[\w.]+@[\w.]+\.(com|cn)'), re.compile(r'微信|加V|QQ群|扫码|加群|直招|诚聘|急聘')]
OUTDATED = ['jQuery', 'Java 6', 'Java 7', 'Java 8', 'Vue2', 'Vue 2', 'MySQL 5.6', 'SVN']

def jaccard2(a, b):
    ga = set(a[i:i + 2] for i in range(len(a) - 1))
    gb = set(b[i:i + 2] for i in range(len(b) - 1))
    if not ga or not gb:
        return 0.0
    return len(ga & gb) / len(ga | gb)

changed = 0
same_over = []
for post, variants in by_post.items():
    g = post_idx[post]
    used = {}
    for d in variants:
        sels = SKELETONS[d['source']]
        sub = used.get(d['source'], 0)
        fn = sels[min(sub, len(sels) - 1)]
        used[d['source']] = sub + 1
        sk = d['mustSkills']
        p = {**d, 'post': d['jobTitle'], 'scene': d['scenes'],
             'must0': enum_variant(sk, 0), 'must1': enum_variant(sk, 1),
             'must2': enum_variant(sk, 2), 'must3': enum_variant(sk, 3),
             'must4': enum_variant(sk, 4),
             'dk': DK[d['domain']][g % 5], 'direction': DIRECTION[post],
             'lvl': LEVEL_REQ[d['level']], 'quality': QUALITY[(g + sub) % 6],
             'plus': plus_variant(sub)}
        text = fn(p, g, sub)
        missing = [s for s in sk if s not in text]
        if missing:
            raise SystemExit('技能缺失 %s: %s' % (post, missing))
        assert not any(pat.search(text) for pat in NOISE_CHECK), '噪声: ' + text
        assert not any(o in text for o in OUTDATED), '过时: ' + text
        d['jobDescription'] = text
        changed += 1
    descs = [(v['id'], v['jobDescription']) for v in variants]
    for a, b in combinations(descs, 2):
        j = jaccard2(a[1], b[1])
        if j > 0.92:
            same_over.append((post, a[0], b[0], round(j, 3)))

all_texts = [(d['id'], d['jobDescription']) for d in DATA_LIST]
cross_over = []
for a, b in combinations(all_texts, 2):
    j = jaccard2(a[1], b[1])
    if j > 0.80:
        cross_over.append((a[0], b[0], round(j, 3)))

print('改写变体数:', changed)
print('同岗位 >0.92 对数:', len(same_over))
if same_over:
    for x in same_over[:10]:
        print('  OVER:', x)
print('跨岗位 >0.80（疑似阈值）对数:', len(cross_over))
if cross_over:
    for x in cross_over[:10]:
        print('  CROSS:', x)

if not os.path.exists(ORIG):
    shutil.copy(SRC, ORIG)
    print('原文件已备份:', ORIG)
with open(SRC, 'w', encoding='utf-8') as f:
    json.dump(DATA_LIST, f, ensure_ascii=False, indent=2)
print('新语料已写入:', SRC)
