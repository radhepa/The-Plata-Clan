"""Plata Scuttler: the clan's footsoldier. Short two-segment feelers, slate-blue tail cords and twin
fang knives held reverse-grip, the blades running back and down from each fist."""
import common  # noqa: F401  (path setup)
from common import SILVER, UMBER, painter
from costume import dress
from mobkit import Model, faces


def blade_px(c, x, y):
    if c.face == "top":
        return SILVER[3]
    if c.face == "bottom":
        return SILVER[1]
    if c.face in ("left", "right"):              # the flat: bright edge on top, a glint at the tip
        tip = c.w - 1 if c.face == "left" else 0
        if y == 0:
            return SILVER[4] if x == tip else SILVER[3]
        return SILVER[2] if x == tip else SILVER[1]
    return SILVER[2] if y == 0 else SILVER[1]


def grip_px(c, x, y):
    if c.face in ("top", "bottom"):
        return UMBER[2]
    return UMBER[3] if (x + y) % 2 == 0 else UMBER[1]


def fang(m, P, arm, side):
    cx = -1 if side == "right" else 1
    f = arm.part("fang_" + side[0], (cx, 9.5, 0), (-0.38, 0, 0))
    P.add(f, (-0.5, -0.5, -2.5), (1, 1, 4), faces(painter(grip_px)))        # grip through the fist
    P.add(f, (-0.5, -1, 1.5), (1, 2, 1), faces(painter(grip_px)), inflate=0.1)   # small guard
    P.add(f, (-0.5, -1, 2.5), (1, 2, 5), faces(painter(blade_px)))        # narrow straight blade
    return f


def build():
    m = Model("plata_scuttler", 128, 128, seed=3)
    parts, P = dress(m, "scuttler", antenna=(3, 3))
    for side in ("right", "left"):
        fang(m, P, parts[side + "_arm"], side)
    P.apply()
    m.save()
    return m


if __name__ == "__main__":
    build()
