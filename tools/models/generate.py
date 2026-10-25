"""Генератор объёмных моделей OrbitCore: ракета по модулям и скафандр.

Модели собираются из кубов, как в Blockbench, и сохраняются сразу в трёх видах:

* ``assets/orbitcore/geo/...geo.json`` — геометрия для GeckoLib (её читает игра);
* ``assets/orbitcore/textures/...png`` — развёртка текстуры, нарисованная кодом;
* ``blockbench/*.bbmodel`` — проект Blockbench с той же моделью и текстурой,
  чтобы её можно было открыть, посмотреть и поправить руками.

    python tools/models/generate.py

Нужен Python 3 и Pillow. Единица длины — пиксель Minecraft (16 = один блок).
"""
import base64
import io
import json
import math
import random
import uuid
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "orbitcore"
BLOCKBENCH = ROOT / "blockbench"

FACES = ("north", "south", "east", "west", "up", "down")


# ================================================================ модель

class Cube:
    def __init__(self, origin, size, material, faces=FACES, inflate=0.0):
        self.origin = [float(v) for v in origin]
        self.size = [float(v) for v in size]
        self.material = material
        self.faces = faces
        self.inflate = inflate
        self.uv = {}

    def face_size(self, face):
        w, h, d = self.size
        return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}[face]


class Bone:
    def __init__(self, name, pivot=(0, 0, 0), parent=None, rotation=None):
        self.name = name
        self.pivot = list(pivot)
        self.parent = parent
        self.rotation = rotation
        self.cubes = []

    def add(self, origin, size, material, **kw):
        cube = Cube(origin, size, material, **kw)
        self.cubes.append(cube)
        return cube


class Model:
    def __init__(self, name, atlas_width=256):
        self.name = name
        self.bones = []
        self.atlas_width = atlas_width

    def bone(self, name, **kw):
        bone = Bone(name, **kw)
        self.bones.append(bone)
        return bone


# ================================================================ материалы

def noise(rng, color, spread):
    return tuple(max(0, min(255, c + rng.randint(-spread, spread))) for c in color)


def shade(color, k):
    return tuple(max(0, min(255, int(c * k))) for c in color)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


WHITE = (236, 238, 242)
BLACK = (34, 36, 42)
STEEL = (150, 156, 166)
DARK = (70, 74, 82)
RED = (196, 46, 38)
ORANGE = (232, 118, 40)
YELLOW = (236, 186, 40)
GOLD = (214, 168, 64)
COPPER = (176, 104, 62)
GLASS = (36, 58, 92)


def paint(material, face, w, h, cube, rng):
    """Рисует грань куба. Строка 0 — верх грани; для боковых граней строке r
    соответствует мировая высота y = верх куба − r, поэтому полосы на соседних
    кубах совпадают."""
    img = Image.new("RGBA", (w, h))
    y_top = cube.origin[1] + cube.size[1]
    side = face not in ("up", "down")
    for r in range(h):
        y = y_top - r - 1 if side else None
        for c in range(w):
            img.putpixel((c, r), pixel(material, face, c, r, w, h, y, cube, rng) + (255,))
    return img


def pixel(material, face, c, r, w, h, y, cube, rng):
    edge = c == 0 or c == w - 1
    if material == "hull" or material == "hull_roll":
        color = noise(rng, WHITE, 4)
        if material == "hull_roll" and y is not None:
            # Чёрно-белая «шахматка» по кругу, как на первых ракетах: видно вращение
            band = int(y // 32)
            parity = {"north": 0, "east": 1, "south": 0, "west": 1}[face] + (1 if cube.size[0] < cube.size[2] else 0)
            if (band + parity) % 2 == 0 and cube.size[0] != cube.size[2]:
                color = noise(rng, BLACK, 3)
        if y is not None and int(y) % 16 == 0:
            color = shade(color, 0.82)
            if c % 4 == 2:
                color = shade(color, 0.7)
        return color
    if material == "ring":
        return shade(noise(rng, DARK, 4), 1.25 if r == 0 else 1.0)
    if material == "hazard":
        return YELLOW if ((c + r) // 3) % 2 else BLACK
    if material == "steel":
        color = noise(rng, STEEL, 6)
        return shade(color, 0.8) if r == h - 1 or edge else color
    if material == "dark":
        return noise(rng, DARK, 5)
    if material == "black":
        return noise(rng, BLACK, 3)
    if material == "truss":
        # Ферма: светлые диагонали на тёмном фоне
        bg = noise(rng, (46, 48, 54), 3)
        k = (c + r) % 8
        k2 = (c - r) % 8
        return noise(rng, (120, 124, 132), 4) if k == 0 or k2 == 0 or r == 0 or r == h - 1 else bg
    if material == "gold_foil":
        base = mix(GOLD, (250, 220, 120), rng.random() * 0.5)
        return shade(base, 0.75 + rng.random() * 0.35)
    if material in ("bell", "bell_gold"):
        # Сопло темнеет к срезу и отливает синевой от нагрева
        t = 0 if y is None else max(0.0, min(1.0, (y - cube_bottom(cube)) / max(1.0, bell_height(cube))))
        if material == "bell_gold":
            color = mix((150, 104, 44), (228, 186, 92), t)
        else:
            color = mix((84, 78, 98), (168, 112, 66), t)
        if face == "down":
            return pixel("nozzle", face, c, r, w, h, y, cube, rng)
        color = noise(rng, color, 5)
        if y is not None and int(y) % 5 == 0:
            color = shade(color, 0.8)
        if edge:
            color = shade(color, 0.85)
        return color
    if material == "nozzle":
        # Срез сопла снизу: тёмная глубина и светлый край
        if r == 0 or c == 0 or r == h - 1 or c == w - 1:
            return (130, 120, 120)
        d = min(r, c, h - 1 - r, w - 1 - c)
        return shade((40, 30, 32), 1.0 - min(0.6, d * 0.08))
    if material == "pipe":
        return noise(rng, (190, 196, 204), 6) if c % 2 == 0 else noise(rng, (150, 156, 166), 4)
    if material == "copper_pipe":
        return noise(rng, COPPER, 8)
    if material == "heat_shield":
        return noise(rng, (64, 48, 38), 10)
    if material == "capsule":
        color = noise(rng, (210, 214, 222), 5)
        if y is not None and int(y) % 4 == 0:
            color = shade(color, 0.86)
        return color
    if material == "window":
        color = GLASS
        if r < h // 2 and c < w // 2 and (c + r) % 3 == 0:
            color = (120, 160, 210)
        return noise(rng, color, 4)
    if material == "red":
        return noise(rng, RED, 6)
    if material == "red_white":
        return noise(rng, RED if (y is not None and int(y) % 4 < 2) else WHITE, 5)
    if material == "fin":
        color = noise(rng, WHITE, 4)
        if y is not None and y - cube.origin[1] < 3:
            color = noise(rng, RED, 6)
        return color
    if material == "leg":
        color = noise(rng, (176, 182, 190), 5)
        return shade(color, 0.8) if edge else color
    if material == "foot":
        return noise(rng, (110, 114, 122), 6)
    if material == "lamp":
        return (255, 236, 170) if 0 < c < w - 1 and 0 < r < h - 1 else (120, 124, 132)
    # ---------------------------------------------------------------- скафандр
    if material == "suit":
        color = noise(rng, (232, 232, 228), 4)
        if rng.random() < 0.08:
            color = shade(color, 0.9)
        return color
    if material == "suit_hard":
        color = noise(rng, (244, 245, 247), 3)
        return shade(color, 0.92) if r == h - 1 else color
    if material == "suit_shade":
        return noise(rng, (196, 198, 200), 4)
    if material == "suit_orange":
        return noise(rng, ORANGE, 6)
    if material == "suit_metal":
        return noise(rng, (168, 174, 184), 5)
    if material == "visor":
        # Золотое напыление: сверху светлее, по диагонали блик
        t = r / max(1, h - 1)
        color = mix((250, 214, 120), (120, 82, 18), t)
        if abs(c - r - 1) <= 0 or abs(c - r - 3) == 0:
            color = mix(color, (255, 250, 230), 0.6)
        return noise(rng, color, 3)
    if material == "panel":
        color = noise(rng, (52, 64, 86), 3)
        if r == 1 and c in (1, 3) and w > 4:
            color = (90, 230, 120) if c == 1 else (240, 80, 60)
        if r == h - 2 and c == w - 2:
            color = (90, 170, 240)
        return color
    if material == "glove":
        return noise(rng, (206, 206, 200), 5)
    if material == "boot":
        return noise(rng, (210, 212, 214), 4)
    if material == "sole":
        return noise(rng, (58, 60, 66), 4)
    if material == "hose_blue":
        return noise(rng, (60, 120, 210), 6)
    if material == "hose_red":
        return noise(rng, (200, 60, 50), 6)
    if material == "flag":
        # Флажок на плече: синий с красной полосой
        return (40, 80, 170) if r < h / 2 else (200, 50, 40)
    raise ValueError("unknown material " + material)


def cube_bottom(cube):
    return getattr(cube, "bell_bottom", cube.origin[1])


def bell_height(cube):
    return getattr(cube, "bell_height", cube.size[1])


# ================================================================ упаковка развёртки

def build_texture(model, seed):
    """Раскладывает грани всех кубов по атласу (полками) и рисует их."""
    rng = random.Random(seed)
    rects = []
    for bone in model.bones:
        for cube in bone.cubes:
            for face in cube.faces:
                fw, fh = cube.face_size(face)
                w, h = max(1, math.ceil(fw)), max(1, math.ceil(fh))
                rects.append((cube, face, w, h))
    # Высокие грани первыми — плотнее укладка
    order = sorted(rects, key=lambda t: (-t[3], -t[2]))
    x = y = shelf = 0
    width = model.atlas_width
    placed = []
    for cube, face, w, h in order:
        if w > width:
            raise ValueError(f"{model.name}: грань {w}px шире атласа {width}")
        if x + w > width:
            x = 0
            y += shelf
            shelf = 0
        placed.append((cube, face, w, h, x, y))
        x += w
        shelf = max(shelf, h)
    height = 16
    while height < y + shelf:
        height *= 2
    atlas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for cube, face, w, h, px, py in placed:
        atlas.paste(paint(cube.material, face, w, h, cube, rng), (px, py))
        cube.uv[face] = (px, py, w, h)
    return atlas


# ================================================================ запись

def r4(v):
    return round(v, 4)


def geo_json(model, atlas):
    bones = []
    for bone in model.bones:
        entry = {"name": bone.name, "pivot": [r4(v) for v in bone.pivot]}
        if bone.parent:
            entry["parent"] = bone.parent
        if bone.rotation:
            entry["rotation"] = [r4(v) for v in bone.rotation]
        cubes = []
        for cube in bone.cubes:
            uv = {}
            for face in cube.faces:
                u, v, w, h = cube.uv[face]
                fw, fh = cube.face_size(face)
                # Нижняя грань в Bedrock-развёртке читается снизу вверх
                if face == "down":
                    uv[face] = {"uv": [u, v + h], "uv_size": [r4(fw), -r4(fh)]}
                else:
                    uv[face] = {"uv": [u, v], "uv_size": [r4(fw), r4(fh)]}
            c = {"origin": [r4(v) for v in cube.origin], "size": [r4(v) for v in cube.size], "uv": uv}
            if cube.inflate:
                c["inflate"] = cube.inflate
            cubes.append(c)
        if cubes:
            entry["cubes"] = cubes
        bones.append(entry)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.orbitcore.{model.name}",
                "texture_width": atlas.width,
                "texture_height": atlas.height,
                "visible_bounds_width": 8,
                "visible_bounds_height": 40,
                "visible_bounds_offset": [0, 16, 0],
            },
            "bones": bones,
        }],
    }


def bbmodel(model, atlas):
    """Проект Blockbench в формате Bedrock: те же кубы, кости и текстура."""
    def uid():
        return str(uuid.uuid4())

    elements = []
    groups = {}
    for bone in model.bones:
        children = []
        for cube in bone.cubes:
            ox, oy, oz = cube.origin
            w, h, d = cube.size
            faces = {}
            for face in FACES:
                if face in cube.faces:
                    u, v, fw, fh = cube.uv[face]
                    faces[face] = {"uv": [u, v, u + fw, v + fh], "texture": 0}
                else:
                    faces[face] = {"uv": [0, 0, 0, 0], "texture": None}
            element = {
                "name": cube.material, "box_uv": False, "rescale": False, "locked": False,
                # В Blockbench ось X зеркальна относительно файла Bedrock
                "from": [r4(-(ox + w)), r4(oy), r4(oz)], "to": [r4(-ox), r4(oy + h), r4(oz + d)],
                "autouv": 0, "color": 0, "inflate": cube.inflate,
                "origin": [r4(-bone.pivot[0]), r4(bone.pivot[1]), r4(bone.pivot[2])],
                "faces": faces, "type": "cube", "uuid": uid(),
            }
            elements.append(element)
            children.append(element["uuid"])
        rotation = bone.rotation or [0, 0, 0]
        groups[bone.name] = {
            "name": bone.name, "origin": [r4(-bone.pivot[0]), r4(bone.pivot[1]), r4(bone.pivot[2])],
            "rotation": [r4(-rotation[0]), r4(-rotation[1]), r4(rotation[2])],
            "color": 0, "uuid": uid(), "export": True, "isOpen": True, "visibility": True,
            "children": children,
        }
    outliner = []
    for bone in model.bones:
        group = groups[bone.name]
        if bone.parent:
            groups[bone.parent]["children"].append(group)
        else:
            outliner.append(group)
    png = io.BytesIO()
    atlas.save(png, format="PNG")
    return {
        "meta": {"format_version": "4.5", "model_format": "bedrock", "box_uv": False},
        "name": model.name,
        "model_identifier": f"orbitcore.{model.name}",
        "visible_box": [8, 40, 16],
        "resolution": {"width": atlas.width, "height": atlas.height},
        "elements": elements,
        "outliner": outliner,
        "textures": [{
            "path": "", "name": f"{model.name}.png", "folder": "", "namespace": "", "id": "0",
            "width": atlas.width, "height": atlas.height, "uv_width": atlas.width, "uv_height": atlas.height,
            "particle": False, "render_mode": "default", "visible": True, "mode": "bitmap", "saved": True,
            "uuid": uid(), "source": "data:image/png;base64," + base64.b64encode(png.getvalue()).decode(),
        }],
    }


def save(model, geo_path, texture_path, seed):
    atlas = build_texture(model, seed)
    geo_path.parent.mkdir(parents=True, exist_ok=True)
    texture_path.parent.mkdir(parents=True, exist_ok=True)
    geo_path.write_text(json.dumps(geo_json(model, atlas), indent=1) + "\n", encoding="utf-8")
    atlas.save(texture_path)
    BLOCKBENCH.mkdir(parents=True, exist_ok=True)
    (BLOCKBENCH / f"{model.name}.bbmodel").write_text(json.dumps(bbmodel(model, atlas)), encoding="utf-8")
    print(f"{model.name}: {sum(len(b.cubes) for b in model.bones)} кубов, текстура {atlas.width}×{atlas.height}")


# ================================================================ детали ракеты

R = 24  # радиус корпуса ракеты: 48 пикселей = 3 блока


def cylinder(bone, y0, height, radius, material, caps=True):
    """«Пиксельный круг» из трёх пересекающихся кубов: восьмиугольник со ступеньками."""
    faces = FACES if caps else ("north", "south", "east", "west")
    a = round(radius * 2 / 3)
    b = round(radius * 5 / 6)
    bone.add((-radius, y0, -a), (radius * 2, height, a * 2), material, faces=faces)
    bone.add((-a, y0, -radius), (a * 2, height, radius * 2), material, faces=faces)
    bone.add((-b, y0, -b), (b * 2, height, b * 2), material, faces=faces)


def ring(bone, y, radius, material="ring", height=2):
    cylinder(bone, y, height, radius + 1, material)


def tank(name, height):
    model = Model(name)
    body = model.bone("body")
    cylinder(body, 0, height, R, "hull_roll" if height > 64 else "hull")
    ring(body, 0, R)
    ring(body, height - 2, R)
    if height > 64:
        ring(body, height // 2 - 1, R)
    # Кабельный короб и магистраль окислителя по бортам
    body.add((-3, 4, R), (6, height - 8, 2), "steel")
    body.add((R, 6, -2), (2, height - 12, 2), "pipe")
    for y in range(10, height - 6, 16):
        body.add((R, y, -3), (3, 2, 4), "dark")
    # Красная опознавательная полоса и «окошко» с надписью
    body.add((-R - 1, height - 14, -6), (1, 8, 12), "red")
    return model


def engine(name, height, bay, bell_layers, bell_material, bay_material, with_bay=True, with_bell=True, bell_scale=1.0):
    """Двигательный отсек сверху и сопло, расширяющееся книзу.

    Для связок двигателей строятся отдельно отсек (на всю ширину ступени)
    и узкие сопла той же высоты, которые ставятся под ним по кругу.
    """
    model = Model(name)
    body = model.bone("body")
    bay_y = height - bay
    if with_bay:
        cylinder(body, bay_y, bay, R, bay_material)
        ring(body, height - 2, R)
    if not with_bell:
        return model
    # Сопло: слои от горла к срезу; нижний слой закрыт тёмным срезом
    layer_h = bay_y / len(bell_layers)
    for i, width in enumerate(bell_layers):
        width = max(4, round(width * bell_scale / 2) * 2)
        y = bay_y - (i + 1) * layer_h
        half = width / 2
        last = i == len(bell_layers) - 1
        cube = body.add((-half, y, -half), (width, layer_h, width), bell_material,
                        faces=("north", "south", "east", "west") + (("down",) if last else ()))
        cube.bell_bottom = 0
        cube.bell_height = bay_y
        cube.bell_last = last
    # Кольцо жёсткости на срезе сопла
    rim = max(4, round(bell_layers[-1] * bell_scale / 2) * 2) + 2
    body.add((-rim / 2, 0, -rim / 2), (rim, 1.5, rim), "steel", faces=("north", "south", "east", "west", "up"))
    # Турбонасос, трубы и рулевые машинки у горла
    top = bay_y
    k = bell_scale
    body.add((-10 * k, top - 8, 6 * k), (6 * k, 8, 6 * k), "steel")
    body.add((4 * k, top - 10, -12 * k), (6 * k, 10, 6 * k), "dark")
    body.add((-2 * k, top - 14, -12 * k), (2, 14, 2), "copper_pipe")
    body.add((8 * k, top - 12, 6 * k), (2, 12, 2), "copper_pipe")
    return model


def decoupler():
    model = Model("stage_decoupler")
    body = model.bone("body")
    cylinder(body, 0, 12, R - 4, "truss")
    ring(body, 10, R - 1, "hazard")
    ring(body, 0, R - 1, "ring")
    return model


def capsule():
    model = Model("capsule_kaplya")
    body = model.bone("body")
    cylinder(body, 0, 4, R, "heat_shield")
    layers = [(4, 8, 23), (12, 8, 21), (20, 8, 18), (28, 6, 15), (34, 6, 12)]
    for y, h, radius in layers:
        cylinder(body, y, h, radius, "capsule")
    # Иллюминаторы и люк спереди (на север, к камере на старте)
    body.add((-4, 21, -19), (8, 6, 1), "window")
    body.add((-5, 20, -19.5), (10, 1, 1), "steel")
    body.add((-5, 27, -19.5), (10, 1, 1), "steel")
    body.add((12, 14, -12), (5, 5, 1), "window")
    body.add((-17, 14, -12), (5, 5, 1), "window")
    body.add((-6, 6, 21.5), (12, 12, 1), "steel")
    # Стыковочный узел
    cylinder(body, 40, 4, 8, "dark")
    # Башня системы аварийного спасения: ферма и твердотопливный двигатель
    for sx in (-4, 3):
        for sz in (-4, 3):
            body.add((sx, 44, sz), (1, 10, 1), "red")
    for y in (46, 50):
        body.add((-4, y, -4), (8, 1, 1), "red", faces=("north", "south", "up", "down"))
        body.add((-4, y, 3), (8, 1, 1), "red", faces=("north", "south", "up", "down"))
    body.add((-3, 54, -3), (6, 8, 6), "red_white")
    for sx, sz in ((-4, -1), (3, -1), (-1, -4), (-1, 3)):
        body.add((sx, 54, sz), (1, 2, 2) if sx in (-4, 3) else (2, 2, 1), "dark")
    body.add((-1, 62, -1), (2, 3, 2), "steel")
    return model


def fins():
    model = Model("rocket_fins")
    body = model.bone("body")
    # Четыре стабилизатора трапецией, с красной кромкой снизу
    for sx, sz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for start, length, height in ((R, 6, 34), (R + 6, 6, 26), (R + 12, 5, 16)):
            if sx:
                x0 = start if sx > 0 else -start - length
                body.add((x0, 0, -1), (length, height, 2), "fin")
            else:
                z0 = start if sz > 0 else -start - length
                body.add((-1, 0, z0), (2, height, length), "fin")
        # Корневой обтекатель
        if sx:
            x0 = R if sx > 0 else -R - 2
            body.add((x0, 0, -3), (2, 36, 6), "steel")
        else:
            z0 = R if sz > 0 else -R - 2
            body.add((-3, 0, z0), (6, 36, 2), "steel")
    return model


def legs():
    """Посадочные опоры: крепятся к низу бака и уходят к земле под срезом сопла."""
    model = Model("landing_legs")
    model.bone("body")
    angle = 22
    for i, (sx, sz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        pivot = (sx * (R + 1), 18, sz * (R + 1))
        # Наклон наружу: вокруг Z для опор по X, вокруг X для опор по Z
        if sx:
            rotation = (0, 0, -angle * sx)
        else:
            rotation = (angle * sz, 0, 0)
        strut = model.bone(f"leg_{i}", pivot=pivot, parent="body", rotation=rotation)
        strut.add((pivot[0] - 1.5, -26, pivot[2] - 1.5), (3, 46, 3), "leg")
        strut.add((pivot[0] - 2, 8, pivot[2] - 2), (4, 6, 4), "steel")
        foot = model.bone(f"foot_{i}", pivot=pivot, parent="body")
        reach = R + 1 + 44 * math.sin(math.radians(angle)) - 2
        fx, fz = sx * reach, sz * reach
        foot.add((fx - 4, -40, fz - 4), (8, 2, 8), "foot")
        foot.add((fx - 1.5, -38, fz - 1.5), (3, 4, 3), "leg")
        # Крепление к корпусу
        if sx:
            model.bones[0].add((R - 2 if sx > 0 else -R - 2, 14, -3), (4, 8, 6), "dark")
        else:
            model.bones[0].add((-3, 14, R - 2 if sz > 0 else -R - 2), (6, 8, 4), "dark")
    return model


# ================================================================ скафандр

def spacesuit():
    model = Model("spacesuit", atlas_width=128)
    model.bone("bipedHead", pivot=(0, 24, 0))
    head = model.bone("armorHead", pivot=(0, 24, 0), parent="bipedHead")
    head.add((-5, 23.5, -5), (10, 10, 10), "suit_hard")
    head.add((-4, 25, -5.6), (8, 6.5, 1), "visor")
    head.add((-4.5, 31.5, -5.8), (9, 1, 1.2), "suit_metal")
    head.add((-4.8, 24.5, -5.8), (0.8, 7, 1.2), "suit_metal")
    head.add((4, 24.5, -5.8), (0.8, 7, 1.2), "suit_metal")
    head.add((5, 29, -3), (1.4, 2, 3), "lamp")
    head.add((-6.4, 29, -3), (1.4, 2, 3), "lamp")
    head.add((-5.5, 23, -5.5), (11, 1.5, 11), "suit_metal")
    head.add((-1, 33.5, -1), (2, 0.8, 4), "suit_shade")

    model.bone("bipedBody", pivot=(0, 24, 0))
    body = model.bone("armorBody", pivot=(0, 24, 0), parent="bipedBody")
    body.add((-4.6, 11.6, -2.6), (9.2, 12.8, 5.2), "suit")
    body.add((-3, 16.5, -4), (6, 4.5, 1.4), "panel")
    body.add((-4.8, 11.4, -2.8), (9.6, 1.4, 5.6), "suit_shade")
    body.add((-4.6, 12.5, 2.6), (9.2, 11, 4.2), "suit_hard")
    body.add((-3.5, 23.5, 3), (7, 1, 3.4), "suit_metal")
    body.add((-4, 14, 6.8), (8, 1, 0.4), "suit_orange")
    body.add((4.6, 16, -3.2), (0.9, 0.9, 6.4), "hose_blue")
    body.add((-5.5, 16, -3.2), (0.9, 0.9, 6.4), "hose_red")

    for side, sign in (("Right", -1), ("Left", 1)):
        model.bone(f"biped{side}Arm", pivot=(5 * sign, 22, 0))
        arm = model.bone(f"armor{side}Arm", pivot=(5 * sign, 22, 0), parent=f"biped{side}Arm")
        x0 = -8.7 if sign < 0 else 3.5
        arm.add((x0, 12.5, -2.6), (5.2, 11.6, 5.2), "suit")
        arm.add((x0 - 0.3, 20, -2.9), (5.8, 1.2, 5.8), "suit_orange")
        arm.add((x0 - 0.3, 12.2, -2.9), (5.8, 1.2, 5.8), "suit_metal")
        arm.add((x0 + 0.1, 9.6, -2.5), (5, 2.8, 5), "glove")
        if sign < 0:
            arm.add((x0 - 0.2, 16.5, -1.6), (0.4, 2.4, 3.2), "flag")

        leg_pivot = (1.9 * sign, 12, 0)
        model.bone(f"biped{side}Leg", pivot=leg_pivot)
        leg = model.bone(f"armor{side}Leg", pivot=leg_pivot, parent=f"biped{side}Leg")
        lx = -4.6 if sign < 0 else -0.4
        leg.add((lx, 3, -2.6), (5, 9.6, 5.2), "suit")
        leg.add((lx + 0.3, 5.5, -3.1), (4.4, 2.6, 1), "suit_shade")
        leg.add((lx - 0.2, 10.4, -2.8), (5.4, 1, 5.6), "suit_orange")
        boot = model.bone(f"armor{side}Boot", pivot=leg_pivot, parent=f"biped{side}Leg")
        boot.add((lx - 0.1, 0, -3.2), (5.2, 4.2, 6), "boot")
        boot.add((lx - 0.3, -0.4, -3.6), (5.6, 1, 6.8), "sole")
        boot.add((lx + 0.1, 0.6, -3.9), (4.8, 2, 0.8), "sole")
        boot.add((lx - 0.2, 3.6, -3.4), (5.4, 0.8, 6.4), "suit_metal")
    return model


# ================================================================ запуск

def main():
    rocket = ASSETS / "geo" / "rocket"
    textures = ASSETS / "textures" / "rocket"
    iskra = (40, 14, [12, 16, 20, 24, 26, 28], "bell", "gold_foil")
    fakel = (48, 16, [14, 18, 22, 26, 30, 34, 36, 38], "bell_gold", "dark")
    parts = [
        (engine("engine_iskra", *iskra), 201),
        (engine("engine_iskra_bay", *iskra, with_bell=False), 211),
        (engine("engine_iskra_bell", *iskra, with_bay=False, bell_scale=0.58), 212),
        (engine("engine_fakel", *fakel), 202),
        (engine("engine_fakel_bay", *fakel, with_bell=False), 213),
        (engine("engine_fakel_bell", *fakel, with_bay=False, bell_scale=0.58), 214),
        (tank("fuel_tank_small", 48), 203),
        (tank("fuel_tank_large", 112), 204),
        (decoupler(), 205),
        (capsule(), 206),
        (fins(), 207),
        (legs(), 208),
    ]
    for model, seed in parts:
        save(model, rocket / f"{model.name}.geo.json", textures / f"{model.name}.png", seed)
    suit = spacesuit()
    save(suit, ASSETS / "geo" / "armor" / "spacesuit.geo.json", ASSETS / "textures" / "armor" / "spacesuit.png", 210)
    empty = {"format_version": "1.8.0", "animations": {}, "geckolib_format_version": 2}
    animations = ASSETS / "animations"
    animations.mkdir(parents=True, exist_ok=True)
    (animations / "static.animation.json").write_text(json.dumps(empty, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
