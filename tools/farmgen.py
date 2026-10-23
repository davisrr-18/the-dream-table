"""Builds the Medium and Big farm schematics from Davi's hand-made Tiny designs.

Run from the project root: python3 tools/farmgen.py
The files go to src/main/resources/data/the_dream_table/structures/ as Sponge v3 schematics, like the originals.

Piston rows (sugar cane, bamboo) grow upwards, so the footprint stays small: each layer is one row of up to 15
columns, the most a single redstone line reaches, stacked every 5 blocks. Items fall to the hopper line under each
row and run down a hopper column just outside the side wall into a double chest. The walls stay solid stone:
a hopper inside the wall would leave gaps around its narrow middle where loose items get stuck. Big alternates its layers between two
full-height columns and two double chests, so it holds twice as much.

The crop farm's Tiny is the top terrace of the original, with the collection channel moved right under it.

The mob farm's Big stacks more spawning floors on the original's drop. It has no Tiny: a dark room small enough
for Tiny has too little spawning floor to work.
"""

import gzip
import io
import os
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STRUCTURES = os.path.join(ROOT, "src", "main", "resources", "data", "the_dream_table", "structures")

AIR = "minecraft:air"
BASE = "minecraft:stone_bricks"
STONE = "minecraft:stone"
GLASS = "minecraft:glass"
MUD = "minecraft:mud"
WATER = "minecraft:water[level=0]"
PISTON = "minecraft:piston[extended=false,facing=north]"
OBSERVER = "minecraft:observer[facing=north,powered=false]"
CHEST_LEFT = "minecraft:chest[facing=north,type=left,waterlogged=false]"
CHEST_RIGHT = "minecraft:chest[facing=north,type=right,waterlogged=false]"
WIRE_FIRST = "minecraft:redstone_wire[east=side,north=side,power=0,south=none,west=none]"
WIRE_MIDDLE = "minecraft:redstone_wire[east=side,north=side,power=0,south=none,west=side]"
WIRE_LAST = "minecraft:redstone_wire[east=none,north=side,power=0,south=none,west=side]"
TORCH_EAST = "minecraft:wall_torch[facing=east]"
TORCH_WEST = "minecraft:wall_torch[facing=west]"
CANE = "minecraft:sugar_cane[age=0]"
BAMBOO = "minecraft:bamboo_sapling"


def hopper(facing):
    return f"minecraft:hopper[enabled=true,facing={facing}]"


class Grid:
    def __init__(self):
        self.cells = {}

    def set(self, x, y, z, state):
        self.cells[(x, y, z)] = state

    def get(self, x, y, z):
        return self.cells.get((x, y, z), AIR)

    def count(self):
        return sum(1 for state in self.cells.values() if state != AIR)


# Piston rows

LAYER_HEIGHT = 5


def piston_row(columns, layers, plant, split):
    """One row per layer, stacked. With split, layers take turns draining left and right, into two full-height
    hopper columns and two double chests, so each side carries half the items."""
    grid = Grid()
    left, right = 1, columns + 2
    first, last = 2, columns + 1
    top = (layers - 1) * LAYER_HEIGHT

    # Ground slab and storage, like the original's base.
    for x in range(left - 1, right + 2):
        for z in range(0, 7):
            grid.set(x, 0, z, BASE)
    grid.set(left - 1, 0, 1, CHEST_LEFT)
    grid.set(left, 0, 1, CHEST_RIGHT)
    if split:
        grid.set(right, 0, 1, CHEST_LEFT)
        grid.set(right + 1, 0, 1, CHEST_RIGHT)
    for x in (left - 1, right + 1):
        for z in range(0, 7):
            if grid.get(x, 0, z) == AIR:
                grid.set(x, 0, z, BASE)

    for layer in range(layers):
        y = layer * LAYER_HEIGHT
        drains_right = split and layer % 2 == 1
        # Floor of the layer: the hopper line, under the plants, runs to the side column.
        for x in range(left, right + 1):
            for z in (1, 3, 4, 5):
                if not (layer == 0 and z == 1 and x == left):
                    if grid.get(x, y, z) not in (CHEST_LEFT, CHEST_RIGHT):
                        grid.set(x, y, z, BASE)
        # Under the side walls the line passes into the hopper column outside, where no loose item can reach it.
        grid.set(left, y, 2, hopper("west"))
        grid.set(right, y, 2, hopper("east") if split else BASE)
        for x in range(first, last + 1):
            grid.set(x, y, 2, hopper("east" if drains_right else "west"))
            grid.set(x, y + 1, 1, GLASS)
            grid.set(x, y + 1, 2, MUD)
            grid.set(x, y + 1, 3, WATER if plant == CANE else STONE)
            grid.set(x, y + 1, 4, STONE)
            grid.set(x, y + 2, 1, GLASS)
            grid.set(x, y + 2, 2, plant)
            grid.set(x, y + 2, 4, STONE)
            grid.set(x, y + 3, 1, GLASS)
            grid.set(x, y + 3, 3, PISTON)
            grid.set(x, y + 3, 4, STONE)
            grid.set(x, y + 4, 1, GLASS)
            grid.set(x, y + 4, 3, OBSERVER)
            grid.set(x, y + 4, 5, STONE)
            wire = WIRE_FIRST if x == first else WIRE_LAST if x == last else WIRE_MIDDLE
            grid.set(x, y + 4, 4, wire)
        # Side walls; the plants' slot gets a torch at each end so nothing spawns inside at night.
        for wall in (left, right):
            for dy in range(1, 5):
                grid.set(wall, y + dy, 2, STONE)
                grid.set(wall, y + dy, 3, STONE)
            grid.set(wall, y + 4, 4, STONE)
        grid.set(first, y + 2, 3, TORCH_EAST)
        grid.set(last, y + 2, 3, TORCH_WEST)

    # Roof over the top layer.
    for x in range(left, right + 1):
        for z in (1, 2, 3, 4, 5):
            grid.set(x, top + LAYER_HEIGHT, z, STONE)

    # Hopper columns down the side walls into the chests.
    hopper_column(grid, left - 1, top)
    if split:
        hopper_column(grid, right + 1, top)
    return grid


def hopper_column(grid, x, height):
    """Hoppers from the highest layer it serves down to the ground, the last one feeding the chest in front."""
    for y in range(height, 0, -1):
        grid.set(x, y, 2, hopper("down"))
    grid.set(x, 0, 2, hopper("north"))


# Crop terrace

def crop_tiny():
    """The top terrace of the original: the dirt and trapdoor row, six farmland rows, then the collection channel."""
    original = read_schematic(os.path.join(STRUCTURES, "crop_farm.schem"))
    grid = Grid()
    shift = -2
    for (x, y, z), state in original.items():
        if 3 <= y <= 5 and 3 <= z <= 10 and 3 <= x <= 21:
            grid.set(x - 2, y + shift, z - 2, state)
    channel_z = 9
    for (x, y, z), state in original.items():
        if y == 1 and z in (27, 28) and 3 <= x <= 21:
            grid.set(x - 2, 1, channel_z + (z - 27), state)
    for x in range(1, 20):
        for z in range(1, 11):
            grid.set(x, 0, z, BASE)
    return grid


# Dark room mob farm

MOB_FLOOR = 124
MOB_PERIOD = 5
MOB_HOLE = [(10, 9), (11, 9), (10, 10), (11, 10)]


def mob_big(floors):
    """The original with more spawning floors on the same drop. Each new floor rests on a copy of the original roof,
    which is also the ceiling of the floor below, with the drop opened so mobs from above fall straight through."""
    original = read_schematic(os.path.join(STRUCTURES, "mob_farm.schem"))
    grid = Grid()
    for (x, y, z), state in original.items():
        if y < MOB_FLOOR:
            grid.set(x, y, z, state)

    def copy_layer(source_y, target_y, open_hole=False):
        for (x, y, z), state in original.items():
            if y == source_y and not (open_hole and (x, z) in MOB_HOLE):
                grid.set(x, target_y, z, state)

    for floor in range(floors):
        base = MOB_FLOOR + floor * MOB_PERIOD
        if floor == 0:
            copy_layer(MOB_FLOOR, base)
        else:
            copy_layer(MOB_FLOOR + MOB_PERIOD, base, open_hole=True)
        for offset in range(1, MOB_PERIOD):
            copy_layer(MOB_FLOOR + offset, base + offset)
    top = MOB_FLOOR + floors * MOB_PERIOD
    copy_layer(MOB_FLOOR + MOB_PERIOD, top)
    copy_layer(MOB_FLOOR + MOB_PERIOD + 1, top + 1)
    return grid


# Schematic files

def read_schematic(path):
    root = read_nbt(gzip.decompress(open(path, "rb").read()))
    schem = root.get("Schematic", root)
    w, h, l = schem["Width"], schem["Height"], schem["Length"]
    blocks = schem["Blocks"]
    names = {v: k for k, v in blocks["Palette"].items()}
    data = varints(blocks["Data"])
    cells = {}
    for y in range(h):
        for z in range(l):
            for x in range(w):
                name = names[data[(y * l + z) * w + x]]
                if name != AIR:
                    cells[(x, y, z)] = name
    return cells


def write_schematic(grid, path):
    cells = {key: state for key, state in grid.cells.items() if state != AIR}
    xs = [key[0] for key in cells]
    ys = [key[1] for key in cells]
    zs = [key[2] for key in cells]
    min_x, min_y, min_z = min(xs), min(ys), min(zs)
    width, height, length = max(xs) - min_x + 1, max(ys) - min_y + 1, max(zs) - min_z + 1
    palette = {AIR: 0}
    data = bytearray()
    block_entities = []
    for y in range(height):
        for z in range(length):
            for x in range(width):
                state = cells.get((x + min_x, y + min_y, z + min_z), AIR)
                if state not in palette:
                    palette[state] = len(palette)
                write_varint(data, palette[state])
                if state.startswith("minecraft:chest") or state.startswith("minecraft:hopper"):
                    block_id = state.split("[")[0]
                    block_entities.append({"Id": ("str", block_id), "Pos": ("int_array", [x, y, z]),
                                           "Data": ("compound", {"id": ("str", block_id), "Items": ("list_compound", [])})})
    schematic = {
        "Version": ("int", 3),
        "DataVersion": ("int", 3955),
        "Width": ("int", width),
        "Height": ("int", height),
        "Length": ("int", length),
        "Offset": ("int_array", [0, 0, 0]),
        "Blocks": ("compound", {
            "Palette": ("compound", {name: ("int", index) for name, index in palette.items()}),
            "Data": ("bytes", bytes(data)),
            "BlockEntities": ("list_compound", [("compound", entity) for entity in block_entities]),
        }),
    }
    out = io.BytesIO()
    out.write(b"\x0a")
    write_string(out, "")
    write_compound(out, {"Schematic": ("compound", schematic)})
    with open(path, "wb") as file:
        file.write(gzip.compress(out.getvalue()))
    steps = sum(1 for state in cells.values() if not (is_liquid(state) and "level=0" not in state))
    return width, height, length, steps


def is_liquid(state):
    """Flowing water and lava are not build steps: they spread from the source blocks."""
    return state.startswith("minecraft:water") or state.startswith("minecraft:lava")


def write_varint(data, value):
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            data.append(byte | 0x80)
        else:
            data.append(byte)
            return


def write_string(out, text):
    raw = text.encode("utf-8")
    out.write(struct.pack(">H", len(raw)))
    out.write(raw)


TAGS = {"int": 3, "bytes": 7, "str": 8, "list_compound": 9, "compound": 10, "int_array": 11}


def write_compound(out, entries):
    for name, (kind, value) in entries.items():
        out.write(bytes([TAGS[kind]]))
        write_string(out, name)
        write_payload(out, kind, value)
    out.write(b"\x00")


def write_payload(out, kind, value):
    if kind == "int":
        out.write(struct.pack(">i", value))
    elif kind == "bytes":
        out.write(struct.pack(">i", len(value)))
        out.write(value)
    elif kind == "str":
        write_string(out, value)
    elif kind == "list_compound":
        out.write(bytes([TAGS["compound"]]))
        out.write(struct.pack(">i", len(value)))
        for _, entry in value:
            write_compound(out, entry)
    elif kind == "compound":
        write_compound(out, value)
    elif kind == "int_array":
        out.write(struct.pack(">i", len(value)))
        out.write(struct.pack(">" + "i" * len(value), *value))


def read_nbt(data):
    pos = [0]

    def take(n):
        chunk = data[pos[0]:pos[0] + n]
        pos[0] += n
        return chunk

    def string():
        (length,) = struct.unpack(">H", take(2))
        return take(length).decode("utf-8", "replace")

    def payload(tag):
        formats = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if tag in formats:
            size = struct.calcsize(formats[tag])
            return struct.unpack(formats[tag], take(size))[0]
        if tag == 7:
            (n,) = struct.unpack(">i", take(4))
            return take(n)
        if tag == 8:
            return string()
        if tag == 9:
            inner = take(1)[0]
            (n,) = struct.unpack(">i", take(4))
            return [payload(inner) for _ in range(n)]
        if tag == 10:
            out = {}
            while True:
                inner = take(1)[0]
                if inner == 0:
                    return out
                name = string()
                out[name] = payload(inner)
        if tag == 11:
            (n,) = struct.unpack(">i", take(4))
            return list(struct.unpack(">" + "i" * n, take(4 * n)))
        if tag == 12:
            (n,) = struct.unpack(">i", take(4))
            return list(struct.unpack(">" + "q" * n, take(8 * n)))
        raise ValueError(tag)

    tag = take(1)[0]
    string()
    return payload(tag)


def varints(raw):
    values, value, shift = [], 0, 0
    for byte in raw:
        value |= (byte & 0x7F) << shift
        if byte & 0x80:
            shift += 7
        else:
            values.append(value)
            value, shift = 0, 0
    return values


FARMS = {
    "sugar_cane_farm_medium": lambda: piston_row(15, 4, CANE, False),
    "sugar_cane_farm_big": lambda: piston_row(15, 14, CANE, True),
    "bamboo_farm_medium": lambda: piston_row(15, 4, BAMBOO, False),
    "bamboo_farm_big": lambda: piston_row(15, 14, BAMBOO, True),
    "crop_farm_tiny": crop_tiny,
    "mob_farm_big": lambda: mob_big(4),
}

if __name__ == "__main__":
    for name, build in FARMS.items():
        size = write_schematic(build(), os.path.join(STRUCTURES, name + ".schem"))
        print(f"{name}: {size[0]}x{size[1]}x{size[2]}, {size[3]} build steps")
