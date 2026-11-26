#!/usr/bin/env python3
"""Building kit for the Superb Warfare vehicle models: cubes, shared parts, and the texture baker.

Vehicle space: x across (positive = left), y up, z towards the front, origin on the ground under the middle,
in blocks (1 block = 1 metre). Models are lists of cubes made with B(); bake() gives every face of every cube
its own patch of a per-vehicle texture and paints it from the face's real position, so camouflage flows from
one part to the next, dirt gathers low down, wheels get round rims and panels get edges and rivets.
"""
import math
import numpy as np
from PIL import Image

PX = 16            # texels per block

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

DUST = np.array([132, 120, 94], dtype=float)


# ---------------------------------------------------------------- cubes
# How this file's rotations map onto Bedrock's. pitch: front end up; yaw: front end towards +x; roll: +x side up.
SIGN_PITCH, SIGN_YAW, SIGN_ROLL = -1, 1, -1   # checked against the game


def B(mat, x, y, z, w, h, d, pitch=0, yaw=0, roll=0, pivot=None, **opt):
    """One cube from a corner and a size, optionally turned about one axis through `pivot` (default: its middle).
    Options: marks=[(face, kind, fu, fv, size)], wheel=(cy, cz, radius, rim material), plain=True (no rivets/seams)."""
    assert sum(1 for a in (pitch, yaw, roll) if a) <= 1, "turn a cube about one axis only"
    p = pivot or (x + w / 2, y + h / 2, z + d / 2)
    return dict(mat=mat, box=(x, y, z, w, h, d), rot=(pitch, yaw, roll), pivot=p, opt=opt)


def P(mat, x, y, z, w, h, d, pitch=0, yaw=0, roll=0, pivot=None, **opt):
    """The same cube on both sides."""
    mp = (-pivot[0], pivot[1], pivot[2]) if pivot else None
    if "marks" in opt:   # a marking on the left face belongs on the right face of the mirrored cube
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


def rod_z(mat, cx, cy, z0, length, dia, **opt):
    """A round bar along the vehicle (barrels, tubes)."""
    if dia >= 0.3:   # big enough to see the outline: a true octagon from four slabs
        t = dia * 0.4142
        return [B(mat, cx - dia / 2, cy - t / 2, z0, dia, t, length, roll=a, plain=True, **opt) for a in (0, 45, 90, 135)]
    s = dia * 0.86
    return [B(mat, cx - s / 2, cy - s / 2, z0, s, s, length, plain=True, **opt),
            B(mat, cx - s / 2, cy - s / 2, z0, s, s, length, roll=45, plain=True, **opt)]


def rods_z(mat, cx, cy, z0, length, dia):
    return rod_z(mat, cx, cy, z0, length, dia) + rod_z(mat, -cx, cy, z0, length, dia)


def rod_y(mat, cx, cz, y0, height, dia, **opt):
    if dia >= 0.3:
        t = dia * 0.4142
        return [B(mat, cx - dia / 2, y0, cz - t / 2, dia, height, t, yaw=a, plain=True, **opt) for a in (0, 45, 90, 135)]
    s = dia * 0.86
    return [B(mat, cx - s / 2, y0, cz - s / 2, s, height, s, plain=True, **opt),
            B(mat, cx - s / 2, y0, cz - s / 2, s, height, s, yaw=45, plain=True, **opt)]


def rod_x(mat, x0, length, cy, cz, dia, **opt):
    s = dia * 0.86
    return [B(mat, x0, cy - s / 2, cz - s / 2, length, s, s, plain=True, **opt),
            B(mat, x0, cy - s / 2, cz - s / 2, length, s, s, pitch=45, plain=True, **opt)]


def disc_x(mat, x0, width, cy, cz, dia, **opt):
    """An eight-sided disc lying across the vehicle: four slabs fanned around the axle."""
    t = dia * 0.4142
    return [B(mat, x0, cy - dia / 2, cz - t / 2, width, dia, t, pitch=a, plain=True, **opt) for a in (0, 45, 90, 135)]


def wheel(x0, width, cy, cz, dia, rim, tyre=0.62):
    """A road wheel: the tyre and rim are painted onto the disc faces, with a hub standing proud of it."""
    out = disc_x(TYRE, x0, width, cy, cz, dia, wheel=(cy, cz, dia / 2, rim, tyre))
    outer = x0 + width if x0 >= 0 else x0 - 0.04
    out += disc_x(rim, outer, 0.04, cy, cz, dia * 0.26, wheel=(cy, cz, dia * 0.13, rim, 9.0))
    return out


def wheel_bone(name, x0, width, cy, cz, dia, rim=STEEL_D, parent="base", tyre=0.62):
    return {"name": name, "parent": parent, "pivot": (x0 + width / 2, cy, cz), "cubes": wheel(x0, width, cy, cz, dia, rim, tyre)}


def truck_wheels(zs, x, width, dia, rim=STEEL_D):
    """Wheel bones for both sides at each axle position; x is the inner face of the tyre."""
    bones = []
    for i, z in enumerate(zs, start=1):
        bones.append(wheel_bone(f"wheelL{i}", x, width, dia / 2, z, dia, rim))
        bones.append(wheel_bone(f"wheelR{i}", -x - width, width, dia / 2, z, dia, rim))
    return bones


def axles(zs, x, dia, mat=STEEL_D):
    """Axle beams, differentials and leaf springs under a truck."""
    c = []
    for z in zs:
        c += rod_x(mat, -x, 2 * x, dia / 2, z, 0.16)
        c += [B(mat, -0.22, dia / 2 - 0.2, z - 0.2, 0.44, 0.4, 0.4, plain=True)]
        c += P(mat, x - 0.32, dia / 2 + 0.1, z - 0.55, 0.08, 0.08, 1.1, plain=True)
    return c


def running_gear(x, width, z0, z1, count, dia, body, rollers=3):
    """Tracks for both sides: road wheels (which turn), sprocket, idler, return rollers and the track itself
    with raised links."""
    cubes, bones = [], []
    top = dia + 0.26
    for sx in (x, -x - width):
        cubes += [B(TRACK, sx, 0, z0 + 0.5, width, 0.09, z1 - z0 - 1.0),                     # run on the ground
                  B(TRACK, sx, top - 0.09, z0 + 0.15, width, 0.09, z1 - z0 - 0.3),           # return run
                  slope(TRACK, sx, width, z1 - 0.55, 0.09, z1 - 0.02, top - 0.5, 0.09),
                  slope(TRACK, sx, width, z0 + 0.02, top - 0.5, z0 + 0.55, 0.09, 0.09),
                  B(TRACK, sx, top - 0.5, z1 - 0.09, width, 0.42, 0.09),
                  B(TRACK, sx, top - 0.5, z0, width, 0.42, 0.09)]
        # raised link edges along both runs, and guide horns down the middle of the return run
        n = int((z1 - z0 - 1.0) / 0.3)
        for i in range(n + 1):
            z = z0 + 0.5 + i * (z1 - z0 - 1.06) / n
            cubes += [B(TRACK, sx - 0.01, -0.02, z, width + 0.02, 0.03, 0.07, plain=True),
                      B(TRACK, sx - 0.01, top - 0.01, z, width + 0.02, 0.03, 0.07, plain=True)]
        for end, zc in ((1, z1 - 0.32), (-1, z0 + 0.32)):                                     # sprocket and idler
            cubes += disc_x(STEEL_D, sx + 0.06, width - 0.12, top - 0.42, zc, 0.66, wheel=(top - 0.42, zc, 0.33, body, 0.86))
            cubes += disc_x(body, sx + (width - 0.04 if sx >= 0 else -0.02), 0.06, top - 0.42, zc, 0.24)
        for i in range(rollers):                                                              # return rollers
            zc = z0 + 1.2 + i * (z1 - z0 - 2.4) / max(1, rollers - 1)
            cubes += disc_x(STEEL_D, sx + 0.1, width - 0.2, top - 0.2, zc, 0.2)
    span = (z1 - z0 - 1.6) / (count - 1)
    for i in range(count):
        z = z0 + 0.8 + span * i
        for side, sx in (("L", x), ("R", -x - width)):
            cy = dia / 2 + 0.09
            c = disc_x(TYRE, sx + 0.04, width - 0.08, cy, z, dia, wheel=(cy, z, dia / 2, body, 0.82))
            c += disc_x(body, sx + (width - 0.05 if sx >= 0 else -0.03), 0.08, cy, z, dia * 0.3, wheel=(cy, z, dia * 0.15, body, 9.0))
            bones.append({"name": f"wheel{side}{i + 1}", "parent": "base", "pivot": (sx + width / 2, cy, z), "cubes": c})
            # suspension arm behind each wheel
            cubes.append(B(STEEL_D, sx + (0.0 if sx >= 0 else width - 0.06), cy - 0.05, z - 0.42, 0.06, 0.1, 0.45, plain=True))
    return cubes, bones


# ---------------------------------------------------------------- shared truck parts
def cab(mat, dark, z0, length, width, floor, height, bonnet=0.0):
    """A cab-over truck cab starting at z0: sloped windscreen, doors, bumper, lights, mirrors, steps, wipers."""
    hw = width / 2
    front = z0 + length
    c = [B(mat, -hw, floor, z0, width, height * 0.5, length, marks=[("left", "hatch", 0.55, 0.5, length * 0.5, height * 0.42),
                                                                    ("right", "hatch", 0.45, 0.5, length * 0.5, height * 0.42)]),
         B(mat, -hw, floor + height * 0.5, z0, width, height * 0.5, length - 0.3),
         B(mat, -hw + 0.05, floor + height, z0 + 0.1, width - 0.1, 0.08, length - 0.5),
         B(dark, -hw + 0.3, floor + height + 0.08, z0 + 0.3, 0.6, 0.06, 0.6, plain=True),      # roof hatch
         slope(mat, -hw, width, front, floor + height * 0.5, front - 0.3, floor + height, 0.06),
         slope(GLASS, -hw + 0.12, width * 0.5 - 0.17, front + 0.02, floor + height * 0.55, front - 0.25, floor + height * 0.95, 0.03),
         slope(GLASS, 0.05, width * 0.5 - 0.17, front + 0.02, floor + height * 0.55, front - 0.25, floor + height * 0.95, 0.03),
         B(dark, -hw - 0.03, floor - 0.25, front - 0.12, width + 0.06, 0.32, 0.22),             # bumper
         B(STEEL_D, -0.3, floor - 0.2, front + 0.1, 0.6, 0.12, 0.05, plain=True),              # tow bar
         B(GRILLE, -hw * 0.55, floor + 0.12, front - 0.01, width * 0.55, height * 0.3, 0.04),
         B(WHITE, -0.25, floor - 0.08, front + 0.1, 0.5, 0.12, 0.02, marks=[("front", "plate", 0.5, 0.5, 0.5, 0.12)])]
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


# ---------------------------------------------------------------- painting one face
FACES = ("north", "south", "east", "west", "up", "down")
ALIAS = {"front": "north", "rear": "south", "left": "east", "right": "west", "top": "up", "bottom": "down"}


def face_size(box, face):
    x, y, z, w, h, d = box
    return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}[face]


def face_points(box, face, U, V):
    """Vehicle-space position of the point at fraction (U, V) of a face, the way the game maps the texture."""
    x, y, z, w, h, d = box
    one = np.ones_like(U)
    if face == "north": return x + w * (1 - U), y + h * (1 - V), (z + d) * one       # front
    if face == "south": return x + w * U, y + h * (1 - V), z * one                    # rear
    if face == "east":  return (x + w) * one, y + h * (1 - V), z + d * U              # left side
    if face == "west":  return x * one, y + h * (1 - V), z + d * (1 - U)              # right side
    if face == "up":    return x + w * U, (y + h) * one, z + d * (1 - V)
    return x + w * U, y * one, z + d * V


_FONT = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111", "4": "101101111001001",
         "5": "111100111001111", "6": "111100111101111", "7": "111001001010010", "8": "111101111101111", "9": "111101111001111",
         "-": "000000111000000", "Y": "101101010010010", " ": "000000000000000"}


def _mark(img, kind, cu, cv, size, size2=None):
    hp, wp, _ = img.shape
    cx, cy = cu * wp, cv * hp

    def rect(x0, y0, x1, y1, col):
        x0, y0, x1, y1 = (int(round(v)) for v in (x0, y0, x1, y1))
        img[max(0, y0):max(0, y1), max(0, x0):max(0, x1)] = col
    if kind == "cross":                                    # Balkenkreuz: black cross edged in white
        r, a = max(3, size * PX / 2), max(1, size * PX / 6)
        rect(cx - r, cy - a - 1, cx + r, cy + a + 1, (225, 225, 220)); rect(cx - a - 1, cy - r, cx + a + 1, cy + r, (225, 225, 220))
        rect(cx - r + 1, cy - a, cx + r - 1, cy + a, (16, 16, 16)); rect(cx - a, cy - r + 1, cx + a, cy + r - 1, (16, 16, 16))
    elif kind == "plate":
        w2, h2 = size * PX / 2, (size2 or size / 4) * PX / 2
        rect(cx - w2, cy - h2, cx + w2, cy + h2, (228, 228, 222))
        for i in range(int(w2) - 1):
            if i % 2 == 0: rect(cx - w2 + 1 + i * 2, cy - 1, cx - w2 + 2 + i * 2, cy + 1, (24, 24, 24))
    elif kind.startswith("digits:"):
        text = kind[7:]
        x0, y0 = int(cx - len(text) * 2), int(cy - 3)
        for n, ch in enumerate(text):
            bits = _FONT.get(ch, _FONT[" "])
            for k, bit in enumerate(bits):
                if bit == "1":
                    px, py = x0 + n * 4 + k % 3, y0 + k // 3
                    if 0 <= px < wp and 0 <= py < hp: img[py, px] = (230, 230, 224)
    elif kind in ("hatch", "panel"):                        # an outlined door, hatch or access panel
        w2, h2 = size * PX / 2, (size2 or size) * PX / 2
        x0, y0, x1, y1 = (int(round(v)) for v in (cx - w2, cy - h2, cx + w2, cy + h2))
        x0, y0, x1, y1 = max(0, x0), max(0, y0), min(wp, x1), min(hp, y1)
        if x1 - x0 >= 3 and y1 - y0 >= 3:
            dark = (img[y0:y1, x0:x1].astype(float) * 0.62).astype(np.uint8)
            lite = np.clip(img[y0:y1, x0:x1].astype(float) * 1.16, 0, 255).astype(np.uint8)
            edge = np.zeros((y1 - y0, x1 - x0), bool); edge[0, :] = edge[-1, :] = edge[:, 0] = edge[:, -1] = True
            inner = np.zeros_like(edge)
            if x1 - x0 >= 6 and y1 - y0 >= 6: inner[1, 1:-1] = inner[1:-1, 1] = True
            img[y0:y1, x0:x1][inner] = lite[inner]
            img[y0:y1, x0:x1][edge] = dark[edge]
            if kind == "hatch" and x1 - x0 >= 8:
                rect(x1 - 4, (y0 + y1) / 2 - 1, x1 - 2, (y0 + y1) / 2 + 1, (30, 30, 30))
    elif kind == "vent":
        w2, h2 = size * PX / 2, (size2 or size) * PX / 2
        for r in range(int(cy - h2), int(cy + h2)):
            rect(cx - w2, r, cx + w2, r + 1, (26, 26, 28) if r % 2 == 0 else (70, 72, 70))
    elif kind == "hazard":
        w2, h2 = size * PX / 2, (size2 or size / 4) * PX / 2
        for i in range(int(2 * w2)):
            col = (200, 40, 36) if (i // 2) % 2 == 0 else (226, 226, 220)
            rect(cx - w2 + i, cy - h2, cx - w2 + i + 1, cy + h2, col)
    elif kind == "lens":
        r = max(1, size * PX / 2)
        rect(cx - r, cy - r, cx + r, cy + r, (20, 24, 30)); rect(cx - r + 1, cy - r + 1, cx, cy, (60, 90, 120))


def paint_face(cube, face, wp, hp, seed):
    """Texture for one face as an (hp, wp, 3) array."""
    spec = MATS[cube["mat"]]
    opt = cube["opt"]
    J, I = np.mgrid[0:hp, 0:wp]
    U, V = (I + 0.5) / wp, (J + 0.5) / hp
    X, Y, Z = face_points(cube["box"], face, U, V)
    g = grain(X, Y, Z, seed)
    col = np.empty((hp, wp, 3), dtype=float)
    col[:] = spec["colour"]
    kind = spec["kind"]
    ring = np.minimum(np.minimum(I, wp - 1 - I), np.minimum(J, hp - 1 - J))    # texels in from the face edge
    side = face in ("north", "south", "east", "west")
    along = Z if face in ("east", "west") else X                                # horizontal direction on the face

    if "wheel" in opt and face in ("east", "west"):
        cy, cz, R, rim, tyre = opt["wheel"]
        r = np.hypot(Y - cy, Z - cz) / R
        ang = np.arctan2(Y - cy, Z - cz)
        rimc = np.array(MATS[rim]["colour"], dtype=float)
        col[:] = (30, 30, 32)
        col[(r > tyre + 0.14) & (r < tyre + 0.2)] = (48, 48, 50)                # moulded line on the sidewall
        inside = r <= tyre
        col[inside] = rimc * 0.9
        col[inside & (r > tyre - 0.08)] = rimc * 1.25                           # rim lip
        col[inside & (r < tyre * 0.72) & (r > tyre * 0.6)] = rimc * 0.7         # dished centre
        bolts = inside & (np.abs(r - tyre * 0.42) < tyre * 0.07) & (np.cos(ang * 8) > 0.55)
        col[bolts] = (26, 26, 28)
        col[r < tyre * 0.2] = rimc * 0.6                                        # hub cap
        col += (g[..., None] - 0.5) * 10
        mud = np.clip((r - 0.7) * 2.2, 0, 0.5) * vnoise(X, Y, Z, 0.12, seed + 3)
        col = col * (1 - mud[..., None]) + DUST * mud[..., None]
    elif kind == "rubber":
        col += (g[..., None] - 0.5) * 12
        if face in ("up", "down", "north", "south"):                            # tread blocks across the tyre
            tread = ((np.floor(Y * PX / 2) + np.floor(Z * PX / 2) + np.floor(X * PX)) % 2 == 0)
            col[tread] *= 0.7
        mud = 0.25 * vnoise(X, Y, Z, 0.15, seed + 3)
        col = col * (1 - mud[..., None]) + DUST * mud[..., None]
    elif kind == "track":
        link = np.floor((Z if face != "north" and face != "south" else Y) * PX / 2.5) % 2 == 0
        col[link] *= 0.72
        col += (g[..., None] - 0.5) * 16
        if face in ("up", "down"):                                              # worn bright where the wheels run
            mid = np.abs(U - 0.5) < 0.14
            col[mid] += 34
        rust = vnoise(X, Y, Z, 0.25, seed + 5)[..., None]
        col = col * (1 - 0.3 * rust) + np.array([110, 74, 48.0]) * 0.3 * rust
        mud = 0.3 * vnoise(X, Y, Z, 0.2, seed + 3)[..., None]
        col = col * (1 - mud) + DUST * mud
    elif kind == "steel":
        col += (g[..., None] - 0.5) * 12 + (vnoise(X * 4, Y, Z * 4, 0.3, seed)[..., None] - 0.5) * 14
        if wp >= 4 and hp >= 4:
            col[ring == 0] += 22                                                # worn bright edges
    elif kind == "glass":
        col *= (1.12 - 0.35 * V[..., None])
        glint = np.abs((U - V * 0.6) % 0.7 - 0.2) < 0.06
        col[glint] += 46
        if wp >= 5 and hp >= 5:
            col[ring == 0] = (24, 26, 28)
    elif kind == "grille":
        slat = J % 2 == 0
        col[slat] = (20, 20, 22)
        col[~slat] += 26
        if wp >= 5 and hp >= 5:
            col[ring == 0] = (34, 36, 36)
    elif kind == "lamp":
        col *= (0.78 + 0.34 * (1 - np.clip(np.hypot(U - 0.4, V - 0.4) * 1.6, 0, 1)))[..., None]
        if wp >= 3 and hp >= 3:
            col[ring == 0] *= 0.6
    elif kind == "flat":
        col += (g[..., None] - 0.5) * 12
    else:                                                                       # painted metal
        camo = spec.get("camo")
        if camo:
            n1 = vnoise(X, Y, Z, camo["scale"], 11) * 0.7 + vnoise(X, Y, Z, camo["scale"] * 0.4, 12) * 0.3
            n2 = vnoise(X, Y, Z, camo["scale"] * 0.8, 21) * 0.7 + vnoise(X, Y, Z, camo["scale"] * 0.3, 22) * 0.3
            col[n1 > camo["cut"][0]] = camo["colours"][1]
            col[n2 > camo["cut"][1]] = camo["colours"][2]
        # uneven, faded paint
        col *= (0.9 + 0.2 * vnoise(X, Y, Z, 0.7, seed + 1))[..., None]
        col += (g[..., None] - 0.5) * 9
        big = wp >= 6 and hp >= 6
        plain = opt.get("plain")
        if big and not plain:
            col[ring == 0] *= 0.72                                              # shadowed panel edge
            col[ring == 1] *= 1.1                                               # and its highlight
        if spec.get("armour") and not plain and wp >= 16 and hp >= 16:
            # weld seams at fixed places on the vehicle, so they line up from one face to the next
            seam = np.floor(along * PX) % 26 == 0
            col[seam & (ring > 1)] *= 0.74
            col[np.roll(seam, 1, axis=1) & (ring > 1)] *= 1.08
            rivet = (ring == 3) & (((I + J) % 5) == 0)
            col[rivet] *= 0.6
            col[np.roll(rivet, -1, axis=0) & (ring >= 3)] *= 1.14
        if side:
            # rain streaks, chipped edges and road dust thrown up from below
            streak = vnoise(along * 9, Y * 0.5, along * 0 + 3, 1.0, seed + 7)
            col[(streak > 0.74) & (ring > 0)] *= 0.9
            dust = np.clip((1.3 - Y) / 1.3, 0, 1) ** 1.5 * (0.35 + 0.5 * vnoise(X, Y, Z, 0.4, seed + 3))
            col = col * (1 - dust[..., None]) + DUST * dust[..., None]
        elif face == "up":
            dust = 0.16 * vnoise(X, Y, Z, 0.5, seed + 3)
            col = col * (1 - dust[..., None]) + DUST * dust[..., None]
        chips = (g > 0.985) & (ring <= 2) & big
        col[chips] = (58, 54, 50)
    col *= {"up": 1.06, "down": 0.6, "north": 0.97, "south": 0.9, "east": 0.94, "west": 0.94}[face]
    img = np.clip(col, 0, 255).astype(np.uint8)
    for m in opt.get("marks", []):
        if ALIAS.get(m[0], m[0]) == face:
            _mark(img, *m[1:])
    return img


# ---------------------------------------------------------------- baking a whole vehicle
def _all_cubes(model):
    return model["base"] + model["turret"] + model["barrel"] + [c for b in model["bones"] for c in b["cubes"]]


def bake(model, name):
    """Lays every face out on one texture and paints it. Returns (bones as Bedrock JSON, image)."""
    seed = sum(ord(ch) for ch in name)
    rects, shared = [], {}
    for cube in _all_cubes(model):
        cube["uv"] = {}
        for face in FACES:
            a, b = face_size(cube["box"], face)
            wp, hp = max(1, round(a * PX)), max(1, round(b * PX))
            # tiny faces of the same kind share one patch; everything else is painted individually
            key = (cube["mat"], face, wp, hp) if wp * hp <= 12 and not cube["opt"].get("marks") and "wheel" not in cube["opt"] else None
            if key is not None and key in shared:
                cube["uv"][face] = shared[key]
                continue
            r = dict(cube=cube, face=face, w=wp, h=hp)
            rects.append(r)
            cube["uv"][face] = r
            if key is not None:
                shared[key] = r
    width = 512 if sum(r["w"] * r["h"] for r in rects) < 380000 else 1024
    rects.sort(key=lambda r: -r["h"])
    x = y = shelf = 0
    for r in rects:
        if x + r["w"] > width:
            x, y, shelf = 0, y + shelf, 0
        r["x"], r["y"] = x, y
        x += r["w"]
        shelf = max(shelf, r["h"])
    height = 64
    while height < y + shelf:
        height *= 2
    img = np.zeros((height, width, 4), dtype=np.uint8)
    for r in rects:
        img[r["y"]:r["y"] + r["h"], r["x"]:r["x"] + r["w"], :3] = paint_face(r["cube"], r["face"], r["w"], r["h"], seed)
        img[r["y"]:r["y"] + r["h"], r["x"]:r["x"] + r["w"], 3] = 255
    return img, width, height


def _piv(p):
    return [round(p[0] * 16, 3), round(p[1] * 16, 3), round(-p[2] * 16, 3)]


def to_bedrock(cube):
    x, y, z, w, h, d = cube["box"]
    out = {"origin": [round(x * 16, 3), round(y * 16, 3), round(-(z + d) * 16, 3)],
           "size": [round(w * 16, 3), round(h * 16, 3), round(d * 16, 3)],
           "uv": {f: {"uv": [r["x"], r["y"]], "uv_size": [r["w"], r["h"]]} for f, r in cube["uv"].items()}}
    pitch, yaw, roll = cube["rot"]
    if pitch or yaw or roll:
        out["rotation"] = [pitch * SIGN_PITCH, yaw * SIGN_YAW, roll * SIGN_ROLL]
        out["pivot"] = _piv(cube["pivot"])
    return out


# ---------------------------------------------------------------- offline preview
def _turn(cube, pt):
    pitch, yaw, roll = cube["rot"]
    p = cube["pivot"]
    px, py, pz = pt[0] - p[0], pt[1] - p[1], pt[2] - p[2]
    if pitch:
        a = math.radians(pitch); py, pz = py * math.cos(a) + pz * math.sin(a), pz * math.cos(a) - py * math.sin(a)
    if yaw:
        a = math.radians(yaw); px, pz = px * math.cos(a) + pz * math.sin(a), pz * math.cos(a) - px * math.sin(a)
    if roll:
        a = math.radians(roll); px, py = px * math.cos(a) - py * math.sin(a), py * math.cos(a) + px * math.sin(a)
    return (px + p[0], py + p[1], pz + p[2])


_NORMAL = {"north": (0, 0, 1), "south": (0, 0, -1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def render(model, img, yaw, pitch, size=900, bg=(150, 180, 210)):
    """A textured picture of the model with a depth buffer, to check shapes and paint without starting the game."""
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    R = np.array([[cy, 0, -sy], [0, 1, 0], [sy, 0, cy]]); Pm = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]); M = Pm @ R
    cubes = _all_cubes(model)
    corners = []
    for c in cubes:
        x, y, z, w, h, d = c["box"]
        corners += [_turn(c, (x + i * w, y + j * h, z + k * d)) for i in (0, 1) for j in (0, 1) for k in (0, 1)]
    pts = np.array(corners) @ M.T
    lo, hi = pts.min(0), pts.max(0)
    sc = (size - 40) / max(hi[0] - lo[0], hi[1] - lo[1]); mid = (lo + hi) / 2
    out = np.empty((size, size, 3), dtype=np.uint8); out[:] = bg
    depth = np.full((size, size), -1e9)
    L = np.array([0.3, 0.8, 0.52]); L /= np.linalg.norm(L)
    sub = max(1, int(math.ceil(sc / PX)))          # screen pixels per texel
    for c in cubes:
        o = np.array(_turn(c, (0, 0, 0)))
        for face, r in c["uv"].items():
            n = (np.array(_turn(c, _NORMAL[face])) - o) @ M.T
            if n[2] <= 0.02:
                continue
            tex = img[r["y"]:r["y"] + r["h"], r["x"]:r["x"] + r["w"], :3]
            k = sub + 1
            J, I = np.mgrid[0:r["h"] * k, 0:r["w"] * k]
            U, V = (I + 0.5) / (r["w"] * k), (J + 0.5) / (r["h"] * k)
            X, Y, Z = face_points(c["box"], face, U, V)
            P3 = np.stack([X, Y, Z], -1).reshape(-1, 3)
            # rotate the cube, then the camera
            pitch_, yaw_, roll_ = c["rot"]
            if pitch_ or yaw_ or roll_:
                pv = np.array(c["pivot"]); q = P3 - pv
                a = math.radians(pitch_ or yaw_ or roll_); ca, sa = math.cos(a), math.sin(a)
                if pitch_: q = np.stack([q[:, 0], q[:, 1] * ca + q[:, 2] * sa, q[:, 2] * ca - q[:, 1] * sa], -1)
                elif yaw_: q = np.stack([q[:, 0] * ca + q[:, 2] * sa, q[:, 1], q[:, 2] * ca - q[:, 0] * sa], -1)
                else: q = np.stack([q[:, 0] * ca - q[:, 1] * sa, q[:, 1] * ca + q[:, 0] * sa, q[:, 2]], -1)
                P3 = q + pv
            S = P3 @ M.T
            sx = np.round(size / 2 + (S[:, 0] - mid[0]) * sc).astype(int)
            sy_ = np.round(size / 2 - (S[:, 1] - mid[1]) * sc).astype(int)
            ok = (sx >= 0) & (sx < size) & (sy_ >= 0) & (sy_ < size)
            shade = 0.5 + 0.5 * max(0.0, float(n @ L) / (np.linalg.norm(n) + 1e-9))
            colr = (tex[(J // k).ravel(), (I // k).ravel()].astype(float) * shade).astype(np.uint8)
            sx, sy_, zz, colr = sx[ok], sy_[ok], S[ok, 2], colr[ok]
            closer = zz > depth[sy_, sx]
            depth[sy_[closer], sx[closer]] = zz[closer]
            out[sy_[closer], sx[closer]] = colr[closer]
    return Image.fromarray(out)
