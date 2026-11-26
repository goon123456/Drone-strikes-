#!/usr/bin/env python3
"""The Superb Warfare vehicle models. Each function returns the cubes of the three aiming bones (base, turret,
barrel) plus extra bones; bones named wheelL*/wheelR* are spun by Superb Warfare when driving. The kit of parts
and the texture painter are in vehicle_kit.py. Used by gen_assets.py.

Preview without starting the game:  python3 tools/gen_vehicles.py <output dir> [vehicle ...]
"""
import math
from vehicle_kit import *


# ---------------------------------------------------------------- anti-aircraft
def zu_23_2():
    """ZU-23-2 in firing position: wheels swung up off the ground, gun on three jacks. Pivots (0,.5,0) / (0,1.15,0)."""
    # carriage: a low delta-shaped frame, the towing trail, and the turntable race
    base = [tower(OLIVE_D, 0.2, [(-0.95, -0.42), (0.95, -0.42), (0.34, 1.2), (-0.34, 1.2)], 0.34, [(-0.9, -0.38), (0.9, -0.38), (0.3, 1.12), (-0.3, 1.12)]),
            hull(OLIVE_D, -0.12, 0.12, [(-1.75, 0.22), (-1.75, 0.32), (-0.4, 0.36), (-0.4, 0.2)], plain=True),      # trail
            B(STEEL_D, -0.07, 0.2, -1.95, 0.14, 0.1, 0.22, plain=True)]                                             # towing eye
    base += [lathe(STEEL_D, "y", 0, 0, [(0.34, 0.0), (0.34, 0.5), (0.42, 0.5), (0.46, 0.46), (0.5, 0.46), (0.5, 0.0)])]
    base += jack(0, 1.32, 0.34) + jack(-0.86, -0.34, 0.34) + jack(0.86, -0.34, 0.34)
    for sx in (1, -1):
        # road wheels on their swing arms, tipped outwards clear of the ground
        w = wheel(sx * 0.98 - (0.0 if sx > 0 else 0.2), 0.2, 0.62, 0.0, 0.8, OLIVE_D)[0]
        w["rot"], w["pivot"] = (0, 0, -sx * 26), (sx * 0.9, 0.3, 0)
        base += [w, B(STEEL_D, sx * 0.9 - 0.05, 0.3, -0.06, 0.1, 0.36, 0.12, roll=-sx * 26, pivot=(sx * 0.9, 0.3, 0), plain=True)]
        base += rod_x(STEEL_D, sx * 0.55 - (0.0 if sx > 0 else 0.4), 0.4, 0.3, 0.0, 0.1)
        base += [B(OLIVE_D, sx * 0.62 - 0.12, 0.34, 0.5, 0.24, 0.16, 0.34, marks=[("top", "panel", 0.5, 0.5, 0.18, 0.26)])]   # tool boxes
    # upper mount: platform, two trapezoid cradle cheeks, ammunition boxes with sloped lids and feed chutes
    turret = [lathe(OLIVE, "y", 0, 0, [(0.5, 0.0), (0.5, 0.48), (0.56, 0.5), (0.6, 0.5), (0.6, 0.0)]),
              B(OLIVE, -0.5, 0.56, -0.5, 1.0, 0.06, 0.95, plain=True)]
    for sx in (1, -1):
        x0, x1 = (0.34, 0.44) if sx > 0 else (-0.44, -0.34)
        turret += [hull(OLIVE, x0, x1, [(-0.36, 0.62), (-0.24, 1.3), (0.12, 1.36), (0.36, 1.0), (0.4, 0.62)])]
        turret += rod_x(STEEL_D, x0 - 0.02, 0.14, 1.15, 0.0, 0.2)                                    # trunnion cap
        bx0, bx1 = (0.5, 0.94) if sx > 0 else (-0.94, -0.5)
        side = "cap1" if sx > 0 else "cap0"
        turret += [hull(OLIVE_D, bx0, bx1, [(-0.3, 0.7), (-0.3, 1.16), (-0.2, 1.24), (0.38, 1.24), (0.5, 1.12), (0.5, 0.7)],
                        marks=[(side, "panel", 0.5, 0.5, 0.6, 0.36)])]
        turret += [B(STEEL_D, sx * 0.72 - 0.16, 1.24, -0.12, 0.32, 0.02, 0.4, plain=True),            # lid strap and latches
                   B(STEEL, sx * 0.95 - 0.015, 0.98, -0.1, 0.03, 0.06, 0.05, plain=True), B(STEEL, sx * 0.95 - 0.015, 0.98, 0.25, 0.03, 0.06, 0.05, plain=True),
                   B(STEEL_D, sx * 0.4 - 0.08, 1.02, 0.02, 0.16, 0.12, 0.3, plain=True)]              # feed chute into the gun
        # gunner's seat: pan, tilted back rest, post and foot rest
        turret += [B(BLACK, sx * 0.42 - 0.17, 0.66, -1.02, 0.34, 0.06, 0.34), B(BLACK, sx * 0.42 - 0.17, 0.7, -1.08, 0.34, 0.4, 0.05, pitch=-12),
                   B(STEEL_D, sx * 0.42 - 0.03, 0.56, -0.9, 0.06, 0.12, 0.06, plain=True), B(STEEL_D, sx * 0.42 - 0.12, 0.42, -0.6, 0.24, 0.03, 0.12, plain=True)]
        turret += [B(STEEL_D, sx * 0.42 - 0.03, 0.56, -1.0, 0.06, 0.05, 0.5, plain=True)]
    # sight: column, collimator box, ring sight for ground targets, eyepiece
    turret += rod_y(STEEL_D, 0, -0.62, 0.62, 0.6, 0.08) + [B(BLACK, -0.13, 1.22, -0.78, 0.26, 0.18, 0.34), B(GLASS, -0.08, 1.26, -0.44, 0.16, 0.1, 0.01)]
    turret += rod_z(BLACK, 0, 1.31, -0.92, 0.14, 0.07) + rod_z(STEEL_D, 0.2, 1.3, -0.7, 0.03, 0.2) + rod_z(STEEL_D, 0.2, 1.3, -0.45, 0.02, 0.12)
    for sx in (1, -1):                                                                               # laying hand wheels
        turret += rod_x(STEEL, sx * 0.2 - 0.015, 0.03, 0.86, -0.7, 0.26) + rod_x(STEEL_D, sx * 0.2 - 0.04, 0.08, 0.86, -0.7, 0.05)
        turret += [B(BLACK, sx * 0.2 - 0.02, 0.95, -0.72, 0.07, 0.04, 0.04, plain=True)]
    # the twin 23 mm guns: receivers on a cradle, barrels with conical flash hiders that kick back as they fire
    barrel, recoil = [hull(OLIVE_D, -0.32, 0.32, [(-0.3, 1.0), (-0.3, 1.1), (0.45, 1.1), (0.55, 1.04), (0.55, 1.0)], plain=True)], []
    for sx in (0.18, -0.18):
        barrel += [hull(OLIVE_D, sx - 0.1, sx + 0.1, [(-0.62, 1.05), (-0.62, 1.26), (-0.5, 1.3), (0.5, 1.3), (0.6, 1.24), (0.6, 1.05)],
                        marks=[("s3", "panel", 0.4, 0.5, 0.12, 0.5)])]
        barrel += [B(STEEL_D, sx - 0.04, 1.3, -0.3, 0.08, 0.04, 0.3, plain=True)]                     # top cover latch
        barrel += rod_z(STEEL_D, sx, 1.17, 0.6, 0.4, 0.13)                                            # barrel jacket
        barrel += rod_z(STEEL_D, sx + (0.07 if sx > 0 else -0.07), 1.1, 0.2, 0.9, 0.035)              # gas tube
        recoil += [lathe(STEEL_D, "z", sx, 1.17, [(0.9, 0.0), (0.9, 0.04), (2.2, 0.034), (2.22, 0.05), (2.3, 0.05), (2.5, 0.085), (2.5, 0.06), (2.32, 0.03)])]
        recoil += rod_z(STEEL, sx, 1.17, 1.45, 0.05, 0.1)
        recoil += [B(STEEL_D, sx - 0.012, 1.2, 1.3, 0.024, 0.09, 0.3, plain=True)]                    # barrel-change handle
    barrel += [B(STEEL_D, -0.03, 1.3, -0.5, 0.06, 0.05, 0.9, plain=True)]
    bones = [{"name": "move_recoilLight", "parent": "barrel", "pivot": (0, 1.17, 0.9), "cubes": recoil}]
    # ---- fittings
    base += chock(1.35, 0.25) + chock(-1.35, 0.25) + eye(0.0, 0.34, 0.9, along="x") + extinguisher(-0.5, 0.34, 0.75)
    base += [B(OLIVE_D, -0.1, 0.34, -1.0, 0.2, 0.02, 0.3, marks=[("top", "digits:23-2", 0.5, 0.5, 0)])]
    base += run(BLACK, [(0.3, 0.36, -0.3), (0.5, 0.36, -0.6), (0.6, 0.32, -1.1)], 0.03)
    for sx in (1, -1):
        turret += [bar(STEEL_D, (sx * 0.3, 0.62, 0.42), (sx * 0.3, 0.9, 0.7), 0.03), B(STEEL_D, sx * 0.3 - 0.1, 0.56, 0.7, 0.2, 0.03, 0.12, plain=True)]   # foot rests
        turret += [B(STEEL_D, sx * 0.72 - 0.2, 1.02, 0.5, 0.4, 0.2, 0.04, plain=True)]                         # spent-case deflector
        turret += eye(sx * 0.72, 1.26, 0.3)
        # a few rounds of the belt showing where it leaves the box
        for i in range(4):
            turret += rod_x(BRASS, sx * 0.44 - 0.07, 0.14, 1.14 - i * 0.012, 0.06 + i * 0.045, 0.028)
    turret += [B(STEEL_D, -0.04, 0.62, 0.3, 0.08, 0.2, 0.06, plain=True)] + rod_x(STEEL, -0.2, 0.4, 0.9, -0.3, 0.03)   # traverse lock, equilibrator shaft
    turret += [lathe(STEEL_D, "z", 0.0, 0.86, [(-0.3, 0.0), (-0.3, 0.07), (0.2, 0.07), (0.2, 0.04), (0.42, 0.04), (0.42, 0.0)])]   # balancing spring
    return dict(base=base, turret=turret, barrel=barrel, bones=bones)


def gepard():
    """Flakpanzer Gepard: Leopard 1 hull, tall slab turret, guns outside, two radars. Pivots (0,2,-.3) / (0,2.8,-.1)."""
    gear, bones, track = running_gear(1.07, 0.55, -3.75, 3.75, 7, 0.7, NATO_D, rollers=4)
    N, D = NATO, NATO_D
    base = gear + [
        # lower hull tub between the tracks, and the upper hull with its long sloped glacis and undercut tail
        hull(D, -1.07, 1.07, [(-3.5, 0.5), (-3.72, 1.1), (3.4, 1.1), (3.78, 0.95), (3.25, 0.45), (-3.2, 0.42)], plain=True),
        hull(N, -1.62, 1.62, [(-3.82, 1.1), (-3.86, 1.78), (-3.55, 2.0), (2.15, 2.0), (3.86, 1.27), (3.86, 1.12), (3.6, 1.08)],
             marks=[("s0", "cross", 0.5, 0.5, 0.32), ("cap0", "panel", 0.3, 0.55, 0.9, 0.3), ("cap1", "panel", 0.3, 0.55, 0.9, 0.3)]),
        B(D, -1.62, 1.2, 3.84, 3.24, 0.1, 0.06, plain=True),                                   # front lip
        B(WHITE, 0.55, 1.3, -3.9, 0.5, 0.13, 0.02, marks=[("rear", "plate", 0.5, 0.5, 0.5, 0.13)])]
    # ---- nose: lights with guards, towing shackles, spare track links, driver's position
    for sx in (1, -1):
        base += [B(WHITE, sx * 1.2 - 0.1, 1.42, 3.5, 0.2, 0.18, 0.1), B(AMBER, sx * 0.95 - 0.05, 1.46, 3.55, 0.1, 0.1, 0.06),
                 B(D, sx * 1.2 - 0.14, 1.38, 3.62, 0.03, 0.26, 0.03, plain=True), B(D, sx * 1.2 + 0.11, 1.38, 3.62, 0.03, 0.26, 0.03, plain=True),
                 B(D, sx * 1.2 - 0.14, 1.62, 3.5, 0.28, 0.03, 0.15, plain=True),
                 B(STEEL_D, sx * 0.75 - 0.06, 0.82, 3.72, 0.12, 0.2, 0.14), B(STEEL, sx * 0.75 - 0.03, 0.8, 3.84, 0.06, 0.12, 0.05),   # shackles
                 B(BLACK, sx * 1.62 - 0.02, 0.75, 3.5, 0.04, 0.5, 0.3, plain=True)]                                               # mud flap
        base += P(D, 1.07, 1.25, 3.3, 0.55, 0.06, 0.6, plain=True) if sx == 1 else []                                              # front mudguards
    for i in range(6):                                                                         # spare links across the glacis
        base += [slope(TRACK, -0.72 + i * 0.25, 0.2, 2.7, 1.84, 3.05, 1.68, 0.05, plain=True)]
    base += [B(D, -1.25, 2.0, 1.55, 0.7, 0.06, 0.62, plain=True), B(STEEL_D, -1.0, 2.06, 1.7, 0.2, 0.04, 0.05, plain=True)]        # driver's hatch
    for i in range(3):
        base += [B(BLACK, -1.2 + i * 0.22, 2.0, 2.2, 0.16, 0.1, 0.1, plain=True), B(GLASS, -1.19 + i * 0.22, 2.02, 2.3, 0.14, 0.07, 0.01)]
    base += [B(D, 0.5, 2.0, 1.4, 0.9, 0.2, 0.8), B(GRILLE, 0.55, 2.2, 1.45, 0.8, 0.01, 0.7),       # auxiliary engine box
             B(STEEL_D, 1.42, 2.05, 1.5, 0.12, 0.12, 0.5, plain=True),                             # its exhaust
             B(D, -0.4, 2.0, 2.0, 0.5, 0.05, 0.18, plain=True)]
    # ---- sides: skirts in sections, tools, tow cable, exhaust grilles
    for sx in (1, -1):
        for i in range(5):
            base += [B(D, sx * 1.62 - (0.0 if sx > 0 else 0.05), 0.7, -3.55 + i * 1.42, 0.05, 0.45, 1.36,
                       marks=[("left" if sx > 0 else "right", "panel", 0.5, 0.5, 1.2, 0.3)])]
        base += [B(GRILLE, sx * 1.63 - 0.02, 1.2, -3.5, 0.04, 0.62, 1.5), B(D, sx * 1.65 - 0.03, 1.16, -3.55, 0.06, 0.05, 1.6, plain=True),
                 B(D, sx * 1.65 - 0.03, 1.82, -3.55, 0.06, 0.05, 1.6, plain=True)]
        base += rod_z(BLACK, sx * 1.52, 2.04, -1.6, 3.0, 0.06)                                     # tow cable
        base += [B(STEEL_D, sx * 1.52 - 0.06, 2.0, 1.4, 0.12, 0.08, 0.18, plain=True), B(STEEL_D, sx * 1.52 - 0.06, 2.0, -1.75, 0.12, 0.08, 0.18, plain=True)]
    base += rod_z(WOOD, 1.3, 2.04, -0.6, 1.0, 0.05) + [B(STEEL_D, 1.22, 2.0, 0.4, 0.16, 0.05, 0.22, plain=True)]                   # shovel
    base += rod_z(WOOD, -1.3, 2.04, -0.2, 0.8, 0.05) + [B(STEEL_D, -1.38, 2.0, 0.55, 0.16, 0.08, 0.1, plain=True)]                 # axe
    base += rod_z(STEEL, -1.18, 2.03, -1.4, 1.1, 0.04)                                             # crowbar
    # ---- engine deck: louvres, round fan grille, access hatches, lifting eyes
    base += [B(GRILLE, -1.3, 2.0, -3.4, 1.1, 0.03, 1.3), B(GRILLE, 0.2, 2.0, -3.4, 1.1, 0.03, 1.3),
             B(D, -1.4, 2.0, -3.5, 2.8, 0.05, 0.08, plain=True), B(D, -0.15, 2.0, -3.45, 0.3, 0.05, 1.4, plain=True),
             B(D, -1.2, 2.0, -2.0, 1.0, 0.04, 0.7, plain=True), B(D, 0.2, 2.0, -2.0, 1.0, 0.04, 0.7, plain=True)]
    base += rod_y(GRILLE, 0, -2.75, 2.0, 0.05, 0.7)
    for (x, z) in ((1.4, -3.6), (-1.4, -3.6), (1.4, 1.9), (-1.4, 1.9)):
        base += [B(STEEL_D, x - 0.04, 2.0, z, 0.08, 0.1, 0.03, plain=True)]
    # ---- rear plate: lights, tow hooks, telephone box, jerrycans, cable, mud flaps
    base += [B(D, -1.6, 1.1, -3.83, 3.2, 0.9, 0.08), B(D, -0.5, 1.2, -3.9, 1.0, 0.5, 0.08, marks=[("rear", "hatch", 0.5, 0.5, 0.8, 0.4)]),
             B(STEEL_D, -1.25, 0.9, -3.92, 0.14, 0.22, 0.16), B(STEEL_D, 1.11, 0.9, -3.92, 0.14, 0.22, 0.16),
             B(D, 0.7, 1.3, -3.96, 0.3, 0.4, 0.14), B(CANVAS, -1.45, 1.3, -4.02, 0.5, 0.45, 0.2),
             B(D, -1.5, 1.25, -4.05, 0.6, 0.05, 0.25, plain=True)]
    base += P(RED, 1.3, 1.65, -3.86, 0.2, 0.12, 0.04) + P(AMBER, 1.08, 1.65, -3.86, 0.14, 0.12, 0.04)
    base += P(BLACK, 1.1, 0.5, -3.88, 0.5, 0.55, 0.03, plain=True)
    base += rod_x(BLACK, -0.9, 1.8, 1.9, -3.9, 0.07)
    # ---- turret
    turret = [
        # faceted turret: slab sides, a front that narrows and leans back, and a bustle behind
        tower(N, 2.0, [(-1.05, -1.9), (1.05, -1.9), (1.05, 0.85), (0.72, 1.75), (-0.72, 1.75), (-1.05, 0.85)],
              3.25, [(-1.0, -1.86), (1.0, -1.86), (1.0, 0.6), (0.64, 1.1), (-0.64, 1.1), (-1.0, 0.6)],
              marks=[("s1", "cross", 0.3, 0.5, 0.42), ("s5", "cross", 0.7, 0.5, 0.42), ("s1", "panel", 0.72, 0.45, 0.6, 0.5), ("s5", "panel", 0.28, 0.45, 0.6, 0.5)]),
        tower(N, 2.1, [(-0.92, -2.5), (0.92, -2.5), (0.98, -1.88), (-0.98, -1.88)], 3.05, [(-0.85, -2.42), (0.85, -2.42), (0.95, -1.88), (-0.95, -1.88)],
              marks=[("s0", "vent", 0.5, 0.5, 1.2, 0.4)]),
        B(D, -1.02, 3.25, -1.88, 2.04, 0.04, 2.5, plain=True),                                  # roof plate
        B(D, -1.12, 2.0, -1.95, 2.24, 0.1, 3.1, plain=True)]                                    # turret ring skirt
    turret += P(N, 1.05, 2.1, -1.7, 0.12, 0.9, 0.7) + P(N, 1.05, 2.15, 0.3, 0.1, 0.7, 0.6)       # side stowage bins
    # hatches, periscopes and sights on the roof
    turret += rod_y(D, -0.45, -0.1, 3.3, 0.1, 0.62) + rod_y(D, 0.45, -0.1, 3.3, 0.1, 0.62)
    turret += [B(STEEL_D, -0.5, 3.4, -0.42, 0.1, 0.04, 0.06, plain=True), B(STEEL_D, 0.4, 3.4, -0.42, 0.1, 0.04, 0.06, plain=True)]
    for (x, z) in ((-0.75, 0.45), (-0.45, 0.5), (-0.15, 0.45), (0.2, 0.45), (0.5, 0.5), (0.8, 0.45)):
        turret += [B(BLACK, x - 0.1, 3.3, z, 0.2, 0.12, 0.14, plain=True), B(GLASS, x - 0.08, 3.33, z + 0.14, 0.16, 0.07, 0.01)]
    turret += [B(D, -0.2, 3.3, 0.75, 0.4, 0.32, 0.35), B(GLASS, -0.14, 3.38, 1.1, 0.28, 0.18, 0.01),   # panoramic sight head
               B(D, 0.55, 3.3, -1.2, 0.35, 0.14, 0.35), B(GRILLE, 0.58, 3.44, -1.17, 0.29, 0.01, 0.29)]  # ventilator
    # tracking radar on the turret front: yoke arms and a round dish with its feed
    turret += [B(D, -0.34, 2.3, 1.2, 0.68, 0.7, 0.3)] + P(D, 0.42, 2.38, 1.3, 0.1, 0.55, 0.45)
    turret += rod_z(D, 0, 2.66, 1.42, 0.2, 1.0) + rod_z(RADOME, 0, 2.66, 1.62, 0.2, 0.92) + rod_z(RADOME, 0, 2.66, 1.82, 0.08, 0.6)
    turret += rod_z(STEEL_D, 0, 2.66, 1.9, 0.12, 0.12)
    # search radar at the back: a mast, and a head that turns once a second carrying a curved reflector made of
    # five slats, its frame and feed horn
    turret += [B(D, -0.14, 3.3, -2.2, 0.28, 0.2, 0.28)] + rod_y(D, 0, -2.06, 3.5, 0.5, 0.18)
    radar = rod_y(STEEL_D, 0, -2.06, 4.0, 0.08, 0.26)
    radar += [B(STEEL_D, -0.7, 4.3, -2.1, 1.4, 0.05, 0.05, plain=True), B(STEEL_D, -0.03, 4.05, -2.1, 0.06, 0.06, 0.55, plain=True),
              B(BLACK, -0.12, 4.0, -1.6, 0.24, 0.16, 0.12), B(D, -0.6, 4.72, -2.2, 1.2, 0.06, 0.06, plain=True),
              B(D, -0.03, 4.55, -2.2, 0.06, 0.2, 0.06, plain=True)]
    for i, ang in enumerate((-30, -15, 0, 15, 30)):
        x = (i - 2) * 0.38
        radar += [B(D, x - 0.2, 3.95, -2.24 + abs(i - 2) * 0.05, 0.4, 0.6, 0.05, yaw=ang),
                  B(STEEL_D, x - 0.19, 3.97, -2.19 + abs(i - 2) * 0.05, 0.38, 0.56, 0.02, yaw=ang, plain=True)]
    bones.append({"name": "move_spinRadar", "parent": "turret", "pivot": (0, 4.0, -2.06), "cubes": radar})
    # smoke-grenade dischargers, aerials, lifting hooks, hand rails
    for sx in (1, -1):
        for i in range(4):
            turret += [B(STEEL_D, sx * (0.6 + i * 0.12) - 0.045, 2.3 + (i % 2) * 0.1, 1.5, 0.09, 0.09, 0.28, pitch=28, plain=True)]
        turret += [B(STEEL_D, sx * 0.95 - 0.04, 3.3, -1.7, 0.08, 0.14, 0.08, plain=True), B(BLACK, sx * 0.95 - 0.012, 3.44, -1.67, 0.025, 1.7, 0.025, plain=True),
                   B(STEEL_D, sx * 0.9 - 0.03, 3.3, 0.9, 0.06, 0.08, 0.12, plain=True), B(STEEL_D, sx * 0.9 - 0.03, 3.3, -1.85, 0.06, 0.08, 0.12, plain=True),
                   B(STEEL_D, sx * 1.07 - 0.015, 2.9, -1.2, 0.03, 0.03, 1.2, plain=True)]
        # the big round trunnions the guns swing on
        turret += disc_x(D, sx * 1.05 - (0 if sx > 0 else 0.14), 0.14, 2.8, -0.1, 0.95)
        turret += [B(N, sx * 1.05 - (0 if sx > 0 else 0.1), 2.3, -0.75, 0.1, 0.95, 1.3)]
    # ---- guns: one 35 mm Oerlikon on each side
    barrel, recoil = [], []
    for sx in (1, -1):
        cx = 1.31 * sx
        side = "left" if sx > 0 else "right"
        barrel += [B(N, cx - 0.2, 2.42, -1.05, 0.4, 0.72, 1.95, marks=[(side, "panel", 0.5, 0.5, 1.2, 0.4)]),      # gun housing
                   B(D, cx - 0.23, 2.5, -0.7, 0.46, 0.55, 0.95), B(D, cx - 0.17, 3.14, -0.9, 0.34, 0.06, 1.5, plain=True),
                   B(D, cx - 0.15, 2.3, -0.3, 0.3, 0.14, 0.7), B(STEEL_D, cx - 0.1, 2.22, 0.0, 0.2, 0.1, 0.3, plain=True),   # case ejection chute
                   B(D, cx - 0.14, 2.6, 0.9, 0.28, 0.3, 0.45), B(STEEL_D, cx - 0.2, 2.5, -1.12, 0.4, 0.5, 0.08, plain=True)]
        barrel += rod_z(STEEL_D, cx, 2.74, 1.35, 0.6, 0.2)                                       # recoil sleeve
        recoil += [lathe(STEEL_D, "z", cx, 2.74, [(1.3, 0.0), (1.3, 0.06), (1.95, 0.06), (2.0, 0.055), (3.1, 0.048), (3.1, 0.085), (3.42, 0.085), (3.42, 0.04), (3.3, 0.04)])]
        recoil += rod_z(STEEL, cx, 2.74, 2.4, 0.06, 0.15) + rod_z(STEEL, cx, 2.74, 2.8, 0.06, 0.15)
        recoil += [B(BLACK, cx - 0.1, 2.72, 3.2, 0.2, 0.04, 0.05, plain=True), B(BLACK, cx - 0.1, 2.72, 3.32, 0.2, 0.04, 0.05, plain=True),
                   B(STEEL_D, cx - 0.045, 2.84, 3.05, 0.09, 0.1, 0.4, plain=True),                # muzzle-velocity gauge
                   B(STEEL_D, cx - 0.02, 2.82, 2.0, 0.04, 0.04, 1.05, plain=True)]
    bones.append({"name": "move_recoilLight", "parent": "barrel", "pivot": (0, 2.74, 1.3), "cubes": recoil})
    return dict(base=base, turret=turret, barrel=barrel, bones=bones, track=track)


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
    for sx in (1, -1):                                                              # stabiliser jacks on outrigger beams
        base += jack(sx * 1.3, -1.1, 1.0, RUS_D) + jack(sx * 1.3, -4.85, 1.0, RUS_D)
        base += [B(RUS_D, sx * 1.1 - 0.2, 0.9, -1.2, 0.4, 0.16, 0.2, plain=True), B(RUS_D, sx * 1.1 - 0.2, 0.9, -4.95, 0.4, 0.16, 0.2, plain=True)]
    base += P(RED, 1.0, 0.85, -5.07, 0.2, 0.12, 0.03)
    base += mudguards(RUS_D, 0.85, 0.45, zs, 1.2)
    base += truck_details(RUS, RUS_D, zs, 0.85, 1.2, -5.0, 3.0, 1.25) + axles(zs, 0.85, 1.2)
    turret = [B(RUS, -0.9, 2.2, -2.75, 1.8, 0.5, 2.5),                             # turret ring housing
              tower(RUS, 2.7, [(-0.72, -2.62), (0.72, -2.62), (0.72, -0.9), (0.45, -0.58), (-0.45, -0.58), (-0.72, -0.9)],
                    4.2, [(-0.62, -2.55), (0.62, -2.55), (0.62, -1.0), (0.4, -0.75), (-0.4, -0.75), (-0.62, -1.0)]),   # central tower
              B(RUS_D, -0.75, 4.2, -2.6, 1.5, 0.08, 2.0),
              # phased-array tracking radar on the tower front, leaning back
              B(RUS_D, -0.55, 2.9, -0.7, 1.1, 1.3, 0.25),
              B(BLACK, -0.3, 2.4, -0.3, 0.6, 0.45, 0.25), B(GLASS, -0.22, 2.48, -0.05, 0.44, 0.28, 0.02),   # optical sight
              B(RUS_D, -0.15, 4.28, -2.35, 0.3, 0.2, 0.3)]
    # search radar: a flat panel on top at the back that turns continuously
    radar = rod_y(RUS_D, 0, -2.2, 4.48, 0.15, 0.24) + [
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
    bones = truck_wheels(zs, 0.85, 0.42, 1.2, steer=2)
    bones.append({"name": "move_spinRadar", "parent": "turret", "pivot": (0, 4.5, -2.2), "cubes": radar})
    # ---- fittings
    base += beacon(-0.9, 3.08, 3.6) + whip(1.0, 3.2, 3.0, 2.2) + whip(-1.0, 3.2, 3.0, 1.6)
    base += lamp(WHITE, 0.55, 3.14, 4.55, 0.18) + lamp(WHITE, -0.55, 3.14, 4.55, 0.18) + [B(RUS_D, -0.8, 3.0, 4.45, 1.6, 0.06, 0.08, plain=True)]
    base += shackle(0.7, 1.05, 5.0) + shackle(-0.7, 1.05, 5.0)
    base += handrail(STEEL_D, (1.2, 2.95, 0.7), (1.2, 2.95, 2.7), 0.18) + handrail(STEEL_D, (-1.2, 2.95, 0.7), (-1.2, 2.95, 2.7), 0.18)
    base += ladder(STEEL_D, 1.28, 0.75, 1.3, 2.9, 0.4) + extinguisher(-1.33, 1.6, 2.4) + extinguisher(-1.33, 1.6, 2.2)
    for (x, z) in ((1.1, 0.75), (-1.1, 0.75), (1.1, 2.65), (-1.1, 2.65)):
        base += eye(x, 2.95, z)
    base += [B(RUS_D, -1.26, 1.6, 0.9, 0.03, 1.2, 0.7, marks=[("right", "hatch", 0.5, 0.5, 0.6, 1.1)]),
             B(RUS_D, 1.23, 1.6, 0.9, 0.03, 1.2, 0.7, marks=[("left", "hatch", 0.5, 0.5, 0.6, 1.1)])]
    base += rod_y(STEEL_D, -1.05, 0.75, 2.95, 0.5, 0.12)                                               # generator exhaust
    for sx in (1, -1):
        base += [B(RUS_D, sx * 1.02 - 0.2, 2.2, -4.6, 0.4, 0.3, 0.9, marks=[("top", "panel", 0.5, 0.5, 0.3, 0.7)])]   # tool lockers
        base += chock(sx * 1.5, -2.0) + [eye(sx * 1.15, 1.5, z)[0] for z in (-4.5, -3.0, -1.0)]
        base += run(BLACK, [(sx * 0.6, 1.5, 0.6), (sx * 0.6, 1.9, 0.3), (sx * 0.6, 2.2, -0.3)], 0.05)   # cable looms up to the turret
    base += tools(2.2, -0.6, 0.0)
    turret += handrail(STEEL_D, (0.66, 4.2, -2.5), (0.66, 4.2, -1.1), 0.14) + handrail(STEEL_D, (-0.66, 4.2, -2.5), (-0.66, 4.2, -1.1), 0.14)
    turret += eye(0.5, 4.28, -1.0, along="x") + eye(-0.5, 4.28, -1.0, along="x") + ladder(STEEL_D, -0.2, -2.66, 2.75, 4.1, 0.4, across="x")
    turret += [B(RUS_D, -0.74, 3.0, -2.2, 0.03, 0.9, 0.9, marks=[("right", "hatch", 0.5, 0.5, 0.7, 0.8)]),
               B(RUS_D, 0.71, 3.0, -2.2, 0.03, 0.9, 0.9, marks=[("left", "hatch", 0.5, 0.5, 0.7, 0.8)]),
               B(BLACK, -0.34, 2.86, -0.08, 0.68, 0.05, 0.3, plain=True)]                               # sight hood
    radar += [B(STEEL_D, -0.8, 5.36, -2.22, 1.6, 0.04, 0.04, plain=True)] + [B(STEEL_D, x, 5.36, -2.24, 0.03, 0.14, 0.03, plain=True) for x in (-0.7, -0.35, 0, 0.35, 0.67)]
    for sx in (1, -1):
        barrel += [lathe(STEEL_D, "x", 2.3, -1.2, [(sx * 0.62, 0.0), (sx * 0.62, 0.3), (sx * 0.92, 0.3), (sx * 0.92, 0.0)])]   # ammunition drum
        barrel += run(STEEL_D, [(sx * 0.78, 2.5, -1.5), (sx * 0.8, 2.48, -0.9), (sx * 0.84, 2.46, -0.4)], 0.08)   # feed chute
        for i in range(3):
            for j in range(2):
                cx, cy = sx * (0.98 + i * 0.2), 3.02 + j * 0.42 + (0.1 if i == 1 else 0)
                barrel += rod_z(RUS_D, cx, cy, -3.22, 0.06, 0.225) + rod_z(RUS_D, cx, cy, -1.6, 0.05, 0.215) + rod_z(STEEL_D, cx, cy, -0.06, 0.06, 0.225)
    return dict(base=base, turret=turret, barrel=barrel, bones=bones)


def patriot():
    """M901 Patriot launching station on its semi-trailer. Pivots (0,1.3,-1.0) / (0,1.9,-2.0)."""
    zs = [-3.5, -2.1]
    # trailer: a deep frame with a raised gooseneck at the front, the power unit and its louvred generator
    base = [hull(TAN_D, -1.2, 1.2, [(-4.6, 0.95), (-4.6, 1.25), (2.4, 1.25), (2.6, 1.32), (4.55, 1.32), (4.55, 1.1), (2.7, 1.1), (2.4, 0.95)]),
            hull(TAN_D, -0.5, 0.5, [(-4.4, 0.68), (-4.4, 0.95), (0.8, 0.95), (0.5, 0.68)], plain=True),
            hull(TAN, -1.15, 1.15, [(2.6, 1.32), (2.6, 2.5), (2.7, 2.6), (4.4, 2.6), (4.52, 2.48), (4.52, 1.32)],
                 marks=[("cap0", "panel", 0.5, 0.5, 1.2, 0.8), ("cap1", "panel", 0.5, 0.5, 1.2, 0.8)]),
            B(GRILLE, -0.9, 1.6, 4.5, 1.8, 0.8, 0.04), B(GRILLE, -1.17, 1.7, 3.5, 0.03, 0.6, 0.8), B(GRILLE, 1.14, 1.7, 3.5, 0.03, 0.6, 0.8),
            B(TAN_D, -0.5, 2.6, 3.0, 0.6, 0.12, 0.6, plain=True),                                      # exhaust hood
            B(TAN_D, -0.9, 1.25, -0.2, 1.8, 0.08, 2.6, plain=True)]                                    # walkway
    base += rod_y(STEEL_D, -0.2, 3.3, 2.6, 0.35, 0.12)                                                 # exhaust pipe
    base += rod_y(TAN_D, 0.7, 2.9, 2.6, 2.2, 0.07) + [B(WHITE, 0.62, 4.5, 2.86, 0.16, 0.5, 0.08), B(TAN_D, 0.55, 2.6, 2.8, 0.3, 0.1, 0.2, plain=True)]   # data-link mast
    base += jack(0, 4.2, 1.1, TAN_D)                                                                  # landing leg
    for z in (1.5, -4.3):                                                                              # four outriggers on screw jacks
        for sx in (1, -1):
            base += [hull(TAN_D, min(sx * 1.2, sx * 1.75), max(sx * 1.2, sx * 1.75), [(z - 0.1, 0.95), (z - 0.1, 1.1), (z + 0.1, 1.1), (z + 0.1, 0.95)], plain=True)]
            base += jack(sx * 1.68, z, 0.98, TAN_D)
    base += rod_x(BLACK, -1.3, 0.5, 1.15, 0.9, 0.5) + rod_x(STEEL_D, -1.34, 0.58, 1.15, 0.9, 0.12)     # cable reel
    for i in range(4):                                                                                 # access ladder
        base.append(B(STEEL_D, 1.22, 0.4 + i * 0.22, 2.0, 0.04, 0.03, 0.4, plain=True))
    base += P(RED, 0.8, 1.02, -4.62, 0.25, 0.12, 0.03) + P(AMBER, 0.5, 1.02, -4.62, 0.16, 0.12, 0.03)
    base += mudguards(TAN_D, 0.9, 0.42, zs, 1.1) + axles(zs, 0.9, 1.1, TAN_D)
    # turntable and the two trunnion brackets the launcher tips up on
    turret = [lathe(TAN_D, "y", 0, -1.0, [(1.25, 0.0), (1.25, 0.9), (1.32, 0.95), (1.5, 0.95), (1.5, 0.0)]),
              B(TAN_D, -1.1, 1.3, -2.3, 2.2, 0.2, 2.6)]
    for sx in (1, -1):
        x0, x1 = (0.95, 1.13) if sx > 0 else (-1.13, -0.95)
        turret += [hull(TAN, x0, x1, [(-2.4, 1.5), (-2.3, 2.2), (-1.8, 2.3), (-1.3, 1.5)])]
        turret += rod_x(STEEL_D, x0 - 0.03, 0.24, 1.9, -2.0, 0.3)
    # launcher: a welded frame carrying four ribbed canisters, lifted by two hydraulic rams
    barrel = [B(TAN_D, -1.05, 1.62, -4.1, 2.1, 0.12, 6.1), B(TAN_D, -1.05, 1.7, -4.18, 2.1, 2.05, 0.14),
              B(TAN_D, -0.04, 1.74, -4.0, 0.08, 1.98, 6.0, plain=True), B(TAN_D, -1.05, 2.7, -4.0, 2.1, 0.06, 6.0, plain=True)]
    for sx in (1, -1):
        ram = lathe(STEEL_D, "z", sx * 0.55, 1.48, [(-1.7, 0.0), (-1.7, 0.09), (-0.5, 0.09), (-0.5, 0.05), (0.4, 0.05), (0.4, 0.0)], pitch=16, pivot=(0, 1.5, -1.7))
        barrel.append(ram)
    for sx in (-1.02, 0.04):
        for sy in (1.76, 2.76):
            barrel += [B(TAN, sx, sy, -4.0, 0.98, 0.94, 6.0, marks=[("left", "digits:1 2", 0.2, 0.5, 0), ("right", "digits:3 4", 0.8, 0.5, 0)])]
            for z in (-3.9, -2.6, -1.3, 0.0, 1.3, 1.86):                                               # stiffening frames
                barrel += [B(TAN_D, sx - 0.025, sy - 0.025, z, 1.03, 0.99, 0.09, plain=True)]
            for (dx, dy) in ((0.0, 0.0), (0.9, 0.0), (0.0, 0.86), (0.9, 0.86)):                         # corner posts
                barrel += [B(TAN_D, sx + dx - 0.01, sy + dy - 0.01, -4.0, 0.1, 0.1, 6.0, plain=True)]
            barrel += [B(TAN_D, sx + 0.07, sy + 0.07, 1.98, 0.84, 0.8, 0.04, marks=[("front", "hatch", 0.5, 0.5, 0.7, 0.66)]),   # front cover
                       B(BLACK, sx + 0.07, sy + 0.07, -4.03, 0.84, 0.8, 0.03, plain=True)]             # blow-out rear cover
    # ---- fittings
    base += handrail(STEEL_D, (1.1, 2.6, 2.7), (1.1, 2.6, 4.4), 0.2, 4) + handrail(STEEL_D, (-1.1, 2.6, 2.7), (-1.1, 2.6, 4.4), 0.2, 4)
    for (x, z) in ((1.05, 2.7), (-1.05, 2.7), (1.05, 4.4), (-1.05, 4.4)):
        base += eye(x, 2.6, z)
    base += extinguisher(1.22, 1.32, 2.4) + extinguisher(-1.22, 1.32, 2.4) + chock(1.5, -2.9) + chock(-1.5, -2.9)
    base += [B(TAN_D, -1.2, 0.95, -4.62, 2.4, 0.3, 0.03, marks=[("rear", "hazard", 0.5, 0.75, 2.0, 0.1)])]
    base += run(BLACK, [(-1.0, 1.3, 0.9), (-0.8, 1.3, 0.2), (-0.6, 1.3, -0.4), (-0.3, 1.32, -0.8)], 0.07)      # power cable to the turntable
    base += run(BLACK, [(0.9, 1.34, 2.6), (0.9, 1.3, 1.2), (0.5, 1.3, 0.0), (0.3, 1.32, -0.7)], 0.05)
    base += [B(TAN_D, 0.3, 1.25, 1.0, 0.8, 0.5, 1.2, marks=[("top", "panel", 0.5, 0.5, 0.6, 1.0), ("left", "panel", 0.5, 0.5, 1.0, 0.36)])]   # junction box
    for z in (-4.0, -3.0, -1.4, -0.4, 0.6):
        base += eye(1.12, 1.25, z) + eye(-1.12, 1.25, z)
    base += tools(1.25, -1.3, -0.8)
    turret += run(BLACK, [(0.5, 1.5, -0.4), (0.7, 1.5, -1.6), (0.6, 1.6, -2.2)], 0.06) + [B(TAN_D, -0.9, 1.5, -1.0, 0.5, 0.4, 0.8, marks=[("top", "panel", 0.5, 0.5, 0.36, 0.6)])]
    for sx in (-1.02, 0.04):
        for sy in (1.76, 2.76):
            for z in (-3.2, -0.6, 1.5):                                                                        # canister latches and lifting points
                barrel += [B(STEEL_D, sx + 0.44, sy + 0.94, z, 0.1, 0.03, 0.14, plain=True)]
            barrel += [B(STEEL_D, sx + 0.1, sy + 0.42, 2.02, 0.06, 0.1, 0.03, plain=True), B(STEEL_D, sx + 0.82, sy + 0.42, 2.02, 0.06, 0.1, 0.03, plain=True)]
    barrel += run(BLACK, [(0.0, 1.7, -4.25), (0.0, 2.7, -4.25), (0.0, 3.7, -4.25)], 0.06)
    for x in (-0.99, -0.03, 0.07, 1.03):
        barrel += run(BLACK, [(x * 0.9, 2.72, -4.25), (x * 0.9, 2.72, -4.08)], 0.04)
    return dict(base=base, turret=turret, barrel=barrel, bones=truck_wheels(zs, 0.9, 0.4, 1.1, TAN_D, steer=0))


# ---------------------------------------------------------------- artillery
def himars():
    """M142 HIMARS: armoured FMTV cab, launcher cage with one six-rocket pod. Pivots (0,1.2,-1.5) / (0,1.7,-2.5)."""
    zs = [-2.9, -1.6, 1.8]
    f, h = 1.25, 1.5
    base = [B(US_D, -0.5, 0.7, -3.5, 1.0, 0.45, 6.6),                              # chassis
            B(US, -1.2, 1.1, -3.5, 2.4, 0.12, 4.6),                                # deck
            # angular armoured cab from its side profile: flat roof, steeply raked windscreen, blunt nose
            hull(US, -1.2, 1.2, [(1.3, f), (1.3, f + h - 0.06), (1.4, f + h), (2.9, f + h), (3.0, f + h - 0.03), (3.45, f + 0.82), (3.5, f + 0.72), (3.5, f + 0.1), (3.4, f)],
                 marks=[("cap0", "panel", 0.42, 0.62, 1.0, 0.95), ("cap1", "panel", 0.42, 0.62, 1.0, 0.95)]),
            B(US_D, -1.12, f + h, 1.4, 2.24, 0.05, 1.45, plain=True), B(US_D, -0.35, f + h + 0.05, 1.7, 0.7, 0.06, 0.7, plain=True),
            slope(GLASS, -1.0, 0.9, 3.47, f + 0.9, 3.07, f + h - 0.12, 0.04),
            slope(GLASS, 0.1, 0.9, 3.47, f + 0.9, 3.07, f + h - 0.12, 0.04),
            slope(US_D, -0.06, 0.12, 3.48, f + 0.86, 3.05, f + h - 0.06, 0.05, plain=True),
            B(GRILLE, -0.9, f + 0.1, 3.44, 1.8, 0.55, 0.04),
            B(US_D, -1.25, 0.75, 3.4, 2.5, 0.4, 0.22),                             # bumper
            B(US_D, -1.2, 1.2, 0.6, 2.4, 1.0, 0.65), B(BLACK, -0.9, 1.3, 0.75, 0.5, 0.9, 0.4),   # spare wheel, stowage
            B(STEEL_D, 0.75, 1.3, 0.72, 0.18, 1.3, 0.18)]                          # exhaust stack
    base += P(GLASS, 1.2, f + 0.92, 1.75, 0.02, 0.4, 0.8) + P(US_D, 1.2, f + 0.86, 1.68, 0.04, 0.06, 0.94, plain=True)   # armoured side windows
    base += P(US_D, 1.2, f + 1.32, 1.68, 0.04, 0.06, 0.94, plain=True) + P(STEEL_D, 1.2, f + 0.5, 2.3, 0.05, 0.05, 0.18, plain=True)
    base += truck_details(US, US_D, zs, 0.85, 1.0, -3.5, 1.2, 1.1)
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
    # ---- fittings
    base += round_hatch(-0.5, 2.2, f + h + 0.05, 0.6, US_D) + whip(1.0, 1.5, f + h, 2.4) + whip(-1.0, 1.5, f + h, 1.8)
    base += [periscope(x, f + h + 0.05, 2.75) for x in (-0.7, 0.0, 0.7)][0] + periscope(0.0, f + h + 0.05, 2.75) + periscope(0.7, f + h + 0.05, 2.75)
    base += shackle(0.8, 0.95, 3.62) + shackle(-0.8, 0.95, 3.62)
    base += lamp(WHITE, 0.95, 1.05, 3.62, 0.18) + lamp(WHITE, -0.95, 1.05, 3.62, 0.18) + lamp(AMBER, 0.72, 1.05, 3.62, 0.09) + lamp(AMBER, -0.72, 1.05, 3.62, 0.09)
    base += [B(US_D, -0.3, 0.82, 3.6, 0.6, 0.22, 0.2), B(STEEL_D, -0.22, 0.86, 3.8, 0.44, 0.14, 0.03, plain=True)]                # winch
    base += rod_x(STEEL, -0.2, 0.4, 0.93, 3.72, 0.1)
    for sx in (1, -1):
        base += handrail(STEEL_D, (sx * 1.22, f + 0.5, 1.45), (sx * 1.22, f + 1.3, 1.45), 0.0, 2) + [B(STEEL_D, sx * 1.2 - 0.02, f + 0.2, 2.2, 0.05, 0.06, 0.2, plain=True)]
        base += [bar(STEEL_D, (sx * 1.2, f + 1.0, 2.95), (sx * 1.42, f + 1.1, 3.1), 0.03)]                                        # mirror arms
        base += ladder(STEEL_D, sx * 1.22, 1.55, 0.5, 1.2, 0.4) + chock(sx * 1.45, 0.6)
    base += [B(US, -1.2, 1.2, 0.6, 2.4, 0.5, 0.65, marks=[("left", "panel", 0.5, 0.5, 0.5, 0.36), ("right", "panel", 0.5, 0.5, 0.5, 0.36)])]
    base += spare_wheel(-1.05, 1.7, 0.92, 1.0, US_D) + jerrycan(0.3, 1.7, 0.72) + jerrycan(0.5, 1.7, 0.72) + jerrycan(0.7, 1.7, 0.72)
    base += extinguisher(1.05, 1.7, 0.75) + [bar(STEEL_D, (-0.4, 1.7, 0.7), (-0.4, 2.9, 0.7), 0.05), bar(STEEL_D, (-0.4, 2.9, 0.7), (-0.9, 2.9, 0.9), 0.05)]   # davit for the spare
    base += tools(1.22, -1.2, 0.95)
    for z in (-3.3, -2.2, -0.9, 0.2):
        base += eye(1.12, 1.22, z) + eye(-1.12, 1.22, z)
    turret += [lathe(US_D, "y", 0, -1.5, [(1.2, 0.0), (1.2, 0.95), (1.26, 1.0), (1.4, 1.0), (1.4, 0.0)])]
    for sx in (1, -1):
        x0, x1 = (0.95, 1.15) if sx > 0 else (-1.15, -0.95)
        turret += [hull(US, x0, x1, [(-2.95, 1.4), (-2.85, 2.0), (-2.3, 2.1), (-1.8, 1.4)])] + rod_x(STEEL_D, x0 - 0.03, 0.26, 1.7, -2.5, 0.28)
        barrel.append(lathe(STEEL_D, "z", sx * 0.7, 1.45, [(-2.2, 0.0), (-2.2, 0.08), (-1.2, 0.08), (-1.2, 0.045), (-0.3, 0.045), (-0.3, 0.0)], pitch=10, pivot=(0, 1.45, -2.2)))
        for z in (-2.9, -1.9, -0.9, 0.1):                                                                                        # cage ribs
            barrel += [B(US_D, sx * 1.2 - (0.0 if sx > 0 else 0.05), 1.6, z, 0.05, 1.2, 0.1, plain=True)]
        barrel += [bar(US_D, (sx * 1.2, 1.6, -3.3), (sx * 1.2, 2.8, -2.0), 0.06), bar(US_D, (sx * 1.2, 1.6, 1.1), (sx * 1.2, 2.8, -0.2), 0.06)]
        barrel += eye(sx * 0.9, 2.84, -3.0) + eye(sx * 0.9, 2.84, 0.8)
    barrel += [B(US_D, -1.2, 1.55, -3.44, 2.4, 0.2, 0.05, marks=[("rear", "hazard", 0.5, 0.5, 2.2, 0.14)])]
    barrel += [B(TAN_D, -1.05, 1.7, -3.31, 2.1, 1.0, 0.02, marks=[("rear", "digits:M31 6", 0.5, 0.3, 0)]), B(STEEL_D, -0.06, 2.96, -2.8, 0.12, 0.1, 0.12, plain=True)]
    for i in range(3):
        for j in range(2):                                                                                                       # tube rims
            cx, cy = -0.68 + i * 0.68, 1.96 + j * 0.5
            barrel += [lathe(US_D, "z", cx, cy, [(1.0, 0.24), (1.09, 0.24), (1.09, 0.2)])]
    return dict(base=base, turret=turret, barrel=barrel, bones=truck_wheels(zs, 0.85, 0.38, 1.0, US_D))


def _bomb(x, z):
    """A 120 mm mortar bomb standing on its tail."""
    c = [lathe(OLIVE_D, "y", x, z, [(0.1, 0.0), (0.1, 0.02), (0.3, 0.025), (0.36, 0.05), (0.5, 0.06), (0.62, 0.05), (0.7, 0.025), (0.74, 0.012), (0.74, 0.0)])]
    c += [B(STEEL_D, x - 0.05, 0.0, z - 0.004, 0.1, 0.14, 0.008, plain=True), B(STEEL_D, x - 0.004, 0.0, z - 0.05, 0.008, 0.14, 0.1, plain=True)]
    c += rod_y(BRASS, x, z, 0.7, 0.05, 0.03)
    return c


def mortar_120():
    """120 mm mortar. Pivots (0,.1,-.1) / (0,.22,-.1)."""
    # baseplate: a dished disc with spade ribs underneath and a socket for the ball of the tube
    base = [lathe(STEEL_D, "y", 0, -0.1, [(0.0, 0.0), (0.0, 0.42), (0.03, 0.5), (0.07, 0.5), (0.1, 0.3), (0.13, 0.12), (0.13, 0.0)])]
    for ang in (0, 60, 120):
        base += [B(STEEL_D, -0.5, 0.0, -0.12, 1.0, 0.08, 0.04, yaw=ang, pivot=(0, 0, -0.1), plain=True)]
    base += rod_x(STEEL, -0.36, 0.12, 0.09, -0.1, 0.03) + rod_x(STEEL, 0.24, 0.12, 0.09, -0.1, 0.03)    # carrying handles
    # ammunition: an open crate of bombs and two set out ready
    base += [hull(OLIVE_D, 0.55, 0.95, [(-0.6, 0.0), (-0.6, 0.3), (0.0, 0.3), (0.0, 0.0)], marks=[("cap0", "digits:120", 0.5, 0.5, 0), ("cap1", "digits:120", 0.5, 0.5, 0)]),
             B(OLIVE, 0.55, 0.3, -0.62, 0.4, 0.03, 0.2, pitch=55, pivot=(0, 0.3, -0.6), plain=True),
             B(BLACK, 0.58, 0.28, -0.56, 0.34, 0.02, 0.52, plain=True)]
    base += rod_x(STEEL_D, 0.95, 0.03, 0.2, -0.3, 0.1)
    base += _bomb(-0.7, -0.5) + _bomb(-0.86, -0.34)
    # bipod: two legs with spiked feet, a spreader chain bar, the elevating screw and cross-levelling gear
    turret = []
    for sx in (1, -1):
        turret += [lathe(STEEL, "y", sx * 0.02, 0.64, [(0.0, 0.0), (0.03, 0.03), (0.06, 0.022), (1.02, 0.02), (1.02, 0.0)], roll=-sx * 22, pivot=(sx * 0.02, 1.0, 0.64))]
        turret += rod_y(STEEL_D, sx * 0.4, 0.64, 0.0, 0.03, 0.14)
    turret += rod_x(STEEL_D, -0.24, 0.48, 0.5, 0.64, 0.02) + rod_y(STEEL_D, 0, 0.64, 0.86, 0.3, 0.05) + rod_y(STEEL, 0, 0.64, 0.6, 0.3, 0.03)
    turret += rod_x(STEEL, -0.2, 0.4, 0.96, 0.62, 0.035) + rod_x(BLACK, 0.2, 0.05, 0.96, 0.62, 0.08) + rod_z(BLACK, 0.0, 0.76, 0.66, 0.05, 0.08)
    turret += [B(STEEL_D, -0.08, 0.9, 0.56, 0.16, 0.12, 0.14, plain=True)]
    # tube: ball breech, reinforced base, long barrel with a flared muzzle, and the clamp the bipod grips
    barrel = [lathe(OLIVE_D, "z", 0, 0.22, [(-0.22, 0.0), (-0.2, 0.05), (-0.14, 0.07), (-0.1, 0.11), (0.1, 0.11), (0.14, 0.085), (1.48, 0.085),
                                            (1.5, 0.1), (1.6, 0.1), (1.6, 0.068), (1.3, 0.066)])]
    barrel += rod_z(STEEL_D, 0, 0.22, 0.7, 0.14, 0.2) + rod_z(STEEL_D, 0, 0.22, 0.2, 0.04, 0.19)
    barrel += [B(STEEL_D, 0.09, 0.22, 0.42, 0.05, 0.05, 0.2, plain=True), B(BLACK, 0.12, 0.26, 0.44, 0.1, 0.16, 0.1), B(GLASS, 0.14, 0.42, 0.47, 0.06, 0.04, 0.04)]
    barrel += rod_y(BLACK, 0.17, 0.49, 0.42, 0.08, 0.03)
    # ---- fittings: aiming stakes, cleaning rod, sight case, a third bomb and its packing tube
    base += [lathe(WHITE, "y", 0.4, 2.0, [(0.0, 0.0), (0.0, 0.012), (1.1, 0.012), (1.1, 0.0)]), lathe(RED, "y", 0.4, 2.0, [(0.75, 0.0125), (0.9, 0.0125)]),
             lathe(WHITE, "y", 0.44, 2.9, [(0.0, 0.0), (0.0, 0.012), (1.1, 0.012), (1.1, 0.0)]), lathe(RED, "y", 0.44, 2.9, [(0.75, 0.0125), (0.9, 0.0125)])]
    base += rod_z(STEEL, -0.55, 0.02, 0.2, 1.5, 0.025) + rod_z(BLACK, -0.55, 0.02, 1.7, 0.1, 0.08)
    base += [hull(OLIVE_D, -1.0, -0.72, [(0.3, 0.0), (0.3, 0.16), (0.34, 0.2), (0.6, 0.2), (0.64, 0.16), (0.64, 0.0)], plain=True)]
    base += rod_z(OLIVE, -0.3, 0.07, -0.9, 0.85, 0.14) + rod_z(BLACK, -0.3, 0.07, -0.92, 0.03, 0.15) + _bomb(-0.55, -0.62)
    barrel += [B(BLACK, -0.06, 0.14, 1.05, 0.12, 0.02, 0.2, plain=True)] + rod_x(STEEL, -0.12, 0.24, 0.22, 0.34, 0.025)
    return dict(base=base, turret=turret, barrel=barrel, bones=[])


def m109():
    """M109A6 Paladin. Pivots (0,1.7,-.9) / (0,2.3,.9); the muzzle is 5.2 ahead of the barrel pivot."""
    gear, bones, track = running_gear(1.05, 0.5, -3.0, 3.0, 7, 0.62, US_D)
    base = gear + [
        hull(US_D, -1.05, 1.05, [(-2.9, 0.42), (-3.12, 0.95), (3.22, 0.95), (2.72, 0.42)], plain=True),
        # upper hull: flat deck, a long glacis running down to a blunt nose, vertical tail
        hull(US, -1.57, 1.57, [(-3.14, 0.95), (-3.14, 1.66), (-3.08, 1.7), (2.2, 1.7), (3.26, 1.06), (3.26, 0.95)],
             marks=[("cap0", "panel", 0.4, 0.45, 1.0, 0.36), ("cap1", "panel", 0.4, 0.45, 1.0, 0.36)]),
        B(GRILLE, 0.2, 1.7, 1.1, 1.2, 0.03, 0.9),                                  # engine louvres, front right
        B(US_D, -1.3, 1.7, 1.3, 0.7, 0.12, 0.6),                                   # driver's hatch
        B(US_D, -1.55, 1.0, -3.2, 3.1, 0.65, 0.12),
        B(US_D, -0.45, 1.05, -3.3, 0.9, 0.55, 0.1)]                                # rear door
    base += P(US_D, 1.55, 0.72, -3.0, 0.05, 0.3, 6.0)
    base += P(WHITE, 1.1, 1.35, 2.95, 0.2, 0.14, 0.04) + P(US_D, 1.2, 0.5, -3.35, 0.3, 0.5, 0.3)   # lights, spades
    # travel lock for the barrel on the glacis
    base += [B(US_D, -0.3, 1.5, 2.6, 0.08, 0.5, 0.08), B(US_D, 0.22, 1.5, 2.6, 0.08, 0.5, 0.08)]
    turret = [
        # the big welded turret: sides lean in, the front narrows and leans back towards the gun
        tower(US, 1.7, [(-1.47, -3.0), (1.47, -3.0), (1.47, -0.1), (1.0, 0.95), (-1.0, 0.95), (-1.47, -0.1)],
              2.9, [(-1.3, -2.92), (1.3, -2.92), (1.3, -0.45), (0.86, 0.3), (-0.86, 0.3), (-1.3, -0.45)],
              marks=[("s1", "panel", 0.5, 0.5, 1.5, 0.6), ("s5", "panel", 0.5, 0.5, 1.5, 0.6), ("s0", "hatch", 0.5, 0.55, 0.9, 0.8)]),
        B(US_D, -1.28, 2.9, -2.9, 2.56, 0.04, 2.5, plain=True),
        B(US_D, -1.5, 1.85, -3.75, 3.0, 0.75, 0.75),                               # bustle rack
        B(GRILLE, -1.4, 2.6, -3.7, 2.8, 0.03, 0.65),
        B(STEEL_D, 1.3, 2.96, -2.7, 0.05, 1.5, 0.05),                              # aerial
        B(US_D, -1.22, 2.94, -0.3, 0.5, 0.06, 0.4, plain=True)]
    # .50 cal on the cupola
    turret += [B(BLACK, 0.8, 3.22, -1.9, 0.12, 0.14, 0.7)] + rod_z(BLACK, 0.86, 3.3, -1.2, 0.9, 0.05)
    barrel = [B(US_D, -0.5, 1.95, 0.5, 1.0, 0.7, 0.75),                            # mantlet
              B(US, -0.35, 2.05, 1.2, 0.7, 0.5, 0.5)]
    barrel += rod_z(US_D, 0, 2.3, 1.7, 0.9, 0.36)                                   # recoil sleeve
    # the tube slides back through the sleeve when the gun fires
    recoil = [lathe(US, "z", 0, 2.3, [(0.9, 0.0), (0.9, 0.12), (3.6, 0.115), (3.62, 0.19), (4.4, 0.19), (4.42, 0.11), (5.5, 0.1),
                                      (5.52, 0.2), (5.62, 0.21), (6.08, 0.21), (6.1, 0.19), (6.1, 0.08), (5.9, 0.08)])]
    recoil += [B(BLACK, -0.23, 2.22, 5.68, 0.46, 0.16, 0.1, plain=True), B(BLACK, -0.23, 2.22, 5.9, 0.46, 0.16, 0.1, plain=True)]
    bones.append({"name": "move_recoilHeavy", "parent": "barrel", "pivot": (0, 2.3, 1.7), "cubes": recoil})
    # ---- fittings
    base += lamp(WHITE, 1.2, 1.36, 3.0, 0.16) + lamp(WHITE, -1.2, 1.36, 3.0, 0.16) + lamp(AMBER, 1.0, 1.36, 3.02, 0.08) + lamp(AMBER, -1.0, 1.36, 3.02, 0.08)
    for sx in (1, -1):
        base += [bar(US_D, (sx * 1.32, 1.25, 3.1), (sx * 1.32, 1.5, 3.14), 0.025), bar(US_D, (sx * 1.08, 1.25, 3.1), (sx * 1.08, 1.5, 3.14), 0.025),
                 bar(US_D, (sx * 1.32, 1.5, 3.14), (sx * 1.08, 1.5, 3.14), 0.025)]                       # lamp guards
        base += shackle(sx * 0.7, 0.75, 3.2) + [B(BLACK, sx * 1.57 - 0.02, 0.7, 2.9, 0.04, 0.4, 0.36, plain=True)]
        base += eye(sx * 1.45, 1.7, 1.9) + eye(sx * 1.45, 1.7, -2.9)
        # recoil spades folded up on the tail
        x0, x1 = (0.95, 1.35) if sx > 0 else (-1.35, -0.95)
        base += [hull(US_D, x0, x1, [(-3.2, 0.5), (-3.2, 1.0), (-3.34, 1.05), (-3.62, 0.72), (-3.5, 0.45)])]
        base += lamp(RED, sx * 1.35, 1.5, -3.2, 0.12, -1)
    base += [slope(TRACK, -0.7 + i * 0.24, 0.2, 2.5, 1.56, 2.85, 1.35, 0.05, plain=True) for i in range(6)]   # spare track links
    base += periscope(-1.15, 1.82, 1.9) + periscope(-0.92, 1.82, 1.92) + periscope(-0.69, 1.82, 1.9)
    base += tools(1.7, -0.3, -1.25) + run(BLACK, [(1.45, 1.72, 1.8), (1.45, 1.72, -0.4), (1.3, 1.72, -0.7)], 0.05)   # tow cable
    base += rod_z(STEEL_D, 1.42, 1.82, 0.4, 1.0, 0.14) + [B(GRILLE, 1.3, 1.7, 0.2, 0.25, 0.03, 0.3)]     # exhaust and heat shield
    base += [B(US_D, 1.35, 1.72, 0.35, 0.16, 0.04, 1.1, plain=True)] + rod_y(STEEL_D, 0.9, -2.8, 1.7, 0.05, 0.14) + rod_y(STEEL_D, -0.9, -2.8, 1.7, 0.05, 0.14)
    base += [B(STEEL_D, -0.5, 1.3, -3.33, 0.08, 0.2, 0.04, plain=True), B(STEEL_D, 0.42, 1.3, -3.33, 0.08, 0.2, 0.04, plain=True), B(STEEL, 0.2, 1.3, -3.42, 0.1, 0.04, 0.03, plain=True)]
    base += extinguisher(-1.5, 1.7, -2.6) + jerrycan(1.2, 1.7, -3.05) + jerrycan(1.0, 1.7, -3.05)
    # turret roof: turned cupola with a ring of vision blocks, loader's hatch, sight hood, aerials, hooks
    turret += round_hatch(0.87, -1.52, 2.94, 0.8, US_D) + round_hatch(-0.75, -0.85, 2.94, 0.62, US_D)
    for k in range(6):
        a = math.radians(k * 60 + 30)
        turret += [B(BLACK, 0.87 + 0.44 * math.cos(a) - 0.06, 2.96, -1.52 + 0.44 * math.sin(a) - 0.06, 0.12, 0.09, 0.12, yaw=-k * 60 - 30, plain=True)]
    turret += [hull(US_D, -1.2, -0.7, [(-0.35, 2.94), (-0.35, 3.12), (-0.05, 3.16), (0.1, 3.05), (0.1, 2.94)]), B(GLASS, -1.12, 2.98, 0.1, 0.34, 0.1, 0.01)]
    turret += whip(1.2, -2.7, 2.94, 2.6) + whip(-1.2, -2.7, 2.94, 2.0)
    for (x, z) in ((1.2, -0.6), (-1.2, -0.6), (1.2, -2.8), (-1.2, -2.8)):
        turret += eye(x, 2.94, z)
    for sx in (1, -1):
        turret += handrail(STEEL_D, (sx * 1.42, 2.35, -2.6), (sx * 1.38, 2.35, -0.8), 0.0, 2) + [B(STEEL_D, sx * 1.47 - 0.02, 2.0, -1.2, 0.04, 0.3, 0.05, plain=True)]
    # machine gun on a pintle with its ammunition box
    turret += rod_y(STEEL_D, 0.87, -1.12, 3.05, 0.2, 0.06) + [B(BLACK, 0.81, 3.24, -1.5, 0.12, 0.13, 0.62, plain=True), B(US_D, 0.62, 3.2, -1.25, 0.18, 0.16, 0.26, plain=True)]
    turret += rod_z(BLACK, 0.87, 3.3, -0.88, 0.9, 0.035) + rod_z(BLACK, 0.87, 3.3, -0.2, 0.2, 0.06) + [B(BLACK, 0.84, 3.2, -1.7, 0.06, 0.1, 0.2, plain=True)]
    # bustle rack: an open frame of rails holding rolled tarpaulins, a camouflage net and cans
    turret += handrail(STEEL_D, (-1.5, 2.6, -3.75), (1.5, 2.6, -3.75), 0.35, 5) + handrail(STEEL_D, (-1.5, 2.6, -3.7), (-1.5, 2.6, -3.05), 0.35, 2)
    turret += handrail(STEEL_D, (1.5, 2.6, -3.7), (1.5, 2.6, -3.05), 0.35, 2)
    turret += bedroll(-1.35, -0.2, 2.76, -3.4) + bedroll(0.1, 1.3, 2.74, -3.45, 0.24, US_D) + bedroll(-1.2, 0.6, 2.78, -3.15, 0.2)
    turret += jerrycan(0.75, 2.62, -3.3) + jerrycan(0.95, 2.62, -3.3)
    barrel += [bar(US_D, (-0.3, 2.2, 0.9), (-0.3, 2.2, 1.6), 0.07), bar(US_D, (0.3, 2.2, 0.9), (0.3, 2.2, 1.6), 0.07)]   # recoil cylinders
    barrel += eye(0.0, 2.65, 0.8, along="x") + [B(CANVAS, -0.48, 1.97, 0.46, 0.96, 0.66, 0.06, plain=True)]            # mantlet dust cover
    return dict(base=base, turret=turret, barrel=barrel, bones=bones, track=track)


# ---------------------------------------------------------------- missile launchers
def tomahawk_launcher():
    """Trailer launcher: a container that tips up to vertical about its rear end. Pivot (0,1.2,-3.9)."""
    zs = [-3.7, -2.5]
    # trailer: a low bed with a raised gooseneck, the power unit on it, landing legs and rear stabilisers
    base = [hull(US_D, -1.2, 1.2, [(-4.5, 0.9), (-4.5, 1.2), (3.3, 1.2), (3.5, 1.3), (4.5, 1.3), (4.5, 1.08), (3.55, 1.08), (3.3, 0.9)]),
            hull(US_D, -0.45, 0.45, [(-4.4, 0.62), (-4.4, 0.9), (1.8, 0.9), (1.5, 0.62)], plain=True),
            hull(US, -1.1, 1.1, [(3.5, 1.3), (3.5, 2.3), (3.6, 2.4), (4.36, 2.4), (4.46, 2.3), (4.46, 1.3)],
                 marks=[("cap0", "panel", 0.5, 0.5, 0.6, 0.7), ("cap1", "panel", 0.5, 0.5, 0.6, 0.7)]),
            B(GRILLE, -0.8, 1.5, 4.45, 1.6, 0.7, 0.04), B(US_D, -0.5, 2.4, 3.7, 0.5, 0.1, 0.5, plain=True),
            B(STEEL_D, -1.2, 0.95, -4.56, 2.4, 0.2, 0.08, marks=[("rear", "hazard", 0.5, 0.5, 2.0, 0.12)])]
    base += rod_y(STEEL_D, -0.25, 3.95, 2.4, 0.4, 0.1)                                                 # exhaust
    base += rod_y(STEEL_D, 0, 4.2, 0.78, 0.3, 0.16)                                                    # kingpin
    for sx in (1, -1):
        base += jack(sx * 0.9, 3.1, 1.08, US_D) + jack(sx * 1.28, -4.3, 0.9, US_D)
        base += [B(US_D, sx * 1.2 - (0.0 if sx > 0 else 0.16), 0.75, -4.4, 0.16, 0.16, 0.2, plain=True)]
        base += rod_z(STEEL, sx * 0.95, 0.72, 0.2, 0.9, 0.24)                                           # air tanks
        base += [B(US_D, sx * 0.95 - 0.25, 0.42, -1.2, 0.5, 0.46, 0.9, marks=[("left" if sx > 0 else "right", "panel", 0.5, 0.5, 0.7, 0.3)])]   # stowage lockers
    base += P(RED, 0.8, 1.0, -4.6, 0.25, 0.12, 0.03) + P(AMBER, 0.5, 1.0, -4.6, 0.16, 0.12, 0.03)
    base += mudguards(US_D, 0.85, 0.4, zs, 1.0) + axles(zs, 0.85, 1.0)
    # the container: corrugated walls between a frame of corner posts and rails, doors with locking bars at the
    # tail, and four armoured cell hatches at the end that points skywards
    barrel = [B(US, -1.06, 1.24, -3.86, 2.12, 1.82, 7.22, plain=True)]
    for (dx, dy) in ((-1.1, 1.2), (0.98, 1.2), (-1.1, 2.98), (0.98, 2.98)):                             # corner posts and castings
        barrel += [B(US_D, dx, dy, -3.9, 0.12, 0.12, 7.3, plain=True),
                   B(STEEL_D, dx - 0.01, dy - 0.01, -3.92, 0.14, 0.14, 0.18, plain=True), B(STEEL_D, dx - 0.01, dy - 0.01, 3.24, 0.14, 0.14, 0.18, plain=True)]
    for z in (-3.9, 3.28):
        barrel += [B(US_D, -1.1, 1.2, z, 2.2, 0.12, 0.12, plain=True), B(US_D, -1.1, 2.98, z, 2.2, 0.12, 0.12, plain=True)]
        barrel += P(US_D, 0.98, 1.2, z, 0.12, 1.9, 0.12, plain=True)
    n = 30
    for i in range(n):                                                                                 # corrugation on both walls and the roof
        z = -3.72 + i * (6.9 / (n - 1))
        barrel += P(US, 1.06, 1.34, z - 0.045, 0.035, 1.62, 0.09, plain=True)
        barrel += [B(US, -0.96, 3.06, z - 0.045, 1.92, 0.035, 0.09, plain=True)]
    for sx in (1, -1):                                                                                 # tail doors
        barrel += [B(US_D, sx * 0.52 - 0.5, 1.34, -3.9, 1.0, 1.62, 0.04, marks=[("rear", "panel", 0.5, 0.5, 0.86, 1.4)])]
        for off in (0.25, 0.42):
            barrel += rod_y(STEEL, sx * off, -3.93, 1.3, 1.7, 0.03)
            barrel += [B(STEEL_D, sx * off - 0.05, 2.0, -3.96, 0.1, 0.04, 0.03, plain=True)]
    for i in range(2):
        for j in range(2):                                                                             # four cell hatches
            cx, cy = -0.5 + i * 1.0, 1.7 + j * 0.86
            barrel += [B(STEEL_D, cx - 0.44, cy - 0.38, 3.38, 0.88, 0.76, 0.06, marks=[("front", "hatch", 0.5, 0.5, 0.74, 0.62)]),
                       B(STEEL, cx - 0.4, cy + 0.34, 3.44, 0.2, 0.06, 0.04, plain=True), B(STEEL, cx + 0.2, cy + 0.34, 3.44, 0.2, 0.06, 0.04, plain=True)]
    for sx in (1, -1):                                                                                 # erecting rams
        barrel.append(lathe(STEEL_D, "z", sx * 0.72, 1.3, [(-2.9, 0.0), (-2.9, 0.09), (-1.4, 0.09), (-1.4, 0.05), (-0.2, 0.05), (-0.2, 0.0)], pitch=-10, pivot=(0, 1.3, -1.6)))
    # ---- fittings
    base += beacon(0.6, 2.4, 3.9) + extinguisher(1.15, 1.3, 3.3) + extinguisher(-1.15, 1.3, 3.3)
    base += handrail(STEEL_D, (1.05, 2.4, 3.6), (1.05, 2.4, 4.4), 0.2, 3) + handrail(STEEL_D, (-1.05, 2.4, 3.6), (-1.05, 2.4, 4.4), 0.2, 3)
    base += ladder(STEEL_D, 1.22, 2.6, 0.3, 1.2, 0.4) + chock(1.45, -3.1) + chock(-1.45, -3.1)
    base += run(BLACK, [(-0.6, 1.3, 3.5), (-0.6, 1.22, 2.0), (-0.4, 1.22, -1.0), (-0.3, 1.22, -3.6)], 0.07)      # hydraulic and power lines
    base += run(STEEL, [(0.6, 1.3, 3.5), (0.6, 1.22, 1.0), (0.72, 1.25, -1.6)], 0.04)
    for z in (-4.2, -2.9, -1.0, 0.6, 2.4):
        base += eye(1.12, 1.2, z) + eye(-1.12, 1.2, z)
    base += tools(1.2, 0.2, 0.0)
    barrel += [B(US, 1.1, 1.9, -3.5, 0.02, 0.5, 0.9, marks=[("left", "digits:MK70-01", 0.5, 0.35, 0), ("left", "panel", 0.5, 0.65, 0.5, 0.16)]),
               B(US, -1.12, 1.9, -3.5, 0.02, 0.5, 0.9, marks=[("right", "digits:MK70-01", 0.5, 0.35, 0), ("right", "panel", 0.5, 0.65, 0.5, 0.16)])]
    for z in (-3.0, -0.3, 2.4):
        barrel += eye(0.9, 3.1, z) + eye(-0.9, 3.1, z)
    barrel += ladder(STEEL_D, -1.16, -0.2, 1.3, 3.0, 0.4)
    barrel += [B(STEEL_D, -1.05, 1.3, 3.36, 2.1, 0.06, 0.06, plain=True), B(STEEL_D, -1.05, 2.56, 3.36, 2.1, 0.04, 0.06, plain=True), B(STEEL_D, -0.02, 1.3, 3.36, 0.04, 1.7, 0.06, plain=True)]
    return dict(base=base, turret=[], barrel=barrel, bones=truck_wheels(zs, 0.85, 0.38, 1.0, US_D, steer=0))


def iskander_launcher():
    """9P78-1 Iskander launcher on its 8x8 chassis; the missile and its cradle tip up about (0,2.25,-5.5)."""
    zs = [-5.5, -3.9, 1.7, 3.3]
    base = [B(RUS_D, -0.7, 0.8, -6.4, 1.4, 0.6, 12.6),                             # chassis
            B(RUS, -1.5, 1.4, -6.5, 3.0, 0.8, 10.7),                               # lower body
            # wide, low cab right at the front, from its side profile
            hull(RUS, -1.5, 1.5, [(4.2, 1.4), (4.2, 3.05), (4.3, 3.12), (5.95, 3.12), (6.08, 3.06), (6.5, 2.32), (6.52, 2.2), (6.52, 1.5), (6.42, 1.4)],
                 marks=[("cap0", "panel", 0.4, 0.6, 1.0, 1.0), ("cap1", "panel", 0.4, 0.6, 1.0, 1.0)]),
            B(RUS_D, -1.4, 3.12, 4.35, 2.8, 0.05, 1.5, plain=True),
            slope(GLASS, -1.3, 1.15, 6.5, 2.4, 6.14, 3.0, 0.04),
            slope(GLASS, 0.15, 1.15, 6.5, 2.4, 6.14, 3.0, 0.04),
            slope(RUS_D, -0.08, 0.16, 6.51, 2.36, 6.12, 3.04, 0.05, plain=True),
            B(RUS_D, -1.52, 1.0, 6.42, 3.04, 0.45, 0.16),                          # bumper
            B(GRILLE, -1.3, 2.4, 3.5, 2.6, 0.75, 0.6),                             # engine bay behind the cab
            B(RUS, -1.5, 2.2, 3.4, 3.0, 0.2, 0.75),
            B(RUS_D, -1.5, 2.2, -6.5, 3.0, 0.9, 0.3)]                              # rear wall
    base += P(RUS, 1.2, 2.2, -6.2, 0.3, 0.9, 9.6)                                   # side walls of the missile bay
    base += P(GLASS, 1.49, 2.4, 4.7, 0.04, 0.5, 1.1) + P(WHITE, 1.0, 1.1, 6.59, 0.25, 0.16, 0.03)
    base += P(BLACK, 1.52, 2.5, 5.9, 0.22, 0.36, 0.05) + P(RUS_D, 0.9, 0.9, -1.0, 0.6, 0.5, 1.6)   # mirrors, jack housings
    base += mudguards(RUS_D, 1.0, 0.52, zs, 1.3)
    base += truck_details(RUS, RUS_D, zs, 1.0, 1.3, -6.5, 4.0, 1.4)
    barrel = [B(STEEL_D, -0.6, 2.2, -5.5, 1.2, 0.1, 5.4),                          # erector cradle
              B(STEEL_D, -0.65, 2.2, -5.5, 0.1, 0.5, 5.4), B(STEEL_D, 0.55, 2.2, -5.5, 0.1, 0.5, 5.4),
              B(STEEL_D, -0.6, 2.25, -1.2, 1.2, 0.6, 0.12), B(STEEL_D, -0.6, 2.25, -3.6, 1.2, 0.6, 0.12)]
    barrel += [lathe(RUS, "z", 0, 2.78, [(-5.62, 0.0), (-5.62, 0.33), (-5.5, 0.4), (-5.3, 0.46), (-0.6, 0.46), (0.6, 0.38), (1.6, 0.2),
                                         (2.05, 0.07), (2.15, 0.0)])]                    # the missile
    barrel += rod_z(RUS_D, 0, 2.78, -2.2, 0.1, 0.94) + rod_z(RUS_D, 0, 2.78, -0.62, 0.06, 0.93)
    barrel += [B(RUS_D, -0.8, 2.75, -5.3, 1.6, 0.06, 0.6), B(RUS_D, -0.03, 2.0, -5.3, 0.06, 1.56, 0.6)]   # tail fins
    # ---- fittings
    base += round_hatch(0.8, 5.0, 3.17, 0.62, RUS_D) + round_hatch(-0.8, 5.0, 3.17, 0.62, RUS_D) + beacon(0.0, 3.17, 4.6)
    base += whip(1.3, 4.4, 3.12, 2.4) + whip(-1.3, 4.4, 3.12, 1.8)
    base += [periscope(x, 3.17, 5.7) for x in (-0.8,)][0] + periscope(0.8, 3.17, 5.7)
    for sx in (1, -1):
        base += lamp(WHITE, sx * 1.1, 1.72, 6.52, 0.2) + lamp(WHITE, sx * 0.8, 1.72, 6.52, 0.16) + lamp(AMBER, sx * 1.35, 1.72, 6.52, 0.1)
        base += [B(RUS_D, sx * 1.1 - 0.28, 1.58, 6.6, 0.56, 0.03, 0.02, plain=True), B(RUS_D, sx * 1.1 - 0.28, 1.84, 6.6, 0.56, 0.03, 0.02, plain=True)]
        base += shackle(sx * 0.9, 1.2, 6.58) + ladder(STEEL_D, sx * 1.53, 4.6, 0.6, 1.5, 0.45)
        base += [B(STEEL_D, sx * 1.5 - 0.02, 2.0, 5.3, 0.05, 0.06, 0.18, plain=True)] + handrail(STEEL_D, (sx * 1.53, 2.0, 4.35), (sx * 1.53, 2.9, 4.35), 0.0, 2)
        base += [bar(STEEL_D, (sx * 1.5, 2.6, 5.9), (sx * 1.72, 2.7, 6.0), 0.03)]
        # lockers and access panels along the body, door hinges and the rams that hold the roof doors open
        for i, z in enumerate((-5.8, -4.4, -2.9, -0.6, 0.9, 2.4)):
            base += [B(RUS_D, sx * 1.5 - (0.0 if sx > 0 else 0.03), 1.5, z, 0.03, 0.6, 1.1, marks=[("left" if sx > 0 else "right", "hatch", 0.5, 0.5, 0.95, 0.5)])]
        for z in (-5.6, -3.2, -0.8, 1.6, 3.0):
            base += rod_z(STEEL_D, sx * 1.5, 3.1, z, 0.3, 0.06)
        base += jack(sx * 1.42, -1.1, 1.0, RUS_D) + jack(sx * 1.42, -6.2, 1.0, RUS_D) + chock(sx * 1.75, 0.4)
        base += lamp(RED, sx * 1.25, 1.75, -6.5, 0.14, -1) + lamp(AMBER, sx * 1.0, 1.75, -6.5, 0.12, -1)
        base += [B(RUS, sx * 1.35 - 0.15, 3.1, -6.2, 0.3, 0.16, 9.6, plain=True)]                              # coaming the doors shut onto
    base += [B(RUS_D, -1.5, 1.5, -6.52, 3.0, 0.5, 0.03, marks=[("rear", "hazard", 0.5, 0.2, 2.6, 0.12), ("rear", "plate", 0.5, 0.7, 0.5, 0.12)])]
    base += rod_y(STEEL_D, 1.2, 3.8, 3.15, 0.5, 0.14) + rod_y(STEEL_D, -1.2, 3.8, 3.15, 0.5, 0.14)        # exhaust stacks
    base += extinguisher(1.56, 1.5, 4.0) + extinguisher(-1.56, 1.5, 4.0) + spare_wheel(-0.2, 2.42, 3.75, 1.2, RUS_D, lying=True)
    base += run(BLACK, [(0.9, 2.2, -6.0), (0.9, 2.2, -2.0), (0.9, 2.2, 3.0)], 0.06) + run(BLACK, [(-0.9, 2.2, -6.0), (-0.9, 2.2, 3.0)], 0.05)   # cable trays in the bay
    for z in (-4.6, -2.4, -0.2):                                                                           # cradle cross-braces and rollers
        barrel += [bar(STEEL_D, (-0.6, 2.25, z), (0.6, 2.25, z + 0.9), 0.05), bar(STEEL_D, (0.6, 2.25, z), (-0.6, 2.25, z + 0.9), 0.05)]
        barrel += rod_x(STEEL, -0.45, 0.9, 2.3, z + 0.45, 0.1)
    for sx in (1, -1):
        barrel.append(lathe(STEEL_D, "z", sx * 0.5, 2.1, [(-4.6, 0.0), (-4.6, 0.09), (-3.2, 0.09), (-3.2, 0.05), (-2.0, 0.05), (-2.0, 0.0)], pitch=-8, pivot=(0, 2.1, -3.3)))
    barrel += [B(RUS_D, -0.02, 3.22, -2.0, 0.04, 0.04, 2.6, plain=True)]                                   # cable duct along the missile
    # roof doors over the missile bay: shut for the road, swung up and out when the missile is raised
    bones = truck_wheels(zs, 1.0, 0.5, 1.3, RUS_D, steer=2)
    for name, sx in (("move_doorL", 1), ("move_doorR", -1)):
        x0 = 0.02 if sx > 0 else -1.5
        door = [B(RUS_D, x0, 3.26, -6.2, 1.48, 0.06, 9.6, marks=[("top", "panel", 0.5, 0.25, 1.2, 2.0), ("top", "panel", 0.5, 0.75, 1.2, 2.0)])]
        door += [B(RUS_D, x0 + 0.1, 3.32, z, 1.28, 0.03, 0.08, plain=True) for z in (-5.0, -2.6, -0.2, 2.2)]
        bones.append({"name": name, "parent": "move_body", "pivot": (sx * 1.5, 3.26, 0), "cubes": door})
    return dict(base=base, turret=[], barrel=barrel, bones=bones)


# ---------------------------------------------------------------- electronic warfare
def jammer():
    """Field EW station: an equipment shelter on jacks with a tall telescopic aerial mast."""
    # shelter with chamfered roof edges, skids, a door with a porthole, air conditioner and cable entry
    base = [hull(US, -1.4, 1.4, [(-1.4, 0.35), (-1.4, 2.4), (-1.25, 2.55), (1.25, 2.55), (1.4, 2.4), (1.4, 0.35)],
                 marks=[("cap1", "panel", 0.3, 0.55, 0.9, 1.4), ("cap0", "vent", 0.72, 0.4, 0.8, 0.5), ("s0", "hatch", 0.5, 0.52, 0.9, 1.8)]),
            B(US_D, -1.3, 2.55, -1.3, 2.6, 0.04, 2.6, plain=True)]
    base += P(US_D, 1.0, 0.2, -1.5, 0.2, 0.15, 3.0, plain=True)                                         # skids
    base += rod_z(GLASS, 0.0, 1.9, -1.43, 0.03, 0.24) + [B(STEEL, 0.3, 1.3, -1.44, 0.06, 0.18, 0.04, plain=True)]
    base += [hull(STEEL_D, -1.72, -1.4, [(-0.6, 1.5), (-0.6, 2.2), (-0.5, 2.3), (0.5, 2.3), (0.6, 2.2), (0.6, 1.5)]),   # air conditioner
             B(GRILLE, -1.74, 1.6, -0.45, 0.03, 0.55, 0.9), B(BLACK, 1.4, 0.6, 0.2, 0.05, 0.9, 0.9, plain=True)]
    for i in range(5):
        base += rod_x(STEEL, 1.44, 0.05, 0.75 + i * 0.16, 0.65, 0.07)                                   # cable sockets
    base += [hull(STEEL_D, 0.9, 1.5, [(-2.15, 0.0), (-2.15, 0.45), (-2.05, 0.52), (-1.65, 0.52), (-1.6, 0.45), (-1.6, 0.0)]),   # generator
             B(GRILLE, 0.95, 0.1, -2.17, 0.5, 0.3, 0.03)]
    base += rod_y(STEEL_D, 1.2, -1.85, 0.52, 0.5, 0.06)
    base += rod_x(BLACK, -1.2, 0.5, 0.3, -1.9, 0.5) + rod_x(STEEL_D, -1.24, 0.58, 0.3, -1.9, 0.1)       # cable drum
    for (x, z) in ((1.25, 1.25), (-1.25, 1.25), (1.25, -1.25), (-1.25, -1.25)):
        base += jack(x * 1.12, z * 1.12, 0.4, US_D)
    for i in range(6):                                                                                 # roof ladder
        base.append(B(STEEL_D, -1.0, 0.5 + i * 0.32, 1.4, 0.4, 0.03, 0.04, plain=True))
    base += [B(STEEL_D, -1.02, 0.4, 1.4, 0.04, 2.2, 0.04, plain=True), B(STEEL_D, -0.62, 0.4, 1.4, 0.04, 2.2, 0.04, plain=True)]
    # mast: a base turret and four telescoping sections with collars, then the aerial head
    base += [lathe(STEEL_D, "y", 0, 0, [(2.55, 0.0), (2.55, 0.3), (2.8, 0.26), (2.8, 0.17), (4.6, 0.17), (4.6, 0.2), (4.7, 0.2), (4.7, 0.13),
                                         (6.3, 0.13), (6.3, 0.16), (6.4, 0.16), (6.4, 0.09), (7.8, 0.09), (7.8, 0.12), (7.9, 0.12), (7.9, 0.06),
                                         (8.8, 0.06), (8.8, 0.0)])]
    for k, a in enumerate((0, 90)):                                                                    # cross arms with panel aerials
        y = 7.0 + k * 1.1
        base += [B(STEEL_D, -1.5, y, -0.04, 3.0, 0.08, 0.08, yaw=a, plain=True)]
    for (x, z, yaw, y) in ((1.45, 0, 0, 6.55), (-1.45, 0, 0, 6.55), (0, 1.45, 90, 7.65), (0, -1.45, 90, 7.65)):
        base += [B(RADOME, x - 0.06, y, z - 0.3, 0.12, 0.95, 0.6, yaw=yaw), B(STEEL_D, x - 0.08, y + 0.4, z - 0.05, 0.16, 0.14, 0.1, yaw=yaw, plain=True)]
    base += [lathe(RADOME, "y", 0, 0, [(8.8, 0.0), (8.8, 0.2), (9.0, 0.2), (9.12, 0.17), (9.22, 0.1), (9.26, 0.0)])]   # omni dome
    base += [B(RED, -0.05, 9.26, -0.05, 0.1, 0.1, 0.1, plain=True)]
    for (x, z) in ((0.5, 0.5), (-0.5, 0.5), (0.5, -0.5), (-0.5, -0.5)):                                 # whip aerials on spring bases
        base += [lathe(STEEL_D, "y", x, z, [(2.59, 0.0), (2.59, 0.05), (2.75, 0.03), (2.75, 0.012), (4.4, 0.008), (4.4, 0.0)])]
    # ---- fittings: guy wires from the mast to stakes, earth spike, warning sign, spare cable, lifting eyes
    for (x, z) in ((3.2, 3.2), (-3.2, 3.2), (3.2, -3.2), (-3.2, -3.2)):
        base += [bar(STEEL, (x * 0.04, 6.3, z * 0.04), (x, 0.05, z), 0.02), bar(STEEL, (x * 0.03, 7.8, z * 0.03), (x, 0.05, z), 0.02)]
        base += [lathe(STEEL_D, "y", x, z, [(0.0, 0.0), (0.0, 0.03), (0.3, 0.03), (0.3, 0.0)])]
    for (x, z) in ((1.2, 1.2), (-1.2, 1.2), (1.2, -1.2), (-1.2, -1.2)):
        base += eye(x, 2.55, z)
    base += extinguisher(0.7, 0.35, -1.5) + rod_y(BRASS, 1.9, 0.9, 0.0, 0.5, 0.03) + run(BRASS, [(1.9, 0.4, 0.9), (1.6, 0.3, 0.7), (1.4, 0.5, 0.5)], 0.02)
    base += [B(WHITE, -0.95, 1.5, -1.42, 0.4, 0.3, 0.01, marks=[("rear", "hazard", 0.5, 0.2, 0.36, 0.08), ("rear", "digits:EW", 0.5, 0.65, 0)])]
    base += run(BLACK, [(1.45, 0.9, 0.65), (1.8, 0.2, 0.4), (1.7, 0.08, -1.0), (1.3, 0.2, -1.7)], 0.06)
    base += handrail(STEEL_D, (-1.25, 2.55, -1.2), (-1.25, 2.55, 1.2), 0.2, 4) + round_hatch(0.7, -0.7, 2.59, 0.5, US_D)
    return dict(base=base, turret=[], barrel=[], bones=[])


def trench_jammer():
    """Dugout jammer: a rugged ribbed case on short legs with a cluster of stub aerials."""
    # moulded case with chamfered edges, stiffening ribs, a gasket line, latches and a carrying handle
    base = [hull(OLIVE_D, -0.36, 0.36, [(-0.3, 0.12), (-0.3, 0.46), (-0.26, 0.5), (0.26, 0.5), (0.3, 0.46), (0.3, 0.12), (0.26, 0.08), (-0.26, 0.08)], plain=True),
            hull(OLIVE, -0.38, 0.38, [(-0.32, 0.5), (-0.32, 0.56), (-0.27, 0.61), (0.27, 0.61), (0.32, 0.56), (0.32, 0.5)], plain=True),
            B(BLACK, -0.375, 0.485, -0.315, 0.75, 0.02, 0.63, plain=True)]
    for x in (-0.24, -0.08, 0.08, 0.24):                                                               # ribs round the body
        base += [B(OLIVE_D, x - 0.02, 0.1, -0.315, 0.04, 0.38, 0.63, plain=True)]
    for x in (-0.2, 0.2):
        base += [B(STEEL, x - 0.04, 0.4, -0.33, 0.08, 0.14, 0.03, plain=True), B(STEEL_D, x - 0.03, 0.44, -0.34, 0.06, 0.04, 0.02, plain=True)]
    base += rod_x(BLACK, -0.14, 0.28, 0.66, 0.0, 0.03) + [B(STEEL_D, -0.16, 0.6, -0.02, 0.03, 0.07, 0.04, plain=True), B(STEEL_D, 0.13, 0.6, -0.02, 0.03, 0.07, 0.04, plain=True)]
    base += [B(GRILLE, 0.36, 0.2, -0.2, 0.02, 0.2, 0.4), B(BLACK, -0.38, 0.22, 0.05, 0.02, 0.14, 0.2, plain=True)]
    base += rod_x(STEEL, -0.41, 0.03, 0.34, 0.1, 0.05) + rod_x(RED, -0.41, 0.03, 0.34, 0.2, 0.03)        # power socket, status lamp
    for (x, z) in ((0.3, 0.24), (-0.3, 0.24), (0.3, -0.24), (-0.3, -0.24)):
        base += rod_y(BLACK, x, z, 0.0, 0.08, 0.07)
    # battery with terminals and the lead across to the case
    base += [hull(BLACK, -0.82, -0.56, [(-0.2, 0.0), (-0.2, 0.2), (-0.17, 0.23), (0.17, 0.23), (0.2, 0.2), (0.2, 0.0)], plain=True)]
    base += rod_y(RED, -0.76, -0.1, 0.23, 0.04, 0.03) + rod_y(STEEL, -0.62, -0.1, 0.23, 0.04, 0.03) + rod_x(BLACK, -0.6, 0.24, 0.27, -0.1, 0.02)
    # aerials: a threaded base, a spring, then a stub of its own length for each band
    for i, (x, z, hgt) in enumerate(((0.25, 0.18, 0.9), (-0.25, 0.18, 0.7), (0.25, -0.18, 0.55), (-0.25, -0.18, 1.2), (0, 0.2, 0.45), (0, -0.2, 0.35))):
        base += [lathe(BLACK, "y", x, z, [(0.61, 0.0), (0.61, 0.04), (0.66, 0.04), (0.67, 0.03), (0.74, 0.03), (0.76, 0.022),
                                           (0.76 + hgt * 0.7, 0.018), (0.76 + hgt, 0.012), (0.76 + hgt, 0.0)])]
        base += rod_y(STEEL, x, z, 0.66, 0.02, 0.07)
    base += rod_y(RED, -0.25, -0.18, 1.96, 0.03, 0.03)
    # ---- fittings
    base += [B(WHITE, -0.12, 0.2, -0.335, 0.24, 0.12, 0.004, marks=[("rear", "digits:TJ-4", 0.5, 0.5, 0)])]
    base += [bar(BLACK, (-0.38, 0.3, 0.2), (-0.5, 0.26, 0.26), 0.02), bar(BLACK, (-0.5, 0.26, 0.26), (-0.62, 0.25, 0.0), 0.02)]
    base += eye(0.3, 0.61, 0.0) + eye(-0.3, 0.61, 0.0)
    base += [B(BLACK, 0.08, 0.61, -0.12, 0.2, 0.02, 0.1, plain=True)] + [lathe(BLACK, "y", 0.12 + i * 0.05, -0.07, [(0.63, 0.0), (0.63, 0.014), (0.65, 0.014), (0.65, 0.0)]) for i in range(3)]
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
    # three round dishes that sweep slowly from side to side: a big one in the middle and one on each side
    dish = [B(RUS_D, -2.3, 4.0, -4.1, 4.6, 0.3, 0.3)]
    for cx, dia in ((0, 2.5), (2.2, 2.1), (-2.2, 2.1)):
        dish += [lathe(RADOME, "z", cx, 4.3, [(-4.32, dia * 0.12), (-4.42, dia * 0.3), (-4.56, dia * 0.5), (-4.52, dia * 0.5),
                                               (-4.38, dia * 0.3), (-4.28, dia * 0.12), (-4.26, 0.0)])]
        dish += rod_z(RUS_D, cx, 4.3, -4.3, 0.2, dia * 0.24)
        dish += [B(STEEL_D, cx - 0.03, 4.27, -5.2, 0.06, 0.06, 0.9, plain=True), B(BLACK, cx - 0.1, 4.2, -5.3, 0.2, 0.2, 0.12)]
    dish += rod_y(STEEL_D, 0, -4.0, 4.7, 1.3, 0.1) + [B(BLACK, -0.25, 5.9, -4.2, 0.5, 0.4, 0.4)]
    dish += [B(RUS_D, -0.4, 3.7, -4.3, 0.8, 0.8, 0.8)]
    base += P(STEEL_D, 0.6, 0.5, 0.3, 0.5, 0.6, 0.9)
    base += P(RUS_D, 0.9, 0.9, -1.8, 0.4, 0.16, 0.2, plain=True) + P(RUS_D, 0.9, 0.9, -5.45, 0.4, 0.16, 0.2, plain=True)
    base += P(RED, 1.0, 0.85, -5.57, 0.2, 0.12, 0.03)
    base += mudguards(RUS_D, 0.85, 0.45, zs, 1.2)
    base += truck_details(RUS, RUS_D, zs, 0.85, 1.2, -5.5, 3.5, 1.25) + axles(zs, 0.85, 1.2)
    bones = truck_wheels(zs, 0.85, 0.42, 1.2, steer=2)
    bones.append({"name": "move_scanDish", "parent": "base", "pivot": (0, 4.3, -3.9), "cubes": dish})
    # ---- fittings
    base += beacon(-0.9, 3.08, 4.1) + whip(1.0, 3.7, 3.0, 2.2) + whip(-1.0, 3.7, 3.0, 1.6) + shackle(0.7, 1.05, 5.5) + shackle(-0.7, 1.05, 5.5)
    base += lamp(WHITE, 0.55, 3.14, 5.05, 0.18) + lamp(WHITE, -0.55, 3.14, 5.05, 0.18) + [B(RUS_D, -0.8, 3.0, 4.95, 1.6, 0.06, 0.08, plain=True)]
    base += [B(RUS_D, -1.27, 1.6, -0.6, 0.04, 1.6, 0.8, marks=[("right", "hatch", 0.5, 0.5, 0.7, 1.5)]), B(GLASS, -1.29, 2.6, -0.4, 0.02, 0.3, 0.4),
             B(GLASS, 1.26, 2.6, 0.2, 0.02, 0.3, 0.5), B(GLASS, 1.26, 2.6, -1.2, 0.02, 0.3, 0.5),
             B(RUS_D, 1.24, 1.6, 2.0, 0.03, 1.5, 1.1, marks=[("left", "panel", 0.5, 0.5, 0.9, 1.3)])]
    base += ladder(STEEL_D, -1.32, -0.55, 0.6, 1.55, 0.7) + ladder(STEEL_D, -0.2, 3.32, 1.5, 3.3, 0.4, across="x")
    base += handrail(STEEL_D, (1.2, 3.43, -1.8), (1.2, 3.43, 3.2), 0.22, 6) + handrail(STEEL_D, (-1.2, 3.43, -1.8), (-1.2, 3.43, 3.2), 0.22, 6)
    base += [hull(STEEL_D, -1.1, -0.4, [(1.8, 3.43), (1.8, 3.8), (1.9, 3.88), (2.9, 3.88), (3.0, 3.8), (3.0, 3.43)]), B(GRILLE, -1.05, 3.5, 3.0, 0.6, 0.3, 0.02)]   # air conditioners
    base += [hull(STEEL_D, 0.4, 1.1, [(1.8, 3.43), (1.8, 3.8), (1.9, 3.88), (2.9, 3.88), (3.0, 3.8), (3.0, 3.43)]), B(GRILLE, 0.45, 3.5, 3.0, 0.6, 0.3, 0.02)]
    for (x, z) in ((1.15, -1.8), (-1.15, -1.8), (1.15, 3.2), (-1.15, 3.2)):
        base += eye(x, 3.43, z)
    base += rod_x(BLACK, -1.2, 0.55, 2.0, -2.4, 0.6) + rod_x(STEEL_D, -1.25, 0.65, 2.0, -2.4, 0.12) + rod_x(BLACK, 0.65, 0.55, 2.0, -2.4, 0.6) + rod_x(STEEL_D, 0.6, 0.65, 2.0, -2.4, 0.12)   # cable drums
    base += run(BLACK, [(0.4, 1.95, -2.6), (0.3, 2.6, -3.2), (0.25, 3.4, -3.7)], 0.07) + run(BLACK, [(-0.4, 1.95, -2.6), (-0.3, 2.8, -3.3), (-0.25, 3.6, -3.7)], 0.06)   # feeds up the mast
    base += [lathe(RUS_D, "y", 0, -3.9, [(1.95, 0.5), (2.1, 0.5), (2.15, 0.38)]), lathe(RUS_D, "y", 0, -3.9, [(3.5, 0.4), (3.5, 0.48), (3.75, 0.48), (3.75, 0.0)])]
    base += rod_y(STEEL_D, 1.05, 3.1, 3.43, 0.6, 0.12) + extinguisher(1.32, 1.6, 3.0) + tools(1.95, -5.2, -0.5)
    for sx in (1, -1):
        base += jack(sx * 1.3, -1.7, 1.0, RUS_D) + jack(sx * 1.3, -5.35, 1.0, RUS_D) + chock(sx * 1.5, 0.3)
        base += [eye(sx * 1.15, 1.45, z)[2] for z in (-5.0, -3.6, -2.4)]
    # the aerial head: bracing behind each dish, waveguides, and a rotary joint under the yoke
    for cx, dia in ((0, 2.5), (2.2, 2.1), (-2.2, 2.1)):
        for k in range(4):
            a = math.radians(45 + 90 * k)
            dish += [bar(STEEL_D, (cx, 4.3, -4.1), (cx + dia * 0.42 * math.cos(a), 4.3 + dia * 0.42 * math.sin(a), -4.44), 0.035)]
            dish += [bar(STEEL_D, (cx + dia * 0.46 * math.cos(a), 4.3 + dia * 0.46 * math.sin(a), -4.56), (cx, 4.3, -5.2), 0.02)]
        dish += run(BLACK, [(cx, 4.2, -4.1), (cx * 0.5, 3.95, -4.0), (0, 3.9, -3.95)], 0.05)
    dish += [lathe(STEEL_D, "y", 0, -3.9, [(3.75, 0.0), (3.75, 0.3), (3.9, 0.3), (3.9, 0.0)])]
    return dict(base=base, turret=[], barrel=[], bones=bones)


PIVOTS = {"himars": ((0, 1.2, -1.5), (0, 1.7, -2.5))}

MODELS = {"zu_23_2": zu_23_2, "gepard": gepard, "pantsir": pantsir, "patriot": patriot, "himars": himars,
          "mortar_120": mortar_120, "m109": m109, "tomahawk_launcher": tomahawk_launcher,
          "iskander_launcher": iskander_launcher, "jammer": jammer, "trench_jammer": trench_jammer, "krasukha": krasukha}



if __name__ == "__main__":
    import sys, time
    from PIL import Image
    names = sys.argv[2:] or list(MODELS)
    for name in names:
        t0 = time.time()
        model = MODELS[name]()
        tex, w, h = bake(model, name)
        Image.fromarray(tex).convert("RGB").save(f"{sys.argv[1]}/{name}_texture.png")
        views = [render(model, tex, math.radians(a), math.radians(b), 900) for a, b in ((35, 18), (215, 22), (90, 2), (0, 60))]
        sheet = Image.new("RGB", (1800, 1800))
        for i, v in enumerate(views):
            sheet.paste(v, ((i % 2) * 900, (i // 2) * 900))
        sheet.save(f"{sys.argv[1]}/{name}.png")
        parts = model["base"] + model["turret"] + model["barrel"] + [c for b in model["bones"] for c in b["cubes"]]
        print(name, len(parts), "parts", sum(len(sf["faces"]) for p in parts for sf in p["surfaces"]), "faces", w, h, round(time.time() - t0, 1), "s")
