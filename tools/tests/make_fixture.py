#!/usr/bin/env python3
"""Writes fixtures/mini.osm.json — a tiny hand-built Overpass response used by
the renderer tests. Region: lon 29.00–29.10, lat 41.00–41.05.

Layout (north is up):
  - coastline way 1 runs west→east at lat ≈ 41.02 with land on the LEFT
    (north) and sea on the RIGHT (south), entering and leaving the region;
  - way 2 is a small counter-clockwise coastline ring = an island in the sea;
  - way 3 is a lake (natural=water), way 4 a forest (landuse=forest), both on
    land; relation 5 is a multipolygon lake made of two outer ways drawn in
    opposite directions;
  - way 6 primary road (E–W), way 7 residential road (N–S), way 8 river
    flowing south into the sea, way 9 railway (E–W), way 10 track (diagonal).
"""
import json
import os


def way(i, tags, pts, nodes=None):
    nodes = nodes or [i * 100 + k for k in range(len(pts))]
    return {"type": "way", "id": i, "tags": tags, "nodes": nodes,
            "geometry": [{"lon": lon, "lat": lat} for lon, lat in pts]}


def ring(cx, cy, r, ccw=True):
    pts = [(cx + r, cy), (cx, cy + r), (cx - r, cy), (cx, cy - r)]
    if not ccw:
        pts.reverse()
    return pts + [pts[0]]


def square(x0, y0, x1, y1):
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1), (x0, y0)]


elements = [
    # coastline in two ways sharing node 1004 so stitching is exercised
    way(1, {"natural": "coastline"},
        [(28.90, 41.018), (29.02, 41.021), (29.05, 41.019), (29.07, 41.022)],
        nodes=[1001, 1002, 1003, 1004]),
    way(11, {"natural": "coastline"},
        [(29.07, 41.022), (29.09, 41.020), (29.20, 41.023)],
        nodes=[1004, 1102, 1103]),
    way(2, {"natural": "coastline"}, ring(29.05, 41.008, 0.003, ccw=True)),
    way(3, {"natural": "water"}, square(29.015, 41.036, 29.025, 41.044)),
    way(4, {"landuse": "forest"}, square(29.075, 41.036, 29.095, 41.048)),
    {"type": "relation", "id": 5,
     "tags": {"type": "multipolygon", "natural": "water"},
     "members": [
         {"type": "way", "ref": 51, "role": "outer",
          "geometry": [{"lon": 29.055, "lat": 41.033}, {"lon": 29.068, "lat": 41.033},
                       {"lon": 29.068, "lat": 41.040}]},
         {"type": "way", "ref": 52, "role": "outer",   # drawn backwards on purpose
          "geometry": [{"lon": 29.055, "lat": 41.033}, {"lon": 29.055, "lat": 41.040},
                       {"lon": 29.068, "lat": 41.040}]},
     ]},
    way(6, {"highway": "primary"}, [(29.00, 41.045), (29.10, 41.045)]),
    way(7, {"highway": "residential"}, [(29.03, 41.030), (29.03, 41.050)]),
    way(8, {"waterway": "river"}, [(29.09, 41.050), (29.09, 41.031)]),
    way(9, {"railway": "rail"}, [(29.00, 41.035), (29.10, 41.035)]),
    way(10, {"highway": "track"}, [(29.04, 41.024), (29.05, 41.032)]),
]

out = os.path.join(os.path.dirname(__file__), "fixtures", "mini.osm.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump({"version": 0.6, "generator": "fixture", "elements": elements}, f, indent=1)
print(out, len(elements), "elements")
