#!/usr/bin/env python3
"""Builds Norda's geoid table from the EGM96 15' grid.

One-shot data import, like the bitmap font table: the OUTPUT is committed and
the app build needs nothing. Reading the source GeoTIFF needs `tifffile` and
`imagecodecs`; nothing in the app or in CI depends on them.

    pip install tifffile imagecodecs
    curl -L -o egm96_15.tif \\
      https://raw.githubusercontent.com/OSGeo/PROJ-data/master/us_nga/us_nga_egm96_15.tif
    python3 tools/geoid/build_geoid_table.py egm96_15.tif \\
      app/src/main/res/raw/geoid_egm96_1deg.bin --step 1.0

Source and licence: tools/geoid/EGM96-PROVENANCE.md.

Output format (read by `core/geo/Geoid`):

    0  'N' 'G' '1'      magic
    3  version = 1
    4  rows  uint16 BE  (181 at a 1 degree step)
    6  cols  uint16 BE  (360 at a 1 degree step)
    8  rows*cols int16 BE, decimetres, row-major
       row 0 = +90 deg, row rows-1 = -90 deg, step = 180/(rows-1)
       col 0 = -180 deg, step = 360/cols, wrapping east

Decimetres cost 0.05 m of rounding, an order of magnitude below the
interpolation error of any practical step, and keep the table an integer
array with no floating point in the file.
"""

import argparse
import struct
import sys

MAGIC = b"NG1"
VERSION = 1


def build(source_tif, step):
    import tifffile  # noqa: PLC0415 — optional, only for the one-shot import

    grid = tifffile.imread(source_tif)
    rows, cols = grid.shape
    # The source is 0.25 deg: 721 x 1440, row 0 = +90 deg, col 0 = -180 deg.
    if rows != 721 or cols != 1440:
        raise SystemExit(f"unexpected source grid {rows}x{cols}, expected 721x1440")
    k = step / 0.25
    if k != int(k) or int(k) < 1:
        raise SystemExit(f"step {step} is not a multiple of the source 0.25 deg")
    k = int(k)
    if (rows - 1) % k:
        raise SystemExit(f"step {step} does not divide the source rows")
    out = grid[::k, ::k]
    return out


def encode(grid):
    rows, cols = grid.shape
    body = bytearray(struct.pack(">3sBHH", MAGIC, VERSION, rows, cols))
    for r in range(rows):
        for c in range(cols):
            body += struct.pack(">h", int(round(float(grid[r][c]) * 10)))
    return bytes(body)


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("source", help="EGM96 15' GeoTIFF (see the docstring)")
    ap.add_argument("target", help="output .bin for app/src/main/res/raw")
    ap.add_argument("--step", type=float, default=1.0, help="grid step in degrees")
    args = ap.parse_args(argv)

    grid = build(args.source, args.step)
    data = encode(grid)
    with open(args.target, "wb") as f:
        f.write(data)
    rows, cols = grid.shape
    print(f"{args.target}: {rows}x{cols} at {args.step} deg, {len(data)} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
