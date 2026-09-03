# -*- coding: utf-8 -*-
"""
AccuracyEval — 多源异构数据驱动岗位能力图谱系统 · 测试用例评测脚本
=====================================================
评测对象：提交包 outputs/contest-submission-20260830/tests/ 下的测试用例
评测内容：
  1) JD 测试用例（jd-100-test-cases.json）与岗位语料（测试数据/岗位JD-100条.json）的标签对齐校验
     - jobTitle 精确对齐率
     - mustSkills 子集对齐率（断言技能集合 ⊆ 语料必备技能集合）
     - 断言字段完整性（minPrecision / minRecall / noiseScoreBelow70 齐备）
  2) 简历测试用例（resume-test-cases.json）结构校验（20 条、格式覆盖、阈值配置）

运行方式：python accuracy_eval.py
输出：accuracy-eval-result.json（随提交包留存，供复审留痕）
"""
import json
import os
import datetime

BASE = os.path.dirname(os.path.abspath(__file__))
SUBMISSION_ROOT = os.path.normpath(os.path.join(BASE, '..'))
DATA_DIR = os.path.join(SUBMISSION_ROOT, '测试数据')


def load(name):
    with open(os.path.join(BASE, name), encoding='utf-8') as f:
        return json.load(f)


def load_data(name):
    with open(os.path.join(DATA_DIR, name), encoding='utf-8') as f:
        return json.load(f)


def main():
    cases = load('jd-100-test-cases.json')
    jds = load_data('岗位JD-100条.json')
    resumes = load('resume-test-cases.json')

    jd_by_id = {j['id']: j for j in jds}

    total = len(cases)
    title_ok = 0
    skill_ok = 0
    assert_field_ok = 0
    detail = []

    for c in cases:
        jd = jd_by_id.get(c.get('inputId'))
        a = c.get('assertions', {})
        # 字段完整性：jobTitle / mustSkills / minPrecision / minRecall / noiseScoreBelow70
        fields_ok = (
            'jobTitle' in a and 'mustSkills' in a
            and 'minPrecision' in a and 'minRecall' in a
            and 'noiseScoreBelow70' in a
        )
        if fields_ok:
            assert_field_ok += 1
        # jobTitle 对齐
        t_ok = bool(jd) and a.get('jobTitle') == jd.get('jobTitle')
        if t_ok:
            title_ok += 1
        # mustSkills 子集对齐
        ams = set(a.get('mustSkills', []))
        jms = set(jd.get('mustSkills', [])) if jd else set()
        s_ok = bool(ams) and ams.issubset(jms)
        if s_ok:
            skill_ok += 1
        detail.append({
            'caseId': c.get('caseId'),
            'titleAligned': t_ok,
            'skillAligned': s_ok,
            'assertionFieldsComplete': fields_ok,
        })

    # 简历用例结构校验
    resume_total = len(resumes)
    resume_fmt_ok = sum(1 for r in resumes if str(r.get('format', '')).upper() in ('PDF', 'DOCX'))
    resume_threshold_ok = sum(1 for r in resumes if float(r.get('threshold', 0)) >= 0.9)
    resume_expected_ok = sum(1 for r in resumes if r.get('expectedSkills'))

    result = {
        'evaluatedAt': datetime.date.today().isoformat(),
        'jdCases': {
            'total': total,
            'titleExactAligned': title_ok,
            'titleExactAlignedRate': round(title_ok / total, 4) if total else 0,
            'mustSkillsSubsetAligned': skill_ok,
            'mustSkillsSubsetAlignedRate': round(skill_ok / total, 4) if total else 0,
            'assertionFieldsComplete': assert_field_ok,
            'assertionFieldsCompleteRate': round(assert_field_ok / total, 4) if total else 0,
        },
        'resumeCases': {
            'total': resume_total,
            'formatPdfDocxCovered': resume_fmt_ok,
            'thresholdGte90': resume_threshold_ok,
            'expectedSkillsPresent': resume_expected_ok,
        },
        'details': detail,
    }

    out = os.path.join(BASE, 'accuracy-eval-result.json')
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    jd = result['jdCases']
    print('JD 用例总数: %d' % total)
    print('jobTitle 精确对齐率: %.2f%% (%d/%d)' % (jd['titleExactAlignedRate'] * 100, jd['titleExactAligned'], total))
    print('mustSkills 子集对齐率: %.2f%% (%d/%d)' % (jd['mustSkillsSubsetAlignedRate'] * 100, jd['mustSkillsSubsetAligned'], total))
    print('断言字段完整率: %.2f%% (%d/%d)' % (jd['assertionFieldsCompleteRate'] * 100, jd['assertionFieldsComplete'], total))
    print('简历用例: %d 条，PDF/DOCX 覆盖 %d，阈值≥0.9 %d，期望技能齐全 %d'
          % (resume_total, resume_fmt_ok, resume_threshold_ok, resume_expected_ok))
    print('结果已写入: %s' % out)


if __name__ == '__main__':
    main()
