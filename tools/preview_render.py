"""A hole-free preview renderer: every face is sampled by real distance (finer than a pixel) and z-buffered."""
import math, numpy as np
from vehicle_kit import _all_parts, _inside
from PIL import Image

def rend(model, tex, yaw, pitch, size=1600, bg=(170, 190, 205), crop=True):
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    Rm = np.array([[cy, 0, -sy], [0, 1, 0], [sy, 0, cy]]); Pm = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]); M = Pm @ Rm
    parts = _all_parts(model)
    allp = np.concatenate([np.asarray(s["grid"], float).reshape(-1, 3) for p in parts for s in p["surfaces"]]) @ M.T
    lo, hi = allp.min(0), allp.max(0)
    sc = (size - 40) / max(hi[0] - lo[0], hi[1] - lo[1]); mid = (lo + hi) / 2
    step = 0.45 / sc
    out = np.empty((size, size, 3), np.uint8); out[:] = bg
    depth = np.full((size, size), -1e9)
    L = np.array([0.3, 0.8, 0.52]); L /= np.linalg.norm(L); Lv = M @ L
    for part in parts:
        for s in part["surfaces"]:
            g = np.asarray(s["grid"], float); nn = np.asarray(s["normals"], float); r = s["rect"]
            R, C = g.shape[:2]
            Ps, Ns, Us, Vs = [], [], [], []
            if s["mask"]:
                a, b, c, d = g[0, 0], g[0, -1], g[-1, -1], g[-1, 0]
                nu = int(np.linalg.norm(b - a) / step) + 2; nv = int(np.linalg.norm(d - a) / step) + 2
                u, v = np.meshgrid(np.linspace(0, 1, nu), np.linspace(0, 1, nv))
                keep = (np.hypot(u - 0.5, v - 0.5) <= 0.5) if s["mask"] == "disc" else _inside(s["mask"][1], u, v)
                u, v = u[keep], v[keep]
                P = ((1 - u) * (1 - v))[:, None] * a + (u * (1 - v))[:, None] * b + (u * v)[:, None] * c + ((1 - u) * v)[:, None] * d
                N = np.repeat(nn[0, 0][None], len(u), 0)
                Ps.append(P); Ns.append(N); Us.append(u); Vs.append(v)
            else:
                for f in s["faces"]:
                    i, j = f[0]
                    if i + 1 >= R or j + 1 >= C: continue
                    a, b, c, d = g[i, j], g[i, j + 1], g[i + 1, j + 1], g[i + 1, j]
                    nu = int(max(np.linalg.norm(b - a), np.linalg.norm(c - d)) / step) + 2
                    nv = int(max(np.linalg.norm(d - a), np.linalg.norm(c - b)) / step) + 2
                    fu, fv = np.meshgrid(np.linspace(0, 1, nu), np.linspace(0, 1, nv)); fu, fv = fu.ravel(), fv.ravel()
                    w = [((1 - fu) * (1 - fv))[:, None], (fu * (1 - fv))[:, None], (fu * fv)[:, None], ((1 - fu) * fv)[:, None]]
                    Ps.append(w[0] * a + w[1] * b + w[2] * c + w[3] * d)
                    Ns.append(w[0] * nn[i, j] + w[1] * nn[i, j + 1] + w[2] * nn[i + 1, j + 1] + w[3] * nn[i + 1, j])
                    Us.append((j + fu) / max(1, C - 1)); Vs.append((i + fv) / max(1, R - 1))
            if not Ps: continue
            P = np.concatenate(Ps) @ M.T; N = np.concatenate(Ns) @ M.T; U = np.concatenate(Us); V = np.concatenate(Vs)
            tx = np.clip((U * r["w"]).astype(int), 0, r["w"] - 1) + r["x"]; ty = np.clip((V * r["h"]).astype(int), 0, r["h"] - 1) + r["y"]
            shade = 0.45 + 0.55 * np.abs(N @ Lv) / (np.linalg.norm(N, axis=1) + 1e-9)
            col = (tex[ty, tx, :3].astype(float) * shade[:, None]).clip(0, 255).astype(np.uint8)
            sx = np.round(size / 2 + (P[:, 0] - mid[0]) * sc).astype(int); sy_ = np.round(size / 2 - (P[:, 1] - mid[1]) * sc).astype(int)
            ok = (sx >= 0) & (sx < size) & (sy_ >= 0) & (sy_ < size)
            sx, sy_, zz, col = sx[ok], sy_[ok], P[ok, 2], col[ok]
            o = np.argsort(zz); sx, sy_, zz, col = sx[o], sy_[o], zz[o], col[o]
            cl = zz > depth[sy_, sx]
            depth[sy_[cl], sx[cl]] = zz[cl]; out[sy_[cl], sx[cl]] = col[cl]
    img = Image.fromarray(out)
    if crop:
        fg = depth > -1e8; ys, xs = np.where(fg)
        img = img.crop((max(0, xs.min() - 12), max(0, ys.min() - 12), min(size, xs.max() + 12), min(size, ys.max() + 12)))
    return img
