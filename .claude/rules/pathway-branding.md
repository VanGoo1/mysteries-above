# Брендинг шляхів (колір зілль і Характеристик)

Єдине джерело правди — `me.vangoo.domain.PathwayBranding` (корінь domain, де
дозволений `org.bukkit.Color`): `pathwayName → Branding(Color liquid, ChatColor text)`.

- **Зілля**: `PathwayPotions` бере колір рідини (`PathwayBranding.liquidOf`) і колір
  назви (`PathwayBranding.textOf`) з брендингу за іменем шляху — конструктор
  `*Potions` кольорів НЕ приймає. Стосується всіх 22 шляхів.
- **Характеристики**: `CharacteristicCodec` фарбує назву `PathwayBranding.textOf(name)` і
  ставить ключ моделі `CharacteristicCodec.modelKeyFor(name)` (`characteristic_<name>`,
  окремий на кожен шлях) — не тонування дай-компонентом: на 1.21.1 `dyed_color` до
  не-шкіряних предметів ще не застосовується (Paper `io.papermc.paper.datacomponent`
  з'явився в 1.21.4), і рядкових `minecraft:select`-ключів там теж ще немає, тільки числові
  `custom_model_data`-override'и. Кожен pathway-ключ мусить мати свій override у
  `models/item/music_disc_chirp.json` (генерує `tools/resourcepack/rp-item-models.gen.ps1`) —
  інакше Характеристика того шляху падає на ванільний вигляд диска. Доки художньо не
  намальовано окрему текстуру на pathway, усі 22 override'и можуть показувати ту саму
  модель — інфраструктура вже per-pathway, розрізнити вигляд можна пізніше без зміни коду.
- Невідомий/`null` шлях → нейтральний сірий фолбек.

## Як додати/змінити колір

1. Додай/зміни рядок `put("<Name>", r, g, b, ChatColor.X)` у статичному блоці
   `PathwayBranding`. Ім'я = ключ `PathwayManager` (без пробілів).
2. Онови `PathwayBrandingTest` (кількість 22, наявність нового імені).
3. Ресурс-пак: перегенеруй `music_disc_chirp.json` (`tools/resourcepack/rp-item-models.gen.ps1`) —
   новий шлях автоматично отримає власний ключ `characteristic_<Name>` і override.

## Заборони

- ❌ Хардкодити `Color.fromRGB(...)` у `PotionManager`/`CharacteristicCodec` — лише через
  `PathwayBranding`.
- ❌ Передавати `Color`/`ChatColor` у конструктор `*Potions` — кольори резолвить
  `PathwayPotions` з `PathwayBranding` за `pathway.getName()`.
