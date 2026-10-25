# Звуки OrbitCore

Свои звуки мода собирает синтезатор [`tools/sounds/synthesize.py`](../tools/sounds/synthesize.py): из синусоид, шума, фильтров и реверберации. Всё можно пересобрать одной командой:

```
python tools/sounds/synthesize.py
```

Нужны numpy и ffmpeg с libvorbis. Если ffmpeg не в PATH, путь задаётся переменной `OC_FFMPEG`.

## Как заменить звук своим

Любой файл можно заменить, например, сгенерированным в [ElevenLabs Sound Effects](https://elevenlabs.io/sound-effects):

1. Сгенерируйте звук по подсказке из таблицы ниже (подсказки на английском, так генератор понимает лучше).
2. Сохраните его как **моно** `.ogg` (Vorbis) по указанному пути, поверх старого файла. Стерео-звуки Minecraft не умеет располагать в пространстве.
   ```
   ffmpeg -i sound.mp3 -ac 1 -c:a libvorbis -q:a 4 out.ogg
   ```
3. Повторно запускать синтезатор не надо: он перезапишет файл. Если звук заменён насовсем, уберите его строку из `main()` в `synthesize.py`.

> Перед публикацией мода проверьте лицензию своего тарифа ElevenLabs: на бесплатном тарифе могут действовать ограничения на коммерческое использование и требование указывать авторство.

## Страх пустоты (Луна)

Звучат «в голове», внутри шлема, поэтому не глохнут в вакууме. Громкость регулирует ползунок «Окружение».

| Файл (`assets/orbitcore/sounds/…`) | Когда звучит | Подсказка для ElevenLabs |
|---|---|---|
| `ambient/void_drone.ogg` | Постоянно вне базы: тем громче, чем дальше от базы, темнее и ночнее | `deep ominous space drone, slow beating low frequencies, distant metallic resonance, dark ambient, seamless loop, 20 seconds` |
| `ambient/void_swell.ogg` | Если долго смотреть в чёрное небо | `slow rising dissonant orchestral cluster swell, horror, abyss, cuts off abruptly at the end` |
| `ambient/whisper1–3.ogg` | Иногда прямо за спиной | `unintelligible breathy whispers behind you, eerie, reverb, horror` |
| `ambient/creak1–3.ogg` | Скрип металла: снаружи и в стенах базы | `metal structure groaning and creaking under stress, low, slow, eerie` |
| `ambient/heartbeat.ogg` | Мало кислорода, ранен или совсем жутко | `muffled heartbeat lub-dub heard from inside a helmet, single beat` |
| `ambient/breath.ogg` | При беге и когда кончается воздух | `astronaut breathing inside a helmet, one inhale and exhale, slight reverb` |
| `ambient/knock.ogg` | По ночам в герметичной базе: кто-то стучит снаружи | `three slow heavy knocks on a metal hull from outside, muffled, reverb` |
| `ambient/radio_ghost.ogg` | Голос в помехах шлемофона (с репликой на экране) | `radio static with a faint distorted human voice fading in and out, creepy transmission` |

## «Эхо»

| Файл | Когда звучит | Подсказка |
|---|---|---|
| `echo/signal.ogg` | Сигнал объекта «Эхо» | `mysterious alien radio signal, short pure tone pattern with echoes repeating and fading, old tape wobble, faint static` |
| `echo/hum.ogg` | Гул рядом с объектом | `deep resonant hum of an ancient alien monolith, glassy overtones, slow beating, seamless loop` |
| `echo/stinger.ogg` | Страшный момент | `horror stinger, screeching dissonant strings with a deep sub boom, long reverb tail` |
| `echo/static.ogg` | Помехи на визоре рядом с Эхом | `short burst of harsh radio static interference` |
| `echo/contact.ogg` | Катсцена у объекта | `endlessly rising shepard tone building tension with alien signal fragments, ends in sudden silence` |
| `echo/crystal1–3.ogg` | Светящиеся кристаллы | `single glassy crystal chime, bell-like, ethereal, long decay` |

## Метеориты

В вакууме удар не слышен: только толчок через подошвы, а если близко — звон в ушах, и остальные звуки на несколько секунд глохнут.

| Файл | Когда звучит | Подсказка |
|---|---|---|
| `meteor/ground_impact.ogg` | Удар метеорита, как его чувствуют через грунт | `deep muffled ground thump felt through boots, low rumble, no air blast` |
| `meteor/tinnitus.ogg` | Звон в ушах после близкого удара | `high pitched ear ringing tinnitus after an explosion, slowly fading` |

## Сканер и планшеты

| Файл | Когда звучит | Подсказка |
|---|---|---|
| `echo/scanner_ping.ogg` | Сканер слышит планшет: чем ближе, тем чаще и выше | `short clean sonar ping with a small echo` |
| `echo/hologram.ogg` | Голограмма включается | `hologram flickering on, rising electric tone and soft hum` |

## Полёт и техника

| Файл | Когда звучит | Подсказка |
|---|---|---|
| `flight/quindar_in.ogg`, `quindar_out.ogg` | Каждая новая фраза ЦУП | Это тоны Квиндар NASA: чистый синус 2525 Гц и 2475 Гц по 250 мс. Генерировать не нужно |
| `flight/cabin_rumble.ogg` | Рёв двигателей внутри кабины | `rocket engine rumble heard from inside a spacecraft cabin, heavy low vibration, seamless loop` |
| `flight/rocket_roar.ogg` | Рёв двигателей снаружи: звук летит вместе с ракетой | `real rocket engine roar, broadband crackling exhaust, deep rumble, no rhythm, seamless loop` |
| `flight/ignition.ogg` | Зажигание на столе | `rocket engine ignition, rising low rumble then a heavy burst of flame` |
| `flight/arrival.ogg` | Прибытие на Луну и возвращение на Землю (вместо гула портала) | `soft descending whoosh followed by two calm spacecraft computer chimes` |
| `flight/separation.ogg` | Отделение ступени | `spacecraft stage separation, pyrotechnic bang, metallic clank, gas hiss` |
| `flight/plasma.ogg` | Вход в атмосферу | `spacecraft reentry plasma roar with crackling, seamless loop` |
| `flight/hull_groan.ogg` | Корпус скрипит под нагрузкой | `spacecraft hull creaking under stress, deep metallic groan` |
| `vehicle/rover_motor.ogg` | Мотор лунохода | `small electric rover motor whine with gear rumble, seamless loop` |

## Озвучка

Реплики собирает [`tools/sounds/voice.py`](../tools/sounds/voice.py) из голосов Windows (Microsoft Irina и Pavel — русский, Microsoft Zira — английский) и обрабатывает их под нужную сцену. Список реплик — в [`tools/sounds/voice_lines.json`](../tools/sounds/voice_lines.json), тексты записей с планшетов берутся из файлов перевода.

```
python tools/sounds/voice.py
```

| Реплики | Голос | Обработка |
|---|---|---|
| `launch_*`, `moon_landing`, `signal_received` | ЦУП (Павел) | Эфир: узкая полоса, лёгкий перегруз, тоны Квиндар |
| `meteor_warning` | Компьютер скафандра (Ирина) | Ровный «цифровой» голос с двумя сигналами |
| `radio_1`–`radio_5` | Голоса «Зари» в помехах | Ниже обычного, рвётся, тонет в эхе |
| `scare` | Шёпот | От голоса остаётся одно дыхание |
| `zarya_1`–`zarya_4` | Волкова (Ирина), Седов (Павел) | Запись с плёнки: плывёт, шипит |
| `builder_1`–`builder_8` | Строители | Голос ниже человеческого, октава вниз, обратное эхо |

Файлы лежат в `sounds/voice/<реплика>_<язык>.ogg` и играют на языке игры. Любую реплику можно заменить записью живого актёра или голосом из ElevenLabs: моно `.ogg` с тем же именем.
