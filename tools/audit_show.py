"""Shows the parts behind audit findings: python3 tools/audit_show.py <model> fixed|all <index> ..."""
import sys
sys.path.insert(0, "tools")
import numpy as np
import vehicle_kit as VK
import audit_models as AM
import gen_vehicles as GV

name, which, idx = sys.argv[1], sys.argv[2], [int(i) for i in sys.argv[3:]]
m = VK.finish(GV.MODELS[name]())
tb = {"turret", "barrel"}
fixed = m["base"] + [c for b in m["bones"] if b["name"] not in tb and b["parent"] not in tb and not b["name"].startswith(("wheel", "move_hatch", "track", "move_sway", "move_payload")) for c in b["cubes"]]
allp = m["base"] + m["turret"] + m["barrel"] + [c for b in m["bones"] for c in b["cubes"]]
parts = fixed if which == "fixed" else allp
for i in idx:
    p = parts[i]
    lo, hi = AM.bbox(AM.points(p))
    owner = next((b["name"] for b in m["bones"] if any(c is p for c in b["cubes"])), "base/turret/barrel")
    print(i, owner, p["kind"], p["mat"], "bbox", np.round(lo, 2), np.round(hi, 2), {k: v for k, v in p.items() if k in ("box", "axis", "centre", "a", "profile", "rot")})
