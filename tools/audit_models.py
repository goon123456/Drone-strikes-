"""Model audit: finds parts that clip, overlap or stick out.

    python3 tools/audit_models.py [model ...]

For every model built by the generators it reports:
  * MOVING  - a moving part (turret, gun, launcher, steered wheel, hatch lid) that passes through a fixed part
              somewhere in its travel, deeper than it already sits at rest;
  * FLOAT   - a part that touches nothing (floating in the air, or the gap left by a part that moved);
  * ZFIGHT  - two box faces lying in the same plane and overlapping, which flicker in game;
  * EYE     - a crew position whose eye is inside solid geometry (the camera would see the inside of the model);
  * POKE    - a small part that pokes clean through a thin panel and out the far side.
Inside tests are exact for boxes, turned (lathe) parts and lofted hulls; skinned and ribbon parts (aircraft
fuselages, wings) are only used as moving or tested points, not as solids.
"""
import math
import sys

import numpy as np

sys.path.insert(0, "tools")
import vehicle_kit as VK          # noqa: E402

# ---------------------------------------------------------------- how each vehicle moves, from the Java classes
#                 turret yaw sweep   gun elevation range   eye positions of the crew (seat + 1.62), seat index on turret
MOTION = {
    "himars":            dict(yaw=(-110, 110), pitch=(0, 50), clear=18, seats=[(0.5, 0.75, 2.45), (-0.5, 0.75, 2.45)], turret_seats=[]),
    "m109":              dict(yaw=(-180, 180), pitch=(-3, 70), seats=[(-0.95, 0.5, 1.6), (0.0, 1.58, -1.5), (-0.5, 0.4, -2.0)], turret_seats=[1]),
    "mortar_120":        dict(yaw=(-180, 180), pitch=(45, 85), seats=[(0.75, -0.45, -0.55)], turret_seats=[0]),
    "tomahawk_launcher": dict(yaw=(0, 0), pitch=(0, 85), seats=[], turret_seats=[]),
    "iskander_launcher": dict(yaw=(0, 0), pitch=(0, 88), seats=[(0.6, 1.13, 5.4), (-0.6, 1.13, 5.4)], turret_seats=[]),
    "zu_23_2":           dict(yaw=(-180, 180), pitch=(-5, 85), seats=[(0, 0.35, -0.6)], turret_seats=[0]),
    "gepard":            dict(yaw=(-180, 180), pitch=(-8, 85), seats=[(0.6, 0.45, 2.4), (0, 2.3, 0.3)], turret_seats=[1]),
    "pantsir":           dict(yaw=(-180, 180), pitch=(0, 85), seats=[(0.55, 0.9, 4.0), (0, 2.48, -1.0)], turret_seats=[1]),
    "patriot":           dict(yaw=(-110, 110), pitch=(0, 80), clear=15, seats=[], turret_seats=[]),
    "jammer":            dict(yaw=(0, 0), pitch=(0, 0), seats=[], turret_seats=[]),
    "trench_jammer":     dict(yaw=(0, 0), pitch=(0, 0), seats=[], turret_seats=[]),
    "krasukha":          dict(yaw=(0, 0), pitch=(0, 0), seats=[(0.55, 0.9, 4.5), (-0.55, 0.9, 4.5)], turret_seats=[]),
}
EYE = 1.62
STEER = 0.6          # radians the front wheels turn either way
HATCH = 1.75         # radians a hatch lid swings open


# ---------------------------------------------------------------- inside tests
def _unrotate(part, pts):
    pitch, yaw, roll = part["rot"]
    if not (pitch or yaw or roll):
        return pts
    back = dict(part, rot=(-pitch, -yaw, -roll))
    return VK._rotate(back, pts)


def _poly_inside_depth(poly, u, v):
    """Signed distance into a polygon (positive inside) for arrays of points."""
    poly = np.asarray(poly, float)
    n = len(poly)
    inside = np.zeros(u.shape, bool)
    dist = np.full(u.shape, np.inf)
    for i in range(n):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
        if y1 != y2:
            hit = ((y1 > v) != (y2 > v)) & (u < (x2 - x1) * (v - y1) / (y2 - y1) + x1)
            inside ^= hit
        ex, ey = x2 - x1, y2 - y1
        L2 = ex * ex + ey * ey or 1e-12
        t = np.clip(((u - x1) * ex + (v - y1) * ey) / L2, 0, 1)
        dist = np.minimum(dist, np.hypot(u - (x1 + t * ex), v - (y1 + t * ey)))
    return np.where(inside, dist, -dist)


def depth(part, pts):
    """How far each point is inside the part (negative: outside by that much). None if the part has no solid test."""
    q = _unrotate(part, np.asarray(pts, float))
    k = part["kind"]
    if k == "box":
        x, y, z, w, h, d = part["box"]
        dx = np.minimum(q[:, 0] - x, x + w - q[:, 0])
        dy = np.minimum(q[:, 1] - y, y + h - q[:, 1])
        dz = np.minimum(q[:, 2] - z, z + d - q[:, 2])
        inside = np.minimum(np.minimum(dx, dy), dz)
        outside = -np.sqrt(np.maximum(-dx, 0) ** 2 + np.maximum(-dy, 0) ** 2 + np.maximum(-dz, 0) ** 2)
        return np.where(inside > 0, inside, outside)
    if k == "skin":
        secs, n = part["sections"], part["power"]
        zs = np.array([sc[0] for sc in secs])
        out = np.full(len(q), -1.0)
        for i in range(len(q)):
            z = q[i, 2]
            for a, b in zip(secs, secs[1:]):
                lo, hi = min(a[0], b[0]), max(a[0], b[0])
                if not (lo <= z <= hi) or hi - lo < 1e-9:
                    continue
                t = (z - a[0]) / (b[0] - a[0])
                cx, cy, w, h = [a[k] + (b[k] - a[k]) * t for k in range(1, 5)]
                if w < 1e-6 or h < 1e-6:
                    break
                f = (abs(q[i, 0] - cx) / (w / 2)) ** n + (abs(q[i, 1] - cy) / (h / 2)) ** n
                out[i] = (1 - f ** (1 / n)) * min(w, h) / 2
                break
        return out
    if k in ("lathe", "loft"):
        ai = "xyz".index(part["axis"])
        oth = [i for i in range(3) if i != ai]
        a = q[:, ai]
        if k == "lathe":
            (c1, c2), prof = part["centre"], part["profile"]
            r = np.hypot(q[:, oth[0]] - c1, q[:, oth[1]] - c2)
            best = np.full(len(q), -np.inf)
            for (a0, r0), (a1, r1) in zip(prof, prof[1:]):
                lo, hi = min(a0, a1), max(a0, a1)
                if hi - lo < 1e-9:
                    continue
                t = np.clip((a - a0) / (a1 - a0), 0, 1)
                rr = r0 + (r1 - r0) * t
                along = np.minimum(a - lo, hi - a)
                d = np.minimum(rr - r, along)
                best = np.maximum(best, np.where((a >= lo) & (a <= hi), d, -np.maximum(np.abs(a - np.clip(a, lo, hi)), r - rr)))
            return best
        a0, a1 = part["a"]
        p0, p1 = np.asarray(part["polys"][0], float), np.asarray(part["polys"][1], float)
        t = np.clip((a - a0) / ((a1 - a0) or 1e-9), 0, 1)
        out = np.empty(len(q))
        for i in range(len(q)):
            poly = p0 + (p1 - p0) * t[i]
            pd = _poly_inside_depth(poly, np.array([q[i, oth[0]]]), np.array([q[i, oth[1]]]))[0]
            along = min(a[i] - min(a0, a1), max(a0, a1) - a[i])
            out[i] = min(pd, along) if along >= 0 else -max(-along, abs(min(pd, 0)))
        return out
    return None


def points(part, step=0.05):
    """Points spread over a part's surface: the grid corners plus points along every face."""
    out = []
    for s in VK.surfaces(part):
        g = s["grid"].reshape(-1, 3)
        out.append(g)
        if s["mask"]:
            continue
        R, C = s["grid"].shape[:2]
        for r in range(R - 1):
            for c in range(C - 1):
                quad = s["grid"][r:r + 2, c:c + 2].reshape(4, 3)
                size = max(np.linalg.norm(quad[0] - quad[3]), np.linalg.norm(quad[1] - quad[2]))
                n = int(min(6, max(1, size / step)))
                u, v = np.meshgrid(np.linspace(0, 1, n + 1), np.linspace(0, 1, n + 1))
                u, v = u.reshape(-1, 1), v.reshape(-1, 1)
                p = quad[0] * (1 - u) * (1 - v) + quad[1] * u * (1 - v) + quad[2] * (1 - u) * v + quad[3] * u * v
                out.append(p)
    return np.unique(np.round(np.concatenate(out), 4), axis=0)


def bbox(pts):
    return pts.min(0), pts.max(0)


def overlap(b1, b2, pad=0.0):
    return np.all(b1[0] - pad <= b2[1]) and np.all(b2[0] - pad <= b1[1])


def label(part, i):
    k = part["kind"]
    where = part.get("box") or part.get("centre") or part.get("a")
    return f"#{i} {k} {part['mat']} {tuple(round(v, 2) for v in where) if where else ''}"


# ---------------------------------------------------------------- moving parts
def yaw_pts(p, pivot, deg):
    a = math.radians(deg)
    q = p - pivot
    return np.stack([q[:, 0] * math.cos(a) + q[:, 2] * math.sin(a), q[:, 1], -q[:, 0] * math.sin(a) + q[:, 2] * math.cos(a)], 1) + pivot


def pitch_pts(p, pivot, deg):
    a = math.radians(deg)
    q = p - pivot
    return np.stack([q[:, 0], q[:, 1] * math.cos(a) + q[:, 2] * math.sin(a), q[:, 2] * math.cos(a) - q[:, 1] * math.sin(a)], 1) + pivot


def audit(name, model, pivots, motion, report):
    model = VK.finish(model)
    VK._deconflict(VK._all_parts(model))
    fixed = [p for p in model["base"]]
    tp, bp = np.array(pivots[0], float), np.array(pivots[1], float)
    turret_bones = {"turret", "barrel"}
    changed = True
    while changed:                                            # everything hung from the turret or gun moves with it
        changed = False
        for b in model["bones"]:
            if b["parent"] in turret_bones and b["name"] not in turret_bones:
                turret_bones.add(b["name"]); changed = True
    turret = list(model["turret"]) + [c for b in model["bones"] if b["parent"] == "turret" for c in b["cubes"]]
    barrel = list(model["barrel"]) + [c for b in model["bones"] if b["parent"] in turret_bones - {"turret"} and b["parent"] != "turret" for c in b["cubes"]]
    wheels = [(b, c) for b in model["bones"] if b["name"].startswith("wheel") for c in b["cubes"]]
    hatches = [(b, c) for b in model["bones"] if b["name"].startswith("move_hatch") for c in b["cubes"]]
    moving_names = ("wheel", "move_hatch", "track", "move_sway", "move_payload", "prop", "gear")
    fixed += [c for b in model["bones"] if b["name"] not in turret_bones and not b["name"].startswith(moving_names)
              and not (b["name"].startswith("r") and b["name"][1:].isdigit()) for c in b["cubes"]]
    solids = [(i, p) for i, p in enumerate(fixed) if p["kind"] in ("box", "lathe", "loft", "skin")]
    fixed_boxes = {i: bbox(points(p)) for i, p in solids}

    def hits(pts, base_depth, tag, label_moving):
        for i, p in solids:
            bb = fixed_boxes[i]
            if not overlap(bbox(pts), bb):
                continue
            d = depth(p, pts)
            if d is None:
                continue
            worst = float(d.max())
            if worst > base_depth.get(i, 0.0) + 0.03:
                report.append(("MOVING", name, f"{label_moving} {tag} goes {worst:.2f} into {label(p, i)}"))

    # turret and gun through their travel
    y0, y1 = motion["yaw"]
    p0, p1 = motion["pitch"]
    moving = [("turret", t, False) for t in turret] + [("gun", b, True) for b in barrel]
    for kind, part, on_gun in moving:
        pts = points(part)
        base = {}
        for i, p in solids:                                       # how deep it already sits at rest
            d = depth(p, pts) if overlap(bbox(pts), fixed_boxes[i]) else None
            if d is not None:
                base[i] = max(0.0, float(d.max()))
        worst = {}
        clear = motion.get("clear", p0)               # some launchers only traverse once raised clear of the cab
        for yaw in (np.arange(y0, y1 + 1, 15) if y1 > y0 else [y0]):
            lo = p0 if abs(yaw) < 1 else max(p0, clear)
            for pitch in ([lo, (lo + p1) / 2, p1] if on_gun else [0]):
                q = pitch_pts(pts, bp, pitch) if on_gun else pts
                q = yaw_pts(q, tp, yaw)
                for i, p in solids:
                    if not overlap(bbox(q), fixed_boxes[i]):
                        continue
                    d = depth(p, q)
                    if d is None:
                        continue
                    v = float(d.max())
                    if v > base.get(i, 0.0) + 0.03 and v > worst.get(i, (0,))[0]:
                        worst[i] = (v, yaw, pitch)
        for i, (v, yaw, pitch) in worst.items():
            report.append(("MOVING", name, f"{kind} {label(part, -1)} at yaw {yaw:.0f} pitch {pitch:.0f} goes {v:.2f} into {label(fixed[i], i)}"))

    # steered wheels, and every wheel against the body (wheel arches, mudguards)
    for b, c in wheels:
        pts = points(c)
        piv = np.array(b["pivot"], float)
        angles = [-STEER, 0, STEER] if b["name"].endswith("Turn") else [0]
        for ang in angles:
            q = yaw_pts(pts, piv, math.degrees(ang))
            for i, p in solids:
                if not overlap(bbox(q), fixed_boxes[i]):
                    continue
                d = depth(p, q)
                if d is not None and float(d.max()) > 0.04:
                    report.append(("MOVING", name, f"{b['name']} steered {math.degrees(ang):.0f} goes {float(d.max()):.2f} into {label(p, i)}"))
                    break

    # hatch lids opened
    for b, c in hatches:
        pts = pitch_pts(points(c), np.array(b["pivot"], float), math.degrees(HATCH))
        for i, p in solids:
            if overlap(bbox(pts), fixed_boxes[i]):
                d = depth(p, pts)
                if d is not None and float(d.max()) > 0.04:
                    report.append(("MOVING", name, f"{b['name']} opened goes {float(d.max()):.2f} into {label(p, i)}"))

    # aircraft: propellers and rotors through a full turn, landing gear through its retraction
    for b in model["bones"]:
        name_b = b["name"]
        if name_b == "prop" or (name_b.startswith("r") and name_b[1:].isdigit()) or name_b.startswith("gear"):
            piv = np.array(b["pivot"], float)
            for c in b["cubes"]:
                pts = points(c)
                if name_b.startswith("gear"):
                    sign = 1 if name_b == "gearNf" else -1         # "gearNf" folds forward, the others back
                    poses = [("up", pitch_pts(pts, piv, sign * math.degrees(r))) for r in (0.3, 0.6, 0.9)]
                elif name_b == "prop":
                    poses = []
                    for a in range(0, 360, 20):
                        r = math.radians(a); q = pts - piv
                        poses.append((f"turned {a}", np.stack([q[:, 0] * math.cos(r) - q[:, 1] * math.sin(r), q[:, 0] * math.sin(r) + q[:, 1] * math.cos(r), q[:, 2]], 1) + piv))
                else:
                    poses = [(f"turned {a}", yaw_pts(pts, piv, a)) for a in range(0, 360, 20)]
                worst = {}
                for tag, q in poses:
                    for i, p in solids:
                        if not overlap(bbox(q), fixed_boxes[i]):
                            continue
                        d = depth(p, q)
                        if d is None:
                            continue
                        v = float(d.max())
                        if v > 0.02 and v > worst.get(i, (0, ""))[0]:
                            worst[i] = (v, tag)
                for i, (v, tag) in worst.items():
                    # gear swinging up into the fuselage is it entering its well (it is hidden once fully up);
                    # blade roots sitting in their hub are meant to
                    if name_b.startswith("gear") and fixed[i]["kind"] == "skin":
                        continue
                    if v < 0.035 and not name_b.startswith("gear"):
                        continue
                    report.append(("MOVING", name, f"{name_b} {label(c, -1)} {tag} goes {v:.2f} into {label(fixed[i], i)}"))

    # crew eyes inside solid geometry (turret seats swept with the turret)
    everything = [(i, p) for i, p in enumerate(fixed + turret + barrel) if p["kind"] in ("box", "lathe", "loft")]
    for s, seat in enumerate(motion["seats"]):
        eye = np.array([[seat[0], seat[1] + EYE, seat[2]]])
        yaws = np.arange(-180, 181, 30) if s in motion["turret_seats"] else [0]
        for yaw in yaws:
            e = yaw_pts(eye, tp, yaw) if s in motion["turret_seats"] else eye
            for i, p in everything:
                d = depth(p, e)
                if d is not None and d[0] > 0.01:
                    report.append(("EYE", name, f"seat {s} eye {tuple(np.round(e[0], 2))} (turret {yaw}) is {d[0]:.2f} inside {label(p, i)}"))
                    break

    # floating parts: nothing within 1 cm
    allp = [p for p in model["base"] + model["turret"] + model["barrel"] + [c for b in model["bones"] for c in b["cubes"]]]
    pts_all = [points(p) for p in allp]
    boxes = [bbox(q) for q in pts_all]
    running = {id(c) for b in model["bones"] if b["name"].startswith(("wheel", "track")) for c in b["cubes"]}
    for i, p in enumerate(allp):
        if id(p) in running:                                  # wheels ride on the track, which is only placed in game
            continue
        if boxes[i][0][1] < 0.02:                              # standing on the ground
            continue
        touching = False
        for j, o in enumerate(allp):
            if i == j or not overlap(boxes[i], boxes[j], 0.012):
                continue
            if o["kind"] not in ("box", "lathe", "loft"):
                touching = True
                break
            d = depth(o, pts_all[i])
            if d is not None and float(d.max()) > -0.012:
                touching = True
                break
            if p["kind"] in ("box", "lathe", "loft"):
                d2 = depth(p, pts_all[j])
                if d2 is not None and float(d2.max()) > -0.012:
                    touching = True
                    break
        if not touching:
            report.append(("FLOAT", name, f"{label(p, i)} touches nothing"))

    # z-fighting: overlapping coplanar box faces facing the same way, different parts
    faces = []
    links = {id(c) for b in model["bones"] if b["name"].startswith("track") for c in b["cubes"]}
    for i, p in enumerate(allp):
        if p["kind"] != "box" or any(p["rot"]) or id(p) in links:
            continue
        x, y, z, w, h, d = p["box"]
        lo, hi = np.array([x, y, z]), np.array([x + w, y + h, z + d])
        for ax in range(3):
            for sgn, val in ((-1, lo[ax]), (1, hi[ax])):
                o = [k for k in range(3) if k != ax]
                faces.append((ax, sgn, round(val, 4), lo[o], hi[o], i, p["mat"]))
    faces.sort(key=lambda f: (f[0], f[1], f[2]))
    for a in range(len(faces)):
        for b2 in range(a + 1, len(faces)):
            fa, fb = faces[a], faces[b2]
            if (fa[0], fa[1], fa[2]) != (fb[0], fb[1], fb[2]):
                break
            ov = np.minimum(fa[4], fb[4]) - np.maximum(fa[3], fb[3])
            if np.all(ov > 0.004) and fa[5] != fb[5]:
                report.append(("ZFIGHT", name, f"{label(allp[fa[5]], fa[5])} and {label(allp[fb[5]], fb[5])} share a face ({'xyz'[fa[0]]}={fa[2]}, {ov[0]:.2f}x{ov[1]:.2f})"))


def main():
    import gen_vehicles as GV
    import gen_aircraft as GA
    import gen_munitions as GM
    import gen_sites as GS
    names = sys.argv[1:]
    report = []
    for name, build in GV.MODELS.items():
        if names and name not in names:
            continue
        piv = GV.PIVOTS.get(name, ((0, 0, 0), (0, 0, 0)))
        audit(name, build(), piv, MOTION.get(name, dict(yaw=(0, 0), pitch=(0, 0), seats=[], turret_seats=[])), report)
    for group in (GA.MODELS, GM.MODELS, GS.MODELS):
        for name, (build, _) in group.items():
            if names and name not in names:
                continue
            audit(name, build(), ((0, 0, 0), (0, 0, 0)), dict(yaw=(0, 0), pitch=(0, 0), seats=[], turret_seats=[]), report)
    from collections import Counter
    for kind, name, text in report:
        print(f"{kind:7} {name:18} {text}")
    print(Counter((k, n) for k, n, _ in report))


if __name__ == "__main__":
    main()
