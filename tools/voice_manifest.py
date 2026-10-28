"""Lists every Japanese sentence the app can say, for recording with VOICEVOX.

Each line of the output (JSON list) is {"key", "voice", "kana", "text"}:
  key   what the app looks up: sha1 of voice|kana|kanji sentence, first 16 hex
  text  the same sentence written with kanji, which gives VOICEVOX a better accent
The app uses the same rules (Speech.kt): markup {kanji|reading} becomes the reading, then the
text is split into sentences.
"""
import hashlib, json, re, sys, os

ASSETS = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets"
GROUP = re.compile(r"\{([^{}|]+)\|([^{}|]+)\}")
SENT = re.compile(r"[^。！？!?\n]+[。！？!?」』）]*")
JP = re.compile(r"[぀-ヿ一-鿿々]")

def kana(s): return GROUP.sub(lambda m: m.group(2), s)
def plain(s): return GROUP.sub(lambda m: m.group(1), s)

def sentences(s):
    """Pairs of (kana sentence, kanji sentence), split the same way in both forms."""
    k = [x.strip() for x in SENT.findall(kana(s)) if x.strip()]
    p = [x.strip() for x in SENT.findall(plain(s)) if x.strip()]
    if len(k) != len(p): p = k
    return list(zip(k, p))

def speakable(s):
    """Japanese to be read out: mostly Japanese, no English letters."""
    t = plain(s).strip()
    if not t or re.search(r"[A-Za-z]", t): return False
    jp = len(JP.findall(t))
    return jp >= 1 and jp >= len(re.sub(r"\s", "", t)) * 0.6

def key(voice, k, p): return hashlib.sha1(f"{voice}|{k}|{p}".encode()).hexdigest()[:16]

out = {}
def add(s, voice="N"):
    if not isinstance(s, str) or not speakable(s): return
    for k, p in sentences(s):
        h = key(voice, k, p)
        if h not in out: out[h] = {"key": h, "voice": voice, "kana": k, "text": p}

def walk(o, voice="N"):
    if isinstance(o, dict):
        sp = o.get("s") or o.get("speaker")
        v = sp if sp in ("M", "F") else voice
        for kk, val in o.items():
            walk(val, v if kk in ("jp", "text", "line") else voice)
    elif isinstance(o, list):
        for x in o: walk(x, voice)
    elif isinstance(o, str):
        add(o, voice)

for f in sorted(os.listdir(ASSETS)):
    if not f.endswith(".json") or f == "strokes.json": continue
    data = json.load(open(os.path.join(ASSETS, f), encoding="utf-8"))
    if f == "vocab.json":
        for r in data:
            add(r[1] if r[1] == r[2] else "{%s|%s}" % (r[1], r[2])); add(r[2]); add(r[5])
        continue
    if f == "kanji.json":
        for k in data:
            said = [x.split("(")[0].replace(".", "").replace("-", "") for x in k["kun"][:1] + k["on"][:1]]
            said = "、".join(x for x in said if x)
            if said: add(said)
        continue
    if f == "grammar.json":
        for g in data:
            for q in g.get("quiz", []):
                for o in q.get("options", []): add(q.get("q", "").replace("＿＿", o))
    walk(data)

json.dump(sorted(out.values(), key=lambda x: x["key"]), sys.stdout, ensure_ascii=False)
