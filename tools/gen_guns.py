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


# ---------------------------------------------------------------- AK-74M
def ak74m():
    """AK-74M, 5.45x39: stamped receiver with a ribbed top cover, black polymer furniture, side-folding stock,
    the long two-chamber muzzle brake and a plum 30-round magazine. 943 mm long."""
    S, K, PL = GUN_STEEL, GUN_BLACK, POLYMER
    HW = 0.0165                                                                     # half the receiver's width
    p = [side(K, HW * 2, [(-0.105, 0.018), (0.150, 0.018), (0.150, -0.020), (0.138, -0.047), (-0.060, -0.047), (-0.105, -0.030)])]
    # top cover, rounded over, with its stamped ribs and the recoil-spring guide button at the back
    p += [section(K, -0.100, 0.118, [(-HW, 0.018), (HW, 0.018), (HW, 0.034), (0.0105, 0.043), (-0.0105, 0.043), (-HW, 0.034)])]
    for z in (-0.08, -0.05, -0.02, 0.01, 0.04, 0.07):
        p += [B(K, -0.011, 0.0428, z, 0.022, 0.0012, 0.007, plain=True)]
    p += [B(S, -0.005, 0.024, -0.1035, 0.010, 0.010, 0.004, plain=True)]
    # rear sight block over the chamber, the leaf with its slider, and the notch
    p += [side(S, 0.026, [(0.118, 0.018), (0.228, 0.018), (0.228, 0.03), (0.205, 0.042), (0.15, 0.042), (0.118, 0.034)])]
    p += [B(S, -0.0055, 0.0425, 0.130, 0.011, 0.0035, 0.085, pitch=-2, pivot=(0, 0.0425, 0.212), plain=True),
          B(S, -0.008, 0.0415, 0.168, 0.016, 0.006, 0.010, plain=True),
          B(S, -0.0065, 0.0455, 0.128, 0.005, 0.0065, 0.004, plain=True), B(S, 0.0015, 0.0455, 0.128, 0.005, 0.0065, 0.004, plain=True)]
    # barrel, gas tube and gas block, front sight tower with its ears and post, cleaning rod
    p += [tube(S, 0.150, 0.530, 0.0155), tube(S, 0.228, 0.392, 0.017, y=0.030)]
    p += [side(S, 0.022, [(0.392, -0.012), (0.428, -0.012), (0.428, 0.012), (0.412, 0.041), (0.392, 0.041)]),
          B(S, -0.004, -0.021, 0.398, 0.008, 0.010, 0.016, plain=True)]                                   # bayonet lug
    p += [side(S, 0.020, [(0.490, -0.012), (0.524, -0.012), (0.524, 0.012), (0.517, 0.030), (0.498, 0.030), (0.490, 0.014)])]
    p += [B(S, 0.0072, 0.030, 0.501, 0.0016, 0.026, 0.014, roll=-7, pivot=(0.008, 0.030, 0.508), plain=True),
          B(S, -0.0088, 0.030, 0.501, 0.0016, 0.026, 0.014, roll=7, pivot=(-0.008, 0.030, 0.508), plain=True),
          B(S, -0.0011, 0.030, 0.507, 0.0022, 0.022, 0.0022, plain=True)]
    p += [bar(S, (0, -0.0185, 0.365), (0, -0.0185, 0.540), 0.006)]
    # handguards: ribbed polymer below, a half-round cover over the gas tube, steel ferrules at each end
    p += [section(PL, 0.165, 0.360, [(-0.019, 0.015), (0.019, 0.015), (0.0235, -0.008), (0.019, -0.041), (-0.019, -0.041), (-0.0235, -0.008)])]
    for i in range(4):
        p += P(PL, 0.0228 - i * 0.0008, -0.006 - i * 0.008, 0.185, 0.0016, 0.0035, 0.150, plain=True)
    p += [section(PL, 0.234, 0.346, [(-0.020, 0.017), (0.020, 0.017), (0.022, 0.032), (0.014, 0.047), (-0.014, 0.047), (-0.022, 0.032)])]
    for z in (0.252, 0.282, 0.312):
        p += P(BLACK, 0.0213, 0.030, z, 0.0012, 0.006, 0.016, plain=True)                                # cooling slots
    p += [B(S, -0.022, -0.040, 0.360, 0.044, 0.058, 0.010, plain=True), B(S, -0.021, -0.030, 0.156, 0.042, 0.047, 0.009, plain=True)]
    # pistol grip, trigger guard and magazine catch
    p += [side(PL, 0.029, [(-0.010, -0.047), (-0.052, -0.047), (-0.090, -0.140), (-0.052, -0.150), (-0.030, -0.088)],
               marks=[("cap0", "checker", 0.45, 0.5, 0.06, 0.03), ("cap1", "checker", 0.45, 0.5, 0.06, 0.03)])]
    p += [B(S, -0.008, -0.080, -0.012, 0.016, 0.0016, 0.090, plain=True), B(S, -0.008, -0.080, 0.0765, 0.016, 0.034, 0.0016, plain=True),
          side(S, 0.010, [(0.070, -0.047), (0.076, -0.047), (0.075, -0.088), (0.066, -0.092)])]
    # optic rail on the left wall, rivets and pins, sling loops, markings
    p += [B(S, HW, -0.020, -0.068, 0.0045, 0.022, 0.130, plain=True), B(S, HW + 0.0045, -0.018, -0.060, 0.002, 0.018, 0.114, plain=True)]
    for y, z in ((0.008, 0.128), (-0.012, 0.128), (-0.002, 0.143), (0.004, -0.092), (-0.016, -0.092), (-0.012, 0.004), (-0.020, -0.022), (-0.004, -0.030)):
        p += pin(HW, y, z)
    p += [B(BLACK, -HW - 0.0005, -0.036, 0.100, 2 * HW + 0.001, 0.006, 0.020, plain=True)]                # magazine dimple
    p += [bar(S, (0.012, 0.016, 0.400), (0.020, 0.004, 0.400), 0.004), bar(S, (0.012, -0.008, 0.400), (0.020, 0.004, 0.400), 0.004)]
    p += [label(K, "74M 0719", HW, -0.006, 0.090, 0.045, 0.0045), label(K, "AB", -HW, -0.014, 0.040, 0.014, 0.0045, "right"),
          label(K, "OD", -HW, -0.028, 0.040, 0.014, 0.0045, "right")]
    # folding stock: hinge block, polymer butt with its lightening groove, steel butt plate
    p += [B(S, -0.017, -0.030, -0.117, 0.034, 0.048, 0.012, plain=True)]
    p += [side(PL, 0.030, [(-0.117, 0.018), (-0.340, 0.004), (-0.344, -0.105), (-0.330, -0.110), (-0.150, -0.042), (-0.117, -0.030)],
               marks=[("cap0", "slot", 0.62, 0.52, 0.016, 0.11), ("cap1", "slot", 0.62, 0.52, 0.016, 0.11)])]
    p += [B(S, -0.016, -0.110, -0.349, 0.032, 0.116, 0.006, plain=True), bar(S, (0.015, -0.030, -0.300), (0.022, -0.045, -0.300), 0.004)]

    brake = [turned(S, [(0.524, 0.016), (0.534, 0.024), (0.552, 0.024), (0.556, 0.027), (0.610, 0.027)]), bore(0.610, 0.012),
             B(BLACK, -0.0138, -0.006, 0.584, 0.0276, 0.012, 0.015, plain=True), B(BLACK, -0.003, 0.0118, 0.540, 0.006, 0.0006, 0.006, plain=True),
             B(BLACK, -0.0085, 0.0100, 0.540, 0.004, 0.0006, 0.006, roll=40, pivot=(0, 0, 0.543), plain=True),
             B(BLACK, 0.0045, 0.0100, 0.540, 0.004, 0.0006, 0.006, roll=-40, pivot=(0, 0, 0.543), plain=True)]
    # the bolt carrier showing in the ejection port, with its charging handle
    carrier = [B(GUN_BARE, -0.0186, 0.004, 0.030, 0.008, 0.026, 0.088, plain=True),
               bar(GUN_BARE, (-0.016, 0.016, 0.106), (-0.040, 0.016, 0.106), 0.011), bar(GUN_BARE, (-0.040, 0.016, 0.106), (-0.046, 0.014, 0.100), 0.010)]
    trigger = [side(S, 0.006, [(0.010, -0.047), (0.019, -0.047), (0.016, -0.060), (0.020, -0.073), (0.015, -0.074), (0.009, -0.060)])]
    selector = [side(S, 0.0014, [(-0.050, 0.014), (0.072, 0.010), (0.076, 0.003), (0.072, -0.003), (-0.032, -0.005), (-0.050, -0.003)], x=-HW - 0.0010),
                B(S, -HW - 0.0075, -0.002, 0.066, 0.006, 0.004, 0.010, plain=True), pin(0.0, 0.004, -0.040, 0.009, S)[0]]
    selector[2] = lathe(S, "x", 0.004, -0.040, [(-HW - 0.003, 0.0045), (-HW, 0.0045)], plain=True, seg=6)
    mag = curved_mag(POLYMER_PLUM, 0.108, -0.045, 0.064, 0.205, 0.300, 0.027)
    mag += cartridge(CASE_STEEL, 0.080, -0.040, 0.0396, 0.010, 0.0062, 0.017)
    mag += [B(S, -0.004, -0.050, 0.137, 0.008, 0.006, 0.006, plain=True)]                                  # front lug
    return gun(p, [bone("muzzle", (0, 0, 0.524), brake), bone("bolt", (0, 0.016, 0.07), carrier), bone("trigger", (0, -0.047, 0.014), trigger),
                   bone("selector", (-HW, 0.004, -0.040), selector), bone("mag", (0, -0.047, 0.138), mag)],
               dict(muzzle=(0, 0, 0.610), thread=(0, 0, 0.524), eject=(-0.02, 0.02, 0.075), sight=(0, 0.052, 0.128), front=(0, 0.052, 0.508),
                    optic=(0, 0.0445, -0.005), under=(0, -0.041, 0.265), grip=(0, -0.095, -0.055), hand=(0, -0.042, 0.270), mag=(0, -0.047, 0.108),
                    charge=(-0.046, 0.016, 0.104)))


# ---------------------------------------------------------------- M4A1
def m4a1():
    """M4A1 carbine, 5.56x45: flat-top receiver, quad-rail handguard, A-frame front sight, flip-up rear sight,
    six-position stock and a 30-round aluminium magazine. 838 mm long with the stock out."""
    A, S, PL = GUN_GREY, GUN_STEEL, POLYMER
    HW = 0.0155
    # upper receiver with its rail, forward assist, case deflector and the open dust cover
    p = [side(A, HW * 2, [(-0.105, -0.012), (0.105, -0.012), (0.105, 0.030), (-0.105, 0.030)])]
    p += rail(A, -0.100, 0.105, 0.030)
    p += [side(A, 0.012, [(-0.040, -0.006), (-0.012, 0.004), (-0.012, 0.022), (-0.040, 0.022)], x=-HW - 0.004),
          lathe(A, "z", -HW - 0.007, 0.012, [(-0.075, 0.006), (-0.040, 0.006)], plain=True, seg=8),
          lathe(S, "z", -HW - 0.007, 0.012, [(-0.090, 0.0075), (-0.075, 0.0075)], plain=True, seg=8)]
    p += [B(BLACK, -HW - 0.0004, -0.004, -0.006, 0.001, 0.020, 0.072, plain=True),
          B(A, -HW - 0.004, -0.030, -0.006, 0.003, 0.022, 0.072, plain=True)]
    # lower receiver: magazine well, trigger guard, buffer tower, bolt catch and magazine button
    p += [side(A, HW * 2, [(-0.105, -0.012), (0.105, -0.012), (0.105, -0.088), (0.036, -0.076), (0.036, -0.050), (-0.060, -0.050), (-0.105, -0.030)])]
    p += [side(A, 0.034, [(-0.132, -0.030), (-0.105, -0.030), (-0.105, 0.022), (-0.132, 0.022)])]
    p += [B(A, -0.007, -0.0795, -0.014, 0.014, 0.0025, 0.052, plain=True)]
    p += [B(S, HW, -0.012, 0.030, 0.003, 0.022, 0.012, plain=True), B(S, -HW - 0.003, -0.034, 0.030, 0.003, 0.009, 0.012, plain=True)]
    for y, z in ((-0.030, 0.018), (-0.024, -0.012), (-0.020, 0.098), (-0.040, -0.088)):
        p += pin(HW, y, z, 0.006, S)
    p += [label(A, "SAFE", HW, -0.014, -0.058, 0.02, 0.003), label(A, "SEMI", HW, -0.032, -0.036, 0.02, 0.003),
          label(A, "AUTO", HW, -0.014, -0.014, 0.02, 0.003), label(A, "M4A1 CAL 5.56MM", HW, -0.044, 0.045, 0.055, 0.0036)]
    # pistol grip
    p += [side(PL, 0.030, [(-0.014, -0.050), (-0.058, -0.050), (-0.092, -0.142), (-0.050, -0.150), (-0.036, -0.092)],
               marks=[("cap0", "grooves", 0.5, 0.5, 0.07, 0.03), ("cap1", "grooves", 0.5, 0.5, 0.07, 0.03)])]
    # barrel nut, quad-rail handguard, gas block with the front sight, barrel and flash hider
    p += [turned(S, [(0.105, 0.036), (0.128, 0.040), (0.132, 0.034)])]
    p += [section(A, 0.132, 0.300, [(-0.019, 0.022), (0.019, 0.022), (0.022, 0.016), (0.022, -0.016), (0.019, -0.022), (-0.019, -0.022), (-0.022, -0.016), (-0.022, 0.016)])]
    p += rail(A, 0.134, 0.300, 0.022) + rail(A, 0.134, 0.300, -0.022, up=-1)
    p += side_rail(A, 0.134, 0.300, 0.022, 0.0, 1) + side_rail(A, 0.134, 0.300, -0.022, 0.0, -1)
    p += [tube(S, 0.300, 0.474, 0.0185), tube(S, 0.370, 0.410, 0.0145)]
    p += [side(S, 0.018, [(0.308, -0.011), (0.345, -0.011), (0.345, 0.012), (0.338, 0.046), (0.330, 0.046), (0.308, 0.016)]),
          B(S, -0.008, 0.046, 0.328, 0.0018, 0.021, 0.012, plain=True), B(S, 0.0062, 0.046, 0.328, 0.0018, 0.021, 0.012, plain=True),
          B(S, -0.0009, 0.046, 0.3335, 0.0018, 0.020, 0.0018, plain=True), B(S, -0.006, -0.022, 0.318, 0.012, 0.012, 0.006, plain=True)]
    hider = [turned(S, [(0.452, 0.0185), (0.458, 0.022), (0.495, 0.022)]), bore(0.495, 0.011)]
    for a in (0, 72, 144, 216, 288):
        s_, c_ = math.sin(math.radians(a)), math.cos(math.radians(a))
        hider += [B(BLACK, -0.002, 0.0106, 0.468, 0.004, 0.0008, 0.020, roll=a, pivot=(0, 0, 0.478), plain=True)]
    # receiver extension and the collapsible stock
    p += [tube(A, -0.132, -0.290, 0.030), turned(S, [(-0.132, 0.036), (-0.140, 0.036)])]
    p += [side(PL, 0.038, [(-0.150, 0.020), (-0.325, 0.020), (-0.335, 0.010), (-0.335, -0.100), (-0.322, -0.104), (-0.265, -0.030), (-0.150, -0.022)],
               marks=[("cap0", "slot", 0.45, 0.82, 0.016, 0.03), ("cap1", "slot", 0.45, 0.82, 0.016, 0.03)]),
          B(RUBBER_PAD, -0.019, -0.102, -0.339, 0.038, 0.118, 0.005, plain=True),
          B(PL, -0.006, -0.036, -0.250, 0.012, 0.014, 0.060, plain=True)]

    carrier = [B(GUN_BARE, -HW - 0.0002, -0.002, -0.002, 0.004, 0.017, 0.066, plain=True)]
    charge = [B(A, -0.019, 0.022, -0.118, 0.038, 0.008, 0.014, plain=True), B(A, -0.004, 0.022, -0.108, 0.008, 0.006, 0.06, plain=True)]
    trigger = [side(S, 0.006, [(0.006, -0.050), (0.015, -0.050), (0.013, -0.062), (0.017, -0.073), (0.012, -0.074), (0.006, -0.062)])]
    selector = [lathe(S, "x", -0.024, -0.036, [(HW, 0.005), (HW + 0.003, 0.005)], plain=True, seg=8),
                B(S, HW, -0.0265, -0.060, 0.004, 0.005, 0.024, plain=True)]
    irons = [B(S, -0.010, 0.0375, -0.094, 0.020, 0.006, 0.018, plain=True), B(S, -0.008, 0.0435, -0.084, 0.0035, 0.024, 0.004, plain=True),
             B(S, 0.0045, 0.0435, -0.084, 0.0035, 0.024, 0.004, plain=True), B(S, -0.008, 0.0600, -0.084, 0.016, 0.003, 0.004, plain=True),
             B(S, -0.0045, 0.0570, -0.084, 0.003, 0.003, 0.004, plain=True), B(S, 0.0015, 0.0570, -0.084, 0.003, 0.003, 0.004, plain=True),
             B(S, -0.008, 0.0675, -0.084, 0.016, 0.0025, 0.004, plain=True)]
    mag = [side(GUN_ALU, 0.0225, [(0.040, -0.048), (0.100, -0.048), (0.100, -0.140), (0.112, -0.228), (0.050, -0.236), (0.040, -0.140)])]
    mag += [B(GUN_ALU, -0.0125, -0.240, 0.046, 0.025, 0.005, 0.070, pitch=7, pivot=(0, -0.236, 0.05), plain=True)]
    mag += cartridge(CASE_BRASS, 0.046, -0.044, 0.0447, 0.0095, 0.0063, 0.012)
    for y in (-0.11, -0.15, -0.19):
        mag += [B(GUN_ALU, -0.0118, y, 0.052, 0.0236, 0.003, 0.044, plain=True)]
    return gun(p, [bone("muzzle", (0, 0, 0.452), hider), bone("bolt", (0, 0.006, 0.03), carrier), bone("charge", (0, 0.026, -0.105), charge),
                   bone("trigger", (0, -0.050, 0.010), trigger), bone("selector", (HW, -0.024, -0.036), selector),
                   bone("irons", (0, 0.0375, -0.078), irons), bone("mag", (0, -0.050, 0.070), mag)],
               dict(muzzle=(0, 0, 0.495), thread=(0, 0, 0.452), eject=(-0.02, 0.006, 0.03), sight=(0, 0.066, -0.082), front=(0, 0.066, 0.334),
                    optic=(0, 0.0375, 0.030), under=(0, -0.0295, 0.215), grip=(0, -0.100, -0.060), hand=(0, -0.030, 0.230), mag=(0, -0.050, 0.070),
                    charge=(0.0, 0.026, -0.118)))


# ---------------------------------------------------------------- SVD
def svd():
    """SVD Dragunov, 7.62x54R: a long thin barrel, slotted flash hider, vented laminated handguards, the skeleton
    stock with its cheek rest, and a ten-round magazine. 1225 mm long. The PSO-1 scope is a separate fitting."""
    S, K, WD = GUN_STEEL, GUN_BLACK, LAMINATE
    HW = 0.0165
    p = [side(K, HW * 2, [(-0.120, 0.020), (0.165, 0.020), (0.165, -0.022), (0.140, -0.044), (-0.050, -0.044), (-0.120, -0.026)])]
    p += [section(K, -0.115, 0.125, [(-HW, 0.020), (HW, 0.020), (HW, 0.036), (0.010, 0.046), (-0.010, 0.046), (-HW, 0.036)])]
    for z in (-0.09, -0.05, -0.01, 0.03):
        p += [B(K, -0.010, 0.0458, z, 0.020, 0.0012, 0.022, plain=True)]
    # rear sight on the front of the receiver
    p += [side(S, 0.024, [(0.125, 0.020), (0.250, 0.020), (0.250, 0.030), (0.225, 0.040), (0.150, 0.040), (0.125, 0.034)])]
    p += [B(S, -0.0055, 0.0405, 0.150, 0.011, 0.0035, 0.085, pitch=-2, pivot=(0, 0.0405, 0.232), plain=True),
          B(S, -0.008, 0.0395, 0.190, 0.016, 0.006, 0.010, plain=True),
          B(S, -0.0065, 0.0435, 0.148, 0.005, 0.0065, 0.004, plain=True), B(S, 0.0015, 0.0435, 0.148, 0.005, 0.0065, 0.004, plain=True)]
    # handguards: two laminated halves with six slots a side, held by steel rings
    p += [section(WD, 0.215, 0.470, [(-0.019, -0.030), (0.019, -0.030), (0.0225, -0.008), (0.021, 0.022), (0.012, 0.036), (-0.012, 0.036), (-0.021, 0.022), (-0.0225, -0.008)])]
    for i in range(6):
        p += P(BLACK, 0.0218, -0.002, 0.242 + i * 0.036, 0.0012, 0.012, 0.022, plain=True)
    p += [B(S, -0.0235, -0.032, 0.205, 0.047, 0.070, 0.010, plain=True), B(S, -0.0235, -0.032, 0.470, 0.047, 0.070, 0.010, plain=True)]
    # barrel, gas block, front sight in its hood, flash hider with five slots
    p += [turned(S, [(0.165, 0.022), (0.480, 0.019), (0.730, 0.0155)]), tube(S, 0.480, 0.560, 0.014, y=0.024)]
    p += [side(S, 0.022, [(0.520, -0.011), (0.562, -0.011), (0.562, 0.034), (0.520, 0.034)])]
    p += [side(S, 0.018, [(0.690, -0.010), (0.722, -0.010), (0.722, 0.012), (0.714, 0.028), (0.698, 0.028), (0.690, 0.012)]),
          lathe(S, "z", 0.0, 0.040, [(0.700, 0.011), (0.714, 0.011)], plain=True, open=True, seg=10),
          B(S, -0.001, 0.028, 0.706, 0.002, 0.016, 0.002, plain=True), B(S, -0.004, -0.020, 0.700, 0.008, 0.010, 0.016, plain=True)]
    hider = [turned(S, [(0.722, 0.018), (0.730, 0.022), (0.790, 0.020), (0.800, 0.017)]), bore(0.800, 0.0105)]
    for a in (0, 72, 144, 216, 288):
        hider += [B(BLACK, -0.002, 0.0098, 0.738, 0.004, 0.0008, 0.046, roll=a, pivot=(0, 0, 0.76), plain=True)]
    # trigger guard and magazine catch
    p += [B(S, -0.008, -0.078, -0.008, 0.016, 0.0016, 0.062, plain=True), B(S, -0.008, -0.078, 0.0525, 0.016, 0.034, 0.0016, plain=True),
          side(S, 0.010, [(0.046, -0.044), (0.052, -0.044), (0.051, -0.086), (0.042, -0.090)])]
    # scope rail on the left wall, pins and markings
    p += [B(S, HW, -0.018, -0.090, 0.0045, 0.024, 0.160, plain=True), B(S, HW + 0.0045, -0.016, -0.082, 0.002, 0.020, 0.144, plain=True)]
    for y, z in ((0.006, 0.140), (-0.014, 0.140), (0.004, -0.104), (-0.012, 0.006), (-0.020, -0.022)):
        p += pin(HW, y, z)
    p += [label(K, "SVD 7.62", HW, -0.030, 0.085, 0.045, 0.0045)]
    # skeleton stock: grip, upper and lower limbs, butt, and the clip-on cheek rest
    T = 0.030
    p += [side(WD, T, [(-0.010, -0.044), (-0.120, -0.026), (-0.125, 0.012), (-0.170, 0.008), (-0.190, -0.020), (-0.150, -0.126), (-0.112, -0.136), (-0.060, -0.068)]),
          side(WD, T, [(-0.160, 0.010), (-0.415, -0.008), (-0.415, -0.034), (-0.185, -0.016)]),
          side(WD, T, [(-0.140, -0.128), (-0.415, -0.128), (-0.415, -0.104), (-0.165, -0.098)]),
          side(WD, T, [(-0.385, -0.010), (-0.418, -0.012), (-0.418, -0.134), (-0.385, -0.130)]),
          B(S, -0.0155, -0.136, -0.424, 0.031, 0.128, 0.006, plain=True)]
    p += [section(BLACK, -0.345, -0.215, [(-0.017, -0.012), (0.017, -0.012), (0.017, 0.014), (0.010, 0.024), (-0.010, 0.024), (-0.017, 0.014)])]

    carrier = [B(GUN_BARE, -0.0186, 0.004, 0.030, 0.008, 0.028, 0.100, plain=True),
               bar(GUN_BARE, (-0.016, 0.018, 0.118), (-0.042, 0.018, 0.118), 0.011)]
    trigger = [side(S, 0.006, [(0.008, -0.044), (0.017, -0.044), (0.014, -0.058), (0.018, -0.071), (0.013, -0.072), (0.007, -0.058)])]
    selector = [side(S, 0.0014, [(-0.050, 0.014), (0.062, 0.010), (0.066, 0.003), (0.062, -0.003), (-0.032, -0.005), (-0.050, -0.003)], x=-HW - 0.0010),
                B(S, -HW - 0.0075, -0.002, 0.056, 0.006, 0.004, 0.010, plain=True),
                lathe(S, "x", 0.004, -0.040, [(-HW - 0.003, 0.0045), (-HW, 0.0045)], plain=True, seg=6)]
    mag = curved_mag(K, 0.094, -0.042, 0.072, 0.135, 0.420, 0.028)
    for i in range(3):
        mag += [B(K, -0.0148, -0.085 - i * 0.03, 0.068 + i * 0.006, 0.0296, 0.012, 0.050, plain=True)]
    mag += cartridge(CASE_STEEL, 0.058, -0.037, 0.0537, 0.0124, 0.0085, 0.022)
    return gun(p, [bone("muzzle", (0, 0, 0.722), hider), bone("bolt", (0, 0.018, 0.07), carrier), bone("trigger", (0, -0.044, 0.012), trigger),
                   bone("selector", (-HW, 0.004, -0.040), selector), bone("mag", (0, -0.044, 0.135), mag)],
               dict(muzzle=(0, 0, 0.800), thread=(0, 0, 0.722), eject=(-0.02, 0.02, 0.08), sight=(0, 0.050, 0.148), front=(0, 0.044, 0.707),
                    optic=(0, 0.0465, -0.005), under=(0, -0.030, 0.30), grip=(0, -0.095, -0.090), hand=(0, -0.030, 0.330), mag=(0, -0.044, 0.094),
                    charge=(-0.042, 0.018, 0.118)))


# ---------------------------------------------------------------- PKM
def pkm():
    """PKM, 7.62x54R: belt-fed from a 100-round box under the receiver, fluted barrel with a carry handle, gas
    cylinder underneath, folding bipod and a laminated skeleton stock. 1192 mm long."""
    S, K, WD = GUN_STEEL, GUN_BLACK, LAMINATE
    HW = 0.022
    p = [side(K, HW * 2, [(-0.090, 0.030), (0.285, 0.030), (0.285, -0.050), (-0.040, -0.050), (-0.090, -0.030)])]
    for z in (-0.05, 0.02, 0.09, 0.16, 0.23):                                                         # stamped ribs
        p += P(K, HW, -0.036, z, 0.0012, 0.050, 0.030, plain=True)
    p += [label(K, "PKM 7.62", HW + 0.0012, -0.012, 0.100, 0.045, 0.0045)]
    # feed tray, with the belt entering from the right and the links leaving on the left
    p += [B(S, -0.036, 0.018, 0.050, 0.014, 0.014, 0.070, plain=True), B(BLACK, HW + 0.0004, 0.004, 0.060, 0.0008, 0.018, 0.050, plain=True)]
    # rear sight, trigger guard, pistol grip
    p += [B(S, -0.010, 0.066, -0.060, 0.020, 0.006, 0.070, plain=True),
          B(S, -0.0065, 0.072, -0.058, 0.005, 0.0065, 0.004, plain=True), B(S, 0.0015, 0.072, -0.058, 0.005, 0.0065, 0.004, plain=True)]
    p += [B(S, -0.008, -0.084, -0.012, 0.016, 0.0016, 0.068, plain=True), B(S, -0.008, -0.084, 0.0545, 0.016, 0.034, 0.0016, plain=True)]
    p += [side(BAKELITE, 0.030, [(-0.014, -0.050), (-0.056, -0.050), (-0.094, -0.140), (-0.054, -0.150), (-0.034, -0.090)],
               marks=[("cap0", "checker", 0.45, 0.5, 0.06, 0.03), ("cap1", "checker", 0.45, 0.5, 0.06, 0.03)])]
    # barrel: trunnion, flutes, carry handle, front sight and flash hider; gas cylinder underneath
    p += [turned(S, [(0.285, 0.040), (0.330, 0.040), (0.340, 0.030), (0.600, 0.026), (0.770, 0.020)])]
    for a in range(0, 360, 45):
        p += [B(K, -0.0022, 0.0128, 0.350, 0.0044, 0.0016, 0.230, roll=a, pivot=(0, 0, 0.45), plain=True)]
    p += [bar(S, (0.0, 0.016, 0.318), (0.030, 0.050, 0.318), 0.008), bar(WD, (0.030, 0.050, 0.300), (0.030, 0.050, 0.395), 0.024)]
    p += [tube(S, 0.285, 0.620, 0.022, y=-0.036), side(S, 0.026, [(0.598, -0.050), (0.640, -0.050), (0.640, 0.012), (0.598, 0.012)])]
    p += [side(S, 0.018, [(0.735, -0.010), (0.765, -0.010), (0.765, 0.012), (0.758, 0.050), (0.744, 0.050), (0.735, 0.012)]),
          B(S, 0.0066, 0.050, 0.744, 0.0016, 0.024, 0.014, roll=-7, pivot=(0.0074, 0.05, 0.75), plain=True),
          B(S, -0.0082, 0.050, 0.744, 0.0016, 0.024, 0.014, roll=7, pivot=(-0.0074, 0.05, 0.75), plain=True),
          B(S, -0.0011, 0.050, 0.750, 0.0022, 0.028, 0.0022, plain=True)]
    hider = [turned(S, [(0.765, 0.020), (0.772, 0.026), (0.812, 0.024), (0.820, 0.020)]), bore(0.820, 0.011)]
    for a in (0, 72, 144, 216, 288):
        hider += [B(BLACK, -0.002, 0.0118, 0.778, 0.004, 0.0008, 0.032, roll=a, pivot=(0, 0, 0.79), plain=True)]
    # skeleton stock: limbs round the big cut-out, steel butt plate with its shoulder strap
    T = 0.032
    p += [side(WD, T, [(-0.090, 0.028), (-0.360, 0.012), (-0.360, -0.014), (-0.090, -0.002)]),
          side(WD, T, [(-0.090, -0.030), (-0.130, -0.030), (-0.360, -0.110), (-0.360, -0.084), (-0.130, -0.002), (-0.090, -0.002)]),
          side(WD, T, [(-0.330, 0.012), (-0.366, 0.010), (-0.366, -0.114), (-0.330, -0.104)]),
          B(S, -0.0165, -0.116, -0.372, 0.033, 0.130, 0.006, plain=True)]

    cover = [section(K, -0.085, 0.200, [(-HW, 0.030), (HW, 0.030), (HW, 0.054), (0.014, 0.066), (-0.014, 0.066), (-HW, 0.054)]),
             B(S, -0.012, 0.030, -0.092, 0.024, 0.024, 0.008, plain=True)]
    for z in (-0.04, 0.03, 0.10):
        cover += [B(K, -0.014, 0.0658, z, 0.028, 0.0014, 0.040, plain=True)]
    charge = [bar(GUN_BARE, (-HW, -0.020, 0.090), (-HW - 0.030, -0.020, 0.090), 0.012), B(S, -HW - 0.002, -0.026, 0.060, 0.002, 0.012, 0.060, plain=True)]
    trigger = [side(S, 0.006, [(0.006, -0.050), (0.015, -0.050), (0.013, -0.062), (0.017, -0.075), (0.012, -0.076), (0.006, -0.062)])]
    selector = [lathe(S, "x", -0.030, -0.020, [(HW, 0.005), (HW + 0.003, 0.005)], plain=True, seg=8),
                B(S, HW, -0.0325, -0.044, 0.004, 0.005, 0.024, plain=True)]
    bipod = []
    for sgn in (-1, 1):
        bipod += [bar(S, (sgn * 0.014, -0.050, 0.560), (sgn * 0.020, -0.058, 0.250), 0.010),
                  B(S, sgn * 0.020 - 0.006, -0.064, 0.240, 0.012, 0.004, 0.022, plain=True)]
    bipod += [B(S, -0.018, -0.054, 0.552, 0.036, 0.014, 0.016, plain=True)]
    # ammunition box hung under the receiver, belt up into the feed tray on the right
    box = [B(GUN_STEEL, -0.050, -0.165, 0.045, 0.092, 0.110, 0.175, plain=True, marks=[("left", "text:100 7.62", 0.5, 0.5, 0.012)]),
           B(S, -0.050, -0.056, 0.045, 0.092, 0.006, 0.175, plain=True), B(S, -0.004, -0.050, 0.110, 0.008, 0.004, 0.040, plain=True),
           B(S, -0.052, -0.120, 0.045, 0.002, 0.020, 0.175, plain=True), B(S, 0.050, -0.120, 0.045, 0.002, 0.020, 0.175, plain=True)]
    belt = []
    for i in range(5):
        y = -0.050 + i * 0.017
        x = -0.046 + (0.008 if i == 4 else 0)
        belt += cartridge(CASE_STEEL, 0.050, y, 0.0537, 0.0124, 0.0085, 0.022, x=x)
        belt += [B(GUN_STEEL, x - 0.0075, y - 0.0075, 0.066, 0.015, 0.015, 0.014, plain=True)]
    return gun(p, [bone("muzzle", (0, 0, 0.765), hider), bone("cover", (0, 0.034, 0.200), cover), bone("bolt", (0, -0.020, 0.09), charge),
                   bone("trigger", (0, -0.050, 0.010), trigger), bone("selector", (HW, -0.030, -0.020), selector),
                   bone("bipod", (0, -0.050, 0.560), bipod), bone("mag", (0, -0.056, 0.130), box),
                   dict(name="belt", pivot=(-0.046, -0.02, 0.07), cubes=belt)],
               dict(muzzle=(0, 0, 0.820), thread=(0, 0, 0.765), eject=(0.026, 0.01, 0.085), sight=(0, 0.0785, -0.058), front=(0, 0.0785, 0.751),
                    optic=(0, 0.066, 0.05), under=(0, -0.05, 0.40), grip=(0, -0.100, -0.058), hand=(0, -0.168, 0.130), mag=(0, -0.056, 0.130),
                    charge=(-0.052, -0.020, 0.090)))


# ---------------------------------------------------------------- M17
def m17():
    """M17 service pistol (SIG P320), 9x19: coyote slide and polymer frame, light rail, thumb safety, night
    sights and a 17-round magazine. 203 mm long."""
    T, TS, S = POLYMER_TAN, _mat("gun_tan", (146, 124, 92), "gunmetal", wear=(96, 90, 82)), GUN_STEEL
    # frame: rail, trigger guard, grip with its stippled panels and beavertail
    p = [side(T, 0.028, [(-0.060, -0.013), (0.092, -0.013), (0.092, -0.030), (0.050, -0.032), (0.046, -0.066), (0.040, -0.074), (-0.004, -0.074),
                         (-0.010, -0.066), (0.040, -0.066), (0.040, -0.034), (-0.006, -0.034), (-0.012, -0.050), (-0.034, -0.128), (-0.086, -0.118),
                         (-0.066, -0.040), (-0.078, -0.022), (-0.078, -0.013)],
              marks=[("cap0", "checker", 0.3, 0.72, 0.07, 0.034), ("cap1", "checker", 0.3, 0.72, 0.07, 0.034)])]
    p += rail(T, 0.048, 0.090, -0.030, w=0.020, up=-1)
    p += [B(S, 0.014, -0.022, 0.010, 0.002, 0.005, 0.022, plain=True), B(S, 0.014, -0.024, 0.040, 0.002, 0.008, 0.012, plain=True),
          B(S, -0.016, -0.024, 0.040, 0.002, 0.008, 0.012, plain=True), B(S, -0.016, -0.050, -0.008, 0.002, 0.008, 0.008, plain=True)]
    p += [label(T, "M17", 0.014, -0.022, 0.060, 0.020, 0.004)]
    # barrel showing at the muzzle, recoil spring guide under it
    p += [tube(S, 0.060, 0.1005, 0.0142), bore(0.1005, 0.009), tube(S, 0.070, 0.0995, 0.008, y=-0.0135)]

    slide = [side(TS, 0.026, [(-0.104, -0.013), (0.099, -0.013), (0.099, 0.012), (0.094, 0.020), (-0.100, 0.020), (-0.104, 0.014)],
                  marks=[("cap0", "grooves", 0.55, 0.9, 0.024, 0.030), ("cap1", "grooves", 0.55, 0.9, 0.024, 0.030),
                         ("cap0", "grooves", 0.55, 0.12, 0.024, 0.024), ("cap1", "grooves", 0.55, 0.12, 0.024, 0.024)]),
             B(BLACK, -0.0132, 0.004, 0.002, 0.0142, 0.0162, 0.034, plain=True),                               # ejection port
             B(GUN_BARE, -0.0128, 0.006, 0.004, 0.012, 0.012, 0.030, plain=True),                             # barrel hood
             B(S, -0.0016, 0.020, 0.084, 0.0032, 0.006, 0.006, plain=True, marks=[("rear", "dot", 0.5, 0.5, 0.002, (150, 230, 120))]),
             B(S, -0.009, 0.020, -0.100, 0.0065, 0.006, 0.008, plain=True, marks=[("rear", "dot", 0.5, 0.5, 0.002, (150, 230, 120))]),
             B(S, 0.0025, 0.020, -0.100, 0.0065, 0.006, 0.008, plain=True, marks=[("rear", "dot", 0.5, 0.5, 0.002, (150, 230, 120))]),
             B(S, -0.009, 0.0175, -0.100, 0.018, 0.0025, 0.008, plain=True),
             B(TS, -0.010, 0.020, -0.080, 0.020, 0.0012, 0.040, plain=True),                                  # optic cover plate
             label(TS, "SIG SAUER", 0.013, 0.006, 0.040, 0.040, 0.0036)]
    trigger = [side(S, 0.007, [(0.012, -0.034), (0.022, -0.034), (0.020, -0.048), (0.024, -0.062), (0.019, -0.063), (0.012, -0.048)])]
    selector = [B(S, 0.014, -0.019, -0.062, 0.004, 0.005, 0.024, plain=True), B(S, -0.018, -0.019, -0.062, 0.004, 0.005, 0.024, plain=True)]
    mag = [side(GUN_STEEL, 0.021, [(-0.016, -0.016), (-0.058, -0.016), (-0.084, -0.118), (-0.040, -0.126)]),
           side(POLYMER_TAN, 0.029, [(-0.036, -0.126), (-0.088, -0.116), (-0.091, -0.130), (-0.039, -0.140)])]
    mag += cartridge(CASE_BRASS, -0.052, -0.013, 0.019, 0.0098, 0.0096, 0.010)
    for i in range(4):
        mag += [lathe(BLACK, "x", -0.045 - i * 0.018, -0.040 - i * 0.0045, [(-0.0108, 0.002), (0.0108, 0.002)], plain=True, seg=6)]
    return gun(p, [bone("bolt", (0, 0.0, 0.0), slide), bone("trigger", (0, -0.030, 0.016), trigger), bone("selector", (0, -0.0165, -0.040), selector),
                   bone("mag", (0, -0.070, -0.050), mag)],
               dict(muzzle=(0, 0, 0.1005), thread=(0, 0, 0.1005), eject=(-0.014, 0.012, 0.02), sight=(0, 0.026, -0.100), front=(0, 0.026, 0.087),
                    optic=(0, 0.0212, -0.060), under=(0, -0.034, 0.07), grip=(0, -0.075, -0.050), hand=(0.012, -0.085, -0.040), mag=(0, -0.070, -0.050),
                    charge=(0.0, 0.016, -0.085)))


# ---------------------------------------------------------------- M590A1
def m590():
    """Mossberg 590A1 pump shotgun, 12 gauge: heavy barrel over an eight-round tube, ghost-ring sights, a ribbed
    forend on twin action bars and a polymer stock. 1040 mm long."""
    S, A, PL = GUN_STEEL, GUN_GREY, POLYMER
    HW = 0.0175
    p = [side(A, HW * 2, [(-0.090, 0.026), (0.140, 0.026), (0.140, -0.046), (0.020, -0.050), (-0.060, -0.050), (-0.090, -0.036)])]
    p += [B(BLACK, -HW - 0.0004, -0.012, 0.050, 0.001, 0.022, 0.070, plain=True), B(GUN_BARE, -HW - 0.0010, -0.010, 0.054, 0.001, 0.018, 0.062, plain=True)]
    p += [B(BLACK, -0.012, -0.0506, 0.040, 0.024, 0.001, 0.085, plain=True)]                                # loading port underneath
    p += [B(S, -0.005, 0.026, -0.082, 0.010, 0.003, 0.016, plain=True), label(A, "590A1 12GA", HW, -0.030, 0.050, 0.050, 0.0045)]
    for y, z in ((-0.010, 0.020), (-0.030, -0.030)):
        p += pin(HW, y, z, 0.006, S)
    # ghost-ring rear sight with its guard wings, trigger guard
    p += [B(S, -0.011, 0.026, -0.050, 0.022, 0.006, 0.030, plain=True), B(S, -0.011, 0.032, -0.046, 0.0025, 0.020, 0.018, plain=True),
          B(S, 0.0085, 0.032, -0.046, 0.0025, 0.020, 0.018, plain=True),
          lathe(S, "z", 0.0, 0.044, [(-0.040, 0.0030), (-0.040, 0.0058), (-0.036, 0.0058), (-0.036, 0.0030)], plain=True, seg=10)]
    p += [side(PL, 0.020, [(-0.020, -0.050), (0.070, -0.050), (0.066, -0.058), (0.056, -0.084), (-0.006, -0.084), (-0.016, -0.076), (0.048, -0.076),
                           (0.056, -0.058), (-0.014, -0.058)])]
    # barrel and magazine tube with its cap and the barrel clamp, bayonet lug, front sight
    p += [tube(S, 0.140, 0.640, 0.0235), bore(0.640, 0.0185), tube(S, 0.140, 0.598, 0.026, y=-0.0285),
          turned(S, [(0.598, 0.028), (0.622, 0.028), (0.628, 0.020)], y=-0.0285)]
    p += [side(S, 0.034, [(0.540, -0.044), (0.560, -0.044), (0.560, 0.014), (0.540, 0.014)]), B(S, -0.004, -0.052, 0.585, 0.008, 0.010, 0.022, plain=True)]
    p += [side(S, 0.006, [(0.600, 0.011), (0.630, 0.011), (0.626, 0.030), (0.612, 0.030)]),
          B((_mat("sight_orange", (230, 110, 40), "lamp")), -0.0015, 0.024, 0.6115, 0.003, 0.006, 0.0006, plain=True)]
    # stock with pistol-grip wrist, comb and recoil pad
    p += [side(PL, 0.036, [(-0.090, 0.024), (-0.150, 0.012), (-0.400, -0.004), (-0.404, -0.122), (-0.390, -0.126), (-0.200, -0.070), (-0.150, -0.110),
                           (-0.112, -0.112), (-0.075, -0.050), (-0.090, -0.036)],
               marks=[("cap0", "checker", 0.2, 0.2, 0.05, 0.03), ("cap1", "checker", 0.2, 0.2, 0.05, 0.03)]),
          B(RUBBER_PAD, -0.018, -0.126, -0.418, 0.036, 0.124, 0.014, plain=True)]

    pump = [section(PL, 0.250, 0.420, [(-0.018, -0.012), (0.018, -0.012), (0.024, -0.028), (0.020, -0.050), (-0.020, -0.050), (-0.024, -0.028)]),
            bar(S, (0.019, -0.022, 0.140), (0.019, -0.022, 0.250), 0.006), bar(S, (-0.019, -0.022, 0.140), (-0.019, -0.022, 0.250), 0.006)]
    for i in range(9):
        pump += P(PL, 0.0225, -0.046, 0.262 + i * 0.017, 0.0022, 0.030, 0.008, plain=True)
    trigger = [side(S, 0.006, [(0.012, -0.050), (0.021, -0.050), (0.019, -0.060), (0.023, -0.071), (0.018, -0.072), (0.012, -0.060)])]
    selector = [B(S, -0.004, 0.026, -0.076, 0.008, 0.0045, 0.010, plain=True)]
    shell = shotshell(0.060, -0.060)
    return gun(p, [bone("bolt", (0, -0.03, 0.33), pump), bone("trigger", (0, -0.050, 0.016), trigger), bone("selector", (0, 0.026, -0.070), selector),
                   bone("mag", (0, -0.060, 0.090), shell)],
               dict(muzzle=(0, 0, 0.640), thread=(0, 0, 0.640), eject=(-0.02, 0.0, 0.085), sight=(0, 0.044, -0.038), front=(0, 0.030, 0.612),
                    optic=(0, 0.026, 0.06), under=(0, -0.05, 0.33), grip=(0, -0.085, -0.110), hand=(0, -0.050, 0.335), mag=(0, -0.060, 0.090),
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
