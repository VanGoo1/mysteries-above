# Market Barter, Sale Confirmation & Gathering Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the characteristic buyback gap, add a sale-confirmation dialog, let market prices be items (± a currency boot) instead of only currency, and spread attendees around the gathering room instead of piling them inside the Посередник.

**Architecture:** A new pure `domain.market.Consideration` value object generalises "price" (item demand + optional currency) and replaces `PoundMoney` in `Lot`/`Negotiation`/`Settlement`. Escrow stays single-sided (only the seller's good is escrowed; the buyer's item is pulled atomically at settlement), so the snapshot schema and conservation invariant are untouched. Bukkit effects (off-hand demand entry, a reusable confirmation GUI, spawn distribution) live in the effects/UI layer; balance/rules stay pure and unit-tested.

**Tech Stack:** Java 21, Spigot/Paper API 1.21, JUnit 5, triumph-gui, Citizens API. No new dependencies.

## Global Constraints

- **Language:** all user-facing strings in **Ukrainian** (`getName`/descriptions/messages/GUI); identifiers, logs, config keys in English (per `.claude/rules/localization.md`).
- **Commits:** Conventional Commits, English only, imperative subject ≤ 72 chars, no em-dash separator; one logical change per commit (per `.claude/rules/commit-messages.md`). End every commit message body with:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
- **Domain purity:** `domain.market` must not import `org.bukkit..`, `dev.triumphteam..`, `net.kyori..`; it is pinned in `ArchitectureTest` `PURE_DOMAIN`. Balance math + rules → pure VO with a unit test; Bukkit effects → application/infrastructure.
- **Money math:** all in coppets (1 pound = 20 coppets = `PoundMoney.COPPETS_PER_POUND`).
- **Escrow invariant:** every escrow item has exactly one exit (settlement or refund); the buyer's barter item is never escrowed (pulled atomically at settlement).
- **Currency emission channels stay limited to** buyback, loot, `/coins give` — never mint coins elsewhere.
- **Maven is NOT on PATH.** Run tests via IntelliJ's bundled Maven (offline):
  ```powershell
  $mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"
  & $mvn -o test -Dtest=SomeTest
  ```
  Full build: `& $mvn -o clean package`. Test runs emit harmless SLF4J NOP warnings. The IDE version segment can change — if the path is missing, locate it with `Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd`.

---

## File Structure

**Create:**
- `src/main/java/me/vangoo/domain/market/Consideration.java` — price VO (item demand + money).
- `src/test/java/me/vangoo/domain/market/ConsiderationTest.java` — VO unit tests.
- `src/main/java/me/vangoo/infrastructure/market/VenueLayout.java` — pure spawn geometry.
- `src/test/java/me/vangoo/infrastructure/market/VenueLayoutTest.java` — geometry tests.
- `src/main/java/me/vangoo/infrastructure/ui/ConfirmationMenu.java` — reusable give/get confirm GUI.

**Modify:**
- `src/main/resources/config.yml` — buyback characteristic prices for Seq 4→0.
- `src/test/java/me/vangoo/domain/market/BuybackPriceTableTest.java` — full-range guard.
- `src/main/java/me/vangoo/domain/market/MarketSession.java` — `Consideration` in Lot/Negotiation/Settlement + methods.
- `src/test/java/me/vangoo/domain/market/MarketNegotiationTest.java` — new price type + barter cases.
- `src/main/java/me/vangoo/application/services/GatheringService.java` — barter settlement, pre-checks, buyback quote, demand plumbing, spawn distribution call.
- `src/main/java/me/vangoo/infrastructure/market/GatheringVenueProvider.java` — `organizerSpawn()` / `attendeeSpawn()`.
- `src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java` — off-hand demand entry + confirm on barter accept/buy.
- `src/main/java/me/vangoo/presentation/listeners/OrganizerClickListener.java` — buyback confirm.
- `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` — construct `ConfirmationMenu`, reorder `marketItemNamer`, thread new deps.
- `.claude/rules/market-gathering.md` — document the new mechanics.

---

## Task 1: Fix characteristic buyback gap (config + guard test)

**Files:**
- Modify: `src/main/resources/config.yml:26-32`
- Test: `src/test/java/me/vangoo/domain/market/BuybackPriceTableTest.java`

**Interfaces:**
- Consumes: `BuybackPriceTable.unitPriceFor(MarketItemCategory, int sequenceOrMinus1, String itemKey)` → `PoundMoney` (existing).
- Produces: nothing new; data + regression guard only.

- [ ] **Step 1: Write the guard test**

Add to `BuybackPriceTableTest` (a table populated for every sequence must price them all — documents the completeness contract the config must satisfy):

```java
@Test
void completeTablePricesEverySequence() {
    java.util.Map<Integer, Integer> full = java.util.Map.of(
            9, 80, 8, 130, 7, 220, 6, 360, 5, 540,
            4, 780, 3, 1100, 2, 1500, 1, 2000, 0, 2600);
    BuybackPriceTable complete = new BuybackPriceTable(2, 200, full, java.util.Map.of());
    for (int seq = 0; seq <= 9; seq++) {
        assertTrue(complete.unitPriceFor(
                        MarketItemCategory.CHARACTERISTIC, seq, "characteristic:Fool:" + seq).coppets() > 0,
                "characteristic seq " + seq + " must have a non-zero buyback price");
    }
}
```

- [ ] **Step 2: Run it — verify it passes**

Run: `& $mvn -o test -Dtest=BuybackPriceTableTest`
Expected: PASS (this documents intent; the real gap is in config, fixed next). Keep the existing `unknownCharacteristicSequenceFallsBackToZero` test unchanged — it verifies the fallback mechanism on a deliberately-sparse table.

- [ ] **Step 3: Fill the config for Seq 4→0**

Replace `config.yml` lines 26–31 (`characteristic-coppets-by-seq:` block, currently 9→5) with the full range:

```yaml
    characteristic-coppets-by-seq:
      "9": 80
      "8": 130
      "7": 220
      "6": 360
      "5": 540
      "4": 780     # нижчі послідовності — сильніші, дорожча скупка
      "3": 1100
      "2": 1500
      "1": 2000
      "0": 2600
```

- [ ] **Step 4: Full build to confirm resources still filter**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS (config.yml is Maven-filtered; this proves it still loads).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/config.yml src/test/java/me/vangoo/domain/market/BuybackPriceTableTest.java
git commit -F- <<'EOF'
fix(market): price characteristic buyback for every sequence

Sequences 4 to 0 had no buyback entry and fell back to zero, so the
organizer refused the strongest characteristics. Fill the full 9..0 range
and guard completeness with a test.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

**In-server verification (later):** at a gathering, right-click Посередник holding a Seq-0 characteristic → he now quotes a price instead of "не дам і коппета".

---

## Task 2: `Consideration` value object (TDD)

**Files:**
- Create: `src/main/java/me/vangoo/domain/market/Consideration.java`
- Test: `src/test/java/me/vangoo/domain/market/ConsiderationTest.java`

**Interfaces:**
- Consumes: `PoundMoney` (existing: `isZero()`, `commission(double)`, `ofCoppets`, `of`).
- Produces:
  - `Consideration.ItemDemand(String itemKey, int amount)` (record; amount > 0).
  - `Consideration.money(PoundMoney)` → currency-only price.
  - `Consideration.of(ItemDemand item, PoundMoney boot)` → item ± boot (boot may be zero).
  - `Consideration.item()` → `ItemDemand` or `null`; `money()` → `PoundMoney`.
  - `isBarter()` → boolean; `hasMoney()` → boolean; `commission(double rate)` → `PoundMoney`.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.domain.market;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsiderationTest {

    private static final Consideration.ItemDemand DEMAND =
            new Consideration.ItemDemand("characteristic:Door:7", 1);

    @Test
    void moneyOnlyIsNotBarterAndPaysCommission() {
        Consideration c = Consideration.money(PoundMoney.ofCoppets(20));
        assertFalse(c.isBarter());
        assertNull(c.item());
        assertTrue(c.hasMoney());
        assertEquals(2, c.commission(0.10).coppets()); // ceil(20 * 0.10)
    }

    @Test
    void pureItemIsBarterAndChargesNoCommission() {
        Consideration c = Consideration.of(DEMAND, PoundMoney.ofCoppets(0));
        assertTrue(c.isBarter());
        assertEquals(DEMAND, c.item());
        assertFalse(c.hasMoney());
        assertEquals(0, c.commission(0.10).coppets()); // barter never pays commission
    }

    @Test
    void itemWithBootIsBarterAndStillChargesNoCommission() {
        Consideration c = Consideration.of(DEMAND, PoundMoney.ofCoppets(60));
        assertTrue(c.isBarter());
        assertTrue(c.hasMoney());
        assertEquals(60, c.money().coppets());
        assertEquals(0, c.commission(0.10).coppets());
    }

    @Test
    void emptyConsiderationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Consideration.money(PoundMoney.ofCoppets(0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Consideration.ItemDemand("x", 0));
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `& $mvn -o test -Dtest=ConsiderationTest`
Expected: FAIL / compile error (class `Consideration` not found).

- [ ] **Step 3: Implement `Consideration`**

```java
package me.vangoo.domain.market;

/**
 * Ціна на ринку: предметна вимога (обмін) та/або гроші (буст). Мінімум одне з двох
 * присутнє. Чисте правило — жодного Bukkit. Комісія організатора береться лише з
 * ЧИСТО грошових угод; будь-яка предметна складова робить угоду бартером без комісії.
 */
public record Consideration(ItemDemand item, PoundMoney money) {

    /** Скільки й чого треба віддати як предметну частину ціни (amount > 0). */
    public record ItemDemand(String itemKey, int amount) {
        public ItemDemand {
            if (itemKey == null || itemKey.isEmpty()) {
                throw new IllegalArgumentException("itemKey не може бути порожнім");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("Кількість має бути додатною: " + amount);
            }
        }
    }

    public Consideration {
        if (money == null) {
            throw new IllegalArgumentException("money не може бути null (використай PoundMoney.ofCoppets(0))");
        }
        if (item == null && money.isZero()) {
            throw new IllegalArgumentException("Ціна не може бути порожньою: потрібні гроші або предмет");
        }
    }

    /** Чисто грошова ціна (як було до бартера). */
    public static Consideration money(PoundMoney money) {
        return new Consideration(null, money);
    }

    /** Предметна ціна з опційним грошовим бустом (boot може бути 0). */
    public static Consideration of(ItemDemand item, PoundMoney boot) {
        if (item == null) {
            throw new IllegalArgumentException("item не може бути null для бартерної ціни");
        }
        return new Consideration(item, boot);
    }

    public boolean isBarter() {
        return item != null;
    }

    public boolean hasMoney() {
        return !money.isZero();
    }

    /** Комісія: нуль для бартеру, ceil(гроші×rate) для чисто грошової угоди. */
    public PoundMoney commission(double rate) {
        return isBarter() ? PoundMoney.ofCoppets(0) : money.commission(rate);
    }
}
```

- [ ] **Step 4: Run test — verify it passes**

Run: `& $mvn -o test -Dtest=ConsiderationTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/market/Consideration.java src/test/java/me/vangoo/domain/market/ConsiderationTest.java
git commit -F- <<'EOF'
feat(market): add Consideration price value object for barter

Item demand plus optional currency boot; barter charges no commission.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 3: Migrate `MarketSession`/`Settlement` to `Consideration` (currency-only behaviour preserved)

Flip the price type across the domain and all call sites in one atomic change; behaviour stays currency-only (every price is built via `Consideration.money(...)`). This keeps the whole project compiling and green before the item dimension is added in Task 6/7.

**Files:**
- Modify: `src/main/java/me/vangoo/domain/market/MarketSession.java`
- Modify: `src/test/java/me/vangoo/domain/market/MarketNegotiationTest.java`
- Modify: `src/main/java/me/vangoo/application/services/GatheringService.java` (call sites + messages only)
- Modify: `src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java` (display `.money().format()`)

**Interfaces:**
- Consumes: `Consideration` (Task 2).
- Produces (new domain signatures later tasks rely on):
  - `Lot(UUID lotId, UUID sellerId, String itemKey, int amount, Consideration price, boolean sold)`
  - `Settlement(UUID payerId, UUID payeeId, Consideration price, PoundMoney commissionPaid, PoundMoney sellerProceeds, CoinChange payerCharge, UUID escrowRef, String itemKey, int amount)`
  - `NegotiationView(..., Consideration currentPrice, ...)`
  - `listLot(UUID sellerId, String itemKey, int amount, Consideration price)`
  - `offerOnOrder(UUID sellerId, UUID orderId, Consideration price)`
  - `counter(UUID actorId, UUID negId, Consideration price)`
  - `buyLot(UUID buyerId, UUID lotId, int buyerPounds, int buyerCoppets)` (signature unchanged)
  - `accept(UUID actorId, UUID negId, int buyerPounds, int buyerCoppets)` (signature unchanged)

- [ ] **Step 1: Update the domain tests to the new price type (red)**

In `MarketNegotiationTest`, replace every `PoundMoney.ofCoppets(n)` price argument with `Consideration.money(PoundMoney.ofCoppets(n))`, and every price assertion `s.price().coppets()` with `s.price().money().coppets()`. Concretely:

- `listLot(..., PoundMoney.ofCoppets(30))` → `listLot(..., Consideration.money(PoundMoney.ofCoppets(30)))` (all occurrences).
- `offerOnOrder(..., PoundMoney.ofCoppets(20))` → `offerOnOrder(..., Consideration.money(PoundMoney.ofCoppets(20)))` (all occurrences).
- `counter(..., PoundMoney.ofCoppets(14))` → `counter(..., Consideration.money(PoundMoney.ofCoppets(14)))` (all occurrences).
- `buyLot(..., PoundMoney.ofCoppets(10))` — note `buyLot` takes `int, int` not a price; leave those unchanged.
- In `turnAlternatesAndOnlyReceiverAccepts`: `assertEquals(14, s.price().coppets())` → `assertEquals(14, s.price().money().coppets())`.
- In `conservationInvariantHolds`: `assertEquals(s.price().coppets(), ...)` → `assertEquals(s.price().money().coppets(), ...)` (both the sellerProceeds+commission line and the payerCharge line).
- Add import: `import me.vangoo.domain.market.Consideration;`

Add a new barter test (drives the domain's commission-free + item-echo behaviour):

```java
@Test
void barterOfferChargesNoCommissionAndEchoesDemand() {
    Consideration.ItemDemand demand = new Consideration.ItemDemand("characteristic:Door:7", 1);
    UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
    UUID negId = session.offerOnOrder(sellerA, orderId,
            Consideration.of(demand, PoundMoney.ofCoppets(0)));
    AcceptResult result = session.accept(buyer, negId, 0, 0); // no money needed for pure barter
    Settlement s = result.settlement();
    assertTrue(s.price().isBarter());
    assertEquals(demand, s.price().item());
    assertEquals(0, s.commissionPaid().coppets());
    assertEquals(0, s.sellerProceeds().coppets());
    assertEquals(0, s.payerCharge().paidCoppets());
}

@Test
void barterWithBootMovesBootWithoutCommission() {
    Consideration.ItemDemand demand = new Consideration.ItemDemand("custom:crimson_star", 2);
    UUID lotId = session.listLot(sellerA, "custom:dimensional_wanderer_eye", 1,
            Consideration.of(demand, PoundMoney.ofCoppets(40)));
    Settlement s = session.buyLot(buyer, lotId, 2, 0); // 2 pounds = 40 coppets boot
    assertTrue(s.price().isBarter());
    assertEquals(0, s.commissionPaid().coppets());   // barter: no commission even on boot
    assertEquals(40, s.sellerProceeds().coppets());  // full boot to seller
    assertEquals(40, s.payerCharge().paidCoppets());
}
```

- [ ] **Step 2: Run tests — verify they fail to compile**

Run: `& $mvn -o test -Dtest=MarketNegotiationTest`
Expected: FAIL (compile errors — `listLot`/`offerOnOrder`/`counter` still take `PoundMoney`, `Settlement.price()` still `PoundMoney`).

- [ ] **Step 3: Change `MarketSession` records and methods**

Edit `MarketSession.java`:

1. `Lot` record — change `PoundMoney price` → `Consideration price`:
```java
public record Lot(UUID lotId, UUID sellerId, String itemKey, int amount,
                  Consideration price, boolean sold) {}
```

2. `Settlement` record — change `PoundMoney price` → `Consideration price` (keep the rest):
```java
public record Settlement(UUID payerId, UUID payeeId, Consideration price, PoundMoney commissionPaid,
                         PoundMoney sellerProceeds, CoinChange payerCharge,
                         UUID escrowRef, String itemKey, int amount) {}
```

3. `NegotiationView` — change `PoundMoney currentPrice` → `Consideration currentPrice`:
```java
public record NegotiationView(UUID negotiationId, UUID orderId, UUID sellerId, UUID buyerId,
                              Consideration currentPrice, UUID turnOf, NegotiationState state,
                              String itemKey, int amount) {}
```

4. `Negotiation` inner class field: `PoundMoney currentPrice;` → `Consideration currentPrice;`

5. `listLot`:
```java
public UUID listLot(UUID sellerId, String itemKey, int amount, Consideration price) {
    ensureOpen();
    ensureParticipant(sellerId);
    if (amount <= 0) {
        throw new MarketException("Кількість має бути додатною");
    }
    // Consideration's own invariant guarantees price is non-empty (money > 0 or an item demand).
    UUID lotId = UUID.randomUUID();
    lots.put(lotId, new Lot(lotId, sellerId, itemKey, amount, price, false));
    return lotId;
}
```

6. `buyLot` — charge the money portion, commission via `Consideration`:
```java
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
    CoinChange charge = CoinChange.make(buyerPounds, buyerCoppets, lot.price().money())
            .orElseThrow(() -> new MarketException("Недостатньо монет: потрібно " + lot.price().money().format()));
    lots.put(lotId, new Lot(lot.lotId(), lot.sellerId(), lot.itemKey(), lot.amount(), lot.price(), true));
    PoundMoney commission = lot.price().commission(commissionRate);
    return new Settlement(buyerId, lot.sellerId(), lot.price(), commission,
            lot.price().money().minus(commission), charge, lotId, lot.itemKey(), lot.amount());
}
```

7. `offerOnOrder`:
```java
public UUID offerOnOrder(UUID sellerId, UUID orderId, Consideration price) {
    ensureOpen();
    ensureParticipant(sellerId);
    BuyOrder order = orders.get(orderId);
    if (order == null || order.fulfilled()) {
        throw new MarketException("Замовлення вже недоступне");
    }
    if (order.buyerId().equals(sellerId)) {
        throw new MarketException("Це ваше власне замовлення");
    }
    UUID negotiationId = UUID.randomUUID();
    Negotiation negotiation = new Negotiation(negotiationId, orderId, sellerId, order.buyerId());
    negotiation.currentPrice = price;
    negotiation.turnOf = order.buyerId();
    negotiations.put(negotiationId, negotiation);
    return negotiationId;
}
```

8. `counter`:
```java
public void counter(UUID actorId, UUID negotiationId, Consideration newPrice) {
    ensureOpen();
    Negotiation negotiation = openNegotiation(negotiationId);
    if (!negotiation.turnOf.equals(actorId)) {
        throw new MarketException("Зараз не ваш хід у цьому торзі");
    }
    negotiation.currentPrice = newPrice;
    negotiation.turnOf = actorId.equals(negotiation.buyerId)
            ? negotiation.sellerId : negotiation.buyerId;
}
```

9. `accept` — money portion + commission via `Consideration`:
```java
public AcceptResult accept(UUID actorId, UUID negotiationId, int buyerPounds, int buyerCoppets) {
    ensureOpen();
    Negotiation negotiation = openNegotiation(negotiationId);
    if (!negotiation.turnOf.equals(actorId)) {
        throw new MarketException("Прийняти може лише той, хто отримав останню ціну");
    }
    BuyOrder order = orders.get(negotiation.orderId);
    CoinChange charge = CoinChange.make(buyerPounds, buyerCoppets, negotiation.currentPrice.money())
            .orElseThrow(() -> new MarketException(
                    "У покупця недостатньо монет: потрібно " + negotiation.currentPrice.money().format()));
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
            negotiation.currentPrice, commission, negotiation.currentPrice.money().minus(commission),
            charge, negotiationId, order.itemKey(), order.amount());
    return new AcceptResult(settlement, released);
}
```

10. `negotiationsOf` — the `NegotiationView` constructor call now passes `n.currentPrice` (a `Consideration`); no code change beyond the field type already being `Consideration`. Verify it compiles.

- [ ] **Step 4: Update `GatheringService` call sites (currency-only wrapping)**

In `GatheringService.java`:

- `listLotFromHand(Player seller, PoundMoney price)` — wrap the price:
  change `session.listLot(seller.getUniqueId(), classified.itemKey(), hand.getAmount(), price)`
  → `session.listLot(seller.getUniqueId(), classified.itemKey(), hand.getAmount(), Consideration.money(price))`.
- `offerFromHand(Player seller, UUID orderId, PoundMoney price)` — change
  `session.offerOnOrder(seller.getUniqueId(), orderId, price)`
  → `session.offerOnOrder(seller.getUniqueId(), orderId, Consideration.money(price))`;
  and the notify line `... + price.format() ...` stays (still `PoundMoney`).
- `counter(Player actor, UUID negotiationId, PoundMoney price)` — change
  `session.counter(actor.getUniqueId(), negotiationId, price)`
  → `session.counter(actor.getUniqueId(), negotiationId, Consideration.money(price))`.
- `buyLot`: message `"Куплено за " + s.price().format()` → `"Куплено за " + s.price().money().format()`.
- `accept`: message `result.settlement().price().format()` → `result.settlement().price().money().format()`.
- Add import `import me.vangoo.domain.market.Consideration;`.

(Leave `settle(...)`, `sellerProceeds().format()`, `commissionPaid().format()` unchanged — those are `PoundMoney`.)

- [ ] **Step 5: Update `MarketMenu` display call sites**

In `MarketMenu.java`, prices are read for display:
- `openLots`: `ChatColor.GOLD + "Ціна: " + lot.price().format()` → `... + lot.price().money().format()`.
- `openNegotiations`: `ChatColor.GOLD + "Поточна ціна: " + view.currentPrice().format()` → `... + view.currentPrice().money().format()`.

(These become full item±boot descriptions in Task 7; money-only for now.)

- [ ] **Step 6: Run the market test suite — verify green**

Run: `& $mvn -o test -Dtest=MarketNegotiationTest+ConsiderationTest+BuybackPriceTableTest`
Expected: PASS (existing cases migrated + 2 new barter cases).

- [ ] **Step 7: Full build**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS (all call sites migrated; project compiles).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/vangoo/domain/market/MarketSession.java src/test/java/me/vangoo/domain/market/MarketNegotiationTest.java src/main/java/me/vangoo/application/services/GatheringService.java src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java
git commit -F- <<'EOF'
refactor(market): quote prices as Consideration instead of PoundMoney

Lot, negotiation, and settlement prices now carry an optional item demand
alongside currency. Behaviour is unchanged: all prices are currency-only
until barter entry is wired. Commission is computed via Consideration
(zero for barter).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 4: Distribute attendee spawns (pure geometry + venue wiring)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/market/VenueLayout.java`
- Test: `src/test/java/me/vangoo/infrastructure/market/VenueLayoutTest.java`
- Modify: `src/main/java/me/vangoo/infrastructure/market/GatheringVenueProvider.java`
- Modify: `src/main/java/me/vangoo/application/services/GatheringService.java` (`open()`)

**Interfaces:**
- Produces:
  - `VenueLayout.Spot(double x, double z, float yaw)` (record).
  - `VenueLayout.organizer()` → `Spot` at the room head, facing attendees.
  - `VenueLayout.attendee(int index, int total)` → `Spot`, distinct per index, within floor bounds, facing the organizer.
  - `GatheringVenueProvider.organizerSpawn()` → `Location`.
  - `GatheringVenueProvider.attendeeSpawn(int index, int total)` → `Location`.

- [ ] **Step 1: Write the failing geometry test**

```java
package me.vangoo.infrastructure.market;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class VenueLayoutTest {

    @Test
    void attendeesAreDistinctAndWithinBounds() {
        for (int total = 1; total <= 16; total++) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < total; i++) {
                VenueLayout.Spot s = VenueLayout.attendee(i, total);
                assertTrue(seen.add(s.x() + "," + s.z()),
                        "spot " + i + "/" + total + " duplicated");
                assertTrue(s.x() >= -7.5 && s.x() <= 7.5, "x in bounds: " + s.x());
                assertTrue(s.z() >= -7.5 && s.z() <= 7.5, "z in bounds: " + s.z());
            }
        }
    }

    @Test
    void attendeesStandInFrontOfOrganizerFacingBack() {
        VenueLayout.Spot org = VenueLayout.organizer();
        VenueLayout.Spot a = VenueLayout.attendee(0, 4);
        assertTrue(a.z() > org.z(), "attendees are on the +z side of the organizer");
        // organizer faces +z (toward attendees); attendees face -z (toward organizer)
        assertEquals(0f, org.yaw(), 0.5f);
        assertEquals(180f, a.yaw(), 45f);
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `& $mvn -o test -Dtest=VenueLayoutTest`
Expected: FAIL (compile error — `VenueLayout` not found).

- [ ] **Step 3: Implement `VenueLayout` (pure, no Bukkit)**

```java
package me.vangoo.infrastructure.market;

/**
 * Чиста геометрія зали зборів: де стоїть Посередник і де розставити учасників,
 * щоб вони не спавнились один в одному. Координати відносні до центру підлоги
 * (0.5, 0.5); GatheringVenueProvider перетворює їх на Location зі світом і y.
 */
public final class VenueLayout {

    /** Позиція+поворот (x,z у блоках, yaw у градусах Minecraft). */
    public record Spot(double x, double z, float yaw) {}

    private static final double ORG_X = 0.5;
    private static final double ORG_Z = -6.5; // північний край зали
    private static final double SPACING = 2.0;

    private VenueLayout() {}

    /** Посередник — на чолі зали, обличчям до учасників (+z). */
    public static Spot organizer() {
        return new Spot(ORG_X, ORG_Z, 0f);
    }

    /** i-й з total учасників: сітка перед Посередником, обличчям до нього. */
    public static Spot attendee(int index, int total) {
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(total)));
        int row = index / cols;
        int col = index % cols;
        double x = ORG_X + (col - (cols - 1) / 2.0) * SPACING;
        double z = 1.5 + row * SPACING; // починається за кілька блоків від Посередника, тягнеться до +z
        x = clamp(x);
        z = clamp(z);
        return new Spot(x, z, yawToward(x, z, ORG_X, ORG_Z));
    }

    private static double clamp(double v) {
        return Math.max(-7.5, Math.min(7.5, v));
    }

    /** Yaw, що дивиться з (x,z) на (tx,tz) за конвенцією Minecraft. */
    private static float yawToward(double x, double z, double tx, double tz) {
        double dx = tx - x;
        double dz = tz - z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
```

- [ ] **Step 4: Run test — verify it passes**

Run: `& $mvn -o test -Dtest=VenueLayoutTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Add spawn accessors to `GatheringVenueProvider`**

Add these methods to `GatheringVenueProvider` (reuse the existing `getOrCreateWorld()`; players stand at `PLATFORM_Y + 1`):

```java
public Location organizerSpawn() {
    World world = getOrCreateWorld();
    VenueLayout.Spot s = VenueLayout.organizer();
    return new Location(world, s.x(), PLATFORM_Y + 1, s.z(), s.yaw(), 0f);
}

public Location attendeeSpawn(int index, int total) {
    World world = getOrCreateWorld();
    VenueLayout.Spot s = VenueLayout.attendee(index, total);
    return new Location(world, s.x(), PLATFORM_Y + 1, s.z(), s.yaw(), 0f);
}
```

- [ ] **Step 6: Use distributed spawns in `GatheringService.open()`**

In `open()`, replace the attendee loop and the organizer spawn. Current:
```java
Location venue = venueProvider.venueSpawn();
for (Player player : attendees) {
    session.registerParticipant(player.getUniqueId());
    openParticipantIds.add(player.getUniqueId());
    returnLocations.put(player.getUniqueId(), player.getLocation());
    player.teleport(venue);
    anonymizer.mask(player, session.aliasOf(player.getUniqueId()));
    frozen.add(player.getUniqueId());
}
organizerNpc.spawn(venue);
```
Replace with:
```java
int total = attendees.size();
for (int i = 0; i < total; i++) {
    Player player = attendees.get(i);
    session.registerParticipant(player.getUniqueId());
    openParticipantIds.add(player.getUniqueId());
    returnLocations.put(player.getUniqueId(), player.getLocation());
    player.teleport(venueProvider.attendeeSpawn(i, total));
    anonymizer.mask(player, session.aliasOf(player.getUniqueId()));
    frozen.add(player.getUniqueId());
}
organizerNpc.spawn(venueProvider.organizerSpawn());
```

- [ ] **Step 7: Full build**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/market/VenueLayout.java src/test/java/me/vangoo/infrastructure/market/VenueLayoutTest.java src/main/java/me/vangoo/infrastructure/market/GatheringVenueProvider.java src/main/java/me/vangoo/application/services/GatheringService.java
git commit -F- <<'EOF'
fix(market): spread attendees across the room instead of stacking

Every attendee teleported to the same spawn point where the organizer NPC
also stood, piling players inside him. Distribute them on a spaced grid
facing the organizer, who now sits at his own podium spot.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

**In-server verification (later):** start a gathering with 3+ online Beyonders → each spawns at a distinct spot in the room, all facing the Посередник, nobody clipping into him.

---

## Task 5: Reusable `ConfirmationMenu` + buyback confirmation

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/ui/ConfirmationMenu.java`
- Modify: `src/main/java/me/vangoo/application/services/GatheringService.java` (add `buybackPayout`)
- Modify: `src/main/java/me/vangoo/presentation/listeners/OrganizerClickListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`

**Interfaces:**
- Consumes: `MarketItemNamer`, `WalletService`, `GatheringService`, triumph-gui `Gui`.
- Produces:
  - `ConfirmationMenu.open(Player player, ItemStack give, ItemStack get, String title, Runnable onConfirm)`.
  - `GatheringService.buybackPayout(ItemStack hand)` → `Optional<PoundMoney>` (empty when not classifiable or zero-priced).

- [ ] **Step 1: Add `buybackPayout` to `GatheringService`**

Add this public method (pure quote — no state change; reused for the confirm display). It mirrors the price logic already inside `buybackFromHand`:

```java
/** Ціна скупки за весь стак у руці, якщо організатор це скуповує (для підтвердження). */
public Optional<PoundMoney> buybackPayout(ItemStack hand) {
    if (hand == null || hand.getType().isAir()) {
        return Optional.empty();
    }
    return classifier.classify(hand).map(c -> config.buyback()
            .unitPriceFor(c.category(), c.sequence(), c.itemKey()).times(hand.getAmount()))
            .filter(total -> !total.isZero());
}
```

(`buybackFromHand` stays as-is — it re-reads the hand and performs the emission when the player confirms.)

- [ ] **Step 2: Implement `ConfirmationMenu`**

Follow `MarketMenu`'s triumph-gui idiom (3-row GUI, `disableAllInteractions`, `button(...)` helper):

```java
package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
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

import java.util.List;

/** Одноразове підтвердження необоротної дії: «віддаєте X ↔ отримуєте Y», Підтвердити/Скасувати. */
public class ConfirmationMenu {

    private final Plugin plugin;

    public ConfirmationMenu(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ItemStack give, ItemStack get, String title, Runnable onConfirm) {
        Gui gui = Gui.gui()
                .title(Component.text(title).color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD))
                .rows(3)
                .disableAllInteractions()
                .create();
        gui.setItem(2, 3, new GuiItem(labeled(give.clone(), ChatColor.RED + "Ви віддаєте")));
        gui.setItem(2, 5, new GuiItem(labeled(get.clone(), ChatColor.GREEN + "Ви отримаєте")));
        gui.setItem(2, 1, new GuiItem(button(Material.LIME_CONCRETE, ChatColor.GREEN + "✔ Підтвердити"), e -> {
            e.setCancelled(true);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, onConfirm);
        }));
        gui.setItem(2, 9, new GuiItem(button(Material.RED_CONCRETE, ChatColor.RED + "✘ Скасувати"), e -> {
            e.setCancelled(true);
            player.closeInventory();
        }));
        gui.open(player);
    }

    private ItemStack labeled(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name + ChatColor.GRAY + ": "
                    + (meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name().toLowerCase()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of());
        item.setItemMeta(meta);
        return item;
    }
}
```

- [ ] **Step 3: Wire buyback confirm into `OrganizerClickListener`**

Change the listener to hold `ConfirmationMenu` + `WalletService` and open a confirm instead of selling instantly:

```java
package me.vangoo.presentation.listeners;

import me.vangoo.application.services.GatheringService;
import me.vangoo.application.services.WalletService;
import me.vangoo.domain.market.PoundMoney;
import me.vangoo.infrastructure.citizens.OrganizerNpcService;
import me.vangoo.infrastructure.ui.ConfirmationMenu;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;

/** ПКМ по Посереднику з предметом у руці → підтвердження скупки за конфіг-прайсом. */
public class OrganizerClickListener implements Listener {

    private final OrganizerNpcService organizerNpc;
    private final GatheringService gatheringService;
    private final ConfirmationMenu confirmationMenu;
    private final WalletService walletService;

    public OrganizerClickListener(OrganizerNpcService organizerNpc, GatheringService gatheringService,
                                  ConfirmationMenu confirmationMenu, WalletService walletService) {
        this.organizerNpc = organizerNpc;
        this.gatheringService = gatheringService;
        this.confirmationMenu = confirmationMenu;
        this.walletService = walletService;
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        if (!organizerNpc.isOrganizer(event.getNPC())) {
            return;
        }
        var player = event.getClicker();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                    + "Посередник: «Покажи, що приніс — візьми річ у руку.»");
            return;
        }
        Optional<PoundMoney> payout = gatheringService.buybackPayout(hand);
        if (payout.isEmpty()) {
            player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                    + "Посередник: «За таке я не дам і коппета.»");
            return;
        }
        ItemStack coins = coinLabel(payout.get());
        confirmationMenu.open(player, hand.clone(), coins, "🕯 Скупка", () -> gatheringService.buybackFromHand(player));
    }

    private ItemStack coinLabel(PoundMoney money) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + money.format());
        meta.setLore(List.of());
        item.setItemMeta(meta);
        return item;
    }
}
```

- [ ] **Step 4: Wire `ConfirmationMenu` in `ServiceContainer`**

In `ServiceContainer.java`:
1. Add a field near the other UI fields (line ~75): `private me.vangoo.infrastructure.ui.ConfirmationMenu confirmationMenu;`
2. In `initializeUI()` (after `marketMenu` creation, line ~279) add:
   `this.confirmationMenu = new me.vangoo.infrastructure.ui.ConfirmationMenu(plugin);`
3. In `initializeEventListeners()` update the `organizerClickListener` construction (line ~332):
   ```java
   this.organizerClickListener = new me.vangoo.presentation.listeners.OrganizerClickListener(
           organizerNpcService, gatheringService, confirmationMenu, walletService);
   ```
4. Add a getter near the others: `public me.vangoo.infrastructure.ui.ConfirmationMenu getConfirmationMenu() { return confirmationMenu; }`

**Note:** `initializeUI()` runs before `initializeEventListeners()` (per `.claude/rules/wiring.md` order), so `confirmationMenu` is non-null when the listener is built.

- [ ] **Step 5: Full build**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/ui/ConfirmationMenu.java src/main/java/me/vangoo/application/services/GatheringService.java src/main/java/me/vangoo/presentation/listeners/OrganizerClickListener.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -F- <<'EOF'
feat(market): confirm organizer buyback before selling

Right-clicking the organizer now opens a give/get confirmation showing the
payout instead of selling instantly. Adds a reusable ConfirmationMenu for
irreversible market actions.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

**In-server verification (later):** right-click Посередник with a sellable item → confirm dialog shows the item and the coin payout; Confirm sells, Cancel/close does nothing; an unsellable item still gives the "не дам і коппета" line with no dialog.

---

## Task 6: Barter settlement, pre-checks & demand plumbing in `GatheringService`

Add the item dimension to the application: prices become full `Consideration`s (item ± boot), the buyer's item is pulled atomically at settlement, and buy/accept pre-check the buyer's holdings. Message formatting gains item names via `MarketItemNamer`.

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (reorder `marketItemNamer`; inject into `GatheringService`)
- Modify: `src/main/java/me/vangoo/application/services/GatheringService.java`

**Interfaces:**
- Consumes: `Consideration`, `Consideration.ItemDemand`, `MarketItemClassifier`, `MarketItemNamer`.
- Produces (public API Task 7's `MarketMenu` calls):
  - `listLotFromHand(Player seller, Consideration price)` → `boolean`
  - `offerFromHand(Player seller, UUID orderId, Consideration price)` → `boolean`
  - `counter(Player actor, UUID negotiationId, Consideration price)` → `boolean`
  - `Optional<Consideration.ItemDemand> readDemand(ItemStack offhand)` → item demand from an off-hand sample (empty if not classifiable)
  - `String describeConsideration(Consideration price)` → Ukrainian "item ×n + N ф" for GUI/messages
  - `Optional<String> barterShortfall(Player buyer, Consideration price)` → error text if the buyer lacks the item/funds, else empty (for pre-confirm checks)

- [ ] **Step 1: Reorder `marketItemNamer` and inject it into `GatheringService`**

In `ServiceContainer.java`:
1. Move `this.marketItemNamer = new MarketItemNamer(customItemService);` from `initializeUI()` (line ~277) to `initializeApplicationServices()` **before** the `new GatheringService(...)` call (line ~258). (`customItemService` is created earlier in infrastructure init, so it is available.)
2. Change the `GatheringService` constructor call to pass `marketItemNamer` as the final argument:
   ```java
   this.gatheringService = new GatheringService(plugin, marketConfig, walletService,
           marketItemClassifier, gatheringVenueProvider, gatheringAnonymizer,
           gatheringSnapshotRepository, organizerNpcService, beyonderService,
           recipeUnlockService, potionManager, marketItemNamer);
   ```
3. Ensure the field declaration for `marketItemNamer` stays; only its assignment moves.

- [ ] **Step 2: Add `MarketItemNamer` param to `GatheringService` constructor**

Add the field and constructor parameter:
```java
private final MarketItemNamer namer;
```
Append `MarketItemNamer namer` to the constructor signature and `this.namer = namer;` in the body. Add import `import me.vangoo.domain.market.Consideration;` (if not already from Task 3) and `import me.vangoo.domain.market.MarketItemNamer;` — note `MarketItemNamer` is in `me.vangoo.application.services`, same package as `GatheringService`, so no import needed.

- [ ] **Step 3: Add demand-reading, description, and inventory helpers**

Add these methods to `GatheringService`:

```java
/** Читає предметну вимогу з OFF-HAND зразка (не споживає його). */
public Optional<Consideration.ItemDemand> readDemand(ItemStack offhand) {
    if (offhand == null || offhand.getType().isAir()) {
        return Optional.empty();
    }
    return classifier.classify(offhand)
            .map(c -> new Consideration.ItemDemand(c.itemKey(), offhand.getAmount()));
}

/** Укр-опис ціни: «предмет ×n + N ф» / лише предмет / лише гроші. */
public String describeConsideration(Consideration price) {
    StringBuilder sb = new StringBuilder();
    if (price.isBarter()) {
        sb.append(namer.displayName(price.item().itemKey())).append(" ×").append(price.item().amount());
        if (price.hasMoney()) {
            sb.append(" + ").append(price.money().format());
        }
    } else {
        sb.append(price.money().format());
    }
    return sb.toString();
}

/** Скільки одиниць itemKey має гравець (за класифікатором). */
private int countMatching(Player player, String itemKey) {
    int total = 0;
    for (ItemStack stack : player.getInventory().getContents()) {
        if (stack == null || stack.getType().isAir()) {
            continue;
        }
        if (classifier.classify(stack).map(c -> c.itemKey().equals(itemKey)).orElse(false)) {
            total += stack.getAmount();
        }
    }
    return total;
}

/** Знімає amount одиниць itemKey у гравця; повертає зняті стаки для видачі продавцю. */
private List<ItemStack> removeMatching(Player player, String itemKey, int amount) {
    List<ItemStack> removed = new ArrayList<>();
    int remaining = amount;
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length && remaining > 0; i++) {
        ItemStack stack = contents[i];
        if (stack == null || stack.getType().isAir()) {
            continue;
        }
        if (!classifier.classify(stack).map(c -> c.itemKey().equals(itemKey)).orElse(false)) {
            continue;
        }
        int take = Math.min(stack.getAmount(), remaining);
        remaining -= take;
        ItemStack chunk = stack.clone();
        chunk.setAmount(take);
        removed.add(chunk);
        if (take == stack.getAmount()) {
            player.getInventory().setItem(i, null);
        } else {
            stack.setAmount(stack.getAmount() - take);
        }
    }
    return removed;
}

/** Причина, чому покупець не може заплатити цю ціну (для перевірки перед підтвердженням); empty — може. */
public Optional<String> barterShortfall(Player buyer, Consideration price) {
    if (price.isBarter() && countMatching(buyer, price.item().itemKey()) < price.item().amount()) {
        return Optional.of("Для обміну потрібно " + describeConsideration(price)
                + " — у вас цього немає");
    }
    if (price.hasMoney() && CoinChange.make(walletService.countPounds(buyer),
            walletService.countCoppets(buyer), price.money()).isEmpty()) {
        return Optional.of("Недостатньо монет: потрібно " + price.money().format());
    }
    return Optional.empty();
}
```

Add imports as needed: `import me.vangoo.domain.market.CoinChange;` (if not present).

- [ ] **Step 4: Change public price-taking methods to `Consideration`**

`listLotFromHand`, `offerFromHand`, `counter` now take a `Consideration` and use `describeConsideration` in messages:

```java
public boolean listLotFromHand(Player seller, Consideration price) {
    return guarded(seller, () -> {
        ItemStack hand = requireHandItem(seller);
        var classified = classifier.classify(hand).orElseThrow(() -> new MarketException(
                "Це не потойбічна річ — на ринку їй не місце (інгредієнти, Характеристики, книги рецептів)"));
        UUID lotId = session.listLot(seller.getUniqueId(), classified.itemKey(), hand.getAmount(), price);
        escrow.put(lotId, new EscrowEntry(seller.getUniqueId(), hand.clone()));
        seller.getInventory().setItemInMainHand(null);
        seller.sendMessage(PREFIX + ChatColor.GREEN + "Лот виставлено: "
                + describe(hand) + ChatColor.GREEN + " за " + describeConsideration(price));
        persist();
    });
}

public boolean offerFromHand(Player seller, UUID orderId, Consideration price) {
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
        seller.sendMessage(PREFIX + ChatColor.GREEN + "Пропозицію зроблено: " + describeConsideration(price));
        notify(order.buyerId(), PREFIX + ChatColor.YELLOW + session.aliasOf(seller.getUniqueId())
                + " пропонує ваше замовлення за " + describeConsideration(price)
                + ChatColor.GRAY + " — див. «Мої угоди»");
        persist();
    });
}

public boolean counter(Player actor, UUID negotiationId, Consideration price) {
    return guarded(actor, () -> {
        session.counter(actor.getUniqueId(), negotiationId, price);
        actor.sendMessage(PREFIX + ChatColor.GREEN + "Зустрічна ціна: " + describeConsideration(price));
        otherParty(negotiationId, actor.getUniqueId()).ifPresent(other -> notify(other,
                PREFIX + ChatColor.YELLOW + session.aliasOf(actor.getUniqueId())
                        + " дає зустрічну ціну " + describeConsideration(price)
                        + ChatColor.GRAY + " — див. «Мої угоди»"));
        persist();
    });
}
```

- [ ] **Step 5: Pre-check the buyer's item in `buyLot` / `accept`, and pull it in `settle`**

In `buyLot`, before the domain call, guard the item side (funds are guarded inside the domain):
```java
public boolean buyLot(Player buyer, UUID lotId) {
    return guarded(buyer, () -> {
        Lot lot = session.activeLots().stream().filter(l -> l.lotId().equals(lotId)).findFirst()
                .orElseThrow(() -> new MarketException("Лот уже недоступний"));
        if (lot.price().isBarter()
                && countMatching(buyer, lot.price().item().itemKey()) < lot.price().item().amount()) {
            throw new MarketException("Для обміну потрібно " + describeConsideration(lot.price())
                    + " — у вас цього немає");
        }
        Settlement s = session.buyLot(buyer.getUniqueId(), lotId,
                walletService.countPounds(buyer), walletService.countCoppets(buyer));
        settle(buyer, s);
        buyer.sendMessage(PREFIX + ChatColor.GREEN + "Куплено за " + describeConsideration(s.price()) + ".");
    });
}
```

In `accept`, add the buyer-item pre-check before `session.accept(...)` (the buyer is `view.buyerId()`, already resolved to `buyer`):
```java
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
if (view.currentPrice().isBarter()
        && countMatching(buyer, view.currentPrice().item().itemKey()) < view.currentPrice().item().amount()) {
    throw new MarketException("У покупця немає предмета для обміну — торг лишається відкритим");
}
AcceptResult result = session.accept(actor.getUniqueId(), negotiationId,
        walletService.countPounds(buyer), walletService.countCoppets(buyer));
```
(Rest of `accept` unchanged; `settle(buyer, result.settlement())` moves the item below.)

Extend `settle` to move the buyer's consideration item to the seller after the good/money move:
```java
private void settle(Player buyer, Settlement s) {
    walletService.charge(buyer, s.price().money()).orElseThrow(
            () -> new IllegalStateException("Wallet changed between check and charge"));
    EscrowEntry entry = escrow.remove(s.escrowRef());
    if (entry != null) {
        deliverItem(s.payerId(), entry.stack());
    }
    if (s.price().isBarter()) {
        for (ItemStack chunk : removeMatching(buyer, s.price().item().itemKey(), s.price().item().amount())) {
            deliverItem(s.payeeId(), chunk);
        }
    }
    deliverMoney(s.payeeId(), s.sellerProceeds());
    if (s.sellerProceeds().isZero()) {
        notify(s.payeeId(), PREFIX + ChatColor.GREEN + "Вашу річ обміняно за "
                + describeConsideration(s.price()) + ".");
    } else {
        notify(s.payeeId(), PREFIX + ChatColor.GREEN + "Вашу річ продано: +"
                + s.sellerProceeds().format() + ChatColor.GRAY + " (комісія посередника: "
                + s.commissionPaid().format() + ")");
    }
    persist();
}
```
Note `walletService.charge(buyer, s.price().money())` with zero money is a no-op charge (returns a present `CoinChange` of zeros), so the `orElseThrow` never trips for pure barter — the funds were already validated in the domain `accept`/`buyLot`.

- [ ] **Step 6: Full build**

Run: `& $mvn -o clean package`
Expected: FAIL to compile — `MarketMenu` still calls `listLotFromHand(player, PoundMoney)`, `offerFromHand(..., PoundMoney)`, `counter(..., PoundMoney)`. This is expected; Task 7 updates `MarketMenu`. To keep this task independently green, do the minimal `MarketMenu` call-site adaptation now as part of Step 7 below (wrap existing prices in `Consideration.money(...)`), deferring the off-hand UX to Task 7.

- [ ] **Step 7: Minimal `MarketMenu` call-site adaptation (keep build green)**

In `MarketMenu.java`, wrap the existing currency prices so the project compiles (no UX change yet):
- `promptListLot`: `gatheringService.listLotFromHand(player, price)` → `gatheringService.listLotFromHand(player, me.vangoo.domain.market.Consideration.money(price))`.
- `openOrders` offer prompt: `gatheringService.offerFromHand(player, order.orderId(), price)` → `... Consideration.money(price))`.
- `openNegotiations` counter prompt: `gatheringService.counter(player, view.negotiationId(), price)` → `... Consideration.money(price))`.
- Add import `import me.vangoo.domain.market.Consideration;`.

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/vangoo/application/services/GatheringService.java src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -F- <<'EOF'
feat(market): settle barter trades by pulling the buyer's item

Prices are full Considerations now: buy/accept pre-check the buyer holds the
demanded item, and settlement pulls it from the buyer to the seller
atomically alongside any currency boot. Messages name items via
MarketItemNamer. Menu still enters currency-only prices until the next change.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 7: Barter entry & accept-confirm in `MarketMenu`

Wire the off-hand demand convention and the confirmation dialog into the GUI so players can actually create and accept barter trades.

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (pass `ConfirmationMenu` to `MarketMenu`)

**Interfaces:**
- Consumes: `GatheringService.readDemand`, `describeConsideration`, `barterShortfall`, `buyLot`, `accept`, `Consideration`; `ConfirmationMenu.open`.

- [ ] **Step 1: Give `MarketMenu` a `ConfirmationMenu`**

In `MarketMenu.java` add a `private final ConfirmationMenu confirm;` field, add it as the last constructor parameter, and assign it. Add import `import me.vangoo.infrastructure.ui.ConfirmationMenu;` (same package — no import needed) and `import me.vangoo.domain.market.Consideration;` (already added in Task 6 Step 7).

In `ServiceContainer.initializeUI()`, `confirmationMenu` is created (Task 5) **before** `marketMenu`? Currently `marketMenu` is created at line ~278 and `confirmationMenu` was added after it in Task 5. Reorder so `confirmationMenu` is created first, then pass it:
```java
this.confirmationMenu = new me.vangoo.infrastructure.ui.ConfirmationMenu(plugin);
this.marketMenu = new me.vangoo.infrastructure.ui.MarketMenu(
        plugin, gatheringService, walletService, chatPromptService, marketItemNamer, confirmationMenu);
```

- [ ] **Step 2: Build a `Consideration` from off-hand + chat boot for listing a lot**

Replace `promptListLot` so it reads the off-hand demand and prompts for an optional boot:

```java
private void promptListLot(Player player) {
    var demand = gatheringService.readDemand(player.getInventory().getItemInOffHand());
    if (demand.isPresent()) {
        String hint = ChatColor.GOLD + "Ви хочете за це: " + ChatColor.WHITE
                + namer.displayName(demand.get().itemKey()) + " ×" + demand.get().amount()
                + ChatColor.GOLD + ". Напишіть доплату монетами «<ф> <к>» або «0»:";
        prompts.prompt(player, hint, withBoot(player, boot ->
                gatheringService.listLotFromHand(player, Consideration.of(demand.get(), boot))));
    } else {
        prompts.prompt(player, PRICE_HINT + ChatColor.GRAY + " — за предмет у вашій руці",
                withPrice(player, price -> gatheringService.listLotFromHand(player, Consideration.money(price))));
    }
}
```

Add a `withBoot` helper (accepts `0`, unlike `withPrice`):
```java
private Consumer<String> withBoot(Player player, Consumer<PoundMoney> action) {
    return input -> {
        PoundMoney boot = ChatPromptService.parsePrice(input);
        if (boot == null) {
            player.sendMessage(ChatColor.RED + "Невірна доплата. Формат: «2 15», «7» або «0».");
            return;
        }
        action.accept(boot);
    };
}
```

- [ ] **Step 3: Off-hand demand for offers on orders**

In `openOrders`, replace the offer prompt (inside the non-own `GuiItem` handler) with the same off-hand-aware branch:

```java
gui.addItem(new GuiItem(display, e -> {
    e.setCancelled(true);
    if (own) {
        return;
    }
    var demand = gatheringService.readDemand(player.getInventory().getItemInOffHand());
    if (demand.isPresent()) {
        String hint = ChatColor.GOLD + "Ви просите за це: " + ChatColor.WHITE
                + namer.displayName(demand.get().itemKey()) + " ×" + demand.get().amount()
                + ChatColor.GOLD + ". Доплата монетами «<ф> <к>» або «0»:";
        prompts.prompt(player, hint, withBoot(player, boot ->
                gatheringService.offerFromHand(player, order.orderId(), Consideration.of(demand.get(), boot))));
    } else {
        prompts.prompt(player, PRICE_HINT + ChatColor.GRAY + " — ваша ціна за це замовлення",
                withPrice(player, price ->
                        gatheringService.offerFromHand(player, order.orderId(), Consideration.money(price))));
    }
}));
```

- [ ] **Step 4: Off-hand demand for counter-offers**

In `openNegotiations`, replace the counter branch (`myTurn && RIGHT`) with the off-hand-aware version:

```java
} else if (myTurn && e.getClick() == org.bukkit.event.inventory.ClickType.RIGHT) {
    var demand = gatheringService.readDemand(player.getInventory().getItemInOffHand());
    if (demand.isPresent()) {
        String hint = ChatColor.GOLD + "Зустрічна: за це — " + ChatColor.WHITE
                + namer.displayName(demand.get().itemKey()) + " ×" + demand.get().amount()
                + ChatColor.GOLD + ". Доплата монетами «<ф> <к>» або «0»:";
        prompts.prompt(player, hint, withBoot(player, boot ->
                gatheringService.counter(player, view.negotiationId(), Consideration.of(demand.get(), boot))));
    } else {
        prompts.prompt(player, PRICE_HINT + ChatColor.GRAY + " — ваша зустрічна ціна",
                withPrice(player, price ->
                        gatheringService.counter(player, view.negotiationId(), Consideration.money(price))));
    }
}
```

- [ ] **Step 5: Confirm dialog when buying/accepting an item-bearing trade**

In `openLots`, change the buy handler to confirm when the price is barter:
```java
gui.addItem(new GuiItem(display, e -> {
    e.setCancelled(true);
    if (own) {
        return;
    }
    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    if (lot.price().isBarter()) {
        var shortfall = gatheringService.barterShortfall(player, lot.price());
        if (shortfall.isPresent()) {
            player.sendMessage(ChatColor.RED + "[Збори] " + shortfall.get());
            return;
        }
        ItemStack give = payLabel(lot.price());
        ItemStack get = gatheringService.escrowStack(lot.lotId());
        confirm.open(player, give, get, "🕯 Обмін", () -> {
            gatheringService.buyLot(player, lot.lotId());
            runSynced(player, () -> openLots(player));
        });
    } else {
        gatheringService.buyLot(player, lot.lotId());
        runSynced(player, () -> openLots(player));
    }
}));
```

In `openNegotiations`, change the accept branch (`myTurn && LEFT`) similarly. Determine what the actor gives: if the actor is the buyer they give the price; if the actor is the seller they give the good (escrow) and receive the price. Show it from the actor's side:
```java
} else if (myTurn && e.getClick() == org.bukkit.event.inventory.ClickType.LEFT) {
    if (view.currentPrice().isBarter()) {
        // покупець віддає ціну й отримує товар; продавець — навпаки
        ItemStack priceLabel = payLabel(view.currentPrice());
        ItemStack goodLabel = gatheringService.escrowStack(view.negotiationId());
        ItemStack give = iAmSeller ? goodLabel : priceLabel;
        ItemStack get = iAmSeller ? priceLabel : goodLabel;
        if (!iAmSeller) {
            var shortfall = gatheringService.barterShortfall(player, view.currentPrice());
            if (shortfall.isPresent()) {
                player.sendMessage(ChatColor.RED + "[Збори] " + shortfall.get());
                return;
            }
        }
        confirm.open(player, give, get, "🕯 Обмін", () -> {
            gatheringService.accept(player, view.negotiationId());
            runSynced(player, () -> openNegotiations(player));
        });
    } else {
        gatheringService.accept(player, view.negotiationId());
        runSynced(player, () -> openNegotiations(player));
    }
}
```

Add a `payLabel` helper that renders a `Consideration` as a display item (falls back to a paper icon for the item demand):
```java
private ItemStack payLabel(Consideration price) {
    Material icon = price.isBarter() ? Material.PAPER : Material.GOLD_NUGGET;
    ItemStack item = new ItemStack(icon);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(ChatColor.GOLD + gatheringService.describeConsideration(price));
    meta.setLore(new ArrayList<>());
    item.setItemMeta(meta);
    return item;
}
```
(`escrowStack` may return `null` if the escrow is gone; guard by falling back to `payLabel`-style paper if needed — in practice the lot/negotiation is live when shown.)

- [ ] **Step 6: Full build**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/ui/MarketMenu.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -F- <<'EOF'
feat(market): enter and confirm barter trades in the market menu

Off-hand item sets the demanded item (main hand still the good), chat adds
an optional currency boot; empty off-hand keeps the currency-only flow.
Buying or accepting an item-bearing trade opens a give/get confirmation.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

**In-server verification (later):**
- List a lot: main hand = a characteristic, off-hand = an ingredient sample ×3, boot `2 0` → lot shows "Ingredient ×3 + 2 ф". Another player with those 3 ingredients + 2 pounds buys it → confirm shows give/get → items swap, no commission burned.
- Empty off-hand → currency-only lot exactly as before.
- Place an order, offer with an off-hand item demand, counter, accept → confirm dialog, item pulled from buyer to seller.
- A buyer lacking the demanded item gets the shortfall message and no dialog.

---

## Task 8: Document the new mechanics

**Files:**
- Modify: `.claude/rules/market-gathering.md`

**Interfaces:** none (docs only).

- [ ] **Step 1: Add a "Бартер" section to `market-gathering.md`**

Under the money/trading rules, add:

```markdown
## Бартер: ціна як `Consideration`

- Ціна лота / оферти / зустрічної — `domain.market.Consideration` (чистий VO):
  опційна `ItemDemand` (`itemKey` + `amount`) та/або грошовий буст `PoundMoney`
  (мінімум одне присутнє; юніт-тест `ConsiderationTest`). `Lot`, `Negotiation`,
  `Settlement` квотуються `Consideration`, не `PoundMoney`.
- **Комісія лише з ЧИСТО грошових угод.** `Consideration.commission(rate)` = нуль,
  якщо є предметна складова (бартер, навіть із бустом), інакше `ceil(гроші×rate)`.
  Це свідомо лишає бартер поза грошовим стоком.
- **Ескроу однобічний.** Ескроюється лише товар продавця (як раніше). Предмет
  покупця НЕ ескроюється — його знімають атомарно на сеттлменті
  (`GatheringService.removeMatching` → `deliverItem` продавцю). Тому схема
  снепшота й інваріант збереження ескроу не змінюються.
- **Введення ціни:** головна рука = товар, який віддаєш; OFF-HAND = зразок
  предмета, який просиш (його `amount` = потрібна кількість, не споживається);
  чат-промпт = грошовий буст (`0` дозволено за наявності предметної вимоги).
  Порожня off-hand → чисто грошова ціна = стара поведінка.
- **Перед-перевірки:** `buyLot`/`accept` звіряють, що покупець має предметну
  вимогу (`countMatching`) ДО мутації домену (симетрично до перевірки монет);
  брак предмета лишає торг відкритим. Утиліта `barterShortfall` дає текст
  причини для UI перед підтвердженням.

## Підтвердження необоротних дій

- `infrastructure.ui.ConfirmationMenu.open(player, give, get, title, onConfirm)` —
  спільне «Ви віддаєте ↔ Ви отримуєте» з кнопками Підтвердити/Скасувати.
- Скупка організатором (`OrganizerClickListener`) більше НЕ продає миттєво:
  рахує ціну через `GatheringService.buybackPayout` і відкриває підтвердження;
  сам продаж (`buybackFromHand`, емісія) — на кнопці Підтвердити. Нульова ціна
  відхиляється одразу.
- Прийняття/купівля БАРТЕРНОЇ угоди (де віддаєш предмет) теж проходить
  `ConfirmationMenu`; чисто грошові купівлі/акцепти лишаються в один клік.

## Розсадка учасників

- `infrastructure.market.VenueLayout` — чиста геометрія (юніт-тест
  `VenueLayoutTest`): `organizer()` (чоло зали, обличчям до залу) і
  `attendee(index, total)` (рознесена сітка перед Посередником, обличчям до
  нього, в межах підлоги). `GatheringVenueProvider.organizerSpawn()` /
  `attendeeSpawn(i, n)` загортають їх у `Location`; `GatheringService.open()`
  телепортує кожного на власний слот, а NPC — на подіум. Раніше всі падали в
  одну точку всередині Посередника.
```

- [ ] **Step 2: Update the snapshot/escrow note if needed**

Verify the existing "Снепшот" and "Заборони" sections still hold (they do — schema unchanged, no new emission channel). No edit required beyond Step 1. Confirm by re-reading the file.

- [ ] **Step 3: Commit**

```bash
git add .claude/rules/market-gathering.md
git commit -F- <<'EOF'
docs(market): document barter pricing, confirmations, and spawn layout

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Final verification

- [ ] **Full clean build + all tests**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS, all tests pass (`ConsiderationTest`, `MarketNegotiationTest`, `BuybackPriceTableTest`, `VenueLayoutTest`, plus the existing `ArchitectureTest` confirming `domain.market` stays Bukkit-free).

- [ ] **In-server smoke (copy the shaded JAR to the test server `plugins/`, restart, `/gathering start`):**
  - Seq-0 characteristic sells to Посередник (Task 1).
  - Buyback shows a confirm dialog (Task 5).
  - Attendees spawn spread out, NPC on the podium (Task 4).
  - List / buy a barter lot with off-hand demand + boot; confirm dialog; items swap; no commission burned (Tasks 6–7).
  - Order → barter offer → counter → accept with confirm (Tasks 6–7).
  - Empty off-hand still gives currency-only trades unchanged (Task 3/7).

---

## Notes for the implementer

- **Task order matters:** 2 → 3 before 6/7 (type migration precedes item behaviour); 5 before 7 (MarketMenu needs `ConfirmationMenu`). Tasks 1 and 4 are independent and can be done any time.
- **`domain.market` stays pure** — `Consideration` and `VenueLayout`'s pure sibling logic import no Bukkit. `VenueLayout` itself lives in `infrastructure.market` but is deliberately Bukkit-free so it unit-tests headless; do not add `Location` to it.
- **Do not escrow the buyer's item** — pulling it atomically at settlement is the design invariant that keeps the snapshot schema and conservation test intact.
- **PowerShell heredoc for commits:** the `git commit -F- <<'EOF'` form shown here is bash. In PowerShell use a single-quoted here-string piped to `git commit -F -`, or write the message to a temp file — do NOT paste literal `@` sequences (see memory `commit-via-msgfile-not-herestring`).
```
