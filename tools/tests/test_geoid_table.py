"""Guards the shipped geoid table (app/src/main/res/raw). Pure stdlib.

The table is committed data, produced once by tools/geoid/build_geoid_table.py
from the EGM96 15' grid. These tests keep the file honest: the header the app
parses (core/geo/Geoid), the exact size, the model's value range, and two node
values that also catch a flipped axis.

Run: python3 -m unittest discover -s tools/tests -v
"""
import os
import struct
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
TABLE = os.path.join(REPO, "app", "src", "main", "res", "raw", "geoid_egm96_1deg.bin")


class GeoidTableTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(TABLE, "rb") as f:
            cls.data = f.read()
        cls.magic, cls.version, cls.rows, cls.cols = struct.unpack(">3sBHH", cls.data[:8])
        cls.values = struct.unpack(">%dh" % (cls.rows * cls.cols), cls.data[8:])

    def node(self, lat, lon):
        """Value at an exact 1 degree node, in metres."""
        row = int(round(90.0 - lat))
        col = int(round(180.0 + lon)) % self.cols
        return self.values[row * self.cols + col] / 10.0

    def test_header_is_what_the_app_parses(self):
        self.assertEqual(b"NG1", self.magic)
        self.assertEqual(1, self.version)
        self.assertEqual(181, self.rows)
        self.assertEqual(360, self.cols)

    def test_size_matches_the_header(self):
        self.assertEqual(8 + self.rows * self.cols * 2, len(self.data))

    def test_values_stay_inside_the_models_range(self):
        # EGM96 undulation runs from about -107 m to +85 m.
        self.assertGreater(min(self.values) / 10.0, -110.0)
        self.assertLess(max(self.values) / 10.0, 90.0)
        self.assertLess(min(self.values) / 10.0, -100.0)
        self.assertGreater(max(self.values) / 10.0, 80.0)

    def test_known_nodes(self):
        # Published EGM96 values; these also catch a transposed or flipped grid.
        self.assertAlmostEqual(17.2, self.node(0, 0), delta=0.2)
        self.assertAlmostEqual(37.4, self.node(41, 29), delta=0.3)
        # The Indian Ocean low and the high over Iceland.
        self.assertLess(self.node(-8, 78), -70.0)
        self.assertGreater(self.node(64, -22), 55.0)


if __name__ == "__main__":
    unittest.main()
