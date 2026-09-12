#!/usr/bin/env python3
"""Rasterizes DejaVu Sans once into 1-bit glyph masks and writes tools/fontdata.py.

Run on a machine with a headless Chromium (the CI pipeline never runs this: the
output module is committed data, and the renderer stays standard-library only).

  python3 tools/fonts/build_font_table.py /path/to/chrome

Variants: regular 20/24/28 px and bold 24/28 px — drawn at 2× and box-downsampled
by the tile renderer, i.e. 10/12/14 px text on a tile. Charset: ASCII, Latin-1
letters, Turkish letters and a few map symbols.
"""
import base64
import html
import json
import os
import re
import subprocess
import sys
import tempfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "fontdata.py")

CHARS = ([chr(c) for c in range(32, 127)]
         + [chr(c) for c in range(0xC0, 0x100) if chr(c) not in "×÷"]
         + list("ĞğİıŞşŒœŠšŽžŸ") + list("–—’‘“”·°▲•…"))
VARIANTS = [("r20", '20px "DejaVu Sans"'), ("r24", '24px "DejaVu Sans"'), ("r28", '28px "DejaVu Sans"'),
            ("b24", 'bold 24px "DejaVu Sans"'), ("b28", 'bold 28px "DejaVu Sans"')]

PAGE = """<!doctype html><html><body><pre id="out"></pre><script>
const chars = %s; const variants = %s;
const c = document.createElement('canvas'); c.width = 128; c.height = 96;
const ctx = c.getContext('2d');
const out = {};
for (const [key, font] of variants) {
  ctx.font = font; ctx.textBaseline = 'alphabetic'; ctx.fillStyle = '#000';
  const table = {};
  for (const ch of chars) {
    ctx.clearRect(0, 0, 128, 96); ctx.fillText(ch, 32, 64);
    const adv = ctx.measureText(ch).width;
    const d = ctx.getImageData(0, 0, 128, 96).data;
    let x0 = 128, x1 = -1, y0 = 96, y1 = -1;
    for (let y = 0; y < 96; y++) for (let x = 0; x < 128; x++) {
      if (d[(y * 128 + x) * 4 + 3] > 110) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    }
    if (x1 < 0) { table[ch] = [adv, 0, 0, 0, 0, []]; continue; }
    const rows = [];
    for (let y = y0; y <= y1; y++) { let bits = 0; for (let x = x0; x <= x1; x++) { bits = bits * 2 + (d[(y * 128 + x) * 4 + 3] > 110 ? 1 : 0); } rows.push(bits); }
    table[ch] = [Math.round(adv * 100) / 100, x0 - 32, 64 - y0, x1 - x0 + 1, y1 - y0 + 1, rows];
  }
  out[key] = table;
}
document.getElementById('out').textContent = JSON.stringify(out);
</script></body></html>"""


def main():
    chrome = sys.argv[1] if len(sys.argv) > 1 else "chromium"
    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "glyphs.html")
        with open(page, "w", encoding="utf-8") as f:
            f.write(PAGE % (json.dumps(CHARS, ensure_ascii=False), json.dumps(VARIANTS)))
        dom = subprocess.run(
            [chrome, "--headless=new", "--no-sandbox", "--disable-gpu", "--dump-dom", "file://" + page],
            check=True, capture_output=True, text=True).stdout
    m = re.search(r'<pre id="out">(.*?)</pre>', dom, re.S)
    if not m:
        raise SystemExit("no glyph table in the DOM dump")
    table = json.loads(html.unescape(m.group(1)))
    packed = base64.b64encode(zlib.compress(json.dumps(table, separators=(",", ":"), ensure_ascii=False).encode("utf-8"), 9)).decode("ascii")
    lines = [packed[i:i + 96] for i in range(0, len(packed), 96)]
    with open(OUT, "w", encoding="utf-8") as f:
        f.write('"""Bitmap glyphs for map labels — DejaVu Sans, rasterized once by\n'
                'tools/fonts/build_font_table.py with a headless browser. This module is\n'
                'generated data; the renderer stays standard-library only.\n\n'
                'Variants: r20/r24/r28 (regular) and b24/b28 (bold), pixel sizes at the 2×\n'
                'supersampled tile; glyph = [advance, left, top-above-baseline, width, height,\n'
                'rows as bit masks, MSB = leftmost pixel].\n\n'
                'DejaVu fonts: Bitstream Vera license (tools/fonts/DEJAVU-LICENSE).\n"""\n'
                'import base64\nimport json\nimport zlib\n\n'
                'FONT_TABLE_B64 = (\n' + "".join(f'    "{l}"\n' for l in lines) + ')\n\n\n'
                'def load():\n    return json.loads(zlib.decompress(base64.b64decode(FONT_TABLE_B64)).decode("utf-8"))\n')
    n = sum(len(t) for t in table.values())
    print(f"{OUT}: {n} glyphs in {len(table)} variants, {len(packed) / 1024:.0f} KB base64")


if __name__ == "__main__":
    main()
