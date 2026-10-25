"""Генератор пиксельных текстур OrbitCore.

Все текстуры мода рисуются этим скриптом, чтобы их можно было перерисовать
одной командой и не хранить исходники в графических редакторах:

    python tools/textures/generate.py

Нужен Python 3 и Pillow (pip install pillow).
"""
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "orbitcore" / "textures"
BLOCK = ASSETS / "block"
ITEM = ASSETS / "item"

S = 16
CLEAR = (0, 0, 0, 0)

# Палитры от тёмного к светлому
STEEL = [(44, 48, 56), (72, 78, 88), (104, 110, 122), (142, 148, 160), (184, 190, 200), (222, 227, 234)]
ALUMINUM = [(96, 104, 116), (138, 148, 160), (172, 181, 192), (200, 208, 217), (226, 231, 237), (248, 250, 253)]
TITANIUM = [(52, 48, 66), (82, 76, 102), (114, 107, 140), (152, 145, 180), (190, 184, 214), (228, 224, 244)]
BAUXITE = [(78, 34, 20), (120, 56, 32), (160, 84, 48), (194, 116, 70), (222, 152, 102), (240, 190, 140)]
RUTILE = [(36, 14, 12), (74, 28, 20), (122, 48, 28), (170, 80, 40), (214, 128, 64), (246, 186, 110)]
SILICON = [(30, 34, 48), (52, 60, 82), (80, 92, 122), (114, 130, 166), (156, 174, 210), (206, 220, 245)]
COPPER = [(86, 38, 24), (132, 64, 40), (178, 96, 60), (214, 132, 88), (238, 172, 128)]
FIREBRICK = [(70, 34, 24), (112, 54, 34), (148, 76, 46), (176, 98, 60), (204, 128, 82)]
GLOW = [(120, 30, 10), (200, 70, 16), (244, 128, 30), (255, 186, 64), (255, 236, 150)]
CYAN = [(10, 60, 80), (20, 110, 140), (50, 170, 200), (110, 220, 240), (200, 250, 255)]


def new(w=S, h=S, fill=CLEAR):
    return Image.new("RGBA", (w, h), fill)


def rgba(c, a=255):
    return tuple(c[:3]) + (a,)


def put(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), rgba(c))


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, c)


def frame(img, x0, y0, x1, y1, light, dark):
    """Рамка с объёмом: светлый верх и лево, тёмный низ и право."""
    for x in range(x0, x1 + 1):
        put(img, x, y0, light)
        put(img, x, y1, dark)
    for y in range(y0, y1 + 1):
        put(img, x0, y, light)
        put(img, x1, y, dark)


def noisy(img, x0, y0, x1, y1, palette, base, rng, spread=1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            i = max(0, min(len(palette) - 1, base + rng.randint(-spread, spread) * (rng.random() < 0.35)))
            put(img, x, y, palette[i])


def inside(poly, x, y):
    """Попадает ли центр пикселя (x, y) в многоугольник."""
    px, py = x + 0.5, y + 0.5
    hit = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
            hit = not hit
        j = i
    return hit


def fill_poly(img, poly, color_at):
    for y in range(img.height):
        for x in range(img.width):
            if inside(poly, x, y):
                c = color_at(x, y)
                if c is not None:
                    put(img, x, y, c)


def outline(img, color):
    """Тёмный контур вокруг непрозрачных пикселей предмета."""
    src = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and src.getpixel((nx, ny))[3] > 0:
                        put(img, x, y, color)
                        break


def line(img, x0, y0, x1, y1, c):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        put(img, x0, y0, c)
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def save(img, folder, name):
    folder.mkdir(parents=True, exist_ok=True)
    img.save(folder / f"{name}.png")


def save_animation(frames, folder, name, frametime):
    sheet = new(S, S * len(frames))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, S * i))
    save(sheet, folder, name)
    (folder / f"{name}.png.mcmeta").write_text(
        '{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % frametime, encoding="utf-8")


# ---------------------------------------------------------------- предметы

def ingot(palette, seed):
    rng = random.Random(seed)
    img = new()
    top = [(5, 4), (14, 4), (11, 8), (2, 8)]
    front = [(2, 8), (11, 8), (11, 12), (2, 12)]
    side = [(11, 8), (14, 4), (14, 8), (11, 12)]
    fill_poly(img, top, lambda x, y: palette[4 if rng.random() > 0.15 else 3])
    fill_poly(img, front, lambda x, y: palette[3 if y < 10 else 2] if rng.random() > 0.1 else palette[2])
    fill_poly(img, side, lambda x, y: palette[1])
    for x in range(5, 13):
        put(img, x, 4, palette[5])
    put(img, 4, 5, palette[5])
    put(img, 3, 6, palette[5])
    outline(img, palette[0])
    return img


def plate(palette, seed):
    rng = random.Random(seed)
    img = new()
    top = [(4, 3), (15, 3), (12, 11), (1, 11)]
    fill_poly(img, top, lambda x, y: palette[3 + (rng.random() < 0.2)])
    rect(img, 1, 11, 11, 12, palette[2])
    for y in range(4, 12):
        x = round(15 - (y - 3) * 3 / 8)
        put(img, x, y + 1, palette[1])
    line(img, 4, 3, 14, 3, palette[5])
    line(img, 3, 5, 1, 10, palette[4])
    for x, y in ((5, 5), (12, 5), (3, 9), (10, 9)):
        put(img, x, y, palette[5])
        put(img, x + 1, y + 1, palette[1])
    outline(img, palette[0])
    return img


def chunk(palette, seed, blobs):
    rng = random.Random(seed)
    img = new()
    for y in range(S):
        for x in range(S):
            d = min(math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r for cx, cy, r in blobs)
            if d <= 1:
                light = 1 - d * 0.55 - (x + y - 12) * 0.035
                i = max(1, min(5, round(light * 5 + rng.uniform(-0.8, 0.8))))
                put(img, x, y, palette[i])
    outline(img, palette[0])
    return img


def silicon():
    img = new()
    facets = [
        ([(8, 1), (13, 6), (8, 8)], SILICON[5]),
        ([(8, 1), (8, 8), (3, 6)], SILICON[4]),
        ([(3, 6), (8, 8), (7, 14)], SILICON[3]),
        ([(8, 8), (13, 6), (12, 12)], SILICON[2]),
        ([(8, 8), (12, 12), (7, 14)], SILICON[1]),
    ]
    for poly, c in facets:
        fill_poly(img, poly, lambda x, y, c=c: c)
    put(img, 7, 3, (255, 255, 255))
    put(img, 6, 5, SILICON[5])
    outline(img, SILICON[0])
    return img


def copper_wire():
    img = new()
    # Катушка: намотка между двумя щёчками
    for y in range(4, 12):
        for x in range(4, 12):
            put(img, x, y, COPPER[2] if y % 2 else COPPER[3])
    line(img, 4, 4, 11, 4, COPPER[4])
    line(img, 4, 11, 11, 11, COPPER[1])
    for x0 in (2, 12):
        rect(img, x0, 2, x0 + 1, 13, STEEL[3])
        line(img, x0, 2, x0, 13, STEEL[4])
        line(img, x0 + 1, 2, x0 + 1, 13, STEEL[2])
        put(img, x0, 2, STEEL[5])
    # Свободный конец провода
    line(img, 11, 12, 14, 15, COPPER[3])
    put(img, 14, 15, COPPER[4])
    outline(img, COPPER[0])
    return img


def circuit_board():
    img = new()
    board = [(30, 70, 44), (40, 96, 58), (52, 120, 72)]
    rect(img, 1, 3, 14, 12, board[1])
    for x in range(1, 15):
        put(img, x, 3, board[2])
        put(img, x, 12, board[0])
    gold = [(150, 110, 30), (212, 170, 60), (246, 214, 110)]
    for y in (5, 10):
        line(img, 2, y, 5, y, gold[1])
        line(img, 10, y, 13, y, gold[1])
    line(img, 5, 5, 5, 10, gold[1])
    line(img, 10, 5, 10, 10, gold[1])
    rect(img, 6, 6, 9, 9, (24, 26, 32))
    frame(img, 6, 6, 9, 9, (70, 74, 86), (14, 15, 20))
    for x in (2, 13):
        put(img, x, 7, (180, 40, 40))
        put(img, x, 8, gold[2])
    put(img, 7, 7, (90, 220, 240))
    outline(img, (16, 30, 20))
    return img


# ---------------------------------------------------------------- блоки

def ore_overlay(palette, seed, clusters):
    rng = random.Random(seed)
    img = new()
    for cx, cy, n in clusters:
        pts = {(cx, cy)}
        while len(pts) < n:
            x, y = rng.choice(sorted(pts))
            dx, dy = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
            if 0 <= x + dx < S and 0 <= y + dy < S:
                pts.add((x + dx, y + dy))
        for x, y in pts:
            shade = 4 if (x - 1, y) not in pts or (x, y - 1) not in pts else 3
            if (x + 1, y) not in pts and (x, y + 1) not in pts:
                shade = 2
            put(img, x, y, palette[shade])
        for x, y in pts:
            if (x + 1, y + 1) not in pts and img.getpixel((min(x + 1, S - 1), min(y + 1, S - 1)))[3] == 0:
                put(img, x + 1, y + 1, palette[0])
        hx, hy = min(pts)
        put(img, hx, hy, palette[5])
    return img


def ore_needles(palette, needles):
    """Рутил: тонкие игольчатые кристаллы с блестящим концом."""
    img = new()
    for x, y, dx, dy, n in needles:
        for i in range(n):
            px, py = x + dx * i, y + dy * i
            put(img, px + 1, py + 1, palette[0])
        for i in range(n):
            put(img, x + dx * i, y + dy * i, palette[2 if i < n - 1 else 5] if i else palette[3])
    return img


def metal_block(palette, seed):
    rng = random.Random(seed)
    img = new()
    noisy(img, 0, 0, 15, 15, palette, 3, rng)
    frame(img, 0, 0, 15, 15, palette[4], palette[1])
    frame(img, 1, 1, 14, 14, palette[5], palette[2])
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        put(img, x, y, palette[5])
        put(img, x + 1, y + 1, palette[1])
    line(img, 5, 7, 10, 7, palette[2])
    line(img, 5, 8, 10, 8, palette[4])
    return img


def machine_side(seed, vents=True):
    rng = random.Random(seed)
    img = new()
    noisy(img, 0, 0, 15, 15, STEEL, 2, rng)
    frame(img, 0, 0, 15, 15, STEEL[4], STEEL[0])
    frame(img, 2, 2, 13, 13, STEEL[1], STEEL[3])
    if vents:
        for y in (5, 8, 11):
            line(img, 4, y, 11, y, STEEL[0])
            line(img, 4, y - 1, 11, y - 1, STEEL[1])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, STEEL[5])
    return img


def machine_casing():
    img = machine_side(11, vents=False)
    rect(img, 3, 3, 12, 12, (24, 28, 34))
    for i in range(3, 13, 3):
        line(img, i, 3, i, 12, STEEL[1])
        line(img, 3, i, 12, i, STEEL[1])
    frame(img, 2, 2, 13, 13, STEEL[0], STEEL[4])
    return img


def machine_top(seed, detail):
    rng = random.Random(seed)
    img = new()
    noisy(img, 0, 0, 15, 15, STEEL, 3, rng)
    frame(img, 0, 0, 15, 15, STEEL[5], STEEL[1])
    detail(img)
    return img


def firebricks(img, seed):
    rng = random.Random(seed)
    for y in range(S):
        row = y // 4
        offset = 4 if row % 2 else 0
        for x in range(S):
            mortar = y % 4 == 3 or (x + offset) % 8 == 7
            c = FIREBRICK[0] if mortar else FIREBRICK[rng.choice((2, 3, 3, 4))]
            put(img, x, y, c)


def furnace_front(lit, phase=0):
    img = new()
    firebricks(img, 21)
    frame(img, 0, 0, 15, 15, STEEL[3], STEEL[0])
    rect(img, 0, 0, 15, 1, STEEL[2])
    line(img, 0, 0, 15, 0, STEEL[4])
    # Окно топки
    rect(img, 4, 6, 11, 13, (20, 12, 10))
    frame(img, 3, 5, 12, 14, STEEL[4], STEEL[0])
    if lit:
        rng = random.Random(100 + phase)
        for y in range(6, 14):
            for x in range(4, 12):
                heat = (y - 6) / 7 + rng.uniform(-0.25, 0.25) + 0.15 * math.sin(x * 1.3 + phase * 2.1)
                put(img, x, y, GLOW[max(0, min(4, round(heat * 4)))])
    for x in range(4, 12, 2):
        line(img, x, 6, x, 13, STEEL[1] if not lit else (90, 40, 20))
    # Термометр
    rect(img, 13, 2, 14, 4, (30, 30, 34))
    put(img, 13, 3, GLOW[3] if lit else STEEL[2])
    return img


def furnace_side():
    img = new()
    firebricks(img, 22)
    for y in (0, 1, 14, 15):
        line(img, 0, y, 15, y, STEEL[2 if y in (1, 14) else 3])
    for x, y in ((2, 0), (13, 0), (2, 15), (13, 15)):
        put(img, x, y, STEEL[5])
    return img


def furnace_top():
    def detail(img):
        for y in range(3, 13):
            for x in range(3, 13):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 4.5:
                    put(img, x, y, (24, 18, 16) if d < 3 else STEEL[1])
        put(img, 6, 6, (70, 40, 30))
    return machine_top(23, detail)


def generator_front(lit, phase=0):
    img = machine_side(31, vents=False)
    # Манометр
    for y in range(2, 8):
        for x in range(4, 12):
            d = math.hypot(x - 7.5, y - 4.5)
            if d < 3.2:
                put(img, x, y, (226, 226, 214))
            elif d < 3.9:
                put(img, x, y, COPPER[2])
    angle = -2.4 + (1.6 if lit else 0) + (0.25 * math.sin(phase * 1.7) if lit else 0)
    line(img, 7, 4, 7 + round(2.3 * math.cos(angle)), 4 + round(2.3 * math.sin(angle)), (180, 30, 30))
    # Топка с решёткой
    rect(img, 4, 9, 11, 13, (16, 14, 14))
    frame(img, 3, 8, 12, 14, STEEL[4], STEEL[0])
    if lit:
        rng = random.Random(300 + phase)
        for y in range(9, 14):
            for x in range(4, 12):
                heat = (y - 9) / 4 + rng.uniform(-0.3, 0.3)
                put(img, x, y, GLOW[max(0, min(4, round(heat * 4)))])
    for y in (10, 12):
        line(img, 4, y, 11, y, STEEL[1] if not lit else (80, 36, 18))
    return img


def generator_top():
    def detail(img):
        for cx in (5, 10):
            for y in range(3, 13):
                for x in range(cx - 2, cx + 3):
                    if math.hypot(x - cx, y - 7.5) < 2.4 or abs(x - cx) < 2 and 4 <= y <= 11:
                        put(img, x, y, COPPER[1 + (x + y) % 3])
        line(img, 3, 13, 12, 13, STEEL[0])
    return machine_top(33, detail)


def electrolyzer_front(lit, phase=0):
    rng = random.Random(41)
    img = new()
    noisy(img, 0, 0, 15, 15, ALUMINUM, 2, rng)
    frame(img, 0, 0, 15, 15, ALUMINUM[5], ALUMINUM[0])
    # Бак с электролитом
    rect(img, 3, 3, 12, 13, (30, 52, 70))
    for y in range(5, 14):
        for x in range(3, 13):
            put(img, x, y, CYAN[1] if (x + y) % 5 else CYAN[2])
    line(img, 3, 5, 12, 5, CYAN[3])
    frame(img, 2, 2, 13, 14, STEEL[1], STEEL[4])
    # Электроды
    for x, c in ((5, (190, 50, 40)), (10, (40, 40, 48))):
        line(img, x, 2, x, 11, STEEL[4] if not lit else CYAN[4])
        put(img, x, 1, c)
        put(img, x + (1 if x == 5 else -1), 1, c)
    if lit:
        bubbles = random.Random(400)
        for i in range(7):
            bx = bubbles.choice((4, 6, 9, 11))
            by = 12 - (bubbles.randint(0, 7) + phase * 2) % 8
            put(img, bx, by, CYAN[4])
    # Полоса предупреждения
    for x in range(1, 15):
        put(img, x, 15 - 1, (230, 180, 40) if (x // 2) % 2 else (30, 30, 30))
    return img


def electrolyzer_side():
    rng = random.Random(42)
    img = new()
    noisy(img, 0, 0, 15, 15, ALUMINUM, 2, rng)
    frame(img, 0, 0, 15, 15, ALUMINUM[5], ALUMINUM[0])
    frame(img, 2, 2, 13, 13, ALUMINUM[1], ALUMINUM[4])
    for y in range(4, 12, 2):
        line(img, 4, y, 11, y, ALUMINUM[1])
    for x in range(1, 15):
        put(img, x, 14, (230, 180, 40) if (x // 2) % 2 else (30, 30, 30))
    return img


def electrolyzer_top():
    def detail(img):
        for cx, c in ((5, (190, 50, 40)), (10, (40, 40, 48))):
            for y in range(4, 12):
                for x in range(cx - 2, cx + 3):
                    if math.hypot(x - cx, y - 7.5) < 2.5:
                        put(img, x, y, c if math.hypot(x - cx, y - 7.5) < 1.5 else STEEL[4])
    return machine_top(43, detail)


def table_top():
    img = new()
    blueprint = [(22, 52, 104), (30, 70, 138), (210, 228, 255)]
    rect(img, 0, 0, 15, 15, blueprint[1])
    for i in range(2, 15, 3):
        line(img, i, 1, i, 14, blueprint[0])
        line(img, 1, i, 14, i, blueprint[0])
    # Чертёж ракеты
    line(img, 8, 3, 8, 11, blueprint[2])
    line(img, 7, 4, 7, 11, blueprint[2])
    put(img, 7, 3, blueprint[2])
    line(img, 5, 12, 7, 10, blueprint[2])
    line(img, 10, 12, 8, 10, blueprint[2])
    put(img, 11, 3, (255, 210, 90))
    frame(img, 0, 0, 15, 15, STEEL[4], STEEL[1])
    return img


def table_side():
    rng = random.Random(51)
    img = new()
    noisy(img, 0, 0, 15, 15, STEEL, 2, rng)
    rect(img, 0, 0, 15, 2, STEEL[4])
    line(img, 0, 0, 15, 0, STEEL[5])
    # Ящик
    rect(img, 3, 5, 12, 10, STEEL[3])
    frame(img, 3, 5, 12, 10, STEEL[5], STEEL[1])
    line(img, 6, 7, 9, 7, STEEL[0])
    # Ножки
    for x in (1, 14):
        line(img, x, 3, x, 15, STEEL[1])
    rect(img, 2, 12, 13, 15, (30, 32, 38))
    line(img, 2, 12, 13, 12, STEEL[2])
    return img


def table_front():
    img = table_side()
    # Инструменты на стойке
    line(img, 4, 13, 4, 15, (180, 40, 40))
    line(img, 7, 13, 7, 15, (230, 180, 40))
    line(img, 10, 13, 10, 15, CYAN[3])
    return img


BRASS = [(70, 46, 18), (118, 80, 30), (168, 120, 46), (206, 160, 70), (236, 200, 110), (252, 232, 170)]
SCREEN_GREEN = [(8, 22, 16), (14, 46, 30), (30, 110, 64), (70, 200, 120), (170, 255, 200)]
GUI = ASSETS / "gui"


def telescope_tube():
    rng = random.Random(61)
    img = new()
    for y in range(S):
        for x in range(S):
            shade = 3 if y in (4, 5, 6) else 2
            shade += 1 if y in (5,) else 0
            if x in (0, 7, 15):
                shade = 1
            put(img, x, y, BRASS[max(1, min(5, shade + (rng.random() < 0.12)))])
    for y in (0, 15):
        line(img, 0, y, 15, y, BRASS[0])
    return img


def telescope_metal():
    rng = random.Random(62)
    img = new()
    noisy(img, 0, 0, 15, 15, STEEL, 1, rng)
    for x in (0, 15):
        line(img, x, 0, x, 15, STEEL[0])
    return img


def telescope_lens():
    img = new()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.2:
                put(img, x, y, BRASS[1])
            elif d > 6:
                put(img, x, y, BRASS[3])
            else:
                put(img, x, y, (30, 60 + int(d * 8), 120 + int(d * 10)))
    for x, y in ((5, 4), (6, 4), (4, 5)):
        put(img, x, y, (220, 240, 255))
    return img


def mission_screen():
    img = new()
    rect(img, 0, 0, 15, 15, STEEL[1])
    rect(img, 1, 1, 14, 11, SCREEN_GREEN[0])
    # Планета и орбита
    for a in range(0, 360, 10):
        t = math.radians(a)
        put(img, round(7.5 + 5.5 * math.cos(t)), round(6 + 3.2 * math.sin(t)), SCREEN_GREEN[2])
    for y in range(4, 9):
        for x in range(5, 11):
            if math.hypot(x - 7.5, y - 6) < 2.1:
                put(img, x, y, SCREEN_GREEN[3])
    put(img, 13, 5, SCREEN_GREEN[4])
    line(img, 2, 10, 5, 10, SCREEN_GREEN[2])
    line(img, 10, 10, 13, 10, SCREEN_GREEN[2])
    frame(img, 0, 0, 15, 12, STEEL[3], STEEL[0])
    rect(img, 0, 13, 15, 15, STEEL[2])
    for x in (3, 7, 11):
        put(img, x, 14, (230, 180, 40) if x == 7 else (180, 40, 40))
    return img


def mission_panel():
    rng = random.Random(63)
    img = new()
    noisy(img, 0, 0, 15, 15, STEEL, 2, rng)
    frame(img, 0, 0, 15, 15, STEEL[4], STEEL[0])
    colors = [(180, 40, 40), (230, 180, 40), (60, 180, 90), (60, 150, 220)]
    for row, y in enumerate((3, 7, 11)):
        for col, x in enumerate((3, 6, 9, 12)):
            c = colors[(row + col) % 4] if rng.random() < 0.6 else STEEL[4]
            put(img, x, y, c)
            put(img, x + 1, y, c)
            put(img, x, y + 1, STEEL[0])
    return img


def mission_side():
    img = machine_side(64, vents=True)
    rect(img, 3, 12, 12, 13, (230, 180, 40))
    return img


def analyzer_front(lit, phase=0):
    img = machine_side(71, vents=False)
    # Камера с образцом
    rect(img, 2, 2, 8, 9, (12, 18, 26))
    frame(img, 1, 1, 9, 10, STEEL[4], STEEL[0])
    rect(img, 3, 7, 7, 8, STEEL[3])
    rect(img, 4, 5, 6, 6, BAUXITE[3])
    if lit:
        line(img, 5, 2, 5, 4, CYAN[4])
        put(img, 4, 3, CYAN[2])
        put(img, 6, 3, CYAN[2])
    # Экран спектра
    rect(img, 10, 2, 14, 9, SCREEN_GREEN[0])
    frame(img, 10, 2, 14, 9, STEEL[0], STEEL[4])
    rng = random.Random(500 + phase)
    for x in range(11, 14):
        h = rng.randint(1, 5) if lit else 1
        line(img, x, 8, x, 8 - h + 1, SCREEN_GREEN[3] if lit else SCREEN_GREEN[1])
    # Кнопки
    for x, c in ((3, (60, 180, 90)), (6, (230, 180, 40)), (9, (180, 40, 40))):
        put(img, x, 12, c if lit or x != 3 else STEEL[2])
        put(img, x + 1, 12, c if lit or x != 3 else STEEL[2])
    return img


def analyzer_top():
    def detail(img):
        for y in range(3, 13):
            for x in range(3, 13):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 4.6:
                    put(img, x, y, (40 + int(d * 10), 90 + int(d * 12), 120 + int(d * 10)) if d < 3.6 else STEEL[1])
        put(img, 6, 6, (210, 240, 255))
    return machine_top(72, detail)


def moon_phases():
    """Луна для окуляра телескопа: 8 фаз по 64×64 в одну полосу (0 — полнолуние, 4 — новолуние)."""
    size = 64
    rng = random.Random(81)
    craters = [(rng.uniform(-0.8, 0.8), rng.uniform(-0.8, 0.8), rng.uniform(0.03, 0.09)) for _ in range(26)]
    grid = [[rng.random() for _ in range(33)] for _ in range(33)]

    def smooth_noise(x, y, cells):
        """Плавный шум: сетка cells×cells на квадрат [-1, 1]², сглаженная интерполяция."""
        gx, gy = (x + 1) / 2 * cells, (y + 1) / 2 * cells
        ix, iy = max(0, min(cells - 1, int(gx))), max(0, min(cells - 1, int(gy)))
        fx, fy = gx - ix, gy - iy
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        top = grid[iy][ix] * (1 - fx) + grid[iy][ix + 1] * fx
        bottom = grid[iy + 1][ix] * (1 - fx) + grid[iy + 1][ix + 1] * fx
        return top * (1 - fy) + bottom * fy

    def maria(x, y):
        # Несколько слоёв шума; моря тяготеют к северо-западу видимой стороны, как у настоящей Луны
        n = smooth_noise(x, y, 5) * 0.55 + smooth_noise(-y, x, 11) * 0.3 + smooth_noise(x, -y, 23) * 0.15
        bias = 0.16 * (-x * 0.6 - y * 0.4)
        return max(0.0, min(1.0, (n + bias - 0.47) * 7))
    sheet = Image.new("RGBA", (size * 8, size), CLEAR)
    base = Image.new("RGBA", (size, size), CLEAR)
    normals = {}
    for py in range(size):
        for px in range(size):
            x, y = (px + 0.5) / size * 2 - 1, (py + 0.5) / size * 2 - 1
            r2 = x * x + y * y
            if r2 > 1:
                continue
            z = math.sqrt(1 - r2)
            v = 0.82 + rng.uniform(-0.035, 0.035) - 0.3 * maria(x, y)
            for cx, cy, cr in craters:
                d = math.hypot(x - cx, y - cy)
                if d < cr:
                    v -= 0.1
                elif d < cr * 1.35 and (x - cx) + (y - cy) < 0:
                    v += 0.08
            # Кратер Тихо с лучами
            d = math.hypot(x + 0.15, y - 0.62)
            if d < 0.06:
                v += 0.2
            elif abs(math.sin(math.atan2(y - 0.62, x + 0.15) * 7)) > 0.97 and d < 0.6:
                v += 0.06
            v *= 0.75 + 0.25 * z
            c = max(0, min(255, int(v * 255)))
            base.putpixel((px, py), (c, c, min(255, c + 6), 255))
            normals[(px, py)] = (x, y, z)
    for phase in range(8):
        frame_img = base.copy()
        if phase == 4:
            angle = math.pi
        else:
            angle = (phase if phase < 4 else phase - 8) * math.pi / 4
        sun = (-math.sin(angle), 0.0, math.cos(angle))
        for (px, py), (x, y, z) in normals.items():
            light = x * sun[0] + y * sun[1] + z * sun[2]
            if light < 0.04:
                r, g, b, a = frame_img.getpixel((px, py))
                k = 0.1 if light < -0.04 else 0.1 + (light + 0.04) / 0.08 * 0.6
                frame_img.putpixel((px, py), (int(r * k), int(g * k), int(b * k + 6), a))
        sheet.paste(frame_img, (phase * size, 0))
    return sheet


def eyepiece():
    """Круг окуляра: прозрачный центр и мягкое затемнение к краю."""
    size = 256
    img = Image.new("RGBA", (size, size), CLEAR)
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2) / (size / 2)
            if d < 0.86:
                continue
            a = 255 if d >= 0.98 else int((d - 0.86) / 0.12 * 255)
            img.putpixel((x, y), (0, 0, 0, a))
    return img


def main():
    save(ingot(STEEL, 1), ITEM, "steel_ingot")
    save(ingot(ALUMINUM, 2), ITEM, "aluminum_ingot")
    save(ingot(TITANIUM, 3), ITEM, "titanium_ingot")
    save(plate(STEEL, 4), ITEM, "steel_plate")
    save(plate(ALUMINUM, 5), ITEM, "aluminum_plate")
    save(plate(TITANIUM, 6), ITEM, "titanium_plate")
    save(chunk(BAUXITE, 7, [(7, 8, 5.2), (10.5, 6, 3.6), (5, 11, 3.2)]), ITEM, "raw_bauxite")
    raw_rutile = chunk(RUTILE[:1] + RUTILE[:5], 8, [(8, 8, 4.6), (11, 10.5, 3.4), (5, 6, 3.0)])
    for x, y in ((5, 9), (6, 8), (7, 7), (10, 6), (11, 5), (9, 11), (10, 10)):
        put(raw_rutile, x, y, RUTILE[5])
    save(raw_rutile, ITEM, "raw_rutile")
    save(silicon(), ITEM, "silicon")
    save(copper_wire(), ITEM, "copper_wire")
    save(circuit_board(), ITEM, "circuit_board")

    save(ore_overlay(BAUXITE, 11, [(3, 3, 7), (11, 2, 5), (7, 8, 8), (13, 9, 5), (2, 12, 6), (9, 13, 5)]),
         BLOCK, "bauxite_ore_overlay")
    save(ore_needles(RUTILE, [(2, 2, 1, 1, 4), (10, 1, -1, 1, 4), (12, 5, 0, 1, 4), (5, 8, 1, 1, 5),
                              (2, 10, 1, -1, 3), (11, 10, -1, 1, 4), (7, 3, 1, 0, 3), (13, 13, -1, 0, 3)]),
         BLOCK, "rutile_ore_overlay")
    save(metal_block(STEEL, 13), BLOCK, "steel_block")
    save(metal_block(ALUMINUM, 14), BLOCK, "aluminum_block")
    save(metal_block(TITANIUM, 15), BLOCK, "titanium_block")
    save(machine_casing(), BLOCK, "machine_casing")
    save(machine_side(16), BLOCK, "machine_side")

    save(furnace_front(False), BLOCK, "metallurgical_furnace_front")
    save_animation([furnace_front(True, p) for p in range(4)], BLOCK, "metallurgical_furnace_front_on", 3)
    save(furnace_side(), BLOCK, "metallurgical_furnace_side")
    save(furnace_top(), BLOCK, "metallurgical_furnace_top")

    save(generator_front(False), BLOCK, "coal_generator_front")
    save_animation([generator_front(True, p) for p in range(4)], BLOCK, "coal_generator_front_on", 3)
    save(generator_top(), BLOCK, "coal_generator_top")

    save(electrolyzer_front(False), BLOCK, "electrolyzer_front")
    save_animation([electrolyzer_front(True, p) for p in range(4)], BLOCK, "electrolyzer_front_on", 4)
    save(electrolyzer_side(), BLOCK, "electrolyzer_side")
    save(electrolyzer_top(), BLOCK, "electrolyzer_top")

    save(table_top(), BLOCK, "engineering_table_top")
    save(table_side(), BLOCK, "engineering_table_side")
    save(table_front(), BLOCK, "engineering_table_front")

    save(telescope_tube(), BLOCK, "telescope_tube")
    save(telescope_metal(), BLOCK, "telescope_metal")
    save(telescope_lens(), BLOCK, "telescope_lens")
    save(mission_screen(), BLOCK, "mission_control_screen")
    save(mission_panel(), BLOCK, "mission_control_panel")
    save(mission_side(), BLOCK, "mission_control_side")
    save(analyzer_front(False), BLOCK, "sample_analyzer_front")
    save_animation([analyzer_front(True, p) for p in range(4)], BLOCK, "sample_analyzer_front_on", 4)
    save(analyzer_top(), BLOCK, "sample_analyzer_top")

    telescope_gui = GUI / "telescope"
    telescope_gui.mkdir(parents=True, exist_ok=True)
    moon_phases().save(telescope_gui / "moon.png")
    eyepiece().save(telescope_gui / "eyepiece.png")


if __name__ == "__main__":
    main()
