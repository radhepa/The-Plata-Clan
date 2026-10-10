"""Write everything the Plata Clan's hideouts need in the data pack: the block templates, their jigsaw
pools, one structure per hideout and region, the two structure sets (with their own placement that
keeps clear of villages and homesteads), biome and structure tags, loot tables, the clan's banner
pattern and its translations.

  python tools/hideouts/build.py            build (templates are validated against vanilla's blockstates)
  python tools/hideouts/build.py --render   also render every piece to tools/hideouts/out/
"""
import json
import os
import shutil
import sys

from kit import NS, RES, write_json
from pieces import PALETTES, all_pieces

DATA = os.path.join(RES, 'data', NS)

# Which biomes each regional style claims. The clan avoids water, ice, swamps, jungles and peaks.
BIOMES = {
    'green': ['plains', 'sunflower_plains', 'meadow', 'forest', 'flower_forest', 'birch_forest', 'old_growth_birch_forest',
              'dark_forest', 'pale_garden', 'cherry_grove', 'dappled_forest', 'sparse_jungle'],
    'cold': ['snowy_plains', 'snowy_taiga', 'taiga', 'old_growth_pine_taiga', 'old_growth_spruce_taiga', 'grove'],
    'sand': ['desert'],
    'mesa': ['badlands', 'eroded_badlands', 'wooded_badlands'],
    'dry': ['savanna', 'savanna_plateau', 'windswept_savanna'],
    'stone': ['windswept_hills', 'windswept_gravelly_hills', 'windswept_forest'],
}

NO_SPAWNS = {cat: {'bounding_box': 'piece', 'spawns': []} for cat in (
    'monster', 'creature', 'ambient', 'axolotls', 'underground_water_creature', 'water_creature', 'water_ambient', 'misc')}


def element(location, weight=1):
    return {'weight': weight, 'element': {'element_type': 'minecraft:single_pool_element', 'location': f'{NS}:{location}',
                                          'processors': {'processors': []}, 'projection': 'rigid'}}


def pool(name, elements, fallback='minecraft:empty'):
    write_json(f'data/{NS}/worldgen/template_pool/{name}.json', {'fallback': fallback, 'elements': elements})


def pools():
    for key in PALETTES:
        pool(f'den/surface_{key}', [element(f'den/surface_{key}')])
        pool(f'warren/surface_{key}', [element(f'warren/surface_{key}')])
    pool('den/shaft', [element('den/shaft')])
    pool('den/rooms', [element('den/room_a'), element('den/room_b')])
    pool('warren/stair', [element('warren/stair')])
    pool('warren/hall', [element('warren/hall')])
    pool('warren/vault', [element('warren/vault')])
    pool('warren/caps', [element('warren/tunnel_cap')])
    pool('warren/tunnels', [element('warren/tunnel_short', 4), element('warren/tunnel_long', 2), element('warren/tunnel_bend', 3),
                            element('warren/tunnel_tee', 2), element('warren/tunnel_stairs', 2)], f'{NS}:warren/caps')
    pool('warren/branches', [element('warren/gallery', 3), element('warren/pit', 2), element('warren/forge', 2),
                             element('warren/nest', 2), element('warren/shrine', 2), element('warren/store', 2),
                             element('warren/tunnel_bend', 2), element('warren/tunnel_tee', 1), element('warren/tunnel_short', 1)],
         f'{NS}:warren/caps')


def structures():
    for key in PALETTES:
        write_json(f'data/{NS}/tags/worldgen/biome/has_hideout/{key}.json', {'values': ['minecraft:' + b for b in BIOMES[key]]})
        common = {'type': f'{NS}:hideout', 'biomes': f'#{NS}:has_hideout/{key}', 'step': 'surface_structures',
                  'terrain_adaptation': 'beard_thin', 'spawn_overrides': NO_SPAWNS, 'start_jigsaw_name': f'{NS}:descent'}
        write_json(f'data/{NS}/worldgen/structure/den_{key}.json', {
            **common, 'start_pool': f'{NS}:den/surface_{key}', 'size': 3,
            'max_distance_from_center': {'horizontal': 32, 'vertical': 40},
            'survey': {'radius': 8, 'step': 4, 'max_height_range': 4}, 'cover': 3})
        write_json(f'data/{NS}/worldgen/structure/warren_{key}.json', {
            **common, 'start_pool': f'{NS}:warren/surface_{key}', 'size': 7,
            'max_distance_from_center': {'horizontal': 80, 'vertical': 64},
            'survey': {'radius': 12, 'step': 6, 'max_height_range': 6}, 'cover': 4,
            # every Warren has its Silver Mother: no Warren without its vault
            'require': f'{NS}:warren/vault'})
    write_json(f'data/{NS}/worldgen/structure_set/dens.json', {
        'structures': [{'structure': f'{NS}:den_{k}', 'weight': 1} for k in PALETTES],
        'placement': {'type': f'{NS}:away_from', 'spacing': 28, 'separation': 9, 'salt': 1803261447,
                      'avoid': [{'set': 'minecraft:villages', 'chunks': 6}, {'set': 'villagefriends:homesteads', 'chunks': 2}]}})
    write_json(f'data/{NS}/worldgen/structure_set/warrens.json', {
        'structures': [{'structure': f'{NS}:warren_{k}', 'weight': 1} for k in PALETTES],
        'placement': {'type': f'{NS}:away_from', 'spacing': 48, 'separation': 16, 'salt': 2104905671,
                      'avoid': [{'set': 'minecraft:villages', 'chunks': 8}, {'set': 'villagefriends:homesteads', 'chunks': 3},
                                {'set': f'{NS}:dens', 'chunks': 4}]}})
    write_json(f'data/{NS}/tags/worldgen/structure/dens.json', {'values': [f'{NS}:den_{k}' for k in PALETTES]})
    write_json(f'data/{NS}/tags/worldgen/structure/warrens.json', {'values': [f'{NS}:warren_{k}' for k in PALETTES]})
    write_json(f'data/{NS}/tags/worldgen/structure/hideouts.json', {'values': [f'#{NS}:dens', f'#{NS}:warrens']})


# -- loot ------------------------------------------------------------------------------------------

def item(name, weight=1, lo=1, hi=1, extra=None):
    e = {'type': 'minecraft:item', 'name': name if ':' in name else 'minecraft:' + name, 'weight': weight}
    mods = []
    if (lo, hi) != (1, 1):
        mods.append({'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}})
    mods += extra or []
    if mods: e['modifier'] = mods if len(mods) > 1 else mods[0]
    return e


def empty(weight):
    return {'type': 'minecraft:empty', 'weight': weight}


def rolls(lo, hi):
    return lo if lo == hi else {'type': 'minecraft:uniform', 'min': lo, 'max': hi}


WARREN_MAP = item('map', 1, extra=[
    {'type': 'minecraft:exploration_map', 'destination': f'#{NS}:warrens', 'decoration': 'minecraft:red_x', 'zoom': 2,
     'search_radius': 40, 'skip_existing_chunks': False},
    {'type': 'minecraft:set_name', 'target': 'item_name', 'name': {'translate': f'item.{NS}.silver_inked_map'}},
    {'type': 'minecraft:filtered', 'item_filter': {'predicates': {'minecraft:map_id': {}}}, 'on_fail': {'type': 'minecraft:discard'}}])
PATTERN = f'{NS}:plata_mark_banner_pattern'


def loot():
    chests = {
        'den': [
            {'rolls': rolls(3, 6), 'entries': [
                item('bread', 10, 1, 3), item('cooked_mutton', 6, 1, 3), item('baked_potato', 5, 1, 4),
                item(f'{NS}:silver_scale', 10, 1, 4), item(f'{NS}:silver_shard', 10, 2, 8), item('emerald', 8, 1, 4),
                item('iron_nugget', 8, 3, 9), item('string', 5, 1, 4), item('torch', 5, 2, 6), item('white_candle', 3, 1, 3)]},
            {'rolls': 1, 'entries': [empty(14), item(f'{NS}:plata_fang', 2), item(PATTERN, 2), {**WARREN_MAP, 'weight': 3}]},
        ],
        'warren_stores': [
            {'rolls': rolls(4, 8), 'entries': [
                item('bread', 10, 2, 4), item('cooked_mutton', 6, 1, 4), item('dried_kelp', 4, 2, 6), item('apple', 6, 1, 3),
                item(f'{NS}:silver_scale', 8, 2, 5), item(f'{NS}:silver_shard', 10, 4, 12), item('emerald', 8, 2, 5),
                item('leather', 5, 1, 3), item('arrow', 4, 4, 12), item('saddle', 1), item('lead', 2), item('compass', 1)]},
            {'rolls': 1, 'entries': [empty(10), item(f'{NS}:plata_fang', 2), item(PATTERN, 1), item('golden_apple', 1)]},
        ],
        'warren_forge': [
            {'rolls': rolls(3, 6), 'entries': [
                item('iron_ingot', 8, 1, 4), item('iron_nugget', 10, 4, 12), item(f'{NS}:silver_scale', 10, 3, 6),
                item(f'{NS}:silver_shard', 10, 6, 16), item('coal', 6, 2, 6), item('flint', 4, 1, 3), item('iron_chain', 3, 1, 3)]},
            {'rolls': 1, 'entries': [empty(4), item(f'{NS}:plata_fang', 3), item('iron_sword', 1, extra=[
                {'type': 'minecraft:enchant_randomly', 'options': '#minecraft:on_random_loot'}])]},
        ],
        'clan_coffer': [
            {'rolls': rolls(5, 8), 'entries': [
                item('emerald', 10, 4, 10), item(f'{NS}:silver_scale', 10, 4, 10), item('gold_ingot', 6, 2, 6),
                item('iron_ingot', 6, 3, 8), item('diamond', 3, 1, 3), item('golden_apple', 3), item('experience_bottle', 5, 2, 6),
                item('book', 4, extra=[{'type': 'minecraft:enchant_with_levels', 'levels': {'type': 'minecraft:uniform', 'min': 20, 'max': 39},
                                        'options': '#minecraft:on_random_loot'}]),
                item('name_tag', 2)]},
            {'rolls': 1, 'entries': [item(PATTERN, 1)]},
            {'rolls': 1, 'entries': [empty(3), item(f'{NS}:plata_mask', 2), item(f'{NS}:plata_fang', 3, extra=[
                {'type': 'minecraft:enchant_with_levels', 'levels': {'type': 'minecraft:uniform', 'min': 15, 'max': 30},
                 'options': '#minecraft:on_random_loot'}])]},
        ],
    }
    # what a Silver Mother leaves (her crest, named for her, is added by the game)
    enchanted = lambda lo, hi: [{'type': 'minecraft:enchant_with_levels', 'levels': {'type': 'minecraft:uniform', 'min': lo, 'max': hi},
                                 'options': '#minecraft:on_random_loot'}]
    write_json(f'data/{NS}/loot_table/entities/silver_mother.json', {
        'type': 'minecraft:entity', 'random_sequence': f'{NS}:entities/silver_mother',
        'pools': [
            {'rolls': 1, 'entries': [item('emerald', 1, 16, 28)]},
            {'rolls': 1, 'entries': [item(f'{NS}:silver_scale', 1, 12, 20)]},
            {'rolls': 1, 'entries': [item(f'{NS}:silver_shard', 1, 16, 32)]},
            {'rolls': 1, 'entries': [item(f'{NS}:plata_mask', 1)]},
            {'rolls': 1, 'entries': [item(f'{NS}:plata_fang', 1, extra=enchanted(25, 35))]},
            {'rolls': 1, 'entries': [item('golden_apple', 3, 1, 2), item('enchanted_golden_apple', 1)]},
            {'rolls': 1, 'entries': [item('diamond', 1, 2, 5)]},
            {'rolls': 1, 'entries': [item(PATTERN, 1)]},
        ]})
    for name, pools_ in chests.items():
        write_json(f'data/{NS}/loot_table/chests/{name}.json', {'type': 'minecraft:chest', 'pools': pools_,
                                                                'random_sequence': f'{NS}:chests/{name}'})
    # an empty husk gives up a couple of scales; a sealed one gives nothing (someone was in it)
    write_json(f'data/{NS}/loot_table/blocks/silver_husk.json', {
        'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/silver_husk',
        'pools': [{'rolls': 1, 'condition': {'type': 'minecraft:match_block', 'blocks': f'{NS}:silver_husk',
                                             'state': {'half': 'lower', 'split': 'true'}},
                   'entries': [item(f'{NS}:silver_scale', 1, 1, 2)], 'modifier': {'type': 'minecraft:explosion_decay'}}]})


def banner():
    write_json(f'data/{NS}/banner_pattern/plata_mark.json', {'asset_id': f'{NS}:plata_mark',
                                                             'translation_key': f'block.{NS}.banner.plata_mark'})
    write_json(f'data/{NS}/tags/banner_pattern/pattern_item/plata_mark.json', {'values': [f'{NS}:plata_mark']})


COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple',
          'blue', 'brown', 'green', 'red', 'black']


def lang():
    path = os.path.join(RES, 'assets', NS, 'lang', 'en_us.json')
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    data.update({
        f'block.{NS}.silver_husk': 'Silver Husk',
        f'block.{NS}.lookout': 'Plata Lookout',
        f'item.{NS}.plata_mark_banner_pattern': 'Banner Pattern',
        f'item.{NS}.plata_mark_banner_pattern.desc': 'Plata Mark',
        f'item.{NS}.plata_mark_banner_pattern.new': 'Plata Mark Banner Pattern',
        f'item.{NS}.silver_inked_map': 'Silver-Inked Map',
        f'container.{NS}.clan_coffer': 'Clan Coffer',
        f'block.{NS}.mother_seat': "Silver Mother's Dais",
        f'entity.{NS}.silver_mother': 'Silver Mother',
        f'item.{NS}.silver_mother_spawn_egg': 'Silver Mother Spawn Egg',
        f'item.{NS}.mother_crest': "Mother's Crest",
        f'item.{NS}.mother_crest.named': 'Crest of %s',
        f'item.{NS}.mother_crest.lore': 'Taken from a Silver Mother. Every Plata in the stone knows it.',
    })
    for c in COLORS:
        data[f'block.{NS}.banner.plata_mark.{c}'] = c.replace('_', ' ').title() + ' Plata Mark'
    for k in PALETTES:
        data[f'structure.{NS}.den_{k}'] = 'Scale Den'
        data[f'structure.{NS}.warren_{k}'] = 'Silver Warren'
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False) + '\n')


def main():
    shutil.rmtree(os.path.join(DATA, 'structure'), ignore_errors=True)
    shutil.rmtree(os.path.join(DATA, 'worldgen'), ignore_errors=True)
    catalog = {}
    pieces = all_pieces()
    for t in pieces:
        catalog[t.name] = t.save()
    pools()
    structures()
    loot()
    banner()
    lang()
    for name, info in catalog.items():
        husks = info['notes'].get('sleeper', 0) + info['notes'].get('elder', 0)
        if husks: print(f'  {name}: {husks} sealed husk(s)')
    print(f'{len(pieces)} templates, {len(PALETTES) * 2} structures, 2 structure sets')
    if '--render' in sys.argv:
        os.system(f'"{sys.executable}" "{os.path.join(os.path.dirname(__file__), "preview.py")}"')


if __name__ == '__main__':
    main()
