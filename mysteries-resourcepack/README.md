# Mysteries Above — серверний ресурспак

Джерело правди для клієнтських ассетів сервера: текстури/моделі кастомних інгредієнтів
і «зачарованих» блоків фореджу. Датапак (структури) — окремо, у `mysteries-datapack/`.

## Кастомні предмети (інгредієнти)

Кожен інгредієнт = base material `PAPER` + рядковий `custom_model_data` (= id інгредієнта
з `src/main/resources/custom-items.yml`). Плагін виставляє компонент у `CustomItemFactory`;
пак ловить рядок у `assets/minecraft/items/paper.json` і підставляє модель.

Додати новий інгредієнт:
1. Спрайт → `assets/minecraft/textures/item/<id>.png` (16×16 або 32×32).
2. Модель → `assets/minecraft/models/item/<id>.json` (скопіюй сусідню, заміни id).
3. Прив'язка → новий `case` в `assets/minecraft/items/paper.json`:
   `{ "when": "<id>", "model": { "type": "minecraft:model", "model": "minecraft:item/<id>" } }`

## «Зачаровані» блоки фореджу (донори)

Плагін фізично підміняє вегетацію на блок-донор; пак перемальовує донора ЦІЛКОМ —
лише PNG, без JSON-моделей (ванільні моделі підхоплюють текстури за іменем):

| Файл у `textures/block/` | Що малює | Де з'являється |
|---|---|---|
| `warped_roots.png`   | зачарована трава/папороть | трава, папороть |
| `crimson_roots.png`  | зачарована квітка         | квіти |
| `nether_sprouts.png` | зачарований кущик         | ягідний кущ та інше |
| `azalea_leaves.png`  | зачароване листя          | крони дерев |

Поточні PNG — ЗАГЛУШКИ (шаховий візерунок): заміни своїми текстурами тих самих імен.
Свідома жертва: донори так виглядатимуть і в рідному вимірі (Незер / люш-печери).
Мапінг оригінал→донор конфігурується в `src/main/resources/forage.yml` (`donors:`).

## Роздача гравцям

1. Заархівуй ВМІСТ цієї теки в zip (щоб `pack.mcmeta` був у корені архіву).
2. Захости zip за прямим URL.
3. SHA-1: `Get-FileHash pack.zip -Algorithm SHA1`.
4. `server.properties`:
   ```
   resource-pack=https://.../pack.zip
   resource-pack-sha1=<хеш>
   require-resource-pack=true
   ```

`pack.mcmeta` тримає `min_format`/`max_format` під версію клієнта — онови при апдейті MC.
