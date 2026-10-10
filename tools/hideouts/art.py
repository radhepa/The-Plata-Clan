"""Art for the hideouts: the silver husk (textures, block models, blockstates, item), the lookout mark
(no model: it is invisible), and the clan's banner pattern (banner and shield textures, the loom item).
All drawn here from the clan's palette in DESIGN.md; no vanilla pixels are copied.

  python tools/hideouts/art.py
"""
import json
import os

from PIL import Image

from kit import RES, NS
from pieces import MARK

ASSETS = os.path.join(RES, 'assets', NS)
SILVER = ['#5E646C', '#858C95', '#A9B0B8', '#C9CFD5', '#E6EAEE']
SLATE = ['#24282D', '#34393F', '#464C54', '#5C636B', '#6E767F']
BRONZE = ['#5F4724', '#8A6A38', '#B08A4E']
GLINT = ['#8CC7D4', '#BFE6EE']
PAPER = ['#8A7B5E', '#B9A983', '#D9CBA3', '#EFE4C3']


def rgb(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def save(img, *parts):
    path = os.path.join(ASSETS, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write(obj, *parts):
    path = os.path.join(ASSETS, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(obj, indent=2) + '\n')


# -- husk textures -------------------------------------------------------------------------------

def plates(trim=None):
    """The shell's side: overlapping horizontal plates, each lit along its top edge and shadowed at its
    lower lip, with the tiny dark leg-ticks of a silverfish down both sides. Trim: bronze edging (elder)."""
    img = Image.new('RGBA', (16, 16))
    bands = [(0, 4), (4, 8), (8, 12), (12, 16)]
    for i, (a, b) in enumerate(bands):
        for y in range(a, b):
            t = y - a
            tone = 3 if t == 0 else 2 if t == 1 else 1 if y < b - 1 else 0
            for x in range(16):
                shade = tone + (1 if x < 3 and tone < 4 else 0) - (1 if x > 12 and tone > 0 else 0)
                c = SILVER[max(0, min(4, shade))]
                if trim and t == b - a - 1: c = trim[1] if 2 < x < 13 else trim[0]
                img.putpixel((x, y), rgb(c))
        # the seam under each plate: a dark line with a glint
        img.putpixel((7 + i % 3, a), rgb(SILVER[4]))
    for y in (2, 6, 10, 14):  # leg ticks
        img.putpixel((0, y), rgb(SLATE[1])); img.putpixel((15, y), rgb(SLATE[1]))
    return img


def cap():
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** .5
            tone = 4 if r < 2 else 3 if r < 4.5 else 2 if r < 6.5 else 1
            img.putpixel((x, y), rgb(SILVER[tone]))
    for x in range(4, 12):  # the dorsal seam where the shell split once before
        img.putpixel((x, 7), rgb(SILVER[0]))
    return img


def hollow():
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            edge = min(x, 15 - x, y, 15 - y)
            tone = 0 if edge > 2 else 1 if edge > 0 else 2
            img.putpixel((x, y), rgb(SLATE[tone]))
    for (x, y) in ((4, 5), (11, 9), (7, 12)):  # a few scales still stuck inside
        img.putpixel((x, y), rgb(SILVER[1]))
    return img


def husk_icon():
    """A 16x16 item icon: the standing husk, feelers up."""
    img = Image.new('RGBA', (16, 16))
    widths = {2: 2, 3: 4, 4: 5, 5: 6, 6: 6, 7: 6, 8: 6, 9: 6, 10: 6, 11: 5, 12: 5, 13: 4, 14: 4}
    for y, hw in widths.items():
        for x in range(8 - hw // 2 - (hw % 2), 8 + hw // 2):
            band = (y - 3) % 3
            tone = 3 if band == 0 else 2 if band == 1 else 1
            if x == 8 - hw // 2 - (hw % 2): tone = min(4, tone + 1)
            if x == 8 + hw // 2 - 1: tone = max(0, tone - 1)
            img.putpixel((x, y), rgb(SILVER[tone]))
    for (x, y) in ((6, 1), (5, 0), (9, 1), (10, 0)):
        img.putpixel((x, y), rgb(SILVER[3]))
    for x in range(5, 11): img.putpixel((x, 15), rgb(SLATE[1]))
    return img


# -- husk models ---------------------------------------------------------------------------------

def face(tex, uv=None, cull=None):
    f = {'texture': tex}
    if uv: f['uv'] = uv
    if cull: f['cullface'] = cull
    return f


def box(frm, to, side='#side', top='#top', bottom='#top', north=None, name=None):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    faces = {
        'north': face(north or side, [x0, 16 - y1, x1, 16 - y0]),
        'south': face(side, [16 - x1, 16 - y1, 16 - x0, 16 - y0]),
        'west': face(side, [z0, 16 - y1, z1, 16 - y0]),
        'east': face(side, [16 - z1, 16 - y1, 16 - z0, 16 - y0]),
        'up': face(top, [x0, z0, x1, z1]),
        'down': face(bottom, [x0, 16 - z1, x1, 16 - z0]),
    }
    e = {'from': list(frm), 'to': list(to), 'faces': faces}
    if name: e['name'] = name
    return e


def husk_model(elder, upper, split):
    """Segments stacked like a silverfish's back, wider at the shoulders. Facing north (front = low z)."""
    w = 1 if elder else 0  # an elder's shell is a pixel larger all round
    side, top = '#side', '#top'
    segs = ([(0, 3, 4), (3, 7, 3), (7, 11, 3), (11, 16, 3)] if not upper
            else [(0, 4, 3), (4, 8, 4), (8, 11, 5), (11, 13, 6)])
    elements = []
    for (y0, y1, inset) in segs:
        i = inset - w
        z_front = 8 if split else i
        elements.append(box((i, y0, z_front), (16 - i, y1, 16 - i), side, top, top,
                            north='#hollow' if split else None))
        if split:  # the broken-open front: jagged shell edges standing forward on both sides
            depth = 3 + (y0 * 7) % 4
            elements.append(box((i, y0, max(i, 8 - depth)), (i + 1, y1, 8), side, top, top))
            elements.append(box((15 - i, y0, max(i, 8 - depth + 1)), (16 - i, y1, 8), side, top, top))
    if upper and not split:
        # two feeler stubs swept back from the crown
        for x, angle in ((6, -22.5), (9, 22.5)):
            elements.append({'from': [x, 12, 8], 'to': [x + 1, 16, 9], 'rotation': {'origin': [x + .5, 12, 8.5], 'axis': 'z', 'angle': angle},
                             'faces': {d: face('#top', [0, 0, 1, 4]) for d in ('north', 'south', 'east', 'west', 'up', 'down')}})
    textures = {'side': f'{NS}:block/silver_husk{"_elder" if elder else ""}_side', 'top': f'{NS}:block/silver_husk_top',
                'hollow': f'{NS}:block/silver_husk_inside', 'particle': f'{NS}:block/silver_husk_side'}
    return {'textures': textures, 'elements': elements}


def husk():
    save(plates(), 'textures', 'block', 'silver_husk_side.png')
    save(plates(BRONZE), 'textures', 'block', 'silver_husk_elder_side.png')
    save(cap(), 'textures', 'block', 'silver_husk_top.png')
    save(hollow(), 'textures', 'block', 'silver_husk_inside.png')
    save(husk_icon(), 'textures', 'item', 'silver_husk.png')
    variants = {}
    for kind in ('sleeper', 'elder'):
        for half in ('lower', 'upper'):
            for split in (False, True):
                name = f'silver_husk_{kind}_{half}{"_split" if split else ""}'
                write(husk_model(kind == 'elder', half == 'upper', split), 'models', 'block', name + '.json')
                for facing, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
                    v = {'model': f'{NS}:block/{name}'}
                    if rot: v['y'] = rot
                    variants[f'facing={facing},half={half},kind={kind},split={str(split).lower()}'] = v
    write({'variants': variants}, 'blockstates', 'silver_husk.json')
    write({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/silver_husk'}}, 'models', 'item', 'silver_husk.json')
    write({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/silver_husk'}}, 'items', 'silver_husk.json')
    # the lookout's mark: nothing to draw
    write({'textures': {'particle': 'minecraft:block/stone'}}, 'models', 'block', 'lookout.json')
    write({'variants': {'': {'model': f'{NS}:block/lookout'}}}, 'blockstates', 'lookout.json')


# -- the banner pattern --------------------------------------------------------------------------

TONES = {'F': 255, 'H': 235, 'A': 255, 'B': 205, 'S': 170}


def mark_pixels(scale):
    for z, row in enumerate(MARK):
        for x, c in enumerate(row):
            if c in TONES:
                for dy in range(scale):
                    for dx in range(scale):
                        yield x * scale + dx, z * scale + dy, TONES[c]


def banner():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    # front face x 1..20, y 1..40; the back face mirrors it at x' = 41 - x
    for x, y, v in mark_pixels(2):
        fx, fy = 2 + x, 2 + y
        img.putpixel((fx, fy), (v, v, v, 255))
        img.putpixel((41 - fx, fy), (v, v, v, 255))
    save(img, 'textures', 'entity', 'banner', 'plata_mark.png')
    sh = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    for x, y, v in mark_pixels(1):  # shield face x 2..11, y 2..21
        sh.putpixel((2 + x, 2 + y), (v, v, v, 255))
    save(sh, 'textures', 'entity', 'shield', 'plata_mark.png')
    # the loom item: a curled sheet of paper with the mark drawn on it in silver
    it = Image.new('RGBA', (16, 16))
    for y in range(1, 15):
        for x in range(3, 13):
            tone = 3 if 4 <= x <= 11 else 2
            if y in (1, 14): tone = 1
            it.putpixel((x, y), rgb(PAPER[tone]))
    for y in range(2, 14):
        it.putpixel((3, y), rgb(PAPER[1])); it.putpixel((12, y), rgb(PAPER[0]))
    small = ['F...F', '.HHH.', 'AAAAS', 'BBBBS', 'AAAAS', '.BBS.', '..A..', '.F.F.']
    for z, row in enumerate(small):
        for x, c in enumerate(row):
            if c != '.':
                it.putpixel((5 + x, 3 + z + (1 if z > 6 else 0)),
                            rgb({'F': SILVER[1], 'H': SILVER[0], 'A': SILVER[2], 'B': SILVER[1], 'S': SILVER[0]}[c]))
    save(it, 'textures', 'item', 'plata_mark_banner_pattern.png')
    write({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/plata_mark_banner_pattern'}},
          'models', 'item', 'plata_mark_banner_pattern.json')
    write({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/plata_mark_banner_pattern'}}, 'items', 'plata_mark_banner_pattern.json')


if __name__ == '__main__':
    husk()
    banner()
    print('husk: 8 models, 4 textures, icon; banner: banner + shield + loom item')
