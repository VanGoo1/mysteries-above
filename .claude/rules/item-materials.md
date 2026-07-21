---
paths:
  - "src/main/java/me/vangoo/infrastructure/items/**"
  - "src/main/java/me/vangoo/infrastructure/abilities/**"
  - "src/main/resources/custom-items.yml"
  - "mysteries-resourcepack/**"
---

# Матеріали предметів плагіну

## Правило

**Кожен предмет плагіну стоїть на музичній пластинці (`MUSIC_DISC_*`), а не на `PAPER`.**
Причина не косметична: шлях Блазня споживає **ванільний папір як справжній ресурс**
(`DollBatch.PAPER_PER_DOLL`, `PaperSubstitution.craftBatch`, `PaperThrower`, крафт
`RecipeBookCraftingRecipe`). Поки інгредієнти були папером, кожна з цих механік мусила
відрізняти «свій» папір від чужого евристиками — і одна така евристика вже була дірявою
(див. нижче про `ShadowTheft`).

Категорія = власний матеріал, тож предмети лишаються розрізнюваними навіть у гравця
без ресурс-паку.

| Категорія | Матеріал | Хто будує |
|---|---|---|
| Інгредієнти | `MUSIC_DISC_FAR` | `CustomItemFactory` (з `custom-items.yml`) |
| Предмети здібностей | `MUSIC_DISC_WARD` | `AbilityItemFactory.ABILITY_ITEM_MATERIAL` |
| Предмети орденів (талісман, шифроване послання) | `MUSIC_DISC_11` | `CustomItemFactory` |
| Характеристика | `MUSIC_DISC_CHIRP` | `CharacteristicCodec` |
| Монети (фунт / коппет) | `MUSIC_DISC_MELLOHI` / `MUSIC_DISC_STAL` | `CurrencyCodec` |
| Книга рецептів | `ENCHANTED_BOOK` | `CustomItemFactory` |

Книга рецептів — свідомий виняток: вона мусить лишитись нестакованою й читається як книга.

## `DiscItems` — обов'язкова обробка

Пластинка приносить дві вади, і **обидві треба лікувати на кожному такому предметі**.
Єдине джерело правди — `infrastructure.items.DiscItems`:

- `applyStackSize(meta)` — ванільний диск стакається до **1**; піднімаємо до 64, як було на
  папері. Пропустиш — інвентар гравця забиває поштучними інгредієнтами, а лут/схованки/сховища
  видають по одному.
- `stripJukeboxPlayable(item)` — інакше предмет **вставляється в програвач і зникає в ньому**.
  Реалізація — рефлексія по Paper DataComponent API (компілюємось проти spigot-api). На чистому
  Spigot мовчки no-op: **ризик прийнято свідомо**, сервер — Paper.
- `finish(item)` — обидва разом, для фабрик без `ItemMeta` під рукою; сам гейтиться на `isDisc`.

Новий канал створення предмета зобов'язаний пройти через `DiscItems`. Не копіюй ці два виклики
руками — саме так вони й розійшлися між `CharacteristicCodec` і `CurrencyCodec` до появи класу.

## Розпізнавання предмета — по NBT, ніколи по матеріалу чи lore

`ShadowTheft.isProtected` колись захищав предмети здібностей евристикою
«`Material.PAPER` + слово *кулдаун*/*вартість* у lore». Вона ламалась від зміни матеріалу,
від перекладу опису й спрацьовувала на будь-який чужий папір зі схожим lore. Тепер —
`AbilityItemFactory.isAbilityItem(item)` (статична NBT-мітка `ability_item`).

Мітки, за якими розпізнають предмети: `ability_item` (`AbilityItemFactory`),
`custom_item_id` (`CustomItemFactory`), `characteristic_pathway` (`CharacteristicCodec`),
`currency_coin` (`CurrencyCodec`).

## GUI-іконки — не предмети

`Material.PAPER` у `MarketMenu`, `ChurchMenu`, `OrderMenu`, `AmountPicker`, `Alteration` — це
кнопки меню, а не предмети плагіну. Їх це правило не стосується.

## Ресурс-пак

Вигляд задає `mysteries-resourcepack/assets/minecraft/items/<material>.json` — `select` по
`custom_model_data` з кейсом на кожен ключ. `ResourcePackItemModelTest` пінить обидва боки:
кожен ключ із конфігу покритий кейсом, і кожна модель із кейсу існує в `models/item/`.
Матеріал без файлу `items/*.json` законно падає на ванільний вигляд і тестом пропускається.

## Як додати категорію предметів

1. Обери вільну пластинку (зайняті — в таблиці вище) і додай рядок у ту таблицю.
2. Створи предмет через фабрику, що кличе `DiscItems`.
3. Розпізнавай його по власному NBT-ключу, не по матеріалу.
4. Потрібна текстура → `items/<material>.json` + моделі; інакше свідомо лишай ванільний вигляд.

## Заборони

- ❌ Ставити предмет плагіну на `Material.PAPER` — сплутається з ресурсом Блазня.
- ❌ Створювати предмет-пластинку повз `DiscItems` (стак і jukebox).
- ❌ Розпізнавати предмет плагіну по матеріалу, display name чи тексту lore — тільки NBT.
- ❌ Міняти матеріал книги рецептів на пластинку (`ENCHANTED_BOOK` — навмисно).
