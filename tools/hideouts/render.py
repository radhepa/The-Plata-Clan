"""Isometric previews of hideout templates (and of whole hideouts dumped from the game), drawn
with vanilla's own block textures. Rough shapes for slabs, stairs, posts, lanterns and the like;
good enough to judge a layout and a palette before building.

  from render import render
  render(blocks, 'out/den.png', cut_y=4)     # blocks: {(x, y, z): 'name[props]' or (name, props)}
"""
import os
from functools import lru_cache

import numpy as np
from PIL import Image, ImageDraw

from kit import CACHE, ROOT, parse

TEX = os.path.join(CACHE, 'textures', 'block')
MOD_TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'plataclan', 'textures', 'block')

TINTS = {'grass_block_top': (124, 189, 107), 'short_grass': (124, 189, 107), 'tall_grass_top': (124, 189, 107),
         'tall_grass_bottom': (124, 189, 107), 'fern': (124, 189, 107), 'oak_leaves': (89, 174, 48),
         'spruce_leaves': (97, 153, 97), 'birch_leaves': (128, 167, 85), 'vine': (89, 174, 48),
         'acacia_leaves': (174, 164, 42), 'jungle_leaves': (48, 187, 11), 'dark_oak_leaves': (89, 174, 48)}
FLAT = {'chest': (160, 110, 45), 'trapped_chest': (160, 110, 45), 'water': (60, 90, 200), 'lava': (220, 110, 20),
        'decorated_pot': (150, 85, 60), 'lookout': None}
WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak', 'bamboo',
         'crimson', 'warped', 'poplar']


@lru_cache(maxsize=None)
def tex(name):
    for base in (MOD_TEX, TEX):
        path = os.path.join(base, name + '.png')
        if os.path.exists(path):
            im = Image.open(path).convert('RGBA')
            if im.height > im.width: im = im.crop((0, 0, im.width, im.width))  # first animation frame
            if im.width != 16: im = im.resize((16, 16), Image.NEAREST)
            if name in TINTS:
                a = np.asarray(im).astype(np.float32)
                a[..., :3] *= np.array(TINTS[name]) / 255.0
                im = Image.fromarray(a.clip(0, 255).astype(np.uint8))
            return im
    return None


def solid(color):
    im = Image.new('RGBA', (16, 16), tuple(color) + (255,))
    return im


def base_of(n):
    """Texture base for derived blocks (stairs, slabs, walls, fences...)."""
    for suffix in ('_stairs', '_slab', '_wall', '_fence_gate', '_fence', '_pressure_plate', '_button'):
        if n.endswith(suffix):
            b = n[:-len(suffix)]
            for cand in (b, b + 's', b + '_planks', b.replace('brick', 'bricks'), b.replace('tile', 'tiles')):
                if tex(cand) is not None or tex(cand + '_side') is not None: return cand
            return b
    if n.startswith('infested_'): return n[len('infested_'):]
    if n.endswith('_carpet'): return n[:-len('_carpet')] + '_wool'
    if n.startswith('stripped_') and n.endswith('_wood'): return n[:-5] + '_log'
    if n.endswith('_wood'): return n[:-5] + '_log'
    return n


def textures(n, props):
    """(top, south, east) textures for a block."""
    special = {
        'grass_block': ('grass_block_top', 'grass_block_side', 'grass_block_side'),
        'dirt_path': ('dirt_path_top', 'dirt_path_side', 'dirt_path_side'),
        'podzol': ('podzol_top', 'podzol_side', 'podzol_side'),
        'barrel': ('barrel_top', 'barrel_side', 'barrel_side'),
        'hay_block': ('hay_block_top', 'hay_block_side', 'hay_block_side'),
        'smooth_stone_slab': ('smooth_stone', 'smooth_stone_slab_side', 'smooth_stone_slab_side'),
        'crafting_table': ('crafting_table_top', 'crafting_table_front', 'crafting_table_side'),
        'smithing_table': ('smithing_table_top', 'smithing_table_front', 'smithing_table_side'),
        'blast_furnace': ('blast_furnace_top', 'blast_furnace_front', 'blast_furnace_side'),
        'furnace': ('furnace_top', 'furnace_front', 'furnace_side'),
        'target': ('target_top', 'target_side', 'target_side'),
        'anvil': ('anvil_top', 'anvil', 'anvil'),
        'cauldron': ('cauldron_top', 'cauldron_side', 'cauldron_side'),
        'bookshelf': ('oak_planks', 'bookshelf', 'bookshelf'),
        'chiseled_bookshelf': ('chiseled_bookshelf_top', 'chiseled_bookshelf_empty', 'chiseled_bookshelf_side'),
        'deepslate': ('deepslate_top', 'deepslate', 'deepslate'),
        'campfire': ('campfire_log', 'campfire_log', 'campfire_log'),
        'snow': ('snow', 'snow', 'snow'),
        'grindstone': ('grindstone_round', 'grindstone_side', 'grindstone_pivot'),
        'loom': ('loom_top', 'loom_front', 'loom_side'),
        'fletching_table': ('fletching_table_top', 'fletching_table_front', 'fletching_table_side'),
        'composter': ('composter_top', 'composter_side', 'composter_side'),
        'tnt': ('tnt_top', 'tnt_side', 'tnt_side'),
        'quartz_block': ('quartz_block_top', 'quartz_block_side', 'quartz_block_side'),
        'smooth_sandstone': ('sandstone_top', 'sandstone_top', 'sandstone_top'),
        'smooth_red_sandstone': ('red_sandstone_top', 'red_sandstone_top', 'red_sandstone_top'),
        'cut_sandstone': ('sandstone_top', 'cut_sandstone', 'cut_sandstone'),
        'cut_red_sandstone': ('red_sandstone_top', 'cut_red_sandstone', 'cut_red_sandstone'),
        'chiseled_sandstone': ('sandstone_top', 'chiseled_sandstone', 'chiseled_sandstone'),
        'chiseled_red_sandstone': ('red_sandstone_top', 'chiseled_red_sandstone', 'chiseled_red_sandstone'),
        'mud_brick': ('mud_bricks', 'mud_bricks', 'mud_bricks'),
        'smooth_quartz': ('quartz_block_bottom', 'quartz_block_bottom', 'quartz_block_bottom'),
        'silver_husk': ('silver_husk_top', 'silver_husk_side', 'silver_husk_side'),
        'spawner': ('spawner', 'spawner', 'spawner'),
        'lantern': ('lantern', 'lantern', 'lantern'),
        'soul_lantern': ('soul_lantern', 'soul_lantern', 'soul_lantern'),
    }
    b = n if n in special else base_of(n)
    if b in special:
        names = special[b]
    elif b.endswith('_log') or b.endswith('_stem'):
        names = (b + '_top', b, b)
        axis = props.get('axis', 'y')
        if axis == 'x': names = (b, b, b + '_top')
        if axis == 'z': names = (b, b + '_top', b)
    elif b in ('sandstone', 'red_sandstone'):
        names = (b + '_top', b, b)
    elif tex(b) is not None:
        names = (b, b, b)
    elif tex(b + '_side') is not None:
        names = (b + '_top' if tex(b + '_top') is not None else b + '_side', b + '_side', b + '_side')
    elif b.endswith('_bricks') or b.endswith('_tiles'):
        names = (b, b, b)
    else:
        names = ('missing', 'missing', 'missing')
    out = []
    for name in names:
        t = tex(name)
        out.append(t if t is not None else solid((230, 0, 230)))
    return out


P = 1 / 16


def shape(n, props):
    """Boxes (x0, y0, z0, x1, y1, z1) in block units, or a kind string for billboards."""
    if n in ('air', 'cave_air', 'void_air', 'structure_void', 'jigsaw', 'barrier', 'light', 'lookout'): return []
    if n.endswith('_slab'):
        t = props.get('type', 'bottom')
        return [(0, 0, 0, 1, 1, 1)] if t == 'double' else [(0, .5, 0, 1, 1, 1)] if t == 'top' else [(0, 0, 0, 1, .5, 1)]
    if n.endswith('_stairs'):
        top = props.get('half', 'bottom') == 'top'
        f = props.get('facing', 'north')
        back = {'north': (0, 0, 1, .5), 'south': (0, .5, 1, 1), 'west': (0, 0, .5, 1), 'east': (.5, 0, 1, 1)}[f]
        x0, z0, x1, z1 = back
        if top: return [(0, .5, 0, 1, 1, 1), (x0, 0, z0, x1, .5, z1)]
        return [(0, 0, 0, 1, .5, 1), (x0, .5, z0, x1, 1, z1)]
    if n.endswith('_carpet') or n == 'moss_carpet': return [(0, 0, 0, 1, P, 1)]
    if n == 'snow': return [(0, 0, 0, 1, int(props.get('layers', 1)) / 8, 1)]
    if n.endswith('_trapdoor'):
        if props.get('open') == 'true':
            f = props.get('facing', 'north')
            return [{'north': (0, 0, 13 * P, 1, 1, 1), 'south': (0, 0, 0, 1, 1, 3 * P),
                     'west': (13 * P, 0, 0, 1, 1, 1), 'east': (0, 0, 0, 3 * P, 1, 1)}[f]]
        return [(0, 13 * P, 0, 1, 1, 1)] if props.get('half') == 'top' else [(0, 0, 0, 1, 3 * P, 1)]
    if n == 'ladder':
        f = props.get('facing', 'north')
        return [{'south': (0, 0, 0, 1, 1, P), 'north': (0, 0, 15 * P, 1, 1, 1),
                 'east': (0, 0, 0, P, 1, 1), 'west': (15 * P, 0, 0, 1, 1, 1)}[f]]
    if n.endswith('_fence') or n.endswith('_wall') or n in ('iron_bars',) or n.endswith('glass_pane') or n.endswith('copper_bars'):
        thick = 2 * P if n.endswith('_fence') else 1.5 * P if 'bars' in n or 'pane' in n else 4 * P
        top = 1 if not n.endswith('_wall') else 14 * P
        boxes = [(.5 - thick, 0, .5 - thick, .5 + thick, top, .5 + thick)]
        arm = P * (1.2 if n.endswith('_fence') else 3)
        lo = 6 * P if n.endswith('_fence') else 0
        for d, box in (('north', (.5 - arm, lo, 0, .5 + arm, top - P, .5)), ('south', (.5 - arm, lo, .5, .5 + arm, top - P, 1)),
                       ('west', (0, lo, .5 - arm, .5, top - P, .5 + arm)), ('east', (.5, lo, .5 - arm, 1, top - P, .5 + arm))):
            if props.get(d) in ('true', 'low', 'tall'): boxes.append(box)
        return boxes
    if n.endswith('_chain'):
        a = props.get('axis', 'y')
        return [{'y': (7 * P, 0, 7 * P, 9 * P, 1, 9 * P), 'x': (0, 7 * P, 7 * P, 1, 9 * P, 9 * P), 'z': (7 * P, 7 * P, 0, 9 * P, 9 * P, 1)}[a]]
    if n.endswith('lantern') and n not in ('sea_lantern', 'jack_o_lantern'):
        y = P if props.get('hanging') == 'true' else 0
        return [(5 * P, y, 5 * P, 11 * P, y + 7 * P, 11 * P), (6 * P, y + 7 * P, 6 * P, 10 * P, y + 9 * P, 10 * P)]
    if n.endswith('candle'): return [(7 * P, 0, 7 * P, 9 * P, 6 * P, 9 * P)]
    if n.endswith('torch'): return [(7 * P, 0, 7 * P, 9 * P, 10 * P, 9 * P)]
    if n.endswith('wall_banner'):
        f = props.get('facing', 'north')
        return [{'south': (P, -14 * P, 0, 15 * P, 1, 2 * P), 'north': (P, -14 * P, 14 * P, 15 * P, 1, 1),
                 'east': (0, -14 * P, P, 2 * P, 1, 15 * P), 'west': (14 * P, -14 * P, P, 1, 1, 15 * P)}[f]]
    if n == 'campfire': return [(0, 0, 0, 1, 7 * P, 1)]
    if n in ('chest', 'trapped_chest'): return [(P, 0, P, 15 * P, 14 * P, 15 * P)]
    if n == 'decorated_pot': return [(P, 0, P, 15 * P, 1, 15 * P)]
    if n == 'flower_pot' or n.startswith('potted_'): return [(5 * P, 0, 5 * P, 11 * P, 6 * P, 11 * P)]
    if n == 'anvil': return [(2 * P, 0, 2 * P, 14 * P, 4 * P, 14 * P), (4 * P, 4 * P, 6 * P, 12 * P, 10 * P, 10 * P), (P, 10 * P, 3 * P, 15 * P, 1, 13 * P)]
    if n == 'grindstone': return [(4 * P, 0, 4 * P, 12 * P, 12 * P, 12 * P)]
    if n == 'cauldron': return [(0, 3 * P, 0, 1, 1, 1)]
    if n == 'silver_husk':
        lower = props.get('half') == 'lower'
        split = props.get('split') == 'true'
        w = 3 * P if props.get('kind') == 'elder' else 4 * P
        top = 1 if lower else 12 * P
        boxes = [(w, 0, w, 1 - w, top, 1 - w)]
        if split: boxes = [(w, 0, w, 1 - w, top, .5)]
        return boxes
    if n in ('cobweb', 'dead_bush', 'short_grass', 'fern', 'tall_grass', 'large_fern', 'sweet_berry_bush', 'poppy',
             'dandelion', 'azure_bluet', 'oxeye_daisy', 'cornflower', 'brown_mushroom', 'red_mushroom', 'short_dry_grass',
             'tall_dry_grass', 'bush', 'firefly_bush', 'pointed_dripstone', 'hanging_roots', 'glow_lichen', 'vine') \
            or n.endswith('_sapling'):
        return 'cross'
    return [(0, 0, 0, 1, 1, 1)]


SHADE = {'top': 1.0, 'south': .80, 'east': .64}


class Iso:
    def __init__(self, s=8):
        self.s = s

    def project(self, x, y, z):
        s = self.s
        return ((x - z) * s, (x + z) * s / 2 - y * s)

    def face(self, canvas, origin, poly_u, poly_v, texture, region, shade):
        """Draw `texture` cropped to `region` (u0, v0, u1, v1 in 0..16) onto the parallelogram origin + u*U + v*V."""
        u0, v0, u1, v1 = region
        crop = texture.crop((int(round(u0)), int(round(v0)), max(int(round(u0)) + 1, int(round(u1))), max(int(round(v0)) + 1, int(round(v1)))))
        tw, th = crop.size
        ox, oy = origin
        (ux, uy), (vx, vy) = poly_u, poly_v
        det = ux * vy - vx * uy
        if abs(det) < 1e-9: return
        a11, a12, a21, a22 = vy / det, -vx / det, -uy / det, ux / det
        coeffs = (tw * a11, tw * a12, -tw * (a11 * ox + a12 * oy), th * a21, th * a22, -th * (a21 * ox + a22 * oy))
        img = crop.transform(canvas.size, Image.AFFINE, coeffs, resample=Image.NEAREST)
        if shade != 1:
            arr = np.asarray(img).astype(np.float32)
            arr[..., :3] *= shade
            img = Image.fromarray(arr.clip(0, 255).astype(np.uint8))
        canvas.alpha_composite(img)

    @lru_cache(maxsize=None)
    def sprite(self, key):
        """(image, offset) for one block state, drawn at the block's origin."""
        name, props = parse(key)
        n = name.split(':')[1]
        props = dict(props)
        s = self.s
        size = (4 * s, 6 * s)
        off = (-2 * s, -4 * s)  # sprite pixel (0, 0) relative to the projected block origin
        canvas = Image.new('RGBA', size, (0, 0, 0, 0))
        boxes = shape(n, props)
        if not boxes: return canvas, off
        P2 = lambda x, y, z: (self.project(x, y, z)[0] - off[0], self.project(x, y, z)[1] - off[1])
        if n in FLAT and FLAT[n] is None: return canvas, off
        top_t, south_t, east_t = textures(n, props) if n not in FLAT else [solid(FLAT[n])] * 3
        if n.endswith('wall_banner'):
            color = n[:-len('_wall_banner')]
            wool = tex(color + '_wool') or solid((200, 200, 200))
            top_t = south_t = east_t = wool
        if boxes == 'cross':
            t = top_t
            w = int(2 * s)
            spr = t.resize((w, w), Image.NEAREST)
            cx, cy = P2(.5, 0, .5)
            canvas.alpha_composite(spr, (int(cx - w / 2), int(cy - w)))
            return canvas, off
        for (x0, y0, z0, x1, y1, z1) in sorted(boxes, key=lambda b: (b[0] + b[2], b[1])):
            # top face (y = y1): u along +x, v along +z
            self.face(canvas, P2(x0, y1, z0), np.subtract(P2(x1, y1, z0), P2(x0, y1, z0)), np.subtract(P2(x0, y1, z1), P2(x0, y1, z0)),
                      top_t, (x0 * 16, z0 * 16, x1 * 16, z1 * 16), SHADE['top'])
            # south face (z = z1): u along +x, v down (-y)
            self.face(canvas, P2(x0, y1, z1), np.subtract(P2(x1, y1, z1), P2(x0, y1, z1)), np.subtract(P2(x0, y0, z1), P2(x0, y1, z1)),
                      south_t, (x0 * 16, (1 - min(y1, 1)) * 16, x1 * 16, (1 - max(y0, 0)) * 16), SHADE['south'])
            # east face (x = x1): u along -z, v down
            self.face(canvas, P2(x1, y1, z1), np.subtract(P2(x1, y1, z0), P2(x1, y1, z1)), np.subtract(P2(x1, y0, z1), P2(x1, y1, z1)),
                      east_t, ((1 - z1) * 16, (1 - min(y1, 1)) * 16, (1 - z0) * 16, (1 - max(y0, 0)) * 16), SHADE['east'])
        return canvas, off


def _key(b):
    if isinstance(b, str): return b
    name, props = b
    return name + ('[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']' if props else '')


OPAQUE_EXCEPT = ('slab', 'stairs', 'carpet', 'fence', 'wall', 'bars', 'pane', 'chain', 'lantern', 'candle', 'torch',
                 'banner', 'ladder', 'trapdoor', 'door', 'campfire', 'chest', 'pot', 'anvil', 'grindstone', 'cauldron',
                 'husk', 'glass', 'leaves', 'cobweb', 'spawner', 'snow', 'water', 'air', 'jigsaw', 'lookout', 'bush', 'grass',
                 'fern', 'flower', 'sapling', 'button', 'plate', 'rod', 'lichen', 'vine', 'roots', 'dripstone')


def opaque(key):
    n = parse(key)[0].split(':')[1]
    return not any(w in n for w in OPAQUE_EXCEPT)


def render(blocks, out, s=8, cut_y=None, hide=(), background=(34, 38, 44), title=None, jigsaw_final=None, crop=True,
           open_near=None):
    """blocks: {(x, y, z): spec}. cut_y hides everything above that y (a dollhouse cut); open_near=y hides the
    two walls nearest the camera (highest x, highest z) above that y."""
    iso = Iso(s)
    cells = {}
    mx = max(p[0] for p in blocks) if blocks else 0
    mz = max(p[2] for p in blocks) if blocks else 0
    for (x, y, z), b in blocks.items():
        if cut_y is not None and y > cut_y: continue
        if open_near is not None and y > open_near and (x == mx or z == mz): continue
        key = _key(b)
        if jigsaw_final and key.startswith('minecraft:jigsaw') and (x, y, z) in jigsaw_final:
            key = jigsaw_final[(x, y, z)]
        n = parse(key)[0]
        if n.split(':')[1] in hide: continue
        cells[(x, y, z)] = key
    if not cells:
        Image.new('RGB', (64, 64), background).save(out)
        return
    xs = [p[0] for p in cells]; ys = [p[1] for p in cells]; zs = [p[2] for p in cells]
    pts = [iso.project(x, y, z) for x in (min(xs), max(xs) + 1) for y in (min(ys) - 1, max(ys) + 2) for z in (min(zs), max(zs) + 1)]
    minx = min(p[0] for p in pts) - 4 * s; maxx = max(p[0] for p in pts) + 4 * s
    miny = min(p[1] for p in pts) - 4 * s; maxy = max(p[1] for p in pts) + 4 * s
    W, H = int(maxx - minx), int(maxy - miny)
    canvas = Image.new('RGBA', (W, H), background + (255,))
    for (x, y, z) in sorted(cells, key=lambda p: (p[0] + p[2], p[1], p[0])):
        key = cells[(x, y, z)]
        # skip blocks buried behind full cubes on every visible side
        if all(cells.get(n) is not None and opaque(cells[n]) for n in ((x, y + 1, z), (x, y, z + 1), (x + 1, y, z))): continue
        img, (ox, oy) = iso.sprite(key)
        px, py = iso.project(x, y, z)
        canvas.alpha_composite(img, (int(round(px + ox - minx)), int(round(py + oy - miny))))
    if crop:
        bbox = Image.eval(canvas.convert('RGB'), lambda v: v).getbbox()
        arr = np.asarray(canvas)[..., :3].astype(int)
        mask = np.abs(arr - np.array(background)).sum(axis=2) > 0
        ys_, xs_ = np.nonzero(mask)
        if len(xs_):
            pad = 2 * s
            canvas = canvas.crop((max(0, xs_.min() - pad), max(0, ys_.min() - pad), min(W, xs_.max() + pad), min(H, ys_.max() + pad)))
    if title:
        d = ImageDraw.Draw(canvas)
        d.text((6, 4), title, fill=(230, 230, 230, 255))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    canvas.convert('RGB').save(out)


def render_template(t, out, **kw):
    finals = {}
    for pos, nbt in t.nbt.items():
        if nbt.get('id') == 'minecraft:jigsaw': finals[pos] = nbt['final_state']
    render({p: b for p, b in t.blocks.items()}, out, jigsaw_final=finals, **kw)


def plan(blocks, out, y=None, cell=8):
    """A straight-down plan of the topmost block in each column (or of layer y), for checking floor inlays."""
    xs = [p[0] for p in blocks]; zs = [p[2] for p in blocks]
    img = Image.new('RGB', ((max(xs) - min(xs) + 1) * cell, (max(zs) - min(zs) + 1) * cell), (20, 20, 24))
    top = {}
    for (x, yy, z), b in blocks.items():
        if y is not None and yy != y: continue
        key = _key(b)
        if parse(key)[0].split(':')[1] in ('air', 'cave_air', 'jigsaw'): continue
        if (x, z) not in top or yy > top[(x, z)][0]: top[(x, z)] = (yy, key)
    for (x, z), (_, key) in top.items():
        n, props = parse(key)
        t = textures(n.split(':')[1], props)[0]
        img.paste(t.resize((cell, cell), Image.NEAREST).convert('RGB'), ((x - min(xs)) * cell, (z - min(zs)) * cell))
    img.save(out)
