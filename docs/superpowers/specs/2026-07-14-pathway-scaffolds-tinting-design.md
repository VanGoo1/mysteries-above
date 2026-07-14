# Заготовки 16 шляхів замість спільного stub + уніфікація tinting

**Дата:** 2026-07-14
**Гілка:** feat/church-organizations (або нова)
**Статус:** дизайн затверджено

## Мотивація

Зараз усі 16 нереалізованих шляхів обслуговує один спільний
`me.vangoo.pathways.stub.StubPathway` / `StubPotions`. Коли якийсь шлях
дописуватимуть по-справжньому, стуб доведеться видаляти й з нуля створювати папку
пакета — тобто спільний стуб є тимчасовим боргом, а не заготовкою.

Мета: замінити спільний стуб на **окремі повноцінні заготовки-пакети**, ідентичні за
формою реальним шляхам (Fool/Error), щоб майбутня реалізація зводилася до наповнення
`initializeAbilities()` і `potion-recipes.yml`, без видалення й перестворення.

Побічно — довести до кінця «tinting»: колір НАЗВИ зілля має братися з
`PathwayBranding` (єдине джерело правди) для ВСІХ шляхів. Зараз лише `StubPotions`
робить це правильно; 6 реальних `*Potions` хардкодять `ChatColor`-літерал, який до того
ж розійшовся з брендингом.

## Рішення (затверджені)

1. **Гейт «шлях не готовий»** — авто-детект по порожніх здібностях (не marker-інтерфейс,
   не окремий прапор).
2. **Tinting** — прибрати параметри кольору з конструкторів `*Potions`; кольори тягне сам
   `PathwayPotions` з `PathwayBranding`. Брендинг виграє (назви Error/Door/Justiciar/
   WhiteTower/Fool зміняться й узгодяться з кольором рідини).
3. **Порожня папка `abilities/`** тримається файлом `package-info.java` (не `.gitkeep`).

## Архітектура

### 1. Заготовки шляхів (16 пакетів)

Для кожного шляху — пакет `me.vangoo.pathways.<name>/`, ідентичний за формою реальним:

- `<Name>.java` — `public class <Name> extends Pathway` з конструктором
  `(PathwayGroup group, List<String> sequenceNames)` (2-арг, як у реальних — ім'я
  виводиться з simple-name класу) і **порожнім** `initializeAbilities()`.
- `<Name>Potions.java` — `public class <Name>Potions extends PathwayPotions`, конструктор
  за новою сигнатурою (§3), `loadRecipes(recipes)` з поки-порожньою мапою.
- `<name>/abilities/package-info.java` — матеріалізує порожній пакет `abilities` у git +
  javadoc-нотатка «сюди додавати здібності, реєструвати в `<Name>.initializeAbilities`».

**16 класів** (ім'я класу = ключ `PathwayManager`):
`Sun, Tyrant, HangedMan, Hermit, Paragon, BlackEmperor, Darkness, Death, TwilightGiant,
Mother, Moon, RedPriest, Demoness, Abyss, Chained, WheelOfFortune`.

Пакети (lowercase simple-name): `sun, tyrant, hangedman, hermit, paragon, blackemperor,
darkness, death, twilightgiant, mother, moon, redpriest, demoness, abyss, chained,
wheeloffortune`.

Приклад заготовки (за зразком `Fool`):

```java
package me.vangoo.pathways.sun;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import java.util.List;

/**
 * Sun Pathway (Шлях Сонця) — заготовка. Здібностей ще немає; наповнюється в
 * initializeAbilities() при реалізації. Належить групі God Almighty.
 */
public class Sun extends Pathway {
    public Sun(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        // Заготовка — здібностей ще немає.
    }
}
```

### 2. Гейт «шлях ще не готовий» — авто-детект

- `StubPathway` і `StubPotions` **видаляються** повністю.
- У `domain.entities.Pathway` — новий чистий метод:

  ```java
  /** true, якщо бодай одна послідовність має зареєстровану здібність. */
  public boolean hasAnyAbility() {
      return sequenceAbilities.values().stream().anyMatch(list -> !list.isEmpty());
  }
  ```

  Юніт-тест у `PathwayTest` (порожній шлях → false; шлях зі здібністю → true).
- `ChurchService.initiationPathwayChoices` (наразі рядок 563):
  `!(pathway instanceof StubPathway)` → `pathway.hasAnyAbility()`.
  Заготовки (0 здібностей) автоматично не пропонуються як вибір ініціації; 6 реальних
  (Fool має здібності 9–5 тощо) — пропонуються. **Поведінка збережена**, залежність від
  конкретного класу-стуба прибрана.

### 3. Tinting: колір з `PathwayBranding` для всіх

`PathwayPotions` (корінь `domain`, де `org.bukkit.Color`/`ChatColor` дозволені) сам тягне
обидва кольори з `PathwayBranding.of(pathway.getName())`. Конструктор втрачає параметри
`Color potionColor` і `ChatColor nameColor`:

```java
// Було:
public PathwayPotions(Pathway pathway, Color potionColor, ChatColor nameColor,
                      List<String> description, IItemResolver itemResolver) { ... }

// Стане:
public PathwayPotions(Pathway pathway, List<String> description, IItemResolver itemResolver) {
    PathwayBranding.Branding b = PathwayBranding.of(pathway.getName());
    this.potionColor = b.liquid();
    this.nameColor = b.text();
    ...
}
```

Усі 6 реальних `*Potions` і 16 заготовок викликають `super(pathway, List.of(), itemResolver)`
— жоден клас більше не може задати колір повз брендинг.

```java
public class SunPotions extends PathwayPotions {
    public SunPotions(Pathway pathway, IItemResolver itemResolver,
                      Map<Integer, RecipeDefinition> recipes) {
        super(pathway, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
```

**Наслідок для кольору назви (брендинг виграє):**

| Шлях | Було (хардкод) | Стане (брендинг) |
|------|----------------|-------------------|
| Error | RED | DARK_BLUE |
| Visionary | GRAY | GRAY (без змін) |
| Door | RED | BLUE |
| Justiciar | GOLD | YELLOW |
| WhiteTower | AQUA | RED |
| Fool | DARK_PURPLE | LIGHT_PURPLE |

Колір рідини (`liquidOf`) не змінюється в жодного — `PotionManager` уже брав його з
брендингу; тепер це просто робить сам `PathwayPotions`.

### 4. Проводка

**`PathwayManager.initializePathways`:**
- Прибрати import `StubPathway` і helper `registerStub(...)`.
- 16 рядків `registerStub("Sun", group, List.of(...))` →
  `pathways.put("Sun", new Sun(group, List.of(...)))`.
- 10-назв-списки послідовностей лишаються тут (як у реальних шляхів) — тільки міняється
  спосіб інстанціювання. Додати 16 import-ів конкретних класів.

**`PotionManager.initializePotions`:**
- Прибрати import `StubPotions` і окремий `Set<String> stubs` цикл.
- Кожен із 22 шляхів реєструється через свій `*Potions` клас; конструктори більше не
  приймають кольори:
  ```java
  potions.add(new ErrorPotions(pathwayManager.getPathway("Error"),
          customItemService, recipeConfig.getOrDefault("Error", Map.of())));
  // ... і так далі для всіх 22, включно з 16 заготовками
  ```

### 5. Тести й документація

- Видалити `src/test/java/me/vangoo/pathways/stub/StubPathwayTest.java`.
- `PathwayTest` — додати тест `hasAnyAbility()` (порожній → false; зі здібністю → true).
- `InstitutionRegistryTest.stubOnlyChurchesAreTheKnownSet` — оновити коментар (згадку
  `me.vangoo.pathways.stub.StubPathway` → «шлях без здібностей»); логіка (набір із 6 імен)
  не змінюється.
- `PathwayBrandingTest` — не чіпаємо (22 імені лишаються).
- Оновити правила/доки в тому ж коміті:
  - `.claude/rules/new-content-checklist.md` — прибрати абзац про спільний
    `StubPathway`/`StubPotions`; описати заготовки-пакети.
  - `.claude/rules/church-organizations.md` — згадки `StubPathway` → авто-детект
    `hasAnyAbility()`.
  - `.claude/rules/pathway-branding.md` — додати: колір НАЗВИ зілля теж лише через
    брендинг; `PathwayPotions` не приймає кольори.
  - `CLAUDE.md` — опис «22 шляхи: 6 реальних + 16 заготовок-пакетів» (без спільного stub);
    оновити згадку `me.vangoo.pathways.stub`.

## Обсяг

- **+48 нових файлів:** 16 × (`<Name>.java`, `<Name>Potions.java`,
  `abilities/package-info.java`).
- **−3 видалених:** `StubPathway.java`, `StubPotions.java`, `StubPathwayTest.java`.
- **~10 змінених:** `Pathway.java`, `PathwayPotions.java`, 6 реальних `*Potions`,
  `PathwayManager.java`, `PotionManager.java`, `ChurchService.java`, `PathwayTest.java`,
  `InstitutionRegistryTest.java` + 4 доки.

## Інваріанти (зберігаються)

- `domain` не залежить від `me.vangoo.pathways` (ArchitectureTest) — заготовки в
  `pathways`, `hasAnyAbility()` у `domain.entities.Pathway`, кольори через
  `PathwayBranding` (корінь `domain`).
- Хардкод `Color`/`ChatColor` кольорів шляху повз `PathwayBranding` неможливий (усунено з
  конструкторів `*Potions`).
- Церква пропонує ініціацію тільки для шляхів зі здібностями (`hasAnyAbility()`), як і
  раніше пропонувала лише не-стуби.

## Перевірка

1. `mvn clean package` — компіляція + усі тести (ArchitectureTest, PathwayTest,
   PathwayBrandingTest, InstitutionRegistryTest, ChurchTaskGeneratorTest).
2. In-server: `/pathway` видати заготовку (напр. Sun) — зілля кольорове за брендингом,
   здібностей нема; переконатися, що дуель-вибір церкви заготовки не пропонує.
3. Візуально: назви зілль Error/Door/Justiciar/WhiteTower/Fool узгоджені з кольором рідини.
