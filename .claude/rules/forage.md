---
paths:
  - "src/main/java/me/vangoo/domain/forage/**"
  - "src/main/java/me/vangoo/infrastructure/forage/**"
  - "src/main/java/me/vangoo/infrastructure/schedulers/ForageNodeSpawner.java"
  - "src/main/java/me/vangoo/presentation/listeners/ForageHarvestListener.java"
  - "src/main/resources/forage.yml"
  - "mysteries-resourcepack/**"
---

# Форедж: «зачаровані» блоки

Канал допоміжних інгредієнтів: `ForageNodeSpawner` періодично підміняє вегетацію/листя біля
гравця на блок-донор (`forage.yml` → `donors:`), який ресурспак `mysteries-resourcepack/`
малює «зачарованим». Ламаєш донора → падає ЛИШЕ інгредієнт (`BlockBreakEvent`,
`setDropItems(false)`). TTL/stop/чанк-анлоад → блок тихо відновлюється.

## Межі шарів

- Чисте (юніт-тести, без Bukkit): `domain.forage.ForageSelector` (вибір інгредієнта за
  біомом), `domain.forage.ForageDonorMap` (оригінал → донор). Пакет пінить `ArchitectureTest`.
- Мутація світу: `infrastructure.forage.ForageNode` (place/restore, двоблокова флора),
  `ForageNodeCodec` (PDC чанка), `ForageNodeSpawner` (реєстр + тіки), край-кейси —
  `presentation.listeners.ForageHarvestListener`.

## Інваріанти (не ламати)

- Живі ноди існують ЛИШЕ в завантажених чанках: `ChunkUnloadEvent` відновлює достроково;
  `ChunkLoadEvent`/старт → `healChunk` лікує «осиротілі» записи PDC після крешу.
- Кожен `place()` має дзеркальний шлях відновлення: збір (блок ламається), TTL-прюн,
  `stop()`, нештатні події (поршень/вибух/вогонь/рідина/декей) — БЕЗ дропу інгредієнта.
- Реєстр нод — інстанс-поле спавнера; ключ — позиція блока. Ніякого static.
- Донор мусить бути блоком, якого нема в Оверворлді природно (незерські/ендові рослини),
  інакше звичайні блоки світу виглядатимуть зачарованими.

## Додати новий донор/текстуру

1. `forage.yml` → `donors.overrides.<ОРИГІНАЛ>: <ДОНОР>` (або зміни дефолт).
2. PNG у `mysteries-resourcepack/assets/minecraft/textures/block/<донор>.png`
   (перемальовується ввесь блок-донор; JSON-моделі не потрібні).
3. Перезбери й перероздай ресурспак (SHA-1 у `server.properties`).

## Відомі спрощення

- Знесення ОПОРИ під донором (блок під рослиною) фізикою дропає предмет донора (не
  інгредієнт); нода тихо знімається наступним прюном (`isIntact()`).
- Донори виглядають «зачаровано» й у рідному вимірі (Незер/люш-печери) — свідома жертва.
