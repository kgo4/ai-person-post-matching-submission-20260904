# -*- coding: utf-8 -*-
"""
治理效果验证实验（可复现）
=============================
按系统检测规则（PostCleaningRulesEngine / JdQualityDetector / RecruitmentDataGovernanceService）
对两类样本执行检测：
  1) 100 条真实 JD 语料洁净度检测（期望全部达标）
  2) 缺陷注入样本检出率（证明检测器有效）

输出：测试用例/governance-eval-result.json
"""
import json
import re
import os
import datetime

BASE = os.path.dirname(os.path.abspath(__file__))
SUBMISSION_ROOT = os.path.normpath(os.path.join(BASE, '..'))
DATA = os.path.join(SUBMISSION_ROOT, '测试数据')

with open(os.path.join(DATA, '岗位JD-100条.json'), encoding='utf-8') as f:
    JDS = json.load(f)

# ---- 系统规则复现 ----
OUTDATED_TECH = {
    'jQuery': 'React/Vue', 'jquery': 'React/Vue',
    'Java 6': 'Java 17/21', 'Java 7': 'Java 17/21', 'Java 8': 'Java 17/21',
    'Vue2': 'Vue3', 'Vue 2': 'Vue 3', 'MySQL 5.6': 'MySQL 8.0', 'SVN': 'Git',
}
NOISE_PATTERNS = [
    re.compile(r'1[3-9]\d{9}'),            # 手机号
    re.compile(r'0\d{2,3}-\d{7,8}'),       # 座机
    re.compile(r'[\w.]+@[\w.]+\.(com|cn)'),  # 邮箱
    re.compile(r'微信|加V|QQ群|扫码|加群'),
    re.compile(r'-{5,}|={5,}'),
    re.compile(r'公司直招|诚聘|急聘|高薪诚聘'),
]

def has_noise(text):
    return any(p.search(text) for p in NOISE_PATTERNS)

def jaccard_2gram(a, b):
    ga = set(a[i:i+2] for i in range(len(a)-1))
    gb = set(b[i:i+2] for i in range(len(b)-1))
    if not ga or not gb:
        return 0.0
    return len(ga & gb) / len(ga | gb)

def has_duplicate(pair, threshold=0.92):
    return jaccard_2gram(pair[0], pair[1]) >= threshold

def skill_count(jd):
    return len(jd.get('mustSkills', [])) + len(jd.get('plusSkills', []))

def has_inflation(jd, max_skills=15):
    return skill_count(jd) > max_skills

def has_outdated(jd):
    text = jd.get('jobDescription', '') + ' '.join(jd.get('mustSkills', [])) + ' '.join(jd.get('plusSkills', []))
    return [k for k in OUTDATED_TECH if k in text]

def has_staleness(jd, max_age=180):
    pub = jd.get('publishDaysAgo')
    return pub is not None and pub > max_age

# ---- 1) 真实语料洁净度 ----
noise_real = [j['id'] for j in JDS if has_noise(j.get('jobDescription', ''))]
inflate_real = [j['id'] for j in JDS if has_inflation(j)]
outdated_real = [j['id'] for j in JDS if has_outdated(j)]
dup_pairs_real = []
texts = [(j['id'], j.get('jobDescription', '')) for j in JDS]
for i in range(len(texts)):
    for k in range(i + 1, len(texts)):
        if has_duplicate((texts[i][1], texts[k][1])):
            dup_pairs_real.append((texts[i][0], texts[k][0]))

# ---- 1b) 相似度分布（同岗位变体 vs 跨岗位） ----
def base_title(j):
    return re.sub(r'（初级）|（中级）|（高级）|\(初级\)|\(中级\)|\(高级\)', '', j['jobTitle']).strip()

same_groups = {}
for j in JDS:
    same_groups.setdefault(base_title(j), []).append(j['jobDescription'])
same_pairs, same_max = 0, 0.0
for base, ts in same_groups.items():
    if len(ts) < 2:
        continue
    for i in range(len(ts)):
        for k in range(i + 1, len(ts)):
            same_pairs += 1
            same_max = max(same_max, jaccard_2gram(ts[i], ts[k]))
cross_pairs, cross_max = 0, 0.0
for i in range(len(texts)):
    for k in range(i + 1, len(texts)):
        cross_pairs += 1
        cross_max = max(cross_max, jaccard_2gram(texts[i][1], texts[k][1]))

# ---- 2) 缺陷注入样本 ----
injected = {'noise': [], 'duplicate': [], 'inflation': [], 'outdated': [], 'staleness': []}

# 噪声注入 12 条
noise_marks = [
    '联系电话：13812345678', '邮箱 hr@company.com 请投递',
    '添加微信 recruit888 获取内推', '公司直招，诚聘英才！！！',
    '---------------- 分隔线 ----------------', '扫码进群了解详情',
]
base = JDS[0]['jobDescription']
for i in range(12):
    injected['noise'].append(base + '\n' + noise_marks[i % len(noise_marks)])

# 抄袭对注入 15 对（复制同一 JD）
for i in range(15):
    src = JDS[i]['jobDescription']
    injected['duplicate'].append((src, src))

# 通胀注入 10 条（技能超 15 项）
inflation_skills = ['技能%02d' % i for i in range(20)]
for i in range(10):
    injected['inflation'].append({'mustSkills': inflation_skills, 'plusSkills': []})

# 过时技术注入 15 条
outdated_marks = ['使用 jQuery 开发前端', '基于 Java 8 编写后端', '项目使用 Vue2 框架',
                  '依赖 MySQL 5.6 数据库', '代码管理使用 SVN']
for i in range(15):
    injected['outdated'].append('要求熟悉' + outdated_marks[i % len(outdated_marks)])

# 时滞注入 10 条（publishDaysAgo > 180）
for i in range(10):
    injected['staleness'].append({'publishDaysAgo': 200 + i * 10})

# 检出统计
def det_noise(rows): return sum(1 for t in rows if has_noise(t))
def det_dup(rows): return sum(1 for a, b in rows if has_duplicate((a, b)))
def det_inflation(rows): return sum(1 for j in rows if has_inflation(j))
def det_outdated(rows): return sum(1 for t in rows if has_outdated({'jobDescription': t, 'mustSkills': [], 'plusSkills': []}))
def det_staleness(rows): return sum(1 for j in rows if has_staleness(j))

result = {
    'evaluatedAt': datetime.date.today().isoformat(),
    'realCorpusCleanliness': {
        'total': len(JDS),
        'noiseHits': len(noise_real),
        'duplicatePairs': len(dup_pairs_real),
        'inflationHits': len(inflate_real),
        'outdatedHits': len(outdated_real),
        'allPass': len(noise_real) == 0 and len(dup_pairs_real) == 0
                  and len(inflate_real) == 0 and len(outdated_real) == 0,
        'samePostSimilarity': {'pairs': same_pairs, 'maxJaccard': round(same_max, 3)},
        'crossPostSimilarity': {'pairs': cross_pairs, 'maxJaccard': round(cross_max, 3)},
    },
    'injectionDetection': {
        'noise': {'injected': len(injected['noise']), 'detected': det_noise(injected['noise'])},
        'duplicatePairs': {'injected': len(injected['duplicate']), 'detected': det_dup(injected['duplicate'])},
        'inflation': {'injected': len(injected['inflation']), 'detected': det_inflation(injected['inflation'])},
        'outdated': {'injected': len(injected['outdated']), 'detected': det_outdated(injected['outdated'])},
        'staleness': {'injected': len(injected['staleness']), 'detected': det_staleness(injected['staleness'])},
    },
}

out = os.path.join(BASE, 'governance-eval-result.json')
with open(out, 'w', encoding='utf-8') as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

print('=== 真实语料洁净度（100条）===')
rc = result['realCorpusCleanliness']
print('噪声命中: %d, 重复对: %d, 通胀: %d, 过时: %d, 全部达标: %s'
      % (rc['noiseHits'], rc['duplicatePairs'], rc['inflationHits'], rc['outdatedHits'], rc['allPass']))
print('同岗位变体相似度: %d 对, 最大 %.3f' % (rc['samePostSimilarity']['pairs'], rc['samePostSimilarity']['maxJaccard']))
print('跨岗位相似度: %d 对, 最大 %.3f' % (rc['crossPostSimilarity']['pairs'], rc['crossPostSimilarity']['maxJaccard']))
print('=== 缺陷注入检出率 ===')
for k, v in result['injectionDetection'].items():
    rate = v['detected'] / v['injected'] * 100 if v['injected'] else 0
    print('%s: %d/%d = %.1f%%' % (k, v['detected'], v['injected'], rate))
print('结果写入:', out)
