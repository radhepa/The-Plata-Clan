"""Plata Molt Warden: the elder enforcer. Long feelers, heavy three-tier pauldrons trimmed in tarnished
bronze, a ridged carapace down the back with a long three-tailed cape falling from under it, and the
Silvertail: a whip-flail of chained silver segments shrinking to a three-strand tip."""
import math

import common  # noqa: F401  (path setup)
from common import BRONZE, SILVER, SLATE, UMBER, painter, plate, plate_px, ring_u
from costume import dress
from mobkit import Model, faces

# Silvertail: (width, length, rest bend about x relative to the previous segment)
WHIP = [(3, 2, 0.04), (3, 2, 0.06), (3, 2, 0.12), (2, 2, 0.32), (2, 2, 0.42),
        (2, 2, 0.34), (1, 2, 0.18), (1, 3, 0.16)]
HANDLE_BEND = 0.08


def heavy_pauldrons(m, P, arm, side):
    """Three flaring tiers, each lower tier further out and down, bronze along every lower edge."""
    cx, out = (-1 if side == "right" else 1), (-1 if side == "right" else 1)
    for k, (w, y, g) in enumerate(((5, -3.4, 0.0), (5, -1.9, 0.2), (4, -0.4, 0.4))):
        outer = cx + out * (2.6 + 0.5 * k)
        x0 = outer if out < 0 else outer - w
        P.add(arm, (x0, y, -3), (w, 2, 6), plate(SILVER, top=3 - (k > 0), trim=BRONZE), inflate=g)


def carapace_paint(c):
    painter(plate_px(SILVER, top=2, trim=BRONZE))(c)
    if c.face == "back":                        # ribbing across each shell segment
        for x in range(1, c.w - 1, 2):
            c.set(x, 1, SILVER[1])


def carapace(m, P, body):
    """Four shell segments down the back, tapering, each upper one standing prouder, each with a keel."""
    for k, w in enumerate((9, 8, 7, 6)):
        y0, z0 = 0.5 + 2.45 * k, 2.4 - 0.3 * k
        P.add(body, (-w / 2, y0, z0), (w, 3, 2), faces(carapace_paint))
        P.add(body, (-0.5, y0 + 0.4, z0 + 2), (1, 2, 1), plate(SILVER, top=3, trim=BRONZE))


def cape_px(w, d, hem=True):
    def fn(c, x, y):
        if c.face == "front":
            return SLATE[0]                     # lining against the body
        if c.face in ("top", "bottom"):
            return BRONZE[0] if c.face == "bottom" and hem else SLATE[1]
        if hem and y == c.h - 1:
            return BRONZE[2] if x == 0 else BRONZE[1]                       # bronze hem
        if c.face == "back" and x in (0, c.w - 1):
            return SLATE[1]
        u = x + ring_u(c, w, d)
        return SLATE[1] if u % 3 == 0 else SLATE[2]
    return fn


# the clan mark seen from above, head up: feelers, head, a segmented teardrop body; its three tail
# strands are the cape's own three tails below it
MARK = ["#......#",
        ".#....#.",
        "...hh...",
        "..abba..",
        "..baab..",
        "...ab...",
        "...ba..."]


def cape_upper(c):
    painter(cape_px(10, 1, hem=False))(c)
    if c.face == "back":
        ox = (c.w - 8) // 2
        cols = {"#": SILVER[1], "h": SILVER[3], "a": SILVER[3], "b": SILVER[2]}
        for y, row in enumerate(MARK):
            for x, ch in enumerate(row):
                if ch != ".":
                    c.set(ox + x, y, cols[ch] if y % 2 or ch in "#h" else (SILVER[2] if ch == "a" else SILVER[1]))


def cape(m, P, body):
    c1 = body.part("cape_1", (0, 9.8, 3.1), (0.1, 0, 0))
    P.add(c1, (-5, 0, -0.5), (10, 7, 1), faces(cape_upper))
    c2 = c1.part("cape_2", (0, 6.8, 0), (0.05, 0, 0))
    for x0, w, ln in ((-5, 2, 6), (-1.5, 3, 7), (3, 2, 6)):   # three tails, the middle one longest
        P.add(c2, (x0, 0, -0.5), (w, ln, 1), faces(painter(cape_px(w, 1))))
    return c1, c2


def grip_px(c, x, y):
    if c.face in ("top", "bottom"):
        return UMBER[2]
    return UMBER[3] if (y + x) % 3 == 0 else UMBER[1]


def whip_seg_px(c, x, y):
    if c.face == "top":
        return SILVER[2]
    if c.face == "bottom":
        return SILVER[0]
    if y == 0:
        return SILVER[3]
    if y == c.h - 1:
        return SILVER[0]
    return SILVER[2] if c.face in ("front", "left") else SILVER[1]


def strand_px(c, x, y):
    if c.face in ("top", "bottom"):
        return SILVER[1]
    if y == c.h - 1:
        return SILVER[4]
    return SILVER[2] if y % 2 == 0 else SILVER[1]


def silvertail(m, P, arm):
    """whip_0 is the handle in the fist; whip_1..8 hang from it, each a child of the last."""
    h = arm.part("whip_0", (-1, 9.5, 0), (HANDLE_BEND, 0, 0))
    P.add(h, (-0.5, -1.5, -0.5), (1, 4, 1), faces(painter(grip_px)))               # grip
    P.add(h, (-1, -2, -1), (2, 1, 2), plate(BRONZE, top=2), inflate=-0.05)         # pommel above the fist
    P.add(h, (-1, 2.5, -1), (2, 1, 2), plate(BRONZE, top=2), inflate=0.05)         # bronze ferrule
    parent, pivot = h, (0, 3.3, 0)
    for i, (w, ln, bend) in enumerate(WHIP, start=1):
        seg = parent.part(f"whip_{i}", pivot, (bend, 0, 0))
        if i < len(WHIP):
            P.add(seg, (-w / 2, 0, -w / 2), (w, ln, w), faces(painter(whip_seg_px)))
        else:                                   # the three-strand tip
            P.add(seg, (-0.5, 0, -0.5), (1, ln, 1), faces(painter(strand_px)))
            for name, rz in (("whip_tip_r", 0.45), ("whip_tip_l", -0.45)):
                s = seg.part(name, (0, 0, 0), (0.1, 0, rz))
                P.add(s, (-0.5, 0, -0.5), (1, ln - 1, 1), faces(painter(strand_px)))
        parent, pivot = seg, (0, ln - 0.3, 0)
    return h


def whip_rest_points():
    """Forward kinematics of the resting whip (x bends only): absolute (y, z) of each joint."""
    y, z, a = 2 + 9.5, 0.0, HANDLE_BEND
    pts = [(y, z)]
    steps = [(3.3, 0)] + [(ln - 0.3, bend) for (_, ln, bend) in WHIP]
    a_next = [b for (_, _, b) in WHIP] + [0]
    for i, (ln, _) in enumerate(steps):
        y, z = y + ln * math.cos(a), z + ln * math.sin(a)
        pts.append((round(y, 2), round(z, 2)))
        a += a_next[i]
    return pts


def build():
    m = Model("plata_warden", 128, 128, seed=9)
    parts, P = dress(m, "warden", antenna=(4, 3, 3), back_plates=False, cords=False, shoulder=False,
                     ridge_trim=BRONZE)
    for side in ("right", "left"):
        heavy_pauldrons(m, P, parts[side + "_arm"], side)
    carapace(m, P, parts["body"])
    cape(m, P, parts["body"])
    silvertail(m, P, parts["right_arm"])
    P.apply()
    m.save()
    return m


if __name__ == "__main__":
    print("whip joints (y, z):", whip_rest_points())
    build()
