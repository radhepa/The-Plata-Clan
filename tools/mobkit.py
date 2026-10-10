"""The Plata Clan model kit (from the Not-So-Vanilla Mobs kit): one Python file per model describes its parts and cubes, in Minecraft
model space) and paints its pixel texture face by face.

Output, per mob:
  src/client/resources/assets/<MOD_ID>/geometry/<id>.json   model, loaded by the client at startup
  src/main/resources/assets/<MOD_ID>/textures/entity/<id>.png         texture (box UV)
  src/main/resources/assets/<MOD_ID>/textures/entity/<id>_glow.png    optional emissive layer

Coordinates follow vanilla ModelPart: y points DOWN, the mob faces -z, the mob's right side is -x.
A part has a pivot (offset from its parent) and a rotation (radians, applied Z then Y then X like
ModelPart). A cube has an origin relative to the pivot, a size, a texture offset and an inflate.

Painting: every cube face gets a painter, called with a Canvas in face-local pixels as seen from
OUTSIDE the mob, x to the right and y downward. Faces are named by what you see:
  front (-z), back (+z), right (the mob's right, -x), left (+x), top, bottom.
On top/bottom, row 0 is the BACK edge and column 0 is the mob's RIGHT side.
"""
from __future__ import annotations

import json
import math
import os
import random
from typing import Callable, Dict, List, Optional, Sequence

from PIL import Image

MOD_ID = "plataclan"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEOMETRY_DIR = os.path.join(ROOT, "src", "client", "resources", "assets", MOD_ID, "geometry")
TEXTURE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", MOD_ID, "textures", "entity")
ITEM_TEXTURE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", MOD_ID, "textures", "item")

FACES = ("top", "bottom", "right", "front", "left", "back")


def hexc(c) -> tuple:
    """'#rrggbb' or (r, g, b[, a]) to an RGBA tuple."""
    if isinstance(c, tuple):
        return c if len(c) == 4 else (c[0], c[1], c[2], 255)
    c = c.lstrip("#")
    if len(c) == 6:
        return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), int(c[6:8], 16))


def shade(c, f: float) -> tuple:
    """Lighten (f > 1) or darken (f < 1) a colour."""
    r, g, b, a = hexc(c)
    return (max(0, min(255, int(r * f))), max(0, min(255, int(g * f))), max(0, min(255, int(b * f))), a)


def mix(a, b, t: float) -> tuple:
    a, b = hexc(a), hexc(b)
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


class Canvas:
    """One cube face. set() writes the base texture; glow() writes the base AND the emissive layer."""

    def __init__(self, model: "Model", x0: int, y0: int, w: int, h: int, face: str, rng: random.Random):
        self.model, self.x0, self.y0, self.w, self.h, self.face, self.rng = model, x0, y0, w, h, face, rng

    def inside(self, x, y) -> bool:
        return 0 <= x < self.w and 0 <= y < self.h

    def set(self, x, y, c):
        if c is None or not self.inside(x, y):
            return
        self.model.image.putpixel((self.x0 + x, self.y0 + y), hexc(c))

    def get(self, x, y) -> tuple:
        return self.model.image.getpixel((self.x0 + x, self.y0 + y))

    def glow(self, x, y, c):
        if not self.inside(x, y):
            return
        self.set(x, y, c)
        self.model.glow_image().putpixel((self.x0 + x, self.y0 + y), hexc(c))

    def clear(self, x, y):
        if self.inside(x, y):
            self.model.image.putpixel((self.x0 + x, self.y0 + y), (0, 0, 0, 0))

    def pick(self, pal: Sequence) -> tuple:
        return hexc(self.rng.choice(pal))

    def fill(self, c):
        for y in range(self.h):
            for x in range(self.w):
                self.set(x, y, c)

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, c)

    def noise(self, pal: Sequence, x=0, y=0, w=None, h=None):
        w = self.w if w is None else w
        h = self.h if h is None else h
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, self.pick(pal))

    def speckle(self, pal: Sequence, chance: float):
        for y in range(self.h):
            for x in range(self.w):
                if self.rng.random() < chance:
                    self.set(x, y, self.pick(pal))


Painter = Callable[[Canvas], None]


def faces(all: Optional[Painter] = None, **per_face: Painter) -> Dict[str, Painter]:
    """faces(all=fur, front=face_painter) -> painter per face; 'sides' sets front/back/left/right."""
    out = {f: all for f in FACES}
    sides = per_face.pop("sides", None)
    if sides:
        for f in ("front", "back", "left", "right"):
            out[f] = sides
    for k, v in per_face.items():
        if k not in FACES:
            raise ValueError("unknown face " + k)
        out[k] = v
    return out


def noise(*pal) -> Painter:
    return lambda c: c.noise(pal)


def chain(*painters: Painter) -> Painter:
    def run(c: Canvas):
        for p in painters:
            if p:
                p(c)
    return run


class Cube:
    def __init__(self, uv, origin, size, inflate=0.0, mirror=False, paint=None):
        self.uv, self.origin, self.size, self.inflate, self.mirror = tuple(uv), tuple(origin), tuple(size), inflate, mirror
        self.paint = paint if isinstance(paint, dict) else faces(paint)

    def rects(self):
        """Texture rectangles per face, straight from ModelPart.Cube (u0..u4, v0..v2)."""
        u, v = self.uv
        w, h, d = (int(math.ceil(s)) for s in self.size)
        return {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + d + w + d, v + d, w, h),
        }

    def json(self):
        return {"uv": list(self.uv), "from": list(self.origin), "size": list(self.size),
                "inflate": self.inflate, "mirror": self.mirror}


class Part:
    def __init__(self, model: "Model", name: str, parent: Optional["Part"], pivot, rotation):
        self.model, self.name, self.parent = model, name, parent
        self.pivot, self.rotation = tuple(pivot), tuple(rotation)
        self.cubes: List[Cube] = []

    def cube(self, uv, origin, size, paint=None, inflate=0.0, mirror=False) -> "Part":
        self.cubes.append(Cube(uv, origin, size, inflate, mirror, paint))
        return self

    def part(self, name, pivot=(0, 0, 0), rotation=(0, 0, 0)) -> "Part":
        return self.model.part(name, pivot, rotation, parent=self)


class Model:
    def __init__(self, mob_id: str, tex_w: int = 64, tex_h: int = 64, seed: int = 1, texture_id: Optional[str] = None):
        """texture_id: save the texture under another name (texture variants share one geometry)."""
        self.id, self.tex_w, self.tex_h = mob_id, tex_w, tex_h
        self.texture_id = texture_id or mob_id
        self.parts: List[Part] = []
        self.image = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
        self._glow: Optional[Image.Image] = None
        self.rng = random.Random(seed)
        self.preview: dict = {}   # optional preview pose overrides {part: [rx, ry, rz]}
        self.scale = 1.0          # whole-model render scale (the renderer applies it)
        self.extra_textures: Dict[str, Image.Image] = {}

    def part(self, name, pivot=(0, 0, 0), rotation=(0, 0, 0), parent: Optional[Part] = None) -> Part:
        if any(p.name == name for p in self.parts):
            raise ValueError("duplicate part " + name)
        p = Part(self, name, parent, pivot, rotation)
        self.parts.append(p)
        return p

    def get(self, name) -> Part:
        return next(p for p in self.parts if p.name == name)

    def glow_image(self) -> Image.Image:
        if self._glow is None:
            self._glow = Image.new("RGBA", (self.tex_w, self.tex_h), (0, 0, 0, 0))
        return self._glow

    # -- painting ----------------------------------------------------------------------------------
    def paint(self):
        used = {}
        for part in self.parts:
            for cube in part.cubes:
                for face, (x, y, w, h) in cube.rects().items():
                    if w <= 0 or h <= 0:
                        continue
                    if x + w > self.tex_w or y + h > self.tex_h:
                        raise ValueError(f"{self.id}: {part.name} {face} face runs off the texture")
                    painter = cube.paint.get(face)
                    if painter is None:
                        continue
                    key = (x, y, w, h)
                    for yy in range(y, y + h):
                        for xx in range(x, x + w):
                            owner = used.get((xx, yy))
                            if owner and owner[0] != key and not cube.mirror:
                                raise ValueError(f"{self.id}: {part.name} {face} overlaps {owner[1]} at {xx},{yy}")
                            used[(xx, yy)] = (key, f"{part.name}.{face}")
                    painter(Canvas(self, x, y, w, h, face, self.rng))

    # -- export ------------------------------------------------------------------------------------
    def geometry(self) -> dict:
        return {
            "texture": [self.tex_w, self.tex_h],
            "parts": [{
                "name": p.name,
                "parent": p.parent.name if p.parent else "",
                "pivot": list(p.pivot),
                "rotation": [round(r, 5) for r in p.rotation],
                "cubes": [c.json() for c in p.cubes],
            } for p in self.parts],
            **({"preview": self.preview} if self.preview else {}),
            **({"scale": self.scale} if self.scale != 1.0 else {}),
        }

    def save(self, geometry: bool = True):
        """Paint and write files. geometry=False writes only the texture (for a texture variant)."""
        self.paint()
        os.makedirs(GEOMETRY_DIR, exist_ok=True)
        os.makedirs(TEXTURE_DIR, exist_ok=True)
        if geometry:
            with open(os.path.join(GEOMETRY_DIR, self.id + ".json"), "w", encoding="utf-8") as f:
                json.dump(self.geometry(), f, indent=1)
        self.image.save(os.path.join(TEXTURE_DIR, self.texture_id + ".png"))
        glow_path = os.path.join(TEXTURE_DIR, self.texture_id + "_glow.png")
        if self._glow is not None:
            self._glow.save(glow_path)
        elif os.path.exists(glow_path):
            os.remove(glow_path)
        for suffix, img in self.extra_textures.items():
            img.save(os.path.join(TEXTURE_DIR, f"{self.texture_id}_{suffix}.png"))


class Pack:
    """Collects cubes, then shelf-packs their box-UV rectangles tallest-first into free texture areas,
    so a model never needs hand-placed UVs. Cubes added with the same `share` key reuse one rectangle
    (left/right limbs with mirror=True)."""

    def __init__(self, model: "Model", areas=None):
        self.m, self.items = model, []
        self.areas = areas or [(0, 0, model.tex_w, model.tex_h)]

    def add(self, part: "Part", origin, size, paint=None, mirror=False, share=None, inflate=0.0):
        self.items.append((part, origin, size, paint, mirror, share, inflate))
        return self

    def apply(self):
        def dims(size):
            w, h, d = (int(math.ceil(s)) for s in size)
            return 2 * d + 2 * w, d + h
        keys, order = {}, []
        for i, it in enumerate(self.items):
            k = it[5] or ("#", i)
            if k not in keys:
                keys[k] = None
                order.append((k, dims(it[2])))
        order.sort(key=lambda kv: (-kv[1][1], -kv[1][0]))
        shelves, tops = [], [a[1] for a in self.areas]
        for k, (w, h) in order:
            for s in shelves:                       # s = [y, height, next x, right edge]
                if h <= s[1] and s[2] + w <= s[3]:
                    keys[k] = (s[2], s[0])
                    s[2] += w
                    break
            else:
                for i, (ax, ay, aw, ah) in enumerate(self.areas):
                    if w <= aw and tops[i] + h <= ay + ah:
                        shelves.append([tops[i], h, ax + w, ax + aw])
                        keys[k] = (ax, tops[i])
                        tops[i] += h
                        break
                else:
                    raise ValueError(f"{self.m.id}: no texture room for {k} {w}x{h}")
        for i, (part, origin, size, paint, mirror, share, inflate) in enumerate(self.items):
            part.cube(keys[share or ("#", i)], origin, size, paint, inflate=inflate, mirror=mirror)


def humanoid(model: Model, skin: Dict[str, Dict[str, Painter]], arm_width: int = 4, slim_limbs: bool = False):
    """The seven parts vanilla HumanoidModel needs (head, hat, body, arms, legs) on a 64x64 sheet.
    Left limbs mirror the right ones like vanilla zombies. skin maps part -> faces dict.
    slim_limbs=True gives skeleton-style 2x12x2 limbs."""
    head = model.part("head")
    head.cube((0, 0), (-4, -8, -4), (8, 8, 8), skin["head"])
    head.part("hat")
    body = model.part("body")
    body.cube((16, 16), (-4, 0, -2), (8, 12, 4), skin["body"])
    if slim_limbs:
        model.part("right_arm", (-5, 2, 0)).cube((40, 16), (-1, -2, -1), (2, 12, 2), skin["arm"])
        model.part("left_arm", (5, 2, 0)).cube((40, 16), (-1, -2, -1), (2, 12, 2), skin["arm"], mirror=True)
        model.part("right_leg", (-2, 12, 0)).cube((0, 16), (-1, 0, -1), (2, 12, 2), skin["leg"])
        model.part("left_leg", (2, 12, 0)).cube((0, 16), (-1, 0, -1), (2, 12, 2), skin["leg"], mirror=True)
    else:
        model.part("right_arm", (-5, 2, 0)).cube((40, 16), (-3, -2, -2), (4, 12, 4), skin["arm"])
        model.part("left_arm", (5, 2, 0)).cube((40, 16), (-1, -2, -2), (4, 12, 4), skin["arm"], mirror=True)
        model.part("right_leg", (-1.9, 12, 0)).cube((0, 16), (-2, 0, -2), (4, 12, 4), skin["leg"])
        model.part("left_leg", (1.9, 12, 0)).cube((0, 16), (-2, 0, -2), (4, 12, 4), skin["leg"], mirror=True)
    return head, body


# -- spawn eggs ------------------------------------------------------------------------------------
EGG_MASK = [
    "......####......",
    ".....######.....",
    "....########....",
    "...##########...",
    "...##########...",
    "..############..",
    "..############..",
    "..############..",
    ".##############.",
    ".##############.",
    ".##############.",
    ".##############.",
    "..############..",
    "..############..",
    "...##########...",
    ".....######.....",
]


def spawn_egg(mob_id: str, base, spots, accent=None, seed: int = 7):
    """A 16x16 spawn egg: shaded base, outline, highlight, scattered spots (and an optional accent)."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    inside = {(x, y) for y, row in enumerate(EGG_MASK) for x, ch in enumerate(row) if ch == "#"}
    for (x, y) in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        lit = 1.12 - 0.035 * (x + y - 8)
        c = shade(base, max(0.55, min(1.25, lit)))
        if edge:
            c = shade(base, 0.45)
        img.putpixel((x, y), c)
    spots_at = [p for p in inside if rng.random() < 0.2]
    for (x, y) in spots_at:
        if all((x + dx, y + dy) in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            img.putpixel((x, y), shade(spots, 1.0 if (x + y) % 3 else 0.82))
    if accent:
        for (x, y) in [p for p in inside if rng.random() < 0.06]:
            if all((x + dx, y + dy) in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.putpixel((x, y), hexc(accent))
    for (x, y) in ((5, 3), (4, 4), (4, 5), (5, 4)):
        if (x, y) in inside:
            img.putpixel((x, y), shade(base, 1.6))
    os.makedirs(ITEM_TEXTURE_DIR, exist_ok=True)
    img.save(os.path.join(ITEM_TEXTURE_DIR, mob_id + "_spawn_egg.png"))
