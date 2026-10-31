"""Models, blockstates, item models, recipes, tags, loot and lang for M2. Run from the repo root."""
import json, os

R = 'common/src/main/resources/'
A = R + 'assets/railworks/'
D = R + 'data/'

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f: json.dump(obj, f, indent=2); f.write('\n')

def cube(name, tex=None):
    w(A + 'models/block/%s.json' % name, {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'railworks:block/%s' % (tex or name)}})
    w(A + 'blockstates/%s.json' % name, {'variants': {'': {'model': 'railworks:block/%s' % name}}})
    block_item(name)

def block_item(name, model=None):
    w(A + 'items/%s.json' % name, {'model': {'type': 'minecraft:model', 'model': model or 'railworks:block/%s' % name}})

def flat_item(name, tex=None, parent='minecraft:item/generated'):
    w(A + 'models/item/%s.json' % name, {'parent': parent, 'textures': {'layer0': 'railworks:item/%s' % (tex or name)}})
    w(A + 'items/%s.json' % name, {'model': {'type': 'minecraft:model', 'model': 'railworks:item/%s' % name}})

def el(f, t, tex, faces='all', uv=None):
    out = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        if faces != 'all' and d not in faces: continue
        if d in ('north', 'south'): u = [f[0], 16 - t[1], t[0], 16 - f[1]]
        elif d in ('east', 'west'): u = [f[2], 16 - t[1], t[2], 16 - f[1]]
        else: u = [f[0], f[2], t[0], t[2]]
        u = [max(0, min(16, v)) for v in u]
        out[d] = {'uv': u, 'texture': '#' + (tex[d] if isinstance(tex, dict) else tex)}
    return {'from': f, 'to': t, 'faces': out}

cube('fire_bricks')
cube('set_steel')
cube('steel_block')
w(A + 'models/block/molten_steel.json', {'textures': {'particle': 'railworks:block/molten_steel_still'}})
w(A + 'blockstates/molten_steel.json', {'variants': {'': {'model': 'railworks:block/molten_steel'}}})

TEX = {'iron': 'railworks:block/coke_oven_iron', 'sand': 'minecraft:block/sand', 'wood': 'railworks:block/treated_planks',
       'steel': 'railworks:block/cast_steel', 'hot': 'railworks:block/molten_steel_still', 'red': 'railworks:block/cooling_steel',
       'dark': 'railworks:block/coke_oven_flue', 'particle': 'railworks:block/coke_oven_iron'}

def mould_elements(kind, stage, shift=0):
    z0 = 0 if kind == 'main' else 2
    z1 = 16 if kind == 'tail' else 14
    open_n = kind == 'main'
    open_s = kind == 'tail'
    e = []
    closed = stage in ('ready', 'filling', 'hot')
    h = 10 if closed else 5
    side = {'north': 'iron', 'south': 'iron', 'east': 'iron', 'west': 'iron', 'up': 'iron', 'down': 'iron'}
    if closed:
        e.append(el([2, 0, z0], [14, h, z1], dict(side, up='sand'), faces=[d for d in side if not (d == 'north' and open_n) and not (d == 'south' and open_s)]))
        if kind != 'tail':
            fill = {'ready': 'dark', 'filling': 'hot', 'hot': 'red'}[stage]
            e.append(el([6, 10, 6], [10, 11, 7], 'iron'))
            e.append(el([6, 10, 9], [10, 11, 10], 'iron'))
            e.append(el([6, 10, 7], [7, 11, 9], 'iron'))
            e.append(el([9, 10, 7], [10, 11, 9], 'iron'))
            e.append(el([7, 10, 7], [9, 10.5, 9], fill, faces=['up']))
            if stage == 'hot':
                for x, z in ((4, 3), (11, 12)):
                    e.append(el([x, 10, z], [x + 1, 10.2, z + 1], 'red', faces=['up']))
    else:
        e.append(el([2, 0, z0], [14, 1, z1], 'iron'))
        e.append(el([2, 1, z0], [3, h, z1], 'iron'))
        e.append(el([13, 1, z0], [14, h, z1], 'iron'))
        if not open_n: e.append(el([3, 1, z0], [13, h, z0 + 1], 'iron'))
        if not open_s: e.append(el([3, 1, z1 - 1], [13, h, z1], 'iron'))
        iz0 = z0 if open_n else z0 + 1
        iz1 = z1 if open_s else z1 - 1
        if stage == 'empty':
            e.append(el([3, 1, iz0], [13, 1.5, iz1], 'dark', faces=['up']))
        if stage == 'pattern':
            e.append(el([3, 1, iz0], [13, 1.5, iz1], 'dark', faces=['up']))
            if kind == 'single':
                for x in (4, 9):
                    for z in (4, 9):
                        e.append(el([x, 1, z], [x + 3, 3, z + 3], 'wood'))
            else:
                e.append(el([6, 1, iz0 + (1 if kind == 'tail' else 0)], [10, 3, iz1 - (1 if kind == 'main' else 0)], 'wood'))
        if stage == 'cast':
            e.append(el([3, 1, iz0], [13, 4.5, iz1], 'sand', faces=['up']))
            if kind == 'single':
                for x in (3.5, 8.5):
                    for z in (3.5, 8.5):
                        e.append(el([x, 4.5, z], [x + 4, 6.5, z + 4], 'steel'))
            else:
                a = iz0 + (1 if kind == 'tail' else 0)
                b = iz1 - (1 if kind == 'main' else 0)
                e.append(el([5.5, 4.5, a], [10.5, 5.5, b], 'steel'))
                e.append(el([7.5, 5.5, a], [8.5, 7, b], 'steel'))
                e.append(el([6.5, 7, a], [9.5, 9, b], 'steel'))
    if shift:
        for x in e:
            x['from'][2] += shift
            x['to'][2] += shift
    return e

STAGES = ['empty', 'pattern', 'ready', 'filling', 'hot', 'cast']
def model(path, elements, extra=None):
    m = {'parent': 'minecraft:block/block', 'textures': TEX, 'elements': elements}
    if extra: m.update(extra)
    w(A + 'models/block/%s.json' % path, m)

ROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
for mould, kinds in (('ingot_mould', ['single']), ('rail_bar_mould', ['main', 'tail'])):
    variants = {}
    for kind in kinds:
        for st in STAGES:
            name = '%s/%s_%s' % (mould, kind, st) if kind != 'single' else '%s/%s' % (mould, st)
            model(name, mould_elements(kind, st))
            for f, r in ROT.items():
                v = {'model': 'railworks:block/' + name}
                if r: v['y'] = r
                variants['facing=%s,stage=%s,tail=%s' % (f, st, 'true' if kind == 'tail' else 'false')] = v
    if mould == 'ingot_mould':
        for k in list(variants):
            variants[k.replace('tail=false', 'tail=true')] = variants[k]
    w(A + 'blockstates/%s.json' % mould, {'variants': variants})

block_item('ingot_mould', 'railworks:block/ingot_mould/cast')
GUI = {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.42, 0.42, 0.42]},
       'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
       'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
       'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.3, 0.3, 0.3]},
       'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]}}
els = mould_elements('main', 'cast', 8) + mould_elements('tail', 'cast', -8)
w(A + 'models/item/rail_bar_mould.json', {'parent': 'minecraft:block/block', 'textures': TEX, 'elements': els, 'display': GUI})
block_item('rail_bar_mould', 'railworks:item/rail_bar_mould')

rt = [el([0, 0, 0], [2, 15, 16], 'iron'), el([14, 0, 0], [16, 15, 16], 'iron'),
      el([2, 4, 1], [14, 6, 3], 'iron'), el([2, 4, 13], [14, 6, 15], 'iron')]
for z in (0.5, 4.5, 8.5, 12.5):
    rt.append(el([2, 13, z], [14, 16, z + 3], 'steel'))
model('roller_table', rt)
w(A + 'blockstates/roller_table.json', {'variants': {'axis=z': {'model': 'railworks:block/roller_table'}, 'axis=x': {'model': 'railworks:block/roller_table', 'y': 90}}})
block_item('roller_table')

for n in ('fire_clay', 'fire_brick', 'steel_ingot', 'rail_bar', 'rail_bar_pattern', 'ingot_pattern', 'molten_steel_bucket'):
    flat_item(n)
for n in ('hand_ladle', 'hand_ladle_full'):
    w(A + 'models/item/%s.json' % n, {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'railworks:item/' + n}})
w(A + 'items/hand_ladle.json', {'model': {'type': 'minecraft:condition', 'property': 'minecraft:custom_model_data', 'index': 0,
    'on_true': {'type': 'minecraft:model', 'model': 'railworks:item/hand_ladle_full'},
    'on_false': {'type': 'minecraft:model', 'model': 'railworks:item/hand_ladle'}}})

def recipe(name, obj): w(D + 'railworks/recipe/%s.json' % name, obj)
def shaped(name, pattern, key, result, count=1, cat='misc'):
    res = {'id': 'railworks:' + result}
    if count > 1: res['count'] = count
    recipe(name, {'type': 'minecraft:crafting_shaped', 'category': cat, 'key': key, 'pattern': pattern, 'result': res})
def shapeless(name, ings, result, count=1):
    res = {'id': result if ':' in result else 'railworks:' + result}
    if count > 1: res['count'] = count
    recipe(name, {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ings, 'result': res})

shapeless('fire_clay', ['minecraft:clay_ball', '#minecraft:sand'], 'fire_clay', 2)
recipe('fire_brick', {'type': 'minecraft:smelting', 'category': 'misc', 'cookingtime': 200, 'experience': 0.3, 'ingredient': 'railworks:fire_clay', 'result': {'id': 'railworks:fire_brick'}})
shaped('fire_bricks', ['BB', 'BB'], {'B': 'railworks:fire_brick'}, 'fire_bricks', cat='building')
shapeless('steel_ingot_from_set_steel', ['railworks:set_steel'], 'steel_ingot', 10)
shaped('steel_block', ['###', '###', '###'], {'#': 'railworks:steel_ingot'}, 'steel_block', cat='building')
shapeless('steel_ingot_from_steel_block', ['railworks:steel_block'], 'steel_ingot', 9)
shaped('rail_bar_mould', ['I I', 'IPI', 'IPI'], {'I': '#c:ingots/iron', 'P': 'railworks:treated_planks'}, 'rail_bar_mould')
shaped('ingot_mould', [' I ', 'IPI', ' I '], {'I': '#c:ingots/iron', 'P': 'railworks:treated_planks'}, 'ingot_mould')
shaped('roller_table', ['III', 'B B'], {'I': '#c:ingots/iron', 'B': 'railworks:treated_beam'}, 'roller_table', 4)
shaped('hand_ladle', ['I I', ' IB'], {'I': '#c:ingots/iron', 'B': 'railworks:treated_beam'}, 'hand_ladle', cat='equipment')
shaped('rail_bar_pattern', ['PPP'], {'P': 'railworks:treated_planks'}, 'rail_bar_pattern')
shaped('ingot_pattern', ['PP'], {'P': 'railworks:treated_planks'}, 'ingot_pattern')

def tag(path, values):
    p = D + path + '.json'
    old = []
    if os.path.exists(p):
        old = json.load(open(p))['values']
    w(p, {'values': old + [v for v in values if v not in old]})

tag('c/tags/item/ingots/steel', ['railworks:steel_ingot'])
tag('c/tags/item/ingots', ['railworks:steel_ingot'])
tag('c/tags/item/storage_blocks/steel', ['railworks:steel_block'])
tag('c/tags/item/storage_blocks', ['railworks:steel_block'])
tag('c/tags/block/storage_blocks/steel', ['railworks:steel_block'])
tag('c/tags/block/storage_blocks', ['railworks:steel_block'])
tag('c/tags/item/buckets/molten_steel', ['railworks:molten_steel_bucket'])
tag('c/tags/item/buckets', ['railworks:molten_steel_bucket', 'railworks:creosote_bucket'])
tag('c/tags/fluid/molten_steel', ['railworks:molten_steel', 'railworks:flowing_molten_steel'])
tag('railworks/tags/item/brick_furnace_iron', ['minecraft:iron_ingot', 'minecraft:raw_iron'])
tag('railworks/tags/item/brick_furnace_fuel', ['#c:coal_coke'])
tag('minecraft/tags/block/mineable/pickaxe', ['railworks:' + n for n in ('fire_bricks', 'brick_furnace', 'brick_furnace_part', 'set_steel', 'steel_block', 'rail_bar_mould', 'ingot_mould', 'roller_table')])
tag('minecraft/tags/block/needs_iron_tool', ['railworks:set_steel', 'railworks:steel_block'])

def loot(name, pools):
    w(D + 'railworks/loot_table/blocks/%s.json' % name, {'type': 'minecraft:block', 'pools': pools, 'random_sequence': 'railworks:blocks/' + name})
def self_pool(name, cond=None):
    p = {'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}] + (cond or []),
         'entries': [{'type': 'minecraft:item', 'name': 'railworks:' + name}], 'rolls': 1.0}
    return [p]
for n in ('fire_bricks', 'set_steel', 'steel_block', 'roller_table', 'ingot_mould'):
    loot(n, self_pool(n))
loot('rail_bar_mould', self_pool('rail_bar_mould', [{'condition': 'minecraft:block_state_property', 'block': 'railworks:rail_bar_mould', 'properties': {'tail': 'false'}}]))
loot('brick_furnace', [])
loot('brick_furnace_part', [])

lang_p = A + 'lang/en_us.json'
lang = json.load(open(lang_p))
lang.update({
    'item.railworks.fire_clay': 'Fire Clay',
    'item.railworks.fire_brick': 'Fire Brick',
    'item.railworks.steel_ingot': 'Steel Ingot',
    'item.railworks.rail_bar': 'Rail Bar',
    'item.railworks.rail_bar_pattern': 'Rail Bar Pattern',
    'item.railworks.ingot_pattern': 'Ingot Pattern',
    'item.railworks.hand_ladle': 'Hand Ladle',
    'item.railworks.hand_ladle.amount': 'Molten steel: %s / %s mB',
    'item.railworks.molten_steel_bucket': 'Molten Steel Bucket',
    'block.railworks.fire_bricks': 'Fire Bricks',
    'block.railworks.brick_furnace': 'Brick Furnace',
    'block.railworks.brick_furnace_part': 'Brick Furnace',
    'block.railworks.molten_steel': 'Molten Steel',
    'block.railworks.set_steel': 'Set Steel',
    'block.railworks.steel_block': 'Block of Steel',
    'block.railworks.rail_bar_mould': 'Rail Bar Mould',
    'block.railworks.ingot_mould': 'Ingot Mould',
    'block.railworks.roller_table': 'Roller Table',
    'fluid_type.railworks.molten_steel': 'Molten Steel',
    'fluid.railworks.molten_steel': 'Molten Steel',
    'container.railworks.brick_furnace': 'Brick Furnace',
    'gui.railworks.steel_tank': 'Molten steel: %s / %s mB',
    'message.railworks.brick_furnace.formed': 'Brick furnace formed',
    'message.railworks.brick_furnace.incomplete': 'The brick furnace needs a hollow 3x3 shaft of fire bricks, 5 high (40 bricks). Hit the middle brick of the bottom row on a side',
    'message.railworks.brick_furnace.tap_open': 'Tap open',
    'message.railworks.brick_furnace.tap_shut': 'Tap shut',
    'message.railworks.mould.cooling': 'Still cooling: %s s',
})
w(lang_p, lang)
print('ok')
