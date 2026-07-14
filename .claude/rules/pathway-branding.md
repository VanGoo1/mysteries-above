# Брендинг шляхів (колір зілль і Характеристик)

Єдине джерело правди — `me.vangoo.domain.PathwayBranding` (корінь domain, де
дозволений `org.bukkit.Color`): `pathwayName → Branding(Color liquid, ChatColor text)`.

- **Зілля**: `PotionManager` бере колір рідини з `PathwayBranding.liquidOf(name)` для всіх 22.
- **Характеристики**: `CharacteristicCodec` фарбує назву `PathwayBranding.textOf(name)` і
  ставить пер-шляховий ключ моделі `characteristic_<pathway>` (lowercase) під ресурс-пак.
- Невідомий/`null` шлях → нейтральний сірий фолбек.

## Як додати/змінити колір

1. Додай/зміни рядок `put("<Name>", r, g, b, ChatColor.X)` у статичному блоці
   `PathwayBranding`. Ім'я = ключ `PathwayManager` (без пробілів).
2. Онови `PathwayBrandingTest` (кількість 22, наявність нового імені).
3. Ресурс-пак: текстура під ключ `characteristic_<name>` (lowercase) — опційно.

## Заборони

- ❌ Хардкодити `Color.fromRGB(...)` у `PotionManager`/`CharacteristicCodec` — лише через
  `PathwayBranding`.
