"""Shared palette and painters for the Plata Clan costume models.

Every colour here comes straight from the five-shade ramps in DESIGN.md section 7; painters pick ramp
INDICES (0 = deep, last = highlight) so shading stays on-palette. Light comes from the top-left: the
top row and left column of a face are lifted one step, the bottom row and right column dropped one.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))      # tools/ for mobkit
sys.path.insert(0, HERE)

from mobkit import hexc  # noqa: E402,F401

SIDES = ("front", "back", "left", "right")

SILVER = ["#5E646C", "#858C95", "#A9B0B8", "#C9CFD5", "#E6EAEE"]
SLATE = ["#24282D", "#34393F", "#464C54", "#5C636B", "#6E767F"]
UMBER = ["#1F1C19", "#2A2622", "#3A3530", "#4B453F"]
GLINT = ["#8CC7D4", "#BFE6EE"]
BRONZE = ["#5F4724", "#8A6A38", "#B08A4E"]
CORDS = {
    "scuttler": ["#45566B", "#5A6E86"],      # slate blue
    "shardcaster": ["#3B6B68", "#4F8A86"],   # muted teal
    "warden": ["#45566B", "#5A6E86"],
}
INK = UMBER[0]          # eye slits, engraved lines


def at(ramp, i):
    """Ramp colour at index i, clamped."""
    return ramp[max(0, min(len(ramp) - 1, int(i)))]


def painter(fn):
    """fn(c, x, y) -> colour or None, run over every pixel of a face."""
    def run(c):
        for y in range(c.h):
            for x in range(c.w):
                col = fn(c, x, y)
                if col is not None:
                    c.set(x, y, col)
    return run


def ring_u(c, w, d):
    """Column offset of a side face when walking round a w x d box (front, left, back, right)."""
    return {"front": 0, "left": w, "back": w + d, "right": w + d + w}.get(c.face, 0)


def lit(c, x, y, i, k=1):
    """Top-left lighting on side faces: left column +k, right column -k (faces 3+ wide)."""
    if c.face in SIDES and c.w >= 3:
        if x == 0:
            i += k
        elif x == c.w - 1:
            i -= k
    return i


# -- plates ----------------------------------------------------------------------------------------
def band_index(y, h, top=3, low=0):
    """Light upper edge, dark lower edge, a soft fall in between."""
    if h == 1:
        return max(low, top - 1)
    if y == 0:
        return top
    if y == h - 1:
        return low if h >= 3 else low + 1
    if h <= 3:
        return top - 1
    return top - 1 if y < h / 2 else max(low + 1, top - 2)


def plate_px(ramp=SILVER, top=3, low=0, trim=None, edge_lit=True, glint=True):
    """A metal plate: banded side faces, lit top, deep underside. trim=ramp paints the lower edge.
    Ramp index 4 (the near-white highlight) appears only as a one-pixel glint at a lit corner."""

    def fn(c, x, y):
        if c.face == "top":
            i = top - 1
            if y == c.h - 1 or x == 0:
                i = top
            return at(ramp, i)
        if c.face == "bottom":
            return at(trim, 0) if trim else at(ramp, low)
        if trim and y == c.h - 1 and c.h >= 2:
            return at(trim, 2 if x == 0 else 0 if x == c.w - 1 and c.w > 2 else 1)
        i = band_index(y, c.h, top, low)
        if glint and y == 0 and x == 0 and c.face in ("front", "left", "right") and c.w >= 2:
            i += 1
        if edge_lit and 0 < y < c.h - 1:
            i = lit(c, x, y, i)
        return at(ramp, i)
    return fn


def plate(ramp=SILVER, **kw):
    return painter(plate_px(ramp, **kw))


# -- cloth -----------------------------------------------------------------------------------------
def cloth_px(base=2, fold=4, w=8, d=4, ramp=SLATE, hem=True):
    """Slate cloth: soft vertical folds every `fold` columns, lit top row, darker hem."""
    def fn(c, x, y):
        if c.face == "top":
            return at(ramp, base + 1)
        if c.face == "bottom":
            return at(ramp, base - 1)
        u = x + ring_u(c, w, d)
        i = base
        if u % fold == fold - 1:
            i -= 1
        elif u % fold == 0 and c.face != "back":
            i += 1
        if y == 0:
            i = max(i, base + 1)
        if hem and y == c.h - 1:
            i = base - 1
        return at(ramp, i)
    return fn


def solid(col):
    return lambda c: c.fill(col)


def wraps_px(ramp=UMBER, base=2):
    """Bound cloth strips: diagonal wraps with a dark seam every third row."""
    def fn(c, x, y):
        if c.face == "top":
            return at(ramp, base)
        if c.face == "bottom":
            return at(ramp, 0)
        u = x + y
        if (u + (1 if c.face in ("left", "back") else 0)) % 3 == 0:
            return at(ramp, base - 1)
        return at(ramp, base + 1 if y % 3 == 1 else base)
    return fn


def cord_px(ramp, tip=0):
    """A rank-coloured cord; the last `tip` rows are a silver weight."""
    def fn(c, x, y):
        if tip and y >= c.h - tip:
            if c.face == "bottom":
                return SILVER[1]
            return SILVER[4 if y == c.h - tip else 2] if c.face != "back" else SILVER[2]
        if c.face in ("top", "bottom"):
            return ramp[0]
        return ramp[1] if (y % 2 == 0) ^ (c.face in ("back", "left")) else ramp[0]
    return fn


def glyph(c, cx, cy, col, feelers=True):
    """The clan mark, 3 wide x 3 tall: two feelers, a body, three tail strands below it."""
    if feelers:
        c.set(cx - 1, cy, col)
        c.set(cx + 1, cy, col)
    c.set(cx, cy + 1, col)
    for dx in (-1, 0, 1):
        c.set(cx + dx, cy + 2, col)
