# Geoid table provenance

`app/src/main/res/raw/geoid_egm96_1deg.bin` is a 1° resampling of the **EGM96**
geoid model, stored as int16 decimetres (see `build_geoid_table.py` for the
format).

- **Model:** Earth Gravitational Model 1996 (EGM96), NASA GSFC and the US
  National Imagery and Mapping Agency (now NGA). A work of the United States
  government: not subject to copyright, free to use and redistribute.
- **Source file:** `us_nga_egm96_15.tif`, the 15′ (0.25°) grid as distributed
  by the PROJ project's data repository,
  <https://github.com/OSGeo/PROJ-data/tree/master/us_nga>.
- **Import:** `build_geoid_table.py`, run once; the output is committed so that
  the app build depends on nothing.

## Why 1°

The table converts the ellipsoid height the GNSS receiver reports into the
height above mean sea level that maps, signposts and other tools use. The
separation is ~37 m in Istanbul, so the correction matters; its own precision
does not have to be perfect. Measured against the 15′ grid as reference:

| Step | Nodes | File | RMS (Turkey) | Worst (Turkey) | Worst (global) |
|---|---|---|---|---|---|
| 0.5° | 361×720 | 520 KB | 0.21 m | 1.58 m | 3.93 m |
| **1°** | **181×360** | **130 KB** | **0.76 m** | **3.69 m** | **13.7 m** |
| 2° | 91×180 | 33 KB | 1.90 m | 9.46 m | 17.1 m |
| 15′ | 721×1440 | 2.1 MB | reference | | |

1° was chosen: sub-metre where the app is used, an order of magnitude finer
than GPS vertical noise, and the file compresses to ~84 KB inside the APK.
