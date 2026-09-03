#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Categorise missed JaCoCo lines so we can pick the cheapest coverage wins."""
import csv
import os
import re
import sys
from collections import defaultdict

CSV_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'target', 'site', 'jacoco', 'jacoco.csv'))

BUCKETS = [
    ('entity', re.compile(r'(entity|domain/model|\.po$|\.entity\.)', re.I)),
    ('dto/vo/bo', re.compile(r'\.(dto|vo|bo|query|request|response|form|param)\.', re.I)),
    ('enums/const', re.compile(r'\.(enums?|constant|const)\.', re.I)),
    ('config', re.compile(r'\.(config|configuration)\.', re.I)),
    ('controller', re.compile(r'\.controller\.', re.I)),
    ('service-impl', re.compile(r'\.service\..*\.impl\.', re.I)),
    ('service-other', re.compile(r'\.(service|application|facade)\.', re.I)),
    ('infra/mapper', re.compile(r'\.(infrastructure|mapper|vector|kg\.|rag\.|websocket|listener|schedule)\.', re.I)),
    ('agent/ai', re.compile(r'\.(agent|ai)\.', re.I)),
    ('util/common', re.compile(r'\.(util|utils|common|exception|handler|aspect|interceptor)\.', re.I)),
]


def bucket_of(fqcn):
    for name, rx in BUCKETS:
        if rx.search(fqcn):
            return name
    return 'other'


def main():
    rows = list(csv.DictReader(open(CSV_PATH, newline='', encoding='utf-8')))
    agg = defaultdict(lambda: {'lm': 0, 'lc': 0, 'bm': 0, 'bc': 0, 'n': 0, 'zero': 0})
    for r in rows:
        fqcn = r['PACKAGE'] + '.' + r['CLASS']
        b = agg[bucket_of(fqcn)]
        lm, lc = int(r['LINE_MISSED']), int(r['LINE_COVERED'])
        bm, bc = int(r['BRANCH_MISSED']), int(r['BRANCH_COVERED'])
        b['lm'] += lm
        b['lc'] += lc
        b['bm'] += bm
        b['bc'] += bc
        b['n'] += 1
        if lc == 0 and lm > 0:
            b['zero'] += 1

    print('%-16s %6s %8s %8s %8s %8s' % ('BUCKET', 'CLS', 'MISSED_L', 'TOTAL_L', 'LINE%', 'BRCH%'))
    print('-' * 62)
    tot_m = tot_c = 0
    for name, v in sorted(agg.items(), key=lambda kv: -kv[1]['lm']):
        total = v['lm'] + v['lc']
        tot_m += v['lm']
        tot_c += v['lc']
        print('%-16s %6d %8d %8d %7.1f%% %7.1f%%  (uncovered cls: %d)' % (
            name, v['n'], v['lm'], total,
            (v['lc'] * 100.0 / total) if total else 0,
            (v['bc'] * 100.0 / (v['bc'] + v['bm'])) if (v['bc'] + v['bm']) else 0,
            v['zero']))
    print('-' * 62)
    print('%-16s %6d %8d %8d %7.2f%%' % ('TOTAL', len(rows), tot_m, tot_m + tot_c, tot_c * 100.0 / (tot_m + tot_c)))

    # Small-class cheap wins: classes with 0 coverage, few lines and few branches
    print()
    print('=== cheap wins: fully uncovered classes, sorted by (missed lines) DESC ===')
    zero = []
    for r in rows:
        lm, lc = int(r['LINE_MISSED']), int(r['LINE_COVERED'])
        if lc == 0 and lm > 0:
            zero.append((int(r['LINE_MISSED']), int(r['BRANCH_MISSED']), r['PACKAGE'] + '.' + r['CLASS']))
    zero.sort(reverse=True)
    cum = 0
    for lm, bm, name in zero:
        cum += lm
        print('%5d  (cum %6d)  br=%-4d  %s' % (lm, cum, bm, name))
    print()
    print('total lines in fully-uncovered classes: %d' % cum)
    return 0


if __name__ == '__main__':
    sys.exit(main())
