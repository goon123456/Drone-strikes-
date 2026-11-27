#!/usr/bin/env python3
"""Makes the small-arms sounds from nothing but arithmetic: each report is a burst of shaped noise (the muzzle
blast), a falling thump (the body of the gun and the ground), a sharp front edge and a tail of echoes; the
mechanical sounds are short struck-metal clicks. They are written to tools/sounds/ as mono 44.1 kHz .ogg files,
where gen_sounds.py picks them up. Replace any of them with a recording of the same name and it is used instead.

Needs numpy and ffmpeg:  python3 tools/gen_gun_sounds.py
"""
import os, subprocess, tempfile, wave
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sounds")


def noise(n, rng):
    return rng.standard_normal(n)


def lowpass(x, hz):
    a = np.exp(-2 * np.pi * hz / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = a * acc + (1 - a) * v
        y[i] = acc
    return y


def highpass(x, hz):
    return x - lowpass(x, hz)


def band(x, lo, hi):
    return highpass(lowpass(x, hi), lo)


def env(n, attack, decay):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(attack, 1e-5)) * np.exp(-t / decay)


def echoes(x, rng, length, density, decay, tone):
    """A tail of reflections: the dry sound repeated at random delays, getting duller and quieter."""
    out = np.zeros(len(x) + int(length * SR))
    out[:len(x)] += x
    soft = lowpass(x, tone)
    for _ in range(density):
        d = int(rng.uniform(0.012, length) * SR)
        g = np.exp(-d / SR / decay) * rng.uniform(0.15, 0.5) * rng.choice((-1, 1))
        out[d:d + len(x)] += soft[:len(out) - d][:len(x)] * g
    return out


def report(seed, crack=1.0, blast_hz=(400, 5200), blast_decay=0.045, thump_hz=(150, 55), thump=0.9, thump_decay=0.09, tail=0.9, tail_decay=0.28,
           tone=2400, length=0.28):
    rng = np.random.default_rng(seed)
    n = int(length * SR)
    t = np.arange(n) / SR
    blast = band(noise(n, rng), *blast_hz) * env(n, 0.0004, blast_decay)
    snap = highpass(noise(n, rng), 3200) * env(n, 0.0002, 0.006) * crack
    f = thump_hz[1] + (thump_hz[0] - thump_hz[1]) * np.exp(-t / 0.03)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.001, thump_decay) * thump
    dry = blast * 1.6 + snap * 1.2 + body
    wet = echoes(dry, rng, tail, 26, tail_decay, tone)
    return np.tanh(wet * 1.7)


def far(seed, low=700, decay=0.5, length=1.4):
    """The same shot from a long way off: the highs are gone and the echoes are most of it."""
    rng = np.random.default_rng(seed)
    n = int(0.3 * SR)
    t = np.arange(n) / SR
    dry = lowpass(noise(n, rng), low) * env(n, 0.004, 0.06) * 2 + np.sin(2 * np.pi * 70 * t) * env(n, 0.004, 0.12)
    wet = lowpass(echoes(dry, rng, length, 40, decay, low * 0.7), low)
    return np.tanh(wet * 2.2) * 0.8


def suppressed(seed, hz=(500, 2600), decay=0.03):
    rng = np.random.default_rng(seed)
    n = int(0.22 * SR)
    t = np.arange(n) / SR
    hiss = band(noise(n, rng), *hz) * env(n, 0.002, decay)
    action = click(seed + 1, 2400, 0.012, 0.5)
    out = hiss * 1.4 + np.sin(2 * np.pi * 180 * t) * env(n, 0.002, 0.03) * 0.5
    out[:len(action)] += action[:len(out)] * 0.6
    return np.tanh(echoes(out, rng, 0.25, 8, 0.08, 1500) * 1.3) * 0.7


def click(seed, hz, decay, level=1.0, ring=None, length=None):
    """Metal on metal: a tick of noise and a short ring at a couple of pitches."""
    rng = np.random.default_rng(seed)
    n = int((length or decay * 8) * SR)
    t = np.arange(n) / SR
    out = highpass(noise(n, rng), 1200) * env(n, 0.0003, decay * 0.5)
    for k, f in enumerate(ring or (hz, hz * 1.63, hz * 2.71)):
        out += np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) * env(n, 0.0005, decay * (1.2 - 0.25 * k)) * (0.6 - 0.15 * k)
    return np.tanh(out * level)


def seq(parts, length):
    """Clicks laid one after another: (start time, sound)."""
    out = np.zeros(int(length * SR))
    for at, s in parts:
        i = int(at * SR)
        s = s[:len(out) - i]
        out[i:i + len(s)] += s
    return np.clip(out, -1, 1)


def slide(seed, length, lo, hi, level=0.35):
    """Something dragged: filtered noise swelling and fading."""
    rng = np.random.default_rng(seed)
    n = int(length * SR)
    t = np.arange(n) / SR
    return band(noise(n, rng), lo, hi) * np.sin(np.pi * t / length) ** 2 * level * 3


def crack(seed):
    rng = np.random.default_rng(seed)
    n = int(0.12 * SR)
    s = highpass(noise(n, rng), 1800) * env(n, 0.0001, 0.004) * 2 + band(noise(n, rng), 600, 3000) * env(n, 0.001, 0.03) * 0.5
    return np.tanh(echoes(s, rng, 0.2, 6, 0.06, 2500) * 2)


def whizz(seed):
    rng = np.random.default_rng(seed)
    n = int(0.3 * SR)
    t = np.arange(n) / SR
    f = 1500 * np.exp(-t / 0.18) + 400
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t / 0.3) ** 2 * 0.5 + band(noise(n, rng), 800, 4000) * np.sin(np.pi * t / 0.3) ** 3 * 1.2
    return np.clip(s, -1, 1)


def ricochet(seed):
    rng = np.random.default_rng(seed)
    n = int(0.55 * SR)
    t = np.arange(n) / SR
    f = 2600 * np.exp(-t / 0.25) + 700 + 120 * np.sin(2 * np.pi * 31 * t)
    whine = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.002, 0.16) * 0.7
    hit = highpass(noise(n, rng), 1500) * env(n, 0.0002, 0.008) * 1.5
    return np.clip(whine + hit, -1, 1)


def build():
    c = click
    return {
        # reports, one voice per gun
        "gun_ak74m_fire": report(1, crack=1.1, blast_hz=(450, 5600), blast_decay=0.040, thump_hz=(170, 60), thump_decay=0.07),
        "gun_m4a1_fire": report(2, crack=1.3, blast_hz=(600, 6500), blast_decay=0.034, thump_hz=(190, 70), thump=0.7, thump_decay=0.06, tone=2900),
        "gun_svd_fire": report(3, crack=1.2, blast_hz=(300, 4600), blast_decay=0.060, thump_hz=(140, 45), thump=1.2, thump_decay=0.12, tail=1.2, tail_decay=0.36, length=0.34),
        "gun_pkm_fire": report(4, crack=1.0, blast_hz=(320, 4800), blast_decay=0.050, thump_hz=(150, 50), thump=1.1, thump_decay=0.10, tail=1.0),
        "gun_m17_fire": report(5, crack=1.4, blast_hz=(700, 5200), blast_decay=0.022, thump_hz=(210, 90), thump=0.5, thump_decay=0.04, tail=0.5, tail_decay=0.16, length=0.2),
        "gun_m590_fire": report(6, crack=0.8, blast_hz=(180, 3600), blast_decay=0.075, thump_hz=(120, 38), thump=1.5, thump_decay=0.14, tail=1.2, tail_decay=0.34, tone=1700, length=0.4),
        "gun_rifle_far": far(11, 800, 0.45), "gun_heavy_far": far(12, 520, 0.6, 1.7), "gun_pistol_far": far(13, 1000, 0.3, 0.9),
        "gun_suppressed": suppressed(21), "gun_pistol_suppressed": suppressed(22, (700, 3200), 0.022),
        # handling
        "gun_mag_out": seq([(0.0, c(31, 1900, 0.010, 0.9)), (0.03, slide(32, 0.16, 500, 2500)), (0.17, c(33, 900, 0.02, 0.5))], 0.36),
        "gun_mag_in": seq([(0.0, slide(34, 0.1, 500, 2200)), (0.10, c(35, 1300, 0.018, 1.0)), (0.135, c(36, 2400, 0.010, 0.8))], 0.34),
        "gun_bolt": seq([(0.0, c(37, 1500, 0.012)), (0.02, slide(38, 0.12, 700, 3500, 0.5)), (0.17, c(39, 1100, 0.022, 1.2)), (0.185, c(40, 2700, 0.012, 0.8))], 0.42),
        "gun_bolt_release": seq([(0.0, c(41, 2300, 0.008, 0.7)), (0.03, c(42, 1200, 0.022, 1.2)), (0.045, c(43, 2900, 0.012, 0.7))], 0.28),
        "gun_dry": seq([(0.0, c(44, 2600, 0.008, 0.8)), (0.012, c(45, 1500, 0.012, 0.5))], 0.14),
        "gun_selector": seq([(0.0, c(46, 3100, 0.007, 0.8)), (0.05, c(47, 2500, 0.008, 0.6))], 0.16),
        "gun_pump": seq([(0.0, c(48, 1200, 0.014)), (0.01, slide(49, 0.13, 400, 2600, 0.6)), (0.15, c(50, 800, 0.03, 1.2)), (0.2, slide(51, 0.12, 400, 2800, 0.6)),
                         (0.32, c(52, 1000, 0.026, 1.3)), (0.335, c(53, 2200, 0.012, 0.7))], 0.56),
        "gun_shell_in": seq([(0.0, slide(54, 0.08, 500, 2000, 0.4)), (0.08, c(55, 1000, 0.016, 0.9)), (0.11, c(56, 1900, 0.010, 0.6))], 0.26),
        "gun_round_in": seq([(0.0, c(57, 2200, 0.008, 0.6)), (0.03, c(58, 1400, 0.012, 0.5))], 0.14),
        "gun_belt": seq([(0.0, c(59, 2800, 0.006, 0.5)), (0.06, c(60, 3200, 0.006, 0.5)), (0.11, c(61, 2600, 0.007, 0.6)), (0.19, c(62, 3000, 0.006, 0.5)),
                         (0.25, c(63, 1600, 0.016, 0.8))], 0.42),
        "gun_cover": seq([(0.0, c(64, 1400, 0.016, 1.0)), (0.02, slide(65, 0.08, 300, 1800, 0.4)), (0.11, c(66, 700, 0.035, 1.0))], 0.34),
        "gun_casing": seq([(0.0, c(67, 5200, 0.012, 0.5, ring=(5200, 7900, 9300))), (0.07, c(68, 5600, 0.010, 0.35, ring=(5600, 8300))),
                           (0.12, c(69, 5400, 0.008, 0.2, ring=(5400, 8100)))], 0.26),
        # bullets going past
        "bullet_crack": crack(71), "bullet_whizz": whizz(72), "bullet_ricochet": ricochet(73),
    }


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, data in build().items():
        data = data / max(1e-9, np.abs(data).max()) * 0.92
        fade = min(len(data), int(0.01 * SR))
        data[-fade:] *= np.linspace(1, 0, fade)
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            path = tmp.name
        with wave.open(path, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
            w.writeframes((data * 32767).astype(np.int16).tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "5", f"{OUT}/{name}.ogg"], check=True)
        os.remove(path)
        print(name, round(len(data) / SR, 2), "s")


if __name__ == "__main__":
    main()
