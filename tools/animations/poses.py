"""Poses and helpers shared by the Plata clips (plata_moves.py) and the Molt (molt.py). No clips here.

Kit conventions apply (see kit.py): degrees on top of the live pose, waist bends at the hips, root pivots at the
feet, *_pos in model pixels with y- up and z- forward.
"""
from __future__ import annotations

import math

FLAT = (0, 0, 0)
LEG_X = 1.9          # leg pivots sit 1.9 px either side of the hips' centre


# -- feet on the ground ----------------------------------------------------------------------------
def _rot(x, y, z):
    """Degrees -> Rz @ Ry @ Rx as nested lists (ModelPart order)."""
    x, y, z = (math.radians(a) for a in (x, y, z))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return [[cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx],
            [sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx],
            [-sy, cy * sx, cy * cx]]


def _apply(m, v):
    return [sum(m[i][j] * v[j] for j in range(3)) for i in range(3)]


def soles(pose):
    """Height above the ground (px) of the centre of each sole, right then left, as ResidentPoser poses it."""
    rx, ry, rz = pose.get("root", FLAT)
    turn = _rot(rx, ry, rz)
    px, py, pz = pose.get("root_pos", FLAT)
    out = []
    for leg, side in (("rl", -1), ("ll", 1)):
        pitch, yaw, roll = pose.get(leg, FLAT)
        if side > 0:
            yaw, roll = -yaw, -roll
        ox, oy, oz = pose.get(leg + "_pos", FLAT)
        foot = _apply(_rot(pitch, yaw, roll), (0, 12, 0))
        point = (side * (LEG_X + ox) + foot[0], 12 + oy + foot[1] - 24, oz + foot[2])
        out.append(24 - (_apply(turn, point)[1] + 24 + py))
    return out


def plant(pose, sole=0.0):
    """The pose with root_pos y set so the lower sole rests on the ground (sole > 0 lifts it off)."""
    x, y, z = pose.get("root_pos", FLAT)
    return dict(pose, root_pos=(x, round(y + min(soles(pose)) - sole, 3), z))


# The fighting stance: low and coiled, weight forward, left foot leading, both knife hands forward-low.
# Every fighter clip starts and ends here (except where the contract says otherwise), so blends are clean.
STANCE = plant(dict(root_pos=(0, 2, 0), waist=(18, 6, 0), head=(-14, -6, 0),
                    ra=(-42, -18, 6), la=(-60, -10, 4), rl=(34, 0, 6), ll=(-40, 0, 6)))

# The silverfish curl: the Molt ends in it and the unfurl starts from it, so the swap under the dust is seamless.
CURL = plant(dict(root_pos=(0, 7, 0), waist=(46, 0, 0), head=(6, 0, 0),
                  ra=(-90, -50, 0), la=(-78, -44, 0), rl=(-64, 0, 8), ll=(-64, 0, 8)))


def merge(*poses, **extra):
    out = {}
    for p in poses:
        out.update(p)
    out.update(extra)
    return out


def add(pose, **deltas):
    """The pose with these channel deltas added component-wise (channels missing from the pose start at rest)."""
    out = dict(pose)
    for name, delta in deltas.items():
        base = out.get(name, (0,) * len(delta))
        out[name] = tuple(round(a + b, 3) for a, b in zip(base, delta))
    return out


def spun(pose, angle, axis="pitch"):
    """The pose turned rigidly about the hips: waist and both legs share the turn, so a flip or roll pivots at the
    middle of the body instead of the feet (root rotation would swing the head through the ground).

    pitch: + tips forward, - goes over backwards. roll: + rolls toward the left shoulder. Legs keep yaw 0 for pitch.
    """
    p = dict(pose)
    w, rl, ll = p.get("waist", FLAT), p.get("rl", FLAT), p.get("ll", FLAT)
    if axis == "pitch":
        p["waist"] = (w[0] + angle, w[1], w[2])
        p["rl"] = (rl[0] + angle, rl[1], rl[2])
        p["ll"] = (ll[0] + angle, ll[1], ll[2])
        return p
    a = math.radians(angle)
    p["waist"] = (w[0], w[1], w[2] + angle)
    p["rl"] = (rl[0], rl[1], rl[2] + angle)
    p["ll"] = (ll[0], ll[1], ll[2] - angle)
    # The legs pivot 1.9 px off the hips' centre: carry them round the centre with the body.
    out, lift = -LEG_X * (1 - math.cos(a)), LEG_X * math.sin(a)
    rp, lp = p.get("rl_pos", FLAT), p.get("ll_pos", FLAT)
    p["rl_pos"] = (rp[0] + out, rp[1] - lift, rp[2])
    p["ll_pos"] = (lp[0] + out, lp[1] + lift, lp[2])
    return p


def lerp(a, b, f):
    """A pose part-way (f 0..1) from a to b, channel by channel (channels missing from one count as rest)."""
    out = {}
    for name in set(a) | set(b):
        va, vb = a.get(name), b.get(name)
        if va is None:
            va = (0,) * len(vb)
        if vb is None:
            vb = (0,) * len(va)
        out[name] = tuple(round(x + (y - x) * f, 3) for x, y in zip(va, vb))
    return out


def tween(c, t, a, b, f=0.5, sole=0.0):
    """Key a planted in-between of two poses, so a foot that should stay down does not dip on the way."""
    c.key(t, **plant(lerp(a, b, f), sole))
    return c


def full(c, t, pose):
    """Key every channel of a pose at t."""
    c.key(t, **pose)
    return c


def snap(c, t, before, after):
    """Swap a turned pose (e.g. spun 360) for its unturned twin in 0.1 ms. Both read the same on screen; the
    clip then ends on small numbers, so blending back to the stance never unwinds the turn."""
    after = dict(after)
    for name, value in before.items():
        after.setdefault(name, (0,) * len(value) if isinstance(value, tuple) else 0)
    c.key(t, **before)
    c.key(t + 0.0001, **after)
    return c
