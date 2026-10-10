"""The Silver Mother's moves (DESIGN.md section 11), played by id by MotherModel.

She fights upright and unhurried, a head taller than anyone she fights: everything starts from and returns to her
STANCE, so the poser can cross-fade any move over the looping stance. The game times every blow to the beats in HITS
(the fists landing, the train passing through, the Keen, the fling, the palms on the floor, the shell bursting).

She sleeps curled up small on her dais like a silverfish in a crack (the "curl" loop) and rises out of it ("rise"),
and she dies the same way she slept ("defeat" ends curled). Turns round the vertical axis spin the root and end with a
0.1 ms snap to the unturned twin (poses.snap), as in plata_moves.py.
"""
from kit import clip
from poses import add, full, merge, plant, snap

# Where a clip is meant to be in the ground (the preview checks skip it there).
UNDERGROUND = {"burrow": (0.8, 1.0), "erupt": (0.0, 0.12)}
HITS = {"rise": 1.6, "crush": 1.0, "sweep": 0.85, "keen": 1.2, "rain": 0.6, "brood": 0.8, "burrow": 0.9, "erupt": 0.3,
        "shed": 1.6, "stagger": 0.15, "defeat": 2.4}
BEATS = {
    "stance": (0.0, 0.6, 1.2, 1.8, 2.4),
    "curl": (0.0, 1.0, 2.0, 3.0, 4.0),
    "rise": (0.0, 0.6, 1.1, 1.6, 2.0, 2.5),
    "crush": (0.0, 0.5, 0.9, 1.0, 1.5, 2.2),
    "sweep": (0.0, 0.45, 0.7, 0.85, 1.0, 1.4),
    "keen": (0.0, 0.6, 1.1, 1.2, 1.5, 2.0),
    "rain": (0.0, 0.35, 0.5, 0.6, 0.8, 1.3),
    "brood": (0.0, 0.4, 0.8, 1.1, 1.4),
    "burrow": (0.0, 0.3, 0.6, 0.8, 0.9),
    "erupt": (0.0, 0.15, 0.3, 0.5, 0.8, 1.1),
    "shed": (0.0, 0.6, 1.2, 1.6, 2.0, 2.5),
    "stagger": (0.0, 0.15, 0.4, 0.8, 1.2),
    "defeat": (0.0, 0.6, 1.2, 1.8, 2.4, 3.0),
}


def move(id, name, length, blend=(0.08, 0.1)):
    """A Mother clip: the 'idle' trigger (MotherModel picks clips by id), never mirrored, arms always driven."""
    return clip(id, name, length=length, blend=blend, mirror="never", items="override")


# Upright and regal: weight back a little, chin down to look at whoever is below her, the right gauntlet a little
# forward and open, the left at her side.
STANCE = plant(dict(waist=(-3, 4, 0), head=(8, -4, 0), ra=(-26, -12, 10), la=(6, 0, 12), rl=(5, 0, 3), ll=(-7, 0, 2)))
# Asleep: kneeling, folded over her knees, arms wrapped round herself, head tucked.
CURL = plant(dict(waist=(64, 0, 0), head=(22, 0, 0), ra=(-74, -46, 0), la=(-70, -42, 0), rl=(86, 0, 4), ll=(86, 0, -4)))
# On her knees, upright (rising, and falling).
KNEES = plant(dict(waist=(8, 0, 0), head=(6, 0, 0), ra=(-14, 0, 14), la=(-14, 0, 14), rl=(84, 0, 4), ll=(84, 0, -4)))


def bookend(c, length):
    full(c, 0, STANCE)
    full(c, length, STANCE)


@move("stance", "Upright and watchful (loops)", 2.4, blend=(0, 0))
def _(c):
    breathe = dict(root_pos=(0, -.35, 0), waist=(-1.5, 0, 0), head=(-1, 0, 0), ra=(-2, 0, 1), la=(0, 0, 1.5))
    turn = dict(waist=(0, -6, 0), head=(0, 8, 0), ra=(2, 2, 0))
    full(c, 0, STANCE)
    full(c, 0.6, add(STANCE, **breathe))
    full(c, 1.2, add(STANCE, **turn))
    full(c, 1.8, add(add(STANCE, **breathe), **turn))
    full(c, 2.4, STANCE)


@move("curl", "Asleep, curled on the dais (loops)", 4.0, blend=(0, 0))
def _(c):
    full(c, 0, CURL)
    full(c, 2.0, add(CURL, root_pos=(0, -.5, 0), waist=(-3, 0, 0), head=(-2, 0, 0), ra=(2, 2, 0), la=(2, 2, 0)))
    full(c, 4.0, CURL)


@move("rise", "Uncurls and rises to her full height", 2.5, blend=(0, 0.12))
def _(c):
    stir = add(CURL, waist=(-12, 0, 0), head=(-26, 0, 0))
    present = plant(merge(STANCE, waist=(-12, 0, 0), head=(-14, 0, 0), ra=(-60, 60, 40), la=(-60, 60, 40)))
    full(c, 0, CURL)
    full(c, 0.5, stir)                                    # the head comes up first
    full(c, 0.9, merge(KNEES, head=(-10, 0, 0), ra=(-30, 0, 30), la=(-30, 0, 30)))
    full(c, 1.3, plant(merge(STANCE, waist=(20, 0, 0), head=(-6, 0, 0), ra=(-40, 0, 20), la=(-40, 0, 20),
                             rl=(-30, 0, 4), ll=(30, 0, 4))))
    full(c, 1.6, present)                                 # standing: arms wide, chin up
    full(c, 2.0, add(present, ra=(-6, 4, 2), la=(-6, 4, 2), root_pos=(0, -.3, 0)))
    full(c, 2.5, STANCE)


@move("crush", "Both gauntlets overhead and down into the floor; stuck there a moment", 2.4)
def _(c):
    reach = plant(merge(STANCE, waist=(-16, 0, 0), head=(-18, 0, 0), ra=(-176, 0, -8), la=(-176, 0, -8),
                        rl=(-6, 0, 4), ll=(6, 0, 4)), sole=1.5)
    smash = plant(merge(STANCE, root_pos=(0, 0, -1), waist=(62, 0, 0), head=(-34, 0, 0), ra=(-86, 0, 8),
                        la=(-86, 0, 8), rl=(58, 0, 10), ll=(-52, 0, 10)))
    stuck = add(smash, waist=(4, 0, 0), ra=(4, 0, 0), la=(4, 0, 0))
    bookend(c, 2.4)
    full(c, 0.5, plant(merge(STANCE, waist=(-8, 0, 0), head=(-12, 0, 0), ra=(-140, 0, -6), la=(-140, 0, -6))))
    full(c, 0.9, reach)
    full(c, 1.0, smash)                                   # the fists hit the floor
    c.wobble(1.02, 1.3, 12, "root_pos", .5, base=smash["root_pos"], axis=1, decay=.6)
    full(c, 1.3, stuck)
    c.wobble(1.35, 1.9, 6, "ra", 4, base=stuck["ra"], axis=0)  # wrenching the gauntlets out of the stone
    full(c, 2.0, stuck)
    full(c, 2.2, plant(merge(STANCE, waist=(30, 0, 0), head=(-10, 0, 0), ra=(-50, 0, 12), la=(-50, 0, 12))))


@move("sweep", "Turns on her heel: the train whips round in a full circle", 1.6)
def _(c):
    wind = plant(merge(STANCE, root=(0, -40, 0), waist=(10, -20, 0), head=(4, 30, 0), ra=(-40, 50, 30),
                       la=(-30, -30, 20), rl=(30, 0, 6), ll=(-30, 0, 6)))
    out = dict(waist=(14, 0, 0), head=(8, 0, 0), ra=(-70, 70, 40), la=(-70, 70, 40), rl=(20, 0, 8), ll=(-20, 0, 8))
    bookend(c, 1.6)
    full(c, 0.45, wind)
    full(c, 0.6, plant(merge(STANCE, out, root=(0, 40, 0))))
    full(c, 0.75, plant(merge(STANCE, out, root=(0, 170, 0))))
    full(c, 0.85, plant(merge(STANCE, out, root=(0, 250, 0))))     # the train passes through
    full(c, 0.95, plant(merge(STANCE, out, root=(0, 320, 0))))
    end = plant(merge(STANCE, waist=(10, 0, 0), ra=(-40, 30, 20), la=(-30, 20, 20)))
    snap(c, 1.05, merge(end, root=(0, 360, 0)), merge(end, root=(0, 0, 0)))


@move("keen", "Rears back, arms wide, and shrieks with a thousand silverfish voices", 2.2)
def _(c):
    rear = plant(merge(STANCE, waist=(-22, 0, 0), head=(-38, 0, 0), ra=(-40, 80, 60), la=(-40, 80, 60),
                       rl=(-10, 0, 4), ll=(10, 0, 4)))
    shriek = plant(merge(STANCE, root_pos=(0, 0, -1), waist=(24, 0, 0), head=(-26, 0, 0), ra=(10, 30, 50),
                         la=(10, 30, 50), rl=(30, 0, 6), ll=(-30, 0, 6)))
    bookend(c, 2.2)
    full(c, 0.6, plant(merge(STANCE, waist=(-12, 0, 0), head=(-20, 0, 0), ra=(-30, 60, 40), la=(-30, 60, 40))))
    full(c, 1.1, rear)
    full(c, 1.2, shriek)                                  # the Keen
    c.wobble(1.22, 1.7, 18, "head", 3, base=shriek["head"], axis=1)
    full(c, 1.7, shriek)
    full(c, 2.0, plant(merge(STANCE, waist=(8, 0, 0), ra=(-20, 10, 20), la=(0, 10, 20))))


@move("rain", "Scoops a handful of scales and flings them high", 1.6)
def _(c):
    scoop = plant(merge(STANCE, waist=(34, 20, 0), head=(-20, -16, 0), ra=(30, 20, 30), la=(-30, -10, 20),
                        rl=(40, 0, 6), ll=(-40, 0, 6)))
    fling = plant(merge(STANCE, waist=(-20, -10, 0), head=(-40, 6, 0), ra=(-190, -10, -10), la=(-20, 0, 30)), sole=0.8)
    bookend(c, 1.6)
    full(c, 0.35, scoop)
    full(c, 0.5, plant(merge(scoop, waist=(10, 10, 0), ra=(-90, 10, 10))))
    full(c, 0.6, fling)                                   # the scales leave her hand
    full(c, 0.8, add(fling, ra=(4, 0, 0)))
    full(c, 1.3, plant(merge(STANCE, head=(-14, 0, 0))))


@move("brood", "Kneels and presses both palms to the floor: calls her children", 1.6)
def _(c):
    kneel = plant(merge(STANCE, waist=(40, 0, 0), head=(-10, 0, 0), ra=(-40, -10, 16), la=(-40, -10, 16),
                        rl=(80, 0, 6), ll=(-70, 0, 6)))
    press = plant(merge(kneel, waist=(54, 0, 0), head=(-24, 0, 0), ra=(-26, -6, 22), la=(-26, -6, 22)))
    bookend(c, 1.6)
    full(c, 0.4, kneel)
    full(c, 0.8, press)                                   # the palms land; the floor answers
    c.wobble(0.82, 1.1, 10, "ra", 3, base=press["ra"], axis=0)
    full(c, 1.1, press)
    full(c, 1.4, plant(merge(STANCE, waist=(24, 0, 0), head=(-6, 0, 0))))


@move("burrow", "Spirals down into the floor", 1.0, blend=(0.08, 0))
def _(c):
    crouch = plant(merge(STANCE, waist=(40, 0, 0), head=(-20, 0, 0), ra=(-120, 30, 30), la=(-120, 30, 30),
                         rl=(56, 0, 8), ll=(-60, 0, 8)))
    full(c, 0, STANCE)
    full(c, 0.3, merge(crouch, root=(0, 60, 0)))
    full(c, 0.6, merge(crouch, root=(0, 200, 0), root_pos=(0, 6, 0), ra=(-170, 0, 0), la=(-170, 0, 0)))
    full(c, 0.8, merge(crouch, root=(0, 300, 0), root_pos=(0, 12, 0), ra=(-176, 0, 0), la=(-176, 0, 0)))
    full(c, 1.0, merge(crouch, root=(0, 360, 0), root_pos=(0, 12, 0), ra=(-176, 0, 0), la=(-176, 0, 0)))


@move("erupt", "Bursts up out of the floor, arms high", 1.2)
def _(c):
    low = merge(STANCE, root_pos=(0, 12, 0), waist=(40, 0, 0), head=(-30, 0, 0), ra=(-170, 0, 0), la=(-170, 0, 0))
    up = plant(merge(STANCE, waist=(-16, 0, 0), head=(-24, 0, 0), ra=(-180, 20, -20), la=(-180, 20, -20),
                     rl=(-4, 0, 6), ll=(4, 0, 6)), sole=2)
    land = plant(merge(STANCE, waist=(36, 0, 0), head=(-14, 0, 0), ra=(-40, 30, 30), la=(-40, 30, 30),
                       rl=(50, 0, 10), ll=(-50, 0, 10)))
    full(c, 0, low)
    full(c, 0.15, merge(low, root_pos=(0, 4, 0), waist=(10, 0, 0)))
    full(c, 0.3, up)                                      # out, and everything near is thrown
    full(c, 0.5, add(up, root_pos=(0, 1, 0)))
    full(c, 0.8, land)
    full(c, 1.2, STANCE)


@move("shed", "Clutches herself, shudders, and bursts out of her old shell", 2.6)
def _(c):
    clutch = plant(merge(STANCE, waist=(30, 0, 0), head=(16, 0, 0), ra=(-80, -60, 0), la=(-80, -60, 0),
                         rl=(20, 0, 6), ll=(-20, 0, 6)))
    burst = plant(merge(STANCE, waist=(-26, 0, 0), head=(-40, 0, 0), ra=(-60, 90, 70), la=(-60, 90, 70),
                        rl=(-12, 0, 8), ll=(12, 0, 8)), sole=1)
    bookend(c, 2.6)
    full(c, 0.6, clutch)
    c.wobble(0.65, 1.45, 14, "waist", 4, base=clutch["waist"], axis=2)
    full(c, 1.5, add(clutch, waist=(6, 0, 0)))
    full(c, 1.6, burst)                                   # the old shell bursts off her
    full(c, 2.0, add(burst, head=(4, 0, 0)))
    full(c, 2.3, plant(merge(STANCE, waist=(-6, 0, 0), head=(-10, 0, 0))))


@move("stagger", "Rocks back on her heels", 1.4)
def _(c):
    reel = plant(merge(STANCE, root_pos=(0, 0, 2), waist=(-20, 10, 6), head=(-24, -10, 8), ra=(-70, 40, 20),
                       la=(-30, 20, 30), rl=(-20, 0, 6), ll=(20, 0, 6)))
    bookend(c, 1.4)
    full(c, 0.15, reel)
    c.wobble(0.2, 0.6, 8, "head", 6, base=reel["head"], axis=1, decay=.5)
    full(c, 0.8, plant(merge(STANCE, waist=(10, 0, 0), head=(10, 0, 0), ra=(-60, -30, 0))))   # a hand to her brow


@move("defeat", "Sinks to her knees and curls up where she slept", 3.0, blend=(0.08, 0))
def _(c):
    full(c, 0, STANCE)
    full(c, 0.3, plant(merge(STANCE, waist=(-12, 0, 6), head=(-24, 0, 10), ra=(-40, 30, 30), la=(-20, 20, 30))))
    full(c, 0.8, KNEES)
    full(c, 1.2, add(KNEES, waist=(6, 0, 4), head=(10, 0, 6)))
    full(c, 1.8, merge(KNEES, waist=(30, 0, 0), head=(16, 0, 0), ra=(-50, -30, 0), la=(-50, -30, 0)))
    full(c, 2.4, CURL)                                    # curled small; the game bursts her into dust after
    full(c, 3.0, CURL)
