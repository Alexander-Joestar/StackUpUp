# -*- coding: utf-8 -*-
"""解析 JetBrains inspection HTML 导出,提取叶子问题为 TSV(去重、过滤无效)。

用法: python parse_inspections.py <check.html> <out.tsv>
输出列: level \t path \t line \t problem
- 只取"叶子问题"label(内含级别 span);文件/inspection 汇总节点不带级别 span,跳过。
- path 取该节点 div#dID 内第一个 file:// 链接
- 过滤:无文件的、含 /build/、/build-logic/、.idea、gradle 缓存、依赖源码路径
"""
import re
import sys
import html as html_mod

LEAF_RE = re.compile(
    r'<li><label for="(\d+)">(.*?)</label><input[^>]*?id="\1"[^>]*?>\s*'
    r'<div id="d\1"[^>]*>(.*?)</div>',
    re.S,
)
# inspection 节点: <b>检查名</b>&nbsp;inspection&nbsp;
INSP_RE = re.compile(r'<label for="(\d+)"><b>(.*?)</b>\s*&nbsp;inspection', re.S)
LEVEL_RE = re.compile(r'<span style="margin:1px;background:[^"]*">([^<]*)</span>')
FILE_RE = re.compile(r'<a href="file://([^"#]*?)(?:#(\d+))?"')
STRIP_RE = re.compile(r'<[^>]+>')

# 无效路径段(依赖/生成物/外部内容)
BAD_SEG = (
    '/build/', '\\build\\',
    '/build-logic',
    '/.idea/',
    '/gradle/', '/gradle-',
    '/.gradle/',
    '/run/',
    '/.git/',
    '.jar', '.class',
    '/local-dev-mods/',
    '/captures/',
)


def strip_tags(s: str) -> str:
    s = STRIP_RE.sub('', s)
    s = html_mod.unescape(s)
    return ' '.join(s.split())


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, encoding='utf-8', errors='replace') as fin:
        data = fin.read()
    seen = set()
    rows = 0
    # 收集 inspection 节点位置: (pos, name)
    insp_pos = [(m.start(), strip_tags(m.group(2))) for m in INSP_RE.finditer(data)]
    import bisect
    with open(dst, 'w', encoding='utf-8', newline='\n') as fout:
        fout.write('level\tpath\tline\tproblem\tinspection\n')
        for m in LEAF_RE.finditer(data):
            label_html = m.group(2)
            lvl_m = LEVEL_RE.search(label_html)
            if not lvl_m:
                continue  # 汇总节点,非叶子问题
            level = lvl_m.group(1)
            problem = strip_tags(label_html)
            body = m.group(3)
            fm = FILE_RE.search(body)
            path = fm.group(1) if fm else 'no-path'
            line = fm.group(2) if fm and fm.group(2) else ''
            # 过滤
            if path == 'no-path':
                continue
            if any(s in path for s in BAD_SEG):
                continue
            # 最近的父 inspection 节点
            idx = bisect.bisect_right(insp_pos, (m.start(), '')) - 1
            insp = insp_pos[idx][1] if idx >= 0 else ''
            key = (level, path, line, problem)
            if key in seen:
                continue
            seen.add(key)
            rows += 1
            fout.write(f'{level}\t{path}\t{line}\t{problem}\t{insp}\n')
    print(f'leaf rows: {rows}')


if __name__ == '__main__':
    main()
