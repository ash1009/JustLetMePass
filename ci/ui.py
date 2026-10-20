#!/usr/bin/env python3
"""Find something on the emulator screen by its text and tap it. Used only for test screenshots.

  ui.py tap "Update"        tap the first thing whose text or description equals "Update"
  ui.py has "Update"        exit 0 if it is on screen, 1 if not
  ui.py texts               print every text on screen
  ui.py drag "Tidy up" -600 0   drag from that thing by so many pixels (to scroll a row sideways)
  ui.py hold "Travel Co"        press and hold it
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def dump():
    for _ in range(3):
        subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
        out = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout
        text = out.decode("utf-8", "ignore")
        if "<hierarchy" in text:
            return ET.fromstring(text[text.index("<?xml") if "<?xml" in text else text.index("<hierarchy"):])
        time.sleep(1)
    return None


def nodes(root):
    for node in root.iter("node"):
        yield node.get("text") or "", node.get("content-desc") or "", node.get("bounds") or ""


def find(root, wanted, nth=0):
    hits = []
    for text, desc, bounds in nodes(root):
        if text == wanted or desc == wanted:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                # Things parked off the left of the screen (a closed menu) do not count.
                # Nor do things waiting off the right (the next page of a pager).
                if x1 >= 0 and x1 < 1080 and x2 > x1 and y2 > y1:
                    hits.append(((x1 + x2) // 2, (y1 + y2) // 2))
    return hits[nth] if len(hits) > nth else None


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    root = dump()
    if root is None:
        print("could not read the screen")
        return 1
    cmd = sys.argv[1]
    if cmd == "texts":
        for text, desc, _ in nodes(root):
            if text or desc:
                print(repr(text), repr(desc))
        return 0
    wanted = sys.argv[2]
    nth = int(sys.argv[3]) if len(sys.argv) > 3 and cmd != "drag" else 0
    hit = find(root, wanted, nth)
    if cmd == "has":
        return 0 if hit else 1
    if cmd == "drag":
        if not hit:
            print(f"not on screen: {wanted!r}")
            return 1
        dx, dy = int(sys.argv[3]), int(sys.argv[4])
        subprocess.run(["adb", "shell", "input", "swipe", str(hit[0]), str(hit[1]), str(hit[0] + dx), str(hit[1] + dy), "400"])
        return 0
    if cmd == "hold":
        if not hit:
            print(f"not on screen: {wanted!r}")
            return 1
        subprocess.run(["adb", "shell", "input", "swipe", str(hit[0]), str(hit[1]), str(hit[0]), str(hit[1]), "900"])
        return 0
    if cmd == "tap":
        if not hit:
            print(f"not on screen: {wanted!r}")
            return 1
        subprocess.run(["adb", "shell", "input", "tap", str(hit[0]), str(hit[1])])
        print(f"tapped {wanted!r} at {hit}")
        return 0
    return 2


if __name__ == "__main__":
    sys.exit(main())
