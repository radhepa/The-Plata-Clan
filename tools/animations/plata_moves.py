"""The Plata fighter's moves (DESIGN.md sections 4 and 9), played by id by PlataPoser.

They fight low and scuttling like silverfish: everything starts from and returns to the coiled STANCE (poses.py),
so the poser can cross-fade any move over the looping stance. The game times damage and movement to the beats in
HITS (the cut, the stab, the release, the dive); BEATS are the preview frames (clip_preview.py).

Flips and rolls turn the waist and both legs together (poses.spun), so they pivot about the hips instead of the
feet, and end with a 0.1 ms snap from the turned pose to its unturned twin (poses.snap): the numbers are small again
before the clip blends out, so a cross-fade can never unwind a full turn. The spinning cut turns the root (which
pivots on the vertical axis through the feet, the body's own axis) and snaps the same way.
"""
from kit import clip
from poses import CURL, STANCE, add, full, merge, plant, snap, spun, tween

# Where a clip is meant to be in the ground (the preview checks skip it there).
UNDERGROUND = {"burrow_dive": (0.46, 0.6), "emerge": (0.0, 0.09)}
HITS = {"unfurl": 0.7, "ready": 0.3, "flurry_1": 0.2, "flurry_2": 0.2, "flurry_3": 0.2, "flurry_4": 0.2,
        "lunge": 0.5, "backflip": 0.52, "sideroll": 0.46, "burrow_dive": 0.5, "emerge": 0.25, "throw_fan": 0.4,
        "kick": 0.25, "lash": 0.85, "slam": 0.7, "call": 0.5, "stagger": 0.08, "taunt": 0.8, "defeat": 1.1}
BEATS = {
    "stance": (0.0, 0.5, 1.0, 1.5, 1.75, 2.0),
    "unfurl": (0.0, 0.2, 0.3, 0.4, 0.7, 1.1),
    "ready": (0.0, 0.1, 0.2, 0.3, 0.35, 0.4),
    "flurry_1": (0.0, 0.1, 0.15, 0.2, 0.28, 0.45),
    "flurry_2": (0.0, 0.1, 0.15, 0.2, 0.28, 0.45),
    "flurry_3": (0.0, 0.07, 0.13, 0.2, 0.27, 0.45),
    "flurry_4": (0.0, 0.1, 0.15, 0.2, 0.3, 0.5),
    "lunge": (0.0, 0.25, 0.4, 0.5, 0.65, 0.9),
    "backflip": (0.0, 0.1, 0.22, 0.32, 0.52, 0.7),
    "sideroll": (0.0, 0.08, 0.18, 0.28, 0.46, 0.6),
    "burrow_dive": (0.0, 0.15, 0.3, 0.45, 0.5, 0.55),
    "emerge": (0.0, 0.1, 0.25, 0.35, 0.55, 0.9),
    "throw_fan": (0.0, 0.2, 0.35, 0.4, 0.5, 0.8),
    "kick": (0.0, 0.12, 0.2, 0.25, 0.35, 0.6),
    "lash": (0.0, 0.35, 0.7, 0.85, 0.95, 1.3),
    "slam": (0.0, 0.35, 0.6, 0.7, 0.9, 1.4),
    "call": (0.0, 0.3, 0.5, 0.75, 0.95, 1.2),
    "stagger": (0.0, 0.08, 0.25, 0.45, 0.7, 1.0),
    "taunt": (0.0, 0.3, 0.55, 0.8, 1.2, 1.6),
    "defeat": (0.0, 0.2, 0.5, 0.8, 1.1, 1.4),
}


def move(id, name, length, blend=(0.06, 0.08)):
    """A fighter clip: always the 'idle' trigger (PlataPoser picks clips by id), never mirrored, and it drives the
    arms even when they hold something."""
    return clip(id, name, length=length, blend=blend, mirror="never", items="override")


def bookend(c, length):
    """Key the stance at both ends so every channel starts and ends exactly in it."""
    full(c, 0, STANCE)
    full(c, length, STANCE)


# Shared fighting shapes (absolute poses; plant() rests the lower sole on the ground).
KNEEL = dict(rl=(58, 0, 6), ll=(-62, 0, 8))                                  # right knee down, left foot ahead
DEEP = plant(merge(STANCE, waist=(36, 4, 0), head=(-30, -4, 0), ra=(-46, -10, 22), la=(-62, -6, 18),
                   rl=(56, 0, 10), ll=(-64, 0, 8)))                          # landing crouch
TUCK = dict(waist=(40, 0, 0), head=(16, 0, 0), ra=(-62, 0, 4), la=(-62, 0, 4), rl=(-64, 0, 0), ll=(-64, 0, 0))


# -- stance ----------------------------------------------------------------------------------------
@move("stance", "Coiled fighting stance (loops)", 2.0, blend=(0, 0))
def _(c):
    # Two breaths per loop; the weight drifts onto the back foot and returns. Head steady, eyes on the foe.
    breath_in = dict(root_pos=(0, -.4, 0), waist=(-2, 0, 0), head=(2, 0, 0), ra=(-3, 2, 0), la=(-3, 2, 0))
    drift = dict(root_pos=(-.6, .2, .3), waist=(1, -3, 1.5), head=(-1, 3, -1.5), ra=(2, -2, 0), la=(-2, 0, 0))
    full(c, 0, STANCE)
    full(c, 0.5, add(STANCE, **breath_in))
    full(c, 1.0, add(STANCE, **drift))
    full(c, 1.5, add(add(STANCE, **breath_in), root_pos=(-.3, 0, .1)))
    full(c, 2.0, STANCE)


# -- unfurl: the fighter springs out of the Molt's dust ---------------------------------------------
@move("unfurl", "Springs out of the dust in a backflip", 1.1, blend=(0, 0.1))
def _(c):
    land = plant(dict(waist=(30, 0, 0), head=(-18, 0, 0), ra=(-60, 10, 40), la=(-60, 10, 40), rl=(30, 0, 4),
                      ll=(-38, 0, 4)))
    full(c, 0, CURL)
    full(c, 0.08, add(CURL, root_pos=(0, .3, 0), waist=(5, 0, 0), head=(4, 0, 0)))
    c.key(0.13, ra=(-110, 50, -6), la=(-110, 50, -6))  # the arms fling out round the head, not through it
    # Launch: legs drive, arms whip up, head throws back; then tuck and turn over high above the dust.
    full(c, 0.18, spun(dict(root_pos=(0, -5, 0), waist=(0, 0, 0), head=(-24, 0, 0), ra=(-160, 0, -14),
                            la=(-160, 0, -14), rl=(6, 0, 0), ll=(6, 0, 0)), -30))
    full(c, 0.28, spun(merge(TUCK, root_pos=(0, -10.5, 0)), -110))
    full(c, 0.38, spun(merge(TUCK, root_pos=(0, -12, 0)), -205))
    full(c, 0.48, spun(merge(TUCK, root_pos=(0, -10, 0)), -290))
    full(c, 0.6, spun(merge(land, root_pos=(0, -4, 0), rl=(-22, 0, 0), ll=(-22, 0, 0), waist=(18, 0, 0)), -345))
    snap(c, 0.7, spun(land, -360), land)        # touchdown
    full(c, 0.8, DEEP)
    full(c, 0.92, add(DEEP, root_pos=(0, -1, 0)))
    full(c, 1.1, STANCE)


# -- the flurry ------------------------------------------------------------------------------------
@move("ready", "Coils back before a flurry", 0.4)
def _(c):
    coil = plant(merge(STANCE, root_pos=(0, 0, 1.5), waist=(26, -14, 0), head=(-22, 12, 0), ra=(24, 10, 22),
                       la=(14, 8, 26), rl=(52, 0, 8), ll=(-58, 0, 8)))
    bookend(c, 0.4)
    full(c, 0.12, coil)
    full(c, 0.3, add(coil, root_pos=(0, .3, .3), waist=(1, -2, 0), ra=(6, 2, 2), la=(4, 2, 2)))


def cut(c, length, windup, peak, hit, through):
    """Wind up 0-0.15 (peaking at 0.15), the cut lands at 0.2, follow through, back to the stance by the end."""
    bookend(c, length)
    full(c, 0.1, plant(windup))
    full(c, 0.15, plant(peak))
    full(c, 0.2, plant(hit))
    full(c, 0.28, plant(through))


LUNGE_LEGS = dict(rl=(44, 0, 6), ll=(-48, 0, 6))    # the stance stretched as the cut lands


@move("flurry_1", "Right forehand cut", 0.45)
def _(c):
    windup = merge(STANCE, root_pos=(0, 0, .5), waist=(20, 22, 0), head=(-16, -14, 0), ra=(-64, 70, 40),
                   la=(-50, -30, 6))
    hit = merge(windup, LUNGE_LEGS, root_pos=(0, 0, -1.5), waist=(24, -24, 0), head=(-18, 12, 0),
                ra=(-88, -40, 8), la=(-30, 10, 22))
    cut(c, 0.45, windup, add(windup, waist=(0, 4, 0), ra=(0, 8, -2)), hit,
        merge(hit, waist=(24, -30, 0), head=(-18, 16, 0), ra=(-84, -50, 4), la=(-26, 12, 24)))


@move("flurry_2", "Left backhand cut", 0.45)
def _(c):
    windup = merge(STANCE, root_pos=(0, 0, .5), waist=(20, 24, 0), head=(-16, -14, 0), la=(-100, -48, 0),
                   ra=(-30, 10, 20))
    hit = merge(windup, LUNGE_LEGS, root_pos=(0, 0, -1.2), waist=(24, -22, 0), head=(-18, 10, 0),
                la=(-84, 60, 20))
    cut(c, 0.45, windup, add(windup, waist=(0, 4, 0), la=(-2, -4, 0)), hit,
        merge(hit, waist=(24, -26, 0), head=(-18, 12, 0), la=(-72, 76, 30)))


@move("flurry_3", "Right spinning cut", 0.45)
def _(c):
    # The root turns a full circle on the feet; the outflung right arm sweeps through the front at 0.2 (yaw 270).
    out = dict(ra=(-90, 76, 12), la=(-40, -20, 20), waist=(22, 0, 0), head=(-16, 0, 0))
    bookend(c, 0.45)
    full(c, 0.07, plant(merge(STANCE, root=(0, -15, 0), waist=(24, -10, 0), ra=(-70, -50, 0), rl=(40, 0, 6),
                              ll=(-46, 0, 6))))
    full(c, 0.13, plant(merge(STANCE, out, root=(0, 120, 0))))
    full(c, 0.2, plant(merge(STANCE, out, root=(0, 270, 0), ra=(-92, 80, 8))))
    full(c, 0.27, plant(merge(STANCE, out, root=(0, 342, 0), ra=(-84, 40, 10))))
    end = plant(merge(STANCE, waist=(24, 4, 0), ra=(-70, 10, 10), la=(-50, -14, 8), rl=(40, 0, 6), ll=(-46, 0, 6)))
    snap(c, 0.32, merge(end, root=(0, 360, 0)), merge(end, root=(0, 0, 0)))


@move("flurry_4", "Both-arm cross cut", 0.5)
def _(c):
    wide = merge(STANCE, waist=(6, 0, 0), head=(-8, 0, 0), ra=(-140, 20, -40), la=(-140, 20, -40))
    hit = merge(STANCE, LUNGE_LEGS, root_pos=(0, 0, -1.5), waist=(30, 0, 0), head=(-24, 0, 0), ra=(-62, -40, 0),
                la=(-50, -36, 0))
    cut(c, 0.5, wide, merge(wide, waist=(2, 0, 0), ra=(-146, 24, -44), la=(-146, 24, -44)), hit,
        merge(hit, waist=(32, 0, 0), head=(-26, 0, 0), ra=(-42, -50, 0), la=(-34, -46, 0)))


@move("lunge", "Dashing lunge with a stab", 0.9)
def _(c):
    load = plant(merge(STANCE, root_pos=(0, 0, 2), waist=(30, -18, 0), head=(-28, 14, 0), ra=(30, 10, 16),
                       la=(-74, -10, 10), rl=(60, 0, 8), ll=(-64, 0, 8)))
    stab = plant(merge(STANCE, root_pos=(0, 0, -3), waist=(40, -6, 0), head=(-32, 6, 0), ra=(-96, -10, 4),
                       la=(24, 6, 34), rl=(70, 0, 6), ll=(-68, 0, 6)))
    bookend(c, 0.9)
    full(c, 0.22, load)
    full(c, 0.3, add(load, root_pos=(0, .3, .3), ra=(4, 0, 0)))
    # 0.3-0.55: the dash (the game moves the body); airborne for a moment, then the stab lands at 0.5.
    full(c, 0.4, merge(STANCE, root_pos=(0, -1, -3), waist=(44, 6, 0), head=(-36, 0, 0), ra=(-130, -8, 6),
                       la=(16, 0, 30), rl=(64, 0, 4), ll=(-46, 0, 6)))
    full(c, 0.5, stab)
    full(c, 0.58, add(stab, ra=(2, 0, 0), root_pos=(0, .2, 0)))
    full(c, 0.72, plant(merge(STANCE, root_pos=(0, 0, -1.5), waist=(30, 0, 0), head=(-24, 0, 0),
                              ra=(-60, -14, 8), la=(-40, -6, 10), rl=(48, 0, 6), ll=(-52, 0, 6))))


# -- dodges ----------------------------------------------------------------------------------------
@move("backflip", "Backflip dodge", 0.7)
def _(c):
    land = plant(dict(waist=(26, 0, 0), head=(-20, 0, 0), ra=(-64, 10, 40), la=(-64, 10, 40), rl=(30, 0, 4),
                      ll=(-38, 0, 4)))
    bookend(c, 0.7)
    full(c, 0.1, plant(merge(STANCE, waist=(30, 0, 0), head=(-24, 0, 0), ra=(30, 0, 14), la=(30, 0, 14),
                             rl=(40, 0, 0), ll=(-56, 0, 0))))
    full(c, 0.2, spun(dict(root_pos=(0, -6, 0), waist=(-4, 0, 0), head=(-26, 0, 0), ra=(-165, 0, -14),
                           la=(-165, 0, -14), rl=(8, 0, 0), ll=(0, 0, 0)), -40))
    full(c, 0.29, spun(merge(TUCK, root_pos=(0, -11.5, 0)), -150))
    full(c, 0.37, spun(merge(TUCK, root_pos=(0, -12, 0)), -245))
    full(c, 0.45, spun(dict(root_pos=(0, -6, 0), waist=(18, 0, 0), head=(-14, 0, 0), ra=(-80, 10, 46),
                            la=(-80, 10, 46), rl=(-24, 0, 0), ll=(-30, 0, 0)), -322))
    snap(c, 0.52, spun(land, -360), land)       # touchdown
    full(c, 0.6, DEEP)


BALL = dict(waist=(44, 0, 0), head=(22, 0, 0), ra=(-56, 0, 6), la=(-56, 0, 6), rl=(-66, 0, 6), ll=(-66, 0, 6))


@move("sideroll", "Sideways tuck-roll dodge (to its left)", 0.6)
def _(c):
    land = plant(merge(STANCE, root_pos=(2, 0, 0), waist=(30, 0, -6), head=(-24, 0, 6), ra=(-50, -10, 24),
                       la=(-56, -6, 30), rl=(48, 0, 14), ll=(-56, 0, 10)))
    opened = dict(waist=(34, 0, 0), head=(-10, 0, 0), ra=(-60, 0, 30), la=(-60, 0, 30), rl=(-40, 0, 10),
                  ll=(-50, 0, 10))
    bookend(c, 0.6)
    full(c, 0.08, plant(merge(STANCE, root_pos=(1.5, 0, 0), waist=(26, 0, 14), head=(-18, 0, -10),
                              rl=(40, 0, 2), ll=(-50, 0, 14), rl_pos=(0, 0, 0), ll_pos=(0, 0, 0))))
    # Keyed every ~50 degrees: between keys the legs' carry-round is only approximate.
    for t, turn, lift in ((0.15, 70, -2), (0.195, 120, -3.5), (0.24, 170, -4.5), (0.285, 218, -4.5),
                          (0.33, 265, -3), (0.37, 300, -1.5)):
        full(c, t, spun(merge(BALL, root_pos=(4 if t > 0.16 else 3, lift, 0)), turn, "roll"))
    opened = plant(spun(merge(opened, root_pos=(3, 0, 0)), 335, "roll"), sole=1)
    full(c, 0.41, opened)
    tween(c, 0.435, opened, spun(land, 360, "roll"), 0.5, sole=0.3)
    snap(c, 0.46, spun(land, 360, "roll"), land)  # lands crouched


# -- burrowing -------------------------------------------------------------------------------------
@move("burrow_dive", "Claws at the ground and dives in head first", 0.6, blend=(0.06, 0))
def _(c):
    crouch = plant(merge(STANCE, waist=(46, 0, 0), head=(-36, 0, 0), ra=(-40, -6, 10), la=(-40, -6, 10),
                         rl=(58, 0, 10), ll=(-66, 0, 10)))
    reach, rake = (-58, -6, 10), (-20, -6, 12)
    full(c, 0, STANCE)
    full(c, 0.08, crouch)
    for i, t in enumerate((0.14, 0.22, 0.3, 0.38)):          # four quick digging strokes, hand over hand
        c.key(t, ra=reach if i % 2 == 0 else rake, la=rake if i % 2 == 0 else reach,
              root_pos=add(crouch, root_pos=(0, -.4 * (i % 2), 0))["root_pos"])
    full(c, 0.45, plant(merge(crouch, root=(0, 0, 0), root_pos=(0, 0, 1), waist=(44, 0, 0), head=(-24, 0, 0),
                              ra=(24, 0, 20), la=(24, 0, 20))))
    # 0.5: head-first into the ground, arms ahead like a diver; hidden from 0.55.
    full(c, 0.5, merge(crouch, root=(60, 0, 0), root_pos=(0, 5, -3), waist=(50, 0, 0), head=(-10, 0, 0),
                       ra=(-170, -6, 6), la=(-170, -6, 6), rl=(40, 0, 6), ll=(-30, 0, 6)))
    full(c, 0.55, merge(crouch, root=(100, 0, 0), root_pos=(0, 12, -6), waist=(40, 0, 0), head=(0, 0, 0),
                        ra=(-176, -6, 6), la=(-176, -6, 6), rl=(10, 0, 0), ll=(10, 0, 0)))
    full(c, 0.6, merge(crouch, root=(110, 0, 0), root_pos=(0, 12, -6), waist=(40, 0, 0), head=(0, 0, 0),
                       ra=(-176, -6, 6), la=(-176, -6, 6), rl=(10, 0, 0), ll=(10, 0, 0)))


@move("emerge", "Bursts out of the ground with a rising cut", 0.9)
def _(c):
    low = merge(STANCE, root_pos=(0, 12, 0), waist=(50, 0, 0), head=(-40, 0, 0), ra=(28, 4, 14), la=(-50, 0, 24),
                rl=(60, 0, 10), ll=(-64, 0, 10))
    hit = merge(STANCE, root_pos=(0, -4, -1), waist=(-10, -18, 0), head=(-12, 12, 0), ra=(-168, 0, -10),
                la=(14, 0, 30), rl=(16, 0, 4), ll=(-18, 0, 4))
    spent = plant(merge(STANCE, waist=(42, 0, 0), head=(6, 0, 0), ra=(-14, 0, 16), la=(-16, 0, 14),
                        rl=(40, 0, 10), ll=(-46, 0, 10)))
    full(c, 0, low)                                     # still half in the ground
    full(c, 0.1, merge(low, root_pos=(0, 5, 0), waist=(34, -10, 0), head=(-30, 8, 0), ra=(34, 6, 16)))
    full(c, 0.25, hit)                                  # the rising cut lands
    full(c, 0.35, add(hit, ra=(-6, 4, 2), root_pos=(0, 1, 0), waist=(-4, -4, 0)))
    # Exposed: it lands heavily and pants, open to a counter, then gathers itself.
    full(c, 0.55, spent)
    full(c, 0.64, add(spent, waist=(-4, 0, 0), root_pos=(0, -.4, 0), head=(-4, 0, 0)))
    full(c, 0.72, spent)
    full(c, 0.9, STANCE)


# -- Shardcaster -----------------------------------------------------------------------------------
@move("throw_fan", "Side-arm throw of a shard fan", 0.8)
def _(c):
    draw = plant(merge(STANCE, root_pos=(0, 0, 1), waist=(16, 30, 0), head=(-14, -24, 0), ra=(-70, 84, 34),
                       la=(-84, -20, 10)))
    release = plant(merge(STANCE, LUNGE_LEGS, root_pos=(0, 0, -1), waist=(22, -14, 0), head=(-18, 10, 0),
                          ra=(-88, -10, 10), la=(-20, 20, 30)))
    bookend(c, 0.8)
    full(c, 0.2, draw)
    full(c, 0.35, plant(add(draw, waist=(0, 6, 0), ra=(-6, 12, 4))))
    full(c, 0.4, release)                               # the shards leave the hand
    full(c, 0.5, plant(merge(release, waist=(26, -24, 0), ra=(-74, -48, 4))))


@move("kick", "Front push kick", 0.6)
def _(c):
    chamber = plant(merge(STANCE, waist=(2, 0, 0), head=(-8, 0, 0), ra=(-92, -30, 10), la=(-80, -20, 10),
                          rl=(-64, 0, 4), ll=(-18, 0, 4)))
    bookend(c, 0.6)
    tween(c, 0.05, STANCE, chamber, 0.4)
    full(c, 0.12, chamber)
    full(c, 0.2, plant(merge(chamber, waist=(-10, 0, 0), rl=(-96, 0, 2), ra=(-50, 10, 30), la=(-60, 10, 24))))
    full(c, 0.25, plant(merge(chamber, root_pos=(0, 0, 1.5), waist=(-16, 0, 0), head=(0, 0, 0), rl=(-104, 0, 0),
                              ra=(-36, 20, 44), la=(-44, 20, 40))))
    back = plant(merge(chamber, rl=(-50, 0, 4), waist=(8, 0, 0)))
    full(c, 0.35, back)
    tween(c, 0.45, back, STANCE, 0.45)
    tween(c, 0.52, back, STANCE, 0.8)


# -- Molt Warden -----------------------------------------------------------------------------------
@move("lash", "Silvertail lash: overhead wind-up, horizontal sweep", 1.3)
def _(c):
    wind = plant(merge(STANCE, root_pos=(0, 0, 2), waist=(-6, 36, 0), head=(-4, -30, 0), ra=(-200, 0, -24),
                       la=(-50, -30, 20), rl=(44, 0, 8), ll=(-36, 0, 6)))
    hit = plant(merge(STANCE, LUNGE_LEGS, root_pos=(0, 0, -2), waist=(24, -20, 0), head=(-20, 12, 0),
                      ra=(-92, -20, 10), la=(-20, 10, 30)))
    bookend(c, 1.3)
    full(c, 0.3, plant(merge(STANCE, root_pos=(0, 0, 1.5), waist=(6, 22, 0), head=(-10, -18, 0),
                             ra=(-160, 10, -16), la=(-60, -20, 20))))
    full(c, 0.7, wind)
    full(c, 0.78, plant(merge(wind, ra=(-96, 100, 4), waist=(4, 26, 0))))   # drops to the side, sweep begins
    full(c, 0.85, hit)                                  # the tail sweeps through
    full(c, 0.95, plant(merge(hit, waist=(28, -34, 0), head=(-22, 18, 0), ra=(-90, -48, 4))))
    full(c, 1.1, plant(merge(STANCE, waist=(24, -10, 0), ra=(-60, -30, 8))))


@move("slam", "Two-fisted ground slam", 1.4)
def _(c):
    rise = plant(merge(STANCE, waist=(-6, 0, 0), head=(-16, 0, 0), ra=(-160, 0, -14), la=(-160, 0, -14),
                       rl=(8, 0, 6), ll=(-8, 0, 6)), sole=1.0)
    slam = add(plant(merge(STANCE, waist=(58, 0, 0), head=(-36, 0, 0), ra=(-80, 0, 6), la=(-80, 0, 6),
                           rl=(66, 0, 12), ll=(-60, 0, 10))), root_pos=(0, 0, 0))
    up = plant(merge(STANCE, waist=(40, 0, 0), head=(-24, 0, 0), ra=(-50, 0, 14), la=(-50, 0, 14),
                     rl=(50, 0, 10), ll=(-54, 0, 10)))
    bookend(c, 1.4)
    full(c, 0.35, rise)
    full(c, 0.6, plant(merge(rise, waist=(-14, 0, 0), head=(-20, 0, 0), ra=(-186, 0, -10), la=(-186, 0, -10),
                             rl=(4, 0, 4), ll=(-4, 0, 4)), sole=2.5))
    full(c, 0.7, slam)                                  # fists hit the ground
    c.wobble(0.72, 0.9, 14, "root_pos", .35, base=slam["root_pos"], axis=1, decay=.6)
    full(c, 0.9, add(slam, root_pos=(0, .3, 0)))
    full(c, 1.08, up)


@move("call", "Palm to the ground: calls the silverfish", 1.2)
def _(c):
    kneel = plant(merge(STANCE, KNEEL, waist=(40, -10, 0), head=(-26, 8, 0), ra=(-40, 0, 20), la=(-50, -10, 24)))
    press = plant(merge(kneel, waist=(50, -14, 0), head=(-30, 12, 0), ra=(-30, 0, 30)))
    bookend(c, 1.2)
    full(c, 0.3, kneel)
    full(c, 0.5, press)                                 # the palm lands
    c.wobble(0.55, 0.8, 10, "ra", 3, base=press["ra"], axis=0)
    full(c, 0.85, add(press, head=(-4, 0, 0)))
    full(c, 1.0, plant(merge(STANCE, waist=(30, 0, 0), head=(-20, 0, 0))))


# -- reactions -------------------------------------------------------------------------------------
@move("stagger", "Reels back off balance", 1.0)
def _(c):
    bookend(c, 1.0)
    full(c, 0.08, plant(merge(STANCE, root=(-6, 0, 0), root_pos=(0, 0, 3), waist=(-22, -10, 6), head=(-28, 10, 8),
                              ra=(-110, 60, -20), la=(-130, 50, -30), rl=(28, 0, 6), ll=(-26, 0, 6))))
    full(c, 0.25, plant(merge(STANCE, root=(-8, 0, 3), root_pos=(0, 0, 4), waist=(-26, 8, -6),
                              head=(-20, -10, -6), ra=(-90, 70, 0), la=(-140, 40, -20), rl=(22, 0, 8),
                              ll=(12, 0, 6))))
    shaken = plant(merge(STANCE, root=(0, 0, 0), root_pos=(.5, 0, 2), waist=(10, 0, 4), head=(10, 0, 4), ra=(-50, 20, 40),
                         la=(-40, 20, 46), rl=(30, 0, 8), ll=(-34, 0, 8)))
    full(c, 0.45, shaken)
    c.wobble(0.48, 0.7, 7, "head", 9, base=shaken["head"], axis=1, decay=.5)   # shakes it off
    full(c, 0.75, plant(merge(STANCE, root_pos=(0, 0, .5), waist=(20, 0, 0))))


@move("taunt", "Beckons you in, head tilted", 1.6)
def _(c):
    tall = plant(merge(STANCE, waist=(8, 10, 0), head=(-8, -12, 16), ra=(-18, 0, 14), la=(-82, 12, 0),
                       rl=(20, 0, 6), ll=(-24, 0, 6)))
    bookend(c, 1.6)
    full(c, 0.3, tall)
    c.cycle(0.4, 1.1, .3, dict(la=(-80, 12, 0)), dict(la=(-108, 12, -6)))       # come here
    c.key(0.8, head=(-6, -12, 22))
    c.key(1.2, head=(-10, -4, -8), la=(-60, 0, 4))


@move("defeat", "Drops to a knee and curls up", 1.4, blend=(0.06, 0))
def _(c):
    kneel = plant(merge(STANCE, KNEEL, waist=(36, 0, 0), head=(20, 0, 0), ra=(-34, 0, 22), la=(-40, -10, 24)))
    # Curled over the front knee, arms wrapped round itself (the right knee stays down, so no leg swings through
    # the ground on the way).
    curl = plant(merge(STANCE, waist=(56, 0, 0), head=(4, 0, 0), ra=(-96, -52, 0), la=(-84, -46, 0), rl=(66, 0, 6),
                       ll=(-56, 0, 14)))
    full(c, 0, STANCE)
    full(c, 0.15, plant(merge(STANCE, waist=(-8, 0, 0), head=(-18, 0, 6), root_pos=(0, 0, 1))))
    full(c, 0.5, kneel)
    full(c, 0.75, add(kneel, waist=(4, 0, 3), head=(4, 0, 4)))
    c.key(0.9, la=(-80, -14, 22), ra=(-80, -30, 10))
    full(c, 1.1, curl)                                  # curled small; the game bursts it into dust at 1.2
    full(c, 1.4, curl)
