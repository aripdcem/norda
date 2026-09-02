#!/usr/bin/env python3
"""Builds an MBTiles map pack for a region (standard library only) — the
"build the pack" step of the map-pack pipeline (docs/MVP.md, 7.2).

Cartography comes from OpenStreetMap via the Overpass API and is rendered by
tools/osmrender.py in Norda's minimalist outdoor style (coastline and sea,
water, green areas, roads by class, rails). The procedural parity grid that
served the pipeline until real cartography arrived is still available with
`--source grid`; alignment/TMS mistakes show up on it instantly.

Usage:
  python3 tools/make-map-pack.py --id istanbul --name "Istanbul" \
      --bbox 28.6,40.8,29.4,41.3 --minzoom 8 --maxzoom 13 --out dist

  --osm-json FILE   render from a saved Overpass response instead of fetching
                    (tests, offline reruns); the fetched data is cached as
                    <out>/<id>.osm.json on every online run.
"""

import argparse
import json
import math
import os
import sqlite3
import struct
import sys
import time
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import osmrender  # noqa: E402

TILE = 256

ATTRIBUTION = "© OpenStreetMap contributors (ODbL)"


def x_tile(lon, z):
    return osmrender.x_tile(lon, z)


def y_tile(lat, z):
    return osmrender.y_tile(lat, z)


def png_chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def grid_tile_png(z, x, y, min_zoom):
    """Procedural parity tile: alternating ground colour, a tile edge line and
    an L mark in the top-left corner — mirroring or TMS errors are obvious."""
    even = (x + y) % 2 == 0
    shade = (z - min_zoom) * 4
    bg = (232 - shade, 235 - shade, 230 - shade) if even \
        else (212 - shade, 224 - shade, 210 - shade)
    border = (47, 107, 76)
    rows = []
    for py in range(TILE):
        row = bytearray()
        for px in range(TILE):
            c = bg
            if px == 0 or py == 0:
                c = border                      # top/left edge: alignment guide
            if (px < 28 and py < 7) or (px < 7 and py < 28):
                c = border                      # L corner: mirroring detector
            row += bytes(c)
        rows.append(b"\x00" + bytes(row))
    ihdr = struct.pack(">IIBBBBB", TILE, TILE, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr)
            + png_chunk(b"IDAT", zlib.compress(b"".join(rows), 9))
            + png_chunk(b"IEND", b""))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", required=True, help="pack id (file name)")
    ap.add_argument("--name", required=True, help="display name")
    ap.add_argument("--bbox", required=True, help="left,bottom,right,top (lon/lat)")
    ap.add_argument("--minzoom", type=int, default=8)
    ap.add_argument("--maxzoom", type=int, default=13)
    ap.add_argument("--out", default="dist")
    ap.add_argument("--source", choices=("osm", "grid"), default="osm",
                    help="osm: OpenStreetMap cartography (default); grid: parity test pattern")
    ap.add_argument("--osm-json", help="render from this saved Overpass response")
    ap.add_argument("--overpass", default=osmrender.DEFAULT_ENDPOINT)
    ap.add_argument("--no-minor-roads", action="store_true",
                    help="skip residential/unclassified/track/path (smaller download)")
    args = ap.parse_args()

    parts = [float(v) for v in args.bbox.split(",")]
    if len(parts) != 4:
        raise SystemExit("bbox must be 'left,bottom,right,top'")
    left, bottom, right, top = parts
    if not (left < right and bottom < top):
        raise SystemExit("bbox order is broken: left<right and bottom<top required")
    if not (args.minzoom <= args.maxzoom):
        raise SystemExit("minzoom must be <= maxzoom")
    bbox = (left, bottom, right, top)

    os.makedirs(args.out, exist_ok=True)
    out = os.path.join(args.out, f"{args.id}.mbtiles")
    if os.path.exists(out):
        os.remove(out)

    scene = None
    if args.source == "osm":
        if args.osm_json:
            with open(args.osm_json, encoding="utf-8") as f:
                js = json.load(f)
        else:
            js = osmrender.fetch_region(
                bbox, osmrender.coast_extent(bbox, args.minzoom),
                endpoint=args.overpass, minor=not args.no_minor_roads)
            cache = os.path.join(args.out, f"{args.id}.osm.json")
            with open(cache, "w", encoding="utf-8") as f:
                json.dump(js, f)
            print(f"{cache}: {os.path.getsize(cache) / 1048576:.1f} MB cached")
        data = osmrender.parse_overpass(js)
        scene = osmrender.Scene(data, bbox, args.minzoom)
        print(f"features: {len(data.features)} · sea rings: {len(scene.sea)}")

    db = sqlite3.connect(out)
    db.execute("CREATE TABLE metadata (name TEXT, value TEXT)")
    db.execute("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER,"
               " tile_row INTEGER, tile_data BLOB)")
    db.execute("CREATE UNIQUE INDEX tile_index ON tiles"
               " (zoom_level, tile_column, tile_row)")
    meta = [
        ("name", args.name),
        ("format", "png"),
        ("bounds", f"{left},{bottom},{right},{top}"),
        ("minzoom", str(args.minzoom)),
        ("maxzoom", str(args.maxzoom)),
    ]
    if args.source == "osm":
        meta += [("attribution", ATTRIBUTION),
                 ("description", "Norda map pack — OpenStreetMap cartography")]
    db.executemany("INSERT INTO metadata VALUES (?, ?)", meta)

    blob_cache = {}
    total = 0
    started = time.time()
    for z in range(args.minzoom, args.maxzoom + 1):
        x0, x1 = int(x_tile(left, z)), int(x_tile(right, z))
        y0, y1 = int(y_tile(top, z)), int(y_tile(bottom, z))
        count = 0
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                if scene is not None:
                    blob = scene.render_tile(z, x, y)
                else:
                    key = (z, (x + y) % 2)
                    if key not in blob_cache:
                        blob_cache[key] = grid_tile_png(z, x, y, args.minzoom)
                    blob = blob_cache[key]
                tms_row = (1 << z) - 1 - y      # MBTiles TMS row
                db.execute("INSERT INTO tiles VALUES (?, ?, ?, ?)", (z, x, tms_row, blob))
                total += 1
                count += 1
        db.commit()
        print(f"z{z}: {count} tiles ({time.time() - started:.0f} s)")
    db.close()
    size = os.path.getsize(out)
    print(f"{out}: {total} tiles, {size / 1024:.0f} KB, z{args.minzoom}-z{args.maxzoom}")


if __name__ == "__main__":
    main()
