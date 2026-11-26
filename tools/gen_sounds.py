#!/usr/bin/env python3
"""Synthesises the sounds Superb Warfare does not already provide (drone motors, aircraft, jammer, static).
Needs numpy and ffmpeg. Called by gen_assets.py; writes .ogg files and sounds.json into the mod's assets."""
import json, os, subprocess, wave
import numpy as np

SR = 44100
OUT = "src/main/resources/assets/steelrain"
rng = np.random.default_rng(7)


def t(seconds):
    return np.arange(int(SR * seconds)) / SR


def noise(seconds, lo, hi, tilt=0.0):
    """Band-limited noise built in the frequency domain, so it loops with no click."""
    n = int(SR * seconds)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec = (rng.normal(size=f.size) + 1j * rng.normal(size=f.size)) * ((f >= lo) & (f <= hi))
    spec *= np.where(f > 0, (np.maximum(f, 1) / 1000.0) ** tilt, 0)
    x = np.fft.irfft(spec, n)
    return x / (np.abs(x).max() + 1e-9)


def harmonics(seconds, f0, count, rolloff, jitter=True):
    """Buzzy periodic tone: f0 must be a multiple of 1/seconds for a clean loop."""
    tt = t(seconds)
    x = np.zeros_like(tt)
    for k in range(1, count + 1):
        if f0 * k > SR / 2.2:
            break
        x += np.sin(2 * np.pi * f0 * k * tt + (rng.uniform(0, 6.28) if jitter else 0)) / k ** rolloff
    return x / (np.abs(x).max() + 1e-9)


def save(name, x, gain=0.8):
    x = np.tanh(x * 1.4)
    x = x / (np.abs(x).max() + 1e-9) * gain
    os.makedirs(f"{OUT}/sounds", exist_ok=True)
    wav = f"{OUT}/sounds/{name}.wav"
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", "-ac", "1",
                    f"{OUT}/sounds/{name}.ogg"], check=True)
    os.remove(wav)


def main():
    D = 2.0
    tt = t(D)
    # FPV racing quad: four screaming motors a few Hz apart, lots of upper harmonics.
    fpv = sum(harmonics(D, f, 28, 0.6) for f in (205, 212.5, 219, 198.5)) / 4
    fpv = fpv * (1 + 0.15 * np.sin(2 * np.pi * 7 * tt)) + 0.12 * noise(D, 2500, 9000)
    save("fpv_motor", fpv)
    # Mavic: lower, smoother, more of a hum with a soft whoosh.
    mavic = sum(harmonics(D, f, 14, 1.1) for f in (140, 146.5, 151, 137.5)) / 4
    save("mavic_motor", mavic + 0.2 * noise(D, 800, 5000, -0.5), 0.6)
    # Shahed: the two-stroke "moped". Harsh 50 Hz pulse train with an uneven beat and rattle.
    shahed = harmonics(D, 50, 60, 0.45) * (1 + 0.45 * np.sin(2 * np.pi * 25 * tt)) * (1 + 0.2 * np.sin(2 * np.pi * 3.5 * tt))
    save("shahed_engine", shahed + 0.25 * noise(D, 300, 4000) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * 50 * tt))))
    # Turboprop pusher (Reaper, Bayraktar, Orlan): blade-pass drone under broadband wash.
    prop = harmonics(D, 80, 20, 1.0) * (1 + 0.1 * np.sin(2 * np.pi * 2 * tt))
    save("turboprop", prop + 0.45 * noise(D, 120, 3000, -0.8), 0.7)
    # Jet (Global Hawk): shaped roar with a thin turbine whine on top.
    jet = noise(D, 150, 6000, -0.4) + 0.06 * np.sin(2 * np.pi * 2200 * tt) + 0.04 * np.sin(2 * np.pi * 3100 * tt)
    save("jet", jet, 0.7)
    # Jammer: mains hum with a crackle riding on it.
    crackle = noise(D, 3000, 12000) * (rng.random(tt.size) > 0.985)
    hum = np.sin(2 * np.pi * 100 * tt) + 0.5 * np.sin(2 * np.pi * 200 * tt) + 0.4 * np.sin(2 * np.pi * 50 * tt)
    save("jammer_hum", hum * 0.5 + 0.8 * np.convolve(crackle, np.ones(30) / 6, mode="same"), 0.55)
    # Video static for a jammed feed.
    steps = np.repeat(rng.uniform(0.4, 1.0, 40), int(SR * 1.0 / 40) + 1)[: int(SR * 1.0)]
    save("static", noise(1.0, 400, 14000) * steps, 0.6)
    # Fibre cable parting: a sharp twang.
    ts = t(0.4)
    sweep = np.sin(2 * np.pi * (800 * ts + (2200 / 12) * (1 - np.exp(-12 * ts)))) * np.exp(-9 * ts)
    save("cable_snap", sweep + 0.5 * noise(0.4, 2000, 9000) * np.exp(-40 * ts))

    names = ["fpv_motor", "mavic_motor", "shahed_engine", "turboprop", "jet", "jammer_hum", "static", "cable_snap"]
    far = {"shahed_engine": 96, "turboprop": 112, "jet": 160, "fpv_motor": 40, "mavic_motor": 28, "jammer_hum": 24}
    sounds = {n: {"subtitle": f"subtitles.steelrain.{n}", "sounds": [
        {"name": f"steelrain:{n}", **({"attenuation_distance": far[n]} if n in far else {})}]} for n in names}
    with open(f"{OUT}/sounds.json", "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
