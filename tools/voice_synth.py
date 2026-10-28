"""Records sentences from a manifest with a running VOICEVOX engine.

usage: voice_synth.py manifest.json out_dir [part] [parts]
Writes out_dir/<key>.ogg (Opus, mono). The kanji text gives the best accent; if VOICEVOX reads it
differently from our furigana, the kana text is used instead so the reading is always ours.
"""
import json, os, re, subprocess, sys, urllib.parse, urllib.request
from concurrent.futures import ThreadPoolExecutor

URL = os.environ.get("VOICEVOX_URL", "http://127.0.0.1:50021")
# Voices: N narrator (calm adult woman), M man, F woman. Credit: VOICEVOX:No.7, 玄野武宏, 冥鳴ひまり.
NAMES = {"N": "No.7", "M": "玄野武宏", "F": "冥鳴ひまり"}

def post(path, params, body=b""):
    req = urllib.request.Request(URL + path + "?" + urllib.parse.urlencode(params), data=body,
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=300) as r:
        return r.read()

def speaker_ids():
    out = {}
    for sp in json.loads(urllib.request.urlopen(URL + "/speakers", timeout=60).read()):
        for v, name in NAMES.items():
            if sp["name"] == name:
                style = next((s for s in sp["styles"] if s["name"] == "ノーマル"), sp["styles"][0])
                out[v] = style["id"]
    missing = set(NAMES) - set(out)
    if missing: sys.exit(f"voices not found: {missing}")
    return out

def hira(s): return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)

VOWEL = {}
for row, v in [("あかがさざただなはばぱまやらわゃぁ", "あ"), ("いきぎしじちぢにひびぴみりぃ", "い"), ("うくぐすずつづぬふぶぷむゆるゅぅ", "う"),
               ("えけげせぜてでねへべぺめれぇ", "え"), ("おこごそぞとどのほぼぽもよろをょぉ", "お")]:
    for c in row: VOWEL[c] = v

def norm(s):
    """A reading in a form where small spelling differences (は/わ, おう/おー) do not count."""
    s = hira(re.sub(r"[^぀-ヿー]", "", s))
    out = []
    for c in s:
        if c == "ー" and out: c = VOWEL.get(out[-1], "")
        out.append(c)
    s = "".join(out)
    for a, b in [("は", "わ"), ("へ", "え"), ("を", "お"), ("づ", "ず"), ("ぢ", "じ"), ("おう", "おお"), ("えい", "ええ"), ("ゃう", "ゃあ")]:
        s = s.replace(a, b)
    s = re.sub(r"([こそとのほもよろごぞどぼぽょ])う", r"\1お", s)
    s = re.sub(r"([えけせてねへめれげぜでべぺ])い", r"\1え", s)
    return s

def query(text, sid):
    return json.loads(post("/audio_query", {"text": text, "speaker": sid}))

def said(q):
    return "".join(m["text"] for ph in q["accent_phrases"] for m in ph["moras"])

def record(item, ids, out_dir):
    path = os.path.join(out_dir, item["key"] + ".ogg")
    if os.path.exists(path): return "skip"
    sid = ids[item["voice"]]
    q = query(item["text"], sid)
    used = "kanji"
    if item["text"] != item["kana"] and norm(said(q)) != norm(item["kana"]):
        q = query(item["kana"], sid)
        used = "kana"
    q["prePhonemeLength"] = 0.05
    q["postPhonemeLength"] = 0.15
    q["outputSamplingRate"] = 24000
    wav = post("/synthesis", {"speaker": sid}, json.dumps(q).encode())
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "wav", "-i", "pipe:0", "-ac", "1", "-c:a", "libopus",
                    "-b:a", "20k", "-application", "voip", path + ".tmp.ogg"], input=wav, check=True)
    os.replace(path + ".tmp.ogg", path)
    return used

def main():
    items = json.load(open(sys.argv[1], encoding="utf-8"))
    out_dir = sys.argv[2]
    part, parts = (int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (0, 1)
    items = items[part::parts]
    os.makedirs(out_dir, exist_ok=True)
    ids = speaker_ids()
    counts = {}
    def one(it):
        try: return record(it, ids, out_dir)
        except Exception as e: return "fail: " + type(e).__name__
    with ThreadPoolExecutor(2) as ex:
        for i, r in enumerate(ex.map(one, items)):
            k = r if not r.startswith("fail") else "fail"
            counts[k] = counts.get(k, 0) + 1
            if i % 500 == 0: print(i, len(items), counts, flush=True)
    print("done", counts)

if __name__ == "__main__":
    main()
