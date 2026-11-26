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
    gear, bones = running_gear(1.07, 0.55, -3.75, 3.75, 7, 0.7, NATO_D, rollers=4)
    N, D = NATO, NATO_D
    base = gear + [
        B(D, -1.07, 0.42, -3.55, 2.14, 0.7, 6.9, plain=True),                                  # belly
        B(N, -1.62, 1.08, -3.75, 3.24, 0.5, 6.0, marks=[("left", "panel", 0.3, 0.5, 0.9, 0.3), ("right", "panel", 0.7, 0.5, 0.9, 0.3)]),
        B(N, -1.62, 1.58, -3.75, 3.24, 0.42, 5.95, marks=[("rear", "cross", 0.2, 0.5, 0.3), ("rear", "digits:Y-301", 0.75, 0.5, 0)]),
        slope(N, -1.6, 3.2, 2.2, 2.0, 3.85, 1.25),                                             # glacis
        slope(N, -1.07, 2.14, 3.0, 0.5, 3.85, 1.25),                                           # lower nose plate
        B(N, -1.6, 1.08, 2.2, 3.2, 0.5, 0.95), B(N, -1.6, 1.08, 3.1, 3.2, 0.22, 0.55),         # nose sides under the glacis
        B(D, -1.62, 1.2, 3.82, 3.24, 0.1, 0.08, plain=True)]                                   # front lip
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
        B(N, -1.05, 2.0, -1.9, 2.1, 1.25, 3.0, marks=[("left", "cross", 0.35, 0.45, 0.42), ("right", "cross", 0.65, 0.45, 0.42),
                                                    ("left", "panel", 0.75, 0.6, 0.6, 0.5), ("right", "panel", 0.25, 0.6, 0.6, 0.5)]),
        slope(N, -1.0, 2.0, 1.75, 2.0, 1.1, 3.25),                                              # sloped front
        B(N, -1.0, 2.0, 1.1, 2.0, 0.5, 0.65),
        B(D, -1.08, 3.25, -1.93, 2.16, 0.05, 2.96, plain=True),                                 # roof plate
        B(N, -0.9, 2.1, -2.45, 1.8, 0.95, 0.55, marks=[("rear", "vent", 0.5, 0.5, 1.2, 0.4)]),  # rear bustle
        B(D, -0.95, 3.05, -2.5, 1.9, 0.05, 0.6, plain=True),
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
    # search radar at the back: a mast, a curved reflector made of five slats, its frame and feed horn
    turret += [B(D, -0.14, 3.3, -2.2, 0.28, 0.2, 0.28), B(D, -0.09, 3.5, -2.15, 0.18, 0.55, 0.18),
               B(STEEL_D, -0.7, 4.3, -2.1, 1.4, 0.05, 0.05, plain=True), B(STEEL_D, -0.03, 4.05, -2.1, 0.06, 0.06, 0.55, plain=True),
               B(BLACK, -0.12, 4.0, -1.6, 0.24, 0.16, 0.12), B(D, -0.6, 4.72, -2.2, 1.2, 0.06, 0.06, plain=True),
               B(D, -0.03, 4.55, -2.2, 0.06, 0.2, 0.06, plain=True)]
    for i, ang in enumerate((-30, -15, 0, 15, 30)):
        x = (i - 2) * 0.38
        turret += [B(D, x - 0.2, 3.95, -2.24 + abs(i - 2) * 0.05, 0.4, 0.6, 0.05, yaw=ang),
                   B(STEEL_D, x - 0.19, 3.97, -2.19 + abs(i - 2) * 0.05, 0.38, 0.56, 0.02, yaw=ang, plain=True)]
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
    barrel = []
    for sx in (1, -1):
        cx = 1.31 * sx
        side = "left" if sx > 0 else "right"
        barrel += [B(N, cx - 0.2, 2.42, -1.05, 0.4, 0.72, 1.95, marks=[(side, "panel", 0.5, 0.5, 1.2, 0.4)]),      # gun housing
                   B(D, cx - 0.23, 2.5, -0.7, 0.46, 0.55, 0.95), B(D, cx - 0.17, 3.14, -0.9, 0.34, 0.06, 1.5, plain=True),
                   B(D, cx - 0.15, 2.3, -0.3, 0.3, 0.14, 0.7), B(STEEL_D, cx - 0.1, 2.22, 0.0, 0.2, 0.1, 0.3, plain=True),   # case ejection chute
                   B(D, cx - 0.14, 2.6, 0.9, 0.28, 0.3, 0.45), B(STEEL_D, cx - 0.2, 2.5, -1.12, 0.4, 0.5, 0.08, plain=True)]
        barrel += rod_z(STEEL_D, cx, 2.74, 1.35, 0.6, 0.2)                                       # recoil sleeve
        barrel += rod_z(STEEL_D, cx, 2.74, 1.95, 1.2, 0.11)                                      # barrel
        barrel += rod_z(STEEL, cx, 2.74, 2.4, 0.06, 0.15) + rod_z(STEEL, cx, 2.74, 2.8, 0.06, 0.15)
        barrel += rod_z(BLACK, cx, 2.74, 3.12, 0.3, 0.17)                                        # muzzle brake
        barrel += [B(BLACK, cx - 0.1, 2.72, 3.2, 0.2, 0.04, 0.05, plain=True), B(BLACK, cx - 0.1, 2.72, 3.32, 0.2, 0.04, 0.05, plain=True),
                   B(STEEL_D, cx - 0.045, 2.84, 3.05, 0.09, 0.1, 0.4, plain=True),                # muzzle-velocity gauge
                   B(STEEL_D, cx - 0.02, 2.82, 2.0, 0.04, 0.04, 1.05, plain=True)]
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



if __name__ == "__main__":
    import sys
    from PIL import Image
    names = sys.argv[2:] or list(MODELS)
    for name in names:
        model = MODELS[name]()
        tex, w, h = bake(model, name)
        Image.fromarray(tex).save(f"{sys.argv[1]}/{name}_texture.png")
        views = [render(model, tex, math.radians(a), math.radians(b), 760) for a, b in ((35, 18), (215, 22), (90, 2), (0, 60))]
        sheet = Image.new("RGB", (1520, 1520))
        for i, v in enumerate(views):
            sheet.paste(v, ((i % 2) * 760, (i // 2) * 760))
        sheet.save(f"{sys.argv[1]}/{name}.png")
        print(name, len([c for c in model["base"] + model["turret"] + model["barrel"]]) + sum(len(b["cubes"]) for b in model["bones"]), "cubes", w, h)
