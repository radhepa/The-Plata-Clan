"""The Silver Mother: the oldest of a Warren, who sleeps curled on the dais of its vault (DESIGN.md section 11).

She is a person like every Plata (the vanilla player mesh, drawn 1.5x by MotherRenderer) and wears the clan's own face:
the same hood and the same banded silver face plate as her children. Everything else says she is more than they are.
The slits of her face plate, the clan mark on its brow and the mark on her train burn with a cold light (an emissive
layer, MotherRenderer); a circlet round her hood carries four feelers where her children have two; a fan collar of
shell plates rises behind her head; a long coat of overlapping silver segments trimmed in tarnished bronze; plated
gauntlets; and her train, the shell of her first molt, falling from her shoulders to the floor and trailing behind
her in segments that end in the silverfish's three tail strands, their tips alight. In the game silver motes drift
round her too.
No white mask, no red, no eye symbol, no sickle, no topknot.

Named parts MotherModel moves on its own: antenna_r/l_1..3, feeler_r/l_1..2, train_1..4, cercus_r/m/l, tabard.
"""
import common  # noqa: F401  (path setup)
from common import BRONZE, GLINT, SILVER, SLATE, UMBER, at, painter, plate, plate_px, ring_u
from costume import antenna_px, bracers, brow_paint, eyes_paint, face_px, hood_px, hood_ridge, sleeve_px, trouser_px
from mobkit import Model, Pack, faces


# -- the clan's face, alight ------------------------------------------------------------------------
def glowing_brow(c):
    """The face plate's brow, exactly as her children wear it, but the engraved mark is inlaid with light."""
    brow_paint(c)
    if c.face == "front":
        cx = c.w // 2
        for x in (cx - 1, cx):
            c.glow(x, 1, "#3E6A73")                             # a dim light in the engraving, no more


def glowing_eyes(c):
    """The eye band with its two slits; the glint in each burns."""
    eyes_paint(c)
    if c.face == "front":
        for x in (2, c.w - 3):
            c.glow(x, 1, GLINT[1])
        for x in (1, c.w - 2):
            c.glow(x, 1, "#3E6A73")                             # light bleeding along the slit


def mask(m, P, head):
    """The clan's face plate (costume.mask), with the Mother's light in it."""
    mk = head.part("mask", (0, -4, -4))
    P.add(mk, (-4, -3.5, -1.45), (8, 3, 1), faces(glowing_brow))
    P.add(mk, (-4, -0.5, -1.25), (8, 3, 1), faces(glowing_eyes))
    P.add(mk, (-3, 2.4, -1.05), (6, 2, 1), plate(SILVER, top=2))
    P.add(mk, (-2, 4.4, -0.85), (4, 1, 1), plate(SILVER, top=2))
    for x0 in (-4.75, 3.75):
        P.add(mk, (x0, -0.25, -1.15), (1, 4, 2), plate(SILVER, top=2))
    return mk


def circlet_px(c, x, y):
    """A thin silver band round the brow, bronze studs; a cold glint set at the centre of the brow."""
    if c.face in ("top", "bottom"):
        return None
    if c.face == "front" and x == c.w // 2:
        return GLINT[1]
    if c.face == "front" and x in (c.w // 2 - 1, c.w // 2 + 1):
        return BRONZE[2]
    u = x + ring_u(c, 9, 9)
    return BRONZE[1] if u % 4 == 0 else SILVER[3]


# -- the fan collar ---------------------------------------------------------------------------------
def collar_px(rim=True):
    def fn(c, x, y):
        if c.face == "front":
            return SILVER[1] if y % 2 else SILVER[0]            # the inner face, in shadow
        if c.face == "top":
            return BRONZE[2]
        if c.face == "bottom":
            return SILVER[0]
        if y == 0 and rim:
            return BRONZE[2] if x == 0 else BRONZE[1]
        band = y % 2
        i = 3 if band == 1 else 1
        if x == 0:
            i += 1
        elif x == c.w - 1:
            i -= 1
        return at(SILVER, i)
    return fn


def collar(m, P, body):
    """Three plates rising from the back of her shoulders and leaning away behind her head, the outer two fanned."""
    centre = body.part("collar", (0, 2, 2.3), (-0.5, 0, 0))
    P.add(centre, (-2.5, -10, 0), (5, 10, 1), faces(painter(collar_px())))
    for side, sx in (("r", -1), ("l", 1)):
        p = body.part(f"collar_{side}", (2.3 * sx, 2, 2.2), (-0.42, 0.5 * sx, 0.32 * sx))
        x0 = -4 if sx < 0 else 0
        P.add(p, (x0, -8, 0), (4, 8, 1), faces(painter(collar_px())))


# -- the coat, the sash and the tabard --------------------------------------------------------------
def coat(m, P, body):
    """Four segment plates stepping down the front of the coat, narrowing like a silverfish's body; only the last
    is edged in bronze."""
    for k, (w, h, y0) in enumerate(((8, 3, 0.2), (8, 3, 3.2), (7, 2, 6.2), (6, 2, 8.2))):
        P.add(body, (-w / 2, y0, -2.9 + 0.12 * k), (w, h, 1), plate(SILVER, top=3, trim=BRONZE if k == 3 else None),
              inflate=0.04 if w == 8 else 0)


def pauldrons(m, P, arm, side):
    """Two flared tiers over each shoulder, bronze along their lower edges."""
    cx, out = (-1 if side == "right" else 1), (-1 if side == "right" else 1)
    for k, (w, d, y, g) in enumerate(((5, 6, -3.2, 0.0), (4, 5, -1.6, 0.15))):
        outer = cx + out * (2.6 + 0.4 * k)
        x0 = outer if out < 0 else outer - w
        P.add(arm, (x0, y, -d / 2), (w, 2, d), plate(SILVER, top=3 - k, trim=BRONZE), inflate=g)


def suit_px(c, x, y):
    if c.face in ("top", "bottom"):
        return SLATE[2] if c.face == "top" else SLATE[0]
    if y == 0:
        return SLATE[3]
    u = x + ring_u(c, 8, 4)
    return SLATE[1] if u % 4 == 3 else SLATE[2]


def sash_px(c, x, y):
    if c.face in ("top", "bottom"):
        return UMBER[1]
    u = x + ring_u(c, 9, 5)
    if y == 0:
        return BRONZE[1] if u % 3 else BRONZE[0]
    if y == c.h - 1:
        return UMBER[0]
    return UMBER[1] if u % 3 == 0 else UMBER[2]


def buckle(c):
    painter(plate_px(SILVER, edge_lit=False))(c)
    if c.face == "front":
        c.fill(SILVER[2])
        for x in range(c.w):
            c.set(x, 0, SILVER[4] if x == 0 else SILVER[3])
            c.set(x, c.h - 1, SILVER[0])
        c.set(c.w // 2, 1, GLINT[0])


TABARD_MARK = ["#...#",
               ".hhh.",
               ".aba.",
               ".bab.",
               "..a..",
               ".#.#."]


def tabard_px(c, x, y):
    if c.face in ("top", "bottom"):
        return SLATE[1]
    if c.face != "front":
        return SLATE[1] if c.face == "back" else SLATE[2]
    if x in (0, c.w - 1):
        return SILVER[2] if x == 0 else SILVER[1]               # silver piping down the edges
    if y == c.h - 1:
        return BRONZE[1]
    row = y - 1
    if 0 <= row < len(TABARD_MARK) and 0 <= x - 1 < 5:
        ch = TABARD_MARK[row][x - 1]
        if ch != ".":
            return {"#": SILVER[1], "h": SILVER[3], "a": SILVER[3], "b": SILVER[2]}[ch]
    return SLATE[2] if (x + y) % 4 else SLATE[1]


# -- the train: the shell of her first molt ---------------------------------------------------------
def train_px(seg_h=3, mark=False):
    """Overlapping shell segments down the train; each segment light at its upper edge, dark at its lower;
    a keel down the middle, and the clan mark large on the first panel."""
    def fn(c, x, y):
        if c.face == "front":
            return SLATE[0]                                     # the lining
        if c.face in ("top", "bottom"):
            return SILVER[1] if c.face == "top" else SILVER[0]
        if c.face in ("right", "left"):
            return SILVER[1] if y % seg_h else SILVER[3]
        band = y % seg_h
        i = 3 if band == 0 else 1 if band == seg_h - 1 else 2
        edge = x in (0, c.w - 1)
        if edge and band == seg_h - 1:
            return None                                         # scalloped segment ends
        if edge:
            i -= 1
        if x == c.w // 2 or (c.w % 2 == 0 and x == c.w // 2 - 1):
            i = min(4, i + 1)                                   # the keel
        if mark and 2 <= y < 2 + len(TRAIN_MARK) and 0 <= x - (c.w - 9) // 2 < 9:
            ch = TRAIN_MARK[y - 2][x - (c.w - 9) // 2]
            if ch != ".":
                return {"F": SILVER[4], "H": BRONZE[2], "A": SILVER[4], "B": SILVER[3], "S": SILVER[1]}[ch]
        return at(SILVER, i)
    return fn


TRAIN_MARK = ["F.......F",
              ".F.....F.",
              "..FHHHF..",
              "..AAAAS..",
              ".BBBBBBS.",
              "AAAAAAAAS",
              ".BBBBBBS.",
              "..AAAAS..",
              "...BBS...",
              "....A....",
              "..F.F.F..",
              ".F..F..F."]


def train_mark(c):
    """The first panel of the train, its great mark traced in cold light (feelers, head and tail strands)."""
    painter(train_px(3, mark=True))(c)
    if c.face != "back":
        return
    ox = (c.w - 9) // 2
    for y, row in enumerate(TRAIN_MARK):
        for x, ch in enumerate(row):
            if ch in "FH":
                c.glow(ox + x, 2 + y, GLINT[1] if ch == "H" else GLINT[0])


def cercus_paint(c):
    painter(cercus_px)(c)
    if c.face not in ("top", "bottom"):
        c.glow(0, c.h - 1, GLINT[1])


def cercus_px(c, x, y):
    if c.face in ("top", "bottom"):
        return SILVER[1]
    if y == c.h - 1:
        return GLINT[1]
    return SILVER[3] if y % 3 == 0 else SILVER[2] if y % 3 == 1 else SILVER[1]


def train(m, P, body):
    t1 = body.part("train_1", (0, 0.5, 2.6), (0.1, 0, 0))
    P.add(t1, (-5, 0, 0), (10, 22, 1), faces(train_mark))
    t2 = t1.part("train_2", (0, 21.8, 0.4), (1.25, 0, 0))
    P.add(t2, (-4.5, 0, 0), (9, 7, 1), faces(painter(train_px(3))))
    t3 = t2.part("train_3", (0, 6.8, 0), (0.18, 0, 0))
    P.add(t3, (-3.5, 0, 0), (7, 6, 1), faces(painter(train_px(3))))
    t4 = t3.part("train_4", (0, 5.8, 0), (0.02, 0, 0))
    P.add(t4, (-2.5, 0, 0), (5, 5, 1), faces(painter(train_px(2))))
    for name, x, rz, ln in (("cercus_r", -1.5, 0.38, 9), ("cercus_m", 0, 0, 11), ("cercus_l", 1.5, -0.38, 9)):
        s = t4.part(name, (x, 4.6, 0.5), (0, 0, rz))
        P.add(s, (-0.5, 0, -0.5), (1, ln, 1), faces(cercus_paint))


# -- limbs ------------------------------------------------------------------------------------------
def gauntlet_px(c, x, y):
    if c.face == "top":
        return BRONZE[1]
    if c.face == "bottom":
        return SILVER[0]
    if y == 0:
        return BRONZE[2] if x == 0 else BRONZE[1]               # a bronze cuff
    if y == c.h - 1:
        return SILVER[1] if c.face == "front" and x % 2 else SILVER[0]    # knuckle plates
    i = 3 if y == 1 else 2
    if x == 0:
        i += 1
    elif x == c.w - 1:
        i -= 1
    if c.face == "front" and y >= 2 and x % 2 == 1:
        i -= 1                                                  # finger plates
    return at(SILVER, i)


def sabaton_px(c, x, y):
    if c.face == "top":
        return SILVER[2]
    if c.face == "bottom":
        return UMBER[0]
    if y == c.h - 1:
        return UMBER[1]
    i = 3 if y == 0 else 2
    if x == 0:
        i += 1
    return at(SILVER, i)


def limbs(m, P, parts):
    for side in ("right", "left"):
        arm, leg = parts[side + "_arm"], parts[side + "_leg"]
        pauldrons(m, P, arm, side)
        bracers(m, P, arm, side)
        cx = -1 if side == "right" else 1
        P.add(arm, (cx - 2.5, 6.6, -2.5), (5, 4, 5), faces(painter(gauntlet_px)), inflate=0.12)
        P.add(leg, (-2, 0, -2), (4, 12, 4), faces(painter(trouser_px)), inflate=0.3)       # full trousers
        P.add(leg, (-1.5, 3.3, -2.6), (3, 2, 1), plate(SILVER, top=3, trim=BRONZE), inflate=0.1)   # knee
        P.add(leg, (-1.5, 6, -2.7), (3, 3, 1), plate(SILVER, top=2), inflate=0.05)               # greave
        P.add(leg, (-2, 9, -2), (4, 3, 4), faces(painter(sabaton_px)), inflate=0.42 if side == "right" else 0.38)


# -- the whole Mother -------------------------------------------------------------------------------
def build():
    m = Model("silver_mother", 128, 128, seed=13)
    P = Pack(m)
    head = m.part("head")
    P.add(head, (-4, -8, -4), (8, 8, 8), faces(painter(face_px)))
    P.add(head, (-4, -8, -4), (8, 8, 8), faces(painter(hood_px)), inflate=0.5)
    head.part("hat")
    mask(m, P, head)
    hood_ridge(m, P, head, BRONZE)
    P.add(head, (-4.5, -7.6, -4.5), (9, 1, 9), faces(painter(circlet_px)), inflate=0.15)
    bends = (-0.38, -0.5, -0.45)
    for side, sx in (("r", -1), ("l", 1)):
        parent, pivot = head, (3.4 * sx, -7.4, -5.0)
        for i, ln in enumerate((5, 5, 4)):
            seg = parent.part(f"antenna_{side}_{i + 1}", pivot, (bends[i], 0, sx * (0.3 if i == 0 else 0.08)))
            P.add(seg, (-0.5, -ln, -0.5), (1, ln, 1), faces(painter(antenna_px(i == 2))))
            parent, pivot = seg, (0, -ln + 0.3, 0)
        parent, pivot = head, (4.6 * sx, -7.2, -1.8)
        for i, ln in enumerate((3, 3)):
            seg = parent.part(f"feeler_{side}_{i + 1}", pivot, ((-0.3, -0.5)[i], 0, sx * (1.05 if i == 0 else 0.2)))
            P.add(seg, (-0.5, -ln, -0.5), (1, ln, 1), faces(painter(antenna_px(i == 1))))
            parent, pivot = seg, (0, -ln + 0.3, 0)
    body = m.part("body")
    P.add(body, (-4, 0, -2), (8, 12, 4), faces(painter(suit_px)))
    coat(m, P, body)
    P.add(body, (-4.5, 10.5, -2.5), (9, 2, 5), faces(painter(sash_px)))
    P.add(body, (-1.5, 10, -3.25), (3, 3, 1), faces(buckle))
    tab = body.part("tabard", (0, 12, -2.45), (0, 0, 0))
    P.add(tab, (-3.5, 0, -0.5), (7, 9, 1), faces(painter(tabard_px)))
    collar(m, P, body)
    train(m, P, body)
    ra = m.part("right_arm", (-5, 2, 0))
    P.add(ra, (-3, -2, -2), (4, 12, 4), faces(painter(sleeve_px)))
    la = m.part("left_arm", (5, 2, 0))
    P.add(la, (-1, -2, -2), (4, 12, 4), faces(painter(sleeve_px)))
    rl = m.part("right_leg", (-1.9, 12, 0))
    ll = m.part("left_leg", (1.9, 12, 0))
    limbs(m, P, {"right_arm": ra, "left_arm": la, "right_leg": rl, "left_leg": ll})
    P.apply()
    m.scale = 1.5
    m.save()
    return m


if __name__ == "__main__":
    model = build()
    print(f"{model.id}: {len(model.parts)} parts, {sum(len(p.cubes) for p in model.parts)} cubes")
