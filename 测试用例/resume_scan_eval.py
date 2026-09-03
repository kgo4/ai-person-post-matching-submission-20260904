# -*- coding: utf-8 -*-
"""
扫描件简历验证集评测脚本
=========================
读取系统对扫描件样本的解析结果，对照黄金集计算 P/R/F1。

运行流程（两阶段）：
  1) 启动系统，依次上传 data/resumes/scan/scan-resume-001.pdf ~ 005.pdf
     并触发 OCR 回退路径（无需手动干预：PDFBox 文本层 <50 字符自动触发）
  2) 从系统中导出每份简历的技能解析结果，保存为 JSON：
     tests/resume-scan-result.json
     格式：[{"caseId": "SCAN-001", "extractedSkills": ["Spark", "Flink", ...]}, ...]
  3) 执行本脚本：python tests/resume_scan_eval.py

输出：tests/resume-scan-eval-result.json（同时打印表格）
"""
import json
import os
import re

BASE = os.path.dirname(os.path.abspath(__file__))
CASES_PATH = os.path.join(BASE, 'resume-scan-test-cases.json')
RESULT_PATH = os.path.join(BASE, 'resume-scan-result.json')
OUT_PATH = os.path.join(BASE, 'resume-scan-eval-result.json')

# 与 5.2 节技能项对齐规则保持一致
NORM_SUFFIX = re.compile(r'(框架|技术|工具|库|平台)$')
SYNONYMS = {}
syn_path = os.path.join(BASE, 'skill-synonyms.json')
if os.path.exists(syn_path):
    with open(syn_path, encoding='utf-8') as f:
        SYNONYMS = json.load(f)


def normalize(s):
    s = s.strip()
    s = s.replace(' ', '').replace('\u3000', '')
    s = s.replace('（', '(').replace('）', ')')
    s = s.lower()
    s = NORM_SUFFIX.sub('', s)
    for k, v in (SYNONYMS or {}).items():
        if s == k.lower() or s == v.lower():
            s = k.lower()
            break
    return s


def align_pair(gold, ext):
    g, e = normalize(gold), normalize(ext)
    if g == e:
        return True
    if g in e or e in g:
        return True
    return False


def case_metrics(expected, extracted):
    if not expected:
        return None
    tp, fp, fn = 0, 0, 0
    matched = [False] * len(expected)
    for ext in extracted:
        hit = False
        for i, exp in enumerate(expected):
            if not matched[i] and align_pair(exp, ext):
                matched[i] = True
                hit = True
                break
        if hit:
            tp += 1
        else:
            fp += 1
    for m in matched:
        if not m:
            fn += 1
    p = tp / (tp + fp) if (tp + fp) else 0.0
    r = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = 2 * p * r / (p + r) if (p + r) else 0.0
    return {'precision': round(p, 3), 'recall': round(r, 3), 'f1': round(f1, 3),
            'tp': tp, 'fp': fp, 'fn': fn}


def main():
    with open(CASES_PATH, encoding='utf-8') as f:
        cases = json.load(f)
    if not os.path.exists(RESULT_PATH):
        print('!! 未找到系统解析结果：%s' % RESULT_PATH)
        print('   请先在系统中上传扫描件样本并导出结果到此文件。')
        print('   期望格式：[{"caseId": "SCAN-001", "extractedSkills": [...]}, ...]')
        return
    with open(RESULT_PATH, encoding='utf-8') as f:
        results = {r['caseId']: r for r in json.load(f)}

    per_case = []
    f1s = []
    for c in cases:
        cid = c['caseId']
        r = results.get(cid, {'caseId': cid, 'extractedSkills': []})
        m = case_metrics(c['expectedSkills'], r.get('extractedSkills', []))
        per_case.append({'caseId': cid, 'format': c['format'],
                         'expected': c['expectedSkills'],
                         'extracted': r.get('extractedSkills', []),
                         'metrics': m})
        if m:
            f1s.append(m['f1'])

    avg_f1 = round(sum(f1s) / len(f1s), 3) if f1s else 0
    summary = {
        'evaluatedAt': __import__('datetime').date.today().isoformat(),
        'sampleCount': len(cases),
        'method': '图片型扫描件 → PDFBox 文本 <50 字符 → 百度 OCR 回退 → 技能抽取',
        'perCase': per_case,
        'overall': {
            'avgF1': avg_f1,
            'meetsThreshold_0_9': avg_f1 >= 0.9,
            'conclusion': '扫描件端到端指标（OCR + 技能抽取）通过验收' if avg_f1 >= 0.9
                          else '扫描件指标未达 0.9，需调整 OCR 预处理或抽取阈值',
        },
    }
    with open(OUT_PATH, 'w', encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print('=== 扫描件验证集评测（%d 例）===' % len(cases))
    print('%-10s %-8s %-8s %-8s' % ('case', 'P', 'R', 'F1'))
    for c in per_case:
        m = c['metrics'] or {}
        print('%-10s %-8s %-8s %-8s' % (c['caseId'], m.get('precision', '-'), m.get('recall', '-'), m.get('f1', '-')))
    print('平均 F1: %.3f   达标(≥0.9): %s' % (avg_f1, '是' if avg_f1 >= 0.9 else '否'))
    print('结果写入:', OUT_PATH)


if __name__ == '__main__':
    main()
