#!/usr/bin/env python3
"""Generates every resource file of the mod (textures, models, lang, recipes, tags).
Run from the repository root:  python3 tools/gen_assets.py
"""
import json, math, os, random
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
    icons["mortar_shell"] = shell((70, 74, 78))
    icons["shell_cluster"] = shell((230, 230, 224))
    icons["shell_smoke"] = shell((120, 190, 230))
    icons["shell_incendiary"] = shell((230, 80, 40))
    icons["atacms_missile"] = missile_icon((92, 104, 62), (30, 30, 34), (52, 60, 40), 3, a=(4, 12), b=(12, 4))

    img, d = canvas()   # HIMARS from the side: cab, launcher pod, six wheels
    d.rectangle([1, 6, 5, 11], fill=c((96, 104, 70))); d.rectangle([2, 7, 4, 8], fill=c((70, 110, 140)))
    d.polygon([(6, 4), (14, 2), (14, 9), (6, 9)], fill=c((110, 116, 82)))
    d.point([(13, 4), (13, 6), (11, 5), (11, 7)], fill=c((30, 30, 30)))
    d.rectangle([1, 10, 14, 11], fill=c((70, 76, 52)))
    for x in (3, 9, 12):
        d.rectangle([x - 1, 12, x + 1, 14], fill=c((34, 34, 34)))
    icons["himars"] = outlined(img)
    img, d = canvas()   # M109 from the side: tracks, hull, big turret, long barrel
    d.rectangle([1, 10, 13, 13], fill=c((40, 40, 38)))
    for x in (3, 6, 9, 12):
        d.point((x, 12), fill=c((90, 90, 86)))
    d.rectangle([1, 8, 13, 10], fill=c((96, 104, 70)))
    d.rectangle([3, 5, 9, 8], fill=c((110, 116, 82)))
    d.line([(9, 6), (15, 3)], fill=c((80, 86, 60)))
    icons["m109"] = outlined(img)
    img, d = canvas()   # mortar: baseplate, bipod, tube raised high
    d.rectangle([2, 12, 9, 13], fill=c((60, 62, 58)))
    d.line([(5, 12), (12, 2)], fill=c((80, 90, 60)), width=2)
    d.line([(10, 6), (8, 13)], fill=c((120, 120, 116))); d.line([(10, 6), (12, 13)], fill=c((120, 120, 116)))
    icons["mortar_120"] = outlined(img)
    img, d = canvas()   # Tomahawk trailer: container tipped up on a low trailer
    d.rectangle([1, 11, 14, 12], fill=c((70, 76, 52)))
    d.polygon([(4, 11), (8, 2), (12, 4), (8, 11)], fill=c((150, 156, 120)))
    d.rectangle([3, 13, 5, 14], fill=c((34, 34, 34))); d.rectangle([9, 13, 11, 14], fill=c((34, 34, 34)))
    icons["tomahawk_launcher"] = outlined(img)
    img, d = canvas()   # Iskander: long truck with the missile raised
    d.rectangle([1, 9, 14, 11], fill=c((88, 104, 62)))
    d.rectangle([11, 6, 14, 9], fill=c((96, 110, 70)))
    d.line([(3, 9), (6, 1)], fill=c((220, 220, 214)), width=2)
    for x in (2, 5, 10, 13):
        d.point((x, 12), fill=c((34, 34, 34))); d.point((x, 13), fill=c((34, 34, 34)))
    icons["iskander_launcher"] = outlined(img)
    img, d = canvas()   # ZU-23-2: twin barrels on a low carriage
    d.rectangle([3, 10, 11, 12], fill=c((86, 96, 64)))
    d.line([(6, 9), (14, 3)], fill=c((50, 52, 48))); d.line([(6, 10), (14, 5)], fill=c((50, 52, 48)))
    d.rectangle([5, 8, 8, 10], fill=c((96, 106, 72)))
    d.point([(3, 13), (11, 13)], fill=c((34, 34, 34)))
    icons["zu_23_2"] = outlined(img)
    img, d = canvas()   # Gepard: tracked hull, tall turret, twin guns and radar dish
    d.rectangle([1, 11, 14, 13], fill=c((40, 40, 38)))
    d.rectangle([2, 9, 13, 11], fill=c((96, 104, 70)))
    d.rectangle([5, 5, 10, 9], fill=c((110, 116, 82)))
    d.line([(10, 6), (15, 4)], fill=c((50, 52, 48)))
    d.ellipse([5, 1, 9, 4], outline=c((150, 150, 146)))
    icons["gepard"] = outlined(img)
    img, d = canvas()   # Pantsir: truck with a turret, missiles and radar
    d.rectangle([1, 9, 14, 11], fill=c((96, 110, 70)))
    d.rectangle([11, 6, 14, 9], fill=c((106, 118, 78)))
    d.rectangle([4, 5, 8, 9], fill=c((110, 120, 84)))
    d.line([(3, 6), (3, 3)], fill=c((220, 220, 214))); d.line([(9, 6), (9, 3)], fill=c((220, 220, 214)))
    d.rectangle([5, 2, 7, 3], fill=c((150, 150, 146)))
    for x in (2, 5, 10, 13):
        d.point((x, 12), fill=c((34, 34, 34)))
    icons["pantsir"] = outlined(img)
    img, d = canvas()   # Patriot: trailer with four canisters raised
    d.rectangle([1, 11, 14, 12], fill=c((140, 132, 100)))
    d.polygon([(4, 11), (9, 3), (13, 5), (8, 11)], fill=c((176, 168, 130)))
    d.line([(6, 8), (11, 5)], fill=c((120, 112, 86)))
    d.point([(3, 13), (11, 13)], fill=c((34, 34, 34)))
    icons["patriot"] = outlined(img)
    img, d = canvas()   # EW Jammer: shelter with aerials
    d.rectangle([2, 8, 13, 13], fill=c((96, 104, 70)))
    d.line([(5, 8), (5, 1)], fill=c((60, 60, 60))); d.line([(10, 8), (10, 3)], fill=c((60, 60, 60)))
    d.point([(5, 1), (10, 3)], fill=c((220, 60, 50)))
    icons["jammer"] = outlined(img)
    img, d = canvas()   # Trench Jammer: small box with a whip aerial
    d.rectangle([4, 9, 11, 13], fill=c((70, 76, 52)))
    d.line([(9, 9), (12, 1)], fill=c((50, 50, 50))); d.point((6, 11), fill=c((90, 220, 90)))
    icons["trench_jammer"] = outlined(img)
    img, d = canvas()   # Krasukha: truck with a big dish
    d.rectangle([1, 9, 14, 11], fill=c((88, 104, 62)))
    d.rectangle([11, 6, 14, 9], fill=c((96, 110, 70)))
    d.ellipse([1, 1, 8, 8], outline=c((200, 200, 196)))
    for x in (2, 5, 10, 13):
        d.point((x, 12), fill=c((34, 34, 34)))
    icons["krasukha"] = outlined(img)
    for name, body, cap in (("jerrycan", (70, 84, 56), (60, 60, 60)), ("diesel_jerrycan", (180, 60, 40), (230, 190, 60))):
        img, d = canvas()
        d.rectangle([4, 4, 11, 14], fill=c(body)); d.line([(5, 9), (10, 9)], fill=c(tuple(v - 25 for v in body)))
        d.line([(5, 6), (10, 12)], fill=c(tuple(v + 25 for v in body))); d.line([(10, 6), (5, 12)], fill=c(tuple(v + 25 for v in body)))
        d.rectangle([6, 2, 9, 3], fill=c((50, 50, 50))); d.rectangle([10, 2, 11, 3], fill=c(cap))
        icons[name] = outlined(img)
    for name, img in icons.items():
        save(img, f"{A}/textures/item/{name}.png")
        parent = "minecraft:item/handheld" if name == "anti_drone_gun" else "minecraft:item/generated"
        if name == "field_laptop":   # drawn as its 3D model by LaptopItemRenderer
            write(f"{A}/models/item/{name}.json", {"parent": "minecraft:builtin/entity", "gui_light": "front",
                                                   "textures": {"particle": f"{MOD}:item/{name}"}})
            continue
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

    shaped("patriot_missile", [" U ", "IWI", "IEI"], {"U": "guidance_unit", "I": I, "W": "warhead", "E": "rocket_engine"}, 2)
    shaped("mavic", ["PGP", "MCM", "PBP"], {"P": "propeller", "G": MC + "glass_pane", "M": "electric_motor", "C": "circuit_board", "B": "drone_battery"})
    shaped("drone_grenade", [" N ", "NGN", " N "], {"N": N, "G": P}, 4)
    shaped("orlan_10", ["IPI", "IUI", " M "], {"I": I, "P": "propeller", "U": "guidance_unit", "M": "electric_motor"})
    shaped("bayraktar_tb2", [" U ", "BMB", "DPD"], {"U": "guidance_unit", "B": B, "M": "electric_motor", "D": "drone_battery", "P": "propeller"})
    shaped("global_hawk", ["DUD", "BEB", "DUD"], {"D": MC + "diamond", "U": "guidance_unit", "B": B, "E": "rocket_engine"})
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
    shaped("ground_station", ["SSS", "CUC", "BBB"], {"S": MC + "smooth_stone", "C": "circuit_board", "U": "guidance_unit", "B": B})
    shaped("orlan_catapult", ["  I", " I ", "IPI"], {"I": I, "P": MC + "piston"})
    shaped("shahed_launcher", ["  I", " I ", "BPB"], {"I": I, "B": B, "P": MC + "piston"})
    shaped("mortar_shell", [" I ", "ITI", " P "], {"I": N, "T": T, "P": P}, 4)
    # A fresh battery puts a used Mavic back to full charge.
    write(f"{D}/recipe/mavic_recharge.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": f"{MOD}:mavic"}, {"item": f"{MOD}:drone_battery"}],
        "result": {"id": f"{MOD}:mavic", "count": 1}})


# ---------------------------------------------------------------- tags
def tags():
    opt = lambda i: {"id": i, "required": False}
    # Curios (optional): give players a back slot and allow the backpacks in it.
    write(f"{RES}/data/curios/tags/item/back.json", {"replace": False, "values": [f"{MOD}:fiber_backpack", f"{MOD}:jammer_backpack"]})
    write(f"{D}/curios/entities/steelrain_player.json", {"entities": ["minecraft:player"], "slots": ["back"]})


# ---------------------------------------------------------------- small arms
GUN_ITEMS = ["ak74m", "m4a1", "svd", "pkm", "m17", "m590", "m107", "rpg7", "m18", "m9", "hk416", "g36", "scarh", "m249", "m240", "m110"]
GUN_MAGS = ["ak74m", "m4a1", "svd", "pkm", "m17", "m107", "m18", "m9", "hk416", "g36", "scarh", "m249", "m240", "m110"]
GUN_PARTS = ["red_dot", "acog", "pso1", "suppressor", "pistol_suppressor", "foregrip", "scope_10x", "holo", "lpvo", "magnifier", "nv_scope",
             "thermal_scope", "flashlight", "laser", "angled_grip", "bipod"]
ROUND_NAMES = {"ammo_545": "5.45x39", "ammo_556": "5.56x45", "ammo_762": "7.62x54R", "ammo_9mm": "9x19", "ammo_50": ".50 BMG", "ammo_76251": "7.62x51"}
GUN_AMMO = ["ammo_545", "ammo_556", "ammo_762", "ammo_9mm", "ammo_12g", "ammo_50", "rocket_pg7v", "rocket_og7v", "ammo_76251"] + [r + k for r in ROUND_NAMES for k in ("_tracer", "_training")]

GUN_LANG = {
    "item.steelrain.ak74m": "AK-74M", "item.steelrain.m4a1": "M4A1", "item.steelrain.svd": "SVD", "item.steelrain.pkm": "PKM",
    "item.steelrain.m17": "M17", "item.steelrain.m590": "M590A1", "item.steelrain.m107": "Barrett M107", "item.steelrain.rpg7": "RPG-7",
    "item.steelrain.m107_magazine": "M107 Magazine (10)", "item.steelrain.ammo_50": ".50 BMG Round",
    "item.steelrain.rocket_pg7v": "PG-7V Anti-Tank Rocket", "item.steelrain.rocket_og7v": "OG-7V Fragmentation Rocket",
    "item.steelrain.scope_10x": "10x Scope",
    "item.steelrain.m18": "M18", "item.steelrain.m9": "M9", "item.steelrain.hk416": "HK416", "item.steelrain.g36": "G36",
    "item.steelrain.scarh": "SCAR-H", "item.steelrain.m249": "M249", "item.steelrain.m240": "M240", "item.steelrain.m110": "M110",
    "item.steelrain.m18_magazine": "M18 Magazine (17)", "item.steelrain.m9_magazine": "M9 Magazine (15)",
    "item.steelrain.hk416_magazine": "HK416 Magazine (30)", "item.steelrain.g36_magazine": "G36 Magazine (30)",
    "item.steelrain.scarh_magazine": "SCAR-H Magazine (20)", "item.steelrain.m249_magazine": "M249 Belt Box (200)",
    "item.steelrain.m240_magazine": "M240 Belt Can (100)", "item.steelrain.m110_magazine": "M110 Magazine (20)",
    "item.steelrain.ammo_76251": "7.62x51 Round",
    "gui.steelrain.gun.reloading": "RELOADING",
    **{f"item.steelrain.{r}_tracer": f"{n} Tracer Round" for r, n in ROUND_NAMES.items()},
    **{f"item.steelrain.{r}_training": f"{n} Training Round" for r, n in ROUND_NAMES.items()},
    "gun.steelrain.kind.ball": "Ball", "gun.steelrain.kind.tracer": "Tracer", "gun.steelrain.kind.training": "Training",
    "item.steelrain.holo": "Holographic Sight", "item.steelrain.lpvo": "1-6x Variable Scope", "item.steelrain.magnifier": "3x Flip Magnifier",
    "item.steelrain.nv_scope": "Night Sight", "item.steelrain.thermal_scope": "Thermal Sight", "item.steelrain.flashlight": "Weapon Light",
    "item.steelrain.laser": "Laser Pointer", "item.steelrain.angled_grip": "Angled Foregrip", "item.steelrain.bipod": "Clip-on Bipod",
    "tooltip.steelrain.holo": "Unmagnified sight: a lit ring and dot in a wide window",
    "tooltip.steelrain.lpvo": "1x to 6x; turn the power with the scroll wheel while aiming",
    "tooltip.steelrain.magnifier": "Sits behind a red dot or holographic sight and makes it 3x; flips aside with its key",
    "tooltip.steelrain.nv_scope": "4x night sight: sees in the dark, in green",
    "tooltip.steelrain.thermal_scope": "3x thermal sight: living things in the open show as bright shapes",
    "tooltip.steelrain.flashlight": "Lights what the gun points at, out to 14 blocks; switched with its key",
    "tooltip.steelrain.laser": "A red spot where the gun points; steadier fire from the hip while it is on",
    "tooltip.steelrain.angled_grip": "Sights come up quicker; a little less muzzle climb",
    "tooltip.steelrain.bipod": "Crouch and keep still to rest the gun: far less kick and drift",
    "gui.steelrain.part.red_dot": "Dot", "gui.steelrain.part.acog": "4x", "gui.steelrain.part.pso1": "PSO-1", "gui.steelrain.part.scope_10x": "10x",
    "gui.steelrain.part.holo": "Holo", "gui.steelrain.part.lpvo": "1-6x", "gui.steelrain.part.nv_scope": "Night", "gui.steelrain.part.thermal_scope": "Thermal",
    "gui.steelrain.part.magnifier": "3x Magnifier", "gui.steelrain.part.suppressor": "Suppressor", "gui.steelrain.part.pistol_suppressor": "Suppressor",
    "gui.steelrain.part.foregrip": "Grip", "gui.steelrain.part.angled_grip": "Angled", "gui.steelrain.part.bipod": "Bipod",
    "gui.steelrain.part.flashlight": "Light", "gui.steelrain.part.laser": "Laser",
    "gui.steelrain.gun.slot.magnifier": "Magnifier", "gui.steelrain.gun.slot.side": "Side",
    "key.steelrain.gun_magnifier": "Firearm: flip magnifier", "key.steelrain.gun_side": "Firearm: light or laser on/off",
    "tooltip.steelrain.scope_10x": "10x telescope with a mil-dot reticle and an elevation drum, 100 to 1800 m",
    "message.steelrain.gun_no_rockets": "No rockets",
    "message.steelrain.gun_backblast": "Backblast: there is a wall behind you",
    "gui.steelrain.gun.rockets": "ROCKETS %s",
    "item.steelrain.ak74m_magazine": "AK-74 Magazine (30)", "item.steelrain.m4a1_magazine": "STANAG Magazine (30)",
    "item.steelrain.svd_magazine": "SVD Magazine (10)", "item.steelrain.pkm_magazine": "PKM Belt Box (100)",
    "item.steelrain.m17_magazine": "M17 Magazine (17)",
    "item.steelrain.ammo_545": "5.45x39 Round", "item.steelrain.ammo_556": "5.56x45 Round", "item.steelrain.ammo_762": "7.62x54R Round",
    "item.steelrain.ammo_9mm": "9x19 Round", "item.steelrain.ammo_12g": "12 Gauge Buckshot",
    "item.steelrain.red_dot": "Red Dot Sight", "item.steelrain.acog": "4x Combat Sight", "item.steelrain.pso1": "PSO-1 Scope",
    "item.steelrain.suppressor": "Rifle Suppressor", "item.steelrain.pistol_suppressor": "Pistol Suppressor",
    "item.steelrain.foregrip": "Vertical Foregrip",
    "tooltip.steelrain.red_dot": "Unmagnified sight with a lit aiming dot",
    "tooltip.steelrain.acog": "4x sight; bars under the chevron give the hold for 200 to 600 m",
    "tooltip.steelrain.pso1": "4x scope with range-finder and elevation drum, 100 to 1000 m",
    "tooltip.steelrain.suppressor": "Hides the flash and quietens the report; a little less kick",
    "tooltip.steelrain.pistol_suppressor": "Hides the flash and quietens the report",
    "tooltip.steelrain.foregrip": "Steadier sights and less muzzle climb",
    "tooltip.steelrain.attachment.use": "Hold the gun and press the fittings key to fit it",
    "tooltip.steelrain.gun.calibre": "Calibre: %s",
    "tooltip.steelrain.gun.loaded": "Loaded: %s / %s",
    "tooltip.steelrain.gun.mode": "Selector: %s",
    "tooltip.steelrain.gun.velocity": "Muzzle velocity: %s m/s",
    "tooltip.steelrain.gun.rate": "Rate of fire: %s rounds/min",
    "tooltip.steelrain.gun.accuracy": "Accuracy: %s MOA",
    "tooltip.steelrain.gun.weight": "Weight: %s kg",
    "tooltip.steelrain.gun.at300": "At 300 m: %s m/s after %s s",
    "tooltip.steelrain.gun.more": "Hold Shift for ballistics",
    "tooltip.steelrain.magazine.rounds": "%s / %s rounds of %s",
    "tooltip.steelrain.magazine.fits": "Fits: %s",
    "tooltip.steelrain.magazine.use": "Hold use to load rounds; sneak + use to empty",
    "gun.steelrain.mode.safe": "Safe", "gun.steelrain.mode.semi": "Semi", "gun.steelrain.mode.auto": "Auto",
    "message.steelrain.gun_safe": "Safety is on",
    "message.steelrain.gun_no_magazine": "No magazine fitted",
    "message.steelrain.gun_no_magazines": "No loaded magazine for this gun",
    "message.steelrain.gun_no_ammo": "No shells",
    "key.steelrain.gun_reload": "Firearm: reload",
    "key.steelrain.gun_mode": "Firearm: fire selector",
    "key.steelrain.gun_zero_up": "Firearm: sights up 100 m",
    "key.steelrain.gun_zero_down": "Firearm: sights down 100 m",
    "key.steelrain.gun_fit": "Firearm: fittings",
    "key.steelrain.gun_breath": "Firearm: hold breath",
    "gui.steelrain.gun.fittings": "Fittings",
    "gui.steelrain.gun.zero": "ZERO %s M",
    "gui.steelrain.gun.bipod": "BIPOD",
    "gui.steelrain.gun.no_spare": "NO SPARE MAGAZINE",
    "gui.steelrain.gun.shells": "SHELLS %s",
    "gui.steelrain.gun.slot.optic": "Sight",
    "gui.steelrain.gun.slot.muzzle": "Muzzle",
    "gui.steelrain.gun.slot.under": "Handguard",
    "gui.steelrain.gun.no_mount": "no mount on this gun",
    "gui.steelrain.gun.fit_hint": "Click a fitting to take it from your pack; click it again to take it off. Drag to turn the gun.",
    "gui.steelrain.gun.s_calibre": "Calibre", "gui.steelrain.gun.s_velocity": "Muzzle", "gui.steelrain.gun.s_rate": "Rate",
    "gui.steelrain.gun.s_feed": "Feed", "gui.steelrain.gun.s_accuracy": "Group", "gui.steelrain.gun.s_weight": "Weight",
    "gui.steelrain.gun.s_zero": "Zero", "gui.steelrain.gun.s_drop300": "Drop 300", "gui.steelrain.gun.s_v300": "V 300",
    "death.attack.steelrain.bullet": "%1$s was shot",
    "death.attack.steelrain.bullet.player": "%1$s was shot by %2$s",
    "death.attack.steelrain.bullet.item": "%1$s was shot by %2$s with %3$s",
    "subtitles.steelrain.gun_fire": "Gunshot", "subtitles.steelrain.gun_far": "Distant gunfire",
    "subtitles.steelrain.gun_handling": "Gun handled", "subtitles.steelrain.bullet_pass": "Bullet passes",
}


def guns():
    """Small arms: mesh and texture for every gun, magazine-bearing or not, every fitting and each kind of round;
    item files that hand the drawing to GunItemRenderer; recipes; the bullet damage type."""
    import gen_aircraft as GA
    import gen_guns as GG
    for name, (build, px) in GG.MODELS.items():
        model = build()
        tex, tw, th = VK.bake(model, name, px)
        save(Image.fromarray(tex).convert("RGB"), f"{A}/textures/entity/gun/{name}.png")
        out = GA.export(model, tw, th)
        out["points"] = {k: [round(v, 4) for v in p] for k, p in model["points"].items()}
        path = f"{A}/models/gun/{name}.json"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            json.dump(out, f, separators=(",", ":"))
        # a small side view for the item's break particles
        icon = VK.render(model, tex, math.radians(90), 0.0, 64, bg=(0, 0, 0)).resize((16, 16), Image.LANCZOS).convert("RGBA")
        px_ = icon.load()
        for yy in range(16):
            for xx in range(16):
                r, g_, b, _ = px_[xx, yy]
                px_[xx, yy] = (r, g_, b, 0 if r + g_ + b < 12 else 255)
        save(icon, f"{A}/textures/item/{name}.png")
    for name in GUN_ITEMS + [m + "_magazine" for m in GUN_MAGS] + GUN_PARTS + GUN_AMMO:
        tex = name[:-9] if name.endswith("_magazine") else name
        write(f"{A}/models/item/{name}.json", {"parent": "minecraft:builtin/entity", "gui_light": "front",
                                               "textures": {"particle": f"{MOD}:item/{tex}"}})
    I, N, B, C, P = MC + "iron_ingot", MC + "iron_nugget", MC + "iron_block", MC + "copper_ingot", MC + "gunpowder"
    K, W, G, R = MC + "black_dye", MC + "oak_planks", MC + "glass_pane", MC + "redstone"
    shaped("ak74m", ["KII", "IBI", " I "], {"K": K, "I": I, "B": B})
    shaped("m4a1", ["III", "KBI", " I "], {"K": K, "I": I, "B": B})
    shaped("svd", ["III", "WBI", "W  "], {"W": W, "I": I, "B": B})
    shaped("pkm", ["IIB", "WBI", " I "], {"W": W, "I": I, "B": B})
    shaped("m17", ["II", "KI"], {"K": K, "I": I})
    shaped("m590", ["III", "WII"], {"W": W, "I": I})
    shaped("m107", ["IIB", "KBB", " II"], {"K": K, "I": I, "B": B})
    S_, L_ = MC + "sand", MC + "leather"
    shaped("m18", ["II", "SI"], {"S": S_, "I": I})
    shaped("m9", ["II", "LI"], {"L": L_, "I": I})
    shaped("hk416", ["III", "KBI", " N "], {"K": K, "I": I, "B": B, "N": N})
    shaped("g36", ["KKK", "IBI", " I "], {"K": K, "I": I, "B": B})
    shaped("scarh", ["III", "SBI", " I "], {"S": S_, "I": I, "B": B})
    shaped("m249", ["IIB", "KBI", " I "], {"K": K, "I": I, "B": B})
    shaped("m240", ["IBB", "KBI", " I "], {"K": K, "I": I, "B": B})
    shaped("m110", ["IIB", "SBI", " I "], {"S": S_, "I": I, "B": B})
    shaped("m18_magazine", ["N", "N", "S"], {"N": N, "S": S_})
    shaped("m9_magazine", ["N", "N", "L"], {"N": N, "L": L_})
    shaped("hk416_magazine", ["NK", "N ", "N "], {"N": N, "K": K})
    shaped("g36_magazine", ["NG", "N ", "N "], {"N": N, "G": G})
    shaped("scarh_magazine", ["NS", "N ", "N "], {"N": N, "S": S_})
    shaped("m249_magazine", ["NIN", "I I", "III"], {"N": N, "I": I})
    shaped("m240_magazine", ["INI", "I I", "INI"], {"N": N, "I": I})
    shaped("m110_magazine", ["NN", "NS"], {"N": N, "S": S_})
    shaped("ammo_76251", ["I", "P", "N"], {"I": I, "P": P, "N": N}, 20)
    shaped("rpg7", ["IWI", " W "], {"W": W, "I": I})
    shaped("m107_magazine", ["II", "N ", "N "], {"N": N, "I": I})
    shaped("ammo_50", ["I", "P", "G"], {"I": I, "P": P, "G": MC + "gold_ingot"}, 10)
    shaped("rocket_pg7v", [" C ", "ITI", " P "], {"C": C, "I": I, "T": MC + "tnt", "P": P}, 2)
    shaped("rocket_og7v", [" N ", "ITI", " P "], {"N": N, "I": I, "T": MC + "tnt", "P": P}, 2)
    shaped("scope_10x", ["GIG", "IRI", " I "], {"R": R, "I": I, "G": G})
    E_, T_, Q_ = MC + "ender_eye", MC + "torch", MC + "quartz"
    shaped("holo", ["GRG", "III"], {"R": R, "I": I, "G": G})
    shaped("lpvo", ["GIG", "IRI", "I  "], {"R": R, "I": I, "G": G})
    shaped("magnifier", ["GIG", " N "], {"I": I, "G": G, "N": N})
    shaped("nv_scope", ["GEG", "IRI", " I "], {"R": R, "I": I, "G": G, "E": E_})
    shaped("thermal_scope", ["QEQ", "IRI", " I "], {"R": R, "I": I, "Q": Q_, "E": E_})
    shaped("flashlight", ["NTG", " N "], {"N": N, "T": T_, "G": G})
    shaped("laser", ["NRG", " N "], {"N": N, "R": R, "G": G})
    shaped("angled_grip", ["II", " K"], {"I": I, "K": K})
    shaped("bipod", [" N ", "I I", "I I"], {"I": I, "N": N})
    for r in ROUND_NAMES:                                       # a round and red dye makes a tracer; with blue dye, a training round
        write(f"{D}/recipe/{r}_tracer.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"item": f"{MOD}:{r}"}] * 8 + [{"item": MC + "red_dye"}], "result": {"id": f"{MOD}:{r}_tracer", "count": 8}})
        write(f"{D}/recipe/{r}_training.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"item": f"{MOD}:{r}"}] * 8 + [{"item": MC + "blue_dye"}], "result": {"id": f"{MOD}:{r}_training", "count": 8}})
    shaped("ak74m_magazine", ["N ", "NN", " N"], {"N": N})
    shaped("m4a1_magazine", ["NN", "N ", "N "], {"N": N})
    shaped("svd_magazine", ["NN", "NN"], {"N": N})
    shaped("pkm_magazine", ["INI", "I I", "III"], {"N": N, "I": I})
    shaped("m17_magazine", ["N", "N", "N"], {"N": N})
    shaped("ammo_545", ["N", "P", "C"], {"N": N, "P": P, "C": C}, 20)
    shaped("ammo_556", ["N", "P", "G"], {"N": N, "P": P, "G": MC + "gold_nugget"}, 20)
    shaped("ammo_762", ["I", "P", "C"], {"I": I, "P": P, "C": C}, 20)
    shaped("ammo_9mm", ["NP", "C "], {"N": N, "P": P, "C": C}, 24)
    shaped("ammo_12g", ["N", "P", "R"], {"N": N, "P": P, "R": MC + "paper"}, 8)
    shaped("red_dot", [" R ", "IGI"], {"R": R, "I": I, "G": G})
    shaped("acog", ["GIG", " I "], {"I": I, "G": G})
    shaped("pso1", ["GIG", "IRI"], {"R": R, "I": I, "G": G})
    shaped("suppressor", ["I I", "I I"], {"I": I})
    shaped("pistol_suppressor", ["N N", "N N"], {"N": N})
    shaped("foregrip", ["I", "K", "K"], {"I": I, "K": K})
    write(f"{D}/damage_type/bullet.json", {"exhaustion": 0.1, "message_id": "steelrain.bullet", "scaling": "never"})
    for tag in ("no_knockback", "bypasses_cooldown", "is_projectile"):
        write(f"{RES}/data/minecraft/tags/damage_type/{tag}.json", {"replace": False, "values": [f"{MOD}:bullet"]})


# ---------------------------------------------------------------- lang
def lang():
    L = {
        "itemGroup.steelrain": "Steel Rain",
        **GUN_LANG,
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
        "entity.steelrain.tomahawk_launcher": "Tomahawk Launcher",
        "entity.steelrain.iskander_launcher": "Iskander-M Launcher",
        "entity.steelrain.himars": "M142 HIMARS",
        "item.steelrain.himars": "M142 HIMARS",
        "item.steelrain.m109": "M109 Howitzer",
        "item.steelrain.mortar_120": "120 mm Mortar",
        "item.steelrain.zu_23_2": "ZU-23-2",
        "item.steelrain.gepard": "Flakpanzer Gepard",
        "item.steelrain.pantsir": "Pantsir-S1",
        "item.steelrain.patriot": "MIM-104 Patriot",
        "item.steelrain.jammer": "EW Jammer",
        "item.steelrain.trench_jammer": "Trench Jammer",
        "item.steelrain.krasukha": "Krasukha-4",
        "message.steelrain.battery_full": "Battery is full",
        "message.steelrain.battery_charged": "Battery: %sk / %sk FE",
        "key.steelrain.auto_mode": "Air defence: automatic mode on/off",
        "key.steelrain.range_table": "Gun: show/hide range table",
        "message.steelrain.auto_on": "Automatic mode ON: engages hostile aircraft and missiles while nobody is at the guns",
        "message.steelrain.auto_off": "Automatic mode OFF",
        "hud.steelrain.radar_none": "NO RADAR",
        "hud.steelrain.radar_search_short": "SEARCH",
        "hud.steelrain.radar_track_short": "TRACK",
        "hud.steelrain.radar_lock_short": "LOCK",
        "hud.steelrain.no_contact": "No contact",
        "hud.steelrain.belts": "BELTS  HE %s  AP %s",
        "hud.steelrain.missiles_short": "MSL  %s",
        "hud.steelrain.auto_chip_on": "AUTO",
        "hud.steelrain.auto_chip_off": "MANUAL",
        "hud.steelrain.gun_title": "Gun",
        "hud.steelrain.breech_loaded": "LOADED",
        "hud.steelrain.breech_loading": "LOADING",
        "hud.steelrain.breech_empty": "BREECH EMPTY",
        "hud.steelrain.seat_driver": "DRIVER",
        "hud.steelrain.seat_gunner": "GUNNER",
        "hud.steelrain.seat_passenger": "CREW",
        "hud.steelrain.engine_running": "ENGINE ON",
        "hud.steelrain.engine_off": "ENGINE OFF",
        "hud.steelrain.engine_flooded": "FLOODED",
        "hud.steelrain.engine_no_fuel": "NO FUEL",
        "hud.steelrain.engine_static": "EMPLACED",
        "hud.steelrain.jamming_on": "JAMMING",
        "hud.steelrain.jamming_off": "JAMMER OFF",
        "hud.steelrain.key_seat": "change seat",
        "hud.steelrain.radar_search": "RADAR: searching",
        "hud.steelrain.radar_track": "RADAR: tracking %s  %sm",
        "hud.steelrain.radar_lock": "RADAR LOCK: %s  %sm",
        "hud.steelrain.belt_he": "HE %s rounds   belts: HE %s  AP %s",
        "hud.steelrain.belt_ap": "AP %s rounds   belts: HE %s  AP %s",
        "hud.steelrain.missiles": "Missiles: %s  (right click)",
        "hud.steelrain.auto_on": "Automatic mode: on (I)",
        "hud.steelrain.auto_off": "Automatic mode: off (I)",
        "hud.steelrain.air_keys_radar": "HOLD LEFT fire   MIDDLE lock   RIGHT missile   B belt   I auto   Z seat",
        "hud.steelrain.air_keys": "HOLD LEFT fire   B belt   I auto   Z seat",
        "item.steelrain.tomahawk_launcher": "Tomahawk Launcher",
        "item.steelrain.iskander_launcher": "Iskander-M Launcher",
        "message.steelrain.moving": "Stop the vehicle before launching",
        "message.steelrain.traverse_limit": "Target is behind the cab: turn the truck towards it",
        "key.steelrain.switch_seat": "Change seat in a vehicle",
        "key.steelrain.shell_type": "Gun: next shell type",
        "message.steelrain.seat_0": "Driver's seat",
        "message.steelrain.seat_1": "Gunner's seat",
        "message.steelrain.seat_2": "Passenger seat",
        "hud.steelrain.gun_charge": "Charge %s of %s  (scroll)",
        "hud.steelrain.gun_shell": "Shell: %s  (B)",
        "hud.steelrain.gun_loaded": "LOADED: %s",
        "hud.steelrain.gun_loading": "Loading... %ss",
        "hud.steelrain.gun_empty": "Breech empty: no shells of this type",
        "hud.steelrain.gun_keys": "LOOK lay   LEFT CLICK fire   SCROLL charge   B shell   Z seat",
        "item.steelrain.jerrycan": "Jerrycan",
        "item.steelrain.diesel_jerrycan": "Jerrycan of Diesel",
        "item.steelrain.vehicle.tip": "Place on the ground. Right-click to board, sneak + right-click for storage, diesel can to refuel. Owner sneak-hits to pack it up.",
        "message.steelrain.tank_full": "Tank is full",
        "message.steelrain.refuelled": "Refuelled: %s / %s L",
        "message.steelrain.vehicle_status": "Fuel %s / %s L   Battery %sk / %sk FE",
        "message.steelrain.no_room": "No room to put it here",
        "item.steelrain.vehicle.packed": "Packed: health %s, fuel %s L",
        "message.steelrain.no_launcher": "No launcher within 64 blocks",
        "entity.steelrain.m109": "M109 Howitzer",
        "block.steelrain.shahed_launcher": "Shahed Launch Rail",
        "block.steelrain.ground_station": "UAV Ground Station",
        "block.steelrain.orlan_catapult": "Orlan Catapult",
        "entity.steelrain.gepard": "Flakpanzer Gepard",
        "entity.steelrain.zu_23_2": "ZU-23-2",
        "entity.steelrain.pantsir": "Pantsir-S1",
        "entity.steelrain.patriot": "MIM-104 Patriot",
        "weapon.steelrain.gepard": "35 mm Oerlikon KDA",
        "weapon.steelrain.zu_23_2": "23 mm 2A14",
        "weapon.steelrain.pantsir": "30 mm 2A38M",
        "entity.steelrain.trench_jammer": "Trench Jammer",
        "entity.steelrain.krasukha": "Krasukha-4 Long-Range Jammer",
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
        "item.steelrain.mortar_shell": "120 mm Mortar Bomb",
        "entity.steelrain.mortar_shell": "Mortar Bomb",
        "entity.steelrain.mortar_120": "120 mm Mortar",
        "weapon.steelrain.charge_1": "Charge 1",
        "weapon.steelrain.charge_2": "Charge 2",
        "weapon.steelrain.charge_3": "Charge 3",
        "message.steelrain.solution": "Bearing %s°  range %s:  %s",
        "message.steelrain.reloading": "Reloading pod: %ss",
        "hud.steelrain.gun_bearing": "BEARING  %s°",
        "hud.steelrain.gun_elevation": "ELEVATION  %s°",
        "hud.steelrain.range_table": "Range table, level ground",
        "gui.steelrain.solution": "SOLUTION",
        "block.steelrain.weapon_part": "Emplacement",
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
        "tooltip.steelrain.jammer_dome": "Right-click any Superb Warfare vehicle to fit it",
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
        "subtitles.steelrain.fpv_low": "FPV drone buzzes",
        "subtitles.steelrain.fpv_medium": "FPV drone buzzes",
        "subtitles.steelrain.fpv_high": "FPV drone screams",
        "subtitles.steelrain.fpv_accelerate": "FPV drone spins up",
        "subtitles.steelrain.mavic_low": "Quadcopter hums",
        "subtitles.steelrain.mavic_medium": "Quadcopter hums",
        "subtitles.steelrain.mavic_high": "Quadcopter whines",
        "subtitles.steelrain.mavic_accelerate": "Quadcopter spins up",
        "subtitles.steelrain.shahed_engine": "Shahed engine rattles",
        "subtitles.steelrain.reaper_engine": "Reaper drones overhead",
        "subtitles.steelrain.tb2_engine": "Bayraktar buzzes overhead",
        "subtitles.steelrain.orlan_engine": "Orlan buzzes overhead",
        "subtitles.steelrain.global_hawk_jet": "Jet engine roars",
        "subtitles.steelrain.jammer_hum": "Jammer hums",
        "subtitles.steelrain.static_light": "Video static",
        "subtitles.steelrain.static_medium": "Video static",
        "subtitles.steelrain.static_heavy": "Video static",
        "subtitles.steelrain.cable_snap": "Cable snaps",
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
        "entity.steelrain.jammer": "EW Jammer",
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
        "gui.steelrain.units": "Units",
        "gui.steelrain.mission": "Fire mission",
        "gui.steelrain.select_unit": "No unit selected",
        "gui.steelrain.f_range": "Range",
        "gui.steelrain.f_rounds": "Rounds",
        "gui.steelrain.f_distance": "Distance",
        "gui.steelrain.f_bearing": "Bearing",
        "gui.steelrain.in_range": "IN RANGE",
        "gui.steelrain.too_close_short": "TOO CLOSE",
        "gui.steelrain.out_of_range": "OUT OF RANGE",
        "gui.steelrain.empty": "NO AMMO",
        "gui.steelrain.no_links": "No units linked",
        "gui.steelrain.offline": "Out of reach",
        "gui.steelrain.centre": "Centre",
        "gui.steelrain.launch": "LAUNCH",
        "gui.steelrain.range": "Rng %s-%s",
        "gui.steelrain.ammo_count": "Ammo: %s",
        "gui.steelrain.fuel_count": "Energy: %s kFE",
        "gui.steelrain.distance": "Dist: %s",
        "gui.steelrain.pick_target": "No target",
        "gui.steelrain.no_signal": "NO SIGNAL",
        "gui.steelrain.rounds": "Rounds",
        "gui.steelrain.layer_topo": "TOPO",
        "gui.steelrain.layer_height": "HEIGHT",
        "gui.steelrain.f_flight": "Flight time",
        "gui.steelrain.f_elev": "Target elev",
        "gui.steelrain.f_blast": "Blast radius",
        "gui.steelrain.f_error": "Error",
        "gui.steelrain.f_grid": "Grid",
        "gui.steelrain.f_height": "Height",
        "gui.steelrain.f_energy": "Energy",
        "gui.steelrain.f_reload": "Reload",
        "gui.steelrain.f_ready": "Ready",
        "gui.steelrain.f_envelope": "Engagement envelope",
        "gui.steelrain.profile": "Profile",
        "gui.steelrain.log_fire": "FIRE x%s %s -> %s %s",
    }
    write(f"{A}/lang/en_us.json", L)


# ---------------------------------------------------------------- Superb Warfare vehicles
# Box lists use vehicle space: x across, y up, z towards the front, origin on the ground under the middle, in blocks.
OL, OD, OLL, TY, ST, SD, GL, WH, SA, SAD, BK, RD = (0x5C683E, 0x424C2E, 0x6E7C4C, 0x242426, 0x8A8E94, 0x3E4246,
                                                    0x28405A, 0xD8D8D2, 0xB8AC8C, 0x8E846A, 0x18181A, 0xBE2824)
PALETTE = [OL, OD, OLL, TY, ST, SD, GL, WH, SA, SAD, BK, RD]

def pair(c, x, y, z, w, h, d):
    return [(c, x, y, z, w, h, d), (c, -x - w, y, z, w, h, d)]

import gen_vehicles as GV
import vehicle_kit as VK

def piv(p):
    return [round(p[0] * 16, 3), round(p[1] * 16, 3), round(-p[2] * 16, 3)]

def own_vehicle(name):
    """A vehicle driven and drawn by Steel Rain itself: one mesh file in the aircraft format with a bone tree,
    running gear on the root, hull on "body" (leans on its springs), turret on the hull, gun or pod on the turret."""
    import gen_aircraft as GA
    m = GV.MODELS[name]()
    def links(b):
        if b["name"].startswith("trackRot"):          # track link: moved along its path by the renderer
            return dict(b, name="track" + b["name"][8:], parent="root")
        return dict(b, parent={"base": "root", "move_body": "body"}.get(b["parent"], b["parent"]))
    tex, tw, th = VK.bake(m, name)
    save(Image.fromarray(tex).convert("RGB"), f"{A}/textures/entity/vehicle/{name}.png")
    # window glass is its own part, so it can be left out while the crew look out from inside
    glass = [p for p in m["base"] if p["mat"] == "glass"]
    m["base"] = [p for p in m["base"] if p["mat"] != "glass"]
    out = GA.export({"base": [], "bones": [
        {"name": "body", "parent": "root", "pivot": (0, 0.6, 0), "cubes": m["base"]},
        *([{"name": "move_glass", "parent": "body", "pivot": (0, 0.6, 0), "cubes": glass}] if glass else []),
        {"name": "turret", "parent": "body", "pivot": GV.PIVOTS[name][0], "cubes": m["turret"]},
        {"name": "barrel", "parent": "turret", "pivot": GV.PIVOTS[name][1], "cubes": m["barrel"]},
        *[links(b) for b in m["bones"] if not b["name"].startswith("trackMov")]]}, tw, th)
    if m.get("track"):   # the path each link follows round the running gear, in blocks and degrees
        t = m["track"]
        out["track"] = {"links": t["links"], "length": t["length"], "y": [round(v / 16, 4) for v in t["y"]],
                        "z": [round(-v / 16, 4) for v in t["z"]], "rot": [-v for v in t["rot"]]}
    path = f"{A}/models/vehicle/{name}.json"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(out, f, separators=(",", ":"))

def vehicles():
    """Steel Rain's own vehicles: mesh models and textures, and their survival recipes."""
    own_vehicle("zu_23_2")
    own_vehicle("gepard")
    own_vehicle("pantsir")
    own_vehicle("patriot")
    own_vehicle("himars")   # driven and laid by Steel Rain itself, not Superb Warfare
    own_vehicle("mortar_120")   # laid and fired through Steel Rain itself
    own_vehicle("m109")   # driven, laid and fired through Steel Rain itself
    own_vehicle("tomahawk_launcher")   # laid and fired through Steel Rain itself
    own_vehicle("iskander_launcher")   # laid and fired through Steel Rain itself
    own_vehicle("jammer")
    own_vehicle("trench_jammer")
    own_vehicle("krasukha")
    # Survival recipes: each one crafts the vehicle as an item; place it on the ground.
    I, N, B = MC + "iron_ingot", MC + "iron_nugget", MC + "iron_block"
    shaped("zu_23_2", ["I I", "IMI", "NBN"], {"I": I, "M": "electric_motor", "N": N, "B": B})
    shaped("gepard", ["UOU", "MCM", "BBB"], {"U": "ammo_35mm", "O": MC + "observer", "M": "electric_motor", "C": "circuit_board", "B": B})
    shaped("pantsir", ["UOU", "MCM", "BBB"], {"U": "guidance_unit", "O": MC + "observer", "M": "electric_motor", "C": "circuit_board", "B": B})
    shaped("tomahawk_launcher", ["IHI", "BCB", "BBB"], {"I": I, "H": MC + "hopper", "B": B, "C": "circuit_board"})
    shaped("iskander_launcher", ["IPI", "BCB", "BMB"], {"I": I, "P": MC + "piston", "B": B, "C": "circuit_board", "M": "electric_motor"})
    shaped("mortar_120", ["  I", " I ", "NBN"], {"I": I, "N": N, "B": B})
    shaped("himars", ["III", "MCM", "BBB"], {"I": I, "M": "electric_motor", "C": "circuit_board", "B": B})
    shaped("jerrycan", [" I ", "I I", "III"], {"I": I}, 2)
    write(f"{D}/recipe/diesel_jerrycan.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": f"{MOD}:jerrycan"}, {"tag": "minecraft:coals"}, {"tag": "minecraft:coals"}],
        "result": {"id": f"{MOD}:diesel_jerrycan", "count": 1}})
    shaped("m109", ["I  ", "BIC", "BMB"], {"I": I, "B": B, "C": "circuit_board", "M": "electric_motor"})
    shaped("jammer", [" A ", "CUC", "BDB"], {"A": MC + "lightning_rod", "C": "circuit_board", "U": "guidance_unit", "B": B, "D": "drone_battery"})
    shaped("trench_jammer", [" A ", "CDC", "III"], {"A": MC + "lightning_rod", "C": "circuit_board", "D": "drone_battery", "I": I})
    shaped("krasukha", ["AUA", "CUC", "BBB"], {"A": MC + "lightning_rod", "U": "guidance_unit", "C": "circuit_board", "B": B})
    shaped("patriot", ["UDU", "MCM", "BBB"], {"U": "guidance_unit", "D": MC + "diamond", "M": "electric_motor", "C": "circuit_board", "B": B})


def aircraft():
    """Aircraft, drones and munitions: a mesh file and a painted texture each, read by Steel Rain's own renderer."""
    import gen_aircraft as GA
    import gen_munitions as GM
    import gen_sites as GS
    for name, (build, px) in {**GA.MODELS, **GM.MODELS, **GS.MODELS}.items():
        model = build()
        tex, tw, th = VK.bake(model, name, px)
        save(Image.fromarray(tex).convert("RGB"), f"{A}/textures/entity/aircraft/{name}.png")
        path = f"{A}/models/aircraft/{name}.json"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            json.dump(GA.export(model, tw, th), f, separators=(",", ":"))


if __name__ == "__main__":
    import gen_sounds
    gen_sounds.main()
    vehicles()
    aircraft()
    guns()
    block_textures(); block_models(); item_icons(); entity_textures(); recipes(); tags(); lang()
    print("assets generated")
