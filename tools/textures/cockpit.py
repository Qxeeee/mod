"""Кабина корабля для полёта от первого лица: рама иллюминатора, приборная панель, экраны.

    python tools/textures/cockpit.py

Окно в центре прозрачное — сквозь него видно мир. Положение экранов и лампочек
должно совпадать с константами в FlightScreen (раздел «кабина»).
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src" / "main" / "resources" / "assets" / "orbitcore" / "textures" / "gui" / "flight" / "cockpit.png"

W, H = 640, 360
WINDOW = (320, 148, 196, 104)  # центр x, центр y, полуось x, полуось y
SCREENS = [(52, 286, 188, 346), (252, 282, 388, 350), (452, 286, 588, 346)]

rng = np.random.default_rng(61)


def superellipse(cx, cy, a, b, n=4.0):
    ys, xs = np.mgrid[0:H, 0:W]
    return (np.abs((xs + 0.5 - cx) / a) ** n + np.abs((ys + 0.5 - cy) / b) ** n)


def main():
    img = np.zeros((H, W, 4))
    ys, xs = np.mgrid[0:H, 0:W]

    # Стенки кабины: тёмный металл, панели со швами и заклёпками
    base = np.array([0.15, 0.16, 0.19])
    grain = rng.normal(0, 0.012, (H, W))
    shade = 0.85 + 0.25 * (1 - ys / H) * 0.6
    img[..., :3] = base * shade[..., None] + grain[..., None]
    img[..., 3] = 1
    for x in range(0, W, 80):
        img[:, x:x + 1, :3] *= 0.55
        img[:, x + 1:x + 2, :3] *= 1.25
    for y in (60, 250):
        img[y:y + 1, :, :3] *= 0.55
        img[y + 1:y + 2, :, :3] *= 1.25
    for x in range(6, W, 80):
        for y in range(10, H, 26):
            img[y:y + 2, x:x + 2, :3] = [0.32, 0.33, 0.36]
            img[y + 2, x:x + 2, :3] = [0.06, 0.06, 0.07]

    # Трубы и кабели по бокам
    for x0, color in ((18, [0.55, 0.42, 0.18]), (30, [0.20, 0.22, 0.26]), (606, [0.20, 0.22, 0.26]), (620, [0.60, 0.16, 0.12])):
        for dx in range(7):
            k = 0.55 + 0.6 * np.sin(np.pi * (dx + 0.5) / 7)
            img[:250, x0 + dx, :3] = np.array(color) * k
        for y in range(20, 250, 44):
            img[y:y + 4, x0 - 1:x0 + 8, :3] = [0.42, 0.43, 0.46]

    # Рама иллюминатора: толстое кольцо, светлее сверху-слева, болты по кругу
    cx, cy, a, b = WINDOW
    inner = superellipse(cx, cy, a, b)
    outer = superellipse(cx, cy, a + 16, b + 16)
    lip = superellipse(cx, cy, a + 4, b + 4)
    frame = (outer <= 1) & (inner > 1)
    light = np.clip(0.5 - ((xs - cx) / (a * 2.5) + (ys - cy) / (b * 2.5)), 0, 1)
    frame_rgb = np.array([0.30, 0.31, 0.34])[None, None, :] * (0.7 + 0.8 * light[..., None])
    img[frame] = np.concatenate([frame_rgb, np.ones((H, W, 1))], -1)[frame]
    img[(lip <= 1) & (inner > 1), :3] *= 0.55
    for i in range(16):
        ang = i / 16 * 2 * np.pi
        bx = int(cx + (a + 9) * np.cos(ang) * (1 + 0.04 * np.cos(4 * ang)))
        by = int(cy + (b + 9) * np.sin(ang) * (1 + 0.04 * np.cos(4 * ang)))
        img[by - 2:by + 2, bx - 2:bx + 2, :3] = [0.55, 0.56, 0.6]
        img[by + 1:by + 2, bx - 2:bx + 2, :3] = [0.18, 0.18, 0.2]
    # Стекло: прозрачное, у края лёгкая дымка и блик
    glass = inner <= 1
    edge = np.clip((inner - 0.55) / 0.45, 0, 1)
    img[glass, :3] = [0.75, 0.85, 1.0]
    img[glass, 3] = (edge ** 3 * 0.25)[glass]
    streak = glass & (np.abs((xs - cx) * 0.7 + (ys - cy) - (-50)) < 7) & (xs < cx)
    img[streak, 3] = np.maximum(img[streak, 3], 0.07)
    img[streak, :3] = 1.0

    # Приборная панель снизу
    panel = ys >= 268
    img[panel, :3] = np.array([0.11, 0.12, 0.14]) + grain[panel][..., None]
    img[268:270, :, :3] = [0.38, 0.39, 0.42]
    img[270:272, :, :3] = [0.05, 0.05, 0.06]
    for x0, y0, x1, y1 in SCREENS:
        img[y0 - 4:y1 + 4, x0 - 4:x1 + 4, :3] = [0.24, 0.25, 0.28]
        img[y0 - 4:y0 - 3, x0 - 4:x1 + 4, :3] = [0.45, 0.46, 0.5]
        img[y0:y1, x0:x1, :3] = [0.02, 0.06, 0.04]
        # Строки развёртки старого экрана
        img[y0:y1:2, x0:x1, :3] *= 0.7
    # Тумблеры между экранами
    for x0 in (204, 220, 236, 404, 420, 436):
        for y0 in (292, 318):
            img[y0:y0 + 14, x0:x0 + 8, :3] = [0.07, 0.07, 0.08]
            img[y0 + 2:y0 + 7, x0 + 3:x0 + 5, :3] = [0.8, 0.8, 0.82]
    # Ряды кнопок под лампочки (сами лампочки мигают в коде)
    for x0 in range(60, 590, 22):
        img[354:358, x0:x0 + 10, :3] = [0.2, 0.2, 0.22]

    # Верхний пульт
    img[0:24, 120:520, :3] = [0.12, 0.13, 0.15]
    img[24:26, 120:520, :3] = [0.36, 0.37, 0.4]
    for x0 in range(140, 500, 30):
        img[5:19, x0:x0 + 18, :3] = [0.06, 0.06, 0.07]
        img[8:16, x0 + 3:x0 + 15, :3] = [0.16, 0.17, 0.2]
    # Жёлто-чёрная полоса у рукоятки аварийного сброса
    for x in range(560, 600):
        for y in range(70, 82):
            img[y, x, :3] = [0.85, 0.7, 0.1] if ((x + y) // 5) % 2 else [0.05, 0.05, 0.05]
    img[82:120, 574:586, :3] = [0.55, 0.12, 0.1]

    data = (np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(data, "RGBA").save(OUT)
    print("cockpit ok")


if __name__ == "__main__":
    main()
