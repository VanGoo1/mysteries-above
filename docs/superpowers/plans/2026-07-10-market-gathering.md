# Підпільний ринок (Збори Потойбічних) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Щотижнева анонімна подія-ринок: валюта (фунт=20 коппетів), телепорт у світ-заглушку, NPC-організатор зі скупкою, лоти з вільною ціною, замовлення з торгом, краш-безпечне закриття з поверненням усього.

**Architecture:** Чисте ядро `domain.market` (гроші, сесія ринку, фази — юніт-тести) + оркестратор `GatheringService` в `application` + адаптери в `infrastructure` (кодек монет, світ, анонімізація, снепшот, Citizens-NPC, triumph-gui меню). Спек: `docs/superpowers/specs/2026-07-10-ingredient-economy-market-design.md`.

**Tech Stack:** Java 21, Paper API 1.21 (provided), Citizens (depend), triumph-gui (shaded), Gson, JUnit 5 + ArchUnit.

## Global Constraints

- Увесь user-facing текст — українською; ідентифікатори/логи — англійською (`.claude/rules/localization.md`).
- Коміти — Conventional Commits англійською (`.claude/rules/commit-messages.md`).
- `domain.market` — нуль Bukkit/triumph/kyori імпортів; додається в `PURE_DOMAIN` `ArchitectureTest`.
- `mvn` НЕ в PATH. Запуск тестів у PowerShell:
  ```powershell
  $mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName
  & $mvn -o test "-Dtest=PoundMoneyTest"
  ```
- Кожна нова команда — оголошення в `plugin.yml` + `setExecutor` у `MysteriesAbovePlugin.registerCommands()`; кожен listener — реєстрація в `registerEvents()`; кожен scheduler — `start()`/`stop()` у `ServiceContainer`.
- Вся грошова математика — в коппетах (`int`); 1 фунт = 20 коппетів.
- Жоден предмет/монета гравця не губиться: кожен ескроу має рівно один вихід (угода або повернення).

---

### Task 1: Домен — `PoundMoney` і `CoinChange` (гроші й розмін)

**Files:**
- Create: `src/main/java/me/vangoo/domain/market/PoundMoney.java`
- Create: `src/main/java/me/vangoo/domain/market/CoinChange.java`
- Modify: `src/test/java/me/vangoo/architecture/ArchitectureTest.java:22-29` (додати `me.vangoo.domain.market` у `PURE_DOMAIN`)
- Test: `src/test/java/me/vangoo/domain/market/PoundMoneyTest.java`

**Interfaces:**
- Produces: `PoundMoney(int coppets)` record — `COPPETS_PER_POUND=20`, `of(int pounds,int coppets)`, `ofCoppets(int)`, `wholePounds()`, `remainderCoppets()`, `plus(PoundMoney)`, `minus(PoundMoney)`, `commission(double rate)`, `format()`, `isZero()`.
- Produces: `CoinChange(int takePounds,int takeCoppets,int changeCoppets)` record — `static Optional<CoinChange> make(int poundsHeld,int coppetsHeld,PoundMoney price)`, `paidCoppets()`.

- [ ] **Step 1: Написати падаючий тест**

```java
package me.vangoo.domain.market;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PoundMoneyTest {

    // --- PoundMoney ---

    @Test
    void convertsPoundsAndCoppets() {
        PoundMoney m = PoundMoney.of(2, 15);
        assertEquals(55, m.coppets());
        assertEquals(2, m.wholePounds());
        assertEquals(15, m.remainderCoppets());
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> PoundMoney.ofCoppets(-1));
        assertThrows(IllegalArgumentException.class, () -> PoundMoney.of(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> PoundMoney.of(0, -5));
        assertThrows(IllegalArgumentException.class,
                () -> PoundMoney.of(1, 0).minus(PoundMoney.of(2, 0)));
    }

    @Test
    void formatsUkrainian() {
        assertEquals("2 ф 15 к", PoundMoney.of(2, 15).format());
        assertEquals("2 ф", PoundMoney.of(2, 0).format());
        assertEquals("15 к", PoundMoney.of(0, 15).format());
        assertEquals("0 к", PoundMoney.ofCoppets(0).format());
    }

    @Test
    void commissionIsCeiled() {
        // 10% від 55 к = 5.5 → 6 к
        assertEquals(6, PoundMoney.ofCoppets(55).commission(0.10).coppets());
        assertEquals(0, PoundMoney.ofCoppets(0).commission(0.10).coppets());
    }

    // --- CoinChange (жадібний розмін: спершу коппети, потім фунти) ---

    @Test
    void paysExactlyWithCoppetsFirst() {
        // ціна 30 к; у гаманці 2 ф + 12 к → зняти 12 к + 1 ф, здача 2 к
        CoinChange change = CoinChange.make(2, 12, PoundMoney.ofCoppets(30)).orElseThrow();
        assertEquals(1, change.takePounds());
        assertEquals(12, change.takeCoppets());
        assertEquals(2, change.changeCoppets());
        assertEquals(30, change.paidCoppets());
    }

    @Test
    void paysWithoutChangeWhenCoppetsSuffice() {
        CoinChange change = CoinChange.make(5, 30, PoundMoney.ofCoppets(30)).orElseThrow();
        assertEquals(0, change.takePounds());
        assertEquals(30, change.takeCoppets());
        assertEquals(0, change.changeCoppets());
    }

    @Test
    void poundsOnlyWalletGetsChange() {
        // ціна 30 к; лише 2 фунти → зняти 2 ф (40 к), здача 10 к
        CoinChange change = CoinChange.make(2, 0, PoundMoney.ofCoppets(30)).orElseThrow();
        assertEquals(2, change.takePounds());
        assertEquals(0, change.takeCoppets());
        assertEquals(10, change.changeCoppets());
        assertEquals(30, change.paidCoppets());
    }

    @Test
    void insufficientFundsIsEmpty() {
        assertEquals(Optional.empty(), CoinChange.make(1, 9, PoundMoney.ofCoppets(30)));
        assertEquals(Optional.empty(), CoinChange.make(0, 0, PoundMoney.ofCoppets(1)));
    }

    @Test
    void zeroPriceTakesNothing() {
        CoinChange change = CoinChange.make(3, 3, PoundMoney.ofCoppets(0)).orElseThrow();
        assertEquals(0, change.takePounds());
        assertEquals(0, change.takeCoppets());
        assertEquals(0, change.changeCoppets());
    }
}
```

- [ ] **Step 2: Запустити тест — має впасти (класів ще нема, помилка компіляції)**

```powershell
$mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName
& $mvn -o test "-Dtest=PoundMoneyTest"
```
Expected: BUILD FAILURE (compilation error: `PoundMoney` does not exist).

- [ ] **Step 3: Реалізація**

`src/main/java/me/vangoo/domain/market/PoundMoney.java`:
```java
package me.vangoo.domain.market;

/**
 * Грошова сума підпільного ринку. Внутрішнє представлення — коппети.
 * 1 золотий фунт = {@value #COPPETS_PER_POUND} коппетів.
 */
public record PoundMoney(int coppets) {

    public static final int COPPETS_PER_POUND = 20;

    public PoundMoney {
        if (coppets < 0) {
            throw new IllegalArgumentException("Сума не може бути від'ємною: " + coppets);
        }
    }

    public static PoundMoney ofCoppets(int coppets) {
        return new PoundMoney(coppets);
    }

    public static PoundMoney of(int pounds, int coppets) {
        if (pounds < 0 || coppets < 0) {
            throw new IllegalArgumentException("Сума не може бути від'ємною: " + pounds + " ф " + coppets + " к");
        }
        return new PoundMoney(pounds * COPPETS_PER_POUND + coppets);
    }

    public int wholePounds() {
        return coppets / COPPETS_PER_POUND;
    }

    public int remainderCoppets() {
        return coppets % COPPETS_PER_POUND;
    }

    public boolean isZero() {
        return coppets == 0;
    }

    public PoundMoney plus(PoundMoney other) {
        return new PoundMoney(coppets + other.coppets);
    }

    /** Кидає IllegalArgumentException, якщо результат від'ємний (через компактний конструктор). */
    public PoundMoney minus(PoundMoney other) {
        return new PoundMoney(coppets - other.coppets);
    }

    /** Комісія організатора: ceil(coppets × rate). */
    public PoundMoney commission(double rate) {
        return new PoundMoney((int) Math.ceil(coppets * rate));
    }

    /** «2 ф 15 к» / «2 ф» / «15 к» / «0 к». */
    public String format() {
        int p = wholePounds();
        int c = remainderCoppets();
        if (p > 0 && c > 0) return p + " ф " + c + " к";
        if (p > 0) return p + " ф";
        return c + " к";
    }
}
```

`src/main/java/me/vangoo/domain/market/CoinChange.java`:
```java
package me.vangoo.domain.market;

import java.util.Optional;

/**
 * Інструкція оплати фізичними монетами з розміном: скільки фунтів/коппетів зняти
 * і скільки здачі повернути коппетами. Жадібно: спершу коппети, потім фунти.
 */
public record CoinChange(int takePounds, int takeCoppets, int changeCoppets) {

    /** @return empty, якщо в гаманці не вистачає на ціну. */
    public static Optional<CoinChange> make(int poundsHeld, int coppetsHeld, PoundMoney price) {
        if (poundsHeld < 0 || coppetsHeld < 0) {
            throw new IllegalArgumentException("Гаманець не може бути від'ємним");
        }
        int remaining = price.coppets();
        int takeCoppets = Math.min(coppetsHeld, remaining);
        remaining -= takeCoppets;
        int takePounds = (remaining + PoundMoney.COPPETS_PER_POUND - 1) / PoundMoney.COPPETS_PER_POUND;
        if (takePounds > poundsHeld) {
            return Optional.empty();
        }
        int change = takePounds * PoundMoney.COPPETS_PER_POUND - remaining;
        return Optional.of(new CoinChange(takePounds, takeCoppets, change));
    }

    /** Фактично сплачена вартість у коппетах (зняте мінус здача). */
    public int paidCoppets() {
        return takePounds * PoundMoney.COPPETS_PER_POUND + takeCoppets - changeCoppets;
    }
}
```

У `ArchitectureTest` додати до масиву `PURE_DOMAIN` рядок:
```java
            "me.vangoo.domain.forage",
            "me.vangoo.domain.market"
```

- [ ] **Step 4: Запустити тести — мають пройти (і ArchUnit теж)**

```powershell
& $mvn -o test "-Dtest=PoundMoneyTest,ArchitectureTest"
```
Expected: `Tests run: ..., Failures: 0, Errors: 0` → BUILD SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/me/vangoo/domain/market src/test/java/me/vangoo/domain/market src/test/java/me/vangoo/architecture/ArchitectureTest.java
git commit -m "feat(market): pound money and greedy coin change rules"
```

---

### Task 2: Домен — `GatheringPhase` (стейт-машина фаз)

**Files:**
- Create: `src/main/java/me/vangoo/domain/market/GatheringPhase.java`
- Test: `src/test/java/me/vangoo/domain/market/GatheringPhaseTest.java`

**Interfaces:**
- Produces: `enum GatheringPhase { IDLE, ANNOUNCED, OPEN, CLOSING }` з `boolean canTransitionTo(GatheringPhase next)`.

- [ ] **Step 1: Написати падаючий тест**

```java
package me.vangoo.domain.market;

import org.junit.jupiter.api.Test;

import static me.vangoo.domain.market.GatheringPhase.*;
import static org.junit.jupiter.api.Assertions.*;

class GatheringPhaseTest {

    @Test
    void allowsHappyPathCycle() {
        assertTrue(IDLE.canTransitionTo(ANNOUNCED));
        assertTrue(ANNOUNCED.canTransitionTo(OPEN));
        assertTrue(OPEN.canTransitionTo(CLOSING));
        assertTrue(CLOSING.canTransitionTo(IDLE));
    }

    @Test
    void allowsCancellingAnnouncementWhenNobodyJoined() {
        assertTrue(ANNOUNCED.canTransitionTo(IDLE));
    }

    @Test
    void rejectsSkippingPhases() {
        assertFalse(IDLE.canTransitionTo(OPEN));
        assertFalse(IDLE.canTransitionTo(CLOSING));
        assertFalse(ANNOUNCED.canTransitionTo(CLOSING));
        assertFalse(OPEN.canTransitionTo(IDLE));
        assertFalse(OPEN.canTransitionTo(ANNOUNCED));
        assertFalse(CLOSING.canTransitionTo(OPEN));
    }
}
```

- [ ] **Step 2: Запустити — впаде компіляцією**

```powershell
& $mvn -o test "-Dtest=GatheringPhaseTest"
```
Expected: BUILD FAILURE (`GatheringPhase` does not exist).

- [ ] **Step 3: Реалізація**

```java
package me.vangoo.domain.market;

/** Фази події-збору. Єдиний дозволений цикл: IDLE → ANNOUNCED → OPEN → CLOSING → IDLE. */
public enum GatheringPhase {
    IDLE, ANNOUNCED, OPEN, CLOSING;

    public boolean canTransitionTo(GatheringPhase next) {
        return switch (this) {
            case IDLE -> next == ANNOUNCED;
            // ANNOUNCED → IDLE: скасування, якщо ніхто не погодився прийти
            case ANNOUNCED -> next == OPEN || next == IDLE;
            case OPEN -> next == CLOSING;
            case CLOSING -> next == IDLE;
        };
    }
}
```

- [ ] **Step 4: Запустити — пройде**

```powershell
& $mvn -o test "-Dtest=GatheringPhaseTest"
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/me/vangoo/domain/market/GatheringPhase.java src/test/java/me/vangoo/domain/market/GatheringPhaseTest.java
git commit -m "feat(market): gathering phase state machine"
```

---

### Task 3: Домен — `MarketSession`: учасники, псевдоніми, лоти, купівля

**Files:**
- Create: `src/main/java/me/vangoo/domain/market/MarketSession.java`
- Test: `src/test/java/me/vangoo/domain/market/MarketSessionTest.java`

**Interfaces:**
- Consumes: `PoundMoney`, `CoinChange` (Task 1).
- Produces (використовують Task 4, 12, 14):
  - `MarketSession(double commissionRate, Random random)`
  - `int registerParticipant(UUID)` (ідемпотентно), `boolean isParticipant(UUID)`, `String aliasOf(UUID)` → `"Незнайомець №N"`
  - `UUID listLot(UUID sellerId, String itemKey, int amount, PoundMoney price)`
  - `List<Lot> activeLots()`
  - `Settlement buyLot(UUID buyerId, UUID lotId, int buyerPounds, int buyerCoppets)`
  - records: `Lot(UUID lotId, UUID sellerId, String itemKey, int amount, PoundMoney price, boolean sold)`; `Settlement(UUID payerId, UUID payeeId, PoundMoney price, PoundMoney commissionPaid, PoundMoney sellerProceeds, CoinChange payerCharge, UUID escrowRef, String itemKey, int amount)`; `Refund(UUID ownerId, UUID escrowRef)`
  - `MarketSession.MarketException extends RuntimeException` — повідомлення українською, показується гравцеві як є.

- [ ] **Step 1: Написати падаючий тест**

```java
package me.vangoo.domain.market;

import me.vangoo.domain.market.MarketSession.Lot;
import me.vangoo.domain.market.MarketSession.MarketException;
import me.vangoo.domain.market.MarketSession.Settlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketSessionTest {

    private static final double RATE = 0.10;

    private MarketSession session;
    private final UUID seller = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        session = new MarketSession(RATE, new Random(42));
        session.registerParticipant(seller);
        session.registerParticipant(buyer);
    }

    @Test
    void aliasesAreUniqueAndStable() {
        int a = session.registerParticipant(seller);
        int b = session.registerParticipant(buyer);
        assertNotEquals(a, b);
        assertEquals(a, session.registerParticipant(seller)); // ідемпотентно
        assertEquals("Незнайомець №" + a, session.aliasOf(seller));
    }

    @Test
    void listedLotIsVisibleAndAnonymousViewCarriesData() {
        UUID lotId = session.listLot(seller, "custom:stellar_aqua_crystal", 2, PoundMoney.of(1, 5));
        Lot lot = session.activeLots().stream()
                .filter(l -> l.lotId().equals(lotId)).findFirst().orElseThrow();
        assertEquals("custom:stellar_aqua_crystal", lot.itemKey());
        assertEquals(2, lot.amount());
        assertEquals(PoundMoney.of(1, 5), lot.price());
        assertFalse(lot.sold());
    }

    @Test
    void rejectsInvalidListings() {
        assertThrows(MarketException.class,
                () -> session.listLot(seller, "custom:x", 0, PoundMoney.ofCoppets(5)));
        assertThrows(MarketException.class,
                () -> session.listLot(seller, "custom:x", 1, PoundMoney.ofCoppets(0)));
        assertThrows(MarketException.class,
                () -> session.listLot(stranger, "custom:x", 1, PoundMoney.ofCoppets(5)));
    }

    @Test
    void buyLotSettlesWithCommission() {
        UUID lotId = session.listLot(seller, "custom:x", 1, PoundMoney.ofCoppets(30));
        // покупець: 2 фунти, 0 коппетів
        Settlement s = session.buyLot(buyer, lotId, 2, 0);
        assertEquals(buyer, s.payerId());
        assertEquals(seller, s.payeeId());
        assertEquals(30, s.price().coppets());
        assertEquals(3, s.commissionPaid().coppets());     // ceil(30×0.10)
        assertEquals(27, s.sellerProceeds().coppets());    // 30 − 3
        assertEquals(30, s.payerCharge().paidCoppets());
        assertEquals(lotId, s.escrowRef());
        assertTrue(session.activeLots().isEmpty());        // лот зник зі списку
    }

    @Test
    void doubleBuyIsImpossible() {
        UUID lotId = session.listLot(seller, "custom:x", 1, PoundMoney.ofCoppets(10));
        session.buyLot(buyer, lotId, 1, 0);
        assertThrows(MarketException.class, () -> session.buyLot(buyer, lotId, 1, 0));
    }

    @Test
    void cannotBuyOwnLotOrWithoutFunds() {
        UUID lotId = session.listLot(seller, "custom:x", 1, PoundMoney.ofCoppets(30));
        assertThrows(MarketException.class, () -> session.buyLot(seller, lotId, 5, 5));
        assertThrows(MarketException.class, () -> session.buyLot(buyer, lotId, 1, 9)); // 29 к < 30 к
        assertEquals(1, session.activeLots().size()); // невдалі спроби не знімають лот
    }

    @Test
    void nonParticipantCannotBuy() {
        UUID lotId = session.listLot(seller, "custom:x", 1, PoundMoney.ofCoppets(10));
        assertThrows(MarketException.class, () -> session.buyLot(stranger, lotId, 5, 5));
    }
}
```

- [ ] **Step 2: Запустити — впаде компіляцією**

```powershell
& $mvn -o test "-Dtest=MarketSessionTest"
```
Expected: BUILD FAILURE (`MarketSession` does not exist).

- [ ] **Step 3: Реалізація**

`src/main/java/me/vangoo/domain/market/MarketSession.java`:
```java
package me.vangoo.domain.market;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Агрегат одного збору: учасники з псевдонімами, лоти, замовлення, торги, комісія.
 * Чисте правило: жодного Bukkit; предмети представлені itemKey (custom:<id> /
 * characteristic:<pathway>:<seq> / recipe:<pathway>:<seq>), реальні стаки тримає
 * ескроу-сховище в application за escrowRef (lotId / negotiationId).
 */
public final class MarketSession {

    /** Порушення правила ринку; message — українською, показується гравцеві як є. */
    public static class MarketException extends RuntimeException {
        public MarketException(String message) {
            super(message);
        }
    }

    public record Lot(UUID lotId, UUID sellerId, String itemKey, int amount,
                      PoundMoney price, boolean sold) {}

    /** Команда застосунку: хто платить, хто отримує, скільки згорає, який ескроу передати. */
    public record Settlement(UUID payerId, UUID payeeId, PoundMoney price, PoundMoney commissionPaid,
                             PoundMoney sellerProceeds, CoinChange payerCharge,
                             UUID escrowRef, String itemKey, int amount) {}

    /** Команда застосунку: повернути ескроу-стак власникові. */
    public record Refund(UUID ownerId, UUID escrowRef) {}

    private final double commissionRate;
    private final Random random;
    private final Map<UUID, Integer> aliases = new LinkedHashMap<>();
    private final Map<UUID, Lot> lots = new LinkedHashMap<>();
    private boolean closed;

    public MarketSession(double commissionRate, Random random) {
        if (commissionRate < 0.0 || commissionRate >= 1.0) {
            throw new IllegalArgumentException("Commission rate must be in [0,1): " + commissionRate);
        }
        this.commissionRate = commissionRate;
        this.random = random;
    }

    /** Реєструє учасника; повертає його номер-псевдонім (ідемпотентно). */
    public int registerParticipant(UUID playerId) {
        Integer existing = aliases.get(playerId);
        if (existing != null) {
            return existing;
        }
        int alias;
        do {
            alias = 1 + random.nextInt(99);
        } while (aliases.containsValue(alias));
        aliases.put(playerId, alias);
        return alias;
    }

    public boolean isParticipant(UUID playerId) {
        return aliases.containsKey(playerId);
    }

    public String aliasOf(UUID playerId) {
        Integer alias = aliases.get(playerId);
        if (alias == null) {
            throw new MarketException("Ви не учасник збору");
        }
        return "Незнайомець №" + alias;
    }

    public UUID listLot(UUID sellerId, String itemKey, int amount, PoundMoney price) {
        ensureOpen();
        ensureParticipant(sellerId);
        if (amount <= 0) {
            throw new MarketException("Кількість має бути додатною");
        }
        if (price.isZero()) {
            throw new MarketException("Ціна має бути більшою за нуль");
        }
        UUID lotId = UUID.randomUUID();
        lots.put(lotId, new Lot(lotId, sellerId, itemKey, amount, price, false));
        return lotId;
    }

    public List<Lot> activeLots() {
        List<Lot> active = new ArrayList<>();
        for (Lot lot : lots.values()) {
            if (!lot.sold()) {
                active.add(lot);
            }
        }
        return active;
    }

    public Settlement buyLot(UUID buyerId, UUID lotId, int buyerPounds, int buyerCoppets) {
        ensureOpen();
        ensureParticipant(buyerId);
        Lot lot = lots.get(lotId);
        if (lot == null || lot.sold()) {
            throw new MarketException("Лот уже недоступний");
        }
        if (lot.sellerId().equals(buyerId)) {
            throw new MarketException("Це ваш власний лот");
        }
        CoinChange charge = CoinChange.make(buyerPounds, buyerCoppets, lot.price())
                .orElseThrow(() -> new MarketException("Недостатньо монет: потрібно " + lot.price().format()));
        lots.put(lotId, new Lot(lot.lotId(), lot.sellerId(), lot.itemKey(), lot.amount(), lot.price(), true));
        PoundMoney commission = lot.price().commission(commissionRate);
        return new Settlement(buyerId, lot.sellerId(), lot.price(), commission,
                lot.price().minus(commission), charge, lotId, lot.itemKey(), lot.amount());
    }

    private void ensureOpen() {
        if (closed) {
            throw new MarketException("Ринок уже закрито");
        }
    }

    private void ensureParticipant(UUID playerId) {
        if (!aliases.containsKey(playerId)) {
            throw new MarketException("Ви не учасник збору");
        }
    }
}
```
(Поле `closed` поки що ніколи не стає true — метод `close()` додає Task 4.)

- [ ] **Step 4: Запустити — пройде**

```powershell
& $mvn -o test "-Dtest=MarketSessionTest"
```
Expected: BUILD SUCCESS, Failures: 0.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/me/vangoo/domain/market/MarketSession.java src/test/java/me/vangoo/domain/market/MarketSessionTest.java
git commit -m "feat(market): market session with anonymous lots and commission"
```

---

### Task 4: Домен — `MarketSession`: замовлення, торг, закриття, інваріант збереження

**Files:**
- Modify: `src/main/java/me/vangoo/domain/market/MarketSession.java`
- Test: `src/test/java/me/vangoo/domain/market/MarketNegotiationTest.java`

**Interfaces:**
- Consumes: усе з Task 3.
- Produces (використовують Task 12, 14):
  - `UUID placeOrder(UUID buyerId, String itemKey, int amount, Set<String> allowedKeys)`
  - `List<BuyOrder> openOrders()`; record `BuyOrder(UUID orderId, UUID buyerId, String itemKey, int amount, boolean fulfilled)`
  - `UUID offerOnOrder(UUID sellerId, UUID orderId, PoundMoney price)` — ескроу предмета робить застосунок ПЕРЕД викликом, ключем є повернутий negotiationId
  - `void counter(UUID actorId, UUID negotiationId, PoundMoney newPrice)`
  - `AcceptResult accept(UUID actorId, UUID negotiationId, int buyerPounds, int buyerCoppets)`; record `AcceptResult(Settlement settlement, List<Refund> releasedEscrows)`
  - `Refund withdraw(UUID actorId, UUID negotiationId)`
  - `List<NegotiationView> negotiationsOf(UUID playerId)`; record `NegotiationView(UUID negotiationId, UUID orderId, UUID sellerId, UUID buyerId, PoundMoney currentPrice, UUID turnOf, NegotiationState state, String itemKey, int amount)`; `enum NegotiationState { OPEN, ACCEPTED, WITHDRAWN }`
  - `List<Refund> close()` — непродані лоти + відкриті торги → повернення.

- [ ] **Step 1: Написати падаючий тест**

```java
package me.vangoo.domain.market;

import me.vangoo.domain.market.MarketSession.AcceptResult;
import me.vangoo.domain.market.MarketSession.MarketException;
import me.vangoo.domain.market.MarketSession.Refund;
import me.vangoo.domain.market.MarketSession.Settlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketNegotiationTest {

    private static final Set<String> KNOWN = Set.of("custom:dimensional_wanderer_eye", "custom:crimson_star");

    private MarketSession session;
    private final UUID buyer = UUID.randomUUID();
    private final UUID sellerA = UUID.randomUUID();
    private final UUID sellerB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        session = new MarketSession(0.10, new Random(7));
        session.registerParticipant(buyer);
        session.registerParticipant(sellerA);
        session.registerParticipant(sellerB);
    }

    @Test
    void orderAllowsOnlyKnownIngredients() {
        assertThrows(MarketException.class,
                () -> session.placeOrder(buyer, "custom:unknown_root", 1, KNOWN));
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 2, KNOWN);
        assertEquals(1, session.openOrders().size());
        assertEquals(orderId, session.openOrders().get(0).orderId());
    }

    @Test
    void turnAlternatesAndOnlyReceiverAccepts() {
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
        UUID negId = session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(20));
        // Продавець назвав ціну → хід покупця; продавець прийняти не може
        assertThrows(MarketException.class, () -> session.accept(sellerA, negId, 99, 99));
        // Покупець дає зустрічну → хід продавця; покупець прийняти не може
        session.counter(buyer, negId, PoundMoney.ofCoppets(14));
        assertThrows(MarketException.class, () -> session.accept(buyer, negId, 99, 99));
        assertThrows(MarketException.class, () -> session.counter(buyer, negId, PoundMoney.ofCoppets(1)));
        // Продавець приймає зустрічну; платить ЗАВЖДИ покупець
        AcceptResult result = session.accept(sellerA, negId, 1, 0); // гаманець ПОКУПЦЯ: 1 фунт
        Settlement s = result.settlement();
        assertEquals(buyer, s.payerId());
        assertEquals(sellerA, s.payeeId());
        assertEquals(14, s.price().coppets());
        assertEquals(2, s.commissionPaid().coppets());   // ceil(14×0.10)
        assertEquals(12, s.sellerProceeds().coppets());
        assertEquals(negId, s.escrowRef());
        assertTrue(session.openOrders().isEmpty());
    }

    @Test
    void firstAcceptClosesOrderAndReleasesSiblings() {
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
        UUID negA = session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(20));
        UUID negB = session.offerOnOrder(sellerB, orderId, PoundMoney.ofCoppets(18));
        AcceptResult result = session.accept(buyer, negB, 1, 0);
        // Паралельний торг A автоматично скасовано з поверненням його ескроу
        assertEquals(List.of(new Refund(sellerA, negA)), result.releasedEscrows());
        // Другий accept по мертвому торгу неможливий
        assertThrows(MarketException.class, () -> session.accept(buyer, negA, 9, 9));
        assertThrows(MarketException.class, () -> session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(5)));
    }

    @Test
    void acceptWithoutBuyerFundsKeepsNegotiationOpen() {
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
        UUID negId = session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(30));
        assertThrows(MarketException.class, () -> session.accept(buyer, negId, 1, 5)); // 25 < 30
        // торг живий — покупець може торгуватись далі
        session.counter(buyer, negId, PoundMoney.ofCoppets(25));
    }

    @Test
    void withdrawByEitherPartyRefundsSeller() {
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
        UUID negId = session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(30));
        Refund refund = session.withdraw(buyer, negId);
        assertEquals(new Refund(sellerA, negId), refund);
        assertThrows(MarketException.class, () -> session.counter(buyer, negId, PoundMoney.ofCoppets(1)));
    }

    @Test
    void closeRefundsUnsoldLotsAndOpenNegotiations() {
        UUID lotId = session.listLot(sellerA, "custom:crimson_star", 1, PoundMoney.ofCoppets(10));
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
        UUID negId = session.offerOnOrder(sellerB, orderId, PoundMoney.ofCoppets(30));
        List<Refund> refunds = session.close();
        assertTrue(refunds.contains(new Refund(sellerA, lotId)));
        assertTrue(refunds.contains(new Refund(sellerB, negId)));
        assertEquals(2, refunds.size());
        // після закриття всі операції відхиляються
        assertThrows(MarketException.class,
                () -> session.listLot(sellerA, "custom:crimson_star", 1, PoundMoney.ofCoppets(1)));
    }

    /**
     * Інваріант збереження: після довільного сценарію «зайшло = видано + повернено»
     * і для ескроу-предметів, і для грошей (сплачено = виручка + комісія).
     */
    @Test
    void conservationInvariantHolds() {
        UUID lotId = session.listLot(sellerA, "custom:crimson_star", 2, PoundMoney.ofCoppets(30));
        UUID orderId = session.placeOrder(buyer, "custom:dimensional_wanderer_eye", 1, KNOWN);
        UUID negA = session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(20));
        UUID negB = session.offerOnOrder(sellerB, orderId, PoundMoney.ofCoppets(25));
        // ескроу зайшло: lotId, negA, negB
        Settlement lotSale = session.buyLot(buyer, lotId, 2, 0);
        AcceptResult acceptA = session.accept(buyer, negA, 1, 0);
        List<Refund> closeRefunds = session.close();

        // Предмети: кожен із 3 ескроу має рівно один вихід
        java.util.Set<UUID> settled = Set.of(lotSale.escrowRef(), acceptA.settlement().escrowRef());
        java.util.Set<UUID> refunded = new java.util.HashSet<>();
        acceptA.releasedEscrows().forEach(r -> refunded.add(r.escrowRef()));
        closeRefunds.forEach(r -> refunded.add(r.escrowRef()));
        assertEquals(Set.of(lotId, negA), settled);
        assertEquals(Set.of(negB), refunded);

        // Гроші: сплачене покупцем = виручка продавців + комісія
        for (Settlement s : List.of(lotSale, acceptA.settlement())) {
            assertEquals(s.price().coppets(),
                    s.sellerProceeds().coppets() + s.commissionPaid().coppets());
            assertEquals(s.price().coppets(), s.payerCharge().paidCoppets());
        }
    }
}
```

- [ ] **Step 2: Запустити — впаде компіляцією**

```powershell
& $mvn -o test "-Dtest=MarketNegotiationTest"
```
Expected: BUILD FAILURE (нових методів/records нема).

- [ ] **Step 3: Дописати в `MarketSession` (після `Refund`, перед полями)**

```java
    public record BuyOrder(UUID orderId, UUID buyerId, String itemKey, int amount, boolean fulfilled) {}

    public enum NegotiationState { OPEN, ACCEPTED, WITHDRAWN }

    /** Проєкція торгу для GUI (без мутабельного стану назовні). */
    public record NegotiationView(UUID negotiationId, UUID orderId, UUID sellerId, UUID buyerId,
                                  PoundMoney currentPrice, UUID turnOf, NegotiationState state,
                                  String itemKey, int amount) {}

    public record AcceptResult(Settlement settlement, List<Refund> releasedEscrows) {}

    private static final class Negotiation {
        final UUID negotiationId;
        final UUID orderId;
        final UUID sellerId;
        final UUID buyerId;
        PoundMoney currentPrice;
        UUID turnOf; // хто ОТРИМАВ останню ціну — тільки він може прийняти або дати зустрічну
        NegotiationState state = NegotiationState.OPEN;

        Negotiation(UUID negotiationId, UUID orderId, UUID sellerId, UUID buyerId) {
            this.negotiationId = negotiationId;
            this.orderId = orderId;
            this.sellerId = sellerId;
            this.buyerId = buyerId;
        }
    }
```

Нові поля (поруч із `lots`):
```java
    private final Map<UUID, BuyOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, Negotiation> negotiations = new LinkedHashMap<>();
```

Нові методи (після `buyLot`):
```java
    public UUID placeOrder(UUID buyerId, String itemKey, int amount, java.util.Set<String> allowedKeys) {
        ensureOpen();
        ensureParticipant(buyerId);
        if (amount <= 0) {
            throw new MarketException("Кількість має бути додатною");
        }
        if (!allowedKeys.contains(itemKey)) {
            throw new MarketException("Можна замовляти лише інгредієнти відомих вам рецептів");
        }
        UUID orderId = UUID.randomUUID();
        orders.put(orderId, new BuyOrder(orderId, buyerId, itemKey, amount, false));
        return orderId;
    }

    public List<BuyOrder> openOrders() {
        List<BuyOrder> open = new ArrayList<>();
        for (BuyOrder order : orders.values()) {
            if (!order.fulfilled()) {
                open.add(order);
            }
        }
        return open;
    }

    /** Оферта продавця. Застосунок кладе предмет у ескроу за повернутим negotiationId. */
    public UUID offerOnOrder(UUID sellerId, UUID orderId, PoundMoney price) {
        ensureOpen();
        ensureParticipant(sellerId);
        BuyOrder order = orders.get(orderId);
        if (order == null || order.fulfilled()) {
            throw new MarketException("Замовлення вже недоступне");
        }
        if (order.buyerId().equals(sellerId)) {
            throw new MarketException("Це ваше власне замовлення");
        }
        if (price.isZero()) {
            throw new MarketException("Ціна має бути більшою за нуль");
        }
        UUID negotiationId = UUID.randomUUID();
        Negotiation negotiation = new Negotiation(negotiationId, orderId, sellerId, order.buyerId());
        negotiation.currentPrice = price;
        negotiation.turnOf = order.buyerId(); // покупець отримав першу ціну
        negotiations.put(negotiationId, negotiation);
        return negotiationId;
    }

    public void counter(UUID actorId, UUID negotiationId, PoundMoney newPrice) {
        ensureOpen();
        Negotiation negotiation = openNegotiation(negotiationId);
        if (!negotiation.turnOf.equals(actorId)) {
            throw new MarketException("Зараз не ваш хід у цьому торзі");
        }
        if (newPrice.isZero()) {
            throw new MarketException("Ціна має бути більшою за нуль");
        }
        negotiation.currentPrice = newPrice;
        negotiation.turnOf = actorId.equals(negotiation.buyerId)
                ? negotiation.sellerId : negotiation.buyerId;
    }

    /**
     * Акцепт того, чий зараз хід. Платить ЗАВЖДИ покупець, тому гаманець
     * (buyerPounds/buyerCoppets) — покупця, незалежно від того, хто приймає.
     */
    public AcceptResult accept(UUID actorId, UUID negotiationId, int buyerPounds, int buyerCoppets) {
        ensureOpen();
        Negotiation negotiation = openNegotiation(negotiationId);
        if (!negotiation.turnOf.equals(actorId)) {
            throw new MarketException("Прийняти може лише той, хто отримав останню ціну");
        }
        BuyOrder order = orders.get(negotiation.orderId);
        CoinChange charge = CoinChange.make(buyerPounds, buyerCoppets, negotiation.currentPrice)
                .orElseThrow(() -> new MarketException(
                        "У покупця недостатньо монет: потрібно " + negotiation.currentPrice.format()));
        negotiation.state = NegotiationState.ACCEPTED;
        orders.put(order.orderId(),
                new BuyOrder(order.orderId(), order.buyerId(), order.itemKey(), order.amount(), true));
        List<Refund> released = new ArrayList<>();
        for (Negotiation sibling : negotiations.values()) {
            if (sibling.orderId.equals(negotiation.orderId) && sibling.state == NegotiationState.OPEN) {
                sibling.state = NegotiationState.WITHDRAWN;
                released.add(new Refund(sibling.sellerId, sibling.negotiationId));
            }
        }
        PoundMoney commission = negotiation.currentPrice.commission(commissionRate);
        Settlement settlement = new Settlement(negotiation.buyerId, negotiation.sellerId,
                negotiation.currentPrice, commission, negotiation.currentPrice.minus(commission),
                charge, negotiationId, order.itemKey(), order.amount());
        return new AcceptResult(settlement, released);
    }

    /** Відмова будь-якої сторони; ескроу повертається продавцеві. */
    public Refund withdraw(UUID actorId, UUID negotiationId) {
        ensureOpen();
        Negotiation negotiation = openNegotiation(negotiationId);
        if (!negotiation.sellerId.equals(actorId) && !negotiation.buyerId.equals(actorId)) {
            throw new MarketException("Ви не сторона цього торгу");
        }
        negotiation.state = NegotiationState.WITHDRAWN;
        return new Refund(negotiation.sellerId, negotiationId);
    }

    public List<NegotiationView> negotiationsOf(UUID playerId) {
        List<NegotiationView> views = new ArrayList<>();
        for (Negotiation n : negotiations.values()) {
            if (n.state == NegotiationState.OPEN
                    && (n.sellerId.equals(playerId) || n.buyerId.equals(playerId))) {
                BuyOrder order = orders.get(n.orderId);
                views.add(new NegotiationView(n.negotiationId, n.orderId, n.sellerId, n.buyerId,
                        n.currentPrice, n.turnOf, n.state, order.itemKey(), order.amount()));
            }
        }
        return views;
    }

    /** Закриває сесію: усі непродані лоти й відкриті торги → повернення власникам. */
    public List<Refund> close() {
        closed = true;
        List<Refund> refunds = new ArrayList<>();
        for (Lot lot : lots.values()) {
            if (!lot.sold()) {
                refunds.add(new Refund(lot.sellerId(), lot.lotId()));
            }
        }
        for (Negotiation n : negotiations.values()) {
            if (n.state == NegotiationState.OPEN) {
                n.state = NegotiationState.WITHDRAWN;
                refunds.add(new Refund(n.sellerId, n.negotiationId));
            }
        }
        return refunds;
    }

    private Negotiation openNegotiation(UUID negotiationId) {
        Negotiation negotiation = negotiations.get(negotiationId);
        if (negotiation == null || negotiation.state != NegotiationState.OPEN) {
            throw new MarketException("Цей торг уже завершено");
        }
        return negotiation;
    }
```

- [ ] **Step 4: Запустити всі доменні тести ринку — пройдуть**

```powershell
& $mvn -o test "-Dtest=MarketSessionTest,MarketNegotiationTest,PoundMoneyTest,GatheringPhaseTest,ArchitectureTest"
```
Expected: BUILD SUCCESS, Failures: 0.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/me/vangoo/domain/market/MarketSession.java src/test/java/me/vangoo/domain/market/MarketNegotiationTest.java
git commit -m "feat(market): buy orders with alternating-turn haggling and session close"
```

---

### Task 5: Домен — `MarketItemCategory` + `BuybackPriceTable` (прайс скупки)

**Files:**
- Create: `src/main/java/me/vangoo/domain/market/MarketItemCategory.java`
- Create: `src/main/java/me/vangoo/domain/market/BuybackPriceTable.java`
- Test: `src/test/java/me/vangoo/domain/market/BuybackPriceTableTest.java`

**Interfaces:**
- Produces: `enum MarketItemCategory { INGREDIENT, CHARACTERISTIC, RECIPE_BOOK }`
- Produces: `BuybackPriceTable(int ingredientCoppets, int recipeBookCoppets, Map<Integer,Integer> characteristicCoppetsBySeq, Map<String,Integer> overridesByItemKey)` з `PoundMoney unitPriceFor(MarketItemCategory category, int sequenceOrMinus1, String itemKey)`.

- [ ] **Step 1: Написати падаючий тест**

```java
package me.vangoo.domain.market;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuybackPriceTableTest {

    private final BuybackPriceTable table = new BuybackPriceTable(
            2,   // інгредієнт за замовчуванням
            10,  // книга рецептів
            Map.of(9, 40, 8, 60),                     // Характеристики за seq (коппети)
            Map.of("custom:stellar_aqua_crystal", 5)  // точковий override
    );

    @Test
    void usesCategoryDefaults() {
        assertEquals(2, table.unitPriceFor(MarketItemCategory.INGREDIENT, -1, "custom:crimson_star").coppets());
        assertEquals(10, table.unitPriceFor(MarketItemCategory.RECIPE_BOOK, 9, "recipe:Fool:9").coppets());
    }

    @Test
    void characteristicPricedBySequence() {
        assertEquals(40, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 9, "characteristic:Fool:9").coppets());
        assertEquals(60, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 8, "characteristic:Door:8").coppets());
    }

    @Test
    void unknownCharacteristicSequenceFallsBackToZero() {
        // seq без запису в таблиці → 0 к (організатор таке не скуповує)
        assertEquals(0, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 0, "characteristic:Fool:0").coppets());
    }

    @Test
    void exactOverrideWinsOverCategoryDefault() {
        assertEquals(5, table.unitPriceFor(MarketItemCategory.INGREDIENT, -1, "custom:stellar_aqua_crystal").coppets());
    }
}
```

- [ ] **Step 2: Запустити — впаде компіляцією**

```powershell
& $mvn -o test "-Dtest=BuybackPriceTableTest"
```
Expected: BUILD FAILURE.

- [ ] **Step 3: Реалізація**

`MarketItemCategory.java`:
```java
package me.vangoo.domain.market;

/** Категорії потойбічних речей, дозволені на ринку (зілля свідомо НЕ дозволені). */
public enum MarketItemCategory {
    INGREDIENT, CHARACTERISTIC, RECIPE_BOOK
}
```

`BuybackPriceTable.java`:
```java
package me.vangoo.domain.market;

import java.util.Map;

/**
 * Прайс скупки організатором (за одиницю, в коппетах). Свідомо нижче ринкових
 * очікувань — «підлога» цін і джерело емісії монет. Дані — з config.yml (market.buyback).
 */
public record BuybackPriceTable(int ingredientCoppets,
                                int recipeBookCoppets,
                                Map<Integer, Integer> characteristicCoppetsBySeq,
                                Map<String, Integer> overridesByItemKey) {

    public PoundMoney unitPriceFor(MarketItemCategory category, int sequenceOrMinus1, String itemKey) {
        Integer override = overridesByItemKey.get(itemKey);
        if (override != null) {
            return PoundMoney.ofCoppets(override);
        }
        return switch (category) {
            case INGREDIENT -> PoundMoney.ofCoppets(ingredientCoppets);
            case RECIPE_BOOK -> PoundMoney.ofCoppets(recipeBookCoppets);
            case CHARACTERISTIC -> PoundMoney.ofCoppets(
                    characteristicCoppetsBySeq.getOrDefault(sequenceOrMinus1, 0));
        };
    }
}
```

- [ ] **Step 4: Запустити — пройде**

```powershell
& $mvn -o test "-Dtest=BuybackPriceTableTest"
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/me/vangoo/domain/market/MarketItemCategory.java src/main/java/me/vangoo/domain/market/BuybackPriceTable.java src/test/java/me/vangoo/domain/market/BuybackPriceTableTest.java
git commit -m "feat(market): organizer buyback price table"
```

---

### Task 6: Інфраструктура — `CurrencyCodec` + `WalletService` (фізичні монети)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/items/CurrencyCodec.java`
- Create: `src/main/java/me/vangoo/application/services/WalletService.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (поля+створення+геттери)

**Interfaces:**
- Consumes: `NBTBuilder` (патерн `CharacteristicCodec`), `PoundMoney`, `CoinChange`.
- Produces (використовують Task 7, 12, 13, 14):
  - `CurrencyCodec`: `ItemStack createPounds(int amount)`, `ItemStack createCoppets(int amount)`, `boolean isPound(ItemStack)`, `boolean isCoppet(ItemStack)`; константи `NBT_COIN="currency_coin"`, `MODEL_POUND="gold_pound"`, `MODEL_COPPET="coppet"`.
  - `WalletService(CurrencyCodec codec)`: `int countPounds(Player)`, `int countCoppets(Player)`, `Optional<CoinChange> charge(Player, PoundMoney price)` (знімає монети, видає здачу), `void give(Player, PoundMoney)` (фунти+коппети, надлишок — дроп під ноги), `List<ItemStack> asStacks(PoundMoney)` (для черги повернень офлайн-гравцям).

- [ ] **Step 1: Реалізувати `CurrencyCodec`**

Дзеркалить `CharacteristicCodec` (та сама техніка glow/model/NBT; без jukebox-хаку — матеріали не диски):

```java
package me.vangoo.infrastructure.items;

import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

/**
 * Монети підпільного ринку: «Золотий фунт» і «Коппет» (1 фунт = 20 коппетів).
 * Валюта НЕ прив'язана до зборів — інші механіки можуть видавати/приймати її.
 */
public final class CurrencyCodec {

    public static final String NBT_COIN = "currency_coin";
    public static final String COIN_POUND = "pound";
    public static final String COIN_COPPET = "coppet";
    public static final String MODEL_POUND = "gold_pound";
    public static final String MODEL_COPPET = "coppet";

    public ItemStack createPounds(int amount) {
        return createCoin(Material.GOLD_NUGGET, amount, COIN_POUND, MODEL_POUND,
                ChatColor.GOLD + "Золотий фунт",
                ChatColor.GRAY + "Тверда валюта Потойбічних.",
                ChatColor.DARK_GRAY + "1 фунт = 20 коппетів");
    }

    public ItemStack createCoppets(int amount) {
        return createCoin(Material.COPPER_INGOT, amount, COIN_COPPET, MODEL_COPPET,
                ChatColor.YELLOW + "Коппет",
                ChatColor.GRAY + "Дрібна монета Потойбічних.",
                ChatColor.DARK_GRAY + "20 коппетів = 1 фунт");
    }

    private ItemStack createCoin(Material material, int amount, String coinType, String modelKey,
                                 String displayName, String loreLine1, String loreLine2) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(List.of("", loreLine1, loreLine2));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true); // лише для світіння
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        try {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(modelKey));
            meta.setCustomModelDataComponent(cmd);
        } catch (Throwable ignored) {
            // Старіше API без CustomModelDataComponent — предмет лишається валідним.
        }
        item.setItemMeta(meta);
        return new NBTBuilder(item).setString(NBT_COIN, coinType).build();
    }

    public boolean isPound(ItemStack item) {
        return isCoin(item, COIN_POUND);
    }

    public boolean isCoppet(ItemStack item) {
        return isCoin(item, COIN_COPPET);
    }

    private boolean isCoin(ItemStack item, String coinType) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (!NBTBuilder.hasKey(item, NBT_COIN, PersistentDataType.STRING)) {
            return false;
        }
        Optional<String> value = new NBTBuilder(item).getString(item, NBT_COIN);
        return value.map(coinType::equals).orElse(false);
    }
}
```

- [ ] **Step 2: Реалізувати `WalletService`**

```java
package me.vangoo.application.services;

import me.vangoo.domain.market.CoinChange;
import me.vangoo.domain.market.PoundMoney;
import me.vangoo.infrastructure.items.CurrencyCodec;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Фізичний гаманець гравця: рахує/знімає/видає монети в інвентарі.
 * Математика розміну — чистий CoinChange; тут лише рух стаків.
 */
public class WalletService {

    private final CurrencyCodec codec;

    public WalletService(CurrencyCodec codec) {
        this.codec = codec;
    }

    public int countPounds(Player player) {
        return count(player, codec::isPound);
    }

    public int countCoppets(Player player) {
        return count(player, codec::isCoppet);
    }

    private int count(Player player, Predicate<ItemStack> isCoin) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isCoin.test(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /**
     * Знімає ціну з розміном (жадібно: коппети → фунти) і видає здачу коппетами.
     * @return empty, якщо монет не вистачає (інвентар не змінюється).
     */
    public Optional<CoinChange> charge(Player player, PoundMoney price) {
        Optional<CoinChange> change =
                CoinChange.make(countPounds(player), countCoppets(player), price);
        if (change.isEmpty()) {
            return Optional.empty();
        }
        removeCoins(player, codec::isCoppet, change.get().takeCoppets());
        removeCoins(player, codec::isPound, change.get().takePounds());
        if (change.get().changeCoppets() > 0) {
            give(player, PoundMoney.ofCoppets(change.get().changeCoppets()));
        }
        return change;
    }

    /** Видає суму монетами: фунти + решта коппетами; надлишок — дропом під ноги. */
    public void give(Player player, PoundMoney money) {
        for (ItemStack stack : asStacks(money)) {
            player.getInventory().addItem(stack).values()
                    .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        }
    }

    /** Сума як стаки монет (використовується чергою повернень для офлайн-гравців). */
    public java.util.List<ItemStack> asStacks(PoundMoney money) {
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        addStacks(stacks, codec.createPounds(1), money.wholePounds());
        addStacks(stacks, codec.createCoppets(1), money.remainderCoppets());
        return stacks;
    }

    private void addStacks(java.util.List<ItemStack> out, ItemStack prototype, int totalAmount) {
        int remaining = totalAmount;
        while (remaining > 0) {
            int batch = Math.min(remaining, prototype.getMaxStackSize());
            ItemStack stack = prototype.clone();
            stack.setAmount(batch);
            out.add(stack);
            remaining -= batch;
        }
    }

    private void removeCoins(Player player, Predicate<ItemStack> isCoin, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !isCoin.test(item)) {
                continue;
            }
            int take = Math.min(item.getAmount(), remaining);
            remaining -= take;
            if (take == item.getAmount()) {
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - take);
            }
        }
    }
}
```

- [ ] **Step 3: Wiring у `ServiceContainer`**

Поля (біля `characteristicCodec`):
```java
    private CurrencyCodec currencyCodec;
    private WalletService walletService;
```
Створення в `initializeInfrastructure()` (одразу після `this.characteristicCodec = ...`):
```java
        this.currencyCodec = new CurrencyCodec();
        this.walletService = new WalletService(currencyCodec);
```
Геттери (біля `getCharacteristicCodec`):
```java
    public CurrencyCodec getCurrencyCodec() { return currencyCodec; }
    public WalletService getWalletService() { return walletService; }
```

- [ ] **Step 4: Компіляція**

```powershell
& $mvn -o clean package "-DskipTests"
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/me/vangoo/infrastructure/items/CurrencyCodec.java src/main/java/me/vangoo/application/services/WalletService.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(market): physical pound and coppet coins with wallet service"
```

---

### Task 7: Команда `/coins give` (адмін-видача валюти)

**Files:**
- Create: `src/main/java/me/vangoo/presentation/commands/CoinsCommand.java`
- Modify: `src/main/resources/plugin.yml` (команда `coins`)
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (`registerCommands()`)

**Interfaces:**
- Consumes: `WalletService.give(Player, PoundMoney)` (Task 6).

- [ ] **Step 1: Реалізувати команду (за зразком `CharacteristicCommand`)**

```java
package me.vangoo.presentation.commands;

import me.vangoo.application.services.WalletService;
import me.vangoo.domain.market.PoundMoney;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /coins give <player> <pounds> [coppets]
 * Адмін-видача валюти. Свідомо НЕ під /gathering: монети — загальна валюта,
 * інші механіки можуть використовувати її поза зборами.
 */
public class CoinsCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.GOLD + "[Coins] " + ChatColor.RESET;
    private static final String USAGE = "Використання: /coins give <гравець> <фунти> [коппети]";

    private final WalletService walletService;

    public CoinsCommand(WalletService walletService) {
        this.walletService = walletService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(PREFIX + ChatColor.RED + USAGE);
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Гравець не онлайн: " + args[1]);
            return true;
        }
        int pounds;
        int coppets = 0;
        try {
            pounds = Integer.parseInt(args[2]);
            if (args.length >= 4) {
                coppets = Integer.parseInt(args[3]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Невірне число. " + USAGE);
            return true;
        }
        if (pounds < 0 || coppets < 0 || (pounds == 0 && coppets == 0)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Сума має бути додатною");
            return true;
        }
        PoundMoney money = PoundMoney.of(pounds, coppets);
        walletService.give(target, money);
        sender.sendMessage(PREFIX + ChatColor.GREEN
                + String.format("Видано %s гравцю %s", money.format(), target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give");
        }
        if (args.length == 2) {
            String lower = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(lower))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            return List.of("1", "5", "10");
        }
        if (args.length == 4) {
            return List.of("0", "10", "19");
        }
        return List.of();
    }
}
```

- [ ] **Step 2: `plugin.yml` — після блоку `characteristic:`**

```yaml
  coins:
    description: Grant market currency coins (admin/testing)
    usage: /coins give <player> <pounds> [coppets]
    permission: mysteriesabove.admin
    permission-message: "§cYou do not have permission to use this command."
```

- [ ] **Step 3: Реєстрація в `MysteriesAbovePlugin.registerCommands()` (в кінець методу)**

```java
        CoinsCommand coinsCommand = new CoinsCommand(services.getWalletService());
        getCommand("coins").setExecutor(coinsCommand);
        getCommand("coins").setTabCompleter(coinsCommand);
```

- [ ] **Step 4: Збірка**

```powershell
& $mvn -o clean package "-DskipTests"
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/me/vangoo/presentation/commands/CoinsCommand.java src/main/resources/plugin.yml src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(market): /coins admin command for currency grants"
```

---

### Task 8: Лут-канал фунтів (`currency:` id у луті структур)

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/structures/LootGenerationService.java:170-181` (`createItemFromId`)
- Modify: `src/main/resources/global_loot.yml` (запис фунтів)

**Interfaces:**
- Consumes: `CurrencyCodec` (Task 6).
- Produces: лут-id `currency:pound` і `currency:coppet`, резолвиться `createItemFromId`.

- [ ] **Step 1: Додати `CurrencyCodec` у `LootGenerationService`**

Конструктор отримує ще один параметр (оновити і виклик у `ServiceContainer.initializeInfrastructure()`):
```java
    private final me.vangoo.infrastructure.items.CurrencyCodec currencyCodec;

    public LootGenerationService(
            Plugin plugin,
            CustomItemService customItemService,
            PotionManager potionManager,
            RecipeBookFactory recipeBookFactory,
            me.vangoo.infrastructure.items.CurrencyCodec currencyCodec) {
        ...
        this.currencyCodec = currencyCodec;
    }
```
У `ServiceContainer` перемістити рядок `this.currencyCodec = new CurrencyCodec();` ВИЩЕ створення `lootGenerationService` і передати кодек:
```java
        this.lootGenerationService = new LootGenerationService(
                plugin, customItemService, potionManager, recipeBookFactory, currencyCodec);
```

У `createItemFromId` — нова гілка ПІСЛЯ гілки `characteristic:`:
```java
        if (itemId.equals("currency:pound")) return currencyCodec.createPounds(1);
        if (itemId.equals("currency:coppet")) return currencyCodec.createCoppets(1);
```
(Кількість задає лут-таблиця через `amount_min`/`amount_max`, як для інших предметів.)

- [ ] **Step 2: Запис у `global_loot.yml` (в кінець секції `items:`)**

```yaml
    # --- Валюта підпільного ринку (рідкісна; НЕ Характеристика — інваріант луту дотримано) ---
    gold_pound:
      item_id: "currency:pound"
      weight: 12
      amount_min: 1
      amount_max: 3
    coppet:
      item_id: "currency:coppet"
      weight: 25
      amount_min: 2
      amount_max: 8
```

- [ ] **Step 3: Збірка + юніт-регресія**

```powershell
& $mvn -o clean package
```
Expected: BUILD SUCCESS (усі наявні тести зелені).

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/me/vangoo/infrastructure/structures/LootGenerationService.java src/main/resources/global_loot.yml src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(market): rare pound and coppet drops in structure loot"
```

---

### Task 9: Інфраструктура — `MarketConfig` + `config.yml` + `MarketItemClassifier`

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/market/MarketConfig.java`
- Create: `src/main/java/me/vangoo/application/services/MarketItemClassifier.java`
- Modify: `src/main/resources/config.yml` (секція `market.*`)
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`

**Interfaces:**
- Consumes: `BuybackPriceTable` (Task 5), `CharacteristicCodec`, `RecipeBookFactory`, `CustomItemService`.
- Produces (використовують Task 12, 13, 14):
  - `MarketConfig`: `int intervalDays()`, `int joinWindowMinutes()`, `int durationMinutes()`, `double commissionRate()`, `BuybackPriceTable buyback()`; `static MarketConfig load(org.bukkit.plugin.Plugin plugin)`.
  - `MarketItemClassifier`: record `ClassifiedItem(MarketItemCategory category, String itemKey, int sequence)`; `Optional<ClassifiedItem> classify(ItemStack item)` — Характеристика → книга рецептів → custom-інгредієнт; зілля/ванільне/монети → `empty` (виставляти не можна).

- [ ] **Step 1: `config.yml` — додати секцію (в кінець файла)**

```yaml

# Підпільний ринок (Збори Потойбічних)
market:
  gathering:
    interval-days: 7          # реальних днів між зборами (відлік персиститься)
    join-window-minutes: 5    # вікно згоди після оголошення
    duration-minutes: 15      # тривалість відкритого ринку
  commission-rate: 0.10       # комісія організатора з угод гравець-гравець (згорає)
  buyback:                    # скупка організатором (коппети за одиницю; джерело емісії)
    ingredient-coppets: 2
    recipe-book-coppets: 10
    characteristic-coppets-by-seq:
      "9": 40
      "8": 60
      "7": 90
      "6": 130
      "5": 180
    overrides: {}             # точкові ціни: "custom:<id>": коппети
```

- [ ] **Step 2: `MarketConfig`**

```java
package me.vangoo.infrastructure.market;

import me.vangoo.domain.market.BuybackPriceTable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/** Читає секцію market.* із config.yml (через plugin.getConfig(), як creatures.*). */
public record MarketConfig(int intervalDays,
                           int joinWindowMinutes,
                           int durationMinutes,
                           double commissionRate,
                           BuybackPriceTable buyback) {

    public static MarketConfig load(Plugin plugin) {
        var cfg = plugin.getConfig();
        int intervalDays = cfg.getInt("market.gathering.interval-days", 7);
        int joinWindow = cfg.getInt("market.gathering.join-window-minutes", 5);
        int duration = cfg.getInt("market.gathering.duration-minutes", 15);
        double rate = cfg.getDouble("market.commission-rate", 0.10);

        Map<Integer, Integer> bySeq = new HashMap<>();
        ConfigurationSection seqSection =
                cfg.getConfigurationSection("market.buyback.characteristic-coppets-by-seq");
        if (seqSection != null) {
            for (String key : seqSection.getKeys(false)) {
                try {
                    bySeq.put(Integer.parseInt(key), seqSection.getInt(key));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("market.buyback: bad sequence key '" + key + "', skipped");
                }
            }
        }
        Map<String, Integer> overrides = new HashMap<>();
        ConfigurationSection overridesSection =
                cfg.getConfigurationSection("market.buyback.overrides");
        if (overridesSection != null) {
            for (String key : overridesSection.getKeys(false)) {
                overrides.put(key, overridesSection.getInt(key));
            }
        }
        BuybackPriceTable table = new BuybackPriceTable(
                cfg.getInt("market.buyback.ingredient-coppets", 2),
                cfg.getInt("market.buyback.recipe-book-coppets", 10),
                bySeq, overrides);
        return new MarketConfig(intervalDays, joinWindow, duration, rate, table);
    }
}
```

- [ ] **Step 3: `MarketItemClassifier`**

Порядок перевірок критичний: Характеристика й книга рецептів — НЕ custom items, їх треба розпізнати першими (дзеркалить `PotionCraftingService.getItemKey`).

```java
package me.vangoo.application.services;

import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.domain.market.MarketItemCategory;
import me.vangoo.infrastructure.items.CharacteristicCodec;
import me.vangoo.infrastructure.items.CurrencyCodec;
import me.vangoo.infrastructure.items.RecipeBookFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Визначає, чи можна виставити предмет на ринок, і його itemKey.
 * Дозволено: інгредієнти (custom items), Характеристики, книги рецептів.
 * Заборонено: зілля, ванільні предмети, монети.
 */
public class MarketItemClassifier {

    /** sequence = −1, якщо категорія без послідовності (інгредієнт). */
    public record ClassifiedItem(MarketItemCategory category, String itemKey, int sequence) {}

    private final CharacteristicCodec characteristicCodec;
    private final RecipeBookFactory recipeBookFactory;
    private final CustomItemService customItemService;
    private final CurrencyCodec currencyCodec;

    public MarketItemClassifier(CharacteristicCodec characteristicCodec,
                                RecipeBookFactory recipeBookFactory,
                                CustomItemService customItemService,
                                CurrencyCodec currencyCodec) {
        this.characteristicCodec = characteristicCodec;
        this.recipeBookFactory = recipeBookFactory;
        this.customItemService = customItemService;
        this.currencyCodec = currencyCodec;
    }

    public Optional<ClassifiedItem> classify(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return Optional.empty();
        }
        if (currencyCodec.isPound(item) || currencyCodec.isCoppet(item)) {
            return Optional.empty(); // монети — засіб платежу, не товар
        }
        Optional<Characteristic> characteristic = characteristicCodec.read(item);
        if (characteristic.isPresent()) {
            Characteristic c = characteristic.get();
            return Optional.of(new ClassifiedItem(
                    MarketItemCategory.CHARACTERISTIC, c.itemKey(), c.sequence()));
        }
        if (recipeBookFactory.isRecipeBook(item)) {
            Optional<String> pathway = recipeBookFactory.getPathwayName(item);
            Optional<Integer> sequence = recipeBookFactory.getSequence(item);
            if (pathway.isPresent() && sequence.isPresent()) {
                return Optional.of(new ClassifiedItem(MarketItemCategory.RECIPE_BOOK,
                        "recipe:" + pathway.get() + ":" + sequence.get(), sequence.get()));
            }
            return Optional.empty();
        }
        if (customItemService.isCustomItem(item)) {
            return customItemService.getCustomItem(item)
                    .map(ci -> new ClassifiedItem(MarketItemCategory.INGREDIENT, "custom:" + ci.id(), -1));
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Wiring у `ServiceContainer`**

Поля: `private MarketConfig marketConfig; private MarketItemClassifier marketItemClassifier;`
В `initializeInfrastructure()` після створення `currencyCodec`:
```java
        this.marketConfig = MarketConfig.load(plugin);
        this.marketItemClassifier = new MarketItemClassifier(
                characteristicCodec, recipeBookFactory, customItemService, currencyCodec);
```
(`import me.vangoo.infrastructure.market.MarketConfig;`) Геттери:
```java
    public MarketConfig getMarketConfig() { return marketConfig; }
    public MarketItemClassifier getMarketItemClassifier() { return marketItemClassifier; }
```

- [ ] **Step 5: Збірка + commit**

```powershell
& $mvn -o clean package "-DskipTests"
git add src/main/java/me/vangoo/infrastructure/market/MarketConfig.java src/main/java/me/vangoo/application/services/MarketItemClassifier.java src/main/resources/config.yml src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(market): market config section and tradeable item classifier"
```

---

### Task 10: Інфраструктура — `GatheringSnapshotRepository` (краш-безпека)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/market/GatheringSnapshotRepository.java`

**Interfaces:**
- Produces (використовує Task 12):
  - `record Snapshot(long nextGatheringEpochMillis, List<ParticipantHome> participants, List<EscrowItem> escrow, List<EscrowItem> pendingReturns)`
  - `record ParticipantHome(String playerId, String world, double x, double y, double z, float yaw, float pitch)`
  - `record EscrowItem(String ownerId, String base64Stack)`
  - `Optional<Snapshot> load()`, `void save(Snapshot snapshot)`
  - хелпери: `static String encodeStack(ItemStack)`, `static ItemStack decodeStack(String)` (Paper `serializeAsBytes`/`deserializeBytes` + Base64).

Політика відновлення (реалізує Task 12): незакрита сесія зі снепшота НЕ продовжується — ескроу переливається в `pendingReturns`, учасники повертаються додому при вході.

- [ ] **Step 1: Реалізація (Gson-патерн `JSONRecipeUnlockRepository`)**

```java
package me.vangoo.infrastructure.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * gathering-state.json: час наступного збору + активний ескроу + черга повернень.
 * Пишеться після кожної мутації сесії (обсяг малий — синхронний запис прийнятний).
 * Мета — краш-безпека: після рестарту все з ескроу повертається власникам.
 */
public class GatheringSnapshotRepository {

    private static final Logger LOGGER = Logger.getLogger(GatheringSnapshotRepository.class.getName());

    public record ParticipantHome(String playerId, String world,
                                  double x, double y, double z, float yaw, float pitch) {}

    public record EscrowItem(String ownerId, String base64Stack) {}

    public record Snapshot(long nextGatheringEpochMillis,
                           List<ParticipantHome> participants,
                           List<EscrowItem> escrow,
                           List<EscrowItem> pendingReturns) {}

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public GatheringSnapshotRepository(String filePath) {
        this.file = new File(filePath);
    }

    public Optional<Snapshot> load() {
        if (!file.exists() || file.length() == 0) {
            return Optional.empty();
        }
        try (FileReader reader = new FileReader(file)) {
            return Optional.ofNullable(gson.fromJson(reader, Snapshot.class));
        } catch (IOException e) {
            LOGGER.warning("Failed to load gathering snapshot: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Snapshot snapshot) {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(snapshot, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save gathering snapshot: " + e.getMessage());
        }
    }

    /** Paper ItemStack#serializeAsBytes — стабільний бінарний формат із міграцією версій. */
    public static String encodeStack(ItemStack stack) {
        return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    public static ItemStack decodeStack(String base64) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
    }
}
```

- [ ] **Step 2: Wiring у `ServiceContainer.initializeInfrastructure()`**

```java
        this.gatheringSnapshotRepository = new GatheringSnapshotRepository(
                plugin.getDataFolder() + File.separator + "gathering-state.json");
```
Поле `private GatheringSnapshotRepository gatheringSnapshotRepository;` + геттер
`public GatheringSnapshotRepository getGatheringSnapshotRepository() { return gatheringSnapshotRepository; }`
(+ `import me.vangoo.infrastructure.market.GatheringSnapshotRepository;`).

- [ ] **Step 3: Збірка + commit**

```powershell
& $mvn -o clean package "-DskipTests"
git add src/main/java/me/vangoo/infrastructure/market/GatheringSnapshotRepository.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(market): crash-safe gathering snapshot repository"
```

---

### Task 11: Інфраструктура — `GatheringVenueProvider` (світ-заглушка) + `GatheringAnonymizer`

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/market/GatheringVenueProvider.java`
- Create: `src/main/java/me/vangoo/infrastructure/market/GatheringAnonymizer.java`

**Interfaces:**
- Produces (використовує Task 12):
  - `GatheringVenueProvider`: `Location venueSpawn()` (ідемпотентно створює void-світ `mysteries_gathering` із платформою), `boolean isVenueWorld(World)`.
  - `GatheringAnonymizer`: `void mask(Player, String alias)`, `void unmask(Player)`, `void unmaskAll()` — Paper `setPlayerProfile` (спільний дефолтний скін), scoreboard-team без нік-табличок, маскування табліста.

- [ ] **Step 1: `GatheringVenueProvider`**

```java
package me.vangoo.infrastructure.market;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

/**
 * Світ-заглушка для зборів: порожній void-світ із кам'яною залою 16×16 на y=64.
 * Інтерфейс (venueSpawn) дозволяє згодом замінити заглушку на справжню структуру,
 * не чіпаючи GatheringService.
 */
public class GatheringVenueProvider {

    public static final String WORLD_NAME = "mysteries_gathering";
    private static final int PLATFORM_Y = 64;
    private static final int HALF = 8;

    public Location venueSpawn() {
        World world = getOrCreateWorld();
        return new Location(world, 0.5, PLATFORM_Y + 1, 0.5);
    }

    public boolean isVenueWorld(World world) {
        return world != null && WORLD_NAME.equals(world.getName());
    }

    private World getOrCreateWorld() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            return existing;
        }
        World world = new WorldCreator(WORLD_NAME)
                .environment(World.Environment.NORMAL)
                .generator(new EmptyChunkGenerator())
                .createWorld();
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setTime(18000L); // вічна північ — атмосфера таємного збору
        buildPlatformIfMissing(world);
        world.setSpawnLocation(0, PLATFORM_Y + 1, 0);
        return world;
    }

    private void buildPlatformIfMissing(World world) {
        if (world.getBlockAt(0, PLATFORM_Y, 0).getType() == Material.POLISHED_BLACKSTONE) {
            return; // зала вже збудована
        }
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                world.getBlockAt(x, PLATFORM_Y, z).setType(Material.POLISHED_BLACKSTONE);
            }
        }
        // Ліхтарі по кутах — мінімальне освітлення
        for (int[] corner : new int[][]{{-HALF, -HALF}, {-HALF, HALF}, {HALF, -HALF}, {HALF, HALF}}) {
            world.getBlockAt(corner[0], PLATFORM_Y + 1, corner[1]).setType(Material.SOUL_LANTERN);
        }
    }

    /** Порожній генератор: жодних чанків, лише void. */
    private static final class EmptyChunkGenerator extends ChunkGenerator {
        // Нове API ChunkGenerator: дефолтні no-op методи вже генерують порожнечу.
    }
}
```

- [ ] **Step 2: `GatheringAnonymizer`**

```java
package me.vangoo.infrastructure.market;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Анонімність на час OPEN: усім один дефолтний скін (Paper setPlayerProfile без
 * властивості textures), нік-таблички сховані scoreboard-командою, табліст маскується.
 * Профіль НЕ персистентний — релогін/рестарт повертає справжній вигляд сам собою.
 */
public class GatheringAnonymizer {

    private static final String TEAM_NAME = "ma_gathering";

    private final Map<UUID, PlayerProfile> savedProfiles = new HashMap<>();

    public void mask(Player player, String alias) {
        savedProfiles.putIfAbsent(player.getUniqueId(), player.getPlayerProfile());
        PlayerProfile masked = player.getPlayerProfile();
        masked.removeProperty("textures"); // без текстур → дефолтний скін у всіх
        player.setPlayerProfile(masked);
        player.setPlayerListName(ChatColor.DARK_GRAY + alias);
        team().addEntry(player.getName());
    }

    public void unmask(Player player) {
        PlayerProfile original = savedProfiles.remove(player.getUniqueId());
        if (original != null) {
            player.setPlayerProfile(original);
        }
        player.setPlayerListName(null);
        team().removeEntry(player.getName());
    }

    public void unmaskAll() {
        for (UUID id : Map.copyOf(savedProfiles).keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                unmask(player);
            } else {
                savedProfiles.remove(id); // офлайн: релогін і так поверне справжній профіль
            }
        }
    }

    private Team team() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(TEAM_NAME);
        if (team == null) {
            team = board.registerNewTeam(TEAM_NAME);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        return team;
    }
}
```

- [ ] **Step 3: Wiring у `ServiceContainer.initializeInfrastructure()`**

```java
        this.gatheringVenueProvider = new GatheringVenueProvider();
        this.gatheringAnonymizer = new GatheringAnonymizer();
```
Поля + геттери `getGatheringVenueProvider()` / `getGatheringAnonymizer()` — за зразком Task 10.

- [ ] **Step 4: Збірка + commit**

```powershell
& $mvn -o clean package "-DskipTests"
git add src/main/java/me/vangoo/infrastructure/market/GatheringVenueProvider.java src/main/java/me/vangoo/infrastructure/market/GatheringAnonymizer.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(market): void gathering venue and player anonymizer"
```

---

### Task 12: Інфраструктура — `OrganizerNpcService` (Citizens-NPC організатора)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/citizens/OrganizerNpcService.java`

**Interfaces:**
- Produces (використовують Task 13, 16): `void spawn(Location)`, `void despawn()`, `boolean isOrganizer(net.citizensnpcs.api.npc.NPC npc)`.
- NPC — тимчасовий (живе лише під час OPEN, `despawn()` знищує його) — на відміну від Маріонеток, його НЕ треба зберігати в Citizens `saves.yml`.

- [ ] **Step 1: Реалізація**

```java
package me.vangoo.infrastructure.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

/**
 * NPC-організатор Зборів: підтверджує справжність обмінюваних речей і скуповує їх
 * за фіксованим прайсом. Живе лише під час фази OPEN — spawn на відкритті,
 * despawn (destroy) на закритті; НЕ персиститься Citizens'ом між рестартами.
 */
public class OrganizerNpcService {

    private NPC npc;

    public void spawn(Location location) {
        despawn(); // страховка від подвійного спавну
        npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER,
                ChatColor.DARK_PURPLE + "Посередник");
        npc.setProtected(true); // невразливий до атак
        npc.spawn(location);
    }

    public void despawn() {
        if (npc != null) {
            npc.destroy();
            npc = null;
        }
    }

    public boolean isOrganizer(NPC candidate) {
        return npc != null && candidate != null && npc.getId() == candidate.getId();
    }
}
```

- [ ] **Step 2: Wiring у `ServiceContainer.initializeInfrastructure()`**

```java
        this.organizerNpcService = new OrganizerNpcService();
```
Поле `private OrganizerNpcService organizerNpcService;` + геттер `getOrganizerNpcService()`
(+ `import me.vangoo.infrastructure.citizens.OrganizerNpcService;`).

- [ ] **Step 3: Збірка + commit**

```powershell
& $mvn -o clean package "-DskipTests"
git add src/main/java/me/vangoo/infrastructure/citizens/OrganizerNpcService.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(market): ephemeral organizer NPC service"
```

---

### Task 13: Застосунок — `GatheringService` (оркестратор життєвого циклу і ринкових операцій)

**Files:**
- Create: `src/main/java/me/vangoo/application/services/GatheringService.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (створення в `initializeApplicationServices` ПІСЛЯ `beyonderService`)
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (`onEnable`: `initializeFromSnapshot()`; `onDisable`: `forceCloseIfActive()` ПЕРЕД `saveAll()`)

**Interfaces:**
- Consumes: `MarketSession` (+records), `GatheringPhase`, `PoundMoney`, `CoinChange` (Tasks 1–4); `WalletService` (Task 6); `MarketConfig`, `MarketItemClassifier` (Task 9); `GatheringSnapshotRepository` (Task 10); `GatheringVenueProvider`, `GatheringAnonymizer` (Task 11); `OrganizerNpcService` (Task 12); наявні `BeyonderService.getBeyonder(UUID)`, `RecipeUnlockService.getUnlockedRecipes(UUID)`, `PotionManager.getPotionsPathway(String)`.
- Produces (використовують Tasks 14–17):
  - фази: `GatheringPhase phase()`, `long nextGatheringMillis()`, `void announce()`, `boolean join(Player)`, `void forceCloseIfActive()`, `void initializeFromSnapshot()`
  - ринок: `boolean listLotFromHand(Player, PoundMoney price)`, `boolean buyLot(Player, UUID lotId)`, `boolean placeOrder(Player, String itemKey, int amount)`, `boolean offerFromHand(Player, UUID orderId, PoundMoney price)`, `boolean counter(Player, UUID negotiationId, PoundMoney price)`, `boolean accept(Player, UUID negotiationId)`, `boolean withdraw(Player, UUID negotiationId)`, `boolean buybackFromHand(Player)`
  - в'ю для GUI: `List<MarketSession.Lot> activeLots()`, `List<MarketSession.BuyOrder> openOrders()`, `List<MarketSession.NegotiationView> negotiationsOf(UUID)`, `String aliasOf(UUID)`, `ItemStack escrowStack(UUID escrowRef)` (клон для рендера), `Optional<String> classifyKey(ItemStack)`, `Set<String> knownIngredientKeys(Player)`, `List<ItemStack> knownIngredientStacks(Player)`, `boolean isOpenParticipant(Player)`
  - події гравців: `void handleJoin(Player)`, `void handleQuit(Player)`.

**Рішення, зафіксовані тут:**
- Виставлення/оферта/скупка — **предметом у головній руці** (не слоти-приймачі, як у спеку): менша поверхня дюпів через GUI-кліки; спек оновлюється в Task 18.
- Краш-відновлення: незакрита сесія НЕ продовжується — ескроу → черга повернень, учасники повертаються додому при вході.
- Усі `MarketException` ловляться тут і показуються гравцеві червоним; методи повертають `boolean success`.

- [ ] **Step 1: Реалізація**

```java
package me.vangoo.application.services;

import me.vangoo.domain.market.GatheringPhase;
import me.vangoo.domain.market.MarketItemCategory;
import me.vangoo.domain.market.MarketSession;
import me.vangoo.domain.market.MarketSession.AcceptResult;
import me.vangoo.domain.market.MarketSession.BuyOrder;
import me.vangoo.domain.market.MarketSession.Lot;
import me.vangoo.domain.market.MarketSession.MarketException;
import me.vangoo.domain.market.MarketSession.NegotiationView;
import me.vangoo.domain.market.MarketSession.Refund;
import me.vangoo.domain.market.MarketSession.Settlement;
import me.vangoo.domain.market.PoundMoney;
import me.vangoo.domain.valueobjects.UnlockedRecipe;
import me.vangoo.infrastructure.citizens.OrganizerNpcService;
import me.vangoo.infrastructure.market.GatheringAnonymizer;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository.EscrowItem;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository.ParticipantHome;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository.Snapshot;
import me.vangoo.infrastructure.market.GatheringVenueProvider;
import me.vangoo.infrastructure.market.MarketConfig;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Оркестратор Зборів Потойбічних: фази (IDLE→ANNOUNCED→OPEN→CLOSING→IDLE),
 * телепорт/анонімність/NPC, ескроу реальних стаків і виконання Settlement/Refund
 * команд чистої MarketSession. Після кожної мутації — снепшот на диск.
 */
public class GatheringService {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Збори] " + ChatColor.RESET;

    private record EscrowEntry(UUID ownerId, ItemStack stack) {}

    private final Plugin plugin;
    private final MarketConfig config;
    private final WalletService walletService;
    private final MarketItemClassifier classifier;
    private final GatheringVenueProvider venueProvider;
    private final GatheringAnonymizer anonymizer;
    private final GatheringSnapshotRepository snapshotRepository;
    private final OrganizerNpcService organizerNpc;
    private final BeyonderService beyonderService;
    private final RecipeUnlockService recipeUnlockService;
    private final PotionManager potionManager;

    private GatheringPhase phase = GatheringPhase.IDLE;
    private MarketSession session;
    private final Map<UUID, EscrowEntry> escrow = new HashMap<>();
    private final Set<UUID> joined = new LinkedHashSet<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();
    private final Map<UUID, ParticipantHome> crashHomes = new HashMap<>();
    private long nextGatheringMillis;
    private final List<BukkitTask> phaseTasks = new ArrayList<>();

    public GatheringService(Plugin plugin, MarketConfig config, WalletService walletService,
                            MarketItemClassifier classifier, GatheringVenueProvider venueProvider,
                            GatheringAnonymizer anonymizer, GatheringSnapshotRepository snapshotRepository,
                            OrganizerNpcService organizerNpc, BeyonderService beyonderService,
                            RecipeUnlockService recipeUnlockService, PotionManager potionManager) {
        this.plugin = plugin;
        this.config = config;
        this.walletService = walletService;
        this.classifier = classifier;
        this.venueProvider = venueProvider;
        this.anonymizer = anonymizer;
        this.snapshotRepository = snapshotRepository;
        this.organizerNpc = organizerNpc;
        this.beyonderService = beyonderService;
        this.recipeUnlockService = recipeUnlockService;
        this.potionManager = potionManager;
    }

    // ── Відновлення після рестарту/крашу ────────────────────────────────────

    /** Викликати з onEnable. Незакрита сесія НЕ продовжується — все повертається власникам. */
    public void initializeFromSnapshot() {
        Optional<Snapshot> loaded = snapshotRepository.load();
        if (loaded.isEmpty()) {
            nextGatheringMillis = System.currentTimeMillis() + intervalMillis();
            persist();
            return;
        }
        Snapshot snapshot = loaded.get();
        nextGatheringMillis = snapshot.nextGatheringEpochMillis();
        for (EscrowItem item : snapshot.pendingReturns()) {
            queueReturn(UUID.fromString(item.ownerId()),
                    GatheringSnapshotRepository.decodeStack(item.base64Stack()));
        }
        // Краш посеред збору: ескроу → повернення, доми учасників → на телепорт при вході
        for (EscrowItem item : snapshot.escrow()) {
            queueReturn(UUID.fromString(item.ownerId()),
                    GatheringSnapshotRepository.decodeStack(item.base64Stack()));
        }
        for (ParticipantHome home : snapshot.participants()) {
            crashHomes.put(UUID.fromString(home.playerId()), home);
        }
        if (!snapshot.escrow().isEmpty() || !snapshot.participants().isEmpty()) {
            plugin.getLogger().warning("Gathering session was interrupted by restart; "
                    + "escrow queued for return to " + snapshot.escrow().size() + " owners");
        }
        persist();
    }

    // ── Фази ─────────────────────────────────────────────────────────────────

    public GatheringPhase phase() {
        return phase;
    }

    public long nextGatheringMillis() {
        return nextGatheringMillis;
    }

    /** Оголошення збору (планувальник або /gathering start). */
    public void announce() {
        if (!phase.canTransitionTo(GatheringPhase.ANNOUNCED)) {
            return;
        }
        phase = GatheringPhase.ANNOUNCED;
        joined.clear();
        nextGatheringMillis = System.currentTimeMillis() + intervalMillis();
        persist();

        TextComponent invite = new TextComponent(PREFIX + ChatColor.LIGHT_PURPLE
                + "Шепіт у пітьмі: сьогодні Потойбічні збираються в таємному місці... ");
        TextComponent button = new TextComponent(ChatColor.GREEN + "" + ChatColor.BOLD + "[Прийти]");
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gathering join"));
        button.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GRAY + "Погодитись піти на Збори").create()));
        invite.addExtra(button);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (beyonderService.getBeyonder(player.getUniqueId()) != null) {
                player.spigot().sendMessage(invite);
                player.sendMessage(PREFIX + ChatColor.GRAY + "Вікно згоди — "
                        + config.joinWindowMinutes() + " хв. Візьміть речі на обмін із собою.");
            }
        }
        schedule(() -> open(), config.joinWindowMinutes() * 60L * 20L);
    }

    public boolean join(Player player) {
        if (phase != GatheringPhase.ANNOUNCED) {
            player.sendMessage(PREFIX + ChatColor.RED + "Зараз немає відкритого запрошення.");
            return false;
        }
        if (beyonderService.getBeyonder(player.getUniqueId()) == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Збори — лише для Потойбічних.");
            return false;
        }
        if (joined.add(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.GREEN
                    + "Ви відчуваєте тяжіння... Не опирайтесь, коли настане час.");
        }
        return true;
    }

    private void open() {
        if (phase != GatheringPhase.ANNOUNCED) {
            return;
        }
        List<Player> attendees = new ArrayList<>();
        for (UUID id : joined) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                attendees.add(player);
            }
        }
        if (attendees.isEmpty()) {
            phase = GatheringPhase.IDLE;
            cancelPhaseTasks();
            persist();
            return;
        }
        phase = GatheringPhase.OPEN;
        session = new MarketSession(config.commissionRate(), new Random());
        Location venue = venueProvider.venueSpawn();
        for (Player player : attendees) {
            session.registerParticipant(player.getUniqueId());
            returnLocations.put(player.getUniqueId(), player.getLocation());
            player.teleport(venue);
            anonymizer.mask(player, session.aliasOf(player.getUniqueId()));
        }
        organizerNpc.spawn(venue);
        broadcastToParticipants(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                + "Посередник: Вітаю в місці, якого немає на жодній мапі. Тут ніхто не має імені.");
        broadcastToParticipants(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                + "Посередник: Я ручаюся за справжність кожної речі. Торгуйте — /gathering menu. "
                + "Маєте непотріб — принесіть мені, скуплю.");
        broadcastToParticipants(PREFIX + ChatColor.GRAY + "Збір триватиме "
                + config.durationMinutes() + " хв.");
        long durationTicks = config.durationMinutes() * 60L * 20L;
        if (config.durationMinutes() > 5) {
            schedule(() -> broadcastToParticipants(PREFIX + ChatColor.YELLOW
                    + "Збір закінчиться за 5 хвилин!"), durationTicks - 5 * 60L * 20L);
        }
        schedule(() -> broadcastToParticipants(PREFIX + ChatColor.YELLOW
                + "Збір закінчиться за 1 хвилину!"), durationTicks - 60L * 20L);
        schedule(this::close, durationTicks);
        persist();
    }

    private void close() {
        if (phase != GatheringPhase.OPEN) {
            return;
        }
        phase = GatheringPhase.CLOSING;
        cancelPhaseTasks();
        for (Refund refund : session.close()) {
            releaseEscrow(refund);
        }
        // Захист: якщо в ескроу щось лишилось (не мало б) — теж повернути
        for (Map.Entry<UUID, EscrowEntry> orphan : Map.copyOf(escrow).entrySet()) {
            escrow.remove(orphan.getKey());
            deliverItem(orphan.getValue().ownerId(), orphan.getValue().stack());
        }
        for (UUID id : Set.copyOf(returnLocations.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                anonymizer.unmask(player);
                player.teleport(returnLocations.remove(id));
                player.sendMessage(PREFIX + ChatColor.GRAY
                        + "Збір завершено. Ви знову там, звідки прийшли.");
            }
            // офлайн: локація лишається — handleJoin поверне при вході
        }
        anonymizer.unmaskAll();
        organizerNpc.despawn();
        session = null;
        joined.clear();
        phase = GatheringPhase.IDLE;
        persist();
    }

    /** /gathering stop та onDisable: коректно закрити активний збір. */
    public void forceCloseIfActive() {
        if (phase == GatheringPhase.OPEN) {
            close();
        } else if (phase == GatheringPhase.ANNOUNCED) {
            phase = GatheringPhase.IDLE;
            cancelPhaseTasks();
            joined.clear();
            persist();
        }
    }

    // ── Ринкові операції (усі вимагають OPEN + учасник) ──────────────────────

    public boolean listLotFromHand(Player seller, PoundMoney price) {
        return guarded(seller, () -> {
            ItemStack hand = requireHandItem(seller);
            var classified = classifier.classify(hand).orElseThrow(() -> new MarketException(
                    "Це не потойбічна річ — на ринку їй не місце (інгредієнти, Характеристики, книги рецептів)"));
            UUID lotId = session.listLot(seller.getUniqueId(), classified.itemKey(), hand.getAmount(), price);
            escrow.put(lotId, new EscrowEntry(seller.getUniqueId(), hand.clone()));
            seller.getInventory().setItemInMainHand(null);
            seller.sendMessage(PREFIX + ChatColor.GREEN + "Лот виставлено: "
                    + describe(hand) + ChatColor.GREEN + " за " + price.format());
            persist();
        });
    }

    public boolean buyLot(Player buyer, UUID lotId) {
        return guarded(buyer, () -> {
            Settlement s = session.buyLot(buyer.getUniqueId(), lotId,
                    walletService.countPounds(buyer), walletService.countCoppets(buyer));
            settle(buyer, s);
            buyer.sendMessage(PREFIX + ChatColor.GREEN + "Куплено за " + s.price().format() + ".");
        });
    }

    public boolean placeOrder(Player buyer, String itemKey, int amount) {
        return guarded(buyer, () -> {
            session.placeOrder(buyer.getUniqueId(), itemKey, amount, knownIngredientKeys(buyer));
            buyer.sendMessage(PREFIX + ChatColor.GREEN
                    + "Замовлення розміщено. Чекайте на пропозиції продавців.");
            persist();
        });
    }

    public boolean offerFromHand(Player seller, UUID orderId, PoundMoney price) {
        return guarded(seller, () -> {
            BuyOrder order = session.openOrders().stream()
                    .filter(o -> o.orderId().equals(orderId)).findFirst()
                    .orElseThrow(() -> new MarketException("Замовлення вже недоступне"));
            ItemStack hand = requireHandItem(seller);
            var classified = classifier.classify(hand).orElseThrow(
                    () -> new MarketException("Це не потойбічна річ"));
            if (!classified.itemKey().equals(order.itemKey()) || hand.getAmount() != order.amount()) {
                throw new MarketException("У руці має бути саме те, що замовлено: потрібна кількість — "
                        + order.amount());
            }
            UUID negotiationId = session.offerOnOrder(seller.getUniqueId(), orderId, price);
            escrow.put(negotiationId, new EscrowEntry(seller.getUniqueId(), hand.clone()));
            seller.getInventory().setItemInMainHand(null);
            seller.sendMessage(PREFIX + ChatColor.GREEN + "Пропозицію зроблено: " + price.format());
            notify(order.buyerId(), PREFIX + ChatColor.YELLOW + session.aliasOf(seller.getUniqueId())
                    + " пропонує ваше замовлення за " + price.format()
                    + ChatColor.GRAY + " — див. «Мої угоди»");
            persist();
        });
    }

    public boolean counter(Player actor, UUID negotiationId, PoundMoney price) {
        return guarded(actor, () -> {
            session.counter(actor.getUniqueId(), negotiationId, price);
            actor.sendMessage(PREFIX + ChatColor.GREEN + "Зустрічна ціна: " + price.format());
            otherParty(negotiationId, actor.getUniqueId()).ifPresent(other -> notify(other,
                    PREFIX + ChatColor.YELLOW + session.aliasOf(actor.getUniqueId())
                            + " дає зустрічну ціну " + price.format()
                            + ChatColor.GRAY + " — див. «Мої угоди»"));
            persist();
        });
    }

    public boolean accept(Player actor, UUID negotiationId) {
        return guarded(actor, () -> {
            NegotiationView view = session.negotiationsOf(actor.getUniqueId()).stream()
                    .filter(n -> n.negotiationId().equals(negotiationId)).findFirst()
                    .orElseThrow(() -> new MarketException("Цей торг уже завершено"));
            Player buyer = Bukkit.getPlayer(view.buyerId());
            if (buyer == null || !buyer.isOnline()) {
                Refund refund = session.withdraw(actor.getUniqueId(), negotiationId);
                releaseEscrow(refund);
                persist();
                throw new MarketException("Покупець покинув збір — торг скасовано");
            }
            AcceptResult result = session.accept(actor.getUniqueId(), negotiationId,
                    walletService.countPounds(buyer), walletService.countCoppets(buyer));
            for (Refund released : result.releasedEscrows()) {
                releaseEscrow(released);
                notify(released.ownerId(), PREFIX + ChatColor.GRAY
                        + "Замовлення закрили без вас — вашу річ повернуто.");
            }
            settle(buyer, result.settlement());
            actor.sendMessage(PREFIX + ChatColor.GREEN + "Угоду укладено: "
                    + result.settlement().price().format());
        });
    }

    public boolean withdraw(Player actor, UUID negotiationId) {
        return guarded(actor, () -> {
            Refund refund = session.withdraw(actor.getUniqueId(), negotiationId);
            releaseEscrow(refund);
            actor.sendMessage(PREFIX + ChatColor.GRAY + "Торг скасовано.");
            otherPartyOfClosed(refund, actor.getUniqueId());
            persist();
        });
    }

    /** Скупка організатором: предмет із руки згорає, монети з'являються (емісія). */
    public boolean buybackFromHand(Player seller) {
        return guarded(seller, () -> {
            ItemStack hand = requireHandItem(seller);
            var classified = classifier.classify(hand).orElseThrow(
                    () -> new MarketException("Посередник: «Таке я не скуповую»"));
            PoundMoney unit = config.buyback().unitPriceFor(
                    classified.category(), classified.sequence(), classified.itemKey());
            if (unit.isZero()) {
                throw new MarketException("Посередник: «За таке я не дам і коппета»");
            }
            PoundMoney total = PoundMoney.ofCoppets(unit.coppets() * hand.getAmount());
            seller.getInventory().setItemInMainHand(null);
            walletService.give(seller, total);
            seller.sendMessage(PREFIX + ChatColor.GREEN + "Посередник забрав "
                    + describe(hand) + ChatColor.GREEN + " і відсипав " + total.format());
        });
    }

    // ── В'ю для GUI ──────────────────────────────────────────────────────────

    public boolean isOpenParticipant(Player player) {
        return phase == GatheringPhase.OPEN && session != null
                && session.isParticipant(player.getUniqueId());
    }

    public List<Lot> activeLots() {
        return session == null ? List.of() : session.activeLots();
    }

    public List<BuyOrder> openOrders() {
        return session == null ? List.of() : session.openOrders();
    }

    public List<NegotiationView> negotiationsOf(UUID playerId) {
        return session == null ? List.of() : session.negotiationsOf(playerId);
    }

    public String aliasOf(UUID playerId) {
        return session == null ? "?" : session.aliasOf(playerId);
    }

    /** Клон ескроу-стака для рендера в GUI (оригінал лишається у сховищі). */
    public ItemStack escrowStack(UUID escrowRef) {
        EscrowEntry entry = escrow.get(escrowRef);
        return entry == null ? null : entry.stack().clone();
    }

    /** itemKey предмета, якщо він валідний для ринку (делегат для GUI). */
    public Optional<String> classifyKey(ItemStack stack) {
        return classifier.classify(stack).map(c -> c.itemKey());
    }

    /** itemKey-и інгредієнтів усіх розблокованих гравцем рецептів. */
    public Set<String> knownIngredientKeys(Player player) {
        Set<String> keys = new HashSet<>();
        for (ItemStack stack : knownIngredientStacks(player)) {
            classifier.classify(stack).ifPresent(c -> keys.add(c.itemKey()));
        }
        return keys;
    }

    /** Стаки-зразки інгредієнтів відомих рецептів (для меню створення замовлення). */
    public List<ItemStack> knownIngredientStacks(Player player) {
        List<ItemStack> stacks = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (UnlockedRecipe recipe : recipeUnlockService.getUnlockedRecipes(player.getUniqueId())) {
            potionManager.getPotionsPathway(recipe.pathwayName()).ifPresent(potions -> {
                ItemStack[] ingredients = potions.getIngredients(recipe.sequence());
                if (ingredients == null) {
                    return;
                }
                for (ItemStack ingredient : ingredients) {
                    classifier.classify(ingredient).ifPresent(c -> {
                        if (seenKeys.add(c.itemKey())) {
                            ItemStack sample = ingredient.clone();
                            sample.setAmount(1);
                            stacks.add(sample);
                        }
                    });
                }
            });
        }
        return stacks;
    }

    // ── Вхід/вихід гравців ───────────────────────────────────────────────────

    public void handleJoin(Player player) {
        UUID id = player.getUniqueId();
        // 1) черга повернень (предмети/монети з минулих сесій)
        List<ItemStack> queued = pendingReturns.remove(id);
        if (queued != null) {
            for (ItemStack stack : queued) {
                player.getInventory().addItem(stack).values()
                        .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            }
            player.sendMessage(PREFIX + ChatColor.GREEN + "Вам повернуто речі зі Зборів.");
            persist();
        }
        // 2) застряг у світі-заглушці (краш/вихід під час збору) → додому
        if (venueProvider.isVenueWorld(player.getWorld()) && !isOpenParticipant(player)) {
            Location home = returnLocations.remove(id);
            if (home == null) {
                ParticipantHome crashHome = crashHomes.remove(id);
                if (crashHome != null && Bukkit.getWorld(crashHome.world()) != null) {
                    home = new Location(Bukkit.getWorld(crashHome.world()), crashHome.x(),
                            crashHome.y(), crashHome.z(), crashHome.yaw(), crashHome.pitch());
                }
            }
            player.teleport(home != null ? home
                    : Bukkit.getWorlds().get(0).getSpawnLocation());
            persist();
        }
    }

    public void handleQuit(Player player) {
        if (!isOpenParticipant(player)) {
            return;
        }
        // Його відкриті торги скасовуються (ескроу продавцям), лоти лишаються висіти
        for (NegotiationView view : session.negotiationsOf(player.getUniqueId())) {
            try {
                Refund refund = session.withdraw(player.getUniqueId(), view.negotiationId());
                releaseEscrow(refund);
            } catch (MarketException ignored) {
                // торг уже закрився паралельно — нічого повертати
            }
        }
        anonymizer.unmask(player);
        persist();
    }

    // ── Приватні хелпери ─────────────────────────────────────────────────────

    private void settle(Player buyer, Settlement s) {
        walletService.charge(buyer, s.price()).orElseThrow(
                () -> new IllegalStateException("Wallet changed between check and charge"));
        EscrowEntry entry = escrow.remove(s.escrowRef());
        if (entry != null) {
            deliverItem(s.payerId(), entry.stack());
        }
        deliverMoney(s.payeeId(), s.sellerProceeds());
        notify(s.payeeId(), PREFIX + ChatColor.GREEN + "Вашу річ продано: +"
                + s.sellerProceeds().format() + ChatColor.GRAY + " (комісія посередника: "
                + s.commissionPaid().format() + ")");
        persist();
    }

    private void releaseEscrow(Refund refund) {
        EscrowEntry entry = escrow.remove(refund.escrowRef());
        if (entry != null) {
            deliverItem(refund.ownerId(), entry.stack());
        }
    }

    private void deliverItem(UUID ownerId, ItemStack stack) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            owner.getInventory().addItem(stack).values()
                    .forEach(rest -> owner.getWorld().dropItem(owner.getLocation(), rest));
        } else {
            queueReturn(ownerId, stack);
        }
    }

    private void deliverMoney(UUID ownerId, PoundMoney money) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            walletService.give(owner, money);
        } else {
            walletService.asStacks(money).forEach(stack -> queueReturn(ownerId, stack));
        }
    }

    private void queueReturn(UUID ownerId, ItemStack stack) {
        pendingReturns.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(stack);
    }

    private void notify(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    private Optional<UUID> otherParty(UUID negotiationId, UUID actorId) {
        return session.negotiationsOf(actorId).stream()
                .filter(n -> n.negotiationId().equals(negotiationId))
                .map(n -> n.sellerId().equals(actorId) ? n.buyerId() : n.sellerId())
                .findFirst();
    }

    private void otherPartyOfClosed(Refund refund, UUID actorId) {
        if (!refund.ownerId().equals(actorId)) {
            notify(refund.ownerId(), PREFIX + ChatColor.GRAY
                    + "Торг скасовано — вашу річ повернуто.");
        }
    }

    private ItemStack requireHandItem(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            throw new MarketException("Візьміть предмет у головну руку");
        }
        return hand;
    }

    private boolean guarded(Player player, Runnable action) {
        if (!isOpenParticipant(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ринок зараз не відкритий для вас.");
            return false;
        }
        try {
            action.run();
            return true;
        } catch (MarketException e) {
            player.sendMessage(PREFIX + ChatColor.RED + e.getMessage());
            return false;
        }
    }

    private void broadcastToParticipants(String message) {
        if (session == null) {
            return;
        }
        for (UUID id : returnLocations.keySet()) {
            notify(id, message);
        }
    }

    private void schedule(Runnable action, long delayTicks) {
        if (delayTicks > 0) {
            phaseTasks.add(Bukkit.getScheduler().runTaskLater(plugin, action, delayTicks));
        }
    }

    private void cancelPhaseTasks() {
        phaseTasks.forEach(BukkitTask::cancel);
        phaseTasks.clear();
    }

    private long intervalMillis() {
        return config.intervalDays() * 24L * 60L * 60L * 1000L;
    }

    private String describe(ItemStack stack) {
        String name = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                ? stack.getItemMeta().getDisplayName()
                : stack.getType().name().toLowerCase().replace('_', ' ');
        return name + ChatColor.RESET + " ×" + stack.getAmount();
    }

    private void persist() {
        List<ParticipantHome> homes = new ArrayList<>();
        returnLocations.forEach((id, loc) -> homes.add(new ParticipantHome(id.toString(),
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch())));
        List<EscrowItem> escrowItems = new ArrayList<>();
        escrow.forEach((ref, entry) -> escrowItems.add(new EscrowItem(
                entry.ownerId().toString(), GatheringSnapshotRepository.encodeStack(entry.stack()))));
        List<EscrowItem> returns = new ArrayList<>();
        pendingReturns.forEach((owner, stacks) -> stacks.forEach(stack -> returns.add(
                new EscrowItem(owner.toString(), GatheringSnapshotRepository.encodeStack(stack)))));
        snapshotRepository.save(new Snapshot(nextGatheringMillis, homes, escrowItems, returns));
    }
}
```

- [ ] **Step 2: Wiring**

`ServiceContainer.initializeApplicationServices()` — в кінці методу:
```java
        this.gatheringService = new GatheringService(plugin, marketConfig, walletService,
                marketItemClassifier, gatheringVenueProvider, gatheringAnonymizer,
                gatheringSnapshotRepository, organizerNpcService, beyonderService,
                recipeUnlockService, potionManager);
```
Поле + геттер `getGatheringService()`.

`MysteriesAbovePlugin.onEnable()` — після `services.startSchedulers();`:
```java
        services.getGatheringService().initializeFromSnapshot();
```
`MysteriesAbovePlugin.onDisable()` — ПЕРЕД `services.saveAll()`:
```java
        if (services != null) {
            services.getGatheringService().forceCloseIfActive();
        }
```

- [ ] **Step 3: Збірка + юніт-регресія**

```powershell
& $mvn -o clean package
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/me/vangoo/application/services/GatheringService.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(market): gathering lifecycle orchestrator with escrow and settlements"
```

---

### Task 14: `GatheringScheduler` + `GatheringListener` (розклад, вхід/вихід, чат, захист зали)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/schedulers/GatheringScheduler.java`
- Create: `src/main/java/me/vangoo/presentation/listeners/GatheringListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (scheduler у `initializeSchedulers` + `startSchedulers`/`stopSchedulers`; listener у `initializeEventListeners`)
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (`registerEvents()`)

**Interfaces:**
- Consumes: `GatheringService` (Task 13), `GatheringVenueProvider` (Task 11).
- Produces: автозапуск оголошення за розкладом; повернення речей/додому при вході; скасування торгів при виході; анонімний чат зали; захист блоків зали.

- [ ] **Step 1: `GatheringScheduler` (патерн `MasteryRegenerationScheduler`)**

```java
package me.vangoo.infrastructure.schedulers;

import me.vangoo.application.services.GatheringService;
import me.vangoo.domain.market.GatheringPhase;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

/** Щохвилини перевіряє, чи настав час оголосити Збори (час персистується в снепшоті). */
public class GatheringScheduler {

    private static final Logger LOGGER = Logger.getLogger(GatheringScheduler.class.getName());
    private static final long CHECK_INTERVAL_TICKS = 60L * 20L;

    private final Plugin plugin;
    private final GatheringService gatheringService;
    private BukkitTask task;

    public GatheringScheduler(Plugin plugin, GatheringService gatheringService) {
        this.plugin = plugin;
        this.gatheringService = gatheringService;
    }

    public void start() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check,
                CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
        LOGGER.info("GatheringScheduler started (1 min interval)");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        LOGGER.info("GatheringScheduler stopped");
    }

    private void check() {
        if (gatheringService.phase() == GatheringPhase.IDLE
                && System.currentTimeMillis() >= gatheringService.nextGatheringMillis()) {
            gatheringService.announce();
        }
    }
}
```

- [ ] **Step 2: `GatheringListener`**

```java
package me.vangoo.presentation.listeners;

import me.vangoo.application.services.GatheringService;
import me.vangoo.infrastructure.market.GatheringVenueProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Побутові події зборів: повернення при вході, скасування торгів при виході,
 * анонімний чат зали («Незнайомець №N»), захист блоків світу-заглушки.
 */
public class GatheringListener implements Listener {

    private final Plugin plugin;
    private final GatheringService gatheringService;
    private final GatheringVenueProvider venueProvider;

    public GatheringListener(Plugin plugin, GatheringService gatheringService,
                             GatheringVenueProvider venueProvider) {
        this.plugin = plugin;
        this.gatheringService = gatheringService;
        this.venueProvider = venueProvider;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Наступний тік: інвентар/світ гравця вже повністю завантажені
        Bukkit.getScheduler().runTask(plugin,
                () -> gatheringService.handleJoin(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gatheringService.handleQuit(event.getPlayer());
    }

    /**
     * Анонімний чат зали. NORMAL: ChatPromptService (LOWEST, Task 15) уже забрав
     * свої відповіді й скасував подію.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (!gatheringService.isOpenParticipant(sender)) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!gatheringService.isOpenParticipant(sender)) {
                return; // збір закрився, поки повідомлення летіло
            }
            String line = ChatColor.DARK_GRAY + gatheringService.aliasOf(sender.getUniqueId())
                    + ChatColor.GRAY + ": " + message;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (gatheringService.isOpenParticipant(online)) {
                    online.sendMessage(line);
                }
            }
            plugin.getLogger().info("[Gathering chat] " + sender.getName() + ": " + message);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        cancelInVenue(event.getPlayer().getUniqueId(), event.getBlock().getWorld(),
                () -> event.setCancelled(true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelInVenue(event.getPlayer().getUniqueId(), event.getBlock().getWorld(),
                () -> event.setCancelled(true));
    }

    private void cancelInVenue(UUID playerId, org.bukkit.World world, Runnable cancel) {
        if (venueProvider.isVenueWorld(world)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.hasPermission("mysteriesabove.admin")) {
                cancel.run();
            }
        }
    }
}
```

- [ ] **Step 3: Wiring**

`ServiceContainer.initializeSchedulers()` — в кінці:
```java
        this.gatheringScheduler = new GatheringScheduler(plugin, gatheringService);
```
> Увага: `initializeSchedulers()` викликається ПІСЛЯ `initializeApplicationServices()` — `gatheringService` уже створений.

У `startSchedulers()` додати `gatheringScheduler.start();`, у `stopSchedulers()`:
```java
        if (gatheringScheduler != null) {
            gatheringScheduler.stop();
        }
```
`initializeEventListeners()` — в кінці:
```java
        this.gatheringListener = new me.vangoo.presentation.listeners.GatheringListener(
                plugin, gatheringService, gatheringVenueProvider);
```
Поля + геттери `getGatheringScheduler()` / `getGatheringListener()`.

`MysteriesAbovePlugin.registerEvents()` — в кінці:
```java
        getServer().getPluginManager().registerEvents(services.getGatheringListener(), this);
```

- [ ] **Step 4: Збірка + commit**

```powershell
& $mvn -o clean package "-DskipTests"
git add src/main/java/me/vangoo/infrastructure/schedulers/GatheringScheduler.java src/main/java/me/vangoo/presentation/listeners/GatheringListener.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(market): weekly scheduler, venue protection and anonymous hall chat"
```

---

### Task 15: UI — `ChatPromptService` + `MarketMenu` (лоти, замовлення, торг)

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/ChatPromptService.java`
- Create: `src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (`registerEvents()`: ChatPromptService)

**Interfaces:**
- Consumes: `GatheringService` в'ю та операції (Task 13), `WalletService` (Task 6), triumph-gui (`Gui`, `PaginatedGui`, `GuiItem` — патерн `RecipeBookMenu`).
- Produces: `ChatPromptService`: `void prompt(Player, String instruction, Consumer<String> handler)` (одноразовий, «скасувати» — відмова); `static PoundMoney parsePrice(String)` (`"2 15"`→2ф15к, `"7"`→7ф; невалідне → `null`). `MarketMenu`: `void openMain(Player)` (використовує Task 17).

- [ ] **Step 1: `ChatPromptService`**

```java
package me.vangoo.presentation.listeners;

import me.vangoo.domain.market.PoundMoney;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Одноразовий чат-промпт (ціна, кількість). LOWEST: забирає відповідь ДО того,
 * як GatheringListener (NORMAL) перетворить її на анонімне повідомлення зали.
 */
public class ChatPromptService implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatPromptService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void prompt(Player player, String instruction, Consumer<String> handler) {
        pending.put(player.getUniqueId(), handler);
        player.closeInventory();
        player.sendMessage(instruction);
        player.sendMessage(ChatColor.DARK_GRAY + "(напишіть «скасувати», щоб відмовитись)");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Consumer<String> handler = pending.remove(event.getPlayer().getUniqueId());
        if (handler == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("скасувати")) {
                player.sendMessage(ChatColor.GRAY + "Скасовано.");
                return;
            }
            handler.accept(message);
        });
    }

    /** «2 15» → 2 ф 15 к; «7» → 7 ф. Невалідний ввід → null. */
    public static PoundMoney parsePrice(String input) {
        try {
            String[] parts = input.trim().split("\\s+");
            int pounds = Integer.parseInt(parts[0]);
            int coppets = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (pounds < 0 || coppets < 0 || parts.length > 2) {
                return null;
            }
            return PoundMoney.of(pounds, coppets);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 2: `MarketMenu`**

```java
package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import me.vangoo.application.services.GatheringService;
import me.vangoo.application.services.WalletService;
import me.vangoo.domain.market.MarketSession.BuyOrder;
import me.vangoo.domain.market.MarketSession.Lot;
import me.vangoo.domain.market.MarketSession.NegotiationView;
import me.vangoo.domain.market.PoundMoney;
import me.vangoo.presentation.listeners.ChatPromptService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Меню підпільного ринку (фаза OPEN): Лоти / Замовлення / Мої угоди.
 * Виставлення лота й оферта — предметом у головній руці; ціни — чат-промптом.
 */
public class MarketMenu {

    private static final String PRICE_HINT =
            ChatColor.GOLD + "Напишіть ціну в чат: «<фунти> <коппети>», напр. «2 15» або «7»";

    private final Plugin plugin;
    private final GatheringService gatheringService;
    private final WalletService walletService;
    private final ChatPromptService prompts;

    public MarketMenu(Plugin plugin, GatheringService gatheringService,
                      WalletService walletService, ChatPromptService prompts) {
        this.plugin = plugin;
        this.gatheringService = gatheringService;
        this.walletService = walletService;
        this.prompts = prompts;
    }

    // ── Головне меню ─────────────────────────────────────────────────────────

    public void openMain(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("🕯 Підпільний ринок").color(NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .rows(3)
                .disableAllInteractions()
                .create();
        gui.setItem(2, 2, new GuiItem(button(Material.CHEST, ChatColor.GOLD + "Лоти",
                        "Купити виставлене іншими"),
                e -> runSynced(player, () -> openLots(player))));
        gui.setItem(2, 4, new GuiItem(button(Material.WRITABLE_BOOK, ChatColor.AQUA + "Замовлення",
                        "Розмістити запит або відповісти на чужий"),
                e -> runSynced(player, () -> openOrders(player))));
        gui.setItem(2, 6, new GuiItem(button(Material.LECTERN, ChatColor.YELLOW + "Мої угоди",
                        "Активні торги: прийняти / зустрічна / відмова"),
                e -> runSynced(player, () -> openNegotiations(player))));
        gui.setItem(2, 8, new GuiItem(button(Material.GOLD_NUGGET, ChatColor.GREEN + "Виставити лот",
                        "Продати предмет із ГОЛОВНОЇ РУКИ за вашу ціну"),
                e -> promptListLot(player)));
        gui.setItem(3, 5, new GuiItem(button(Material.SUNFLOWER,
                ChatColor.GOLD + "Ваш гаманець",
                walletService.countPounds(player) + " ф "
                        + walletService.countCoppets(player) + " к (монетами)")));
        gui.open(player);
    }

    // ── Лоти ─────────────────────────────────────────────────────────────────

    private void openLots(Player player) {
        PaginatedGui gui = paginated("🕯 Лоти");
        for (Lot lot : gatheringService.activeLots()) {
            ItemStack display = gatheringService.escrowStack(lot.lotId());
            if (display == null) {
                continue;
            }
            boolean own = lot.sellerId().equals(player.getUniqueId());
            List<String> lore = new ArrayList<>(List.of(
                    "",
                    ChatColor.GOLD + "Ціна: " + lot.price().format(),
                    ChatColor.DARK_GRAY + "Продавець: " + gatheringService.aliasOf(lot.sellerId()),
                    ""));
            lore.add(own ? ChatColor.GRAY + "Це ваш лот (повернеться після збору, якщо не продано)"
                    : ChatColor.GREEN + "▸ Клацніть, щоб купити");
            appendLore(display, lore);
            gui.addItem(new GuiItem(display, e -> {
                e.setCancelled(true);
                if (own) {
                    return;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                gatheringService.buyLot(player, lot.lotId());
                runSynced(player, () -> openLots(player)); // оновити список
            }));
        }
        gui.open(player);
    }

    private void promptListLot(Player player) {
        prompts.prompt(player, PRICE_HINT + ChatColor.GRAY + " — за предмет у вашій руці",
                withPrice(player, price -> gatheringService.listLotFromHand(player, price)));
    }

    // ── Замовлення ───────────────────────────────────────────────────────────

    private void openOrders(Player player) {
        PaginatedGui gui = paginated("🕯 Замовлення");
        // Кнопка «створити» — фіксований слот унизу
        gui.setItem(6, 5, new GuiItem(button(Material.NETHER_STAR,
                        ChatColor.GREEN + "Створити замовлення",
                        "Обрати інгредієнт із відомих вам рецептів"),
                e -> runSynced(player, () -> openKnownIngredients(player))));
        for (BuyOrder order : gatheringService.openOrders()) {
            boolean own = order.buyerId().equals(player.getUniqueId());
            ItemStack display = button(Material.PAPER,
                    ChatColor.AQUA + "Шукають: " + ChatColor.WHITE + order.itemKey()
                            + " ×" + order.amount(),
                    ChatColor.DARK_GRAY + "Замовник: " + gatheringService.aliasOf(order.buyerId()));
            appendLore(display, List.of("", own
                    ? ChatColor.GRAY + "Це ваше замовлення — чекайте на пропозиції"
                    : ChatColor.GREEN + "▸ Клацніть із предметом у РУЦІ, щоб запропонувати ціну"));
            gui.addItem(new GuiItem(display, e -> {
                e.setCancelled(true);
                if (own) {
                    return;
                }
                prompts.prompt(player, PRICE_HINT + ChatColor.GRAY + " — ваша ціна за це замовлення",
                        withPrice(player, price ->
                                gatheringService.offerFromHand(player, order.orderId(), price)));
            }));
        }
        gui.open(player);
    }

    private void openKnownIngredients(Player player) {
        PaginatedGui gui = paginated("🕯 Інгредієнти ваших рецептів");
        for (ItemStack sample : gatheringService.knownIngredientStacks(player)) {
            String itemKey = gatheringService.classifyKey(sample).orElse(null);
            if (itemKey == null) {
                continue;
            }
            ItemStack display = sample.clone();
            appendLore(display, List.of("", ChatColor.GREEN + "▸ Клацніть, щоб замовити"));
            gui.addItem(new GuiItem(display, e -> {
                e.setCancelled(true);
                prompts.prompt(player, ChatColor.GOLD + "Напишіть кількість (1–64):", input -> {
                    int amount = parseAmount(input);
                    if (amount <= 0) {
                        player.sendMessage(ChatColor.RED + "Невірна кількість.");
                        return;
                    }
                    gatheringService.placeOrder(player, itemKey, amount);
                });
            }));
        }
        gui.open(player);
    }

    // ── Мої угоди (торг) ─────────────────────────────────────────────────────

    private void openNegotiations(Player player) {
        PaginatedGui gui = paginated("🕯 Мої угоди");
        UUID me = player.getUniqueId();
        for (NegotiationView view : gatheringService.negotiationsOf(me)) {
            boolean myTurn = view.turnOf().equals(me);
            boolean iAmSeller = view.sellerId().equals(me);
            UUID other = iAmSeller ? view.buyerId() : view.sellerId();
            ItemStack display = button(Material.BELL,
                    ChatColor.YELLOW + (iAmSeller ? "Ви продаєте: " : "Ви купуєте: ")
                            + ChatColor.WHITE + view.itemKey() + " ×" + view.amount(),
                    ChatColor.GOLD + "Поточна ціна: " + view.currentPrice().format());
            List<String> lore = new ArrayList<>(List.of(
                    ChatColor.DARK_GRAY + "Інша сторона: " + gatheringService.aliasOf(other), ""));
            if (myTurn) {
                lore.add(ChatColor.GREEN + "▸ ЛКМ — прийняти ціну");
                lore.add(ChatColor.AQUA + "▸ ПКМ — зустрічна ціна");
            } else {
                lore.add(ChatColor.GRAY + "Хід іншої сторони...");
            }
            lore.add(ChatColor.RED + "▸ Shift+ПКМ — відмовитись");
            appendLore(display, lore);
            gui.addItem(new GuiItem(display, e -> {
                e.setCancelled(true);
                if (e.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                    gatheringService.withdraw(player, view.negotiationId());
                    runSynced(player, () -> openNegotiations(player));
                } else if (myTurn && e.getClick() == org.bukkit.event.inventory.ClickType.LEFT) {
                    gatheringService.accept(player, view.negotiationId());
                    runSynced(player, () -> openNegotiations(player));
                } else if (myTurn && e.getClick() == org.bukkit.event.inventory.ClickType.RIGHT) {
                    prompts.prompt(player, PRICE_HINT + ChatColor.GRAY + " — ваша зустрічна ціна",
                            withPrice(player, price ->
                                    gatheringService.counter(player, view.negotiationId(), price)));
                }
            }));
        }
        gui.open(player);
    }

    // ── Хелпери ──────────────────────────────────────────────────────────────

    private Consumer<String> withPrice(Player player, Consumer<PoundMoney> action) {
        return input -> {
            PoundMoney price = ChatPromptService.parsePrice(input);
            if (price == null || price.isZero()) {
                player.sendMessage(ChatColor.RED + "Невірна ціна. Формат: «2 15» або «7».");
                return;
            }
            action.accept(price);
        };
    }

    private int parseAmount(String input) {
        try {
            int amount = Integer.parseInt(input.trim());
            return (amount >= 1 && amount <= 64) ? amount : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private PaginatedGui paginated(String title) {
        PaginatedGui gui = Gui.paginated()
                .title(Component.text(title).color(NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .rows(6)
                .pageSize(36)
                .disableAllInteractions()
                .create();
        gui.setItem(6, 3, new GuiItem(button(Material.ARROW, ChatColor.GRAY + "◄ Попередня"),
                e -> gui.previous()));
        gui.setItem(6, 7, new GuiItem(button(Material.ARROW, ChatColor.GRAY + "Наступна ►"),
                e -> gui.next()));
        return gui;
    }

    private void runSynced(Player player, Runnable action) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.1f);
        Bukkit.getScheduler().runTask(plugin, action);
    }

    private ItemStack button(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(ChatColor.GRAY + line);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void appendLore(ItemStack item, List<String> lines) {
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.addAll(lines);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }
}
```

- [ ] **Step 3: Wiring**

`ServiceContainer`: поля `chatPromptService`, `marketMenu`; створення в `initializeUI()`:
```java
        this.chatPromptService = new me.vangoo.presentation.listeners.ChatPromptService(plugin);
        this.marketMenu = new me.vangoo.infrastructure.ui.MarketMenu(
                plugin, gatheringService, walletService, chatPromptService);
```
Геттери `getChatPromptService()` / `getMarketMenu()`.
`MysteriesAbovePlugin.registerEvents()`:
```java
        getServer().getPluginManager().registerEvents(services.getChatPromptService(), this);
```

- [ ] **Step 4: Збірка + commit**

```powershell
& $mvn -o clean package "-DskipTests"
git add src/main/java/me/vangoo/presentation/listeners/ChatPromptService.java src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(market): market menu with lots, orders and haggling flows"
```

---

### Task 16: Скупка через клік по NPC (`OrganizerClickListener`)

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/OrganizerClickListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`, `MysteriesAbovePlugin.java` (`registerEvents()`)

**Interfaces:**
- Consumes: Citizens `NPCRightClickEvent`, `OrganizerNpcService.isOrganizer(NPC)` (Task 12), `GatheringService.buybackFromHand(Player)` (Task 13).

- [ ] **Step 1: Реалізація**

```java
package me.vangoo.presentation.listeners;

import me.vangoo.application.services.GatheringService;
import me.vangoo.infrastructure.citizens.OrganizerNpcService;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** ПКМ по Посереднику з предметом у руці → миттєва скупка за конфіг-прайсом. */
public class OrganizerClickListener implements Listener {

    private final OrganizerNpcService organizerNpc;
    private final GatheringService gatheringService;

    public OrganizerClickListener(OrganizerNpcService organizerNpc, GatheringService gatheringService) {
        this.organizerNpc = organizerNpc;
        this.gatheringService = gatheringService;
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        if (!organizerNpc.isOrganizer(event.getNPC())) {
            return;
        }
        var player = event.getClicker();
        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                    + "Посередник: «Покажи, що приніс — візьми річ у руку.»");
            return;
        }
        gatheringService.buybackFromHand(player);
    }
}
```

- [ ] **Step 2: Wiring**

`ServiceContainer.initializeEventListeners()`:
```java
        this.organizerClickListener = new me.vangoo.presentation.listeners.OrganizerClickListener(
                organizerNpcService, gatheringService);
```
Поле + геттер; у `MysteriesAbovePlugin.registerEvents()`:
```java
        getServer().getPluginManager().registerEvents(services.getOrganizerClickListener(), this);
```

- [ ] **Step 3: Збірка + commit**

```powershell
& $mvn -o clean package "-DskipTests"
git add src/main/java/me/vangoo/presentation/listeners/OrganizerClickListener.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(market): organizer NPC buyback on right click"
```

---

### Task 17: Команда `/gathering` (start|stop|join|menu)

**Files:**
- Create: `src/main/java/me/vangoo/presentation/commands/GatheringCommand.java`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (`registerCommands()`)

**Interfaces:**
- Consumes: `GatheringService.announce()/join()/forceCloseIfActive()/isOpenParticipant()` (Task 13), `MarketMenu.openMain(Player)` (Task 15).
- `join`/`menu` — для всіх гравців (клікабельне [Прийти] виконує `/gathering join`); `start`/`stop` — перевірка `mysteriesabove.admin` У КОДІ, тому в `plugin.yml` команда БЕЗ поля `permission`.

- [ ] **Step 1: Реалізація**

```java
package me.vangoo.presentation.commands;

import me.vangoo.application.services.GatheringService;
import me.vangoo.domain.market.GatheringPhase;
import me.vangoo.infrastructure.ui.MarketMenu;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /gathering join|menu — гравцям (join — ціль клікабельного [Прийти]);
 * /gathering start|stop — адмінам (перевірка права в коді: команда відкрита для всіх).
 */
public class GatheringCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Збори] " + ChatColor.RESET;

    private final GatheringService gatheringService;
    private final MarketMenu marketMenu;

    public GatheringCommand(GatheringService gatheringService, MarketMenu marketMenu) {
        this.gatheringService = gatheringService;
        this.marketMenu = marketMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Використання: /gathering <join|menu>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (!sender.hasPermission("mysteriesabove.admin")) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Недостатньо прав.");
                    return true;
                }
                if (gatheringService.phase() != GatheringPhase.IDLE) {
                    sender.sendMessage(PREFIX + ChatColor.RED
                            + "Збір уже йде (фаза: " + gatheringService.phase() + ").");
                    return true;
                }
                gatheringService.announce();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Збір оголошено.");
            }
            case "stop" -> {
                if (!sender.hasPermission("mysteriesabove.admin")) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Недостатньо прав.");
                    return true;
                }
                gatheringService.forceCloseIfActive();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Збір закрито.");
            }
            case "join" -> {
                if (sender instanceof Player player) {
                    gatheringService.join(player);
                }
            }
            case "menu" -> {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                if (!gatheringService.isOpenParticipant(player)) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Ринок зараз не відкритий для вас.");
                    return true;
                }
                marketMenu.openMain(player);
            }
            default -> sender.sendMessage(PREFIX + ChatColor.GRAY
                    + "Використання: /gathering <join|menu>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("join", "menu"));
        if (sender.hasPermission("mysteriesabove.admin")) {
            options.add("start");
            options.add("stop");
        }
        String lower = args[0].toLowerCase();
        return options.stream().filter(o -> o.startsWith(lower)).toList();
    }
}
```

- [ ] **Step 2: `plugin.yml` — після блоку `coins:` (БЕЗ `permission` — join/menu відкриті всім)**

```yaml
  gathering:
    description: Secret Beyonder gathering (underground market)
    usage: /gathering <join|menu> | /gathering <start|stop> (admin)
```

- [ ] **Step 3: Реєстрація в `registerCommands()`**

```java
        GatheringCommand gatheringCommand = new GatheringCommand(
                services.getGatheringService(), services.getMarketMenu());
        getCommand("gathering").setExecutor(gatheringCommand);
        getCommand("gathering").setTabCompleter(gatheringCommand);
```

- [ ] **Step 4: Збірка + commit**

```powershell
& $mvn -o clean package "-DskipTests"
git add src/main/java/me/vangoo/presentation/commands/GatheringCommand.java src/main/resources/plugin.yml src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(market): /gathering command with join and menu subcommands"
```

---

### Task 18: Документація + фінальна перевірка

**Files:**
- Create: `.claude/rules/market-gathering.md`
- Modify: `CLAUDE.md` (розділ «Config & persistence» + команди)
- Modify: `docs/superpowers/specs/2026-07-10-ingredient-economy-market-design.md` (слоти-приймачі → предмет у руці)

- [ ] **Step 1: `.claude/rules/market-gathering.md`**

```markdown
# Підпільний ринок (Збори Потойбічних)

## Механізм

- Фази `IDLE → ANNOUNCED → OPEN → CLOSING → IDLE` (`domain.market.GatheringPhase`);
  веде `GatheringService` (application); автозапуск — `GatheringScheduler`
  (щохвилини звіряє персистований час наступного збору).
- Правила грошей/лотів/торгу — ЧИСТИЙ `domain.market` (`PoundMoney`, `CoinChange`,
  `MarketSession`, `BuybackPriceTable`) з юніт-тестами; пакет у `PURE_DOMAIN`
  `ArchitectureTest`. Ефекти (телепорт, профілі, NPC, GUI) — application/infrastructure.
- Домен ВІДДАЄ команди (`Settlement`, `Refund`) — виконує їх `GatheringService`.
  ІНВАРІАНТ: кожен ескроу-предмет має рівно один вихід (угода або повернення);
  захищено `MarketNegotiationTest.conservationInvariantHolds`.
- Гроші: вся математика в коппетах (1 фунт = 20 коппетів). Емісія: скупка NPC
  (`BuybackPriceTable`, конфіг `market.buyback.*`) + лут (`currency:pound` /
  `currency:coppet` у `global_loot.yml`). Сток: комісія `market.commission-rate` (згорає).
- Краш-безпека: `gathering-state.json` (`GatheringSnapshotRepository`) пишеться після
  кожної мутації; відновлення НЕ продовжує сесію — усе в чергу повернень
  (видається лістенером при вході гравця).
- Анонімність (на час OPEN): Paper `setPlayerProfile` без `textures` (дефолтний скін),
  scoreboard-team `ma_gathering` ховає ніки, чат зали перехоплює `GatheringListener`
  і подає від «Незнайомець №N» (`MarketSession.aliasOf`).
- Виставлення лота / оферта / скупка — предметом у ГОЛОВНІЙ РУЦІ (не GUI-слоти):
  менша поверхня дюпів. Ціни/кількість — через `ChatPromptService` (одноразовий чат-промпт).

## Як додати товарну категорію на ринок

1. Значення в `MarketItemCategory` (domain) + гілка в `MarketItemClassifier.classify`
   (порядок перевірок: Характеристика → книга рецептів → custom item; зілля/ванільне/монети → empty).
2. Ціна скупки: `BuybackPriceTable.unitPriceFor` + секція `market.buyback.*` у config.yml
   (+ кейс у `BuybackPriceTableTest`).

## Заборони

- ❌ Створювати монети повз `CurrencyCodec`/`WalletService` — канали емісії лише:
  скупка організатором, лут, `/coins give`.
- ❌ Мутувати `MarketSession` повз `GatheringService` — обхід ескроу і снепшота.
- ❌ Готові зілля на ринку — свідомо заборонені (пряма купівля просування).
- ❌ Товар у лут-таблицях із префіксом `characteristic:` — інваріант Спеку 1 лишається.
```

- [ ] **Step 2: CLAUDE.md — розділ «Config & persistence»**

Додати до списку конфігів у першому буліті: `` `config.yml` (секції `creatures.*`, `market.*`) ``.
Додати новий буліт після рядка про `beyonders.json`:
```markdown
- Підпільний ринок: стан зборів персиститься в `gathering-state.json`
  (`GatheringSnapshotRepository`; час наступного збору + ескроу + черга повернень);
  світ-заглушка `mysteries_gathering` створюється ідемпотентно. Див. `.claude/rules/market-gathering.md`.
```
У рядку про адмін-команди додати `/coins`; додати речення: «`/gathering` — гравецька команда (join/menu), її start/stop — адмінські (перевірка права в коді).»

- [ ] **Step 3: Оновити спек** (`2026-07-10-ingredient-economy-market-design.md`) — три відхилення, ухвалені на етапі планування:

1. Розділ «GUI»: слоти-приймачі й «меню скупки організатора» замінити на —
   > Виставлення лота, оферта на замовлення і скупка виконуються **предметом у головній руці** (ціна/кількість — чат-промптом `ChatPromptService`); скупка — ПКМ по NPC із предметом у руці. Менша поверхня дюпів, ніж GUI-слоти-приймачі.
2. Розділ «Wiring, конфіг, команди»: ключ `loot.pound-chance` прибрати — фунти/коппети в луті керуються **вагами записів `global_loot.yml`** (`currency:pound`, `currency:coppet`), як усі інші лут-предмети.
3. Там само: зазначити, що `/gathering` у `plugin.yml` — без `permission` (join/menu відкриті всім), а право `mysteriesabove.admin` для start/stop перевіряється в коді.

- [ ] **Step 4: Повна збірка з усіма тестами**

```powershell
& $mvn -o clean package
```
Expected: BUILD SUCCESS; усі тести зелені (`PoundMoneyTest`, `GatheringPhaseTest`, `MarketSessionTest`, `MarketNegotiationTest`, `BuybackPriceTableTest`, `ArchitectureTest`, решта наявних).

- [ ] **Step 5: Commit**

```powershell
git add .claude/rules/market-gathering.md CLAUDE.md docs/superpowers/specs/2026-07-10-ingredient-economy-market-design.md
git commit -m "docs(market): gathering mechanism rules and persistence notes"
```

- [ ] **Step 6: Ручний in-server чекліст (JAR у plugins тестового Paper-сервера)**

1. `/coins give <нік> 5 10` → у інвентарі 5 «Золотий фунт» + 10 «Коппет».
2. `/gathering start` → Beyonder'и бачать оголошення; клік **[Прийти]** → підтвердження.
3. Через 5 хв — телепорт у чорну залу; всі гравці з дефолтним скіном, без ніків; NPC «Посередник» вітає.
4. Чат у залі → «Незнайомець №N: ...» для всіх учасників.
5. `/gathering menu` → «Виставити лот» із інгредієнтом у руці → ціна «1 5» → лот видно другому гравцеві анонімно; купівля → монети зняті з розміном, продавцю прийшло `ціна − комісія`.
6. Замовлення: створити (список лише з інгредієнтів РОЗБЛОКОВАНИХ рецептів) → другий гравець із предметом у руці пропонує ціну → торг (зустрічна → прийняти) → предмет/гроші передані.
7. ПКМ по NPC з інгредієнтом у руці → скупка за конфіг-ціною (предмет зник, монети прийшли).
8. Дочекатися закриття (або `/gathering stop`) → непродане повернуто, всі вдома, скіни/ніки відновлені, NPC зник.
9. **Краш-тест:** під час OPEN з виставленим лотом убити процес сервера → рестарт → лот у черзі повернень, при вході гравець отримує предмет і телепортується додому.
10. **Офлайн-тест:** продати лот гравця, що вийшов під час OPEN → монети видані йому при наступному вході.
11. Блоки в залі не ламаються/не ставляться (без admin-права).
12. Перевірити `gathering-state.json`: час наступного збору ≈ тепер + 7 днів.

---

## Порядок виконання і залежності

```
1 (гроші) → 3 (лоти) → 4 (торг) ─┐
2 (фази) ────────────────────────┤
5 (прайс скупки) ────────────────┼→ 13 (GatheringService) → 14 (scheduler+listeners)
6 (монети+гаманець) → 7 (/coins) │        ↓
                    → 8 (лут)    │   15 (MarketMenu) → 16 (NPC-скупка) → 17 (/gathering) → 18 (docs+фінал)
9 (конфіг+класифікатор) ─────────┤
10 (снепшот) ────────────────────┤
11 (світ+анонімність) ───────────┤
12 (NPC) ────────────────────────┘
```
Tasks 1–12 — переважно незалежні між собою (крім 3→4 і 6→7/8); Task 13 збирає все.






