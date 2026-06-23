# Marionette Exit + Skin Disguise Implementation Plan

> **For agentic workers:** Implement task-by-task. Steps use checkbox (`- [ ]`) syntax. This codebase verifies ability **effects in-server, not via unit tests** — so the per-task automated gate is `mvn clean package` (must compile and keep the existing 14 tests green), and each feature ends with a manual in-server checklist. Full method bodies are intentionally left to execution time; this plan fixes the **direction, files, interfaces, and wiring**.

**Goal:** Fix the Marionettist exit (#1) so a possessing player can return to their body, and make the possessing player **appear as the cloned target** (#2) via a shaded PacketEvents skin disguise.

**Architecture:** Two independent features.
- **#1 Exit** — a dedicated Bukkit listener detects a right-click on the "Вийти" echo-shard item and calls `MarionettistControl`'s exit **directly** (building an `IAbilityContext` via `AbilityContextFactory`), bypassing the normal ability-trigger pipeline — which is unusable during possession because the inventory (and thus the ability item) is cleared and `possessIdentity` hides the ability. Exit is free (no spirituality cost) since it doesn't go through `execute()`.
- **#2 Skin** — capture the target's skin texture at clone time into `MarionetteMinionTrait`; on possession, resend the caster's player entity to viewers with a spoofed GameProfile using PacketEvents (shaded + relocated); restore on exit.

**Tech Stack:** Java 21, Paper/Spigot API 1.21.11, Citizens2 (provided), maven-shade-plugin 3.5.3, **PacketEvents 2.x (new — shaded + relocated)**.

## Global Constraints

- Target API: **Paper 1.21.11** (`spigot-api` provided). Skin-resend logic targets the modern packet set only (post-1.20.2: players spawn via the generic spawn-entity packet; `PlayerInfo` is split into update/remove).
- User-facing strings stay **Ukrainian** (`§`/`ChatColor`), consistent with existing code.
- Behavior layer rules (CLAUDE.md): registries on an ability are **instance fields, never static**; sessions/effect helpers call global APIs (Bukkit, now PacketEvents) **directly**; `domain` must not gain Bukkit/PacketEvents imports (ArchUnit pins this — keep all new packet code in `infrastructure` / `me.vangoo.pathways`).
- New services are wired manually in `infrastructure.di.ServiceContainer` (no DI framework) and registered in the plugin's `registerEvents()`.
- Per-task gate: `mvn -B clean package` compiles and the **14 existing tests pass**.

---

## File Structure

**Feature A — Exit (#1):**
- Modify `src/main/java/me/vangoo/pathways/fool/abilities/MarionettistControl.java` — add `IDENTITY` + `getIdentity()`, make swap-back detection `public static`, add public `exitIfPossessing(IAbilityContext)`.
- Create `src/main/java/me/vangoo/presentation/listeners/MarionetteExitListener.java` — interact listener for the echo-shard.
- Modify `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` — construct + expose the listener.
- Modify `src/main/java/me/vangoo/MysteriesAbovePlugin.java` — register the listener in `registerEvents()`.

**Feature B — Skin (#2):**
- Modify `pom.xml` — CodeMC repo, `packetevents-spigot` dependency, shade `<relocations>`.
- Modify `src/main/java/me/vangoo/MysteriesAbovePlugin.java` — PacketEvents lifecycle (`onLoad` / `onEnable` / `onDisable`).
- Modify `src/main/java/me/vangoo/infrastructure/citizens/MarionetteMinionTrait.java` — store captured skin texture (value + signature).
- Create `src/main/java/me/vangoo/infrastructure/disguise/SkinDisguiseService.java` — packet resend (disguise / undisguise).
- Modify `src/main/java/me/vangoo/pathways/fool/abilities/MarionettistControl.java` — capture texture in `convertToMarionette`; disguise in `swapIn`; undisguise in `swapOut` + `cleanUp`.

**Decision — how `MarionettistControl` reaches the disguise service:** `SkinDisguiseService` is **stateless** (it derives "restore" from the player's real profile, storing nothing) and wraps the global `PacketEvents.getAPI()` singleton — exactly like sessions wrap the global `Bukkit`/`JavaPlugin` APIs. So it is exposed as **static methods** and called directly from the effect layer. This avoids threading a service through the `Fool` → `PathwayManager` ability-construction path (abilities are built with `new MarionettistControl()`, no args) and matches the established "effect layer calls global APIs directly" pattern. It is **not** mutable static state on the ability (the anti-pattern), it is a stateless utility.

---

# Feature A — Marionette Exit (#1)

### Task A1: Expose a direct exit entry on `MarionettistControl`

**Files:**
- Modify: `src/main/java/me/vangoo/pathways/fool/abilities/MarionettistControl.java`

**Interfaces (Produces):**
- `public static final AbilityIdentity IDENTITY = AbilityIdentity.of("marionettist_control");`
- `@Override public AbilityIdentity getIdentity()` → returns `IDENTITY` (so the listener can locate the single instance via `PathwayManager.findAbilityInAllPathways(IDENTITY)`).
- `public static boolean isSwapBackItem(ItemStack item)` — promote the existing private instance method to `public static` (it only reads NBT via a guarded `NBTBuilder`, no instance state). The private `isSwapBackItemInHand` keeps delegating to it.
- `public boolean exitIfPossessing(IAbilityContext ctx)` — if `currentPossession` contains `ctx.getCasterId()`, call `swapOut(ctx, casterId, false)` and return `true`; else return `false`. (Direct call ⇒ no cost/cooldown.)

- [ ] **Step 1:** Add the `IDENTITY` constant and `getIdentity()` override. First verify the class doesn't already override `getIdentity` (it doesn't, per current source).
- [ ] **Step 2:** Change `private boolean isSwapBackItem(ItemStack item)` → `public static boolean isSwapBackItem(ItemStack item)` (body unchanged; it already guards `!item.hasItemMeta()`).
- [ ] **Step 3:** Add `public boolean exitIfPossessing(IAbilityContext ctx)` delegating to `swapOut(...)` as specified above.
- [ ] **Step 4:** Gate — `mvn -B clean package`; expect BUILD SUCCESS, 14 tests pass.
- [ ] **Step 5:** Commit — `fix(fool): expose direct marionette exit entry + stable identity`.

### Task A2: `MarionetteExitListener` + wiring

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/MarionetteExitListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (`registerEvents()`)

**Interfaces (Consumes):**
- `AbilityContextFactory.createContext(Player) : IAbilityContext` (existing — used by `AbilityExecutor`).
- `PathwayManager.findAbilityInAllPathways(AbilityIdentity) : Ability` (existing).
- `MarionettistControl.IDENTITY`, `.isSwapBackItem(ItemStack)`, `.exitIfPossessing(IAbilityContext)` (Task A1).

**Listener responsibility (`onPlayerInteract`, `@EventHandler`):**
1. Only `RIGHT_CLICK_AIR` / `RIGHT_CLICK_BLOCK`.
2. `ItemStack item = event.getItem();` → if `item == null || !MarionettistControl.isSwapBackItem(item)` return.
3. `event.setCancelled(true);`
4. Resolve the singleton ability once (cache it): `MarionettistControl mc = (MarionettistControl) pathwayManager.findAbilityInAllPathways(MarionettistControl.IDENTITY);` guard null.
5. `IAbilityContext ctx = contextFactory.createContext(event.getPlayer());`
6. `mc.exitIfPossessing(ctx);` (no-op if not possessing — `swapOut` already returns early when there's no snapshot).

- [ ] **Step 1:** Create `MarionetteExitListener implements Listener` with constructor `(AbilityContextFactory contextFactory, PathwayManager pathwayManager)` and the handler above.
- [ ] **Step 2:** In `ServiceContainer`: construct `marionetteExitListener` after `pathwayManager` and the context factory exist; add a getter.
- [ ] **Step 3:** In `MysteriesAbovePlugin.registerEvents()`: register the listener via `getServer().getPluginManager().registerEvents(services.getMarionetteExitListener(), this);`.
- [ ] **Step 4:** Gate — `mvn -B clean package`; BUILD SUCCESS, 14 tests pass.
- [ ] **Step 5:** Commit — `fix(fool): wire echo-shard right-click to marionette exit`.

**Feature A in-server verification:**
- Become Marionettist, create a marionette, Shift+RMB ability item to possess → confirm you swap in.
- RMB the "Вийти з маріонетки" echo shard → you return to your body, identity/inventory restored, **no spirituality charged**.
- RMB the echo shard when NOT possessing → nothing happens (no error).
- Confirm no `IllegalArgumentException` spam (depends on the already-applied `CustomItemFactory` null-meta guard).

---

# Feature B — Skin Disguise via PacketEvents (#2)

### Task B1: Add PacketEvents dependency + shade relocation

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1:** Add repo:
```xml
<repository><id>codemc</id><url>https://repo.codemc.io/repository/maven-releases/</url></repository>
```
- [ ] **Step 2:** Add dependency (compile scope so it shades):
```xml
<dependency>
  <groupId>com.github.retrooper</groupId>
  <artifactId>packetevents-spigot</artifactId>
  <version>2.9.4</version> <!-- pin to a current 2.x at execution time -->
</dependency>
```
- [ ] **Step 3:** Add `<relocations>` to the shade plugin `<configuration>` (project currently has none; both PE base packages MUST be relocated):
```xml
<relocations>
  <relocation><pattern>com.github.retrooper.packetevents</pattern>
              <shadedPattern>me.vangoo.shaded.packetevents.api</shadedPattern></relocation>
  <relocation><pattern>io.github.retrooper.packetevents</pattern>
              <shadedPattern>me.vangoo.shaded.packetevents.impl</shadedPattern></relocation>
</relocations>
```
- [ ] **Step 4:** Gate — `mvn -B clean package`; resolves PE, BUILD SUCCESS, 14 tests pass. (No code uses PE yet.)
- [ ] **Step 5:** Commit — `build: add shaded+relocated PacketEvents dependency`.

### Task B2: PacketEvents lifecycle in the plugin

**Files:**
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java`

**Interfaces (Produces):** PacketEvents API initialized and usable globally via `PacketEvents.getAPI()` for all later tasks.

- [ ] **Step 1:** Add `onLoad()` (the plugin currently has none):
```java
@Override public void onLoad() {
    PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
    PacketEvents.getAPI().load();
}
```
- [ ] **Step 2:** In `onEnable()` (early, before wiring listeners): `PacketEvents.getAPI().init();`
- [ ] **Step 3:** In `onDisable()`: `PacketEvents.getAPI().terminate();` (guard for null in case load failed).
- [ ] **Step 4:** Gate — `mvn -B clean package`; BUILD SUCCESS. Optional in-server smoke: server boots, no PE init errors in console.
- [ ] **Step 5:** Commit — `feat: initialize PacketEvents lifecycle`.

### Task B3: Capture target skin into `MarionetteMinionTrait`

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/citizens/MarionetteMinionTrait.java`
- Modify: `src/main/java/me/vangoo/pathways/fool/abilities/MarionettistControl.java` (`convertToMarionette`)

**Interfaces (Produces):**
- `MarionetteMinionTrait`: add fields `String skinTextureValue; String skinTextureSignature;`, extend `initialise(...)` (or add `setSkin(String value, String signature)`), add getters `getSkinTextureValue()`, `getSkinTextureSignature()`. Null when target was not a player or had no skin.

**Capture source:** in `convertToMarionette`, when `target instanceof Player p`, read the "textures" property from `p.getPlayerProfile().getProperties()` (Paper PlayerProfile API) → its `getValue()` / `getSignature()`. Pass into the trait alongside the existing `initialise(...)` data.

- [ ] **Step 1:** Add the two fields + getters + a `setSkin(value, signature)` to the trait.
- [ ] **Step 2:** In `convertToMarionette`, before `trait.initialise(...)`, extract the target's texture property (guard non-player / missing property → leave null) and call `trait.setSkin(value, signature)`.
- [ ] **Step 3:** Gate — `mvn -B clean package`; BUILD SUCCESS, 14 tests pass.
- [ ] **Step 4:** Commit — `feat(fool): capture target skin texture into marionette trait`.

### Task B4: `SkinDisguiseService` (packet resend)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/disguise/SkinDisguiseService.java`

**Interfaces (Produces):**
- `public static void disguise(Player player, String textureValue, String textureSignature)` — make `player` appear with the given skin to all viewers in render distance.
- `public static void undisguise(Player player)` — re-render `player` with their **real** profile (no stored state needed).

**Resend sequence (per viewer who can see `player`; the modern 1.21 path):**
1. Build a PacketEvents `UserProfile` for `player` (its UUID + name) and set a `textures` `TextureProperty(value, signature)` (or the real one for undisguise).
2. `WrapperPlayServerPlayerInfoRemove(player UUID)` — drop the tab/profile entry.
3. `WrapperPlayServerPlayerInfoUpdate` with actions `ADD_PLAYER` (+ `UPDATE_LISTED`) carrying that profile.
4. `WrapperPlayServerDestroyEntities(player entityId)` — despawn the entity for the viewer.
5. Respawn the player entity for the viewer (post-1.20.2 spawn-entity packet, `EntityTypes.PLAYER`), then resend metadata + `EntityEquipment` so armor/held items reappear.
- Send packets via `PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, wrapper)`.
- `undisguise` = same sequence with `player`'s real texture property.

**Notes baked into the plan (not placeholders — known caveats to implement against):**
- Skip sending to `player` themselves (first-person skin won't change; resending self risks a client flicker) — disguise is for **other** viewers.
- Exact PE wrapper class names are PE-version-specific; finalize against the pinned 2.x at execution time. The sequence above is the contract.
- Equipment/metadata resend (step 5) is required or the disguised player shows with no armor.

- [ ] **Step 1:** Create the class with the two static methods and a private `resend(Player player, UserProfile profile)` helper implementing steps 2–5.
- [ ] **Step 2:** Gate — `mvn -B clean package`; BUILD SUCCESS, 14 tests pass.
- [ ] **Step 3:** Commit — `feat: add PacketEvents-based skin disguise service`.

### Task B5: Hook disguise into possession lifecycle

**Files:**
- Modify: `src/main/java/me/vangoo/pathways/fool/abilities/MarionettistControl.java` (`swapIn`, `swapOut`, `cleanUp`)

**Interfaces (Consumes):** `SkinDisguiseService.disguise/undisguise` (B4); trait `getSkinTextureValue/Signature` (B3).

- [ ] **Step 1:** In `swapIn`, after the position swap, if `trait.getSkinTextureValue() != null`, call `SkinDisguiseService.disguise(ctx.getCasterPlayer(), value, signature)`.
- [ ] **Step 2:** In `swapOut`, before/after restoring identity, call `SkinDisguiseService.undisguise(casterPlayer)` (guard player non-null). Covers both the normal and `forced` (marionette-died) paths.
- [ ] **Step 3:** In `cleanUp`, for every caster still in `currentPossession`, resolve the player and `undisguise` them (so a plugin reload doesn't leave anyone disguised). Do this before clearing the maps.
- [ ] **Step 4:** Gate — `mvn -B clean package`; BUILD SUCCESS, 14 tests pass.
- [ ] **Step 5:** Commit — `feat(fool): disguise possessing player as the cloned target`.

**Feature B in-server verification (2 players, A possesses a clone of B):**
- A possesses → other players see **A rendered with B's skin** at A's location; armor/held items visible.
- A exits (echo shard, Feature A) → A reverts to their own skin for all viewers.
- Marionette dies while A is inside → A force-exits and reverts skin.
- A relogs while disguised, or `/reload` → A is not left stuck with B's skin (cleanUp / rejoin handling).
- Confirm no console packet errors; confirm a second plugin bundling PacketEvents would not clash (relocation present).

---

## Self-Review

**Spec coverage:**
- #1 exit unreachable (cleared inventory + hidden ability) → Tasks A1–A2 (direct call via a dedicated listener, no cost). ✓
- #2 player appears as clone → Tasks B1–B5 (capture texture → resend with spoofed profile → restore). ✓
- ProtocolLib-alternative requirement → PacketEvents, shaded + relocated, no external server plugin. ✓

**Open items to resolve at execution (flagged, not placeholders):** pin exact PE 2.x version + confirm wrapper class names for 1.21; confirm Paper `PlayerProfile` getter names for texture value/signature. These are version lookups, not design gaps.

**Type consistency:** `IDENTITY` / `isSwapBackItem` / `exitIfPossessing` used by A2 match A1; `disguise/undisguise(Player, String, String)` used by B5 match B4; `getSkinTextureValue/Signature` used by B5 match B3. ✓

**Ordering:** A is fully independent and ships first. B1→B2 (build+init) precede B3→B5 (capture→service→hooks).
