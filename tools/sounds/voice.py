"""Озвучка OrbitCore: голоса Windows, обработанные под эфир, скафандр, запись и Строителей.

    python tools/sounds/voice.py

Нужна Windows с голосами Microsoft Irina и Pavel (русский) и Microsoft Zira (английский):
они ставятся вместе с языковыми пакетами. Реплики — в voice_lines.json, тексты записей
с планшетов берутся из файлов перевода. Готовые файлы: sounds/voice/<реплика>_<язык>.ogg.
Любую реплику можно заменить настоящей записью актёра — тем же именем файла.
"""
import json
import re
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import synthesize as sx  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
LANG = ROOT / "src" / "main" / "resources" / "assets" / "orbitcore" / "lang"
RATE = sx.RATE

PS_SCRIPT = r'''
param([string]$Jobs)
Add-Type -AssemblyName System.Speech
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.SpeechSynthesis.SpeechSynthesizer, Windows.Media.SpeechSynthesis, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, [Type]$type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait() | Out-Null; $t.Result }
$winrt = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer
$sapi = New-Object System.Speech.Synthesis.SpeechSynthesizer
foreach ($job in (Get-Content -Raw -Encoding UTF8 $Jobs | ConvertFrom-Json)) {
    $voice = [Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices | Where-Object { $_.DisplayName -like ("*" + $job.voice + "*") } | Select-Object -First 1
    if ($voice) {
        $winrt.Voice = $voice
        $winrt.Options.SpeakingRate = $job.rate
        $stream = Await ($winrt.SynthesizeTextToStreamAsync($job.text)) ([Windows.Media.SpeechSynthesis.SpeechSynthesisStream])
        $reader = New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0))
        $size = [uint32]$stream.Size
        $null = Await ($reader.LoadAsync($size)) ([uint32])
        $bytes = New-Object byte[] $size
        $reader.ReadBytes($bytes)
        [System.IO.File]::WriteAllBytes($job.out, $bytes)
    } else {
        $sapi.SelectVoice("Microsoft " + $job.voice + " Desktop")
        $sapi.Rate = [int](($job.rate - 1.0) * 10)
        $sapi.SetOutputToWaveFile($job.out)
        $sapi.Speak($job.text)
        $sapi.SetOutputToNull()
    }
}
'''


def read_wav(path):
    with wave.open(str(path), "rb") as w:
        rate = w.getframerate()
        channels = w.getnchannels()
        data = np.frombuffer(w.readframes(w.getnframes()), "<i2").astype(float) / 32768
    if channels > 1:
        data = data.reshape(-1, channels).mean(axis=1)
    # Пересэмплировать к частоте мода
    n = int(len(data) * RATE / rate)
    return np.interp(np.linspace(0, len(data) - 1, n), np.arange(len(data)), data)


def trim(x, threshold=0.01):
    idx = np.where(np.abs(x) > threshold)[0]
    return x if len(idx) == 0 else x[max(0, idx[0] - 800):idx[-1] + 2000]


def repitch(x, ratio):
    """Ниже и медленнее (ratio < 1) — простым пересэмплированием, как на замедленной плёнке."""
    return np.interp(np.arange(0, len(x) - 1, ratio), np.arange(len(x)), x)


def fit(x, n):
    """Подогнать длину под голос: секунды округляются, и шум бывает на отсчёт короче."""
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def noise_for(x):
    return sx.rng.standard_normal(len(x))


def wander(x, rate_hz):
    return fit(sx.smooth_random(len(x) / RATE + 0.01, rate_hz), len(x))


def envelope_follower(x, cutoff=30):
    return sx.lowpass(np.abs(x), cutoff, 2)


# ---------------------------------------------------------------- обработка

def radio(x):
    """Эфир ЦУП: узкая полоса, лёгкий перегруз, фон и тоны Квиндар в начале и в конце."""
    voice = np.tanh(sx.bandpass(x, 1500, 1000) * 2.2) * 0.8
    voice += sx.highpass(noise_for(voice), 1500) * 0.012
    beep_in = sx.quindar(2525) * 0.25
    beep_out = sx.quindar(2475) * 0.25
    return np.concatenate([beep_in, sx.silence(0.15), voice, sx.silence(0.1), beep_out])


def computer(x):
    """Компьютер скафандра: два коротких сигнала и ровный, чуть «цифровой» голос внутри шлема."""
    voice = sx.lowpass(sx.highpass(x, 220), 6000)
    voice = np.round(voice * 64) / 64  # лёгкое огрубление, как у бортового синтезатора
    beep = np.concatenate([sx.sine(1760, 0.08) * 0.2, sx.silence(0.05), sx.sine(1320, 0.08) * 0.2])
    return sx.reverb(np.concatenate([beep, sx.silence(0.1), voice]), 0.3, 0.12, 4000)


def record(x):
    """Запись на планшете: голос с плёнки — плывёт, шипит, чуть глухо."""
    t = np.arange(len(x)) / RATE
    wow = 1 + 0.003 * np.sin(2 * np.pi * 0.8 * t)
    idx = np.clip(np.cumsum(wow) - 1, 0, len(x) - 1)
    voice = np.interp(idx, np.arange(len(x)), x)
    voice = sx.lowpass(sx.highpass(voice, 150), 5000)
    voice += sx.highpass(noise_for(voice), 3000) * 0.008
    return sx.reverb(voice, 0.6, 0.15, 3000)


def ghost(x):
    """Голос в помехах: эфирная полоса, ниже обычного, рвётся и тонет в шуме и эхе."""
    voice = repitch(x, 0.92)
    voice = np.tanh(sx.bandpass(voice, 1300, 900) * 2.5) * 0.7
    gate = 0.25 + 0.75 * (wander(voice, 9) > 0.3)
    static = sx.bandpass(noise_for(voice), 2000, 1200) * 0.12 * (0.5 + wander(voice, 14))
    return sx.echo(sx.reverb(voice * gate + static, 2.0, 0.4, 2500), 0.33, 0.35, 3)


def whisper(x):
    """Шёпот: от голоса остаётся только дыхание — шум, повторяющий его очертания, и тень самого голоса."""
    env = envelope_follower(x)
    breath = sum(sx.bandpass(noise_for(x), f, f * 0.4) * g for f, g in ((1200, 0.7), (2600, 1.0), (4200, 0.5)))
    voice = sx.normalize(breath * env) * 0.8 + sx.normalize(sx.highpass(x, 1800)) * 0.25
    return sx.reverb(voice, 2.5, 0.45, 5000)


def builders(x):
    """Строители: голос ниже человеческого, удвоенный октавой ниже, с нарастающим обратным эхом."""
    low = repitch(x, 0.78)
    sub = repitch(x, 0.55)[:len(low)]
    sub = np.pad(sub, (0, len(low) - len(sub)))
    voice = low + 0.45 * sub
    t = np.arange(len(voice)) / RATE
    voice *= 0.85 + 0.15 * np.sin(2 * np.pi * 38 * t)
    swell = sx.reverb(voice[::-1], 1.8, 0.8, 3000)[::-1][:int(1.8 * RATE)] * 0.35
    voice = np.concatenate([swell, voice])
    return sx.reverb(sx.lowpass(voice, 4500), 3.5, 0.45, 2500)


STYLES = {"radio": radio, "computer": computer, "record": record, "ghost": ghost, "whisper": whisper, "builders": builders}


# ---------------------------------------------------------------- тексты

def record_text(line, lang):
    """Текст записи с планшета из перевода — без знаков помех, с паузой вместо них."""
    data = json.loads((LANG / ("ru_ru.json" if lang == "ru" else "en_us.json")).read_text(encoding="utf-8"))
    text = data[f"holo.orbitcore.{line}.text"]
    text = re.sub("▒+", " ... ", text).replace("\n", " ")
    return re.sub(r"\s+", " ", text).strip()


def main():
    config = json.loads((HERE / "voice_lines.json").read_text(encoding="utf-8"))["lines"]
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        jobs = []
        for line, spec in config.items():
            for lang in ("ru", "en"):
                voice = spec[lang][0]
                text = spec[lang][1] if len(spec[lang]) > 1 else record_text(line, lang)
                rate = 0.9 if spec["style"] in ("record", "builders", "ghost") else 1.0
                jobs.append({"voice": voice, "text": text, "rate": rate, "out": str(tmp / f"{line}_{lang}.wav")})
        (tmp / "jobs.json").write_text(json.dumps(jobs, ensure_ascii=False), encoding="utf-8-sig")
        script = tmp / "tts.ps1"
        script.write_text(PS_SCRIPT, encoding="utf-8-sig")
        subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script), "-Jobs", str(tmp / "jobs.json")],
                       check=True)
        for line, spec in config.items():
            for lang in ("ru", "en"):
                x = trim(read_wav(tmp / f"{line}_{lang}.wav"))
                pitch = spec.get("pitch_" + lang)
                if pitch:
                    x = repitch(x, pitch)
                sx.save(f"voice/{line}_{lang}", STYLES[spec["style"]](x), 0.85)


if __name__ == "__main__":
    main()
