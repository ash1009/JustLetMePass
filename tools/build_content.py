#!/usr/bin/env python3
"""Turns content/ into the compact files the app reads from app/src/main/assets/.
Run from the repo root:  python3 tools/build_content.py
"""
import csv, json, os, re, glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, 'content', 'raw')
WR = os.path.join(ROOT, 'content', 'written')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets')
os.makedirs(OUT, exist_ok=True)

def dump(name, data):
    with open(os.path.join(OUT, name), 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
    print(f'{name}: {os.path.getsize(os.path.join(OUT, name)) // 1024} KB')

def load(name):
    return json.load(open(os.path.join(WR, name), encoding='utf-8'))

# Known slips in the source word lists.
FIX = {('下る', 'さがる'): ('下がる', 'さがる')}

# ---- vocab ----
examples = {}
for f in sorted(glob.glob(os.path.join(WR, 'ex*.jsonl'))):
    for line in open(f, encoding='utf-8'):
        if line.strip():
            o = json.loads(line)
            examples[o['id']] = (o['jp'], o['en'])

# Checked fixes to readings and meanings (the source list has slips), by word id.
WORD_FIXES = {}
for f in sorted(glob.glob(os.path.join(WR, 'word_fixes', '*.json'))):
    WORD_FIXES.update(json.load(open(f, encoding='utf-8')))
vocab, seen = [], set()
for lvl in [5, 4, 3, 2, 1]:
    for i, r in enumerate(csv.DictReader(open(os.path.join(RAW, f'n{lvl}.csv'), encoding='utf-8'))):
        w, rd = r['expression'].strip(), r['reading'].strip()
        if (w, rd) in seen:
            continue
        seen.add((w, rd))
        if not rd:
            rd = w
        w, rd = FIX.get((w, rd), (w, rd))
        vid = f'v{lvl}-{i:04d}'
        ex = examples.get(vid, ('', ''))
        # Trim very long meaning lists to the first few senses.
        m = r['meaning'].strip()
        parts, depth, cur = [], 0, ''
        for ch in m:
            depth += ch == '('
            depth -= ch == ')'
            if ch in ';,' and depth == 0:
                if cur.strip(): parts.append(cur.strip())
                cur = ''
            else:
                cur += ch
        if cur.strip(): parts.append(cur.strip())
        if len(parts) > 4:
            m = ', '.join(parts[:4])
        fx = WORD_FIXES.get(vid, {})
        rd = fx.get('reading', rd)
        m = fx.get('meaning', m)
        vocab.append([vid, w, rd, m, lvl, ex[0], ex[1]])
# Learn the most useful words first: order each level by how often the word turns up in all the
# written sentences, readings and dialogues (a rough stand-in for everyday frequency).
GROUP = re.compile(r'\{([^{}|]+)\|([^{}|]+)\}')
corpus = []
for f in glob.glob(os.path.join(WR, '*.json*')):
    corpus.append(GROUP.sub(lambda m: m.group(1), open(f, encoding='utf-8').read()))
corpus = '\n'.join(corpus)
def score(v):
    w = v[1].replace('～', '')
    if not w:
        return 0
    n = corpus.count(w)
    if not re.search(r'[一-鿿]', w) and len(w) <= 2:
        n *= 0.3
    if '～' in v[1]:
        n *= 0.05   # affixes like ～人 match inside every other word
    elif len(w) == 1:
        n *= 0.4    # so do single kanji
    return n
vocab.sort(key=lambda v: (-v[4], -score(v)))
dump('vocab.json', vocab)

# ---- kanji ----
kd = json.load(open(os.path.join(RAW, 'kanji.json'), encoding='utf-8'))
def kun(s):
    s = s.replace('-', '')
    if '.' in s:
        a, b = s.split('.', 1)
        return f'{a}({b})'
    return s
kanji = []
for c, v in kd.items():
    if not v.get('jlpt_new'):
        continue
    means = [m for m in v['meanings'] if 'radical' not in m.lower() and '**' not in m][:3] or v['meanings'][:1]
    kanji.append({
        'c': c, 'l': v['jlpt_new'], 's': v['strokes'],
        'm': means,
        'on': v['readings_on'][:3], 'kun': list(dict.fromkeys(kun(k) for k in v['readings_kun']))[:4],
        'f': v.get('freq') or 9999,
    })
kanji.sort(key=lambda k: (-k['l'], k['f']))
dump('kanji.json', kanji)

# ---- strokes ----
strokes = json.load(open(os.path.join(RAW, 'kanjivg', 'strokes.json'), encoding='utf-8'))
dump('strokes.json', strokes)

# ---- written content ----
dump('grammar.json', sum((load(f'grammar_n{l}.json') for l in [5, 4, 3, 2, 1]), []))
dump('reading.json', sum((load(f'reading_n{l}.json') for l in [5, 4, 3, 2, 1]), []))
dump('listening.json', sum((load(f'listening_n{l}.json') for l in [5, 4, 3, 2, 1]), []))
dump('daily.json', load('daily.json'))
stories = []
for f in sorted(glob.glob(os.path.join(WR, 'stories_*.json'))):
    stories += json.load(open(f, encoding='utf-8'))
stories.sort(key=lambda x: (-x['level'], x['id']))
dump('stories.json', stories)
sentences = []
for f in sorted(glob.glob(os.path.join(WR, 'sentences_*.json'))):
    sentences += json.load(open(f, encoding='utf-8'))
sentences.sort(key=lambda x: (-x['level'], x['id']))
dump('sentences.json', sentences)
papers = []
for f in sorted(glob.glob(os.path.join(WR, 'mocks', '*.json'))):
    papers.append(json.load(open(f, encoding='utf-8')))
papers.sort(key=lambda x: (-x['level'], x['id']))
dump('mocks.json', papers)
notes = {'kanji': [], 'words': []}
for f in sorted(glob.glob(os.path.join(WR, 'notes', 'kanji_*.json'))):
    notes['kanji'] += json.load(open(f, encoding='utf-8'))
for f in sorted(glob.glob(os.path.join(WR, 'notes', 'words_*.json'))):
    notes['words'] += json.load(open(f, encoding='utf-8'))
dump('notes.json', notes)
dump('katakana.json', load('katakana.json'))
# Groups of words a learner could mix up by meaning: never wrong options for each other.
groups = []
for f in sorted(glob.glob(os.path.join(WR, 'confusable', 'n*.json'))):
    groups += [g for g in json.load(open(f, encoding='utf-8')) if len(g) >= 2]
dump('confusable.json', groups)

print('words', len(vocab), 'with examples', sum(1 for v in vocab if v[5]), 'kanji', len(kanji))
