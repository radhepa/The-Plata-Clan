"""Building blocks for the Plata Clan hideouts: block templates written as native, compressed
Minecraft 26.3 structure files (standard library only), plus checks that every block and
property exists in vanilla's own blockstate files.

A Template holds only the blocks it sets. Anything left unset is a structure void, so the
world's own block stays there (terrain around a surface camp, for instance).
"""
import gzip
import json
import os
import random
import re
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'plataclan'
DATA_VERSION = 5023  # Minecraft 26.3, as in vanilla's own templates
CACHE = os.path.join(HERE, '.cache', 'assets', 'minecraft')

# Our own blocks, with the properties they take.
MOD_BLOCKS = {
    'plataclan:silver_husk': {'half': {'lower', 'upper'}, 'facing': {'north', 'south', 'east', 'west'},
                              'kind': {'sleeper', 'elder'}, 'split': {'true', 'false'}},
    'plataclan:lookout': {},
}
# Properties that never show up in blockstate files because no model depends on them.
HIDDEN_PROPERTIES = {'waterlogged', 'powered', 'persistent', 'distance', 'occupied', 'triggered', 'enabled',
                     'signal_fire', 'stage', 'note', 'instrument', 'has_book', 'cracked', 'ominous', 'disarmed',
                     'attached', 'conditional', 'lit', 'hanging', 'open', 'bottom', 'in_wall', 'inverted'}


class Byte(int):
    """An NBT byte (TAG_Byte) rather than an int."""


def _tag_type(value):
    if isinstance(value, (Byte, bool)): return 1
    if isinstance(value, int): return 3
    if isinstance(value, float): return 6
    if isinstance(value, str): return 8
    if isinstance(value, list): return 9
    if isinstance(value, dict): return 10
    raise TypeError(value)


def _string(value):
    encoded = value.encode('utf-8')
    return struct.pack('>H', len(encoded)) + encoded


def _payload(value):
    kind = _tag_type(value)
    if kind == 1: return struct.pack('>b', int(value))
    if kind == 3: return struct.pack('>i', value)
    if kind == 6: return struct.pack('>d', value)
    if kind == 8: return _string(value)
    if kind == 9:
        element = _tag_type(value[0]) if value else 10
        assert all(_tag_type(v) == element for v in value), value
        return bytes([element]) + struct.pack('>i', len(value)) + b''.join(_payload(v) for v in value)
    return b''.join(bytes([_tag_type(v)]) + _string(k) + _payload(v) for k, v in value.items()) + b'\0'


def write_nbt(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(gzip.compress(b'\x0a\x00\x00' + _payload(value), mtime=0))


def write_json(rel, value):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(value, indent=2) + '\n')


# -- block states ------------------------------------------------------------------------------

_STATE = re.compile(r'^([a-z0-9_:]+)(?:\[(.*)\])?$')


def parse(spec):
    """'oak_stairs[facing=north,half=top]' -> ('minecraft:oak_stairs', {'facing': 'north', 'half': 'top'})."""
    m = _STATE.match(spec)
    assert m, spec
    name = m.group(1) if ':' in m.group(1) else 'minecraft:' + m.group(1)
    props = {}
    if m.group(2):
        for part in m.group(2).split(','):
            k, v = part.split('=')
            props[k.strip()] = v.strip()
    return name, props


def spec(name, props):
    short = name[len('minecraft:'):] if name.startswith('minecraft:') else name
    if not props: return short
    return short + '[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']'


def full_spec(name, props):
    """With the namespace, as a jigsaw's final_state wants it."""
    return name + ('[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']' if props else '')


_known = {}


def _vanilla_properties(name):
    """Property names and values a vanilla block shows in its blockstate file, or None if no such block."""
    if name in _known: return _known[name]
    path = os.path.join(CACHE, 'blockstates', name.split(':')[1] + '.json')
    if not os.path.exists(path):
        _known[name] = None
        return None
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    props = {}

    def add(key):
        for part in key.split(','):
            if '=' in part:
                k, v = part.split('=')
                props.setdefault(k, set()).update(v.split('|'))

    for key in data.get('variants', {}):
        add(key)
    for part in data.get('multipart', []):
        when = part.get('when', {})
        for cond in when.get('OR', [when]) if 'OR' in when else [when]:
            for k, v in cond.items():
                props.setdefault(k, set()).update(str(v).split('|'))
    _known[name] = props
    return props


def validate_state(name, props, where=''):
    if name.startswith(NS + ':'):
        allowed = MOD_BLOCKS.get(name)
        assert allowed is not None, f'unknown mod block {name} {where}'
        for k, v in props.items():
            assert k in allowed and v in allowed[k], f'bad property {k}={v} on {name} {where}'
        return
    known = _vanilla_properties(name)
    assert known is not None, f'unknown block {name} {where}'
    for k, v in props.items():
        if k in known:
            assert v in known[k], f'bad value {k}={v} on {name} {where} (allowed {sorted(known[k])})'
        else:
            assert k in HIDDEN_PROPERTIES or k in ('axis', 'rotation', 'facing', 'age', 'level', 'layers', 'type'), \
                f'unknown property {k} on {name} {where}'


# -- templates ---------------------------------------------------------------------------------

HORIZONTAL = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east', 'up': 'down', 'down': 'up'}
LEFT = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}
RIGHT = {v: k for k, v in LEFT.items()}


class Template:
    def __init__(self, name, size, seed=None):
        self.name, self.size = name, tuple(size)
        self.blocks = {}   # (x, y, z) -> (name, props)
        self.nbt = {}      # (x, y, z) -> block entity compound
        self.entities = []
        self.jigsaws = []
        self.notes = {}    # free-form facts for tests and docs (husk counts, chests, ...)
        self.rng = random.Random(seed if seed is not None else name)

    def inside(self, x, y, z):
        return all(0 <= p < s for p, s in zip((x, y, z), self.size))

    def set(self, x, y, z, block, nbt=None):
        assert self.inside(x, y, z), (self.name, x, y, z, block)
        if block is None:
            self.blocks.pop((x, y, z), None)
            self.nbt.pop((x, y, z), None)
            return
        name, props = parse(block) if isinstance(block, str) else block
        self.blocks[(x, y, z)] = (name, props)
        if nbt is not None: self.nbt[(x, y, z)] = nbt
        else: self.nbt.pop((x, y, z), None)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return None if b is None else spec(*b)

    def name_at(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return None if b is None else b[0]

    def fill(self, a, b, block):
        """Fill the box a..b (inclusive). `block` may be a callable (x, y, z) -> spec."""
        for y in range(min(a[1], b[1]), max(a[1], b[1]) + 1):
            for z in range(min(a[2], b[2]), max(a[2], b[2]) + 1):
                for x in range(min(a[0], b[0]), max(a[0], b[0]) + 1):
                    self.set(x, y, z, block(x, y, z) if callable(block) else block)

    def walls(self, a, b, block):
        """Only the four vertical sides of the box a..b."""
        for y in range(a[1], b[1] + 1):
            for x in range(a[0], b[0] + 1):
                for z in (a[2], b[2]): self.set(x, y, z, block(x, y, z) if callable(block) else block)
            for z in range(a[2], b[2] + 1):
                for x in (a[0], b[0]): self.set(x, y, z, block(x, y, z) if callable(block) else block)

    def jigsaw(self, x, y, z, orientation, name, target='minecraft:empty', pool='minecraft:empty', final='air',
               joint='aligned', placement=0, selection=0):
        q = lambda s: s if ':' in s else NS + ':' + s
        final_name, final_props = parse(final)
        validate_state(final_name, final_props, f'final state in {self.name}')
        self.set(x, y, z, f'jigsaw[orientation={orientation}]', {
            'id': 'minecraft:jigsaw', 'name': q(name), 'target': q(target), 'pool': q(pool),
            'final_state': full_spec(final_name, final_props),
            'joint': joint, 'placement_priority': placement, 'selection_priority': selection})
        self.jigsaws.append({'pos': (x, y, z), 'orientation': orientation, 'name': q(name), 'target': q(target), 'pool': q(pool)})

    def chest(self, x, y, z, facing, loot, kind='chest', custom_name=None):
        props = {'facing': facing} if kind != 'barrel' else {'facing': facing, 'open': 'false'}
        if kind == 'chest': props.update({'type': 'single', 'waterlogged': 'false'})
        nbt = {'id': 'minecraft:' + kind, 'LootTable': loot if ':' in loot else NS + ':' + loot}
        if custom_name: nbt['CustomName'] = {'translate': custom_name}
        self.set(x, y, z, ('minecraft:' + kind, props), nbt)
        self.notes.setdefault('loot', []).append(nbt['LootTable'])

    def husk(self, x, y, z, facing, kind='sleeper'):
        """A sealed silver husk (two blocks tall) with a member asleep inside."""
        self.set(x, y, z, ('plataclan:silver_husk', {'half': 'lower', 'facing': facing, 'kind': kind, 'split': 'false'}),
                 {'id': 'plataclan:silver_husk'})
        self.set(x, y + 1, z, ('plataclan:silver_husk', {'half': 'upper', 'facing': facing, 'kind': kind, 'split': 'false'}))
        self.notes[kind] = self.notes.get(kind, 0) + 1

    def lookout(self, x, y, z):
        """Where the hideout's disguised lookout waits (spawned when a player comes near)."""
        self.set(x, y, z, 'plataclan:lookout', {'id': 'plataclan:lookout'})
        self.notes['lookout'] = (x, y, z)

    def banner(self, x, y, z, facing, color='light_gray', pattern_color='gray'):
        """A wall banner bearing the clan's mark, hung on the block behind it (facing away from that wall)."""
        self.set(x, y, z, f'{color}_wall_banner[facing={facing}]', {'id': 'minecraft:banner', 'patterns': [
            {'pattern': 'plataclan:plata_mark', 'color': pattern_color}]})

    def spawner(self, x, y, z, mob='minecraft:silverfish'):
        self.set(x, y, z, 'spawner', {'id': 'minecraft:mob_spawner', 'SpawnData': {'entity': {'id': mob}},
                                      'SpawnCount': 2, 'MaxNearbyEntities': 5, 'RequiredPlayerRange': 10,
                                      'MinSpawnDelay': 300, 'MaxSpawnDelay': 700, 'Delay': 100})

    def validate(self):
        for pos, (name, props) in self.blocks.items():
            validate_state(name, props, f'at {pos} in {self.name}')
        for j in self.jigsaws:
            x, y, z = j['pos']
            front = j['orientation'].split('_')[0]
            # A jigsaw must face out of the template, or its partner would land inside it.
            dx, dy, dz = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0),
                          'up': (0, 1, 0), 'down': (0, -1, 0)}[front]
            assert not self.inside(x + dx, y + dy, z + dz), (self.name, 'jigsaw faces inward', j)
        for pos, nbt in self.nbt.items():
            assert pos in self.blocks, (self.name, 'block entity without a block', pos)

    def save(self):
        self.validate()
        palette, index, blocks = [], {}, []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            name, props = self.blocks[pos]
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = {'id': name}
                if props: entry['properties'] = dict(sorted(props.items()))
                palette.append(entry)
            entry = {'pos': list(pos), 'state': index[key]}
            if pos in self.nbt: entry['nbt'] = self.nbt[pos]
            blocks.append(entry)
        data = {'DataVersion': DATA_VERSION, 'size': list(self.size), 'palette': palette, 'blocks': blocks, 'entities': self.entities}
        write_nbt(os.path.join(RES, 'data', NS, 'structure', self.name + '.nbt'), data)
        return {'id': f'{NS}:{self.name}', 'size': list(self.size), 'blocks': len(self.blocks),
                'jigsaws': [{**j, 'pos': list(j['pos'])} for j in self.jigsaws], 'notes': self.notes}


# -- small helpers for rotations within a design ---------------------------------------------

def facing_of(dx, dz):
    return {(0, -1): 'north', (0, 1): 'south', (-1, 0): 'west', (1, 0): 'east'}[(dx, dz)]
