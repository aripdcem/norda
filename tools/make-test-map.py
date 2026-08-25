#!/usr/bin/env python3
"""Renderer'ı sınamak için küçük bir MBTiles paketi üretir (yalnız stdlib).

Karolar prosedüreldir: parite renkli zemin, üst/sol kenar çizgisi ve sol üst
köşede L işareti. Hizalama ya da TMS çevirme hatası, ızgaranın süreksizliği
olarak anında göze batar. Gerçek harita paketleri map-pack hattından gelir
(docs/MVP.md, 7.2); bu paket yalnız geliştirme içindir.

Kullanım:  python3 tools/make-test-map.py   ->  dist/norda-test-istanbul.mbtiles
"""

import math
import os
import sqlite3
import struct
import zlib

BOUNDS = (28.6, 40.8, 29.4, 41.3)   # left, bottom, right, top (İstanbul)
MIN_ZOOM, MAX_ZOOM = 8, 13
TILE = 256
OUT = os.path.join(os.path.dirname(__file__), "..", "dist", "norda-test-istanbul.mbtiles")


def x_tile(lon, z):
    return (lon + 180.0) / 360.0 * (1 << z)


def y_tile(lat, z):
    r = math.radians(lat)
    return (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.pi) / 2.0 * (1 << z)


def png_chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def tile_png(z, x, y):
    even = (x + y) % 2 == 0
    shade = (z - MIN_ZOOM) * 4
    bg = (232 - shade, 235 - shade, 230 - shade) if even \
        else (212 - shade, 224 - shade, 210 - shade)
    border = (47, 107, 76)
    rows = []
    for py in range(TILE):
        row = bytearray()
        for px in range(TILE):
            c = bg
            if px == 0 or py == 0:
                c = border                      # üst/sol kenar: hizalama kılavuzu
            if (px < 28 and py < 7) or (px < 7 and py < 28):
                c = border                      # L köşe: aynalanma dedektörü
            row += bytes(c)
        rows.append(b"\x00" + bytes(row))
    ihdr = struct.pack(">IIBBBBB", TILE, TILE, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr)
            + png_chunk(b"IDAT", zlib.compress(b"".join(rows), 9))
            + png_chunk(b"IEND", b""))


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    if os.path.exists(OUT):
        os.remove(OUT)
    db = sqlite3.connect(OUT)
    db.execute("CREATE TABLE metadata (name TEXT, value TEXT)")
    db.execute("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER,"
               " tile_row INTEGER, tile_data BLOB)")
    db.execute("CREATE UNIQUE INDEX tile_index ON tiles"
               " (zoom_level, tile_column, tile_row)")
    left, bottom, right, top = BOUNDS
    db.executemany("INSERT INTO metadata VALUES (?, ?)", [
        ("name", "Norda Test İstanbul"),
        ("format", "png"),
        ("bounds", f"{left},{bottom},{right},{top}"),
        ("minzoom", str(MIN_ZOOM)),
        ("maxzoom", str(MAX_ZOOM)),
    ])

    # Aynı görsel (parite, zoom) başına bir kez üretilir — paket küçük kalır.
    blob_cache = {}
    total = 0
    for z in range(MIN_ZOOM, MAX_ZOOM + 1):
        x0, x1 = int(x_tile(left, z)), int(x_tile(right, z))
        y0, y1 = int(y_tile(top, z)), int(y_tile(bottom, z))
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                key = (z, (x + y) % 2)
                if key not in blob_cache:
                    blob_cache[key] = tile_png(z, x, y)
                tms_row = (1 << z) - 1 - y      # MBTiles TMS satırı
                db.execute("INSERT INTO tiles VALUES (?, ?, ?, ?)",
                           (z, x, tms_row, blob_cache[key]))
                total += 1
    db.commit()
    db.close()
    size_kb = os.path.getsize(OUT) / 1024
    print(f"{OUT}: {total} karo, {size_kb:.0f} KB, z{MIN_ZOOM}-z{MAX_ZOOM}")


if __name__ == "__main__":
    main()
