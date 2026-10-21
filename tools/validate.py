#!/usr/bin/env python3
"""Checks content files: JSON (or JSONL), furigana markup, quiz answers.
Keys ending in _raw are exempt from the markup check."""
import json, re, sys

KANJI = re.compile(r'[㐀-䶿一-鿿豈-﫿々〆ヶ]')
GROUP = re.compile(r'\{([^{}|]+)\|([^{}|]+)\}')
HIRA = re.compile(r'^[ぁ-ゟー]+$')

problems = []

def check_text(s, where):
    rest = GROUP.sub('', s)
    if '{' in rest or '}' in rest or '|' in rest:
        problems.append(f'{where}: broken braces in: {s}')
    bad = KANJI.findall(rest)
    if bad:
        problems.append(f'{where}: kanji without reading {"".join(sorted(set(bad)))} in: {s}')
    for m in GROUP.finditer(s):
        k, r = m.group(1), m.group(2)
        if not KANJI.search(k):
            problems.append(f'{where}: group has no kanji {m.group(0)}')
        if not HIRA.match(r):
            problems.append(f'{where}: reading not hiragana {m.group(0)}')

def walk(o, where):
    if isinstance(o, dict):
        if ('options' in o or 'options_raw' in o) and 'answer' in o:
            opts = o.get('options', o.get('options_raw'))
            if not isinstance(o['answer'], int) or not (0 <= o['answer'] < len(opts)):
                problems.append(f'{where}: answer index out of range')
            if len(set(map(str, opts))) != len(opts):
                problems.append(f'{where}: duplicate options')
            if len(opts) < 3:
                problems.append(f'{where}: fewer than 3 options')
        for k, v in o.items():
            if k.endswith('_raw'):
                continue
            walk(v, f'{where}.{k}')
    elif isinstance(o, list):
        for i, v in enumerate(o):
            walk(v, f'{where}[{i}]')
    elif isinstance(o, str):
        check_text(o, where)

def main(path):
    txt = open(path, encoding='utf-8').read()
    try:
        data = json.loads(txt)
        items = [data]
    except json.JSONDecodeError:
        items = []
        for n, line in enumerate(txt.splitlines(), 1):
            if not line.strip():
                continue
            try:
                items.append(json.loads(line))
            except json.JSONDecodeError as e:
                problems.append(f'line {n}: bad JSON ({e})')
    ids = []
    def collect(o):
        if isinstance(o, dict):
            if 'id' in o: ids.append(o['id'])
            for v in o.values(): collect(v)
        elif isinstance(o, list):
            for v in o: collect(v)
    for i, it in enumerate(items):
        walk(it, f'#{i}')
        collect(it)
    dup = {x for x in ids if ids.count(x) > 1}
    if dup:
        problems.append(f'duplicate ids: {sorted(dup)[:20]}')
    if problems:
        print(f'{len(problems)} problems')
        for p in problems[:200]:
            print(' -', p)
        sys.exit(1)
    print(f'OK ({len(items)} top-level items, {len(ids)} ids)')

if __name__ == '__main__':
    main(sys.argv[1])
