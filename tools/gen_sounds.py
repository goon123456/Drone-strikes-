#!/usr/bin/env python3
"""Installs the mod's own sounds (drone motors, aircraft engines, jammer, video static, cable snap).
The .ogg masters live in tools/sounds/ (mono, 44.1 kHz, loops cut to repeat cleanly); everything else the mod
plays comes from Superb Warfare. Called by gen_assets.py; copies the files and writes sounds.json."""
import json, os, shutil

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sounds")
OUT = "src/main/resources/assets/steelrain"

# event -> (files, attenuation distance in blocks or None for the default 16)
EVENTS = {
    "fpv_low": (["fpv_low"], 48), "fpv_medium": (["fpv_medium"], 56), "fpv_high": (["fpv_high"], 64),
    "fpv_accelerate": (["fpv_accelerate"], 48),
    "mavic_low": (["mavic_low"], 32), "mavic_medium": (["mavic_medium"], 36), "mavic_high": (["mavic_high"], 40),
    "mavic_accelerate": (["mavic_accelerate"], 32),
    "shahed_engine": (["shahed_engine"], 160), "reaper_engine": (["reaper_engine"], 128),
    "tb2_engine": (["tb2_engine"], 112), "orlan_engine": (["orlan_engine"], 64),
    "global_hawk_jet": (["global_hawk_jet"], 192),
    "jammer_hum": (["jammer_hum"], 24),
    "static_light": (["static_light"], None), "static_medium": (["static_medium"], None), "static_heavy": (["static_heavy"], None),
    "cable_snap": (["cable_snap_1", "cable_snap_2", "cable_snap_3"], None),
}


def main():
    os.makedirs(f"{OUT}/sounds", exist_ok=True)
    out = {}
    for event, (files, distance) in EVENTS.items():
        sounds = []
        for f in files:
            shutil.copyfile(f"{SRC}/{f}.ogg", f"{OUT}/sounds/{f}.ogg")
            entry = {"name": f"steelrain:{f}"}
            if distance:
                entry["attenuation_distance"] = distance
            sounds.append(entry)
        out[event] = {"subtitle": f"subtitles.steelrain.{event}", "sounds": sounds}
    with open(f"{OUT}/sounds.json", "w") as fh:
        json.dump(out, fh, indent=2)


if __name__ == "__main__":
    main()
