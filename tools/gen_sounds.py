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

# small arms, made by gen_gun_sounds.py: event -> (attenuation distance, subtitle)
for _gun in ("ak74m", "m4a1", "svd", "pkm", "m17", "m590", "m107", "rpg7", "m18", "m9", "hk416", "g36", "scarh", "m249", "m240", "m110"):
    EVENTS[f"gun_{_gun}_fire"] = ([f"gun_{_gun}_fire"], None, "gun_fire")
for _far in ("gun_rifle_far", "gun_heavy_far", "gun_pistol_far"):
    EVENTS[_far] = ([_far], None, "gun_far")
for _quiet in ("gun_suppressed", "gun_pistol_suppressed"):
    EVENTS[_quiet] = ([_quiet], None, "gun_fire")
for _mech in ("gun_mag_out", "gun_mag_in", "gun_bolt", "gun_bolt_release", "gun_dry", "gun_selector", "gun_pump", "gun_shell_in",
              "gun_round_in", "gun_belt", "gun_cover", "gun_casing", "gun_rocket_in"):
    EVENTS[_mech] = ([_mech], None, "gun_handling")
for _pass in ("bullet_crack", "bullet_whizz", "bullet_ricochet"):
    EVENTS[_pass] = ([_pass], None, "bullet_pass")


def main():
    os.makedirs(f"{OUT}/sounds", exist_ok=True)
    out = {}
    for event, spec in EVENTS.items():
        files, distance = spec[0], spec[1]
        sounds = []
        for f in files:
            shutil.copyfile(f"{SRC}/{f}.ogg", f"{OUT}/sounds/{f}.ogg")
            entry = {"name": f"steelrain:{f}"}
            if distance:
                entry["attenuation_distance"] = distance
            sounds.append(entry)
        out[event] = {"subtitle": f"subtitles.steelrain.{spec[2] if len(spec) > 2 else event}", "sounds": sounds}
    with open(f"{OUT}/sounds.json", "w") as fh:
        json.dump(out, fh, indent=2)


if __name__ == "__main__":
    main()
