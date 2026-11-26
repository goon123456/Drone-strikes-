#!/usr/bin/env python3
"""Aircraft and drone models, built with the same kit as the ground vehicles (vehicle_kit.py) and drawn in the
game by Steel Rain's own renderer. Origin on the fuselage axis, +z is the nose, +x the left wing, in metres.

Moving bones: "prop" spins about z, "r1".."r4" spin about y, "gearN/L/R" fold back about x, "grenade" is hidden
once dropped. Used by gen_assets.py.

Preview without starting the game:  python3 tools/gen_aircraft.py <output dir> [model ...]
"""
import math
from vehicle_kit import *


def _model(parts, bones):
    return dict(base=parts, turret=[], barrel=[], bones=bones)


def _bone(name, pivot, parts):
    return {"name": name, "parent": "root", "pivot": pivot, "cubes": parts}


def _gear(name, pivot, foot, dia, rim=STEEL, twin=False, door=None):
    """A landing-gear leg from its pivot down to a wheel standing at `foot`: strut, oleo, scissor link, wheel(s)."""
    px, py, pz = pivot
    fx, fy, fz = foot
    hub = (fx, fy + dia / 2, fz)
    parts = [bar(STEEL, pivot, hub, 0.09), bar(STEEL_D, pivot, (px + (fx - px) * 0.45, py + (hub[1] - py) * 0.45, pz + (fz - pz) * 0.45), 0.13),
             bar(STEEL_D, (hub[0], hub[1] + 0.1, hub[2] - 0.02), (hub[0], hub[1] + 0.35, hub[2] - 0.16), 0.035),
             bar(STEEL_D, (hub[0], hub[1] + 0.35, hub[2] - 0.16), (hub[0], hub[1] + 0.55, hub[2] - 0.02), 0.035)]
    w = dia * 0.34
    if twin:
        parts += wheel(fx + 0.05, w, hub[1], fz, dia, rim) + wheel(fx - 0.05 - w, w, hub[1], fz, dia, rim)
        parts += rod_x(STEEL, fx - 0.06, 0.12, hub[1], fz, 0.07)
    else:
        parts += wheel(fx - w / 2, w, hub[1], fz, dia, rim)
    if door:
        parts.append(B(door, px - 0.02 + (0.12 if px >= 0 else -0.14), py - 0.5, pz - 0.2, 0.02, 0.5, 0.4, plain=True))
    return _bone(name, pivot, parts)


def _cylinders(y, z_positions, inner, outer, radius, exhaust=None):
    """Air-cooled engine cylinders standing out either side of a crankcase: a finned barrel, a head with its
    rocker cover and spark plug, and optionally a short exhaust stub swept back underneath."""
    out = []
    for z in z_positions:
        for sx in (1, -1):
            fins = [(sx * inner, radius * 0.7)]
            n = max(3, int((outer - inner) / 0.014))
            for i in range(n):                                   # alternate fin and gap along the barrel
                a = inner + (outer - inner) * 0.78 * i / n
                b = inner + (outer - inner) * 0.78 * (i + 0.5) / n
                fins += [(sx * a, radius), (sx * b, radius), (sx * b, radius * 0.72), (sx * (inner + (outer - inner) * 0.78 * (i + 1) / n), radius * 0.72)]
            head = inner + (outer - inner) * 0.78
            fins += [(sx * head, radius * 0.95), (sx * outer * 0.985, radius * 0.9), (sx * outer, radius * 0.6), (sx * outer, 0.0)]
            out.append(lathe(STEEL_D, "x", y, z, fins, seg=12))
            out.append(bar(STEEL, (sx * outer, y + radius * 0.3, z), (sx * (outer + radius * 0.5), y + radius * 0.5, z), radius * 0.22))   # spark plug
            if exhaust:
                out.append(bar(COPPER, (sx * head, y - radius * 0.8, z), (sx * head * 0.9, y - radius * 1.6, z + exhaust), radius * 0.45))
    return out


def _hellfire(x, y, z):
    """An AGM-114 on its rail: seeker dome, body, mid and tail fins."""
    parts = [lathe(MUNITION, "z", x, y, [(z - 0.8, 0.0), (z - 0.8, 0.07), (z - 0.76, 0.089), (z + 0.6, 0.089), (z + 0.7, 0.085), (z + 0.7, 0.0)]),
             lathe(GLASS, "z", x, y, [(z + 0.7, 0.085), (z + 0.78, 0.07), (z + 0.83, 0.035), (z + 0.84, 0.0)]),
             lathe(YELLOW, "z", x, y, [(z + 0.3, 0.0905), (z + 0.34, 0.0905)])]
    for a in (45, 135, 225, 315):
        s, c = math.sin(math.radians(a)), math.cos(math.radians(a))
        parts.append(wing(AF_DARK, x + s * 0.08, y + c * 0.08, z - 0.45, 0.33, 0.02, x + s * 0.2, y + c * 0.2, z - 0.55, 0.2, 0.012))
        parts.append(wing(AF_DARK, x + s * 0.08, y + c * 0.08, z + 0.45, 0.14, 0.015, x + s * 0.16, y + c * 0.16, z + 0.42, 0.08, 0.01))
    return parts


def _sensor_ball(x, z, top, dia):
    """An electro-optical turret under the fuselage: a ball in a fork with a lens window."""
    r = dia / 2
    cy = top - r * 1.15
    pts = [(cy - r * math.cos(math.radians(a)), r * math.sin(math.radians(a))) for a in range(0, 181, 20)]
    return [lathe(STEEL_D, "y", x, z, [(top - r * 0.3, 0.0), (top - r * 0.3, r * 0.75), (top, r * 0.8), (top, 0.0)]),
            lathe(BLACK, "y", x, z, pts),
            lathe(GLASS, "z", x, cy, [(z + r * 0.86, r * 0.42), (z + r * 0.99, r * 0.36), (z + r * 1.01, 0.0)]),
            lathe(GLASS, "z", x + r * 0.5, cy + r * 0.25, [(z + r * 0.7, r * 0.2), (z + r * 0.86, r * 0.16), (z + r * 0.88, 0.0)])]


# ---------------------------------------------------------------- MQ-9 Reaper: 11 m long, 20 m span
def reaper():
    g, d = AF_GREY, AF_DARK
    parts = [skin(g, [(5.5, 0, -0.1, 0, 0), (5.42, 0, -0.09, 0.2, 0.22), (5.2, 0, -0.04, 0.5, 0.54), (4.9, 0, 0.02, 0.76, 0.84),
                      (4.4, 0, 0.09, 0.95, 1.12), (3.8, 0, 0.11, 1.0, 1.2), (3.0, 0, 0.07, 1.0, 1.1), (2.0, 0, 0.02, 0.98, 0.98),
                      (-1.0, 0, 0, 0.95, 0.92), (-3.3, 0, 0, 0.9, 0.88), (-4.2, 0, 0.02, 0.78, 0.76), (-4.9, 0, 0.03, 0.6, 0.6), (-5.0, 0, 0.03, 0.44, 0.44)],
                  power=2.3),
             lathe(STEEL_D, "z", 0, 0.03, [(-5.0, 0.22), (-5.06, 0.2), (-5.06, 0.0)]),                           # propeller shaft housing
             skin(d, [(-2.2, 0, 0.5, 0.36, 0.2), (-2.4, 0, 0.56, 0.4, 0.26), (-3.4, 0, 0.5, 0.34, 0.16), (-3.9, 0, 0.44, 0.1, 0.04)]),   # engine air scoop
             B(BLACK, -0.15, 0.48, -2.19, 0.3, 0.16, 0.01, plain=True),
             skin(STEEL_D, [(-4.1, 0, -0.5, 0.2, 0.12), (-4.7, 0, -0.5, 0.24, 0.14)]),                             # exhaust
             # nose radome line, anti-glare and markings
             B(g, 0.478, -0.2, -2.6, 0.006, 0.34, 2.2, plain=True, marks=[("left", "digits:U.S. AIR FORCE", 0.5, 0.5, 0)]),
             B(g, -0.484, -0.2, -2.6, 0.006, 0.34, 2.2, plain=True, marks=[("right", "digits:U.S. AIR FORCE", 0.5, 0.5, 0)]),
             B(g, 0.486, -0.2, 0.2, 0.006, 0.5, 0.9, plain=True, marks=[("left", "roundel", 0.5, 0.5, 0.36)]),
             B(g, -0.492, -0.2, 0.2, 0.006, 0.5, 0.9, plain=True, marks=[("right", "roundel", 0.5, 0.5, 0.36)])]
    # long, slender wings with a little dihedral and winglets
    parts += wings(g, 0.4, -0.2, 0.62, 1.5, 0.22, 5.5, 0.12, 0.32, 1.05, 0.14)
    parts += wings(g, 5.5, 0.12, 0.32, 1.05, 0.14, 10.0, 0.5, 0.02, 0.6, 0.07)
    parts += wings(d, 10.0, 0.5, 0.02, 0.6, 0.06, 10.14, 0.98, -0.2, 0.3, 0.035)
    for sx in (1, -1):                                                                                            # ailerons and flaps as inset panels
        parts += [wing(d, sx * 6.2, 0.175, -0.42, 0.26, 0.05, sx * 9.6, 0.47, -0.36, 0.2, 0.03),
                  wing(d, sx * 1.2, -0.13, -0.62, 0.3, 0.07, sx * 5.2, 0.1, -0.5, 0.26, 0.05)]
        parts += [B(STEEL_D, sx * 9.98 - 0.02, 0.5, -0.1, 0.04, 0.04, 0.08, plain=True)]                           # navigation lights
    # V tail and the fin underneath
    parts += wings(g, 0.3, 0.25, -3.2, 1.45, 0.15, 2.3, 2.35, -4.2, 0.75, 0.07)
    parts += [wing(g, 0, -0.36, -3.6, 1.2, 0.13, 0, -1.4, -4.2, 0.6, 0.06)]
    for sx in (1, -1):
        parts += [B(g, sx * 1.6 - 0.003, 1.55, -4.5, 0.006, 0.4, 0.7, roll=-sx * 43, plain=True, marks=[("left" if sx > 0 else "right", "digits:AF 07 104", 0.5, 0.5, 0)])]
    parts += _sensor_ball(0, 4.25, -0.42, 0.56)
    # aerials, pitot, lights
    parts += [wing(d, 0, 0.5, 0.9, 0.3, 0.03, 0, 0.82, 0.8, 0.16, 0.02), wing(d, 0, -0.5, 1.4, 0.26, 0.03, 0, -0.8, 1.3, 0.14, 0.02),
              wing(d, 0, 0.6, 2.9, 0.2, 0.03, 0, 0.8, 2.85, 0.1, 0.02), bar(STEEL, (0.2, -0.3, 5.1), (0.2, -0.32, 5.5), 0.02)]
    parts += rod_y(RED, 0, -1.0, 0.46, 0.05, 0.06)
    # two weapon stations a side, each a pylon with a twin rail of Hellfires
    for sx in (1, -1):
        for x, y in ((2.4, -0.3), (3.9, -0.2)):
            cx = sx * x
            parts += [wing(d, cx, y, 0.2, 0.9, 0.08, cx, y - 0.3, 0.1, 0.8, 0.07), B(STEEL_D, cx - 0.2, y - 0.36, -0.75, 0.4, 0.06, 0.95, plain=True)]
            parts += _hellfire(cx - 0.13, y - 0.46, -0.3) + _hellfire(cx + 0.13, y - 0.46, -0.3)
    bones = [_bone("prop", (0, 0.03, -5.16), propeller(STEEL_D, 0, 0.03, -5.16, 2.7, 3, 0.3, facing=-1, hub=STEEL_D)),
             _gear("gearN", (0, -0.42, 3.5), (0, -1.6, 3.55), 0.42, door=d),
             _gear("gearL", (0.45, -0.3, -0.9), (1.25, -1.6, -0.85), 0.5),
             _gear("gearR", (-0.45, -0.3, -0.9), (-1.25, -1.6, -0.85), 0.5)]
    return _model(parts, bones)


# ---------------------------------------------------------------- Bayraktar TB2: 6.5 m long, 12 m span
def tb2():
    g, d = TB2_GREY, TB2_DARK
    parts = [skin(g, [(3.25, 0, -0.05, 0, 0), (3.15, 0, -0.04, 0.2, 0.18), (2.8, 0, 0.0, 0.58, 0.46), (2.0, 0, 0.04, 0.82, 0.66), (1.2, 0, 0.05, 0.92, 0.72),
                      (-0.6, 0, 0.05, 0.95, 0.7), (-1.4, 0, 0.08, 0.76, 0.62), (-1.9, 0, 0.1, 0.56, 0.52), (-2.0, 0, 0.1, 0.46, 0.46)], power=2.6),
             lathe(STEEL_D, "z", 0, 0.1, [(-2.0, 0.2), (-2.25, 0.19), (-2.3, 0.1), (-2.34, 0.06), (-2.34, 0.0)]),   # pusher engine
             *_cylinders(0.1, (-2.08, -2.22), 0.16, 0.32, 0.06, exhaust=-0.14),                                     # its four cylinders
             B(g, 0.44, -0.12, 0.2, 0.006, 0.3, 1.2, plain=True, marks=[("left", "digits:T 253", 0.5, 0.5, 0)]),
             B(g, -0.446, -0.12, 0.2, 0.006, 0.3, 1.2, plain=True, marks=[("right", "digits:T 253", 0.5, 0.5, 0)])]
    # blended wing roots, long tapered panels and upturned tips
    parts += wings(g, 0.38, 0.15, 0.8, 1.55, 0.24, 1.5, 0.2, 0.47, 1.05, 0.17)
    parts += wings(g, 1.5, 0.2, 0.47, 1.05, 0.17, 5.7, 0.38, 0.22, 0.55, 0.07)
    parts += wings(g, 5.7, 0.38, 0.22, 0.55, 0.07, 6.0, 0.62, 0.06, 0.3, 0.04)
    for sx in (1, -1):
        parts += [wing(d, sx * 3.2, 0.275, -0.36, 0.18, 0.04, sx * 5.5, 0.37, -0.22, 0.14, 0.03)]
        # twin booms carrying the inverted-V tail
        parts += [skin(g, [(0.2, sx * 1.5, 0.2, 0.16, 0.16), (-1.0, sx * 1.5, 0.2, 0.14, 0.14), (-3.4, sx * 1.5, 0.2, 0.1, 0.1), (-3.5, sx * 1.5, 0.2, 0, 0)])]
        parts += [wing(g, sx * 1.5, 0.2, -2.72, 0.8, 0.07, 0.0, 1.36, -2.98, 0.55, 0.06)]
        parts += [wing(d, sx * 1.3, 0.34, -3.36, 0.2, 0.03, sx * 0.2, 1.2, -3.36, 0.16, 0.03)]
    parts += _sensor_ball(0, 1.9, -0.3, 0.44)
    parts += [wing(d, 0, 0.4, 1.1, 0.16, 0.03, 0, 0.9, 1.05, 0.08, 0.02), wing(d, 0, 0.4, 0.2, 0.2, 0.03, 0, 0.62, 0.15, 0.12, 0.02),
              bar(STEEL, (0.15, -0.2, 3.0), (0.15, -0.22, 3.3), 0.018)]
    for sx in (1, -1):                                                                                            # four small guided munitions
        for x in (2.0, 2.9):
            cx, y = sx * x, 0.14
            parts += [wing(d, cx, y, 0.1, 0.45, 0.06, cx, y - 0.2, 0.05, 0.4, 0.05),
                      lathe(MUNITION, "z", cx, y - 0.28, [(-0.5, 0.0), (-0.5, 0.06), (-0.44, 0.08), (0.3, 0.08), (0.42, 0.05), (0.5, 0.0)])]
            for a in (45, 135, 225, 315):
                s, c = math.sin(math.radians(a)), math.cos(math.radians(a))
                parts.append(wing(d, cx + s * 0.07, y - 0.28 + c * 0.07, -0.3, 0.2, 0.015, cx + s * 0.17, y - 0.28 + c * 0.17, -0.36, 0.12, 0.01))
    bones = [_bone("prop", (0, 0.1, -2.42), propeller(STEEL_D, 0, 0.1, -2.42, 1.5, 2, 0.2, facing=-1, hub=STEEL_D)),
             _gear("gearN", (0, -0.2, 2.2), (0, -1.1, 2.25), 0.3),
             _gear("gearL", (0.4, -0.15, -0.5), (0.8, -1.1, -0.45), 0.36),
             _gear("gearR", (-0.4, -0.15, -0.5), (-0.8, -1.1, -0.45), 0.36)]
    return _model(parts, bones)


# ---------------------------------------------------------------- RQ-4 Global Hawk: 14.5 m long, 40 m span
def global_hawk():
    g, w = HAWK_GREY, HAWK_WHITE
    parts = [  # the whale-shaped nose hides a big satellite dish
        skin(g, [(7.25, 0, 0.0, 0, 0), (7.15, 0, 0.02, 0.4, 0.4), (6.8, 0, 0.14, 1.1, 1.14), (6.2, 0, 0.3, 1.6, 1.78), (5.4, 0, 0.38, 1.76, 2.0),
                 (4.4, 0, 0.34, 1.72, 1.9), (3.2, 0, 0.22, 1.6, 1.62), (1.6, 0, 0.04, 1.48, 1.34), (-3.0, 0, 0.0, 1.38, 1.22),
                 (-4.6, 0, 0.0, 1.3, 1.16), (-6.2, 0, 0.1, 0.86, 0.76), (-7.1, 0, 0.15, 0.5, 0.44), (-7.2, 0, 0.15, 0, 0)], power=2.4),
        skin(w, [(5.2, 0, -0.5, 0.9, 0.32), (4.0, 0, -0.56, 1.2, 0.36), (-3.0, 0, -0.5, 1.0, 0.26), (-4.6, 0, -0.44, 0.6, 0.2)], power=3),   # pale belly
        # jet engine on the back, between the tail fins
        skin(g, [(0.3, 0, 1.3, 1.1, 1.1), (0.1, 0, 1.3, 1.3, 1.3), (-1.2, 0, 1.3, 1.42, 1.42), (-3.8, 0, 1.22, 1.26, 1.26), (-4.8, 0, 1.18, 1.0, 1.0), (-5.4, 0, 1.15, 0.8, 0.8)]),
        lathe(BLACK, "z", 0, 1.3, [(0.31, 0.5), (0.1, 0.46), (-0.1, 0.3), (-0.1, 0.0)]),                           # intake
        lathe(STEEL_D, "z", 0, 1.3, [(0.0, 0.0), (0.0, 0.16), (0.22, 0.08), (0.3, 0.0)]),                          # fan spinner
        lathe(STEEL_D, "z", 0, 1.15, [(-5.4, 0.38), (-5.6, 0.34), (-5.6, 0.28), (-5.3, 0.3)]),                     # exhaust
        skin(g, [(0.5, 0, 0.72, 0.9, 0.5), (-2.0, 0, 0.7, 0.86, 0.46), (-4.6, 0, 0.66, 0.7, 0.36)], power=3),       # pylon joining engine and body
        B(g, 0.746, -0.1, 0.6, 0.006, 0.6, 1.2, plain=True, marks=[("left", "roundel", 0.5, 0.5, 0.44)]),
        B(g, -0.752, -0.1, 0.6, 0.006, 0.6, 1.2, plain=True, marks=[("right", "roundel", 0.5, 0.5, 0.44)]),
        B(g, 0.83, 0.1, 3.6, 0.006, 0.5, 2.4, plain=True, marks=[("left", "digits:U.S. AIR FORCE", 0.5, 0.5, 0)]),
        B(g, -0.836, 0.1, 3.6, 0.006, 0.5, 2.4, plain=True, marks=[("right", "digits:U.S. AIR FORCE", 0.5, 0.5, 0)])]
    # enormous glider-like wings that bow upwards in flight
    parts += wings(w, 0.6, -0.36, 0.95, 2.4, 0.32, 4.0, -0.16, 0.62, 1.9, 0.24)
    parts += wings(w, 4.0, -0.16, 0.62, 1.9, 0.24, 12.0, 0.4, -0.08, 1.2, 0.14)
    parts += wings(w, 12.0, 0.4, -0.08, 1.2, 0.14, 19.9, 1.12, -0.78, 0.65, 0.06)
    for sx in (1, -1):
        parts += [wing(g, sx * 13.0, 0.5, -1.2, 0.34, 0.05, sx * 19.4, 1.08, -1.25, 0.22, 0.03),
                  wing(g, sx * 4.6, -0.1, -1.24, 0.5, 0.08, sx * 11.6, 0.38, -1.08, 0.4, 0.06)]
        parts += [B(g, sx * 1.9 - 0.004, 2.2, -5.9, 0.008, 0.7, 1.0, roll=-sx * 40, plain=True,
                    marks=[("left" if sx > 0 else "right", "digits:BB", 0.5, 0.35, 0), ("left" if sx > 0 else "right", "digits:02 2010", 0.5, 0.7, 0)])]
    parts += wings(g, 0.45, 0.35, -4.4, 1.9, 0.17, 2.9, 3.3, -5.9, 0.95, 0.08)                                      # V tail
    # sensor fairings under the chin and belly, aerials, pitot
    parts += [skin(STEEL_D, [(6.1, 0, -0.56, 0.3, 0.1), (5.8, 0, -0.66, 0.72, 0.34), (5.0, 0, -0.7, 0.8, 0.38), (4.6, 0, -0.66, 0.4, 0.2)], power=3)]
    parts += _sensor_ball(0, 5.5, -0.8, 0.5)
    parts += [wing(g, 0, 1.9, -2.3, 0.3, 0.04, 0, 2.3, -2.4, 0.16, 0.03), wing(g, 0, -0.6, 1.0, 0.3, 0.04, 0, -0.95, 0.9, 0.16, 0.03),
              bar(STEEL, (0, 0.0, 7.2), (0, 0.0, 7.7), 0.025)]
    bones = [_gear("gearN", (0, -0.5, 4.3), (0, -1.7, 4.35), 0.45, twin=True, door=g),
             _gear("gearL", (0.9, -0.4, -1.2), (1.6, -1.7, -1.15), 0.6, twin=True),
             _gear("gearR", (-0.9, -0.4, -1.2), (-1.6, -1.7, -1.15), 0.6, twin=True)]
    return _model(parts, bones)


# ---------------------------------------------------------------- Orlan-10: 2 m long, 3.1 m span
def orlan():
    g = ORLAN_GREY
    parts = [lathe(STEEL_D, "z", 0, 0, [(0.8, 0.1), (0.9, 0.09), (0.95, 0.05), (0.95, 0.0)]),                       # engine nose
             *_cylinders(0.0, (0.86,), 0.07, 0.17, 0.035),                                                          # its two cylinders
             skin(g, [(0.8, 0, 0.0, 0.2, 0.22), (0.6, 0, -0.01, 0.26, 0.3), (0.2, 0, -0.01, 0.27, 0.32), (-0.15, 0, -0.01, 0.26, 0.3),
                      (-0.6, 0, 0.03, 0.14, 0.16), (-1.0, 0, 0.06, 0.08, 0.09), (-1.02, 0, 0.06, 0, 0)], power=2.6),
             skin(BLACK, [(0.42, 0, -0.16, 0, 0), (0.36, 0, -0.18, 0.1, 0.05), (0.25, 0, -0.19, 0.15, 0.08), (0.14, 0, -0.18, 0.12, 0.06),
                          (0.08, 0, -0.16, 0, 0)], seg=12),                                                         # camera blister
             B(GLASS, -0.04, -0.232, 0.2, 0.08, 0.004, 0.1, plain=True),
             bar(STEEL_D, (0, 0.16, -0.2), (0, 0.4, -0.22), 0.012),                                                 # aerial
             B(g, -0.14, 0.14, -0.05, 0.28, 0.06, 0.42, plain=True),                                                # wing saddle
             B(g, 0.134, -0.08, -0.1, 0.004, 0.14, 0.4, plain=True, marks=[("left", "digits:10332", 0.5, 0.5, 0)])]
    # high wing with a kink and raised tips; red tip panels
    parts += wings(g, 0.0, 0.19, 0.38, 0.44, 0.055, 0.75, 0.2, 0.36, 0.38, 0.05)
    parts += wings(g, 0.75, 0.2, 0.36, 0.38, 0.05, 1.4, 0.29, 0.31, 0.27, 0.035)
    parts += wings(RED, 1.4, 0.29, 0.31, 0.27, 0.035, 1.55, 0.31, 0.3, 0.24, 0.03)
    parts += [wing(g, 0, 0.08, -0.72, 0.3, 0.028, 0, 0.42, -0.86, 0.18, 0.02)]                                      # fin
    parts += wings(g, 0.0, 0.1, -0.74, 0.26, 0.024, 0.45, 0.1, -0.78, 0.18, 0.018)                                   # tailplane
    return _model(parts, [_bone("prop", (0, 0, 0.99), propeller(BLACK, 0, 0, 0.99, 0.55, 2, 0.085, facing=1, hub=STEEL_D))])


# ---------------------------------------------------------------- Shahed-136: 3.5 m long, 2.5 m span
def shahed():
    g, d = SHAHED_WHITE, SHAHED_DARK
    parts = [skin(g, [(1.75, 0, 0, 0, 0), (1.68, 0, 0, 0.14, 0.14), (1.45, 0, 0, 0.32, 0.32), (0.9, 0, 0, 0.46, 0.46), (-0.6, 0, 0, 0.46, 0.44),
                      (-1.0, 0, 0, 0.4, 0.38), (-1.22, 0, 0, 0.3, 0.29), (-1.26, 0, 0, 0.26, 0.25)]),
             # the engine: a flat-four on the tail with its crankcase, four finned cylinders and the propeller shaft
             lathe(STEEL_D, "z", 0, 0, [(-1.26, 0.12), (-1.3, 0.1), (-1.5, 0.1), (-1.53, 0.07), (-1.56, 0.05), (-1.56, 0.0)]),
             B(STEEL_D, -0.06, 0.08, -1.48, 0.12, 0.05, 0.18, plain=True),                                          # carburettor
             bar(STEEL_D, (0, 0.2, 0.3), (0, 0.34, 0.28), 0.014),
             *_cylinders(0.0, (-1.34, -1.46), 0.09, 0.25, 0.055, exhaust=-0.12),
             B(g, 0.232, -0.08, -0.4, 0.004, 0.16, 0.7, plain=True, marks=[("left", "digits:M 214", 0.5, 0.5, 0)]),
             B(g, -0.236, -0.08, -0.4, 0.004, 0.16, 0.7, plain=True, marks=[("right", "digits:M 214", 0.5, 0.5, 0)])]
    # cropped delta blended into the body, with elevons and fins on the tips above and below
    parts += wings(g, 0.0, 0.0, 1.05, 2.45, 0.22, 0.55, 0.0, 0.22, 1.6, 0.13)
    parts += wings(g, 0.55, 0.0, 0.22, 1.6, 0.13, 1.25, 0.0, -0.88, 0.5, 0.05)
    parts += wings(d, 0.55, 0.003, -1.14, 0.26, 0.06, 1.22, 0.003, -1.2, 0.2, 0.035)
    for sx in (1, -1):
        parts += [wing(g, sx * 1.25, 0.0, -0.9, 0.5, 0.045, sx * 1.25, 0.5, -1.08, 0.28, 0.03),
                  wing(g, sx * 1.25, 0.0, -0.9, 0.5, 0.045, sx * 1.25, -0.34, -1.0, 0.36, 0.03)]
    return _model(parts, [_bone("prop", (0, 0, -1.6), propeller(WOOD, 0, 0, -1.6, 0.8, 2, 0.12, facing=-1, hub=STEEL_D))])


# ---------------------------------------------------------------- racing-style attack quadcopter
def _rotor(name, x, y, z, dia, mat, blades=3):
    r = dia / 2
    parts = rod_y(STEEL_D, x, z, y - 0.004, 0.012, 0.03)
    for i in range(blades):
        a = math.radians(360 * i / blades)
        s, c = math.sin(a), math.cos(a)
        parts.append(loft(mat, "y", y, [(x + s * 0.012 + c * 0.012, z + c * 0.012 - s * 0.012), (x + s * r + c * 0.01, z + c * r - s * 0.01),
                                        (x + s * r - c * 0.006, z + c * r + s * 0.006), (x + s * 0.012 - c * 0.01, z + c * 0.012 + s * 0.01)], y + 0.003, None, plain=True))
    return _bone(name, (x, y, z), parts)


def fpv(fiber=False):
    props = CYAN if fiber else ORANGE
    parts = [tower(CARBON, 0.0, [(-0.05, -0.13), (0.05, -0.13), (0.06, -0.02), (0.05, 0.15), (-0.05, 0.15), (-0.06, -0.02)], 0.012),   # bottom plate
             tower(CARBON, 0.045, [(-0.045, -0.11), (0.045, -0.11), (0.045, 0.13), (-0.045, 0.13)], 0.055),                             # top plate
             B(STEEL_D, -0.03, 0.014, -0.06, 0.06, 0.028, 0.1, plain=True),                                                              # flight electronics stack
             B(LIPO, -0.04, 0.056, -0.09, 0.08, 0.04, 0.17, plain=True, marks=[("top", "digits:1300", 0.5, 0.5, 0)]),                    # battery
             B(BLACK, -0.045, 0.07, -0.03, 0.09, 0.03, 0.03, plain=True), B(BLACK, -0.045, 0.07, 0.04, 0.09, 0.03, 0.02, plain=True),    # straps
             B(BLACK, -0.028, 0.012, 0.13, 0.056, 0.04, 0.035, pitch=20, plain=True),                                                    # camera, tilted up
             bar(STEEL_D, (0, 0.05, -0.15), (0, 0.16, -0.18), 0.008), bar(STEEL_D, (0.03, 0.03, -0.12), (0.06, 0.06, -0.2), 0.004),
             bar(STEEL_D, (-0.03, 0.03, -0.12), (-0.06, 0.06, -0.2), 0.004)]
    parts += rod_z(GLASS, 0, 0.036, 0.16, 0.012, 0.03) + rod_y(BLACK, 0, -0.18, 0.155, 0.03, 0.03)
    for (x, z) in ((0.035, 0.09), (-0.035, 0.09), (0.035, -0.09), (-0.035, -0.09)):
        parts += rod_y(STEEL, x, z, 0.012, 0.033, 0.008)                                                                                 # stand-offs
    # shaped-charge warhead slung underneath, nose forward, held by two cable ties
    parts += [lathe(MUNITION, "z", 0, -0.055, [(-0.14, 0.0), (-0.14, 0.02), (0.02, 0.025), (0.08, 0.045), (0.2, 0.0425), (0.2, 0.0)]),
              lathe(BRASS, "z", 0, -0.055, [(0.2, 0.0425), (0.27, 0.02), (0.3, 0.008), (0.3, 0.0)]),
              B(BLACK, -0.05, -0.1, 0.0, 0.1, 0.1, 0.008, plain=True), B(BLACK, -0.05, -0.1, 0.13, 0.1, 0.1, 0.008, plain=True)]
    if fiber:   # the cable reel hangs behind the warhead and pays out backwards
        parts += [lathe(SPOOL, "z", 0, -0.07, [(-0.33, 0.0), (-0.33, 0.065), (-0.17, 0.065), (-0.17, 0.0)]),
                  lathe(STEEL_D, "z", 0, -0.07, [(-0.34, 0.0), (-0.34, 0.075), (-0.33, 0.075), (-0.33, 0.0)]),
                  lathe(STEEL_D, "z", 0, -0.07, [(-0.17, 0.075), (-0.16, 0.075), (-0.16, 0.0)]),
                  B(STEEL_D, -0.01, -0.02, -0.3, 0.02, 0.03, 0.12, plain=True), bar(SPOOL, (0, -0.07, -0.34), (0, -0.07, -0.5), 0.003)]
    bones, i = [], 1
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, z = 0.2 * sx, 0.2 * sz + 0.01
            parts += [bar(CARBON, (0.02 * sx, 0.007, 0.02 * sz + 0.01), (x, 0.007, z), 0.022)]                                           # arm
            parts += [lathe(STEEL_D, "y", x, z, [(0.014, 0.0), (0.014, 0.022), (0.036, 0.022), (0.04, 0.014), (0.048, 0.008), (0.048, 0.0)]),
                      lathe(RED, "y", x, z, [(0.036, 0.0225), (0.04, 0.0225)])]                                                          # motor
            bones.append(_rotor(f"r{i}", x, 0.05, z, 0.18, props))
            i += 1
    return _model(parts, bones)


# ---------------------------------------------------------------- DJI Mavic 3 carrying one grenade
def mavic():
    g, d = PLASTIC, PLASTIC_D
    parts = [skin(g, [(0.175, 0, 0.0, 0.05, 0.03), (0.16, 0, 0.0, 0.085, 0.06), (0.1, 0, 0.005, 0.11, 0.086), (-0.05, 0, 0.006, 0.108, 0.09),
                      (-0.12, 0, 0.006, 0.105, 0.088), (-0.18, 0, 0.01, 0.085, 0.07), (-0.19, 0, 0.01, 0.07, 0.055)], power=4, seg=16),
             B(d, -0.04, 0.05, -0.1, 0.08, 0.006, 0.16, plain=True),                                                                       # battery lid
             B(BLACK, -0.035, 0.012, 0.172, 0.022, 0.012, 0.004, plain=True), B(BLACK, 0.013, 0.012, 0.172, 0.022, 0.012, 0.004, plain=True),   # obstacle sensors
             B(BLACK, -0.03, -0.062, 0.12, 0.06, 0.028, 0.04, plain=True),                                                                 # gimbal
             B(ORANGE, -0.025, -0.055, -0.04, 0.05, 0.015, 0.07, plain=True)]                                                              # drop mechanism
    parts += [lathe(BLACK, "z", 0, -0.085, [(0.13, 0.0), (0.13, 0.03), (0.18, 0.032), (0.19, 0.024), (0.19, 0.0)]), lathe(GLASS, "z", 0, -0.085, [(0.19, 0.022), (0.196, 0.0)])]
    bones, i = [], 1
    for sx in (-1, 1):
        fx, fz, rx, rz = 0.2 * sx, 0.16, 0.2 * sx, -0.19
        parts += [bar(d, (0.05 * sx, 0.028, 0.1), (fx, 0.043, fz), 0.02), bar(d, (0.05 * sx, -0.002, -0.1), (rx, 0.003, rz), 0.018)]         # folding arms
        parts += [lathe(g, "y", fx, fz, [(0.035, 0.0), (0.035, 0.02), (0.058, 0.02), (0.062, 0.012), (0.062, 0.0)]),
                  lathe(g, "y", rx, rz, [(-0.005, 0.0), (-0.005, 0.02), (0.018, 0.02), (0.022, 0.012), (0.022, 0.0)]),
                  bar(d, (fx, 0.035, fz), (fx, -0.07, fz), 0.014), bar(d, (rx, -0.005, rz), (rx, -0.07, rz), 0.011)]                         # motors, landing legs
        bones.append(_rotor(f"r{i}", fx, 0.064, fz, 0.24, BLACK, 2)); i += 1
        bones.append(_rotor(f"r{i}", rx, 0.024, rz, 0.24, BLACK, 2)); i += 1
    grenade = [lathe(MUNITION, "y", 0, -0.005, [(-0.15, 0.0), (-0.15, 0.016), (-0.13, 0.028), (-0.075, 0.028), (-0.07, 0.016), (-0.07, 0.0)]),
               lathe(BRASS, "y", 0, -0.005, [(-0.075, 0.016), (-0.055, 0.016), (-0.055, 0.0)]),
               lathe(YELLOW, "y", 0, -0.005, [(-0.115, 0.0285), (-0.107, 0.0285)])]
    bones.append(_bone("grenade", (0, 0, 0), grenade))
    return _model(parts, bones)


#          model            builder                      texels per metre
MODELS = {"mq9_reaper": (reaper, 64), "bayraktar_tb2": (tb2, 64), "global_hawk": (global_hawk, 48), "orlan_10": (orlan, 160),
          "shahed_136": (shahed, 128), "fpv_drone": (lambda: fpv(False), 1024), "fiber_fpv_drone": (lambda: fpv(True), 1024),
          "mavic": (mavic, 1024)}


def export(model, width, height):
    """The plain mesh format Steel Rain's own renderer reads: per bone, a list of faces of four corners, each
    corner x, y, z, normal x, y, z, texture u, v."""
    def faces(parts):
        out = []
        for part in parts:
            for s in part["surfaces"]:
                r = s["rect"]
                R, C = s["grid"].shape[:2]
                for face in s["faces"]:
                    corners = []
                    for (a, b) in face:
                        if s["mask"]:
                            u, v = a, b
                            g = s["grid"]
                            p = g[0, 0] * (1 - u) * (1 - v) + g[0, 1] * u * (1 - v) + g[1, 0] * (1 - u) * v + g[1, 1] * u * v
                            n = s["normals"][0, 0]
                        else:
                            p, n = s["grid"][a, b], s["normals"][a, b]
                            u, v = b / (C - 1), a / (R - 1)
                        # mirrored left to right, which is how the texture painter expects to be seen
                        corners.append([round(-float(p[0]), 4), round(float(p[1]), 4), round(float(p[2]), 4),
                                        round(-float(n[0]), 3), round(float(n[1]), 3), round(float(n[2]), 3),
                                        round((r["x"] + 0.5 + u * (r["w"] - 1)) / width, 5), round((r["y"] + 0.5 + v * (r["h"] - 1)) / height, 5)])
                    if len(corners) == 3:
                        corners.append(corners[0])
                    if len(corners) == 4:
                        out.append([v for c in corners for v in c])
                    else:
                        for k in range(1, len(corners) - 1):
                            out.append([v for c in (corners[0], corners[k], corners[k + 1], corners[0]) for v in c])
        return out
    bones = [{"name": "root", "pivot": [0, 0, 0], "faces": faces(model["base"])}]
    for b in model["bones"]:
        px, py, pz = b["pivot"]
        bones.append({"name": b["name"], "pivot": [round(-px, 4), round(py, 4), round(pz, 4)], "faces": faces(b["cubes"])})
    return {"bones": bones}


if __name__ == "__main__":
    import sys, time
    from PIL import Image
    for name in sys.argv[2:] or list(MODELS):
        t0 = time.time()
        fn, px = MODELS[name]
        model = fn()
        tex, w, h = bake(model, name, px)
        views = [render(model, tex, math.radians(a), math.radians(b), 900) for a, b in ((35, 18), (215, -14), (90, 2), (0, 75))]
        sheet = Image.new("RGB", (1800, 1800))
        for i, v in enumerate(views):
            sheet.paste(v, ((i % 2) * 900, (i // 2) * 900))
        sheet.save(f"{sys.argv[1]}/{name}.png")
        parts = model["base"] + [c for b in model["bones"] for c in b["cubes"]]
        print(name, len(parts), "parts", sum(len(sf["faces"]) for p in parts for sf in p["surfaces"]), "faces", w, h, round(time.time() - t0, 1), "s")
