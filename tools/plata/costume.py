"""The Plata clan uniform, shared by every rank, layered on the vanilla player mesh.

dress(model, rank, ...) builds the seven humanoid parts with their exact player boxes, then the
costume pieces (mask, hood ridge, antennae, segmented vest, sash, tail cords, pauldrons, bracers,
knee plates, boots) and returns (parts, pack) so a rank file can add its own pieces before packing.
All cube sizes are whole pixels (the game maps UVs from raw sizes); sub-pixel offsets use inflate.
"""
import math

from common import (BRONZE, CORDS, GLINT, INK, SILVER, SLATE, UMBER, at, cloth_px, cord_px, glyph,
                    lit, painter, plate, plate_px, ring_u, wraps_px)
from mobkit import Pack, faces

VEST_W = (4, 6, 6, 4, 2)          # the five segment plates: a silverfish teardrop down the torso
VEST_Y = (1, 3, 5, 7, 9)


# -- the body underneath ---------------------------------------------------------------------------
def hood_px(c, x, y):
    """The hood shell (head box, inflate 0.5). Its front is a slate rim round an open face."""
    if c.face == "front":
        if y == 0:
            return SLATE[3]
        if x in (0, c.w - 1):
            return SLATE[3] if x == 0 else SLATE[2]
        return None                             # the opening: mask and shadow show through
    if c.face == "top":
        if y == c.h - 1:
            return SLATE[4]
        return SLATE[3] if (x + y) % 5 else SLATE[2]
    if c.face == "bottom":
        return SLATE[0]
    if c.face in ("right", "left"):
        front_col = c.w - 1 if c.face == "right" else 0
        d = abs(x - front_col)                  # columns back from the face opening
        if d == 0:
            return SLATE[4] if y == 0 else SLATE[3]          # lit rim of the hood
        if y == 0:
            return SLATE[3]
        if y == c.h - 1:
            return SLATE[1]
        if d == 1 or (d == 4 and y >= 2):
            return SLATE[1]                     # the fold behind the rim, a long drape fold
        if d == 5 and y >= 2:
            return SLATE[3]
        return SLATE[2]
    if y == 0:
        return SLATE[3]
    if y == c.h - 1:
        return SLATE[1]
    if x in (3, 4) and y >= 3:
        return SLATE[1] if x == 4 else SLATE[2]             # seam down the back
    if x in (1, 6) and y >= 2:
        return SLATE[1] if x == 6 else SLATE[3]
    return SLATE[2]


def face_px(c, x, y):
    """The head itself, deep in the hood's shadow."""
    if c.face == "front":
        return SLATE[1] if y == 0 or x in (0, c.w - 1) else SLATE[0]
    return SLATE[1]


def bodysuit_px(c, x, y):
    """Slate under-suit. The vest hides most of it; the sides carry the 'many legs' ticks."""
    if c.face == "top":
        return SLATE[3]
    if c.face == "bottom":
        return SLATE[0]
    if y == 0:
        return SLATE[3]                         # collar
    if c.face in ("right", "left"):
        front_col = c.w - 1 if c.face == "right" else 0
        back_col = 0 if c.face == "right" else c.w - 1
        step = 1 if c.face == "left" else -1
        for col in (front_col, back_col):
            inward = step if col == front_col else -step
            if 2 <= y <= 9 and (y % 2 == 0 and x == col or y % 2 == 1 and x == col + inward):
                return INK                      # tiny dark leg ticks along the flanks
        return SLATE[2] if x in (1, 2) else SLATE[1]
    # front/back: only slivers beside the narrow plates show
    for k, (w, y0) in enumerate(zip(VEST_W, VEST_Y)):
        if y0 <= y < y0 + 2 and (x < (8 - w) // 2 or x >= 8 - (8 - w) // 2):
            edge = (8 - w) // 2 - 1
            if y == y0 + 1 and x in (edge, 7 - edge):
                return INK                      # a leg tick beside the plate
            return SLATE[1]
    return SLATE[1]


def binding(u, y):
    """Umber strip wound round a limb: lit top, a slanted seam every fourth column."""
    if (u + y) % 4 == 0:
        return UMBER[1]
    return UMBER[3] if y % 2 == 0 and u % 4 == 1 else UMBER[2]


def sleeve_px(c, x, y):
    if c.face == "top":
        return SLATE[2]
    if c.face == "bottom":
        return UMBER[1]                         # the bound fist
    u = x + ring_u(c, 4, 4)
    if y >= 9:
        return binding(u, y)                    # umber hand wraps
    if y >= 5:
        return SLATE[1]                         # under the bracers
    i = 2
    if u % 4 == 3:
        i = 1
    elif u % 4 == 0:
        i = 3
    return at(SLATE, i)


def trouser_px(c, x, y):
    if c.face == "top":
        return SLATE[2]
    if c.face == "bottom":
        return UMBER[0]
    u = x + ring_u(c, 4, 4)
    if y >= 9:
        return UMBER[1]                         # inside the boot
    if y in (6, 8):
        return UMBER[3] if u % 4 == 0 else UMBER[2] if (u + y) % 4 else UMBER[1]   # shin bindings
    if y in (5, 7):
        return SLATE[1]
    i = 2
    if u % 4 == 3:
        i = 1
    elif u % 4 == 0 and y < 3:
        i = 3
    if y == 4:
        i = 1
    return at(SLATE, i)


def boot_px(c, x, y):
    if c.face == "top":
        return UMBER[2]
    if c.face == "bottom":
        return UMBER[0]
    if y == 0:
        return UMBER[3]                         # soft folded cuff
    if y == c.h - 1:
        return UMBER[0]                         # sole
    if x == 0 and c.face != "back":
        return UMBER[2]
    return UMBER[1]


def base_body(m, P):
    head = m.part("head")
    P.add(head, (-4, -8, -4), (8, 8, 8), faces(painter(face_px)))
    P.add(head, (-4, -8, -4), (8, 8, 8), faces(painter(hood_px)), inflate=0.5)
    head.part("hat")
    body = m.part("body")
    P.add(body, (-4, 0, -2), (8, 12, 4), faces(painter(bodysuit_px)))
    ra = m.part("right_arm", (-5, 2, 0))
    P.add(ra, (-3, -2, -2), (4, 12, 4), faces(painter(sleeve_px)))
    la = m.part("left_arm", (5, 2, 0))
    P.add(la, (-1, -2, -2), (4, 12, 4), faces(painter(sleeve_px)))
    rl = m.part("right_leg", (-1.9, 12, 0))
    P.add(rl, (-2, 0, -2), (4, 12, 4), faces(painter(trouser_px)))
    ll = m.part("left_leg", (1.9, 12, 0))
    P.add(ll, (-2, 0, -2), (4, 12, 4), faces(painter(trouser_px)))
    return {"head": head, "body": body, "right_arm": ra, "left_arm": la, "right_leg": rl, "left_leg": ll}


# -- head: mask, hood ridge, antennae --------------------------------------------------------------
def brow_paint(c):
    painter(plate_px(SILVER))(c)
    if c.face == "front":                       # the clan mark engraved on the brow
        cx = c.w // 2
        c.set(cx - 2, 0, SILVER[1])
        c.set(cx + 1, 0, SILVER[1])
        c.set(cx - 1, 1, SILVER[0])
        c.set(cx, 1, SILVER[0])


def eyes_paint(c):
    painter(plate_px(SILVER))(c)
    if c.face == "front":
        for x0 in (0, c.w - 3):                 # two narrow slits, a cold glint in each
            for dx in range(3):
                c.set(x0 + dx, 1, INK)
        c.set(2, 1, GLINT[1])
        c.set(c.w - 3, 1, GLINT[1])
    elif c.face in ("left", "right"):
        c.set(0, 1, INK)                        # the slits wrap round the edge


def mask(m, P, head):
    """Face plate: four carapace bands stepping back and narrowing toward the chin, set in the hood."""
    mk = head.part("mask", (0, -4, -4))
    P.add(mk, (-4, -3.5, -1.45), (8, 3, 1), faces(brow_paint))                  # brow, clan mark
    P.add(mk, (-4, -0.5, -1.25), (8, 3, 1), faces(eyes_paint))                  # eye slits
    P.add(mk, (-3, 2.4, -1.05), (6, 2, 1), plate(SILVER, top=2))
    P.add(mk, (-2, 4.4, -0.85), (4, 1, 1), plate(SILVER, top=2))                # chin
    for x0 in (-4.75, 3.75):                                                    # cheek flaps
        P.add(mk, (x0, -0.25, -1.15), (1, 4, 2), plate(SILVER, top=2))
    return mk


def hood_ridge(m, P, head, trim=None):
    """Dorsal ridge: overlapping silver plates from brow to nape, each tucked a little lower."""
    P.add(head, (-1.5, -9.3, -4.7), (3, 1, 3), plate(SILVER))
    P.add(head, (-1.5, -9.15, -1.7), (3, 1, 3), plate(SILVER, top=2))
    P.add(head, (-1, -9.0, 1.3), (2, 1, 3), plate(SILVER, top=2))
    P.add(head, (-1, -8.8, 4.3), (2, 3, 1), plate(SILVER, top=2, trim=trim))


def antenna_px(tip):
    def fn(c, x, y):
        if c.face == "top":
            return SILVER[3] if tip else SILVER[1]
        if c.face == "bottom":
            return SILVER[0]
        if tip and y == 0:
            return SILVER[3]
        seg = (y // 2) % 2
        if c.face in ("back", "left"):
            return SILVER[1] if seg else SILVER[0]
        return SILVER[2] if seg == 0 else SILVER[1]
    return fn


def antennae(m, P, head, lengths, splay=0.32):
    """Two segmented feelers from the brow corners, sweeping up and back."""
    bends = (-0.42, -0.55, -0.5)
    for side, sx in (("r", -1), ("l", 1)):
        parent, pivot = head, (3.4 * sx, -7.4, -5.0)
        for i, ln in enumerate(lengths):
            rot = (bends[i], 0, sx * (splay if i == 0 else 0.1))
            seg = parent.part(f"antenna_{side}_{i + 1}", pivot, rot)
            P.add(seg, (-0.5, -ln, -0.5), (1, ln, 1), faces(painter(antenna_px(i == len(lengths) - 1))))
            parent, pivot = seg, (0, -ln + 0.3, 0)


# -- torso: vest, sash, tail cords -----------------------------------------------------------------
def vest(m, P, body, back=True):
    """Five segment plates on the chest (and back), each upper plate standing a little prouder."""
    for k, (w, y0) in enumerate(zip(VEST_W, VEST_Y)):
        g = 0.06 if w == 8 and k == 1 else 0.03 if w == 8 else 0.0
        top = 2
        P.add(body, (-w / 2, y0, -3 + 0.15 * k), (w, 2, 1), plate(SILVER, top=top), inflate=g)
        if back:
            P.add(body, (-w / 2, y0, 2 - 0.15 * k), (w, 2, 1), plate(SILVER, top=top), inflate=g)


def sash_px(cord):
    """Umber sash with folds; a twisted rank-coloured cord binds its upper edge."""
    def fn(c, x, y):
        if c.face in ("top", "bottom"):
            return UMBER[1]
        u = x + ring_u(c, 9, 5)
        if y == 0:
            return cord[1] if u % 3 else cord[0]
        if y == c.h - 1:
            return UMBER[0]
        return UMBER[1] if u % 3 == 0 else UMBER[2]
    return fn


def buckle_paint(c):
    painter(plate_px(SILVER, edge_lit=False))(c)
    if c.face == "front":
        c.fill(SILVER[2])
        for x in range(c.w):
            c.set(x, 0, SILVER[3])
            c.set(x, c.h - 1, SILVER[0])
        c.set(0, 0, SILVER[4])
        c.set(c.w // 2, 1, SILVER[0])           # the mark: body over three tail strands
        c.set(c.w // 2, 2, SILVER[0])


def sash(m, P, body, cord):
    P.add(body, (-4.5, 10.5, -2.5), (9, 2, 5), faces(painter(sash_px(cord))))
    P.add(body, (-1.5, 10, -3.25), (3, 3, 1), faces(buckle_paint))
    P.add(body, (-1.5, 10.25, 2.25), (3, 2, 1), plate(SILVER, top=3))     # knot plate for the cords


def tail_cords(m, P, body, ramp):
    """Three cords from the back of the belt: the silverfish's three tail strands."""
    for name, x, ln1, ln2, splay in (("tail_r", -1.2, 5, 4, 0.16), ("tail_m", 0, 6, 5, 0), ("tail_l", 1.2, 5, 4, -0.16)):
        t1 = body.part(name + "_1", (x, 11.5, 2.9), (0.2, 0, splay))
        P.add(t1, (-0.5, 0, -0.5), (1, ln1, 1), faces(painter(cord_px(ramp))))
        t2 = t1.part(name + "_2", (0, ln1 - 0.2, 0), (0.14, 0, splay * 0.5))
        P.add(t2, (-0.5, 0, -0.5), (1, ln2, 1), faces(painter(cord_px(ramp, tip=2))))


# -- limbs -----------------------------------------------------------------------------------------
def arm_center(side):
    return -1 if side == "right" else 1


def pauldrons(m, P, arm, side, ramp=SILVER, trim=None):
    """Small two-plate pauldron: a cap over the shoulder and a narrower lame on the outer arm."""
    cx, out = arm_center(side), (-1 if side == "right" else 1)
    x_cap = cx - 2.5 if out < 0 else cx - 1.5
    P.add(arm, (x_cap, -2.75, -2.5), (4, 2, 5), plate(ramp, top=2, trim=trim), inflate=0.12)
    x_lame = cx - 2.5 if out < 0 else cx - 0.5
    P.add(arm, (x_lame, -0.8, -2.5), (3, 2, 5), plate(ramp, top=2, trim=trim), inflate=-0.08)


def bracers(m, P, arm, side, ramp=SILVER):
    """Three slim overlapping bands on the forearm, each upper band standing a little prouder."""
    cx = arm_center(side)
    for j, g in enumerate((-0.25, -0.3, -0.35)):
        P.add(arm, (cx - 2.5, 4.0 + 1.25 * j, -2.5), (5, 2, 5), plate(ramp, top=2 - (j > 1)), inflate=g)


def leg_gear(m, P, leg, side):
    g = 0.25 if side == "right" else 0.2      # boots overlap between the legs: keep their faces apart
    P.add(leg, (-1.5, 3.5, -2.5), (3, 2, 1), plate(SILVER, top=2), inflate=0.05)    # knee plate
    P.add(leg, (-2, 9, -2), (4, 3, 4), faces(painter(boot_px)), inflate=g)          # soft boot


# -- the whole uniform -----------------------------------------------------------------------------
def dress(m, rank, antenna=(3, 3), back_plates=True, cords=True, shoulder=True, ridge_trim=None,
          packing=None):
    """Build body + shared costume. Returns (parts, pack); call pack.apply() then m.save()."""
    P = Pack(m, packing)
    parts = base_body(m, P)
    parts["mask"] = mask(m, P, parts["head"])
    hood_ridge(m, P, parts["head"], ridge_trim)
    antennae(m, P, parts["head"], antenna)
    vest(m, P, parts["body"], back=back_plates)
    sash(m, P, parts["body"], BRONZE[1:] if rank == "warden" else CORDS[rank])
    if cords:
        tail_cords(m, P, parts["body"], CORDS[rank])
    for side in ("right", "left"):
        arm = parts[side + "_arm"]
        if shoulder:
            pauldrons(m, P, arm, side)
        bracers(m, P, arm, side)
        leg_gear(m, P, parts[side + "_leg"], side)
    return parts, P
