"""Plata Shardcaster: the clan's skirmisher. Long three-segment feelers, muted-teal tail cords, a mantle
of overlapping silver scale rows over the shoulders, a bandolier of shards across the chest and a fan
of three sharpened shards (thrown scales) held in the right hand."""
import common  # noqa: F401  (path setup)
from common import CORDS, GLINT, SILVER, UMBER, painter, ring_u
from costume import dress
from mobkit import Model, faces

TEAL = CORDS["shardcaster"]


def scales_px(w, d):
    """Rows of overlapping scales: each scale a lit crown over a shadowed lower edge, rows offset."""
    def fn(c, x, y):
        if c.face == "top":
            return SILVER[2] if (x + y) % 2 else SILVER[1]
        if c.face == "bottom":
            return TEAL[0]                       # teal lining under the mantle
        u = x + ring_u(c, w, d)
        row = y // 2
        lit = (u + row) % 2 == 0
        if y % 2 == 0:
            return SILVER[3] if lit else SILVER[2]
        if y == c.h - 1:
            return TEAL[1] if lit else TEAL[0]   # the hem shows the teal lining
        return SILVER[1] if lit else SILVER[0]
    return fn


def mantle(m, P, parts):
    """A collar of scales round the neck, a scale drape over the upper back, and scale tiers over
    each shoulder. The shoulder tiers ride on the arms so they never cut through a raised arm."""
    body = parts["body"]
    P.add(body, (-5, -0.75, -3), (10, 2, 6), faces(painter(scales_px(10, 6))), inflate=0.1)
    P.add(body, (-4.5, 1.0, 1.5), (9, 3, 2), faces(painter(scales_px(9, 2))), inflate=0.15)
    for side, out in (("right", -1), ("left", 1)):
        arm, cx = parts[side + "_arm"], (-1 if side == "right" else 1)
        x0 = cx - 3 if out < 0 else cx - 2
        P.add(arm, (x0, -2.6, -3), (5, 2, 6), faces(painter(scales_px(5, 6))), inflate=0.15)
        x1 = cx - 2.75 if out < 0 else cx - 1.25
        P.add(arm, (x1, -0.9, -2.75), (4, 2, 5), faces(painter(scales_px(4, 5))), inflate=0.1)


def strap_px(c, x, y):
    if c.face in ("top", "bottom"):
        return UMBER[1]
    if c.face in ("left", "right"):
        return UMBER[1]
    if x == 0:
        return UMBER[3]
    return UMBER[2] if (y % 3) else UMBER[1]   # stitched leather


def shard_px(glint):
    """A sharpened silver scale: bright edge, darker back; a cold glint only at the point if `glint`."""
    def fn(c, x, y):
        if c.face == "top":
            return SILVER[2]
        if c.face == "bottom":
            return GLINT[1] if glint else SILVER[3]
        if y == c.h - 1:
            return GLINT[1] if glint and c.face == "front" else SILVER[4] if c.face in ("front", "left") else SILVER[2]
        if c.face in ("front", "left"):
            return SILVER[3] if y < c.h - 2 or not glint else SILVER[4]
        return SILVER[1]
    return fn


def bandolier(m, P, body):
    b = body.part("bandolier", (0, 5.5, -3.55), (0, 0, -0.72))
    P.add(b, (-1, -7, -0.5), (2, 14, 1), faces(painter(strap_px)))
    for y in (-5.5, -2.5, 0.5, 3.5):                       # shards slotted into the strap
        P.add(b, (-0.5, y, -1.3), (1, 2, 1), faces(painter(shard_px(False))))
    return b


def shard_fan(m, P, arm):
    """Three shards fanned between the fingers like a hand of cards, points down and forward."""
    fan = arm.part("shard_fan", (-1, 9.4, -1.2), (-0.55, 0, 0))
    P.add(fan, (-0.5, 0, -0.5), (1, 5, 1), faces(painter(shard_px(True))))
    for name, rz in (("shard_fan_r", 0.62), ("shard_fan_l", -0.62)):
        side = fan.part(name, (0, 0.4, 0.1), (0, 0, rz))
        P.add(side, (-0.5, 0, -0.5), (1, 4, 1), faces(painter(shard_px(True))))
    return fan


def build():
    m = Model("plata_shardcaster", 128, 128, seed=5)
    parts, P = dress(m, "shardcaster", antenna=(4, 3, 3), shoulder=False)
    mantle(m, P, parts)
    bandolier(m, P, parts["body"])
    shard_fan(m, P, parts["right_arm"])
    P.apply()
    m.save()
    return m


if __name__ == "__main__":
    build()
