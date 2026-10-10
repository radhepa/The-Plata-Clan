"""The Molt: a disguised Plata, still wearing the villager, drops the act (DESIGN.md section 2.3).

Played on the Village Friends resident by our ResidentRenderer mixin, never by VF's director (trigger "pet" with
an impossible requirement). Beats the game relies on:
    0.00-0.35  the head snaps forward and tilts, slow and sinister; eyes narrow
    0.35-0.80  hunch; both arms cross to grip the opposite shoulders; a shiver runs through body and arms
    0.80-1.20  curl down into a tight crouch (the poses.CURL pose, reached at 1.15)
    1.20       the game swaps the villager for the fighter under a dust burst (unfurl starts in CURL)
    1.20-1.30  hold
The clip blends in over 0.1 s and never blends out, so the curl holds until the swap.
"""
from kit import clip
from poses import CURL, add

BEATS = {"molt": (0.0, 0.3, 0.55, 0.8, 1.0, 1.2)}
HITS = {"molt": 1.2}

GRIP = dict(ra=(-100, -48, 0), la=(-80, -46, 0))       # each hand on the opposite shoulder, right arm on top


@clip("molt", "Drops the disguise and curls like a silverfish", trigger="pet", require=["play:plata_never"],
      length=1.3, weight=1, blend=(0.1, 0.0), mirror="never", items="override")
def _(c):
    # The head snaps round to face front, then tilts slowly, eyes narrowing.
    c.key(0.07, head=(-4, 0, 3), lid=.2)
    c.key(0.14, head=(-2, 0, 6), lid=.5)
    c.key(0.35, head=(0, 0, 16), lid=.5, root_pos=(0, 0, 0), waist=(0, 0, 0))
    # Hunch; the arms come up and across to grip the shoulders; it looks at you from under its brow.
    c.key(0.5, waist=(16, 0, 0), head=(-8, 0, 19), ra=(-60, -30, 4), la=(-50, -26, 4),
          root_pos=(0, 0.8, 0), rl=(-10, 0, 3), ll=(-10, 0, 3))
    c.key(0.62, waist=(22, 0, 0), head=(-12, 0, 20), **GRIP, root_pos=(0, 1.2, 0), rl=(-16, 0, 4), ll=(-16, 0, 4),
          lid=.55)
    # Something shivers under the skin.
    c.wobble(0.62, 0.8, 16, "waist", 2.5, base=(22, 0, 0), axis=2)
    c.wobble(0.62, 0.8, 18, "ra", 3, base=GRIP["ra"], axis=1)
    c.wobble(0.62, 0.8, 18, "la", 4, base=GRIP["la"], axis=1)
    c.key(0.8, head=(-10, 0, 18), root_pos=(0, 1.4, 0), rl=(-18, 0, 4), ll=(-18, 0, 4))
    # Curl down tight, head tucked, and hold for the swap.
    c.key(0.98, **add(CURL, root_pos=(0, -1.5, 0), waist=(-10, 0, 0), head=(-8, 0, 6)), lid=.6)
    c.key(1.15, **CURL, lid=.7)
    c.key(1.3, **CURL, lid=.7)
