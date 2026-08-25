# Брендинг шляхів (колір зілль і Характеристик)

Єдине джерело правди — `me.vangoo.domain.PathwayBranding` (корінь domain, де
дозволений `org.bukkit.Color`): `pathwayName → Branding(Color liquid, ChatColor text)`.

- **Зілля**: `PathwayPotions` бере колір рідини (`PathwayBranding.liquidOf`) і колір
  назви (`PathwayBranding.textOf`) з брендингу за іменем шляху — конструктор
  `*Potions` кольорів НЕ приймає. Стосується всіх 22 шляхів.
- **Характеристики**: `CharacteristicCodec` фарбує назву `PathwayBranding.textOf(name)`,
  ставить УНІВЕРСАЛЬНИЙ ключ моделі `characteristic` (`CharacteristicCodec.MODEL_KEY`, один
  на всі шляхи) і компонент `DYED_COLOR` = `PathwayBranding.liquidOf(name)`. Ресурс-пак
  тонує одну модель диска через `minecraft:dye`-tint, читаючи цей компонент — колір задає
  дай, НЕ пер-шляховий ключ. Ключ моделі мусить збігатися з єдиним кейсом
  `"when": "characteristic"` в `items/music_disc_chirp.json`, інакше select падає на
  ванільну модель без tint і колір не застосовується.
- Невідомий/`null` шлях → нейтральний сірий фолбек.

## Як додати/змінити колір

1. Додай/зміни рядок `put("<Name>", r, g, b, ChatColor.X)` у статичному блоці
   `PathwayBranding`. Ім'я = ключ `PathwayManager` (без пробілів).
2. Онови `PathwayBrandingTest` (кількість 22, наявність нового імені).
3. Ресурс-пак: колір Характеристики бере дай (`DYED_COLOR`) — окрема текстура на шлях НЕ
   потрібна, вистачає однієї тонованої моделі під ключем `characteristic`.

## Заборони

- ❌ Хардкодити `Color.fromRGB(...)` у `PotionManager`/`CharacteristicCodec` — лише через
  `PathwayBranding`.
- ❌ Передавати `Color`/`ChatColor` у конструктор `*Potions` — кольори резолвить
  `PathwayPotions` з `PathwayBranding` за `pathway.getName()`.
