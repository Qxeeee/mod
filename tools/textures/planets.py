"""Реалистичные планеты OrbitCore: Земля, Луна и Солнце.

Планета — шар, на поверхность которого нанесён фрактальный шум: материки, климатические
зоны, горы, шельф, закрученные облака, огни городов. Свет считается по-настоящему:
терминатор с красноватой полосой сумерек, блик Солнца на океане, атмосфера у края.

    python tools/textures/planets.py

Нужны Python 3, numpy и Pillow. Остальные (пиксельные) текстуры — в generate.py.
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "orbitcore" / "textures"


# ---------------------------------------------------------------- шум

class Noise:
    """Градиентный шум Перлина в 3D, векторизованный."""

    def __init__(self, seed):
        rng = np.random.default_rng(seed)
        self.perm = np.concatenate([rng.permutation(256)] * 2)
        g = rng.standard_normal((256, 3))
        self.grad = g / np.linalg.norm(g, axis=1, keepdims=True)

    def __call__(self, p):
        pi = np.floor(p).astype(np.int64)
        pf = p - pi
        pi &= 255
        u = pf * pf * pf * (pf * (pf * 6 - 15) + 10)
        x, y, z = pi[..., 0], pi[..., 1], pi[..., 2]
        perm = self.perm

        def corner(dx, dy, dz):
            h = perm[perm[perm[x + dx] + y + dy] + z + dz]
            g = self.grad[h]
            return g[..., 0] * (pf[..., 0] - dx) + g[..., 1] * (pf[..., 1] - dy) + g[..., 2] * (pf[..., 2] - dz)

        def lerp(a, b, t):
            return a + (b - a) * t

        x00 = lerp(corner(0, 0, 0), corner(1, 0, 0), u[..., 0])
        x10 = lerp(corner(0, 1, 0), corner(1, 1, 0), u[..., 0])
        x01 = lerp(corner(0, 0, 1), corner(1, 0, 1), u[..., 0])
        x11 = lerp(corner(0, 1, 1), corner(1, 1, 1), u[..., 0])
        return lerp(lerp(x00, x10, u[..., 1]), lerp(x01, x11, u[..., 1]), u[..., 2]) * 1.6


# Поворот между октавами, чтобы не было видно сетки
_ROT = np.array([[0.00, 0.80, 0.60], [-0.80, 0.36, -0.48], [-0.60, -0.48, 0.64]])


def fbm(noise, p, octaves, lacunarity=2.0, gain=0.5, ridged=False):
    total = np.zeros(p.shape[:-1])
    amp, norm = 1.0, 0.0
    q = p
    for _ in range(octaves):
        n = noise(q)
        if ridged:
            n = 1 - np.abs(n)
            n = n * n
        total += n * amp
        norm += amp
        amp *= gain
        q = (q @ _ROT) * lacunarity
    return total / norm


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    """Линейная смесь; t — число или поле значений, a и b — цвета или поля цветов."""
    t = np.asarray(t, dtype=float)
    if t.ndim and t.shape[-1] != 3:
        t = t[..., None]
    return a + (b - a) * t


def normalize(v):
    return v / np.linalg.norm(v, axis=-1, keepdims=True)


def rotation(yaw, pitch, roll=0.0):
    """Матрица поворота планеты (в градусах)."""
    a, b, c = np.radians([yaw, pitch, roll])
    ry = np.array([[np.cos(a), 0, np.sin(a)], [0, 1, 0], [-np.sin(a), 0, np.cos(a)]])
    rx = np.array([[1, 0, 0], [0, np.cos(b), -np.sin(b)], [0, np.sin(b), np.cos(b)]])
    rz = np.array([[np.cos(c), -np.sin(c), 0], [np.sin(c), np.cos(c), 0], [0, 0, 1]])
    return rz @ rx @ ry


# ---------------------------------------------------------------- Земля

LAND = Noise(201)
DETAIL = Noise(203)
CLOUD = Noise(202)
CITY = Noise(204)

C = {
    "deep": (0.02, 0.06, 0.17), "ocean": (0.03, 0.12, 0.30), "shelf": (0.07, 0.30, 0.42),
    "rainforest": (0.08, 0.20, 0.07), "forest": (0.13, 0.25, 0.10), "taiga": (0.12, 0.20, 0.13),
    "grass": (0.36, 0.40, 0.20), "savanna": (0.52, 0.47, 0.28), "desert": (0.80, 0.66, 0.45),
    "red_desert": (0.72, 0.45, 0.27), "tundra": (0.42, 0.40, 0.33), "rock": (0.44, 0.40, 0.36),
    "snow": (0.93, 0.95, 0.98), "ice": (0.88, 0.93, 0.98),
}


def col(name):
    return np.array(C[name])


def _continents(p, detail_octaves):
    warp = np.stack([fbm(LAND, p * 0.9 + 11, 4), fbm(LAND, p * 0.9 + 23, 4), fbm(LAND, p * 0.9 + 37, 4)], -1)
    return fbm(LAND, p * 0.95 + warp * 0.45, 5) + 0.18 * fbm(DETAIL, p * 5, detail_octaves - 3)


def _sea_level():
    """Уровень моря подбирается так, чтобы суша занимала около 30 % поверхности, как на Земле."""
    sample = normalize(np.random.default_rng(7).standard_normal((40000, 3)))
    return float(np.quantile(_continents(sample, 6), 0.70))


def earth_surface(p, detail_octaves=10, fine=False):
    """Цвет поверхности Земли в точках p единичной сферы (ось Y — к полюсу)."""
    h = _continents(p, detail_octaves)
    elevation = h - SEA
    land = elevation > 0
    lat = np.degrees(np.arcsin(np.clip(p[..., 1], -1, 1)))
    alat = np.abs(lat)

    mountains = fbm(DETAIL, p * 4 + 3, 6, ridged=True) * smoothstep(0.04, 0.22, elevation)
    height = elevation + mountains * 0.3
    temperature = np.cos(np.radians(alat)) ** 0.8 - height * 0.55 + 0.06 * fbm(DETAIL, p * 3 + 50, 4)
    # Влажность: у экватора и в средних широтах дожди, на 20–30° пояс пустынь, у моря влажнее
    belt = 0.35 * np.exp(-(lat / 10) ** 2) - 0.5 * np.exp(-((alat - 23) / 8) ** 2) + 0.15 * np.exp(-((alat - 52) / 12) ** 2)
    inland = smoothstep(0.0, 0.2, elevation)
    moisture = 0.62 + belt + 0.3 * fbm(LAND, p * 2.4 + 70, 5) - 0.3 * inland

    forest = mix(col("forest"), col("rainforest"), smoothstep(0.8, 0.95, temperature))
    green = mix(mix(col("savanna"), col("grass"), smoothstep(0.3, 0.5, moisture)), forest, smoothstep(0.5, 0.75, moisture))
    dry = mix(col("red_desert"), col("desert"), smoothstep(-0.3, 0.3, fbm(DETAIL, p * 4 + 9, 5)))
    ground = mix(dry, green, smoothstep(0.18, 0.36, moisture))
    cold = mix(col("taiga"), col("tundra"), smoothstep(0.5, 0.38, temperature))
    ground = mix(cold, ground, smoothstep(0.45, 0.6, temperature))
    ground = mix(ground, col("rock"), smoothstep(0.35, 0.7, mountains))
    ground = mix(ground, col("snow"), smoothstep(0.28, 0.2, temperature))
    ground *= (0.88 + 0.24 * fbm(DETAIL, p * 30, 5))[..., None]
    if fine:
        # Вблизи видны хребты, долины и пятна полей
        ridges = fbm(DETAIL, p * 160 + 5, 6, ridged=True)
        ground *= (0.7 + 0.45 * ridges * smoothstep(0.02, 0.15, elevation) + 0.2 * fbm(DETAIL, p * 900, 4))[..., None]

    depth = -elevation
    water = mix(col("shelf"), col("ocean"), smoothstep(0.0, 0.04, depth))
    water = mix(water, col("deep"), smoothstep(0.05, 0.25, depth))

    color = np.where(land[..., None], ground, water)
    ice_edge = 74 + 5 * fbm(DETAIL, p * 3 + 90, 4)
    ice = smoothstep(ice_edge - 1.5, ice_edge + 1.5, alat)
    color = mix(color, col("ice"), ice)
    is_water = (~land) & (ice < 0.5)

    # Огни городов: на тёплой и не слишком сухой суше, гуще у побережий
    coast = smoothstep(0.1, 0.0, elevation)
    clusters = smoothstep(0.1, 0.45, fbm(CITY, p * 7, 4) + coast * 0.25)
    sparkle = smoothstep(0.2, 0.6, fbm(CITY, p * 90, 3))
    lights = land * clusters * sparkle * smoothstep(0.45, 0.7, temperature) * smoothstep(0.2, 0.4, moisture) * (1 - ice)
    return color, is_water, lights


# Циклоны средних широт: (широта, долгота, сила закрутки)
CYCLONES = [(48, -150, 2.0), (57, -30, 2.4), (42, 70, 1.6), (62, 150, 2.0), (-50, -100, -2.2),
            (-45, 20, -1.8), (-58, 120, -2.4), (16, -60, 3.0)]


def _spin(p):
    """Закрутить точку вокруг центров циклонов: чем ближе к центру, тем сильнее."""
    for lat0, lon0, strength in CYCLONES:
        a, b = np.radians(lat0), np.radians(lon0)
        c = np.array([np.cos(a) * np.sin(b), np.sin(a), np.cos(a) * np.cos(b)])
        d = np.arccos(np.clip(p @ c, -1, 1))
        angle = (strength * np.exp(-(d / 0.3) ** 2))[..., None]
        cos, sin = np.cos(angle), np.sin(angle)
        p = p * cos + np.cross(c, p) * sin + c * (p @ c)[..., None] * (1 - cos)
    return p


def earth_clouds(p, octaves=8, fine=False):
    """Плотность облаков 0–1: полоса у экватора, фронты и циклоны средних широт, ясно над пустынями."""
    lat = np.degrees(np.arcsin(np.clip(p[..., 1], -1, 1)))
    alat = np.abs(lat)
    q = _spin(p)
    warp = np.stack([fbm(CLOUD, q * 2 + 7, 4), fbm(CLOUD, q * 2 + 13, 4), fbm(CLOUD, q * 2 + 17, 4)], -1)
    stretch = np.array([1.0, 1.8, 1.0])  # облачные полосы вытянуты вдоль параллелей
    large = fbm(CLOUD, (q + warp * 0.25) * 2.2 * stretch, 5)
    small = fbm(CLOUD, q * 14 + 3, max(2, octaves - 3))
    bands = 0.22 * np.exp(-((lat - 6) / 7) ** 2) + 0.2 * np.exp(-((alat - 55) / 13) ** 2) - 0.22 * np.exp(-((alat - 24) / 9) ** 2)
    c = large + bands + 0.35 * small - 0.05
    if fine:
        # Вблизи облака рвутся на кучевые клочья с резкими краями
        cumulus = fbm(CLOUD, q * 110 + 9, 6)
        return smoothstep(0.06, 0.24, c + 0.3 * cumulus) * (0.65 + 0.35 * smoothstep(-0.3, 0.4, cumulus))
    # Мелкие рваные облака по краям больших масс
    return smoothstep(-0.12, 0.26, c) * (0.7 + 0.3 * smoothstep(-0.2, 0.3, small))


SEA = _sea_level()


def shade_earth(n_view, p_planet, sun, view=np.array([0.0, 0.0, 1.0]), octaves=10, cloud_octaves=8, fine=False):
    """Освещённый цвет Земли. n_view — нормаль в осях камеры, p_planet — точка на планете."""
    albedo, water, lights = earth_surface(p_planet, octaves, fine)
    clouds = earth_clouds(p_planet, cloud_octaves, fine)
    mu = np.sum(n_view * sun, -1)
    day = smoothstep(-0.1, 0.2, mu)
    diffuse = np.clip(mu, 0, 1) ** 0.75
    # Тень облака на поверхности (облако чуть ближе к Солнцу)
    shadow = earth_clouds(p_planet + (0.0015 if fine else 0.006) * sun, max(3, cloud_octaves - 3), fine)
    surface = albedo * (diffuse * (1 - 0.45 * shadow) + 0.015)[..., None]
    # Блик Солнца на воде
    half = normalize(sun + view)
    spec = np.clip(np.sum(n_view * half, -1), 0, 1) ** 70 * 1.4 + np.clip(np.sum(n_view * half, -1), 0, 1) ** 12 * 0.08
    surface += (spec * water * (1 - clouds) * day)[..., None] * np.array([1.0, 0.92, 0.8])
    cloud_light = np.clip(mu * 1.1 + 0.05, 0, 1) ** 0.8
    color = mix(surface, np.array([0.96, 0.97, 0.99]) * cloud_light[..., None], clouds * 0.95)
    # Сумерки: красноватая полоса вдоль терминатора
    twilight = np.exp(-((mu - 0.0) / 0.045) ** 2)
    color += twilight[..., None] * np.array([0.09, 0.03, 0.005]) * (1 - clouds * 0.5)[..., None]
    # Ночная сторона: огни городов сквозь редкие облака
    night = (1 - day) ** 2
    color += (lights * night * (1 - clouds * 0.75))[..., None] * np.array([1.0, 0.72, 0.38]) * 1.3
    return color


def tone(color):
    """Мягкое сжатие ярких мест и гамма."""
    c = 1 - np.exp(-np.maximum(color, 0) * 1.35)
    return np.clip(c / (1 - np.exp(-1.35)), 0, 1) ** (1 / 1.15)


def disk_coords(size, margin):
    radius = size / 2 - margin
    ys, xs = np.mgrid[0:size, 0:size]
    x = (xs + 0.5 - size / 2) / radius
    y = -(ys + 0.5 - size / 2) / radius
    return x, y, x * x + y * y


def earth_disk(size, rot, sun, halo=True, octaves=10):
    """Земля целиком: шар с атмосферой вокруг."""
    margin = size * 0.06 if halo else 1
    x, y, r2 = disk_coords(size, margin)
    inside = r2 <= 1
    z = np.sqrt(np.clip(1 - r2, 0, 1))
    n = np.stack([x, y, z], -1)
    p = n @ rot  # из осей камеры в оси планеты
    color = shade_earth(n, p, sun, octaves=octaves)
    mu = np.sum(n * sun, -1)
    # Атмосфера у края диска: голубая дымка на дневной стороне
    fres = (1 - z) ** 2.2
    atm = np.clip(mu + 0.25, 0, 1)
    color = mix(color, np.array([0.30, 0.52, 1.0]) * atm[..., None], fres * 0.7)
    rgb = tone(color)
    alpha = np.where(inside, 1.0, 0.0)
    # Сглаженный край
    edge = np.sqrt(r2)
    alpha = np.clip((1 - edge) * size / 2 + 0.5, 0, 1)
    if halo:
        d = edge - 1
        radial = np.stack([x, y, np.zeros_like(x)], -1) / np.maximum(edge, 1e-6)[..., None]
        lit = np.clip(np.sum(radial * sun, -1) + 0.45, 0, 1)
        glow = np.exp(-np.maximum(d, 0) / 0.018) * lit * (d > 0)
        alpha = np.maximum(alpha, glow * 0.9)
        halo_rgb = np.array([0.45, 0.65, 1.0])
        rgb = np.where((d > 0)[..., None], halo_rgb, rgb)
    return to_image(rgb, alpha)


def to_image(rgb, alpha):
    data = np.concatenate([rgb, alpha[..., None]], -1)
    return Image.fromarray((np.clip(data, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")


def orbit_view(width=1280, height=720):
    """Вид из иллюминатора с высоты 400 км: внизу Земля в перспективе, наверху тонкая кромка атмосферы."""
    altitude = 400 / 6371
    cam = np.array([0.0, 1 + altitude, 0.0])
    dip = np.degrees(np.arccos(1 / (1 + altitude)))
    pitch = np.radians(-(dip + 9))  # горизонт чуть выше середины кадра
    forward = np.array([0, np.sin(pitch), -np.cos(pitch)])
    right = np.array([1.0, 0, 0])
    up = np.cross(right, forward)
    fov = np.radians(78)
    ys, xs = np.mgrid[0:height, 0:width]
    u = (xs + 0.5) / width * 2 - 1
    v = 1 - (ys + 0.5) / height * 2
    d = forward + right * (u * np.tan(fov / 2))[..., None] + up * (v * np.tan(fov / 2) * height / width)[..., None]
    d = normalize(d)
    # Пересечение луча с шаром радиуса 1
    b = np.sum(d * cam, -1)
    c = np.dot(cam, cam) - 1
    disc = b * b - c
    hit = disc > 0
    t = -b - np.sqrt(np.maximum(disc, 0))
    hit &= t > 0
    point = cam + d * t[..., None]
    n = normalize(point)
    sun = normalize(np.array([0.55, 0.42, -0.72]))
    rot = rotation(52, 0, 0) @ rotation(0, -24, 0)
    p = n @ rot.T
    color = shade_earth(n, p, sun, view=-d, octaves=13, cloud_octaves=10, fine=True)
    # Дымка: чем длиннее путь сквозь воздух (ближе к горизонту), тем голубее
    graze = np.clip(np.sum(n * -d, -1), 0, 1)
    haze = np.exp(-graze * 9)
    lit = np.clip(np.sum(n * sun, -1) + 0.2, 0, 1)
    color = mix(color, np.array([0.42, 0.62, 1.0]) * lit[..., None], haze * 0.8)
    rgb = tone(color)
    alpha = hit.astype(float)
    # Над горизонтом — светящаяся кромка атмосферы: тонкая, яркая у земли, гаснет к чёрному
    closest = np.sqrt(np.maximum(np.dot(cam, cam) - b * b, 0)) - 1
    above = ~hit
    h = np.maximum(closest, 0)
    glow = np.exp(-h / 0.0035) * 0.95 + np.exp(-h / 0.012) * 0.25
    tangent = normalize(cam + d * (-b)[..., None])
    glow_lit = np.clip(np.sum(tangent * sun, -1) + 0.35, 0.05, 1)
    glow_rgb = mix(np.array([0.15, 0.3, 0.9]), np.array([0.75, 0.9, 1.0]), np.exp(-h / 0.002))
    rgb = np.where(above[..., None], tone(glow_rgb * glow_lit[..., None] * 1.6), rgb)
    alpha = np.where(above, np.clip(glow * glow_lit, 0, 1), alpha)
    return to_image(rgb, alpha)


# ---------------------------------------------------------------- Луна

MOON = Noise(301)
# Моря видимой стороны: (широта, долгота, радиус в градусах) — примерно как на настоящей Луне
MARIA = [
    (33, -16, 17), (28, 17, 10), (8, 31, 11), (17, 59, 8), (-4, 51, 9), (-21, -17, 10),
    (-24, -39, 6), (-15, 35, 6), (56, 0, 7), (10, -55, 20), (25, -50, 14), (-10, -25, 7),
    (0, 10, 6), (-30, 5, 4),
]
# Яркие молодые кратеры с лучами: (широта, долгота, радиус)
RAYED = [(-43, -11, 2.4), (10, -20, 2.6), (8, -38, 1.4), (24, -47, 1.5)]


def latlon(p):
    lat = np.degrees(np.arcsin(np.clip(p[..., 1], -1, 1)))
    lon = np.degrees(np.arctan2(p[..., 0], p[..., 2]))
    return lat, lon


def ang_dist(lat, lon, lat0, lon0):
    a, b = np.radians(lat), np.radians(lat0)
    dl = np.radians(lon - lon0)
    return np.degrees(np.arccos(np.clip(np.sin(a) * np.sin(b) + np.cos(a) * np.cos(b) * np.cos(dl), -1, 1)))


def moon_albedo(p):
    lat, lon = latlon(p)
    # Края морей рваные: координаты сдвинуты шумом, а граница размыта
    wlat = lat + fbm(MOON, p * 3 + 40, 5) * 9
    wlon = lon + fbm(MOON, p * 3 + 80, 5) * 9
    edge_noise = fbm(MOON, p * 9, 5) * 4
    maria = np.zeros(p.shape[:-1])
    for lat0, lon0, r in MARIA:
        maria = np.maximum(maria, smoothstep(r + 5, r - 4, ang_dist(wlat, wlon, lat0, lon0) + edge_noise))
    maria *= 0.8 + 0.2 * smoothstep(-0.3, 0.3, fbm(MOON, p * 5 + 12, 4))
    highlands = 0.72 + 0.12 * fbm(MOON, p * 7 + 5, 6) + 0.05 * fbm(MOON, p * 40, 4)
    mare = 0.38 + 0.07 * fbm(MOON, p * 6 + 20, 5)
    a = highlands * (1 - maria) + mare * maria
    # Кратеры: тёмное дно, светлый вал
    rng = np.random.default_rng(302)
    for _ in range(140):
        lat0 = np.degrees(np.arcsin(rng.uniform(-1, 1)))
        lon0 = rng.uniform(-100, 100)
        r = rng.uniform(0.6, 4.0) ** 1.3
        d = ang_dist(lat, lon, lat0, lon0) / r
        a += np.where(d < 1, -0.05 * np.clip(1 - d, 0, 1) ** 0.5, 0) + 0.025 * np.exp(-((d - 1.05) / 0.15) ** 2)
    for lat0, lon0, r in RAYED:
        d = ang_dist(lat, lon, lat0, lon0)
        a += 0.25 * np.exp(-(d / r) ** 2)
        bearing = np.arctan2(lon - lon0, lat - lat0)
        rays = np.clip(np.sin(bearing * 7 + fbm(MOON, p * 3, 3) * 5), 0, 1) ** 3
        a += 0.07 * rays * np.exp(-d / (r * 7)) * smoothstep(r, r * 2, d)
    return np.clip(a, 0.15, 1.1), maria


def moon_phases(size=128):
    """Восемь фаз Луны полосой (0 — полнолуние, 4 — новолуние) для телескопа и иконок."""
    x, y, r2 = disk_coords(size, 1.5)
    z = np.sqrt(np.clip(1 - r2, 0, 1))
    n = np.stack([x, y, z], -1)
    albedo, _ = moon_albedo(n)
    alpha = np.clip((1 - np.sqrt(r2)) * size / 2 + 0.5, 0, 1)
    frames = []
    for phase in range(8):
        angle = np.pi if phase == 4 else (phase if phase < 4 else phase - 8) * np.pi / 4
        sun = np.array([-np.sin(angle), 0.0, np.cos(angle)])
        mu = np.sum(n * sun, -1)
        # У Луны почти нет потемнения к краю: смесь Ламберта и «плоского» закона
        light = np.clip(mu, 0, 1)
        lommel = light / np.maximum(light + z, 1e-3) * 2
        k = 0.35 * light + 0.65 * lommel
        k = k * smoothstep(-0.02, 0.06, mu)
        earthshine = 0.018 * (1 - smoothstep(-0.05, 0.1, mu))
        v = albedo * (k + earthshine)
        rgb = np.stack([v * 0.98, v * 0.97, v * 0.95], -1)
        frames.append(to_image(np.clip(rgb, 0, 1) ** (1 / 1.1), alpha))
    sheet = Image.new("RGBA", (size * 8, size))
    for i, f in enumerate(frames):
        sheet.paste(f, (i * size, 0))
    return sheet


def earth_phases(size=128):
    """Восемь фаз Земли для неба Луны (0 — «полноземлие»): на тёмной стороне горят города."""
    x, y, r2 = disk_coords(size, size * 0.06)
    inside = r2 <= 1
    z = np.sqrt(np.clip(1 - r2, 0, 1))
    n = np.stack([x, y, z], -1)
    rot = rotation(-20, 18, 0)
    p = n @ rot
    sheet = Image.new("RGBA", (size * 8, size))
    for phase in range(8):
        angle = np.pi if phase == 4 else (phase if phase < 4 else phase - 8) * np.pi / 4
        sun = np.array([-np.sin(angle), 0.0, np.cos(angle)])
        color = shade_earth(n, p, sun, octaves=9, cloud_octaves=7)
        mu = np.sum(n * sun, -1)
        fres = (1 - z) ** 2.2
        color = mix(color, np.array([0.30, 0.52, 1.0]) * np.clip(mu + 0.25, 0, 1)[..., None], fres * 0.7)
        rgb = tone(color)
        edge = np.sqrt(r2)
        alpha = np.clip((1 - edge) * size / 2 + 0.5, 0, 1)
        d = edge - 1
        radial = np.stack([x, y, np.zeros_like(x)], -1) / np.maximum(edge, 1e-6)[..., None]
        lit = np.clip(np.sum(radial * sun, -1) + 0.3, 0, 1)
        glow = np.exp(-np.maximum(d, 0) / 0.02) * lit * (d > 0)
        alpha = np.where(inside, alpha, np.maximum(alpha, glow * 0.8))
        rgb = np.where((d > 0)[..., None], np.array([0.45, 0.65, 1.0]), rgb)
        sheet.paste(to_image(rgb, alpha), (phase * size, 0))
    return sheet


def vacuum_sun(size=128):
    """Солнце без атмосферы: резкий белый диск, лёгкое потемнение к краю и тонкая корона."""
    x, y, r2 = disk_coords(size, 0)
    r = np.sqrt(r2)
    disk_r = 0.32
    inside = r < disk_r
    mu = np.sqrt(np.clip(1 - (r / disk_r) ** 2, 0, 1))
    limb = 0.75 + 0.25 * mu
    angle = np.arctan2(y, x)
    streamers = 0.6 + 0.4 * np.abs(np.sin(angle * 3 + 0.4)) * (0.7 + 0.3 * np.sin(angle * 7))
    corona = np.exp(-(r - disk_r) / 0.09) * streamers * (r >= disk_r)
    glow = np.exp(-r / 0.35) * 0.35
    v = np.where(inside, limb, np.clip(corona * 0.9 + glow, 0, 1))
    rgb = np.stack([v, v * 0.98, v * 0.94], -1)
    alpha = np.where(inside, 1.0, np.clip(corona * 0.9 + glow, 0, 1))
    # Аддитивное смешивание в небе Луны: яркость — это и есть прозрачность
    return to_image(rgb * alpha[..., None], alpha)


def main():
    env = TEXTURES / "environment"
    flight = TEXTURES / "gui" / "flight"
    telescope = TEXTURES / "gui" / "telescope"
    for folder in (env, flight, telescope):
        folder.mkdir(parents=True, exist_ok=True)
    jobs = [
        ("moon phases", lambda: moon_phases().save(telescope / "moon.png")),
        ("vacuum sun", lambda: vacuum_sun().save(env / "vacuum_sun.png")),
        ("earth phases", lambda: earth_phases().save(env / "earth_phases.png")),
        ("earth disk", lambda: earth_disk(256, rotation(-20, 18, 0), normalize(np.array([-0.45, 0.25, 0.86]))).save(flight / "earth_disk.png")),
        ("orbit view", lambda: orbit_view().save(flight / "orbit_view.png")),
    ]
    for name, job in jobs:
        job()
        print(name, "ok")


if __name__ == "__main__":
    main()
