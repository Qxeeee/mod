"""Проверка ресурсов OrbitCore без запуска игры.

Ищет то, что сервер в игровом тесте не заметит: блоки без моделей или лута,
модели со ссылками на несуществующие текстуры, пропущенные переводы.

    python tools/check_assets.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src" / "main" / "java" / "com" / "orbitcore" / "registry"
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / "orbitcore"
DATA = RES / "data" / "orbitcore"
NS = "orbitcore"

errors = []


def registered(java_file):
    return re.findall(r'register\("([a-z0-9_]+)"', (JAVA / java_file).read_text(encoding="utf-8"))


def load(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        errors.append(f"{path.relative_to(ROOT)}: {e}")
        return None


def resource(kind, ref, ext):
    """orbitcore:block/x -> путь к файлу; для чужих пространств имён — None (не проверяем)."""
    ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", None, ref)
    if ns != NS:
        return None
    return ASSETS / kind / f"{path}.{ext}"


def check_model(path, seen=None):
    seen = seen or set()
    if path in seen:
        return
    seen.add(path)
    model = load(path)
    if model is None:
        return
    parent = model.get("parent")
    if parent:
        parent_path = resource("models", parent, "json")
        if parent_path is not None:
            if not parent_path.exists():
                errors.append(f"{path.relative_to(ROOT)}: нет родительской модели {parent}")
            else:
                check_model(parent_path, seen)
    for key, tex in model.get("textures", {}).items():
        if tex.startswith("#"):
            continue
        tex_path = resource("textures", tex, "png")
        if tex_path is not None and not tex_path.exists():
            errors.append(f"{path.relative_to(ROOT)}: нет текстуры {tex} ({key})")


blocks = registered("ModBlocks.java")
items = registered("ModItems.java")
# Модули ракеты, собранная ракета и вёдра регистрируются не через register("...")
items += re.findall(r'^\s+[A-Z_]+\("([a-z0-9_]+)", Kind\.',
                    (ROOT / "src/main/java/com/orbitcore/rocket/RocketPart.java").read_text(encoding="utf-8"), re.M)
for java in ("ModItems.java", "ModFluids.java"):
    for name in re.findall(r'Registries\.ITEM, OrbitCore\.id\("([a-z0-9_]+)"\)', (JAVA / java).read_text(encoding="utf-8")):
        if name not in items:
            items.append(name)
langs = {name: load(ASSETS / "lang" / f"{name}.json") or {} for name in ("ru_ru", "en_us")}

for block in blocks:
    state = ASSETS / "blockstates" / f"{block}.json"
    if not state.exists():
        errors.append(f"блок {block}: нет blockstates/{block}.json")
    else:
        data = load(state) or {}
        for variant in data.get("variants", {}).values():
            for v in variant if isinstance(variant, list) else [variant]:
                model_path = resource("models", v["model"], "json")
                if model_path is not None and not model_path.exists():
                    errors.append(f"блок {block}: нет модели {v['model']}")
    if not (DATA / "loot_tables" / "blocks" / f"{block}.json").exists():
        errors.append(f"блок {block}: нет таблицы лута")
    for lang, entries in langs.items():
        if f"block.{NS}.{block}" not in entries:
            errors.append(f"блок {block}: нет перевода в {lang}")

for item in blocks + items:
    model = ASSETS / "models" / "item" / f"{item}.json"
    if not model.exists():
        errors.append(f"предмет {item}: нет модели предмета")
for item in items:
    for lang, entries in langs.items():
        if f"item.{NS}.{item}" not in entries:
            errors.append(f"предмет {item}: нет перевода в {lang}")

for model in sorted((ASSETS / "models").rglob("*.json")):
    check_model(model)

ru, en = set(langs["ru_ru"]), set(langs["en_us"])
for key in sorted(ru ^ en):
    errors.append(f"перевод {key}: есть только в {'ru_ru' if key in ru else 'en_us'}")

known = {f"{NS}:{name}" for name in blocks + items}
for recipe in sorted((DATA / "recipes").rglob("*.json")):
    text = recipe.read_text(encoding="utf-8")
    load(recipe)
    for ref in re.findall(r'"item":\s*"(orbitcore:[a-z0-9_]+)"', text):
        if ref not in known:
            errors.append(f"{recipe.relative_to(ROOT)}: неизвестный предмет {ref}")

for path in sorted(RES.rglob("*.json")):
    load(path)

if errors:
    print("\n".join(sorted(set(errors))))
    print(f"\nОшибок: {len(set(errors))}")
    sys.exit(1)
print(f"Ресурсы в порядке: блоков {len(blocks)}, предметов {len(items)}.")
