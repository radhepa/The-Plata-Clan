# The Plata Clan — design

An add-on for Village Friends (mod id `plataclan`). It needs Village Friends to run, and Village Friends never needs it. It also gets along with the Village Friends RPG add-on, Not-So-Vanilla Mobs and Turning Seasons, and plays fine without them.

*Plata* means silver. The clan's whole creed is the silverfish: a small grey thing that hides in plain stone, can't be stamped out, and swarms the moment you disturb it.

## 1. Lore

A generation ago a crew of village miners broke into the walls of a stronghold and found them alive with silverfish. The others fled. Five of them sat down in the dust and watched instead. They saw a creature nobody can kill off. It looks exactly like the stone around it, waits as long as it has to, and pours out when the stone is struck.

They came back up as the **Plata Clan**, and they live the way the silverfish taught them:

- **They hide in plain stone.** A Plata looks like any other villager: a name, a face, a pleasant word. You find them out in the empty places: a desert flat, a mountain pass, a lonely road.
- **They hate stone-breakers.** Adventurers dig through the world and crush the little silver ones by the hundred, so the clan keeps count. The more silverfish you've killed, the more often they come for you.
- **They are in the stone.** In a fight they dive into the ground in a burst of grit and come up behind you.
- **Their mark** is a silverfish seen from above: a segmented teardrop with two feelers and three tail strands.
- **Their motto** is *"We are in the stone."*

Ranks:

| Rank | Who | Fights with |
|---|---|---|
| **Scuttler** | The footsoldier, most of the clan | Twin *fang knives* held reverse-grip: fast flurries, a lunge, backflips |
| **Shardcaster** | A skirmisher who keeps you at arm's length | Fans of sharpened silver *shards* (thrown scales); burrows away when you close in |
| **Molt Warden** | An elder enforcer, rare | The *Silvertail*: a segmented whip-flail built like a giant silverfish, plus a ground slam that shakes silverfish out of the earth |

The **Silver Mother**, the founder whom nobody has seen, and the clan's hideout are left for later.

## 2. The encounter

1. **The waiting traveler.** While you play in the Overworld, a Plata in disguise sometimes turns up 36–56 blocks ahead of you, out of sight where possible, on dry open ground well away from any village or homestead. To the world they are a real Village Friends resident: a name, a look, a biome villager type, wandering slowly within about ten blocks of where they stand. They belong to no village (the Village Friends `dweller` mechanism with the role `plata`), so no census, news or houses.
   - Defaults: about one encounter per 25 minutes of eligible play, none in a player's first 20 minutes, never two within 8 minutes, at most one disguised Plata per player, and none within 96 blocks of another. Deserts and badlands ×2; stony peaks, windswept hills and other stone country ×1.5; ocean and river 0.
   - The grudge: chance × (1 + min(1, silverfish killed ÷ 200)).
   - Tells for players who look closely: now and then a puff of grey stone dust at their feet and the faint skitter of a silverfish.
   - Someone disguised whom nobody comes near (no player within 128 blocks for a minute, or two in-game days with no meeting) leaves quietly.
2. **The talk.** Right-click them and Village Friends' own conversation window opens with a disguise line, slightly off ("Out here on your own? How… convenient."), and two or three replies. Choosing any reply (or any other button in the window), closing the window, waiting 10 s or walking away starts the reveal. With `talkFirst: false` in the config, the right-click reveals at once.
   - Hitting a disguised Plata does no damage and reveals them at once ("Rude.").
   - Monsters can't hurt them while disguised; they simply aren't there when the blow lands.
   - Lightning doesn't turn them into witches.
3. **The reveal: "the Molt"** (about 2.2 s). The window closes and the villager freezes and turns to you. Their head tilts and their eyes narrow. They hunch and grip their own shoulders while something shivers under the skin and grit trickles off them. They curl into a tight crouch, like a silverfish curling. Then a burst of stone dust and the swap: the villager is gone, and a Plata springs out of the dust in a backflip and lands in a fighting crouch, weapons out.
   - On a player's first ever reveal, a title card reads **THE PLATA CLAN** with the member's taunt.
   - On every reveal, the taunt shows as a speech line and a boss bar appears: `Plata Scuttler · Lv 23`.
4. **The fight** (below).
5. **The end.** A beaten Plata drops to one knee, curls up and bursts into dust, gone like a silverfish into a wall. They leave loot, and any silverfish they called flee into the ground. If everyone they were fighting gets more than 48 blocks away or dies, the Plata burrows away after 10 s with a parting line.

## 3. Levels: always a fight, never a slaughter

Every Plata has a **level from 1 to 100**, set from the player who revealed them:

- **With Village Friends RPG installed:** the player's RPG character level.
- **Without it:** a *power level* estimated from the player's armor and toughness, main-hand damage and attack speed (Sharpness counted), max health and XP level, put on the same 1–100 scale.
- The member's level is that ± 2 (seeded), +3 for a Molt Warden. Wardens only appear at level 20 and up.

Stats come from **your actual numbers**, not a fixed table, which is how the fight stays fair at every stage of the game:

- **Health** = your expected melee hit × a hits-to-kill budget. That's about 10 hits for a Scuttler, 8 for a Shardcaster and 16 for a Warden, growing slowly with level. Each extra player within 32 blocks at the reveal adds 60%. Bounds are 20–1024.
- **Damage** is aimed at a share of *your* max health after your armor and toughness, solved with vanilla's own armor formula. A Scuttler cut takes ~7%, a shard ~5%, a Silvertail lash ~13%, a slam ~10% and a burrow eruption ~11%, each creeping up by a hundredth of a percent per level. Protection, resistance and blocking still help, so good gear still pays off.
- **The level also decides how cleverly they fight:**
  - **Dodge:** 12% + 0.15%/level, capped at 27%, never twice in a row, never during their own recovery.
  - **Flurry length:** 2 hits, 3 from level 30, 4 from level 70.
  - **Burrow cooldown:** 14 s, falling to 7 s.
  - **Reaction delay:** 16 ticks, falling to 6.
  - **Silverfish called:** 1–3.
  - **Shards per fan:** 3, then 5 from level 40.
  - **Molt (desperation phase):** from level 15.

All numbers live in `Balance` (pure, unit-tested) and two config multipliers (`healthScale`, `damageScale`).

## 4. How they fight

Every rank:

- **Burrow.** Telegraph: a crouch and a quick dig (0.5 s). Then they dive in a burst of grit and travel *under* the surface toward you (invisible, untouchable). The only sign is a running trail of cracking dust and a rumble. The trail stops under you, the ground shakes for 0.6 s (your cue to move), and they erupt with a rising cut that knocks you up.
  - Hit them in the 0.8 s after they come up and they **stagger**.
  - Water, lava or a long drop on the way makes them surface early.
- **Dodge.** A backflip or side-roll away from a melee swing, or a sidestep from an arrow (25%).
- **Stagger.** Taking 3 hits within 2 s, a critical hit, or having a flurry blocked by a shield staggers them for 1 s, open to anything.
- **Molt** (once, under 35% health, level 15+). They shed a burst of grit, burrow and come up twice in a row, and fight 15% faster after.
- **Who they fight.** Only players, and anything that hurts them. Never villagers: they *are* villagers. Village Friends guards treat them as predators (they're added to `villagefriends:villager_predators`), and iron golems fight them as monsters.

By rank:

| | Scuttler | Shardcaster | Molt Warden |
|---|---|---|---|
| Spawn share | 60% | 25% | 15% (level 20+, else a Scuttler) |
| Moves | **Flurry**: 2–4 quick cuts after a crouched pull-back (0.4 s). **Lunge**: a 5-block dash-cut. Circles you at 3–4 blocks between attacks. **Silverfish call** at level 20+ | **Shard fan**: 3 or 5 shards in a spread, small damage + Slowness I 2 s. Keeps 8–14 blocks away. **Kick** when cornered. Burrows away when you get within 4 | **Lash**: a long Silvertail sweep (reach 4.5) after a 0.7 s wind-up. **Slam**: cracks a ring that erupts 0.6 s later, so step out of it; it also releases 2–3 silverfish. **Burrow charge**: erupts under you and launches you |
| Speed | fast (0.33) | 0.31 | 0.27, heavy (knockback resistance 0.6) |

## 5. Loot

| Drop | Chance |
|---|---|
| Emeralds 1–3 (+1 per 20 levels) | always |
| **Silver Scale** ×2–4 (+1 per 25 levels): plates the clan stitches into its garb, a crafting material | always |
| **Plata Fang**: a reverse-grip knife, fast (attack speed 2.0), damage just under an iron sword; also craftable from 3 Silver Scales, an iron ingot and a stick | 8% + 0.1%/level |
| **Plata Mask**: a silver face plate (2 armor). Wear it and disguised Plata take you for kin: talking to one gets a whispered clan line instead of a fight | 2.5% + 0.05%/level, Wardens ×2 |

## 6. Compatibility

- **Village Friends** is hooked from outside only, with no VF file changed:
  - an early-phase `UseEntityCallback`;
  - `VillageFriends.show` for the disguise talk;
  - mixins on `NarrativeEngine.choices` (the `plataclan` tab) and `VillageFriends.handleAction` (`plataclan_*` actions);
  - a client mixin at the tail of `ResidentRenderer.extractRenderState` that plays the Molt clip;
  - VF's own animation-pack format, so clips are authored with VF's kit and parsed with `AnimationPack.parse`.

  Disguised villagers carry VF's `dweller` attachment with role `plata`, so VF keeps them out of villages and doesn't tether them. After the reveal the villager is discarded (no death, so no news or knockout), and its acquaintance entry is removed from the player.
- **Village Friends RPG** (optional): its character level sets the Plata level, read by reflection (no hard dependency). An optional mixin maps every Plata to the **Vermin** family in the Bestiary; its Legend ability is Burrow Rush, which fits.
- **Not-So-Vanilla Mobs, Turning Seasons:** no shared ids, no mixins into vanilla classes they touch. A modpack game test runs with every jar in the VillageFriends instance.
- **Same versions:** MC 26.3, loader 0.19.5, Fabric API 0.161.0+26.3, Java 25.

## 7. Art direction (the costume)

The Plata are people: the costume sits on the **same body** Village Friends draws (the vanilla player mesh, wide arms) and changes nothing about it. Nothing may echo the Yiga look: **no red, no white mask, no eye symbol, no sickle, no split-toe boots, no topknot.**

Palette (five-shade ramps, deep → highlight):

| Ramp | Colours |
|---|---|
| Silver plate | `#5E646C #858C95 #A9B0B8 #C9CFD5 #E6EAEE` |
| Slate cloth (bodysuit, hood) | `#24282D #34393F #464C54 #5C636B #6E767F` |
| Umber wraps and straps | `#1F1C19 #2A2622 #3A3530 #4B453F` |
| Cold glint (tiny accents only) | `#8CC7D4 #BFE6EE` |
| Scuttler cords | slate blue `#45566B #5A6E86` |
| Shardcaster cords | muted teal `#3B6B68 #4F8A86` |
| Molt Warden trim | tarnished bronze `#5F4724 #8A6A38 #B08A4E` |

Pieces, all ranks:

- **Mask:** a silver face plate over the whole face, banded by 4 overlapping horizontal segments like a silverfish's carapace (dark lower edge, light upper edge). Two narrow dark eye slits, each with a 1 px cold glint. No mouth. A tiny silverfish glyph on the brow. Small side flaps wrap the cheeks.
- **Hood:** slate cloth, with a dorsal ridge of 3–4 small overlapping silver plates from brow to nape.
- **Antennae:** two thin segmented feelers from the brow corners, sweeping up and back. They sway procedurally.
- **Vest:** 5 overlapping silver segment plates stepping down the torso over a slate bodysuit. Rows of tiny dark tick marks along the sides suggest a silverfish's many legs. An umber sash with a silver clan-glyph buckle.
- **Tail:** three cords (the silverfish's three tail strands) hanging from the back of the belt, rank-coloured with silver tips. They sway.
- **Arms:** slate sleeves, forearm bracers of 3 overlapping silver bands, umber-wrapped hands. Small two-plate pauldrons.
- **Legs:** slate trousers, umber shin bindings, small silver knee plates, soft dark boots.

Per rank:

- **Scuttler:** short two-segment antennae; twin fang knives in reverse grip (narrow straight silver blades, umber grips).
- **Shardcaster:** long three-segment antennae; a **mantle** of overlapping scale rows over the shoulders; a **bandolier** of shards across the chest; a **fan of three shards** held in the right hand.
- **Molt Warden:** heavy three-tier pauldrons with bronze trim; a ridged **carapace** back plate; a long three-tailed cape to the ankles; the **Silvertail** whip (handle plus 8 chained segments that shrink toward a three-strand tip).

The part contract that `PlataModel` reads is in §9.

## 8. Code map

- **`dev.plataclan`:**
  - `PlataClan` (init); `PlataConfig`
  - `Balance` (pure: level → stats, behaviour, loot)
  - `PlayerPower` (level source: RPG by reflection, or the estimate)
  - `PlataLines` (dialogue picks from `plata_lines.json`)
- **`dev.plataclan.disguise`:**
  - `Encounters` (where and when a disguised Plata turns up; tells; despawn)
  - `Reveal` (talk hooks, the Molt sequence, the swap)
- **`dev.plataclan.entity`:**
  - `PlataMember` (the fighter, one class for all ranks); `Rank`
  - `Moves` (the move state machine)
  - `SilverShard` (projectile)
- **`dev.plataclan.item`:** Silver Scale, Plata Fang, Plata Mask, spawn eggs.
- **Mixins:**
  - `dev.plataclan.mixin`: VF conversation hooks, villager thunder.
  - `dev.plataclan.compat.mixin`: RPG Bestiary, applied only when the RPG is installed.
  - `dev.plataclan.client.mixin`: ResidentRenderer, for the Molt.
- **Client (`dev.plataclan.client`):**
  - `PlataRenderer`, `PlataModel`, `PlataPoser` (VF clip format on our model), `Geometry` (mobkit loader)
  - `RevealHud` (title card and speech line)
- **Tools:**
  - `tools/mobkit.py` + `tools/plata/*.py`: model and texture.
  - `tools/preview/preview.py`: offline renders.
  - `tools/animations/kit.py` (VF's kit) + `tools/animations/plata_moves.py` + `molt.py`: clips.
  - `tools/lines/*.txt` → `plata_lines.json`.

## 9. Contracts for the art, animation and dialogue files

**Geometry** (`src/client/resources/assets/plataclan/geometry/plata_<rank>.json`, ranks `scuttler`, `shardcaster`, `warden`) and **texture** (`src/main/resources/assets/plataclan/textures/entity/plata_<rank>.png`, 128×128):

- **Root parts:** exactly vanilla's humanoid layout, `head` (child `hat`), `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`, with the player mesh's pivots and boxes:
  - head `(-4,-8,-4) 8×8×8` at pivot 0,0,0
  - body `(-4,0,-2) 8×12×4`
  - arms at pivot ∓5,2,0 with boxes `(-3,-2,-2)` and `(-1,-2,-2) 4×12×4`
  - legs at pivot ∓1.9,12,0 with box `(-2,0,-2) 4×12×4`
- **Optional named parts** (Java animates them if present):
  - `antenna_r_1..3`, `antenna_l_1..3` (each a child of the previous; `_1` under `head`)
  - `tail_l_1..2`, `tail_m_1..2`, `tail_r_1..2` (`_1` under `body`, at the back of the belt)
  - `fang_r` (under `right_arm`), `fang_l` (under `left_arm`)
  - `shard_fan` (under `right_arm`)
  - `whip_0..8` (`whip_0` = handle under `right_arm`, each next a child of the previous)
  - `cape_1..2` (under `body`, Warden)
  - `mask` (under `head`)

  Hands are at the bottom of the arm boxes: y≈10 from the arm pivot.

**Clips** follow VF's animation-pack format (format 1), authored with VF's `kit.py`:

- **Plata clips** go in `src/client/resources/assets/plataclan/plata_moves/plata.json`, trigger `idle`, played by id by `PlataPoser`:
  - `stance`, `taunt`
  - `flurry_1`, `flurry_2`, `flurry_3`, `flurry_4`
  - `lunge`, `backflip`, `sideroll`
  - `burrow_dive`, `emerge`
  - `throw_fan`, `kick`
  - `lash`, `slam`, `call`
  - `stagger`, `unfurl` (the backflip out of the dust), `defeat`
- **The Molt** goes in `src/client/resources/assets/plataclan/resident_animations/plata.json`, so VF loads it as pack `plataclan.plata`. Its id is `molt`, with trigger `pet` and require `["play:plata_never"]` so VF never picks it on its own. It's played by our renderer mixin.

**Lines** (`tools/lines/*.txt` → `src/main/resources/data/plataclan/plata_lines.json`) come in pools:

- `disguise.greet` (by biome group `sand|stone|green|cold`)
- `disguise.reply` (reply labels)
- `reveal.taunt` (by reply kind `polite|curious|suspicious|rude|timeout`)
- `reveal.first` (title-card subtitle)
- `fight.burrow`, `fight.hurt`, `fight.molt`
- `fight.win` (the player fell), `fight.flee`, `defeat`
- `kin.whisper` (the player wears a Plata Mask)
