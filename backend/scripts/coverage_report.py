#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Summarise JaCoCo CSV report: bundle totals, per-package and worst classes."""
import csv
import os
import sys

CSV_PATH = os.path.join(os.path.dirname(__file__), '..', 'target', 'site', 'jacoco', 'jacoco.csv')


def pct(covered, total):
    return 0.0 if total == 0 else covered * 100.0 / total


def main():
    path = os.path.abspath(CSV_PATH)
    if not os.path.exists(path):
        print('jacoco.csv not found: %s' % path)
        return 1

    rows = []
    with open(path, newline='', encoding='utf-8') as fh:
        for r in csv.DictReader(fh):
            rows.append({
                'pkg': r['PACKAGE'],
                'cls': r['CLASS'],
                'im': int(r['INSTRUCTION_MISSED']),
                'ic': int(r['INSTRUCTION_COVERED']),
                'bm': int(r['BRANCH_MISSED']),
                'bc': int(r['BRANCH_COVERED']),
                'lm': int(r['LINE_MISSED']),
                'lc': int(r['LINE_COVERED']),
            })

    def tot(key_m, key_c):
        m = sum(r[key_m] for r in rows)
        c = sum(r[key_c] for r in rows)
        return m, c

    lm, lc = tot('lm', 'lc')
    bm, bc = tot('bm', 'bc')
    im_, ic = tot('im', 'ic')

    print('=' * 78)
    print('BUNDLE COVERAGE')
    print('=' * 78)
    print('classes            : %d' % len(rows))
    print('LINE   covered/total: %6d / %6d  = %6.2f%%' % (lc, lc + lm, pct(lc, lc + lm)))
    print('BRANCH covered/total: %6d / %6d  = %6.2f%%' % (bc, bc + bm, pct(bc, bc + bm)))
    print('INSTR  covered/total: %6d / %6d  = %6.2f%%' % (ic, ic + im_, pct(ic, ic + im_)))

    gap_line = 0.70 * (lc + lm) - lc
    gap_branch = 0.70 * (bc + bm) - bc
    print()
    print('To reach 70%% LINE  : need %d more covered lines' % max(0, int(gap_line) + 1))
    print('To reach 70%% BRANCH: need %d more covered branches' % max(0, int(gap_branch) + 1))

    # Per package
    pkg = {}
    for r in rows:
        p = pkg.setdefault(r['pkg'], {'lm': 0, 'lc': 0, 'bm': 0, 'bc': 0, 'n': 0})
        p['lm'] += r['lm']
        p['lc'] += r['lc']
        p['bm'] += r['bm']
        p['bc'] += r['bc']
        p['n'] += 1

    print()
    print('=' * 78)
    print('TOP 40 PACKAGES BY MISSED LINES')
    print('=' * 78)
    print('%-64s %8s %8s %7s %7s' % ('PACKAGE', 'MISS_L', 'TOT_L', 'LINE%', 'BRCH%'))
    for name, v in sorted(pkg.items(), key=lambda kv: -kv[1]['lm'])[:40]:
        print('%-64s %8d %8d %6.1f%% %6.1f%%' % (
            name[-64:], v['lm'], v['lm'] + v['lc'],
            pct(v['lc'], v['lc'] + v['lm']),
            pct(v['bc'], v['bc'] + v['bm'])))

    print()
    print('=' * 78)
    print('TOP 60 CLASSES BY MISSED LINES')
    print('=' * 78)
    print('%-70s %7s %7s %7s' % ('CLASS', 'MISS_L', 'TOT_L', 'LINE%'))
    for r in sorted(rows, key=lambda r: -r['lm'])[:60]:
        print('%-70s %7d %7d %6.1f%%' % (
            (r['pkg'] + '.' + r['cls'])[-70:], r['lm'], r['lm'] + r['lc'],
            pct(r['lc'], r['lc'] + r['lm'])))

    # count fully uncovered classes
    zero = [r for r in rows if r['lc'] == 0 and r['lm'] > 0]
    print()
    print('fully-uncovered classes: %d (missed lines: %d)' % (len(zero), sum(r['lm'] for r in zero)))
    zero_pkg = {}
    for r in zero:
        zero_pkg[r['pkg']] = zero_pkg.get(r['pkg'], 0) + r['lm']
    print('--- top packages of fully-uncovered classes ---')
    for name, v in sorted(zero_pkg.items(), key=lambda kv: -kv[1])[:25]:
        print('  %-64s %6d' % (name[-64:], v))
    return 0


if __name__ == '__main__':
    sys.exit(main())
