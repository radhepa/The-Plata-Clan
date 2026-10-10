"""Every piece of the Plata Clan's hideouts.

Underground everything shares the clan's own stonework: stronghold-style stone bricks they learned
to love in the walls where it all began, kept alive with silverfish (some bricks are infested),
ribbed with tuff like the segments of a silverfish's back, floored in polished andesite and picked
out in silver (calcite, polished diorite, iron). Their mark, a silverfish seen from above, is set in
the floors and hung on banners.

Up top nothing looks like the clan at all: a traveler's camp over a Scale Den, a ruined toll-house
over a Silver Warren, in six regional styles.

Conventions: x runs east, z south, y up; a piece's floor is y = 0 and doorways sit at y = 1.
Jigsaw names (all in the plataclan namespace):
  passage      3-wide tunnel mouth (rooms, tunnels, the hall)
  vault_gate   the hall's south door, which only the vault (the Silver Mother's Hollow) answers
  stair_foot   foot of the Warren's spiral stair (attaches to the hall)
  den_door     1-wide crawl from the den's ladder shaft into the den
  descent      surface hatch down to a shaft (start jigsaw of every hideout)
"""
import random

from kit import Template

# -- materials -----------------------------------------------------------------------------------


def pick(rng, table):
    total = sum(w for _, w in table)
    r = rng.random() * total
    for item, w in table:
        r -= w
        if r <= 0: return item
    return table[-1][0]


class Clan:
    """The clan's underground stonework, with a little seeded wear and a few infested bricks."""

    def __init__(self, rng, infested=1.0):
        self.rng = rng
        self.infested = infested

    def wall(self, *_):
        return pick(self.rng, [('stone_bricks', 55), ('cracked_stone_bricks', 17), ('mossy_stone_bricks', 9),
                               ('infested_stone_bricks', 8 * self.infested), ('infested_cracked_stone_bricks', 3 * self.infested),
                               ('tuff_bricks', 6), ('polished_tuff', 2)])

    def ceiling(self, *_):
        return pick(self.rng, [('stone_bricks', 60), ('tuff_bricks', 22), ('cracked_stone_bricks', 12), ('infested_stone_bricks', 4 * self.infested)])

    def floor(self, *_):
        # polished and brick stone only: raw andesite and tuff would be eaten by ore veins after the build
        return pick(self.rng, [('polished_andesite', 70), ('polished_tuff', 14), ('tuff_bricks', 8), ('cobblestone', 8)])

    def rib(self, *_):
        return pick(self.rng, [('tuff_bricks', 60), ('polished_tuff', 40)])

    def plinth(self, *_):
        return pick(self.rng, [('polished_tuff', 70), ('tuff_bricks', 30)])

    def rubble(self, *_):
        return pick(self.rng, [('cobblestone', 35), ('mossy_cobblestone', 15), ('cracked_stone_bricks', 25),
                               ('infested_cobblestone', 15 * self.infested), ('cobbled_deepslate', 10)])


# The clan's mark, a silverfish seen from above, in blocks: the 32-pixel mark of the mod's icon at
# about two-thirds scale. Row 0 is the feeler tips; the head is H, light and dark body segments
# A and B (their right-hand cells shaded S), feelers and tail strands F.
MARK = [
    'F.......F',
    '.F.....F.',
    '..F...F..',
    '...HHH...',
    '..AAAAS..',
    '.AAAAAAS.',
    '.BBBBBBS.',
    'AAAAAAAAS',
    'BBBBBBBBS',
    '.AAAAAAS.',
    '.BBBBBBS.',
    '..AAAAS..',
    '..BBBBS..',
    '...AAS...',
    '...BBS...',
    '....A....',
    '...FFF...',
    '..F.F.F..',
    '.F..F..F.',
]
# Shorter, for walls and the vault floor.
MARK_WALL = [
    'F.......F',
    '.F.....F.',
    '..FHHHF..',
    '..AAAAS..',
    '.BBBBBBS.',
    'AAAAAAAAS',
    '.BBBBBBS.',
    '..AAAAS..',
    '...BBS...',
    '....A....',
    '..F.F.F..',
    '.F..F..F.',
]
MARK_TINY = [
    'F...F',
    '.HHH.',
    'AAAAS',
    'BBBBS',
    '.AAS.',
    '..B..',
    '.F.F.',
]
SILVER = {'F': 'calcite', 'H': 'polished_diorite', 'A': 'calcite', 'B': 'polished_diorite', 'S': 'smooth_stone'}


def floor_mark(t, x0, y, z0, flip=False, mark=MARK, colors=SILVER, bg=None):
    """Inlay the mark in the floor at layer y, top-left corner (x0, z0), head toward -z (or +z if flipped)."""
    rows = mark[::-1] if flip else mark
    for dz, row in enumerate(rows):
        for dx, c in enumerate(row):
            b = colors.get(c)
            if b is None:
                if bg: t.set(x0 + dx, y, z0 + dz, bg)
                continue
            t.set(x0 + dx, y, z0 + dz, b)


def wall_mark(t, x0, y0, z, mark=MARK_WALL, colors=SILVER, bg='deepslate_tiles'):
    """The mark upright on a north/south wall, head up, its bottom row at y0 (x runs east)."""
    h = len(mark)
    for dy, row in enumerate(mark):
        for dx, c in enumerate(row):
            t.set(x0 + dx, y0 + h - 1 - dy, z, colors.get(c, bg))


def hanging_lantern(t, x, y, z, chain=1):
    """A lantern on `chain` links hanging from the ceiling block above (x, y + chain, z)."""
    for i in range(chain):
        t.set(x, y + 1 + i, z, 'iron_chain[axis=y]')
    t.set(x, y, z, 'lantern[hanging=true]')


# -- tunnels -------------------------------------------------------------------------------------

def tunnel_slice(t, z, C, rib, y0=0, ceil=None, light=False):
    """One cross-section of a 3-wide ribbed tunnel at z (x 0..4), floor at y0."""
    ceil = y0 + 5 if ceil is None else ceil
    for x in range(5):
        t.set(x, y0, z, (C.rib() if rib else C.floor()) if x in (1, 2, 3) else C.plinth())
    if rib:
        t.set(2, y0, z, 'chiseled_tuff_bricks')
    for y in range(y0 + 1, ceil):
        for x in (0, 4):
            t.set(x, y, z, C.rib() if rib else (C.plinth() if y == y0 + 1 else C.wall()))
        for x in (1, 2, 3):
            t.set(x, y, z, 'air')
    # the rounded crown: stair corners under a ceiling
    t.set(1, ceil - 1, z, ('tuff_brick_stairs' if rib else 'stone_brick_stairs') + '[facing=west,half=top]')
    t.set(3, ceil - 1, z, ('tuff_brick_stairs' if rib else 'stone_brick_stairs') + '[facing=east,half=top]')
    for x in range(5):
        t.set(x, ceil, z, 'chiseled_tuff_bricks' if rib and x == 2 else (C.rib() if rib else C.ceiling()))
    if light:
        hanging_lantern(t, 2, ceil - 2, z, chain=1)


def passage(t, x, y, z, facing, pool='warren/tunnels'):
    t.jigsaw(x, y, z, f'{facing}_up', 'passage', 'passage', pool)


def tunnel_straight(name, length, C, pool='warren/branches'):
    t = Template(name, (5, 6, length))
    C.rng = t.rng
    for z in range(length):
        tunnel_slice(t, z, C, rib=(z % 3 == 1), light=(z == length // 2 and length >= 7))
    # a bit of life: candle stubs and a cobweb
    t.set(1, 1, 2, 'white_candle[candles=2,lit=false]')
    if length > 8:
        t.set(3, 3, length - 3, 'cobweb')
        t.set(3, 1, length - 4, 'white_candle[candles=1,lit=false]')
    passage(t, 2, 1, 0, 'north', pool)
    passage(t, 2, 1, length - 1, 'south', pool)
    return t


def corner_shell(t, C, exits):
    """A 5x6x5 junction: walls all round, openings on the given sides, crown stairs on closed sides."""
    t.fill((0, 0, 0), (4, 0, 4), lambda *_: C.floor())
    t.set(2, 0, 2, 'chiseled_tuff_bricks')
    t.fill((0, 5, 0), (4, 5, 4), lambda *_: C.ceiling())
    t.set(2, 5, 2, 'chiseled_tuff_bricks')
    for y in range(1, 5):
        for i in range(5):
            for (x, z) in ((i, 0), (i, 4), (0, i), (4, i)):
                t.set(x, y, z, C.rib() if i in (0, 4) else (C.plinth() if y == 1 else C.wall()))
    t.fill((1, 1, 1), (3, 4, 3), 'air')
    openings = {'north': [(x, 0) for x in (1, 2, 3)], 'south': [(x, 4) for x in (1, 2, 3)],
                'west': [(0, z) for z in (1, 2, 3)], 'east': [(4, z) for z in (1, 2, 3)]}
    for side in exits:
        for (x, z) in openings[side]:
            for y in (1, 2, 3): t.set(x, y, z, 'air')
    # crowns along closed walls
    if 'north' not in exits:
        for x in (1, 2, 3): t.set(x, 4, 1, 'tuff_brick_stairs[facing=north,half=top]')
    if 'south' not in exits:
        for x in (1, 2, 3): t.set(x, 4, 3, 'tuff_brick_stairs[facing=south,half=top]')
    if 'west' not in exits:
        for z in (1, 2, 3): t.set(1, 4, z, 'tuff_brick_stairs[facing=west,half=top]')
    if 'east' not in exits:
        for z in (1, 2, 3): t.set(3, 4, z, 'tuff_brick_stairs[facing=east,half=top]')
    hanging_lantern(t, 2, 3, 2)
    jig = {'north': (2, 1, 0), 'south': (2, 1, 4), 'west': (0, 1, 2), 'east': (4, 1, 2)}
    for side in exits:
        passage(t, *jig[side], side, 'warren/branches')


def tunnel_bend(C):
    t = Template('warren/tunnel_bend', (5, 6, 5))
    C.rng = t.rng
    corner_shell(t, C, ['north', 'east'])
    t.set(1, 1, 3, 'white_candle[candles=3,lit=false]')
    return t


def tunnel_tee(C):
    t = Template('warren/tunnel_tee', (5, 6, 5))
    C.rng = t.rng
    corner_shell(t, C, ['north', 'east', 'west'])
    t.set(2, 1, 3, 'decorated_pot[facing=north]')
    return t


def tunnel_stairs(C):
    """Down four blocks over nine: upper mouth north at y = 5, lower mouth south at y = 1."""
    t = Template('warren/tunnel_stairs', (5, 10, 9))
    C.rng = t.rng
    floor_at = {0: 4, 1: 4, 2: 3, 3: 2, 4: 1, 5: 0, 6: 0, 7: 0, 8: 0}
    for z in range(9):
        f = floor_at[z]
        ceil = f + 5
        tunnel_slice(t, z, C, rib=(z % 3 == 1), y0=f, ceil=ceil, light=(z == 6))
        if z in (2, 3, 4):
            for x in (1, 2, 3):
                t.set(x, f, z, 'stone_brick_stairs[facing=north,half=bottom]')
        for y in range(0, f):  # solid under the steps and the landing
            for x in range(5): t.set(x, y, z, C.plinth() if y == 0 else C.wall())
        for y in range(ceil + 1, 10):
            for x in range(5): t.set(x, y, z, C.ceiling())
    passage(t, 2, 5, 0, 'north', 'warren/branches')
    passage(t, 2, 1, 8, 'south', 'warren/branches')
    return t


def tunnel_cap(C):
    """Where a tunnel was never finished: a collapse of rubble (the fallback for every open mouth)."""
    t = Template('warren/tunnel_cap', (5, 6, 2))
    C.rng = t.rng
    tunnel_slice(t, 0, C, rib=False)
    for y in range(0, 6):
        for x in range(5): t.set(x, y, 1, C.rubble())
    t.set(1, 1, 0, 'cobblestone_slab[type=bottom]')
    t.set(3, 1, 0, 'cobblestone_stairs[facing=south,half=bottom]')
    t.set(2, 3, 0, 'cobweb')
    t.jigsaw(2, 1, 0, 'north_up', 'passage', 'passage', 'minecraft:empty')
    return t


# -- rooms ---------------------------------------------------------------------------------------

def shell(t, C, lo, hi, ribs=()):
    """Floor, walls and ceiling of the box lo..hi with an air interior. Ribs: x (or z) planes in tuff."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    t.fill((x0, y0, z0), (x1, y0, z1), lambda *_: C.floor())
    t.fill((x0, y1, z0), (x1, y1, z1), lambda *_: C.ceiling())
    for y in range(y0 + 1, y1):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                t.set(x, y, z, C.plinth() if y == y0 + 1 else C.wall())
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                t.set(x, y, z, C.plinth() if y == y0 + 1 else C.wall())
    t.fill((x0 + 1, y0 + 1, z0 + 1), (x1 - 1, y1 - 1, z1 - 1), 'air')
    # tuff pillars in the corners
    for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        for y in range(y0 + 1, y1):
            t.set(x, y, z, C.rib())


def doorway(t, side, c, y, width=3, height=3, pool='warren/tunnels', name='passage', **jig):
    """Open a doorway in the outer wall on `side`, centred on c (x for north/south, z for east/west), floor at y - 1."""
    sx, sy, sz = t.size
    half = width // 2
    for d in range(-half, half + 1):
        for h in range(height):
            if side == 'north': t.set(c + d, y + h, 0, 'air')
            elif side == 'south': t.set(c + d, y + h, sz - 1, 'air')
            elif side == 'west': t.set(0, y + h, c + d, 'air')
            else: t.set(sx - 1, y + h, c + d, 'air')
    pos = {'north': (c, y, 0), 'south': (c, y, sz - 1), 'west': (0, y, c), 'east': (sx - 1, y, c)}[side]
    if name == 'passage':
        t.jigsaw(*pos, f'{side}_up', 'passage', 'passage', pool, **jig)
    else:
        t.jigsaw(*pos, f'{side}_up', name, jig.pop('target', 'minecraft:empty'), pool, **jig)
    # a lintel of crown stairs over 3-wide doors
    if width == 3 and height == 3:
        top = y + height
        if side in ('north', 'south'):
            z = 0 if side == 'north' else sz - 1
            t.set(c - 1, top, z, 'tuff_brick_stairs[facing=west,half=top]')
            t.set(c, top, z, 'chiseled_tuff_bricks')
            t.set(c + 1, top, z, 'tuff_brick_stairs[facing=east,half=top]')
        else:
            x = 0 if side == 'west' else sx - 1
            t.set(x, top, c - 1, 'tuff_brick_stairs[facing=north,half=top]')
            t.set(x, top, c, 'chiseled_tuff_bricks')
            t.set(x, top, c + 1, 'tuff_brick_stairs[facing=south,half=top]')


def hall(C):
    """The Molting Hall: the Warren's heart, where the spiral stair comes down. The mark runs the length of the floor."""
    W, L, H = 19, 23, 11
    t = Template('warren/hall', (W, H, L))
    C.rng = t.rng
    X, Z, Y = W - 1, L - 1, H - 1
    shell(t, C, (0, 0, 0), (X, Y, Z))
    # pilasters on the walls and tuff ribs across the vault of the ceiling
    for i in (5, 11, 17):
        for y in range(1, Y):
            t.set(0, y, i, C.rib()); t.set(X, y, i, C.rib())
        for x in range(1, X): t.set(x, Y, i, C.rib())
    for i in (5, 9, 13):
        for y in range(1, Y):
            t.set(i, y, 0, C.rib()); t.set(i, y, Z, C.rib())
    for x in range(1, X):
        t.set(x, Y - 1, 1, 'tuff_brick_stairs[facing=north,half=top]')
        t.set(x, Y - 1, Z - 1, 'tuff_brick_stairs[facing=south,half=top]')
    for z in range(1, Z):
        t.set(1, Y - 1, z, 'tuff_brick_stairs[facing=west,half=top]')
        t.set(X - 1, Y - 1, z, 'tuff_brick_stairs[facing=east,half=top]')
    # floor: andesite with a tuff border; the mark on a deepslate runner down the middle
    t.fill((1, 0, 1), (X - 1, 0, Z - 1), lambda *_: 'polished_andesite' if t.rng.random() < .8 else 'polished_tuff')
    for x in range(1, X):
        t.set(x, 0, 1, 'tuff_bricks'); t.set(x, 0, Z - 1, 'tuff_bricks')
    for z in range(1, Z):
        t.set(1, 0, z, 'tuff_bricks'); t.set(X - 1, 0, z, 'tuff_bricks')
    t.fill((4, 0, 1), (14, 0, Z - 1), 'polished_deepslate')
    t.fill((5, 0, 1), (13, 0, Z - 1), 'deepslate_tiles')
    floor_mark(t, 5, 0, 2)
    # four great pillars around the mark, candles at their feet
    for (x, z) in ((3, 6), (15, 6), (3, 16), (15, 16)):
        for y in range(1, Y - 1): t.set(x, y, z, 'chiseled_tuff_bricks' if y in (1, Y - 2) else 'polished_tuff')
        t.set(x, Y - 1, z, 'tuff_bricks')
        t.set(x, 1, z + (1 if z < 11 else -1), 'white_candle[candles=3,lit=true]')
    for (x, z) in ((9, 4), (9, 11), (9, 18), (4, 11), (14, 11)):
        hanging_lantern(t, x, Y - 4, z, chain=3)
    # the sleepers stand against the side walls between the pilasters, under the clan's banners
    for (x, f) in ((1, 'east'), (X - 1, 'west')):
        for z in (3, 8, 14, 19):
            t.husk(x, 1, z, f) if z in (8, 14) else t.set(x, 1, z, 'decorated_pot[facing=' + f + ']')
        t.banner(x, 6, 8, f); t.banner(x, 6, 14, f)
    doorway(t, 'north', 9, 1, pool='warren/tunnels')
    doorway(t, 'east', 11, 1, pool='warren/tunnels')
    doorway(t, 'west', 11, 1, pool='warren/tunnels')
    doorway(t, 'south', 9, 1, pool='warren/vault', name='vault_gate_hall', target='vault_gate', placement=10)
    return t


def gallery(C):
    """The Shell Gallery: where the clan sleeps, standing in their husks. Split husks are members out on the road."""
    t = Template('warren/gallery', (11, 7, 17))
    C.rng = t.rng
    shell(t, C, (0, 0, 0), (10, 6, 16))
    t.fill((1, 0, 1), (9, 0, 15), lambda *_: C.floor())
    for z in range(1, 16): t.set(5, 0, z, 'polished_tuff' if z % 2 else 'tuff_bricks')
    slots = [3, 6, 9, 12]
    for z in range(1, 16):
        for x in (1, 9):
            if z in (4, 5, 7, 8, 10, 11, 13, 14) or z <= 2 or z >= 15:
                pass
    for i, z in enumerate(slots):
        for (x, f) in ((1, 'east'), (9, 'west')):
            split = (i + (x == 9)) % 3 == 2
            if split:
                t.set(x, 1, z, f'plataclan:silver_husk[facing={f},half=lower,kind=sleeper,split=true]')
                t.set(x, 2, z, f'plataclan:silver_husk[facing={f},half=upper,kind=sleeper,split=true]')
            else:
                t.husk(x, 1, z, f)
        # tuff ribs between the berths
        for (x, d) in ((1, 1), (9, -1)):
            for zz in (z - 1, z + 1):
                if 1 <= zz <= 15: t.set(x, 3, zz, 'tuff_brick_stairs[facing=' + ('west' if x == 1 else 'east') + ',half=top]')
    for z in range(1, 16):
        t.set(1, 5, z, 'tuff_brick_stairs[facing=west,half=top]')
        t.set(9, 5, z, 'tuff_brick_stairs[facing=east,half=top]')
    for z in (4, 10):
        hanging_lantern(t, 5, 4, z)
    t.set(4, 1, 15, 'white_carpet'); t.set(6, 1, 15, 'white_carpet')
    t.chest(5, 1, 15, 'north', 'chests/warren_stores')
    t.set(2, 1, 15, 'barrel[facing=up]')
    t.banner(5, 4, 15, 'north')
    t.set(3, 1, 1, 'white_candle[candles=2,lit=true]')
    t.set(7, 1, 1, 'white_candle[candles=2,lit=true]')
    doorway(t, 'north', 5, 1)
    return t


def pit_room(C):
    """The Sparring Pit, laid out with its own floor two below the walkway."""
    t = Template('warren/pit', (13, 9, 13))
    C.rng = t.rng
    shell(t, C, (0, 0, 0), (12, 8, 12))
    t.fill((1, 0, 1), (11, 2, 11), lambda x, y, z: C.floor() if y == 2 else C.plinth())  # walkway at y = 2
    t.fill((3, 0, 3), (9, 0, 9), lambda *_: pick(t.rng, [('packed_mud', 30), ('coarse_dirt', 30), ('sand', 0), ('rooted_dirt', 20), ('mud_bricks', 20)]))
    t.fill((3, 1, 3), (9, 2, 9), 'air')
    for i in range(3, 10):
        for (x, z) in ((i, 3), (i, 9), (3, i), (9, i)):
            t.set(x, 0, z, 'polished_tuff')
    # a rim of slabs around the drop
    for i in range(2, 11):
        for (x, z) in ((i, 2), (i, 10), (2, i), (10, i)):
            t.set(x, 2, z, 'smooth_stone')
    for i in range(3, 10):
        t.set(i, 3, 2, 'iron_bars[east=true,west=true]') if i not in (5, 6, 7) else None
        t.set(i, 3, 10, 'iron_bars[east=true,west=true]') if i not in (5, 6, 7) else None
    # steps down into the pit on the north side
    t.set(6, 2, 3, 'air'); t.set(6, 1, 3, 'tuff_brick_stairs[facing=north,half=bottom]')
    t.set(6, 2, 2, 'tuff_brick_stairs[facing=north,half=bottom]')
    # practice targets: posts with target-block heads, and targets on the walls
    for (x, z) in ((4, 6), (8, 6)):
        t.set(x, 1, z, 'oak_fence'); t.set(x, 2, z, 'hay_block'); t.set(x, 3, z, 'target')
    for (x, z, f) in ((1, 6, 'east'), (11, 6, 'west')):
        t.set(x, 4, z, 'target'); t.set(x, 5, z, 'target')
    for (x, z) in ((6, 6),):
        hanging_lantern(t, x, 6, z, chain=1)
    for i in range(1, 12):
        t.set(i, 7, 1, 'tuff_brick_stairs[facing=north,half=top]')
        t.set(i, 7, 11, 'tuff_brick_stairs[facing=south,half=top]')
    t.husk(1, 3, 3, 'east')
    t.husk(11, 3, 9, 'west')
    t.set(1, 3, 9, 'barrel[facing=up]'); t.set(11, 3, 3, 'barrel[facing=up]')
    t.set(1, 3, 10, 'white_candle[candles=2,lit=true]')
    t.banner(6, 6, 11, 'north')
    hanging_lantern(t, 2, 6, 6); hanging_lantern(t, 10, 6, 6)
    doorway(t, 'north', 6, 3)
    doorway(t, 'south', 6, 3)
    return t


def forge(C):
    """The Scale Forge: where silver scales are beaten into plates and shards are sharpened."""
    t = Template('warren/forge', (11, 7, 11))
    C.rng = t.rng
    shell(t, C, (0, 0, 0), (10, 6, 10))
    t.fill((1, 0, 1), (9, 0, 9), lambda *_: pick(t.rng, [('polished_andesite', 50), ('smooth_stone', 30), ('cobblestone', 20)]))
    # the hearth: blast furnace in a deepslate chimney breast on the south wall
    for y in range(1, 6):
        for x in (4, 5, 6): t.set(x, y, 9, 'polished_deepslate' if y < 5 else 'deepslate_tiles')
    t.set(5, 1, 9, 'blast_furnace[facing=north,lit=true]')
    t.set(4, 1, 9, 'deepslate_tile_stairs[facing=south,half=bottom]'); t.set(6, 1, 9, 'deepslate_tile_stairs[facing=south,half=bottom]')
    t.set(5, 2, 8, 'iron_bars'), t.set(5, 3, 9, 'deepslate_tiles')
    t.set(5, 1, 6, 'anvil[facing=east]')
    t.set(3, 1, 8, 'smithing_table'); t.set(7, 1, 8, 'grindstone[face=floor,facing=north]')
    t.set(8, 1, 4, 'water_cauldron[level=3]')
    t.set(1, 1, 4, 'barrel[facing=east]'); t.set(1, 1, 5, 'barrel[facing=east]'); t.set(1, 2, 4, 'barrel[facing=up]')
    t.chest(1, 1, 7, 'east', 'chests/warren_forge')
    # plates hung to cool: iron trapdoors on the east wall
    for z in (3, 5, 7):
        t.set(9, 3, z, 'iron_trapdoor[facing=west,half=top,open=true]')
    for x in (2, 8):
        hanging_lantern(t, x, 4, 2)
    t.husk(8, 1, 7, 'west')
    t.set(2, 1, 1, 'white_candle[candles=1,lit=true]')
    doorway(t, 'north', 5, 1)
    return t


def nest(C):
    """The Brood: the clan keeps silverfish here, in walls that are mostly silverfish."""
    t = Template('warren/nest', (11, 7, 11))
    C.rng = t.rng
    crawl = Clan(t.rng, infested=5)
    shell(t, crawl, (0, 0, 0), (10, 6, 10))
    t.fill((1, 0, 1), (9, 0, 9), lambda *_: pick(t.rng, [('infested_cobblestone', 30), ('infested_stone', 20), ('cobblestone', 25), ('mossy_cobblestone', 15), ('cobbled_deepslate', 10)]))
    # lumps of infested rubble growing off the walls
    for (x, z) in ((1, 1), (2, 1), (1, 2), (9, 1), (9, 2), (8, 1), (1, 9), (1, 8), (2, 9), (9, 9), (8, 9), (9, 8)):
        h = t.rng.randint(1, 3)
        for y in range(1, 1 + h): t.set(x, y, z, crawl.rubble())
    for (x, y, z) in ((1, 5, 5), (9, 5, 4), (5, 5, 9), (3, 4, 1), (7, 5, 1), (2, 3, 9), (9, 3, 6)):
        t.set(x, y, z, 'cobweb')
    # the brood stone: a silverfish spawner on a plinth, ringed with candles
    t.set(5, 1, 5, 'chiseled_tuff_bricks')
    t.spawner(5, 2, 5)
    for (x, z) in ((4, 4), (6, 4), (4, 6), (6, 6)):
        t.set(x, 1, z, 'white_candle[candles=1,lit=true]')
    t.husk(9, 1, 6, 'west')
    t.chest(1, 1, 6, 'east', 'chests/den')
    hanging_lantern(t, 5, 4, 2)
    doorway(t, 'north', 5, 1)
    return t


def shrine(C):
    """The Shrine of the First Molt: the mark set high on the wall, a dais of candles, two keepers."""
    t = Template('warren/shrine', (11, 15, 13))
    C.rng = t.rng
    shell(t, C, (0, 0, 0), (10, 14, 12))
    t.fill((1, 0, 1), (9, 0, 11), lambda *_: C.floor())
    for z in range(1, 9): t.set(5, 0, z, 'polished_diorite')
    # the dais: two steps up at the south end
    t.fill((2, 1, 9), (8, 1, 11), 'polished_tuff')
    t.fill((3, 2, 10), (7, 2, 11), 'polished_tuff')
    for x in range(2, 9): t.set(x, 1, 8, 'tuff_brick_stairs[facing=north,half=bottom]') if x not in (2, 8) else None
    for x in range(3, 8): t.set(x, 2, 9, 'tuff_brick_stairs[facing=north,half=bottom]')
    # the mark on the back wall, framed in tuff
    for y in range(2, 14):
        for x in range(1, 10): t.set(x, y, 12, 'tuff_bricks')
    wall_mark(t, 1, 2, 11, bg='deepslate_tiles')
    for y in range(1, 14):
        t.set(0, y, 11, 'chiseled_tuff_bricks' if y % 3 == 0 else 'polished_tuff')
        t.set(10, y, 11, 'chiseled_tuff_bricks' if y % 3 == 0 else 'polished_tuff')
    # candles banked on the steps, an offering pot and chest
    for x in (3, 4, 6, 7):
        t.set(x, 3, 10, f'white_candle[candles={1 + (x % 3)},lit=true]')
    t.set(5, 3, 10, 'decorated_pot[facing=north]')
    t.set(2, 2, 9, 'light_gray_candle[candles=3,lit=true]'); t.set(8, 2, 9, 'light_gray_candle[candles=3,lit=true]')
    t.husk(1, 1, 7, 'east')
    t.husk(9, 1, 7, 'west')
    t.banner(1, 6, 4, 'east'); t.banner(9, 6, 4, 'west')
    t.chest(9, 1, 1, 'west', 'chests/den')
    hanging_lantern(t, 5, 6, 4, chain=2)
    for i in range(1, 10):
        t.set(i, 13, 1, 'tuff_brick_stairs[facing=north,half=top]')
    doorway(t, 'north', 5, 1)
    return t


def store(C):
    """The Storeroom: what the clan lifts off travelers, and what it eats."""
    t = Template('warren/store', (9, 6, 9))
    C.rng = t.rng
    shell(t, C, (0, 0, 0), (8, 5, 8))
    t.fill((1, 0, 1), (7, 0, 7), lambda *_: pick(t.rng, [('spruce_planks', 50), ('polished_andesite', 50)]))
    for (x, z, h) in ((1, 1, 2), (2, 1, 1), (1, 2, 1), (7, 1, 2), (7, 2, 1), (1, 7, 1), (2, 7, 2), (3, 7, 1)):
        for y in range(1, 1 + h): t.set(x, y, z, 'barrel[facing=up]' if t.rng.random() < .6 else 'barrel[facing=north]')
    t.chest(7, 1, 7, 'west', 'chests/warren_stores')
    t.chest(7, 1, 6, 'west', 'chests/warren_stores')
    t.set(6, 1, 1, 'hay_block'); t.set(5, 1, 1, 'hay_block'); t.set(6, 2, 1, 'hay_block')
    t.set(4, 1, 7, 'white_wool'); t.set(5, 1, 7, 'light_gray_wool'); t.set(4, 2, 7, 'brown_wool')
    t.set(4, 1, 4, 'decorated_pot[facing=north]')
    hanging_lantern(t, 4, 3, 3)
    t.husk(7, 1, 4, 'west')
    doorway(t, 'north', 4, 1)
    doorway(t, 'west', 4, 1)
    return t


def vault(C):
    """The Coffer Vault behind the hall, where the Warren's Silver Mother sleeps. A barred antechamber at the hall's level
    with two keepers; a stair eight blocks down; then the Hollow, a great ribbed chamber with six calcite pillars (cover
    from her Keen), the mark in the floor, the shells of her brood along the walls, and at the far end her dais: she
    sleeps curled on it over the Clan Coffer, the Warren's elder asleep beside her."""
    t = Template('warren/vault', (23, 15, 39))
    C.rng = t.rng
    F = 8                     # the antechamber floor: level with the hall's
    # -- the antechamber (x 7..15, z 0..8) ----------------------------------------------------------------
    shell(t, C, (7, F, 0), (15, F + 6, 8))
    t.fill((8, F, 1), (14, F, 7), 'deepslate_tiles')
    for x in range(8, 15):  # the gate: iron bars with the middle pulled open
        for y in range(F + 1, F + 6):
            if x in (10, 11, 12) and y <= F + 3: continue
            t.set(x, y, 3, 'iron_bars[east=true,west=true]')
    t.set(11, F + 4, 3, 'iron_chain[axis=y]'); t.set(11, F + 5, 3, 'iron_chain[axis=y]')
    t.husk(8, F + 1, 5, 'east')
    t.husk(14, F + 1, 5, 'west')
    t.set(8, F + 1, 7, 'white_candle[candles=3,lit=true]'); t.set(14, F + 1, 7, 'white_candle[candles=3,lit=true]')
    hanging_lantern(t, 11, F + 4, 5, chain=1)
    # -- the stair down (x 8..14, z 8..16): seven steps from the antechamber to the Hollow's floor -----------
    for z in range(8, 17):
        f = max(0, min(F, F - (z - 8)))
        ceil = f + 6
        for x in range(8, 15):
            for y in range(0, f):
                t.set(x, y, z, C.plinth() if y == 0 else C.wall())
            t.set(x, f, z, C.floor())
            for y in range(f + 1, ceil):
                t.set(x, y, z, C.rib() if x in (8, 14) else 'air')
            t.set(x, ceil, z, C.rib() if z % 3 == 0 else C.ceiling())
            for y in range(ceil + 1, 15):
                t.set(x, y, z, C.ceiling())
        if 9 <= z <= 15:
            for x in range(9, 14):
                t.set(x, f, z, 'polished_tuff_stairs[facing=north,half=bottom]')
        if z == 12:
            hanging_lantern(t, 11, f + 4, z, chain=1)
    t.fill((9, F + 1, 8), (13, F + 5, 8), 'air')
    # -- the Hollow (x 0..22, z 16..38) -------------------------------------------------------------------
    X0, X1, Z0, Z1, Y1 = 0, 22, 16, 38, 14
    shell(t, C, (X0, 0, Z0), (X1, Y1, Z1))
    for x in range(9, 14):  # the stair comes in through the north wall
        for y in range(1, 6): t.set(x, y, Z0, 'air')
    t.set(9, 6, Z0, 'tuff_brick_stairs[facing=west,half=top]'); t.set(13, 6, Z0, 'tuff_brick_stairs[facing=east,half=top]')
    for x in range(10, 13): t.set(x, 6, Z0, 'chiseled_tuff_bricks')
    # rounded corners: the walls swell inward in tuff
    for (cx, cz, sx, sz) in ((X0 + 1, Z0 + 1, 1, 1), (X1 - 1, Z0 + 1, -1, 1), (X0 + 1, Z1 - 1, 1, -1), (X1 - 1, Z1 - 1, -1, -1)):
        for y in range(1, Y1):
            t.set(cx, y, cz, C.rib())
            if y >= Y1 - 4:
                t.set(cx + sx, y, cz, C.rib()); t.set(cx, y, cz + sz, C.rib())
    # ribs across the ceiling and down the walls, a crown of stairs where the ceiling meets the walls
    for z in (21, 27, 33):
        for x in range(X0 + 1, X1): t.set(x, Y1, z, 'chiseled_tuff_bricks' if x == 11 else C.rib())
        for y in range(1, Y1): t.set(X0, y, z, C.rib()); t.set(X1, y, z, C.rib())
    for x in range(X0 + 2, X1 - 1):
        t.set(x, Y1 - 1, Z0 + 1, 'tuff_brick_stairs[facing=north,half=top]') if not 9 <= x <= 13 else None
        t.set(x, Y1 - 1, Z1 - 1, 'tuff_brick_stairs[facing=south,half=top]')
    for z in range(Z0 + 2, Z1 - 1):
        t.set(X0 + 1, Y1 - 1, z, 'tuff_brick_stairs[facing=west,half=top]')
        t.set(X1 - 1, Y1 - 1, z, 'tuff_brick_stairs[facing=east,half=top]')
    # the floor: andesite, a deepslate runner from the door to the dais, the mark set in it
    t.fill((X0 + 1, 0, Z0 + 1), (X1 - 1, 0, Z1 - 1), lambda *_: 'polished_andesite' if t.rng.random() < .8 else 'polished_tuff')
    t.fill((6, 0, Z0 + 1), (16, 0, Z1 - 1), 'polished_deepslate')
    t.fill((7, 0, Z0 + 1), (15, 0, Z1 - 1), 'deepslate_tiles')
    floor_mark(t, 7, 0, Z0 + 1, mark=MARK_WALL)
    # six calcite pillars, two blocks thick: something to put between you and her Keen
    for (x, z) in ((3, 20), (18, 20), (3, 26), (18, 26), (5, 31), (16, 31)):
        for y in range(1, Y1):
            for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                t.set(x + dx, y, z + dz, 'polished_diorite' if y in (1, Y1 - 1) else 'calcite' if y % 4 else 'chiseled_tuff_bricks')
    # the dais: two steps up at the south end
    t.fill((6, 1, 29), (16, 1, Z1 - 1), 'polished_tuff')
    for x in range(6, 17): t.set(x, 1, 28, 'tuff_brick_stairs[facing=north,half=bottom]')
    t.fill((8, 2, 31), (14, 2, Z1 - 1), 'calcite')
    for x in range(8, 15): t.set(x, 2, 30, 'polished_tuff_stairs[facing=north,half=bottom]')
    t.fill((9, 2, 33), (13, 2, 35), 'polished_diorite')
    floor_mark(t, 9, 2, 31, mark=MARK_TINY)
    t.mother(11, 3, 33, 'north')
    t.chest(11, 3, 36, 'north', 'chests/clan_coffer', custom_name='container.plataclan.clan_coffer')
    t.husk(8, 3, 36, 'north', kind='elder')
    for x in (10, 12): t.set(x, 3, 36, 'white_candle[candles=4,lit=true]')
    for (x, z) in ((8, 31), (14, 31), (14, 36)): t.set(x, 3, z, 'light_gray_candle[candles=3,lit=true]')
    # the shells of her brood along the walls, every one of them split and empty
    for z in (18, 23, 29, 35):
        for (x, f) in ((X0 + 1, 'east'), (X1 - 1, 'west')):
            t.set(x, 1, z, f'plataclan:silver_husk[facing={f},half=lower,kind=sleeper,split=true]')
            t.set(x, 2, z, f'plataclan:silver_husk[facing={f},half=upper,kind=sleeper,split=true]')
    # the mark high on the back wall over the dais, banners on the side walls
    wall_mark(t, 7, 3, Z1 - 1, mark=MARK_WALL, bg='deepslate_tiles')
    for z in (24, 30):
        t.banner(X0 + 1, 7, z, 'east'); t.banner(X1 - 1, 7, z, 'west')
    # cold light: soul lanterns on long chains, candles at the pillars' feet
    for (x, z) in ((11, 19), (7, 24), (15, 24), (11, 29), (7, 34), (15, 34)):
        hanging_lantern(t, x, Y1 - 5, z, chain=4)
        t.set(x, Y1 - 5, z, 'soul_lantern[hanging=true]')
    for (x, z) in ((5, 22), (17, 22), (5, 28), (17, 28)):
        t.set(x, 1, z, 'white_candle[candles=2,lit=true]')
    for (x, y, z) in ((X0 + 2, Y1 - 2, Z0 + 2), (X1 - 2, Y1 - 2, Z0 + 2), (X0 + 2, Y1 - 2, Z1 - 2), (X1 - 2, Y1 - 2, Z1 - 2)):
        t.set(x, y, z, 'cobweb')
    # the gate jigsaw: the hall's south door, at the antechamber's floor
    t.jigsaw(11, F + 1, 0, 'north_up', 'vault_gate', 'minecraft:empty', 'minecraft:empty')
    for x in (10, 11, 12):
        for y in (F + 1, F + 2, F + 3): t.set(x, y, 0, 'air') if (x, y) != (11, F + 1) else None
    return t


# -- the Warren's spiral stair -------------------------------------------------------------------

RING = [(1, 1), (2, 1), (3, 1), (3, 2), (3, 3), (2, 3), (1, 3), (1, 2)]


def warren_stair(C, depth=20):
    """A spiral round a central post, `depth` steps down; the foot opens west into the hall."""
    H = depth + 1
    t = Template('warren/stair', (5, H, 5))
    C.rng = t.rng
    for y in range(H):
        for i in range(5):
            for (x, z) in ((i, 0), (i, 4), (0, i), (4, i)):
                t.set(x, y, z, C.rib() if i in (0, 4) or y % 4 == 0 else C.wall())
    t.fill((1, 0, 1), (3, 0, 3), lambda *_: C.floor())
    t.fill((1, 1, 1), (3, H - 1, 3), 'air')
    for y in range(1, H):
        t.set(2, y, 2, 'polished_tuff' if y % 4 else 'chiseled_tuff_bricks')
    for i in range(depth):
        y = H - 1 - i
        x, z = RING[i % 8]
        nx, nz = RING[(i - 1) % 8] if i > 0 else RING[7]
        # walking up goes from this cell toward the previous one
        up = {(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}[(nx - x, nz - z)] if i > 0 else 'west'
        t.set(x, y, z, f'polished_tuff_stairs[facing={up},half=bottom]' if y > 0 else C.floor())
        if y - 1 >= 1 and y > 5: t.set(x, y - 1, z, 'polished_tuff_stairs[facing=' + {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}[up] + ',half=top]')
    # wall lanterns: hang from the ring's ceiling every half turn
    t.jigsaw(2, H - 1, 2, 'up_north', 'descent', 'minecraft:empty', 'minecraft:empty', final='polished_tuff')
    for z in (1, 2, 3):
        for y in (1, 2, 3): t.set(0, y, z, 'air')
    t.jigsaw(0, 1, 2, 'west_up', 'stair_foot', 'passage', 'warren/hall')
    t.set(0, 4, 1, 'tuff_brick_stairs[facing=north,half=top]'); t.set(0, 4, 2, 'chiseled_tuff_bricks'); t.set(0, 4, 3, 'tuff_brick_stairs[facing=south,half=top]')
    return t


# -- the Scale Den -------------------------------------------------------------------------------

def den_shaft(C, depth=12):
    """A ladder well: top at the camp's hatch, a crawl south into the den at the bottom."""
    t = Template('den/shaft', (3, depth, 3))
    C.rng = t.rng
    for y in range(depth):
        for (x, z) in ((0, 0), (1, 0), (2, 0), (0, 1), (2, 1), (0, 2), (1, 2), (2, 2)):
            t.set(x, y, z, C.wall() if y < depth - 4 else pick(t.rng, [('cobblestone', 50), ('stone', 30), ('mossy_cobblestone', 20)]))
        t.set(1, y, 1, 'ladder[facing=south]' if y > 0 else C.floor())
    t.set(1, 1, 2, 'air'); t.set(1, 2, 2, 'air')
    t.jigsaw(1, depth - 1, 1, 'up_north', 'descent', 'minecraft:empty', 'minecraft:empty', final='ladder[facing=south]')
    t.jigsaw(1, 1, 2, 'south_up', 'den_hall', 'den_door', 'den/rooms')
    return t


def den_room_a(C):
    """A den for three: husks along the east wall, a table, the clan's chest and a small shrine."""
    t = Template('den/room_a', (9, 6, 11))
    C.rng = t.rng
    shell(t, C, (0, 0, 0), (8, 5, 10))
    t.fill((1, 0, 1), (7, 0, 9), lambda *_: C.floor())
    # the crawl in from the shaft (1 wide, 2 high) at the north wall
    t.set(4, 1, 0, 'air'); t.set(4, 2, 0, 'air')
    t.jigsaw(4, 1, 0, 'north_up', 'den_door', 'minecraft:empty', 'minecraft:empty')
    t.husk(7, 1, 3, 'west'); t.husk(7, 1, 6, 'west')
    t.set(7, 1, 9, 'plataclan:silver_husk[facing=west,half=lower,kind=sleeper,split=true]')
    t.set(7, 2, 9, 'plataclan:silver_husk[facing=west,half=upper,kind=sleeper,split=true]')
    # table: a stripped log with a trapdoor top, two stools
    t.set(3, 1, 5, 'spruce_trapdoor[facing=north,half=top]'); t.set(3, 0, 5, C.floor())
    t.set(2, 1, 5, 'spruce_stairs[facing=east,half=bottom]'); t.set(3, 1, 6, 'spruce_stairs[facing=north,half=bottom]')
    t.set(4, 1, 5, 'white_candle[candles=2,lit=true]')
    t.chest(1, 1, 9, 'east', 'chests/den')
    t.set(1, 1, 8, 'barrel[facing=up]')
    # a little shrine: the mark on the south wall above candles
    t.banner(4, 3, 9, 'north')
    t.set(3, 1, 9, 'white_candle[candles=3,lit=true]'); t.set(5, 1, 9, 'white_candle[candles=2,lit=true]')
    hanging_lantern(t, 4, 3, 4)
    t.set(1, 1, 1, 'cobweb'); t.set(1, 4, 9, 'cobweb')
    return t


def den_room_b(C):
    """A den with a sleeping nook off to the west and the stores to the east."""
    t = Template('den/room_b', (11, 6, 9))
    C.rng = t.rng
    shell(t, C, (0, 0, 0), (10, 5, 8))
    t.fill((1, 0, 1), (9, 0, 7), lambda *_: C.floor())
    # a partition of tuff splitting off the nook
    for z in range(1, 6):
        for y in range(1, 5): t.set(4, y, z, C.rib() if z in (1, 5) else C.wall())
    t.set(5, 1, 0, 'air'); t.set(5, 2, 0, 'air')
    t.jigsaw(5, 1, 0, 'north_up', 'den_door', 'minecraft:empty', 'minecraft:empty')
    t.husk(1, 1, 2, 'east'); t.husk(1, 1, 4, 'east'); t.husk(3, 1, 1, 'south')
    t.chest(9, 1, 7, 'west', 'chests/den')
    t.set(9, 1, 6, 'barrel[facing=up]'); t.set(9, 2, 6, 'barrel[facing=up]'); t.set(9, 1, 5, 'barrel[facing=west]')
    t.set(7, 1, 1, 'hay_block'); t.set(8, 1, 1, 'brown_wool')
    t.banner(7, 3, 7, 'north')
    t.set(6, 1, 7, 'white_candle[candles=3,lit=true]')
    hanging_lantern(t, 7, 3, 4); hanging_lantern(t, 2, 3, 6)
    t.set(9, 4, 1, 'cobweb')
    return t


# -- surfaces: the disguise ------------------------------------------------------------------------

PALETTES = {
    'green': dict(canvas='white_wool', trim='brown_wool', pole='spruce_fence', log='oak_log', plank='spruce_planks',
                  stairs='spruce_stairs', slab='spruce_slab', ground='coarse_dirt', ground2='dirt_path', ring='cobblestone',
                  wall=[('cobblestone', 40), ('mossy_cobblestone', 25), ('stone_bricks', 20), ('mossy_stone_bricks', 15)],
                  wall_stairs='cobblestone_stairs', wall_slab='cobblestone_slab', wall_wall='mossy_cobblestone_wall',
                  floor=[('stone_bricks', 40), ('cracked_stone_bricks', 30), ('mossy_stone_bricks', 30)],
                  plant='short_grass', plant2='fern', trapdoor='spruce_trapdoor', bedroll='light_gray_carpet', snow=False,
                  pot='decorated_pot', beam='oak_log', post='chiseled_stone_bricks'),
    'cold': dict(canvas='light_gray_wool', trim='brown_wool', pole='spruce_fence', log='spruce_log', plank='spruce_planks',
                 stairs='spruce_stairs', slab='spruce_slab', ground='podzol', ground2='coarse_dirt', ring='cobblestone',
                 wall=[('stone_bricks', 45), ('cracked_stone_bricks', 25), ('cobblestone', 20), ('andesite', 10)],
                 wall_stairs='stone_brick_stairs', wall_slab='stone_brick_slab', wall_wall='stone_brick_wall',
                 floor=[('stone_bricks', 50), ('cracked_stone_bricks', 30), ('cobblestone', 20)],
                 plant='fern', plant2='sweet_berry_bush', trapdoor='spruce_trapdoor', bedroll='brown_carpet', snow=True,
                 pot='barrel', beam='spruce_log', post='chiseled_stone_bricks'),
    'sand': dict(canvas='white_wool', trim='yellow_wool', pole='birch_fence', log='stripped_birch_log', plank='birch_planks',
                 stairs='sandstone_stairs', slab='sandstone_slab', ground='smooth_sandstone', ground2='sand', ring='cut_sandstone',
                 wall=[('sandstone', 45), ('cut_sandstone', 30), ('smooth_sandstone', 15), ('chiseled_sandstone', 10)],
                 wall_stairs='sandstone_stairs', wall_slab='sandstone_slab', wall_wall='sandstone_wall',
                 floor=[('smooth_sandstone', 50), ('cut_sandstone', 30), ('sandstone', 20)],
                 plant='dead_bush', plant2='short_dry_grass', trapdoor='birch_trapdoor', bedroll='yellow_carpet', snow=False,
                 pot='decorated_pot', beam='stripped_birch_log', post='chiseled_sandstone'),
    'mesa': dict(canvas='orange_wool', trim='brown_wool', pole='dark_oak_fence', log='dark_oak_log', plank='dark_oak_planks',
                 stairs='dark_oak_stairs', slab='dark_oak_slab', ground='red_sand', ground2='coarse_dirt', ring='cut_red_sandstone',
                 wall=[('red_sandstone', 35), ('cut_red_sandstone', 25), ('terracotta', 20), ('orange_terracotta', 20)],
                 wall_stairs='red_sandstone_stairs', wall_slab='red_sandstone_slab', wall_wall='red_sandstone_wall',
                 floor=[('cut_red_sandstone', 40), ('smooth_red_sandstone', 30), ('terracotta', 30)],
                 plant='dead_bush', plant2='short_dry_grass', trapdoor='dark_oak_trapdoor', bedroll='brown_carpet', snow=False,
                 pot='decorated_pot', beam='dark_oak_log', post='chiseled_red_sandstone'),
    'dry': dict(canvas='brown_wool', trim='yellow_wool', pole='acacia_fence', log='acacia_log', plank='acacia_planks',
                stairs='acacia_stairs', slab='acacia_slab', ground='coarse_dirt', ground2='packed_mud', ring='mud_bricks',
                wall=[('mud_bricks', 55), ('packed_mud', 30), ('terracotta', 15)],
                wall_stairs='mud_brick_stairs', wall_slab='mud_brick_slab', wall_wall='mud_brick_wall',
                floor=[('packed_mud', 50), ('mud_bricks', 30), ('coarse_dirt', 20)],
                plant='short_grass', plant2='short_dry_grass', trapdoor='acacia_trapdoor', bedroll='orange_carpet', snow=False,
                pot='decorated_pot', beam='acacia_log', post='mud_bricks'),
    'stone': dict(canvas='gray_wool', trim='light_gray_wool', pole='spruce_fence', log='spruce_log', plank='spruce_planks',
                  stairs='spruce_stairs', slab='spruce_slab', ground='gravel', ground2='coarse_dirt', ring='andesite',
                  wall=[('cobblestone', 35), ('andesite', 25), ('stone', 20), ('tuff', 20)],
                  wall_stairs='cobblestone_stairs', wall_slab='andesite_slab', wall_wall='cobblestone_wall',
                  floor=[('polished_andesite', 40), ('andesite', 30), ('cobblestone', 30)],
                  plant='short_grass', plant2='fern', trapdoor='spruce_trapdoor', bedroll='gray_carpet', snow=False,
                  pot='barrel', beam='spruce_log', post='polished_andesite'),
}


def den_surface(p, key):
    """A traveler's camp: a canvas tent, a fire with a log bench, a pack and a woodpile. The hatch is under the bedroll's
    neighbour in the tent; the lookout waits by the fire."""
    t = Template(f'den/surface_{key}', (13, 7, 13))
    rng = t.rng
    # tent: an A-frame along z, x 3..9, z 5..10, ridge at x 6
    steps = [(3, 1), (9, 1), (4, 2), (8, 2), (5, 3), (7, 3), (6, 4)]
    for z in range(5, 11):
        for (x, y) in steps:
            t.set(x, y, z, p['trim'] if (x, y) == (6, 4) else p['canvas'])
        for (x, y) in ((4, 1), (5, 1), (6, 1), (7, 1), (8, 1), (5, 2), (6, 2), (7, 2), (6, 3)):
            t.set(x, y, z, 'air' if z < 10 else p['canvas'])
        if p['snow']:
            for (x, y) in ((3, 2), (9, 2), (4, 3), (8, 3), (5, 4), (7, 4), (6, 5)):
                t.set(x, y, z, 'snow[layers=1]')
    for z in range(4, 12):
        t.set(6, 0, z, p['ground2'] if z < 6 else p['ground'])
    t.fill((4, 0, 6), (8, 0, 9), p['ground'])
    # ridge poles out front and back
    for z in (4, 11):
        for y in range(1, 4): t.set(6, y, z, p['pole'])
        t.set(6, 4, z, p['pole'])
    # inside: bedroll, pack, lantern, and the hatch (the start jigsaw) at (6, 0, 8)
    t.set(4, 1, 7, p['bedroll']); t.set(4, 1, 8, p['bedroll'])
    t.set(8, 1, 9, 'barrel[facing=up]')
    t.set(8, 1, 6, 'lantern[hanging=false]')
    t.jigsaw(6, 0, 8, 'down_north', 'descent', 'descent', 'den/shaft', final=p['trapdoor'] + '[facing=south,half=top]')
    # the fire: a ring set in the ground, a campfire, a log bench and a stump
    for (x, z) in ((5, 1), (6, 1), (7, 1), (5, 2), (7, 2), (5, 3), (6, 3), (7, 3)):
        t.set(x, 0, z, p['ring'])
    t.set(6, 0, 2, p['ring'])
    t.set(6, 1, 2, 'campfire[facing=north,lit=true,signal_fire=false]')
    t.set(3, 1, 1, p['log'] + '[axis=z]'); t.set(3, 1, 2, p['log'] + '[axis=z]')
    t.set(9, 1, 3, p['log'] + '[axis=y]')
    for (x, z) in ((5, 1), (6, 1), (7, 1), (5, 2), (7, 2), (5, 3), (6, 3), (7, 3), (6, 2)):
        t.set(x, 1, z, 'air') if (x, z) != (6, 2) else None
    t.lookout(4, 1, 3)
    # gear: a woodpile, a pot, a lantern post
    for (x, y, z) in ((11, 1, 8), (11, 1, 9), (11, 1, 10), (11, 2, 9)):
        t.set(x, y, z, p['log'] + '[axis=x]')
    t.set(1, 1, 9, p['pot'] + ('[facing=north]' if p['pot'] == 'decorated_pot' else '[facing=up]'))
    t.set(1, 1, 5, p['pole']); t.set(1, 2, 5, p['pole']); t.set(1, 3, 5, 'lantern[hanging=false]')
    # clear the ground's tufts around the camp, then scatter a few of our own
    for x in range(13):
        for z in range(13):
            if t.blocks.get((x, 1, z)) is None: t.set(x, 1, z, 'air')
            if t.blocks.get((x, 2, z)) is None and (x, z) not in ((1, 5),): t.set(x, 2, z, 'air')
    for (x, z) in ((0, 2), (12, 4), (2, 12), (10, 12), (12, 0), (0, 11)):
        t.set(x, 1, z, p['plant'] if rng.random() < .6 else p['plant2'])
    return t


def warren_surface(p, key):
    """A ruined toll-house: broken walls, a fallen roof beam and an old cistern in the floor with a windlass post.
    The 'cistern' is the top of the clan's spiral stair."""
    t = Template(f'warren/surface_{key}', (15, 9, 15))
    rng = t.rng
    wall = lambda *_: pick(rng, p['wall'])
    # the floor, with gaps where the ground shows through
    for x in range(2, 13):
        for z in range(2, 13):
            if rng.random() < .82: t.set(x, 0, z, pick(rng, p['floor']))
    # walls of uneven height (x 2..12, z 2..12), a door gap south, window gaps
    heights = {}
    for i in range(2, 13):
        for (x, z) in ((i, 2), (i, 12), (2, i), (12, i)):
            corner = (x in (2, 12)) and (z in (2, 12))
            base = 6 if corner else 2 + int(3 * abs(((i * 37 + x * 11 + z * 7) % 13) / 13.0 - .4) * 2)
            heights[(x, z)] = min(6, max(1, base + rng.randint(-1, 1)))
    for (x, z), h in heights.items():
        for y in range(1, h + 1):
            t.set(x, y, z, wall())
        if h < 6 and rng.random() < .5:
            t.set(x, h + 1, z, p['wall_slab'] + '[type=bottom]')
    for x in (6, 7, 8):  # the doorway
        for y in range(1, 4): t.set(x, y, 12, 'air')
    t.set(6, 4, 12, p['wall_stairs'] + '[facing=west,half=top]'); t.set(8, 4, 12, p['wall_stairs'] + '[facing=east,half=top]')
    t.set(7, 4, 12, wall())
    for (x, z) in ((2, 6), (2, 8), (12, 7), (5, 2), (9, 2)):  # windows
        t.set(x, 2, z, 'air'); t.set(x, 3, z, 'air')
    # roof remains: a beam across the top and a few hanging planks; the rest has fallen in
    for x in range(2, 13):
        t.set(x, 6, 5, p['beam'] + '[axis=x]')
    for z in (3, 4):
        t.set(3, 6, z, p['slab'] + '[type=top]'); t.set(4, 6, z, p['slab'] + '[type=top]'); t.set(11, 6, z, p['slab'] + '[type=top]')
    t.set(10, 1, 9, p['beam'] + '[axis=z]'); t.set(10, 1, 10, p['beam'] + '[axis=z]'); t.set(10, 2, 10, p['slab'] + '[type=bottom]')
    # the old cistern: a 3x3 hole round a post, a rim of slabs, a windlass over it
    # only the north row is open (over the first steps); a broken cover of trapdoors hides the rest
    for x in (6, 7, 8):
        for z in (6, 7, 8):
            t.set(x, 0, z, 'air' if z == 6 else p['trapdoor'] + '[facing=north,half=top]')
    for i in range(5, 10):
        for (x, z) in ((i, 5), (i, 9), (5, i), (9, i)):
            t.set(x, 0, z, p['post']); t.set(x, 1, z, p['wall_slab'] + '[type=bottom]')
    t.jigsaw(7, 0, 7, 'down_north', 'descent', 'descent', 'warren/stair', final=p['post'])
    t.set(7, 1, 7, p['pole']); t.set(7, 2, 7, p['pole'])
    t.set(5, 2, 7, p['pole']); t.set(5, 3, 7, p['pole']); t.set(9, 2, 7, p['pole']); t.set(9, 3, 7, p['pole'])
    for x in range(5, 10): t.set(x, 4, 7, p['beam'] + '[axis=x]')
    t.set(7, 3, 7, 'iron_chain[axis=y]')
    # rubble outside, a bench by the door where the lookout sits, a lantern post on the road
    for (x, z) in ((1, 3), (1, 4), (0, 3), (13, 10), (13, 11), (3, 13), (11, 1), (12, 1)):
        t.set(x, 1, z, pick(rng, [(p['wall_slab'] + '[type=bottom]', 50), (pick(rng, p['wall']), 50)]))
    t.set(4, 1, 13, p['stairs'] + '[facing=north,half=bottom]'); t.set(5, 1, 13, p['stairs'] + '[facing=north,half=bottom]')
    t.lookout(5, 1, 14)
    t.set(10, 1, 14, p['pole']); t.set(10, 2, 14, p['pole']); t.set(10, 3, 14, 'lantern[hanging=false]')
    for (x, z) in ((3, 3), (11, 11), (3, 10)):
        t.set(x, 1, z, p['plant'] if rng.random() < .5 else p['plant2'])
    t.set(11, 1, 3, p['pot'] + ('[facing=north]' if p['pot'] == 'decorated_pot' else '[facing=up]'))
    # clear the ground's growth inside the walls
    for x in range(3, 12):
        for z in range(3, 12):
            for y in range(1, 6):
                if t.blocks.get((x, y, z)) is None: t.set(x, y, z, 'air')
    if p['snow']:
        for (x, z), h in heights.items():
            if t.blocks.get((x, h + 1, z)) is None: t.set(x, h + 1, z, 'snow[layers=1]')
        for x in range(2, 13): t.set(x, 7, 5, 'snow[layers=1]')
    return t


# -- everything ----------------------------------------------------------------------------------

def all_pieces():
    rng = random.Random(7)
    C = Clan(rng)
    out = [
        hall(C), warren_stair(C), vault(C),
        tunnel_straight('warren/tunnel_short', 7, C), tunnel_straight('warren/tunnel_long', 11, C),
        tunnel_bend(C), tunnel_tee(C), tunnel_stairs(C), tunnel_cap(C),
        gallery(C), pit_room(C), forge(C), nest(C), shrine(C), store(C),
        den_shaft(C), den_room_a(C), den_room_b(C),
    ]
    for key, p in PALETTES.items():
        out.append(den_surface(p, key))
        out.append(warren_surface(p, key))
    return out
