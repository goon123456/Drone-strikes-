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
def ak74m():
    """AK-74M, 5.45x39: pressed-steel receiver under a rounded, ribbed top cover, black polymer furniture with a
    swelling handguard, side-folding stock, the long two-chamber muzzle brake and a plum 30-round magazine.
    943 mm long."""
    S, K, PL = GUN_STEEL, GUN_BLACK, POLYMER
    HW = 0.0165                                                                     # half the receiver's width
    # receiver: a rounded box, cut away up to the rear and stepped down behind the magazine well
    p = [body(K, [(-0.108, -0.006, 0.030, 0.044), (-0.098, -0.010, 0.033, 0.056), (-0.060, -0.0135, 0.033, 0.067), (0.136, -0.0135, 0.033, 0.067),
                  (0.150, -0.008, 0.033, 0.056), (0.156, -0.004, 0.030, 0.046)], power=7)]
    # top cover: half round, with its pressed ribs, closed at the back by the recoil-spring guide
    p += [body(K, [(-0.104, 0.022, 0.026, 0.022), (-0.096, 0.021, 0.0335, 0.040), (0.112, 0.021, 0.0335, 0.044), (0.120, 0.021, 0.030, 0.038)], power=2.6)]
    for z in (-0.078, -0.046, -0.014, 0.018, 0.050, 0.082):
        p += [body(K, [(z, 0.021, 0.0335, 0.044), (z + 0.002, 0.021, 0.0347, 0.0456), (z + 0.006, 0.021, 0.0347, 0.0456), (z + 0.008, 0.021, 0.0335, 0.044)], power=2.6)]
    p += [turned(S, [(-0.1050, 0.0), (-0.1050, 0.007), (-0.102, 0.008)], y=0.026)]
    # rear sight block over the chamber, the leaf with its slider, and the notch
    p += slab(S, 0.027, [(0.112, 0.016), (0.232, 0.016), (0.232, 0.030), (0.206, 0.0425), (0.148, 0.0425), (0.112, 0.036)], edge=0.004)
    p += [B(S, -0.0055, 0.0425, 0.130, 0.011, 0.0035, 0.085, pitch=-2, pivot=(0, 0.0425, 0.212), plain=True),
          B(S, -0.008, 0.0415, 0.168, 0.016, 0.006, 0.010, plain=True), turned(S, [(0.204, 0.009), (0.216, 0.009)], y=0.0425),
          B(S, -0.0065, 0.0455, 0.128, 0.005, 0.0065, 0.004, plain=True), B(S, 0.0015, 0.0455, 0.128, 0.005, 0.0065, 0.004, plain=True)]
    # barrel stepping down in three diameters, gas tube and gas block, front sight tower with its ears and post
    p += [turned(S, [(0.150, 0.024), (0.168, 0.024), (0.168, 0.0175), (0.395, 0.0175), (0.395, 0.0155), (0.526, 0.0155)], seg=12),
          turned(S, [(0.228, 0.018), (0.240, 0.020), (0.380, 0.017), (0.394, 0.019)], y=0.030, seg=10)]
    p += slab(S, 0.023, [(0.390, -0.013), (0.428, -0.013), (0.428, 0.010), (0.414, 0.042), (0.390, 0.042)], edge=0.004)
    p += [B(S, -0.004, -0.022, 0.398, 0.008, 0.010, 0.016, plain=True), turned(S, [(0.428, 0.020), (0.434, 0.0155)], seg=10)]
    p += slab(S, 0.021, [(0.488, -0.0125), (0.524, -0.0125), (0.524, 0.012), (0.517, 0.030), (0.497, 0.030), (0.488, 0.014)], edge=0.0035)
    p += [B(S, 0.0072, 0.029, 0.501, 0.0018, 0.027, 0.014, roll=-8, pivot=(0.008, 0.030, 0.508), plain=True),
          B(S, -0.0090, 0.029, 0.501, 0.0018, 0.027, 0.014, roll=8, pivot=(-0.008, 0.030, 0.508), plain=True),
          turned(S, [(0.5068, 0.0), (0.5068, 0.004), (0.5092, 0.004), (0.5092, 0.0)], y=0.0), bar(S, (0, 0.028, 0.508), (0, 0.052, 0.508), 0.0026)]
    p += [bar(S, (0, -0.0185, 0.360), (0, -0.0185, 0.540), 0.006), turned(S, [(0.540, 0.006), (0.546, 0.008), (0.549, 0.006)], y=-0.0185, seg=6)]
    # handguards: a lower one that swells for the palm, ribbed along its sides, and a half-round cover on the gas
    # tube with its cooling slots; steel ferrules hold both
    p += [body(PL, [(0.163, -0.012, 0.040, 0.054), (0.172, -0.013, 0.044, 0.058), (0.250, -0.014, 0.049, 0.060), (0.330, -0.013, 0.046, 0.058),
                    (0.356, -0.012, 0.043, 0.054), (0.362, -0.012, 0.040, 0.050)], power=3.4)]
    for i in range(4):
        p += P(PL, 0.0222 + (0.0012 if i in (1, 2) else 0.0), -0.004 - i * 0.008, 0.190, 0.0016, 0.0032, 0.140, plain=True)
    p += [body(PL, [(0.232, 0.031, 0.038, 0.030), (0.238, 0.031, 0.043, 0.034), (0.340, 0.031, 0.043, 0.034), (0.347, 0.031, 0.038, 0.030)], power=2.4)]
    for z in (0.254, 0.284, 0.314):
        p += P(BLACK, 0.0206, 0.031, z, 0.0012, 0.006, 0.016, plain=True)
    p += [body(S, [(0.359, -0.010, 0.046, 0.060), (0.371, -0.010, 0.046, 0.060)], power=3.4), body(S, [(0.154, -0.008, 0.043, 0.054), (0.165, -0.008, 0.043, 0.054)], power=4)]
    # pistol grip, swelling towards its base; trigger guard as one bent strip from the grip to the magazine catch
    p += sweep(PL, [(-0.030, -0.040), (-0.036, -0.062), (-0.050, -0.100), (-0.066, -0.140)], [0.026, 0.027, 0.029, 0.031], [0.036, 0.038, 0.041, 0.044], power=3.2)
    p += sweep(S, [(-0.022, -0.052), (-0.012, -0.079), (0.060, -0.081), (0.076, -0.074), (0.078, -0.044)], 0.015, 0.0026, power=6, seg=8, ends="flat")
    p += slab(S, 0.010, [(0.070, -0.045), (0.078, -0.045), (0.077, -0.090), (0.068, -0.094)], edge=0.002, r=0.002)
    # optic rail on the left wall, rivets and pins, sling loops, markings
    p += slab(S, 0.0048, [(-0.070, -0.022), (0.062, -0.022), (0.066, -0.011), (0.062, 0.002), (-0.070, 0.002)], x=HW + 0.0022, edge=0.0015, r=0.002)
    p += [B(S, HW + 0.0046, -0.018, -0.062, 0.002, 0.016, 0.118, plain=True)]
    for y, z in ((0.008, 0.128), (-0.012, 0.128), (-0.002, 0.143), (0.004, -0.092), (-0.016, -0.092), (-0.012, 0.004), (-0.020, -0.022), (-0.004, -0.030)):
        p += pin(HW, y, z)
    p += [B(BLACK, -HW - 0.0004, -0.036, 0.100, 2 * HW + 0.0008, 0.006, 0.020, plain=True)]                # magazine dimple
    p += [bar(S, (0.011, 0.014, 0.402), (0.019, 0.003, 0.402), 0.0035), bar(S, (0.011, -0.006, 0.402), (0.019, 0.003, 0.402), 0.0035)]
    p += [label(K, "74M 0719", HW + 0.0002, -0.034, 0.012, 0.045, 0.0042), label(K, "AB", -HW - 0.0002, -0.014, 0.040, 0.014, 0.0045, "right"),
          label(K, "OD", -HW - 0.0002, -0.028, 0.040, 0.014, 0.0045, "right")]
    # folding stock: hinge block, a polymer butt with rolled edges and its long lightening groove, steel butt plate
    p += [body(S, [(-0.122, -0.008, 0.034, 0.052), (-0.106, -0.008, 0.034, 0.052)], power=5)]
    p += slab(PL, 0.031, [(-0.120, 0.017), (-0.342, 0.003), (-0.345, -0.104), (-0.330, -0.110), (-0.170, -0.046), (-0.120, -0.033)], edge=0.007, r=0.010,
              marks=[("cap0", "slot", 0.60, 0.52, 0.014, 0.105), ("cap1", "slot", 0.60, 0.52, 0.014, 0.105)])
    p += slab(S, 0.033, [(-0.343, 0.005), (-0.351, 0.003), (-0.351, -0.108), (-0.343, -0.110)], edge=0.004, r=0.003)
    p += [bar(S, (0.0155, -0.030, -0.300), (0.022, -0.044, -0.300), 0.0035)]

    brake = [turned(S, [(0.524, 0.016), (0.532, 0.0245), (0.552, 0.0245), (0.556, 0.0275), (0.606, 0.0275), (0.611, 0.024)], seg=12), bore(0.611, 0.012),
             B(BLACK, -0.0142, -0.0055, 0.584, 0.0284, 0.011, 0.015, plain=True), B(BLACK, -0.003, 0.0120, 0.540, 0.006, 0.0008, 0.006, plain=True),
             B(BLACK, -0.003, 0.0120, 0.540, 0.006, 0.0008, 0.006, roll=45, pivot=(0, 0, 0.543), plain=True),
             B(BLACK, -0.003, 0.0120, 0.540, 0.006, 0.0008, 0.006, roll=-45, pivot=(0, 0, 0.543), plain=True)]
    # the bolt carrier showing in the ejection port, with its charging handle
    carrier = slab(GUN_BARE, 0.008, [(0.030, 0.004), (0.118, 0.004), (0.118, 0.030), (0.030, 0.030)], x=-0.0148, edge=0.002, r=0.003)
    carrier += [bar(GUN_BARE, (-0.016, 0.016, 0.106), (-0.038, 0.016, 0.106), 0.011), turned(GUN_BARE, [(0.094, 0.0), (0.098, 0.012), (0.110, 0.013), (0.113, 0.0)], y=0.0155, x=-0.042, seg=8)]
    trigger = sweep(S, [(0.014, -0.046), (0.016, -0.058), (0.019, -0.068), (0.016, -0.075)], 0.006, 0.006, power=3, seg=8)
    selector = slab(S, 0.0016, [(-0.050, 0.014), (0.072, 0.010), (0.076, 0.003), (0.072, -0.003), (-0.032, -0.005), (-0.050, -0.003)], x=-HW - 0.0010, edge=0.0005, r=0.003)
    selector += [B(S, -HW - 0.0075, -0.002, 0.066, 0.006, 0.004, 0.010, plain=True), lathe(S, "x", 0.004, -0.040, [(-HW - 0.003, 0.0045), (-HW, 0.0045)], plain=True, seg=8)]
    # magazine: a curved polymer box with a stiffening spine and ribs, a round showing at the lips
    path = arc_path(0.108, -0.040, 0.205, 0.300, 9)
    mag = sweep(POLYMER_PLUM, path, 0.0265, 0.064, power=7, ends="flat")
    mag += sweep(POLYMER_PLUM, path[1:], 0.0085, 0.068, power=4, ends="flat")
    for k in (3, 5, 7):
        mag += sweep(POLYMER_PLUM, [path[k], ((path[k][0] + path[k + 1][0]) / 2, (path[k][1] + path[k + 1][1]) / 2)], 0.0285, 0.040, power=6, seg=10, ends="flat")
    mag += sweep(POLYMER_PLUM, [path[-1], (path[-1][0] + (path[-1][0] - path[-2][0]) * 0.3, path[-1][1] + (path[-1][1] - path[-2][1]) * 0.3)], 0.031, 0.070, power=6, ends="flat")
    mag += cartridge(CASE_STEEL, 0.080, -0.037, 0.0396, 0.010, 0.0062, 0.017)
    mag += [B(S, -0.004, -0.050, 0.137, 0.008, 0.006, 0.006, plain=True)]                                  # front lug
    return gun(p, [bone("muzzle", (0, 0, 0.524), brake), bone("bolt", (0, 0.016, 0.07), carrier), bone("trigger", (0, -0.047, 0.014), trigger),
                   bone("selector", (-HW, 0.004, -0.040), selector), bone("mag", (0, -0.047, 0.138), mag)],
               dict(muzzle=(0, 0, 0.611), thread=(0, 0, 0.524), eject=(-0.02, 0.02, 0.075), sight=(0, 0.052, 0.128), front=(0, 0.052, 0.508),
                    optic=(0, 0.0445, -0.005), under=(0, -0.043, 0.265), grip=(0, -0.095, -0.050), hand=(0, -0.044, 0.265), mag=(0, -0.047, 0.108),
                    charge=(-0.042, 0.016, 0.104)))


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
def svd():
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
    p += [label(K, "SVD 7.62", HW + 0.0002, -0.034, 0.086, 0.045, 0.0042)]
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
def m17():
    """M17 service pistol (SIG P320), 9x19: coyote slide and polymer frame, light rail, thumb safety, night
    sights and a 17-round magazine. 203 mm long."""
    T, TS, S = POLYMER_TAN, _mat("gun_tan", (146, 124, 92), "gunmetal", wear=(96, 90, 82)), GUN_STEEL
    # frame: dust cover with its rail, the grip swelling to the palm, beavertail, trigger guard
    p = [body(T, [(-0.074, -0.020, 0.024, 0.014), (-0.060, -0.022, 0.0285, 0.020), (0.088, -0.022, 0.0285, 0.020), (0.094, -0.021, 0.026, 0.016)], power=5)]
    p += sweep(T, [(-0.034, -0.026), (-0.040, -0.050), (-0.050, -0.090), (-0.060, -0.124)], [0.0295, 0.030, 0.031, 0.0315], [0.050, 0.050, 0.052, 0.054], power=3.6)
    p += sweep(T, [(-0.060, -0.022), (-0.078, -0.018), (-0.086, -0.016)], [0.024, 0.022, 0.018], [0.012, 0.010, 0.007], power=3, seg=10)
    p += sweep(T, [(0.048, -0.028), (0.047, -0.060), (0.038, -0.072), (0.004, -0.073), (-0.016, -0.062)], 0.014, 0.0055, power=4, seg=8, ends="flat")
    p += rail(T, 0.048, 0.090, -0.031, w=0.020, up=-1)
    for sx in (1, -1):                                                                                  # stippled grip panels
        p += [B(T, sx * 0.0152 - 0.0003, -0.118, -0.068, 0.0006, 0.074, 0.030, pitch=14, pivot=(0, -0.08, -0.05), plain=True,
                marks=[("left" if sx > 0 else "right", "checker", 0.5, 0.5, 0.030, 0.074)])]
    p += [B(S, 0.0143, -0.022, 0.010, 0.0016, 0.005, 0.022, plain=True), B(S, 0.0143, -0.024, 0.040, 0.0016, 0.008, 0.012, plain=True),
          B(S, -0.0159, -0.024, 0.040, 0.0016, 0.008, 0.012, plain=True)]
    p += [label(T, "M17", 0.01435, -0.022, 0.062, 0.020, 0.0038)]
    # barrel showing at the muzzle, recoil spring guide under it
    p += [tube(S, 0.060, 0.1005, 0.0142, seg=10), bore(0.1005, 0.009), tube(S, 0.070, 0.0995, 0.008, y=-0.0135)]

    slide = [body(TS, [(-0.104, 0.002, 0.024, 0.028), (-0.100, 0.0035, 0.026, 0.033), (0.090, 0.0035, 0.026, 0.033), (0.099, 0.002, 0.024, 0.028)], power=6)]
    for sx in (1, -1):                                                                                  # cocking serrations, back and front
        for z, ln in ((-0.094, 0.028), (0.058, 0.024)):
            slide += [B(TS, sx * 0.0128 - 0.0003, -0.008, z, 0.0006, 0.022, ln, plain=True,
                        marks=[("left" if sx > 0 else "right", "grooves", 0.5, 0.5, ln, 0.022)])]
    slide += [B(BLACK, -0.0134, 0.004, 0.002, 0.0142, 0.0164, 0.034, plain=True),                               # ejection port
              B(GUN_BARE, -0.0128, 0.006, 0.004, 0.012, 0.012, 0.030, plain=True),                             # barrel hood
              B(S, -0.0016, 0.019, 0.084, 0.0032, 0.007, 0.006, plain=True, marks=[("rear", "dot", 0.5, 0.4, 0.002, (150, 230, 120))]),
              B(S, -0.009, 0.019, -0.100, 0.0065, 0.007, 0.008, plain=True, marks=[("rear", "dot", 0.5, 0.4, 0.002, (150, 230, 120))]),
              B(S, 0.0025, 0.019, -0.100, 0.0065, 0.007, 0.008, plain=True, marks=[("rear", "dot", 0.5, 0.4, 0.002, (150, 230, 120))]),
              B(S, -0.009, 0.0175, -0.100, 0.018, 0.0025, 0.008, plain=True),
              B(TS, -0.010, 0.0195, -0.080, 0.020, 0.0012, 0.040, plain=True),                                 # optic cover plate
              label(TS, "SIG SAUER", 0.01305, 0.006, 0.010, 0.040, 0.0034)]
    trigger = sweep(S, [(0.016, -0.032), (0.017, -0.046), (0.020, -0.056), (0.017, -0.064)], 0.007, 0.006, power=3, seg=8)
    selector = [B(S, 0.0143, -0.019, -0.062, 0.0035, 0.005, 0.024, plain=True), B(S, -0.0178, -0.019, -0.062, 0.0035, 0.005, 0.024, plain=True)]
    mag = sweep(GUN_STEEL, [(-0.037, -0.016), (-0.049, -0.066), (-0.061, -0.120)], 0.021, 0.040, power=6, ends="flat")
    mag += sweep(POLYMER_TAN, [(-0.061, -0.121), (-0.064, -0.134)], 0.030, 0.054, power=4, ends="flat")
    mag += cartridge(CASE_BRASS, -0.052, -0.013, 0.019, 0.0098, 0.0096, 0.010)
    for i in range(4):
        mag += [lathe(BLACK, "x", -0.045 - i * 0.018, -0.040 - i * 0.0045, [(-0.0108, 0.002), (0.0108, 0.002)], plain=True, seg=6)]
    return gun(p, [bone("bolt", (0, 0.0, 0.0), slide), bone("trigger", (0, -0.030, 0.016), trigger), bone("selector", (0, -0.0165, -0.040), selector),
                   bone("mag", (0, -0.070, -0.050), mag)],
               dict(muzzle=(0, 0, 0.1005), thread=(0, 0, 0.1005), eject=(-0.014, 0.012, 0.02), sight=(0, 0.026, -0.100), front=(0, 0.026, 0.087),
                    optic=(0, 0.0212, -0.060), under=(0, -0.034, 0.07), grip=(0, -0.078, -0.047), hand=(0.012, -0.088, -0.040), mag=(0, -0.070, -0.050),
                    charge=(0.0, 0.016, -0.085)))


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
def _rounds(case, case_len, case_dia, neck, bullet_len, n=3):
    p = []
    for i in range(n):
        p += cartridge(case, -(case_len + bullet_len) / 2, 0.0, case_len, case_dia, neck, bullet_len, x=(i - (n - 1) / 2) * case_dia * 1.25)
    return gun(p, [], {})


def ammo_545(): return _rounds(CASE_STEEL, 0.0396, 0.010, 0.0062, 0.017)
def ammo_556(): return _rounds(CASE_BRASS, 0.0447, 0.0095, 0.0063, 0.012)
def ammo_762(): return _rounds(CASE_STEEL, 0.0537, 0.0124, 0.0085, 0.022)
def ammo_9mm(): return _rounds(CASE_BRASS, 0.019, 0.0098, 0.0096, 0.010)


def ammo_12g():
    p = []
    for i in range(3):
        p += shotshell(-0.035, 0.0, (i - 1) * 0.026)
    return gun(p, [], {})


#          model               builder          texels per metre
MODELS = {"ak74m": (ak74m, 1600), "m4a1": (m4a1, 1600), "svd": (svd, 1300), "pkm": (pkm, 1300), "m17": (m17, 3200), "m590": (m590, 1400),
          "red_dot": (red_dot, 3000), "acog": (acog, 2400), "pso1": (pso1, 2000), "suppressor": (suppressor, 2400),
          "pistol_suppressor": (pistol_suppressor, 2400), "foregrip": (foregrip, 2400),
          "ammo_545": (ammo_545, 4000), "ammo_556": (ammo_556, 4000), "ammo_762": (ammo_762, 3500), "ammo_9mm": (ammo_9mm, 5000),
          "ammo_12g": (ammo_12g, 3000)}


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
