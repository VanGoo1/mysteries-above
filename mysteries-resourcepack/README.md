# Mysteries Above — серверний ресурспак

Джерело правди для клієнтських ассетів сервера: текстури/моделі кастомних інгредієнтів
і «зачарованих» блоків фореджу. Датапак (структури) — окремо, у `mysteries-datapack/`.

## Кастомні предмети (інгредієнти)

Кожен предмет плагіну = **музична пластинка** + рядковий `custom_model_data` (= id предмета
з `src/main/resources/custom-items.yml`). Пластинка, а не `PAPER`, бо ванільний папір шлях
Блазня споживає як справжній ресурс — див. `.claude/rules/item-materials.md`, там же таблиця
«категорія → матеріал». Плагін виставляє компонент у `CustomItemFactory`; пак ловить рядок у
`assets/minecraft/items/<material>.json` і підставляє модель.

| Файл визначення | Категорія |
|---|---|
| `items/music_disc_far.json`  | інгредієнти (69 кейсів) |
| `items/music_disc_ward.json` | предмети здібностей (`active` / `passive` / `permanent_passive`) |

Додати новий інгредієнт:
1. Спрайт → `assets/minecraft/textures/item/<id>.png` (16×16 або 32×32).
2. Модель → `assets/minecraft/models/item/<id>.json` (скопіюй сусідню, заміни id).
3. Прив'язка → новий `case` в `assets/minecraft/items/music_disc_far.json`:
   `{ "when": "<id>", "model": { "type": "minecraft:model", "model": "minecraft:item/<id>" } }`

Пропустиш крок 2 або 3 — предмет тихо стане звичайною пластинкою. Це ловить
`ResourcePackItemModelTest` (`mvn test`), а не очі на сервері.

**Ще без текстур** (законно падають на ванільний вигляд, файлів `items/*.json` не мають):
Характеристики (`MUSIC_DISC_CHIRP`, ключі `characteristic_<шлях>`), монети
(`MUSIC_DISC_MELLOHI` / `MUSIC_DISC_STAL`), предмети орденів (`MUSIC_DISC_11`),
книга рецептів (`ENCHANTED_BOOK`).

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
