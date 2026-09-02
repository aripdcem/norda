#!/usr/bin/env python3
"""Builds an MBTiles map pack for a region (stdlib only) — the build step of
the map-pack pipeline (docs/MVP.md, 7.2).

For now the cartography is procedural: a parity-colored background, a tile
edge line and an L mark in the top-left corner — alignment/TMS mistakes show
up in the grid instantly. Real OSM cartography is the only thing that will be
swapped inside this step; the pack format, publishing and download chain stay
the same.

Usage:
  python3 tools/make-map-pack.py --id istanbul --name "Istanbul" \
      --bbox 28.6,40.8,29.4,41.3 --minzoom 8 --maxzoom 13 --out dist
"""

import argparse
import math
import os
import sqlite3
import struct
import zlib

TILE = 256


def x_tile(lon, z):
    return (lon + 180.0) / 360.0 * (1 << z)


def y_tile(lat, z):
    r = math.radians(lat)
    return (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.pi) / 2.0 * (1 << z)


def png_chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def tile_png(z, x, y, min_zoom):
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
    ap.add_argument("--bbox", required=True,
                    help="left,bottom,right,top (lon/lat)")
    ap.add_argument("--minzoom", type=int, default=8)
    ap.add_argument("--maxzoom", type=int, default=13)
    ap.add_argument("--out", default="dist")
    args = ap.parse_args()

    parts = [float(v) for v in args.bbox.split(",")]
    if len(parts) != 4:
        raise SystemExit("bbox must be 'left,bottom,right,top'")
    left, bottom, right, top = parts
    if not (left < right and bottom < top):
        raise SystemExit("bbox is out of order: left<right and bottom<top required")
    if not (args.minzoom <= args.maxzoom):
        raise SystemExit("minzoom must be <= maxzoom")

    os.makedirs(args.out, exist_ok=True)
    out = os.path.join(args.out, f"{args.id}.mbtiles")
    if os.path.exists(out):
        os.remove(out)

    db = sqlite3.connect(out)
    db.execute("CREATE TABLE metadata (name TEXT, value TEXT)")
    db.execute("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER,"
               " tile_row INTEGER, tile_data BLOB)")
    db.execute("CREATE UNIQUE INDEX tile_index ON tiles"
               " (zoom_level, tile_column, tile_row)")
    db.executemany("INSERT INTO metadata VALUES (?, ?)", [
        ("name", args.name),
        ("format", "png"),
        ("bounds", f"{left},{bottom},{right},{top}"),
        ("minzoom", str(args.minzoom)),
        ("maxzoom", str(args.maxzoom)),
    ])

    blob_cache = {}
    total = 0
    for z in range(args.minzoom, args.maxzoom + 1):
        x0, x1 = int(x_tile(left, z)), int(x_tile(right, z))
        y0, y1 = int(y_tile(top, z)), int(y_tile(bottom, z))
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                key = (z, (x + y) % 2)
                if key not in blob_cache:
                    blob_cache[key] = tile_png(z, x, y, args.minzoom)
                tms_row = (1 << z) - 1 - y      # MBTiles TMS row
                db.execute("INSERT INTO tiles VALUES (?, ?, ?, ?)",
                           (z, x, tms_row, blob_cache[key]))
                total += 1
    db.commit()
    db.close()
    size = os.path.getsize(out)
    print(f"{out}: {total} tiles, {size / 1024:.0f} KB, "
          f"z{args.minzoom}-z{args.maxzoom}")


if __name__ == "__main__":
    main()
