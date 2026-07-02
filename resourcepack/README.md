# Ресурспак Mysteries Above — 3D-моделі інгредієнтів

## Datapack чи resource pack?

**Текстури й моделі — клієнтські.** Їх несе **resource pack**, не datapack.
Datapack — це серверна логіка (рецепти, loot tables, функції, виміри) і **фізично не може**
передати клієнту текстури/моделі. Тож «винести текстури в datapack» неможливо.

Але роздавати пак **із сервера автоматично** можна — через **server resource pack** (див. кінець файлу).
Гравцю при заході пропонується завантажити пак; це той самий ефект «вантажиться з сервера», лише
правильним каналом.

## Як це працює з плагіном

Кожен кастомний інгредієнт = base material `PAPER` + **рядковий** `custom_model_data` (= id
інгредієнта, напр. `elf_flower_petals`). Плагін виставляє цей компонент у
`CustomItemFactory` (`comp.setStrings([id])`). Ресурспак ловить цей рядок і підставляє модель.

Структура (вже заскафолджена тут):

```
resourcepack/
  pack.mcmeta
  assets/minecraft/
    items/paper.json                         # select за custom_model_data -> модель (+ fallback на папір)
    models/item/forage/<id>.json             # модель інгредієнта (приклад: elf_flower_petals.json — cross)
    textures/item/forage/<id>.png            # СЮДИ спрайти (16x16 / 32x32)
```

> ⚠️ `pack.mcmeta` має `pack_format`, що залежить від версії клієнта. Тут стоїть `64` +
> `supported_formats` діапазон 46–99 (толерантно до 1.21.x). Якщо гра пише «outdated/incompatible
> pack» — постав точний `pack_format` своєї версії MC.

## Додати новий інгредієнт (3 кроки)

1. **Спрайт:** поклади `textures/item/forage/<id>.png` (ім'я = id інгредієнта).
2. **Модель:** створи `models/item/forage/<id>.json`. Найпростіший «3D-плант» (дві перехрещені
   площини) — скопіюй `elf_flower_petals.json` і заміни id. Для справжньої об'ємної моделі —
   зроби її в Blockbench (нижче) і поклади експорт сюди.
3. **Прив'язка:** додай `case` у `items/paper.json`:
   ```json
   { "when": "<id>", "model": { "type": "minecraft:model", "model": "minecraft:item/forage/<id>" } }
   ```

Id інгредієнтів беруться з `src/main/resources/custom-items.yml` (поле `custom-model-data`).

## Як зробити «справжню 3D» з 2D-спрайта (Blockbench)

[Blockbench](https://www.blockbench.net/) — безкоштовний, веб/десктоп.

- **Варіант А (найпростіший, «плант»):** модель-cross — дві перехрещені площини зі спрайтом.
  Саме це робить приклад `elf_flower_petals.json` (`parent: minecraft/block/cross`). Виглядає як
  квітка/папороть, що стоїть, а не плаский значок.
- **Варіант Б (об'ємний піксель-арт):** у Blockbench: *New Model → Java Block/Item* →
  *Textures: import* свій PNG → інструмент **«Extrude»/«Texture to model»** (генерує товщину з
  пікселів) → *File → Export → Export Block/Item Model* у `models/item/forage/<id>.json`,
  текстуру — у `textures/item/forage/`.
- Орієнтацію/масштаб у руці/на землі правиш у `display` секції моделі (Blockbench робить це через
  *Display settings*).

## Де взяти спрайти (легально)

- **game-icons.net** — тисячі іконок (трави, флакони, кристали), ліцензія CC BY 3.0 (треба атрибуція).
- **OpenGameArt.org** — фільтр по CC0.
- **itch.io** — безкоштовні asset-паки (перевіряй ліцензію кожного).

⚠️ Поважай ліцензії: бери **CC0** або **CC BY** (з атрибуцією у цьому README). Не тягни ассети
без вказаної ліцензії.

## Роздача паку із сервера (auto-download)

1. Заархівуй **вміст** теки `resourcepack/` у zip (щоб `pack.mcmeta` був у корені архіву).
2. Захости zip за прямим URL (свій хостинг / GitHub release asset / Dropbox direct link).
3. Порахуй SHA-1 архіву: `sha1sum pack.zip` (або PowerShell `Get-FileHash pack.zip -Algorithm SHA1`).
4. У `server.properties`:
   ```
   resource-pack=https://.../pack.zip
   resource-pack-sha1=<хеш>
   require-resource-pack=true        # за бажанням — примусово
   ```
   Гравцям при заході запропонується завантажити пак.

(Альтернатива — плагіни роздачі паків, але вбудований server-resource-pack достатній.)
