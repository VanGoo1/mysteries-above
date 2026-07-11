# Market barter, sale confirmation, and gathering fixes — design

**Date:** 2026-07-11
**Branch:** feat/market-gathering
**Scope:** Підпільний ринок (Збори Потойбічних)

## Problem statement

Four issues on the underground market, from playtesting:

1. **Characteristics cannot be sold to the Посередник (bug).** The organizer replies
   *"За таке я не дам і коппета"* for characteristics. Root cause: the buyback price
   table (`market.buyback.characteristic-coppets-by-seq`) only defines Sequences 9–5.
   A characteristic of Sequence 4/3/2/1/0 resolves to `getOrDefault(seq, 0)` → 0 →
   the zero-price rejection branch. The strongest (most valuable) characteristics are
   exactly the unsellable ones.

2. **No sale confirmation.** Selling to the organizer (right-click NPC with an item in
   hand) is instant and irreversible. The player should first see how much they will
   receive and explicitly confirm.

3. **No barter (feature).** A lot's price and an offer on an order are always currency
   (`PoundMoney`). Players want to price/pay in **items** — a characteristic, recipe
   book, or ingredient — optionally topped up with currency (a "boot"), both when
   listing a lot / responding to an order and when the counterparty pays.

4. **Spawn stacking (bug).** Every attendee teleports to the same `venueSpawn()`
   `(0.5, 65, 0.5)`, and the organizer NPC spawns at that same point, so everyone piles
   inside the Посередник. Attendees should be distributed around the room, and the NPC
   seated at its own podium spot.

## Decisions (from brainstorming)

- **Barter medium:** item + *optional* currency boot (both sides may add coins), not a
  strict item-XOR-currency.
- **Commission on barter:** none. The organizer's cut (the currency money-sink) applies
  only to **pure-currency** trades. Any trade with an item component — including
  item+boot — burns nothing.
- **Confirmation scope:** both organizer buyback **and** accepting an item-bearing
  barter trade (lot buy / negotiation accept) get a confirm dialog. Pure-currency lot
  buys and accepts stay one-click.
- **Order scope unchanged:** orders still request an ingredient of a known recipe.
  Barter only enriches the *pricing* of lots and offers, not what can be ordered.

## Non-goals

- Ordering characteristics/recipe books (orders stay ingredient-only).
- Escrowing the buyer's side of a barter (see "single-sided escrow" below).
- Changing the crash-recovery model (snapshot schema is untouched).
- Ready potions on the market (remain forbidden per existing invariant).

---

## Design

### 1. Buyback fix (config + guard test)

`config.yml → market.buyback.characteristic-coppets-by-seq` gains Sequences 4→0,
continuing the increasing curve (stronger sequence = higher buyback). Illustrative
values, tunable:

```yaml
characteristic-coppets-by-seq:
  "9": 80
  "8": 130
  "7": 220
  "6": 360
  "5": 540
  "4": 780
  "3": 1100
  "2": 1500
  "1": 2000
  "0": 2600
```

`BuybackPriceTableTest` gains a case asserting `unitPriceFor(CHARACTERISTIC, seq, key)`
is non-zero for **every** seq 0–9, so the gap cannot silently return. (The table itself
is unchanged; this is data + a regression guard. Config is the source of truth per
`market-gathering.md`.)

### 2. `Consideration` — the barter value object

New pure VO in `domain.market`, replacing `PoundMoney` wherever a *price* is quoted:

```java
public record Consideration(ItemDemand item, PoundMoney money) {
    public record ItemDemand(String itemKey, int amount) { /* amount > 0 */ }

    // Constructor invariant: item != null OR money is non-zero (never empty).
    // Factories: Consideration.money(PoundMoney), Consideration.of(ItemDemand, PoundMoney boot)
    public boolean isBarter();          // item != null
    public boolean hasMoney();          // !money.isZero()
    public PoundMoney commission(double rate); // isBarter() ? zero : money.commission(rate)
}
```

- `item` uses a nullable field with factory methods (no `Optional` in a record field);
  equality/format handle the null case.
- Display formatting lives in the application/UI (needs `MarketItemNamer` for the item
  name); the VO exposes its parts, the UI composes the string.
- Unit-tested (`ConsiderationTest`): empty-rejection invariant, `isBarter`, commission
  (zero for barter, `ceil(money×rate)` for pure currency), item+boot construction.

### 3. `MarketSession` / `Settlement` changes

- `Lot.price`: `PoundMoney` → `Consideration`.
- `Negotiation.currentPrice` (and `NegotiationView.currentPrice`): → `Consideration`.
- `Settlement.price`: → `Consideration`. `commissionPaid`, `sellerProceeds`,
  `payerCharge` are derived from `price.money()` and `price.commission(rate)`:
  - `commissionPaid = price.commission(rate)` (zero when barter).
  - `sellerProceeds = price.money().minus(commissionPaid)` (zero when no money).
  - `payerCharge = CoinChange.make(buyerPounds, buyerCoppets, price.money())` — with
    zero money this yields a no-op change (`paidCoppets == 0`), which is correct.
- `listLot(sellerId, itemKey, amount, Consideration)`,
  `offerOnOrder(sellerId, orderId, Consideration)`,
  `counter(actorId, negId, Consideration)` — take a `Consideration`; the old
  "price must be > 0" check becomes the `Consideration` non-empty invariant.
- `buyLot` / `accept` still take the **buyer's** `buyerPounds, buyerCoppets` and build
  `CoinChange` from `price.money()`. The currency check runs (and throws) **before** any
  state mutation, exactly as today.

**Single-sided escrow (key design point).** Only the seller's good is escrowed, as
today. The buyer's consideration-item is **not** escrowed — it is pulled atomically from
the buyer's inventory at settlement time (see §4). Consequences:

- The snapshot schema (`gathering-state.json`) is **unchanged** — no new persisted
  fields. Crash recovery still refunds seller goods and never resumes a session; the
  buyer never had anything in limbo.
- The escrow conservation invariant (`MarketNegotiationTest.conservationInvariantHolds`)
  still holds verbatim for seller goods (one escrow → one exit).
- The domain cannot see Bukkit inventories, so **item availability of the buyer is
  checked in the application before the domain mutates** (symmetric to how funds work:
  funds throw pre-mutation in the domain; item availability is pre-checked in the app).

### 4. Application (`GatheringService`)

**Reading a barter price from the player.** A helper reads the off-hand item as the
demanded `ItemDemand` (via `MarketItemClassifier` → itemKey; stack amount → demand
amount) and combines it with a chat-entered currency boot into a `Consideration`:

- Main hand = the good being given (unchanged).
- Off-hand = a **sample** of the desired item (read, **not** consumed).
- Chat boot = optional `PoundMoney` (`0` allowed when an item demand is present).
- Empty off-hand → pure-currency `Consideration` = today's behavior (boot must be > 0).

Applies to `listLotFromHand`, `offerFromHand`, and `counter`.

**Settling a barter.** `settle(...)` is extended: after charging the buyer's money and
delivering the seller's good to the buyer (as today), if `price.item()` is present it
removes `amount` matching stacks (matched via the classifier's itemKey) from the buyer's
inventory and delivers them to the seller. Delivery reuses the existing
`deliverItem` / `deliverMoney` (online → inventory, offline → pending-returns queue).

**Pre-checks before mutation.** `buyLot` / `accept` first verify (a) the buyer possesses
the demanded item (scan inventory), and (b) — via the confirm flow — funds; if the item
is missing the operation aborts cleanly with a message and the negotiation stays open
(mirrors `acceptWithoutBuyerFundsKeepsNegotiationOpen`). Because lots have a fixed price
and negotiations are turn-locked on the actor's turn, and all of this runs on the main
thread, there is no TOCTOU gap between pre-check and settlement.

### 5. Confirmation GUI (`ConfirmationMenu`)

A small reusable menu (infrastructure.ui): a give-summary item, a get-summary item, a
green **Підтвердити** and a red **Скасувати**. Constructed with two display
`ItemStack`s and an `onConfirm` runnable.

- **Buyback:** `OrganizerClickListener` stops selling instantly. It classifies the
  hand item, computes the payout via `config.buyback()`, and — if non-zero — opens the
  confirm ("Ви віддасте: X ; Посередник дасть: N"). Confirm → the existing sale
  (`GatheringService.buybackFromHand` split into a preview + a `confirmBuyback` that
  performs the emission). Zero-price still rejects immediately with the existing line.
- **Barter accept / lot buy** (only when the buyer gives up an item): after pre-checks,
  show the confirm ("Ви віддасте: item+boot ; Ви отримаєте: good"). Confirm → domain
  call + settle. Pure-currency buys/accepts skip the confirm (one-click, as today).

Item display names come from `MarketItemNamer`.

### 6. Venue spawn distribution (`GatheringVenueProvider`)

The provider owns venue geometry:

- `organizerSpawn()` — a podium spot at the head of the room (e.g. behind the lectern,
  near `(0.5, 65, -6.5)`), yaw facing the attendees.
- `attendeeSpawn(int index, int total)` (or `List<Location> attendeeSpawns(int total)`)
  — distinct, spaced positions on a ring/grid inside the 16×16 floor (x,z ∈ [-7,7]),
  each yaw facing the organizer. Deterministic; never collides with the lectern or NPC.

`GatheringService.open()` teleports attendee *i* to `attendeeSpawn(i, n)` and the NPC to
`organizerSpawn()`. `returnLocations` still records each player's origin. Players remain
frozen at their distinct spots during the briefing; the briefing/skip flow is untouched.

`venueSpawn()` remains as the world spawn / fallback.

---

## Testing

- **Unit (pure domain):** `ConsiderationTest` (invariant, commission, barter vs money);
  extended `MarketNegotiationTest` (barter offer/accept, item+boot settlement math,
  commission-free barter, conservation still holds for seller goods); updated existing
  assertions for the `Consideration` price type; `BuybackPriceTableTest` full-range
  non-zero guard.
- **In-server (effects):** buyback confirm dialog and emission; listing a lot priced in
  an item±boot; buying it (item pulled from buyer, good delivered, confirm shown);
  offer/counter/accept barter on an order; characteristics of every sequence sellable;
  attendees spawn spread out with the NPC on its podium.

## Docs

Update `.claude/rules/market-gathering.md`:

- `Consideration` VO and the "commission only on pure-currency" rule.
- Off-hand = demanded-item entry convention (main hand gives, off-hand asks, chat boot).
- Single-sided escrow: buyer's item pulled atomically at settlement, snapshot schema
  unchanged.
- Confirmation gate on buyback and item-bearing accepts (`ConfirmationMenu`).
- Venue spawn distribution (`organizerSpawn` / `attendeeSpawn`).

Keep architecture-overview edits in `CLAUDE.md` only if a new cross-cutting seam appears
(none expected — this stays within the existing domain-rules / effects split).

## Risk / compatibility

- **JSON/snapshot compatibility:** untouched — no new persisted fields.
- **Backward-compatible pricing:** empty off-hand reproduces today's currency-only
  flow, so existing muscle memory and pure-currency trades are unaffected.
- **Economy:** barter bypasses the commission sink by design (accepted trade-off). The
  buyback money-source is unchanged; loot + buyback remain the only emission channels.
