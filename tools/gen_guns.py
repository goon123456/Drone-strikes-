#!/usr/bin/env python3
"""Small arms, their magazines, sights and muzzle devices, built with the vehicle kit at real size (metres) and
drawn by Steel Rain's own renderer. Gun space: the bore runs along +z through the origin, the trigger sits at
z = 0, +y is up and +x is the firer's left.

Moving parts are bones the game animates by name: "mag" (the magazine, pivot at its lip), "bolt" (carrier, slide
or pump, slides back along z), "trigger", "selector", and where a gun has them "charge", "cover", "bipod",
"irons" and "muzzle" (the standard muzzle device, hidden under a suppressor). Each model also names points the
game needs: where the muzzle and ejection port are, where the eye sits behind the sights, where the hands hold it
and where attachments go.

Preview without starting the game:  python3 tools/gen_guns.py <output dir> [model ...]
"""
import math
from vehicle_kit import *
from vehicle_kit import _mat


# ---------------------------------------------------------------- helpers
def side(mat, w, profile, x=0.0, **opt):
    """A side profile of (z, y) points, `w` wide, centred on x."""
    return hull(mat, x - w / 2, x + w / 2, profile, **opt)


def tube(mat, z0, z1, dia, y=0.0, x=0.0, **opt):
    return lathe(mat, "z", x, y, [(z0, dia / 2), (z1, dia / 2)], **opt)


def turned(mat, profile, y=0.0, x=0.0, **opt):
    """A part turned about an axis along z, from (z, diameter) points."""
    return lathe(mat, "z", x, y, [(z, d / 2) for z, d in profile], **opt)


def bore(z, dia, y=0.0, x=0.0, facing=1):
    """The dark hole in the end of a barrel."""
    return lathe(BLACK, "z", x, y, [(z + facing * 0.0004, dia / 2), (z - facing * 0.03, dia / 2 * 0.8), (z - facing * 0.03, 0.0)], plain=True, open=True)


def section(mat, z0, z1, poly, poly2=None, **opt):
    """A cross-section of (x, y) points carried along z."""
    return loft(mat, "z", z0, poly, z1, poly2, **opt)


def rail(mat, z0, z1, y, x=0.0, w=0.0212, up=1):
    """A Picatinny rail: a flat base with cross slots every 10 mm. `up` is 1 on top, -1 underneath."""
    out = [B(mat, x - w * 0.36, y if up > 0 else y - 0.004, z0, w * 0.72, 0.004, z1 - z0, plain=True)]
    n = int((z1 - z0) / 0.01)
    for i in range(n):
        out.append(B(mat, x - w / 2, y + 0.004 if up > 0 else y - 0.0075, z0 + 0.0024 + i * 0.01, w, 0.0035, 0.0052, plain=True))
    return out


def side_rail(mat, z0, z1, x, y, facing):
    """A Picatinny rail on the side of a handguard; `facing` is 1 for the left side, -1 for the right."""
    out = []
    n = int((z1 - z0) / 0.01)
    for i in range(n):
        out.append(B(mat, x if facing > 0 else x - 0.0035, y - 0.0106, z0 + 0.0024 + i * 0.01, 0.0035, 0.0212, 0.0052, plain=True))
    return out


def label(mat, text, x, y, z, length, height=0.006, face="left", colour=None):
    """Lettering stamped on the side of a part: a paper-thin plate carrying the text."""
    t = 0.0003
    xx = x if face == "left" else x - t
    return B(mat, xx, y - height, z, t, height * 2, length, plain=True, marks=[(face, "text:" + text, 0.5, 0.5, height, colour)])


def pin(x, y, z, dia=0.005, mat=GUN_BARE):
    """A pin or rivet head showing on both sides of a receiver of half-width x."""
    return [lathe(mat, "x", y, z, [(-x - 0.0006, dia / 2), (x + 0.0006, dia / 2)], plain=True, seg=6)]


def curved_mag(mat, top_z, top_y, depth, length, radius, w, n=8, floor=None):
    """A magazine curving forward as it goes down: (z, y) of the top middle, front-to-back depth, length along its
    curve and the radius of the curve. Returns the body and a floor plate."""
    zc, yc = top_z + radius, top_y
    sweep = length / radius
    back = [(zc - (radius + depth / 2) * math.cos(sweep * i / n), yc - (radius + depth / 2) * math.sin(sweep * i / n)) for i in range(n + 1)]
    front = [(zc - (radius - depth / 2) * math.cos(sweep * i / n), yc - (radius - depth / 2) * math.sin(sweep * i / n)) for i in range(n + 1)]
    body = side(mat, w, back + front[::-1])
    a = sweep
    tz, ty = math.sin(a), -math.cos(a)                      # along the magazine at its foot
    nz, ny = -math.cos(a), -math.sin(a)                     # from front to back across it
    fb, ff = back[-1], front[-1]
    plate = side(floor or mat, w + 0.004, [(fb[0] + nz * 0.003, fb[1] + ny * 0.003), (ff[0] - nz * 0.003, ff[1] - ny * 0.003),
                                           (ff[0] - nz * 0.003 + tz * 0.006, ff[1] - ny * 0.003 + ty * 0.006),
                                           (fb[0] + nz * 0.003 + tz * 0.006, fb[1] + ny * 0.003 + ty * 0.006)])
    return [body, plate]


def cartridge(case, z, y, case_len, case_dia, neck_dia, bullet_len, x=0.0, tip=BULLET):
    """One round lying along z with its base at z: a bottle-necked case and a pointed bullet."""
    s = case_len
    return [turned(case, [(z, case_dia * 0.9), (z + 0.001, case_dia), (z + s * 0.7, case_dia * 0.96), (z + s * 0.82, neck_dia), (z + s, neck_dia)], y, x, plain=True, seg=8),
            turned(tip, [(z + s, neck_dia * 0.94), (z + s + bullet_len * 0.45, neck_dia * 0.8), (z + s + bullet_len * 0.85, neck_dia * 0.36),
                         (z + s + bullet_len, 0.0)], y, x, plain=True, seg=8)]


def gun(parts, bones, points):
    return dict(base=parts, turret=[], barrel=[], bones=[dict(parent="root", **b) for b in bones], points=points)


def bone(name, pivot, cubes):
    return dict(name=name, pivot=pivot, cubes=cubes)


# ---------------------------------------------------------------- rounded shapes
def _unit(dz, dy):
    ln = math.hypot(dz, dy) or 1.0
    return dz / ln, dy / ln


def round_poly(profile, r, n=3):
    """The same outline with every corner eased into a small curve of about radius r."""
    out = []
    m = len(profile)
    for i in range(m):
        p0, p1, p2 = profile[i - 1], profile[i], profile[(i + 1) % m]
        l1, l2 = math.dist(p0, p1), math.dist(p1, p2)
        c = min(r, l1 * 0.45, l2 * 0.45)
        if c < 1e-5:
            out.append(p1)
            continue
        a = (p1[0] + (p0[0] - p1[0]) / l1 * c, p1[1] + (p0[1] - p1[1]) / l1 * c)
        b = (p1[0] + (p2[0] - p1[0]) / l2 * c, p1[1] + (p2[1] - p1[1]) / l2 * c)
        for k in range(n + 1):
            t = k / n
            out.append(((1 - t) ** 2 * a[0] + 2 * t * (1 - t) * p1[0] + t * t * b[0], (1 - t) ** 2 * a[1] + 2 * t * (1 - t) * p1[1] + t * t * b[1]))
    return out


def _inset(poly, d):
    """An outline moved inwards by d all round."""
    m = len(poly)
    area = sum(poly[i][0] * poly[(i + 1) % m][1] - poly[(i + 1) % m][0] * poly[i][1] for i in range(m))
    sgn = 1 if area > 0 else -1
    out = []
    for i in range(m):
        p0, p1, p2 = poly[i - 1], poly[i], poly[(i + 1) % m]
        e1, e2 = _unit(p1[0] - p0[0], p1[1] - p0[1]), _unit(p2[0] - p1[0], p2[1] - p1[1])
        n1, n2 = (-e1[1] * sgn, e1[0] * sgn), (-e2[1] * sgn, e2[0] * sgn)
        b = _unit(n1[0] + n2[0], n1[1] + n2[1])
        k = d / max(0.4, b[0] * n1[0] + b[1] * n1[1])
        out.append((p1[0] + b[0] * k, p1[1] + b[1] * k))
    return out


def slab(mat, w, profile, x=0.0, edge=0.003, r=0.004, **opt):
    """A side profile of (z, y) points carried across the gun like side(), but with its corners eased and its
    edges rolled over, so it reads as a pressed, moulded or machined part and not a cut-out. Use convex outlines."""
    prof = round_poly(profile, r) if r else list(profile)
    edge = min(edge, w * 0.45)
    ins, mid = _inset(prof, edge), _inset(prof, edge * 0.29)
    ring = lambda poly, xx: [(xx, y, z) for z, y in poly]
    return [hull(mat, x - w / 2, x + w / 2, ins, **opt),
            ribbon(mat, [ring(ins, x - w / 2), ring(mid, x - w / 2 + edge * 0.29), ring(prof, x - w / 2 + edge), ring(prof, x + w / 2 - edge),
                         ring(mid, x + w / 2 - edge * 0.29), ring(ins, x + w / 2)], plain=True)]


def sweep(mat, path, w, d, power=4.0, seg=14, x=0.0, ends="round"):
    """A bar of rounded cross-section following a path of (z, y) points in the gun's centre plane: grips,
    magazines, stocks, guards. `w` is its width across the gun and `d` its depth in the plane of the path; either
    may be a list with one value per point. `ends` is "round" or "flat"."""
    n = len(path)
    ws = w if isinstance(w, (list, tuple)) else [w] * n
    ds = d if isinstance(d, (list, tuple)) else [d] * n
    e = 2.0 / power
    ang = [2 * math.pi * k / seg for k in range(seg)]
    shape = [(math.copysign(abs(math.cos(a)) ** e, math.cos(a)), math.copysign(abs(math.sin(a)) ** e, math.sin(a))) for a in ang]
    rings, tans = [], []
    for i, (pz, py) in enumerate(path):
        a, b = path[max(0, i - 1)], path[min(n - 1, i + 1)]
        tz, ty = _unit(b[0] - a[0], b[1] - a[1])
        tans.append((tz, ty))
        rings.append([(x + ws[i] / 2 * u, py + tz * ds[i] / 2 * v, pz - ty * ds[i] / 2 * v) for u, v in shape])
    def shut(i, out):
        (pz, py), (tz, ty) = path[i], tans[i]
        if ends == "flat":
            return [[(x, py, pz)] * seg]
        k = min(ws[i], ds[i]) * 0.22 * out
        small = [(x + (q[0] - x) * 0.62, py + (q[1] - py) * 0.62 + ty * k, pz + (q[2] - pz) * 0.62 + tz * k) for q in rings[i]]
        return [small, [(x, py + ty * k * 1.25, pz + tz * k * 1.25)] * seg]
    head = shut(0, -1)[::-1]
    return [ribbon(mat, head + rings + shut(n - 1, 1), plain=True)]


def body(mat, sections, power=5.0, seg=20, **opt):
    """A receiver or handguard: rounded-box cross-sections (z, centre y, width, height) joined along the bore."""
    return skin(mat, [(z, 0.0, cy, w, h) for z, cy, w, h in sections], power=power, seg=seg, **opt)


def arc_path(top_z, top_y, length, radius, n=8, lean=0.0):
    """Points down a magazine that curves forward as it goes: from its top, `length` along a curve of `radius`.
    `lean` tips the whole thing forward (radians)."""
    zc, yc = top_z + radius * math.cos(lean), top_y + radius * math.sin(lean)
    sweep_ = length / radius
    return [(zc - radius * math.cos(lean + sweep_ * i / n), yc - radius * math.sin(lean + sweep_ * i / n)) for i in range(n + 1)]


# ---------------------------------------------------------------- AK-74M
def _ak(mark, furn=POLYMER, magmat=POLYMER_PLUM, rnd=(0.0396, 0.010, 0.0062, 0.017), case=CASE_STEEL, stock="fold", muzzle="brake74", toprail=False):
    """The Kalashnikov chassis: pressed-steel receiver and ribbed cover, stepped barrel, side rail, and swappable
    furniture (`furn`), magazine colour (`magmat`/`rnd`), buttstock (`stock`: fold/wood/tele), muzzle device and an
    optional full-length top rail (AK-12)."""
    S, K, PL = GUN_STEEL, GUN_BLACK, furn
    HW = 0.0165
    cl, cd, nk, bl = rnd
    p = [body(K, [(-0.108, -0.006, 0.030, 0.044), (-0.098, -0.010, 0.033, 0.056), (-0.060, -0.0135, 0.033, 0.067), (0.136, -0.0135, 0.033, 0.067),
                  (0.150, -0.008, 0.033, 0.056), (0.156, -0.004, 0.030, 0.046)], power=7)]
    if toprail:
        p += [body(K, [(-0.104, 0.020, 0.028, 0.030), (0.120, 0.020, 0.028, 0.030)], power=3)] + rail(K, -0.100, 0.118, 0.035)
    else:
        p += [body(K, [(-0.104, 0.022, 0.026, 0.022), (-0.096, 0.021, 0.0335, 0.040), (0.112, 0.021, 0.0335, 0.044), (0.120, 0.021, 0.030, 0.038)], power=2.6)]
        for z in (-0.078, -0.046, -0.014, 0.018, 0.050, 0.082):
            p += [body(K, [(z, 0.021, 0.0335, 0.044), (z + 0.002, 0.021, 0.0347, 0.0456), (z + 0.006, 0.021, 0.0347, 0.0456), (z + 0.008, 0.021, 0.0335, 0.044)], power=2.6)]
    p += [turned(S, [(-0.1050, 0.0), (-0.1050, 0.007), (-0.102, 0.008)], y=0.026)]
    srh = 0.0425 if not toprail else 0.0455
    p += slab(S, 0.027, [(0.112, 0.016), (0.232, 0.016), (0.232, 0.030), (0.206, srh), (0.148, srh), (0.112, 0.036)], edge=0.004)
    p += [B(S, -0.0055, srh, 0.130, 0.011, 0.0035, 0.085, pitch=-2, pivot=(0, srh, 0.212), plain=True),
          B(S, -0.008, srh - 0.001, 0.168, 0.016, 0.006, 0.010, plain=True), turned(S, [(0.204, 0.009), (0.216, 0.009)], y=srh),
          B(S, -0.0065, srh + 0.003, 0.128, 0.005, 0.0065, 0.004, plain=True), B(S, 0.0015, srh + 0.003, 0.128, 0.005, 0.0065, 0.004, plain=True)]
    p += [turned(S, [(0.150, 0.024), (0.168, 0.024), (0.168, 0.0175), (0.395, 0.0175), (0.395, 0.0155), (0.526, 0.0155)], seg=12),
          turned(S, [(0.228, 0.018), (0.240, 0.020), (0.380, 0.017), (0.394, 0.019)], y=0.030, seg=10)]
    p += slab(S, 0.023, [(0.390, -0.013), (0.428, -0.013), (0.428, 0.010), (0.414, 0.042), (0.390, 0.042)], edge=0.004)
    p += [B(S, -0.004, -0.022, 0.398, 0.008, 0.010, 0.016, plain=True), turned(S, [(0.428, 0.020), (0.434, 0.0155)], seg=10)]
    p += slab(S, 0.021, [(0.488, -0.0125), (0.524, -0.0125), (0.524, 0.012), (0.517, 0.030), (0.497, 0.030), (0.488, 0.014)], edge=0.0035)
    p += [B(S, 0.0072, 0.029, 0.501, 0.0018, 0.027, 0.014, roll=-8, pivot=(0.008, 0.030, 0.508), plain=True),
          B(S, -0.0090, 0.029, 0.501, 0.0018, 0.027, 0.014, roll=8, pivot=(-0.008, 0.030, 0.508), plain=True),
          turned(S, [(0.5068, 0.0), (0.5068, 0.004), (0.5092, 0.004), (0.5092, 0.0)], y=0.0), bar(S, (0, 0.028, 0.508), (0, 0.052, 0.508), 0.0026)]
    p += [bar(S, (0, -0.0185, 0.360), (0, -0.0185, 0.540), 0.006), turned(S, [(0.540, 0.006), (0.546, 0.008), (0.549, 0.006)], y=-0.0185, seg=6)]
    # handguards in the chosen furniture
    p += [body(PL, [(0.163, -0.012, 0.040, 0.054), (0.172, -0.013, 0.044, 0.058), (0.250, -0.014, 0.049, 0.060), (0.330, -0.013, 0.046, 0.058),
                    (0.356, -0.012, 0.043, 0.054), (0.362, -0.012, 0.040, 0.050)], power=3.4)]
    for i in range(4):
        p += P(PL, 0.0222 + (0.0012 if i in (1, 2) else 0.0), -0.004 - i * 0.008, 0.190, 0.0016, 0.0032, 0.140, plain=True)
    p += [body(PL, [(0.232, 0.031, 0.038, 0.030), (0.238, 0.031, 0.043, 0.034), (0.340, 0.031, 0.043, 0.034), (0.347, 0.031, 0.038, 0.030)], power=2.4)]
    for z in (0.254, 0.284, 0.314):
        p += P(BLACK, 0.0206, 0.031, z, 0.0012, 0.006, 0.016, plain=True)
    p += [body(S, [(0.359, -0.010, 0.046, 0.060), (0.371, -0.010, 0.046, 0.060)], power=3.4), body(S, [(0.154, -0.008, 0.043, 0.054), (0.165, -0.008, 0.043, 0.054)], power=4)]
    p += sweep(PL, [(-0.030, -0.040), (-0.036, -0.062), (-0.050, -0.100), (-0.066, -0.140)], [0.026, 0.027, 0.029, 0.031], [0.036, 0.038, 0.041, 0.044], power=3.2)
    p += sweep(S, [(-0.022, -0.052), (-0.012, -0.079), (0.060, -0.081), (0.076, -0.074), (0.078, -0.044)], 0.015, 0.0026, power=6, seg=8, ends="flat")
    p += slab(S, 0.010, [(0.070, -0.045), (0.078, -0.045), (0.077, -0.090), (0.068, -0.094)], edge=0.002, r=0.002)
    p += slab(S, 0.0048, [(-0.070, -0.022), (0.062, -0.022), (0.066, -0.011), (0.062, 0.002), (-0.070, 0.002)], x=HW + 0.0022, edge=0.0015, r=0.002)
    p += [B(S, HW + 0.0046, -0.018, -0.062, 0.002, 0.016, 0.118, plain=True)]
    for y, z in ((0.008, 0.128), (-0.012, 0.128), (-0.002, 0.143), (0.004, -0.092), (-0.016, -0.092), (-0.012, 0.004), (-0.020, -0.022), (-0.004, -0.030)):
        p += pin(HW, y, z)
    p += [B(BLACK, -HW - 0.0004, -0.036, 0.100, 2 * HW + 0.0008, 0.006, 0.020, plain=True)]
    p += [bar(S, (0.011, 0.014, 0.402), (0.019, 0.003, 0.402), 0.0035), bar(S, (0.011, -0.006, 0.402), (0.019, 0.003, 0.402), 0.0035)]
    p += [label(K, mark, HW + 0.0002, -0.034, 0.012, 0.045, 0.0042)]
    # buttstock
    p += [body(S, [(-0.122, -0.008, 0.034, 0.052), (-0.106, -0.008, 0.034, 0.052)], power=5)]
    if stock == "wood":
        p += slab(WOOD, 0.038, [(-0.106, 0.020), (-0.150, 0.030), (-0.300, 0.028), (-0.330, 0.000), (-0.326, -0.070), (-0.150, -0.050), (-0.106, -0.030)], edge=0.009, r=0.014)
        p += slab(S, 0.036, [(-0.328, 0.004), (-0.336, 0.002), (-0.336, -0.072), (-0.328, -0.074)], edge=0.004, r=0.003)
    else:
        p += slab(PL, 0.031, [(-0.120, 0.017), (-0.342, 0.003), (-0.345, -0.104), (-0.330, -0.110), (-0.170, -0.046), (-0.120, -0.033)], edge=0.007, r=0.010,
                  marks=[("cap0", "slot", 0.60, 0.52, 0.014, 0.105), ("cap1", "slot", 0.60, 0.52, 0.014, 0.105)])
        p += slab(S, 0.033, [(-0.343, 0.005), (-0.351, 0.003), (-0.351, -0.108), (-0.343, -0.110)], edge=0.004, r=0.003)
        p += [bar(S, (0.0155, -0.030, -0.300), (0.022, -0.044, -0.300), 0.0035)]
    # muzzle device
    if muzzle == "slant":
        tip = 0.566
        brake = [turned(S, [(0.524, 0.016), (0.528, 0.0235), (0.560, 0.0235), (0.566, 0.021)], seg=12), bore(0.566, 0.012),
                 B(BLACK, -0.012, 0.0, 0.552, 0.024, 0.024, 0.0016, roll=24, pivot=(0, 0, 0.56), plain=True)]
    elif muzzle == "brake12":
        tip = 0.598
        brake = [turned(S, [(0.524, 0.016), (0.530, 0.023), (0.594, 0.023), (0.598, 0.020)], seg=12), bore(0.598, 0.012)]
        for a in (0, 120, 240):
            brake += [B(BLACK, -0.002, 0.0118, 0.540, 0.004, 0.0008, 0.044, roll=a, pivot=(0, 0, 0.565), plain=True)]
    else:
        tip = 0.611
        brake = [turned(S, [(0.524, 0.016), (0.532, 0.0245), (0.552, 0.0245), (0.556, 0.0275), (0.606, 0.0275), (0.611, 0.024)], seg=12), bore(0.611, 0.012),
                 B(BLACK, -0.0142, -0.0055, 0.584, 0.0284, 0.011, 0.015, plain=True), B(BLACK, -0.003, 0.0120, 0.540, 0.006, 0.0008, 0.006, plain=True),
                 B(BLACK, -0.003, 0.0120, 0.540, 0.006, 0.0008, 0.006, roll=45, pivot=(0, 0, 0.543), plain=True),
                 B(BLACK, -0.003, 0.0120, 0.540, 0.006, 0.0008, 0.006, roll=-45, pivot=(0, 0, 0.543), plain=True)]
    carrier = slab(GUN_BARE, 0.008, [(0.030, 0.004), (0.118, 0.004), (0.118, 0.030), (0.030, 0.030)], x=-0.0148, edge=0.002, r=0.003)
    carrier += [bar(GUN_BARE, (-0.016, 0.016, 0.106), (-0.038, 0.016, 0.106), 0.011), turned(GUN_BARE, [(0.094, 0.0), (0.098, 0.012), (0.110, 0.013), (0.113, 0.0)], y=0.0155, x=-0.042, seg=8)]
    trigger = sweep(S, [(0.014, -0.046), (0.016, -0.058), (0.019, -0.068), (0.016, -0.075)], 0.006, 0.006, power=3, seg=8)
    selector = slab(S, 0.0016, [(-0.050, 0.014), (0.072, 0.010), (0.076, 0.003), (0.072, -0.003), (-0.032, -0.005), (-0.050, -0.003)], x=-HW - 0.0010, edge=0.0005, r=0.003)
    selector += [B(S, -HW - 0.0075, -0.002, 0.066, 0.006, 0.004, 0.010, plain=True), lathe(S, "x", 0.004, -0.040, [(-HW - 0.003, 0.0045), (-HW, 0.0045)], plain=True, seg=8)]
    path = arc_path(0.108, -0.040, 0.205, 0.300, 9)
    mag = sweep(magmat, path, 0.0265, 0.064, power=7, ends="flat")
    mag += sweep(magmat, path[1:], 0.0085, 0.068, power=4, ends="flat")
    for k in (3, 5, 7):
        mag += sweep(magmat, [path[k], ((path[k][0] + path[k + 1][0]) / 2, (path[k][1] + path[k + 1][1]) / 2)], 0.0285, 0.040, power=6, seg=10, ends="flat")
    mag += sweep(magmat, [path[-1], (path[-1][0] + (path[-1][0] - path[-2][0]) * 0.3, path[-1][1] + (path[-1][1] - path[-2][1]) * 0.3)], 0.031, 0.070, power=6, ends="flat")
    mag += cartridge(case, 0.080, -0.037, cl, cd, nk, bl)
    mag += [B(S, -0.004, -0.050, 0.137, 0.008, 0.006, 0.006, plain=True)]
    return gun(p, [bone("muzzle", (0, 0, 0.524), brake), bone("bolt", (0, 0.016, 0.07), carrier), bone("trigger", (0, -0.047, 0.014), trigger),
                   bone("selector", (-HW, 0.004, -0.040), selector), bone("mag", (0, -0.047, 0.138), mag)],
               dict(muzzle=(0, 0, tip), thread=(0, 0, 0.524), eject=(-0.02, 0.02, 0.075), sight=(0, srh + 0.0095, 0.128), front=(0, 0.052, 0.508),
                    optic=(0, srh + 0.002 + (0.004 if toprail else 0), -0.005), under=(0, -0.043, 0.265), grip=(0, -0.095, -0.050), hand=(0, -0.044, 0.265),
                    mag=(0, -0.047, 0.108), charge=(-0.042, 0.016, 0.104)))


def ak74m():
    """AK-74M, 5.45x39: black polymer furniture, side-folding stock, the long two-chamber brake, plum magazine."""
    return _ak("74M 0719", furn=POLYMER, magmat=POLYMER_PLUM, rnd=(0.0396, 0.010, 0.0062, 0.017), case=CASE_STEEL, stock="fold", muzzle="brake74")


def akm():
    """AKM, 7.62x39: laminated wood furniture and fixed stock, the 45-degree slant compensator, steel magazine."""
    return _ak("AKM", furn=LAMINATE, magmat=BAKELITE, rnd=(0.0388, 0.0113, 0.0079, 0.019), case=CASE_STEEL, stock="wood", muzzle="slant")


def ak12():
    """AK-12, 5.45x39: modern black polymer, a full-length top rail, telescoping stock and a vented brake."""
    return _ak("AK-12", furn=_mat("ak12", (38, 40, 42), "polymer"), magmat=_mat("ak12mag", (40, 42, 44), "polymer"),
               rnd=(0.0396, 0.010, 0.0062, 0.017), case=CASE_STEEL, stock="tele", muzzle="brake12", toprail=True)


OD_GREEN = _mat("od_green", (74, 78, 52), "polymer")
SAND = _mat("sand", (150, 132, 96), "polymer")


def _bullpup(mark, shell=POLYMER, magmat=GUN_ALU, rnd=(0.0352, 0.0098, 0.0060, 0.016), case=CASE_BRASS,
             handle="rail", grip="rail", barrelL=0.300, flash="birdcage", trigcol=BLACK, muzzlez=None):
    """A bullpup chassis: the magazine rides behind the pistol grip, the buttpad is the back of the shell, and a
    carry handle or rail sits on top. `handle` is scope/rail/carry; `grip` is vertical/rail/none; colour via `shell`."""
    S, K, PL = GUN_STEEL, GUN_BLACK, shell
    HW = 0.0215
    cl, cd, nk, bl = rnd
    recv_front = 0.150
    mz = muzzlez if muzzlez is not None else recv_front + barrelL
    # main shell: buttpad (back) -> receiver body -> nose
    p = [body(PL, [(-0.300, -0.002, 0.026, 0.050), (-0.288, -0.002, 0.032, 0.062), (-0.252, -0.004, 0.037, 0.070),
                   (-0.150, -0.006, 0.040, 0.074), (-0.040, -0.006, 0.041, 0.076), (0.050, -0.006, 0.041, 0.074),
                   (0.104, -0.004, 0.038, 0.066), (0.136, -0.002, 0.032, 0.054), (0.150, 0.000, 0.026, 0.044)], power=4.2)]
    # buttpad
    p += slab(trigcol, 0.030, [(-0.300, 0.044), (-0.314, 0.036), (-0.316, -0.048), (-0.300, -0.056)], edge=0.004, r=0.008)
    for y in (0.028, 0.004, -0.020):
        p += [B(trigcol, -0.0255, y - 0.004, -0.312, 0.051, 0.003, 0.004, plain=True)]
    # raised receiver deck: the cheek rest and the solid base the sights, optics or rail mount on (blended into the shell)
    dtop = 0.052
    p += [body(PL, [(-0.248, 0.004, 0.030, 0.064), (-0.180, 0.012, 0.034, 0.078), (-0.150, 0.016, 0.035, 0.082), (0.060, 0.016, 0.035, 0.082), (0.100, 0.010, 0.030, 0.066), (0.124, 0.004, 0.026, 0.052)], power=3.4)]
    # sling loop + plate at back
    p += [B(S, -HW - 0.004, -0.026, -0.286, 0.004, 0.018, 0.016, plain=True)]
    # trigger group + guard (wraps the whole grip on most bullpups)
    p += sweep(K, [(0.058, -0.052), (0.066, -0.088), (0.030, -0.108), (-0.040, -0.108), (-0.072, -0.090), (-0.078, -0.054)], 0.016, 0.0028, power=5, seg=8, ends="flat")
    trigger = sweep(S, [(-0.006, -0.050), (-0.004, -0.064), (0.000, -0.074), (-0.004, -0.082)], 0.006, 0.006, power=3, seg=8)
    # pistol grip
    p += sweep(PL, [(-0.004, -0.050), (-0.012, -0.082), (-0.028, -0.120), (-0.044, -0.158)], [0.024, 0.025, 0.027, 0.029], [0.030, 0.033, 0.036, 0.040], power=3.2)
    for i in range(3):
        p += P(K, 0.0180, -0.070 - i * 0.022, -0.012 - i * 0.012, 0.0012, 0.0030, 0.016, plain=True)
    # magazine well + magazine (behind the grip): the well funnels down from the receiver belly to meet the magazine top
    p += [body(PL, [(-0.160, -0.012, 0.030, 0.056), (-0.120, -0.014, 0.032, 0.060), (-0.090, -0.010, 0.028, 0.050)], power=3.4)]
    mpath = [(-0.120, -0.014), (-0.126, -0.048), (-0.132, -0.082), (-0.138, -0.116)]
    mag = sweep(magmat, mpath, 0.0235, 0.030, power=4, ends="flat")
    mag += sweep(magmat, mpath[1:], 0.0075, 0.034, power=3, ends="flat")
    for k in (1, 2):
        mag += sweep(magmat, [mpath[k], ((mpath[k][0] + mpath[k + 1][0]) / 2, (mpath[k][1] + mpath[k + 1][1]) / 2)], 0.0255, 0.018, power=5, seg=8, ends="flat")
    mag += cartridge(case, -0.114, -0.006, cl, cd, nk, bl)
    mag += cartridge(case, -0.120, -0.006, cl, cd, nk, bl, x=0.006)
    mag += [B(S, -0.004, -0.020, -0.104, 0.008, 0.006, 0.006, plain=True)]  # follower nub at feed lips
    magrel = [B(S, -HW - 0.002, -0.028, -0.150, 0.006, 0.010, 0.012, plain=True)]  # paddle release
    # barrel + handguard
    p += [turned(S, [(recv_front, 0.020), (recv_front + 0.006, 0.014), (mz - 0.040, 0.013), (mz - 0.030, 0.0135), (mz - 0.030, 0.0115), (mz, 0.0115)], seg=12), bore(mz, 0.009)]
    p += [body(PL, [(recv_front - 0.004, -0.006, 0.030, 0.034), (recv_front + 0.010, -0.008, 0.033, 0.040), (recv_front + 0.120, -0.010, 0.031, 0.038), (recv_front + 0.150, -0.008, 0.026, 0.030)], power=3.4)]
    for i in range(5):
        p += P(K, 0.0205, -0.030, recv_front + 0.020 + i * 0.022, 0.0012, 0.004, 0.012, plain=True)
    # front sight: a solid post standing off the barrel, no gap
    p += [turned(S, [(mz - 0.062, 0.013), (mz - 0.056, 0.020), (mz - 0.046, 0.020), (mz - 0.040, 0.014)], y=0.0, seg=12)]  # sight base clamped on the barrel
    p += [B(S, -0.0018, 0.010, mz - 0.057, 0.0036, 0.030, 0.010, plain=True)]  # post rising from the barrel
    # flash hider / brake
    if flash == "brake":
        p += [turned(S, [(mz, 0.012), (mz + 0.004, 0.019), (mz + 0.044, 0.019), (mz + 0.048, 0.015)], seg=12), bore(mz + 0.048, 0.010)]
        for a in (0, 90, 180, 270):
            p += [B(K, -0.0015, 0.013, mz + 0.012, 0.003, 0.0008, 0.028, roll=a, pivot=(0, 0, mz + 0.024), plain=True)]
        muzzle_tip = mz + 0.048
    else:
        p += [turned(S, [(mz, 0.012), (mz + 0.004, 0.017), (mz + 0.040, 0.017), (mz + 0.044, 0.013)], seg=12), bore(mz + 0.044, 0.010)]
        for a in (30, 90, 150, 210, 270, 330):
            p += [B(K, -0.0012, 0.014, mz + 0.010, 0.0024, 0.0008, 0.024, roll=a, pivot=(0, 0, mz + 0.022), plain=True)]
        muzzle_tip = mz + 0.044
    # top: carry handle / optic / rail, all mounted on the raised deck (dtop) so nothing floats
    opt_y = dtop + 0.030
    if handle == "scope":
        p += [B(K, -0.013, dtop - 0.004, -0.120, 0.026, 0.030, 0.100, plain=True)]  # scope mount, from the deck up to the tube
        sc = dtop + 0.044
        p += [turned(S, [(-0.130, 0.020), (-0.120, 0.027), (0.040, 0.027), (0.050, 0.020)], y=sc, seg=14)]
        p += [turned(BLACK, [(-0.132, 0.025), (-0.128, 0.025)], y=sc, seg=14), turned(BLACK, [(0.050, 0.025), (0.054, 0.025)], y=sc, seg=14)]
        p += [turned(LENS, [(-0.130, 0.022)], y=sc, seg=14), turned(LENS_RED, [(0.050, 0.022)], y=sc, seg=14)]
        opt_y = sc + 0.027
    elif handle == "carry":
        p += [B(K, -0.012, dtop - 0.002, -0.140, 0.024, 0.032, 0.150, plain=True)]  # low carry handle body rising from the deck
        p += [B(S, -0.0018, dtop + 0.028, -0.120, 0.0036, 0.008, 0.006, plain=True), B(S, -0.0018, dtop + 0.028, 0.000, 0.0036, 0.010, 0.006, plain=True)]  # sights through handle
        opt_y = dtop + 0.040
    elif handle == "tall":  # FAMAS "clairon": tall triangular carry handle
        p += slab(PL, 0.016, [(-0.150, dtop + 0.004), (-0.140, 0.150), (-0.118, 0.150), (-0.112, dtop + 0.004)], edge=0.004, r=0.004)  # rear post
        p += slab(PL, 0.016, [(0.020, dtop + 0.004), (0.006, 0.150), (0.028, 0.150), (0.040, dtop + 0.004)], edge=0.004, r=0.004)  # front post
        p += [body(PL, [(-0.150, 0.154, 0.016, 0.012), (-0.140, 0.156, 0.018, 0.014), (0.028, 0.156, 0.018, 0.014), (0.042, 0.154, 0.016, 0.010)], power=3)]
        p += [B(S, -0.0018, 0.150, -0.130, 0.0036, 0.010, 0.006, plain=True), lathe(S, "z", 0.0, 0.152, [(0.028, 0.009), (0.034, 0.009)], plain=True, open=True, seg=12)]
        opt_y = 0.160
    else:  # rail
        p += rail(K, -0.150, 0.118, dtop)
        opt_y = dtop + 0.014
    # forward grip / accessory under handguard
    under_y = -0.040
    if grip == "vertical":
        p += sweep(PL, [(recv_front + 0.070, -0.040), (recv_front + 0.074, -0.074), (recv_front + 0.072, -0.110)], [0.016, 0.017, 0.018], [0.016, 0.017, 0.018], power=4, seg=10)
    elif grip == "rail":
        p += rail(K, recv_front + 0.030, recv_front + 0.120, -0.026, up=-1)
    # charging handle slot (left)
    charge = [B(S, -HW - 0.006, 0.030, 0.050, 0.008, 0.008, 0.020, plain=True), turned(GUN_BARE, [(0.044, 0.0), (0.047, 0.010), (0.061, 0.011), (0.064, 0.0)], y=0.030, x=-HW - 0.012, seg=8)]
    p += [label(K, mark, HW + 0.0002, -0.034, -0.060, 0.050, 0.0040)]
    return gun(p, [bone("muzzle", (0, 0, mz), []), bone("bolt", (0, 0.016, 0.03), []), bone("trigger", (0, 0, 0), trigger),
                   bone("selector", (-HW, 0.004, -0.030), []), bone("mag", (0, 0, 0), mag), bone("charge", (0, 0, 0), charge + magrel)],
               dict(muzzle=(0, 0, muzzle_tip), thread=(0, 0, mz), eject=(-0.028, 0.004, 0.030), sight=(0, dtop + 0.030, 0.000), front=(0, 0.040, mz - 0.057),
                    optic=(0, opt_y, -0.050), under=(0, under_y, recv_front + 0.075), grip=(0, -0.110, -0.024), hand=(0, -0.030, recv_front + 0.075),
                    mag=(0, -0.070, -0.125), charge=(-HW - 0.012, 0.030, 0.055)))


def aug():
    """Steyr AUG A1, 5.56x45: OD-green shell, the integrated 1.5x optic and a folding vertical foregrip."""
    return _bullpup("AUG A1", shell=OD_GREEN, magmat=_mat("aug_mag", (36, 40, 30), "polymer"), rnd=(0.0352, 0.0098, 0.0060, 0.016),
                    case=CASE_BRASS, handle="scope", grip="vertical", barrelL=0.300)


def tavor():
    """IWI Tavor X95, 5.56x45: black polymer, a full top rail and a forward accessory rail."""
    return _bullpup("X95", shell=POLYMER, magmat=GUN_ALU, rnd=(0.0352, 0.0098, 0.0060, 0.016), case=CASE_BRASS,
                    handle="rail", grip="rail", barrelL=0.280)


def qbz95():
    """QBZ-95, 5.8x42: dark-green shell, the low carry handle with its integrated notch sight."""
    return _bullpup("QBZ-95", shell=_mat("qbz_green", (52, 60, 44), "polymer"), magmat=_mat("qbz_mag", (44, 52, 38), "polymer"),
                    rnd=(0.0400, 0.0100, 0.0062, 0.017), case=CASE_STEEL, handle="carry", grip="none", barrelL=0.300)


def l85():
    """L85A2, 5.56x45: black bullpup with the full top rail (SUSAT era) and a forward grip rail."""
    return _bullpup("L85A2", shell=_mat("l85", (34, 36, 38), "polymer"), magmat=GUN_ALU, rnd=(0.0352, 0.0098, 0.0060, 0.016),
                    case=CASE_BRASS, handle="rail", grip="rail", barrelL=0.312)


def f2000():
    """FN F2000, 5.56x45: smooth egg-shell bullpup, a low optic rail and a stubby brake."""
    return _bullpup("F2000", shell=_mat("f2000", (40, 42, 40), "polymer"), magmat=GUN_ALU, rnd=(0.0352, 0.0098, 0.0060, 0.016),
                    case=CASE_BRASS, handle="rail", grip="none", barrelL=0.260, flash="brake")


def famas():
    """FAMAS F1, 5.56x45: the tall triangular carry handle, a long thin barrel and the wrap-around trigger guard."""
    return _bullpup("FAMAS F1", shell=_mat("famas", (36, 37, 34), "polymer"), magmat=GUN_ALU, rnd=(0.0352, 0.0098, 0.0060, 0.016),
                    case=CASE_BRASS, handle="tall", grip="none", barrelL=0.340)


def bren2():
    """CZ BREN 2, 5.56x45: a modern AR-pattern carbine with a full-length railed handguard and a folding stock."""
    return _ar("BREN 2 5.56", barrel=0.352, guard=0.236)


def qbz191():
    """QBZ-191, 5.8x42: China's AR-pattern service rifle, a railed flat-top and a side-folding telescoping stock."""
    return _ar("QBZ-191 5.8", barrel=0.368, guard=0.250, case=CASE_STEEL, round_=(0.0400, 0.0098, 0.0060, 0.016))


# ---------------------------------------------------------------- V5 battle rifles (7.62x51)
R762NATO = (0.0512, 0.0119, 0.0087, 0.020)


def _m14(mark, panels=WOOD):
    """The M14/Garand pattern: a one-piece wood stock under a steel receiver and gas cylinder, a hooded front sight
    and an aperture rear, the op-rod handle on the right and a curved 20-round 7.62 magazine."""
    S, WD = GUN_STEEL, panels
    HW = 0.030
    p = [body(WD, [(-0.342, -0.012, 0.028, 0.048), (-0.300, -0.008, 0.033, 0.064), (-0.180, -0.006, 0.035, 0.060), (-0.070, -0.008, 0.034, 0.052),
                   (0.010, -0.012, 0.033, 0.046), (0.110, -0.014, 0.034, 0.044), (0.250, -0.012, 0.032, 0.040), (0.300, -0.010, 0.026, 0.032)], power=4)]
    p += slab(S, 0.030, [(-0.344, 0.034), (-0.356, 0.028), (-0.356, -0.048), (-0.344, -0.054)], edge=0.004, r=0.006)  # butt plate
    # receiver
    p += [body(S, [(-0.056, 0.008, 0.028, 0.032), (-0.010, 0.010, 0.030, 0.040), (0.110, 0.010, 0.030, 0.040), (0.126, 0.006, 0.026, 0.030)], power=4)]
    p += [B(S, -0.009, 0.022, -0.048, 0.018, 0.030, 0.020, plain=True), lathe(S, "z", 0.0, 0.050, [(-0.044, 0.008), (-0.038, 0.008)], plain=True, open=True, seg=12)]  # rear aperture on a base that meets the receiver
    # barrel, upper hand guard, gas cylinder, front sight, flash hider
    p += [turned(S, [(0.126, 0.019), (0.150, 0.017), (0.300, 0.015), (0.508, 0.013), (0.520, 0.013)], seg=12), bore(0.540, 0.010)]
    p += [body(WD, [(0.130, 0.020, 0.028, 0.026), (0.150, 0.022, 0.032, 0.030), (0.280, 0.020, 0.030, 0.028), (0.300, 0.018, 0.024, 0.022)], power=3.2)]
    p += [turned(S, [(0.300, 0.017), (0.470, 0.017), (0.476, 0.015)], y=-0.018, seg=10)]  # gas cylinder
    p += [turned(S, [(0.484, 0.013), (0.490, 0.021), (0.502, 0.021), (0.508, 0.014)], y=0.0, seg=12), B(S, -0.0018, 0.010, 0.491, 0.0036, 0.030, 0.012, plain=True)]  # front sight wings + post off the barrel
    hider = [turned(S, [(0.520, 0.012), (0.524, 0.018), (0.556, 0.018), (0.560, 0.014)], seg=12), bore(0.560, 0.010)]
    for a in (0, 90, 180, 270):
        hider += [B(BLACK, -0.0015, 0.013, 0.530, 0.003, 0.0008, 0.020, roll=a, pivot=(0, 0, 0.54), plain=True)]
    # pistol-grip swell, trigger group
    p += sweep(WD, [(-0.030, -0.044), (-0.040, -0.070), (-0.054, -0.100), (-0.064, -0.130)], [0.028, 0.029, 0.030, 0.030], [0.034, 0.036, 0.039, 0.040], power=3.2)
    p += sweep(S, [(0.040, -0.046), (0.050, -0.078), (0.012, -0.094), (-0.050, -0.086), (-0.066, -0.052)], 0.014, 0.0026, power=5, seg=8, ends="flat")
    trigger = sweep(S, [(-0.008, -0.046), (-0.006, -0.060), (-0.002, -0.070), (-0.006, -0.078)], 0.006, 0.006, power=3, seg=8)
    p += [label(WD, mark, HW + 0.0002, -0.034, -0.030, 0.042, 0.0040)]
    # op-rod handle on the right side (firer's right = -x)
    charge = [B(GUN_BARE, -HW - 0.004, 0.012, 0.030, 0.006, 0.010, 0.120, plain=True), bar(GUN_BARE, (-HW - 0.004, 0.012, 0.120), (-HW - 0.020, 0.006, 0.120), 0.009)]
    carrier = [B(GUN_BARE, -0.006, 0.030, 0.020, 0.012, 0.006, 0.080, plain=True)]
    # magazine well + curved 20-round 7.62 magazine, raised so it meets the trigger housing
    p += [body(S, [(0.050, -0.022, 0.028, 0.040), (0.082, -0.024, 0.030, 0.044), (0.108, -0.020, 0.026, 0.036)], power=3.4)]
    path = [(0.080, -0.030), (0.080, -0.100)] + arc_path(0.080, -0.100, 0.092, 0.440, 4)[1:]
    mag = sweep(GUN_ALU, path, 0.0235, 0.058, power=8, ends="flat")
    mag += sweep(GUN_ALU, [path[-1], (path[-1][0] + (path[-1][0] - path[-2][0]) * 0.2, path[-1][1] + (path[-1][1] - path[-2][1]) * 0.2)], 0.026, 0.062, power=6, ends="flat")
    mag += cartridge(CASE_BRASS, 0.056, -0.026, *R762NATO)
    return gun(p, [bone("muzzle", (0, 0, 0.520), hider), bone("bolt", (0, 0.030, 0.03), carrier), bone("charge", (0, 0, 0), charge), bone("trigger", (0, -0.046, -0.004), trigger),
                   bone("selector", (-HW, -0.030, -0.030), []), bone("mag", (0, -0.044, 0.078), mag)],
               dict(muzzle=(0, 0, 0.560), thread=(0, 0, 0.520), eject=(-0.028, 0.030, 0.03), sight=(0, 0.052, -0.040), front=(0, 0.052, 0.494),
                    optic=(0, 0.050, -0.010), under=(0, -0.036, 0.210), grip=(0, -0.100, -0.050), hand=(0, -0.034, 0.210), mag=(0, -0.044, 0.078), charge=(-HW - 0.020, 0.012, 0.120)))


def _battle(mark, furn=POLYMER, handle="carry", guard="smooth"):
    """A 7.62 battle rifle: a long receiver, pistol grip and a straight 20-round magazine, with either the FN carry
    handle (`handle="carry"`) or the HK drum sight (`handle="drum"`), and a smooth or slotted handguard."""
    S, K, PL = GUN_STEEL, GUN_BLACK, furn
    body_mat = K if handle == "drum" else GUN_GREY
    HW = 0.026
    p = [body(body_mat, [(-0.130, 0.004, 0.030, 0.044), (-0.118, 0.004, 0.034, 0.052), (0.140, 0.004, 0.034, 0.052), (0.156, 0.002, 0.028, 0.044)], power=4)]
    # top cover / sight rib
    p += [body(body_mat, [(-0.120, 0.044, 0.026, 0.014), (-0.100, 0.046, 0.030, 0.018), (0.120, 0.046, 0.030, 0.018), (0.140, 0.044, 0.026, 0.012)], power=3)]
    # buttstock
    p += [body(S, [(-0.138, 0.004, 0.030, 0.044), (-0.128, 0.004, 0.030, 0.044)], power=5)]
    if furn is WOOD or furn is LAMINATE:
        p += slab(furn, 0.036, [(-0.128, 0.034), (-0.150, 0.042), (-0.330, 0.028), (-0.344, -0.010), (-0.330, -0.078), (-0.150, -0.050), (-0.128, -0.030)], edge=0.009, r=0.014)
        p += slab(S, 0.034, [(-0.342, 0.006), (-0.350, 0.004), (-0.350, -0.080), (-0.342, -0.082)], edge=0.004, r=0.003)
    else:
        p += [body(K, [(-0.150, 0.000, 0.030, 0.044), (-0.320, 0.000, 0.032, 0.048), (-0.336, 0.000, 0.028, 0.044)], power=3.2)]
        p += slab(RUBBER_PAD, 0.038, [(-0.336, 0.040), (-0.344, 0.036), (-0.344, -0.044), (-0.336, -0.048)], edge=0.004, r=0.003)
    # pistol grip, trigger group
    p += sweep(PL, [(-0.030, -0.044), (-0.040, -0.074), (-0.056, -0.112), (-0.072, -0.150)], [0.026, 0.027, 0.029, 0.030], [0.034, 0.036, 0.040, 0.043], power=3.2)
    p += sweep(S, [(0.044, -0.046), (0.054, -0.078), (0.016, -0.094), (-0.050, -0.086), (-0.066, -0.052)], 0.014, 0.0026, power=5, seg=8, ends="flat")
    trigger = sweep(S, [(-0.004, -0.046), (-0.002, -0.060), (0.002, -0.070), (-0.002, -0.078)], 0.006, 0.006, power=3, seg=8)
    # handguard, barrel, flash hider
    p += [body(PL, [(0.158, 0.000, 0.034, 0.044), (0.172, 0.000, 0.038, 0.050), (0.330, 0.000, 0.034, 0.044), (0.348, 0.000, 0.028, 0.036)], power=3.4)]
    if guard == "slot":
        for i in range(4):
            p += P(K, 0.0275, 0.000, 0.190 + i * 0.038, 0.0014, 0.028, 0.020, plain=True)
    else:
        for i in range(3):
            p += P(K, 0.0205, -0.030, 0.190 + i * 0.044, 0.0012, 0.005, 0.030, plain=True)
    p += [turned(S, [(0.346, 0.018), (0.360, 0.016), (0.520, 0.014), (0.532, 0.014)], seg=12), bore(0.552, 0.010)]
    p += [turned(S, [(0.494, 0.014), (0.500, 0.021), (0.512, 0.021), (0.518, 0.015)], y=0.0, seg=12), B(S, -0.0018, 0.012, 0.501, 0.0036, 0.030, 0.010, plain=True)]  # front sight off the barrel
    hider = [turned(S, [(0.532, 0.013), (0.536, 0.020), (0.556, 0.020), (0.572, 0.014)], seg=12), bore(0.572, 0.010)]
    for a in (0, 120, 240):
        hider += [B(BLACK, -0.0018, 0.015, 0.542, 0.0036, 0.0008, 0.022, roll=a, pivot=(0, 0, 0.552), plain=True)]
    # top sight / carry handle
    if handle == "carry":
        p += [B(S, -0.009, 0.028, -0.044, 0.018, 0.032, 0.012, plain=True), B(S, -0.009, 0.028, 0.046, 0.018, 0.032, 0.012, plain=True)]  # two posts up from the receiver
        p += slab(S, 0.016, [(-0.044, 0.056), (0.058, 0.056), (0.058, 0.076), (-0.044, 0.076)], edge=0.004, r=0.004)  # FN folding carry handle bar
        opt_y = 0.080
    else:  # G3 drum rear sight (the front sight on the barrel is the shared one)
        p += [B(K, -0.011, 0.030, -0.070, 0.022, 0.028, 0.036, plain=True)]  # drum base up from the receiver
        p += [turned(K, [(-0.072, 0.014), (-0.050, 0.020), (-0.040, 0.018)], y=0.058, seg=14)]  # rotary drum
        p += [B(S, -0.006, 0.072, -0.058, 0.012, 0.008, 0.006, plain=True)]
        opt_y = 0.066
        opt_y = 0.066
    # charging handle: FN reciprocating on the left, HK cocking tube on the left-front
    if handle == "drum":
        p += [turned(K, [(0.300, 0.012), (0.470, 0.012)], y=0.030, x=HW + 0.014, seg=8)]  # cocking tube
        charge = [B(GUN_BARE, HW + 0.012, 0.030, 0.430, 0.012, 0.010, 0.016, plain=True), bar(GUN_BARE, (HW + 0.012, 0.030, 0.438), (HW + 0.030, 0.038, 0.438), 0.008)]
    else:
        charge = [B(GUN_BARE, HW + 0.002, 0.016, 0.040, 0.008, 0.010, 0.016, plain=True), bar(GUN_BARE, (HW + 0.008, 0.016, 0.048), (HW + 0.022, 0.016, 0.048), 0.007)]
    carrier = [B(GUN_BARE, -0.006, 0.030, 0.030, 0.012, 0.006, 0.070, plain=True)]
    selector = slab(S, 0.003, [(-0.030, -0.028), (-0.006, -0.030), (-0.006, -0.018), (-0.030, -0.016)], x=HW + 0.002, edge=0.001, r=0.002)
    p += [label(body_mat, mark, HW + 0.0002, -0.028, -0.040, 0.044, 0.0040)]
    # straight 20-round magazine
    path = [(0.086, -0.044), (0.090, -0.110), (0.094, -0.172)]
    mag = sweep(GUN_ALU, path, 0.0235, 0.058, power=8, ends="flat")
    mag += sweep(GUN_ALU, [path[-1], (path[-1][0] + 0.002, path[-1][1] - 0.012)], 0.026, 0.062, power=6, ends="flat")
    mag += cartridge(CASE_BRASS, 0.060, -0.040, *R762NATO)
    return gun(p, [bone("muzzle", (0, 0, 0.532), hider), bone("bolt", (0, 0.030, 0.03), carrier), bone("charge", (0, 0, 0), charge),
                   bone("trigger", (0, -0.046, 0.000), trigger), bone("selector", (HW, -0.024, -0.020), selector), bone("mag", (0, -0.044, 0.090), mag)],
               dict(muzzle=(0, 0, 0.572), thread=(0, 0, 0.532), eject=(-0.028, 0.030, 0.03), sight=(0, opt_y + 0.004, -0.050), front=(0, 0.058, 0.506),
                    optic=(0, opt_y, -0.010), under=(0, -0.040, 0.250), grip=(0, -0.104, -0.056), hand=(0, -0.040, 0.250), mag=(0, -0.044, 0.090), charge=(HW + 0.022, 0.016, 0.048)))


def m14():
    """M14, 7.62x51: the walnut-stocked American battle rifle, gas cylinder under the barrel and a 20-round magazine."""
    return _m14("M14", panels=WOOD)


def bm59():
    """Beretta BM59, 7.62x51: the Italian Garand-derived battle rifle in a lighter laminated stock."""
    return _m14("BM59", panels=LAMINATE)


def fal():
    """FN FAL, 7.62x51: the grey receiver with its folding carry handle, wood furniture and a prong flash hider."""
    return _battle("FN FAL", furn=WOOD, handle="carry", guard="smooth")


def g3():
    """HK G3, 7.62x51: the slab-sided roller-delayed rifle, drum rear sight, slotted handguard and HK cocking tube."""
    return _battle("HK G3", furn=POLYMER, handle="drum", guard="slot")


def hk417():
    """HK417, 7.62x51: the scaled-up HK416 with a long railed handguard and a 20-round magazine."""
    return _ar("HK417 7.62MM", scale=1.13, barrel=0.400, guard=0.300, case=CASE_BRASS, round_=R762NATO)


def sg542():
    """SIG SG 542, 7.62x51: the Swiss rifle on the 540-series receiver with a railed top and folding stock."""
    return _ar("SG 542 7.62", scale=1.10, barrel=0.392, guard=0.270, case=CASE_BRASS, round_=R762NATO)


# ---------------------------------------------------------------- V5 marksman rifles
def g28():
    """HK G28, 7.62x51: the precision HK417 derivative, a long railed handguard and a 20-round magazine."""
    return _ar("G28 7.62MM", scale=1.13, barrel=0.430, guard=0.340, case=CASE_BRASS, round_=R762NATO)


def m39emr():
    """M39 EMR, 7.62x51: an accurised M14 dropped into a black polymer chassis stock."""
    return _m14("M39 EMR", panels=POLYMER)


def mk12():
    """Mk 12 SPR, 5.56x45: the AR-15 special-purpose rifle, an 18-inch barrel in a long rail and a fixed stock."""
    return _ar("MK12 SPR 5.56", barrel=0.460, guard=0.330, fixed_stock=True)


def qbu88():
    """QBU-88, 5.8x42: China's bullpup marksman rifle, a top optic rail over the long barrel and a thumbhole stock."""
    return _bullpup("QBU-88", shell=_mat("qbu", (48, 54, 42), "polymer"), magmat=_mat("qbu_mag", (44, 50, 38), "polymer"),
                    rnd=(0.0400, 0.0100, 0.0062, 0.017), case=CASE_STEEL, handle="rail", grip="none", barrelL=0.372)


# ---------------------------------------------------------------- V5 bolt-action snipers
def _bolt(mark, stockmat=OD_GREEN, rnd=(0.0512, 0.0119, 0.0087, 0.020), case=CASE_BRASS, cap=5, barrelL=0.470,
          stock="classic", brake=True, fluted=True):
    """A turn-bolt precision rifle: a heavy (optionally fluted) barrel, a steel action under a full-length optic rail,
    the bolt handle out the right, a short detachable magazine and a bipod lug. `stock` is classic or thumbhole."""
    S, K, ST = GUN_STEEL, GUN_BLACK, stockmat
    HW = 0.020
    cl, cd, nk, bl = rnd
    mz = 0.150 + barrelL
    # the chassis/stock shell: butt -> grip -> forend, one piece
    p = [body(ST, [(-0.340, -0.006, 0.028, 0.050), (-0.300, -0.004, 0.034, 0.064), (-0.180, -0.004, 0.036, 0.060), (-0.090, -0.006, 0.035, 0.052),
                   (-0.010, -0.010, 0.034, 0.046), (0.070, -0.012, 0.035, 0.046), (0.200, -0.012, 0.034, 0.044), (0.300, -0.012, 0.030, 0.038), (0.330, -0.010, 0.024, 0.030)], power=4)]
    p += slab(RUBBER_PAD, 0.034, [(-0.342, 0.040), (-0.354, 0.034), (-0.356, -0.052), (-0.342, -0.058)], edge=0.004, r=0.008)  # buttpad
    # adjustable comb riser (kept just below the optic rail so the cheek piece does not stand over it)
    p += [body(ST, [(-0.250, 0.040, 0.028, 0.020), (-0.160, 0.044, 0.030, 0.028), (-0.040, 0.044, 0.030, 0.028), (0.010, 0.040, 0.028, 0.020)], power=3)]
    # grip
    if stock == "thumbhole":
        p += sweep(ST, [(-0.030, -0.044), (-0.044, -0.076), (-0.060, -0.112), (-0.072, -0.150)], [0.024, 0.025, 0.027, 0.028], [0.030, 0.033, 0.036, 0.040], power=3.2)
        p += sweep(ST, [(-0.070, 0.010), (-0.120, -0.010), (-0.150, -0.050), (-0.120, -0.090), (-0.074, -0.110)], 0.026, 0.0045, power=4, seg=8, ends="flat")  # the thumbhole bridge
    else:
        p += sweep(ST, [(-0.028, -0.044), (-0.040, -0.074), (-0.056, -0.112), (-0.072, -0.152)], [0.026, 0.027, 0.029, 0.030], [0.034, 0.036, 0.040, 0.043], power=3.2)
    for i in range(3):
        p += P(K, 0.0210, -0.070 - i * 0.022, -0.050 + i * 0.004, 0.0012, 0.0030, 0.016, plain=True)
    # steel action
    p += [body(S, [(-0.092, 0.012, 0.026, 0.028), (-0.040, 0.014, 0.028, 0.036), (0.108, 0.014, 0.028, 0.036), (0.126, 0.010, 0.024, 0.028)], power=4)]
    p += [B(S, -0.012, 0.026, -0.090, 0.024, 0.024, 0.210, plain=True)] + rail(S, -0.088, 0.120, 0.048)  # full optic rail on a solid base
    # bolt body + handle out the right (firer's right = -x)
    p += [turned(GUN_BARE, [(-0.060, 0.012), (0.060, 0.012)], x=-0.010, y=0.020, seg=10)]
    charge = [bar(GUN_BARE, (-HW - 0.002, 0.020, 0.030), (-HW - 0.030, 0.000, 0.030), 0.006), turned(GUN_BARE, [(0.024, 0.0), (0.028, 0.011), (0.040, 0.012), (0.043, 0.0)], y=0.000, x=-HW - 0.034, seg=8)]
    # heavy barrel
    # heavy barrel, a touch stepped at the chamber (a smooth bull barrel reads cleaner than thin fluting)
    p += [turned(S, [(0.126, 0.022), (0.150, 0.021), (0.164, 0.019), (mz - 0.010, 0.017), (mz, 0.017)], seg=16), bore(mz + (0.044 if brake else 0.0), 0.011)]
    # muzzle brake or crown
    if brake:
        p += [turned(S, [(mz, 0.015), (mz + 0.004, 0.022), (mz + 0.044, 0.022), (mz + 0.048, 0.018)], seg=12), bore(mz + 0.048, 0.011)]
        for a in (0, 90, 180, 270):
            p += [B(K, -0.0018, 0.016, mz + 0.012, 0.0036, 0.0008, 0.024, roll=a, pivot=(0, 0, mz + 0.024), plain=True)]
        muzzle_tip = mz + 0.048
    else:
        p += [turned(S, [(mz, 0.017), (mz + 0.006, 0.019), (mz + 0.012, 0.017)], seg=12), bore(mz + 0.012, 0.012)]
        muzzle_tip = mz + 0.012
    # trigger group
    p += sweep(S, [(0.038, -0.046), (0.048, -0.078), (0.010, -0.092), (-0.050, -0.086), (-0.064, -0.052)], 0.014, 0.0026, power=5, seg=8, ends="flat")
    trigger = sweep(S, [(-0.006, -0.046), (-0.004, -0.060), (0.000, -0.070), (-0.004, -0.078)], 0.006, 0.006, power=3, seg=8)
    # bipod lug and folded bipod legs tucked under the forend (attached to the lug)
    p += [B(S, -0.011, -0.038, 0.276, 0.022, 0.014, 0.032, plain=True)]
    p += [bar(K, (0.009, -0.034, 0.300), (0.016, -0.108, 0.244), 0.007), bar(K, (-0.009, -0.034, 0.300), (-0.016, -0.108, 0.244), 0.007)]
    p += [label(S, mark, HW + 0.0002, -0.030, -0.060, 0.044, 0.0038)]
    # short detachable magazine, funnelled into the belly so it meets the receiver
    p += [body(ST, [(0.044, -0.022, 0.030, 0.046), (0.062, -0.024, 0.032, 0.050), (0.080, -0.020, 0.028, 0.040)], power=3.4)]  # magwell
    depth = 0.052
    path = [(0.062, -0.030), (0.064, -0.082 - cap * 0.004), (0.066, -0.104 - cap * 0.006)]
    mag = sweep(GUN_ALU, path, 0.0225, depth, power=8, ends="flat")
    mag += sweep(GUN_ALU, [path[-1], (path[-1][0] + 0.002, path[-1][1] - 0.010)], 0.025, depth + 0.004, power=6, ends="flat")
    mag += cartridge(case, 0.058, -0.024, cl, cd, nk, bl)
    carrier = [B(GUN_BARE, -0.008, 0.020, 0.010, 0.016, 0.006, 0.070, plain=True)]
    return gun(p, [bone("muzzle", (0, 0, mz), []), bone("bolt", (0, 0.020, 0.03), carrier), bone("charge", (0, 0, 0), charge),
                   bone("trigger", (0, -0.046, 0.000), trigger), bone("mag", (0, -0.046, 0.062), mag)],
               dict(muzzle=(0, 0, muzzle_tip), thread=(0, 0, mz), eject=(-0.028, 0.020, 0.03), sight=(0, 0.062, -0.040), front=(0, 0.062, mz - 0.020),
                    optic=(0, 0.068, -0.010), under=(0, -0.040, 0.280), grip=(0, -0.104, -0.056), hand=(0, -0.034, 0.230), mag=(0, -0.046, 0.062), charge=(-HW - 0.034, 0.000, 0.030)))


def awm():
    """AI AWM, .338 Lapua: the Arctic Warfare chassis in green, a fluted barrel and a five-round box. Bolt-action."""
    return _bolt("AWM .338", stockmat=OD_GREEN, rnd=(0.0710, 0.0132, 0.0086, 0.026), case=CASE_BRASS, cap=5, barrelL=0.510, stock="thumbhole", brake=True)


def m24():
    """M24 SWS, 7.62x51: the US Army sniper rifle on a Remington 700 action in a composite stock. Bolt-action."""
    return _bolt("M24 SWS", stockmat=_mat("m24", (40, 42, 38), "polymer"), rnd=R762NATO, case=CASE_BRASS, cap=5, barrelL=0.480, stock="classic", brake=False)


def m40():
    """M40A5, 7.62x51: the USMC sniper rifle in a green McMillan stock with a threaded muzzle. Bolt-action."""
    return _bolt("M40A5", stockmat=OD_GREEN, rnd=R762NATO, case=CASE_BRASS, cap=5, barrelL=0.490, stock="classic", brake=True)


def sv98():
    """SV-98, 7.62x54R: the Russian bolt rifle with a thumbhole stock and a ten-round magazine. Bolt-action."""
    return _bolt("SV-98", stockmat=_mat("sv98", (52, 44, 32), "laminate"), rnd=(0.0537, 0.0124, 0.0085, 0.022), case=CASE_STEEL, cap=10, barrelL=0.500, stock="thumbhole", brake=True)


def trg42():
    """Sako TRG-42, .338 Lapua: the Finnish precision rifle, black chassis and a folding thumbhole stock. Bolt-action."""
    return _bolt("TRG-42", stockmat=_mat("trg", (34, 36, 38), "polymer"), rnd=(0.0710, 0.0132, 0.0086, 0.026), case=CASE_BRASS, cap=5, barrelL=0.510, stock="thumbhole", brake=True)


# ---------------------------------------------------------------- V5 grenade launcher
def _gl_round(z0, body_mat, tip_mat):
    """A 40mm low-velocity grenade: the fat aluminium body with its driving band, and the ogive nose cap."""
    r = 0.019
    p = [turned(body_mat, [(z0, 0.0), (z0 + 0.004, r * 0.6), (z0 + 0.009, r), (z0 + 0.012, r * 1.04), (z0 + 0.018, r * 1.04), (z0 + 0.021, r),
                           (z0 + 0.060, r), (z0 + 0.064, r * 0.92)], seg=16)]
    p += [turned(tip_mat, [(z0 + 0.064, r * 0.92), (z0 + 0.078, r * 0.66), (z0 + 0.092, r * 0.30), (z0 + 0.100, r * 0.10)], seg=16)]
    return p


def ammo_gl_he():
    """A loose 40mm HE grenade round, the brass case under the olive body."""
    p = [turned(_mat("gl_case", (178, 150, 92), "gunmetal"), [(0.0, 0.0), (0.0, 0.020), (0.018, 0.020), (0.020, 0.019)], seg=16)]
    p += _gl_round(0.018, _mat("gl_he", (92, 98, 66), "gunmetal"), _mat("gl_he_tip", (128, 100, 44), "gunmetal"))
    return gun(p, [], dict(muzzle=(0, 0, 0.12)))


def ammo_gl_smoke():
    """A loose 40mm smoke grenade round."""
    p = [turned(_mat("gl_case", (178, 150, 92), "gunmetal"), [(0.0, 0.0), (0.0, 0.020), (0.018, 0.020), (0.020, 0.019)], seg=16)]
    p += _gl_round(0.018, _mat("gl_smoke", (74, 76, 78), "gunmetal"), _mat("gl_smoke_tip", (130, 130, 64), "flat"))
    return gun(p, [], dict(muzzle=(0, 0, 0.12)))


def m320():
    """M320 grenade launcher, 40x46mm: a stand-alone break-action launcher with a pistol grip, a flip ladder sight and
    one round in the breech — high-explosive or smoke."""
    S, K = GUN_STEEL, GUN_BLACK
    HW = 0.022
    p = [body(K, [(-0.046, -0.004, 0.030, 0.040), (-0.010, -0.004, 0.036, 0.048), (0.050, -0.004, 0.036, 0.048), (0.066, -0.002, 0.030, 0.038)], power=4)]
    p += [turned(S, [(0.060, 0.025), (0.076, 0.028), (0.300, 0.027), (0.316, 0.026), (0.320, 0.024)], seg=16), bore(0.320, 0.020)]
    p += [turned(K, [(0.070, 0.030), (0.088, 0.030)], seg=14)]  # barrel collar
    p += [bar(S, (0.0, -0.034, 0.050), (0.0, -0.034, 0.066), 0.010)]  # hinge pin
    # pistol grip and trigger group (joined to the receiver belly, no gap)
    p += [body(K, [(-0.058, -0.020, 0.028, 0.030), (-0.010, -0.022, 0.034, 0.034), (0.030, -0.020, 0.030, 0.028)], power=3.4)]  # trigger housing under the breech
    p += sweep(POLYMER, [(-0.014, -0.028), (-0.024, -0.064), (-0.040, -0.110), (-0.054, -0.150)], [0.024, 0.025, 0.027, 0.028], [0.030, 0.033, 0.036, 0.040], power=3.2)
    for i in range(3):
        p += P(K, 0.0205, -0.064 - i * 0.022, -0.032 - i * 0.010, 0.0012, 0.0030, 0.016, plain=True)
    p += sweep(S, [(0.026, -0.044), (0.036, -0.080), (0.000, -0.094), (-0.056, -0.086), (-0.070, -0.048)], 0.014, 0.0026, power=5, seg=8, ends="flat")
    trigger = sweep(S, [(-0.012, -0.046), (-0.010, -0.060), (-0.006, -0.070), (-0.010, -0.078)], 0.006, 0.006, power=3, seg=8)
    # flip ladder sight (rear leaf and front post) and a short accessory rail, on a riser blended into the receiver
    p += [B(K, -0.013, 0.016, -0.044, 0.026, 0.016, 0.090, plain=True)]  # sight riser
    p += rail(K, -0.044, 0.044, 0.030)
    p += [B(S, -0.009, 0.036, -0.034, 0.018, 0.026, 0.008, plain=True)]  # folded rear ladder on the rail
    p += [B(S, -0.0018, 0.026, 0.296, 0.0036, 0.028, 0.008, plain=True), bar(S, (0, 0.026, 0.296), (0, 0.052, 0.296), 0.0022)]  # front post off the barrel
    p += [label(K, "M320", HW + 0.0002, -0.028, -0.030, 0.038, 0.0038)]
    selector = slab(S, 0.003, [(-0.020, -0.020), (0.004, -0.022), (0.004, -0.010), (-0.020, -0.008)], x=HW + 0.002, edge=0.001, r=0.002)
    he = _gl_round(0.044, _mat("gl_he", (92, 98, 66), "gunmetal"), _mat("gl_he_tip", (128, 100, 44), "gunmetal"))
    smoke = _gl_round(0.044, _mat("gl_smoke", (74, 76, 78), "gunmetal"), _mat("gl_smoke_tip", (130, 130, 64), "flat"))
    return gun(p, [bone("trigger", (0, -0.046, 0.000), trigger), bone("selector", (HW, -0.014, -0.014), selector),
                   bone("mag", (0, 0, 0), he), bone("frag", (0, 0, 0), smoke)],
               dict(muzzle=(0, 0, 0.320), thread=(0, 0, 0.320), eject=(0, 0, -0.4), sight=(0, 0.062, -0.030), front=(0, 0.052, 0.296),
                    optic=(0, 0.040, -0.010), under=(0, -0.034, 0.180), grip=(0, -0.104, -0.044), hand=(0, -0.030, 0.180), mag=(0, 0, 0), charge=(0, 0, 0)))


# ---------------------------------------------------------------- M4A1
def m4a1():
    """M4A1 carbine, 5.56x45: a round-topped upper receiver with its rail, forward assist and case deflector, the
    lower with its flared magazine well and trigger guard, quad-rail handguard, A-frame front sight, flip-up rear
    sight, six-position stock on the buffer tube and a 30-round aluminium magazine. 838 mm long."""
    A, S, PL = GUN_GREY, GUN_STEEL, POLYMER
    HW = 0.0145
    # upper receiver: round below the rail, thickening at the barrel end
    p = [body(A, [(-0.108, 0.006, 0.029, 0.040), (-0.100, 0.006, 0.031, 0.046), (0.090, 0.006, 0.031, 0.046), (0.105, 0.004, 0.034, 0.046)], power=3.6)]
    p += [B(A, -0.0085, 0.026, -0.104, 0.017, 0.006, 0.209, plain=True)] + rail(A, -0.102, 0.105, 0.028)
    # forward assist, case deflector, ejection port with the dust cover hanging open under it
    p += slab(A, 0.010, [(-0.044, -0.006), (-0.012, 0.004), (-0.012, 0.020), (-0.044, 0.022)], x=-HW - 0.004, edge=0.003, r=0.004)
    p += [lathe(A, "z", -HW - 0.007, 0.010, [(-0.082, 0.0058), (-0.040, 0.0068)], plain=True, seg=10),
          lathe(S, "z", -HW - 0.007, 0.010, [(-0.094, 0.0), (-0.094, 0.0078), (-0.082, 0.0078), (-0.082, 0.0)], plain=True, seg=10)]
    p += [B(BLACK, -HW - 0.0012, -0.004, -0.006, 0.0016, 0.019, 0.072, plain=True)]
    p += slab(A, 0.003, [(-0.008, -0.030), (0.068, -0.030), (0.068, -0.008), (-0.008, -0.008)], x=-HW - 0.003, edge=0.001, r=0.003)
    # lower receiver: slim behind, the magazine well deep and flared in front, sloping up to the buffer tower
    p += [body(A, [(-0.108, -0.028, 0.026, 0.030), (-0.060, -0.032, 0.029, 0.038), (0.030, -0.032, 0.029, 0.038), (0.036, -0.036, 0.030, 0.046)], power=5)]
    p += slab(A, 0.031, [(0.034, -0.014), (0.105, -0.014), (0.107, -0.050), (0.103, -0.090), (0.040, -0.080), (0.034, -0.052)], edge=0.004, r=0.006)
    p += [body(A, [(-0.138, -0.004, 0.034, 0.056), (-0.106, -0.004, 0.034, 0.056)], power=3.2)]
    p += sweep(A, [(-0.020, -0.050), (-0.012, -0.079), (0.030, -0.081), (0.040, -0.074)], 0.014, 0.003, power=6, seg=8, ends="flat")
    # bolt catch, magazine button, pins and the selector markings
    p += slab(S, 0.003, [(0.026, -0.016), (0.040, -0.016), (0.040, 0.008), (0.032, 0.012), (0.026, 0.006)], x=HW + 0.0015, edge=0.001, r=0.002)
    p += [lathe(S, "x", -0.030, 0.034, [(-HW - 0.004, 0.0), (-HW - 0.004, 0.0048), (-HW, 0.0048)], plain=True, seg=8)]
    for y, z in ((-0.028, 0.020), (-0.024, -0.010), (-0.020, 0.098), (-0.036, -0.092)):
        p += pin(HW, y, z, 0.0055, S)
    p += [label(A, "SAFE", HW + 0.0002, -0.016, -0.058, 0.018, 0.0028), label(A, "SEMI", HW + 0.0002, -0.034, -0.036, 0.018, 0.0028),
          label(A, "AUTO", HW + 0.0002, -0.016, -0.014, 0.018, 0.0028), label(A, "M4A1 CAL 5.56MM", 0.0157, -0.050, 0.044, 0.052, 0.0034)]
    # pistol grip with its finger rest and ribbed back
    p += sweep(PL, [(-0.034, -0.044), (-0.040, -0.064), (-0.054, -0.104), (-0.070, -0.146)], [0.027, 0.028, 0.030, 0.031], [0.036, 0.038, 0.042, 0.045], power=3.4)
    p += sweep(PL, [(-0.030, -0.074), (-0.026, -0.080)], 0.024, 0.014, power=3, seg=8)
    # barrel nut, quad-rail handguard, gas block with the front sight, barrel with its grenade-launcher cut, flash hider
    p += [turned(S, [(0.105, 0.034), (0.110, 0.040), (0.126, 0.040), (0.132, 0.034)], seg=14)]
    p += [body(A, [(0.130, 0.0, 0.040, 0.040), (0.136, 0.0, 0.0445, 0.0445), (0.296, 0.0, 0.0445, 0.0445), (0.302, 0.0, 0.040, 0.040)], power=3.2)]
    p += rail(A, 0.136, 0.298, 0.0215) + rail(A, 0.136, 0.298, -0.0215, up=-1)
    p += side_rail(A, 0.136, 0.298, 0.0215, 0.0, 1) + side_rail(A, 0.136, 0.298, -0.0215, 0.0, -1)
    p += [turned(S, [(0.300, 0.0185), (0.372, 0.0185), (0.372, 0.0150), (0.410, 0.0150), (0.410, 0.0185), (0.456, 0.0185)], seg=12)]
    p += slab(S, 0.019, [(0.306, -0.012), (0.346, -0.012), (0.346, 0.012), (0.339, 0.046), (0.329, 0.046), (0.306, 0.016)], edge=0.004, r=0.004)
    p += [B(S, -0.0082, 0.045, 0.328, 0.0018, 0.022, 0.012, plain=True), B(S, 0.0064, 0.045, 0.328, 0.0018, 0.022, 0.012, plain=True),
          bar(S, (0, 0.044, 0.3345), (0, 0.066, 0.3345), 0.0022), B(S, -0.006, -0.023, 0.318, 0.012, 0.012, 0.006, plain=True)]
    hider = [turned(S, [(0.452, 0.0185), (0.458, 0.022), (0.492, 0.022), (0.496, 0.019)], seg=12), bore(0.496, 0.011)]
    for a in (0, 72, 144, 216, 288):
        hider += [B(BLACK, -0.0018, 0.0106, 0.468, 0.0036, 0.0008, 0.020, roll=a, pivot=(0, 0, 0.478), plain=True)]
    # receiver extension with its castle nut, and the collapsible stock riding on it
    p += [turned(A, [(-0.136, 0.030), (-0.300, 0.030), (-0.300, 0.0)], seg=14), turned(S, [(-0.138, 0.037), (-0.148, 0.037)], seg=12)]
    p += [body(PL, [(-0.162, -0.002, 0.036, 0.044), (-0.170, -0.002, 0.040, 0.048), (-0.300, -0.002, 0.040, 0.048), (-0.318, -0.002, 0.038, 0.046)], power=3.4)]
    p += slab(PL, 0.034, [(-0.236, -0.018), (-0.322, -0.018), (-0.336, -0.030), (-0.336, -0.102), (-0.322, -0.106), (-0.262, -0.040)], edge=0.006, r=0.007,
              marks=[("cap0", "slot", 0.42, 0.80, 0.014, 0.026), ("cap1", "slot", 0.42, 0.80, 0.014, 0.026)])
    p += slab(PL, 0.040, [(-0.316, 0.022), (-0.336, 0.018), (-0.336, -0.104), (-0.316, -0.100)], edge=0.006, r=0.006)
    p += slab(RUBBER_PAD, 0.038, [(-0.335, 0.016), (-0.342, 0.013), (-0.342, -0.101), (-0.335, -0.104)], edge=0.004, r=0.003)
    p += sweep(PL, [(-0.200, -0.030), (-0.250, -0.034)], 0.012, 0.012, power=3, seg=8)                      # adjustment lever

    carrier = [B(GUN_BARE, -HW - 0.0008, -0.002, -0.002, 0.004, 0.016, 0.066, plain=True)]
    charge = slab(A, 0.040, [(-0.122, 0.021), (-0.106, 0.021), (-0.106, 0.029), (-0.122, 0.029)], edge=0.003, r=0.002)
    charge += [B(A, -0.004, 0.021, -0.108, 0.008, 0.005, 0.05, plain=True)]
    trigger = sweep(S, [(0.010, -0.048), (0.011, -0.060), (0.014, -0.070), (0.011, -0.077)], 0.006, 0.006, power=3, seg=8)
    selector = [lathe(S, "x", -0.024, -0.036, [(HW, 0.005), (HW + 0.003, 0.005), (HW + 0.003, 0.0)], plain=True, seg=10)]
    selector += slab(S, 0.004, [(-0.060, -0.0265), (-0.036, -0.0275), (-0.036, -0.0205), (-0.060, -0.0215)], x=HW + 0.002, edge=0.001, r=0.002)
    irons = [B(S, -0.010, 0.0375, -0.094, 0.020, 0.006, 0.018, plain=True), B(S, -0.008, 0.0435, -0.084, 0.0035, 0.024, 0.004, plain=True),
             B(S, 0.0045, 0.0435, -0.084, 0.0035, 0.024, 0.004, plain=True), B(S, -0.008, 0.0600, -0.084, 0.016, 0.003, 0.004, plain=True),
             B(S, -0.0045, 0.0570, -0.084, 0.003, 0.003, 0.004, plain=True), B(S, 0.0015, 0.0570, -0.084, 0.003, 0.003, 0.004, plain=True),
             B(S, -0.008, 0.0675, -0.084, 0.016, 0.0025, 0.004, plain=True)]
    # magazine: straight in the well, then curving forward; pressed ribs down its sides
    path = [(0.070, -0.046), (0.070, -0.130)] + arc_path(0.070, -0.130, 0.100, 0.230, 5)[1:]
    mag = sweep(GUN_ALU, path, 0.0225, 0.060, power=8, ends="flat")
    for sx in (-1, 1):
        mag += sweep(GUN_ALU, [(q[0] - 0.012, q[1]) for q in path[1:]], 0.0015, 0.007, power=3, seg=6, x=sx * 0.0115, ends="flat")
        mag += sweep(GUN_ALU, [(q[0] + 0.012, q[1]) for q in path[1:]], 0.0015, 0.007, power=3, seg=6, x=sx * 0.0115, ends="flat")
    mag += sweep(GUN_ALU, [path[-1], (path[-1][0] + (path[-1][0] - path[-2][0]) * 0.25, path[-1][1] + (path[-1][1] - path[-2][1]) * 0.25)], 0.0255, 0.066, power=6, ends="flat")
    mag += cartridge(CASE_BRASS, 0.046, -0.042, 0.0447, 0.0095, 0.0063, 0.012)
    return gun(p, [bone("muzzle", (0, 0, 0.452), hider), bone("bolt", (0, 0.006, 0.03), carrier), bone("charge", (0, 0.026, -0.105), charge),
                   bone("trigger", (0, -0.050, 0.010), trigger), bone("selector", (HW, -0.024, -0.036), selector),
                   bone("irons", (0, 0.0375, -0.078), irons), bone("mag", (0, -0.050, 0.070), mag)],
               dict(muzzle=(0, 0, 0.496), thread=(0, 0, 0.452), eject=(-0.02, 0.006, 0.03), sight=(0, 0.066, -0.082), front=(0, 0.066, 0.3345),
                    optic=(0, 0.0375, 0.030), under=(0, -0.0295, 0.215), grip=(0, -0.100, -0.056), hand=(0, -0.030, 0.230), mag=(0, -0.050, 0.070),
                    charge=(0.0, 0.026, -0.118)))


# ---------------------------------------------------------------- SVD
def _svd(mark="SVD 7.62"):
    """SVD Dragunov, 7.62x54R: a long thin barrel, slotted flash hider, vented laminated handguards, the skeleton
    stock with its cheek rest, and a ten-round magazine. 1225 mm long. The PSO-1 scope is a separate fitting."""
    S, K, WD = GUN_STEEL, GUN_BLACK, LAMINATE
    HW = 0.0165
    p = [body(K, [(-0.124, -0.004, 0.030, 0.044), (-0.114, -0.008, 0.033, 0.054), (-0.050, -0.012, 0.033, 0.064), (0.142, -0.012, 0.033, 0.064),
                  (0.164, -0.004, 0.033, 0.048), (0.170, -0.002, 0.030, 0.042)], power=7)]
    p += [body(K, [(-0.120, 0.022, 0.026, 0.022), (-0.112, 0.021, 0.0335, 0.042), (0.118, 0.021, 0.0335, 0.044), (0.126, 0.021, 0.030, 0.038)], power=2.6)]
    for z in (-0.092, -0.050, -0.008, 0.034):
        p += [body(K, [(z, 0.021, 0.0335, 0.042), (z + 0.003, 0.021, 0.0347, 0.0448), (z + 0.026, 0.021, 0.0347, 0.0448), (z + 0.029, 0.021, 0.0335, 0.042)], power=2.6)]
    # rear sight on the front of the receiver
    p += slab(S, 0.025, [(0.120, 0.016), (0.252, 0.016), (0.252, 0.030), (0.226, 0.0405), (0.150, 0.0405), (0.120, 0.036)], edge=0.004)
    p += [B(S, -0.0055, 0.0405, 0.150, 0.011, 0.0035, 0.085, pitch=-2, pivot=(0, 0.0405, 0.232), plain=True),
          B(S, -0.008, 0.0395, 0.190, 0.016, 0.006, 0.010, plain=True), turned(S, [(0.224, 0.009), (0.236, 0.009)], y=0.0405),
          B(S, -0.0065, 0.0435, 0.148, 0.005, 0.0065, 0.004, plain=True), B(S, 0.0015, 0.0435, 0.148, 0.005, 0.0065, 0.004, plain=True)]
    # handguards: two laminated halves, slimmer towards the muzzle, six slots a side, held by steel rings
    hg = lambda z: 0.047 - (z - 0.215) / 0.255 * 0.007
    p += [body(WD, [(0.213, 0.002, 0.042, 0.062), (0.222, 0.002, hg(0.222), 0.068), (0.462, 0.003, hg(0.462), 0.058), (0.472, 0.003, 0.036, 0.052)], power=3.0)]
    for i in range(6):
        z = 0.244 + i * 0.036
        p += P(BLACK, hg(z + 0.011) / 2 - 0.0012, -0.003, z, 0.0014, 0.011, 0.022, plain=True)
    p += [body(S, [(0.203, 0.000, 0.047, 0.070), (0.215, 0.000, 0.047, 0.070)], power=3.4), body(S, [(0.466, 0.003, 0.042, 0.060), (0.478, 0.003, 0.042, 0.060)], power=3.2)]
    # barrel, gas block, front sight in its hood, flash hider with five slots
    p += [turned(S, [(0.165, 0.024), (0.205, 0.024), (0.205, 0.021), (0.480, 0.019), (0.726, 0.0155)], seg=12), turned(S, [(0.476, 0.014), (0.560, 0.014)], y=0.024, seg=10)]
    p += slab(S, 0.023, [(0.520, -0.012), (0.564, -0.012), (0.564, 0.034), (0.520, 0.034)], edge=0.004, r=0.006)
    p += slab(S, 0.019, [(0.688, -0.010), (0.724, -0.010), (0.724, 0.012), (0.716, 0.028), (0.697, 0.028), (0.688, 0.012)], edge=0.0035)
    p += [lathe(S, "z", 0.0, 0.045, [(0.699, 0.0125), (0.715, 0.0125)], plain=True, open=True, seg=12),
          lathe(S, "z", 0.0, 0.045, [(0.715, 0.0110), (0.699, 0.0110)], plain=True, open=True, seg=12),
          bar(S, (0, 0.026, 0.707), (0, 0.050, 0.707), 0.0024), B(S, -0.004, -0.021, 0.700, 0.008, 0.012, 0.016, plain=True)]
    hider = [turned(S, [(0.722, 0.018), (0.730, 0.022), (0.790, 0.020), (0.800, 0.017)], seg=12), bore(0.800, 0.0105)]
    for a in (0, 72, 144, 216, 288):
        hider += [B(BLACK, -0.002, 0.0098, 0.738, 0.004, 0.0008, 0.046, roll=a, pivot=(0, 0, 0.76), plain=True)]
    # trigger guard and magazine catch
    p += sweep(S, [(-0.030, -0.046), (-0.014, -0.077), (0.040, -0.079), (0.052, -0.072), (0.054, -0.042)], 0.015, 0.0026, power=6, seg=8, ends="flat")
    p += slab(S, 0.010, [(0.046, -0.042), (0.054, -0.042), (0.053, -0.086), (0.044, -0.090)], edge=0.002, r=0.002)
    # scope rail on the left wall, pins and markings
    p += slab(S, 0.0048, [(-0.092, -0.020), (0.068, -0.020), (0.072, -0.008), (0.068, 0.006), (-0.092, 0.006)], x=HW + 0.0022, edge=0.0015, r=0.002)
    p += [B(S, HW + 0.0046, -0.016, -0.084, 0.002, 0.018, 0.148, plain=True)]
    for y, z in ((0.006, 0.140), (-0.014, 0.140), (0.004, -0.106), (-0.012, 0.006), (-0.020, -0.022)):
        p += pin(HW, y, z)
    p += [label(K, mark, HW + 0.0002, -0.034, 0.086, 0.045, 0.0042)]
    # skeleton stock, all of it rounded timber: wrist and grip, upper and lower limbs, the butt; clip-on cheek rest
    p += slab(WD, 0.031, [(-0.050, -0.044), (-0.116, -0.034), (-0.122, 0.014), (-0.176, 0.006), (-0.170, -0.030), (-0.120, -0.070)], edge=0.008, r=0.008)
    p += sweep(WD, [(-0.160, -0.004), (-0.280, -0.014), (-0.404, -0.024)], 0.030, [0.030, 0.027, 0.028], power=3.2)
    p += sweep(WD, [(-0.100, -0.050), (-0.122, -0.088), (-0.140, -0.122)], [0.031, 0.031, 0.032], [0.044, 0.040, 0.036], power=3.2)
    p += sweep(WD, [(-0.132, -0.116), (-0.260, -0.118), (-0.404, -0.118)], 0.030, 0.026, power=3.2)
    p += sweep(WD, [(-0.402, -0.006), (-0.404, -0.132)], 0.031, 0.036, power=3.6, ends="flat")
    p += slab(S, 0.032, [(-0.419, -0.004), (-0.426, -0.006), (-0.426, -0.132), (-0.419, -0.134)], edge=0.004, r=0.003)
    p += [body(BLACK, [(-0.350, 0.000, 0.034, 0.036), (-0.342, 0.001, 0.038, 0.046), (-0.222, 0.006, 0.038, 0.046), (-0.214, 0.005, 0.034, 0.036)], power=2.6)]

    carrier = slab(GUN_BARE, 0.008, [(0.030, 0.004), (0.130, 0.004), (0.130, 0.032), (0.030, 0.032)], x=-0.0148, edge=0.002, r=0.003)
    carrier += [bar(GUN_BARE, (-0.016, 0.018, 0.118), (-0.038, 0.018, 0.118), 0.011), turned(GUN_BARE, [(0.106, 0.0), (0.110, 0.012), (0.122, 0.013), (0.125, 0.0)], y=0.0175, x=-0.042, seg=8)]
    trigger = sweep(S, [(0.012, -0.043), (0.014, -0.055), (0.017, -0.065), (0.014, -0.072)], 0.006, 0.006, power=3, seg=8)
    selector = slab(S, 0.0016, [(-0.050, 0.014), (0.062, 0.010), (0.066, 0.003), (0.062, -0.003), (-0.032, -0.005), (-0.050, -0.003)], x=-HW - 0.0010, edge=0.0005, r=0.003)
    selector += [B(S, -HW - 0.0075, -0.002, 0.056, 0.006, 0.004, 0.010, plain=True), lathe(S, "x", 0.004, -0.040, [(-HW - 0.003, 0.0045), (-HW, 0.0045)], plain=True, seg=8)]
    path = arc_path(0.094, -0.038, 0.140, 0.420, 6)
    mag = sweep(K, path, 0.0275, 0.072, power=7, ends="flat")
    for k in (1, 3):
        mag += sweep(K, [path[k], path[k + 1]], 0.0295, 0.050, power=6, seg=10, ends="flat")
    mag += sweep(K, [path[-1], (path[-1][0] + (path[-1][0] - path[-2][0]) * 0.3, path[-1][1] + (path[-1][1] - path[-2][1]) * 0.3)], 0.031, 0.078, power=6, ends="flat")
    mag += cartridge(CASE_STEEL, 0.058, -0.034, 0.0537, 0.0124, 0.0085, 0.022)
    return gun(p, [bone("muzzle", (0, 0, 0.722), hider), bone("bolt", (0, 0.018, 0.07), carrier), bone("trigger", (0, -0.044, 0.012), trigger),
                   bone("selector", (-HW, 0.004, -0.040), selector), bone("mag", (0, -0.044, 0.135), mag)],
               dict(muzzle=(0, 0, 0.800), thread=(0, 0, 0.722), eject=(-0.02, 0.02, 0.08), sight=(0, 0.050, 0.148), front=(0, 0.050, 0.707),
                    optic=(0, 0.0465, -0.005), under=(0, -0.030, 0.30), grip=(0, -0.096, -0.124), hand=(0, -0.034, 0.330), mag=(0, -0.044, 0.094),
                    charge=(-0.042, 0.018, 0.118)))


def svd():
    """SVD Dragunov, 7.62x54R, the classic Soviet marksman rifle."""
    return _svd("SVD 7.62")


def psl():
    """PSL (Romanian), 7.62x54R: the Dragunov-pattern marksman rifle, longer and on an RPK-style receiver."""
    return _svd("PSL 7.62")


# ---------------------------------------------------------------- PKM
def pkm():
    """PKM, 7.62x54R: belt-fed from a 100-round box under the receiver, fluted barrel with a carry handle, gas
    cylinder underneath, folding bipod and a laminated skeleton stock. 1192 mm long."""
    S, K, WD = GUN_STEEL, GUN_BLACK, LAMINATE
    HW = 0.022
    p = [body(K, [(-0.094, -0.004, 0.040, 0.060), (-0.084, -0.008, 0.044, 0.074), (-0.040, -0.010, 0.044, 0.080), (0.276, -0.010, 0.044, 0.080),
                  (0.288, -0.008, 0.042, 0.070)], power=8)]
    for z in (-0.05, 0.02, 0.09, 0.16, 0.23):                                                         # pressed ribs
        p += slab(K, 0.0016, [(z, -0.036), (z + 0.030, -0.036), (z + 0.030, 0.014), (z, 0.014)], x=HW + 0.0004, edge=0.0006, r=0.004)
        p += slab(K, 0.0016, [(z, -0.036), (z + 0.030, -0.036), (z + 0.030, 0.014), (z, 0.014)], x=-HW - 0.0004, edge=0.0006, r=0.004)
    p += [label(K, "PKM 7.62", HW + 0.0014, -0.012, 0.100, 0.045, 0.0045)]
    # feed tray, with the belt entering from the right and the links leaving on the left
    p += slab(S, 0.022, [(0.050, 0.026), (0.140, 0.026), (0.140, 0.034), (0.050, 0.034)], x=-0.033, edge=0.003, r=0.003)
    p += slab(S, 0.004, [(0.050, 0.006), (0.140, 0.006), (0.140, 0.034), (0.050, 0.034)], x=-0.0235, edge=0.001, r=0.003)
    p += [B(BLACK, HW + 0.0004, 0.004, 0.066, 0.0008, 0.018, 0.050, plain=True)]
    # trigger guard, pistol grip
    p += sweep(S, [(-0.024, -0.052), (-0.012, -0.083), (0.042, -0.085), (0.054, -0.078), (0.056, -0.048)], 0.015, 0.0026, power=6, seg=8, ends="flat")
    p += sweep(BAKELITE, [(-0.034, -0.046), (-0.040, -0.066), (-0.054, -0.104), (-0.070, -0.144)], [0.027, 0.028, 0.030, 0.032], [0.036, 0.038, 0.042, 0.046], power=3.2)
    # barrel: trunnion, flutes, carry handle, front sight and flash hider; gas cylinder underneath
    p += [turned(S, [(0.285, 0.042), (0.326, 0.042), (0.338, 0.031), (0.600, 0.026), (0.770, 0.020)], seg=14)]
    for a in range(0, 360, 45):
        p += [B(K, -0.0022, 0.0124, 0.350, 0.0044, 0.0016, 0.230, roll=a, pivot=(0, 0, 0.45), plain=True)]
    p += [bar(S, (0.0, 0.012, 0.318), (0.030, 0.050, 0.318), 0.008)] + sweep(WD, [(0.298, 0.050), (0.398, 0.050)], 0.024, 0.024, power=2.2, seg=10, x=0.030)
    p += [turned(S, [(0.285, 0.024), (0.300, 0.026), (0.300, 0.022), (0.622, 0.022)], y=-0.036, seg=12)]
    p += slab(S, 0.027, [(0.596, -0.050), (0.642, -0.050), (0.642, 0.010), (0.596, 0.010)], edge=0.004, r=0.006)
    p += slab(S, 0.019, [(0.733, -0.010), (0.767, -0.010), (0.767, 0.012), (0.759, 0.050), (0.743, 0.050), (0.733, 0.012)], edge=0.0035)
    p += [B(S, 0.0066, 0.049, 0.744, 0.0018, 0.025, 0.014, roll=-8, pivot=(0.0074, 0.05, 0.75), plain=True),
          B(S, -0.0084, 0.049, 0.744, 0.0018, 0.025, 0.014, roll=8, pivot=(-0.0074, 0.05, 0.75), plain=True),
          bar(S, (0, 0.048, 0.751), (0, 0.0785, 0.751), 0.0026)]
    hider = [turned(S, [(0.765, 0.020), (0.772, 0.026), (0.812, 0.024), (0.820, 0.020)], seg=12), bore(0.820, 0.011)]
    for a in (0, 72, 144, 216, 288):
        hider += [B(BLACK, -0.002, 0.0118, 0.778, 0.004, 0.0008, 0.032, roll=a, pivot=(0, 0, 0.79), plain=True)]
    # skeleton stock: rounded limbs round the big cut-out, steel butt plate
    p += slab(WD, 0.033, [(-0.088, 0.028), (-0.150, 0.022), (-0.165, -0.020), (-0.135, -0.046), (-0.088, -0.036)], edge=0.008, r=0.010)
    p += sweep(WD, [(-0.140, 0.008), (-0.240, 0.002), (-0.356, -0.006)], 0.032, [0.034, 0.030, 0.032], power=3.2)
    p += sweep(WD, [(-0.130, -0.028), (-0.200, -0.052), (-0.356, -0.096)], 0.032, [0.034, 0.030, 0.032], power=3.2)
    p += sweep(WD, [(-0.348, 0.010), (-0.352, -0.112)], 0.033, 0.040, power=3.6, ends="flat")
    p += slab(S, 0.034, [(-0.367, 0.012), (-0.374, 0.010), (-0.374, -0.110), (-0.367, -0.112)], edge=0.004, r=0.003)
    p += [body(S, [(-0.100, -0.006, 0.040, 0.054), (-0.088, -0.006, 0.040, 0.054)], power=5)]

    cover = [body(K, [(-0.088, 0.038, 0.038, 0.040), (-0.078, 0.036, 0.0445, 0.058), (0.190, 0.036, 0.0445, 0.058), (0.202, 0.036, 0.040, 0.046)], power=3.2)]
    for z in (-0.04, 0.03, 0.10):
        cover += [body(K, [(z, 0.036, 0.0445, 0.058), (z + 0.003, 0.036, 0.0458, 0.0606), (z + 0.037, 0.036, 0.0458, 0.0606), (z + 0.040, 0.036, 0.0445, 0.058)], power=3.2)]
    cover += [B(S, -0.010, 0.064, -0.064, 0.020, 0.008, 0.070, plain=True),
              B(S, -0.0065, 0.072, -0.058, 0.005, 0.0065, 0.004, plain=True), B(S, 0.0015, 0.072, -0.058, 0.005, 0.0065, 0.004, plain=True)]
    charge = [bar(GUN_BARE, (-HW, -0.020, 0.090), (-HW - 0.026, -0.020, 0.090), 0.012),
              turned(GUN_BARE, [(0.080, 0.0), (0.083, 0.013), (0.097, 0.014), (0.100, 0.0)], y=-0.020, x=-HW - 0.030, seg=8),
              B(S, -HW - 0.002, -0.026, 0.060, 0.002, 0.012, 0.060, plain=True)]
    trigger = sweep(S, [(0.010, -0.049), (0.012, -0.061), (0.015, -0.071), (0.012, -0.078)], 0.006, 0.006, power=3, seg=8)
    selector = [lathe(S, "x", -0.030, -0.020, [(HW, 0.005), (HW + 0.003, 0.005), (HW + 0.003, 0.0)], plain=True, seg=10)]
    selector += slab(S, 0.004, [(-0.044, -0.0325), (-0.020, -0.0335), (-0.020, -0.0265), (-0.044, -0.0275)], x=HW + 0.002, edge=0.001, r=0.002)
    bipod = []
    for sgn in (-1, 1):
        bipod += [bar(S, (sgn * 0.014, -0.050, 0.560), (sgn * 0.020, -0.058, 0.250), 0.010),
                  B(S, sgn * 0.020 - 0.006, -0.064, 0.240, 0.012, 0.004, 0.022, plain=True)]
    bipod += [body(S, [(0.552, -0.046, 0.038, 0.030), (0.568, -0.046, 0.038, 0.030)], power=3)]
    # ammunition box: an olive pressed-steel can hung across the gun under the receiver, well out to the right, its
    # lid held by a wire bail. The belt comes out of a mouth in the lid directly under the feed tray.
    BX, BELT = -0.034, -0.047                                                                         # box centre, belt line
    can = lambda mat, secs, power: skin(mat, [(z, BX, cy, w, h) for z, cy, w, h in secs], power=power, seg=20)
    box = [can(AMMO_GREEN, [(0.049, -0.118, 0.168, 0.112), (0.053, -0.118, 0.175, 0.120), (0.141, -0.118, 0.175, 0.120), (0.145, -0.118, 0.168, 0.112)], 9),
           can(AMMO_GREEN, [(0.048, -0.056, 0.178, 0.010), (0.146, -0.056, 0.178, 0.010)], 6),
           B(AMMO_GREEN, BX - 0.045, -0.125, 0.0486, 0.09, 0.022, 0.0003, yaw=180, plain=True, marks=[("front", "text:100 7.62", 0.5, 0.5, 0.011, (30, 30, 26))]),
           B(S, -0.004, -0.052, 0.090, 0.008, 0.004, 0.030, plain=True)]                                # hanger under the receiver
    for y in (-0.092, -0.118, -0.144):                                                                # pressed beads round the can
        box += [can(AMMO_GREEN, [(0.052, y, 0.1765, 0.006), (0.142, y, 0.1765, 0.006)], 4)]
    # the mouth: a dark opening in the lid with a raised lip, and the flap that covers it standing open behind
    box += [B(BLACK, BELT - 0.013, -0.0512, 0.054, 0.026, 0.0008, 0.084, plain=True),
            B(AMMO_GREEN, BELT - 0.016, -0.052, 0.051, 0.003, 0.006, 0.090, plain=True), B(AMMO_GREEN, BELT + 0.013, -0.052, 0.051, 0.003, 0.006, 0.090, plain=True),
            B(AMMO_GREEN, BELT - 0.016, -0.052, 0.138, 0.032, 0.006, 0.003, plain=True),
            B(AMMO_GREEN, BELT + 0.016, -0.052, 0.052, 0.0016, 0.030, 0.088, roll=-20, pivot=(BELT + 0.016, -0.052, 0.1), plain=True)]
    box += run(S, [(BX - 0.089, -0.075, 0.097), (BX - 0.093, -0.050, 0.097), (BX - 0.060, -0.044, 0.097)], 0.004)       # carrying strap loop
    # the belt: rounds side by side in their links, nose forward, rising out of the mouth and over into the tray
    belt = []
    rows = [(BELT, -0.060), (BELT, -0.043), (BELT, -0.026), (BELT, -0.009), (BELT + 0.002, 0.008), (BELT + 0.010, 0.022)]
    for i, (x, y) in enumerate(rows):
        belt += cartridge(CASE_STEEL, 0.056, y, 0.0537, 0.0124, 0.0085, 0.022, x=x)
        belt += [turned(GUN_STEEL, [(0.072, 0.0152), (0.092, 0.0152)], y=y, x=x, plain=True, open=True, seg=8)]
        if i:
            px, py = rows[i - 1]
            belt += [B(GUN_STEEL, min(x, px) - 0.0045, py + 0.004, 0.076, abs(x - px) + 0.009, y - py - 0.008, 0.012, plain=True)]   # link tab
    return gun(p, [bone("muzzle", (0, 0, 0.765), hider), bone("cover", (0, 0.034, 0.200), cover), bone("bolt", (0, -0.020, 0.09), charge),
                   bone("trigger", (0, -0.050, 0.010), trigger), bone("selector", (HW, -0.030, -0.020), selector),
                   bone("bipod", (0, -0.050, 0.560), bipod), bone("mag", (0, -0.056, 0.130), box),
                   dict(name="belt", pivot=(BELT, -0.02, 0.07), cubes=belt)],
               dict(muzzle=(0, 0, 0.820), thread=(0, 0, 0.765), eject=(0.026, 0.01, 0.085), sight=(0, 0.0785, -0.058), front=(0, 0.0785, 0.751),
                    optic=(0, 0.066, 0.05), under=(0, -0.05, 0.40), grip=(0, -0.100, -0.056), hand=(-0.034, -0.180, 0.097), mag=(-0.034, -0.076, 0.097),
                    charge=(-0.052, -0.020, 0.090), cover=(0, 0.072, -0.030), belt=(-0.060, -0.010, 0.085)))


# ---------------------------------------------------------------- M17
def _p320(name, F=0.0, GB=0.0):
    """The SIG P320 family: `F` moves the muzzle end of slide, barrel and frame (negative for the compact gun), `GB`
    lengthens the grip and its magazine downwards."""
    T, TS, S = POLYMER_TAN, _mat("gun_tan", (146, 124, 92), "gunmetal", wear=(96, 90, 82)), GUN_STEEL
    # frame: dust cover with its rail, the grip swelling to the palm, beavertail, trigger guard
    p = [body(T, [(-0.074, -0.020, 0.024, 0.014), (-0.060, -0.022, 0.0285, 0.020), (0.088 + F, -0.022, 0.0285, 0.020), (0.094 + F, -0.021, 0.026, 0.016)], power=5)]
    p += sweep(T, [(-0.034, -0.026), (-0.040, -0.050), (-0.050, -0.090 - GB * 0.5), (-0.060 - GB * 0.25, -0.124 - GB)], [0.0295, 0.030, 0.031, 0.0315], [0.050, 0.050, 0.052, 0.054], power=3.6)
    p += sweep(T, [(-0.060, -0.022), (-0.078, -0.018), (-0.086, -0.016)], [0.024, 0.022, 0.018], [0.012, 0.010, 0.007], power=3, seg=10)
    p += sweep(T, [(0.048, -0.028), (0.047, -0.060), (0.038, -0.072), (0.004, -0.073), (-0.016, -0.062)], 0.014, 0.0055, power=4, seg=8, ends="flat")
    p += rail(T, 0.048, 0.090 + F, -0.031, w=0.020, up=-1)
    for sx in (1, -1):                                                                                  # stippled grip panels
        p += [B(T, sx * 0.0152 - 0.0003, -0.118, -0.068, 0.0006, 0.074, 0.030, pitch=-14, pivot=(0, -0.08, -0.05), plain=True,
                marks=[("left" if sx > 0 else "right", "checker", 0.5, 0.5, 0.030, 0.074)])]
    p += [B(S, 0.0143, -0.022, 0.010, 0.0016, 0.005, 0.022, plain=True), B(S, 0.0143, -0.024, 0.040, 0.0016, 0.008, 0.012, plain=True),
          B(S, -0.0159, -0.024, 0.040, 0.0016, 0.008, 0.012, plain=True)]
    p += [label(T, name, 0.01435, -0.022, 0.062, 0.020, 0.0038)]
    # barrel showing at the muzzle, recoil spring guide under it
    p += [tube(S, 0.060, 0.1005 + F, 0.0142, seg=10), bore(0.1005 + F, 0.009), tube(S, 0.060, 0.0995 + F, 0.008, y=-0.0135)]

    slide = [body(TS, [(-0.104, 0.002, 0.024, 0.028), (-0.100, 0.0035, 0.026, 0.033), (0.090 + F, 0.0035, 0.026, 0.033), (0.099 + F, 0.002, 0.024, 0.028)], power=6)]
    for sx in (1, -1):                                                                                  # cocking serrations, back and front
        for z, ln in ((-0.094, 0.028), (0.058 + F, 0.024)):
            slide += [B(TS, sx * 0.0128 - 0.0003, -0.008, z, 0.0006, 0.022, ln, plain=True,
                        marks=[("left" if sx > 0 else "right", "grooves", 0.5, 0.5, ln, 0.022)])]
    slide += [B(BLACK, -0.0134, 0.004, 0.002, 0.0142, 0.0164, 0.034, plain=True),                               # ejection port
              B(GUN_BARE, -0.0128, 0.006, 0.004, 0.012, 0.012, 0.030, plain=True),                             # barrel hood
              B(S, -0.0016, 0.019, 0.084 + F, 0.0032, 0.007, 0.006, plain=True, marks=[("rear", "dot", 0.5, 0.4, 0.002, (150, 230, 120))]),
              B(S, -0.009, 0.019, -0.100, 0.0065, 0.007, 0.008, plain=True, marks=[("rear", "dot", 0.5, 0.4, 0.002, (150, 230, 120))]),
              B(S, 0.0025, 0.019, -0.100, 0.0065, 0.007, 0.008, plain=True, marks=[("rear", "dot", 0.5, 0.4, 0.002, (150, 230, 120))]),
              B(S, -0.009, 0.0175, -0.100, 0.018, 0.0025, 0.008, plain=True),
              B(TS, -0.010, 0.0195, -0.080, 0.020, 0.0012, 0.040, plain=True),                                 # optic cover plate
              label(TS, "SIG SAUER", 0.01305, 0.006, 0.010, 0.040, 0.0034)]
    trigger = sweep(S, [(0.016, -0.032), (0.017, -0.046), (0.020, -0.056), (0.017, -0.064)], 0.007, 0.006, power=3, seg=8)
    selector = [B(S, 0.0143, -0.019, -0.062, 0.0035, 0.005, 0.024, plain=True), B(S, -0.0178, -0.019, -0.062, 0.0035, 0.005, 0.024, plain=True)]
    mag = sweep(GUN_STEEL, [(-0.037, -0.016), (-0.049, -0.066), (-0.061 - GB * 0.25, -0.120 - GB)], 0.021, 0.040, power=6, ends="flat")
    mag += sweep(POLYMER_TAN, [(-0.061 - GB * 0.25, -0.121 - GB), (-0.064 - GB * 0.25, -0.134 - GB)], 0.030, 0.054, power=4, ends="flat")
    mag += cartridge(CASE_BRASS, -0.052, -0.013, 0.019, 0.0098, 0.0096, 0.010)
    for i in range(4):
        mag += [lathe(BLACK, "x", -0.045 - i * 0.018, -0.040 - i * 0.0045, [(-0.0108, 0.002), (0.0108, 0.002)], plain=True, seg=6)]
    return gun(p, [bone("bolt", (0, 0.0, 0.0), slide), bone("trigger", (0, -0.030, 0.016), trigger), bone("selector", (0, -0.0165, -0.040), selector),
                   bone("mag", (0, -0.070, -0.050), mag)],
               dict(muzzle=(0, 0, 0.1005 + F), thread=(0, 0, 0.1005 + F), eject=(-0.014, 0.012, 0.02), sight=(0, 0.026, -0.100), front=(0, 0.026, 0.087 + F),
                    optic=(0, 0.0212, -0.060), under=(0, -0.034, 0.07), grip=(0, -0.078, -0.047), hand=(0.012, -0.088, -0.040), mag=(0, -0.070, -0.050),
                    charge=(0.0, 0.016, -0.085)))



def m17():
    """M17 service pistol (SIG P320), 9x19: coyote slide and polymer frame, light rail, thumb safety, night
    sights and a 17-round magazine. 203 mm long."""
    return _p320("M17")


def m18():
    """M18, the compact M17: the same frame under a slide and barrel 20 mm shorter. 183 mm long."""
    return _p320("M18", F=-0.020)


# ---------------------------------------------------------------- M590A1
def m590():
    """Mossberg 590A1 pump shotgun, 12 gauge: heavy barrel over an eight-round tube, ghost-ring sights, a ribbed
    forend on twin action bars and a polymer stock. 1040 mm long."""
    S, A, PL = GUN_STEEL, GUN_GREY, POLYMER
    HW = 0.0175
    p = [body(A, [(-0.094, -0.008, 0.031, 0.056), (-0.084, -0.010, 0.035, 0.068), (-0.050, -0.012, 0.035, 0.076), (0.130, -0.011, 0.035, 0.074),
                  (0.142, -0.010, 0.033, 0.066)], power=5)]
    p += [B(BLACK, -HW - 0.0002, -0.012, 0.050, 0.001, 0.022, 0.070, plain=True), B(GUN_BARE, -HW - 0.0008, -0.010, 0.054, 0.001, 0.018, 0.062, plain=True)]
    p += [B(BLACK, -0.011, -0.0503, 0.040, 0.022, 0.001, 0.085, plain=True)]                                # loading port underneath
    p += [label(A, "590A1 12GA", HW + 0.0002, -0.030, 0.050, 0.050, 0.0042)]
    for y, z in ((-0.010, 0.020), (-0.030, -0.030)):
        p += pin(HW, y, z, 0.006, S)
    # ghost-ring rear sight with its guard wings, trigger guard
    p += [B(S, -0.011, 0.025, -0.050, 0.022, 0.007, 0.030, plain=True), B(S, -0.011, 0.032, -0.046, 0.0025, 0.020, 0.018, plain=True),
          B(S, 0.0085, 0.032, -0.046, 0.0025, 0.020, 0.018, plain=True),
          lathe(S, "z", 0.0, 0.044, [(-0.040, 0.0030), (-0.040, 0.0058), (-0.036, 0.0058), (-0.036, 0.0030)], plain=True, seg=12),
          bar(S, (0, 0.032, -0.038), (0, 0.0385, -0.038), 0.004)]
    p += sweep(PL, [(-0.022, -0.048), (-0.012, -0.078), (0.046, -0.082), (0.060, -0.070), (0.066, -0.046)], 0.018, 0.005, power=4, seg=8, ends="flat")
    p += [body(PL, [(-0.020, -0.052, 0.022, 0.010), (0.070, -0.052, 0.022, 0.010)], power=4)]
    # barrel and magazine tube with its cap, barrel clamp, bayonet lug, front sight
    p += [turned(S, [(0.140, 0.027), (0.170, 0.027), (0.170, 0.0235), (0.640, 0.0235)], seg=14), bore(0.640, 0.0185),
          turned(S, [(0.140, 0.026), (0.598, 0.026), (0.598, 0.0285), (0.622, 0.0285), (0.629, 0.020), (0.629, 0.0)], y=-0.0285, seg=14)]
    p += slab(S, 0.035, [(0.538, -0.045), (0.562, -0.045), (0.562, 0.014), (0.538, 0.014)], edge=0.006, r=0.008)
    p += [B(S, -0.004, -0.053, 0.585, 0.008, 0.012, 0.022, plain=True)]
    p += slab(S, 0.006, [(0.598, 0.010), (0.632, 0.010), (0.627, 0.030), (0.612, 0.030)], edge=0.0015, r=0.002)
    p += [B((_mat("sight_orange", (230, 110, 40), "lamp")), -0.0015, 0.024, 0.6115, 0.003, 0.006, 0.0006, plain=True)]
    # stock: a wrist that drops into a hand-filling grip, a deep rounded butt and a rubber recoil pad
    p += sweep(PL, [(-0.086, -0.012), (-0.120, -0.022), (-0.170, -0.030)], [0.033, 0.034, 0.036], [0.054, 0.050, 0.060], power=3.2)
    p += sweep(PL, [(-0.092, -0.040), (-0.112, -0.074), (-0.130, -0.106)], [0.032, 0.033, 0.034], [0.042, 0.042, 0.040], power=3.2)
    p += slab(PL, 0.037, [(-0.150, 0.006), (-0.398, -0.006), (-0.402, -0.122), (-0.388, -0.126), (-0.190, -0.064), (-0.140, -0.050)], edge=0.011, r=0.012)
    p += slab(RUBBER_PAD, 0.037, [(-0.400, -0.004), (-0.418, -0.008), (-0.418, -0.122), (-0.400, -0.126)], edge=0.007, r=0.006)

    pump = [body(PL, [(0.246, -0.030, 0.042, 0.040), (0.254, -0.031, 0.050, 0.048), (0.412, -0.031, 0.050, 0.048), (0.420, -0.030, 0.042, 0.040)], power=2.8),
            bar(S, (0.019, -0.022, 0.140), (0.019, -0.022, 0.250), 0.006), bar(S, (-0.019, -0.022, 0.140), (-0.019, -0.022, 0.250), 0.006)]
    for i in range(9):
        z = 0.262 + i * 0.017
        pump += [body(PL, [(z, -0.031, 0.050, 0.048), (z + 0.002, -0.031, 0.0525, 0.0505), (z + 0.007, -0.031, 0.0525, 0.0505), (z + 0.009, -0.031, 0.050, 0.048)], power=2.8, seg=14)]
    trigger = sweep(S, [(0.016, -0.049), (0.018, -0.059), (0.021, -0.067), (0.018, -0.074)], 0.006, 0.006, power=3, seg=8)
    selector = slab(S, 0.008, [(-0.076, 0.024), (-0.066, 0.024), (-0.066, 0.0295), (-0.076, 0.0295)], edge=0.0015, r=0.0015)
    shell = shotshell(0.060, -0.060)
    return gun(p, [bone("bolt", (0, -0.03, 0.33), pump), bone("trigger", (0, -0.050, 0.016), trigger), bone("selector", (0, 0.026, -0.070), selector),
                   bone("mag", (0, -0.060, 0.090), shell)],
               dict(muzzle=(0, 0, 0.640), thread=(0, 0, 0.640), eject=(-0.02, 0.0, 0.085), sight=(0, 0.044, -0.038), front=(0, 0.030, 0.612),
                    optic=(0, 0.026, 0.06), under=(0, -0.05, 0.33), grip=(0, -0.078, -0.114), hand=(0, -0.054, 0.335), mag=(0, -0.060, 0.090),
                    charge=(0, -0.050, 0.335)))


# ---------------------------------------------------------------- M107
def m107():
    """Barrett M107, 12.7x99: a long pressed-steel upper with a full-length rail and slotted barrel shroud, the
    heavy fluted barrel with its arrowhead muzzle brake, a deep ten-round magazine, folding bipod, flip-up sights
    and the rear grip under the butt. 1450 mm long."""
    S, K, PL = GUN_STEEL, GUN_BLACK, POLYMER
    HW = 0.024
    # upper receiver and barrel shroud in one long box, rail along the top
    p = [body(K, [(-0.345, 0.010, 0.044, 0.056), (-0.335, 0.012, 0.048, 0.064), (0.410, 0.012, 0.048, 0.064), (0.422, 0.010, 0.044, 0.054)], power=8)]
    p += rail(K, -0.300, 0.400, 0.0425)
    for i in range(5):                                                                                # cooling slots in the shroud
        p += P(BLACK, HW - 0.0006, 0.004, 0.225 + i * 0.036, 0.0012, 0.016, 0.024, plain=True)
    p += [B(BLACK, -HW - 0.0004, -0.004, 0.060, 0.001, 0.026, 0.120, plain=True)]                           # ejection port
    for y, z in ((0.000, -0.300), (0.000, 0.180), (0.024, -0.060)):
        p += pin(HW, y, z, 0.007, S)
    p += [label(K, "M107 CAL .50", HW + 0.0002, 0.010, -0.250, 0.060, 0.0046)]
    # lower receiver: trigger housing and the deep magazine well
    p += [body(K, [(-0.335, -0.034, 0.040, 0.034), (-0.325, -0.036, 0.044, 0.040), (0.030, -0.036, 0.044, 0.040), (0.036, -0.040, 0.046, 0.048),
                   (0.176, -0.040, 0.046, 0.048), (0.184, -0.036, 0.042, 0.038)], power=6)]
    p += sweep(PL, [(-0.034, -0.050), (-0.040, -0.070), (-0.054, -0.108), (-0.068, -0.146)], [0.028, 0.029, 0.031, 0.032], [0.038, 0.040, 0.043, 0.046], power=3.4)
    p += sweep(S, [(-0.022, -0.054), (-0.012, -0.083), (0.022, -0.085), (0.032, -0.078), (0.034, -0.054)], 0.016, 0.003, power=6, seg=8, ends="flat")
    # butt: recoil pad and the hand grip under it
    p += slab(RUBBER_PAD, 0.046, [(-0.343, 0.040), (-0.366, 0.036), (-0.366, -0.060), (-0.343, -0.064)], edge=0.007, r=0.006)
    p += sweep(PL, [(-0.300, -0.052), (-0.306, -0.078), (-0.312, -0.106)], 0.026, [0.040, 0.036, 0.030], power=3.2)
    # barrel: heavy, fluted, ending in the two-chamber arrowhead brake
    p += [turned(S, [(0.420, 0.040), (0.440, 0.040), (0.446, 0.034), (0.900, 0.030), (0.985, 0.030)], seg=14)]
    for a in range(0, 360, 45):
        p += [B(K, -0.003, 0.0138, 0.470, 0.006, 0.0018, 0.400, roll=a, pivot=(0, 0, 0.6), plain=True)]
    brake = [body(S, [(0.980, 0.0, 0.034, 0.034), (0.996, 0.0, 0.084, 0.044), (1.030, 0.0, 0.074, 0.044), (1.036, 0.0, 0.040, 0.036),
                      (1.050, 0.0, 0.078, 0.044), (1.078, 0.0, 0.066, 0.042), (1.086, 0.0, 0.042, 0.036)], power=4), bore(1.086, 0.016)]
    for z in (1.002, 1.054):
        brake += P(BLACK, 0.026, -0.012, z, 0.014, 0.024, 0.018, plain=True)
    # flip-up sights on the rail: rear aperture (folds for an optic) and the front post in its guard
    p += [B(S, -0.010, 0.050, 0.372, 0.020, 0.006, 0.016, plain=True), B(S, -0.0085, 0.056, 0.376, 0.002, 0.022, 0.008, plain=True),
          B(S, 0.0065, 0.056, 0.376, 0.002, 0.022, 0.008, plain=True), bar(S, (0, 0.054, 0.380), (0, 0.076, 0.380), 0.0024)]
    irons = [B(S, -0.010, 0.050, -0.216, 0.020, 0.006, 0.018, plain=True), B(S, -0.008, 0.056, -0.206, 0.0035, 0.024, 0.004, plain=True),
             B(S, 0.0045, 0.056, -0.206, 0.0035, 0.024, 0.004, plain=True), B(S, -0.008, 0.0700, -0.206, 0.016, 0.003, 0.004, plain=True),
             B(S, -0.0045, 0.0670, -0.206, 0.003, 0.003, 0.004, plain=True), B(S, 0.0015, 0.0670, -0.206, 0.003, 0.003, 0.004, plain=True),
             B(S, -0.008, 0.0775, -0.206, 0.016, 0.0025, 0.004, plain=True)]
    # bipod: yoke under the shroud, two legs with feet, folded back
    bipod = [body(S, [(0.372, -0.024, 0.060, 0.018), (0.392, -0.024, 0.060, 0.018)], power=4)]
    for sgn in (-1, 1):
        bipod += [bar(S, (sgn * 0.028, -0.030, 0.382), (sgn * 0.036, -0.044, 0.110), 0.013),
                  slab(S, 0.016, [(0.092, -0.050), (0.118, -0.050), (0.118, -0.040), (0.092, -0.040)], x=sgn * 0.036, edge=0.002, r=0.002)[0]]
    carrier = slab(GUN_BARE, 0.005, [(0.062, -0.002), (0.178, -0.002), (0.178, 0.020), (0.062, 0.020)], x=-HW + 0.001, edge=0.0015, r=0.003)
    carrier += [bar(GUN_BARE, (-HW, 0.010, 0.160), (-HW - 0.026, 0.010, 0.160), 0.012),
                turned(GUN_BARE, [(0.150, 0.0), (0.153, 0.014), (0.167, 0.015), (0.170, 0.0)], y=0.010, x=-HW - 0.030, seg=8)]
    trigger = sweep(S, [(0.010, -0.052), (0.012, -0.064), (0.015, -0.074), (0.012, -0.081)], 0.006, 0.006, power=3, seg=8)
    selector = [lathe(S, "x", -0.030, -0.036, [(HW, 0.005), (HW + 0.003, 0.005), (HW + 0.003, 0.0)], plain=True, seg=10)]
    selector += slab(S, 0.004, [(-0.060, -0.0325), (-0.036, -0.0335), (-0.036, -0.0265), (-0.060, -0.0275)], x=HW + 0.002, edge=0.001, r=0.002)
    # magazine: a deep steel box raked forward, with its ribs and a round at the lips
    path = [(0.106, -0.058), (0.112, -0.120), (0.120, -0.196)]
    mag = sweep(GUN_STEEL, path, 0.036, 0.132, power=9, ends="flat")
    for k in (0.3, 0.6):
        z, y = path[0][0] + (path[2][0] - path[0][0]) * k, path[0][1] + (path[2][1] - path[0][1]) * k
        mag += sweep(GUN_STEEL, [(z, y), (z + 0.001, y - 0.012)], 0.038, 0.100, power=7, seg=10, ends="flat")
    mag += sweep(GUN_STEEL, [path[-1], (path[-1][0] + 0.001, path[-1][1] - 0.008)], 0.040, 0.138, power=7, ends="flat")
    mag += cartridge(CASE_BRASS, 0.046, -0.052, 0.099, 0.0204, 0.0142, 0.036)
    return gun(p, [bone("muzzle", (0, 0, 0.985), brake), bone("bolt", (0, 0.010, 0.12), carrier), bone("trigger", (0, -0.054, 0.012), trigger),
                   bone("selector", (HW, -0.030, -0.036), selector), bone("irons", (0, 0.050, -0.200), irons),
                   bone("bipod", (0, -0.026, 0.382), bipod), bone("mag", (0, -0.058, 0.106), mag)],
               dict(muzzle=(0, 0, 1.086), thread=(0, 0, 0.985), eject=(-0.03, 0.01, 0.12), sight=(0, 0.076, -0.204), front=(0, 0.076, 0.380),
                    optic=(0, 0.050, 0.000), under=(0, -0.06, 0.28), grip=(0, -0.104, -0.056), hand=(0, -0.066, 0.215), mag=(0, -0.058, 0.110),
                    charge=(-0.054, 0.010, 0.160)))


# ---------------------------------------------------------------- RPG-7
def _pg7v(z, tail=False):
    """PG-7V anti-tank rocket with its seat at z: the long nose fuse, the swelling shaped-charge warhead and the
    narrow motor behind it. With `tail` the booster and its folded fins are there too, as it flies."""
    G_, D = _mat("rkt_green", (76, 88, 58), "gunmetal", wear=(120, 126, 104)), GUN_STEEL
    p = [turned(G_, [(z - 0.060, 0.040), (z + 0.020, 0.040), (z + 0.060, 0.070), (z + 0.110, 0.085), (z + 0.150, 0.085), (z + 0.250, 0.040),
                     (z + 0.330, 0.024), (z + 0.345, 0.024)], seg=14),
         turned(D, [(z + 0.345, 0.026), (z + 0.362, 0.022), (z + 0.372, 0.010), (z + 0.372, 0.0)], seg=10),
         turned(D, [(z + 0.012, 0.0412), (z + 0.020, 0.0412)], plain=True, open=True, seg=14),
         turned(BLACK, [(z + 0.146, 0.0856), (z + 0.152, 0.0856)], plain=True, open=True, seg=14)]
    if tail:
        p += [turned(G_, [(z - 0.060, 0.040), (z - 0.300, 0.040), (z - 0.312, 0.030), (z - 0.560, 0.030), (z - 0.572, 0.022), (z - 0.572, 0.0)], seg=12)]
        for a in (0, 90, 180, 270):
            p += [B(D, -0.0012, 0.015, z - 0.560, 0.0024, 0.036, 0.120, roll=a, pivot=(0, 0, z - 0.5), plain=True)]
    return p


def _og7v(z, tail=False):
    """OG-7V fragmentation rocket: a plain steel cylinder no wider than the tube, with a blunt fused nose."""
    G_, D = _mat("rkt_grey", (96, 100, 96), "gunmetal", wear=(150, 152, 148)), GUN_STEEL
    p = [turned(G_, [(z - 0.060, 0.040), (z + 0.010, 0.040), (z + 0.016, 0.044), (z + 0.200, 0.044), (z + 0.232, 0.034), (z + 0.240, 0.028)], seg=14),
         turned(D, [(z + 0.240, 0.028), (z + 0.262, 0.024), (z + 0.270, 0.012), (z + 0.270, 0.0)], seg=10),
         turned(_mat("rkt_band", (196, 60, 40), "polymer"), [(z + 0.150, 0.0446), (z + 0.162, 0.0446)], plain=True, open=True, seg=14)]
    if tail:
        p += [turned(G_, [(z - 0.060, 0.040), (z - 0.250, 0.040), (z - 0.262, 0.030), (z - 0.470, 0.030), (z - 0.480, 0.022), (z - 0.480, 0.0)], seg=12)]
        for a in (0, 90, 180, 270):
            p += [B(D, -0.0012, 0.015, z - 0.470, 0.0024, 0.036, 0.110, roll=a, pivot=(0, 0, z - 0.4), plain=True)]
    return p


def rocket_pg7v(): return gun(_pg7v(0.10, True), [], {})
def rocket_og7v(): return gun(_og7v(0.10, True), [], {})


def rpg7():
    """RPG-7: a steel tube flared to a blast cone at the back, wrapped in a wooden heat guard where it lies on the
    shoulder, with the trigger grip, a second grip behind it and folding iron sights. The rocket's warhead stands
    out of the muzzle. 950 mm long without the rocket."""
    S, K, WD = GUN_STEEL, GUN_BLACK, LAMINATE
    # the tube: muzzle ring, forward barrel, the wider chamber, then the venturi and blast cone
    p = [turned(S, [(0.450, 0.050), (0.438, 0.052), (0.430, 0.046), (0.020, 0.046), (-0.020, 0.058), (-0.300, 0.058), (-0.330, 0.044),
                    (-0.380, 0.044), (-0.470, 0.092), (-0.500, 0.096)], open=True, seg=16),
         turned(BLACK, [(0.450, 0.041), (-0.300, 0.041), (-0.330, 0.034), (-0.380, 0.034), (-0.500, 0.088)], plain=True, open=True, seg=16),
         turned(S, [(0.450, 0.050), (0.450, 0.041)], plain=True, open=True, seg=16),
         turned(S, [(-0.500, 0.088), (-0.500, 0.096)], plain=True, open=True, seg=16)]
    # wooden heat guard in two halves, clamped by steel bands
    p += [turned(WD, [(-0.290, 0.060), (-0.282, 0.070), (-0.030, 0.070), (-0.022, 0.060)], seg=16)]
    for z in (-0.270, -0.160, -0.050):
        p += [turned(S, [(z, 0.0715), (z + 0.012, 0.0715)], plain=True, open=True, seg=16)]
    p += [B(BLACK, -0.036, -0.0008, -0.282, 0.072, 0.0016, 0.252, plain=True)]                                # the join between the halves
    # trigger housing, hammer, grip, guard; second grip under the heat guard
    p += slab(S, 0.022, [(-0.046, -0.022), (0.056, -0.022), (0.056, -0.046), (-0.046, -0.050)], edge=0.004, r=0.005)
    p += sweep(WD, [(-0.012, -0.046), (-0.018, -0.070), (-0.030, -0.106), (-0.042, -0.140)], [0.026, 0.027, 0.029, 0.031], [0.036, 0.038, 0.041, 0.044], power=3.2)
    p += sweep(S, [(-0.002, -0.050), (0.006, -0.078), (0.040, -0.080), (0.052, -0.072), (0.054, -0.048)], 0.014, 0.0026, power=6, seg=8, ends="flat")
    p += slab(S, 0.008, [(-0.044, -0.022), (-0.030, -0.022), (-0.034, -0.008), (-0.048, -0.010)], edge=0.0015, r=0.002)   # cocking piece
    p += slab(S, 0.020, [(-0.214, -0.030), (-0.166, -0.030), (-0.166, -0.046), (-0.214, -0.046)], edge=0.004, r=0.004)
    p += sweep(WD, [(-0.188, -0.044), (-0.192, -0.074), (-0.200, -0.108), (-0.208, -0.138)], [0.027, 0.028, 0.030, 0.031], [0.036, 0.038, 0.040, 0.042], power=3.2)
    # sights: front post on its folding arm, rear leaf with its notch; the bracket for the optical sight on the left
    p += [B(S, -0.008, 0.021, 0.318, 0.016, 0.008, 0.020, plain=True), bar(S, (0, 0.027, 0.328), (0, 0.064, 0.328), 0.0034),
          B(S, -0.008, 0.021, 0.090, 0.016, 0.008, 0.026, plain=True), B(S, -0.0075, 0.029, 0.100, 0.015, 0.026, 0.003, plain=True),
          B(S, -0.0075, 0.055, 0.100, 0.006, 0.009, 0.003, plain=True), B(S, 0.0015, 0.055, 0.100, 0.006, 0.009, 0.003, plain=True)]
    p += slab(S, 0.006, [(0.070, -0.002), (0.160, -0.002), (0.160, 0.018), (0.070, 0.018)], x=0.0245, edge=0.0015, r=0.003)
    # sling swivels and the muzzle guard notch
    p += [bar(S, (0.0, -0.023, 0.380), (0.0, -0.036, 0.380), 0.004), bar(S, (0.0, -0.046, -0.420), (0.0, -0.058, -0.420), 0.004),
          B(BLACK, -0.004, 0.0222, 0.428, 0.008, 0.002, 0.022, plain=True)]
    trigger = sweep(S, [(0.022, -0.046), (0.024, -0.058), (0.027, -0.068), (0.024, -0.075)], 0.006, 0.006, power=3, seg=8)
    selector = [lathe(S, "x", -0.036, 0.030, [(0.011, 0.0), (0.011, 0.005), (0.015, 0.005), (0.015, 0.0)], plain=True, seg=8)]
    return gun(p, [bone("trigger", (0, -0.046, 0.024), trigger), bone("selector", (0, -0.036, 0.030), selector),
                   bone("mag", (0, 0, 0.450), _pg7v(0.450)), bone("frag", (0, 0, 0.450), _og7v(0.450))],
               dict(muzzle=(0, 0, 0.450), thread=(0, 0, 0.450), eject=(0, 0, -0.5), sight=(0, 0.064, 0.100), front=(0, 0.064, 0.328),
                    optic=(0, 0.03, 0.1), under=(0, -0.05, 0.2), grip=(0, -0.094, -0.026), hand=(0, -0.094, -0.196), mag=(0, 0.0, 0.560),
                    charge=(0, -0.01, -0.04)))


# ---------------------------------------------------------------- M9

# ---------------------------------------------------------------- pistol families (V5)
PISTOL_ROUNDS = {"9mm": (0.019, 0.0098, 0.0096, 0.010), "57": (0.029, 0.0079, 0.0060, 0.013), "45": (0.023, 0.0120, 0.0117, 0.009)}


def _poly_pistol(name, mark, L=0.0, G=0.0, railed=True, rnd="9mm", frame=POLYMER, slidemat=GUN_BLACK, boxy=False):
    """A polymer striker pistol: squared slide, moulded frame with a stippled grip and (usually) a dust-cover rail.
    `L` lengthens the slide and barrel forward, `G` the grip downward. Glock, P99, USP, Five-seveN."""
    S = GUN_STEEL
    cl, cd, nk, bl = PISTOL_ROUNDS[rnd]
    sh = 0.034 if boxy else 0.031                                                                      # slide height
    p = [body(frame, [(-0.074, -0.020, 0.024, 0.014), (-0.060, -0.022, 0.0285, 0.020), (0.082 + L, -0.022, 0.0285, 0.020), (0.090 + L, -0.021, 0.026, 0.016)], power=5)]
    p += sweep(frame, [(-0.034, -0.026), (-0.040, -0.050), (-0.050, -0.090 - G * 0.5), (-0.060 - G * 0.25, -0.124 - G)], [0.0295, 0.030, 0.031, 0.0315], [0.050, 0.050, 0.052, 0.054], power=3.6)
    p += sweep(frame, [(-0.060, -0.022), (-0.078, -0.018), (-0.086, -0.016)], [0.024, 0.022, 0.018], [0.012, 0.010, 0.007], power=3, seg=10)
    tg = 0.060 if boxy else 0.047                                                                      # trigger-guard front
    p += sweep(frame, [(tg, -0.028), (tg - 0.001, -0.060), (0.038, -0.072), (0.004, -0.073), (-0.016, -0.062)], 0.014, 0.0055, power=4, seg=8, ends="flat")
    if railed:
        p += rail(frame, 0.048, 0.086 + L, -0.031, w=0.020, up=-1)
    for sx in (1, -1):
        p += [B(frame, sx * 0.0152 - 0.0003, -0.118 - G, -0.068, 0.0006, 0.074 + G, 0.030, pitch=-14, pivot=(0, -0.08, -0.05), plain=True,
                marks=[("left" if sx > 0 else "right", "checker", 0.5, 0.5, 0.030, 0.074)])]
    p += [B(S, 0.0143, -0.022, 0.010, 0.0016, 0.005, 0.022, plain=True), label(frame, mark, 0.01435, -0.022, 0.056, 0.020, 0.0036)]
    p += [tube(S, 0.060, 0.1005 + L, cd * 1.45, seg=10), bore(0.1005 + L, cd * 0.92), tube(S, 0.060, 0.0995 + L, 0.008, y=-0.0135)]

    slide = [body(slidemat, [(-0.104, 0.002, 0.024, 0.026), (-0.100, 0.0035, 0.026, sh), (0.090 + L, 0.0035, 0.026, sh), (0.099 + L, 0.002, 0.024, 0.026)], power=7 if boxy else 6)]
    for sx in (1, -1):
        for z, ln in ((-0.094, 0.028), (0.056 + L, 0.022)):
            slide += [B(slidemat, sx * 0.0128 - 0.0003, -0.008, z, 0.0006, 0.022, ln, plain=True,
                        marks=[("left" if sx > 0 else "right", "grooves", 0.5, 0.5, ln, 0.022)])]
    slide += [B(BLACK, -0.0134, 0.004, 0.002, 0.0142, sh * 0.5, 0.034, plain=True), B(GUN_BARE, -0.0128, 0.006, 0.004, 0.012, 0.012, 0.030, plain=True),
              B(S, -0.0016, 0.013 + sh, 0.084 + L, 0.0032, 0.007, 0.006, plain=True, marks=[("rear", "dot", 0.5, 0.4, 0.002, (230, 230, 230))]),
              B(S, -0.009, 0.013 + sh, -0.100, 0.018, 0.0025, 0.008, plain=True),
              B(S, -0.009, 0.013 + sh, -0.100, 0.0065, 0.007, 0.008, plain=True, marks=[("rear", "dot", 0.5, 0.4, 0.002, (230, 230, 230))]),
              B(S, 0.0025, 0.013 + sh, -0.100, 0.0065, 0.007, 0.008, plain=True, marks=[("rear", "dot", 0.5, 0.4, 0.002, (230, 230, 230))]),
              label(slidemat, mark, 0.01305, 0.006, 0.006, 0.040, 0.0032)]
    trigger = sweep(S, [(0.016, -0.032), (0.017, -0.046), (0.020, -0.056), (0.017, -0.064)], 0.007, 0.006, power=3, seg=8)
    selector = [B(S, 0.0143, -0.019, -0.062, 0.0035, 0.005, 0.024, plain=True)]
    mag = sweep(GUN_STEEL, [(-0.037, -0.016), (-0.049, -0.066), (-0.061 - G * 0.25, -0.120 - G)], 0.021, 0.040, power=6, ends="flat")
    mag += sweep(frame, [(-0.061 - G * 0.25, -0.121 - G), (-0.064 - G * 0.25, -0.134 - G)], 0.030, 0.054, power=4, ends="flat")
    mag += cartridge(CASE_BRASS, -0.052, -0.013, cl, cd, nk, bl)
    return gun(p, [bone("bolt", (0, 0.0, 0.0), slide), bone("trigger", (0, -0.030, 0.016), trigger), bone("selector", (0, -0.0165, -0.040), selector),
                   bone("mag", (0, -0.070, -0.050), mag)],
               dict(muzzle=(0, 0, 0.1005 + L), thread=(0, 0, 0.1005 + L), eject=(-0.014, 0.012, 0.02), sight=(0, 0.013 + sh, -0.100), front=(0, 0.013 + sh, 0.087 + L),
                    optic=(0, 0.013 + sh, -0.060), under=(0, -0.034, 0.07), grip=(0, -0.078 - G, -0.047), hand=(0.012, -0.088, -0.040), mag=(0, -0.070, -0.050),
                    charge=(0.0, 0.016, -0.085)))


def _steel_pistol(name, mark, L=0.0, rnd="9mm", panels=WOOD, framemat=GUN_STEEL, slidemat=_mat("gun_slide", (56, 60, 64), "gunmetal", wear=(120, 124, 128)), squared=False):
    """A metal-framed pistol with an exposed hammer and separate grip panels: 1911, Hi-Power, CZ75, P226, P228.
    `L` moves the muzzle; `squared` gives a SIG/CZ blockier slide, else the rounded 1911/Browning profile."""
    S, K = GUN_STEEL, GUN_BLACK
    cl, cd, nk, bl = PISTOL_ROUNDS[rnd]
    sh = 0.030
    # frame: dust cover, grip with panels, beavertail tang, curved trigger guard
    p = [body(framemat, [(-0.070, -0.019, 0.024, 0.012), (-0.056, -0.021, 0.028, 0.019), (0.084 + L, -0.021, 0.028, 0.019), (0.092 + L, -0.020, 0.024, 0.014)], power=5)]
    p += sweep(framemat, [(-0.036, -0.026), (-0.044, -0.052), (-0.052, -0.092), (-0.058, -0.126)], [0.029, 0.030, 0.031, 0.031], [0.050, 0.050, 0.052, 0.054], power=3.4)
    p += sweep(framemat, [(-0.056, -0.019), (-0.076, -0.015), (-0.090, -0.017)], [0.022, 0.020, 0.015], [0.012, 0.010, 0.007], power=3, seg=10)
    p += sweep(framemat, [(0.046, -0.027), (0.050, -0.050), (0.042, -0.064), (0.012, -0.070), (-0.012, -0.060)], 0.013, 0.005, power=4, seg=8, ends="flat")
    for sx in (1, -1):                                                                                  # separate grip panels, chequered
        p += [B(panels, sx * 0.0156 - 0.0005, -0.124, -0.072, 0.001, 0.080, 0.034, pitch=-12, pivot=(0, -0.08, -0.05), plain=True,
                marks=[("left" if sx > 0 else "right", "checker", 0.5, 0.5, 0.034, 0.080)])]
    p += [B(S, 0.0150, -0.022, 0.008, 0.0016, 0.005, 0.026, plain=True), label(framemat, mark, 0.0148, -0.022, 0.050, 0.018, 0.0034)]
    p += [tube(S, 0.056, 0.100 + L, cd * 1.45, seg=10), bore(0.100 + L, cd * 0.92)]
    # slide: rounded (1911) or squared (SIG), with its ejection port, sights and the exposed hammer behind
    prof = [(-0.100, 0.002, 0.024, 0.026), (-0.096, 0.004, 0.026, sh), (0.090 + L, 0.004, 0.026, sh), (0.100 + L, 0.002, 0.024, 0.024)]
    slide = [body(slidemat, prof, power=7 if squared else 3.6)]
    for sx in (1, -1):
        slide += [B(slidemat, sx * 0.0128 - 0.0003, -0.008, -0.090, 0.0006, 0.022, 0.030, plain=True,
                    marks=[("left" if sx > 0 else "right", "grooves", 0.5, 0.5, 0.030, 0.022)])]
    slide += [B(BLACK, -0.0134, 0.006, 0.004, 0.0146, sh * 0.5, 0.034, plain=True), B(GUN_BARE, -0.0128, 0.008, 0.006, 0.012, 0.011, 0.030, plain=True),
              B(S, -0.0016, 0.012 + sh, 0.086 + L, 0.0032, 0.006, 0.006, plain=True),
              B(S, -0.008, 0.012 + sh, -0.098, 0.016, 0.0025, 0.008, plain=True), B(S, -0.008, 0.012 + sh, -0.098, 0.005, 0.007, 0.008, plain=True), B(S, 0.003, 0.012 + sh, -0.098, 0.005, 0.007, 0.008, plain=True),
              label(slidemat, mark, 0.01305, 0.006, 0.004, 0.040, 0.0030)]
    hammer = [slab(S, 0.007, [(-0.110, 0.012), (-0.104, 0.012), (-0.100, 0.028), (-0.108, 0.030), (-0.114, 0.022)], edge=0.0015, r=0.003)[0]]
    trigger = sweep(S, [(0.016, -0.030), (0.017, -0.044), (0.020, -0.054), (0.017, -0.062)], 0.007, 0.006, power=3, seg=8)
    selector = [B(S, 0.0150, -0.018, -0.058, 0.0035, 0.006, 0.022, plain=True)]
    mag = sweep(GUN_STEEL, [(-0.037, -0.016), (-0.049, -0.068), (-0.058, -0.122)], 0.021, 0.040, power=6, ends="flat")
    mag += sweep(GUN_STEEL, [(-0.058, -0.123), (-0.060, -0.134)], 0.030, 0.054, power=4, ends="flat")
    mag += cartridge(CASE_BRASS, -0.052, -0.013, cl, cd, nk, bl)
    return gun(p + hammer, [bone("bolt", (0, 0.0, 0.0), slide), bone("trigger", (0, -0.028, 0.016), trigger), bone("selector", (0, -0.0155, -0.040), selector),
                            bone("mag", (0, -0.070, -0.050), mag)],
               dict(muzzle=(0, 0, 0.100 + L), thread=(0, 0, 0.100 + L), eject=(-0.014, 0.014, 0.02), sight=(0, 0.012 + sh, -0.098), front=(0, 0.012 + sh, 0.089 + L),
                    optic=(0, 0.012 + sh, -0.060), under=(0, -0.030, 0.06), grip=(0, -0.080, -0.047), hand=(0.012, -0.090, -0.042), mag=(0, -0.070, -0.050),
                    charge=(0.0, 0.016, -0.085)))


def glock17():
    """Glock 17, 9x19: squared polymer-framed striker pistol, low sights, 17-round magazine."""
    return _poly_pistol("Glock 17", "G17", railed=True)


def glock19():
    """Glock 19, 9x19: the compact Glock, shorter slide and grip, 15 rounds."""
    return _poly_pistol("Glock 19", "G19", L=-0.014, G=-0.012, railed=True)


def p99():
    """Walther P99, 9x19: sculpted polymer grip, angular slide, 15 rounds."""
    return _poly_pistol("Walther P99", "P99", frame=_mat("p99_frame", (40, 42, 46), "polymer"))


def hk_usp():
    """HK USP, 9x19: boxy slide, oversized squared trigger guard, moulded frame, 15 rounds."""
    return _poly_pistol("HK USP", "USP", L=0.004, railed=True, boxy=True, frame=_mat("usp_frame", (34, 36, 38), "polymer"))


def five_seven():
    """FN Five-seveN, 5.7x28: long angular polymer slide and broad textured grip, 20 rounds."""
    return _poly_pistol("Five-seveN", "5.7", L=0.014, G=0.010, rnd="57", frame=_mat("fn_frame", (44, 42, 40), "polymer"))


def m1911a1():
    """M1911A1, .45 ACP: slim steel slide, straight-sided wood grips, 7 rounds."""
    return _steel_pistol("M1911A1", "1911", rnd="45", panels=WOOD, framemat=_mat("blued", (46, 48, 52), "gunmetal", wear=(92, 96, 100)),
                         slidemat=_mat("blued_s", (40, 42, 46), "gunmetal", wear=(96, 100, 104)))


def hi_power():
    """Browning Hi-Power, 9x19: rounded steel upper, slender frame, wood grips, 13 rounds."""
    return _steel_pistol("Hi-Power", "HP35", panels=WOOD, framemat=_mat("blued2", (48, 50, 54), "gunmetal", wear=(96, 100, 104)),
                         slidemat=_mat("blued2s", (42, 44, 48), "gunmetal", wear=(100, 104, 108)))


def cz75():
    """CZ 75, 9x19: slender slide riding inside the frame rails, black panels, 16 rounds."""
    return _steel_pistol("CZ 75", "CZ75", panels=GUN_BLACK, squared=True)


def p226():
    """SIG P226, 9x19: squared alloy slide, curved grip, black panels, 15 rounds."""
    return _steel_pistol("SIG P226", "P226", panels=_mat("sig_grip", (32, 34, 36), "polymer"), squared=True,
                         framemat=_mat("alloy", (58, 62, 66), "gunmetal", wear=(110, 114, 118)))


def p228():
    """SIG P228, 9x19: the compact P226, shorter slide, 13 rounds."""
    return _steel_pistol("SIG P228", "P228", L=-0.012, panels=_mat("sig_grip2", (32, 34, 36), "polymer"), squared=True,
                         framemat=_mat("alloy2", (58, 62, 66), "gunmetal", wear=(110, 114, 118)))


def m9():
    """Beretta M9, 9x19: the open-topped slide with the barrel showing along it, rounded frame, ring hammer, slide
    safety and chequered grip panels. 217 mm long, 15 rounds."""
    K, S, PL = GUN_BLACK, GUN_STEEL, POLYMER
    # frame: dust cover, the grip with its panels, tang, rounded trigger guard
    p = [body(K, [(-0.070, -0.018, 0.024, 0.012), (-0.056, -0.021, 0.028, 0.020), (0.060, -0.021, 0.027, 0.018), (0.078, -0.020, 0.024, 0.014)], power=4)]
    p += sweep(K, [(-0.036, -0.026), (-0.042, -0.052), (-0.050, -0.092), (-0.058, -0.128)], [0.029, 0.030, 0.031, 0.032], [0.050, 0.050, 0.052, 0.054], power=3.4)
    p += sweep(K, [(-0.058, -0.020), (-0.076, -0.016), (-0.088, -0.018)], [0.022, 0.020, 0.016], [0.012, 0.010, 0.007], power=3, seg=10)
    p += sweep(K, [(0.044, -0.028), (0.050, -0.048), (0.044, -0.066), (0.026, -0.074), (0.002, -0.070), (-0.014, -0.058)], 0.013, 0.005, power=4, seg=8, ends="flat")
    for sx in (1, -1):
        p += [B(PL, sx * 0.0156 - 0.0005, -0.122, -0.070, 0.001, 0.078, 0.032, pitch=-12, pivot=(0, -0.08, -0.05), plain=True,
                marks=[("left" if sx > 0 else "right", "checker", 0.5, 0.5, 0.032, 0.078)])]
    p += [B(S, 0.0138, -0.023, 0.006, 0.0016, 0.005, 0.030, plain=True), lathe(S, "x", -0.022, 0.050, [(-0.0145, 0.0035), (0.0145, 0.0035)], plain=True, seg=8),
          label(K, "M9", 0.0136, -0.021, 0.046, 0.016, 0.0036)]
    # the barrel, bare along the top of the slide, and its locking block
    p += [tube(S, -0.040, 0.112, 0.0148, seg=10), bore(0.112, 0.009), tube(S, 0.030, 0.104, 0.008, y=-0.012)]
    # slide: two rails either side of the open top, bridged at the breech and the muzzle; sights; safety lever
    slide = []
    for sx in (1, -1):
        slide += slab(K, 0.007, [(-0.108, -0.012), (0.104, -0.012), (0.106, 0.004), (0.100, 0.010), (-0.106, 0.012)], x=sx * 0.0098, edge=0.002, r=0.003)
        slide += [B(K, sx * 0.0132 - 0.0003, -0.008, -0.100, 0.0006, 0.018, 0.026, plain=True, marks=[("left" if sx > 0 else "right", "grooves", 0.5, 0.5, 0.026, 0.018)]),
                  B(S, sx * 0.0135 - 0.002, -0.002, -0.078, 0.004, 0.005, 0.016, plain=True)]
    slide += [body(K, [(-0.108, 0.002, 0.024, 0.024), (-0.046, 0.004, 0.026, 0.022)], power=5), body(K, [(0.086, 0.002, 0.024, 0.018), (0.106, 0.001, 0.022, 0.016)], power=4),
              B(S, -0.0014, 0.010, 0.094, 0.0028, 0.006, 0.006, plain=True), B(S, -0.008, 0.014, -0.102, 0.006, 0.006, 0.007, plain=True),
              B(S, 0.002, 0.014, -0.102, 0.006, 0.006, 0.007, plain=True), label(K, "BERETTA", 0.01345, 0.002, -0.030, 0.034, 0.003)]
    hammer = [lathe(S, "x", 0.004, -0.112, [(-0.004, 0.0075), (0.004, 0.0075)], plain=True, seg=8), B(S, -0.003, -0.010, -0.112, 0.006, 0.014, 0.006, plain=True)]
    trigger = sweep(S, [(0.014, -0.032), (0.016, -0.046), (0.020, -0.056), (0.018, -0.064)], 0.007, 0.006, power=3, seg=8)
    mag = sweep(GUN_STEEL, [(-0.039, -0.016), (-0.050, -0.070), (-0.059, -0.124)], 0.021, 0.040, power=6, ends="flat")
    mag += sweep(GUN_BLACK, [(-0.059, -0.125), (-0.061, -0.136)], 0.030, 0.054, power=4, ends="flat")
    mag += cartridge(CASE_BRASS, -0.054, -0.013, 0.019, 0.0098, 0.0096, 0.010)
    return gun(p + hammer, [bone("bolt", (0, 0.0, 0.0), slide), bone("trigger", (0, -0.030, 0.016), trigger), bone("selector", (0, 0.0, -0.070), []),
                            bone("mag", (0, -0.070, -0.050), mag)],
               dict(muzzle=(0, 0, 0.112), thread=(0, 0, 0.112), eject=(-0.014, 0.012, 0.0), sight=(0, 0.020, -0.100), front=(0, 0.020, 0.097),
                    optic=(0, 0.014, -0.060), under=(0, -0.03, 0.05), grip=(0, -0.080, -0.048), hand=(0.012, -0.090, -0.042), mag=(0, -0.070, -0.050),
                    charge=(0.0, 0.010, -0.090)))


# ---------------------------------------------------------------- the AR family: HK416, M110
def _ar(mark, scale=1.0, barrel=0.368, guard=0.228, fixed_stock=False, tan=False, straight_mag=False, case=CASE_BRASS, round_=(0.0447, 0.0095, 0.0063, 0.012)):
    """An AR-pattern rifle with a full-length railed handguard and folding sights. `scale` enlarges the receivers
    for the 7.62 guns, `barrel` and `guard` are the barrel and handguard lengths from the receiver face."""
    A = _mat("ar_tan", (150, 132, 100), "gunmetal", wear=(96, 92, 84)) if tan else GUN_GREY
    S, PL = GUN_STEEL, (POLYMER_TAN if tan else POLYMER)
    k = scale
    HW = 0.0145 * k
    F = 0.105 * k                                                                                   # receiver face
    p = [body(A, [(-0.108 * k, 0.006 * k, 0.029 * k, 0.040 * k), (-0.100 * k, 0.006 * k, 0.031 * k, 0.046 * k), (F - 0.015, 0.006 * k, 0.031 * k, 0.046 * k),
                  (F, 0.004 * k, 0.034 * k, 0.046 * k)], power=3.6)]
    top = 0.028 * k
    p += [B(A, -0.0085, top - 0.002, -0.104 * k, 0.017, 0.006, 0.209 * k, plain=True)] + rail(A, -0.102 * k, F, top)
    p += slab(A, 0.010, [(-0.044 * k, -0.006 * k), (-0.012 * k, 0.004 * k), (-0.012 * k, 0.020 * k), (-0.044 * k, 0.022 * k)], x=-HW - 0.004, edge=0.003, r=0.004)
    p += [lathe(A, "z", -HW - 0.007, 0.010 * k, [(-0.082 * k, 0.0058), (-0.040 * k, 0.0068)], plain=True, seg=10),
          lathe(S, "z", -HW - 0.007, 0.010 * k, [(-0.094 * k, 0.0), (-0.094 * k, 0.0078), (-0.082 * k, 0.0078), (-0.082 * k, 0.0)], plain=True, seg=10)]
    p += [B(BLACK, -HW - 0.0012, -0.004 * k, -0.006 * k, 0.0016, 0.019 * k, 0.072 * k, plain=True)]
    p += slab(A, 0.003, [(-0.008 * k, -0.030 * k), (0.068 * k, -0.030 * k), (0.068 * k, -0.008 * k), (-0.008 * k, -0.008 * k)], x=-HW - 0.003, edge=0.001, r=0.003)
    p += [body(A, [(-0.108 * k, -0.028 * k, 0.026 * k, 0.030 * k), (-0.060 * k, -0.032 * k, 0.029 * k, 0.038 * k), (0.030 * k, -0.032 * k, 0.029 * k, 0.038 * k),
                   (0.036 * k, -0.036 * k, 0.030 * k, 0.046 * k)], power=5)]
    p += slab(A, 0.031 * k, [(0.034 * k, -0.014 * k), (F, -0.014 * k), (F + 0.002, -0.050 * k), (F - 0.002, -0.090 * k), (0.040 * k, -0.080 * k), (0.034 * k, -0.052 * k)],
              edge=0.004, r=0.006)
    p += [body(A, [(-0.138 * k, -0.004 * k, 0.034 * k, 0.056 * k), (-0.106 * k, -0.004 * k, 0.034 * k, 0.056 * k)], power=3.2)]
    p += sweep(A, [(-0.020 * k, -0.050 * k), (-0.012 * k, -0.079 * k), (0.030 * k, -0.081 * k), (0.040 * k, -0.074 * k)], 0.014, 0.003, power=6, seg=8, ends="flat")
    p += slab(S, 0.003, [(0.026 * k, -0.016 * k), (0.040 * k, -0.016 * k), (0.040 * k, 0.008 * k), (0.032 * k, 0.012 * k), (0.026 * k, 0.006 * k)], x=HW + 0.0015, edge=0.001, r=0.002)
    for y, z in ((-0.028, 0.020), (-0.024, -0.010), (-0.020, 0.098), (-0.036, -0.092)):
        p += pin(HW, y * k, z * k, 0.0055, S)
    p += [label(A, mark, HW + 0.0012, -0.050 * k, 0.044 * k, 0.050 * k, 0.0034)]
    p += sweep(PL, [(-0.034 * k, -0.044 * k), (-0.040 * k, -0.064 * k), (-0.054 * k, -0.104 * k), (-0.070 * k, -0.146 * k)], [0.027, 0.028, 0.030, 0.031],
               [0.036 * k, 0.038 * k, 0.042 * k, 0.045 * k], power=3.4)
    # free-floating handguard, railed all round, with the gas block hidden inside; barrel; flash hider
    G0, G1 = F + 0.004, F + guard
    hg = 0.046 * k
    p += [turned(S, [(F, 0.034 * k), (F + 0.006, 0.040 * k), (F + 0.016, 0.040 * k)], seg=14)]
    p += [body(A, [(G0, 0.0, hg * 0.9, hg * 0.9), (G0 + 0.006, 0.0, hg, hg), (G1 - 0.006, 0.0, hg, hg), (G1, 0.0, hg * 0.9, hg * 0.9)], power=3.2)]
    p += rail(A, G0 + 0.004, G1 - 0.002, hg / 2 - 0.001) + rail(A, G0 + 0.004, G1 - 0.002, -hg / 2 + 0.001, up=-1)
    p += side_rail(A, G0 + 0.004, G1 - 0.002, hg / 2 - 0.001, 0.0, 1) + side_rail(A, G0 + 0.004, G1 - 0.002, -hg / 2 + 0.001, 0.0, -1)
    M = F + barrel
    bd = 0.0185 * (1.1 if k > 1 else 1)
    p += [turned(S, [(G1 - 0.01, bd), (M - 0.04, bd)], seg=12)]
    hider = [turned(S, [(M - 0.042, bd), (M - 0.036, bd + 0.0035), (M - 0.004, bd + 0.0035), (M, bd + 0.0005)], seg=12), bore(M, 0.0115 * k)]
    for a in (0, 72, 144, 216, 288):
        hider += [B(BLACK, -0.0018, (bd + 0.0035) / 2 - 0.0004, M - 0.030, 0.0036, 0.0008, 0.020, roll=a, pivot=(0, 0, M - 0.02), plain=True)]
    # folding front sight on the handguard rail
    sy = hg / 2 + 0.0065
    sh = 0.066 * k if k == 1 else 0.066 + (top - 0.028)
    fz = G1 - 0.030
    p += [B(S, -0.010, sy, fz - 0.008, 0.020, 0.006, 0.016, plain=True), B(S, -0.0082, sy + 0.005, fz - 0.004, 0.0018, sh - sy - 0.004, 0.008, plain=True),
          B(S, 0.0064, sy + 0.005, fz - 0.004, 0.0018, sh - sy - 0.004, 0.008, plain=True), bar(S, (0, sy + 0.004, fz), (0, sh, fz), 0.0022)]
    ry = top + 0.0095
    rz = -0.084 * k
    irons = [B(S, -0.010, ry, rz - 0.010, 0.020, 0.006, 0.018, plain=True), B(S, -0.008, ry + 0.006, rz, 0.0035, sh - ry, 0.004, plain=True),
             B(S, 0.0045, ry + 0.006, rz, 0.0035, sh - ry, 0.004, plain=True), B(S, -0.008, sh - 0.006, rz, 0.016, 0.003, 0.004, plain=True),
             B(S, -0.0045, sh - 0.009, rz, 0.003, 0.003, 0.004, plain=True), B(S, 0.0015, sh - 0.009, rz, 0.003, 0.003, 0.004, plain=True),
             B(S, -0.008, sh + 0.0015, rz, 0.016, 0.0025, 0.004, plain=True)]
    # stock on the receiver extension
    if fixed_stock:
        p += [turned(A, [(-0.136 * k, 0.030), (-0.200 * k, 0.030)], seg=14)]
        p += slab(PL, 0.040, [(-0.150 * k, 0.022 * k), (-0.386, 0.020), (-0.390, -0.106), (-0.376, -0.110), (-0.230, -0.044), (-0.150 * k, -0.030 * k)], edge=0.009, r=0.010)
        p += slab(RUBBER_PAD, 0.040, [(-0.388, 0.018), (-0.398, 0.015), (-0.398, -0.105), (-0.388, -0.108)], edge=0.005, r=0.003)
        butt = -0.398
    else:
        p += [turned(A, [(-0.136, 0.030), (-0.300, 0.030), (-0.300, 0.0)], seg=14), turned(S, [(-0.138, 0.037), (-0.148, 0.037)], seg=12)]
        p += [body(PL, [(-0.162, -0.002, 0.036, 0.044), (-0.170, -0.002, 0.042, 0.050), (-0.300, -0.002, 0.042, 0.050), (-0.318, -0.002, 0.038, 0.046)], power=3.0)]
        p += slab(PL, 0.036, [(-0.220, -0.018), (-0.322, -0.018), (-0.336, -0.030), (-0.336, -0.102), (-0.322, -0.106), (-0.250, -0.044)], edge=0.007, r=0.008)
        p += slab(PL, 0.042, [(-0.316, 0.024), (-0.336, 0.020), (-0.336, -0.104), (-0.316, -0.100)], edge=0.007, r=0.006)
        p += slab(RUBBER_PAD, 0.040, [(-0.335, 0.018), (-0.343, 0.015), (-0.343, -0.101), (-0.335, -0.104)], edge=0.004, r=0.003)
    carrier = [B(GUN_BARE, -HW - 0.0008, -0.002 * k, -0.002 * k, 0.004, 0.016 * k, 0.066 * k, plain=True)]
    charge = slab(A, 0.040, [(-0.122 * k, top - 0.007), (-0.106 * k, top - 0.007), (-0.106 * k, top + 0.001), (-0.122 * k, top + 0.001)], edge=0.003, r=0.002)
    trigger = sweep(S, [(0.010 * k, -0.048 * k), (0.011 * k, -0.060 * k), (0.014 * k, -0.070 * k), (0.011 * k, -0.077 * k)], 0.006, 0.006, power=3, seg=8)
    selector = [lathe(S, "x", -0.024 * k, -0.036 * k, [(HW, 0.005), (HW + 0.003, 0.005), (HW + 0.003, 0.0)], plain=True, seg=10)]
    selector += slab(S, 0.004, [(-0.060 * k, -0.0265 * k), (-0.036 * k, -0.0275 * k), (-0.036 * k, -0.0205 * k), (-0.060 * k, -0.0215 * k)], x=HW + 0.002, edge=0.001, r=0.002)
    mz = 0.070 * k
    if straight_mag:
        path = [(mz, -0.046 * k), (mz + 0.002, -0.110), (mz + 0.006, -0.176)]
        mag = sweep(GUN_STEEL, path, 0.0245 * k, 0.062 * k, power=8, ends="flat")
    else:
        path = [(mz, -0.046), (mz, -0.130)] + arc_path(mz, -0.130, 0.100, 0.230, 5)[1:]
        mag = sweep(PL, path, 0.0235, 0.061, power=8, ends="flat")
        for q in path[2::2]:
            mag += sweep(PL, [q, (q[0] + 0.001, q[1] - 0.006)], 0.0255, 0.050, power=6, seg=10, ends="flat")
    mag += sweep(mag[0]["mat"], [path[-1], (path[-1][0] + (path[-1][0] - path[-2][0]) * 0.2, path[-1][1] + (path[-1][1] - path[-2][1]) * 0.2)], 0.027 * k, 0.067 * k, power=6, ends="flat")
    mag += cartridge(case, mz - round_[0] * 0.55, -0.042 * k, *round_)
    return gun(p, [bone("muzzle", (0, 0, M - 0.042), hider), bone("bolt", (0, 0.006, 0.03), carrier), bone("charge", (0, top - 0.002, -0.105 * k), charge),
                   bone("trigger", (0, -0.050 * k, 0.010), trigger), bone("selector", (HW, -0.024 * k, -0.036 * k), selector),
                   bone("irons", (0, ry, rz + 0.006), irons), bone("mag", (0, -0.050 * k, mz), mag)],
               dict(muzzle=(0, 0, M), thread=(0, 0, M - 0.042), eject=(-0.02, 0.006, 0.03), sight=(0, sh, rz + 0.002), front=(0, sh, fz),
                    optic=(0, top + 0.0095, 0.030 * k), under=(0, -hg / 2 - 0.0065, F + guard * 0.55), grip=(0, -0.100 * k, -0.056 * k),
                    hand=(0, -hg / 2 - 0.008, F + guard * 0.6), mag=(0, -0.050 * k, mz), charge=(0.0, top, -0.118 * k)))


def hk416():
    """HK416, 5.56x45: the AR outline with a long quad-rail handguard over its piston system, folding sights and a
    polymer magazine. 14.5-inch barrel."""
    return _ar("HK416 5.56MM", barrel=0.368, guard=0.262)


def m110():
    """M110 semi-automatic sniper rifle, 7.62x51: a larger AR-pattern receiver in tan, a 20-inch barrel inside a long
    railed handguard, fixed stock and a straight 20-round magazine. The scope is a separate fitting."""
    return _ar("M110 7.62MM", scale=1.13, barrel=0.508, guard=0.330, fixed_stock=True, tan=True, straight_mag=True, round_=(0.0512, 0.0119, 0.0087, 0.020))


# ---------------------------------------------------------------- G36
def g36():
    """G36, 5.56x45: a polymer shell with the long carrying handle that holds its sights, a ventilated handguard,
    the side-folding skeleton stock and a translucent magazine with the rounds showing through."""
    PL, S = POLYMER, GUN_STEEL
    CLEAR = _mat("g36_mag", (96, 92, 70), "polymer")
    HW = 0.019
    p = [body(PL, [(-0.150, -0.004, 0.034, 0.050), (-0.140, -0.006, 0.038, 0.060), (0.150, -0.006, 0.038, 0.060), (0.160, -0.004, 0.034, 0.052)], power=5)]
    # magazine well and trigger housing
    p += slab(PL, 0.034, [(0.040, -0.030), (0.118, -0.030), (0.120, -0.086), (0.046, -0.080)], edge=0.005, r=0.006)
    p += [body(PL, [(-0.110, -0.044, 0.030, 0.030), (0.040, -0.044, 0.030, 0.030)], power=4)]
    p += sweep(PL, [(-0.038, -0.050), (-0.044, -0.070), (-0.058, -0.108), (-0.072, -0.146)], [0.028, 0.029, 0.031, 0.032], [0.038, 0.040, 0.043, 0.046], power=3.4)
    p += sweep(PL, [(-0.024, -0.058), (-0.014, -0.086), (0.030, -0.088), (0.044, -0.078)], 0.016, 0.004, power=5, seg=8, ends="flat")
    # carrying handle: two posts and a long bridge with the sight channel through it
    p += slab(PL, 0.026, [(-0.090, 0.022), (-0.060, 0.022), (-0.066, 0.060), (-0.090, 0.060)], edge=0.005, r=0.005)
    p += slab(PL, 0.026, [(0.090, 0.022), (0.130, 0.022), (0.124, 0.050), (0.096, 0.060)], edge=0.005, r=0.005)
    p += [body(PL, [(-0.096, 0.068, 0.026, 0.022), (-0.086, 0.068, 0.030, 0.026), (0.110, 0.066, 0.030, 0.024), (0.126, 0.062, 0.024, 0.016)], power=3.2)]
    p += rail(PL, -0.080, 0.100, 0.079, w=0.0212)
    p += [B(BLACK, -0.0195, 0.036, -0.058, 0.039, 0.016, 0.146, plain=True)]                               # daylight under the handle
    # handguard with its vent slots, barrel, flash hider
    p += [body(PL, [(0.156, -0.006, 0.040, 0.050), (0.164, -0.006, 0.046, 0.058), (0.360, -0.004, 0.042, 0.052), (0.372, -0.004, 0.036, 0.044)], power=3.4)]
    for i in range(6):
        p += P(BLACK, 0.0213, -0.004, 0.180 + i * 0.030, 0.0014, 0.012, 0.018, plain=True)
    p += [turned(S, [(0.370, 0.019), (0.470, 0.019), (0.470, 0.0165), (0.516, 0.0165)], seg=12)]
    hider = [turned(S, [(0.510, 0.0165), (0.516, 0.022), (0.556, 0.022), (0.560, 0.019)], seg=12), bore(0.560, 0.011)]
    for a in (0, 90, 180, 270):
        hider += [B(BLACK, -0.002, 0.0106, 0.524, 0.004, 0.0008, 0.026, roll=a, pivot=(0, 0, 0.54), plain=True)]
    p += slab(S, 0.016, [(0.440, -0.010), (0.462, -0.010), (0.462, 0.010), (0.440, 0.010)], edge=0.003, r=0.004)
    # sights inside the handle: rear notch block and the front post in its ring
    p += [B(S, -0.0065, 0.079, -0.084, 0.005, 0.014, 0.004, plain=True), B(S, 0.0015, 0.079, -0.084, 0.005, 0.014, 0.004, plain=True),
          B(S, -0.0065, 0.079, -0.084, 0.013, 0.006, 0.004, plain=True),
          lathe(S, "z", 0.0, 0.0925, [(0.104, 0.0085), (0.110, 0.0085)], plain=True, open=True, seg=12), bar(S, (0, 0.082, 0.107), (0, 0.093, 0.107), 0.0022)]
    # folding skeleton stock with its cheek rail and rubber butt
    p += [body(S, [(-0.158, -0.004, 0.036, 0.052), (-0.148, -0.004, 0.036, 0.052)], power=5)]
    p += sweep(PL, [(-0.156, 0.012), (-0.260, 0.010), (-0.372, 0.004)], 0.030, [0.026, 0.022, 0.026], power=3.2)
    p += sweep(PL, [(-0.156, -0.022), (-0.250, -0.050), (-0.372, -0.092)], 0.030, [0.026, 0.022, 0.026], power=3.2)
    p += sweep(PL, [(-0.366, 0.012), (-0.370, -0.104)], 0.031, 0.034, power=3.6, ends="flat")
    p += slab(RUBBER_PAD, 0.032, [(-0.384, 0.014), (-0.392, 0.012), (-0.392, -0.104), (-0.384, -0.106)], edge=0.004, r=0.003)
    carrier = [B(GUN_BARE, -HW - 0.0006, 0.000, 0.040, 0.003, 0.016, 0.070, plain=True)]
    charge = slab(PL, 0.012, [(0.030, 0.024), (0.076, 0.024), (0.070, 0.036), (0.036, 0.036)], edge=0.003, r=0.003)
    trigger = sweep(S, [(0.008, -0.056), (0.010, -0.066), (0.013, -0.076), (0.010, -0.083)], 0.006, 0.006, power=3, seg=8)
    selector = [lathe(S, "x", -0.036, -0.024, [(HW, 0.006), (HW + 0.003, 0.006), (HW + 0.003, 0.0)], plain=True, seg=10)]
    selector += slab(S, 0.004, [(-0.050, -0.0385), (-0.024, -0.0395), (-0.024, -0.0325), (-0.050, -0.0335)], x=HW + 0.002, edge=0.001, r=0.002)
    path = [(0.080, -0.060), (0.080, -0.120)] + arc_path(0.080, -0.120, 0.110, 0.200, 5)[1:]
    mag = sweep(CLEAR, path, 0.029, 0.062, power=8, ends="flat")
    for q in path[1:-1]:                                                                              # the rounds seen through the wall
        for sx in (1, -1):
            mag += [B(CASE_BRASS, sx * 0.0146 - 0.0003, q[1] - 0.008, q[0] - 0.022, 0.0006, 0.007, 0.044, plain=True)]
    mag += sweep(POLYMER, [path[-1], (path[-1][0] + (path[-1][0] - path[-2][0]) * 0.2, path[-1][1] + (path[-1][1] - path[-2][1]) * 0.2)], 0.032, 0.068, power=6, ends="flat")
    mag += cartridge(CASE_BRASS, 0.056, -0.054, 0.0447, 0.0095, 0.0063, 0.012)
    return gun(p, [bone("muzzle", (0, 0, 0.510), hider), bone("bolt", (0, 0.008, 0.07), carrier), bone("charge", (0, 0.03, 0.05), charge),
                   bone("trigger", (0, -0.056, 0.010), trigger), bone("selector", (HW, -0.036, -0.024), selector), bone("mag", (0, -0.060, 0.080), mag)],
               dict(muzzle=(0, 0, 0.560), thread=(0, 0, 0.510), eject=(-0.024, 0.008, 0.07), sight=(0, 0.093, -0.082), front=(0, 0.093, 0.107),
                    optic=(0, 0.0885, 0.010), under=(0, -0.035, 0.26), grip=(0, -0.104, -0.060), hand=(0, -0.038, 0.270), mag=(0, -0.060, 0.080),
                    charge=(0.0, 0.034, 0.050)))


# ---------------------------------------------------------------- SCAR-H
def scarh():
    """SCAR-H, 7.62x51: a tall tan receiver under one unbroken top rail, short railed fore-end, the folding stock
    with its raised cheek piece and angular butt, and a straight 20-round magazine."""
    T, S, PL = _mat("scar_tan", (156, 134, 96), "gunmetal", wear=(100, 94, 84)), GUN_STEEL, POLYMER_TAN
    HW = 0.020
    # upper: one tall extrusion from the stock hinge to the front of the fore-end, rail all along the top
    p = [body(T, [(-0.130, 0.012, 0.036, 0.056), (-0.122, 0.012, 0.040, 0.064), (0.300, 0.012, 0.040, 0.064), (0.310, 0.010, 0.036, 0.054)], power=6)]
    p += rail(T, -0.124, 0.304, 0.0425)
    p += side_rail(T, 0.180, 0.300, 0.020, 0.014, 1) + side_rail(T, 0.180, 0.300, -0.020, 0.014, -1) + rail(T, 0.170, 0.300, -0.020, up=-1)
    p += [B(BLACK, -HW - 0.0004, 0.006, 0.030, 0.001, 0.022, 0.080, plain=True), B(BLACK, HW - 0.0006, 0.014, 0.060, 0.001, 0.008, 0.180, plain=True)]
    p += [label(T, "SCAR-H 7.62", HW + 0.0002, 0.022, -0.090, 0.050, 0.0038)]
    # lower: polymer trigger housing and the magazine well
    p += [body(PL, [(-0.120, -0.030, 0.032, 0.024), (-0.110, -0.032, 0.034, 0.030), (0.036, -0.032, 0.034, 0.030), (0.044, -0.034, 0.034, 0.034)], power=5)]
    p += slab(PL, 0.036, [(0.040, -0.018), (0.128, -0.018), (0.130, -0.074), (0.046, -0.068)], edge=0.005, r=0.006)
    p += sweep(PL, [(-0.036, -0.044), (-0.042, -0.064), (-0.056, -0.104), (-0.072, -0.146)], [0.028, 0.029, 0.031, 0.032], [0.038, 0.040, 0.043, 0.046], power=3.4)
    p += sweep(PL, [(-0.022, -0.048), (-0.012, -0.079), (0.030, -0.081), (0.044, -0.070)], 0.016, 0.004, power=5, seg=8, ends="flat")
    for y, z in ((-0.024, 0.010), (-0.030, -0.080), (0.000, 0.150)):
        p += pin(HW, y, z, 0.006, S)
    # barrel, gas block with the folding front sight, three-prong flash hider
    p += [turned(S, [(0.300, 0.022), (0.400, 0.022), (0.400, 0.0195), (0.470, 0.0195)], seg=12)]
    p += slab(S, 0.022, [(0.312, -0.012), (0.346, -0.012), (0.346, 0.034), (0.312, 0.040)], edge=0.004, r=0.004)
    p += [B(S, -0.0082, 0.040, 0.322, 0.0018, 0.036, 0.010, plain=True), B(S, 0.0064, 0.040, 0.322, 0.0018, 0.036, 0.010, plain=True),
          bar(S, (0, 0.038, 0.327), (0, 0.078, 0.327), 0.0022)]
    hider = [turned(S, [(0.462, 0.0195), (0.468, 0.024), (0.512, 0.024), (0.516, 0.021)], seg=12), bore(0.516, 0.013)]
    for a in (0, 120, 240):
        hider += [B(BLACK, -0.0025, 0.0114, 0.478, 0.005, 0.0010, 0.036, roll=a, pivot=(0, 0, 0.49), plain=True)]
    irons = [B(S, -0.010, 0.050, -0.112, 0.020, 0.006, 0.018, plain=True), B(S, -0.008, 0.056, -0.102, 0.0035, 0.022, 0.004, plain=True),
             B(S, 0.0045, 0.056, -0.102, 0.0035, 0.022, 0.004, plain=True), B(S, -0.008, 0.072, -0.102, 0.016, 0.003, 0.004, plain=True),
             B(S, -0.0045, 0.069, -0.102, 0.003, 0.003, 0.004, plain=True), B(S, 0.0015, 0.069, -0.102, 0.003, 0.003, 0.004, plain=True),
             B(S, -0.008, 0.0795, -0.102, 0.016, 0.0025, 0.004, plain=True)]
    # stock: hinge, tube, the angular butt and the raised cheek piece
    p += [body(S, [(-0.142, 0.004, 0.038, 0.050), (-0.128, 0.004, 0.038, 0.050)], power=5)]
    p += [body(PL, [(-0.150, 0.010, 0.034, 0.036), (-0.160, 0.010, 0.038, 0.042), (-0.300, 0.008, 0.038, 0.042), (-0.320, 0.004, 0.034, 0.036)], power=3.4)]
    p += [body(PL, [(-0.290, 0.036, 0.030, 0.014), (-0.180, 0.038, 0.034, 0.018), (-0.170, 0.036, 0.030, 0.012)], power=3)]
    p += slab(PL, 0.034, [(-0.220, -0.008), (-0.330, -0.010), (-0.346, -0.030), (-0.346, -0.098), (-0.330, -0.104), (-0.262, -0.040)], edge=0.007, r=0.008)
    p += slab(PL, 0.038, [(-0.322, 0.030), (-0.346, 0.026), (-0.346, -0.100), (-0.322, -0.094)], edge=0.007, r=0.006)
    p += slab(RUBBER_PAD, 0.036, [(-0.345, 0.024), (-0.354, 0.021), (-0.354, -0.097), (-0.345, -0.100)], edge=0.004, r=0.003)
    carrier = [B(GUN_BARE, -HW - 0.0008, 0.008, 0.034, 0.004, 0.018, 0.072, plain=True), bar(S, (HW, 0.018, 0.150), (HW + 0.020, 0.018, 0.150), 0.010),
               turned(S, [(0.142, 0.0), (0.145, 0.012), (0.156, 0.012), (0.158, 0.0)], y=0.018, x=HW + 0.024, seg=8)]
    trigger = sweep(S, [(0.008, -0.048), (0.010, -0.060), (0.013, -0.070), (0.010, -0.077)], 0.006, 0.006, power=3, seg=8)
    selector = [lathe(S, "x", -0.024, -0.040, [(HW - 0.003, 0.005), (HW, 0.005), (HW, 0.0)], plain=True, seg=10)]
    selector += slab(S, 0.004, [(-0.064, -0.0265), (-0.040, -0.0275), (-0.040, -0.0205), (-0.064, -0.0215)], x=HW - 0.001, edge=0.001, r=0.002)
    path = [(0.084, -0.046), (0.086, -0.110), (0.090, -0.170)]
    mag = sweep(PL, path, 0.029, 0.070, power=8, ends="flat")
    for q in path[1:]:
        mag += sweep(PL, [q, (q[0] + 0.001, q[1] - 0.006)], 0.031, 0.056, power=6, seg=10, ends="flat")
    mag += sweep(PL, [path[-1], (path[-1][0] + 0.001, path[-1][1] - 0.010)], 0.032, 0.076, power=6, ends="flat")
    mag += cartridge(CASE_BRASS, 0.056, -0.040, 0.0512, 0.0119, 0.0087, 0.020)
    return gun(p, [bone("muzzle", (0, 0, 0.462), hider), bone("bolt", (0, 0.018, 0.10), carrier), bone("trigger", (0, -0.048, 0.010), trigger),
                   bone("selector", (HW, -0.024, -0.040), selector), bone("irons", (0, 0.050, -0.096), irons), bone("mag", (0, -0.050, 0.084), mag)],
               dict(muzzle=(0, 0, 0.516), thread=(0, 0, 0.462), eject=(-0.024, 0.014, 0.07), sight=(0, 0.078, -0.100), front=(0, 0.078, 0.327),
                    optic=(0, 0.050, 0.020), under=(0, -0.0275, 0.235), grip=(0, -0.102, -0.058), hand=(0, -0.030, 0.240), mag=(0, -0.050, 0.084),
                    charge=(HW + 0.024, 0.018, 0.150)))


# ---------------------------------------------------------------- belt-fed: M249, M240
def _belt(case, round_, x, z, y0, y1, turn):
    """A belt rising from a box to the feed tray on the side at `x`: rounds nose forward in their links, the top
    ones leaning in towards the tray (`turn` is 1 or -1, the way they lean)."""
    out = []
    n = max(3, int(round((y1 - y0) / (round_[1] * 1.38))) + 1)
    rows = []
    for i in range(n):
        f = i / (n - 1)
        lean = max(0.0, f - 0.7) / 0.3
        rows.append((x + turn * 0.012 * lean * lean, y0 + (y1 - y0) * f))
    for i, (bx, by) in enumerate(rows):
        out += cartridge(case, z, by, *round_, x=bx)
        out += [turned(GUN_STEEL, [(z + round_[0] * 0.30, round_[1] * 1.22), (z + round_[0] * 0.66, round_[1] * 1.22)], y=by, x=bx, plain=True, open=True, seg=8)]
        if i:
            px, py = rows[i - 1]
            out += [B(GUN_STEEL, min(bx, px) - 0.003, py + 0.003, z + round_[0] * 0.40, abs(bx - px) + 0.006, max(0.002, by - py - 0.006), round_[0] * 0.18, plain=True)]
    return out


def m249():
    """M249 light machine gun, 5.56x45: a pressed-steel receiver with the feed cover on top, the barrel under its
    heat shield with a carrying handle, folding bipod, tubular stock, and a 200-round box clipped underneath to the
    left with the belt running up into the feed tray."""
    S, K, PL = GUN_STEEL, GUN_BLACK, POLYMER
    HW = 0.021
    p = [body(K, [(-0.150, -0.006, 0.038, 0.058), (-0.140, -0.008, 0.042, 0.068), (0.200, -0.008, 0.042, 0.068), (0.212, -0.006, 0.038, 0.058)], power=7)]
    for z in (-0.10, -0.02, 0.06, 0.14):
        p += slab(K, 0.0016, [(z, -0.034), (z + 0.050, -0.034), (z + 0.050, 0.010), (z, 0.010)], x=HW + 0.0004, edge=0.0006, r=0.004)
        p += slab(K, 0.0016, [(z, -0.034), (z + 0.050, -0.034), (z + 0.050, 0.010), (z, 0.010)], x=-HW - 0.0004, edge=0.0006, r=0.004)
    p += [label(K, "M249 5.56MM", HW + 0.0014, -0.012, -0.090, 0.050, 0.004)]
    # feed tray on the left, ejection port on the right
    p += slab(S, 0.020, [(0.010, 0.022), (0.090, 0.022), (0.090, 0.030), (0.010, 0.030)], x=0.031, edge=0.003, r=0.003)
    p += slab(S, 0.004, [(0.010, 0.004), (0.090, 0.004), (0.090, 0.030), (0.010, 0.030)], x=0.0225, edge=0.001, r=0.003)
    p += [B(BLACK, -HW - 0.0004, -0.018, 0.020, 0.001, 0.018, 0.060, plain=True)]
    # trigger housing, grip, guard
    p += [body(K, [(-0.100, -0.046, 0.030, 0.020), (0.030, -0.046, 0.030, 0.020)], power=4)]
    p += sweep(PL, [(-0.036, -0.052), (-0.042, -0.072), (-0.056, -0.110), (-0.072, -0.150)], [0.028, 0.029, 0.031, 0.032], [0.038, 0.040, 0.043, 0.046], power=3.4)
    p += sweep(S, [(-0.022, -0.056), (-0.012, -0.085), (0.026, -0.087), (0.038, -0.078)], 0.015, 0.003, power=6, seg=8, ends="flat")
    # barrel in its heat shield, carrying handle, gas tube, front sight, flash hider
    p += [turned(S, [(0.212, 0.036), (0.240, 0.036), (0.250, 0.028), (0.560, 0.024), (0.640, 0.020)], seg=14)]
    p += [body(PL, [(0.250, 0.012, 0.040, 0.030), (0.258, 0.014, 0.046, 0.036), (0.470, 0.012, 0.042, 0.032), (0.480, 0.010, 0.036, 0.024)], power=2.6)]
    for i in range(6):
        p += P(BLACK, 0.021 - i * 0.0004, 0.014, 0.272 + i * 0.032, 0.0014, 0.007, 0.020, plain=True)
    p += [bar(S, (0.0, 0.026, 0.262), (-0.030, 0.062, 0.262), 0.008)] + sweep(PL, [(0.242, 0.062), (0.340, 0.062)], 0.022, 0.022, power=2.2, seg=10, x=-0.030)
    p += [turned(S, [(0.212, 0.020), (0.520, 0.020)], y=-0.030, seg=10)]
    p += slab(S, 0.024, [(0.504, -0.042), (0.540, -0.042), (0.540, 0.014), (0.504, 0.014)], edge=0.004, r=0.005)
    p += slab(S, 0.018, [(0.560, -0.010), (0.588, -0.010), (0.588, 0.012), (0.582, 0.044), (0.566, 0.044), (0.560, 0.012)], edge=0.0035)
    p += [B(S, 0.0066, 0.043, 0.566, 0.0018, 0.022, 0.014, roll=-8, pivot=(0.0074, 0.044, 0.573), plain=True),
          B(S, -0.0084, 0.043, 0.566, 0.0018, 0.022, 0.014, roll=8, pivot=(-0.0074, 0.044, 0.573), plain=True), bar(S, (0, 0.042, 0.573), (0, 0.070, 0.573), 0.0024)]
    hider = [turned(S, [(0.634, 0.020), (0.640, 0.024), (0.676, 0.024), (0.680, 0.020)], seg=12), bore(0.680, 0.011)]
    for a in (0, 72, 144, 216, 288):
        hider += [B(BLACK, -0.002, 0.0114, 0.646, 0.004, 0.0008, 0.026, roll=a, pivot=(0, 0, 0.66), plain=True)]
    # stock: two tubes to a shoulder plate with its folding rest
    p += [body(S, [(-0.158, -0.008, 0.040, 0.060), (-0.148, -0.008, 0.040, 0.060)], power=5)]
    p += sweep(S, [(-0.154, 0.012), (-0.350, 0.012)], 0.018, 0.018, power=2.2, seg=10) + sweep(S, [(-0.154, -0.030), (-0.350, -0.050)], 0.018, 0.018, power=2.2, seg=10)
    p += slab(PL, 0.040, [(-0.346, 0.028), (-0.372, 0.024), (-0.372, -0.086), (-0.346, -0.082)], edge=0.008, r=0.008)
    p += sweep(PL, [(-0.200, 0.016), (-0.330, 0.018)], 0.036, 0.024, power=3, seg=10)
    cover = [body(K, [(-0.132, 0.036, 0.036, 0.030), (-0.122, 0.034, 0.0425, 0.044), (0.120, 0.034, 0.0425, 0.044), (0.132, 0.034, 0.038, 0.034)], power=3.4)]
    cover += rail(K, -0.110, 0.110, 0.055)
    cover += [B(S, -0.010, 0.062, -0.130, 0.020, 0.008, 0.024, plain=True), B(S, -0.0065, 0.064, -0.126, 0.005, 0.0065, 0.004, plain=True),
              B(S, 0.0015, 0.064, -0.126, 0.005, 0.0065, 0.004, plain=True)]
    charge = [bar(GUN_BARE, (-HW, -0.016, 0.060), (-HW - 0.022, -0.016, 0.060), 0.011),
              turned(GUN_BARE, [(0.050, 0.0), (0.053, 0.013), (0.067, 0.014), (0.070, 0.0)], y=-0.016, x=-HW - 0.026, seg=8)]
    trigger = sweep(S, [(0.008, -0.055), (0.010, -0.065), (0.013, -0.075), (0.010, -0.082)], 0.006, 0.006, power=3, seg=8)
    selector = [lathe(S, "x", -0.036, -0.030, [(-HW - 0.004, 0.0), (-HW - 0.004, 0.005), (-HW, 0.005)], plain=True, seg=8)]
    bipod = [body(S, [(0.500, -0.044, 0.050, 0.014), (0.516, -0.044, 0.050, 0.014)], power=4)]
    for sgn in (-1, 1):
        bipod += [bar(S, (sgn * 0.022, -0.050, 0.508), (sgn * 0.030, -0.060, 0.240), 0.010),
                  B(S, sgn * 0.030 - 0.006, -0.066, 0.228, 0.012, 0.004, 0.022, plain=True)]
    # 200-round box: a green plastic can clipped under the receiver, leaning out to the left, belt out of its top
    BX, BELT = 0.034, 0.040
    can = lambda mat, secs, power: skin(mat, [(z, BX, cy, w, h) for z, cy, w, h in secs], power=power, seg=20)
    box = [can(AMMO_GREEN, [(-0.030, -0.122, 0.108, 0.150), (-0.024, -0.122, 0.116, 0.158), (0.124, -0.122, 0.116, 0.158), (0.130, -0.122, 0.108, 0.150)], 7),
           can(AMMO_GREEN, [(-0.032, -0.046, 0.118, 0.010), (0.132, -0.046, 0.118, 0.010)], 5),
           B(AMMO_GREEN, BX - 0.045, -0.130, -0.0304, 0.09, 0.022, 0.0003, yaw=180, plain=True, marks=[("front", "text:200 5.56", 0.5, 0.5, 0.011, (30, 30, 26))]),
           B(S, -0.006, -0.044, 0.030, 0.012, 0.006, 0.040, plain=True),
           B(BLACK, BELT - 0.010, -0.0412, 0.008, 0.020, 0.0008, 0.066, plain=True)]
    for y in (-0.090, -0.150):
        box += [can(AMMO_GREEN, [(-0.026, y, 0.1175, 0.006), (0.126, y, 0.1175, 0.006)], 4)]
    belt = _belt(CASE_BRASS, (0.0447, 0.0095, 0.0063, 0.012), BELT, 0.016, -0.048, 0.020, -1)
    return gun(p, [bone("muzzle", (0, 0, 0.634), hider), bone("cover", (0, 0.030, 0.132), cover), bone("bolt", (0, -0.016, 0.06), charge),
                   bone("trigger", (0, -0.055, 0.010), trigger), bone("selector", (-HW, -0.036, -0.030), selector),
                   bone("bipod", (0, -0.044, 0.508), bipod), bone("mag", (0, -0.046, 0.050), box),
                   dict(name="belt", pivot=(BELT, -0.02, 0.04), cubes=belt)],
               dict(muzzle=(0, 0, 0.680), thread=(0, 0, 0.634), eject=(-0.026, -0.02, 0.05), sight=(0, 0.0705, -0.126), front=(0, 0.070, 0.573),
                    optic=(0, 0.0645, 0.000), under=(0, -0.05, 0.36), grip=(0, -0.106, -0.058), hand=(0.034, -0.206, 0.050), mag=(0.034, -0.086, 0.050),
                    charge=(-0.048, -0.016, 0.060), cover=(0, 0.062, -0.090), belt=(0.054, -0.010, 0.040)))


def m240():
    """M240 general-purpose machine gun, 7.62x51: a long riveted receiver, feed cover, heavy barrel with its gas
    regulator and carrying handle, bipod, polymer butt, and a 100-round belt in a can hung on the left."""
    S, K, PL = GUN_STEEL, GUN_BLACK, POLYMER
    HW = 0.023
    p = [body(K, [(-0.230, -0.008, 0.040, 0.064), (-0.220, -0.010, 0.046, 0.078), (0.240, -0.010, 0.046, 0.078), (0.252, -0.008, 0.042, 0.066)], power=8)]
    for z in range(-20, 22, 6):                                                                       # rivets along both side plates
        for y in (0.020, -0.040):
            p += pin(HW, y, z / 100.0, 0.0055, S)
    p += [label(K, "M240 7.62MM", HW + 0.0002, -0.012, -0.170, 0.055, 0.0042)]
    p += slab(S, 0.022, [(0.020, 0.024), (0.112, 0.024), (0.112, 0.032), (0.020, 0.032)], x=0.034, edge=0.003, r=0.003)
    p += slab(S, 0.004, [(0.020, 0.004), (0.112, 0.004), (0.112, 0.032), (0.020, 0.032)], x=0.0245, edge=0.001, r=0.003)
    p += [B(BLACK, -0.014, -0.0496, 0.020, 0.028, 0.001, 0.080, plain=True)]                                # cases fall out underneath
    # trigger housing and grip under the rear of the receiver
    p += [body(K, [(-0.200, -0.054, 0.030, 0.022), (-0.090, -0.054, 0.030, 0.022)], power=4)]
    p += sweep(PL, [(-0.170, -0.062), (-0.176, -0.082), (-0.190, -0.118), (-0.204, -0.156)], [0.028, 0.029, 0.031, 0.032], [0.038, 0.040, 0.043, 0.046], power=3.4)
    p += sweep(S, [(-0.158, -0.064), (-0.148, -0.092), (-0.110, -0.094), (-0.098, -0.084)], 0.015, 0.003, power=6, seg=8, ends="flat")
    # barrel: heavy, with the gas regulator and its tube below, carrying handle, front sight, slotted flash hider
    p += [turned(S, [(0.250, 0.044), (0.290, 0.044), (0.300, 0.034), (0.640, 0.028), (0.800, 0.024)], seg=14)]
    p += [bar(S, (0.0, 0.014, 0.300), (-0.034, 0.058, 0.300), 0.009)] + sweep(PL, [(0.280, 0.058), (0.390, 0.058)], 0.024, 0.024, power=2.2, seg=10, x=-0.034)
    p += [turned(S, [(0.250, 0.026), (0.560, 0.026), (0.560, 0.032), (0.600, 0.032), (0.606, 0.022)], y=-0.036, seg=12)]
    p += slab(S, 0.028, [(0.556, -0.052), (0.604, -0.052), (0.604, 0.012), (0.556, 0.012)], edge=0.004, r=0.006)
    p += slab(S, 0.018, [(0.736, -0.011), (0.766, -0.011), (0.766, 0.013), (0.760, 0.048), (0.744, 0.048), (0.736, 0.013)], edge=0.0035)
    p += [B(S, 0.0066, 0.047, 0.744, 0.0018, 0.024, 0.014, roll=-8, pivot=(0.0074, 0.048, 0.751), plain=True),
          B(S, -0.0084, 0.047, 0.744, 0.0018, 0.024, 0.014, roll=8, pivot=(-0.0074, 0.048, 0.751), plain=True), bar(S, (0, 0.046, 0.751), (0, 0.076, 0.751), 0.0026)]
    hider = [turned(S, [(0.796, 0.024), (0.804, 0.030), (0.856, 0.028), (0.864, 0.024)], seg=12), bore(0.864, 0.012)]
    for a in (0, 60, 120, 180, 240, 300):
        hider += [B(BLACK, -0.002, 0.0138, 0.812, 0.004, 0.0008, 0.040, roll=a, pivot=(0, 0, 0.83), plain=True)]
    # butt: a deep polymer stock with a hinged shoulder rest
    p += [body(S, [(-0.240, -0.008, 0.044, 0.070), (-0.228, -0.008, 0.044, 0.070)], power=5)]
    p += slab(PL, 0.042, [(-0.236, 0.026), (-0.420, 0.016), (-0.424, -0.094), (-0.410, -0.100), (-0.290, -0.050), (-0.236, -0.046)], edge=0.010, r=0.012)
    p += slab(S, 0.040, [(-0.422, 0.018), (-0.430, 0.015), (-0.430, -0.095), (-0.422, -0.098)], edge=0.004, r=0.003)
    cover = [body(K, [(-0.150, 0.040, 0.040, 0.030), (-0.140, 0.038, 0.047, 0.046), (0.140, 0.038, 0.047, 0.046), (0.152, 0.038, 0.042, 0.036)], power=3.4)]
    cover += rail(K, -0.120, 0.130, 0.060)
    cover += [B(S, -0.010, 0.067, -0.150, 0.020, 0.008, 0.026, plain=True), B(S, -0.0065, 0.070, -0.146, 0.005, 0.0065, 0.004, plain=True),
              B(S, 0.0015, 0.070, -0.146, 0.005, 0.0065, 0.004, plain=True)]
    charge = [bar(GUN_BARE, (-HW, -0.020, 0.040), (-HW - 0.026, -0.020, 0.040), 0.012),
              turned(GUN_BARE, [(0.030, 0.0), (0.033, 0.014), (0.047, 0.015), (0.050, 0.0)], y=-0.020, x=-HW - 0.030, seg=8),
              B(S, -HW - 0.002, -0.026, 0.010, 0.002, 0.012, 0.080, plain=True)]
    trigger = sweep(S, [(-0.126, -0.063), (-0.124, -0.073), (-0.121, -0.083), (-0.124, -0.090)], 0.006, 0.006, power=3, seg=8)
    selector = [lathe(S, "x", -0.050, -0.150, [(-HW - 0.004, 0.0), (-HW - 0.004, 0.005), (-HW, 0.005)], plain=True, seg=8)]
    bipod = [body(S, [(0.572, -0.058, 0.056, 0.016), (0.590, -0.058, 0.056, 0.016)], power=4)]
    for sgn in (-1, 1):
        bipod += [bar(S, (sgn * 0.024, -0.064, 0.580), (sgn * 0.032, -0.074, 0.290), 0.012),
                  B(S, sgn * 0.032 - 0.007, -0.081, 0.276, 0.014, 0.004, 0.026, plain=True)]
    BX, BELT = 0.046, 0.046
    can = lambda mat, secs, power: skin(mat, [(z, BX, cy, w, h) for z, cy, w, h in secs], power=power, seg=20)
    box = [can(AMMO_GREEN, [(0.010, -0.120, 0.090, 0.130), (0.016, -0.120, 0.098, 0.138), (0.136, -0.120, 0.098, 0.138), (0.142, -0.120, 0.090, 0.130)], 9),
           can(AMMO_GREEN, [(0.008, -0.054, 0.100, 0.010), (0.144, -0.054, 0.100, 0.010)], 6),
           B(AMMO_GREEN, BX - 0.040, -0.128, 0.0096, 0.08, 0.022, 0.0003, yaw=180, plain=True, marks=[("front", "text:100 7.62", 0.5, 0.5, 0.011, (30, 30, 26))]),
           B(S, 0.014, -0.052, 0.050, 0.014, 0.006, 0.040, plain=True),
           B(BLACK, BELT - 0.012, -0.0492, 0.022, 0.024, 0.0008, 0.084, plain=True)]
    for y in (-0.094, -0.146):
        box += [can(AMMO_GREEN, [(0.014, y, 0.0995, 0.006), (0.138, y, 0.0995, 0.006)], 4)]
    belt = _belt(CASE_BRASS, (0.0512, 0.0119, 0.0087, 0.020), BELT, 0.026, -0.056, 0.022, -1)
    return gun(p, [bone("muzzle", (0, 0, 0.796), hider), bone("cover", (0, 0.034, 0.152), cover), bone("bolt", (0, -0.020, 0.04), charge),
                   bone("trigger", (0, -0.063, -0.126), trigger), bone("selector", (-HW, -0.050, -0.150), selector),
                   bone("bipod", (0, -0.058, 0.580), bipod), bone("mag", (0.02, -0.054, 0.076), box),
                   dict(name="belt", pivot=(BELT, -0.02, 0.06), cubes=belt)],
               dict(muzzle=(0, 0, 0.864), thread=(0, 0, 0.796), eject=(0.0, -0.05, 0.06), sight=(0, 0.0765, -0.146), front=(0, 0.076, 0.751),
                    optic=(0, 0.0695, 0.000), under=(0, -0.06, 0.40), grip=(0, -0.112, -0.192), hand=(0.046, -0.194, 0.076), mag=(0.046, -0.090, 0.076),
                    charge=(-0.052, -0.020, 0.040), cover=(0, 0.068, -0.100), belt=(0.060, -0.010, 0.060)))


def shotshell(z, y, x=0.0):
    """A 12-gauge shell lying along z with its brass head at z."""
    return [turned(CASE_BRASS, [(z, 0.0222), (z + 0.002, 0.0222), (z + 0.002, 0.0204), (z + 0.016, 0.0204)], y, x, plain=True, seg=10),
            turned(SHELL_RED, [(z + 0.016, 0.0202), (z + 0.066, 0.0202), (z + 0.070, 0.016), (z + 0.070, 0.0)], y, x, plain=True, seg=10)]


# ---------------------------------------------------------------- sights and muzzle devices
# Each fitting is built with its mounting face at the origin: optics sit on the rail under them, muzzle devices
# screw on at z = 0 and run forward, the grip hangs from the rail above it.
def red_dot():
    """A micro red-dot sight on a riser: a short tube with flip lens covers open."""
    K = GUN_BLACK
    p = rail_clamp(K, -0.022, 0.022) + [B(K, -0.010, 0.012, -0.016, 0.020, 0.012, 0.032, plain=True)]
    # a hollow tube the eye looks straight through, with a rim at each end
    p += [lathe(K, "z", 0.0, 0.040, [(-0.032, 0.0165), (0.032, 0.0165)], open=True, seg=12),
          lathe(BLACK, "z", 0.0, 0.040, [(0.032, 0.0140), (-0.032, 0.0140)], plain=True, open=True, seg=12),
          lathe(K, "z", 0.0, 0.040, [(-0.032, 0.0165), (-0.032, 0.0140)], plain=True, open=True, seg=12),
          lathe(K, "z", 0.0, 0.040, [(0.032, 0.0140), (0.032, 0.019), (0.038, 0.019), (0.038, 0.0165)], plain=True, open=True, seg=12)]
    p += [lathe(K, "y", 0.0, 0.004, [(0.056, 0.008), (0.066, 0.008)], plain=True, seg=8), lathe(K, "x", 0.040, 0.004, [(-0.026, 0.008), (-0.0165, 0.008)], plain=True, seg=8),
          lathe(K, "x", 0.034, -0.012, [(0.0165, 0.007), (0.030, 0.007)], plain=True, seg=8)]
    return gun(p, [], dict(eye=(0, 0.040, -0.032), lens=(0, 0.040, 0.030)))


def rail_clamp(mat, z0, z1):
    """The foot of an optic: a block gripping the rail, with a cross bolt and thumb nut."""
    return [B(mat, -0.0125, 0.0, z0, 0.025, 0.012, z1 - z0, plain=True),
            lathe(GUN_STEEL, "x", 0.006, (z0 + z1) / 2, [(-0.020, 0.005), (-0.0125, 0.005)], plain=True, seg=6)]


def acog():
    """A 4x32 prismatic sight: the wedge-shaped housing with its fibre-optic strip along the top."""
    K = GUN_GREY
    p = rail_clamp(K, -0.045, 0.045) + [B(K, -0.011, 0.012, -0.035, 0.022, 0.012, 0.070, plain=True)]
    p += [turned(K, [(-0.072, 0.0), (-0.072, 0.036), (-0.050, 0.036), (-0.040, 0.030), (0.010, 0.032), (0.030, 0.046), (0.074, 0.046), (0.074, 0.0)], y=0.044, open=True, seg=12)]
    p += [lathe(LENS, "z", 0.0, 0.044, [(0.071, 0.0205), (0.071, 0.0)], plain=True, seg=12),
          lathe(LENS, "z", 0.0, 0.044, [(-0.070, 0.0)  , (-0.070, 0.0155)], plain=True, seg=12)]
    p += [B(LENS_RED, -0.004, 0.060, -0.030, 0.008, 0.004, 0.050, plain=True), B(K, -0.012, 0.030, -0.010, 0.024, 0.030, 0.026, plain=True),
          lathe(K, "y", 0.0, 0.003, [(0.060, 0.008), (0.068, 0.008)], plain=True, seg=8), lathe(K, "x", 0.044, 0.003, [(-0.022, 0.008), (-0.012, 0.008)], plain=True, seg=8)]
    return gun(p, [], dict(eye=(0, 0.044, -0.072), lens=(0, 0.044, 0.072)))


def pso1():
    """PSO-1 4x24: a slim tube on a side-rail bracket, with its long rubber eyecup, two capped turrets, the
    extended sunshade and the battery housing for the lit reticle."""
    K, S = GUN_BLACK, GUN_STEEL
    AX = 0.036
    p = [B(S, 0.016, -0.060, -0.060, 0.012, 0.040, 0.120, plain=True), B(S, 0.004, -0.022, -0.050, 0.022, 0.012, 0.100, plain=True),
         bar(S, (0.030, -0.045, 0.000), (0.040, -0.052, -0.018), 0.008),
         B(S, -0.014, -0.012, -0.040, 0.032, 0.030, 0.014, plain=True), B(S, -0.014, -0.012, 0.030, 0.032, 0.030, 0.014, plain=True)]
    p += [turned(K, [(-0.185, 0.0), (-0.185, 0.044), (-0.150, 0.040), (-0.118, 0.036), (-0.112, 0.030), (0.090, 0.030), (0.100, 0.034), (0.190, 0.034), (0.190, 0.0)],
                 y=AX, open=True, seg=12)]
    p += [lathe(LENS, "z", 0.0, AX, [(0.150, 0.0145), (0.150, 0.0)], plain=True, seg=12), lathe(LENS, "z", 0.0, AX, [(-0.150, 0.0), (-0.150, 0.016)], plain=True, seg=12)]
    p += [lathe(K, "y", 0.0, 0.010, [(AX + 0.014, 0.0125), (AX + 0.032, 0.0125)], plain=True, seg=10),
          lathe(K, "x", AX, 0.010, [(-0.034, 0.0125), (-0.015, 0.0125)], plain=True, seg=10),
          lathe(K, "y", 0.0, 0.060, [(AX - 0.040, 0.008), (AX - 0.014, 0.008)], plain=True, seg=8)]
    return gun(p, [], dict(eye=(0, AX, -0.185), lens=(0, AX, 0.150)))


def scope_10x():
    """A 10x telescopic sight: a long tube swelling to the objective bell, eyepiece with its focus ring, elevation
    and windage drums, in two rings on a rail base."""
    K, S = GUN_BLACK, GUN_STEEL
    AX = 0.046
    p = rail_clamp(K, -0.070, 0.070) + [B(K, -0.011, 0.010, -0.060, 0.022, 0.010, 0.120, plain=True)]
    for z in (-0.052, 0.036):
        p += [turned(K, [(z, 0.040), (z + 0.016, 0.040)], y=AX, seg=14), B(K, -0.009, 0.018, z, 0.018, 0.012, 0.016, plain=True)]
    p += [turned(K, [(-0.175, 0.040), (-0.165, 0.044), (-0.110, 0.044), (-0.100, 0.032), (0.060, 0.032), (0.110, 0.052), (0.180, 0.056), (0.186, 0.052)],
                 y=AX, open=True, seg=14),
          turned(S, [(-0.150, 0.0452), (-0.138, 0.0452)], y=AX, plain=True, open=True, seg=14)]
    p += [lathe(LENS, "z", 0.0, AX, [(0.176, 0.0255), (0.176, 0.0)], plain=True, seg=14), lathe(LENS, "z", 0.0, AX, [(-0.170, 0.0), (-0.170, 0.0205)], plain=True, seg=14)]
    p += [lathe(K, "y", 0.0, -0.016, [(AX + 0.014, 0.012), (AX + 0.034, 0.012), (AX + 0.034, 0.0)], plain=True, seg=10),
          lathe(K, "x", AX, -0.016, [(-0.036, 0.0), (-0.036, 0.012), (-0.014, 0.012)], plain=True, seg=10),
          lathe(K, "x", AX, -0.016, [(0.014, 0.010), (0.030, 0.010), (0.030, 0.0)], plain=True, seg=10)]
    return gun(p, [], dict(eye=(0, AX, -0.175), lens=(0, AX, 0.176)))


def holo():
    """A holographic sight: a squat housing with a square window under a protective hood, battery box in front."""
    K = GUN_BLACK
    p = rail_clamp(K, -0.040, 0.040) + [B(K, -0.019, 0.012, -0.040, 0.038, 0.016, 0.080, plain=True)]
    p += slab(K, 0.004, [(-0.030, 0.026), (0.016, 0.026), (0.016, 0.064), (-0.022, 0.064), (-0.030, 0.052)], x=0.0185, edge=0.001, r=0.004)
    p += slab(K, 0.004, [(-0.030, 0.026), (0.016, 0.026), (0.016, 0.064), (-0.022, 0.064), (-0.030, 0.052)], x=-0.0185, edge=0.001, r=0.004)
    p += [B(K, -0.0205, 0.062, -0.024, 0.041, 0.004, 0.040, plain=True), B(K, -0.019, 0.026, 0.012, 0.038, 0.004, 0.004, plain=True),
          B(K, -0.019, 0.026, 0.012, 0.004, 0.038, 0.004, plain=True), B(K, 0.015, 0.026, 0.012, 0.004, 0.038, 0.004, plain=True),
          B(K, -0.019, 0.016, 0.018, 0.038, 0.022, 0.022, plain=True)]
    p += [lathe(GUN_STEEL, "x", 0.020, -0.030, [(0.019, 0.006), (0.026, 0.006), (0.026, 0.0)], plain=True, seg=8)]
    return gun(p, [], dict(eye=(0, 0.045, -0.030), lens=(0, 0.045, 0.012)))


def lpvo():
    """A 1-6x variable scope: a stout 30 mm tube with a power ring behind the turrets, in a one-piece forward mount."""
    K, S = GUN_BLACK, GUN_STEEL
    AX = 0.048
    p = rail_clamp(K, -0.050, 0.060) + [B(K, -0.011, 0.010, -0.044, 0.022, 0.012, 0.100, plain=True)]
    for z in (-0.040, 0.040):
        p += [turned(K, [(z, 0.038), (z + 0.016, 0.038)], y=AX, seg=14), B(K, -0.009, 0.018, z, 0.018, 0.014, 0.016, plain=True)]
    p += [turned(K, [(-0.140, 0.042), (-0.132, 0.046), (-0.090, 0.046), (-0.082, 0.032), (0.070, 0.032), (0.100, 0.038), (0.130, 0.038), (0.134, 0.034)],
                 y=AX, open=True, seg=14),
          turned(S, [(-0.084, 0.036), (-0.066, 0.036)], y=AX, plain=True, open=True, seg=14)]
    p += [lathe(LENS, "z", 0.0, AX, [(0.126, 0.0175), (0.126, 0.0)], plain=True, seg=14), lathe(LENS, "z", 0.0, AX, [(-0.136, 0.0), (-0.136, 0.0205)], plain=True, seg=14)]
    p += [lathe(K, "y", 0.0, -0.002, [(AX + 0.014, 0.012), (AX + 0.030, 0.012), (AX + 0.030, 0.0)], plain=True, seg=10),
          lathe(K, "x", AX, -0.002, [(-0.034, 0.0), (-0.034, 0.012), (-0.014, 0.012)], plain=True, seg=10)]
    return gun(p, [], dict(eye=(0, AX, -0.140), lens=(0, AX, 0.126)))


def magnifier():
    """A 3x magnifier: a short telescope on a hinged mount that swings it in behind the sight or out to the side."""
    K, S = GUN_BLACK, GUN_STEEL
    AX = 0.040
    p = rail_clamp(K, -0.020, 0.020) + [B(K, -0.022, 0.010, -0.016, 0.034, 0.008, 0.032, plain=True),
                                        lathe(S, "z", -0.020, 0.014, [(-0.018, 0.004), (0.018, 0.004)], plain=True, seg=8)]
    p += [B(K, -0.009, 0.016, -0.012, 0.018, 0.010, 0.024, plain=True)]
    p += [turned(K, [(-0.052, 0.034), (-0.044, 0.038), (-0.020, 0.038), (-0.014, 0.032), (0.030, 0.032), (0.036, 0.036), (0.048, 0.036)], y=AX, open=True, seg=14)]
    p += [lathe(LENS, "z", 0.0, AX, [(0.044, 0.0165), (0.044, 0.0)], plain=True, seg=14), lathe(LENS, "z", 0.0, AX, [(-0.048, 0.0), (-0.048, 0.0165)], plain=True, seg=14)]
    return gun(p, [], dict(eye=(0, AX, -0.052)))


def nv_scope():
    """A night sight: a fat image-intensifier tube with a big objective, rubber eye cup and a battery pod on the side."""
    K, S = GUN_BLACK, GUN_STEEL
    AX = 0.056
    p = rail_clamp(K, -0.060, 0.060) + [B(K, -0.014, 0.010, -0.050, 0.028, 0.016, 0.100, plain=True)]
    p += [turned(K, [(-0.150, 0.050), (-0.110, 0.044), (-0.100, 0.060), (0.060, 0.060), (0.076, 0.076), (0.150, 0.080), (0.156, 0.074)], y=AX, open=True, seg=16)]
    p += [lathe(_mat("nv_lens", (40, 110, 60), "lens"), "z", 0.0, AX, [(0.148, 0.037), (0.148, 0.0)], plain=True, seg=16),
          lathe(LENS, "z", 0.0, AX, [(-0.146, 0.0), (-0.146, 0.022)], plain=True, seg=14)]
    p += [lathe(K, "z", 0.036, AX + 0.010, [(-0.060, 0.0), (-0.060, 0.014), (0.020, 0.014), (0.020, 0.0)], plain=True, seg=10),
          lathe(S, "y", 0.0, -0.030, [(AX + 0.030, 0.010), (AX + 0.040, 0.010), (AX + 0.040, 0.0)], plain=True, seg=10)]
    return gun(p, [], dict(eye=(0, AX, -0.150), lens=(0, AX, 0.148)))


def thermal_scope():
    """A thermal weapon sight: a boxy housing with a short germanium objective, a shrouded eyepiece and a keypad on top."""
    K, S = GUN_GREY, GUN_STEEL
    AX = 0.052
    p = rail_clamp(K, -0.055, 0.055)
    p += [body(K, [(-0.080, AX, 0.050, 0.056), (-0.072, AX, 0.058, 0.066), (0.070, AX, 0.058, 0.066), (0.080, AX, 0.050, 0.056)], power=6)]
    p += [turned(K, [(0.078, 0.052), (0.120, 0.056), (0.124, 0.050)], y=AX, open=True, seg=14),
          lathe(_mat("ge_lens", (70, 50, 40), "lens"), "z", 0.0, AX, [(0.118, 0.025), (0.118, 0.0)], plain=True, seg=14),
          turned(POLYMER, [(-0.078, 0.040), (-0.120, 0.046), (-0.132, 0.054)], y=AX, open=True, seg=14),
          lathe(LENS, "z", 0.0, AX, [(-0.100, 0.0), (-0.100, 0.019)], plain=True, seg=14)]
    for i in range(4):
        p += [B(POLYMER, -0.022 + i * 0.012, AX + 0.033, -0.030, 0.008, 0.002, 0.012, plain=True)]
    p += [label(K, "THERMAL", 0.0292, AX, -0.050, 0.05, 0.004)]
    return gun(p, [], dict(eye=(0, AX, -0.132), lens=(0, AX, 0.118)))


# Side fittings are built with the rail face at x = 0 and the device standing out to the left (+x).
def flashlight():
    """A weapon light: a knurled body with a wide bezel and lens, in a clamp ring, tail switch at the back."""
    K, S = GUN_BLACK, GUN_STEEL
    p = [B(K, 0.0, -0.011, -0.014, 0.008, 0.022, 0.028, plain=True), turned(K, [(-0.012, 0.030), (0.012, 0.030)], y=0.0, x=0.019, seg=12)]
    p += [turned(K, [(-0.058, 0.018), (-0.052, 0.024), (0.030, 0.024), (0.040, 0.034), (0.064, 0.034)], x=0.019, seg=12),
          lathe(WHITE, "z", 0.019, 0.0, [(0.062, 0.0145), (0.062, 0.0)], plain=True, seg=12),
          turned(S, [(-0.066, 0.0), (-0.066, 0.012), (-0.058, 0.014)], x=0.019, seg=8)]
    for z in (-0.040, -0.030, -0.020):
        p += [turned(S, [(z, 0.0246), (z + 0.004, 0.0246)], x=0.019, plain=True, open=True, seg=12)]
    return gun(p, [], {})


def laser():
    """A laser pointer: a small box on a rail clamp with its emitter window and two adjusters."""
    K, S = GUN_BLACK, GUN_STEEL
    p = [B(K, 0.0, -0.011, -0.016, 0.006, 0.022, 0.032, plain=True)]
    p += [body(K, [(-0.036, 0.0, 0.026, 0.030), (-0.030, 0.0, 0.030, 0.034), (0.030, 0.0, 0.030, 0.034), (0.036, 0.0, 0.026, 0.030)], power=6)]
    for q in p[-1:]:
        q["sections"] = [(z, 0.021, cy, w, h) for z, cx, cy, w, h in q["sections"]]
    p += [lathe(LENS_RED, "z", 0.021, 0.004, [(0.0365, 0.005), (0.0365, 0.0)], plain=True, seg=10),
          lathe(S, "y", 0.021, -0.010, [(0.017, 0.004), (0.021, 0.004), (0.021, 0.0)], plain=True, seg=8),
          lathe(S, "x", 0.004, -0.010, [(0.036, 0.004), (0.040, 0.004), (0.040, 0.0)], plain=True, seg=8)]
    return gun(p, [], {})


def angled_grip():
    """An angled fore-grip: a wedge under the rail that the support hand pulls back against."""
    PL = POLYMER
    p = [B(PL, -0.013, -0.012, -0.050, 0.026, 0.012, 0.100, plain=True)]
    p += slab(PL, 0.028, [(-0.050, -0.010), (0.050, -0.010), (0.046, -0.022), (-0.010, -0.050), (-0.044, -0.054), (-0.052, -0.040)], edge=0.006, r=0.008)
    for i in range(4):
        p += P(PL, 0.0135, -0.040 + i * 0.006, -0.040 + i * 0.014, 0.0012, 0.003, 0.020, plain=True)
    return gun(p, [], dict(hand=(0, -0.040, -0.010)))


def bipod():
    """A clip-on bipod: a rail clamp with two sprung legs folded forward along the barrel, rubber feet."""
    S, K = GUN_STEEL, GUN_BLACK
    p = [B(K, -0.016, -0.014, -0.020, 0.032, 0.014, 0.040, plain=True), lathe(S, "x", -0.008, 0.0, [(-0.022, 0.005), (0.022, 0.005)], plain=True, seg=8)]
    for sgn in (-1, 1):
        p += [bar(S, (sgn * 0.016, -0.016, 0.004), (sgn * 0.024, -0.030, 0.200), 0.011), bar(K, (sgn * 0.024, -0.030, 0.200), (sgn * 0.025, -0.031, 0.228), 0.015)]
    return gun(p, [], dict(hand=(0, -0.030, -0.03)))


def suppressor():
    """A rifle sound suppressor: a knurled steel can that screws over the muzzle."""
    K = GUN_BLACK
    p = [turned(K, [(0.0, 0.026), (0.006, 0.040), (0.160, 0.040), (0.170, 0.034), (0.170, 0.0)]), bore(0.170, 0.011)]
    for z in (0.012, 0.150):
        p += [turned(GUN_STEEL, [(z, 0.0412), (z + 0.006, 0.0412)], plain=True, open=True)]
    for a in range(0, 360, 60):
        p += [B(GUN_STEEL, -0.003, 0.0198, 0.030, 0.006, 0.0008, 0.100, roll=a, pivot=(0, 0, 0.08), plain=True)]
    return gun(p, [], dict(muzzle=(0, 0, 0.170)))


def pistol_suppressor():
    K = GUN_BLACK
    p = [turned(K, [(0.0, 0.020), (0.004, 0.034), (0.150, 0.034), (0.156, 0.030), (0.156, 0.0)]), bore(0.156, 0.0095),
         turned(GUN_STEEL, [(0.008, 0.0352), (0.016, 0.0352)], plain=True, open=True)]
    return gun(p, [], dict(muzzle=(0, 0, 0.156)))


def foregrip():
    """A vertical foregrip that clamps under the handguard."""
    PL = POLYMER
    p = [B(PL, -0.013, -0.014, -0.022, 0.026, 0.014, 0.044, plain=True),
         lathe(GUN_STEEL, "x", -0.007, 0.0, [(-0.019, 0.005), (-0.013, 0.005)], plain=True, seg=6),
         lathe(PL, "y", 0.0, 0.0, [(-0.014, 0.0175), (-0.030, 0.0155), (-0.060, 0.0165), (-0.092, 0.0185), (-0.098, 0.016), (-0.098, 0.0)], seg=10)]
    for y in (-0.040, -0.052, -0.064, -0.076):
        p += [lathe(PL, "y", 0.0, 0.0, [(y, 0.0182), (y - 0.004, 0.0182)], plain=True, open=True, seg=10)]
    return gun(p, [], dict(hand=(0, -0.055, 0.0)))


# ---------------------------------------------------------------- loose ammunition, as it lies in the hand
TIP_RED, TIP_GREEN, TIP_BLUE = _mat("tip_red", (214, 60, 40), "polymer"), _mat("tip_green", (70, 170, 80), "polymer"), _mat("tip_blue", (60, 110, 210), "polymer")
ROUND_SPECS = {"ammo_545": (CASE_STEEL, 0.0396, 0.010, 0.0062, 0.017, 3, TIP_GREEN), "ammo_556": (CASE_BRASS, 0.0447, 0.0095, 0.0063, 0.012, 3, TIP_RED),
               "ammo_762": (CASE_STEEL, 0.0537, 0.0124, 0.0085, 0.022, 3, TIP_GREEN), "ammo_9mm": (CASE_BRASS, 0.019, 0.0098, 0.0096, 0.010, 3, TIP_RED),
               "ammo_50": (CASE_BRASS, 0.099, 0.0204, 0.0142, 0.036, 2, TIP_RED), "ammo_76251": (CASE_BRASS, 0.0512, 0.0119, 0.0087, 0.020, 3, TIP_RED),
               "ammo_76239": (CASE_STEEL, 0.0388, 0.0113, 0.0079, 0.019, 3, TIP_GREEN), "ammo_58": (CASE_STEEL, 0.0421, 0.0100, 0.0060, 0.016, 3, TIP_GREEN),
               "ammo_338": (CASE_BRASS, 0.0693, 0.0147, 0.0086, 0.040, 2, TIP_RED), "ammo_57": (CASE_BRASS, 0.0289, 0.0079, 0.0060, 0.013, 3, TIP_RED),
               "ammo_45": (CASE_BRASS, 0.0230, 0.0120, 0.0117, 0.009, 3, TIP_RED)}


def _rounds(case, case_len, case_dia, neck, bullet_len, n=3, tip=BULLET):
    p = []
    for i in range(n):
        p += cartridge(case, -(case_len + bullet_len) / 2, 0.0, case_len, case_dia, neck, bullet_len, x=(i - (n - 1) / 2) * case_dia * 1.25, tip=tip)
    return gun(p, [], {})


def ammo_545(): return _rounds(CASE_STEEL, 0.0396, 0.010, 0.0062, 0.017)
def ammo_556(): return _rounds(CASE_BRASS, 0.0447, 0.0095, 0.0063, 0.012)
def ammo_762(): return _rounds(CASE_STEEL, 0.0537, 0.0124, 0.0085, 0.022)
def ammo_9mm(): return _rounds(CASE_BRASS, 0.019, 0.0098, 0.0096, 0.010)


def ammo_76251(): return _rounds(CASE_BRASS, 0.0512, 0.0119, 0.0087, 0.020)
def ammo_76239(): return _rounds(CASE_STEEL, 0.0388, 0.0113, 0.0079, 0.019)
def ammo_58(): return _rounds(CASE_STEEL, 0.0421, 0.0100, 0.0060, 0.016)
def ammo_338(): return _rounds(CASE_BRASS, 0.0693, 0.0147, 0.0086, 0.040, n=2)
def ammo_57(): return _rounds(CASE_BRASS, 0.0289, 0.0079, 0.0060, 0.013)
def ammo_45(): return _rounds(CASE_BRASS, 0.0230, 0.0120, 0.0117, 0.009)
def ammo_50(): return _rounds(CASE_BRASS, 0.099, 0.0204, 0.0142, 0.036, n=2)


def ammo_12g():
    p = []
    for i in range(3):
        p += shotshell(-0.035, 0.0, (i - 1) * 0.026)
    return gun(p, [], {})


#          model               builder          texels per metre
MODELS = {"ak74m": (ak74m, 1600), "akm": (akm, 1600), "ak12": (ak12, 1600),
          "aug": (aug, 1500), "tavor": (tavor, 1500), "qbz95": (qbz95, 1500), "l85": (l85, 1500), "f2000": (f2000, 1500),
          "famas": (famas, 1500), "bren2": (bren2, 1500), "qbz191": (qbz191, 1500),
          "m14": (m14, 1500), "bm59": (bm59, 1500), "fal": (fal, 1500), "g3": (g3, 1500), "hk417": (hk417, 1500), "sg542": (sg542, 1500),
          "psl": (psl, 1300), "g28": (g28, 1500), "m39emr": (m39emr, 1500), "mk12": (mk12, 1500), "qbu88": (qbu88, 1500),
          "awm": (awm, 1400), "m24": (m24, 1400), "m40": (m40, 1400), "sv98": (sv98, 1400), "trg42": (trg42, 1400),
          "m320": (m320, 1600), "gl_he": (ammo_gl_he, 3000), "gl_smoke": (ammo_gl_smoke, 3000),
          "m4a1": (m4a1, 1600), "svd": (svd, 1300), "pkm": (pkm, 1300), "m17": (m17, 3200), "m590": (m590, 1400), "m107": (m107, 1100), "rpg7": (rpg7, 1200),
          "rocket_pg7v": (rocket_pg7v, 1500), "rocket_og7v": (rocket_og7v, 1500), "scope_10x": (scope_10x, 2000), "ammo_50": (ammo_50, 2600),
          "m18": (m18, 3200), "m9": (m9, 3200),
          "glock17": (glock17, 2600), "glock19": (glock19, 2600), "p99": (p99, 2600), "hk_usp": (hk_usp, 2600), "five_seven": (five_seven, 2600),
          "m1911a1": (m1911a1, 2600), "hi_power": (hi_power, 2600), "cz75": (cz75, 2600), "p226": (p226, 2600), "p228": (p228, 2600), "hk416": (hk416, 1500), "g36": (g36, 1500), "scarh": (scarh, 1500), "m249": (m249, 1200),
          "m240": (m240, 1100), "m110": (m110, 1300), "ammo_76251": (ammo_76251, 3500), "ammo_76239": (ammo_76239, 3800), "ammo_58": (ammo_58, 4000), "ammo_338": (ammo_338, 2800),
          "ammo_57": (ammo_57, 5000), "ammo_45": (ammo_45, 4000),
          "holo": (holo, 2600), "lpvo": (lpvo, 2000), "magnifier": (magnifier, 2600), "nv_scope": (nv_scope, 1800), "thermal_scope": (thermal_scope, 1800),
          "flashlight": (flashlight, 2600), "laser": (laser, 3000), "angled_grip": (angled_grip, 2400), "bipod": (bipod, 2000),
          "red_dot": (red_dot, 3000), "acog": (acog, 2400), "pso1": (pso1, 2000), "suppressor": (suppressor, 2400),
          "pistol_suppressor": (pistol_suppressor, 2400), "foregrip": (foregrip, 2400),
          "ammo_545": (ammo_545, 4000), "ammo_556": (ammo_556, 4000), "ammo_762": (ammo_762, 3500), "ammo_9mm": (ammo_9mm, 5000),
          "ammo_12g": (ammo_12g, 3000)}


for _id, (_case, _cl, _cd, _nk, _bl, _n, _tracer) in ROUND_SPECS.items():
    _px = MODELS[_id][1]
    MODELS[_id + "_tracer"] = ((lambda c=_case, a=_cl, b=_cd, d=_nk, e=_bl, n=_n, t=_tracer: _rounds(c, a, b, d, e, n, t)), _px)
    MODELS[_id + "_training"] = ((lambda c=_case, a=_cl, b=_cd, d=_nk, e=_bl, n=_n: _rounds(c, a, b, d, e, n, TIP_BLUE)), _px)


def preview(names, out):
    from PIL import Image
    for name in names:
        fn, px = MODELS[name]
        model = fn()
        tex, w, h = bake(model, name, px)
        views = [render(model, tex, math.radians(a), math.radians(b), 1000) for a, b in ((90, 4), (-90, 4), (50, 22), (-140, 18))]
        sheet = Image.new("RGB", (2000, 2000))
        for i, v in enumerate(views):
            sheet.paste(v, ((i % 2) * 1000, (i // 2) * 1000))
        sheet.save(f"{out}/{name}.png")
        parts = model["base"] + [c for b in model["bones"] for c in b["cubes"]]
        print(name, len(parts), "parts", sum(len(sf["faces"]) for p in parts for sf in p["surfaces"]), "faces", w, h)


if __name__ == "__main__":
    import sys
    preview(sys.argv[2:] or list(MODELS), sys.argv[1])
