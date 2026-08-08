# Цільова версія гри: Minecraft 1.21.1

Сервер — **Paper 1.21.1**. Компілюємось проти `io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT`,
`plugin.yml` тримає `api-version: "1.21"`, ресурс-пак — `pack_format: 34`.

Це не «просто число в pom». 1.21.1 старша за багато API, які виглядають звичними, тож
нижче — повний список місць, де версія має значення, і чим їх замінено.

## Версії залежностей прив'язані до 1.21.1

| Артефакт | Версія | Чому саме вона |
|---|---|---|
| `io.papermc.paper:paper-api` | `1.21.1-R0.1-SNAPSHOT` | сама цільова версія |
| `io.lumine:Mythic-Dist` | `5.12.1` | дистрибутив ще везе volatile-модуль `v1_21_R1` (= 1.21/1.21.1) |
| `io.github.toxicity188:bettermodel` | `1.15.2` | остання гілка з NMS-модулем `v1_21_R1`; `bettermodel-bukkit-api` (2.x/3.x) починається з `v1_21_R3` і на 1.21.1 НЕ працює |
| `net.citizensnpcs:citizens-main` | `2.0.35-SNAPSHOT` | гілка доби 1.21.1 (останній білд 23.10.2024) |
| `net.coreprotect:coreprotect` | `22.4` | останній реліз під 1.21/1.21.1; 23.x (CE) — від 1.21.2 |
| `com.github.retrooper:packetevents-spigot` | `2.8.0` | `2.13.0` лістить `V_1_21_1` у `ServerVersion` (компілюється й формально підтримує ревізію), але на реальному сервері проєкту ламався — знижено до `2.8.0`. Не піднімай назад без перевірки на живому сервері. |

`glowingentities` 1.4.11, `EffectLib` 10.2 і `triumph-gui` 3.1.7 підтримують 1.21.1 нарівні з
новішими (перевірено: у мапінгах glowingentities є `1.21.1`), тож їх не рухаємо.

**Перед підняттям версії будь-якої з таблиці** перевір, чи є в артефакті модуль під ревізію
сервера (`unzip -l <jar> | grep v1_21_R1` або тека `nms/` у репозиторії проєкту). Плагін-
залежність без модуля під твою ревізію просто не завантажиться на сервері — компіляція про це
не скаже нічого.

## Чого на 1.21.1 ще немає (і чим замінено)

| Новіше API | Замість нього тут |
|---|---|
| `Attribute.MAX_HEALTH`, `ATTACK_DAMAGE`, … (перейменовано в 1.21.3) | старі імена з префіксом: `Attribute.GENERIC_MAX_HEALTH`, `GENERIC_ATTACK_DAMAGE`, `GENERIC_ARMOR`, `GENERIC_KNOCKBACK_RESISTANCE` |
| `CustomModelDataComponent` (рядковий `custom_model_data`, 1.21.4) | `ItemModelData.of(key)` → числовий `meta.setCustomModelData(int)`; пак — `overrides`, див. `docs/item-materials.md` |
| `io.papermc.paper.datacomponent.*` (`DataComponentTypes`, `DyedItemColor`, 1.21.4) | нічого: Характеристика не тонується під шлях, `stripJukeboxPlayable` no-op'иться |
| зняття `jukebox_playable` компонентом | подієвий `JukeboxGuardListener` (клік + хопер) |
| `Tag.ENTITY_TYPES_UNDEAD` | явна перевірка типів у `LightningStrike.isUndead` |
| `Material.IRON_CHAIN` (1.21.9) | `Material.CHAIN` |
| `CopperGolem` (1.21.9) | гілки з ним просто немає |

Список повний на момент міграції — якщо додаєш API, якого немає в 1.21.1, компіляція впаде
одразу (paper-api `provided` тримає межу). Рефлексію, що мовчки no-op'иться (як
`stripJukeboxPlayable`), лишай — але дірку, яку вона мала закривати, закривай явно.

## BetterModel: модуль під ревізію визначає поведінку анімацій

Вбудовані `idle_fly`/`walk_fly`/`walk` рахуються в NMS-модулі під конкретну ревізію гри, і
логіка між модулями РІЗНА. На 1.21.1 це `v1_21_R1`, де «летить» — це
`FlyingAnimal.isFlying | FlyingMob | Mob.isNoAi | Player.abilities.flying | isFallFlying`
(гілка `FlyingMob` пізніше зникла разом із класом). Деталі й наслідки — у
`.claude/rules/bettermodel-models.md`; читай там саме секцію під цю ревізію.

## Заборони

- ❌ Піднімати `paper-api` заради одного зручного методу — це міняє цільову версію сервера.
- ❌ Тягнути залежність, у якої немає модуля під `v1_21_R1`, «бо вона свіжіша».
- ❌ Обходити відсутнє API рефлексією без запасного шляху: no-op, який мовчки нічого не робить,
  на 1.21.1 читається як зламана механіка (саме так вийшло з `jukebox_playable`).
