# Готовые версии мода

Здесь лежат `.jar` файлы OrbitCore, которые можно ставить в игру.

## Как установить
1. Установи [Fabric Loader](https://fabricmc.net/use/installer/) для Minecraft **1.20.1**.
2. Положи в папку `mods`:
   - `orbitcore-<версия>.jar` из этой папки;
   - [Fabric API](https://modrinth.com/mod/fabric-api) для 1.20.1;
   - [GeckoLib](https://modrinth.com/mod/geckolib) для Fabric 1.20.1 (4.8 или новее).

Остальные библиотеки уже встроены в `.jar`.

## Как сюда попадает .jar
Команда `gradlew release` собирает мод и кладёт `.jar` сюда. Новые версии добавляем только из ветки `main`.
