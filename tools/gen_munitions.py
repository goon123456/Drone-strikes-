#!/usr/bin/env python3
"""Missiles, rockets, shells and grenades as they fly, built with the vehicle kit and drawn by Steel Rain's own
renderer like the aircraft. Origin on the body axis at roughly the middle, +z is the nose, in metres.

Preview without starting the game:  python3 tools/gen_munitions.py <output dir> [model ...]
"""
import math
from vehicle_kit import *
from vehicle_kit import _mat
from gen_aircraft import export

NAVY = _mat("msl_navy", (158, 164, 168), clean=True)
NAVY_D = _mat("msl_navy_d", (112, 118, 122), clean=True)
RGREEN = _mat("msl_rgreen", (84, 98, 72), clean=True)
RGREEN_D = _mat("msl_rgreen_d", (58, 70, 50), clean=True)
AOLIVE = _mat("msl_olive", (92, 100, 66), clean=True)
AOLIVE_D = _mat("msl_olive_d", (64, 70, 48), clean=True)
MWHITE = _mat("msl_white", (222, 221, 212), clean=True)
MSAND = _mat("msl_sand", (188, 176, 144), clean=True)
SHELLG = _mat("msl_shell", (80, 92, 60), clean=True)
BROWN = _mat("msl_brown", (108, 76, 44), "flat")


def _model(parts):
    return dict(base=parts, turret=[], barrel=[], bones=[])


def body(mat, profile, **opt):
    """A body of revolution along z from (z, radius) points."""
    return lathe(mat, "z", 0, 0, profile, **opt)


def band(mat, z0, z1, r):
    """A painted band round the body, a hair proud of it."""
    return lathe(mat, "z", 0, 0, [(z0, r + 0.002), (z1, r + 0.002)], plain=True, open=True)


def fins(mat, count, start, root, reach, lead, chord, tip_lead, tip_chord, thick):
    """Aerofoil fins spaced evenly round the body, from its surface out to `reach` from the axis."""
    out = []
    for i in range(count):
        a = math.radians(start + 360 * i / count)
        s, c = math.sin(a), math.cos(a)
        out.append(wing(mat, s * root * 0.9, c * root * 0.9, lead, chord, thick, s * reach, c * reach, tip_lead, tip_chord, thick * 0.5))
    return out


def nozzle(z, r, facing=-1):
    """A rocket nozzle: a dark bell let into the tail."""
    return [lathe(BLACK, "z", 0, 0, [(z, r), (z - facing * r * 0.9, r * 0.55), (z - facing * r * 0.9, 0.0)], plain=True),
            lathe(STEEL_D, "z", 0, 0, [(z + facing * r * 0.25, r * 1.04), (z, r * 1.04), (z, r)], plain=True)]


# ---------------------------------------------------------------- cruise and ballistic missiles
def tomahawk():
    """BGM-109 Tomahawk: 5.6 m long, 0.52 m body, pop-out wings, a chin intake for its little turbofan."""
    r = 0.26
    p = [body(NAVY, [(2.8, 0.0), (2.76, 0.05), (2.62, 0.14), (2.4, 0.21), (2.1, r), (-2.3, r), (-2.6, 0.22), (-2.8, 0.16), (-2.8, 0.0)]),
         band(NAVY_D, 2.1, 2.14, r), band(YELLOW, 1.2, 1.26, r), band(BROWN, -0.4, -0.34, r), band(NAVY_D, -2.3, -2.26, r),
         skin(NAVY_D, [(-1.25, 0, -0.27, 0.0, 0.0), (-1.3, 0, -0.3, 0.26, 0.12), (-1.6, 0, -0.33, 0.3, 0.18), (-2.2, 0, -0.3, 0.24, 0.1), (-2.4, 0, -0.26, 0, 0)], power=3),
         B(BLACK, -0.11, -0.38, -1.31, 0.22, 0.1, 0.01, plain=True),
         B(NAVY, r - 0.002, -0.06, 0.2, 0.004, 0.12, 1.3, plain=True, marks=[("left", "digits:BGM-109", 0.5, 0.5, 0)]),
         B(NAVY, -r - 0.002, -0.06, 0.2, 0.004, 0.12, 1.3, plain=True, marks=[("right", "digits:BGM-109", 0.5, 0.5, 0)])]
    p += wings(NAVY, 0.24, -0.1, 0.06, 0.56, 0.06, 1.33, -0.1, -0.04, 0.4, 0.035)
    p += fins(NAVY, 4, 0, r * 0.8, 0.62, -2.25, 0.5, -2.4, 0.3, 0.04)
    p += [lathe(BLACK, "z", 0, 0, [(-2.8, 0.13), (-2.72, 0.09), (-2.72, 0.0)], plain=True)]
    return _model(p)


def iskander():
    """9M723 Iskander: 7.3 m long, 0.92 m body, a long cone, four small fins and a ring of control jets."""
    r = 0.46
    p = [body(RGREEN, [(3.75, 0.0), (3.7, 0.04), (3.3, 0.15), (1.9, 0.33), (0.2, r), (-3.2, r), (-3.55, 0.4), (-3.55, 0.0)]),
         band(RGREEN_D, 0.18, 0.26, r), band(RGREEN_D, -1.72, -1.6, r), band(MWHITE, 3.28, 3.32, 0.15), band(RED, -3.1, -3.04, r),
         B(STEEL_D, -0.03, r - 0.01, -0.9, 0.06, 0.05, 1.6, plain=True), B(STEEL_D, -0.03, -r - 0.04, -2.4, 0.06, 0.05, 1.2, plain=True),
         B(RGREEN, r - 0.002, -0.1, -1.2, 0.004, 0.2, 1.0, plain=True, marks=[("left", "digits:9M723", 0.5, 0.5, 0)]),
         B(RGREEN, -r - 0.002, -0.1, -1.2, 0.004, 0.2, 1.0, plain=True, marks=[("right", "digits:9M723", 0.5, 0.5, 0)])]
    p += fins(RGREEN_D, 4, 45, r * 0.9, 0.82, -2.9, 0.65, -3.2, 0.35, 0.05)
    p += nozzle(-3.55, 0.36)
    for a in (0, 90, 180, 270):                                                     # jet vanes in the exhaust
        s, c = math.sin(math.radians(a)), math.cos(math.radians(a))
        p.append(B(STEEL_D, s * 0.2 - 0.02, c * 0.2 - 0.02, -3.7, 0.04, 0.04, 0.14, plain=True))
    return _model(p)


def atacms():
    """MGM-140 ATACMS: 4 m long, 0.61 m body, ogive nose, four folding tail fins."""
    r = 0.305
    p = [body(AOLIVE, [(2.0, 0.0), (1.96, 0.04), (1.8, 0.13), (1.4, 0.23), (0.9, 0.29), (0.6, r), (-1.9, r), (-2.0, 0.27), (-2.0, 0.0)]),
         band(BLACK, 1.8, 1.82, 0.13), band(YELLOW, 0.42, 0.5, r), band(BROWN, -0.68, -0.6, r), band(AOLIVE_D, -1.6, -1.56, r),
         B(AOLIVE, r - 0.002, -0.08, -0.4, 0.004, 0.16, 1.0, plain=True, marks=[("left", "digits:MGM-140", 0.5, 0.5, 0)]),
         B(AOLIVE, -r - 0.002, -0.08, -0.4, 0.004, 0.16, 1.0, plain=True, marks=[("right", "digits:MGM-140", 0.5, 0.5, 0)])]
    p += fins(AOLIVE_D, 4, 45, r * 0.9, 0.68, -1.35, 0.55, -1.6, 0.3, 0.04)
    p += nozzle(-2.0, 0.24)
    return _model(p)


def gmlrs():
    """M31 GMLRS: 3.9 m long, 0.23 m body, four small nose fins and wrap-round fins at the tail."""
    r = 0.115
    p = [body(AOLIVE, [(1.97, 0.0), (1.94, 0.02), (1.75, 0.06), (1.5, 0.095), (1.3, r), (-1.9, r), (-1.97, 0.1), (-1.97, 0.0)]),
         body(STEEL_D, [(1.97, 0.0), (1.94, 0.021), (1.8, 0.052), (1.8, 0.0)], plain=True),
         band(YELLOW, 1.0, 1.05, r), band(BROWN, -0.36, -0.3, r), band(AOLIVE_D, 1.28, 1.31, r), band(AOLIVE_D, -1.5, -1.47, r)]
    p += fins(STEEL_D, 4, 45, 0.09, 0.2, 1.52, 0.12, 1.49, 0.07, 0.014)
    p += fins(AOLIVE_D, 4, 45, r * 0.9, 0.3, -1.6, 0.34, -1.72, 0.2, 0.02)
    p += nozzle(-1.97, 0.09)
    return _model(p)


def hellfire():
    """AGM-114 Hellfire: 1.6 m long, 0.18 m body, glass seeker dome."""
    r = 0.089
    p = [body(MUNITION, [(-0.82, 0.0), (-0.82, 0.07), (-0.78, r), (0.62, r), (0.72, 0.085), (0.72, 0.0)]),
         body(STEEL_D, [(0.62, r + 0.001), (0.72, 0.086)], plain=True),
         body(GLASS, [(0.72, 0.085), (0.78, 0.07), (0.82, 0.04), (0.835, 0.0)]),
         band(YELLOW, 0.27, 0.3, r), band(BROWN, -0.3, -0.27, r)]
    p += fins(AF_DARK, 4, 45, 0.08, 0.17, 0.45, 0.14, 0.42, 0.08, 0.014)
    p += fins(AF_DARK, 4, 45, 0.08, 0.2, -0.45, 0.33, -0.55, 0.22, 0.02)
    p += nozzle(-0.82, 0.06)
    return _model(p)


def pantsir_missile():
    """Pantsir 57E6: a fat white booster carrying a thin dart with its own little fins."""
    p = [body(RGREEN, [(1.6, 0.0), (1.56, 0.012), (1.3, 0.045), (-0.1, 0.045), (-0.3, 0.085)]),
         body(MWHITE, [(-0.3, 0.085), (-1.55, 0.085), (-1.6, 0.07), (-1.6, 0.0)]),
         body(STEEL_D, [(1.6, 0.0), (1.56, 0.013), (1.42, 0.03), (1.42, 0.0)], plain=True),
         band(BLACK, -0.34, -0.3, 0.085), band(RED, -1.2, -1.17, 0.085)]
    p += fins(STEEL_D, 4, 45, 0.04, 0.13, 0.0, 0.16, -0.08, 0.08, 0.012)
    p += fins(STEEL_D, 4, 45, 0.08, 0.24, -1.3, 0.25, -1.42, 0.13, 0.016)
    p += nozzle(-1.6, 0.06)
    return _model(p)


def patriot_missile():
    """Patriot PAC-2: 5.3 m long, 0.41 m body, a long pointed white radome, four clipped fins."""
    r = 0.205
    p = [body(MWHITE, [(2.65, 0.0), (2.6, 0.02), (2.2, 0.11), (1.8, 0.17), (1.5, r)]),
         body(MSAND, [(1.5, r), (-2.6, r), (-2.65, 0.18), (-2.65, 0.0)]),
         band(STEEL_D, 1.48, 1.52, r), band(YELLOW, 1.14, 1.2, r), band(BROWN, -0.87, -0.8, r),
         B(MSAND, r - 0.002, -0.06, -0.3, 0.004, 0.12, 1.0, plain=True, marks=[("left", "digits:MIM-104", 0.5, 0.5, 0)]),
         B(MSAND, -r - 0.002, -0.06, -0.3, 0.004, 0.12, 1.0, plain=True, marks=[("right", "digits:MIM-104", 0.5, 0.5, 0)])]
    p += fins(MSAND, 4, 45, r * 0.9, 0.46, -2.0, 0.6, -2.3, 0.3, 0.035)
    p += nozzle(-2.65, 0.16)
    return _model(p)


# ---------------------------------------------------------------- shells, bombs and grenades
def shell():
    """155 mm howitzer shell, drawn a little oversize so it can be seen in flight."""
    r = 0.1
    return _model([body(SHELLG, [(0.3, 0.045), (0.2, 0.07), (0.05, 0.095), (-0.05, r), (-0.3, r), (-0.4, 0.085), (-0.42, 0.08), (-0.42, 0.0)]),
                   body(BRASS, [(0.42, 0.0), (0.41, 0.012), (0.36, 0.03), (0.3, 0.046), (0.3, 0.0)]),
                   band(YELLOW, -0.04, 0.0, r), band(COPPER, -0.29, -0.24, r + 0.003),
                   B(SHELLG, r - 0.004, -0.03, -0.22, 0.006, 0.06, 0.16, plain=True, marks=[("left", "digits:155", 0.5, 0.5, 0)])])


def mortar_bomb():
    """120 mm mortar bomb: a teardrop with a finned tail boom."""
    p = [body(SHELLG, [(0.28, 0.035), (0.2, 0.058), (0.1, 0.073), (0.0, 0.075), (-0.1, 0.06), (-0.2, 0.03), (-0.2, 0.0)]),
         body(STEEL, [(0.36, 0.0), (0.35, 0.01), (0.28, 0.036), (0.28, 0.0)]),
         body(STEEL_D, [(-0.2, 0.02), (-0.36, 0.02), (-0.36, 0.0)]), band(YELLOW, 0.04, 0.07, 0.075)]
    p += fins(STEEL_D, 6, 0, 0.018, 0.075, -0.24, 0.12, -0.26, 0.1, 0.006)
    return _model(p)


def grenade():
    """A drone-dropped grenade with a printed tail; also used for cluster bomblets."""
    p = [body(SHELLG, [(0.09, 0.035), (0.05, 0.058), (0.0, 0.06), (-0.04, 0.056), (-0.08, 0.025), (-0.08, 0.0)]),
         body(BRASS, [(0.13, 0.0), (0.125, 0.012), (0.09, 0.026), (0.09, 0.0)]),
         body(MWHITE, [(-0.08, 0.018), (-0.17, 0.018), (-0.17, 0.0)]), band(YELLOW, -0.01, 0.01, 0.06)]
    p += fins(MWHITE, 4, 45, 0.015, 0.075, -0.1, 0.07, -0.11, 0.06, 0.006)
    return _model(p)


#          model               builder          texels per metre
MODELS = {"tomahawk": (tomahawk, 128), "iskander": (iskander, 96), "atacms": (atacms, 128), "gmlrs": (gmlrs, 192),
          "hellfire": (hellfire, 384), "pantsir_missile": (pantsir_missile, 256), "patriot_missile": (patriot_missile, 128),
          "shell_155": (shell, 512), "mortar_bomb": (mortar_bomb, 640), "grenade": (grenade, 1024)}


if __name__ == "__main__":
    import sys
    from PIL import Image
    names = sys.argv[2:] or list(MODELS)
    tiles = []
    for name in names:
        fn, px = MODELS[name]
        model = fn()
        tex, w, h = bake(model, name, px)
        tiles.append(render(model, tex, math.radians(40), math.radians(20), 600))
        parts = model["base"]
        print(name, len(parts), "parts", sum(len(sf["faces"]) for p in parts for sf in p["surfaces"]), "faces", w, h)
    cols = 5
    sheet = Image.new("RGB", (cols * 600, ((len(tiles) + cols - 1) // cols) * 600), (150, 180, 210))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * 600, (i // cols) * 600))
    sheet.save(f"{sys.argv[1]}/munitions.png")
