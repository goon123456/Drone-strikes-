#!/usr/bin/env python3
"""Geometry and paint for the Superb Warfare vehicles, in SBW's Blockbench (Bedrock geo) format.

Vehicle space: x across (positive = left), y up, z towards the front, origin on the ground under the middle,
measured in blocks (1 block = 1 metre). Each model function returns the cubes of the three aiming bones
(base, turret, barrel) plus extra bones; bones named wheelL*/wheelR* are spun by Superb Warfare when driving.
Used by gen_assets.py.
"""
import math, random
from PIL import Image

# ---------------------------------------------------------------- materials: one 32 px tile each in a 256 px atlas
TILE, ATLAS = 32, 256
MATS = {}          # name -> (index, average colour)
_PAINTERS = []


def _mat(name, base, painter="paint", **kw):
    MATS[name] = (len(_PAINTERS), base)
    _PAINTERS.append((base, painter, kw))
    return name


OLIVE = _mat("olive", (92, 104, 62))               # Soviet-era olive: ZU-23-2
OLIVE_D = _mat("olive_d", (66, 76, 46))
RUS = _mat("rus", (78, 92, 62))                    # Russian protective green: Pantsir, Iskander, Krasukha
RUS_D = _mat("rus_d", (56, 68, 46))
US = _mat("us", (84, 94, 70))                      # US Army green: HIMARS, M109, Tomahawk launcher
US_D = _mat("us_d", (60, 68, 52))
TAN = _mat("tan", (184, 166, 128))                 # desert tan: Patriot
TAN_D = _mat("tan_d", (146, 130, 98))
NATO = _mat("nato", (74, 90, 60), "camo", colours=[(74, 90, 60), (92, 70, 50), (30, 30, 30)])   # Gepard
NATO_D = _mat("nato_d", (54, 66, 44))
TYRE = _mat("tyre", (34, 34, 36), "tread")
TRACK = _mat("track", (46, 44, 42), "links")
STEEL = _mat("steel", (138, 142, 148))
STEEL_D = _mat("steel_d", (62, 66, 70))
BLACK = _mat("black", (22, 22, 24))
GLASS = _mat("glass", (40, 62, 88), "glass")
WHITE = _mat("white", (216, 216, 210))
RED = _mat("red", (190, 40, 36))
RADOME = _mat("radome", (150, 158, 140))
GRILLE = _mat("grille", (40, 42, 44), "grille")
AMBER = _mat("amber", (220, 150, 40))
RUS_CAMO = _mat("rus_camo", (78, 92, 62), "camo", colours=[(78, 92, 62), (150, 138, 100), (40, 44, 36)])
GREY = _mat("grey", (120, 124, 126))


def texture(path, save):
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    px = img.load()
    for i, (base, painter, kw) in enumerate(_PAINTERS):
        rnd = random.Random(100 + i)
        ox, oy = (i % 8) * TILE, (i // 8) * TILE
        blobs = [(rnd.uniform(0, TILE), rnd.uniform(0, TILE), rnd.uniform(5, 10), rnd.randrange(1, 3)) for _ in range(7)]
        for y in range(TILE):
            for x in range(TILE):
                col = base
                n = rnd.randint(-6, 6)
                if painter == "camo":
                    best = None
                    for bx, by, br, bc in blobs:
                        # wrap so the pattern tiles
                        dx = min(abs(x - bx), TILE - abs(x - bx)); dy = min(abs(y - by), TILE - abs(y - by))
                        if dx * dx + dy * dy * 1.8 < br * br:
                            best = bc
                    if best is not None:
                        col = kw["colours"][best]
                elif painter == "tread":
                    if y % 4 == 0: n -= 14
                elif painter == "links":
                    if y % 4 == 0: n -= 18
                    if x % 16 in (0, 1): n += 10
                elif painter == "glass":
                    n = n // 2 + (18 if (x + y) % 23 < 3 else 0)
                elif painter == "grille":
                    n = 26 if y % 3 == 0 else -6
                else:
                    # painted metal: faint streaks of dust and wear running down the panel
                    if rnd.random() < 0.03: n -= 16
                    n += int(4 * math.sin(x * 1.7 + i))
                px[ox + x, oy + y] = tuple(max(0, min(255, c + n)) for c in col) + (255,)
    save(img, path)


# ---------------------------------------------------------------- cubes
def _piv(p):
    return [round(p[0] * 16, 3), round(p[1] * 16, 3), round(-p[2] * 16, 3)]


# How this file's rotations map onto Bedrock's. pitch: front end up; yaw: front end towards +x; roll: +x side up.
SIGN_PITCH, SIGN_YAW, SIGN_ROLL = -1, 1, -1   # checked against the game

_rnd = random.Random(42)


def B(mat, x, y, z, w, h, d, pitch=0, yaw=0, roll=0, pivot=None):
    """One cube from a corner and a size, optionally turned about one axis through `pivot` (default: its middle)."""
    assert sum(1 for a in (pitch, yaw, roll) if a) <= 1, "turn a cube about one axis only"
    idx = MATS[mat][0]
    tx, ty = (idx % 8) * TILE, (idx // 8) * TILE

    def face(a, b):
        fw = max(1, min(TILE - 2, round(a * 8))); fh = max(1, min(TILE - 2, round(b * 8)))
        return {"uv": [tx + 1 + _rnd.randrange(0, TILE - 1 - fw), ty + 1 + _rnd.randrange(0, TILE - 1 - fh)], "uv_size": [fw, fh]}

    out = {"origin": [round(x * 16, 3), round(y * 16, 3), round(-(z + d) * 16, 3)],
           "size": [round(w * 16, 3), round(h * 16, 3), round(d * 16, 3)],
           "uv": {"north": face(w, h), "south": face(w, h), "east": face(d, h), "west": face(d, h),
                  "up": face(w, d), "down": face(w, d)}}
    p = pivot or (x + w / 2, y + h / 2, z + d / 2)
    if pitch or yaw or roll:
        out["rotation"] = [pitch * SIGN_PITCH, yaw * SIGN_YAW, roll * SIGN_ROLL]
        out["pivot"] = _piv(p)
    out["_sem"] = (mat, x, y, z, w, h, d, pitch, yaw, roll, p)
    return out


def P(mat, x, y, z, w, h, d, pitch=0, yaw=0, roll=0, pivot=None):
    """The same cube on both sides."""
    mp = (-pivot[0], pivot[1], pivot[2]) if pivot else None
    return [B(mat, x, y, z, w, h, d, pitch, yaw, roll, pivot), B(mat, -x - w, y, z, w, h, d, pitch, -yaw, -roll, mp)]


def slope(mat, x, w, z0, y0, z1, y1, t=0.1):
    """A flat plate of width w (from x) running from (z0, y0) to (z1, y1) seen from the side: glacis, windscreen."""
    length = math.hypot(z1 - z0, y1 - y0)
    return B(mat, x, y0 - t, z0, w, t, length, pitch=math.degrees(math.atan2(y1 - y0, z1 - z0)), pivot=(0, y0, z0))


def rod_z(mat, cx, cy, z0, length, dia):
    """A round bar along the vehicle (barrels, tubes): two square bars, one turned 45 degrees."""
    if dia >= 0.5:   # big enough to see the outline: a true octagon from four slabs
        t = dia * 0.4142
        return [B(mat, cx - dia / 2, cy - t / 2, z0, dia, t, length, roll=a) for a in (0, 45, 90, 135)]
    s = dia * 0.86
    return [B(mat, cx - s / 2, cy - s / 2, z0, s, s, length), B(mat, cx - s / 2, cy - s / 2, z0, s, s, length, roll=45)]


def rods_z(mat, cx, cy, z0, length, dia):
    return rod_z(mat, cx, cy, z0, length, dia) + rod_z(mat, -cx, cy, z0, length, dia)


def rod_y(mat, cx, cz, y0, height, dia):
    s = dia * 0.86
    return [B(mat, cx - s / 2, y0, cz - s / 2, s, height, s), B(mat, cx - s / 2, y0, cz - s / 2, s, height, s, yaw=45)]


def disc_x(mat, x0, width, cy, cz, dia):
    """An eight-sided disc lying across the vehicle: four slabs fanned around the axle."""
    t = dia * 0.4142
    return [B(mat, x0, cy - dia / 2, cz - t / 2, width, dia, t, pitch=a) for a in (0, 45, 90, 135)]


def wheel_bone(name, x0, width, cy, cz, dia, hub=STEEL_D, parent="base"):
    inner = x0 + (width * 0.25 if x0 >= 0 else -0.02)
    cubes = disc_x(TYRE, x0, width, cy, cz, dia) + disc_x(hub, x0 - 0.02 if x0 >= 0 else x0, width + 0.02, cy, cz, dia * 0.5)
    return {"name": name, "parent": parent, "pivot": _piv((x0 + width / 2, cy, cz)), "cubes": cubes}


def truck_wheels(zs, x, width, dia, hub=STEEL_D):
    """Wheel bones for both sides at each axle position; x is the inner face of the tyre."""
    bones = []
    for i, z in enumerate(zs, start=1):
        bones.append(wheel_bone(f"wheelL{i}", x, width, dia / 2, z, dia, hub))
        bones.append(wheel_bone(f"wheelR{i}", -x - width, width, dia / 2, z, dia, hub))
    return bones


def running_gear(x, width, z0, z1, count, dia, body):
    """Tracks for both sides: road wheels (which turn), sprocket, idler and the track band around them."""
    cubes, bones = [], []
    top = dia + 0.22
    for sx in (x, -x - width):
        cubes += [B(TRACK, sx, 0, z0 + 0.5, width, 0.1, z1 - z0 - 1.0),                      # run on the ground
                  B(TRACK, sx, top - 0.1, z0 + 0.15, width, 0.1, z1 - z0 - 0.3),            # return run
                  slope(TRACK, sx, width, z1 - 0.55, 0.1, z1 - 0.02, top - 0.5),
                  slope(TRACK, sx, width, z0 + 0.02, top - 0.5, z0 + 0.55, 0.1)]
        cubes += disc_x(STEEL_D, sx + 0.05, width - 0.1, top - 0.42, z1 - 0.3, 0.62)          # drive sprocket
        cubes += disc_x(STEEL_D, sx + 0.05, width - 0.1, top - 0.42, z0 + 0.3, 0.62)          # idler
    span = (z1 - z0 - 1.5) / (count - 1)
    for i in range(count):
        z = z0 + 0.75 + span * i
        for side, sx in (("L", x), ("R", -x - width)):
            bones.append({"name": f"wheel{side}{i + 1}", "parent": "base", "pivot": _piv((sx + width / 2, dia / 2 + 0.1, z)),
                          "cubes": disc_x(TYRE, sx + 0.04, width - 0.08, dia / 2 + 0.1, z, dia)
                                   + disc_x(body, sx - 0.01, width + 0.02, dia / 2 + 0.1, z, dia * 0.45)})
    return cubes, bones


# ---------------------------------------------------------------- shared truck parts
def cab(mat, dark, z0, length, width, floor, height, bonnet=0.0):
    """A cab-over truck cab starting at z0: sloped windscreen, side windows, bumper, lights, mirrors, steps."""
    hw = width / 2
    front = z0 + length
    c = [B(mat, -hw, floor, z0, width, height * 0.5, length),                                  # lower half
         B(mat, -hw, floor + height * 0.5, z0, width, height * 0.5, length - 0.3),              # upper half
         B(mat, -hw + 0.05, floor + height, z0 + 0.1, width - 0.1, 0.08, length - 0.5),         # roof
         slope(mat, -hw, width, front, floor + height * 0.5, front - 0.3, floor + height, 0.06),      # windscreen frame
         slope(GLASS, -hw + 0.12, width * 0.5 - 0.17, front + 0.02, floor + height * 0.55, front - 0.25, floor + height * 0.95, 0.03),
         slope(GLASS, 0.05, width * 0.5 - 0.17, front + 0.02, floor + height * 0.55, front - 0.25, floor + height * 0.95, 0.03),
         B(dark, -hw - 0.03, floor - 0.25, front - 0.12, width + 0.06, 0.32, 0.22),             # bumper
         B(GRILLE, -hw * 0.55, floor + 0.12, front - 0.01, width * 0.55, height * 0.3, 0.04)]   # radiator grille
    c += P(GLASS, hw - 0.02, floor + height * 0.55, z0 + length * 0.3, 0.05, height * 0.36, length * 0.45)
    c += P(WHITE, hw - 0.4, floor - 0.16, front + 0.09, 0.22, 0.14, 0.03)                       # headlights
    c += P(AMBER, hw - 0.14, floor - 0.14, front + 0.09, 0.1, 0.1, 0.03)
    c += P(BLACK, hw + 0.02, floor + height * 0.55, front - 0.55, 0.22, 0.34, 0.05)             # mirrors
    c += P(dark, hw - 0.02, floor - 0.45, z0 + length * 0.35, 0.08, 0.2, 0.5)                   # steps
    return c


def mudguards(mat, x, width, zs, dia):
    c = []
    for z in zs:
        c += P(mat, x - 0.03, dia + 0.08, z - dia * 0.62, width + 0.06, 0.06, dia * 1.24)
    return c


# ---------------------------------------------------------------- anti-aircraft
def zu_23_2():
    """ZU-23-2 in firing position: wheels swung up off the ground, gun on three jacks. Pivots (0,.5,0) / (0,1.15,0)."""
    base = [B(STEEL_D, -0.9, 0.22, -0.18, 1.8, 0.14, 0.36),                       # axle beam
            B(STEEL_D, -0.16, 0.2, -1.2, 0.32, 0.14, 2.6),                        # trail
            B(OLIVE_D, -0.42, 0.3, -0.42, 0.84, 0.2, 0.84)]                       # turntable ring
    base += rod_y(OLIVE_D, 0, 0, 0.3, 0.2, 0.9)
    for (x, z) in ((0, 1.35), (-0.85, -0.3), (0.85, -0.3)):                       # three jack pads
        base += rod_y(STEEL_D, x, z, 0, 0.05, 0.34) + rod_y(STEEL, x, z, 0.05, 0.22, 0.08)
    # road wheels tilted outwards, clear of the ground
    for sx in (1, -1):
        base += [B(TYRE, sx * 1.02 - 0.11, 0.22, -0.4, 0.22, 0.8, 0.8, roll=-sx * 28, pivot=(sx * 0.9, 0.3, 0))]
        base += [B(STEEL_D, sx * 1.02 - 0.13, 0.42, -0.2, 0.26, 0.4, 0.4, roll=-sx * 28, pivot=(sx * 0.9, 0.3, 0))]
    turret = [B(OLIVE, -0.5, 0.5, -0.45, 1.0, 0.12, 0.95)]                         # platform
    turret += P(OLIVE, 0.36, 0.62, -0.3, 0.1, 0.72, 0.62)                          # trunnion cheeks
    turret += P(OLIVE_D, 0.5, 0.72, -0.25, 0.42, 0.5, 0.75)                        # ammunition boxes
    turret += P(OLIVE, 0.52, 1.22, -0.2, 0.38, 0.04, 0.65)
    turret += P(BLACK, 0.24, 0.62, -1.0, 0.36, 0.08, 0.38)                         # two gunners' seats
    turret += P(BLACK, 0.24, 0.7, -1.06, 0.36, 0.4, 0.07)
    turret += P(STEEL_D, 0.38, 0.5, -0.95, 0.08, 0.14, 0.5)
    turret += [B(STEEL_D, -0.06, 0.62, -0.6, 0.12, 0.62, 0.12),                    # sight column
               B(BLACK, -0.14, 1.24, -0.72, 0.28, 0.16, 0.3), B(GLASS, -0.08, 1.28, -0.42, 0.16, 0.08, 0.02)]
    turret += P(STEEL, 0.16, 0.8, -0.72, 0.05, 0.05, 0.3)                          # hand wheels
    barrel = []
    for sx in (0.18, -0.18):
        barrel += [B(OLIVE_D, sx - 0.1, 1.03, -0.55, 0.2, 0.26, 1.15)]             # receiver
        barrel += [B(STEEL_D, sx - 0.07, 1.09, 0.6, 0.14, 0.14, 0.35)]             # barrel sleeve
        barrel += rod_z(STEEL_D, sx, 1.16, 0.95, 1.3, 0.075)
        barrel += rod_z(BLACK, sx, 1.16, 2.25, 0.25, 0.14)                         # flash hider
    barrel += [B(OLIVE_D, -0.3, 1.1, -0.2, 0.6, 0.1, 0.4), B(STEEL_D, -0.03, 1.29, -0.5, 0.06, 0.05, 0.9)]
    return dict(base=base, turret=turret, barrel=barrel, bones=[])


def gepard():
    """Flakpanzer Gepard: Leopard 1 hull, tall slab turret, guns outside, two radars. Pivots (0,2,-.3) / (0,2.8,-.1)."""
    gear, bones = running_gear(1.05, 0.55, -3.7, 3.7, 7, 0.72, NATO_D)
    base = gear + [
        B(NATO, -1.05, 0.45, -3.6, 2.1, 0.75, 7.0),                                # lower hull
        B(NATO, -1.62, 1.05, -3.75, 3.24, 0.55, 6.6),                              # upper hull over the tracks
        B(NATO, -1.62, 1.6, -3.4, 3.24, 0.4, 5.6),
        slope(NATO, -1.6, 3.2, 2.2, 2.0, 3.85, 1.25),                              # sloped glacis
        slope(NATO, -1.05, 2.1, 3.0, 0.5, 3.85, 1.25),                             # lower nose
        B(GRILLE, -1.2, 2.0, -3.3, 2.4, 0.03, 1.5),                                # engine deck louvres
        B(NATO_D, -1.6, 1.3, -3.85, 3.2, 0.6, 0.12),                               # rear plate
        B(STEEL_D, -1.3, 1.45, -3.92, 0.5, 0.25, 0.1), B(STEEL_D, 0.8, 1.45, -3.92, 0.5, 0.25, 0.1)]
    base += P(NATO_D, 1.6, 0.75, -3.6, 0.06, 0.55, 7.0)                             # side skirts
    base += P(WHITE, 1.2, 1.5, 3.42, 0.2, 0.14, 0.04) + P(STEEL_D, 0.4, 1.9, 2.4, 0.5, 0.12, 0.4)
    base += P(BLACK, 1.45, 1.62, -3.3, 0.14, 0.14, 1.6)                             # tow cables / tools
    turret = [
        B(NATO, -1.05, 2.0, -1.9, 2.1, 1.25, 3.0),                                 # turret body
        slope(NATO, -1.0, 2.0, 1.75, 2.0, 1.1, 3.25),                              # sloped front
        B(NATO, -1.0, 2.0, 1.1, 2.0, 0.5, 0.65),
        B(NATO_D, -1.05, 3.25, -1.9, 2.1, 0.06, 2.9),
        B(NATO, -0.85, 2.1, -2.4, 1.7, 0.9, 0.5),                                  # rear bustle
        B(NATO_D, -0.55, 3.3, -0.3, 0.5, 0.18, 0.5), B(NATO_D, 0.15, 3.3, 0.2, 0.45, 0.16, 0.45),   # hatches
        B(BLACK, -0.5, 3.3, 0.5, 0.3, 0.2, 0.25), B(GLASS, -0.47, 3.34, 0.75, 0.24, 0.12, 0.02),    # periscopes
        # tracking radar: a drum on the turret front
        B(NATO_D, -0.3, 2.35, 1.25, 0.6, 0.6, 0.3)]
    turret += rod_z(RADOME, 0, 2.65, 1.5, 0.35, 0.95)
    turret += rod_z(NATO_D, 0, 2.65, 1.3, 0.22, 1.05)
    # search radar: folding mast at the back carrying a wide curved aerial
    turret += [B(NATO_D, -0.12, 3.25, -2.1, 0.24, 0.75, 0.24),
               B(NATO_D, -0.95, 3.95, -2.2, 1.9, 0.5, 0.12, pitch=-10),
               B(STEEL_D, -0.85, 3.98, -2.08, 1.7, 0.44, 0.03, pitch=-10, pivot=(0, 4.2, -2.14)),
               B(NATO_D, -0.2, 4.45, -2.25, 0.4, 0.12, 0.3)]
    turret += P(NATO_D, 1.05, 2.45, -0.75, 0.2, 0.7, 1.3)                           # gun trunnion housings
    turret += P(STEEL_D, 0.85, 2.9, -2.2, 0.1, 0.1, 0.5) + P(STEEL_D, 1.02, 3.0, -1.6, 0.05, 0.6, 0.05)   # smoke dischargers, aerials
    barrel = []
    for sx in (1, -1):
        cx = 1.31 * sx
        barrel += [B(NATO, cx - 0.2, 2.42, -1.0, 0.4, 0.7, 1.9),                   # gun pod
                   B(NATO_D, cx - 0.23, 2.5, -0.6, 0.46, 0.5, 0.9),
                   B(STEEL_D, cx - 0.12, 2.62, 0.9, 0.24, 0.24, 0.5)]
        barrel += rod_z(STEEL_D, cx, 2.74, 1.4, 1.75, 0.11)
        barrel += rod_z(BLACK, cx, 2.74, 3.1, 0.3, 0.2)                            # muzzle velocity gauge
        barrel += [B(STEEL_D, cx - 0.04, 2.84, 3.1, 0.08, 0.1, 0.3)]
    return dict(base=base, turret=turret, barrel=barrel, bones=bones)


def pantsir():
    """Pantsir-S1 on its 8x8 KamAZ. Pivots (0,2.2,-1.5) / (0,3.1,-1.5)."""
    zs = [-4.2, -2.8, 1.2, 2.6]
    base = cab(RUS_CAMO, RUS_D, 3.0, 2.0, 2.5, 1.35, 1.65)
    base += [B(RUS_D, -0.55, 0.75, -5.0, 1.1, 0.5, 9.9),                           # chassis rails
             B(RUS, -1.25, 1.25, -5.0, 2.5, 0.25, 7.9),                            # flatbed
             B(RUS_CAMO, -1.25, 1.5, 0.6, 2.5, 1.45, 2.2),                         # crew and power module
             B(GRILLE, -1.0, 1.9, 0.57, 2.0, 0.7, 0.04),
             B(RUS_D, -1.27, 1.5, 1.6, 2.54, 1.45, 0.06),
             B(RUS_CAMO, -1.2, 1.5, -4.9, 2.4, 0.7, 5.4),                          # turret plinth
             B(RUS_D, -0.75, 1.2, 4.2, 1.5, 0.3, 0.7),
             B(STEEL_D, -1.0, 0.75, -5.05, 2.0, 0.3, 0.1)]
    base += P(STEEL_D, 0.6, 0.5, 0.0, 0.5, 0.6, 0.9)                                # fuel tanks
    base += P(RUS_D, 1.05, 0.2, -1.2, 0.2, 1.1, 0.2) + P(STEEL_D, 0.95, 0.0, -1.35, 0.4, 0.2, 0.5)   # stabiliser jacks
    base += P(RUS_D, 1.05, 0.2, -4.95, 0.2, 1.1, 0.2) + P(RED, 1.0, 0.85, -5.07, 0.2, 0.12, 0.03)
    base += mudguards(RUS_D, 0.85, 0.45, zs, 1.2)
    turret = [B(RUS, -0.9, 2.2, -2.75, 1.8, 0.5, 2.5),                             # turret ring housing
              B(RUS, -0.7, 2.7, -2.6, 1.4, 1.5, 2.0),                              # central tower
              B(RUS_D, -0.75, 4.2, -2.6, 1.5, 0.08, 2.0),
              # phased-array tracking radar on the tower front, leaning back
              B(RUS_D, -0.55, 2.9, -0.7, 1.1, 1.3, 0.25),
              B(BLACK, -0.3, 2.4, -0.3, 0.6, 0.45, 0.25), B(GLASS, -0.22, 2.48, -0.05, 0.44, 0.28, 0.02),   # optical sight
              # search radar: flat panel on top at the back
              B(RUS_D, -0.15, 4.28, -2.35, 0.3, 0.35, 0.3),
              B(RUS_D, -0.95, 4.6, -2.3, 1.9, 0.75, 0.16, pitch=-8),
              B(RADOME, -0.88, 4.66, -2.14, 1.76, 0.63, 0.03, pitch=-8, pivot=(0, 4.97, -2.22))]
    turret += rod_z(RUS_D, 0, 3.55, -0.5, 0.3, 1.5) + rod_z(AMBER, 0, 3.55, -0.22, 0.06, 1.36)   # round tracking radar
    barrel = []
    for sx in (1, -1):
        # six missile tubes a side, in two rows of three, plus a twin 30 mm gun below them
        barrel += [B(RUS_D, sx * 0.95 - 0.25, 2.6, -2.0, 0.5, 1.0, 1.0)]           # arm that carries them
        for i in range(3):
            for j in range(2):
                cx, cy = sx * (0.98 + i * 0.2), 3.02 + j * 0.42 + (0.1 if i == 1 else 0)
                barrel += rod_z(RUS, cx, cy, -3.2, 3.2, 0.2)
                barrel += rod_z(BLACK, cx, cy, -0.02, 0.05, 0.16)
        barrel += [B(RUS_D, sx * 1.18 - 0.36, 2.86, -2.6, 0.72, 0.9, 0.08), B(RUS_D, sx * 1.18 - 0.36, 2.86, -0.9, 0.72, 0.9, 0.08)]
        barrel += [B(STEEL_D, sx * 0.85 - 0.14, 2.42, -1.6, 0.28, 0.3, 1.5)]       # gun receiver
        for off in (-0.06, 0.06):
            barrel += rod_z(STEEL_D, sx * 0.85 + off, 2.55, -0.1, 1.55, 0.07)
        barrel += rod_z(BLACK, sx * 0.85, 2.55, 1.4, 0.3, 0.2)
    return dict(base=base, turret=turret, barrel=barrel, bones=truck_wheels(zs, 0.85, 0.42, 1.2))


def patriot():
    """M901 Patriot launching station on its semi-trailer. Pivots (0,1.3,-1.0) / (0,1.9,-2.0)."""
    zs = [-3.5, -2.1]
    base = [B(TAN_D, -1.2, 0.95, -4.6, 2.4, 0.3, 9.2),                             # trailer bed
            B(TAN_D, -0.5, 0.7, -4.4, 1.0, 0.25, 5.0),
            B(TAN, -1.15, 1.25, 2.6, 2.3, 1.25, 1.9),                              # generator and electronics
            B(GRILLE, -0.9, 1.5, 4.5, 1.8, 0.8, 0.04), B(TAN_D, -1.17, 1.25, 3.4, 2.34, 1.25, 0.08),
            B(TAN_D, -0.2, 0.3, 4.4, 0.4, 0.7, 0.4), B(STEEL_D, -0.3, 0.0, 4.3, 0.6, 0.3, 0.6),   # landing leg
            B(TAN_D, -0.9, 1.25, -0.2, 1.8, 0.1, 2.6),
            B(WHITE, 0.2, 2.5, 2.9, 0.08, 1.4, 0.08)]                              # data-link mast
    for z in (1.4, -4.3):                                                          # four outriggers
        base += P(TAN_D, 1.2, 0.9, z, 0.5, 0.14, 0.2) + P(TAN_D, 1.6, 0.15, z, 0.14, 0.9, 0.2) + P(STEEL_D, 1.42, 0.0, z - 0.15, 0.5, 0.15, 0.5)
    base += mudguards(TAN_D, 0.9, 0.42, zs, 1.1)
    turret = [B(TAN_D, -1.1, 1.3, -2.3, 2.2, 0.2, 2.6)]
    turret += rod_y(TAN_D, 0, -1.0, 1.25, 0.3, 1.6)
    turret += P(TAN, 0.95, 1.5, -2.3, 0.18, 0.75, 0.9)                              # elevation trunnions
    barrel = [B(TAN_D, -1.05, 1.62, -4.1, 2.1, 0.12, 6.1),                         # launcher frame
              B(TAN_D, -1.05, 1.7, -4.15, 2.1, 2.05, 0.14)]
    barrel += P(STEEL_D, 0.5, 1.4, -1.6, 0.14, 0.14, 1.9, pitch=18, pivot=(0, 1.5, -1.6))   # elevating rams
    for sx in (-1.02, 0.04):
        for sy in (1.76, 2.76):
            barrel += [B(TAN, sx, sy, -4.0, 0.98, 0.94, 6.0)]                      # canister
            for z in (-3.6, -2.2, -0.8, 0.6, 1.7):                                 # stiffening frames
                barrel += [B(TAN_D, sx - 0.02, sy - 0.02, z, 1.02, 0.98, 0.1)]
            barrel += [B(TAN_D, sx + 0.07, sy + 0.07, 1.98, 0.84, 0.8, 0.04),       # front cover
                       B(BLACK, sx + 0.3, sy + 0.3, 2.01, 0.38, 0.34, 0.02)]
    return dict(base=base, turret=turret, barrel=barrel, bones=truck_wheels(zs, 0.9, 0.4, 1.1, TAN_D))


# ---------------------------------------------------------------- artillery
def himars():
    """M142 HIMARS: armoured FMTV cab, launcher cage with one six-rocket pod. Pivots (0,1.2,-1.5) / (0,1.7,-2.5)."""
    zs = [-2.9, -1.6, 1.8]
    f, h = 1.25, 1.5
    base = [B(US_D, -0.5, 0.7, -3.5, 1.0, 0.45, 6.6),                              # chassis
            B(US, -1.2, 1.1, -3.5, 2.4, 0.12, 4.6),                                # deck
            # angular armoured cab
            B(US, -1.2, f, 1.3, 2.4, 0.8, 2.15),
            B(US, -1.2, f + 0.8, 1.3, 2.4, 0.7, 1.7),
            B(US_D, -1.15, f + h, 1.35, 2.3, 0.07, 1.6),
            slope(US, -1.2, 2.4, 3.45, f + 0.8, 3.0, f + h, 0.08),                 # sloped armoured front
            slope(GLASS, -1.0, 0.9, 3.46, f + 0.88, 3.08, f + h - 0.1, 0.04),
            slope(GLASS, 0.1, 0.9, 3.46, f + 0.88, 3.08, f + h - 0.1, 0.04),
            B(GRILLE, -0.9, f + 0.1, 3.44, 1.8, 0.55, 0.04),
            B(US_D, -1.25, 0.75, 3.4, 2.5, 0.4, 0.22),                             # bumper
            B(US_D, -1.2, 1.2, 0.6, 2.4, 1.0, 0.65), B(BLACK, -0.9, 1.3, 0.75, 0.5, 0.9, 0.4),   # spare wheel, stowage
            B(STEEL_D, 0.75, 1.3, 0.72, 0.18, 1.3, 0.18)]                          # exhaust stack
    base += P(GLASS, 1.19, f + 0.9, 1.7, 0.04, 0.42, 0.9) + P(GRILLE, 1.2, f + 0.85, 1.65, 0.03, 0.5, 1.0)   # louvred side windows
    base += P(WHITE, 0.8, 0.85, 3.62, 0.22, 0.16, 0.03) + P(BLACK, 1.22, f + 0.9, 2.9, 0.2, 0.36, 0.05)
    base += P(US_D, 1.18, 0.75, 1.5, 0.08, 0.5, 0.45) + P(US_D, 0.6, 0.55, -0.4, 0.55, 0.55, 1.0)   # steps, tanks
    base += mudguards(US_D, 0.85, 0.4, zs, 1.0)
    turret = [B(US_D, -1.0, 1.2, -2.6, 2.0, 0.3, 2.3)]
    turret += rod_y(US_D, 0, -1.5, 1.15, 0.2, 1.7)
    turret += P(US, 0.95, 1.4, -2.8, 0.2, 0.6, 1.0)
    barrel = [B(US, -1.2, 1.55, -3.4, 2.4, 0.14, 4.6),                             # cage floor
              B(US, -1.2, 2.72, -3.4, 2.4, 0.12, 4.6)]                             # cage roof
    barrel += P(US, 1.08, 1.6, -3.4, 0.12, 1.2, 4.6)                                # cage sides
    barrel += P(US_D, 1.2, 1.9, -3.0, 0.04, 0.6, 3.6)
    barrel += [B(TAN_D, -1.05, 1.7, -3.3, 2.1, 1.0, 4.35),                         # the rocket pod itself
               B(US_D, -1.07, 1.68, -1.2, 2.14, 1.04, 0.1), B(US_D, -1.07, 1.68, 0.3, 2.14, 1.04, 0.1),
               B(STEEL_D, -0.25, 2.84, -2.6, 0.5, 0.12, 2.4)]                      # reloading boom
    for i in range(3):
        for j in range(2):
            cx, cy = -0.68 + i * 0.68, 1.96 + j * 0.5
            barrel += rod_z(BLACK, cx, cy, 1.02, 0.06, 0.46)
            barrel += rod_z(STEEL_D, cx, cy, -3.34, 0.06, 0.46)
    return dict(base=base, turret=turret, barrel=barrel, bones=truck_wheels(zs, 0.85, 0.38, 1.0, US_D))


def mortar_120():
    """120 mm mortar. Pivots (0,.1,-.1) / (0,.22,-.1)."""
    base = rod_y(STEEL_D, 0, -0.1, 0, 0.08, 1.0) + rod_y(STEEL, 0, -0.1, 0.08, 0.08, 0.3)
    base += [B(STEEL_D, -0.5, 0.0, -0.14, 1.0, 0.12, 0.08), B(STEEL_D, -0.04, 0.0, -0.6, 0.08, 0.12, 1.0)]   # baseplate ribs
    base += [B(OLIVE_D, 0.55, 0, -0.5, 0.35, 0.3, 0.5), B(OLIVE, 0.57, 0.3, -0.48, 0.31, 0.03, 0.46)]        # ammunition box
    turret = []
    # bipod: two splayed legs meeting at a collar on the tube, with a cross-levelling bar
    for sx in (1, -1):
        turret += [B(STEEL, sx * 0.02 - 0.025, 0.0, 0.62, 0.05, 1.05, 0.05, roll=-sx * 22, pivot=(sx * 0.02, 1.0, 0.64))]
        turret += [B(STEEL_D, sx * 0.4 - 0.08, 0.0, 0.56, 0.16, 0.03, 0.16)]
    turret += [B(STEEL, -0.22, 0.55, 0.63, 0.44, 0.04, 0.04), B(STEEL_D, -0.07, 0.92, 0.56, 0.14, 0.14, 0.16)]
    barrel = rod_z(OLIVE_D, 0, 0.22, -0.1, 1.7, 0.17)
    barrel += rod_z(STEEL_D, 0, 0.22, -0.16, 0.14, 0.24)                            # breech cap
    barrel += rod_z(STEEL_D, 0, 0.22, 1.5, 0.1, 0.21)                               # muzzle band
    barrel += rod_z(STEEL_D, 0, 0.22, 0.72, 0.12, 0.22)                             # bipod collar
    barrel += [B(BLACK, 0.1, 0.24, 0.5, 0.1, 0.18, 0.12), B(GLASS, 0.12, 0.42, 0.54, 0.06, 0.04, 0.04)]      # sight
    return dict(base=base, turret=turret, barrel=barrel, bones=[])


def m109():
    """M109A6 Paladin. Pivots (0,1.7,-.9) / (0,2.3,.9); the muzzle is 5.2 ahead of the barrel pivot."""
    gear, bones = running_gear(1.05, 0.5, -3.0, 3.0, 7, 0.62, US_D)
    base = gear + [
        B(US, -1.05, 0.4, -2.95, 2.1, 0.6, 5.7),
        B(US, -1.57, 0.95, -3.1, 3.14, 0.75, 5.3),                                 # hull
        slope(US, -1.55, 3.1, 2.2, 1.7, 3.2, 1.05),                                # glacis
        slope(US, -1.05, 2.1, 2.6, 0.45, 3.2, 1.05),
        B(GRILLE, 0.2, 1.7, 1.1, 1.2, 0.03, 0.9),                                  # engine louvres, front right
        B(US_D, -1.3, 1.7, 1.3, 0.7, 0.12, 0.6),                                   # driver's hatch
        B(US_D, -1.55, 1.0, -3.2, 3.1, 0.65, 0.12),
        B(US_D, -0.45, 1.05, -3.3, 0.9, 0.55, 0.1)]                                # rear door
    base += P(US_D, 1.55, 0.72, -3.0, 0.05, 0.3, 6.0)
    base += P(WHITE, 1.1, 1.35, 2.95, 0.2, 0.14, 0.04) + P(US_D, 1.2, 0.5, -3.35, 0.3, 0.5, 0.3)   # lights, spades
    # travel lock for the barrel on the glacis
    base += [B(US_D, -0.3, 1.5, 2.6, 0.08, 0.5, 0.08), B(US_D, 0.22, 1.5, 2.6, 0.08, 0.5, 0.08)]
    turret = [
        B(US, -1.45, 1.7, -3.0, 2.9, 1.2, 3.3),                                    # big turret set well back
        slope(US, -1.4, 2.8, 0.9, 1.75, 0.3, 2.9),                                 # sloped front
        B(US, -1.4, 1.7, 0.3, 2.8, 0.45, 0.55),
        B(US_D, -1.45, 2.9, -3.0, 2.9, 0.06, 3.25),
        B(US_D, -1.5, 1.85, -3.75, 3.0, 0.75, 0.75),                               # bustle rack
        B(GRILLE, -1.4, 2.6, -3.7, 2.8, 0.03, 0.65),
        B(US_D, 0.5, 2.96, -1.9, 0.75, 0.25, 0.75), B(GLASS, 0.6, 3.05, -1.14, 0.55, 0.1, 0.02),   # commander's cupola
        B(US_D, -1.1, 2.96, -1.2, 0.7, 0.1, 0.7),
        B(STEEL_D, 1.3, 2.96, -2.7, 0.05, 1.5, 0.05),                              # aerial
        B(US_D, -1.52, 2.0, -2.2, 0.08, 0.6, 1.6), B(US_D, 1.44, 2.0, -2.2, 0.08, 0.6, 1.6)]       # stowage panels
    # .50 cal on the cupola
    turret += [B(BLACK, 0.8, 3.22, -1.9, 0.12, 0.14, 0.7)] + rod_z(BLACK, 0.86, 3.3, -1.2, 0.9, 0.05)
    barrel = [B(US_D, -0.5, 1.95, 0.5, 1.0, 0.7, 0.75),                            # mantlet
              B(US, -0.35, 2.05, 1.2, 0.7, 0.5, 0.5)]
    barrel += rod_z(US, 0, 2.3, 1.7, 3.9, 0.24)
    barrel += rod_z(US_D, 0, 2.3, 1.7, 0.9, 0.34)                                   # recoil sleeve
    barrel += rod_z(US, 0, 2.3, 3.6, 0.8, 0.38)                                     # fume extractor
    barrel += rod_z(US_D, 0, 2.3, 5.55, 0.55, 0.4)                                  # muzzle brake
    barrel += [B(BLACK, -0.24, 2.22, 5.7, 0.48, 0.16, 0.1), B(BLACK, -0.24, 2.22, 5.9, 0.48, 0.16, 0.1)]
    return dict(base=base, turret=turret, barrel=barrel, bones=bones)


# ---------------------------------------------------------------- missile launchers
def tomahawk_launcher():
    """Trailer launcher: a container that tips up to vertical about its rear end. Pivot (0,1.2,-3.9)."""
    zs = [-3.7, -2.5]
    base = [B(US_D, -1.2, 0.9, -4.5, 2.4, 0.3, 9.0),                               # trailer bed
            B(US_D, -0.45, 0.6, -4.4, 0.9, 0.3, 6.0),
            B(US, -1.1, 1.2, 3.5, 2.2, 1.2, 0.95),                                 # power unit
            B(GRILLE, -0.8, 1.45, 4.44, 1.6, 0.7, 0.04),
            B(US_D, -0.12, 0.6, 4.2, 0.24, 0.3, 0.6),                              # kingpin
            B(STEEL_D, -1.2, 0.95, -4.56, 2.4, 0.2, 0.08)]
    base += P(STEEL_D, 0.8, 0.0, 3.1, 0.2, 0.9, 0.2) + P(STEEL_D, 0.7, 0.0, 3.0, 0.4, 0.08, 0.4)   # landing legs
    base += P(US_D, 1.2, 0.2, -4.4, 0.16, 0.8, 0.16) + P(STEEL_D, 1.08, 0.0, -4.52, 0.4, 0.08, 0.4)
    base += P(RED, 0.8, 1.0, -4.6, 0.25, 0.12, 0.03)
    base += mudguards(US_D, 0.85, 0.4, zs, 1.0)
    barrel = [B(US, -1.1, 1.2, -3.9, 2.2, 1.9, 7.3)]                               # the container
    for z in (-3.9, -2.7, -1.5, -0.3, 0.9, 2.1, 3.25):                             # frames and corrugation
        barrel += [B(US_D, -1.14, 1.18, z, 2.28, 1.94, 0.14)]
    barrel += P(US_D, 1.1, 2.1, -3.8, 0.03, 0.1, 7.1) + P(US_D, 1.1, 1.6, -3.8, 0.03, 0.1, 7.1) + P(US_D, 1.1, 2.6, -3.8, 0.03, 0.1, 7.1)
    for i in range(2):
        for j in range(2):                                                         # four cell hatches at the top end
            barrel += [B(STEEL, -0.95 + i * 1.0, 1.35 + j * 0.85, 3.4, 0.9, 0.75, 0.04),
                       B(STEEL_D, -0.85 + i * 1.0, 1.45 + j * 0.85, 3.43, 0.7, 0.55, 0.02)]
    barrel += P(STEEL, 0.7, 1.25, -2.9, 0.12, 0.12, 2.6, pitch=-12, pivot=(0, 1.3, -1.6))   # hydraulic rams
    return dict(base=base, turret=[], barrel=barrel, bones=truck_wheels(zs, 0.85, 0.38, 1.0, US_D))


def iskander_launcher():
    """9P78-1 Iskander launcher on its 8x8 chassis; the missile and its cradle tip up about (0,2.25,-5.5)."""
    zs = [-5.5, -3.9, 1.7, 3.3]
    base = [B(RUS_D, -0.7, 0.8, -6.4, 1.4, 0.6, 12.6),                             # chassis
            B(RUS, -1.5, 1.4, -6.5, 3.0, 0.8, 10.7),                               # lower body
            # wide, low cab right at the front
            B(RUS, -1.5, 1.4, 4.2, 3.0, 0.9, 2.3),
            B(RUS, -1.5, 2.3, 4.2, 3.0, 0.8, 1.9),
            B(RUS_D, -1.45, 3.1, 4.25, 2.9, 0.07, 1.8),
            slope(RUS, -1.5, 3.0, 6.5, 2.3, 6.1, 3.1, 0.08),
            slope(GLASS, -1.3, 1.15, 6.52, 2.38, 6.18, 3.02, 0.04),
            slope(GLASS, 0.15, 1.15, 6.52, 2.38, 6.18, 3.02, 0.04),
            B(RUS_D, -1.52, 1.0, 6.42, 3.04, 0.45, 0.16),                          # bumper
            B(GRILLE, -1.3, 2.4, 3.5, 2.6, 0.75, 0.6),                             # engine bay behind the cab
            B(RUS, -1.5, 2.2, 3.4, 3.0, 0.2, 0.75),
            B(RUS_D, -1.5, 2.2, -6.5, 3.0, 0.9, 0.3)]                              # rear wall
    base += P(RUS, 1.2, 2.2, -6.2, 0.3, 0.9, 9.6)                                   # side walls of the missile bay
    base += P(RUS_D, 0.55, 3.1, -6.2, 0.95, 0.06, 9.6, roll=-74, pivot=(1.5, 3.1, 0))   # bay doors standing open
    base += P(GLASS, 1.49, 2.4, 4.7, 0.04, 0.5, 1.1) + P(WHITE, 1.0, 1.1, 6.59, 0.25, 0.16, 0.03)
    base += P(BLACK, 1.52, 2.5, 5.9, 0.22, 0.36, 0.05) + P(RUS_D, 1.3, 0.3, -1.2, 0.2, 1.1, 0.25)   # mirrors, jacks
    base += P(STEEL_D, 1.2, 0.0, -1.35, 0.4, 0.1, 0.55) + P(RUS_D, 0.9, 0.9, -1.0, 0.6, 0.5, 1.6)
    base += mudguards(RUS_D, 1.0, 0.52, zs, 1.3)
    barrel = [B(STEEL_D, -0.6, 2.2, -5.5, 1.2, 0.1, 5.4),                          # erector cradle
              B(STEEL_D, -0.65, 2.2, -5.5, 0.1, 0.5, 5.4), B(STEEL_D, 0.55, 2.2, -5.5, 0.1, 0.5, 5.4),
              B(STEEL_D, -0.6, 2.25, -1.2, 1.2, 0.6, 0.12), B(STEEL_D, -0.6, 2.25, -3.6, 1.2, 0.6, 0.12)]
    barrel += rod_z(RUS, 0, 2.78, -5.5, 5.0, 0.92)                                  # the missile
    barrel += rod_z(RUS, 0, 2.78, -0.5, 1.3, 0.78)
    barrel += rod_z(RUS, 0, 2.78, 0.8, 0.9, 0.52)
    barrel += rod_z(RUS_D, 0, 2.78, 1.7, 0.45, 0.26)
    barrel += rod_z(BLACK, 0, 2.78, -5.62, 0.14, 0.66)
    barrel += [B(RUS_D, -0.8, 2.75, -5.3, 1.6, 0.06, 0.6), B(RUS_D, -0.03, 2.0, -5.3, 0.06, 1.56, 0.6)]   # tail fins
    return dict(base=base, turret=[], barrel=barrel, bones=truck_wheels(zs, 1.0, 0.5, 1.3, RUS_D))


# ---------------------------------------------------------------- electronic warfare
def jammer():
    """Field EW station: an equipment shelter on jacks with a tall telescopic aerial mast."""
    base = [B(US, -1.4, 0.35, -1.4, 2.8, 2.2, 2.8),                                # shelter
            B(US_D, -1.45, 0.3, -1.45, 2.9, 0.1, 2.9), B(US_D, -1.45, 2.5, -1.45, 2.9, 0.1, 2.9),
            B(US_D, -0.45, 0.5, -1.44, 0.9, 1.8, 0.05), B(STEEL, 0.25, 1.3, -1.47, 0.08, 0.2, 0.04),   # door and handle
            B(GRILLE, -1.44, 1.6, -0.5, 0.05, 0.6, 1.0),                           # air conditioner
            B(STEEL_D, -1.7, 1.5, -0.6, 0.28, 0.8, 1.2),
            B(STEEL_D, 0.6, 2.6, 0.5, 0.6, 0.3, 0.6),                              # roof boxes
            B(BLACK, 1.42, 0.6, 0.2, 0.06, 0.9, 0.9),                              # cable panel
            B(STEEL_D, 0.9, 0.0, -2.1, 0.6, 0.5, 0.5), B(GRILLE, 0.95, 0.1, -2.12, 0.5, 0.3, 0.03)]   # generator
    for sx in (1.2, -1.4):
        for sz in (1.2, -1.4):
            base += [B(STEEL_D, sx, 0.0, sz, 0.2, 0.35, 0.2), B(STEEL_D, sx - 0.1, 0.0, sz - 0.1, 0.4, 0.06, 0.4)]
    # mast in three telescoping sections
    base += rod_y(STEEL_D, 0, 0, 2.6, 2.4, 0.3) + rod_y(STEEL, 0, 0, 5.0, 2.2, 0.22) + rod_y(STEEL_D, 0, 0, 7.2, 1.6, 0.14)
    for a in (0, 90):                                                              # guy-wire collars and cross arms
        base += [B(STEEL_D, -1.5, 7.0 + a / 90 * 1.1, -0.04, 3.0, 0.08, 0.08, yaw=a)]
    for (x, z, yaw) in ((1.45, 0, 0), (-1.45, 0, 0), (0, 1.45, 90), (0, -1.45, 90)):
        base += [B(RADOME, x - 0.06, 6.55 + (1.1 if yaw else 0), z - 0.3, 0.12, 0.95, 0.6, yaw=yaw)]   # panel aerials
    base += rod_y(RADOME, 0, 0, 8.8, 0.5, 0.36)
    base += [B(RED, -0.07, 9.3, -0.07, 0.14, 0.14, 0.14)]
    for (x, z) in ((0.5, 0.5), (-0.5, 0.5), (0.5, -0.5), (-0.5, -0.5)):
        base += [B(STEEL_D, x - 0.02, 8.2, z - 0.02, 0.04, 0.9, 0.04)]             # whip aerials
    return dict(base=base, turret=[], barrel=[], bones=[])


def trench_jammer():
    """Dugout jammer: a rugged case on short legs with a cluster of stub aerials."""
    base = [B(OLIVE_D, -0.36, 0.08, -0.3, 0.72, 0.5, 0.6),
            B(OLIVE, -0.38, 0.5, -0.32, 0.76, 0.1, 0.64),                          # lid
            B(BLACK, -0.38, 0.44, -0.33, 0.76, 0.03, 0.66),
            B(STEEL_D, -0.15, 0.6, -0.04, 0.3, 0.05, 0.08),                        # carry handle
            B(STEEL, -0.3, 0.3, -0.33, 0.08, 0.1, 0.03), B(STEEL, 0.22, 0.3, -0.33, 0.08, 0.1, 0.03),   # latches
            B(GRILLE, 0.36, 0.2, -0.2, 0.03, 0.2, 0.4),
            B(BLACK, -0.6, 0.0, -0.2, 0.2, 0.22, 0.4), B(RED, -0.55, 0.22, -0.1, 0.04, 0.04, 0.04)]   # battery
    for (x, z) in ((0.3, 0.24), (-0.3, 0.24), (0.3, -0.24), (-0.3, -0.24)):
        base += [B(STEEL_D, x - 0.03, 0.0, z - 0.03, 0.06, 0.08, 0.06)]
    for i, (x, z, hgt) in enumerate(((0.25, 0.18, 0.9), (-0.25, 0.18, 0.7), (0.25, -0.18, 0.55), (-0.25, -0.18, 1.2), (0, 0.2, 0.45))):
        base += rod_y(BLACK, x, z, 0.6, 0.12, 0.07) + rod_y(BLACK if i % 2 else STEEL_D, x, z, 0.72, hgt, 0.035)
    base += [B(RED, -0.27, 1.92, -0.2, 0.04, 0.04, 0.04)]
    return dict(base=base, turret=[], barrel=[], bones=[])


def krasukha():
    """Krasukha-4: 8x8 truck carrying an equipment cabin and a large dish aerial raised at the back."""
    zs = [-4.7, -3.3, 1.5, 2.9]
    base = cab(RUS, RUS_D, 3.5, 2.0, 2.5, 1.35, 1.65)
    base += [B(RUS_D, -0.55, 0.75, -5.5, 1.1, 0.5, 10.9),
             B(RUS, -1.25, 1.25, -5.5, 2.5, 0.2, 8.9),
             B(RUS, -1.25, 1.45, -1.9, 2.5, 1.9, 5.2),                             # equipment cabin
             B(RUS_D, -1.28, 3.35, -1.9, 2.56, 0.08, 5.2),
             B(GRILLE, -1.27, 2.2, 1.6, 0.04, 0.7, 1.2), B(GRILLE, 1.23, 2.2, 1.6, 0.04, 0.7, 1.2),
             B(RUS_D, -1.27, 1.6, -0.6, 0.04, 1.6, 0.8),                           # door
             B(STEEL_D, -0.5, 3.43, 0.5, 1.0, 0.35, 1.6),                          # roof units
             B(RUS_D, -1.0, 1.45, -5.3, 2.0, 0.5, 3.2),                            # aerial pedestal
             B(STEEL_D, -1.0, 0.75, -5.55, 2.0, 0.3, 0.1)]
    base += rod_y(RUS_D, 0, -3.9, 1.95, 1.9, 0.7)                                   # mast
    base += [B(RUS_D, -0.4, 3.7, -4.3, 0.8, 0.8, 0.8)]                             # yoke
    # three round dishes facing rearwards: a big one in the middle and one on each side, with their feeds
    base += [B(RUS_D, -2.3, 4.0, -4.1, 4.6, 0.3, 0.3)]
    for cx, dia in ((0, 2.5), (2.2, 2.1), (-2.2, 2.1)):
        base += rod_z(RADOME, cx, 4.3, -4.5, 0.12, dia) + rod_z(RUS_D, cx, 4.3, -4.38, 0.14, dia * 0.5)
        base += [B(STEEL_D, cx - 0.03, 4.27, -5.2, 0.06, 0.06, 0.7), B(BLACK, cx - 0.1, 4.2, -5.3, 0.2, 0.2, 0.12)]
    base += rod_y(STEEL_D, 0, -4.0, 4.7, 1.3, 0.1) + [B(BLACK, -0.25, 5.9, -4.2, 0.5, 0.4, 0.4)]
    base += P(STEEL_D, 0.6, 0.5, 0.3, 0.5, 0.6, 0.9)
    base += P(RUS_D, 1.05, 0.2, -1.8, 0.2, 1.1, 0.2) + P(STEEL_D, 0.95, 0.0, -1.95, 0.4, 0.2, 0.5)
    base += P(RUS_D, 1.05, 0.2, -5.45, 0.2, 1.1, 0.2) + P(RED, 1.0, 0.85, -5.57, 0.2, 0.12, 0.03)
    base += mudguards(RUS_D, 0.85, 0.45, zs, 1.2)
    return dict(base=base, turret=[], barrel=[], bones=truck_wheels(zs, 0.85, 0.42, 1.2))


MODELS = {"zu_23_2": zu_23_2, "gepard": gepard, "pantsir": pantsir, "patriot": patriot, "himars": himars,
          "mortar_120": mortar_120, "m109": m109, "tomahawk_launcher": tomahawk_launcher,
          "iskander_launcher": iskander_launcher, "jammer": jammer, "trench_jammer": trench_jammer, "krasukha": krasukha}


# ---------------------------------------------------------------- offline preview (python3 tools/gen_vehicles.py <dir>)
def _quads(model):
    """Every cube as six faces in vehicle space: 12 coordinates, a normal and a colour per face."""
    out = []
    cubes = model["base"] + model["turret"] + model["barrel"] + [c for b in model["bones"] for c in b["cubes"]]
    for cube in cubes:
        mat, x, y, z, w, h, d, pitch, yaw, roll, p = cube["_sem"]
        col = [v / 255 for v in MATS[mat][1]]
        cs = [(x + i * w, y + j * h, z + k * d) for i in (0, 1) for j in (0, 1) for k in (0, 1)]

        def turn(pt):
            px, py, pz = pt[0] - p[0], pt[1] - p[1], pt[2] - p[2]
            if pitch:
                a = math.radians(pitch); py, pz = py * math.cos(a) + pz * math.sin(a), pz * math.cos(a) - py * math.sin(a)
            if yaw:
                a = math.radians(yaw); px, pz = px * math.cos(a) + pz * math.sin(a), pz * math.cos(a) - px * math.sin(a)
            if roll:
                a = math.radians(roll); px, py = px * math.cos(a) - py * math.sin(a), py * math.cos(a) + px * math.sin(a)
            return (px + p[0], py + p[1], pz + p[2])
        cs = [turn(c) for c in cs]
        centre = [sum(c[i] for c in cs) / 8 for i in range(3)]
        for f in ((0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)):
            a, b, c2, d2 = (cs[i] for i in f)
            u = [c2[i] - a[i] for i in range(3)]; v = [d2[i] - b[i] for i in range(3)]
            n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
            ln = math.sqrt(sum(q * q for q in n)) or 1
            n = [q / ln for q in n]
            mid = [(a[i] + b[i] + c2[i] + d2[i]) / 4 - centre[i] for i in range(3)]
            if sum(n[i] * mid[i] for i in range(3)) < 0:
                n = [-q for q in n]
            out.append(list(a) + list(b) + list(c2) + list(d2) + n + col)
    return out


if __name__ == "__main__":
    import sys
    for name, fn in MODELS.items():
        with open(f"{sys.argv[1]}/{name}.txt", "w") as fh:
            for q in _quads(fn()):
                fh.write(" ".join(f"{v:.4f}" for v in q) + "\n")
