"""Tests for the OSM cartography renderer (tools/osmrender.py). Pure stdlib.

Run: python3 -m unittest discover -s tools/tests -v
"""
import json
import os
import sqlite3
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)

import osmrender as R  # noqa: E402

FIXTURE = os.path.join(HERE, "fixtures", "mini.osm.json")
BBOX = (29.00, 41.00, 29.10, 41.05)  # left, bottom, right, top


def png_size(data):
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    w, h = struct.unpack(">II", data[16:24])
    return w, h


def png_pixels(data):
    """Decodes an uncompressed-filter (filter 0) RGB PNG written by osmrender."""
    w, h = png_size(data)
    pos = 8
    idat = b""
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        if tag == b"IDAT":
            idat += data[pos + 8:pos + 8 + n]
        pos += 12 + n
    raw = zlib.decompress(idat)
    stride = 1 + w * 3
    return w, h, [raw[r * stride + 1:(r + 1) * stride] for r in range(h)]


class RasterTest(unittest.TestCase):
    def test_polygon_fill_even_odd_with_hole(self):
        r = R.Raster(10, 10, (0, 0, 0))
        outer = [(1, 1), (9, 1), (9, 9), (1, 9)]
        inner = [(4, 4), (6, 4), (6, 6), (4, 6)]
        r.fill_polygon([outer, inner], (255, 0, 0))
        self.assertEqual(r.get(2, 2), (255, 0, 0))     # inside outer
        self.assertEqual(r.get(5, 5), (0, 0, 0))       # inside hole
        self.assertEqual(r.get(0, 0), (0, 0, 0))       # outside

    def test_polygon_fill_clips_to_raster(self):
        r = R.Raster(4, 4, (0, 0, 0))
        r.fill_polygon([[(-100, -100), (100, -100), (100, 100), (-100, 100)]], (1, 2, 3))
        self.assertEqual(r.get(0, 0), (1, 2, 3))
        self.assertEqual(r.get(3, 3), (1, 2, 3))

    def test_stroke_polyline(self):
        r = R.Raster(20, 20, (0, 0, 0))
        r.stroke_polyline([(2, 10), (18, 10)], 2.0, (0, 255, 0))
        self.assertEqual(r.get(10, 10), (0, 255, 0))
        self.assertEqual(r.get(10, 3), (0, 0, 0))

    def test_downsample_averages_2x2_blocks(self):
        r = R.Raster(4, 4, (0, 0, 0))
        r.fill_polygon([[(0, 0), (2, 0), (2, 4), (0, 4)]], (200, 200, 200))  # left half
        small = r.downsample(2)
        self.assertEqual(small.size, (2, 2))
        self.assertEqual(small.get(0, 0), (200, 200, 200))
        self.assertEqual(small.get(1, 0), (0, 0, 0))

    def test_png_encoding(self):
        r = R.Raster(3, 2, (10, 20, 30))
        w, h, rows = png_pixels(r.to_png())
        self.assertEqual((w, h), (3, 2))
        self.assertEqual(rows[0][:3], bytes((10, 20, 30)))


class GeometryTest(unittest.TestCase):
    def test_boundary_parameter_runs_clockwise_from_top_left(self):
        rect = (0.0, 0.0, 10.0, 5.0)  # left, bottom, right, top
        t = lambda x, y: R.boundary_param(rect, x, y)
        self.assertAlmostEqual(t(0, 5), 0.0)            # top-left
        self.assertAlmostEqual(t(10, 5), 10.0)          # top-right
        self.assertAlmostEqual(t(10, 0), 15.0)          # bottom-right
        self.assertAlmostEqual(t(0, 0), 25.0)           # bottom-left
        self.assertAlmostEqual(t(0, 2.5), 27.5)         # up the left edge

    def test_clip_polyline_to_rect_splits_into_pieces(self):
        rect = (0.0, 0.0, 10.0, 10.0)
        line = [(-5, 5), (5, 5), (15, 5)]
        pieces = R.clip_polyline(line, rect)
        self.assertEqual(len(pieces), 1)
        self.assertAlmostEqual(pieces[0][0][0], 0.0)
        self.assertAlmostEqual(pieces[0][-1][0], 10.0)

    def test_sea_polygons_land_left_water_right(self):
        rect = (0.0, 0.0, 10.0, 10.0)
        # travelling east with land on the left (north): the sea is the south half
        chains = [[(-1.0, 5.0), (11.0, 5.0)]]
        rings = R.sea_rings(chains, rect)
        self.assertTrue(R.rings_contain(rings, (5.0, 2.0)))
        self.assertFalse(R.rings_contain(rings, (5.0, 8.0)))

    def test_sea_polygons_island_is_a_hole(self):
        rect = (0.0, 0.0, 10.0, 10.0)
        island = [(6.0, 2.0), (5.0, 3.0), (4.0, 2.0), (5.0, 1.0), (6.0, 2.0)]  # CCW ring
        rings = R.sea_rings([[(-1.0, 5.0), (11.0, 5.0)], island], rect)
        self.assertTrue(R.rings_contain(rings, (1.0, 1.0)))
        self.assertFalse(R.rings_contain(rings, (5.0, 2.0)))    # on the island

    def test_only_islands_means_open_sea(self):
        rect = (0.0, 0.0, 10.0, 10.0)
        island = [(6.0, 5.0), (5.0, 6.0), (4.0, 5.0), (5.0, 4.0), (6.0, 5.0)]
        rings = R.sea_rings([island], rect)
        self.assertTrue(R.rings_contain(rings, (1.0, 1.0)))
        self.assertFalse(R.rings_contain(rings, (5.0, 5.0)))

    def test_no_coastline_means_land(self):
        self.assertEqual(R.sea_rings([], (0.0, 0.0, 1.0, 1.0)), [])

    def test_stitch_by_node_ids(self):
        ways = [
            {"nodes": [1, 2], "geometry": [(0.0, 0.0), (1.0, 0.0)]},
            {"nodes": [2, 3], "geometry": [(1.0, 0.0), (2.0, 0.0)]},
            {"nodes": [7, 8], "geometry": [(5.0, 5.0), (6.0, 5.0)]},
        ]
        chains = R.stitch_chains(ways)
        self.assertEqual(sorted(len(c) for c in chains), [2, 3])

    def test_multipolygon_outer_rings_any_direction(self):
        members = [
            [(0.0, 0.0), (2.0, 0.0), (2.0, 2.0)],
            [(0.0, 0.0), (0.0, 2.0), (2.0, 2.0)],   # reversed relative to the first
        ]
        rings = R.assemble_rings(members)
        self.assertEqual(len(rings), 1)
        self.assertEqual(rings[0][0], rings[0][-1])
        self.assertEqual(len(rings[0]), 5)


class ParseAndStyleTest(unittest.TestCase):
    def setUp(self):
        with open(FIXTURE, encoding="utf-8") as f:
            self.data = R.parse_overpass(json.load(f))

    def test_parse_layers(self):
        kinds = {}
        for f in self.data.features:
            kinds[f.layer] = kinds.get(f.layer, 0) + 1
        self.assertEqual(kinds["coastline"], 3)
        self.assertEqual(kinds["water"], 2)       # lake way + multipolygon relation
        self.assertEqual(kinds["green"], 1)
        self.assertEqual(kinds["road"], 3)
        self.assertEqual(kinds["waterway"], 1)
        self.assertEqual(kinds["rail"], 1)

    def test_road_visibility_by_zoom(self):
        self.assertTrue(R.road_style("motorway", 8) is not None)
        self.assertIsNone(R.road_style("residential", 10))
        self.assertIsNotNone(R.road_style("residential", 13))
        self.assertIsNone(R.road_style("track", 11))
        self.assertIsNotNone(R.road_style("track", 13))


class RenderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(FIXTURE, encoding="utf-8") as f:
            cls.data = R.parse_overpass(json.load(f))
        cls.scene = R.Scene(cls.data, BBOX, minzoom=12)

    def pixel_at(self, lon, lat, z):
        x, y = int(R.x_tile(lon, z)), int(R.y_tile(lat, z))
        png = self.scene.render_tile(z, x, y)
        w, h, rows = png_pixels(png)
        px = int((R.x_tile(lon, z) - x) * w)
        py = int((R.y_tile(lat, z) - y) * h)
        row = rows[py]
        return tuple(row[px * 3:px * 3 + 3])

    def test_sea_and_land(self):
        self.assertEqual(self.pixel_at(29.02, 41.005, 13), R.STYLE["sea"])
        self.assertEqual(self.pixel_at(29.02, 41.030, 13), R.STYLE["land"])

    def test_island_is_land(self):
        self.assertEqual(self.pixel_at(29.05, 41.008, 13), R.STYLE["land"])

    def test_lake_forest_and_multipolygon(self):
        self.assertEqual(self.pixel_at(29.020, 41.040, 13), R.STYLE["water"])
        self.assertEqual(self.pixel_at(29.085, 41.042, 13), R.STYLE["green"])
        self.assertEqual(self.pixel_at(29.0615, 41.0365, 13), R.STYLE["water"])

    def test_primary_road_is_drawn(self):
        self.assertEqual(self.pixel_at(29.06, 41.045, 13), R.STYLE["road_primary"])

    def test_tile_is_256_png(self):
        png = self.scene.render_tile(12, int(R.x_tile(29.05, 12)), int(R.y_tile(41.03, 12)))
        self.assertEqual(png_size(png), (256, 256))


class MakeMapPackTest(unittest.TestCase):
    def test_end_to_end_from_osm_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run([
                sys.executable, os.path.join(TOOLS, "make-map-pack.py"),
                "--id", "mini", "--name", "Mini", "--bbox", "29.0,41.0,29.1,41.05",
                "--minzoom", "12", "--maxzoom", "13", "--out", tmp,
                "--osm-json", FIXTURE,
            ], check=True, capture_output=True)
            db = sqlite3.connect(os.path.join(tmp, "mini.mbtiles"))
            meta = dict(db.execute("SELECT name, value FROM metadata"))
            self.assertIn("OpenStreetMap", meta["attribution"])
            self.assertEqual(meta["format"], "png")
            n = db.execute("SELECT COUNT(*) FROM tiles").fetchone()[0]
            self.assertGreaterEqual(n, 4)
            for (blob,) in db.execute("SELECT tile_data FROM tiles"):
                self.assertEqual(png_size(blob), (256, 256))
            db.close()


if __name__ == "__main__":
    unittest.main()
