# Цільова версія гри: Minecraft 1.21.1

Компілюємось проти **`org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT`**, `plugin.yml` тримає
`api-version: "1.21"`, ресурс-пак — `pack_format: 34`. **Живий сервер проєкту — Arclight
1.21.1, а не Paper**, і саме тому цільове API — Spigot: усе Paper-only тепер помилка
компіляції, а не `NoSuchMethodError` у гравця (див. окремий розділ нижче).

Це не «просто число в pom». 1.21.1 старша за багато API, які виглядають звичними, тож
нижче — повний список місць, де версія має значення, і чим їх замінено.

## Версії залежностей прив'язані до 1.21.1

| Артефакт | Версія | Чому саме вона |
|---|---|---|
| `org.spigotmc:spigot-api` | `1.21.1-R0.1-SNAPSHOT` | цільова версія + цільове API (репозиторій `hub.spigotmc.org`; НЕ `paper-api`, див. розділ про Arclight) |
| `io.lumine:Mythic-Dist` | `5.12.1` | дистрибутив ще везе volatile-модуль `v1_21_R1` (= 1.21/1.21.1) |
| `io.github.toxicity188:bettermodel` | `1.15.2` | остання гілка з NMS-модулем `v1_21_R1`; `bettermodel-bukkit-api` (2.x/3.x) починається з `v1_21_R3` і на 1.21.1 НЕ працює |
| `net.citizensnpcs:citizens-main` | `2.0.35-SNAPSHOT` | гілка доби 1.21.1 (останній білд 23.10.2024) |
| `net.coreprotect:coreprotect` | `22.4` | останній реліз під 1.21/1.21.1; 23.x (CE) — від 1.21.2 |
| `com.github.retrooper:packetevents-spigot` | `2.8.0` | `2.13.0` лістить `V_1_21_1` у `ServerVersion` (компілюється й формально підтримує ревізію), але на реальному сервері проєкту ламався — знижено до `2.8.0`. Не піднімай назад без перевірки на живому сервері. |

`io.netty:netty-transport` 4.1.97.Final — `provided` і **не** плутати з рештою: netty дає сам
сервер, у джар він не пакується. Потрібен лише щоб компілювався
`infrastructure.compat.PacketEventsUdpGuard` (див. нижче); версія збігається з тією, що везе
Arclight.

`glowingentities` 1.4.11, `EffectLib` 10.2 і `triumph-gui` 3.1.7 підтримують 1.21.1 нарівні з
новішими (перевірено: у мапінгах glowingentities є `1.21.1`), тож їх не рухаємо.

**Перед підняттям версії будь-якої з таблиці** перевір, чи є в артефакті модуль під ревізію
сервера (`unzip -l <jar> | grep v1_21_R1` або тека `nms/` у репозиторії проєкту). Плагін-
залежність без модуля під твою ревізію просто не завантажиться на сервері — компіляція про це
не скаже нічого.

## Сервер — Arclight, тому цільове API — Spigot

Стек-трейси з живого сервера показують `arclight-neoforge` поверх
`org.bukkit.craftbukkit.v1_21_R1` — це NeoForge-мод із Bukkit-сумісністю, а не Paper. Він
реалізує класичний Spigot/CraftBukkit API, але НЕ Paper'ові доповнення до Bukkit-інтерфейсів.
Поки збірка йшла проти `paper-api`, компілятор пропускав такі виклики, і падали вони вже в
грі — `NoSuchMethodError` після натискання здібності. Тому залежність замінено на `spigot-api`:
**те, чого на сервері немає, тепер не збирається.**

Що чим замінено (усе — вже в коді):

| Paper-only | Заміна |
|---|---|
| `ItemMeta.displayName/lore(Component)` | застарілі `setDisplayName(String)` / `setLore(List<String>)` |
| `Player.sendActionBar(Component)` | `infrastructure.compat.ActionBars` |
| `Player.showTitle(Title)` | `player.sendTitle(title, subtitle, fadeIn, stay, fadeOut)` (тіки) |
| `Bukkit.getMobGoals()`, `Mob.getPathfinder()` | `infrastructure.compat.MobAiCompat` |
| `World.getNearbyPlayers/getNearbyLivingEntities` | `infrastructure.compat.Nearby` (куб + фільтр по сфері) |
| `ItemStack.serializeAsBytes/deserializeBytes` | `infrastructure.compat.ItemStacks` (`BukkitObjectOutputStream`) |
| `Player.setPlayerProfile`, `com.destroystokyo.paper.profile.*` | `infrastructure.compat.SkinProfiles` (рефлексія + чесний no-op) |
| `Villager.getReputation` (`com.destroystokyo.paper.entity.villager.*`) | ванільний `HERO_OF_THE_VILLAGE` (`SwindlerCharm`) |
| `Zombie/Skeleton.setShouldBurnInDay` | `setFireTicks(0)` у тіку сесії почту |
| `AttributeInstance.addTransientModifier` | `addModifier` + зняття в `cleanUp()` (інакше переживе рестарт) |
| `Entity.lockFreezeTicks(boolean)` | `setFreezeTicks(0)` щотакту пасивки (`CadavericResilience`) |
| `World.isDayTime()` | порівняння `world.getTime()` з межами дня |
| `Player.getTargetEntity(int)` | `world.rayTraceEntities(...)` |
| `Chunk.getTileEntities(predicate, boolean)` | ванільний `getTileEntities()` + власний фільтр |

**Увесь компат живе в `me.vangoo.infrastructure.compat`** — і більше ніде. Класи маленькі й
за темою: `ActionBars`, `Nearby`, `ItemStacks`, `MobAiCompat`, `SkinProfiles`,
`PacketEventsUdpGuard`. Потрібна ще одна заміна — заводь там сусідній клас, а не хелпер у
здібності.

**Заміни слабші за оригінал — це нормально, але мусить бути в коментарі.** Найважливіші
компроміси, які треба пам'ятати:

- `MobAiCompat.walkTo` — не маршрут, а поштовх швидкістю: моб іде по прямій і застрягає на
  рельєфі, тож кличучий мусить мати власний перескок по відстані.
- `MobAiCompat.suppressTargeting` глушить AI цілком (`setAware(false)`), бо точково зняти
  ЦІЛЬОВІ гоали вміє тільки Paper; на час наказу «атакувати» AI вмикає назад
  `allowVanillaCombat`, і тоді моб може перевибрати ціль сам — перевиставляй її щотакту.
- `SkinProfiles` на Arclight не робить нічого (лише WARNING у лог): личини працюють, скін і
  нік лишаються справжніми. Повноцінна заміна — власна розсилка player-info через
  PacketEvents (він уже в shade'і).
- `ItemStacks` пише ІНШИЙ формат, ніж Paper: старі байти в `gathering-state.json` не
  прочитаються (`null` — і кличучий мусить це пережити).
- Модифікатор атрибута більше не транзієнтний — той, хто його ставить, зобов'язаний зняти
  його в `cleanUp()`, інакше він потрапить у файл гравця назавжди.

❌ Не повертай `paper-api` в `pom.xml` заради одного зручного методу. ❌ Не клич Paper-API
рефлексією «бо так можна» — якщо метод потрібен, спершу пошукай ванільний еквівалент;
рефлексія лише там, де ванільного шляху не існує взагалі (`SkinProfiles`), і завжди з
видимим фолбеком.

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
одразу (`spigot-api` `provided` тримає межу). Рефлексію, що мовчки no-op'иться (як
`stripJukeboxPlayable`), лишай — але дірку, яку вона мала закривати, закривай явно.

## BetterModel: модуль під ревізію визначає поведінку анімацій

Вбудовані `idle_fly`/`walk_fly`/`walk` рахуються в NMS-модулі під конкретну ревізію гри, і
логіка між модулями РІЗНА. На 1.21.1 це `v1_21_R1`, де «летить» — це
`FlyingAnimal.isFlying | FlyingMob | Mob.isNoAi | Player.abilities.flying | isFallFlying`
(гілка `FlyingMob` пізніше зникла разом із класом). Деталі й наслідки — у
`.claude/rules/bettermodel-models.md`; читай там саме секцію під цю ревізію.

## PacketEvents 2.8.0 і чужі UDP-канали

PacketEvents вішає `ServerChannelHandler` на КОЖЕН канал зі списку серверного з'єднання, а той
у першому рядку `channelRead` робить `(Channel) msg` — правильно для TCP-слухача (читання
віддає прийняте з'єднання), фатально для UDP-каналу чужого мода (читання віддає
`DatagramPacket`). Каст падає ДО `fireChannelRead`, тобто чужі датаграми не доходять взагалі,
а на кожен пакет летить стек-трейс. Ловилось на моді Sable.

Гард `if (!(msg instanceof Channel)) return;` з'явився у PacketEvents **2.13.0** (перевірено на
байткоді: у 2.8.0 і 2.11.2 його немає). Версію не піднімаємо — 2.13.0 уже ламався на живому
сервері, див. таблицю вище. Замість цього той самий гард відтворює ззовні
`infrastructure.compat.PacketEventsUdpGuard`: знімає хендлер (ім'я —
`PacketEvents.CONNECTION_HANDLER_NAME`) з усіх каналів `SpigotChannelInjector.
injectedConnectionChannels`, які не є `io.netty.channel.ServerChannel`, і викидає їх із того ж
реєстру, щоб `uninject()` на вимкненні не варнив «handler not found».

Прохід ідемпотентний і кличеться двічі: одразу після `PacketEvents.getAPI().init()` в
`onEnable` і на `ServerLoadEvent` — обидві точки життєвого циклу, бо `InjectedList` усередині
PacketEvents інжектить і канали, прив'язані ПІСЛЯ ініціалізації.

❌ Прибирати гард «бо в лозі тихо» — тиша означає, що він працює. ❌ Знімати хендлер із
`ServerChannel` — це і є канал, заради якого PacketEvents існує.

## PacketEvents і модовані предмети в реєстрі чарів

Другий гард тієї ж природи: `infrastructure.compat.PacketEventsModdedRegistryGuard`.
Внутрішній лістенер PacketEvents розбирає `registry_data` і для кожного чару резолвить
`supported_items` через `ItemTypes.getRegistry().getByNameOrThrow(...)`, а в тому реєстрі
самі ванільні предмети — перший модований (`create:potato_cannon`) кидає виняток, і на
КОЖЕН вхід гравця в лог летить стек-трейс. Гард рефлексією виймає ключ реєстру чарів із
приватної мапи `SynchronizedRegistriesHandler.REGISTRY_KEYS`; `handleRegistry` для
невідомого ключа тихо виходить. Поведінки це не міняє (синхронізація й так падала), і
чарами в пакетах плагін не оперує — PacketEvents тут заради личин, нейм-плейтів і
метаданих сутностей. Кличеться раз в `onEnable` ПІСЛЯ `PacketEvents.getAPI().init()`:
реєстри PacketEvents ініціалізуються лише при виставленому `getAPI()`.

Перейменування поля ловить `PacketEventsModdedRegistryGuardTest` (далі поля тест не йде —
headless реєстри не ініціалізуються). ❌ Не «лагодити» це реєстрацією модованих предметів
у `ItemTypes`: їх тисячі й вони змінюються з модпаком.

## Заборони

- ❌ Піднімати версію API заради одного зручного методу — це міняє цільову версію сервера; повертати `paper-api` — тим паче (див. розділ про Arclight).
- ❌ Тягнути залежність, у якої немає модуля під `v1_21_R1`, «бо вона свіжіша».
- ❌ Обходити відсутнє API рефлексією без запасного шляху: no-op, який мовчки нічого не робить,
  на 1.21.1 читається як зламана механіка (саме так вийшло з `jukebox_playable`).
