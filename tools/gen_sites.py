#!/usr/bin/env python3
"""The three placed launch sites, built with the vehicle kit and drawn by Steel Rain's own renderer.
Origin at the rear-centre-bottom of the footprint, +z towards the front, +y up, in metres.

Preview without starting the game:  python3 tools/gen_sites.py <output dir>
"""
import math
from vehicle_kit import *
from gen_aircraft import export


def _model(parts):
    return dict(base=parts, turret=[], barrel=[], bones=[])


def tilt(parts, degrees, pivot):
    """Raises the front of a group of parts by an angle about a point: launch rails."""
    for p in parts:
        assert p["rot"] == (0, 0, 0), "a tilted part must not already be turned"
        p["rot"], p["pivot"] = (degrees, 0, 0), pivot
    return parts


# ---------------------------------------------------------------- ground control station: 3 x 3 x 6 footprint
def ground_station():
    """A drone ground control station: a 6 m equipment shelter on jacks with a satellite dish, a line-of-sight
    aerial mast, air conditioning, generator and cable reels."""
    t, d = TAN, TAN_D
    sec = [(-1.2, 0.3), (1.2, 0.3), (1.2, 2.56), (1.08, 2.7), (-1.08, 2.7), (-1.2, 2.56)]            # shelter cross-section
    p = [loft(t, "z", 0.0, sec, 6.0, None, marks=[("cap0", "hatch", 0.5, 0.56, 0.9, 1.9), ("s1", "panel", 0.3, 0.5, 1.4, 1.2),
                                                    ("s5", "panel", 0.7, 0.5, 1.4, 1.2)]),
         B(t, 1.2, 1.9, 1.9, 0.004, 0.3, 1.0, plain=True, marks=[("left", "digits:GCS-2", 0.5, 0.5, 0)]),
         B(t, -1.204, 1.9, 1.9, 0.004, 0.3, 1.0, plain=True, marks=[("right", "digits:GCS-2", 0.5, 0.5, 0)]),
         B(d, -1.22, 0.22, -0.02, 2.44, 0.1, 6.04, plain=True),                                         # base frame
         B(d, -1.1, 2.7, 0.1, 2.2, 0.03, 5.8, plain=True)]
    for z in (0.02, 1.5, 3.0, 4.5, 5.88):                                                               # frame ribs round the shelter
        p += P(d, 1.2, 0.3, z, 0.03, 2.26, 0.1, plain=True)
    for x in (1.0, -1.2):
        for z in (0.2, 5.6):
            p += jack(x + 0.1, z + 0.1, 0.3, d)
    # rear door: porthole, handle, hinges, folding steps
    p += rod_z(GLASS, 0.0, 1.9, -0.02, 0.03, 0.26) + [B(STEEL, 0.3, 1.3, -0.03, 0.06, 0.2, 0.04, plain=True)]
    p += [B(STEEL_D, -0.46, y, -0.03, 0.05, 0.12, 0.04, plain=True) for y in (0.6, 1.3, 2.0)]
    p += [B(STEEL_D, -0.45, 0.02 + i * 0.1, -0.3 - (2 - i) * 0.22, 0.9, 0.03, 0.24, plain=True) for i in range(3)]
    p += [bar(STEEL_D, (0.45, 0.02, -0.74), (0.45, 0.3, -0.02), 0.03), bar(STEEL_D, (-0.45, 0.02, -0.74), (-0.45, 0.3, -0.02), 0.03)]
    # air conditioners on the front wall, cable entry panel on the side
    p += [loft(STEEL_D, "z", 6.0, [(-1.0, 1.3), (-0.1, 1.3), (-0.1, 2.2), (-0.2, 2.3), (-0.9, 2.3), (-1.0, 2.2)], 6.4, None),
          B(GRILLE, -0.92, 1.45, 6.4, 0.74, 0.7, 0.02), B(STEEL_D, 0.2, 0.5, 6.0, 0.8, 0.9, 0.3), B(GRILLE, 0.26, 0.6, 6.3, 0.68, 0.7, 0.02),
          B(BLACK, 1.2, 0.6, 3.9, 0.05, 0.9, 0.9, plain=True)]
    for i in range(6):
        p += rod_x(STEEL, 1.24, 0.05, 0.72 + (i % 3) * 0.3, 4.1 + (i // 3) * 0.45, 0.08)
    p += run(BLACK, [(1.28, 0.72, 4.1), (1.42, 0.3, 4.0), (1.4, 0.05, 3.2), (1.35, 0.3, 2.3)], 0.06)
    # generator and cable drum on the ground beside it
    p += [hull(STEEL_D, 0.72, 1.45, [(2.0, 0.0), (2.0, 0.05), (1.2, 0.05), (1.2, 0.0)], plain=True)]
    p += rod_x(BLACK, 1.26, 0.2, 0.34, 1.7, 0.6) + rod_x(STEEL_D, 1.24, 0.24, 0.34, 1.7, 0.12)
    p += [B(STEEL_D, 1.22, 0.0, 1.5, 0.03, 0.5, 0.06, plain=True), B(STEEL_D, 1.46, 0.0, 1.5, 0.03, 0.5, 0.06, plain=True)]
    # roof: hand rails, ladder, lifting eyes, whip aerials
    p += handrail(STEEL_D, (1.05, 2.73, 0.2), (1.05, 2.73, 3.4), 0.25, 5) + handrail(STEEL_D, (-1.05, 2.73, 0.2), (-1.05, 2.73, 5.8), 0.25, 7)
    p += ladder(STEEL_D, -0.2, -0.06, 2.0, 2.75, 0.0) if False else ladder(STEEL_D, -1.26, 0.6, 0.35, 2.75, 0.45)
    for (x, z) in ((1.0, 0.15), (-1.0, 0.15), (1.0, 5.85), (-1.0, 5.85)):
        p += eye(x, 2.73, z)
    p += whip(-0.9, 5.6, 2.73, 2.6) + whip(-0.9, 5.2, 2.73, 1.8) + extinguisher(-0.75, 0.3, -0.12)
    # satellite dish: pedestal, yoke, a turned parabolic reflector leaning back at the sky, and its feed on three struts
    p += rod_y(d, 0.3, 4.8, 2.73, 0.9, 0.22) + [B(d, 0.0, 3.55, 4.62, 0.6, 0.18, 0.36)]
    dish = [lathe(HAWK_WHITE, "z", 0.3, 4.4, [(4.72, 0.0), (4.72, 0.16), (4.78, 0.45), (4.9, 0.72), (5.08, 0.95), (5.05, 0.95), (4.86, 0.7), (4.74, 0.44), (4.69, 0.16), (4.69, 0.0)]),
            lathe(d, "z", 0.3, 4.4, [(4.5, 0.0), (4.5, 0.2), (4.7, 0.16), (4.7, 0.0)]),
            lathe(STEEL_D, "z", 0.3, 4.4, [(5.42, 0.0), (5.42, 0.07), (5.6, 0.06), (5.62, 0.0)])]
    for a in (90, 210, 330):
        dish.append(bar(STEEL_D, (0.3 + 0.86 * math.cos(math.radians(a)), 4.4 + 0.86 * math.sin(math.radians(a)), 5.0), (0.3, 4.4, 5.5), 0.025))
    p += tilt(dish, 38, (0.3, 4.0, 4.8))
    p += [bar(d, (0.06, 3.7, 4.8), (0.06, 4.05, 4.8), 0.1), bar(d, (0.54, 3.7, 4.8), (0.54, 4.05, 4.8), 0.1)]
    # line-of-sight data link: a telescopic mast with a radome and a flat panel aerial
    p += [lathe(STEEL_D, "y", -0.5, 3.0, [(2.73, 0.0), (2.73, 0.16), (2.9, 0.14), (2.9, 0.09), (4.3, 0.09), (4.3, 0.11), (4.36, 0.11), (4.36, 0.06), (5.6, 0.06), (5.6, 0.0)]),
          lathe(RADOME, "y", -0.5, 3.0, [(5.6, 0.0), (5.6, 0.2), (5.86, 0.2), (5.98, 0.15), (6.04, 0.0)]),
          B(RADOME, -0.86, 4.6, 2.9, 0.1, 0.7, 0.5), B(STEEL_D, -0.78, 4.9, 3.1, 0.24, 0.08, 0.08, plain=True)]
    p += rod_y(RED, -0.5, 3.0, 6.04, 0.06, 0.06)
    return _model(p)


# ---------------------------------------------------------------- Orlan catapult: 1 x 1 x 4 footprint
def orlan_catapult():
    """A folding rail catapult: an inclined track on a rear base and a front A-frame, with a carriage, the
    launch cylinder slung underneath and a hand winch."""
    piv, ang = (0, 0.3, 0.2), 15
    p = [hull(OLIVE_D, -0.32, 0.32, [(0.05, 0.0), (0.05, 0.24), (0.12, 0.32), (0.34, 0.32), (0.42, 0.24), (0.42, 0.0)], plain=True),   # rear base block
         B(STEEL_D, -0.42, 0.0, 0.1, 0.84, 0.04, 0.3, plain=True)]
    p += [bar(STEEL_D, (0.3, 0.02, 0.24), (0.46, 0.02, -0.1), 0.05), bar(STEEL_D, (-0.3, 0.02, 0.24), (-0.46, 0.02, -0.1), 0.05)]       # rear outriggers
    rail = [B(STEEL, -0.07, 0.3, 0.2, 0.14, 0.03, 3.7, plain=True), B(STEEL, -0.07, 0.4, 0.2, 0.14, 0.02, 3.7, plain=True),              # top and bottom flanges
            B(STEEL_D, -0.012, 0.33, 0.2, 0.024, 0.07, 3.7, plain=True)]                                                                # web
    for z in (0.5, 1.2, 1.9, 2.6, 3.3):                                                                                                  # joint plates where it folds
        rail += [B(STEEL_D, -0.085, 0.29, z, 0.17, 0.14, 0.05, plain=True)]
    rail += [lathe(STEEL_D, "z", 0, 0.2, [(0.4, 0.0), (0.4, 0.055), (2.2, 0.055), (2.2, 0.03), (3.4, 0.03), (3.4, 0.0)]),                  # launch cylinder
             B(STEEL_D, -0.03, 0.2, 0.42, 0.06, 0.12, 0.05, plain=True), B(STEEL_D, -0.03, 0.2, 3.3, 0.06, 0.12, 0.05, plain=True),
             # carriage with a cradle for the aircraft, drawn back ready to fire
             B(OLIVE_D, -0.12, 0.42, 0.5, 0.24, 0.05, 0.5, plain=True), B(OLIVE_D, -0.16, 0.47, 0.55, 0.05, 0.12, 0.1, plain=True),
             B(OLIVE_D, 0.11, 0.47, 0.55, 0.05, 0.12, 0.1, plain=True), B(OLIVE_D, -0.16, 0.47, 0.85, 0.05, 0.1, 0.1, plain=True),
             B(OLIVE_D, 0.11, 0.47, 0.85, 0.05, 0.1, 0.1, plain=True),
             B(BLACK, -0.1, 0.3, 3.82, 0.2, 0.14, 0.08, plain=True)]                                                                    # buffer at the end
    rail += rod_x(STEEL, -0.1, 0.2, 0.35, 3.86, 0.07)
    p += tilt(rail, ang, piv)
    # front A-frame the rail rests on, with a spreader and foot pads
    top = (0.0, 0.3 + math.tan(math.radians(ang)) * 3.1 - 0.03, 3.3)
    for sx in (1, -1):
        p += [bar(STEEL_D, (sx * 0.42, 0.03, 3.4), (sx * 0.05, top[1], 3.3), 0.05)]
        p += [lathe(STEEL_D, "y", sx * 0.42, 3.4, [(0.0, 0.0), (0.0, 0.09), (0.03, 0.09), (0.03, 0.0)])]
    p += [bar(STEEL_D, (0.3, 0.38, 3.37), (-0.3, 0.38, 3.37), 0.03), bar(STEEL_D, (0.0, top[1], 3.3), (0.0, 0.34, 2.0), 0.035)]
    # hand winch and release lever at the back, pressure bottle on the ground
    p += rod_x(STEEL_D, 0.32, 0.1, 0.2, 0.24, 0.16) + [bar(STEEL, (0.42, 0.2, 0.24), (0.42, 0.34, 0.36), 0.025), bar(BLACK, (0.42, 0.34, 0.36), (0.5, 0.34, 0.36), 0.03)]
    p += [bar(RED, (-0.2, 0.32, 0.25), (-0.26, 0.56, 0.2), 0.03)]
    p += [lathe(STEEL, "z", -0.4, 0.09, [(0.7, 0.0), (0.7, 0.07), (0.76, 0.09), (1.3, 0.09), (1.36, 0.06), (1.42, 0.03), (1.42, 0.0)])]
    p += run(BLACK, [(-0.4, 0.09, 1.42), (-0.25, 0.12, 1.5), (-0.05, 0.4, 1.2)], 0.025)
    return _model(p)


# ---------------------------------------------------------------- Shahed launch rail: 3 x 2 x 5 footprint
def shahed_launcher():
    """A fixed-angle launch frame: a skid base, a pair of inclined rails with cross-bracing and saddles, front
    A-frames, a blast plate behind the booster and a firing box on a cable."""
    piv, ang = (0, 0.4, 0.3), 18
    d = OLIVE_D
    p = []
    for sx in (1, -1):                                                                                   # skid base
        x0, x1 = (0.9, 1.1) if sx > 0 else (-1.1, -0.9)
        p += [hull(d, x0, x1, [(0.0, 0.06), (0.1, 0.0), (4.9, 0.0), (5.0, 0.06), (5.0, 0.2), (0.0, 0.2)])]
        p += eye(sx * 1.0, 0.2, 0.3) + eye(sx * 1.0, 0.2, 4.7)
    for z in (0.3, 1.6, 2.9, 4.5):
        p += [B(d, -0.9, 0.04, z, 1.8, 0.12, 0.14, plain=True)]
    p += [bar(d, (0.9, 0.1, 0.4), (-0.9, 0.1, 1.6), 0.07), bar(d, (-0.9, 0.1, 1.7), (0.9, 0.1, 2.9), 0.07), bar(d, (0.9, 0.1, 3.0), (-0.9, 0.1, 4.5), 0.07)]
    # the inclined launch frame: two channel rails, cross-braces, saddles for the drone, a booster stop
    rail = []
    for sx in (1, -1):
        rail += [B(STEEL, sx * 0.3 - 0.06, 0.4, 0.3, 0.12, 0.16, 4.6, plain=True), B(STEEL_D, sx * 0.3 - 0.075, 0.56, 0.3, 0.15, 0.02, 4.6, plain=True)]
        rail += [B(d, sx * 0.3 - 0.1, 0.58, 1.0, 0.2, 0.1, 0.3, plain=True), B(d, sx * 0.3 - 0.1, 0.58, 3.2, 0.2, 0.1, 0.3, plain=True)]   # saddles
    for z in (0.4, 1.4, 2.4, 3.4, 4.7):
        rail += [B(STEEL_D, -0.3, 0.42, z, 0.6, 0.08, 0.08, plain=True)]
    rail += [bar(STEEL_D, (0.3, 0.46, 0.5), (-0.3, 0.46, 1.4), 0.04), bar(STEEL_D, (-0.3, 0.46, 1.5), (0.3, 0.46, 2.4), 0.04),
             bar(STEEL_D, (0.3, 0.46, 2.5), (-0.3, 0.46, 3.4), 0.04), bar(STEEL_D, (-0.3, 0.46, 3.5), (0.3, 0.46, 4.7), 0.04),
             B(BLACK, -0.36, 0.4, 0.22, 0.72, 0.3, 0.08, plain=True)]
    p += tilt(rail, ang, piv)
    # pivot trunnions at the back and A-frames holding the front up
    for sx in (1, -1):
        x0, x1 = (0.34, 0.44) if sx > 0 else (-0.44, -0.34)
        p += [hull(d, x0, x1, [(0.05, 0.16), (0.2, 0.62), (0.4, 0.62), (0.6, 0.16)])]
        p += rod_x(STEEL_D, x0 - 0.02, 0.14, 0.46, 0.3, 0.1)
        ty = 0.4 + math.tan(math.radians(ang)) * 4.0
        p += [bar(d, (sx * 1.0, 0.2, 4.6), (sx * 0.3, ty, 4.3), 0.09), bar(d, (sx * 1.0, 0.2, 3.2), (sx * 0.3, ty, 4.3), 0.07)]
        p += [lathe(STEEL_D, "z", sx * 0.6, 0.3, [(1.2, 0.0), (1.2, 0.07), (2.0, 0.07), (2.0, 0.04), (2.9, 0.04), (2.9, 0.0)], pitch=20, pivot=(sx * 0.6, 0.3, 1.2))]
    ty = 0.4 + math.tan(math.radians(ang)) * 4.0
    p += [bar(d, (0.34, ty - 0.02, 4.3), (-0.34, ty - 0.02, 4.3), 0.08)]
    # blast plate behind the booster, standing back from the rail
    p += [B(STEEL_D, -0.8, 0.2, 0.02, 1.6, 1.0, 0.05, pitch=-25, pivot=(0, 0.2, 0.05), marks=[("rear", "hazard", 0.5, 0.15, 1.4, 0.12)]),
          bar(d, (0.6, 0.2, 0.5), (0.6, 0.9, -0.28), 0.05), bar(d, (-0.6, 0.2, 0.5), (-0.6, 0.9, -0.28), 0.05)]
    # firing box on its cable, earth spike, a spare booster in its crate
    p += [B(d, 1.15, 0.2, 2.0, 0.3, 0.3, 0.4, marks=[("left", "panel", 0.5, 0.5, 0.3, 0.2), ("top", "digits:FIRE", 0.5, 0.5, 0)]),
          B(RED, 1.26, 0.5, 2.15, 0.06, 0.04, 0.06, plain=True)]
    p += run(BLACK, [(1.15, 0.3, 2.2), (0.9, 0.24, 2.1), (0.5, 0.3, 1.4), (0.3, 0.5, 0.7)], 0.03)
    p += [hull(d, -1.4, -1.12, [(1.2, 0.0), (1.2, 0.26), (1.26, 0.3), (2.6, 0.3), (2.66, 0.26), (2.66, 0.0)], plain=True),
          B(d, -1.404, 0.06, 1.5, 0.004, 0.18, 0.9, plain=True, marks=[("right", "digits:RATO", 0.5, 0.5, 0)])]
    p += rod_y(BRASS, 1.3, 0.6, 0.0, 0.4, 0.03)
    return _model(p)


# ---------------------------------------------------------------- Jammer Dome, fitted on a vehicle's roof
def jammer_dome():
    """A vehicle-mounted drone jammer: a mushroom radome on a short mast over a ring of stub aerials, on a
    base plate with clamps, a control box and its lead. Origin at the middle of the base, on the roof."""
    d = OLIVE_D
    p = [tower(d, 0.0, [(-0.34, -0.3), (0.34, -0.3), (0.38, -0.2), (0.38, 0.2), (0.34, 0.3), (-0.34, 0.3), (-0.38, 0.2), (-0.38, -0.2)], 0.04, plain=True)]
    for (x, z) in ((0.34, 0.24), (-0.34, 0.24), (0.34, -0.24), (-0.34, -0.24)):                           # clamps
        p += [B(STEEL_D, x - 0.04, -0.03, z - 0.04, 0.08, 0.1, 0.08, plain=True)] + rod_y(STEEL, x, z, 0.07, 0.03, 0.03)
    # mast and the mushroom radome with a rain lip
    p += [lathe(d, "y", 0, 0, [(0.04, 0.0), (0.04, 0.14), (0.08, 0.12), (0.1, 0.07), (0.3, 0.06), (0.32, 0.09), (0.32, 0.0)]),
          lathe(RADOME, "y", 0, 0, [(0.32, 0.0), (0.32, 0.27), (0.3, 0.3), (0.34, 0.31), (0.42, 0.29), (0.5, 0.24), (0.56, 0.15), (0.59, 0.06), (0.6, 0.0)]),
          lathe(BLACK, "y", 0, 0, [(0.365, 0.306), (0.385, 0.302)], plain=True, open=True)]
    # six stub aerials round the mast, each for its own band, on sprung bases
    for i in range(6):
        a = math.radians(60 * i)
        x, z, h = 0.24 * math.sin(a), 0.2 * math.cos(a), (0.14, 0.17, 0.11, 0.16, 0.1, 0.17)[i]
        p += [lathe(BLACK, "y", x, z, [(0.04, 0.0), (0.04, 0.03), (0.07, 0.03), (0.08, 0.02), (0.11, 0.02), (0.12, 0.014), (0.12 + h, 0.01), (0.12 + h, 0.0)], seg=8)]
    # control box with a status lamp, cooling fins, and the lead down into the vehicle
    p += [B(d, -0.16, 0.04, -0.29, 0.32, 0.12, 0.12, marks=[("rear", "panel", 0.5, 0.5, 0.24, 0.07)]),
          B(RED, 0.1, 0.16, -0.26, 0.03, 0.02, 0.03, plain=True), B(STEEL, -0.13, 0.16, -0.26, 0.06, 0.015, 0.04, plain=True)]
    p += [B(STEEL_D, -0.14 + i * 0.045, 0.16, -0.2, 0.012, 0.03, 0.06, plain=True) for i in range(4)]
    p += run(BLACK, [(0.16, 0.1, -0.24), (0.3, 0.06, -0.26), (0.4, 0.02, -0.2), (0.44, -0.04, -0.1)], 0.025)
    p += [B(WHITE, -0.1, 0.045, 0.3, 0.2, 0.06, 0.004, plain=True, marks=[("front", "digits:EW-14", 0.5, 0.5, 0)])]
    return _model(p)


# ---------------------------------------------------------------- worn backpacks
# Origin at the middle of the wearer's back, +z away from them, +y up. The wearer's body is 0.5 wide, 0.75 tall
# and 0.25 deep, so the straps run forward over the shoulders and down the chest.
def _harness(mat=BLACK):
    """Padded shoulder straps over the shoulders and down the chest, a chest strap, a waist belt and buckles."""
    p = []
    for sx in (1, -1):
        x = sx * 0.15
        path = [(x, 0.26, 0.02), (x, 0.39, -0.03), (x, 0.4, -0.13), (x, 0.38, -0.24), (x * 1.05, 0.2, -0.275), (x * 1.15, -0.02, -0.275),
                (sx * 0.22, -0.16, -0.2), (sx * 0.26, -0.24, -0.05), (sx * 0.2, -0.26, 0.02)]
        for a, b in zip(path, path[1:]):
            p.append(loft(mat, "y" if abs(b[1] - a[1]) > abs(b[2] - a[2]) else "z",
                          a[1] if abs(b[1] - a[1]) > abs(b[2] - a[2]) else a[2],
                          _strap(a, abs(b[1] - a[1]) > abs(b[2] - a[2])),
                          b[1] if abs(b[1] - a[1]) > abs(b[2] - a[2]) else b[2],
                          _strap(b, abs(b[1] - a[1]) > abs(b[2] - a[2])), plain=True))
        p.append(B(STEEL, x - 0.03, 0.02, -0.295, 0.06, 0.035, 0.02, plain=True))                          # adjuster buckle
    p += [B(mat, -0.16, 0.14, -0.29, 0.32, 0.03, 0.015, plain=True), B(STEEL_D, -0.03, 0.13, -0.3, 0.06, 0.05, 0.02, plain=True)]   # chest strap
    for sx in (1, -1):                                                                                   # waist belt round the hips
        p += [bar(mat, (sx * 0.2, -0.3, 0.02), (sx * 0.27, -0.3, -0.12), 0.045), bar(mat, (sx * 0.27, -0.3, -0.12), (sx * 0.12, -0.3, -0.275), 0.045)]
    p += [bar(mat, (0.12, -0.3, -0.275), (-0.12, -0.3, -0.275), 0.045), B(STEEL_D, -0.04, -0.33, -0.3, 0.08, 0.06, 0.025, plain=True)]
    return p


def _strap(pt, upright):
    """Cross-section of a flat strap at a point: 5 cm wide and 1.5 cm thick."""
    x, y, z = pt
    if upright:
        return [(x - 0.028, z - 0.008), (x + 0.028, z - 0.008), (x + 0.028, z + 0.008), (x - 0.028, z + 0.008)]
    return [(x - 0.028, y - 0.008), (x + 0.028, y - 0.008), (x + 0.028, y + 0.008), (x - 0.028, y + 0.008)]


def fiber_pack():
    """Fibre-optic spool backpack: a frame carrying a big reel of cable that pays out behind, with a guide arm."""
    d = OLIVE_D
    p = _harness()
    p += [loft(d, "z", 0.0, [(-0.2, -0.3), (0.2, -0.3), (0.21, -0.24), (0.21, 0.24), (0.17, 0.3), (-0.17, 0.3), (-0.21, 0.24), (-0.21, -0.24)], 0.07,
               None, marks=[("cap1", "panel", 0.5, 0.5, 0.3, 0.44)])]                                     # padded back plate
    p += [bar(STEEL_D, (0.19, -0.3, 0.05), (0.19, 0.3, 0.05), 0.03), bar(STEEL_D, (-0.19, -0.3, 0.05), (-0.19, 0.3, 0.05), 0.03),
          bar(STEEL_D, (0.19, 0.3, 0.05), (-0.19, 0.3, 0.05), 0.03), bar(STEEL_D, (0.19, -0.3, 0.05), (-0.19, -0.3, 0.05), 0.03)]   # tube frame
    # the reel on its axle across the pack: two flanges with lightening holes painted on, wound white cable between
    p += [lathe(SPOOL, "x", 0.0, 0.23, [(-0.13, 0.0), (-0.13, 0.15), (-0.06, 0.158), (0.06, 0.158), (0.13, 0.15), (0.13, 0.0)], seg=20)]
    for sx in (1, -1):
        x0, x1 = (0.13, 0.145) if sx > 0 else (-0.145, -0.13)
        p += [lathe(STEEL_D, "x", 0.0, 0.23, [(x0, 0.0), (x0, 0.185), (x1, 0.185), (x1, 0.0)], seg=20, wheel=(0.0, 0.23, 0.185, STEEL_D, 0.92))]
        p += [bar(STEEL_D, (sx * 0.16, -0.26, 0.06), (sx * 0.16, 0.0, 0.23), 0.025), bar(STEEL_D, (sx * 0.16, 0.26, 0.06), (sx * 0.16, 0.0, 0.23), 0.025)]   # reel brackets
        p += rod_x(STEEL, sx * 0.145 if sx > 0 else -0.175, 0.03, 0.0, 0.23, 0.04)
    # guide arm that leads the fibre off the back, with a ceramic eye, and the fibre trailing from it
    p += [bar(STEEL_D, (0.0, 0.26, 0.07), (0.0, 0.28, 0.4), 0.02), bar(STEEL_D, (0.0, 0.28, 0.4), (0.0, 0.18, 0.44), 0.02)]
    p += [lathe(WHITE, "z", 0.0, 0.16, [(0.43, 0.012), (0.43, 0.026), (0.45, 0.026), (0.45, 0.012)], plain=True)]
    p += run(SPOOL, [(0.0, 0.155, 0.3), (0.0, 0.16, 0.44), (0.0, 0.1, 0.56)], 0.006)
    # brake lever, a spare-connector pouch, and the length label
    p += [bar(ORANGE, (0.175, 0.05, 0.2), (0.24, 0.2, 0.16), 0.02), B(d, -0.15, -0.28, 0.07, 0.3, 0.12, 0.05, marks=[("front", "panel", 0.5, 0.5, 0.26, 0.09)])]
    p += [B(CYAN, -0.1, 0.2, 0.07, 0.2, 0.05, 0.004, plain=True, marks=[("front", "digits:10 KM", 0.5, 0.5, 0)])]
    return _model(p)


def jammer_pack():
    """Man-portable drone jammer: a rugged electronics pack with a cooling grille, control panel, battery and a
    fan of aerials of different lengths standing out of the top."""
    d, t = OLIVE_D, OLIVE
    p = _harness()
    p += [loft(t, "z", 0.0, [(-0.2, -0.3), (0.2, -0.3), (0.21, -0.26), (0.21, 0.26), (0.18, 0.3), (-0.18, 0.3), (-0.21, 0.26), (-0.21, -0.26)], 0.2,
               None, marks=[("cap1", "panel", 0.5, 0.36, 0.3, 0.22)])]                                    # the case
    for y in (-0.22, -0.02, 0.18):                                                                        # stiffening ribs round it
        p += [B(d, -0.215, y, 0.02, 0.43, 0.03, 0.185, plain=True)]
    p += [B(GRILLE, -0.14, -0.22, 0.2, 0.28, 0.2, 0.006), B(d, -0.16, -0.24, 0.2, 0.32, 0.02, 0.012, plain=True), B(d, -0.16, -0.02, 0.2, 0.32, 0.02, 0.012, plain=True)]
    # control panel: status lamps, band switches, a power knob
    p += [B(BLACK, -0.15, 0.04, 0.2, 0.3, 0.18, 0.008, plain=True)]
    for i, col in enumerate((RED, AMBER, WHITE, WHITE, WHITE)):
        p += rod_z(col, -0.11 + i * 0.055, 0.18, 0.208, 0.008, 0.018)
        p += [B(STEEL, -0.117 + i * 0.055, 0.1, 0.208, 0.014, 0.035, 0.012, plain=True)]
    p += rod_z(STEEL_D, 0.1, 0.07, 0.208, 0.02, 0.04)
    # battery clipped on the bottom, with its latch and lead
    p += [B(BLACK, -0.17, -0.38, 0.03, 0.34, 0.08, 0.15, marks=[("front", "digits:24V", 0.5, 0.5, 0)]), B(STEEL, -0.03, -0.31, 0.18, 0.06, 0.03, 0.012, plain=True)]
    p += run(BLACK, [(0.15, -0.36, 0.18), (0.2, -0.3, 0.21), (0.19, -0.2, 0.2)], 0.016)
    # carry handle and five aerials on sprung bases, one per band, fanned slightly outwards
    p += [bar(BLACK, (-0.08, 0.3, 0.1), (-0.08, 0.34, 0.1), 0.018), bar(BLACK, (0.08, 0.3, 0.1), (0.08, 0.34, 0.1), 0.018), bar(BLACK, (-0.08, 0.34, 0.1), (0.08, 0.34, 0.1), 0.022)]
    for x, z, h, lean in ((-0.16, 0.05, 0.55, -8), (-0.09, 0.15, 0.36, -4), (0.0, 0.05, 0.75, 0), (0.09, 0.15, 0.42, 4), (0.16, 0.05, 0.48, 8)):
        p += [lathe(BLACK, "y", x, z, [(0.3, 0.0), (0.3, 0.022), (0.33, 0.022), (0.34, 0.014), (0.38, 0.014), (0.39, 0.009), (0.3 + h * 0.7, 0.007),
                                       (0.3 + h, 0.004), (0.3 + h, 0.0)], seg=8, roll=-lean, pivot=(x, 0.3, z))]
    p += [B(WHITE, -0.08, -0.28, 0.2, 0.16, 0.035, 0.004, plain=True, marks=[("front", "digits:EW-25", 0.5, 0.5, 0)])]
    return _model(p)


# ---------------------------------------------------------------- the Field Laptop, held in the hands
def field_laptop():
    """Rugged field laptop: a thick olive case with rubber corner bumpers, a carry handle at the front and a lid
    on a hinge at the back (its own bone, "lid", closed flat; the renderer swings it open). The display is on the
    inside of the lid and shows the fire-control map. Origin at the middle of the base, on the bottom."""
    d, t = OLIVE_D, OLIVE
    W, D, T = 0.36, 0.27, 0.034                                    # width, depth, base thickness
    p = [B(t, -W / 2, 0, -D / 2, W, T, D, marks=[("bottom", "panel", 0.5, 0.5, 0.8, 0.8)])]
    for x in (-W / 2 - 0.006, W / 2 - 0.024):                    # rubber bumpers at the four corners
        for z in (-D / 2 - 0.006, D / 2 - 0.024):
            p += [B(BLACK, x, -0.004, z, 0.03, T + 0.008, 0.03, plain=True)]
    p += [B(BLACK, -W / 2 + 0.02, T, -D / 2 + 0.03, W - 0.04, 0.003, D * 0.5, plain=True)]          # keyboard bed
    for r in range(4):                                                                             # key rows
        for c in range(11):
            p += [B(STEEL_D, -W / 2 + 0.03 + c * 0.0275, T + 0.003, -D / 2 + 0.035 + r * 0.03, 0.022, 0.003, 0.024, plain=True)]
    p += [B(STEEL_D, -0.05, T + 0.002, D / 2 - 0.1, 0.1, 0.002, 0.06, plain=True)]                 # touch pad
    p += [B(GREEN_LAMP, W / 2 - 0.04, T + 0.001, D / 2 - 0.03, 0.01, 0.002, 0.006, plain=True),     # power and link lamps
          B(AMBER, W / 2 - 0.06, T + 0.001, D / 2 - 0.03, 0.01, 0.002, 0.006, plain=True)]
    # carry handle folded against the front edge, on two pivots
    p += [B(d, -0.09, 0.006, D / 2, 0.02, 0.02, 0.02, plain=True), B(d, 0.07, 0.006, D / 2, 0.02, 0.02, 0.02, plain=True),
          bar(BLACK, (-0.08, 0.016, D / 2 + 0.03), (0.08, 0.016, D / 2 + 0.03), 0.014)]
    p += [B(d, -W / 2 + 0.03, 0.012, -D / 2 - 0.004, 0.06, 0.012, 0.004, plain=True)]              # vents and ports at the back
    # the lid, lying closed on the base: shell on top, display underneath facing down
    L = 0.03
    lid = [B(t, -W / 2, T, -D / 2, W, L, D, marks=[("top", "panel", 0.5, 0.5, 0.85, 0.85)])]
    for x in (-W / 2 - 0.006, W / 2 - 0.024):
        for z in (-D / 2 - 0.006, D / 2 - 0.024):
            lid += [B(BLACK, x, T - 0.002, z, 0.03, L + 0.008, 0.03, plain=True)]
    lid += [B(BLACK, -W / 2 + 0.012, T - 0.002, -D / 2 + 0.012, W - 0.024, 0.002, D - 0.024, plain=True)]     # bezel
    lid += [B(SCREEN, -W / 2 + 0.024, T - 0.003, -D / 2 + 0.03, W - 0.048, 0.001, D - 0.07, plain=True)]      # display
    # what is on the display: a map with a grid, unit markers and the fire-control panels
    for i in range(1, 6):
        lid += [B(SCREEN_LINE, -W / 2 + 0.024 + i * (W - 0.048) / 6, T - 0.0034, -D / 2 + 0.03, 0.0015, 0.0003, D - 0.07, plain=True)]
    for i in range(1, 4):
        lid += [B(SCREEN_LINE, -W / 2 + 0.024, T - 0.0034, -D / 2 + 0.03 + i * (D - 0.07) / 4, W - 0.048, 0.0003, 0.0015, plain=True)]
    lid += [B(AMBER, 0.03, T - 0.0036, -0.01, 0.01, 0.0003, 0.01, plain=True), B(RED, -0.07, T - 0.0036, 0.03, 0.01, 0.0003, 0.01, plain=True),
            B(GREEN_LAMP, 0.09, T - 0.0036, -0.05, 0.008, 0.0003, 0.008, plain=True)]
    return dict(base=p, turret=[], barrel=[], bones=[{"name": "lid", "parent": "root", "pivot": (0, T, -D / 2), "cubes": lid}])


#          model               builder          texels per metre
MODELS = {"ground_station": (ground_station, 64), "orlan_catapult": (orlan_catapult, 160), "shahed_launcher": (shahed_launcher, 96),
          "jammer_dome": (jammer_dome, 512), "fiber_pack": (fiber_pack, 512), "jammer_pack": (jammer_pack, 512),
          "field_laptop": (field_laptop, 1400)}


if __name__ == "__main__":
    import sys
    from PIL import Image
    tiles = []
    for name in sys.argv[2:] or list(MODELS):
        fn, px = MODELS[name]
        model = fn()
        tex, w, h = bake(model, name, px)
        tiles += [render(model, tex, math.radians(a), math.radians(18), 800) for a in (35, 215)]
        parts = model["base"] + [c for b in model["bones"] for c in b["cubes"]]
        print(name, len(parts), "parts", sum(len(sf["faces"]) for p in parts for sf in p["surfaces"]), "faces", w, h)
    sheet = Image.new("RGB", (1600, 800 * (len(tiles) // 2)), (150, 180, 210))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % 2) * 800, (i // 2) * 800))
    sheet.save(f"{sys.argv[1]}/sites.png")
