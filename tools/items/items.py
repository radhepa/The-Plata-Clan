"""Item, worn-mask and GUI pixel art for The Plata Clan, plus the item model JSON.

  python tools/items/items.py             write every texture and model file
  python tools/items/items.py --preview   ...and an enlarged contact sheet at tools/preview/out/items_sheet.png

Sprites are character grids (one char per pixel, see PAL) in the DESIGN.md section 7 ramps, lit from the
top left. Most grids hold only the fill; outline() then adds the dark rim where vanilla would draw one.
Nothing here echoes the Yiga look: no red, no white mask, no eye symbol, no sickle.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from mobkit import EGG_MASK, MOD_ID, ROOT, hexc  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", MOD_ID)
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
WORN_TEX = os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid")
GUI_TEX = os.path.join(ASSETS, "textures", "gui")
PREVIEW_OUT = os.path.join(ROOT, "tools", "preview", "out")

# DESIGN.md section 7, deep -> highlight
SILVER = ["#5E646C", "#858C95", "#A9B0B8", "#C9CFD5", "#E6EAEE"]
SLATE = ["#24282D", "#34393F", "#464C54", "#5C636B", "#6E767F"]
UMBER = ["#1F1C19", "#2A2622", "#3A3530", "#4B453F"]
GLINT = ["#8CC7D4", "#BFE6EE"]
SCUTTLER = ["#45566B", "#5A6E86"]
SHARDCASTER = ["#3B6B68", "#4F8A86"]
WARDEN = ["#5F4724", "#8A6A38", "#B08A4E"]

PAL = {
    "1": SILVER[0], "2": SILVER[1], "3": SILVER[2], "4": SILVER[3], "5": SILVER[4],
    "K": SLATE[0], "k": SLATE[1], "b": SLATE[2], "c": SLATE[3], "d": SLATE[4],
    "U": UMBER[0], "u": UMBER[1], "v": UMBER[2], "w": UMBER[3],
    "g": GLINT[0], "h": GLINT[1],
}
CLEAR = (0, 0, 0, 0)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


# -- helpers ---------------------------------------------------------------------------------------
def grid(rows, pal=PAL) -> Image.Image:
    """Character grid -> RGBA image. Any char missing from pal ('.') stays transparent."""
    w = len(rows[0])
    for i, r in enumerate(rows):
        if len(r) != w:
            raise ValueError(f"grid row {i} is {len(r)} wide, expected {w}: {r!r}")
    img = Image.new("RGBA", (w, len(rows)), CLEAR)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y), hexc(pal[ch]))
    return img


def paint(img: Image.Image, x0: int, y0: int, rows, pal=PAL):
    """Stamp a character grid onto img at (x0, y0)."""
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x0 + x, y0 + y), hexc(pal[ch]))


def outline(img: Image.Image, color=SLATE[0], special=None) -> Image.Image:
    """Rim every transparent pixel that touches the sprite (4-neighbour). special maps a fill colour
    to its own rim colour; a rim pixel whose fill neighbours all share one special rim uses it."""
    w, h = img.size
    src = img.copy()
    special = {hexc(k): hexc(v) for k, v in (special or {}).items()}
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3]:
                continue
            near = [src.getpixel((x + dx, y + dy)) for dx, dy in N4
                    if 0 <= x + dx < w and 0 <= y + dy < h and src.getpixel((x + dx, y + dy))[3]]
            if not near:
                continue
            rims = {special.get(c) for c in near}
            img.putpixel((x, y), rims.pop() if len(rims) == 1 and None not in rims else hexc(color))
    return img


def save(img: Image.Image, folder: str, name: str) -> str:
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, name + ".png")
    img.save(path)
    return path


def write_json(rel: str, data: dict):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


ITEMS = ["silver_scale", "silver_shard", "plata_fang", "plata_mask",
         "plata_scuttler_spawn_egg", "plata_shardcaster_spawn_egg", "plata_warden_spawn_egg"]
HANDHELD = {"plata_fang"}


def write_models():
    """26.3 item definitions + item models, the same shape Not-So-Vanilla Mobs uses."""
    for item in ITEMS:
        write_json(f"items/{item}.json",
                   {"model": {"type": "minecraft:model", "model": f"{MOD_ID}:item/{item}"}})
        parent = "minecraft:item/handheld" if item in HANDHELD else "minecraft:item/generated"
        write_json(f"models/item/{item}.json",
                   {"parent": parent, "textures": {"layer0": f"{MOD_ID}:item/{item}"}})
    write_json("equipment/plata_mask.json",
               {"layers": {"humanoid": [{"texture": f"{MOD_ID}:plata_mask"}]}})


# -- 16x16 items -----------------------------------------------------------------------------------
# A scale-plate: three lamellae like shingles, each lower edge a dark arc dipping at the middle, the
# lip of the lamella beneath catching the light just under it. Pointed at the bottom.
SCALE = [
    "................",
    "................",
    "................",
    "....45555443....",
    "..444444433322..",
    "..1144443332bb..",
    "..54114433bb33..",
    "..445411bb4332..",
    "..444455443332..",
    "...144444333b...",
    "...5114333bb3...",
    "....5411bb43....",
    ".....455443.....",
    "......4332......",
    ".......21.......",
    "................",
]


def silver_scale() -> Image.Image:
    return outline(grid(SCALE))


# The throwing shard: a faceted sliver along the x+y=15 diagonal, tip top-right.
# Row -> (lowest, highest) x+y it covers. Upper-left facet light, ridge bright, lower-right facet dark.
SHARD_ROWS = {1: (15, 15), 2: (15, 16), 3: (14, 16), 4: (14, 17), 5: (14, 17), 6: (14, 17),
              7: (15, 17), 8: (15, 17), 9: (15, 16), 10: (15, 16), 11: (15, 16), 12: (15, 15)}


def silver_shard() -> Image.Image:
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y, (lo, hi) in SHARD_ROWS.items():
        for s in range(lo, hi + 1):
            base = y >= 10
            tone = {14: 3, 15: 3 if base else 4, 16: 0 if base else 1, 17: 0}[s]
            img.putpixel((s - y, y), hexc(SILVER[tone]))
    img.putpixel((11, 4), hexc(GLINT[1]))
    return outline(img)


# The fang: narrow leaf blade, slate guard, umber-wrapped grip, small round pommel.
FANG = [
    "................",
    "................",
    ".............4..",
    "............52..",
    "...........52...",
    "..........542...",
    ".........542....",
    ".....d..431.....",
    "......c31.......",
    "......wb........",
    ".....vu.k.......",
    "....wv..........",
    "...vu...........",
    "..43............",
    "..21............",
    "................",
]


def plata_fang() -> Image.Image:
    grip_rim = {UMBER[1]: UMBER[0], UMBER[2]: UMBER[0], UMBER[3]: UMBER[0]}
    return outline(grid(FANG), special=grip_rim)


# The mask icon: brow band (with the clan glyph), eye band (two slits, a glint at each light end),
# cheek band and jaw, each overlapping the next; two feeler stubs from the brow corners.
MASK = [
    "................",
    "................",
    "................",
    "....55533443....",
    "...4444kk3332...",
    "...2222bb1111...",
    "..455544444332..",
    "..44gKK43gKK32..",
    "..222221111111..",
    "..455544443332..",
    "...2222111111...",
    "...4555444332...",
    "....44443332....",
    ".....322211.....",
    "......2111......",
    "................",
]
MASK_FEELERS = [
    "..c..........c..",
    "...K........K...",
]


def plata_mask_icon() -> Image.Image:
    img = outline(grid(MASK))
    paint(img, 0, 0, MASK_FEELERS)
    return img


# Spawn eggs: a silver egg (vanilla outline), with rank-coloured spots. 'a' dark spot, 'A' light spot.
EGG_SPOTS = [
    "................",
    "................",
    "........Aa......",
    "........a.......",
    "................",
    "...........Aa...",
    "............a...",
    "....Aa..........",
    ".....a....Aa....",
    "..........a.....",
    "...A........Aa..",
    "...a.........a..",
    "......Aa........",
    ".......a........",
    "................",
    "................",
]
RANKS = {"scuttler": SCUTTLER, "shardcaster": SHARDCASTER, "warden": WARDEN[1:]}


def spawn_egg(accent) -> Image.Image:
    img = Image.new("RGBA", (16, 16), CLEAR)
    inside = {(x, y) for y, row in enumerate(EGG_MASK) for x, ch in enumerate(row) if ch == "#"}
    for (x, y) in inside:
        d = x + y
        if any((x + dx, y + dy) not in inside for dx, dy in N4):
            c = SILVER[0] if d <= 14 else SLATE[2]
        else:
            c = SILVER[3] if d <= 11 else SILVER[2] if d <= 17 else SILVER[1] if d <= 22 else SILVER[0]
        img.putpixel((x, y), hexc(c))
    for (x, y) in ((5, 3), (4, 4), (4, 5), (5, 4)):
        img.putpixel((x, y), hexc(SILVER[4]))
    paint(img, 0, 0, EGG_SPOTS, {"a": accent[0], "A": accent[-1]})
    return img


# -- the mask as worn (vanilla humanoid armour sheet, 64x32) ------------------------------------------
# Head box net at UV (0,0): top (8,0), right (0,8), front (8,8), left (16,8), back (24,8). Faces are
# drawn as seen from outside. Front: 4 overlapping bands, glyph on the brow, eye slits in the gap
# between the eye band and the cheek band, a cold glint at the light end of each slit.
WORN_FRONT = [
    "45555443",
    "444bb332",
    "22222111",
    "45554433",
    "2gK21gK1",
    "45544433",
    "22221111",
    "44443332",
]
# The mob's right side; column 7 meets the front, where the plate wraps and the cheek flap sits.
WORN_SIDE = [
    "dcccdcc3",
    "cccbccb3",
    "cbcbcck1",
    "bcbbcbk3",
    "bbbkbb31",
    "kbbbbk43",
    "bkbbbk21",
    "kkkkkkk2",
]
# Crown: row 0 is the back edge, row 7 the brow. Silver rim at the brow, two feeler stubs sweeping
# back from its corners, and the dorsal ridge (plates with a dark rear edge) down the middle.
WORN_TOP = [
    "cdc43cdc",
    "dcc21ccd",
    "cdc43cdc",
    "ccd21dcc",
    "cdc43cdc",
    "2cc21cc2",
    "c3c43c3c",
    "43344332",
]
# Back: the last ridge plate ends at the nape, then plain hood down to a dark hem.
WORN_BACK = [
    "cbc32cbc",
    "bcb21bcb",
    "cbbbbbbc",
    "bbcbbcbb",
    "bkbbbbkb",
    "bbkbbkbb",
    "kbbkkbbk",
    "kkkkkkkk",
]


def worn_mask() -> Image.Image:
    img = Image.new("RGBA", (64, 32), CLEAR)
    paint(img, 8, 0, WORN_TOP)
    paint(img, 0, 8, WORN_SIDE)
    paint(img, 8, 8, WORN_FRONT)
    paint(img, 16, 8, [r[::-1] for r in WORN_SIDE])
    paint(img, 24, 8, WORN_BACK)
    return img


# -- the clan mark: a silverfish seen from above -----------------------------------------------------
# Half-width per row about x = 15.5 (body), segments (first row light, last row dark), and the left
# feeler / left tail strand plus the centre strand; the right side mirrors (x -> 31 - x).
MARK_HW = {9: 2, 10: 3, 11: 4, 12: 4, 13: 5, 14: 5, 15: 5, 16: 4, 17: 4, 18: 4,
           19: 3, 20: 3, 21: 3, 22: 2, 23: 2, 24: 1, 25: 1}
MARK_SEGS = [(9, 12), (13, 15), (16, 18), (19, 21), (22, 23), (24, 25)]
MARK_FEELER = [(13, 8), (12, 7), (11, 6), (10, 5), (9, 5)]
MARK_TAIL = [(15, 26), (15, 27), (15, 28), (14, 26), (13, 27), (12, 28)]


def clan_mark() -> Image.Image:
    img = Image.new("RGBA", (32, 32), CLEAR)
    for top, bot in MARK_SEGS:
        for y in range(top, bot + 1):
            hw = MARK_HW[y]
            t = 2 if top == bot else 3 if y == top else 1 if y == bot else 2
            for x in range(16 - hw, 16 + hw):
                rel = (x + 0.5 - 16) / hw
                tone = t + (1 if rel < -0.45 else -1 if rel > 0.45 else 0)
                img.putpixel((x, y), hexc(SILVER[max(0, min(4, tone))]))
    for (x, y) in MARK_FEELER:
        img.putpixel((x, y), hexc(SILVER[3]))
        img.putpixel((31 - x, y), hexc(SILVER[2]))
    for (x, y) in MARK_TAIL:
        img.putpixel((x, y), hexc(SILVER[3]))
        img.putpixel((31 - x, y), hexc(SILVER[1]))
    return outline(img)


def mod_icon() -> Image.Image:
    """The mark on a dark slate roundel with a bevelled rim, drawn at 32x32 and scaled x4."""
    img = Image.new("RGBA", (32, 32), CLEAR)
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            r = math.hypot(dx, dy)
            if r > 16.0:
                continue
            if r > 15.0:
                c = SLATE[0]
            elif r > 14.0:
                c = SLATE[3] if dx + dy < 0 else SLATE[2]
            else:
                c = SLATE[1]
            img.putpixel((x, y), hexc(c))
    img.alpha_composite(clan_mark())
    return img.resize((128, 128), Image.NEAREST)


# -- build -------------------------------------------------------------------------------------------
def build() -> dict:
    """Write every texture and model file; return {name: image} for the preview."""
    out = {}
    for name, fn in (("silver_scale", silver_scale), ("silver_shard", silver_shard),
                     ("plata_fang", plata_fang), ("plata_mask", plata_mask_icon)):
        out[name] = fn()
        save(out[name], ITEM_TEX, name)
    for rank, accent in RANKS.items():
        name = f"plata_{rank}_spawn_egg"
        out[name] = spawn_egg(accent)
        save(out[name], ITEM_TEX, name)
    out["worn_mask"] = worn_mask()
    save(out["worn_mask"], WORN_TEX, "plata_mask")
    out["icon"] = mod_icon()
    out["icon"].save(os.path.join(ASSETS, "icon.png"))
    out["plata_mark"] = clan_mark()
    save(out["plata_mark"], GUI_TEX, "plata_mark")
    write_models()
    return out


# -- preview -----------------------------------------------------------------------------------------
INK = (30, 30, 30, 255)
SLOT = (139, 139, 139, 255)
DARK = (28, 30, 34, 255)


def checker(w, h, cell=8) -> Image.Image:
    img = Image.new("RGBA", (w, h), (205, 205, 205, 255))
    d = ImageDraw.Draw(img)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(170, 170, 170, 255))
    return img


def tile(img, scale, bg=None) -> Image.Image:
    big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    base = checker(*big.size) if bg is None else Image.new("RGBA", big.size, bg)
    base.alpha_composite(big)
    return base


def preview(images) -> str:
    sheet = Image.new("RGBA", (1040, 560), (198, 198, 198, 255))
    d = ImageDraw.Draw(sheet)
    small = [n for n, im in images.items() if im.size == (16, 16)]
    for i, n in enumerate(small):                      # x8 on the inventory slot grey
        x = 16 + i * 144
        sheet.alpha_composite(tile(images[n], 8, SLOT), (x, 16))
        d.text((x, 148), n, fill=INK)
    d.text((16, 172), "actual size in slots (1x, 2x) and on a dark ground (2x)", fill=INK)
    for i, n in enumerate(small):
        slot = Image.new("RGBA", (18, 18), SLOT)
        slot.alpha_composite(images[n], (1, 1))
        sheet.alpha_composite(slot, (16 + i * 20, 190))
        sheet.alpha_composite(tile(slot, 2), (180 + i * 40, 190))
        sheet.alpha_composite(tile(images[n], 2, DARK), (480 + i * 36, 190))
    y = 250
    worn = images["worn_mask"]
    sheet.alpha_composite(tile(worn, 6), (16, y))
    d.text((16, y + 196), "worn plata_mask 64x32 (x6)", fill=INK)
    sheet.alpha_composite(tile(worn.crop((0, 8, 24, 16)), 8, DARK), (416, y))       # right | front | left
    sheet.alpha_composite(tile(worn.crop((8, 0, 16, 8)), 8, DARK), (416 + 64, y + 72))  # crown
    d.text((416, y + 140), "sides+front (x8), crown below", fill=INK)
    sheet.alpha_composite(images["icon"], (640, y))
    d.text((640, y + 132), "icon.png 128", fill=INK)
    sheet.alpha_composite(tile(images["plata_mark"], 4), (784, y))
    sheet.alpha_composite(tile(images["plata_mark"], 2, DARK), (920, y))
    sheet.alpha_composite(tile(images["plata_mark"], 1, DARK), (920, y + 72))
    d.text((784, y + 132), "gui/plata_mark x4, x2, x1", fill=INK)
    os.makedirs(PREVIEW_OUT, exist_ok=True)
    path = os.path.join(PREVIEW_OUT, "items_sheet.png")
    sheet.save(path)
    return path


def main():
    ap = argparse.ArgumentParser(description="Plata Clan item, worn-mask and GUI textures + item models.")
    ap.add_argument("--preview", action="store_true", help="also write tools/preview/out/items_sheet.png")
    args = ap.parse_args()
    images = build()
    print(f"wrote {len(images)} textures and {len(ITEMS) * 2 + 1} model files under {ASSETS}")
    if args.preview:
        print("preview:", preview(images))


if __name__ == "__main__":
    main()


