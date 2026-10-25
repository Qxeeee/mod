"""Синтезатор звуков OrbitCore.

Все собственные звуки мода (гул пустоты, шёпот в эфире, сигнал «Эха», шумы кабины)
собираются здесь из синусоид и шума, чтобы их можно было пересобрать одной командой:

    python tools/sounds/synthesize.py

Нужны Python 3, numpy и ffmpeg с libvorbis (путь можно задать переменной OC_FFMPEG).
Готовые .ogg кладутся в src/main/resources/assets/orbitcore/sounds. Любой файл можно
заменить своим (например, сделанным в ElevenLabs): подсказки для генерации — в docs/SOUNDS.md.
"""
import os
import shutil
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src" / "main" / "resources" / "assets" / "orbitcore" / "sounds"
RATE = 44100
rng = np.random.default_rng(1969)


# ---------------------------------------------------------------- основа

def t_axis(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def sine(freq, seconds, phase=0.0):
    """Синус; freq может быть массивом (меняющаяся частота)."""
    if np.isscalar(freq):
        return np.sin(2 * np.pi * freq * t_axis(seconds) + phase)
    return np.sin(2 * np.pi * np.cumsum(freq) / RATE + phase)


def noise(seconds):
    return rng.standard_normal(int(seconds * RATE))


def spectral(x, gain):
    """Фильтр в частотной области: gain(f) — множитель для каждой частоты.
    Края дополняются тишиной, чтобы отклик фильтра не заворачивался с конца в начало."""
    pad = RATE // 4
    padded = np.concatenate([np.zeros(pad), x, np.zeros(pad)])
    spectrum = np.fft.rfft(padded)
    freqs = np.fft.rfftfreq(len(padded), 1 / RATE)
    return np.fft.irfft(spectrum * gain(freqs), len(padded))[pad:pad + len(x)]


def lowpass(x, cutoff, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (f / cutoff) ** (2 * order)))


def highpass(x, cutoff, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (cutoff / np.maximum(f, 1e-3)) ** (2 * order)))


def bandpass(x, center, width):
    return spectral(x, lambda f: np.exp(-0.5 * ((f - center) / width) ** 2))


def pink(seconds):
    return spectral(noise(seconds), lambda f: 1 / np.sqrt(np.maximum(f, 20)))


def brown(seconds):
    return spectral(noise(seconds), lambda f: 1 / np.maximum(f, 8))


def resonate(x, freqs, q=40.0):
    """Набор резонансов (как у металлического корпуса)."""
    def gain(f):
        g = np.zeros_like(f)
        for fr in freqs:
            g += 1 / (1 + ((f - fr) / (fr / q)) ** 2)
        return g
    return spectral(x, gain)


def envelope(n, attack, release, curve=2.0):
    """Огибающая: нарастание и затухание в секундах."""
    e = np.ones(n)
    a = min(n, int(attack * RATE))
    r = min(n, int(release * RATE))
    if a:
        e[:a] = np.linspace(0, 1, a) ** curve
    if r:
        e[n - r:] *= np.linspace(1, 0, r) ** curve
    return e


def decay(n, seconds):
    return np.exp(-np.arange(n) / (seconds * RATE))


def smooth_random(seconds, rate_hz, low=0.0, high=1.0):
    """Медленно блуждающая величина (для модуляций)."""
    points = max(4, int(seconds * rate_hz) + 3)
    knots = rng.uniform(low, high, points)
    xs = np.linspace(0, points - 1, int(seconds * RATE))
    i = np.floor(xs).astype(int)
    f = xs - i
    f = f * f * (3 - 2 * f)
    i2 = np.minimum(i + 1, points - 1)
    return knots[i] * (1 - f) + knots[i2] * f


def reverb(x, seconds=2.5, mix=0.35, brightness=3000):
    """Реверберация свёрткой с синтетическим откликом (затухающий шум)."""
    n = int(seconds * RATE)
    # Обрыв в конце дал бы щелчок, а реверберация растянула бы его в шипение
    x = x * envelope(len(x), 0.0, min(0.05, len(x) / RATE / 4), 1)
    ir = lowpass(noise(seconds), brightness, 2) * decay(n, seconds / 6.9)
    ir /= np.sqrt(np.sum(ir ** 2))
    wet = np.fft.irfft(np.fft.rfft(np.concatenate([x, np.zeros(n)])) * np.fft.rfft(np.concatenate([ir, np.zeros(len(x))])),
                       len(x) + n)
    dry = np.concatenate([x, np.zeros(n)])
    return dry * (1 - mix) + wet * mix * 3


def echo(x, delay, feedback, repeats=6, tail=0.0):
    """Эхо: затухающие повторы, каждый темнее предыдущего."""
    d = int(delay * RATE)
    out = np.concatenate([x, np.zeros(d * repeats + int(tail * RATE))])
    layer = x.copy()
    for k in range(1, repeats + 1):
        layer = lowpass(layer, 5000 / k ** 0.5, 1) * feedback
        out[k * d:k * d + len(layer)] += layer
    return out


def concat(*parts):
    return np.concatenate(parts)


def silence(seconds):
    return np.zeros(int(seconds * RATE))


def place(base, x, at):
    """Вставить звук x в base с момента at (секунды)."""
    i = int(at * RATE)
    end = min(len(base), i + len(x))
    base[i:end] += x[:end - i]
    return base


def loopable(x, fade=1.0):
    """Склеить хвост с началом, чтобы звук повторялся без щелчка."""
    n = int(fade * RATE)
    head, body, tail = x[:n], x[n:-n] if n else x, x[-n:]
    ramp = np.linspace(0, 1, n)
    joined = tail * (1 - ramp) + head * ramp
    return np.concatenate([body, joined])


def normalize(x, peak=0.89):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


# ---------------------------------------------------------------- запись

def ffmpeg_path():
    found = os.environ.get("OC_FFMPEG") or shutil.which("ffmpeg")
    for guess in ("C:/Program Files/ShareX/ffmpeg.exe", "C:/ffmpeg/bin/ffmpeg.exe"):
        if not found and Path(guess).exists():
            found = guess
    if not found:
        sys.exit("ffmpeg не найден: установите его или укажите путь в OC_FFMPEG")
    return found


FFMPEG = None


def save(name, x, peak=0.89):
    """Сохранить моно .ogg (позиционные звуки в Minecraft должны быть моно)."""
    global FFMPEG
    FFMPEG = FFMPEG or ffmpeg_path()
    x = normalize(np.nan_to_num(x), peak)
    # Мягкие края, чтобы не щёлкало
    edge = min(len(x) // 4, int(0.004 * RATE))
    x[:edge] *= np.linspace(0, 1, edge)
    x[-edge:] *= np.linspace(1, 0, edge)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    target = OUT / f"{name}.ogg"
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "sound.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4",
                        "-ac", "1", str(target)], check=True)
    print(f"{name}.ogg  {len(x) / RATE:.1f} с")


# ---------------------------------------------------------------- пустота (хоррор-эмбиент Луны)

def void_drone():
    """Гул пустоты: низкие биения, едва слышный «ветер ниоткуда», далёкий скрежет."""
    s = 24.0
    t = t_axis(s)
    drift = smooth_random(s, 0.15, -0.6, 0.6)
    x = 0.5 * sine(36 + drift, s) + 0.35 * sine(36.7 + drift, s) + 0.25 * sine(54.2, s, 1.0)
    # Гармоники, которые слышно и в обычных наушниках, а не только на сабвуфере
    x = np.tanh(x * 2.5) * 0.6
    x += 0.3 * sine(72.4 + drift * 2, s) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.07 * t))
    x += 0.22 * sine(108.6 + drift, s) * smooth_random(s, 0.25, 0.2, 1.0)
    # Диссонанс: тритон сверху, то появляется, то тонет
    x += 0.16 * sine(153.6, s) * smooth_random(s, 0.2) ** 2
    x += 0.07 * sine(217.2 + drift * 3, s) * smooth_random(s, 0.15) ** 3
    # «Ветер ниоткуда» меняется очень медленно, без ритма: иначе его принимают за дыхание
    air = bandpass(pink(s), 320, 160) * smooth_random(s, 0.06, 0.5, 1.0)
    air = air / np.max(np.abs(air))
    x = x * 0.8 + air * 0.16 + normalize(lowpass(brown(s), 90)) * 0.25
    return loopable(normalize(reverb(x, 4.0, 0.4, 900)[:int(s * RATE)]), 2.0)


def void_swell():
    """Взгляд в бездну: медленно нарастающее диссонансное облако и обрыв."""
    s = 9.0
    t = t_axis(s)
    rise = (t / s) ** 2
    x = np.zeros_like(t)
    for f in (220, 233.1, 311.1, 329.6, 466.2, 493.9, 698.5):
        wob = 1 + 0.004 * np.sin(2 * np.pi * rng.uniform(0.2, 0.5) * t + rng.uniform(0, 6))
        x += sine(f * wob * (1 + 0.04 * rise), s) * rng.uniform(0.5, 1.0)
    x += 1.2 * np.tanh(2 * sine(41.2, s))
    x *= rise
    x[int(s * 0.94 * RATE):] *= np.linspace(1, 0, len(x) - int(s * 0.94 * RATE)) ** 3
    return reverb(lowpass(x, 3500), 3.5, 0.45, 2500)


def whisper(seed, reverse=False):
    """Шёпот: «слоги» из шума с формантами, будто кто-то говорит за спиной."""
    local = np.random.default_rng(seed)
    s = local.uniform(1.6, 2.4)
    x = np.zeros(int(s * RATE))
    at = 0.1
    while at < s - 0.35:
        length = local.uniform(0.12, 0.3)
        n = int(length * RATE)
        syllable = noise(length)
        f1, f2 = local.uniform(400, 900), local.uniform(1200, 2600)
        syllable = bandpass(syllable, f1, 120) * 0.8 + bandpass(syllable, f2, 260) + highpass(syllable, 4500) * 0.5
        syllable *= np.sin(np.linspace(0, np.pi, n)) ** 1.5
        place(x, syllable, at)
        at += length + local.uniform(0.02, 0.18)
    if reverse:
        x = x[::-1]
    return reverb(x, 2.0, 0.5, 4000)


def creak(seed, low=70, high=210, seconds=2.2):
    """Металл стонет под нагрузкой: прерывистое трение через резонансы корпуса."""
    local = np.random.default_rng(seed)
    t = t_axis(seconds)
    pitch = low + (high - low) * smooth_random(seconds, 1.5) + 8 * np.sin(2 * np.pi * 3 * t)
    phase = np.cumsum(pitch) / RATE
    stick = (phase % 1.0) ** 6
    grit = smooth_random(seconds, 25, 0, 1) > 0.35
    x = stick * grit + noise(seconds) * 0.05
    x = resonate(x, sorted(local.uniform(180, 2400, 7)), 30)
    x *= envelope(len(x), 0.25, 0.8, 1.5)
    return reverb(x, 2.0, 0.35, 2500)


def heartbeat():
    """Сердцебиение «тук-тук» глухо, как сквозь скафандр."""
    def thump(strength):
        n = int(0.22 * RATE)
        f = 48 + 30 * np.exp(-np.arange(n) / (0.03 * RATE))
        return sine(f, 0.22) * decay(n, 0.05) * strength + lowpass(noise(0.22), 200) * decay(n, 0.01) * 0.3
    x = silence(1.0)
    place(x, thump(1.0), 0.02)
    place(x, thump(0.7), 0.27)
    return lowpass(x, 400)


def breath():
    """Дыхание в шлеме: щелчок клапана, мягкий вдох через нос и выдох ртом, без звона."""
    def phase_(seconds, formants, attack, loud):
        n = int(seconds * RATE)
        src = lowpass(pink(seconds), 2600, 2)
        x = sum(bandpass(src, f, f * 0.45) * g for f, g in formants)
        t = np.linspace(0, 1, n)
        env = np.minimum(1, t / attack) ** 1.5 * (1 - t) ** 1.2
        return x * env * loud
    x = silence(5.0)
    click = highpass(noise(0.02), 2500) * decay(int(0.02 * RATE), 0.003)
    place(x, click * 0.15, 0.05)
    place(x, phase_(1.3, [(450, 0.6), (1100, 0.8), (2300, 0.25)], 0.35, 0.7), 0.08)
    place(x, phase_(1.7, [(350, 1.0), (900, 0.6), (1800, 0.15)], 0.15, 1.0), 1.75)
    # Чуть-чуть замкнутого пространства шлема, но не металлический звон
    return lowpass(reverb(x, 0.4, 0.12, 2500), 3500)[:len(x)]


def knock():
    """Три удара по металлу снаружи. Там никого нет."""
    x = silence(2.4)
    for at, k in ((0.1, 1.0), (0.55, 0.9), (1.0, 1.1)):
        n = int(0.5 * RATE)
        hit = noise(0.5) * decay(n, 0.008) + sine(95, 0.5) * decay(n, 0.09) * 0.8
        place(x, resonate(hit, [95, 230, 410, 780, 1310], 25) * k, at + rng.uniform(-0.03, 0.03))
    return reverb(lowpass(x, 1800), 1.6, 0.3, 1500)


def radio_ghost():
    """Помехи в шлемофоне, и сквозь них — то ли голос, то ли сигнал."""
    s = 3.2
    t = t_axis(s)
    static = highpass(noise(s), 800) * (0.35 + 0.65 * (smooth_random(s, 12) > 0.4))
    # «Голос»: пилообразный тон с плывущими формантами, опускается вниз
    f0 = 140 * (1 - 0.35 * t / s) * (1 + 0.03 * np.sin(2 * np.pi * 5.5 * t))
    saw = 2 * ((np.cumsum(f0) / RATE) % 1.0) - 1
    voice = bandpass(saw, 700, 160) + bandpass(saw, 1150, 200) * 0.7 + bandpass(saw, 2400, 300) * 0.4
    voice *= np.sin(np.pi * np.clip((t - 0.6) / 1.8, 0, 1)) ** 2
    x = normalize(bandpass(static, 1800, 900)) * 0.25 + normalize(voice) * 0.9
    return x * envelope(len(x), 0.02, 0.3)


# ---------------------------------------------------------------- «Эхо»

def signal_pattern(base=880.0):
    """Мотив сигнала: простые числа длительностей и «неправильный» интервал."""
    x = silence(3.0)
    notes = [(0.0, 0.12, 1.0), (0.2, 0.12, 1.0), (0.4, 0.36, 1.5), (0.9, 0.12, 1.335), (1.1, 0.48, 0.749)]
    for at, length, ratio in notes:
        n = int(length * RATE)
        tone = sine(base * ratio, length) * envelope(n, 0.01, 0.04, 1)
        tone *= 0.6 + 0.4 * np.sign(sine(30, length))  # «цифровая» модуляция
        place(x, tone, at)
    return x


def echo_signal():
    """Сигнал «Эха»: мотив, плывущий как на старой плёнке, тонет в эхе и помехах."""
    motif = signal_pattern()
    t = t_axis(len(motif) / RATE)
    wow = 1 + 0.004 * np.sin(2 * np.pi * 0.7 * t)
    idx = np.clip(np.cumsum(wow) - 1, 0, len(motif) - 1).astype(int)
    motif = motif[idx] * (0.8 + 0.2 * sine(57, len(motif) / RATE))
    x = echo(motif, 0.37, 0.6, 7, 0.5)
    s = len(x) / RATE
    x = normalize(bandpass(x, 1200, 900)) + highpass(noise(s), 1500) * 0.012 * (0.6 + 0.4 * smooth_random(s, 8))
    return x


def echo_hum():
    """Гул монолита: аккорд с «неправильной» квинтой, стеклянные обертоны, медленные биения."""
    s = 12.0
    t = t_axis(s)
    x = np.zeros_like(t)
    for f, a in ((55, 1.0), (55.35, 0.8), (82.1, 0.6), (110, 0.4), (164.4, 0.25), (233.1, 0.18)):
        x += sine(f, s, rng.uniform(0, 6)) * a
    glass = np.zeros_like(t)
    for f in (1318.5, 1760.0, 2217.5, 2793.8):
        glass += sine(f, s) * smooth_random(s, 0.4, 0.0, 1.0) ** 3
    x = x * (0.75 + 0.25 * np.sin(2 * np.pi * 0.23 * t)) + glass * 0.07
    return loopable(normalize(reverb(x, 3.0, 0.35, 2500)[:len(t)]), 2.0)


def stinger():
    """Хоррор-удар: скрежет кластером, удар в низах, долгий хвост."""
    s = 4.0
    t = t_axis(s)
    x = np.zeros_like(t)
    for f in (587, 622, 659, 698, 932, 988):
        vib = 1 + 0.012 * np.sin(2 * np.pi * rng.uniform(5, 7) * t)
        saw = 2 * ((np.cumsum(f * vib * (1 - 0.06 * t / s)) / RATE) % 1.0) - 1
        x += saw * rng.uniform(0.4, 0.9)
    x = bandpass(x, 1800, 1400) * envelope(len(t), 0.02, 3.4, 2.5)
    n = len(t)
    boom = sine(32 + 40 * np.exp(-t / 0.08), s) * decay(n, 0.6) * 2.5
    hit = lowpass(noise(s), 500) * decay(n, 0.05) * 1.5
    return reverb(x + boom + hit, 3.0, 0.4, 3000)


def static_burst():
    s = 1.4
    x = highpass(noise(s), 600) * (smooth_random(s, 30) > 0.3)
    x = bandpass(x, 2200, 1400) * envelope(len(x), 0.01, 0.4)
    return x


def contact():
    """Катсцена у объекта: всё нарастает (бесконечно поднимающийся тон Шепарда), затем тишина."""
    s = 11.0
    t = t_axis(s)
    x = np.zeros_like(t)
    for octave in range(6):
        pos = (octave + t / s * 1.6) % 6
        freq = 40 * 2 ** pos
        amp = np.exp(-((pos - 3) ** 2) / 2.5)
        x += np.sin(2 * np.pi * np.cumsum(freq) / RATE) * amp
    x *= (t / s) ** 1.5
    place(x, echo_signal()[:int(5 * RATE)] * 0.5, 3.0)
    x[int(10.3 * RATE):] = 0  # обрыв — и тишина
    return reverb(x, 2.5, 0.3, 3500)[:int((s + 1.2) * RATE)]


def crystal_chime(seed):
    """Кристалл звенит, если подойти: стеклянный тон с неровными обертонами."""
    local = np.random.default_rng(seed)
    s = 2.6
    n = int(s * RATE)
    base = local.choice([1046.5, 1174.7, 1396.9, 1568.0])
    x = np.zeros(n)
    for ratio, d in ((1, 1.1), (2.76, 0.5), (5.4, 0.25), (8.93, 0.12)):
        x += sine(base * ratio * local.uniform(0.995, 1.005), s) * decay(n, d) / ratio ** 0.5
    return reverb(x, 2.0, 0.4, 6000)


# ---------------------------------------------------------------- полёт и техника

def quindar(freq):
    """Тон Квиндар: так звучал эфир NASA перед и после каждой фразы ЦУП."""
    n = int(0.25 * RATE)
    return sine(freq, 0.25) * envelope(n, 0.005, 0.01, 1)


def cabin_rumble():
    """Кабина на подъёме: рёв двигателей сквозь корпус. Шум без всякого ритма — ракета не стучит, а ревёт."""
    s = 6.0
    roar = lowpass(brown(s), 160, 3) + lowpass(pink(s), 700, 2) * 0.35
    roar *= 0.85 + 0.15 * smooth_random(s, 1.5)
    shake = lowpass(noise(s), 45, 2) * 0.6  # инфразвуковая дрожь корпуса
    x = normalize(roar) + normalize(shake) * 0.4 + normalize(resonate(noise(s), [140, 260, 410], 12)) * 0.12
    return loopable(normalize(lowpass(x, 900)), 1.0)


def separation():
    """Отделение ступени: пиропатроны, удар, лязг, шипение."""
    s = 2.8
    n = int(s * RATE)
    t = t_axis(s)
    bang = lowpass(noise(s), 900) * decay(n, 0.06) * 2 + sine(38 + 60 * np.exp(-t / 0.05), s) * decay(n, 0.4) * 2
    clank = resonate(noise(s) * decay(n, 0.01), [160, 420, 690, 1140, 1870], 40) * 1.5
    hiss = highpass(noise(s), 2500) * envelope(n, 0.05, 2.0) * 0.25
    return reverb(bang + clank + hiss, 1.4, 0.25, 2000)


def plasma():
    """Плазма за иллюминатором: ревущий шум с треском."""
    s = 5.0
    x = lowpass(pink(s), 1400) * (0.7 + 0.3 * smooth_random(s, 9))
    crackle = (rng.random(int(s * RATE)) > 0.9993) * rng.standard_normal(int(s * RATE))
    return loopable(normalize(x) + normalize(highpass(crackle, 2000)) * 0.35, 0.8)


def rover_motor():
    """Мотор лунохода: электрический вой и ворчание редуктора."""
    s = 3.0
    t = t_axis(s)
    whine = sine(420 + 15 * np.sin(2 * np.pi * 0.8 * t), s) * 0.3 + sine(840, s) * 0.12
    gears = resonate(noise(s), [95, 190, 285], 15) * (0.6 + 0.4 * np.sin(2 * np.pi * 18 * t))
    return loopable(normalize(whine + gears * 0.5), 0.5)


def rocket_roar():
    """Рёв ракеты снаружи: широкий шум с низами и характерным треском ударных волн, без ритма."""
    s = 6.0
    n = int(s * RATE)
    body = lowpass(brown(s), 220, 2) * 1.2 + lowpass(pink(s), 2500, 2) * 0.6
    body *= 0.85 + 0.15 * smooth_random(s, 2.0)
    # Треск: редкие несимметричные импульсы, как у настоящего ракетного выхлопа
    impulses = (rng.random(n) > 0.9965) * (rng.random(n) ** 3) * np.sign(rng.standard_normal(n))
    crackle = highpass(lowpass(impulses, 6000, 1), 500, 1)
    x = normalize(body) + normalize(crackle) * 0.45
    return loopable(normalize(x), 1.0)


def ignition():
    """Зажигание: нарастающий низкий гул и первый удар пламени о стартовый стол."""
    s = 3.5
    n = int(s * RATE)
    t = t_axis(s)
    src = brown(s)
    k = t / s
    # Гул светлеет по мере нарастания: смесь тёмного и более яркого фильтра
    build = (lowpass(src, 120, 2) * (1 - k) + lowpass(src, 420, 2) * k) * k ** 1.5
    hit = lowpass(noise(s), 400) * decay(n, 0.25)
    hit = np.roll(hit, int(2.2 * RATE))
    hit[:int(2.2 * RATE)] = 0
    return normalize(build) + normalize(hit) * 0.8


def ground_impact():
    """Удар метеорита, как его слышно в вакууме: только толчок через грунт и подошвы — глухо и низко."""
    s = 4.0
    n = int(s * RATE)
    t = t_axis(s)
    thump = sine(28 + 40 * np.exp(-t / 0.12), s) * decay(n, 0.7)
    rumble = lowpass(brown(s), 80, 3) * decay(n, 1.2)
    rattle = bandpass(noise(s), 900, 500) * decay(n, 0.5) * 0.15  # дрожь шлема и снаряжения
    return normalize(thump) + normalize(rumble) * 0.6 + normalize(rattle) * 0.25


def tinnitus():
    """Звон в ушах после близкого удара: тонкий писк, медленно гаснет."""
    s = 5.0
    n = int(s * RATE)
    t = t_axis(s)
    tone = sine(3700 + 30 * np.sin(2 * np.pi * 0.4 * t), s) + 0.4 * sine(3712, s)
    return tone * envelope(n, 0.05, 0.1, 1) * np.exp(-t / 1.8)


def arrival():
    """Прибытие на Луну: мягкий нисходящий шелест и два спокойных тона бортового компьютера."""
    s = 3.2
    n = int(s * RATE)
    t = t_axis(s)
    sweep = noise(s)
    cutoff_low = lowpass(sweep, 300, 2)
    cutoff_high = lowpass(sweep, 2500, 2)
    k = np.clip(t / 1.6, 0, 1)
    whoosh = (cutoff_high * (1 - k) + cutoff_low * k) * np.sin(np.pi * np.clip(t / 2.2, 0, 1)) ** 2
    chime = np.zeros(n)
    for at, f in ((1.6, 880.0), (1.95, 1318.5)):
        tone = sine(f, 1.2) * decay(int(1.2 * RATE), 0.35) * envelope(int(1.2 * RATE), 0.005, 0.05, 1)
        place(chime, tone, at)
    return reverb(normalize(whoosh) * 0.6 + normalize(chime) * 0.5, 1.5, 0.25, 4000)[:n]


def scanner_ping():
    """Сканер сигналов: короткий чистый «пинг» с отзвуком."""
    s = 0.6
    n = int(s * RATE)
    tone = sine(1567.98, s) * decay(n, 0.08) + 0.3 * sine(3135.96, s) * decay(n, 0.04)
    return echo(tone * envelope(n, 0.002, 0.05, 1), 0.11, 0.35, 3)


def hologram():
    """Голограмма включается: нарастающий электрический тон, мерцание и мягкий гул."""
    s = 2.0
    n = int(s * RATE)
    t = t_axis(s)
    rise = sine(220 + 660 * np.clip(t / 0.5, 0, 1), s) * np.clip(t / 0.5, 0, 1)
    flicker = 0.7 + 0.3 * smooth_random(s, 12) ** 2
    hum = (sine(110, s) + 0.5 * sine(220.5, s) + 0.25 * sine(330, s)) * 0.6
    x = (rise * 0.4 + hum) * flicker + highpass(noise(s), 4000) * 0.04
    return x * envelope(n, 0.02, 0.6)


def main():
    save("ambient/void_drone", void_drone(), 0.8)
    save("ambient/void_swell", void_swell())
    for i in range(3):
        save(f"ambient/whisper{i + 1}", whisper(30 + i, reverse=i == 1), 0.7)
    for i, (low, high) in enumerate(((60, 160), (90, 260), (45, 120))):
        save(f"ambient/creak{i + 1}", creak(40 + i, low, high))
    save("ambient/heartbeat", heartbeat())
    save("ambient/breath", breath(), 0.5)
    save("ambient/knock", knock())
    save("ambient/radio_ghost", radio_ghost(), 0.75)
    save("echo/signal", echo_signal())
    save("echo/hum", echo_hum())
    save("echo/stinger", stinger())
    save("echo/static", static_burst(), 0.6)
    save("echo/contact", contact())
    for i in range(3):
        save(f"echo/crystal{i + 1}", crystal_chime(50 + i), 0.7)
    save("flight/quindar_in", quindar(2525), 0.5)
    save("flight/quindar_out", quindar(2475), 0.5)
    save("flight/cabin_rumble", cabin_rumble())
    save("flight/separation", separation())
    save("flight/plasma", plasma())
    save("flight/hull_groan", creak(60, 35, 90, 3.2))
    save("vehicle/rover_motor", rover_motor(), 0.7)
    save("flight/rocket_roar", rocket_roar())
    save("flight/ignition", ignition())
    save("flight/arrival", arrival(), 0.7)
    save("meteor/ground_impact", ground_impact())
    save("meteor/tinnitus", tinnitus(), 0.35)
    save("echo/scanner_ping", scanner_ping(), 0.6)
    save("echo/hologram", hologram(), 0.6)


if __name__ == "__main__":
    main()
