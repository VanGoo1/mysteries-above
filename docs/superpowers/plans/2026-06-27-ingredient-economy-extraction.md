# Ingredient Economy — Extraction (Spec 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a Beyonder fully loses control, condense their Характеристика into the world — a player rampaging into a Warden drops it only when that Warden is slain, and killing a marionette that carries a captured Beyonder identity drops its Характеристика on the spot.

**Architecture:** No new domain logic — the "rule" (a `(pathway, seq)` carrier condenses into exactly `1×` Характеристика) is already the Spec-1 `Characteristic` VO. Everything new is a Bukkit **effect** in `infrastructure`/`presentation`: a shared `CharacteristicExtractor` (mint via the Spec-1 `CharacteristicCodec` + drop + protect), a `WardenRemnantCodec` that stores `(pathway, seq)` in the rampaging Warden's `PersistentDataContainer`, an `EntityDeathEvent` listener that pays out on Warden death, and a one-line drop on marionette death. Wiring is manual in `ServiceContainer` + `MysteriesAbovePlugin`.

**Tech Stack:** Java 21, Spigot/Bukkit 1.21 API, Citizens API (NPCs), Maven (maven-shade), JUnit 5 + ArchUnit. No DI framework — manual wiring in `ServiceContainer`.

## Global Constraints

- User-facing strings are **Ukrainian** (match existing copy).
- `domain` must not import `org.bukkit.*` for its pure packages and must never depend on `me.vangoo.pathways..` (pinned by `ArchitectureTest`). All new classes here live in `me.vangoo.infrastructure..` / `me.vangoo.presentation..`, so `domain` is untouched and `ArchitectureTest` must stay green.
- No DI framework: every new service is constructed and exposed via a getter in `me.vangoo.infrastructure.di.ServiceContainer`, and wired from there / from `MysteriesAbovePlugin`.
- Reuse from Spec 1 (do not re-create): `me.vangoo.infrastructure.items.CharacteristicCodec` with `ItemStack create(String pathwayName, int sequence, int amount)`; `me.vangoo.domain.brewing.Characteristic` (record `(String pathwayName, int sequence)`, accessors `pathwayName()` / `sequence()`).
- Accessors used: `Beyonder.getPathway()` → `Pathway` (`.getName()` → `String`); `Beyonder.getSequenceLevel()` → `int`; `Sequence.level()` → `int`; `MarionetteMinionTrait.getCapturedPathway()` → `Pathway` (null if target was not a Beyonder); `MarionetteMinionTrait.getCapturedSequence()` → `Sequence`.
- These are Bukkit effect classes; per project convention there are **no headless unit tests** (Bukkit `ItemStack`/PDC/entities require a server). Gates are `mvn -q -o clean compile` / `package` and `ArchitectureTest`; behavior is verified in-server in the final task — consistent with how Spec-1 `CharacteristicCodec` was handled.

---

## File Structure

**New:**
- `src/main/java/me/vangoo/infrastructure/items/CharacteristicExtractor.java` — mint + drop + protect a Характеристика at a `Location`.
- `src/main/java/me/vangoo/infrastructure/items/WardenRemnantCodec.java` — tag/read `(pathway, seq)` on an `Entity` via PDC.
- `src/main/java/me/vangoo/infrastructure/listeners/RampageRemnantDeathListener.java` — `EntityDeathEvent` → pay out a tagged Warden's Характеристика.

**Modified:**
- `src/main/java/me/vangoo/infrastructure/listeners/RampageEventListener.java` — tag the spawned Warden with the player's `(pathway, seq)` before `removeBeyonder`.
- `src/main/java/me/vangoo/presentation/listeners/MarionetteLifecycleListener.java` — drop the captured Характеристика on marionette death.
- `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` — construct + expose the two codecs/extractor and the new listener; thread them into the two listeners.
- `src/main/java/me/vangoo/MysteriesAbovePlugin.java` — register `RampageRemnantDeathListener`.

---

## Task 1: CharacteristicExtractor (mint + drop + protect)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/items/CharacteristicExtractor.java`

**Interfaces:**
- Consumes: `me.vangoo.infrastructure.items.CharacteristicCodec` (`create(String, int, int)`).
- Produces: `class CharacteristicExtractor` with constructor `CharacteristicExtractor(CharacteristicCodec codec)` and method `org.bukkit.entity.Item extractTo(Location loc, String pathwayName, int sequence)`.

- [ ] **Step 1: Create the extractor**

Create `src/main/java/me/vangoo/infrastructure/items/CharacteristicExtractor.java`:

```java
package me.vangoo.infrastructure.items;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * Конденсує (вилучає) Характеристику у світ на заданій локації. Спільний ефект для обох каналів
 * вилучення: смерть Бешаного Warden (рампейдж) та смерть маріонетки із захопленою особистістю.
 * Правило тривіальне — рівно 1× Характеристика[шлях, seq]; мінт делегується {@link CharacteristicCodec}.
 */
public final class CharacteristicExtractor {

    private final CharacteristicCodec codec;

    public CharacteristicExtractor(CharacteristicCodec codec) {
        this.codec = codec;
    }

    /**
     * Мінтить 1× Характеристику й кидає її у світ на {@code loc}. Крапля незнищенна (не згорає в
     * лаві/вибуху). Повертає створену сутність-предмет ({@code null}, якщо локація/світ невалідні).
     */
    public Item extractTo(Location loc, String pathwayName, int sequence) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        World world = loc.getWorld();
        ItemStack item = codec.create(pathwayName, sequence, 1);
        Item dropped = world.dropItem(loc, item);
        dropped.setInvulnerable(true); // дефіцитне ядро не має тривіально зникати

        // Флейвор конденсації.
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.6f);
        world.spawnParticle(Particle.WITCH, loc.clone().add(0, 0.5, 0), 30, 0.3, 0.3, 0.3, 0.05);
        return dropped;
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn -q -o clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/items/CharacteristicExtractor.java
git commit -m "feat(brewing): CharacteristicExtractor mints and drops Характеристика in-world"
```

---

## Task 2: WardenRemnantCodec (tag/read essence on an entity)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/items/WardenRemnantCodec.java`

**Interfaces:**
- Consumes: `me.vangoo.domain.brewing.Characteristic`, `org.bukkit.plugin.Plugin`.
- Produces: `class WardenRemnantCodec` with constructor `WardenRemnantCodec(Plugin plugin)`, methods `void tag(Entity entity, String pathwayName, int sequence)`, `boolean isRemnant(Entity entity)`, `Optional<Characteristic> read(Entity entity)`.

> Entity essence lives in the entity's `PersistentDataContainer` (not an `ItemStack`), so `NBTBuilder` does not apply — use PDC directly with a `NamespacedKey`.

- [ ] **Step 1: Create the codec**

Create `src/main/java/me/vangoo/infrastructure/items/WardenRemnantCodec.java`:

```java
package me.vangoo.infrastructure.items;

import me.vangoo.domain.brewing.Characteristic;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Зберігає «есенцію всередині» сутності (Бешаного Warden) у її {@link PersistentDataContainer}:
 * шлях+послідовність загиблого Beyonder. Виплата відбувається при смерті сутності
 * ({@code RampageRemnantDeathListener}).
 */
public final class WardenRemnantCodec {

    private final NamespacedKey pathwayKey;
    private final NamespacedKey sequenceKey;

    public WardenRemnantCodec(Plugin plugin) {
        this.pathwayKey  = new NamespacedKey(plugin, "characteristic_remnant_pathway");
        this.sequenceKey = new NamespacedKey(plugin, "characteristic_remnant_sequence");
    }

    /** Тегує сутність шляхом+послідовністю есенції, яку вона несе. */
    public void tag(Entity entity, String pathwayName, int sequence) {
        if (entity == null) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(pathwayKey, PersistentDataType.STRING, pathwayName);
        pdc.set(sequenceKey, PersistentDataType.INTEGER, sequence);
    }

    /** Чи несе сутність есенцію Характеристики. */
    public boolean isRemnant(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(pathwayKey, PersistentDataType.STRING);
    }

    /** Читає (шлях, seq) есенції, якщо вона є. */
    public Optional<Characteristic> read(Entity entity) {
        if (!isRemnant(entity)) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String pathway = pdc.get(pathwayKey, PersistentDataType.STRING);
        Integer sequence = pdc.get(sequenceKey, PersistentDataType.INTEGER);
        if (pathway == null || sequence == null) {
            return Optional.empty();
        }
        return Optional.of(new Characteristic(pathway, sequence));
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn -q -o clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/items/WardenRemnantCodec.java
git commit -m "feat(brewing): WardenRemnantCodec tags Beyonder essence onto the rampage Warden"
```

---

## Task 3: Rampage tagging + Warden-death payout + wiring

This task is co-dependent (listener constructors ↔ `ServiceContainer` calls); compile/build is run at the end after all edits.

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/listeners/RampageRemnantDeathListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/listeners/RampageEventListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java`

**Interfaces:**
- Consumes: `WardenRemnantCodec`, `CharacteristicExtractor`, `me.vangoo.domain.entities.Beyonder`.
- Produces: `class RampageRemnantDeathListener implements Listener` with constructor `RampageRemnantDeathListener(WardenRemnantCodec remnantCodec, CharacteristicExtractor extractor)`; `RampageEventListener(PassiveAbilityScheduler, BeyonderService, WardenRemnantCodec)`; `ServiceContainer.getCharacteristicExtractor()`, `getWardenRemnantCodec()`, `getRampageRemnantDeathListener()`.

- [ ] **Step 1: Create `RampageRemnantDeathListener`**

Create `src/main/java/me/vangoo/infrastructure/listeners/RampageRemnantDeathListener.java`:

```java
package me.vangoo.infrastructure.listeners;

import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.infrastructure.items.CharacteristicExtractor;
import me.vangoo.infrastructure.items.WardenRemnantCodec;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;

/**
 * Виплачує Характеристику, коли вбито Бешаного Warden, у який трансформувався потойбічний при
 * втраті контролю. Есенцію (шлях+seq) несе сам Warden — її туди записує {@link RampageEventListener}
 * під час трансформації; тут вона конденсується у предмет на місці смерті.
 */
public class RampageRemnantDeathListener implements Listener {

    private final WardenRemnantCodec remnantCodec;
    private final CharacteristicExtractor extractor;

    public RampageRemnantDeathListener(WardenRemnantCodec remnantCodec, CharacteristicExtractor extractor) {
        this.remnantCodec = remnantCodec;
        this.extractor = extractor;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Optional<Characteristic> remnant = remnantCodec.read(event.getEntity());
        if (remnant.isEmpty()) {
            return;
        }
        Characteristic c = remnant.get();
        extractor.extractTo(event.getEntity().getLocation(), c.pathwayName(), c.sequence());
    }
}
```

- [ ] **Step 2: Tag the Warden in `RampageEventListener`**

In `src/main/java/me/vangoo/infrastructure/listeners/RampageEventListener.java`:

Add imports (with the other `me.vangoo` imports near the top):

```java
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.items.WardenRemnantCodec;
```

Replace the constructor (currently lines 31-37) with a version that also takes the codec:

```java
    private final PassiveAbilityScheduler passiveAbilityScheduler;
    private final BeyonderService beyonderService;
    private final WardenRemnantCodec wardenRemnantCodec;

    public RampageEventListener(PassiveAbilityScheduler passiveAbilityScheduler,
                                BeyonderService beyonderService,
                                WardenRemnantCodec wardenRemnantCodec) {
        this.passiveAbilityScheduler = passiveAbilityScheduler;
        this.beyonderService = beyonderService;
        this.wardenRemnantCodec = wardenRemnantCodec;
    }
```

In `executeTransformation(Player player)`, the existing tail is:

```java
        passiveAbilityScheduler.unregisterPlayer(player);
        beyonderService.removeBeyonder(player.getUniqueId());
        // Вбити гравця
        player.setHealth(0.0);
```

Replace it with (capture the carrier BEFORE `removeBeyonder`, tag the already-spawned `warden`):

```java
        // Есенцію носить сам Warden: вилучення відкладене до його смерті (RampageRemnantDeathListener).
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder != null) {
            wardenRemnantCodec.tag(warden, beyonder.getPathway().getName(), beyonder.getSequenceLevel());
        }

        passiveAbilityScheduler.unregisterPlayer(player);
        beyonderService.removeBeyonder(player.getUniqueId());
        // Вбити гравця
        player.setHealth(0.0);
```

> `warden` is the local variable created earlier in `executeTransformation` (`Warden warden = (Warden) ... spawnEntity(...)`). It is in scope at the tail. The player still keeps no power — `removeBeyonder` strips the pathway exactly as before; only the drop is deferred.

- [ ] **Step 3: Wire in `ServiceContainer`**

In `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`:

Add an import (after line 8, with the other listener import):

```java
import me.vangoo.infrastructure.listeners.RampageRemnantDeathListener;
```
(The existing `import me.vangoo.infrastructure.items.*;` already covers `CharacteristicExtractor` and `WardenRemnantCodec`.)

Add fields (next to `private RampageEventListener rampageEventListener;` ~line 77):

```java
    private CharacteristicExtractor characteristicExtractor;
    private WardenRemnantCodec wardenRemnantCodec;
    private RampageRemnantDeathListener rampageRemnantDeathListener;
```

In `initializeInfrastructure()`, immediately after `this.characteristicCodec = new CharacteristicCodec();` (line 151), add:

```java
        this.characteristicExtractor = new CharacteristicExtractor(characteristicCodec);
        this.wardenRemnantCodec = new WardenRemnantCodec(plugin);
```

In `initializeEventListeners()`, replace the `rampageEventListener` line:

```java
        this.rampageEventListener = new RampageEventListener(passiveAbilityScheduler, beyonderService);
```

with:

```java
        this.rampageEventListener = new RampageEventListener(passiveAbilityScheduler, beyonderService, wardenRemnantCodec);
        this.rampageRemnantDeathListener = new RampageRemnantDeathListener(wardenRemnantCodec, characteristicExtractor);
```

Add getters (next to `public RampageEventListener getRampageEventListener()`):

```java
    public CharacteristicExtractor getCharacteristicExtractor() { return characteristicExtractor; }
    public WardenRemnantCodec getWardenRemnantCodec() { return wardenRemnantCodec; }
    public RampageRemnantDeathListener getRampageRemnantDeathListener() { return rampageRemnantDeathListener; }
```

- [ ] **Step 4: Register the listener in `MysteriesAbovePlugin`**

In `src/main/java/me/vangoo/MysteriesAbovePlugin.java`, inside `registerEvents()` (with the other `registerEvents(...)` calls, e.g. right after the `services.getMarionetteRestorer()` line), add:

```java
        getServer().getPluginManager().registerEvents(services.getRampageRemnantDeathListener(), this);
```

> No new import needed: the listener is passed by value from the getter and never referenced by simple name in this file.

- [ ] **Step 5: Compile**

Run: `mvn -q -o clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/listeners/RampageRemnantDeathListener.java src/main/java/me/vangoo/infrastructure/listeners/RampageEventListener.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(brewing): rampage Warden carries the essence; drops Характеристика when slain"
```

---

## Task 4: Marionette death drops captured Характеристика

**Files:**
- Modify: `src/main/java/me/vangoo/presentation/listeners/MarionetteLifecycleListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`

**Interfaces:**
- Consumes: `CharacteristicExtractor`, `MarionetteMinionTrait` (`getCapturedPathway()` / `getCapturedSequence()`), `NPC.getStoredLocation()`.
- Produces: `MarionetteLifecycleListener(AbilityContextFactory, PathwayManager, CharacteristicExtractor)`.

- [ ] **Step 1: Drop on marionette death**

In `src/main/java/me/vangoo/presentation/listeners/MarionetteLifecycleListener.java`:

Add an import (with the other `me.vangoo` imports):

```java
import me.vangoo.infrastructure.items.CharacteristicExtractor;
```

Replace the constructor + fields (currently lines 34-41) with:

```java
    private final AbilityContextFactory abilityContextFactory;
    private final PathwayManager pathwayManager;
    private final CharacteristicExtractor characteristicExtractor;

    public MarionetteLifecycleListener(AbilityContextFactory abilityContextFactory,
                                       PathwayManager pathwayManager,
                                       CharacteristicExtractor characteristicExtractor) {
        this.abilityContextFactory = abilityContextFactory;
        this.pathwayManager = pathwayManager;
        this.characteristicExtractor = characteristicExtractor;
    }
```

In `onNpcDeath(NPCDeathEvent event)`, the method currently begins:

```java
        NPC npc = event.getNPC();
        if (npc == null || !npc.hasTrait(MarionetteMinionTrait.class)) {
            return;
        }
        MarionettistControl mc = resolveControl();
        if (mc == null) return;
```

Insert the essence drop right after the `hasTrait` guard (BEFORE `mc.onMarionetteDeath` destroys the NPC), so the captured `(pathway, seq)` and stored location are still readable:

```java
        NPC npc = event.getNPC();
        if (npc == null || !npc.hasTrait(MarionetteMinionTrait.class)) {
            return;
        }

        // Якщо маріонетка несла особистість потойбічного — її Характеристика вилучається на місці смерті.
        MarionetteMinionTrait remnantTrait = npc.getTraitNullable(MarionetteMinionTrait.class);
        if (remnantTrait != null && remnantTrait.getCapturedPathway() != null
                && remnantTrait.getCapturedSequence() != null) {
            characteristicExtractor.extractTo(
                    npc.getStoredLocation(),
                    remnantTrait.getCapturedPathway().getName(),
                    remnantTrait.getCapturedSequence().level());
        }

        MarionettistControl mc = resolveControl();
        if (mc == null) return;
```

- [ ] **Step 2: Thread the extractor in `ServiceContainer`**

In `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`, in `initializeEventListeners()`, replace:

```java
        this.marionetteLifecycleListener = new MarionetteLifecycleListener(abilityContextFactory, pathwayManager);
```

with:

```java
        this.marionetteLifecycleListener = new MarionetteLifecycleListener(abilityContextFactory, pathwayManager, characteristicExtractor);
```

> `characteristicExtractor` is already assigned in `initializeInfrastructure()` (Task 3, Step 3), which runs before `initializeEventListeners()`.

- [ ] **Step 3: Compile**

Run: `mvn -q -o clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Run the existing test suite (ArchUnit stays green)**

Run: `mvn -q -o test`
Expected: BUILD SUCCESS — `ArchitectureTest` and all existing tests pass (no `domain` changes; new code is in `infrastructure`/`presentation`).

- [ ] **Step 5: Build the shaded jar**

Run: `mvn -q -o clean package`
Expected: BUILD SUCCESS; jar produced in `target/`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/presentation/listeners/MarionetteLifecycleListener.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(brewing): killing a marionette drops its captured Характеристика"
```

---

## Task 5: In-server verification

**Files:** none (manual verification).

> Bukkit effects are verified in-server per project convention. Run on a 1.21 Paper/Spigot server with Citizens.

- [ ] **Step 1: Build and deploy**

Run: `mvn -q -o clean package`. Copy `target/*.jar` to the server `plugins/` folder, start the server.

- [ ] **Step 2: Rampage transformation defers the drop**

As admin, set a Beyonder's sanity to extreme so rampage transformation fires (or use the rampage admin command, e.g. `/rampager start <you>` if available; otherwise drive sanity up). On transformation: a `Бешаний <нік>` Warden spawns, the player dies and **loses their pathway** (no spirituality bar / abilities on respawn), and **no Характеристика is on the ground yet**.

- [ ] **Step 3: Killing the Warden pays out**

Kill the `Бешаний <нік>` Warden. Expected: on its death location a `Характеристика: <Шлях> [Seq N]` item drops (matching the dead player's pathway+sequence), with the amethyst chime + witch particles. Throw it into lava — it does **not** burn (invulnerable).

- [ ] **Step 4: Marionette with a Beyonder identity drops on death**

Create a marionette from a Beyonder target (Fool `Контроль Маріонетки`), then kill the marionette. Expected: a `Характеристика[captured pathway, captured Seq]` drops at the marionette's death location (in addition to the existing inventory drops).

- [ ] **Step 5: Marionette without a Beyonder identity drops nothing extra**

Create a marionette from a non-Beyonder target, then kill it. Expected: **no** Характеристика drops (only the usual inventory items).

- [ ] **Step 6: Ordinary death extracts nothing**

Kill a Beyonder player normally (no rampage). Expected: **no** Характеристика, pathway retained.

- [ ] **Step 7: Final notes (optional)**

If you recorded results in the spec's as-built notes, commit them; otherwise no commit needed.

---

## Self-Review Notes

- **Spec coverage:** rampage tag + deferred drop on Warden death (Task 3), full pathway loss retained via existing `removeBeyonder` (Task 3, unchanged), marionette captured-essence drop (Task 4), shared `CharacteristicExtractor` with invulnerable protective drop + flavor (Task 1), entity PDC tag/read (Task 2), wiring + registration (Tasks 3-4), in-server checks incl. the "ordinary death extracts nothing" and "no captured identity → no drop" negative cases (Task 5). All spec sections mapped. No domain unit tests by design (rule reuses Spec-1 `Characteristic`).
- **Type consistency:** `CharacteristicExtractor.extractTo(Location, String, int)`, `WardenRemnantCodec.{tag(Entity,String,int),isRemnant(Entity),read(Entity)→Optional<Characteristic>}`, `Characteristic.pathwayName()/sequence()`, `RampageEventListener(PassiveAbilityScheduler, BeyonderService, WardenRemnantCodec)`, `MarionetteLifecycleListener(AbilityContextFactory, PathwayManager, CharacteristicExtractor)`, `ServiceContainer.get{CharacteristicExtractor,WardenRemnantCodec,RampageRemnantDeathListener}()` are used identically across tasks.
- **Cross-task compile note:** Task 3 edits four files that are mutually dependent (listener signatures ↔ `ServiceContainer` calls); compile runs once at the end of Task 3. Task 4 reuses the `characteristicExtractor` field assigned in Task 3.
