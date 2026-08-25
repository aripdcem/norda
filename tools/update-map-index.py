#!/usr/bin/env python3
"""docs/maps/index.json içindeki paket kaydını ekler/günceller (yalnız stdlib).

Uygulamanın Maps ekranı bu dosyayı okur (docs/MVP.md, 7.2). Kimliği aynı olan
kayıt değiştirilir, yenisi eklenir; sıralama kimliğe göre kararlıdır.

Kullanım:
  python3 tools/update-map-index.py --id istanbul --name "İstanbul" \
      --bbox 28.6,40.8,29.4,41.3 --minzoom 8 --maxzoom 13 \
      --size 331776 --sha256 <hex> --url <indirme-url> --version 3
"""

import argparse
import json
import os

INDEX = os.path.join(os.path.dirname(__file__), "..", "docs", "maps", "index.json")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", required=True)
    ap.add_argument("--name", required=True)
    ap.add_argument("--bbox", required=True)
    ap.add_argument("--minzoom", type=int, required=True)
    ap.add_argument("--maxzoom", type=int, required=True)
    ap.add_argument("--size", type=int, required=True)
    ap.add_argument("--sha256", required=True)
    ap.add_argument("--url", required=True)
    ap.add_argument("--version", type=int, required=True)
    args = ap.parse_args()

    bbox = [float(v) for v in args.bbox.split(",")]
    if len(bbox) != 4:
        raise SystemExit("bbox 'left,bottom,right,top' olmalı")

    with open(INDEX, encoding="utf-8") as f:
        index = json.load(f)

    entry = {
        "id": args.id,
        "name": args.name,
        "bbox": bbox,
        "minZoom": args.minzoom,
        "maxZoom": args.maxzoom,
        "sizeBytes": args.size,
        "sha256": args.sha256.lower(),
        "url": args.url,
        "version": args.version,
    }
    packages = [p for p in index.get("packages", []) if p.get("id") != args.id]
    packages.append(entry)
    packages.sort(key=lambda p: p["id"])
    index["packages"] = packages

    with open(INDEX, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"{INDEX}: {args.id} v{args.version} yazıldı ({len(packages)} paket)")


if __name__ == "__main__":
    main()
