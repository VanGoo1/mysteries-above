# Market Gathering Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add seven gathering-phase improvements: human-readable item names, a join countdown, punishment for violence/ability attempts with a persistent ban, a lectern to open the market menu, "back" buttons, raised buyback for rare items, and a typewriter organizer briefing that freezes players on open.

**Architecture:** Pure rules go to `domain.market` (`GatheringConduct`, unit-tested). Orchestration stays in `GatheringService` (application). Bukkit effects (briefing, freeze, lectern, action bar) live in `infrastructure.market` / `presentation.listeners` and are verified in-server. A narrow `GatheringAbilityGuard` interface lets `AbilityExecutor` block ability casts at the gathering without a hard dependency.

**Tech Stack:** Java 21, Spigot/Paper API 1.21, triumph-gui, JUnit 5, Maven.

## Global Constraints

- All user-facing text (ability/GUI/message strings, item names) MUST be Ukrainian. Identifiers, class/package names, config keys, logs, and canonical Sequence/pathway names stay English.
- `domain` MUST NOT import `org.bukkit.*`, `dev.triumphteam.*`, or `net.kyori.*`. `domain` MUST NOT depend on `me.vangoo.pathways`. Enforced by `ArchitectureTest`.
- Money math is in coppets (1 pound = 20 coppets).
- Commit messages: Conventional Commits, English only, imperative subject ≤72 chars, no trailing period, no dash as separator. End each with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Snapshot JSON schema stays backward compatible: a new field read from an old file (gson → `null`) must be treated as empty.
- **Build command** (Maven is not on PATH — use IntelliJ's bundled Maven):
  ```powershell
  & "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" <args>
  ```
  In steps below this is written as `mvn <args>` — substitute the full path.

---

## File Structure

**Create:**
- `src/main/java/me/vangoo/domain/market/GatheringConduct.java` — pure violation counter → sanction.
- `src/test/java/me/vangoo/domain/market/GatheringConductTest.java` — unit test for the above.
- `src/main/java/me/vangoo/application/services/MarketItemNamer.java` — `itemKey → Ukrainian name`.
- `src/test/java/me/vangoo/application/services/MarketItemNamerTest.java` — unit test for name formatting.
- `src/main/java/me/vangoo/application/services/GatheringAbilityGuard.java` — narrow interface for blocking ability casts.
- `src/main/java/me/vangoo/infrastructure/market/OrganizerBriefing.java` — self-ticking typewriter briefing.

**Modify:**
- `src/main/java/me/vangoo/infrastructure/market/GatheringSnapshotRepository.java` — add `bannedFromNext` to `Snapshot`.
- `src/test/java/me/vangoo/infrastructure/market/GatheringSnapshotRepositoryTest.java` — update `new Snapshot(...)`.
- `src/main/java/me/vangoo/application/services/GatheringService.java` — countdown, conduct, ban, freeze, briefing, guard impl.
- `src/main/java/me/vangoo/application/services/AbilityExecutor.java` — consult the guard.
- `src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java` — names, back buttons, principles tab.
- `src/main/java/me/vangoo/presentation/listeners/GatheringListener.java` — damage violation, freeze move-cancel, lectern interact.
- `src/main/java/me/vangoo/infrastructure/market/GatheringVenueProvider.java` — place the lectern.
- `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` — wire namer, guard setter, menu into listener.
- `src/main/resources/config.yml` — raise buyback prices.
- `.claude/rules/market-gathering.md` — document new mechanics.

---

## Task 1: GatheringConduct (pure violation model)

**Files:**
- Create: `src/main/java/me/vangoo/domain/market/GatheringConduct.java`
- Test: `src/test/java/me/vangoo/domain/market/GatheringConductTest.java`

**Interfaces:**
- Produces: `GatheringConduct` with `Sanction recordViolation(UUID)` (enum `Sanction { WARN, KICK }`) and `void reset()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/me/vangoo/domain/market/GatheringConductTest.java`:
```java
package me.vangoo.domain.market;

import me.vangoo.domain.market.GatheringConduct.Sanction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatheringConductTest {

    private final GatheringConduct conduct = new GatheringConduct();

    @Test
    void firstViolationWarnsThenSubsequentKick() {
        UUID p = UUID.randomUUID();
        assertEquals(Sanction.WARN, conduct.recordViolation(p));
        assertEquals(Sanction.KICK, conduct.recordViolation(p));
        assertEquals(Sanction.KICK, conduct.recordViolation(p));
    }

    @Test
    void countersAreIndependentPerPlayer() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(Sanction.WARN, conduct.recordViolation(a));
        assertEquals(Sanction.WARN, conduct.recordViolation(b));
        assertEquals(Sanction.KICK, conduct.recordViolation(a));
    }

    @Test
    void resetClearsCounters() {
        UUID p = UUID.randomUUID();
        conduct.recordViolation(p);
        conduct.reset();
        assertEquals(Sanction.WARN, conduct.recordViolation(p));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=GatheringConductTest`
Expected: FAIL (compilation error — `GatheringConduct` does not exist).

- [ ] **Step 3: Write minimal implementation**

`src/main/java/me/vangoo/domain/market/GatheringConduct.java`:
```java
package me.vangoo.domain.market;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Лічильник порушень спокою на зборах. Чистий домен: перше порушення —
 * попередження, наступні — вигнання. Без Bukkit; санкцію виконує GatheringService.
 */
public class GatheringConduct {

    public enum Sanction { WARN, KICK }

    private final Map<UUID, Integer> strikes = new HashMap<>();

    public Sanction recordViolation(UUID playerId) {
        int count = strikes.merge(playerId, 1, Integer::sum);
        return count <= 1 ? Sanction.WARN : Sanction.KICK;
    }

    public void reset() {
        strikes.clear();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=GatheringConductTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/market/GatheringConduct.java src/test/java/me/vangoo/domain/market/GatheringConductTest.java
git commit -m "feat(market): gathering conduct violation model

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: MarketItemNamer (itemKey → Ukrainian name)

**Files:**
- Create: `src/main/java/me/vangoo/application/services/MarketItemNamer.java`
- Test: `src/test/java/me/vangoo/application/services/MarketItemNamerTest.java`

**Interfaces:**
- Consumes: `CustomItemService.getItem(String) : Optional<CustomItem>`; `CustomItem.displayName() : String`.
- Produces: `MarketItemNamer` with `String displayName(String itemKey)`.

Note: itemKey shapes are `custom:<id>`, `recipe:<pathway>:<seq>`, `characteristic:<pathway>:<seq>`.

- [ ] **Step 1: Write the failing test**

`src/test/java/me/vangoo/application/services/MarketItemNamerTest.java`:
```java
package me.vangoo.application.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketItemNamerTest {

    // Non-custom branches never touch CustomItemService, so null is safe here.
    private final MarketItemNamer namer = new MarketItemNamer(null);

    @Test
    void formatsRecipeKey() {
        assertEquals("Книга рецептів (Fool, Посл. 9)", namer.displayName("recipe:Fool:9"));
    }

    @Test
    void formatsCharacteristicKey() {
        assertEquals("Характеристика (Door, Посл. 8)", namer.displayName("characteristic:Door:8"));
    }

    @Test
    void humanizesUnknownKey() {
        assertEquals("Dimensional wanderer eye", namer.displayName("custom:dimensional_wanderer_eye"));
    }
}
```

Note: the third test drives the `custom:` branch with `CustomItemService == null`; the implementation must guard against a null service and fall back to humanizing. (In production the service is always present.)

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=MarketItemNamerTest`
Expected: FAIL (compilation error — `MarketItemNamer` does not exist).

- [ ] **Step 3: Write minimal implementation**

`src/main/java/me/vangoo/application/services/MarketItemNamer.java`:
```java
package me.vangoo.application.services;

import me.vangoo.domain.valueobjects.CustomItem;

/**
 * Перетворює ринковий itemKey (custom:/recipe:/characteristic:) на людську
 * укр-назву для меню. Головний шлях — інгредієнти (custom:), назву яких дає
 * CustomItemService; решта — форматований рядок, невідоме — олюднений хвіст ключа.
 */
public class MarketItemNamer {

    private final CustomItemService customItems;

    public MarketItemNamer(CustomItemService customItems) {
        this.customItems = customItems;
    }

    public String displayName(String itemKey) {
        if (itemKey == null || itemKey.isEmpty()) {
            return "?";
        }
        if (itemKey.startsWith("custom:")) {
            String id = itemKey.substring("custom:".length());
            if (customItems != null) {
                return customItems.getItem(id).map(CustomItem::displayName).orElseGet(() -> humanize(id));
            }
            return humanize(id);
        }
        String[] parts = itemKey.split(":");
        if (itemKey.startsWith("recipe:") && parts.length == 3) {
            return "Книга рецептів (" + parts[1] + ", Посл. " + parts[2] + ")";
        }
        if (itemKey.startsWith("characteristic:") && parts.length == 3) {
            return "Характеристика (" + parts[1] + ", Посл. " + parts[2] + ")";
        }
        return humanize(itemKey);
    }

    static String humanize(String raw) {
        String tail = raw.contains(":") ? raw.substring(raw.lastIndexOf(':') + 1) : raw;
        String spaced = tail.replace('_', ' ').trim();
        if (spaced.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=MarketItemNamerTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/application/services/MarketItemNamer.java src/test/java/me/vangoo/application/services/MarketItemNamerTest.java
git commit -m "feat(market): resolve market item keys to readable names

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Wire MarketItemNamer into the menu

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java:275-276`

**Interfaces:**
- Consumes: `MarketItemNamer.displayName(String) : String` (Task 2).

- [ ] **Step 1: Add the namer field and constructor param to MarketMenu**

In `MarketMenu.java`, add the import and field, and extend the constructor:
```java
import me.vangoo.application.services.MarketItemNamer;
```
Add field after `private final ChatPromptService prompts;`:
```java
    private final MarketItemNamer namer;
```
Change the constructor signature and body:
```java
    public MarketMenu(Plugin plugin, GatheringService gatheringService,
                      WalletService walletService, ChatPromptService prompts,
                      MarketItemNamer namer) {
        this.plugin = plugin;
        this.gatheringService = gatheringService;
        this.walletService = walletService;
        this.prompts = prompts;
        this.namer = namer;
    }
```

- [ ] **Step 2: Use the namer in openOrders**

In `openOrders`, replace the display line building `ChatColor.AQUA + "Шукають: " + ChatColor.WHITE + order.itemKey() + " ×" + order.amount()` with:
```java
            ItemStack display = button(Material.PAPER,
                    ChatColor.AQUA + "Шукають: " + ChatColor.WHITE
                            + namer.displayName(order.itemKey()) + " ×" + order.amount(),
                    ChatColor.DARK_GRAY + "Замовник: " + gatheringService.aliasOf(order.buyerId()));
```

- [ ] **Step 3: Use the namer in openNegotiations**

In `openNegotiations`, replace `ChatColor.WHITE + view.itemKey() + " ×" + view.amount()` with:
```java
                            + ChatColor.WHITE + namer.displayName(view.itemKey()) + " ×" + view.amount(),
```

- [ ] **Step 4: Create and pass the namer in ServiceContainer**

In `ServiceContainer.java`, add a field near the other market fields (next to `gatheringService`):
```java
    private MarketItemNamer marketItemNamer;
```
In `initializeUI()`, before the `marketMenu` assignment (line ~275), add:
```java
        this.marketItemNamer = new MarketItemNamer(customItemService);
```
Change the `marketMenu` construction to pass it:
```java
        this.marketMenu = new me.vangoo.infrastructure.ui.MarketMenu(
                plugin, gatheringService, walletService, chatPromptService, marketItemNamer);
```
Add the import at the top if not present:
```java
import me.vangoo.application.services.MarketItemNamer;
```

- [ ] **Step 5: Build**

Run: `mvn clean package -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(market): show readable item names in orders and deals

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Raise buyback prices for characteristics and recipe books

**Files:**
- Modify: `src/main/resources/config.yml:24-31`

Balance change (config only; `BuybackPriceTableTest` hardcodes its own table and is unaffected).

- [ ] **Step 1: Edit config values**

In `config.yml`, replace the buyback block:
```yaml
  buyback:                    # скупка організатором (коппети за одиницю; джерело емісії)
    ingredient-coppets: 2
    recipe-book-coppets: 200  # рецепт розблоковує просування — рідкісний, високий викуп
    characteristic-coppets-by-seq:
      "9": 80
      "8": 130
      "7": 220
      "6": 360
      "5": 540
    overrides: {}             # точкові ціни: "custom:<id>": коппети
```

- [ ] **Step 2: Build**

Run: `mvn clean package -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config.yml
git commit -m "balance(market): raise buyback for characteristics and recipe books

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Back buttons in every menu tab

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java`

**Interfaces:**
- Produces: `PaginatedGui paginated(String title, Runnable back)` (adds a back button at slot 6,1).

- [ ] **Step 1: Add a `back` param to `paginated`**

Replace the `paginated` helper. The back button runs its target on the main thread (GUI open must be synchronous):
```java
    private PaginatedGui paginated(String title, Runnable back) {
        PaginatedGui gui = Gui.paginated()
                .title(Component.text(title).color(NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .rows(6)
                .pageSize(36)
                .disableAllInteractions()
                .create();
        gui.setItem(6, 1, new GuiItem(button(Material.BARRIER, ChatColor.GRAY + "◄ Назад"),
                e -> Bukkit.getScheduler().runTask(plugin, back)));
        gui.setItem(6, 3, new GuiItem(button(Material.ARROW, ChatColor.GRAY + "◄ Попередня"),
                e -> gui.previous()));
        gui.setItem(6, 7, new GuiItem(button(Material.ARROW, ChatColor.GRAY + "Наступна ►"),
                e -> gui.next()));
        return gui;
    }
```

- [ ] **Step 2: Pass a back target from each caller**

Update the four `paginated(...)` call sites:
- `openLots`: `PaginatedGui gui = paginated("🕯 Лоти", () -> openMain(player));`
- `openOrders`: `PaginatedGui gui = paginated("🕯 Замовлення", () -> openMain(player));`
- `openKnownIngredients`: `PaginatedGui gui = paginated("🕯 Інгредієнти ваших рецептів", () -> openOrders(player));`
- `openNegotiations`: `PaginatedGui gui = paginated("🕯 Мої угоди", () -> openMain(player));`

- [ ] **Step 3: Build**

Run: `mvn clean package -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java
git commit -m "feat(market): add back button to market menu tabs

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Add `bannedFromNext` to the crash snapshot

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/market/GatheringSnapshotRepository.java:31-34`
- Modify: `src/test/java/me/vangoo/infrastructure/market/GatheringSnapshotRepositoryTest.java:35-48`
- Modify: `src/main/java/me/vangoo/application/services/GatheringService.java:654` (persist)

**Interfaces:**
- Produces: `Snapshot(long, List<ParticipantHome>, List<EscrowItem>, List<EscrowItem>, List<String> bannedFromNext)`.

- [ ] **Step 1: Extend the record**

In `GatheringSnapshotRepository.java`, change the `Snapshot` record:
```java
    public record Snapshot(long nextGatheringEpochMillis,
                            List<ParticipantHome> participants,
                            List<EscrowItem> escrow,
                            List<EscrowItem> pendingReturns,
                            List<String> bannedFromNext) {}
```

- [ ] **Step 2: Update the round-trip test**

In `GatheringSnapshotRepositoryTest.java`, change the `new Snapshot(...)` (currently 4 args) to include the new list:
```java
        Snapshot original = new Snapshot(
                1_720_000_000_000L,
                List.of(
                        new ParticipantHome("player-1", "world", 10.5, 64.0, -20.25, 90.0f, 0.0f),
                        new ParticipantHome("player-2", "world_nether", -5.0, 32.0, 5.0, -45.5f, 12.25f)
                ),
                List.of(
                        new EscrowItem("player-1", "base64-lot-stack"),
                        new EscrowItem("player-2", "base64-order-stack")
                ),
                List.of(
                        new EscrowItem("player-3", "base64-pending-return")
                ),
                List.of("player-4", "player-5")
        );
```

- [ ] **Step 3: Update GatheringService.persist() and add the banned field**

In `GatheringService.java`, add the field near the other maps (after `crashHomes`):
```java
    private final Set<UUID> bannedFromNext = new HashSet<>();
```
Update `persist()` (the last method) to write the banned ids:
```java
        List<String> banned = new ArrayList<>();
        bannedFromNext.forEach(id -> banned.add(id.toString()));
        snapshotRepository.save(new Snapshot(nextGatheringMillis, homes, escrowItems, returns, banned));
```

- [ ] **Step 4: Load the banned set on startup**

In `initializeFromSnapshot()`, after `nextGatheringMillis = snapshot.nextGatheringEpochMillis();`, add:
```java
        if (snapshot.bannedFromNext() != null) {
            snapshot.bannedFromNext().forEach(id -> bannedFromNext.add(UUID.fromString(id)));
        }
```

- [ ] **Step 5: Run the tests**

Run: `mvn test -Dtest=GatheringSnapshotRepositoryTest`
Expected: PASS (4 tests). Then `mvn clean package -q` → BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/market/GatheringSnapshotRepository.java src/test/java/me/vangoo/infrastructure/market/GatheringSnapshotRepositoryTest.java src/main/java/me/vangoo/application/services/GatheringService.java
git commit -m "feat(market): persist next-gathering ban set in snapshot

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Per-minute countdown during the join window

**Files:**
- Modify: `src/main/java/me/vangoo/application/services/GatheringService.java` (announce/open)

Note: `schedule(Runnable, long delayTicks)` adds a one-shot task to `phaseTasks`. For a repeating task, use `Bukkit.getScheduler().runTaskTimer(...)` and add the returned `BukkitTask` to `phaseTasks` so `cancelPhaseTasks()` stops it.

**Interfaces:**
- Consumes: existing `joined` (Set<UUID>), `config.joinWindowMinutes()`, `phaseTasks`, `notify(UUID, String)`.

- [ ] **Step 1: Track the open time and start a repeating countdown in `announce()`**

In `GatheringService.java`, add a field near `nextGatheringMillis`:
```java
    private long openAtMillis;
```
In `announce()`, replace the final `schedule(() -> open(), config.joinWindowMinutes() * 60L * 20L);` with:
```java
        long joinWindowTicks = config.joinWindowMinutes() * 60L * 20L;
        openAtMillis = System.currentTimeMillis() + config.joinWindowMinutes() * 60L * 1000L;
        phaseTasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::announceCountdown, 20L * 60L, 20L * 60L));
        schedule(() -> open(), joinWindowTicks);
```

- [ ] **Step 2: Add the countdown method**

Add this private method to `GatheringService` (near `announce`):
```java
    private void announceCountdown() {
        if (phase != GatheringPhase.ANNOUNCED) {
            return;
        }
        long remainingMillis = openAtMillis - System.currentTimeMillis();
        long minutes = Math.round(remainingMillis / 60000.0);
        String when = minutes <= 1 ? "менше ніж за хвилину" : "за " + minutes + " хв";
        for (UUID id : joined) {
            notify(id, PREFIX + ChatColor.LIGHT_PURPLE + "Збори розпочнуться " + when + ".");
        }
    }
```

- [ ] **Step 3: Build**

Run: `mvn clean package -q`
Expected: BUILD SUCCESS. (The repeating task is cancelled by `cancelPhaseTasks()`, already called in `open()` via... verify: `open()` sets phase then schedules; `cancelPhaseTasks()` runs in `close()` and `forceCloseIfActive()`. The countdown self-exits when phase != ANNOUNCED, and `open()` will proceed; add an explicit `cancelPhaseTasks()` is not needed because `open()` keeps scheduling on the same list. To stop the timer promptly when OPEN begins, add `cancelPhaseTasks();` as the FIRST line inside `open()` after the `if (phase != ANNOUNCED) return;` guard.)

- [ ] **Step 4: Stop the countdown when the market opens**

In `open()`, immediately after the guard `if (phase != GatheringPhase.ANNOUNCED) { return; }`, add:
```java
        cancelPhaseTasks();
```
(The join-window `open` task has already fired; this clears the leftover countdown timer before OPEN schedules its own tasks.)

- [ ] **Step 5: Build again**

Run: `mvn clean package -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: In-server verification**

Deploy the JAR, `/gathering start`, join with a Beyonder, wait 60s. Expected: a chat line "Збори розпочнуться за N хв." each minute; it stops when the market opens.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/me/vangoo/application/services/GatheringService.java
git commit -m "feat(market): announce per-minute countdown to gathering open

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: Punishment system — ability guard, damage violation, ban

**Files:**
- Create: `src/main/java/me/vangoo/application/services/GatheringAbilityGuard.java`
- Modify: `src/main/java/me/vangoo/application/services/AbilityExecutor.java`
- Modify: `src/main/java/me/vangoo/application/services/GatheringService.java`
- Modify: `src/main/java/me/vangoo/presentation/listeners/GatheringListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`

**Interfaces:**
- Consumes: `GatheringConduct.recordViolation(UUID) : Sanction` (Task 1); `bannedFromNext` set, `skipThisRound`, `handleQuit`-style teardown (Task 6).
- Produces: `GatheringAbilityGuard.interceptAbility(UUID) : boolean`; `GatheringService.recordViolation(Player)`; `AbilityExecutor.setGatheringAbilityGuard(GatheringAbilityGuard)`.

- [ ] **Step 1: Create the guard interface**

`src/main/java/me/vangoo/application/services/GatheringAbilityGuard.java`:
```java
package me.vangoo.application.services;

import java.util.UUID;

/**
 * Дозволяє AbilityExecutor заблокувати каст здібності учаснику зборів,
 * не залежачи напряму від GatheringService. Реалізація фіксує порушення.
 */
public interface GatheringAbilityGuard {
    /** true → здібність заблокувати (гравець зараз на зборах); фіксує порушення. */
    boolean interceptAbility(UUID playerId);
}
```

- [ ] **Step 2: Consult the guard in AbilityExecutor**

In `AbilityExecutor.java`, add a nullable field and setter:
```java
    private GatheringAbilityGuard gatheringAbilityGuard;

    public void setGatheringAbilityGuard(GatheringAbilityGuard guard) {
        this.gatheringAbilityGuard = guard;
    }
```
In `execute(...)`, right after the `if (player == null) { return AbilityResult.failure("Player not found"); }` block, add:
```java
        if (gatheringAbilityGuard != null
                && gatheringAbilityGuard.interceptAbility(beyonder.getPlayerId())) {
            return AbilityResult.failure("Тут ваші сили мовчать.");
        }
```

- [ ] **Step 3: Implement the guard, violations, and ban in GatheringService**

In `GatheringService.java`:
- Add `implements GatheringAbilityGuard` to the class declaration:
  ```java
  public class GatheringService implements GatheringAbilityGuard {
  ```
- Add fields (near `bannedFromNext`):
  ```java
  private final GatheringConduct conduct = new GatheringConduct();
  private final Map<UUID, Long> lastViolationAt = new HashMap<>();
  private final Set<UUID> skipThisRound = new HashSet<>();
  private static final long VIOLATION_DEBOUNCE_MILLIS = 2000L;
  ```
- Add the import:
  ```java
  import me.vangoo.domain.market.GatheringConduct;
  ```
- Add the guard method:
  ```java
  @Override
  public boolean interceptAbility(UUID playerId) {
      if (!isOpenParticipant(playerId)) {
          return false;
      }
      Player player = Bukkit.getPlayer(playerId);
      if (player != null) {
          recordViolation(player);
      }
      return true;
  }
  ```
- Add the violation handler:
  ```java
  /** Порушення спокою (удар / спроба здібності): попередження, тоді кік + бан. */
  public void recordViolation(Player player) {
      UUID id = player.getUniqueId();
      long now = System.currentTimeMillis();
      Long last = lastViolationAt.get(id);
      if (last != null && now - last < VIOLATION_DEBOUNCE_MILLIS) {
          return;
      }
      lastViolationAt.put(id, now);
      switch (conduct.recordViolation(id)) {
          case WARN -> player.sendMessage(PREFIX + ChatColor.RED
                  + "⚠ Насильство тут не терплять. Наступного разу — виганяю.");
          case KICK -> {
              player.sendMessage(PREFIX + ChatColor.DARK_RED
                      + "Вас виганяють зі Зборів. На наступний збір вас не пустять.");
              bannedFromNext.add(id);
              expel(player);
              persist();
          }
      }
  }
  ```
- Add the `expel` helper (mirrors `handleQuit` teardown but for an online player):
  ```java
  private void expel(Player player) {
      UUID id = player.getUniqueId();
      if (session != null) {
          for (NegotiationView view : session.negotiationsOf(id)) {
              try {
                  releaseEscrow(session.withdraw(id, view.negotiationId()));
              } catch (MarketException ignored) {
              }
          }
      }
      openParticipantIds.remove(id);
      frozen.remove(id);
      briefed.remove(id);
      anonymizer.unmask(player);
      Location home = returnLocations.remove(id);
      player.teleport(home != null ? home : Bukkit.getWorlds().get(0).getSpawnLocation());
  }
  ```
  Note: `frozen` and `briefed` sets are introduced in Task 9; add the two fields now (this task removes an expelled player from both):
  ```java
  private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
  private final Set<UUID> briefed = new HashSet<>();
  ```

- [ ] **Step 3b: Make the `isOpenParticipant(Player)` overload honor expulsion**

`expel` removes the player from `openParticipantIds`, but the `Player` overload of `isOpenParticipant` only checks `session.isParticipant(...)`, so an expelled-but-online player could still trade via the menu. Tighten it to also require membership in the authoritative `openParticipantIds` set:
```java
    public boolean isOpenParticipant(Player player) {
        return phase == GatheringPhase.OPEN && session != null
                && openParticipantIds.contains(player.getUniqueId())
                && session.isParticipant(player.getUniqueId());
    }
```
(`openParticipantIds` is populated for every attendee in `open()` and cleared in `close()`, so this does not change behavior for normal participants.)

- [ ] **Step 4: Gate `join()` on the ban, and rotate the ban in `announce()`**

In `announce()`, after `joined.clear();`, add:
```java
        skipThisRound.clear();
        skipThisRound.addAll(bannedFromNext);
        bannedFromNext.clear();
```
In `join()`, after the phase check and before the beyonder check, add:
```java
        if (skipThisRound.contains(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Вас не пустять на цей збір — минулого разу ви порушили спокій.");
            return false;
        }
```

- [ ] **Step 5: Add the damage-violation listener**

In `GatheringListener.java`, add the import and handler. Add imports:
```java
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
```
Add the handler:
```java
    @EventHandler(ignoreCancelled = true)
    public void onVenueDamage(EntityDamageByEntityEvent event) {
        if (!venueProvider.isVenueWorld(event.getEntity().getWorld())) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Entity victim = event.getEntity();
        if (!(victim instanceof Player) || victim.hasMetadata("NPC")) {
            return; // NPC-Посередник або не гравець — не рахуємо
        }
        if (gatheringService.isOpenParticipant(attacker)) {
            gatheringService.recordViolation(attacker);
        }
    }
```
Note: the damage is intentionally NOT cancelled (design choice — the hit lands).

- [ ] **Step 6: Wire the guard setter in ServiceContainer**

In `ServiceContainer.java`, immediately after the `gatheringService` is constructed (line ~260, inside `initializeApplicationServices`), add:
```java
        this.abilityExecutor.setGatheringAbilityGuard(gatheringService);
```

- [ ] **Step 7: Build**

Run: `mvn clean package -q`
Expected: BUILD SUCCESS.

- [ ] **Step 8: In-server verification**

Deploy, run a gathering with two players. Player A hits player B → warning; hit again → kicked home. Try to cast an ability at the gathering → "Тут ваші сили мовчать." + counts as a violation. After a kick, `/gathering stop` then `/gathering start` again → the kicked player cannot `join` this next gathering; the one after that they can.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/me/vangoo/application/services/GatheringAbilityGuard.java src/main/java/me/vangoo/application/services/AbilityExecutor.java src/main/java/me/vangoo/application/services/GatheringService.java src/main/java/me/vangoo/presentation/listeners/GatheringListener.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(market): punish violence and ability casts at the gathering

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: Lectern menu entry, freeze, and typewriter briefing

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/market/GatheringVenueProvider.java`
- Create: `src/main/java/me/vangoo/infrastructure/market/OrganizerBriefing.java`
- Modify: `src/main/java/me/vangoo/application/services/GatheringService.java`
- Modify: `src/main/java/me/vangoo/presentation/listeners/GatheringListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`

**Interfaces:**
- Consumes: `frozen`/`briefed` sets, `openParticipantIds`, `returnLocations` (Tasks 6/8); `MarketMenu.openMain(Player)`.
- Produces: `OrganizerBriefing.start(...)` / `cancel()`; `GatheringService.isFrozen(UUID)`; `GatheringService.hasBeenBriefed(UUID)`.

- [ ] **Step 1: Place a permanent lectern in the venue**

In `GatheringVenueProvider.java`, at the end of `buildPlatformIfMissing(World world)`, add:
```java
        // Кафедра ринку: правий клік відкриває меню (обробляє GatheringListener)
        world.getBlockAt(1, PLATFORM_Y + 1, 0).setType(Material.LECTERN);
```

- [ ] **Step 2: Create the typewriter briefing**

`src/main/java/me/vangoo/infrastructure/market/OrganizerBriefing.java`:
```java
package me.vangoo.infrastructure.market;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Доповідь Посередника на відкритті збору: репліки друкуються по літері в
 * action bar усіх учасників зі звуком друкарської машинки. Самотіковий об'єкт —
 * володіє власним BukkitTask; onComplete кличеться в головному потоці по завершенні.
 */
public class OrganizerBriefing {

    private static final int CHAR_TICKS = 2;      // тіків на символ
    private static final int LINE_HOLD_TICKS = 40; // утримання повного рядка

    // Сценарій: де ви → правила → принципи торгівлі
    private static final List<String> SCRIPT = List.of(
            "Ласкаво прошу в місце, якого немає на жодній мапі.",
            "Тут ніхто не має імені. Вас бачать лише як Незнайомця.",
            "Правила прості: тут не місце насильству.",
            "Здійняти руку чи силу на іншого — виженуть, а тоді й не пустять.",
            "Ваші здібності тут мовчать. Не намагайтеся їх пробудити.",
            "Торгуйте так: виставте річ із руки як лот за свою ціну.",
            "Або замовте потрібне — і продавці дадуть свої пропозиції.",
            "Непотріб принесіть мені, Посереднику — скуплю за монету.",
            "З кожної угоди я беру свою частку. Така ціна безпеки.",
            "Кафедра поруч відкриє вам торгове меню. Успіху."
    );

    private final Plugin plugin;
    private final Supplier<List<Player>> audience;
    private final Runnable onComplete;

    private BukkitTask task;
    private int lineIndex;
    private int charIndex;
    private int holdTicks;

    public OrganizerBriefing(Plugin plugin, Supplier<List<Player>> audience, Runnable onComplete) {
        this.plugin = plugin;
        this.audience = audience;
        this.onComplete = onComplete;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, CHAR_TICKS);
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (lineIndex >= SCRIPT.size()) {
            finish();
            return;
        }
        String line = SCRIPT.get(lineIndex);
        if (charIndex < line.length()) {
            charIndex++;
            char revealed = line.charAt(charIndex - 1);
            String shown = ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                    + "Посередник: " + ChatColor.WHITE + line.substring(0, charIndex);
            for (Player p : audience.get()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(shown));
                if (revealed != ' ') {
                    p.playSound(p.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.3f,
                            1.4f + (float) (Math.random() * 0.3));
                }
            }
        } else if (holdTicks < LINE_HOLD_TICKS) {
            // Утримуємо повний рядок, перепосилаючи action bar щоб не згас
            holdTicks++;
            String shown = ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                    + "Посередник: " + ChatColor.WHITE + line;
            for (Player p : audience.get()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(shown));
            }
        } else {
            lineIndex++;
            charIndex = 0;
            holdTicks = 0;
        }
    }

    private void finish() {
        cancel();
        onComplete.run();
    }
}
```

- [ ] **Step 3: Freeze players and run the briefing in GatheringService.open()**

In `GatheringService.java`:
- Add imports:
  ```java
  import me.vangoo.infrastructure.market.OrganizerBriefing;
  ```
- Add a field near `phaseTasks`:
  ```java
  private OrganizerBriefing briefing;
  ```
  (`frozen` and `briefed` sets were added in Task 8 Step 3; if Task 8 is not yet done, add them here.)
- In `open()`, inside the attendee loop where each player is teleported/masked, add the player to `frozen`:
  ```java
          for (Player player : attendees) {
              session.registerParticipant(player.getUniqueId());
              openParticipantIds.add(player.getUniqueId());
              returnLocations.put(player.getUniqueId(), player.getLocation());
              player.teleport(venue);
              anonymizer.mask(player, session.aliasOf(player.getUniqueId()));
              frozen.add(player.getUniqueId());
          }
  ```
- REMOVE the three `broadcastToParticipants(...)` intro lines (the "Посередник: Вітаю…", "Посередник: Я ручаюся…", and "Збір триватиме…" lines) from `open()` — the briefing replaces the intro. Keep the duration/close scheduling.
- After `organizerNpc.spawn(venue);`, start the briefing instead of the removed broadcasts:
  ```java
          briefing = new OrganizerBriefing(plugin, this::frozenAudience, this::onBriefingComplete);
          briefing.start();
  ```
- Add the helper methods:
  ```java
  private List<Player> frozenAudience() {
      List<Player> players = new ArrayList<>();
      for (UUID id : openParticipantIds) {
          Player p = Bukkit.getPlayer(id);
          if (p != null && p.isOnline()) {
              players.add(p);
          }
      }
      return players;
  }

  private void onBriefingComplete() {
      frozen.clear();
      briefed.addAll(openParticipantIds);
      broadcastToParticipants(PREFIX + ChatColor.GREEN
              + "Тепер ви вільні. Торгуйте — кафедра поруч.");
  }

  public boolean isFrozen(UUID playerId) {
      return frozen.contains(playerId);
  }

  public boolean hasBeenBriefed(UUID playerId) {
      return briefed.contains(playerId);
  }
  ```

- [ ] **Step 4: Cancel the briefing and clear state on close**

In `close()`, before `session = null;`, add:
```java
        if (briefing != null) {
            briefing.cancel();
            briefing = null;
        }
        frozen.clear();
        briefed.clear();
        conduct.reset();
        lastViolationAt.clear();
```
Also add the same briefing-cancel guard to `forceCloseIfActive()` in the ANNOUNCED branch is not needed (no briefing yet), but the OPEN branch calls `close()`, which handles it.

- [ ] **Step 5: Cancel freeze movement and handle the lectern in GatheringListener**

In `GatheringListener.java`:
- Add imports:
  ```java
  import org.bukkit.Material;
  import org.bukkit.event.block.Action;
  import org.bukkit.event.player.PlayerInteractEvent;
  import org.bukkit.event.player.PlayerMoveEvent;
  import me.vangoo.infrastructure.ui.MarketMenu;
  ```
- Add a `MarketMenu` field and constructor param:
  ```java
      private final MarketMenu marketMenu;
  ```
  Extend the constructor:
  ```java
      public GatheringListener(Plugin plugin, GatheringService gatheringService,
                               GatheringVenueProvider venueProvider, MarketMenu marketMenu) {
          this.plugin = plugin;
          this.gatheringService = gatheringService;
          this.venueProvider = venueProvider;
          this.marketMenu = marketMenu;
      }
  ```
- Add the move-freeze handler (cancels positional movement, allows head rotation):
  ```java
      @EventHandler(ignoreCancelled = true)
      public void onFrozenMove(PlayerMoveEvent event) {
          if (!gatheringService.isFrozen(event.getPlayer().getUniqueId())) {
              return;
          }
          if (event.getTo() != null && (event.getFrom().getBlockX() != event.getTo().getBlockX()
                  || event.getFrom().getBlockY() != event.getTo().getBlockY()
                  || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) {
              event.setCancelled(true);
          }
      }
  ```
- Add the lectern interact handler:
  ```java
      @EventHandler
      public void onLecternClick(PlayerInteractEvent event) {
          if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
              return;
          }
          if (event.getClickedBlock().getType() != Material.LECTERN
                  || !venueProvider.isVenueWorld(event.getClickedBlock().getWorld())) {
              return;
          }
          event.setCancelled(true); // глушить дефолтне GUI кафедри
          Player player = event.getPlayer();
          if (gatheringService.isOpenParticipant(player)) {
              marketMenu.openMain(player);
          }
      }
  ```

- [ ] **Step 6: Pass MarketMenu into the listener in ServiceContainer**

In `ServiceContainer.java`, update the `gatheringListener` construction (line ~327-328):
```java
        this.gatheringListener = new me.vangoo.presentation.listeners.GatheringListener(
                plugin, gatheringService, gatheringVenueProvider, marketMenu);
```

- [ ] **Step 7: Build**

Run: `mvn clean package -q`
Expected: BUILD SUCCESS.

- [ ] **Step 8: In-server verification**

Deploy, run a gathering. On open: players are teleported, frozen in place (cannot walk, can look around), and the Посередник's speech types out letter-by-letter in the action bar with a soft click per letter. After the script, a chat line frees them. Right-clicking the lectern opens the market menu; it does not open the vanilla lectern book.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/market/GatheringVenueProvider.java src/main/java/me/vangoo/infrastructure/market/OrganizerBriefing.java src/main/java/me/vangoo/application/services/GatheringService.java src/main/java/me/vangoo/presentation/listeners/GatheringListener.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(market): typewriter briefing with freeze and lectern menu entry

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: "Trade principles" menu tab

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java`

**Interfaces:**
- Consumes: `GatheringService.hasBeenBriefed(UUID) : boolean` (Task 9).

- [ ] **Step 1: Add a principles button to the main menu**

In `MarketMenu.openMain`, add a button before `gui.open(player);`:
```java
        gui.setItem(1, 5, new GuiItem(button(Material.KNOWLEDGE_BOOK,
                        ChatColor.LIGHT_PURPLE + "Принципи торгівлі",
                        "Правила й порядок торгів (як розповів Посередник)"),
                e -> runSynced(player, () -> openPrinciples(player))));
```

- [ ] **Step 2: Add the principles page**

Add this method to `MarketMenu`:
```java
    private void openPrinciples(Player player) {
        if (!gatheringService.hasBeenBriefed(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "[Збори] Спершу вислухайте Посередника.");
            runSynced(player, () -> openMain(player));
            return;
        }
        Gui gui = Gui.gui()
                .title(Component.text("🕯 Принципи торгівлі").color(NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .rows(6)
                .disableAllInteractions()
                .create();
        ItemStack scroll = button(Material.WRITTEN_BOOK, ChatColor.GOLD + "Порядок торгів");
        appendLore(scroll, List.of(
                "",
                ChatColor.WHITE + "• Лот: виставте річ із ГОЛОВНОЇ РУКИ за свою ціну.",
                ChatColor.WHITE + "• Замовлення: попросіть потрібне — продавці дадуть оферти.",
                ChatColor.WHITE + "• Торг: приймайте, давайте зустрічну ціну або відмовляйтесь.",
                ChatColor.WHITE + "• Скупка: принесіть непотріб Посереднику за монету.",
                ChatColor.WHITE + "• Комісія: з кожної угоди Посередник бере частку.",
                ChatColor.WHITE + "• Анонімність: усі бачать лише «Незнайомець №N».",
                "",
                ChatColor.RED + "• Насильство й здібності тут заборонені."));
        gui.setItem(3, 5, new GuiItem(scroll, e -> e.setCancelled(true)));
        gui.setItem(6, 1, new GuiItem(button(Material.BARRIER, ChatColor.GRAY + "◄ Назад"),
                e -> Bukkit.getScheduler().runTask(plugin, () -> openMain(player))));
        gui.open(player);
    }
```

- [ ] **Step 3: Build**

Run: `mvn clean package -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: In-server verification**

Open the market menu after the briefing → the "Принципи торгівлі" button shows the rules page with a working "Назад" button. If a player somehow opens the menu before being briefed (e.g. via `/gathering menu` during the speech), the button denies with the hint.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java
git commit -m "feat(market): add trade principles tab to market menu

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 11: Update the market-gathering rule doc

**Files:**
- Modify: `.claude/rules/market-gathering.md`

- [ ] **Step 1: Document the new mechanics**

Add a new subsection under "## Механізм" (or a new section) covering:
- Conduct/ban: `GatheringConduct` (domain, WARN→KICK), debounce in `GatheringService`, `GatheringAbilityGuard` consulted by `AbilityExecutor` (setter-injected), `bannedFromNext` persisted in `Snapshot` (null-default for old files), `skipThisRound` rotation in `announce()`.
- Opening cinematic: `OrganizerBriefing` (infrastructure.market) types the script into action bar with a per-letter sound; players are `frozen` (PlayerMoveEvent cancel in `GatheringListener`) until it finishes, then `briefed`.
- Lectern: permanent `LECTERN` at venue `(1,65,0)`, right-click opens `MarketMenu` (vanilla book GUI suppressed); `/gathering menu` stays as fallback.
- Names: `MarketItemNamer` resolves `itemKey → Ukrainian name` for order/negotiation displays.
- Note the snapshot schema change (new `bannedFromNext` field, backward compatible).

Keep it mechanism-specific (how to use it correctly), not a diff narrative.

- [ ] **Step 2: Commit**

```bash
git add .claude/rules/market-gathering.md
git commit -m "docs(market): document conduct ban, briefing, and lectern entry

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification

- [ ] Full build + unit tests: `mvn clean package` → BUILD SUCCESS, all tests green (including `GatheringConductTest`, `MarketItemNamerTest`, `GatheringSnapshotRepositoryTest`, `ArchitectureTest`).
- [ ] In-server end-to-end: start a gathering with 2+ Beyonders and confirm, in order — per-minute countdown → freeze + typewriter briefing → free movement → lectern opens menu → readable item names in orders → back buttons work → principles tab readable → hitting/ability-casting warns then kicks + bans from the next gathering → buyback pays the raised prices for a characteristic and a recipe book.
```
