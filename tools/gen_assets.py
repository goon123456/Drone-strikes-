#!/usr/bin/env python3
"""Generates every resource file of the mod (textures, models, lang, recipes, tags).
Run from the repository root:  python3 tools/gen_assets.py
"""
import json, os, random
from PIL import Image, ImageDraw

MOD = "steelrain"
RES = "src/main/resources"
A = f"{RES}/assets/{MOD}"
D = f"{RES}/data/{MOD}"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def noisy(colour, size=16, amount=10, seed=0):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            d = rnd.randint(-amount, amount)
            px[x, y] = tuple(max(0, min(255, c + d)) for c in colour) + (255,)
    return img


# ---------------------------------------------------------------- block textures
OLIVE, OLIVE_D, STEEL, STEEL_D = (92, 104, 62), (66, 76, 46), (150, 154, 158), (62, 66, 70)

def block_textures():
    t = {}
    t["olive"] = noisy(OLIVE, seed=1)
    t["olive_dark"] = noisy(OLIVE_D, seed=2)
    t["steel"] = noisy(STEEL, seed=3)
    t["steel_dark"] = noisy(STEEL_D, seed=4)
    t["white"] = noisy((215, 216, 210), seed=5, amount=6)
    t["tan"] = noisy((184, 172, 140), seed=13, amount=8)
    t["red"] = noisy((190, 40, 36), seed=6)
    t["glass"] = noisy((40, 62, 86), seed=7, amount=5)
    ImageDraw.Draw(t["glass"]).line([(2, 12), (12, 2)], fill=(90, 120, 150, 255))

    p = noisy(OLIVE, seed=8)
    d = ImageDraw.Draw(p)
    d.rectangle([0, 0, 15, 15], outline=OLIVE_D + (255,))
    d.line([(0, 8), (15, 8)], fill=OLIVE_D + (255,))
    for xy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        d.point(xy, fill=(120, 132, 84, 255))
    t["panel"] = p

    tr = noisy((38, 38, 36), seed=9, amount=5)
    d = ImageDraw.Draw(tr)
    for x in range(1, 16, 4):
        d.line([(x, 0), (x, 15)], fill=(20, 20, 20, 255))
    t["tracks"] = tr

    tu = noisy(OLIVE_D, seed=10)
    d = ImageDraw.Draw(tu)
    for cy in (4, 11):
        for cx in (3, 8, 13):
            d.ellipse([cx - 2, cy - 2, cx + 1, cy + 1], fill=(24, 26, 22, 255), outline=(110, 120, 80, 255))
    t["tubes"] = tu

    pad = noisy((54, 56, 58), seed=11, amount=5)
    d = ImageDraw.Draw(pad)
    y = (224, 190, 50, 255)
    d.rectangle([1, 1, 14, 14], outline=y)
    d.line([(5, 4), (5, 11)], fill=y); d.line([(10, 4), (10, 11)], fill=y); d.line([(5, 7), (10, 7)], fill=y)
    t["pad_top"] = pad

    ra = noisy((100, 112, 96), seed=12, amount=5)
    d = ImageDraw.Draw(ra)
    for i in range(0, 16, 4):
        d.line([(i, 0), (i, 15)], fill=(60, 70, 58, 255)); d.line([(0, i), (15, i)], fill=(60, 70, 58, 255))
    t["radar"] = ra
    for name, img in t.items():
        save(img, f"{A}/textures/block/{name}.png")


# ---------------------------------------------------------------- block models
def el(tex, box, rot=None, up=None):
    # Explicit UVs: parts reaching outside the block would otherwise sample neighbouring atlas sprites.
    faces = {f: {"texture": "#" + tex, "uv": [0, 0, 16, 16]} for f in ("north", "south", "east", "west", "up", "down")}
    if up:
        faces["up"] = {"texture": "#" + up, "uv": [0, 0, 16, 16]}
    e = {"from": box[:3], "to": box[3:], "faces": faces}
    if rot:
        e["rotation"] = {"origin": rot[0], "axis": "x", "angle": rot[1]}
    return e

def wheels(zs):
    return [el("tracks", [1, 0, z, 15, 3, z + 3.5]) for z in zs]

BLOCKS = {
    "himars": wheels([1, 8.5, 12]) + [
        el("olive", [2, 2, 0, 14, 5, 16]),
        el("panel", [2, 5, 0, 14, 10, 5]),
        el("glass", [3, 7, -0.2, 13, 9.5, 0]),
        el("panel", [3, 6, 6, 13, 11, 17], ([8, 6, 16], 22.5)),
        el("tubes", [3, 6, 5.8, 13, 11, 6], ([8, 6, 16], 22.5)),
    ],
    "m109": [
        el("tracks", [0, 0, 0, 3, 4, 16]), el("tracks", [13, 0, 0, 16, 4, 16]),
        el("olive", [1, 2, 0, 15, 7, 16]),
        el("panel", [3, 7, 5, 13, 12, 15]),
        el("steel_dark", [7, 9, -11, 9, 11, 5], ([8, 10, 5], 22.5)),
        el("steel_dark", [6.5, 8.5, -13, 9.5, 11.5, -11], ([8, 10, 5], 22.5)),
        el("olive_dark", [5, 12, 9, 8, 13, 12]),
    ],
    "tomahawk_launcher": [
        el("steel_dark", [0, 0, 0, 3, 2, 3]), el("steel_dark", [13, 0, 0, 16, 2, 3]),
        el("steel_dark", [0, 0, 13, 3, 2, 16]), el("steel_dark", [13, 0, 13, 16, 2, 16]),
        el("panel", [1, 1, 1, 15, 13, 15]),
        el("steel_dark", [0, 13, 0, 16, 14, 16]),
        el("olive", [2, 14, 2, 7.5, 15, 7.5]), el("olive", [8.5, 14, 2, 14, 15, 7.5]),
        el("olive", [2, 14, 8.5, 7.5, 15, 14]), el("white", [8.5, 14, 8.5, 14, 15, 14]),
    ],
    "iskander_launcher": wheels([0, 6, 12]) + [
        el("olive", [2, 2, -2, 14, 6, 16]),
        el("panel", [2, 6, -2, 14, 11, 3]),
        el("glass", [3, 8, -2.2, 13, 10.5, -2]),
        el("olive_dark", [4, 6, 13, 12, 20, 14.5]),
        el("olive", [5.5, 6, 7.5, 10.5, 26, 12.5]),
        el("white", [6.5, 26, 8.5, 9.5, 29, 11.5]),
        el("white", [7.5, 29, 9.5, 8.5, 31, 10.5]),
    ],
    "shahed_launcher": [
        el("steel_dark", [1, 0, 1, 15, 2, 15]),
        el("steel_dark", [7, 2, 3, 9, 9, 5]),
        el("steel", [6, 3, -3, 10, 5, 16], ([8, 3, 16], 22.5)),
        el("steel_dark", [5, 5, 10, 11, 6, 15], ([8, 3, 16], 22.5)),
        el("red", [7, 5, -3, 9, 6, -2], ([8, 3, 16], 22.5)),
    ],
    "ground_station": [
        el("steel_dark", [1, 0, 0, 15, 1, 16]),
        el("tan", [2, 1, 1, 14, 10, 15]),
        el("steel_dark", [6, 1, 0.8, 10, 8, 1]),
        el("steel", [7.5, 10, 10, 8.5, 14, 11]),
        el("white", [4, 12, 9, 12, 16, 10]),
        el("red", [13, 10, 2, 14, 15, 3]),
    ],
    "orlan_catapult": [
        el("steel_dark", [5, 0, 2, 11, 2, 14]),
        el("steel_dark", [7, 2, 3, 9, 8, 5]),
        el("steel", [7, 2, -2, 9, 3.5, 16], ([8, 2, 16], 22.5)),
        el("white", [7, 3.5, 2, 9, 5.5, 11], ([8, 2, 16], 22.5)),
        el("white", [1, 5, 5, 15, 5.6, 8], ([8, 2, 16], 22.5)),
    ],
    "trench_jammer": [
        el("panel", [3, 0, 3, 13, 9, 13]),
        el("steel_dark", [4, 9, 4, 12, 10, 12]),
        el("steel", [7.5, 10, 7.5, 8.5, 22, 8.5]),
        el("steel_dark", [3, 19, 7.7, 13, 19.6, 8.3]),
        el("steel_dark", [7.7, 16, 3, 8.3, 16.6, 13]),
        el("red", [7.5, 22, 7.5, 8.5, 23, 8.5]),
    ],
    "krasukha": wheels([1, 6.5, 12]) + [
        el("olive", [2, 2, 0, 14, 6, 16]),
        el("panel", [2, 6, 0, 14, 10, 4]),
        el("glass", [3, 7.5, -0.2, 13, 9.5, 0]),
        el("olive_dark", [2, 6, 5, 14, 11, 12]),
        el("steel", [7.5, 6, 13, 8.5, 14, 14]),
        el("radar", [1, 9, 13.5, 15, 20, 14.5], ([8, 14, 14], -22.5)),
    ],
    "jammer": [
        el("panel", [2, 0, 2, 14, 6, 14]),
        el("steel_dark", [3, 6, 3, 13, 7, 13]),
        el("steel", [7, 7, 7, 9, 24, 9]),
        el("steel_dark", [1, 20, 7.5, 15, 21, 8.5]),
        el("steel_dark", [7.5, 16, 1, 8.5, 17, 15]),
        el("radar", [0, 18, 6, 1, 23, 10]), el("radar", [15, 18, 6, 16, 23, 10]),
        el("radar", [6, 14, 0, 10, 19, 1]), el("radar", [6, 14, 15, 10, 19, 16]),
        el("red", [7.5, 24, 7.5, 8.5, 25, 8.5]),
    ],
}

def block_models():
    for name, elements in BLOCKS.items():
        used = sorted({f["texture"][1:] for e in elements for f in e["faces"].values()})
        textures = {t: f"{MOD}:block/{t}" for t in used}
        textures["particle"] = f"{MOD}:block/olive"
        write(f"{A}/models/block/{name}.json", {"parent": "minecraft:block/block", "textures": textures, "elements": elements})
        write(f"{A}/models/item/{name}.json", {"parent": f"{MOD}:block/{name}"})
        write(f"{A}/blockstates/{name}.json", {"variants": {
            "facing=north": {"model": f"{MOD}:block/{name}"},
            "facing=east": {"model": f"{MOD}:block/{name}", "y": 90},
            "facing=south": {"model": f"{MOD}:block/{name}", "y": 180},
            "facing=west": {"model": f"{MOD}:block/{name}", "y": 270},
        }})
        write(f"{D}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # Invisible filler block of the full-size vehicles.
    write(f"{A}/models/block/weapon_part.json", {"textures": {"particle": f"{MOD}:block/olive"}})
    write(f"{A}/blockstates/weapon_part.json", {"variants": {"": {"model": f"{MOD}:block/weapon_part"}}})
    write(f"{RES}/data/minecraft/tags/block/mineable/pickaxe.json",
          {"replace": False, "values": [f"{MOD}:{n}" for n in BLOCKS] + [f"{MOD}:weapon_part"]})
    write(f"{RES}/data/minecraft/tags/block/needs_stone_tool.json",
          {"replace": False, "values": [f"{MOD}:{n}" for n in BLOCKS]})


# ---------------------------------------------------------------- item icons
def outlined(img, colour=(24, 24, 22, 255)):
    src = img.load()
    out = img.copy()
    dst = out.load()
    for y in range(16):
        for x in range(16):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src[nx, ny][3] != 0:
                        dst[x, y] = colour
                        break
    return out

def canvas():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)

def c(rgb):
    return tuple(rgb) + (255,)

def missile_icon(body, nose, fin, width, a=(3, 12), b=(12, 3), wings=None):
    img, d = canvas()
    d.line([a, b], fill=c(fin), width=width)                       # full length in fin colour
    d.line([(a[0] + 2, a[1] - 2), b], fill=c(body), width=width)   # body
    d.line([(b[0] - 1, b[1] + 1), b], fill=c(nose), width=max(1, width - 1))
    d.line([(a[0] - 1, a[1] - 2), (a[0] + 2, a[1] + 1)], fill=c(fin))  # tail fins
    if wings:
        mx, my = (a[0] + b[0]) // 2, (a[1] + b[1]) // 2
        d.line([(mx - wings, my - wings), (mx + wings, my + wings)], fill=c(fin))
    return outlined(img)

def item_icons():
    icons = {}
    icons["tomahawk_missile"] = missile_icon((188, 192, 198), (120, 124, 130), (90, 94, 100), 2, wings=3)
    icons["iskander_missile"] = missile_icon((88, 104, 62), (220, 220, 214), (52, 60, 40), 3, a=(3, 13), b=(13, 3))
    icons["gmlrs_rocket"] = missile_icon((104, 114, 76), (40, 40, 40), (60, 66, 46), 2, a=(4, 11), b=(11, 4))
    icons["hellfire_missile"] = missile_icon((58, 62, 58), (60, 90, 130), (34, 36, 34), 2, a=(5, 10), b=(10, 5), wings=2)
    icons["pantsir_missile"] = missile_icon((214, 214, 208), (96, 110, 72), (96, 110, 72), 1, a=(2, 13), b=(13, 2))

    img, d = canvas()   # 155 mm shell
    d.rectangle([6, 6, 9, 13], fill=c((84, 96, 58)))
    d.polygon([(6, 6), (9, 6), (8, 2), (7, 2)], fill=c((196, 166, 76)))
    d.line([(6, 11), (9, 11)], fill=c((184, 112, 64)))
    d.line([(6, 8), (9, 8)], fill=c((220, 200, 60)))
    icons["shell_155mm"] = outlined(img)

    img, d = canvas()   # Shahed: delta wing from above
    d.polygon([(8, 2), (2, 12), (13, 12)], fill=c((196, 192, 178)))
    d.line([(7, 3), (7, 13)], fill=c((130, 128, 118))); d.line([(8, 3), (8, 13)], fill=c((130, 128, 118)))
    d.line([(2, 12), (2, 13)], fill=c((60, 60, 58))); d.line([(13, 12), (13, 13)], fill=c((60, 60, 58)))
    d.line([(6, 14), (9, 14)], fill=c((60, 60, 58)))
    icons["shahed_136"] = outlined(img)

    img, d = canvas()   # Reaper from above
    g = (152, 156, 162)
    d.rectangle([7, 2, 8, 13], fill=c(g))
    d.rectangle([1, 6, 14, 7], fill=c(g))
    d.line([(5, 13), (7, 11)], fill=c((110, 114, 120))); d.line([(10, 13), (8, 11)], fill=c((110, 114, 120)))
    d.rectangle([7, 1, 8, 2], fill=c((60, 64, 70)))
    d.point([(4, 8), (11, 8)], fill=c((70, 74, 70)))
    icons["mq9_reaper"] = outlined(img)

    img, d = canvas()   # FPV quad
    d.line([(3, 3), (12, 12)], fill=c((40, 40, 44)), width=1); d.line([(12, 3), (3, 12)], fill=c((40, 40, 44)), width=1)
    for x, y in ((2, 2), (11, 2), (2, 11), (11, 11)):
        d.rectangle([x, y, x + 2, y + 2], fill=c((214, 88, 44)))
    d.rectangle([6, 5, 9, 10], fill=c((66, 68, 74)))
    d.rectangle([7, 4, 8, 5], fill=c((80, 110, 70)))
    icons["fpv_drone"] = outlined(img)

    img, d = canvas()   # goggles / controller
    d.rectangle([2, 6, 13, 11], fill=c((48, 50, 56)))
    d.rectangle([3, 7, 6, 10], fill=c((70, 130, 170))); d.rectangle([9, 7, 12, 10], fill=c((70, 130, 170)))
    d.line([(12, 5), (12, 2)], fill=c((120, 120, 120))); d.point((12, 2), fill=c((220, 60, 50)))
    icons["fpv_controller"] = outlined(img)

    img, d = canvas()   # laptop
    d.rectangle([3, 3, 12, 9], fill=c((40, 46, 40)))
    d.rectangle([4, 4, 11, 8], fill=c((50, 110, 60)))
    d.point([(6, 5), (9, 7), (7, 6)], fill=c((150, 230, 150))); d.point((9, 5), fill=c((240, 70, 60)))
    d.rectangle([2, 10, 13, 12], fill=c((104, 112, 88)))
    d.line([(4, 11), (11, 11)], fill=c((70, 76, 60)))
    icons["field_laptop"] = outlined(img)

    img, d = canvas()   # circuit board
    d.rectangle([3, 4, 12, 11], fill=c((40, 120, 70)))
    d.rectangle([5, 6, 8, 9], fill=c((30, 30, 34)))
    d.point([(4, 5), (11, 5), (4, 10), (11, 10), (10, 7), (10, 9)], fill=c((230, 196, 80)))
    icons["circuit_board"] = outlined(img)

    img, d = canvas()   # electric motor
    d.rectangle([4, 5, 10, 11], fill=c((140, 144, 150)))
    d.rectangle([6, 5, 8, 11], fill=c((196, 118, 62)))
    d.rectangle([11, 7, 13, 8], fill=c((200, 200, 204)))
    icons["electric_motor"] = outlined(img)

    img, d = canvas()   # battery
    d.rectangle([4, 4, 11, 12], fill=c((48, 86, 160)))
    d.rectangle([6, 2, 9, 3], fill=c((190, 190, 190)))
    d.line([(8, 5), (6, 8)], fill=c((250, 220, 60))); d.line([(6, 8), (9, 8)], fill=c((250, 220, 60)))
    d.line([(9, 8), (7, 11)], fill=c((250, 220, 60)))
    icons["drone_battery"] = outlined(img)

    img, d = canvas()   # propeller
    d.line([(2, 4), (13, 11)], fill=c((214, 88, 44)), width=2)
    d.rectangle([7, 7, 8, 8], fill=c((60, 60, 64)))
    icons["propeller"] = outlined(img)

    img, d = canvas()   # rocket engine
    d.rectangle([5, 2, 10, 6], fill=c((120, 124, 130)))
    d.polygon([(6, 7), (9, 7), (11, 11), (4, 11)], fill=c((70, 72, 78)))
    d.polygon([(5, 12), (10, 12), (8, 15), (7, 15)], fill=c((250, 150, 40)))
    icons["rocket_engine"] = outlined(img)

    img, d = canvas()   # warhead
    d.polygon([(7, 2), (8, 2), (11, 9), (11, 13), (4, 13), (4, 9)], fill=c((88, 100, 60)))
    d.line([(4, 10), (11, 10)], fill=c((230, 200, 50)))
    d.rectangle([7, 2, 8, 3], fill=c((200, 50, 40)))
    icons["warhead"] = outlined(img)

    img, d = canvas()   # guidance unit
    d.rectangle([3, 4, 12, 12], fill=c((70, 76, 84)))
    d.ellipse([5, 5, 10, 10], fill=c((60, 120, 190)))
    d.point((7, 7), fill=c((200, 230, 255)))
    d.point([(4, 11), (6, 11), (8, 11), (10, 11)], fill=c((230, 196, 80)))
    icons["guidance_unit"] = outlined(img)


    icons["patriot_missile"] = missile_icon((216, 216, 210), (184, 172, 140), (70, 74, 78), 2, a=(3, 13), b=(13, 3))

    img, d = canvas()   # Mavic quad
    d.line([(3, 3), (12, 12)], fill=c((70, 74, 80))); d.line([(12, 3), (3, 12)], fill=c((70, 74, 80)))
    for x, y in ((2, 2), (11, 2), (2, 11), (11, 11)):
        d.rectangle([x, y, x + 2, y + 2], fill=c((30, 30, 34)))
    d.rectangle([6, 5, 9, 10], fill=c((128, 132, 138)))
    d.rectangle([7, 3, 8, 4], fill=c((40, 90, 150)))
    icons["mavic"] = outlined(img)

    img, d = canvas()   # drone grenade
    d.ellipse([5, 3, 10, 10], fill=c((88, 100, 60)))
    d.rectangle([7, 2, 8, 2], fill=c((196, 166, 76)))
    d.rectangle([7, 11, 8, 12], fill=c((70, 74, 78)))
    d.line([(5, 13), (10, 13)], fill=c((70, 74, 78)))
    icons["drone_grenade"] = outlined(img)

    img, d = canvas()   # Orlan-10 from above
    g = (156, 160, 166)
    d.rectangle([7, 3, 8, 11], fill=c(g))
    d.rectangle([2, 5, 13, 6], fill=c(g))
    d.point([(2, 5), (2, 6), (13, 5), (13, 6)], fill=c((190, 40, 36)))
    d.rectangle([5, 12, 10, 12], fill=c(g))
    d.rectangle([7, 2, 8, 2], fill=c((40, 40, 44)))
    icons["orlan_10"] = outlined(img)

    img, d = canvas()   # Bayraktar TB2 from above: twin booms
    d.rectangle([7, 2, 8, 9], fill=c(g))
    d.rectangle([1, 6, 14, 7], fill=c(g))
    d.line([(5, 8), (5, 13)], fill=c((110, 114, 120))); d.line([(10, 8), (10, 13)], fill=c((110, 114, 120)))
    d.line([(5, 13), (10, 13)], fill=c(g))
    d.rectangle([7, 10, 8, 10], fill=c((40, 40, 44)))
    icons["bayraktar_tb2"] = outlined(img)

    img, d = canvas()   # Global Hawk from above: very long wings, fat nose
    w = (220, 220, 214)
    d.rectangle([7, 4, 8, 13], fill=c(w))
    d.rectangle([6, 1, 9, 4], fill=c(w))
    d.rectangle([0, 8, 15, 8], fill=c(w)); d.rectangle([4, 7, 11, 7], fill=c(w))
    d.line([(5, 14), (7, 12)], fill=c((150, 154, 160))); d.line([(10, 14), (8, 12)], fill=c((150, 154, 160)))
    d.rectangle([7, 9, 8, 11], fill=c((110, 114, 120)))
    icons["global_hawk"] = outlined(img)

    img, d = canvas()   # fibre-optic FPV: cyan props and a cable reel
    d.line([(3, 3), (12, 12)], fill=c((40, 40, 44))); d.line([(12, 3), (3, 12)], fill=c((40, 40, 44)))
    for x, y in ((2, 2), (11, 2), (2, 11), (11, 11)):
        d.rectangle([x, y, x + 2, y + 2], fill=c((64, 200, 255)))
    d.rectangle([6, 5, 9, 10], fill=c((66, 68, 74)))
    d.rectangle([7, 8, 8, 9], fill=c((230, 230, 224)))
    d.rectangle([7, 4, 8, 5], fill=c((80, 110, 70)))
    icons["fiber_fpv_drone"] = outlined(img)

    img, d = canvas()   # spool of fibre
    d.ellipse([3, 3, 12, 12], fill=c((226, 226, 220)))
    d.ellipse([5, 5, 10, 10], fill=c((64, 200, 255)))
    d.rectangle([7, 7, 8, 8], fill=c((60, 64, 70)))
    d.line([(12, 9), (14, 13)], fill=c((64, 200, 255)))
    icons["fiber_spool"] = outlined(img)

    img, d = canvas()   # fibre backpack
    d.rectangle([4, 3, 11, 13], fill=c((66, 76, 46)))
    d.ellipse([5, 5, 10, 10], fill=c((226, 226, 220)))
    d.rectangle([7, 7, 8, 8], fill=c((64, 200, 255)))
    d.line([(4, 3), (2, 6)], fill=c((40, 46, 30))); d.line([(11, 3), (13, 6)], fill=c((40, 46, 30)))
    icons["fiber_backpack"] = outlined(img)

    img, d = canvas()   # jammer backpack
    d.rectangle([4, 6, 11, 14], fill=c((92, 104, 62)))
    d.rectangle([6, 8, 9, 11], fill=c((66, 76, 46)))
    d.line([(5, 6), (5, 1)], fill=c((70, 74, 78))); d.line([(8, 6), (8, 0)], fill=c((70, 74, 78))); d.line([(10, 6), (10, 3)], fill=c((70, 74, 78)))
    d.point((8, 12), fill=c((230, 50, 40)))
    icons["jammer_backpack"] = outlined(img)

    img, d = canvas()   # anti-drone gun: stock, body, panel antenna
    d.line([(2, 12), (6, 9)], fill=c((50, 52, 56)), width=2)
    d.rectangle([5, 7, 10, 9], fill=c((70, 74, 80)))
    d.rectangle([10, 4, 13, 9], fill=c((40, 44, 50)))
    d.rectangle([11, 5, 12, 8], fill=c((64, 200, 255)))
    d.line([(6, 10), (6, 12)], fill=c((50, 52, 56)))
    icons["anti_drone_gun"] = outlined(img)

    img, d = canvas()   # jammer dome
    d.pieslice([3, 4, 12, 16], 180, 360, fill=c((156, 160, 166)))
    d.rectangle([3, 10, 12, 11], fill=c((112, 116, 122)))
    d.line([(8, 4), (8, 1)], fill=c((70, 74, 78)))
    icons["jammer_dome"] = outlined(img)

    def belt(shell, tip, n):
        img, d = canvas()
        d.rectangle([2, 9, 13, 12], fill=c((66, 76, 46)))
        for i in range(n):
            x = 3 + i * (10 // n)
            d.rectangle([x, 4, x + 1, 10], fill=c(shell))
            d.point([(x, 3), (x + 1, 3)], fill=c(tip))
        return outlined(img)
    # High explosive rounds have a yellow tip, armour-piercing a black one.
    icons["ammo_30mm"] = belt((196, 166, 76), (240, 210, 50), 4)
    icons["ammo_35mm"] = belt((196, 166, 76), (240, 210, 50), 3)
    icons["ammo_23mm"] = belt((184, 112, 64), (240, 210, 50), 5)
    icons["ammo_30mm_ap"] = belt((196, 166, 76), (30, 30, 34), 4)
    icons["ammo_35mm_ap"] = belt((196, 166, 76), (30, 30, 34), 3)
    icons["ammo_23mm_ap"] = belt((184, 112, 64), (30, 30, 34), 5)

    def shell(band):
        img, d = canvas()
        d.rectangle([6, 6, 9, 13], fill=c((84, 96, 58)))
        d.polygon([(6, 6), (9, 6), (8, 2), (7, 2)], fill=c((196, 166, 76)))
        d.line([(6, 11), (9, 11)], fill=c((184, 112, 64)))
        d.rectangle([6, 7, 9, 8], fill=c(band))
        return outlined(img)
    icons["shell_cluster"] = shell((230, 230, 224))
    icons["shell_smoke"] = shell((120, 190, 230))
    icons["shell_incendiary"] = shell((230, 80, 40))
    icons["atacms_missile"] = missile_icon((92, 104, 62), (30, 30, 34), (52, 60, 40), 3, a=(4, 12), b=(12, 4))

    for name, img in icons.items():
        save(img, f"{A}/textures/item/{name}.png")
        parent = "minecraft:item/handheld" if name == "anti_drone_gun" else "minecraft:item/generated"
        write(f"{A}/models/item/{name}.json", {"parent": parent, "textures": {"layer0": f"{MOD}:item/{name}"}})


# ---------------------------------------------------------------- entity paint
def entity_textures():
    # One tiling near-white grain. Every code-built model is tinted per box, so this is the only texture they need.
    save(noisy((236, 236, 236), size=16, amount=14, seed=77), f"{A}/textures/entity/paint.png")


# ---------------------------------------------------------------- recipes
def shaped(name, pattern, key, count=1):
    write(f"{D}/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": {k: {"item": v if ":" in v else f"{MOD}:{v}"} for k, v in key.items()},
        "result": {"id": f"{MOD}:{name}", "count": count}})

MC = "minecraft:"
def recipes():
    I, R, C, G, N = MC + "iron_ingot", MC + "redstone", MC + "copper_ingot", MC + "gold_ingot", MC + "iron_nugget"
    B, T, P = MC + "iron_block", MC + "tnt", MC + "gunpowder"
    shaped("circuit_board", ["RGR", "CIC", "RGR"], {"R": R, "G": G, "C": C, "I": I}, 2)
    shaped("electric_motor", ["ICI", "CRC", "ICI"], {"I": I, "C": C, "R": R}, 2)
    shaped("drone_battery", [" C ", "IRI", "ILI"], {"C": C, "I": I, "R": R, "L": MC + "lapis_lazuli"}, 2)
    shaped("propeller", ["N N", " I ", "N N"], {"N": N, "I": I}, 4)
    shaped("rocket_engine", ["IPI", "IBI", "I I"], {"I": I, "P": P, "B": MC + "blaze_powder"})
    shaped("warhead", [" I ", "ITI", "ITI"], {"I": I, "T": T})
    shaped("guidance_unit", ["IGI", "CKC", "IRI"], {"I": I, "G": MC + "glass_pane", "C": "circuit_board", "K": MC + "compass", "R": R})

    shaped("fpv_drone", ["PMP", "BCW", "PMP"], {"P": "propeller", "M": "electric_motor", "B": "drone_battery", "C": "circuit_board", "W": MC + "tnt"})
    shaped("fpv_controller", ["GAG", "ICI", " B "], {"G": MC + "glass_pane", "A": MC + "lightning_rod", "I": I, "C": "circuit_board", "B": "drone_battery"})
    shaped("field_laptop", ["GGG", "CUC", "IBI"], {"G": MC + "glass_pane", "C": "circuit_board", "U": "guidance_unit", "I": I, "B": "drone_battery"})

    shaped("shahed_136", [" W ", "IUI", "IMP"], {"W": "warhead", "I": I, "U": "guidance_unit", "M": "electric_motor", "P": "propeller"})
    shaped("mq9_reaper", ["IUI", "BMB", "IPI"], {"I": B, "U": "guidance_unit", "B": "drone_battery", "M": "electric_motor", "P": "propeller"})
    shaped("hellfire_missile", ["W", "U", "E"], {"W": "warhead", "U": "circuit_board", "E": "rocket_engine"}, 2)
    shaped("tomahawk_missile", [" W ", "IUI", "IEI"], {"W": "warhead", "I": I, "U": "guidance_unit", "E": "rocket_engine"})
    shaped("iskander_missile", ["WWW", "BUB", "EBE"], {"W": "warhead", "B": B, "U": "guidance_unit", "E": "rocket_engine"})
    shaped("gmlrs_rocket", ["W", "C", "E"], {"W": "warhead", "C": "circuit_board", "E": "rocket_engine"}, 3)
    shaped("shell_155mm", [" I ", "ITI", "CPC"], {"I": I, "T": T, "C": C, "P": P}, 4)
    shaped("pantsir_missile", [" C ", "IPI", "IEI"], {"C": "circuit_board", "I": I, "P": P, "E": "rocket_engine"}, 4)

    shaped("tomahawk_launcher", ["IHI", "BCB", "BBB"], {"I": I, "H": MC + "hopper", "B": B, "C": "circuit_board"})
    shaped("iskander_launcher", ["IPI", "BCB", "BMB"], {"I": I, "P": MC + "piston", "B": B, "C": "circuit_board", "M": "electric_motor"})
    shaped("himars", ["III", "MCM", "BBB"], {"I": I, "M": "electric_motor", "C": "circuit_board", "B": B})
    shaped("m109", ["I  ", "BIC", "BMB"], {"I": I, "B": B, "C": "circuit_board", "M": "electric_motor"})
    shaped("shahed_launcher", ["  I", " I ", "BPB"], {"I": I, "B": B, "P": MC + "piston"})
    shaped("ground_station", ["SSS", "CUC", "BBB"], {"S": MC + "smooth_stone", "C": "circuit_board", "U": "guidance_unit", "B": B})
    shaped("orlan_catapult", ["  I", " I ", "IPI"], {"I": I, "P": MC + "piston"})
    shaped("patriot_missile", [" U ", "IWI", "IEI"], {"U": "guidance_unit", "I": I, "W": "warhead", "E": "rocket_engine"}, 2)
    shaped("mavic", ["PGP", "MCM", "PBP"], {"P": "propeller", "G": MC + "glass_pane", "M": "electric_motor", "C": "circuit_board", "B": "drone_battery"})
    shaped("drone_grenade", [" N ", "NGN", " N "], {"N": N, "G": P}, 4)
    shaped("orlan_10", ["IPI", "IUI", " M "], {"I": I, "P": "propeller", "U": "guidance_unit", "M": "electric_motor"})
    shaped("bayraktar_tb2", [" U ", "BMB", "DPD"], {"U": "guidance_unit", "B": B, "M": "electric_motor", "D": "drone_battery", "P": "propeller"})
    shaped("global_hawk", ["DUD", "BEB", "DUD"], {"D": MC + "diamond", "U": "guidance_unit", "B": B, "E": "rocket_engine"})
    shaped("trench_jammer", [" A ", "CDC", "III"], {"A": MC + "lightning_rod", "C": "circuit_board", "D": "drone_battery", "I": I})
    shaped("krasukha", ["AUA", "CUC", "BBB"], {"A": MC + "lightning_rod", "U": "guidance_unit", "C": "circuit_board", "B": B})
    shaped("jammer_dome", [" A ", "ICI", "IDI"], {"A": MC + "lightning_rod", "I": N, "C": "circuit_board", "D": "drone_battery"})
    shaped("jammer_backpack", ["A A", "LCL", "LDL"], {"A": MC + "lightning_rod", "L": MC + "leather", "C": "circuit_board", "D": "drone_battery"})
    shaped("anti_drone_gun", ["  A", "ICD", "I  "], {"A": MC + "lightning_rod", "I": I, "C": "circuit_board", "D": "drone_battery"})
    shaped("fiber_spool", ["GGG", "GSG", "GGG"], {"G": MC + "glass_pane", "S": MC + "stick"}, 2)
    shaped("fiber_backpack", ["L L", "LFL", "LIL"], {"L": MC + "leather", "F": "fiber_spool", "I": I})
    shaped("ammo_30mm", ["NNN", "PPP", "CCC"], {"N": N, "P": P, "C": C}, 2)
    shaped("ammo_35mm", ["NNN", "PPP", "CIC"], {"N": N, "P": P, "C": C, "I": I}, 2)
    shaped("ammo_23mm", ["NNN", "PPP", " C "], {"N": N, "P": P, "C": C}, 2)
    shaped("shell_cluster", [" I ", "GGG", "CPC"], {"I": I, "G": "drone_grenade", "C": C, "P": P}, 2)
    shaped("shell_smoke", [" I ", "IWI", "CPC"], {"I": I, "W": MC + "white_wool", "C": C, "P": P}, 4)
    shaped("shell_incendiary", [" I ", "IZI", "CPC"], {"I": I, "Z": MC + "blaze_powder", "C": C, "P": P}, 4)
    shaped("atacms_missile", ["WWW", "IUI", "EIE"], {"W": "warhead", "I": B, "U": "guidance_unit", "E": "rocket_engine"})
    shaped("ammo_30mm_ap", ["III", "PPP", "CCC"], {"I": I, "P": P, "C": C}, 2)
    shaped("ammo_35mm_ap", ["III", "PPP", "CIC"], {"I": I, "P": P, "C": C}, 2)
    shaped("ammo_23mm_ap", ["III", "PPP", " C "], {"I": I, "P": P, "C": C}, 2)
    write(f"{D}/recipe/fiber_fpv_drone.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": f"{MOD}:fpv_drone"}, {"item": f"{MOD}:fiber_spool"}],
        "result": {"id": f"{MOD}:fiber_fpv_drone", "count": 1}})
    for gadget in ("jammer_backpack", "anti_drone_gun"):
        write(f"{D}/recipe/{gadget}_recharge.json", {
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"item": f"{MOD}:{gadget}"}, {"item": f"{MOD}:drone_battery"}],
            "result": {"id": f"{MOD}:{gadget}", "count": 1}})
    # A fresh battery puts a used Mavic back to full charge.
    write(f"{D}/recipe/mavic_recharge.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": f"{MOD}:mavic"}, {"item": f"{MOD}:drone_battery"}],
        "result": {"id": f"{MOD}:mavic", "count": 1}})
    shaped("jammer", [" A ", "CUC", "BDB"], {"A": MC + "lightning_rod", "C": "circuit_board", "U": "guidance_unit", "B": B, "D": "drone_battery"})


# ---------------------------------------------------------------- tags
def tags():
    opt = lambda i: {"id": i, "required": False}
    # Curios (optional): give players a back slot and allow the backpacks in it.
    write(f"{RES}/data/curios/tags/item/back.json", {"replace": False, "values": [f"{MOD}:fiber_backpack", f"{MOD}:jammer_backpack"]})
    write(f"{D}/curios/entities/steelrain_player.json", {"entities": ["minecraft:player"], "slots": ["back"]})


# ---------------------------------------------------------------- lang
def lang():
    L = {
        "itemGroup.steelrain": "Steel Rain",
        "key.categories.steelrain": "Steel Rain",
        "key.steelrain.exit_drone": "Leave drone camera",
        "item.steelrain.circuit_board": "Circuit Board",
        "item.steelrain.electric_motor": "Electric Motor",
        "item.steelrain.drone_battery": "Drone Battery",
        "item.steelrain.propeller": "Propeller",
        "item.steelrain.rocket_engine": "Rocket Engine",
        "item.steelrain.warhead": "Warhead",
        "item.steelrain.guidance_unit": "Guidance Unit",
        "item.steelrain.field_laptop": "Field Laptop",
        "item.steelrain.fpv_controller": "FPV Goggles & Controller",
        "item.steelrain.fpv_drone": "FPV Kamikaze Drone",
        "item.steelrain.shahed_136": "Shahed-136",
        "item.steelrain.mq9_reaper": "MQ-9 Reaper",
        "item.steelrain.hellfire_missile": "AGM-114 Hellfire",
        "item.steelrain.tomahawk_missile": "Tomahawk Cruise Missile",
        "item.steelrain.iskander_missile": "Iskander-M Ballistic Missile",
        "item.steelrain.gmlrs_rocket": "M31 GMLRS Rocket",
        "item.steelrain.shell_155mm": "155 mm HE Shell",
        "item.steelrain.pantsir_missile": "57E6 Interceptor Missile",
        "block.steelrain.tomahawk_launcher": "Tomahawk Launcher",
        "block.steelrain.iskander_launcher": "Iskander-M Launcher",
        "block.steelrain.himars": "M142 HIMARS",
        "block.steelrain.m109": "M109 Howitzer",
        "block.steelrain.shahed_launcher": "Shahed Launch Rail",
        "block.steelrain.ground_station": "UAV Ground Station",
        "block.steelrain.orlan_catapult": "Orlan Catapult",
        "block.steelrain.weapon_part": "Weapon System",
        "entity.steelrain.gepard": "Flakpanzer Gepard",
        "entity.steelrain.zu_23_2": "ZU-23-2",
        "entity.steelrain.pantsir": "Pantsir-S1",
        "entity.steelrain.patriot": "MIM-104 Patriot",
        "weapon.steelrain.gepard": "35 mm Oerlikon KDA",
        "weapon.steelrain.zu_23_2": "23 mm 2A14",
        "weapon.steelrain.pantsir": "30 mm 2A38M",
        "block.steelrain.trench_jammer": "Trench Jammer",
        "block.steelrain.krasukha": "Krasukha-4 Long-Range Jammer",
        "item.steelrain.fiber_fpv_drone": "Fiber-Optic FPV Drone",
        "item.steelrain.fiber_backpack": "Fiber Spool Backpack",
        "item.steelrain.fiber_spool": "Fiber Spool",
        "item.steelrain.jammer_backpack": "Jammer Backpack",
        "item.steelrain.anti_drone_gun": "Anti-Drone Gun",
        "item.steelrain.jammer_dome": "Jammer Dome",
        "item.steelrain.ammo_30mm": "30 mm HE Belt",
        "item.steelrain.ammo_35mm": "35 mm HE Belt",
        "item.steelrain.ammo_23mm": "23 mm HE Box",
        "item.steelrain.ammo_30mm_ap": "30 mm AP Belt",
        "item.steelrain.ammo_35mm_ap": "35 mm AP Belt",
        "item.steelrain.ammo_23mm_ap": "23 mm AP Box",
        "item.steelrain.shell_cluster": "155 mm Cluster Shell",
        "item.steelrain.shell_smoke": "155 mm Smoke Shell",
        "item.steelrain.shell_incendiary": "155 mm Incendiary Shell",
        "item.steelrain.atacms_missile": "MGM-140 ATACMS",
        "entity.steelrain.shell_cluster": "Cluster Shell",
        "entity.steelrain.shell_smoke": "Smoke Shell",
        "entity.steelrain.shell_incendiary": "Incendiary Shell",
        "entity.steelrain.atacms": "ATACMS",
        "entity.steelrain.bomblet": "Bomblet",
        "entity.steelrain.smoke_cloud": "Smoke Screen",
        "message.steelrain.loaded": "Loaded %s: %s / %s",
        "message.steelrain.load_full": "Full or wrong type: %s %s / %s",
        "message.steelrain.fuel_level": "Fuel: %s / %s mB",
        "entity.steelrain.fiber_fpv_drone": "Fiber-Optic FPV Drone",
        "entity.steelrain.bullet": "Cannon Round",
        "entity.steelrain.seat": "Gunner Seat",
        "curios.identifier.back": "Back",
        "tooltip.steelrain.fiber_fpv": "Wired FPV: cannot be jammed. Needs a Fiber Spool Backpack",
        "tooltip.steelrain.cable": "Cable: %s / %s blocks",
        "tooltip.steelrain.fiber_backpack": "Click Fiber Spools onto it to add 250 blocks each",
        "tooltip.steelrain.switched_on": "Switched ON",
        "tooltip.steelrain.switched_off": "Switched off",
        "tooltip.steelrain.jammer_backpack": "Right-click to switch. Jams enemy drones around you",
        "tooltip.steelrain.anti_drone_gun": "Hold use while aiming at a drone to cut its link",
        "message.steelrain.no_spool": "You need a Fiber Spool Backpack",
        "message.steelrain.spool_empty": "The spool is empty",
        "message.steelrain.cable_snapped": "Cable snapped: out of line",
        "message.steelrain.cable_snagged": "Cable snagged and broke",
        "message.steelrain.cable_cut": "Cable cut by an explosion",
        "message.steelrain.flat_battery": "Battery is flat",
        "message.steelrain.jammer_on": "Jammer ON",
        "message.steelrain.jammer_off": "Jammer off",
        "message.steelrain.dome_fitted": "Jammer Dome fitted",
        "message.steelrain.dome_refused": "This vehicle cannot take a dome",
        "message.steelrain.laptop_jammed": "Link jammed: you are inside an enemy jammer's bubble",
        "hud.steelrain.cable": "CABLE %s m",
        "hud.steelrain.jammed": "! JAMMING !",
        "gui.steelrain.switch_on": "Power: ON",
        "gui.steelrain.switch_off": "Power: OFF",
        "gui.steelrain.belts": "Belts",
        "gui.steelrain.hint_manned": "Right-click to man. Sneak-click: ammo",
        "item.steelrain.mavic": "DJI Mavic Recon Drone",
        "item.steelrain.drone_grenade": "Drone Grenade",
        "item.steelrain.orlan_10": "Orlan-10",
        "item.steelrain.bayraktar_tb2": "Bayraktar TB2",
        "item.steelrain.global_hawk": "RQ-4 Global Hawk",
        "item.steelrain.patriot_missile": "PAC-2 Interceptor Missile",
        "entity.steelrain.mavic": "Mavic",
        "entity.steelrain.drone_grenade": "Drone Grenade",
        "entity.steelrain.orlan_10": "Orlan-10",
        "entity.steelrain.bayraktar_tb2": "Bayraktar TB2",
        "entity.steelrain.global_hawk": "RQ-4 Global Hawk",
        "entity.steelrain.patriot_missile": "Patriot Interceptor",
        "entity.steelrain.operator_body": "Drone Operator",
        "tooltip.steelrain.footprint": "Full size: %s wide, %s high, %s long. Needs clear space",
        "tooltip.steelrain.mavic": "Reusable recon quadcopter. Pair the goggles, fly, scroll to zoom",
        "tooltip.steelrain.charge": "Charge: %s s",
        "message.steelrain.no_space": "Not enough room: needs %s x %s x %s blocks clear (blocked at %s, %s, %s)",
        "message.steelrain.no_aircraft": "No aircraft loaded",
        "message.steelrain.runway_ground": "Runway needs %s blocks of flat solid ground: gap at %s, %s, %s",
        "message.steelrain.runway_blocked": "Runway needs %s clear blocks: obstacle at %s, %s, %s",
        "message.steelrain.aircraft_jammed": "%s: link jammed, returning to base",
        "message.steelrain.aircraft_returning": "%s: low on fuel, returning to base",
        "message.steelrain.aircraft_lost": "%s lost",
        "message.steelrain.aircraft_landed": "%s has landed",
        "message.steelrain.not_airborne": "No aircraft in the air",
        "message.steelrain.recalled": "Aircraft recalled",
        "message.steelrain.feed_busy": "Already watching a feed",
        "message.steelrain.feed_unavailable": "Camera feed unavailable",
        "message.steelrain.feed_attacked": "You are under attack! Feed closed",
        "message.steelrain.grenade_loaded": "Grenade loaded",
        "hud.steelrain.zoom": "ZOOM %sx",
        "hud.steelrain.position": "POS %s, %s",
        "hud.steelrain.feed_hint": "[%s] or [Sneak] leave   [Scroll] zoom",
        "hud.steelrain.hint_mavic": "[%s] leave   [Attack] drop grenade   [Jump/Sneak] up/down   [Scroll] zoom",
        "hud.steelrain.grenade_ready": "GRENADE READY",
        "hud.steelrain.grenade_none": "NO GRENADE",
        "gui.steelrain.camera": "CAM",
        "gui.steelrain.recall": "RTB",
        "gui.steelrain.retask": "RETASK",
        "gui.steelrain.airborne": "AIRBORNE",
        "gui.steelrain.aircraft": "Aircraft",
        "gui.steelrain.hint_station": "Needs a clear runway in front",
        "block.steelrain.jammer": "EW Jammer",
        "entity.steelrain.fpv_drone": "FPV Drone",
        "entity.steelrain.shahed_136": "Shahed-136",
        "entity.steelrain.mq9_reaper": "MQ-9 Reaper",
        "entity.steelrain.tomahawk": "Tomahawk",
        "entity.steelrain.iskander": "Iskander-M",
        "entity.steelrain.gmlrs": "GMLRS Rocket",
        "entity.steelrain.shell_155mm": "155 mm Shell",
        "entity.steelrain.hellfire": "Hellfire",
        "entity.steelrain.pantsir_missile": "Interceptor Missile",
        "tooltip.steelrain.laptop_links": "Linked launchers: %s/%s",
        "tooltip.steelrain.laptop_hint": "Right-click a launcher to link it, right-click the air to open",
        "tooltip.steelrain.controller_linked": "Paired with a drone. Right-click to fly",
        "tooltip.steelrain.controller_unlinked": "Right-click an FPV drone to pair",
        "tooltip.steelrain.fpv_drone": "Place it, pair the goggles, then fly it into a target",
        "message.steelrain.linked": "%s linked to laptop",
        "message.steelrain.unlinked": "%s unlinked",
        "message.steelrain.not_linkable": "This system works on its own and cannot be linked",
        "message.steelrain.not_linked": "That launcher is not linked to this laptop",
        "message.steelrain.no_signal": "No signal: launcher is too far away",
        "message.steelrain.busy": "Launcher is busy",
        "message.steelrain.no_ammo": "No ammunition loaded",
        "message.steelrain.no_fuel": "Not enough energy (needs %s FE)",
        "message.steelrain.too_close": "Target too close (minimum %s blocks)",
        "message.steelrain.too_far": "Target out of range (maximum %s blocks)",
        "message.steelrain.launched": "Launch! Target %s, %s",
        "message.steelrain.impact": "%s impact at %s, %s, %s",
        "message.steelrain.unknown_weapon": "Unknown weapon",
        "message.steelrain.fpv_linked": "Drone paired. Right-click the air to fly",
        "message.steelrain.fpv_not_linked": "No drone paired. Right-click a drone first",
        "message.steelrain.fpv_not_found": "Paired drone not found",
        "message.steelrain.fpv_busy": "Someone else is flying this drone",
        "message.steelrain.fpv_no_battery": "Drone battery is empty",
        "message.steelrain.fpv_out_of_range": "Drone is out of range",
        "message.steelrain.fpv_signal_lost": "Signal lost",
        "message.steelrain.fpv_jammed": "Signal jammed!",
        "message.steelrain.fpv_destroyed": "Drone destroyed",
        "hud.steelrain.battery": "BAT %s%%",
        "hud.steelrain.signal": "SIG %s%%",
        "hud.steelrain.altitude": "ALT %s",
        "hud.steelrain.speed": "SPD %s m/s",
        "hud.steelrain.distance": "DST %s m",
        "hud.steelrain.hint": "[%s] leave   [Attack] detonate   [Jump/Sneak] up/down   [Scroll] zoom",
        "gui.steelrain.ammo": "Ammo",
        "gui.steelrain.fuel": "Energy",
        "gui.steelrain.fuel_amount": "%s / %s kFE",
        "gui.steelrain.ready": "READY",
        "gui.steelrain.busy": "BUSY %ss",
        "gui.steelrain.hint_launcher": "Fire it from a Field Laptop",
        "gui.steelrain.hint_air_defence": "Needs fuel for radar and turret",
        "gui.steelrain.hint_jammer": "Jams hostile links (needs energy)",
        "gui.steelrain.laptop_title": "FIRE CONTROL",
        "gui.steelrain.no_links": "No launchers linked. Right-click a launcher with the laptop.",
        "gui.steelrain.offline": "Out of reach",
        "gui.steelrain.centre": "Centre",
        "gui.steelrain.launch": "LAUNCH",
        "gui.steelrain.range": "Rng %s-%s",
        "gui.steelrain.ammo_count": "Ammo: %s",
        "gui.steelrain.fuel_count": "Energy: %s kFE",
        "gui.steelrain.distance": "Dist: %s",
        "gui.steelrain.pick_target": "Pick a target",
        "gui.steelrain.no_signal": "NO SIGNAL",
        "gui.steelrain.rounds": "Rounds",
        "gui.steelrain.map_hint": "Left-click: target   Right-drag: pan   Scroll: zoom",
    }
    write(f"{A}/lang/en_us.json", L)


# ---------------------------------------------------------------- Superb Warfare vehicles
# Box lists use vehicle space: x across, y up, z towards the front, origin on the ground under the middle, in blocks.
OL, OD, OLL, TY, ST, SD, GL, WH, SA, SAD, BK, RD = (0x5C683E, 0x424C2E, 0x6E7C4C, 0x242426, 0x8A8E94, 0x3E4246,
                                                    0x28405A, 0xD8D8D2, 0xB8AC8C, 0x8E846A, 0x18181A, 0xBE2824)
PALETTE = [OL, OD, OLL, TY, ST, SD, GL, WH, SA, SAD, BK, RD]

def pair(c, x, y, z, w, h, d):
    return [(c, x, y, z, w, h, d), (c, -x - w, y, z, w, h, d)]

def vehicle_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rnd = random.Random(5)
    for i, col in enumerate(PALETTE):
        ox, oy = (i % 8) * 8, (i // 8) * 8
        for y in range(8):
            for x in range(8):
                n = rnd.randint(-7, 7)
                img.putpixel((ox + x, oy + y), tuple(max(0, min(255, ((col >> s) & 255) + n)) for s in (16, 8, 0)) + (255,))
    save(img, f"{A}/textures/bedrock/vehicle/steelrain_vehicles.png")

def cube(box):
    c, x, y, z, w, h, d = box
    i = PALETTE.index(c)
    u, v = (i % 8) * 8 + 1, (i // 8) * 8 + 1
    face = {"uv": [u, v], "uv_size": [6, 6]}
    # Bedrock models face -Z, so the vehicle's front (+z here) becomes -Z.
    return {"origin": [round(x * 16, 3), round(y * 16, 3), round(-(z + d) * 16, 3)],
            "size": [round(w * 16, 3), round(h * 16, 3), round(d * 16, 3)],
            "uv": {f: face for f in ("north", "south", "east", "west", "up", "down")}}

def piv(p):
    return [round(p[0] * 16, 3), round(p[1] * 16, 3), round(-p[2] * 16, 3)]

def sbw_vehicle(name, base, turret, barrel, turret_pivot, barrel_pivot, data):
    bones = [{"name": "root", "pivot": [0, 0, 0]},
             {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [cube(b) for b in base]},
             {"name": "turret", "parent": "root", "pivot": piv(turret_pivot), "cubes": [cube(b) for b in turret]},
             {"name": "barrel", "parent": "turret", "pivot": piv(barrel_pivot), "cubes": [cube(b) for b in barrel]}]
    write(f"{A}/models/bedrock/vehicle/{name}.geo.json", {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{name}", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 16, "visible_bounds_height": 8, "visible_bounds_offset": [0, 3, 0]},
        "bones": bones}]})
    write(f"{A}/sbw/vehicles/{name}.json", {"Models": [{
        "Model": f"{MOD}:models/bedrock/vehicle/{name}.geo.json",
        "Texture": f"{MOD}:textures/bedrock/vehicle/steelrain_vehicles.png"}]})
    full = {"MaxEnergy": 2000000, "KeepChunkLoaded": False,
            "TurretPos": list(turret_pivot),
            "BarrelPos": [round(barrel_pivot[i] - turret_pivot[i], 3) for i in range(3)],
            "SeekInfo": {"MinSeekRange": 3, "MaxSeekRange": 128, "SeekIterative": 10, "MinTargetSize": 0.2, "SeekEnergyCost": 500},
            "VehicleIcon": "superbwarfare:textures/vehicle_icon/hpj_11_icon.png",
            "DamageModifiers": ["All - 6", "All * 0.4", "minecraft:explosion * 5", "#superbwarfare:projectile * 0.3"],
            "DestroyInfo": {"ExplosionDamage": 60, "ExplosionRadius": 5}}
    full.update(data)
    write(f"{D}/sbw/vehicles/{name}.json", full)

def cannon(name, belt, rounds, rpm, damage, muzzles, lead):
    """An automatic cannon firing Superb Warfare's real shell projectile. HE and AP belts are separate ammo types."""
    w = {"DefaultFireMode": "Auto", "AvailableFireModes": "Auto",
         "Projectile": "superbwarfare:small_cannon_shell", "ShellType": "AA",
         "AmmoType": [f"{rounds} {MOD}:{belt}",
                      {"Ammo": f"{rounds} {MOD}:{belt}_ap",
                       "Override": {"Damage": round(damage * 1.8, 1), "ExplosionDamage": 0, "ExplosionRadius": 0}}],
         "RPM": rpm, "Velocity": 12, "Gravity": 0.03, "ProjectileLife": 30,
         "Damage": damage, "ExplosionDamage": 4, "ExplosionRadius": 2.5, "ExplosionDestroy": False, "Spread": 0.5,
         "HeatPerShoot": 0.8, "NaturalCooldown": 0.6, "DefaultZoom": 2,
         "Crosshair": "@VehicleDynamicCross", "Name": f"weapon.{MOD}.{name}",
         "Icon": "superbwarfare:textures/overlay/vehicle/weapon/icons/cannon_30mm_ap.png",
         "ShootPos": {"Transform": "Barrel", "Positions": muzzles, "Directions": ["Barrel"]},
         "SoundInfo": {"FireSoundInstances": "superbwarfare:hpj_11_fire"}}
    if lead:   # radar-directed guns show the gunner a lead point; the hand-laid ZU-23-2 deliberately does not
        w["SeekWeaponInfo"] = {"SeekDirection": "Barrel", "SeekRange": 192, "SeekAngle": 20, "SeekTime": 10,
                               "MinTargetSize": 0.1, "OnlyLockEntity": True, "CalculateTrajectory": True}
    return w

def gunner_seat(pos, cam, hide=True):
    return {"HidePassenger": hide, "Transform": "Turret", "Position": pos, "CanRotateHead": not hide,
            "MinPitch": -8, "MaxPitch": 85, "Weapons": ["Main"], "HasThermalImaging": hide,
            **({"CameraPos": {"Transform": "Turret", "UseFixedCameraPos": True, "Position": cam, "Direction": "Barrel"}} if cam else {})}

def sbw_vehicles():
    vehicle_texture()
    wheels8 = lambda zs: [b for z in zs for b in pair(TY, 0.9, 0, z, 0.4, 1.2, 1.2)]

    # ZU-23-2: towed twin 23 mm. Static, one exposed gunner, no lead computer.
    sbw_vehicle("zu_23_2",
        base=pair(TY, 1.0, 0, -0.4, 0.25, 0.8, 0.8) + [(SD, -1.0, 0.25, -0.9, 2.0, 0.2, 1.8), (SD, -0.1, 0, 1.0, 0.2, 0.3, 0.4)],
        turret=[(OL, -0.5, 0.45, -0.5, 1.0, 0.5, 1.0), (OD, -0.3, 0.45, -1.2, 0.6, 0.15, 0.6), (OD, -0.3, 0.6, -1.25, 0.6, 0.6, 0.1)],
        barrel=pair(OD, 0.08, 1.0, -0.5, 0.22, 0.3, 1.2) + pair(SD, 0.14, 1.1, 0.7, 0.09, 0.09, 1.7)
               + pair(BK, 0.12, 1.08, 2.3, 0.13, 0.13, 0.2) + pair(OL, 0.35, 0.9, -0.4, 0.35, 0.45, 0.7),
        turret_pivot=(0, 0.5, 0), barrel_pivot=(0, 1.15, 0),
        data={"MaxHealth": 120, "Mass": 4, "EngineType": "Fixed", "Type": "Defense", "HudType": "@Land",
              "VehicleContainerType": "Mini", "TurretTurnSpeed": [16, 12], "TurretPitchRange": [-5, 85],
              "Seats": [gunner_seat([0, 0.0, -0.9], None, hide=False)],
              "Weapons": {"Main": cannon("zu_23_2", "ammo_23mm", 50, 900, 8, [[0.18, 0, 2.5], [-0.18, 0, 2.5]], False)},
              "ContainerIcon": "superbwarfare:textures/gui/vehicle/type/defense.png"})

    # Flakpanzer Gepard: tracked, radar-laid twin 35 mm.
    sbw_vehicle("gepard",
        base=pair(TY, 1.05, 0, -3.7, 0.5, 1.0, 7.4) + [(OL, -1.5, 0.5, -3.9, 3.0, 1.5, 7.8), (OD, -1.4, 0.9, 3.9, 2.8, 0.7, 0.2)],
        turret=[(OLL, -1.1, 2.0, -1.7, 2.2, 1.3, 3.0), (SD, -0.08, 3.3, -1.5, 0.16, 0.5, 0.16),
                (OD, -0.9, 3.8, -1.6, 1.8, 0.5, 0.25), (OD, -0.45, 2.4, 1.3, 0.9, 0.9, 0.3)],
        barrel=pair(OD, 1.1, 2.4, -1.1, 0.45, 0.8, 2.2) + pair(SD, 1.25, 2.74, 1.1, 0.12, 0.12, 2.4),
        turret_pivot=(0, 2.0, -0.3), barrel_pivot=(0, 2.8, -0.1),
        data={"MaxHealth": 500, "Mass": 20, "Type": "AA", "HudType": "@Land", "VehicleContainerType": "Small",
              "EngineType": "Track",
              "EngineInfo": {"EnergyCostRate": 96, "WheelRotSpeed": 1.25, "WheelDifferential": 0.5, "TrackRotSpeed": 1.9,
                             "TrackDifferential": 0.8, "MaxForwardSpeedRate": 0.8, "MaxBackwardSpeedRate": 0.5,
                             "Increment": 0.025, "Decrement": 0.015, "SteeringSpeed": 0.1},
              "UpStep": 1.5, "RotateOffsetHeight": 2.0, "CollisionLevel": {"Level": 3},
              "TerrainCompat": [[1.4, 0, 3.2], [-1.4, 0, 3.2], [1.4, 0, 0], [-1.4, 0, 0], [1.4, 0, -3.2], [-1.4, 0, -3.2]],
              "TurretTurnSpeed": [14, 10], "TurretPitchRange": [-8, 85],
              "Seats": [gunner_seat([0, -0.4, 0], [0, 1.9, 0.6])],
              "Weapons": {"Main": cannon("gepard", "ammo_35mm", 40, 1100, 14, [[1.31, -0.06, 3.5], [-1.31, -0.06, 3.5]], True)},
              "ContainerIcon": "superbwarfare:textures/gui/vehicle/type/land.png"})

    # Pantsir-S1: 8x8 truck, twin 30 mm plus missiles (the missiles are launched by the vehicle's own fire control).
    sbw_vehicle("pantsir",
        base=wheels8([-4.2, -2.8, 1.2, 2.6]) + [(OD, -1.0, 0.6, -5.0, 2.0, 0.8, 10.0), (OL, -1.25, 1.4, 3.0, 2.5, 1.6, 2.0),
              (GL, -1.05, 2.2, 4.98, 2.1, 0.6, 0.05), (OL, -1.25, 1.4, -4.8, 2.5, 0.8, 7.6)] + pair(GL, 1.23, 2.2, 3.5, 0.04, 0.55, 1.0),
        turret=[(OLL, -1.0, 2.2, -2.8, 2.0, 1.4, 2.6), (SD, -0.08, 3.6, -2.6, 0.16, 0.5, 0.16),
                (OD, -0.9, 4.1, -2.7, 1.8, 0.8, 0.12), (OD, -0.5, 2.9, -0.2, 1.0, 0.9, 0.12)],
        barrel=pair(OD, 1.0, 2.55, -3.0, 0.5, 1.1, 3.4) + pair(BK, 1.05, 2.6, 0.4, 0.4, 1.0, 0.03) + pair(SD, 0.8, 2.5, -0.2, 0.1, 0.1, 1.8),
        turret_pivot=(0, 2.2, -1.5), barrel_pivot=(0, 3.1, -1.5),
        data={"MaxHealth": 400, "Mass": 18, "Type": "AA", "HudType": "@Land", "VehicleContainerType": "Small",
              "EngineType": "Wheel",
              "EngineInfo": {"EnergyCostRate": 64, "WheelRotSpeed": 1.75, "WheelDifferential": 0.3, "MaxForwardSpeedRate": 0.9,
                             "MaxBackwardSpeedRate": 0.55, "Increment": 0.02, "Decrement": 0.017, "SteeringSpeed": 0.05},
              "UpStep": 1.5, "RotateOffsetHeight": 2.2, "CollisionLevel": {"Level": 3},
              "TerrainCompat": [[1.3, 0, 3.2], [-1.3, 0, 3.2], [1.3, 0, 1.8], [-1.3, 0, 1.8], [1.3, 0, -2.2], [-1.3, 0, -2.2], [1.3, 0, -3.6], [-1.3, 0, -3.6]],
              "TurretTurnSpeed": [12, 8], "TurretPitchRange": [-5, 85],
              "Seats": [gunner_seat([0, -0.6, 0], [0, 1.9, 0.5])],
              "Weapons": {"Main": cannon("pantsir", "ammo_30mm", 50, 1000, 12, [[0.85, -0.55, 1.7], [-0.85, -0.55, 1.7]], True)},
              "ContainerIcon": "superbwarfare:textures/gui/vehicle/type/land.png"})

    # MIM-104 Patriot: static trailer launcher, missiles only, no crew position.
    cans = [b for sx in (-1.0, 0.05) for sy in (1.75, 2.75)
            for b in [(SA, sx, sy, -4.1, 0.95, 0.9, 6.1), (SAD, sx + 0.05, sy + 0.05, 2.0, 0.85, 0.8, 0.03)]]
    sbw_vehicle("patriot",
        base=pair(TY, 0.9, 0, -3.5, 0.4, 1.1, 1.1) + pair(TY, 0.9, 0, -2.1, 0.4, 1.1, 1.1) + pair(SD, 1.2, 0, 1.0, 0.25, 0.9, 0.25)
             + pair(SD, 1.2, 0, -4.2, 0.25, 0.9, 0.25) + [(SAD, -1.3, 0.9, -4.5, 2.6, 0.4, 9.0), (SA, -1.2, 1.3, 2.7, 2.4, 1.3, 1.7)],
        turret=[(SAD, -1.2, 1.3, -2.2, 2.4, 0.4, 2.4)],
        barrel=[(SD, -1.05, 1.7, -2.5, 2.1, 2.0, 0.15)] + cans,
        turret_pivot=(0, 1.3, -1.0), barrel_pivot=(0, 1.9, -2.0),
        data={"MaxHealth": 300, "Mass": 30, "EngineType": "Fixed", "Type": "Defense", "HudType": "@Empty",
              "VehicleContainerType": "Mini", "TurretTurnSpeed": [5, 3], "TurretPitchRange": [10, 80],
              "Seats": [], "Weapons": {},
              "ContainerIcon": "superbwarfare:textures/gui/vehicle/type/defense.png"})

    # Survival recipes: each one crafts the vehicle packed in a Superb Warfare container (open it with a crowbar).
    def crate(name, pattern, key):
        write(f"{D}/recipe/{name}.json", {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: {"item": v if ":" in v else f"{MOD}:{v}"} for k, v in key.items()},
            "result": {"id": "superbwarfare:container", "count": 1, "components": {
                "minecraft:block_entity_data": {"id": "superbwarfare:container", "EntityType": f"{MOD}:{name}"}}}})
    I, N, B = MC + "iron_ingot", MC + "iron_nugget", MC + "iron_block"
    crate("zu_23_2", ["I I", "IMI", "NBN"], {"I": I, "M": "electric_motor", "N": N, "B": B})
    crate("gepard", ["UOU", "MCM", "BBB"], {"U": "ammo_35mm", "O": MC + "observer", "M": "electric_motor", "C": "circuit_board", "B": B})
    crate("pantsir", ["UOU", "MCM", "BBB"], {"U": "guidance_unit", "O": MC + "observer", "M": "electric_motor", "C": "circuit_board", "B": B})
    crate("patriot", ["UDU", "MCM", "BBB"], {"U": "guidance_unit", "D": MC + "diamond", "M": "electric_motor", "C": "circuit_board", "B": B})


if __name__ == "__main__":
    sbw_vehicles()
    block_textures(); block_models(); item_icons(); entity_textures(); recipes(); tags(); lang()
    print("assets generated")
