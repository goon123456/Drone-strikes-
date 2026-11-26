#!/usr/bin/env python3
"""Building kit for the Superb Warfare vehicle models: cubes, shared parts, and the texture baker.

Vehicle space: x across (positive = left), y up, z towards the front, origin on the ground under the middle,
in blocks (1 block = 1 metre). Models are lists of parts: boxes with bevelled edges made with B(), and turned
parts (barrels, masts, wheels, dishes) made with the rod and disc helpers. bake() turns them into a true mesh,
gives every surface its own patch of a per-vehicle texture and paints it from the surface's real position, so
camouflage flows from one part to the next, dirt gathers low down and panels get edges, seams and rivets.
"""
import math
import numpy as np
from PIL import Image

PX = 64            # texels per block

# ---------------------------------------------------------------- materials
MATS = {}


def _mat(name, colour, kind="paint", **kw):
    MATS[name] = dict(colour=colour, kind=kind, **kw)
    return name


NATO_CAMO = dict(colours=[(70, 86, 58), (88, 66, 48), (28, 28, 28)], scale=1.5, cut=(0.56, 0.63))
RUS_CAMO_SCHEME = dict(colours=[(74, 88, 60), (146, 134, 98), (38, 42, 34)], scale=1.7, cut=(0.58, 0.64))

OLIVE = _mat("olive", (92, 104, 62), armour=True)              # Soviet-era olive: ZU-23-2
OLIVE_D = _mat("olive_d", (66, 76, 46), armour=True)
RUS = _mat("rus", (78, 92, 62), armour=True)                   # Russian protective green
RUS_D = _mat("rus_d", (56, 68, 46), armour=True)
US = _mat("us", (84, 94, 70), armour=True)                     # US Army green
US_D = _mat("us_d", (60, 68, 52), armour=True)
TAN = _mat("tan", (184, 166, 128), armour=True)                # desert tan
TAN_D = _mat("tan_d", (146, 130, 98), armour=True)
NATO = _mat("nato", (70, 86, 58), armour=True, camo=NATO_CAMO)  # NATO three-tone camouflage
NATO_D = _mat("nato_d", (50, 62, 42), armour=True)
TYRE = _mat("tyre", (30, 30, 32), "rubber")
TRACK = _mat("track", (52, 48, 44), "track")
LINK = _mat("link", (60, 56, 52), "steel")
STEEL = _mat("steel", (138, 142, 148), "steel")
STEEL_D = _mat("steel_d", (62, 66, 70), "steel")
BLACK = _mat("black", (22, 22, 24), "flat")
GLASS = _mat("glass", (36, 58, 84), "glass")
WHITE = _mat("white", (222, 222, 214), "lamp")
RED = _mat("red", (190, 40, 36), "lamp")
RADOME = _mat("radome", (150, 158, 140), "flat")
GRILLE = _mat("grille", (44, 46, 48), "grille")
AMBER = _mat("amber", (224, 150, 40), "lamp")
RUS_CAMO = _mat("rus_camo", (74, 88, 60), armour=True, camo=RUS_CAMO_SCHEME)
GREY = _mat("grey", (120, 124, 126), "steel")
WOOD = _mat("wood", (124, 92, 56), "flat")
CANVAS = _mat("canvas", (86, 88, 66), "flat")
BRASS = _mat("brass", (176, 148, 70), "steel")

# aircraft finishes: smooth and clean, with fine panel lines instead of rivets and road dust
AF_GREY = _mat("af_grey", (158, 164, 170), clean=True)          # US Air Force light grey: Reaper
AF_DARK = _mat("af_dark", (112, 118, 124), clean=True)
HAWK_GREY = _mat("hawk_grey", (104, 110, 118), clean=True)      # Global Hawk fuselage
HAWK_WHITE = _mat("hawk_white", (222, 223, 218), clean=True)
TB2_GREY = _mat("tb2_grey", (184, 188, 192), clean=True)
TB2_DARK = _mat("tb2_dark", (134, 139, 144), clean=True)
ORLAN_GREY = _mat("orlan_grey", (160, 164, 160), clean=True)
SHAHED_WHITE = _mat("shahed_white", (212, 210, 198), clean=True)
SHAHED_DARK = _mat("shahed_dark", (150, 148, 138), clean=True)
CARBON = _mat("carbon", (40, 40, 44), "carbon")
PLASTIC = _mat("plastic", (104, 106, 110), clean=True)          # Mavic shell
PLASTIC_D = _mat("plastic_d", (66, 68, 72), clean=True)
MUNITION = _mat("munition", (78, 86, 66), clean=True)
ORANGE = _mat("orange", (214, 88, 44), "flat")
CYAN = _mat("cyan", (64, 200, 255), "flat")
LIPO = _mat("lipo", (44, 58, 140), "flat")
SPOOL = _mat("spool", (228, 228, 220), "flat")
COPPER = _mat("copper", (184, 112, 64), "steel")
YELLOW = _mat("yellow", (200, 168, 44), "flat")

DUST = np.array([132, 120, 94], dtype=float)


# ---------------------------------------------------------------- parts
def B(mat, x, y, z, w, h, d, pitch=0, yaw=0, roll=0, pivot=None, **opt):
    """A box from a corner and a size, optionally turned about one axis through `pivot` (default: its middle).
    Options: marks=[(face, kind, fu, fv, size)], plain=True (a small fitting: no bevel, rivets or seams)."""
    assert sum(1 for a in (pitch, yaw, roll) if a) <= 1, "turn a part about one axis only"
    p = pivot or (x + w / 2, y + h / 2, z + d / 2)
    return dict(kind="box", mat=mat, box=(x, y, z, w, h, d), rot=(pitch, yaw, roll), pivot=p, opt=opt)


def P(mat, x, y, z, w, h, d, pitch=0, yaw=0, roll=0, pivot=None, **opt):
    """The same box on both sides."""
    mp = (-pivot[0], pivot[1], pivot[2]) if pivot else None
    if "marks" in opt:   # a marking on the left face belongs on the right face of the mirrored box
        flip = {"left": "right", "right": "left"}
        opt2 = dict(opt, marks=[(flip.get(m[0], m[0]),) + tuple(m[1:]) for m in opt["marks"]])
    else:
        opt2 = opt
    return [B(mat, x, y, z, w, h, d, pitch, yaw, roll, pivot, **opt),
            B(mat, -x - w, y, z, w, h, d, pitch, -yaw, -roll, mp, **opt2)]


def slope(mat, x, w, z0, y0, z1, y1, t=0.1, **opt):
    """A flat plate of width w (from x) running from (z0, y0) to (z1, y1) seen from the side: glacis, windscreen."""
    length = math.hypot(z1 - z0, y1 - y0)
    return B(mat, x, y0 - t, z0, w, t, length, pitch=math.degrees(math.atan2(y1 - y0, z1 - z0)), pivot=(0, y0, z0), **opt)


def lathe(mat, axis, c1, c2, profile, pitch=0, yaw=0, roll=0, pivot=None, **opt):
    """A turned part: `profile` is a list of (position along the axis, radius). axis 'x': centre is (y, z);
    'y': (x, z); 'z': (x, y)."""
    return dict(kind="lathe", mat=mat, axis=axis, centre=(c1, c2), profile=list(profile), rot=(pitch, yaw, roll),
                pivot=pivot or (0, 0, 0), opt=opt)


def loft(mat, axis, a0, poly0, a1, poly1=None, **opt):
    """A solid between two flat outlines with the same number of corners, set square to `axis` at a0 and a1.
    Outline points are (y, z) for axis 'x', (x, z) for 'y' and (x, y) for 'z'. With one outline it is a plain
    extrusion. Options: marks=[("s<side number>" or "cap0"/"cap1", kind, fu, fv, size)]."""
    return dict(kind="loft", mat=mat, axis=axis, a=(a0, a1), polys=(list(poly0), list(poly1 or poly0)),
                rot=(0, 0, 0), pivot=(0, 0, 0), opt=opt)


def hull(mat, x0, x1, profile, profile2=None, **opt):
    """A side profile given as (z, y) points, carried across the vehicle from x0 to x1 (optionally changing
    to a second profile at x1): hulls, cabs, bodies."""
    f = lambda pr: [(y, z) for z, y in pr]
    return loft(mat, "x", x0, f(profile), x1, f(profile2) if profile2 else None, **opt)


def tower(mat, y0, outline, y1, outline2=None, **opt):
    """A plan outline given as (x, z) points at height y0, rising to a second outline at y1: turrets, shelters."""
    return loft(mat, "y", y0, outline, y1, outline2, **opt)


def skin(mat, sections, power=2.0, seg=24, **opt):
    """A smooth body through a row of cross-sections along the vehicle: fuselages, pods, nacelles.
    Each section is (z, centre x, centre y, width, height). `power` 2 gives ovals, higher gives squarer shapes."""
    return dict(kind="skin", mat=mat, sections=list(sections), power=power, rot=(0, 0, 0), pivot=(0, 0, 0), opt=dict(opt, seg=seg))


_FOIL = [(0.0, 0.0), (0.02, 0.45), (0.1, 0.85), (0.28, 1.0), (0.55, 0.8), (0.8, 0.42), (1.0, 0.06),
         (1.0, -0.06), (0.8, -0.2), (0.55, -0.36), (0.28, -0.46), (0.1, -0.42), (0.02, -0.26)]


def wing(mat, x0, y0, lead0, chord0, t0, x1, y1, lead1, chord1, t1, **opt):
    """An aerofoil-section panel from a root to a tip: wings, tailplanes, fins, propeller blades. Each end is
    given by where it is (x, y), where its leading edge is along z, its chord and its thickness."""
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / ln, dx / ln                                # which way "up" is for this panel
    if abs(dx) >= abs(dy):
        sec = lambda x, y, lead, chord, t: [(y + ny * t * 0.5 * v + (x - x) * 0, lead - chord * f) for f, v in _FOIL]
        return loft(mat, "x", x0, sec(x0, y0, lead0, chord0, t0), x1, sec(x1, y1, lead1, chord1, t1), plain=True, **opt)
    sec = lambda x, y, lead, chord, t: [(x + nx * t * 0.5 * v, lead - chord * f) for f, v in _FOIL]
    return loft(mat, "y", y0, sec(x0, y0, lead0, chord0, t0), y1, sec(x1, y1, lead1, chord1, t1), plain=True, **opt)


def wings(mat, x0, y0, lead0, chord0, t0, x1, y1, lead1, chord1, t1, **opt):
    """The same panel on both sides."""
    return [wing(mat, x0, y0, lead0, chord0, t0, x1, y1, lead1, chord1, t1, **opt),
            wing(mat, -x0, y0, lead0, chord0, t0, -x1, y1, lead1, chord1, t1, **opt)]


def rod_z(mat, cx, cy, z0, length, dia, **opt):
    """A round bar along the vehicle (barrels, tubes)."""
    return [lathe(mat, "z", cx, cy, [(z0, dia / 2), (z0 + length, dia / 2)], **opt)]


def rods_z(mat, cx, cy, z0, length, dia):
    return rod_z(mat, cx, cy, z0, length, dia) + rod_z(mat, -cx, cy, z0, length, dia)


def rod_y(mat, cx, cz, y0, height, dia, **opt):
    return [lathe(mat, "y", cx, cz, [(y0, dia / 2), (y0 + height, dia / 2)], **opt)]


def rod_x(mat, x0, length, cy, cz, dia, **opt):
    return [lathe(mat, "x", cy, cz, [(x0, dia / 2), (x0 + length, dia / 2)], **opt)]


def disc_x(mat, x0, width, cy, cz, dia, **opt):
    """A disc lying across the vehicle."""
    return [lathe(mat, "x", cy, cz, [(x0, dia / 2), (x0 + width, dia / 2)], **opt)]


def wheel(x0, width, cy, cz, dia, rim, tyre=0.62):
    """A road wheel turned in one piece: rounded tyre shoulders, a dished rim and a hub. The paint (rubber,
    rim, bolts, hub cap) goes on by distance from the axle."""
    R = dia / 2
    out_x, in_x = (x0 + width, x0) if x0 >= 0 else (x0, x0 + width)
    s = 1 if out_x > in_x else -1
    w = width
    profile = [(in_x, R * 0.35), (in_x, R * 0.8), (in_x + s * w * 0.1, R * 0.95), (in_x + s * w * 0.22, R),
               (out_x - s * w * 0.22, R), (out_x - s * w * 0.1, R * 0.95), (out_x, R * 0.8), (out_x, R * tyre),
               (out_x - s * w * 0.3, R * (tyre - 0.06)), (out_x - s * w * 0.3, R * 0.26), (out_x - s * w * 0.05, R * 0.2),
               (out_x + s * 0.02, R * 0.12), (out_x + s * 0.02, 0.0)]
    return [lathe(TYRE, "x", cy, cz, profile, wheel=(cy, cz, R, rim, tyre), seg=24)]


def wheel_bone(name, x0, width, cy, cz, dia, rim=STEEL_D, parent="base", tyre=0.62):
    return {"name": name, "parent": parent, "pivot": (x0 + width / 2, cy, cz), "cubes": wheel(x0, width, cy, cz, dia, rim, tyre)}


def truck_wheels(zs, x, width, dia, rim=STEEL_D, steer=1):
    """Wheel bones for both sides at each axle position; x is the inner face of the tyre. The front `steer`
    axles are named so that Superb Warfare turns them with the steering as well as spinning them."""
    bones = []
    steering = sorted(zs, reverse=True)[:steer]
    for i, z in enumerate(zs, start=1):
        suffix = "Turn" if z in steering else ""
        bones.append(wheel_bone(f"wheelL{i}{suffix}", x, width, dia / 2, z, dia, rim))
        bones.append(wheel_bone(f"wheelR{i}{suffix}", -x - width, width, dia / 2, z, dia, rim))
    return bones


def axles(zs, x, dia, mat=STEEL_D):
    """Axle beams, differentials and leaf springs under a truck."""
    c = []
    for z in zs:
        c += rod_x(mat, -x, 2 * x, dia / 2, z, 0.16)
        c += [B(mat, -0.22, dia / 2 - 0.2, z - 0.2, 0.44, 0.4, 0.4, plain=True)]
        c += P(mat, x - 0.32, dia / 2 + 0.1, z - 0.55, 0.08, 0.08, 1.1, plain=True)
    return c


def belt(circles, samples=400):
    """The path of a track wrapped anticlockwise (seen from the left) round a list of (z, y, radius) wheels,
    starting where it leaves the first one. Returns evenly spaced (z, y, turn in degrees) and its length."""
    n = len(circles)
    angles = []
    for i in range(n):                                       # the angle at which the track leaves each wheel for the next
        (z1, y1, r1), (z2, y2, r2) = circles[i], circles[(i + 1) % n]
        dz, dy = z2 - z1, y2 - y1
        dist = math.hypot(dz, dy); alpha = math.atan2(dy, dz); off = math.acos((r1 - r2) / dist)
        th = alpha - off
        if -math.sin(th) * dz + math.cos(th) * dy < 0:
            th = alpha + off
        angles.append(th)
    pts = []
    turn = angles[0]
    for i in range(n):
        j = (i + 1) % n
        zc, yc, r = circles[j]
        arrive, leave = angles[i], angles[j]
        while leave < arrive - 1e-9:
            leave += 2 * math.pi
        z1, y1, r1 = circles[i]
        pts.append((z1 + r1 * math.cos(arrive), y1 + r1 * math.sin(arrive), arrive))      # straight run to the next wheel
        steps = max(2, int((leave - arrive) / math.radians(4)))
        for k in range(steps + 1):                                                        # then round it
            th = arrive + (leave - arrive) * k / steps
            pts.append((zc + r * math.cos(th), yc + r * math.sin(th), th))
    pts.append(pts[0][:2] + (pts[0][2] + 2 * math.pi,))
    # unwrap the turning angle so it only ever grows, then resample at even spacing
    out, acc = [pts[0]], pts[0][2]
    for p in pts[1:]:
        d = (p[2] - acc + math.pi) % (2 * math.pi) - math.pi
        acc += max(0.0, d) if abs(d) > 1e-9 else 0.0
        out.append((p[0], p[1], acc))
    dists = [0.0]
    for a, b2 in zip(out, out[1:]):
        dists.append(dists[-1] + math.hypot(b2[0] - a[0], b2[1] - a[1]))
    total = dists[-1]
    res, k = [], 0
    for i in range(samples + 1):
        d = total * i / samples
        while k < len(dists) - 2 and dists[k + 1] < d:
            k += 1
        f = (d - dists[k]) / max(1e-9, dists[k + 1] - dists[k])
        a, b2 = out[k], out[k + 1]
        res.append((a[0] + (b2[0] - a[0]) * f, a[1] + (b2[1] - a[1]) * f, math.degrees(a[2] + (b2[2] - a[2]) * f - out[0][2])))
    return res, total


def running_gear(x, width, z0, z1, count, dia, body, rollers=3):
    """Running gear for both sides: road wheels, sprocket, idler and return rollers that all turn, and a track
    made of separate links that travel round them. Returns (fixed parts, bones, track path for the renderer)."""
    cubes, bones = [], []
    top = dia + 0.26
    half = 0.045                                               # half the thickness of a link
    cy, ys = dia / 2 + 2 * half, top - 0.42
    front, rear = z1 - 0.32, z0 + 0.32
    path, length = belt([(front, ys, 0.33 + half), (rear, ys, 0.33 + half), (z0 + 0.8, cy, dia / 2 + half), (z1 - 0.8, cy, dia / 2 + half)])
    links = int(round(length / 0.15))
    pitch = length / links
    pz, py = path[0][0], path[0][1]                            # every link is modelled here and moved along the path
    span = (z1 - z0 - 1.6) / (count - 1)
    for side, sx in (("L", x), ("R", -x - width)):
        outer = sx + width if sx >= 0 else sx
        inner = sx if sx >= 0 else sx + width
        s = 1 if sx >= 0 else -1
        n = 0
        def spinning(parts, yc, zc):
            nonlocal n
            n += 1
            bones.append({"name": f"wheel{side}{n}", "parent": "base", "pivot": (sx + width / 2, yc, zc), "cubes": parts})
        for i in range(count):                                 # road wheels on swing arms
            z = z0 + 0.8 + span * i
            spinning(wheel(sx + 0.04, width - 0.08, cy, z, dia, body, 0.84), cy, z)
            cubes.append(B(STEEL_D, inner - (0.07 if s > 0 else 0), cy - 0.05, z - 0.42, 0.07, 0.1, 0.45, plain=True))
            cubes += rod_x(STEEL_D, inner - (0.2 if s > 0 else 0), 0.2, cy, z, 0.12)
        for zc in (front, rear):                               # toothed sprocket at the front, idler at the back
            parts = disc_x(STEEL_D, sx + 0.08, width - 0.16, ys, zc, 0.5, wheel=(ys, zc, 0.25, body, 0.9))
            for xr in (sx + 0.03, sx + width - 0.08):
                parts += disc_x(STEEL_D, xr, 0.05, ys, zc, 0.66, wheel=(ys, zc, 0.33, body, 0.9))
                if zc == front:
                    parts += [B(STEEL, xr, ys - 0.03, zc - 0.36, 0.05, 0.06, 0.72, pitch=a, pivot=(0, ys, zc), plain=True) for a in range(0, 180, 20)]
            parts += disc_x(body, outer - (0.0 if s > 0 else 0.05), 0.05, ys, zc, 0.26)
            spinning(parts, ys, zc)
        for i in range(rollers):                               # return rollers under the top run
            zc = z0 + 1.2 + i * (z1 - z0 - 2.4) / max(1, rollers - 1)
            spinning(disc_x(STEEL_D, sx + 0.1, width - 0.2, top - 2 * half - 0.1, zc, 0.2), top - 2 * half - 0.1, zc)
            cubes.append(B(STEEL_D, inner - (0.1 if s > 0 else 0), top - 2 * half - 0.14, zc - 0.04, 0.1, 0.08, 0.08, plain=True))
        # one link, shared by every link bone on this side: shoe, rubber pad, end connectors, guide horn
        link = [B(LINK, sx, py - half, pz - pitch * 0.47, width, 2 * half * 0.7, pitch * 0.94, plain=True),
                B(BLACK, sx + width * 0.14, py + half * 0.4, pz - pitch * 0.33, width * 0.72, half * 0.6, pitch * 0.66, plain=True),
                B(STEEL_D, sx - 0.012, py - half * 0.9, pz - pitch * 0.5, 0.05, half * 1.5, pitch * 0.45, plain=True),
                B(STEEL_D, sx + width - 0.038, py - half * 0.9, pz - pitch * 0.5, 0.05, half * 1.5, pitch * 0.45, plain=True),
                B(STEEL_D, sx + width * 0.5 - 0.025, py - half - 0.06, pz - 0.03, 0.05, 0.06, 0.06, plain=True)]
        link += rod_x(STEEL, sx - 0.02, width + 0.04, py - half * 0.3, pz + pitch * 0.47, 0.022)        # link pin
        for i in range(links):
            bones.append({"name": f"trackMov{side}{i}", "parent": "base", "pivot": (sx + width / 2, py, pz), "cubes": []})
            bones.append({"name": f"trackRot{side}{i}", "parent": f"trackMov{side}{i}", "pivot": (sx + width / 2, py, pz), "cubes": link})
    track = {"links": links, "length": round(length, 4),
             "y": [round((p[1] - py) * 16, 3) for p in path], "z": [round(-(p[0] - pz) * 16, 3) for p in path],
             "rot": [round(-p[2], 3) for p in path]}
    return cubes, bones, track


# ---------------------------------------------------------------- shared truck parts
def cab(mat, dark, z0, length, width, floor, height, bonnet=0.0):
    """A cab-over truck cab starting at z0, built from its side profile: raked windscreen, rounded roof edge,
    undercut nose; then doors, bumper, lights, mirrors, steps, wipers."""
    hw = width / 2
    front = z0 + length
    h = height
    profile = [(z0, floor), (z0, floor + h * 0.96), (z0 + 0.12, floor + h), (front - 0.42, floor + h), (front - 0.3, floor + h * 0.97),
               (front - 0.02, floor + h * 0.52), (front, floor + h * 0.46), (front, floor + 0.08), (front - 0.08, floor)]
    c = [hull(mat, -hw, hw, profile, marks=[("cap0", "panel", 0.55, 0.6, length * 0.5, h * 0.62), ("cap1", "panel", 0.55, 0.6, length * 0.5, h * 0.62)]),
         B(dark, -hw + 0.3, floor + h, z0 + 0.3, 0.6, 0.05, 0.6, plain=True),                  # roof hatch
         B(dark, -hw + 0.02, floor + h, z0 + 0.05, width - 0.04, 0.03, 0.06, plain=True),      # rain gutter
         slope(GLASS, -hw + 0.12, width * 0.5 - 0.17, front + 0.012, floor + h * 0.56, front - 0.262, floor + h * 0.94, 0.03),
         slope(GLASS, 0.05, width * 0.5 - 0.17, front + 0.012, floor + h * 0.56, front - 0.262, floor + h * 0.94, 0.03),
         B(dark, -hw - 0.03, floor - 0.25, front - 0.12, width + 0.06, 0.32, 0.22),             # bumper
         B(STEEL_D, -0.3, floor - 0.2, front + 0.1, 0.6, 0.12, 0.05, plain=True),              # tow bar
         B(GRILLE, -hw * 0.55, floor + 0.12, front - 0.01, width * 0.55, h * 0.3, 0.04),
         B(dark, -hw * 0.6, floor + 0.08, front, width * 0.6, 0.04, 0.05, plain=True),
         B(WHITE, -0.25, floor - 0.08, front + 0.1, 0.5, 0.12, 0.02, marks=[("front", "plate", 0.5, 0.5, 0.5, 0.12)]),
         B(dark, -hw * 0.9, floor - 0.1, z0 - 0.02, width * 0.9, h * 0.7, 0.05, plain=True)]   # back wall stiffener
    for sx in (1, -1):                                                                           # snorkel, grab rails, sun visor
        c += [B(dark, sx * hw - (0.0 if sx > 0 else 0.03), floor + h * 0.55, z0 + length * 0.22, 0.03, h * 0.4, 0.04, plain=True)]
    c += [B(dark, -hw + 0.05, floor + h * 0.97, front - 0.36, width - 0.1, 0.03, 0.2, pitch=-12, plain=True)]
    c += P(GLASS, hw - 0.02, floor + height * 0.55, z0 + length * 0.3, 0.05, height * 0.36, length * 0.45)
    c += P(dark, hw, floor + height * 0.5, z0 + length * 0.28, 0.03, height * 0.04, length * 0.5, plain=True)   # window sill
    c += P(STEEL_D, hw, floor + height * 0.3, z0 + length * 0.36, 0.04, 0.05, 0.16, plain=True)                # door handles
    c += P(WHITE, hw - 0.4, floor - 0.16, front + 0.09, 0.22, 0.14, 0.03)                       # headlights
    c += P(AMBER, hw - 0.14, floor - 0.14, front + 0.09, 0.1, 0.1, 0.03)
    c += P(dark, hw - 0.44, floor - 0.2, front + 0.11, 0.3, 0.03, 0.02, plain=True)             # light guards
    c += P(STEEL_D, hw + 0.02, floor + height * 0.6, front - 0.5, 0.2, 0.03, 0.03, plain=True)   # mirror arms
    c += P(BLACK, hw + 0.2, floor + height * 0.5, front - 0.53, 0.05, 0.4, 0.2)                 # mirrors
    c += P(dark, hw - 0.02, floor - 0.45, z0 + length * 0.35, 0.08, 0.05, 0.5, plain=True)      # steps
    c += P(dark, hw - 0.02, floor - 0.22, z0 + length * 0.35, 0.08, 0.05, 0.5, plain=True)
    c += P(BLACK, 0.2, floor + height * 0.56, front + 0.03, 0.5, 0.02, 0.02, plain=True)        # wipers
    c += P(AMBER, hw - 0.25, floor + height + 0.08, front - 0.6, 0.12, 0.06, 0.08)              # roof marker lights
    c += [B(STEEL_D, hw - 0.15, floor + height, z0 + 0.15, 0.04, 1.3, 0.04, plain=True)]        # aerial
    return c


def truck_details(mat, dark, zs, x, dia, z_rear, z_front, bed):
    """The clutter under and around a heavy truck: frame cross-members, drive shafts, fuel and air tanks,
    battery box, exhaust, ladders, rear light clusters, reflectors and a tow hitch."""
    c = []
    zs = sorted(zs)
    for z in np.arange(z_rear + 0.4, z_front - 0.2, 1.1):                                        # frame cross-members
        c.append(B(dark, -0.55, bed - 0.42, float(z), 1.1, 0.1, 0.1, plain=True))
    c += rod_z(STEEL_D, 0, dia / 2 + 0.05, zs[0], zs[-1] - zs[0], 0.11)                          # drive shaft
    gap = [(a + b) / 2 for a, b in zip(zs, zs[1:]) if b - a > dia * 1.6]
    mid = gap[0] if gap else (zs[0] + zs[-1]) / 2
    for sx in (1, -1):
        cx = sx * (x - 0.05)
        c += [lathe(STEEL_D, "z", cx, bed - 0.62, [(mid - 0.75, 0.0), (mid - 0.75, 0.24), (mid - 0.7, 0.27), (mid + 0.1, 0.27), (mid + 0.15, 0.24), (mid + 0.15, 0.0)])]
        c += [B(BLACK, cx - 0.28, bed - 0.9, mid - 0.55, 0.56, 0.03, 0.05, plain=True), B(BLACK, cx - 0.28, bed - 0.9, mid - 0.1, 0.56, 0.03, 0.05, plain=True)]
        c += rod_z(STEEL, cx, bed - 0.55, mid + 0.3, 0.5, 0.2)                                    # air tanks
        c += [B(dark, sx * x - 0.25 + (0.0 if sx > 0 else 0.0), bed - 0.85, mid + 0.9, 0.5, 0.45, 0.4, marks=[("left" if sx > 0 else "right", "panel", 0.5, 0.5, 0.3, 0.3)])]
        c += [B(RED, sx * (x + 0.1) - 0.11, bed - 0.3, z_rear - 0.04, 0.22, 0.12, 0.04), B(AMBER, sx * (x - 0.16) - 0.07, bed - 0.3, z_rear - 0.04, 0.14, 0.12, 0.04),
              B(WHITE, sx * (x - 0.36) - 0.06, bed - 0.3, z_rear - 0.04, 0.12, 0.12, 0.04),
              B(AMBER, sx * (x + 0.36) - 0.02, bed - 0.12, mid, 0.04, 0.06, 0.1, plain=True)]    # side marker
    c += [B(dark, -x - 0.3, bed - 0.42, z_rear - 0.06, 2 * x + 0.6, 0.22, 0.1, marks=[("rear", "hazard", 0.5, 0.5, 1.6, 0.12)]),   # under-run bar
          B(STEEL_D, -0.1, bed - 0.55, z_rear - 0.2, 0.2, 0.16, 0.2, plain=True)]
    c += rod_x(STEEL_D, -0.16, 0.32, bed - 0.47, z_rear - 0.22, 0.06)                             # tow pin
    c += rod_y(STEEL_D, x + 0.1, mid + 1.5, bed - 0.2, 1.5, 0.14) + rod_z(STEEL_D, x + 0.1, bed - 0.35, mid + 1.1, 0.6, 0.26)   # exhaust stack and silencer
    for i in range(4):                                                                            # ladder
        c.append(B(STEEL_D, -x - 0.42, bed - 0.8 + i * 0.22, mid - 1.2, 0.04, 0.03, 0.4, plain=True))
    c += [B(STEEL_D, -x - 0.42, bed - 0.85, mid - 1.22, 0.04, 0.9, 0.03, plain=True), B(STEEL_D, -x - 0.42, bed - 0.85, mid - 0.83, 0.04, 0.9, 0.03, plain=True)]
    return c


# ---------------------------------------------------------------- small fittings
def bar(mat, p0, p1, dia=0.03, **opt):
    """A straight bar between any two points, at any angle: struts, stays, hoses, cable runs, rails."""
    d = [p1[i] - p0[i] for i in range(3)]
    k = max(range(3), key=lambda i: abs(d[i]))               # run it along whichever way it mostly goes
    axis = "xyz"[k]
    others = [i for i in range(3) if i != k]
    r = dia / 2
    ring = lambda p: [(p[others[0]] + r * math.cos(t), p[others[1]] + r * math.sin(t)) for t in (0.52, 1.57, 2.62, 3.67, 4.71, 5.76)]
    return loft(mat, axis, p0[k], ring(p0), p1[k], ring(p1), plain=True, **opt)


def run(mat, points, dia=0.03):
    """A hose or cable through a list of points."""
    return [bar(mat, a, b, dia) for a, b in zip(points, points[1:])]


def eye(x, y, z, mat=STEEL_D, along="z"):
    """A lifting or lashing eye standing on a surface."""
    if along == "z":
        return [B(mat, x - 0.012, y, z - 0.06, 0.024, 0.09, 0.024, plain=True), B(mat, x - 0.012, y, z + 0.036, 0.024, 0.09, 0.024, plain=True),
                B(mat, x - 0.012, y + 0.07, z - 0.06, 0.024, 0.024, 0.12, plain=True)]
    return [B(mat, x - 0.06, y, z - 0.012, 0.024, 0.09, 0.024, plain=True), B(mat, x + 0.036, y, z - 0.012, 0.024, 0.09, 0.024, plain=True),
            B(mat, x - 0.06, y + 0.07, z - 0.012, 0.12, 0.024, 0.024, plain=True)]


def handrail(mat, p0, p1, height=0.0, posts=3, dia=0.028):
    """A rail between two points standing `height` off the surface on posts (or a plain grab rail when 0.1 or so)."""
    c = [bar(mat, (p0[0], p0[1] + height, p0[2]), (p1[0], p1[1] + height, p1[2]), dia)]
    for i in range(posts):
        f = i / max(1, posts - 1)
        q = [p0[k] + (p1[k] - p0[k]) * f for k in range(3)]
        c.append(bar(mat, tuple(q), (q[0], q[1] + height, q[2]), dia))
    return c


def ladder(mat, x, z, y0, y1, width=0.4, across="z", rungs=None):
    """A ladder up a wall; `across` is the direction the rungs run."""
    n = rungs or max(2, int((y1 - y0) / 0.28))
    a, b = ((x, z), (x, z + width)) if across == "z" else ((x, z), (x + width, z))
    c = [bar(mat, (a[0], y0, a[1]), (a[0], y1, a[1]), 0.035), bar(mat, (b[0], y0, b[1]), (b[0], y1, b[1]), 0.035)]
    for i in range(n):
        y = y0 + (y1 - y0) * (i + 0.5) / n
        c.append(bar(mat, (a[0], y, a[1]), (b[0], y, b[1]), 0.028))
    return c


def jerrycan(x, y, z, mat=US_D, across="x"):
    """A 20-litre can: pressed sides, three-bar handle and a cap."""
    prof = [(0.0, 0.0), (0.0, 0.4), (0.03, 0.46), (0.3, 0.46), (0.34, 0.4), (0.34, 0.0)]
    if across == "x":
        c = [hull(mat, x, x + 0.16, [(z + a, y + b) for a, b in prof], marks=[("cap0", "panel", 0.5, 0.45, 0.2, 0.26), ("cap1", "panel", 0.5, 0.45, 0.2, 0.26)])]
        c += [B(STEEL_D, x + 0.02 + i * 0.045, y + 0.46, z + 0.08, 0.02, 0.03, 0.14, plain=True) for i in range(3)]
        c += rod_y(BLACK, x + 0.08, z + 0.29, y + 0.43, 0.06, 0.05)
    else:
        c = [loft(mat, "z", z, [(x + a, y + b) for a, b in prof], z + 0.16, None, marks=[("cap0", "panel", 0.5, 0.45, 0.2, 0.26), ("cap1", "panel", 0.5, 0.45, 0.2, 0.26)])]
        c += [B(STEEL_D, x + 0.08, y + 0.46, z + 0.02 + i * 0.045, 0.14, 0.03, 0.02, plain=True) for i in range(3)]
        c += rod_y(BLACK, x + 0.29, z + 0.08, y + 0.43, 0.06, 0.05)
    return c


def extinguisher(x, y, z):
    """A red fire extinguisher standing in its bracket."""
    c = [lathe(RED, "y", x, z, [(y, 0.0), (y, 0.06), (y + 0.34, 0.06), (y + 0.4, 0.035), (y + 0.44, 0.02), (y + 0.44, 0.0)])]
    c += [B(BLACK, x - 0.02, y + 0.44, z - 0.02, 0.09, 0.03, 0.04, plain=True), B(STEEL_D, x - 0.07, y + 0.15, z - 0.07, 0.14, 0.025, 0.14, plain=True)]
    return c


def tools(y, z0, x, mat=STEEL_D):
    """Pioneer tools clipped to a deck at height y, lying along the vehicle from z0: shovel, axe and pry bar."""
    c = rod_z(WOOD, x, y + 0.03, z0, 0.9, 0.04) + [hull(mat, x - 0.1, x + 0.1, [(z0 + 0.9, y + 0.01), (z0 + 0.9, y + 0.05), (z0 + 1.15, y + 0.03), (z0 + 1.2, y + 0.01)], plain=True)]
    c += rod_z(WOOD, x + 0.16, y + 0.03, z0 + 0.1, 0.75, 0.04) + [B(mat, x + 0.1, y + 0.01, z0 + 0.8, 0.2, 0.05, 0.1, plain=True)]
    c += rod_z(mat, x - 0.16, y + 0.025, z0 + 0.05, 1.1, 0.03)
    for z in (z0 + 0.25, z0 + 0.7):
        c.append(B(BLACK, x - 0.2, y, z, 0.42, 0.07, 0.03, plain=True))
    return c


def whip(x, z, y, height, mat=BLACK):
    """A radio aerial on a sprung base. It gets its own bone so it can whip about as the vehicle moves."""
    return [lathe(mat, "y", x, z, [(y, 0.0), (y, 0.05), (y + 0.05, 0.05), (y + 0.06, 0.03), (y + 0.16, 0.03), (y + 0.18, 0.014),
                                   (y + height * 0.7, 0.009), (y + height, 0.005), (y + height, 0.0)], bone=("sway", (x, y + 0.06, z)))]


def jack(x, z, top, mat=STEEL_D):
    """A screw jack: dished foot, threaded leg, collar and a cranked handle. On vehicles that drive, the jacks
    lift clear of the ground when it moves off and wind back down when it parks."""
    tag = dict(bone=("jack", (x, top, z)))
    c = [lathe(mat, "y", x, z, [(0.0, 0.0), (0.0, 0.2), (0.03, 0.2), (0.07, 0.07), (0.1, 0.05), (0.1, 0.0)], **tag)]
    c += rod_y(STEEL, x, z, 0.08, top - 0.08, 0.06, **tag) + rod_x(STEEL, x - 0.14, 0.28, top - 0.03, z, 0.025, **tag)
    c += rod_y(mat, x, z, top - 0.14, 0.14, 0.12)                                  # the collar stays on the vehicle
    return c


def round_hatch(x, z, y, dia, mat, handle=True):
    """A circular hatch on a deck: raised coaming, and a domed lid on a hinge at the back that swings open when
    there is a crew aboard and the vehicle is standing."""
    r = dia / 2
    tag = dict(bone=("hatch", (x, y + 0.05, z - r)))
    c = [lathe(mat, "y", x, z, [(y, r), (y + 0.05, r), (y + 0.05, r * 0.9), (y, r * 0.9)]),
         lathe(BLACK, "y", x, z, [(y + 0.012, r * 0.9), (y + 0.012, 0.0)], plain=True)]          # the dark opening
    c += [lathe(mat, "y", x, z, [(y + 0.05, 0.0), (y + 0.05, r * 0.96), (y + 0.09, r * 0.88), (y + 0.11, r * 0.5), (y + 0.11, 0.0)], **tag)]
    c += [B(STEEL_D, x - 0.08, y + 0.04, z - r - 0.05, 0.16, 0.06, 0.08, plain=True)]
    if handle:
        c += [B(STEEL_D, x - 0.07, y + 0.11, z + r * 0.4, 0.14, 0.03, 0.025, plain=True, **tag)]
    return c


def periscope(x, y, z, facing="z"):
    """A vision block: armoured hood with a strip of glass."""
    if facing == "z":
        return [hull(BLACK, x - 0.1, x + 0.1, [(z, y), (z, y + 0.1), (z + 0.08, y + 0.12), (z + 0.14, y + 0.1), (z + 0.14, y)], plain=True),
                B(GLASS, x - 0.08, y + 0.03, z + 0.14, 0.16, 0.06, 0.008)]
    s = 1 if facing == "x" else -1
    return [B(BLACK, x - (0.0 if s > 0 else 0.14), y, z - 0.1, 0.14, 0.11, 0.2, plain=True), B(GLASS, x + (0.14 if s > 0 else -0.148), y + 0.03, z - 0.08, 0.008, 0.06, 0.16)]


def lamp(colour, x, y, z, dia=0.16, facing=1):
    """A round lamp in a bezel, pointing forwards (1) or backwards (-1)."""
    r = dia / 2
    z1 = z + facing * 0.08
    return [lathe(STEEL_D, "z", x, y, [(z, 0.0), (z, r), (z1, r * 1.08), (z1, r * 0.86)]),
            lathe(colour, "z", x, y, [(z1 - facing * 0.01, r * 0.86), (z1 + facing * 0.012, r * 0.5), (z1 + facing * 0.016, 0.0)])]


def beacon(x, y, z):
    """An amber warning beacon."""
    return [lathe(STEEL_D, "y", x, z, [(y, 0.0), (y, 0.07), (y + 0.03, 0.07), (y + 0.03, 0.0)]),
            lathe(AMBER, "y", x, z, [(y + 0.03, 0.06), (y + 0.12, 0.06), (y + 0.15, 0.03), (y + 0.15, 0.0)])]


def bedroll(x0, x1, y, z, dia=0.28, mat=CANVAS):
    """A rolled tarpaulin or camouflage net, strapped down."""
    c = [lathe(mat, "x", y, z, [(x0, 0.0), (x0, dia * 0.42), (x0 + 0.04, dia / 2), (x1 - 0.04, dia / 2), (x1, dia * 0.42), (x1, 0.0)])]
    for f in (0.25, 0.75):
        xs = x0 + (x1 - x0) * f
        c += [lathe(BLACK, "x", y, z, [(xs - 0.02, dia / 2 + 0.006), (xs + 0.02, dia / 2 + 0.006)])]
    return c


def spare_wheel(x, y, z, dia, rim, lying=False):
    """A spare road wheel, upright against a wall or lying flat."""
    w = wheel(x, dia * 0.36, y + dia / 2, z, dia, rim)[0]
    if lying:
        w["rot"], w["pivot"] = (0, 0, 90), (x, y, z)
    return [w]


def shackle(x, y, z, mat=STEEL_D):
    """A towing shackle on its lug."""
    return [B(mat, x - 0.05, y - 0.06, z, 0.1, 0.12, 0.1, plain=True)] + rod_x(STEEL, x - 0.08, 0.16, y, z + 0.1, 0.04) + [
        B(STEEL, x - 0.08, y - 0.02, z + 0.08, 0.03, 0.04, 0.12, plain=True), B(STEEL, x + 0.05, y - 0.02, z + 0.08, 0.03, 0.04, 0.12, plain=True)]


def chock(x, z, mat=BLACK):
    """A wheel chock on the ground."""
    # chocks are only out while the vehicle is parked
    return [hull(mat, x - 0.12, x + 0.12, [(z, 0.0), (z + 0.05, 0.18), (z + 0.12, 0.18), (z + 0.3, 0.0)], plain=True, bone=("chock", (x, 0.0, z)))]


def mudguards(mat, x, width, zs, dia):
    c = []
    for z in zs:
        c += P(mat, x - 0.03, dia + 0.08, z - dia * 0.62, width + 0.06, 0.06, dia * 1.24, plain=True)
        c += P(BLACK, x, dia * 0.45, z - dia * 0.66, width, dia * 0.6, 0.03, plain=True)        # mud flap
    return c


# ---------------------------------------------------------------- noise
def _hash(ix, iy, iz, seed):
    n = (ix.astype(np.int64) * 374761393 + iy.astype(np.int64) * 668265263 + iz.astype(np.int64) * 1274126177 + seed * 144665) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1103515245) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(X, Y, Z, scale, seed):
    """Smooth value noise in 0..1 over vehicle space."""
    x, y, z = X / scale + 100, Y / scale + 100, Z / scale + 100
    ix, iy, iz = np.floor(x), np.floor(y), np.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                wgt = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                out = out + wgt * _hash(ix + dx, iy + dy, iz + dz, seed)
    return out


def grain(X, Y, Z, seed):
    """Per-texel random value in 0..1, the same for a point wherever it is painted from."""
    return _hash(np.floor(X * PX + 4000), np.floor(Y * PX + 4000), np.floor(Z * PX + 4000), seed)


# ---------------------------------------------------------------- parts into surfaces
# A surface is one patch of texture: a grid of points (rows x cols x 3) with a normal at each, the faces to draw
# (as lists of (row, col) corners), and its size in metres.
def _rotate(part, pts):
    pitch, yaw, roll = part["rot"]
    if not (pitch or yaw or roll):
        return pts
    p = np.array(part["pivot"], dtype=float)
    q = pts - p
    a = math.radians(pitch or yaw or roll); ca, sa = math.cos(a), math.sin(a)
    if pitch: q = np.stack([q[..., 0], q[..., 1] * ca + q[..., 2] * sa, q[..., 2] * ca - q[..., 1] * sa], -1)
    elif yaw: q = np.stack([q[..., 0] * ca + q[..., 2] * sa, q[..., 1], q[..., 2] * ca - q[..., 0] * sa], -1)
    else: q = np.stack([q[..., 0] * ca - q[..., 1] * sa, q[..., 1] * ca + q[..., 0] * sa, q[..., 2]], -1)
    return q + p


def _rotate_dir(part, n):
    zero = dict(part, pivot=(0, 0, 0))
    return _rotate(zero, n)


def _surface(part, grid, normals, w, h, tag, faces=None, wrap=False, mask=None):
    grid = np.asarray(grid, dtype=float)
    normals = np.asarray(normals, dtype=float)
    if normals.ndim == 1:
        normals = np.broadcast_to(normals, grid.shape).copy()
    R, C = grid.shape[:2]
    if faces is None:
        faces = [[(r, c), (r, c + 1), (r + 1, c + 1), (r + 1, c)] for r in range(R - 1) for c in range(C - 1)]
    return dict(part=part, grid=_rotate(part, grid), normals=_rotate_dir(part, normals), w=w, h=h, tag=tag,
                faces=faces, wrap=wrap, mask=mask)


FACES = ("north", "south", "east", "west", "up", "down")
ALIAS = {"front": "north", "rear": "south", "left": "east", "right": "west", "top": "up", "bottom": "down"}
_NORMAL = {"north": (0, 0, 1), "south": (0, 0, -1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def _face_corners_raw(box, face, b):
    """Corners of one face of a box as a 2x2 grid (row 0 = top of the texture), pulled in by the bevel b."""
    x, y, z, w, h, d = box
    x0, x1, y0, y1, z0, z1 = x + b, x + w - b, y + b, y + h - b, z + b, z + d - b
    if face == "north": return [[(x1, y1, z + d), (x0, y1, z + d)], [(x1, y0, z + d), (x0, y0, z + d)]], (w - 2 * b, h - 2 * b)
    if face == "south": return [[(x0, y1, z), (x1, y1, z)], [(x0, y0, z), (x1, y0, z)]], (w - 2 * b, h - 2 * b)
    if face == "east":  return [[(x + w, y1, z0), (x + w, y1, z1)], [(x + w, y0, z0), (x + w, y0, z1)]], (d - 2 * b, h - 2 * b)
    if face == "west":  return [[(x, y1, z1), (x, y1, z0)], [(x, y0, z1), (x, y0, z0)]], (d - 2 * b, h - 2 * b)
    if face == "up":    return [[(x0, y + h, z1), (x1, y + h, z1)], [(x0, y + h, z0), (x1, y + h, z0)]], (w - 2 * b, d - 2 * b)
    return [[(x0, y, z0), (x1, y, z0)], [(x0, y, z1), (x1, y, z1)]], (w - 2 * b, d - 2 * b)


def _face_corners(box, face, b):
    """As above, laid out so that the texture reads the right way round when the face is seen from outside."""
    grid, size = _face_corners_raw(box, face, b)
    return [row[::-1] for row in grid], size


def _box_surfaces(part):
    x, y, z, w, h, d = part["box"]
    small = min(w, h, d)
    b = 0.0 if (part["opt"].get("plain") or small < 0.12) else min(0.025, small * 0.2)
    out = []
    for face in FACES:
        grid, (fw, fh) = _face_corners(part["box"], face, b)
        out.append(_surface(part, grid, _NORMAL[face], fw, fh, face))
    if b:
        lo, hi = np.array([x, y, z]), np.array([x + w, y + h, z + d])

        def vert(signs, axis):
            # the corner's point on the face square to `axis`: on the surface along it, pulled in along the others
            v = np.where(np.array(signs) > 0, hi - b, lo + b)
            v[axis] = hi[axis] if signs[axis] > 0 else lo[axis]
            return v
        for e in range(3):                                   # the twelve bevelled edges
            a1, a2 = [k for k in range(3) if k != e]
            length = (w, h, d)[e] - 2 * b
            for s1 in (-1, 1):
                for s2 in (-1, 1):
                    def sg(se):
                        s = [0, 0, 0]; s[a1], s[a2], s[e] = s1, s2, se
                        return s
                    grid = [[vert(sg(-1), a1), vert(sg(1), a1)], [vert(sg(-1), a2), vert(sg(1), a2)]]
                    n = np.zeros(3); n[a1], n[a2] = s1, s2
                    out.append(_surface(part, grid, n / np.linalg.norm(n), length, b * 1.414, "bevel"))
        corners = []
        for sx in (-1, 1):                                   # and the eight corners, sharing one small patch
            for sy in (-1, 1):
                for sz in (-1, 1):
                    s = (sx, sy, sz)
                    corners.append([vert(s, 0), vert(s, 1), vert(s, 2)])
        grid = np.array(corners)                              # 8 rows of 3 points
        normals = np.array([[np.array(s) / 1.732] * 3 for s in [(sx, sy, sz) for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]])
        out.append(_surface(part, grid, normals, 0.03, 0.03, "bevel", faces=[[(r, 0), (r, 1), (r, 2)] for r in range(8)]))
    return out


def _axis_point(axis, a, u, v):
    return {"x": (a, u, v), "y": (u, a, v), "z": (u, v, a)}[axis]


def _lathe_surfaces(part):
    axis, (c1, c2), prof = part["axis"], part["centre"], part["profile"]
    rmax = max(r for _, r in prof)
    seg = part["opt"].get("seg") or (8 if rmax < 0.04 else 12 if rmax < 0.12 else 18 if rmax < 0.4 else 28)
    ang = np.linspace(0, 2 * math.pi, seg + 1)
    rows, norms = [], []
    for i, (a, r) in enumerate(prof):
        # surface normal from the slope of the profile either side of this point
        a0, r0 = prof[max(0, i - 1)]; a1, r1 = prof[min(len(prof) - 1, i + 1)]
        da, dr = a1 - a0, r1 - r0
        ln = math.hypot(da, dr) or 1.0
        na, nr = -dr / ln, da / ln
        if nr < 0 and da == 0: na, nr = -na, -nr
        rows.append([_axis_point(axis, a, c1 + r * math.cos(t), c2 + r * math.sin(t)) for t in ang])
        norms.append([_axis_point(axis, na, nr * math.cos(t), nr * math.sin(t)) for t in ang])
    path = sum(math.hypot(prof[i + 1][0] - prof[i][0], prof[i + 1][1] - prof[i][1]) for i in range(len(prof) - 1))
    faces = []
    for r in range(len(prof) - 1):
        for c in range(seg):
            f = [(r, c), (r, c + 1), (r + 1, c + 1), (r + 1, c)]
            if prof[r][1] == 0: f = [(r, c), (r + 1, c + 1), (r + 1, c)]
            elif prof[r + 1][1] == 0: f = [(r, c), (r, c + 1), (r + 1, c)]
            faces.append(f)
    out = [_surface(part, rows, norms, 2 * math.pi * rmax, path, "wall", faces=faces, wrap=True)]
    for end, (a, r) in ((0, prof[0]), (-1, prof[-1])):       # flat ends
        if r <= 0:
            continue
        sign = -1 if (prof[-1][0] - prof[0][0]) * (1 if end == 0 else -1) > 0 else 1
        corners = [[_axis_point(axis, a, c1 - r, c2 + r), _axis_point(axis, a, c1 + r, c2 + r)],
                   [_axis_point(axis, a, c1 - r, c2 - r), _axis_point(axis, a, c1 + r, c2 - r)]]
        ring = [(0.5 + 0.5 * math.cos(t), 0.5 - 0.5 * math.sin(t)) for t in ang[:-1]]   # (u, v) around the disc
        out.append(_surface(part, corners, _axis_point(axis, sign, 0, 0), 2 * r, 2 * r, "cap", faces=[ring], mask="disc"))
    return out


def _ears(poly):
    """Cuts a flat outline (which may have notches) into triangles."""
    pts = list(range(len(poly)))
    area = sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1] for i in range(len(poly)))
    if area < 0:
        pts.reverse()
    def cross(o, a, b): return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    tris, guard = [], 0
    while len(pts) > 3 and guard < 2000:
        guard += 1
        for k in range(len(pts)):
            i, j, l = pts[k - 1], pts[k], pts[(k + 1) % len(pts)]
            a, b, c = poly[i], poly[j], poly[l]
            if cross(a, b, c) <= 1e-12:
                continue
            if any(m not in (i, j, l) and cross(a, b, poly[m]) >= 0 and cross(b, c, poly[m]) >= 0 and cross(c, a, poly[m]) >= 0 for m in pts):
                continue
            tris.append((i, j, l)); pts.pop(k)
            break
        else:
            break
    if len(pts) == 3:
        tris.append(tuple(pts))
    return tris


def _loft_surfaces(part):
    axis, (a0, a1), (p0, p1) = part["axis"], part["a"], part["polys"]
    n = len(p0)
    ring0 = np.array([_axis_point(axis, a0, u, v) for u, v in p0], dtype=float)
    ring1 = np.array([_axis_point(axis, a1, u, v) for u, v in p1], dtype=float)
    centre = (ring0.mean(0) + ring1.mean(0)) / 2
    out = []
    for i in range(n):                                       # the sides, one flat panel each
        j = (i + 1) % n
        a, b, c, d = ring1[i], ring1[j], ring0[i], ring0[j]
        w = (np.linalg.norm(b - a) + np.linalg.norm(d - c)) / 2
        h = (np.linalg.norm(c - a) + np.linalg.norm(d - b)) / 2
        if w < 1e-6 or h < 1e-6:
            continue
        nrm = np.cross(b - a, c - a) if np.linalg.norm(b - a) > 1e-6 else np.cross(d - c, a - c)
        if np.linalg.norm(nrm) < 1e-9:
            continue
        nrm = nrm / np.linalg.norm(nrm)
        if nrm @ ((a + b + c + d) / 4 - centre) < 0:
            nrm = -nrm
        out.append(_surface(part, [[a, b], [c, d]], nrm, w, h, f"s{i}"))
    for tag, a, poly, sign in (("cap0", a0, p0, -1 if a1 > a0 else 1), ("cap1", a1, p1, 1 if a1 > a0 else -1)):
        us, vs = [q[0] for q in poly], [q[1] for q in poly]
        u0, u1, v0, v1 = min(us), max(us), min(vs), max(vs)
        if u1 - u0 < 1e-6 or v1 - v0 < 1e-6:
            continue
        corners = [[_axis_point(axis, a, u0, v1), _axis_point(axis, a, u1, v1)], [_axis_point(axis, a, u0, v0), _axis_point(axis, a, u1, v0)]]
        uv = [((q[0] - u0) / (u1 - u0), (v1 - q[1]) / (v1 - v0)) for q in poly]
        faces = [[uv[i], uv[j], uv[k]] for i, j, k in _ears(uv)]
        out.append(_surface(part, corners, _axis_point(axis, sign, 0, 0), u1 - u0, v1 - v0, tag, faces=faces, mask=("poly", uv)))
    return out


def _skin_surfaces(part):
    secs, n, seg = part["sections"], part["power"], part["opt"]["seg"]
    ang = np.linspace(0, 2 * math.pi, seg + 1)
    ca, sa = np.cos(ang), np.sin(ang)
    e = 2.0 / n
    ux, uy = np.sign(ca) * np.abs(ca) ** e, np.sign(sa) * np.abs(sa) ** e      # a "superellipse" outline
    rows = np.array([[(cx + w / 2 * ux[k], cy + h / 2 * uy[k], z) for k in range(seg + 1)] for (z, cx, cy, w, h) in secs])
    centres = np.array([(cx, cy, z) for (z, cx, cy, w, h) in secs])
    # smooth normals from the slope of the surface both ways, pointed away from the body's centre line
    dr = np.gradient(rows, axis=0)
    dc = np.roll(rows, -1, axis=1) - np.roll(rows, 1, axis=1)
    dc[:, 0] = rows[:, 1] - rows[:, -2]; dc[:, -1] = dc[:, 0]
    nrm = np.cross(dc, dr)
    ln = np.linalg.norm(nrm, axis=2, keepdims=True)
    radial = rows - centres[:, None, :]
    radial[..., 2] = 0
    rl = np.linalg.norm(radial, axis=2, keepdims=True)
    fallback = np.where(rl > 1e-9, radial / np.maximum(rl, 1e-9), np.array([0.0, 0.0, 1.0]))
    nrm = np.where(ln > 1e-9, nrm / np.maximum(ln, 1e-9), fallback)
    flip = (nrm * fallback).sum(axis=2, keepdims=True) < 0
    nrm = np.where(flip, -nrm, nrm)
    ends = np.array([0.0, 0.0, 1.0]) * (1 if secs[-1][0] > secs[0][0] else -1)
    for r, sign in ((0, -1), (len(secs) - 1, 1)):                               # a section that closes to a point faces along the body
        if secs[r][3] < 1e-6 and secs[r][4] < 1e-6:
            nrm[r] = ends * sign
    girth = max(math.pi * (3 * (w + h) / 2 - math.sqrt((3 * w / 2 + h / 2) * (w / 2 + 3 * h / 2))) for (_, _, _, w, h) in secs)
    path = sum(math.dist(centres[i], centres[i + 1]) for i in range(len(secs) - 1))
    faces = []
    for r in range(len(secs) - 1):
        for c in range(seg):
            f = [(r, c), (r, c + 1), (r + 1, c + 1), (r + 1, c)]
            if secs[r][3] < 1e-6: f = [(r, c), (r + 1, c + 1), (r + 1, c)]
            elif secs[r + 1][3] < 1e-6: f = [(r, c), (r, c + 1), (r + 1, c)]
            faces.append(f)
    out = [_surface(part, rows, nrm, girth, path, "wall", faces=faces, wrap=True)]
    for r, sign in ((0, -1), (len(secs) - 1, 1)):                               # flat ends where the body is cut off square
        z, cx, cy, w, h = secs[r]
        if w < 1e-6 or h < 1e-6:
            continue
        corners = [[(cx - w / 2, cy + h / 2, z), (cx + w / 2, cy + h / 2, z)], [(cx - w / 2, cy - h / 2, z), (cx + w / 2, cy - h / 2, z)]]
        uv = [(0.5 + 0.5 * ux[k], 0.5 - 0.5 * uy[k]) for k in range(seg)]
        out.append(_surface(part, corners, tuple(ends * sign), w, h, "cap", faces=[uv], mask=("poly", uv)))
    return out


def surfaces(part):
    return {"box": _box_surfaces, "lathe": _lathe_surfaces, "loft": _loft_surfaces, "skin": _skin_surfaces}[part["kind"]](part)


def _inside(poly, U, V):
    """Which points of a grid lie inside a flat outline."""
    inside = np.zeros(U.shape, bool)
    n = len(poly)
    for i in range(n):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
        if y1 == y2:
            continue
        hit = ((y1 > V) != (y2 > V)) & (U < (x2 - x1) * (V - y1) / (y2 - y1) + x1)
        inside ^= hit
    return inside


# ---------------------------------------------------------------- painting
_FONT = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111", "4": "101101111001001",
         "5": "111100111001111", "6": "111100111101111", "7": "111001001010010", "8": "111101111101111", "9": "111101111001111",
         "-": "000000111000000", "Y": "101101010010010", " ": "000000000000000",
         "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110", "E": "111100110100111",
         "F": "111100110100100", "G": "011100101101011", "H": "101101111101101", "I": "111010010010111", "J": "001001001101010",
         "K": "101101110101101", "L": "100100100100111", "M": "101111111101101", "N": "101111111111101", "O": "111101101101111",
         "P": "110101110100100", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010", "U": "101101101101111",
         "V": "101101101101010", "W": "101101111111101", "X": "101101010101101", "Z": "111001010100111", ".": "000000000000010"}


def _mark(img, wm, kind, cu, cv, size, size2=None):
    """Draws a marking on a face. Sizes are in metres; wm is the face width in metres."""
    hp, wp, _ = img.shape
    k = wp / max(wm, 1e-6)                                  # texels per metre on this face
    cx, cy = cu * wp, cv * hp

    def rect(x0, y0, x1, y1, col, blend=None):
        x0, y0, x1, y1 = (int(round(v)) for v in (x0, y0, x1, y1))
        x0, y0, x1, y1 = max(0, x0), max(0, y0), min(wp, x1), min(hp, y1)
        if x1 > x0 and y1 > y0:
            if blend is None: img[y0:y1, x0:x1] = col
            else: img[y0:y1, x0:x1] = np.clip(img[y0:y1, x0:x1].astype(float) * blend, 0, 255).astype(np.uint8)
    if kind == "cross":                                    # Balkenkreuz: black cross edged in white
        r, a, e = size * k / 2, size * k / 7, max(1.0, size * k / 22)
        rect(cx - r, cy - a - e, cx + r, cy + a + e, (222, 222, 216)); rect(cx - a - e, cy - r, cx + a + e, cy + r, (222, 222, 216))
        rect(cx - r + e, cy - a, cx + r - e, cy + a, (18, 18, 18)); rect(cx - a, cy - r + e, cx + a, cy + r - e, (18, 18, 18))
    elif kind == "plate":
        w2, h2 = size * k / 2, (size2 or size / 4) * k / 2
        rect(cx - w2, cy - h2, cx + w2, cy + h2, (20, 20, 20)); rect(cx - w2 + 2, cy - h2 + 2, cx + w2 - 2, cy + h2 - 2, (228, 228, 222))
        _text(img, "Y-301 428", cx, cy, h2 * 1.1, (22, 22, 22))
    elif kind.startswith("digits:"):
        # stencilled lettering: pale on dark paint, dark on a pale plate
        _text(img, kind[7:], cx, cy, max(4.0, 0.14 * k), (226, 226, 220) if img.mean() < 150 else (26, 26, 26))
    elif kind in ("hatch", "panel"):                        # an outlined door, hatch or access panel
        w2, h2 = size * k / 2, (size2 or size) * k / 2
        t = max(1.0, k * 0.012)
        for (a, b2, c, d2) in ((cx - w2, cy - h2, cx + w2, cy - h2 + t), (cx - w2, cy + h2 - t, cx + w2, cy + h2),
                               (cx - w2, cy - h2, cx - w2 + t, cy + h2), (cx + w2 - t, cy - h2, cx + w2, cy + h2)):
            rect(a, b2, c, d2, None, 0.5)
        rect(cx - w2 + t, cy - h2 + t, cx + w2 - t, cy - h2 + 2 * t, None, 1.18); rect(cx - w2 + t, cy - h2 + t, cx - w2 + 2 * t, cy + h2 - t, None, 1.18)
        n = max(2, int(2 * w2 / (k * 0.14)))
        for i in range(n + 1):                              # bolts round the edge
            bx = cx - w2 + 3 * t + i * (2 * w2 - 6 * t) / n
            rect(bx, cy - h2 + 3 * t, bx + 1.6 * t, cy - h2 + 4.6 * t, None, 0.6); rect(bx, cy + h2 - 4.6 * t, bx + 1.6 * t, cy + h2 - 3 * t, None, 0.6)
        if kind == "hatch":
            rect(cx + w2 * 0.6, cy - t * 2, cx + w2 * 0.85, cy + t * 2, (30, 30, 30)); rect(cx + w2 * 0.6, cy - t * 2, cx + w2 * 0.85, cy - t, (120, 120, 120))
    elif kind == "vent":
        w2, h2 = size * k / 2, (size2 or size) * k / 2
        step = max(2, int(k * 0.035))
        for r in range(int(cy - h2), int(cy + h2)):
            rect(cx - w2, r, cx + w2, r + 1, (22, 22, 24) if (r // step) % 2 == 0 else (74, 76, 74))
    elif kind == "roundel":                                 # low-visibility star and bars
        r = size * k / 2
        yy, xx = np.mgrid[0:hp, 0:wp]
        dxx, dyy = xx + 0.5 - cx, yy + 0.5 - cy
        tone = np.array((70, 76, 84), dtype=np.uint8)
        bar = (np.abs(dyy) < r * 0.36) & (np.abs(dxx) < r * 2.1) & (np.abs(dxx) > r * 0.95)
        ring = np.abs(np.hypot(dxx, dyy) - r * 0.92) < max(1.0, r * 0.1)
        ang = np.arctan2(dxx, -dyy)
        star = np.hypot(dxx, dyy) < r * 0.8 * (0.38 / np.cos(((ang + math.pi / 5) % (2 * math.pi / 5)) - math.pi / 5))
        img[bar | ring | star] = tone
        img[(np.abs(dyy) < r * 0.1) & (np.abs(dxx) < r * 2.1) & (np.abs(dxx) > r * 0.95)] = (150, 154, 158)
    elif kind == "hazard":
        w2, h2 = size * k / 2, (size2 or size / 4) * k / 2
        step = max(2, int(k * 0.05))
        for i in range(int(2 * w2)):
            rect(cx - w2 + i, cy - h2, cx - w2 + i + 1, cy + h2, (200, 40, 36) if (i // step) % 2 == 0 else (226, 226, 220))


def _text(img, text, cx, cy, height, col):
    hp, wp, _ = img.shape
    s = max(1, int(round(height / 5)))
    x0, y0 = int(cx - len(text) * 2 * s), int(cy - 2.5 * s)
    for n, ch in enumerate(text):
        bits = _FONT.get(ch, _FONT[" "])
        for kk, bit in enumerate(bits):
            if bit == "1":
                px, py = x0 + (n * 4 + kk % 3) * s, y0 + (kk // 3) * s
                img[max(0, py):max(0, py + s), max(0, px):max(0, px + s)] = col


def _sample(surf, wp, hp):
    """Position and normal at the middle of every texel of a surface, plus its distance from the patch edge."""
    g, nn = surf["grid"], surf["normals"]
    R, C = g.shape[:2]
    v = (np.arange(hp) + 0.5) / hp * (R - 1); u = (np.arange(wp) + 0.5) / wp * (C - 1)
    r0 = np.clip(np.floor(v).astype(int), 0, R - 2); c0 = np.clip(np.floor(u).astype(int), 0, C - 2)
    fv = (v - r0)[:, None, None]; fu = (u - c0)[None, :, None]
    def lerp(a):
        return (a[r0][:, c0] * (1 - fv) * (1 - fu) + a[r0][:, c0 + 1] * (1 - fv) * fu
                + a[r0 + 1][:, c0] * fv * (1 - fu) + a[r0 + 1][:, c0 + 1] * fv * fu)
    Pm, Nm = lerp(g), lerp(nn)
    J, I = np.mgrid[0:hp, 0:wp]
    U, V = (I + 0.5) / wp, (J + 0.5) / hp
    ev = np.minimum(V, 1 - V) * surf["h"]
    eu = np.minimum(U, 1 - U) * surf["w"]
    E = ev if surf["wrap"] else np.minimum(eu, ev)
    if surf["mask"] == "disc":
        E = (0.5 - np.hypot(U - 0.5, V - 0.5)) * surf["w"]
    elif surf["mask"]:
        poly = surf["mask"][1]                                # distance to the nearest edge of the outline
        E = np.full(U.shape, 1e9)
        um, vm = U * surf["w"], V * surf["h"]
        for i in range(len(poly)):
            ax, ay = poly[i][0] * surf["w"], poly[i][1] * surf["h"]
            bx, by = poly[(i + 1) % len(poly)][0] * surf["w"], poly[(i + 1) % len(poly)][1] * surf["h"]
            dx, dy = bx - ax, by - ay
            t = np.clip(((um - ax) * dx + (vm - ay) * dy) / max(1e-12, dx * dx + dy * dy), 0, 1)
            E = np.minimum(E, np.hypot(um - ax - t * dx, vm - ay - t * dy))
    return Pm[..., 0], Pm[..., 1], Pm[..., 2], Nm, U, V, E


def paint(surf, wp, hp, seed):
    """Texture for one surface as an (hp, wp, 3) array, painted from where each texel really is on the vehicle."""
    part = surf["part"]
    spec = MATS[part["mat"]]
    opt = part["opt"]
    X, Y, Z, N, U, V, E = _sample(surf, wp, hp)
    ny = N[..., 1]
    g = grain(X, Y, Z, seed)
    fine = vnoise(X, Y, Z, 0.05, seed + 9)
    col = np.empty((hp, wp, 3), dtype=float)
    col[:] = spec["colour"]
    kind = spec["kind"]
    um, vm = U * surf["w"], V * surf["h"]                     # metres across and down the patch
    side = np.abs(ny) < 0.6
    horiz = np.where(np.abs(N[..., 0]) > np.abs(N[..., 2]), Z, X)   # the level direction across this surface

    def mix(base, tint, amount):
        a = np.clip(amount, 0, 1)[..., None]
        return base * (1 - a) + np.asarray(tint, dtype=float) * a

    if "wheel" in opt:
        cy, cz, R, rim, tyre = opt["wheel"]
        r = np.hypot(Y - cy, Z - cz) / R
        ang = np.arctan2(Y - cy, Z - cz)
        rimc = np.array(MATS[rim]["colour"], dtype=float)
        rubber = r > tyre - 0.01
        col[:] = (30, 30, 32)
        tread = rubber & (r > 0.96)                            # tread blocks round the running surface
        blocks = ((np.floor(ang * R / 0.045) + np.floor(X / 0.05)) % 2 == 0)
        col[tread & blocks] = (17, 17, 18)
        col[tread & (np.abs((X / 0.11) % 1 - 0.5) < 0.07)] = (12, 12, 13)
        wall = rubber & ~tread                                 # sidewall with moulded rings and lettering band
        col[wall & (np.abs(r - (tyre + 0.2)) < 0.012)] = (50, 50, 52)
        col[wall & (np.abs(r - (tyre + 0.09)) < 0.03) & (np.cos(ang * 14) > 0.2)] = (44, 44, 46)
        col[wall & (r < tyre + 0.03)] = (20, 20, 21)
        metal = ~rubber
        col[metal] = rimc * 0.92
        col[metal & (r > tyre - 0.07)] = rimc * 1.28           # rim lip catches the light
        col[metal & (r < tyre * 0.8) & (r > tyre * 0.5)] = rimc * 0.78
        slots = metal & (np.abs(r - tyre * 0.66) < tyre * 0.09) & (np.cos(ang * 6) > 0.5)
        col[slots] = (18, 18, 20)                              # ventilation slots
        bolts = metal & (np.abs(r - tyre * 0.36) < tyre * 0.045) & (np.cos(ang * 10) > 0.6)
        col[bolts] = (150, 150, 150)
        col[metal & (r < tyre * 0.24)] = rimc * 0.62           # hub cap
        col[metal & (r < tyre * 0.07)] = (160, 160, 158)
        col += (g[..., None] - 0.5) * 9 + (fine[..., None] - 0.5) * 8
        col = mix(col, DUST, np.clip((r - 0.55) * 1.6, 0, 0.55) * vnoise(X, Y, Z, 0.1, seed + 3))
        col = mix(col, (96, 70, 48), metal * 0.35 * (vnoise(X, Y, Z, 0.07, seed + 4) > 0.62))   # rust spots on the rim
    elif kind == "rubber":
        col += (g[..., None] - 0.5) * 10 + (fine[..., None] - 0.5) * 8
        col = mix(col, DUST, 0.3 * vnoise(X, Y, Z, 0.12, seed + 3))
    elif kind == "track":
        zz = Z if surf["tag"] not in ("north", "south") else Y
        pitch = 0.16
        ph = (zz / pitch) % 1
        col[ph < 0.12] *= 0.45                                 # gap between links
        col[(ph > 0.4) & (ph < 0.7)] *= 1.18                   # pad
        if surf["tag"] in ("up", "down"):
            col[np.abs(U - 0.5) < 0.1] += 30                   # polished where the wheels run
            col[(np.abs(U - 0.5) < 0.03)] *= 0.6               # guide horn slot
            col[(np.abs(U - 0.18) < 0.04) | (np.abs(U - 0.82) < 0.04)] *= 0.8
        col += (g[..., None] - 0.5) * 14 + (fine[..., None] - 0.5) * 12
        col = mix(col, (112, 72, 44), 0.4 * vnoise(X, Y, Z, 0.2, seed + 5))
        col = mix(col, DUST, 0.35 * vnoise(X, Y, Z, 0.15, seed + 3))
    elif kind == "steel":
        col += (g[..., None] - 0.5) * 10 + (vnoise(X * 6, Y, Z * 6, 0.2, seed)[..., None] - 0.5) * 16
        col[E < 0.006] += 26                                   # worn bright edges
        col = mix(col, (104, 72, 46), 0.22 * (vnoise(X, Y, Z, 0.09, seed + 4) > 0.66))
    elif kind == "glass":
        col *= (1.18 - 0.42 * V[..., None])
        glint = np.abs((U * surf["w"] - V * surf["h"] * 0.7) % 0.55 - 0.15) < 0.05
        col[glint] += 44
        col[E < 0.02] = (22, 24, 26)                           # rubber seal
        col = mix(col, DUST, 0.18 * (vnoise(X, Y, Z, 0.2, seed + 3)) * (V > 0.6))
    elif kind == "grille":
        slat = (vm / 0.035) % 1
        col[slat < 0.45] = (14, 14, 16)
        col[(slat >= 0.45) & (slat < 0.6)] += 34
        col[((um / 0.3) % 1) < 0.04] = (50, 52, 52)            # dividing bars
        col[E < 0.015] = (40, 42, 42)
        col += (g[..., None] - 0.5) * 8
    elif kind == "lamp":
        col *= (0.7 + 0.45 * (1 - np.clip(np.hypot(U - 0.4, V - 0.4) * 1.5, 0, 1)))[..., None]
        ribs = ((um / 0.015) % 1) < 0.3
        col[ribs] *= 0.9
        col[E < 0.01] *= 0.45
    elif kind == "flat":
        col += (g[..., None] - 0.5) * 9 + (fine[..., None] - 0.5) * 9
    elif kind == "carbon":                                     # woven carbon fibre: a fine twill check
        weave = (np.floor(um / 0.004) + np.floor(vm / 0.004)) % 4 < 2
        col[weave] *= 1.35
        col += (g[..., None] - 0.5) * 6
        col[E < 0.002] *= 1.5
    else:                                                      # painted metal
        camo = spec.get("camo")
        if camo:
            n1 = vnoise(X, Y, Z, camo["scale"], 11) * 0.7 + vnoise(X, Y, Z, camo["scale"] * 0.4, 12) * 0.3
            n2 = vnoise(X, Y, Z, camo["scale"] * 0.8, 21) * 0.7 + vnoise(X, Y, Z, camo["scale"] * 0.3, 22) * 0.3
            # sprayed camouflage has soft edges
            a1 = np.clip((n1 - camo["cut"][0]) / 0.015, 0, 1); a2 = np.clip((n2 - camo["cut"][1]) / 0.015, 0, 1)
            col = mix(col, camo["colours"][1], a1); col = mix(col, camo["colours"][2], a2)
        # faded, uneven paint with a fine orange-peel grain
        col *= (0.88 + 0.24 * vnoise(X, Y, Z, 0.6, seed + 1))[..., None]
        col *= (0.96 + 0.08 * vnoise(X, Y, Z, 0.12, seed + 2))[..., None]
        col += (g[..., None] - 0.5) * 6 + (fine[..., None] - 0.5) * 7
        plain = opt.get("plain")
        if spec.get("clean"):
            # aircraft skin: flush panel lines at frames and ribs, a row of fasteners beside each, little dirt
            if surf["w"] > 0.25 and surf["h"] > 0.25:
                for coord, step in ((Z, 1.15), (X, 1.4)):
                    ph = (coord / step) % 1
                    col[(ph < 0.006 / step) & (E > 0.01)] *= 0.72
                    near = (ph > 0.03 / step) & (ph < 0.045 / step) & (E > 0.01)
                    col[near & (((um + vm) / 0.06) % 1 < 0.25)] *= 0.86
            col[E < 0.006] *= 0.78
            stain = vnoise(X, Y, Z, 0.5, seed + 3)
            col = mix(col, col * 0.86, np.clip((stain - 0.55) * 2.2, 0, 1))
            col = mix(col, (60, 58, 56), np.clip((0.02 - E) / 0.02, 0, 1) * 0.25 * vnoise(X, Y, Z, 0.15, seed + 8))
            img = np.clip(col, 0, 255).astype(np.uint8)
            if part["kind"] in ("box", "loft"):
                for m in opt.get("marks", []):
                    if ALIAS.get(m[0], m[0]) == surf["tag"]:
                        _mark(img, surf["w"], *m[1:])
            return img
        if surf["tag"] == "bevel":
            col *= 1.16                                        # edges rub bright
            col = mix(col, (70, 66, 60), (g > 0.82) * 0.8)
        elif not plain and surf["w"] > 0.1 and surf["h"] > 0.1:
            col[E < 0.008] *= 0.6                              # shadow line where plates meet
            col[(E >= 0.008) & (E < 0.02)] *= 1.1
            if spec.get("armour") and surf["w"] > 0.5 and surf["h"] > 0.3:
                seam = (horiz / 1.45) % 1
                col[(seam < 0.005) & (E > 0.02)] *= 0.62       # weld seams at fixed places on the vehicle
                col[(seam >= 0.005) & (seam < 0.012) & (E > 0.02)] *= 1.12
                # rows of bolts just inside each plate edge
                line = (E > 0.035) & (E < 0.06)
                along = np.where(np.minimum(U, 1 - U) * surf["w"] < np.minimum(V, 1 - V) * surf["h"], vm, um)
                bolt = line & (((along / 0.14) % 1) < 0.18)
                col[bolt] *= 0.55
                col[np.roll(bolt, -1, axis=0) & line] *= 1.2
        # weathering: rain streaks, grime in the corners, chips, road dust thrown up from below
        streak = vnoise(horiz * 14, Y * 0.35, horiz * 0, 1.0, seed + 7)
        col = mix(col, col * 0.78, side * np.clip((streak - 0.66) * 4, 0, 1) * np.clip((Y % 1.3), 0.3, 1))
        col = mix(col, (40, 38, 34), np.clip((0.03 - E) / 0.03, 0, 1) * 0.35 * vnoise(X, Y, Z, 0.2, seed + 8))
        chips = (vnoise(X, Y, Z, 0.018, seed + 6) > 0.8) & (E < 0.05) & (g > 0.4)
        col[chips] = (64, 58, 52)
        scratch = (vnoise(horiz * 3, Y * 40, horiz, 1.0, seed + 12) > 0.86) & side
        col[scratch] *= 1.14
        dust = np.where(side, np.clip((1.4 - Y) / 1.4, 0, 1) ** 1.4 * (0.3 + 0.55 * vnoise(X, Y, Z, 0.35, seed + 3)),
                        np.where(ny > 0, 0.22 * vnoise(X, Y, Z, 0.4, seed + 3), 0.5))
        col = mix(col, DUST, dust)
    img = np.clip(col, 0, 255).astype(np.uint8)
    if part["kind"] in ("box", "loft"):
        for m in opt.get("marks", []):
            if ALIAS.get(m[0], m[0]) == surf["tag"]:
                _mark(img, surf["w"], *m[1:])
    return img


def finish(model):
    """Moves every part tagged for a moving bone (aerials, jacks, hatch lids) out of the fixed lists into bones
    of its own, named move_sway1.., move_jack1.., move_hatch1.., hung from whichever part of the vehicle it was built on."""
    if model.get("finished"):
        return model
    counts, made = {}, {}
    for where, parent in (("base", "move_body"), ("turret", "turret"), ("barrel", "barrel")):
        keep = []
        for part in model[where]:
            tag = part["opt"].get("bone")
            if not tag:
                keep.append(part)
                continue
            key = (tag[0], tuple(round(v, 4) for v in tag[1]), parent)
            if key not in made:
                counts[tag[0]] = counts.get(tag[0], 0) + 1
                made[key] = {"name": f"move_{tag[0]}{counts[tag[0]]}", "parent": parent, "pivot": tag[1], "cubes": []}
                model["bones"].append(made[key])
            made[key]["cubes"].append(part)
        model[where] = keep
    model["finished"] = True
    return model


# ---------------------------------------------------------------- baking a whole vehicle
def _all_parts(model):
    return model["base"] + model["turret"] + model["barrel"] + [c for b in model["bones"] for c in b["cubes"]]


def bake(model, name, px=None):
    """Turns every part into surfaces, lays them out on one texture and paints it. Returns (image, width, height)."""
    px = px or PX
    finish(model)
    seed = sum(ord(ch) for ch in name)
    surfs, shared = [], {}
    seen = set()
    for part in _all_parts(model):
        if id(part) in seen:
            continue
        seen.add(id(part))
        part["surfaces"] = surfaces(part)
        for s in part["surfaces"]:
            s["wp"], s["hp"] = max(2, round(s["w"] * px)), max(2, round(s["h"] * px))
            if s["wrap"]:
                s["wp"] = min(s["wp"], 512)
            # little fittings and bevel strips of one material share patches instead of each having its own
            key = None
            if s["tag"] == "bevel" or s["wp"] * s["hp"] <= 150:
                if "wheel" not in part["opt"] and not part["opt"].get("marks"):
                    key = (part["mat"], s["tag"] == "bevel", min(s["wp"], 64), min(s["hp"], 64))
            if key is not None and key in shared:
                s["rect"] = shared[key]
                continue
            s["rect"] = dict(w=min(s["wp"], 64) if key else s["wp"], h=min(s["hp"], 64) if key else s["hp"], surf=s)
            surfs.append(s["rect"])
            if key is not None:
                shared[key] = s["rect"]
    area = sum((r["w"] + 1) * (r["h"] + 1) for r in surfs)
    width = 512
    while width * width < area * 1.12 and width < 4096:
        width *= 2
    surfs.sort(key=lambda r: -r["h"])
    x = y = shelf = 0
    for r in surfs:
        if x + r["w"] > width:
            x, y, shelf = 0, y + shelf + 1, 0
        r["x"], r["y"] = x, y
        x += r["w"] + 1
        shelf = max(shelf, r["h"])
    height = 64
    while height < y + shelf:
        height *= 2
    img = np.zeros((height, width, 4), dtype=np.uint8)
    for r in surfs:
        img[r["y"]:r["y"] + r["h"], r["x"]:r["x"] + r["w"], :3] = paint(r["surf"], r["w"], r["h"], seed)
        img[r["y"]:r["y"] + r["h"], r["x"]:r["x"] + r["w"], 3] = 255
    # spread colour into the one-texel gaps so no seam can show a hole
    for shift, ax in ((1, 0), (-1, 0), (1, 1), (-1, 1)):
        moved = np.roll(img, shift, axis=ax)
        empty = (img[..., 3] == 0) & (moved[..., 3] == 255)
        img[empty] = moved[empty]
    img[..., 3] = 255
    return img, width, height


# How the mesh is handed to the game (settled by looking at it in the game).
FLIP_V, FLIP_WINDING = True, True


def to_mesh(parts, width, height):
    """Superb Warfare's mesh format for a list of baked parts: positions, normals, texture points and faces."""
    pos, nrm, uvs, polys = [], [], [], []
    for part in parts:
        for s in part["surfaces"]:
            r = s["rect"]
            R, C = s["grid"].shape[:2]
            for face in s["faces"]:
                corners = []
                for (a, b) in face:
                    if s["mask"]:                           # (u, v) on a flat face
                        u, v = a, b
                        g = s["grid"]
                        p = (g[0, 0] * (1 - u) * (1 - v) + g[0, 1] * u * (1 - v) + g[1, 0] * (1 - u) * v + g[1, 1] * u * v)
                        n = s["normals"][0, 0]
                    else:
                        p, n = s["grid"][a, b], s["normals"][a, b]
                        u, v = b / (C - 1), a / (R - 1)
                    tu = (r["x"] + 0.5 + u * (r["w"] - 1)) / width
                    tv = (r["y"] + 0.5 + v * (r["h"] - 1)) / height
                    if FLIP_V: tv = 1 - tv
                    pos.append([round(p[0] * 16, 3), round(p[1] * 16, 3), round(-p[2] * 16, 3)])
                    nrm.append([round(n[0], 3), round(n[1], 3), round(-n[2], 3)])
                    uvs.append([round(tu, 5), round(tv, 5)])
                    corners.append(len(pos) - 1)
                if FLIP_WINDING:
                    corners = corners[::-1]
                if len(corners) == 3:
                    corners = corners + corners[:1]
                if len(corners) == 4:
                    polys.append([[i, i, i] for i in corners])
                else:                                       # a many-sided flat face: fan it out from its first corner
                    for k in range(1, len(corners) - 1):
                        tri = [corners[0], corners[k], corners[k + 1], corners[0]]
                        polys.append([[i, i, i] for i in tri])
    return {"normalized_uvs": True, "positions": pos, "normals": nrm, "uvs": uvs, "polys": polys}


# ---------------------------------------------------------------- offline preview
def render(model, img, yaw, pitch, size=900, bg=(150, 180, 210)):
    """A textured picture of the model with a depth buffer, to check shapes and paint without starting the game."""
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    Rm = np.array([[cy, 0, -sy], [0, 1, 0], [sy, 0, cy]]); Pm = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]); M = Pm @ Rm
    parts = _all_parts(model)
    allp = np.concatenate([s["grid"].reshape(-1, 3) for p in parts for s in p["surfaces"]]) @ M.T
    lo, hi = allp.min(0), allp.max(0)
    sc = (size - 40) / max(hi[0] - lo[0], hi[1] - lo[1]); mid = (lo + hi) / 2
    out = np.empty((size, size, 3), dtype=np.uint8); out[:] = bg
    depth = np.full((size, size), -1e9)
    L = np.array([0.3, 0.8, 0.52]); L /= np.linalg.norm(L)
    for part in parts:
        for s in part["surfaces"]:
            r = s["rect"]
            wp = max(2, int(s["w"] * sc * 1.5) + 2); hp = max(2, int(s["h"] * sc * 1.5) + 2)
            wp, hp = min(wp, 1400), min(hp, 1400)
            X, Y, Z, N, U, V, E = _sample(s, wp, hp)
            keep = np.ones((hp, wp), bool)
            if s["mask"] == "disc": keep = np.hypot(U - 0.5, V - 0.5) <= 0.5
            elif s["mask"]: keep = _inside(s["mask"][1], U, V)
            Nc = N @ M.T
            keep &= Nc[..., 2] > 0.0
            if not keep.any():
                continue
            S = np.stack([X, Y, Z], -1)[keep] @ M.T
            tx = np.clip((U[keep] * r["w"]).astype(int), 0, r["w"] - 1) + r["x"]
            ty = np.clip((V[keep] * r["h"]).astype(int), 0, r["h"] - 1) + r["y"]
            shade = 0.48 + 0.52 * np.clip(Nc[keep] @ (M @ L) / (np.linalg.norm(Nc[keep], axis=1) + 1e-9), 0, 1)
            colr = (img[ty, tx, :3].astype(float) * shade[:, None]).astype(np.uint8)
            sx = np.round(size / 2 + (S[:, 0] - mid[0]) * sc).astype(int)
            sy_ = np.round(size / 2 - (S[:, 1] - mid[1]) * sc).astype(int)
            ok = (sx >= 0) & (sx < size) & (sy_ >= 0) & (sy_ < size)
            sx, sy_, zz, colr = sx[ok], sy_[ok], S[ok, 2], colr[ok]
            order = np.argsort(zz)                           # nearest written last
            sx, sy_, zz, colr = sx[order], sy_[order], zz[order], colr[order]
            closer = zz > depth[sy_, sx]
            depth[sy_[closer], sx[closer]] = zz[closer]
            out[sy_[closer], sx[closer]] = colr[closer]
    return Image.fromarray(out)
