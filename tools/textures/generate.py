"""Генератор пиксельных текстур OrbitCore.

Все текстуры мода рисуются этим скриптом, чтобы их можно было перерисовать
одной командой и не хранить исходники в графических редакторах:

    python tools/textures/generate.py

Нужен Python 3 и Pillow (pip install pillow). Земля, Луна и Солнце рисуются
отдельно, реалистично — в planets.py (там ещё нужен numpy).
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


HULL = [(150, 156, 166), (190, 196, 204), (220, 224, 230), (238, 241, 245), (250, 251, 253)]
ORANGE = [(150, 60, 20), (210, 96, 30), (240, 132, 48)]
KEROSENE = [(120, 70, 20), (170, 110, 40), (210, 150, 70), (240, 190, 110)]
LOX = [(110, 170, 210), (150, 205, 235), (190, 230, 250), (230, 248, 255)]


def rocket_hull(stripe=False):
    """Обшивка бака: белые панели со швами, как у настоящих ракет."""
    rng = random.Random(91 if stripe else 90)
    img = new()
    for y in range(S):
        for x in range(S):
            put(img, x, y, HULL[3 + (rng.random() < 0.25)])
    for x in (0, 8):
        line(img, x, 0, x, 15, HULL[1])
    for y in (0, 15):
        line(img, 0, y, 15, y, HULL[0])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (6, 2), (10, 13)):
        put(img, x, y, HULL[1])
    if stripe:
        rect(img, 0, 6, 15, 9, ORANGE[1])
        line(img, 0, 6, 15, 6, ORANGE[2])
        line(img, 0, 9, 15, 9, ORANGE[0])
    else:
        # Чёрные «шашки» для наблюдения за вращением, как на первых ракетах
        rect(img, 1, 4, 7, 11, (34, 36, 42))
    return img


def engine_bell(gold=False):
    """Сопло: горячая бронза у горловины темнеет к срезу."""
    img = new()
    top = [(196, 150, 70), (170, 120, 50), (130, 90, 40)] if gold else [(150, 110, 60), (120, 86, 48), (90, 66, 40)]
    for y in range(S):
        t = y / 15
        for x in range(S):
            if t < 0.5:
                c = top[min(2, int(t * 6))]
            else:
                k = (t - 0.5) * 2
                c = (int(top[2][0] * (1 - k) + 34 * k), int(top[2][1] * (1 - k) + 36 * k), int(top[2][2] * (1 - k) + 48 * k))
            if x % 4 == 0:
                c = tuple(max(0, v - 18) for v in c)
            put(img, x, y, c)
    return img


def engine_mount():
    img = machine_side(92, vents=False)
    for x in (3, 7, 11):
        line(img, x, 2, x, 13, COPPER[2])
        line(img, x + 1, 2, x + 1, 13, COPPER[0])
    return img


def nozzle_exit():
    img = new()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 7.5, y - 7.5)
            put(img, x, y, (20, 22, 30) if d < 5.5 else (60, 50, 44) if d < 7 else (34, 36, 48))
    return img


def decoupler():
    img = new()
    for y in range(S):
        for x in range(S):
            put(img, x, y, (230, 180, 40) if ((x + y) // 3) % 2 else (30, 30, 34))
    line(img, 0, 0, 15, 0, STEEL[4])
    line(img, 0, 15, 15, 15, STEEL[1])
    for x in (3, 8, 13):
        put(img, x, 7, STEEL[5])
    return img


def capsule_skin():
    rng = random.Random(93)
    img = new()
    for y in range(S):
        for x in range(S):
            put(img, x, y, HULL[2 + (rng.random() < 0.3)])
    for y in (4, 11):
        line(img, 0, y, 15, y, HULL[0])
    put(img, 3, 7, (200, 40, 40))
    put(img, 12, 7, (60, 200, 90))
    return img


def capsule_window():
    img = new()
    rect(img, 0, 0, 15, 15, (40, 70, 110))
    for y in range(S):
        for x in range(S):
            if x + y < 9:
                put(img, x, y, (110, 160, 210))
    frame(img, 0, 0, 15, 15, STEEL[4], STEEL[1])
    return img


def heat_shield():
    rng = random.Random(94)
    img = new()
    for y in range(S):
        for x in range(S):
            put(img, x, y, [(46, 34, 28), (64, 46, 36), (82, 60, 44)][rng.randint(0, 2)])
    for i in range(0, 16, 4):
        line(img, i, 0, i, 15, (34, 26, 22))
        line(img, 0, i, 15, i, (34, 26, 22))
    return img


def fins():
    img = new()
    for y in range(S):
        for x in range(S):
            put(img, x, y, (200, 40, 40) if (y // 4) % 2 == 0 else HULL[3])
    line(img, 0, 0, 0, 15, (120, 20, 20))
    return img


def legs():
    img = machine_side(95, vents=False)
    for y in (3, 8, 12):
        line(img, 2, y, 13, y, (230, 180, 40))
    return img


def refinery_front(lit, phase=0):
    img = machine_side(101, vents=False)
    # Колонна синтеза со смотровыми окнами
    rect(img, 3, 2, 7, 13, STEEL[1])
    frame(img, 3, 2, 7, 13, STEEL[4], STEEL[0])
    for i, y in enumerate((4, 7, 10)):
        level = KEROSENE[(i + phase) % 4] if lit else (40, 30, 20)
        rect(img, 4, y, 6, y + 1, level)
    # Трубы и вентиль
    line(img, 9, 4, 13, 4, COPPER[2])
    line(img, 9, 5, 13, 5, COPPER[0])
    line(img, 12, 4, 12, 12, COPPER[2])
    rect(img, 9, 10, 13, 13, (230, 180, 40) if lit else STEEL[2])
    put(img, 11, 11, (200, 40, 40))
    return img


def separator_front(lit, phase=0):
    img = machine_side(102, vents=False)
    # Сосуд Дьюара с жидким кислородом
    for y in range(2, 14):
        for x in range(4, 12):
            if abs(x - 7.5) < 3.6:
                put(img, x, y, LOX[1] if lit and y > 6 else (40, 60, 80))
    frame(img, 3, 1, 12, 14, STEEL[4], STEEL[0])
    if lit:
        rng = random.Random(600 + phase)
        for _ in range(5):
            put(img, rng.randint(4, 11), rng.randint(2, 6), (255, 255, 255))
    # Иней по краям
    for x, y in ((1, 2), (14, 5), (2, 11), (13, 13), (1, 8)):
        put(img, x, y, (230, 245, 255))
    return img


def separator_top():
    def detail(img):
        for cx, cy in ((5, 5), (10, 5), (5, 10), (10, 10)):
            for y in range(cy - 2, cy + 3):
                for x in range(cx - 2, cx + 3):
                    if math.hypot(x - cx, y - cy) < 2.2:
                        put(img, x, y, (30, 34, 40) if math.hypot(x - cx, y - cy) < 1.2 else STEEL[4])
    return machine_top(103, detail)


def assembler_front():
    img = machine_side(104, vents=False)
    rect(img, 2, 2, 13, 10, (20, 50, 100))
    for i in range(3, 13, 3):
        line(img, i, 3, i, 9, (30, 70, 138))
    # Силуэт ракеты на чертеже
    line(img, 7, 3, 7, 8, (220, 235, 255))
    line(img, 8, 3, 8, 8, (220, 235, 255))
    put(img, 6, 8, (220, 235, 255))
    put(img, 9, 8, (220, 235, 255))
    frame(img, 1, 1, 14, 11, STEEL[4], STEEL[0])
    rect(img, 3, 12, 12, 13, (230, 180, 40))
    return img


def assembler_top():
    def detail(img):
        line(img, 3, 12, 12, 3, (230, 180, 40))
        line(img, 3, 3, 12, 12, (230, 180, 40))
        rect(img, 6, 6, 9, 9, STEEL[1])
    return machine_top(105, detail)


def launch_pad_top():
    rng = random.Random(106)
    img = new()
    concrete = [(96, 98, 102), (110, 112, 116), (124, 126, 130)]
    for y in range(S):
        for x in range(S):
            put(img, x, y, concrete[rng.randint(0, 2)])
    frame(img, 0, 0, 15, 15, (140, 142, 146), (70, 72, 76))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, STEEL[5])
        put(img, x + 1, y + 1, STEEL[0])
    # Жёлтые метки разметки
    for i in range(5, 11):
        put(img, i, 1, (230, 180, 40))
        put(img, i, 14, (230, 180, 40))
    return img


def launch_pad_side():
    img = new()
    rect(img, 0, 0, 15, 15, (92, 94, 98))
    rect(img, 0, 0, 15, 3, (124, 126, 130))
    for x in range(0, 16):
        put(img, x, 4, (230, 180, 40) if (x // 2) % 2 else (30, 30, 34))
    for x in (3, 12):
        line(img, x, 6, x, 15, (70, 72, 76))
    return img


def fluid_frames(palette, size, frames, seed):
    """Медленно колышущаяся жидкость: кадры для анимированной текстуры."""
    rng = random.Random(seed)
    waves = [(rng.uniform(0.2, 0.6), rng.uniform(0.2, 0.6), rng.uniform(0, 6.28)) for _ in range(3)]
    result = []
    for f in range(frames):
        img = Image.new("RGBA", (size, size))
        t = f / frames * 2 * math.pi
        for y in range(size):
            for x in range(size):
                v = sum(math.sin(x * a + y * b + p + t) for a, b, p in waves) / 3
                idx = max(0, min(len(palette) - 1, int((v + 1) / 2 * len(palette))))
                img.putpixel((x, y), rgba(palette[idx], 210))
        result.append(img)
    return result


def save_fluid(palette, name, seed):
    for kind, size in (("still", 16), ("flow", 32)):
        frames = fluid_frames(palette, size, 16, seed + size)
        sheet = Image.new("RGBA", (size, size * len(frames)))
        for i, f in enumerate(frames):
            sheet.paste(f, (0, size * i))
        sheet.save(BLOCK / f"{name}_{kind}.png")
        (BLOCK / f"{name}_{kind}.png.mcmeta").write_text('{\n  "animation": {\n    "frametime": 3,\n    "interpolate": true\n  }\n}\n', encoding="utf-8")


def bucket(fluid):
    img = new()
    metal = [(70, 74, 84), (120, 126, 138), (170, 176, 188), (210, 216, 226)]
    body = [(3, 5), (12, 5), (11, 14), (4, 14)]
    fill_poly(img, body, lambda x, y: metal[2] if x < 7 else metal[1])
    for y in range(5, 8):
        for x in range(3, 13):
            if inside(body, x, y) or y == 5:
                put(img, x, y, fluid[2] if y == 5 else fluid[1])
    line(img, 3, 5, 12, 5, fluid[3])
    # Ручка
    for x in range(3, 13):
        y = 4 - round(2.6 * math.sin((x - 3) / 9 * math.pi))
        put(img, x, y, metal[1])
    outline(img, metal[0])
    return img


def rocket_icon():
    img = new()
    body = [(7, 1), (9, 1), (11, 5), (11, 11), (5, 11), (5, 5)]
    fill_poly(img, body, lambda x, y: HULL[3] if x < 9 else HULL[1])
    rect(img, 6, 6, 9, 7, ORANGE[1])
    for x, y in ((7, 3), (8, 3)):
        put(img, x, y, (60, 110, 170))
    fill_poly(img, [(5, 9), (5, 13), (3, 14), (3, 11)], lambda x, y: (200, 40, 40))
    fill_poly(img, [(11, 9), (11, 13), (13, 14), (13, 11)], lambda x, y: (160, 30, 30))
    rect(img, 6, 12, 9, 13, STEEL[1])
    put(img, 7, 14, GLOW[3])
    put(img, 8, 14, GLOW[2])
    put(img, 7, 15, GLOW[1])
    outline(img, (30, 32, 40))
    return img


REGOLITH = [(118, 118, 120), (138, 138, 140), (156, 156, 158), (172, 172, 174), (188, 188, 190)]
MARIA = [(58, 58, 62), (72, 72, 76), (86, 86, 90), (100, 100, 104), (116, 116, 120)]


def dust(palette, seed):
    """Пыль: мелкая зернистость, редкие камешки с тенью."""
    rng = random.Random(seed)
    img = new()
    for y in range(S):
        for x in range(S):
            put(img, x, y, palette[rng.choice((1, 2, 2, 2, 3))])
    for _ in range(5):
        x, y = rng.randint(1, 14), rng.randint(1, 14)
        put(img, x, y, palette[4])
        put(img, x + 1, y + 1, palette[0])
    return img


def footprints(base, palette):
    """Отпечаток подошвы скафандра: рифлёный след, вдавленный в пыль."""
    img = base.copy()
    for ox, oy in ((3, 1), (9, 8)):
        for y in range(6):
            for x in range(3):
                if (y == 0 or y == 5) and x in (0, 2):
                    continue
                put(img, ox + x, oy + y, palette[0] if (y + x) % 2 else palette[1])
        line(img, ox, oy + 6, ox + 2, oy + 6, palette[4])
    return img


def rock(palette, seed, cracks=3):
    rng = random.Random(seed)
    img = new()
    noisy(img, 0, 0, 15, 15, palette, 2, rng, spread=1)
    for _ in range(cracks):
        x, y = rng.randint(0, 15), rng.randint(0, 15)
        for _ in range(rng.randint(3, 6)):
            put(img, x, y, palette[0])
            x += rng.choice((-1, 0, 1))
            y += rng.choice((0, 1))
    return img


ANORTHOSITE = [(150, 148, 142), (178, 176, 170), (198, 196, 190), (214, 212, 206), (230, 228, 222)]
BASALT = [(34, 34, 38), (48, 48, 54), (62, 62, 68), (78, 78, 86), (98, 98, 106)]
ILMENITE = [(18, 18, 22), (40, 40, 48), (70, 72, 84), (120, 124, 140), (190, 196, 214)]
ICE = [(150, 190, 220), (180, 214, 236), (210, 232, 246), (236, 246, 252)]


def ilmenite_ore():
    img = rock(BASALT, 131, 2)
    rng = random.Random(132)
    for cx, cy in ((4, 4), (11, 3), (7, 9), (12, 12), (3, 12)):
        for _ in range(4):
            x, y = cx + rng.randint(-1, 1), cy + rng.randint(-1, 1)
            put(img, x, y, ILMENITE[rng.randint(1, 3)])
        put(img, cx, cy, ILMENITE[4])
    return img


def ice_regolith():
    img = dust(REGOLITH, 133)
    rng = random.Random(134)
    for _ in range(9):
        x, y = rng.randint(0, 14), rng.randint(0, 14)
        put(img, x, y, ICE[rng.randint(1, 3)])
        if rng.random() < 0.5:
            put(img, x + 1, y, ICE[0])
    return img


def raw_ilmenite():
    img = chunk([(18, 18, 22)] + ILMENITE[1:4] + [(150, 156, 176), (200, 206, 224)], 135,
                [(8, 8, 4.8), (5, 10.5, 3.2), (11, 5.5, 3.0)])
    for x, y in ((6, 6), (9, 9), (7, 11)):
        put(img, x, y, (220, 226, 240))
    return img


def parachute_canopy():
    img = new()
    for y in range(S):
        for x in range(S):
            put(img, x, y, (210, 40, 36) if ((x + 2) // 4) % 2 else (244, 244, 240))
    line(img, 0, 15, 15, 15, (120, 20, 20))
    return img


def parachute_line():
    img = new()
    rect(img, 0, 0, 15, 15, (210, 205, 190))
    for y in range(0, 16, 3):
        line(img, 0, y, 15, y, (170, 165, 150))
    return img


# ---------------------------------------------------------------- жизнеобеспечение

SUIT = [(150, 156, 166), (190, 195, 204), (220, 224, 230), (240, 242, 246), (252, 252, 253)]
SUIT_ORANGE = [(170, 70, 20), (226, 110, 36), (250, 150, 70)]
VISOR = [(60, 40, 8), (130, 92, 20), (200, 150, 40), (250, 214, 110)]
HAZARD = (230, 180, 40)


def suit_helmet_icon():
    img = new()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 7.5, y - 8.0)
            if d < 6.6 and y < 14:
                put(img, x, y, SUIT[3] if x + y < 14 else SUIT[2] if d < 5.5 else SUIT[1])
    # Золотой светофильтр визора
    for y in range(6, 11):
        for x in range(3, 13):
            if math.hypot(x - 7.5, (y - 8.2) * 1.6) < 4.8:
                put(img, x, y, VISOR[3] if x < 6 and y < 8 else VISOR[2] if y < 9 else VISOR[1])
    rect(img, 3, 13, 12, 14, SUIT_ORANGE[1])
    line(img, 3, 13, 12, 13, SUIT_ORANGE[2])
    outline(img, (40, 44, 52))
    return img


def suit_chest_icon():
    img = new()
    body = [(2, 3), (5, 2), (10, 2), (13, 3), (13, 8), (11, 8), (11, 14), (4, 14), (4, 8), (2, 8)]
    fill_poly(img, body, lambda x, y: SUIT[3] if x < 8 else SUIT[2])
    # Пульт на груди и шланг к баллону
    rect(img, 6, 6, 9, 9, (40, 60, 90))
    put(img, 7, 7, (90, 230, 120))
    put(img, 8, 7, (240, 80, 60))
    put(img, 7, 8, (90, 170, 240))
    line(img, 4, 11, 11, 11, SUIT_ORANGE[1])
    line(img, 2, 7, 4, 7, SUIT_ORANGE[1])
    line(img, 11, 7, 13, 7, SUIT_ORANGE[1])
    outline(img, (40, 44, 52))
    return img


def suit_legs_icon():
    img = new()
    legs = [(3, 2), (12, 2), (12, 14), (9, 14), (8, 6), (7, 6), (6, 14), (3, 14)]
    fill_poly(img, legs, lambda x, y: SUIT[3] if x < 7 else SUIT[2])
    line(img, 3, 3, 12, 3, SUIT_ORANGE[1])
    for x0 in (3, 9):
        rect(img, x0, 8, x0 + 2, 9, SUIT[0])
    outline(img, (40, 44, 52))
    return img


def suit_boots_icon():
    img = new()
    for x0 in (1, 9):
        rect(img, x0 + 1, 6, x0 + 4, 11, SUIT[3])
        rect(img, x0, 11, x0 + 5, 13, (84, 88, 96))
        line(img, x0, 13, x0 + 5, 13, (50, 52, 58))
        line(img, x0 + 1, 7, x0 + 4, 7, SUIT_ORANGE[1])
    outline(img, (40, 44, 52))
    return img


def oxygen_tank_icon():
    img = new()
    tank = [(70, 140, 200), (110, 180, 230), (170, 215, 245), (230, 245, 255)]
    for y in range(4, 15):
        for x in range(4, 12):
            d = abs(x - 7.5)
            if y == 4 and d > 2.5 or y == 14 and d > 2.8:
                continue
            put(img, x, y, tank[3] if x == 5 else tank[2] if x < 7 else tank[1] if x < 10 else tank[0])
    line(img, 4, 12, 11, 12, (240, 240, 240))
    rect(img, 7, 1, 8, 3, STEEL[2])
    line(img, 6, 1, 9, 1, (200, 40, 40))
    outline(img, (30, 50, 70))
    return img


def dosimeter_icon():
    img = new()
    rect(img, 3, 2, 12, 14, (226, 186, 40))
    frame(img, 3, 2, 12, 14, (250, 220, 90), (150, 110, 20))
    rect(img, 5, 4, 10, 8, (24, 40, 30))
    for x in (6, 8):
        line(img, x, 5, x, 7, (110, 240, 140))
    put(img, 9, 7, (110, 240, 140))
    # Знак радиации
    for x, y in ((7, 10), (8, 10), (6, 12), (9, 12), (7, 11), (8, 11)):
        put(img, x, y, (30, 30, 30))
    rect(img, 6, 0, 7, 1, STEEL[1])
    outline(img, (60, 44, 10))
    return img


def airlock_icon():
    img = new()
    rect(img, 4, 1, 11, 14, STEEL[3])
    frame(img, 4, 1, 11, 14, STEEL[5], STEEL[0])
    for y in range(3, 7):
        for x in range(5, 11):
            if math.hypot(x - 7.5, y - 4.5) < 2.3:
                put(img, x, y, CYAN[2] if x + y < 11 else CYAN[1])
    for x in range(5, 11):
        put(img, x, 11, HAZARD if x % 2 else (30, 30, 34))
    put(img, 10, 8, (200, 40, 40))
    return img


def airlock_door(top):
    rng = random.Random(140 + top)
    img = new()
    noisy(img, 0, 0, 15, 15, STEEL, 3, rng)
    frame(img, 0, 0, 15, 15, STEEL[5], STEEL[0])
    line(img, 2, 0, 2, 15, STEEL[1])
    line(img, 13, 0, 13, 15, STEEL[1])
    if top:
        # Иллюминатор с толстым уплотнителем
        for y in range(S):
            for x in range(S):
                d = math.hypot(x - 7.5, y - 8.5)
                if d < 4.2:
                    put(img, x, y, CYAN[3] if x + y < 13 else CYAN[2] if d < 3 else CYAN[1])
                elif d < 5.4:
                    put(img, x, y, (40, 42, 48) if y > 8 else (70, 72, 80))
    else:
        for x in range(1, 15):
            put(img, x, 12, HAZARD if (x // 2) % 2 else (30, 30, 34))
            put(img, x, 13, (30, 30, 34) if (x // 2) % 2 else HAZARD)
        # Рукоять и замок
        rect(img, 11, 3, 12, 7, STEEL[1])
        put(img, 11, 5, (200, 40, 40))
        line(img, 4, 2, 4, 9, STEEL[1])
    return img


def oxygen_station_front(lit, phase=0):
    img = machine_side(141, vents=False)
    # Манометр и шланг с пистолетом
    for y in range(2, 8):
        for x in range(2, 8):
            d = math.hypot(x - 4.5, y - 4.5)
            if d < 2.9:
                put(img, x, y, (236, 240, 244) if d < 2.2 else STEEL[0])
    angle = math.pi * (0.9 - (0.2 + 0.15 * phase if lit else 0.0))
    put(img, 4 + round(math.cos(angle) * 1.6), 4 - round(math.sin(angle) * 1.6), (200, 40, 40))
    rect(img, 9, 2, 13, 13, (36, 52, 70))
    frame(img, 9, 2, 13, 13, STEEL[4], STEEL[0])
    level = 3 + (phase if lit else 0)
    for y in range(12 - level * 2, 13):
        line(img, 10, y, 12, y, LOX[1] if lit else (60, 80, 100))
    for x in range(2, 8):
        put(img, x, 12, (40, 120, 200))
    line(img, 2, 10, 2, 12, (40, 120, 200))
    rect(img, 3, 9, 6, 10, STEEL[4] if lit else STEEL[3])
    return img


def oxygen_station_top():
    def detail(img):
        for cx in (5, 10):
            for y in range(3, 13):
                for x in range(cx - 2, cx + 3):
                    if abs(x - cx) < 2 or y in (3, 12):
                        put(img, x, y, (110, 180, 230) if x < cx else (70, 140, 200))
            rect(img, cx - 1, 2, cx, 2, STEEL[1])
    return machine_top(142, detail)


def sealer_front(lit, phase=0):
    img = machine_side(143, vents=False)
    rect(img, 2, 2, 13, 13, (22, 26, 32))
    frame(img, 1, 1, 14, 14, STEEL[4], STEEL[0])
    # Крыльчатка вентилятора: на анимации поворачивается
    turn = phase * math.pi / 8
    for y in range(2, 14):
        for x in range(2, 14):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            if d < 1.6:
                put(img, x, y, STEEL[4])
            elif d < 5.2:
                a = (math.atan2(dy, dx) - turn) % (math.pi / 2)
                if a < 0.9:
                    put(img, x, y, (130, 220, 160) if lit and a < 0.45 else (90, 170, 120) if lit else STEEL[3] if a < 0.45 else STEEL[2])
            elif d < 6.3:
                put(img, x, y, STEEL[1] if y > 7 else STEEL[3])
    put(img, 13, 13, (90, 230, 120) if lit else (120, 40, 30))
    return img


def sealer_top():
    def detail(img):
        rect(img, 4, 4, 11, 11, (22, 26, 32))
        for i in range(4, 12, 2):
            line(img, 4, i, 11, i, STEEL[2])
        frame(img, 3, 3, 12, 12, STEEL[1], STEEL[4])
    return machine_top(144, detail)


def spacesuit_layer(second):
    """Текстура брони 64×32 в раскладке модели игрока."""
    img = new(64, 32)
    rng = random.Random(150 + second)

    def cloth(x0, y0, x1, y1):
        for y in range(y0, y1):
            for x in range(x0, x1):
                put(img, x, y, SUIT[3] if rng.random() < 0.8 else SUIT[2])

    if not second:
        # Шлем (голова 8×8×8, развёртка с (0, 0))
        cloth(8, 0, 24, 8)
        cloth(0, 8, 32, 16)
        for x in range(9, 15):
            for y in range(10, 14):
                put(img, x, y, VISOR[3] if x < 11 and y < 12 else VISOR[2] if y < 13 else VISOR[1])
        for x in range(0, 32):
            put(img, x, 15, SUIT_ORANGE[1])
        rect(img, 1, 10, 2, 12, SUIT[0])
        rect(img, 29, 10, 30, 12, SUIT[0])
        # Торс (8×12×4 с (16, 16)) и руки (4×12×4 с (40, 16))
        cloth(20, 16, 36, 20)
        cloth(16, 20, 40, 32)
        cloth(44, 16, 52, 20)
        cloth(40, 20, 56, 32)
        rect(img, 22, 22, 25, 25, (40, 60, 90))
        put(img, 23, 23, (90, 230, 120))
        put(img, 24, 23, (240, 80, 60))
        put(img, 23, 24, (90, 170, 240))
        for x in range(16, 40):
            put(img, x, 29, SUIT_ORANGE[1])
        # Ранец жизнеобеспечения на спине
        rect(img, 33, 21, 38, 30, SUIT[0])
        frame(img, 33, 21, 38, 30, SUIT[1], (110, 114, 124))
        rect(img, 35, 23, 36, 24, (90, 230, 120))
        for x in range(40, 56):
            put(img, x, 22, SUIT_ORANGE[1])
            put(img, x, 30, (84, 88, 96))
            put(img, x, 31, (84, 88, 96))
        # Ботинки (ноги 4×12×4 с (0, 16)): только низ
        for y in range(26, 32):
            for x in range(0, 16):
                put(img, x, y, (84, 88, 96) if y >= 30 else SUIT[2])
        for x in range(0, 16):
            put(img, x, 27, SUIT_ORANGE[1])
        rect(img, 8, 16, 11, 19, (60, 62, 68))
    else:
        # Пояс брюк на торсе и штанины
        cloth(16, 26, 40, 32)
        for x in range(16, 40):
            put(img, x, 26, SUIT[0])
        cloth(4, 16, 8, 20)
        cloth(0, 20, 16, 32)
        for x in range(0, 16):
            put(img, x, 21, SUIT_ORANGE[1])
        for x0 in (0, 4, 8, 12):
            rect(img, x0 + 1, 24, x0 + 2, 25, SUIT[0])
    return img


# ---------------------------------------------------------------- лунная добыча

HELIUM = [(90, 50, 140), (150, 100, 210), (200, 160, 240), (240, 220, 255)]
METEOR = [(28, 24, 22), (44, 38, 34), (62, 54, 48), (86, 76, 66)]


def processor_front(lit, phase=0):
    img = machine_side(160, vents=False)
    # Барабан печи с окошком: внутри светится прокалённый грунт
    rect(img, 2, 2, 13, 9, (40, 42, 48))
    frame(img, 2, 2, 13, 9, STEEL[4], STEEL[0])
    for x in range(4, 12):
        for y in range(4, 8):
            glow = GLOW[(x + y + phase) % 4] if lit else (70, 64, 60)
            put(img, x, y, glow if (x + y + phase) % 3 else (120, 110, 100) if not lit else GLOW[1])
    # Шкала гелия и выходной патрубок
    rect(img, 3, 11, 9, 13, (30, 26, 40))
    for x in range(3, 3 + (2 + phase if lit else 1)):
        line(img, x, 11, x, 13, HELIUM[2])
    rect(img, 11, 11, 13, 13, STEEL[2])
    put(img, 12, 12, (200, 40, 40) if not lit else (90, 230, 120))
    return img


def processor_top():
    def detail(img):
        # Загрузочная воронка
        for i in range(5):
            frame(img, 2 + i, 2 + i, 13 - i, 13 - i, STEEL[4 - min(i, 3)], STEEL[0])
        rect(img, 7, 7, 8, 8, (20, 20, 24))
    return machine_top(161, detail)


def meteorite_block():
    rng = random.Random(162)
    img = new()
    for y in range(S):
        for x in range(S):
            put(img, x, y, METEOR[rng.randint(0, 2)])
    # Корка плавления — тёмные «капли» и металлические проблески
    for _ in range(9):
        x, y = rng.randint(0, 15), rng.randint(0, 15)
        put(img, x, y, (190, 188, 180))
        if rng.random() < 0.5:
            put(img, min(15, x + 1), y, (150, 148, 140))
    for _ in range(6):
        x, y = rng.randint(1, 14), rng.randint(1, 14)
        put(img, x, y, (110, 70, 40))
    return img


def gas_canister(fill=None):
    img = new()
    body = fill or [(120, 128, 140), (160, 168, 180), (200, 206, 216)]
    for y in range(4, 15):
        for x in range(5, 11):
            if y in (4, 14) and x in (5, 10):
                continue
            put(img, x, y, body[2] if x == 6 else body[1] if x < 9 else body[0])
    rect(img, 7, 1, 8, 3, STEEL[3])
    line(img, 6, 1, 9, 1, STEEL[1])
    if fill:
        # Знак He-3 на этикетке
        rect(img, 6, 8, 9, 10, (240, 240, 245))
        put(img, 7, 9, HELIUM[0])
        put(img, 8, 9, HELIUM[0])
    outline(img, (40, 40, 52))
    return img


def raw_meteoric_iron():
    img = chunk(METEOR + [(110, 98, 86), (140, 126, 110)], 163, [(8, 8, 4.8), (5, 10, 3.0), (11, 6, 3.0)])
    for x, y in ((6, 7), (9, 9), (10, 6), (7, 10)):
        put(img, x, y, (196, 192, 184))
    return img


# ---------------------------------------------------------------- «Эхо» и «Заря»

ECHO = [(6, 6, 10), (12, 11, 18), (20, 18, 28), (30, 26, 40)]
VEIN = [(70, 40, 120), (120, 80, 200), (180, 140, 255)]
CRYSTAL = [(20, 70, 90), (40, 140, 160), (90, 210, 220), (170, 245, 245), (235, 255, 255)]


def echo_stone(phase=0):
    """Эховый камень: почти чёрный, с тонкими геометричными прожилками, которые медленно разгораются."""
    rng = random.Random(141)
    img = new()
    for y in range(S):
        for x in range(S):
            put(img, x, y, ECHO[rng.choice((0, 0, 1, 1, 2))])
    glow = (0.35, 0.6, 1.0, 0.6)[phase % 4]
    lines = [((1, 3), (6, 3), (6, 8)), ((9, 1), (9, 6), (14, 6)), ((3, 11), (8, 11), (8, 14)), ((12, 9), (12, 13))]
    for path in lines:
        for (x0, y0), (x1, y1) in zip(path, path[1:]):
            for i in range(max(abs(x1 - x0), abs(y1 - y0)) + 1):
                x = x0 + (i if x1 > x0 else -i if x1 < x0 else 0)
                y = y0 + (i if y1 > y0 else -i if y1 < y0 else 0)
                c = VEIN[1]
                put(img, x, y, tuple(int(ECHO[1][k] + (c[k] - ECHO[1][k]) * glow) for k in range(3)))
        x, y = path[-1]
        put(img, x, y, tuple(int(ECHO[1][k] + (VEIN[2][k] - ECHO[1][k]) * glow) for k in range(3)))
    return img


def glowing_crystal():
    """Кристаллы растут пучком из грунта (как друза аметиста, но светятся бирюзой)."""
    img = new()
    for base_x, height, lean in ((4, 9, -1), (8, 13, 0), (11, 8, 1), (6, 6, 0)):
        for i in range(height):
            y = 15 - i
            cx = base_x + (lean * i) // 4
            w = 1 if i > height - 3 else 2
            for dx in range(-w + 1, w):
                c = CRYSTAL[min(4, 1 + (i * 3) // height + (1 if dx < 0 else 0))]
                put(img, cx + dx, y, c)
        put(img, base_x + (lean * (height - 1)) // 4, 16 - height, CRYSTAL[4])
    return img


def crystal_shard():
    img = new()
    pts = [(9, 3), (10, 4), (8, 4), (9, 5), (8, 6), (7, 7), (8, 7), (6, 8), (7, 9), (6, 10), (5, 11), (6, 11), (5, 12)]
    for i, (x, y) in enumerate(pts):
        put(img, x, y, CRYSTAL[3 if i % 3 else 2])
        put(img, x + 1, y + 1, CRYSTAL[1])
    put(img, 9, 3, CRYSTAL[4])
    outline(img, (8, 30, 40))
    return img


def echo_shard():
    """Осколок Эха: чёрный клин с фиолетовым свечением по граням."""
    img = new()
    rng = random.Random(142)
    for y in range(2, 14):
        half = max(0, 4 - abs(y - 7) // 2)
        for x in range(8 - half, 8 + half + 1):
            put(img, x, y, ECHO[rng.randint(0, 2)])
        put(img, 8 - half, y, VEIN[1])
        put(img, 8 + half, y, VEIN[0])
    for x, y in ((8, 4), (7, 6), (8, 8), (9, 10)):
        put(img, x, y, VEIN[2])
    outline(img, (10, 4, 20))
    return img


def rover_icon():
    """Луноход сбоку: рама, сиденья, антенна-зонтик, четыре сетчатых колеса."""
    img = new()
    rect(img, 1, 8, 14, 9, (150, 156, 166))
    rect(img, 3, 6, 5, 7, (70, 74, 82))
    rect(img, 7, 6, 9, 7, (70, 74, 82))
    rect(img, 11, 5, 14, 7, (214, 168, 64))
    rect(img, 12, 1, 12, 5, (150, 156, 166))
    rect(img, 10, 1, 14, 1, (236, 238, 242))
    for cx in (3, 12):
        rect(img, cx - 2, 10, cx + 2, 14, (60, 62, 68))
        rect(img, cx - 1, 11, cx + 1, 13, (110, 114, 122))
        put(img, cx, 12, (40, 40, 44))
    return img


def watcher_skin():
    """Скин Наблюдателя: скафандр «Зари» в пыли, чёрный визор без лица."""
    rng = random.Random(143)
    img = new(64, 64)
    suit = [(176, 178, 182), (196, 198, 202), (214, 216, 220)]
    dust = (130, 124, 112)

    def box(x, y, w, h, colors=suit, dirt=0.0):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                c = colors[rng.randint(0, len(colors) - 1)]
                if rng.random() < dirt * (yy - y + 1) / h:
                    c = dust
                put(img, xx, yy, c)

    # Голова (шлем), тело, руки, ноги — по раскладке скина игрока
    box(0, 0, 32, 16)
    box(16, 16, 24, 16)
    box(40, 16, 16, 16)
    box(0, 16, 16, 16, dirt=0.5)
    box(16, 48, 16, 16, dirt=0.5)
    box(32, 48, 16, 16)
    # Визор: чёрный, с одной холодной искрой отражения
    rect(img, 9, 9, 14, 14, (6, 6, 9))
    put(img, 13, 10, (120, 130, 150))
    rect(img, 8, 8, 15, 8, (120, 122, 128))
    # Ранец на спине, нашивка с флагом на груди
    rect(img, 33, 21, 38, 30, (140, 142, 148))
    rect(img, 34, 22, 37, 24, (90, 92, 98))
    rect(img, 21, 21, 23, 21, (240, 240, 240))
    rect(img, 21, 22, 23, 22, (40, 70, 170))
    rect(img, 21, 23, 23, 23, (190, 40, 40))
    # Перчатки и ботинки
    for x0, y0 in ((40, 28), (32, 60)):
        rect(img, x0, y0, x0 + 15, y0 + 3, (110, 112, 118))
    for x0, y0 in ((0, 28), (16, 60)):
        rect(img, x0, y0, x0 + 15, y0 + 3, (80, 80, 84))
    return img


def main():
    save_animation([echo_stone(i) for i in range(4)], BLOCK, "echo_stone", 30)
    save(glowing_crystal(), BLOCK, "glowing_crystal")
    save(crystal_shard(), ITEM, "crystal_shard")
    save(echo_shard(), ITEM, "echo_shard")
    save(rover_icon(), ITEM, "rover")
    save(watcher_skin(), ASSETS / "entity", "watcher")
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

    save(rocket_hull(), BLOCK, "rocket_hull")
    save(rocket_hull(stripe=True), BLOCK, "rocket_hull_stripe")
    save(engine_bell(), BLOCK, "engine_bell")
    save(engine_bell(gold=True), BLOCK, "engine_bell_heavy")
    save(engine_mount(), BLOCK, "engine_mount")
    save(nozzle_exit(), BLOCK, "nozzle_exit")
    save(decoupler(), BLOCK, "stage_decoupler")
    save(capsule_skin(), BLOCK, "capsule_skin")
    save(capsule_window(), BLOCK, "capsule_window")
    save(heat_shield(), BLOCK, "heat_shield")
    save(fins(), BLOCK, "rocket_fins")
    save(legs(), BLOCK, "landing_legs")
    save(refinery_front(False), BLOCK, "fuel_refinery_front")
    save_animation([refinery_front(True, p) for p in range(4)], BLOCK, "fuel_refinery_front_on", 5)
    save(separator_front(False), BLOCK, "air_separator_front")
    save_animation([separator_front(True, p) for p in range(4)], BLOCK, "air_separator_front_on", 4)
    save(separator_top(), BLOCK, "air_separator_top")
    save(assembler_front(), BLOCK, "rocket_assembler_front")
    save(assembler_top(), BLOCK, "rocket_assembler_top")
    save(launch_pad_top(), BLOCK, "launch_pad_top")
    save(launch_pad_side(), BLOCK, "launch_pad_side")
    save_fluid(KEROSENE, "kerosene", 110)
    save_fluid(LOX, "liquid_oxygen", 120)
    save(bucket(KEROSENE), ITEM, "kerosene_bucket")
    save(bucket(LOX), ITEM, "liquid_oxygen_bucket")
    save(rocket_icon(), ITEM, "assembled_rocket")

    regolith = dust(REGOLITH, 121)
    save(regolith, BLOCK, "regolith")
    save(footprints(regolith, REGOLITH), BLOCK, "regolith_trodden")
    maria = dust(MARIA, 122)
    save(maria, BLOCK, "maria_regolith")
    save(footprints(maria, MARIA), BLOCK, "maria_regolith_trodden")
    save(rock(ANORTHOSITE, 123), BLOCK, "anorthosite")
    save(rock(BASALT, 124), BLOCK, "moon_basalt")
    save(ilmenite_ore(), BLOCK, "ilmenite_ore")
    save(ice_regolith(), BLOCK, "ice_regolith")
    save(raw_ilmenite(), ITEM, "raw_ilmenite")

    save(parachute_canopy(), BLOCK, "parachute_canopy")
    save(parachute_line(), BLOCK, "parachute_line")

    telescope_gui = GUI / "telescope"
    telescope_gui.mkdir(parents=True, exist_ok=True)
    eyepiece().save(telescope_gui / "eyepiece.png")

    save(suit_helmet_icon(), ITEM, "spacesuit_helmet")
    save(suit_chest_icon(), ITEM, "spacesuit_chestplate")
    save(suit_legs_icon(), ITEM, "spacesuit_leggings")
    save(suit_boots_icon(), ITEM, "spacesuit_boots")
    save(oxygen_tank_icon(), ITEM, "oxygen_tank")
    save(dosimeter_icon(), ITEM, "dosimeter")
    save(airlock_icon(), ITEM, "airlock_door")
    save(airlock_door(True), BLOCK, "airlock_door_top")
    save(airlock_door(False), BLOCK, "airlock_door_bottom")
    save(oxygen_station_front(False), BLOCK, "oxygen_station_front")
    save_animation([oxygen_station_front(True, p) for p in range(4)], BLOCK, "oxygen_station_front_on", 4)
    save(oxygen_station_top(), BLOCK, "oxygen_station_top")
    save(sealer_front(False), BLOCK, "sealer_front")
    save_animation([sealer_front(True, p) for p in range(4)], BLOCK, "sealer_front_on", 2)
    save(sealer_top(), BLOCK, "sealer_top")
    # Броня ищется в пространстве имён minecraft по имени материала orbitcore_spacesuit
    armor = ROOT / "src" / "main" / "resources" / "assets" / "minecraft" / "textures" / "models" / "armor"
    armor.mkdir(parents=True, exist_ok=True)
    spacesuit_layer(False).save(armor / "orbitcore_spacesuit_layer_1.png")
    spacesuit_layer(True).save(armor / "orbitcore_spacesuit_layer_2.png")

    save(processor_front(False), BLOCK, "regolith_processor_front")
    save_animation([processor_front(True, p) for p in range(4)], BLOCK, "regolith_processor_front_on", 4)
    save(processor_top(), BLOCK, "regolith_processor_top")
    save(meteorite_block(), BLOCK, "meteorite")
    save(gas_canister(), ITEM, "gas_canister")
    save(gas_canister(HELIUM), ITEM, "helium3_canister")
    save(raw_meteoric_iron(), ITEM, "raw_meteoric_iron")


if __name__ == "__main__":
    main()
