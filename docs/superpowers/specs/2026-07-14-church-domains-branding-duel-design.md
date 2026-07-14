# Церкви: домени шляхів, брендинг і дуель ініціації — дизайн

Дата: 2026-07-14
Гілка: `feat/church-organizations`

## Мета

Закрити фідбек по церквах:

1. Вступ лишається миттєвим, але вибір шляху з домену церкви для гравця **без шляху**
   тепер проходить через **дуель** («Випробування шляху») з потойбічним створінням
   9 послідовності, з діалогом священика й безпечним поверненням при смерті.
2. Додати **всі 22 шляхи** як заглушки (16 нових без здібностей): зілля й характеристики
   всіх шляхів існують і фарбуються, але **без рецептів варіння**.
3. Універсальний **колір брендингу** на кожен шлях — застосовується до зілль (колір рідини)
   і Характеристик (колір назви + пер-шляховий ключ моделі під ресурс-пак).
4. Виправлення меню церкви: «Мій ранг» показує все на наведенні (без кліку); плитка
   пожертви предметом зсунута на слот правіше.

Не в обсязі: здібності стуб-шляхів, рецепти варіння стуб-шляхів, істоти/лут стуб-шляхів.

---

## Компонент 1 — 16 шляхів-заглушок + розширення груп

### PathwayGroup

Додаємо 5 канонічних груп (наявні 4 та їхні 6 шляхів не чіпаємо):

| enum | displayName |
|---|---|
| `EternalDarkness` | «Вічна Темрява» |
| `GoddessOfOrigin` | «Богиня Витоків» |
| `CalamityOfDestruction` | «Лихо Руйнування» |
| `FatherOfDevils` | «Батько Дияволів» |
| `KeyOfLight` | «Ключ Світла» |

Розподіл 16 стубів:

- **GodAlmighty** += `Sun`, `Tyrant`, `HangedMan`
- **DemonOfKnowledge** += `Hermit`, `Paragon`
- **TheAnarchy** += `BlackEmperor`
- **EternalDarkness**: `Darkness`, `Death`, `TwilightGiant`
- **GoddessOfOrigin**: `Mother`, `Moon`
- **CalamityOfDestruction**: `RedPriest`, `Demoness`
- **FatherOfDevils**: `Abyss`, `Chained`
- **KeyOfLight**: `WheelOfFortune`

### Класи (без 32 майже-однакових файлів)

`Pathway` отримує явний конструктор імені:

```java
public Pathway(PathwayGroup group, String name, List<String> sequenceNames) { ... }
// наявний Pathway(group, names) делегує з name = getClass().getSimpleName()
```

Один `me.vangoo.pathways.stub.StubPathway extends Pathway` (порожній `initializeAbilities()`),
один `me.vangoo.pathways.stub.StubPotions extends PathwayPotions` (рецепти не вантажаться).
`PathwayManager` реєструє 16 стубів із таблиці `name → group → 10 назв Sequence`.
`PotionManager` реєструє 16 стуб-`StubPotions` із кольором брендингу.

`Pathway.getName()` для стуба повертає передане ім'я (`Darkness`, `TwilightGiant`, …) — воно
має точно збігатися з ключами `InstitutionRegistry` та `PathwayManager` (тест
`InstitutionRegistryTest` уже пінить покриття шляхів; після цієї зміни всі canonical-доступи
резолвитимуться в наявний шлях).

### Назви послідовностей (індекс 0 = найсильніша … 9 = найслабша)

Англійський канон новели (як у наявних 6 шляхів). До ревʼю користувача:

- **Sun**: Sun, White Angel, Light Seeker, Justice Mentor, Shadowless, Priest of Light, Notary, Solar High Priest, Light Petitioner, Bard
- **Tyrant**: Tyrant, God of Thunder, Calamity, Sea King, Calamity Patron, Ocean Songster, Wind-Blessed, Seafarer, Furious One, Sailor
- **HangedMan**: Hanged Man, Dark Angel, Profaner Presbyter, Trinity Templar, Black Knight, Shepherd, Rose Bishop, Shadow Ascetic, Listener, Secrets Supplicant
- **Darkness**: Darkness, Knight of Misfortune, Concealed Servitor, Bishop of Horror, Nightwatcher, Spirit Sorcerer, Soul Assurer, Nightmare, Midnight Poet, Sleepless
- **Death**: Death, Pale Emperor, Consul of Death, Ferryman, Undying, Gatekeeper, Spirit Usher, Spiritualist, Gravedigger, Corpse Collector
- **TwilightGiant**: Twilight Giant, Hand of God, Glory, Silver Knight, Demon Hunter, Guardian, Dawn Paladin, Weapon Master, Pugilist, Warrior
- **RedPriest**: Red Priest, Conqueror, Weather Warlock, War Bishop, Iron-Blooded Knight, Reaper, Conspirator, Pyromaniac, Provoker, Hunter
- **Demoness**: Demoness, Apocalypse, Catastrophe, Everlasting, Despair, Suffering, Pleasure, Witch, Instigator, Assassin
- **Hermit**: Hermit, Sage, Emperor of Knowledge, Clairvoyant, Mysticologist, Constellation Magister, Scroll Professor, Warlock, Adjacent Scholar, Seeker of Mysteries
- **Paragon**: Paragon, Illuminator, Knowledge Mentor, Arcane Scholar, Alchemist, Astronomer, Craftsman, Appraiser, Archaeologist, Scholar
- **Mother**: Mother, Nature Walker, Matriarch of Desolation, Life Giver, Ancient Alchemist, Druid, Biologist, Harvest Priest, Physician, Gardener
- **Moon**: Moon, Goddess of Beauty, Moon Duke, High Sorcerer, Shaman King, Crimson Scholar, Potions Professor, Vampire, Beast Tamer, Apothecary
- **Abyss**: Abyss, Filthy Monarch, Bloody Archduke, Prattler, Demon, Apostle of Desire, Devil, Serial Killer, Wingless Angel, Criminal
- **Chained**: Chained, Abomination, Ancient Curse, Disciple of Silence, Doll, Ghost, Zombie, Werewolf, Sleepwalker, Prisoner
- **BlackEmperor**: Black Emperor, Fallen Angel, Duke of Entropy, Prince of Abrogation, Count of the Fallen, Mentor of Disorder, Baron of Corruption, Briber, Barbarian, Advocate
- **WheelOfFortune**: Wheel of Fortune, Mercury Serpent, Diviner, Mage of Misfortune, Chaos Walker, Victor, Priest of Misfortune, Lucky One, Robot, Monster

---

## Компонент 2 — Брендинг-колір і тонування

Єдине джерело правди `me.vangoo.domain.PathwayBranding` (domain-корінь — там, де вже дозволено
`org.bukkit.Color`, поряд із `PathwayPotions`):

```java
public record Branding(Color liquid, ChatColor text) {}
public static Branding of(String pathwayName)  // фолбек для невідомого → нейтральний сірий
```

Таблиця кольорів (RGB рідини / ChatColor назви) — до ревʼю:

| Шлях | RGB | ChatColor |
|---|---|---|
| Error | 26,0,181 | DARK_BLUE |
| Visionary | 128,128,128 | GRAY |
| Door | 0,0,115 | BLUE |
| Justiciar | 255,255,0 | YELLOW |
| WhiteTower | 255,0,50 | RED |
| Fool | 128,0,128 | LIGHT_PURPLE |
| Sun | 255,190,0 | GOLD |
| Tyrant | 0,140,170 | DARK_AQUA |
| HangedMan | 205,130,160 | LIGHT_PURPLE |
| Darkness | 35,25,70 | DARK_GRAY |
| Death | 150,165,140 | GRAY |
| TwilightGiant | 150,110,70 | GOLD |
| RedPriest | 170,0,0 | DARK_RED |
| Demoness | 200,0,120 | LIGHT_PURPLE |
| Hermit | 0,175,200 | AQUA |
| Paragon | 175,150,90 | YELLOW |
| Mother | 60,160,60 | GREEN |
| Moon | 170,190,225 | AQUA |
| Abyss | 80,0,120 | DARK_PURPLE |
| Chained | 95,95,105 | GRAY |
| BlackEmperor | 45,0,55 | DARK_PURPLE |
| WheelOfFortune | 120,80,165 | LIGHT_PURPLE |

- **PotionManager**: усі 22 зілля беруть `liquid` із `PathwayBranding` (наявні 6 хардкодів
  переїжджають у таблицю без зміни значень).
- **CharacteristicCodec.create(pathway, seq, amount)**: назва фарбується `text`-кольором
  шляху (замість фіксованого `LIGHT_PURPLE`); ключ моделі стає пер-шляховим
  `characteristic_<pathway>` (був єдиний `characteristic`) — ресурс-пак зможе тонувати
  кожен шлях окремо. Матеріал (`MUSIC_DISC_CHIRP`) і решта NBT/флагів — без змін.

---

## Компонент 3 — Випробування шляху (дуель ініціації)

Вступ у церкву лишається миттєвим. Стара vault-ініціація (полювання на N істот) **замінюється**
дуеллю. Гейт незмінний: гравець без шляху, `!initiationUsed`, без активного випробування.

### Потік

1. Меню церкви → плитка **«Випробування шляху»** (замість старої `[Ініціація]`). Наведення
   показує опис і попередження підготуватись: дуель смертельно небезпечна.
2. Клік → `ConfirmationMenu` («Ви ризикуєте життям ↔ Право обрати шлях домену»).
3. Підтвердження → зберігаємо return-локацію + gamemode гравця → телепорт у порожній
   void-світ **`mysteries_duel`** (`DuelArenaProvider`, ідемпотентний, за зразком
   `GatheringVenueProvider`).
4. **Діалог священика** — по-літерна доповідь у action bar (за патерном самотікового
   `OrganizerBriefing` → новий `DuelBriefing`), гравець заморожений; по завершенні
   спавниться **істота Sequence 9 чужої церкві групи** через `MythicCreatureGateway`.
5. `ChurchDuelService` + `DuelSession` стежать за наслідком:
   - **Гравець гине** → keepInventory + keepLevel, телепорт на return-локацію, «Ви програли
     дуель». Речі/досвід збережені.
   - **Істота гине** → телепорт назад, «Ви здолали створіння!» → відкриваємо меню вибору
     шляху домену.
6. Вибір шляху → видаємо **Seq-9 зілля** обраного шляху напряму
   (`potionManager.createPotionItem` — рецепт НЕ потрібен, тож усі 16 стубів працюють) +
   `recipeUnlockService.unlockRecipe(id, pathway, 9)` + `initiationUsed.add(id)`. Випивання
   зілля робить гравця Beyonder'ом наявним флоу.

### Опонент

Будь-яка наявна Seq-9 істота з групи, **чужої** домену церкви (лише 6 Seq-9 істот, усі —
реалізованих шляхів). Опонент тематичний, не привʼязаний до майбутнього вибору шляху.
Логіка «чужої групи» — та сама, що в `ChurchTaskGenerator` HUNT.

### Класи

- `me.vangoo.infrastructure.organizations.DuelArenaProvider` — світ `mysteries_duel`
  (void + арена-платформа), `arenaSpawn()`, `isDuelWorld(world)`.
- `me.vangoo.application.services.ChurchDuelService` — оркестратор: `startTrial(player)`,
  реєстр активних `DuelSession` (instance-`Map<UUID, DuelSession>`, не static), вибір
  опонента, видача зілля/вибір шляху, безпечне завершення.
- `DuelSession` — самотіковий обʼєкт (власний `BukkitTask`): тримає uuid спавненої істоти,
  return-локацію, gamemode; таймаут; `cancel()` (деспавн істоти + повернення гравця).
- `me.vangoo.infrastructure.organizations.DuelBriefing` — по-літерний action-bar діалог
  священика (патерн `OrganizerBriefing`).
- Лістенер смерті/перемоги: `PlayerDeathEvent` (у duel-світі → keepInv, поразка) та
  `EntityDeathEvent` (istota == опонент → перемога). Провід через `ChurchListener` або
  новий `DuelListener` (presentation).

### Краш-безпека

Return-локації — in-memory (дуель короткоживуча). При `onDisable` — усі активні дуелі
завершуються (телепорт учасників геть, деспавн істот). При вході гравця, якщо він застряг у
`mysteries_duel` без активної сесії (крах сервера) — телепорт на spawn головного світу з
речами. Схему персисту не міняємо.

---

## Компонент 4 — Виправлення `ChurchMenu`

- **«Мій ранг»**: плитка головного меню стає **некликабельною**; ранг + вклад за весь час +
  баланс + прогрес до наступного рангу показуються в її **lore** (наведення). Підменю
  `openRank` прибираємо.
- **Пожертви**: плитка «Пожертвувати предмет у руці» (`CHEST`) зсувається зі слота (2,3) на
  (2,4). «Пожертвувати монети» лишається (2,6), «Назад» — (3,5) → симетрично навколо центру.

---

## Провід (ServiceContainer)

- `PathwayBranding` — статичний домен-хелпер, проводу не потребує.
- Нові application/infra сервіси (`ChurchDuelService`, `DuelArenaProvider`, `DuelBriefing`)
  створюються в `ServiceContainer` у фазі application/UI; лістенер дуелі реєструється в
  `registerEvents()`; завершення дуелей — у ланцюжку `onDisable`.
- 16 стуб-шляхів і зілля — в `PathwayManager.initializePathways()` та
  `PotionManager.initializePotions()`.

## Тести

- `PathwayGroupTest`/наявні — 9 груп.
- `InstitutionRegistryTest` — усі canonical-доступи резолвляться (оновити пінінг покриття).
- `PathwayManager` не юніт-тестується headless (тягне Bukkit) — перевірка стубів у грі.
- `PathwayBrandingTest` — усі 22 шляхи мають брендинг; фолбек для невідомого.
- Дуель — не юніт-тест (Bukkit-світ/істоти); перевірка на сервері.
- Меню — перевірка на сервері.

## Оновлення документації (той самий коміт)

- `.claude/rules/church-organizations.md` — дуель-ініціація замість vault-ініціації, брендинг,
  стуб-шляхи.
- `.claude/rules/new-content-checklist.md` — «Наразі зареєстровано 6 pathways» → 22 (6 повних +
  16 стубів); нотатка про `StubPathway`/`StubPotions` і `PathwayBranding`.
- `CLAUDE.md` — секція архітектури pathways (кількість/стуби), `church.*` домени.
- Новий `.claude/rules/pathway-branding.md` — як додати/змінити брендинг-колір.
