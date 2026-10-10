# The Plata Clan

An add-on for [Village Friends](https://github.com/radhepa/Village-Friends-Minecraft-Mod) (Fabric, Minecraft 26.3). It needs Village Friends to run, and Village Friends never needs it.

*Plata* means silver. The clan's creed is the silverfish: a small grey thing that hides in plain stone, can't be stamped out, and swarms the moment you disturb it.

## What happens

**A traveler waits by the road.** Out in the Overworld you'll sometimes come across a lone villager in the middle of nowhere: on a desert flat, a mountain pass, a forest track. To all appearances they're a real Village Friends resident, with a name, a face and a pleasant word, but they belong to no village. Look closely and you might catch a puff of grey dust at their feet, or the faint skitter of something small.

**Talk to them.** Village Friends' conversation window opens, and something about what they say is a little off. Whatever you answer, the disguise drops in **the Molt**:

1. The window closes and they turn to face you, head tilting.
2. They hunch and grip their own shoulders, shivering as grit trickles off them.
3. They curl up tight, the way a silverfish does.
4. A burst of stone dust, and a Plata springs out of it in a backflip, weapons ready.

The first time it happens, the screen reads **THE PLATA CLAN**.

**They fight like a boss, at your level.** Every Plata has a level from 1 to 100, taken from yours:

- **With [Village Friends RPG](https://github.com/radhepa/Village-Friends-Minecraft-Mod/tree/main/rpg) installed:** your character level.
- **Without it:** a power level read from your armor, weapon, health and experience.

Their health is a fixed number of your own hits. Each of their blows takes a set share of your health after your armor. So whatever your gear, they're never a pushover and never a one-shot. Higher levels also make them fight smarter: longer combos, more dodges, shorter cooldowns, quicker reactions.

| Rank | Fights with |
|---|---|
| **Scuttler** | Twin fang knives. Fast flurries, a lunging stab, backflips, and circling just out of reach. |
| **Shardcaster** | Fans of sharpened silver shards that sting and slow. Keeps you at arm's length and burrows away when you close in. |
| **Molt Warden** (level 20+) | The Silvertail, a segmented whip built like a giant silverfish: a long lash, and a slam that cracks the ground and shakes silverfish out of it. |

Every rank can **burrow**: they dive into the ground and come up under you. The only warning is a trail of cracking grit, then the ground shaking. Hit them as they come up and they stagger. Three quick hits, a critical blow or a blocked combo staggers them too. Low on health, a Plata **molts**: they burrow twice in a row and fight faster. Beaten, they curl up and burst into dust, like a silverfish into a wall.

**Loot:**

| Drop | Chance |
|---|---|
| Emeralds | always |
| **Silver Scales** | always |
| **Plata Fang**, a quick reverse-grip knife (also craftable) | sometimes |
| **Plata Mask** | rarely |

Wear the Plata Mask and disguised Plata take you for kin. They whisper clan gossip instead of fighting.

The clan keeps count of every silverfish you kill, and the more you've crushed, the more often they come for you.

## Hideouts

The clan lives in the stone, under places nobody looks at twice. Each hideout has a lookout up top: a disguised Plata who's happy to chat, until they aren't.

- **Scale Den.** A traveler's camp: a tent, a fire, someone sitting by it. Under the tent a ladder goes down to a single burrow room.
- **Silver Warren.** A ruined toll-house with an old cistern in the floor. The cistern is a spiral stair that drops twenty blocks to the **Molting Hall**, with the clan's mark set the length of the floor. Every Warren branches out differently, through ribbed tunnels to some of these rooms:
  - the Shell Gallery
  - a Sparring Pit
  - the Scale Forge
  - the Brood, full of silverfish
  - the Shrine of the First Molt
  - the Storeroom

  Behind the hall is the vault, where an elder sleeps over the **Clan Coffer**.

Both kinds come in six regional styles: green country, snow and taiga, desert, badlands, savanna and windswept hills. They keep well away from villages and homesteads.

Down below, the clan **sleeps standing up in silver husks**, their own shed shells:

- **Waking them.** Come within sight and a few blocks of a husk and it cracks open, and a Plata springs out at your level. The husks around it follow. **Sneak** and you can creep much closer.
- **Never a swarm.** No more than three are up at once. The rest wait their turn.
- **Walking away.** Leave and they crawl back into their shells, which seal behind them.
- **Building your own.** In creative, husks can be placed for ambushes of your own.

The walls are stronghold brick, and some of it is infested. Dig through the clan's walls and the walls bite back.

**What's in them:**
- **Den chests:**
  - food, Silver Scales, shards and emeralds;
  - sometimes a Plata Fang or the **Plata Mark banner pattern**;
  - sometimes a **Silver-Inked Map** to the nearest Warren.
- **The forge:** iron and the clan's knives.
- **The Clan Coffer:** the best of it, and always the banner pattern.

## Compatibility

- **Village Friends 2.26+** is required. It is hooked from outside only; no Village Friends code is changed. Disguised travelers are ordinary residents that belong to no village. Village Friends guards treat a revealed Plata as a threat.
- **Village Friends RPG** is optional. Your RPG level sets theirs, and Plata count toward the **Vermin** family in the Bestiary.
- **Not-So-Vanilla Mobs** and **Turning Seasons** share no ids and no mixin targets with this mod. The test suite runs inside the full modpack.

## Settings

`config/plataclan.json`:

| Setting | Default | What it does |
|---|---|---|
| `minutesBetweenEncounters` | 25 | Average minutes between travelers. 0 turns encounters off. |
| `graceMinutes` | 20 | No travelers in a new player's first minutes. |
| `talkFirst` | true | `false`: right-clicking a traveler reveals them at once, with no conversation. |
| `healthScale`, `damageScale` | 1 | Make the clan tougher or gentler. |
| `minDistance`, `maxDistance` | 36, 56 | How far ahead of you a traveler appears. |
| `villageDistance`, `structureChunks` | 160, 6 | How far from any village they keep. |

Commands:

| Command | Who | What it does |
|---|---|---|
| `/plata level` | anyone | How the clan sizes you up. |
| `/plata encounter` | operators | Places a disguised traveler a few blocks ahead. |
| `/plata reveal` | operators | Drops the nearest disguise. |
| `/plata spawn <scuttler\|shardcaster\|warden> [level]` | operators | Spawns a revealed Plata. |
| `/locate structure #plataclan:hideouts` | operators | The nearest hideout (`#plataclan:dens`, `#plataclan:warrens` for one kind). |

The three ranks also have spawn eggs.

## Building

Needs JDK 25 and a Village Friends jar to compile against. The build finds it at `-PvillageFriendsJar=<path>`, or as `libs/village-friends-<version>.jar`, or as the newest `village-friends-*.jar` in the VillageFriends launcher instance's mods folder.

```
gradlew build                                    # jar in build/libs, runs the unit tests
gradlew runClientGameTest -PtestHeap=2g          # the in-game test, with screenshots in build/run/clientGameTest/screenshots
gradlew runClientGameTest -PcompatMods=<mods>    # the same inside the full modpack (e.g. the instance's mods folder)
```

Use `-PplataTests=Talk,Ranks,Moves,Fight,Mask,Rude,Death,Compat` to run only some scenes.

**Art and data come from the scripts in `tools/`.** Never hand-edit the generated files.

| Tool | What it makes |
|---|---|
| `python tools/plata/build.py` | The costumes (models and textures, three ranks), from `tools/mobkit.py`. Preview with `python tools/preview/preview.py plata_scuttler --views 4`. |
| `python tools/items/items.py --preview` | Items, the worn mask, spawn eggs and the clan mark. |
| `python tools/animations/build_clips.py` | Every animation, in Village Friends' animation-pack format. Contact sheets with `tools/animations/clip_preview.py`. |
| `python tools/lines/build_lines.py --check` | Everything the clan says, from `tools/lines/*.txt`. |

Design notes and numbers are in [DESIGN.md](DESIGN.md).

## License

MIT
