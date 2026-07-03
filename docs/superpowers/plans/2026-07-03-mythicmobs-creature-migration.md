# MythicMobs Creature Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the custom creature engine (spawner, behaviors, codec, appearance) with MythicMobs as the single mob-content system, while keeping spawn-decision rules (convergence, distance gates, selector) and loot economy in the pure domain.

**Architecture:** MythicMobs owns mob *content* (stats, display, equipment, skills/behaviors) via a plugin-shipped content pack. Our plugin keeps mob *rules*: `domain.creatures` (selector, convergence, distance gate) decides *what/where/when* to spawn and calls MythicMobs only as an executor through one gateway class. All `io.lumine` imports are confined to a new `me.vangoo.infrastructure.mythic` package (ArchUnit-enforced). Domain-touching mob effects (sanity drain, beyonder detection) are custom MythicMobs mechanics/conditions registered from that package.

**Tech Stack:** Java 21, Spigot API 1.21, MythicMobs 5.12.1 (free, `provided` scope, plugin dependency), JUnit 5 + ArchUnit, Maven.

## Global Constraints

- Java 21, Spigot API `1.21.11-R0.1-SNAPSHOT`; build with `mvn clean package`; tests with `mvn test`.
- No backward compatibility required — the plugin is pre-release; delete legacy code outright, no deprecation shims.
- User-facing text (mob display names, messages) in **Ukrainian**; code identifiers, YAML keys, logs in English (`.claude/rules/localization.md`).
- Commit convention: `type(scope): subject`, English, imperative, ≤72 chars (`.claude/rules/commit-messages.md`).
- `me.vangoo.domain` must never import `org.bukkit.*` (pure packages) nor `me.vangoo.pathways` — existing `ArchitectureTest` must stay green throughout.
- New invariant added by this plan: `io.lumine..` may only be imported by `me.vangoo.infrastructure.mythic..`.
- `plugin.yml` and `**/*.yml` are Maven-filtered resources — the shipped MythicMobs pack must be **excluded** from filtering (Task 1).
- MythicMobs internal mob names == `creatures.yml` ids (e.g. `error_sphinx_9`). This is the join key between the two systems; never rename one side alone.

## Verification note (in-server vs unit)

Per project convention, pure rules are unit-tested; world effects are verified in-server. MythicMobs skill YAML and bridge components cannot be unit-tested — Task 9 contains the mandatory in-server checklist. Any step marked *[in-server]* is deferred to Task 9; the automated gate for those tasks is `mvn clean package` passing.

A few MythicMobs YAML attribute spellings (`potion` level, `pull` velocity, `healthpercent` condition, `Options.Scale`) vary between MM versions. Task 9 verifies each against the running server (`/mm mechanics`, `/mm conditions` list them in-game); adjust the YAML there if a name differs — that is expected, not a plan failure.

---

### Task 1: Build plumbing (pom, plugin.yml, resource filtering)

**Files:**
- Modify: `pom.xml` (repositories block ~line 95–116, dependencies block ~line 117, resources block ~line 74–93)
- Modify: `src/main/resources/plugin.yml` (line 6, `depend`)

**Interfaces:**
- Produces: compile-time availability of `io.lumine.mythic.*` classes for all later tasks; runtime guarantee that MythicMobs enables before our plugin.

- [ ] **Step 1: Add the Lumine repository to `pom.xml`**

Inside the existing `<repositories>` element append:

```xml
        <repository>
            <id>lumine-repo</id>
            <url>https://mvn.lumine.io/repository/maven-public/</url>
        </repository>
```

- [ ] **Step 2: Add the MythicMobs API dependency**

Inside `<dependencies>`, next to the other `provided` plugin deps (citizens/coreprotect):

```xml
        <dependency>
            <groupId>io.lumine</groupId>
            <artifactId>Mythic-Dist</artifactId>
            <version>5.12.1</version>
            <scope>provided</scope>
        </dependency>
```

- [ ] **Step 3: Exclude the mythic pack from Maven filtering**

The first `<resource>` block filters `**/*.yml`. MythicMobs YAML must ship byte-identical (it contains `<caster.name>`-style placeholders and its own syntax). Change the two resource blocks to:

```xml
        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>true</filtering>
                <includes>
                    <include>plugin.yml</include>
                    <include>config.yml</include>
                    <include>**/*.yml</include>
                </includes>
                <excludes>
                    <exclude>mythic-pack/**</exclude>
                </excludes>
            </resource>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>false</filtering>
                <excludes>
                    <exclude>plugin.yml</exclude>
                    <exclude>config.yml</exclude>
                    <exclude>**/*.yml</exclude>
                </excludes>
            </resource>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>false</filtering>
                <includes>
                    <include>mythic-pack/**</include>
                </includes>
            </resource>
        </resources>
```

- [ ] **Step 4: Declare the hard plugin dependency**

In `src/main/resources/plugin.yml` change line 6:

```yaml
depend: [Citizens, MythicMobs]
```

- [ ] **Step 5: Build**

Run: `mvn clean package`
Expected: BUILD SUCCESS (the new dependency resolves from mvn.lumine.io).

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/resources/plugin.yml
git commit -m "chore(creatures): add MythicMobs dependency and plugin depend"
```

---

### Task 2: Mythic bridge — service holder and custom components

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/mythic/MythicBridge.java`
- Create: `src/main/java/me/vangoo/infrastructure/mythic/components/IsBeyonderCondition.java`
- Create: `src/main/java/me/vangoo/infrastructure/mythic/components/DrainSanityMechanic.java`
- Create: `src/main/java/me/vangoo/infrastructure/mythic/components/ScatterMechanic.java`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (`onEnable`, right after the `ServiceContainer` is constructed)

**Interfaces:**
- Consumes: `BeyonderService.getBeyonder(UUID)`, `Beyonder.increaseSanityLoss(int)`, `BeyonderService.updateBeyonder(Beyonder)`, `me.vangoo.infrastructure.creatures.SafeLocations.passableNear(Location)`.
- Produces: MythicMobs YAML can use condition `isbeyonder` and mechanics `drainsanity{amount=N}`, `scatter{radius=N}` (Task 3 skill files rely on these exact names).

**Design note (allowed static):** MythicMobs instantiates mechanics/conditions itself with a fixed constructor signature — constructor injection is impossible at this boundary. `MythicBridge` is a static holder initialized once in `onEnable` *before* `CustomComponentRegistry` scans the package. This is a plugin-boundary exception to the "no static mutable state" rule and must be documented in the rules file (Task 8).

- [ ] **Step 1: Write `MythicBridge`**

```java
package me.vangoo.infrastructure.mythic;

import me.vangoo.application.services.BeyonderService;

/**
 * Статичний міст до сервісів плагіна для кастомних компонентів MythicMobs.
 * MythicMobs сам конструює механіки/умови з фіксованою сигнатурою конструктора,
 * тому DI тут неможливий — єдиний дозволений static-виняток (див. .claude/rules/mythic-creatures.md).
 */
public final class MythicBridge {

    private static volatile BeyonderService beyonderService;

    private MythicBridge() {}

    public static void init(BeyonderService service) {
        beyonderService = service;
    }

    public static BeyonderService beyonders() {
        return beyonderService;
    }
}
```

- [ ] **Step 2: Write `IsBeyonderCondition`**

```java
package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.utils.annotations.MythicCondition;
import me.vangoo.infrastructure.mythic.MythicBridge;

@MythicCondition(author = "mysteries-above", name = "isbeyonder",
        description = "True if the target player is a Beyonder")
public class IsBeyonderCondition extends SkillCondition implements IEntityCondition {

    public IsBeyonderCondition(String line) {
        super(line);
    }

    @Override
    public boolean check(AbstractEntity target) {
        if (!target.isPlayer()) return false;
        var service = MythicBridge.beyonders();
        return service != null && service.getBeyonder(target.getUniqueId()) != null;
    }
}
```

- [ ] **Step 3: Write `DrainSanityMechanic`**

```java
package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.mythic.MythicBridge;

import java.io.File;

@MythicMechanic(author = "mysteries-above", name = "drainsanity",
        description = "Increases sanity loss of the target Beyonder")
public class DrainSanityMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final int amount;

    public DrainSanityMechanic(SkillExecutor manager, File file, String line, MythicLineConfig mlc) {
        super(manager, file, line, mlc);
        this.amount = mlc.getInteger(new String[]{"amount", "a"}, 1);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!target.isPlayer()) return SkillResult.INVALID_TARGET;
        var service = MythicBridge.beyonders();
        if (service == null) return SkillResult.CONDITION_FAILED;
        Beyonder b = service.getBeyonder(target.getUniqueId());
        if (b == null) return SkillResult.CONDITION_FAILED;
        b.increaseSanityLoss(amount);
        service.updateBeyonder(b);
        return SkillResult.SUCCESS;
    }
}
```

(Note `updateBeyonder` after the mutation — required by `.claude/rules/wiring.md` for persistence.)

- [ ] **Step 4: Write `ScatterMechanic`** (faithful port of `ChaosBehavior` case 0: short random teleport with lava/void safety, Weakness fallback)

```java
package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import me.vangoo.infrastructure.creatures.SafeLocations;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

@MythicMechanic(author = "mysteries-above", name = "scatter",
        description = "Teleports the target a few blocks to a random safe spot; Weakness fallback")
public class ScatterMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final double radius;

    public ScatterMechanic(SkillExecutor manager, File file, String line, MythicLineConfig mlc) {
        super(manager, file, line, mlc);
        this.radius = mlc.getDouble(new String[]{"radius", "r"}, 5.0);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Entity bukkit = target.getBukkitEntity();
        double dx = ThreadLocalRandom.current().nextDouble(-radius, radius);
        double dz = ThreadLocalRandom.current().nextDouble(-radius, radius);
        Location dest = SafeLocations.passableNear(bukkit.getLocation().clone().add(dx, 0, dz));
        var below = dest.clone().subtract(0, 1, 0).getBlock();
        boolean lava = dest.getBlock().getType() == Material.LAVA || below.getType() == Material.LAVA;
        if (below.getType().isSolid() && !lava) {
            bukkit.teleport(dest);
        } else if (bukkit instanceof org.bukkit.entity.LivingEntity living) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, false));
        }
        return SkillResult.SUCCESS;
    }
}
```

- [ ] **Step 5: Register bridge + components in `MysteriesAbovePlugin.onEnable`**

Immediately after `services = new ServiceContainer(...)` is constructed (before listeners/commands registration) add:

```java
        me.vangoo.infrastructure.mythic.MythicBridge.init(services.getBeyonderService());
        new io.lumine.mythic.bukkit.utils.plugins.CustomComponentRegistry(
                this, "me.vangoo.infrastructure.mythic.components");
```

If the import path of `CustomComponentRegistry` differs in 5.12.1 (it lives near the API utils), resolve it via IDE — the class name is stable per the official API docs; only the package may differ.

- [ ] **Step 6: Build**

Run: `mvn clean package`
Expected: BUILD SUCCESS. If any `io.lumine` signature differs (e.g. `SkillCondition` constructor), fix against the resolved artifact sources — the semantics above stay unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/mythic src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(creatures): MythicMobs bridge with beyonder condition and sanity mechanics"
```

---

### Task 3: MythicMobs content pack + installer

**Files:**
- Create: `src/main/resources/mythic-pack/pack.yml`
- Create: `src/main/resources/mythic-pack/Mobs/templates.yml`
- Create: `src/main/resources/mythic-pack/Mobs/error.yml`, `visionary.yml`, `door.yml`, `justiciar.yml`, `whitetower.yml`, `fool.yml`
- Create: `src/main/resources/mythic-pack/Skills/error.yml`, `visionary.yml`, `door.yml`, `justiciar.yml`, `whitetower.yml`, `fool.yml`
- Create: `src/main/java/me/vangoo/infrastructure/mythic/MythicPackInstaller.java`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (`onEnable`, after Task 2 registration)

**Interfaces:**
- Consumes: mechanics/conditions from Task 2 (`isbeyonder`, `drainsanity`, `scatter`).
- Produces: MythicMobs internal mob names identical to `creatures.yml` ids (Task 4 spawns by these names); helper mob `MA_FoolPuppet`.

**Behavior translation contract.** The six deleted Java behaviors map to skills as follows (source classes in `infrastructure/creatures/behavior/`, tick was 20t within r=12, beyonders only):

| Java behavior | Pathway | Faithful YAML translation |
|---|---|---|
| `VerdictBehavior` | justiciar | aura each 20t: Slowness I (II apex) + Mining Fatigue + Weakness, 40t each; WAX_OFF particles on self |
| `RevealBehavior` | whitetower | aura each 20t: Glowing 40t + 25% Blindness 25t; snowball at nearest each 100t (70t apex); END_ROD particles |
| `AmbushBehavior` | visionary | Invisibility on self while no player within 4 blocks; burst on players within 4: Nausea 70t (100t apex) + Blindness 30t + Darkness 60t + 20% `drainsanity`; SQUID_INK particles |
| `SealBehavior` | door | each 140t (100t apex) on nearest: Slowness I (II apex) 80t + Darkness 60t + REVERSE_PORTAL particles |
| `ControlBehavior` | fool | each 60t on nearest: pull (0.9 / 1.2 apex) + Slowness 40t + 20% `drainsanity`; once below 35% HP summon 1 (2 apex) `MA_FoolPuppet`; WITCH particles |
| `ChaosBehavior` | error | each 70t (50t apex) on nearest: random one of {`scatter`, Weakness 80t, Mining Fatigue 80t, Bad Omen 100t} + 10% `drainsanity`; SMOKE particles |

Approximation accepted by design: Java picked the *nearest beyonder* among tracked players; YAML uses `@NearestPlayer{r=12}` + `isbeyonder` target-condition (a non-beyonder standing closer soaks the tick). Arcade-acceptable; noted in Task 8 docs.

- [ ] **Step 1: Write `pack.yml`**

```yaml
name: MysteriesAbove
version: '1.0'
author: mysteries-above
```

- [ ] **Step 2: Write `Mobs/templates.yml`**

```yaml
# Базові шаблони. Кожен pathway має Common/Apex — apex успадковує common і підсилює скіли.
MA_Base:
  AITargetSelectors:
  - 0 clear
  - 1 attacker
  - 2 players
  Options:
    PreventOtherDrops: true
    MaxCombatDistance: 32
    Despawn: true

MA_Justiciar_Common:
  Template: MA_Base
  Skills:
  - skill{s=MA_Justiciar_Aura} @PlayersInRadius{r=12} ~onTimer:20
  - effect:particles{p=wax_off;amount=3;hS=0.4;vS=0.4;y=1} @self ~onTimer:20
MA_Justiciar_Apex:
  Template: MA_Base
  Skills:
  - skill{s=MA_Justiciar_AuraApex} @PlayersInRadius{r=12} ~onTimer:20
  - effect:particles{p=wax_off;amount=3;hS=0.4;vS=0.4;y=1} @self ~onTimer:20

MA_Whitetower_Common:
  Template: MA_Base
  Skills:
  - skill{s=MA_Whitetower_Reveal} @PlayersInRadius{r=12} ~onTimer:20
  - shoot{type=SNOWBALL;velocity=1.6} @NearestPlayer{r=12} ~onTimer:100
  - effect:particles{p=end_rod;amount=4;hS=0.3;vS=0.5;y=1;speed=0.01} @self ~onTimer:20
MA_Whitetower_Apex:
  Template: MA_Base
  Skills:
  - skill{s=MA_Whitetower_Reveal} @PlayersInRadius{r=12} ~onTimer:20
  - shoot{type=SNOWBALL;velocity=1.6} @NearestPlayer{r=12} ~onTimer:70
  - effect:particles{p=end_rod;amount=4;hS=0.3;vS=0.5;y=1;speed=0.01} @self ~onTimer:20

MA_Visionary_Common:
  Template: MA_Base
  Skills:
  - potion{type=INVISIBILITY;duration=45;particles=false} @self ~onTimer:20 ?!playerwithin{d=4}
  - skill{s=MA_Visionary_Burst} @PlayersInRadius{r=4} ~onTimer:20 ?playerwithin{d=4}
MA_Visionary_Apex:
  Template: MA_Base
  Skills:
  - potion{type=INVISIBILITY;duration=45;particles=false} @self ~onTimer:20 ?!playerwithin{d=4}
  - skill{s=MA_Visionary_BurstApex} @PlayersInRadius{r=4} ~onTimer:20 ?playerwithin{d=4}

MA_Door_Common:
  Template: MA_Base
  Skills:
  - skill{s=MA_Door_Seal} @NearestPlayer{r=12} ~onTimer:140
MA_Door_Apex:
  Template: MA_Base
  Skills:
  - skill{s=MA_Door_SealApex} @NearestPlayer{r=12} ~onTimer:100

MA_Fool_Common:
  Template: MA_Base
  Skills:
  - skill{s=MA_Fool_Pull} @NearestPlayer{r=12} ~onTimer:60
  - summon{mob=MA_FoolPuppet;amount=1;radius=1} @self ~onDamaged ?healthpercent{h=<35} 1 
  - effect:particles{p=witch;amount=4;hS=0.3;vS=0.5;y=1} @self ~onTimer:20
MA_Fool_Apex:
  Template: MA_Base
  Skills:
  - skill{s=MA_Fool_PullApex} @NearestPlayer{r=12} ~onTimer:60
  - summon{mob=MA_FoolPuppet;amount=2;radius=1} @self ~onDamaged ?healthpercent{h=<35} 1
  - effect:particles{p=witch;amount=4;hS=0.3;vS=0.5;y=1} @self ~onTimer:20

MA_Error_Common:
  Template: MA_Base
  Skills:
  - skill{s=MA_Error_Chaos} @NearestPlayer{r=12} ~onTimer:70
MA_Error_Apex:
  Template: MA_Base
  Skills:
  - skill{s=MA_Error_ChaosApex} @NearestPlayer{r=12} ~onTimer:50
```

*Once-only puppet summon:* the Java behavior summoned once per life; `~onDamaged ?healthpercent{h=<35}` re-fires on every hit below 35% — add `;cooldown=600` inside the `summon{...}` braces if MM supports mechanic cooldown, otherwise gate via skill cooldown in Task 9 verification. Acceptable arcade drift: a long-fight re-summon every 30s.

- [ ] **Step 3: Write the six `Skills/*.yml` files**

`Skills/justiciar.yml`:

```yaml
MA_Justiciar_Aura:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=SLOWNESS;duration=40;level=1;particles=false}
  - potion{type=MINING_FATIGUE;duration=40;level=1;particles=false}
  - potion{type=WEAKNESS;duration=40;level=1;particles=false}
MA_Justiciar_AuraApex:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=SLOWNESS;duration=40;level=2;particles=false}
  - potion{type=MINING_FATIGUE;duration=40;level=1;particles=false}
  - potion{type=WEAKNESS;duration=40;level=1;particles=false}
```

`Skills/whitetower.yml`:

```yaml
MA_Whitetower_Reveal:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=GLOWING;duration=40;level=1;particles=false}
  - potion{type=BLINDNESS;duration=25;level=1;particles=false} 0.25
```

`Skills/visionary.yml`:

```yaml
MA_Visionary_Burst:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=NAUSEA;duration=70;level=1;particles=false}
  - potion{type=BLINDNESS;duration=30;level=1;particles=false}
  - potion{type=DARKNESS;duration=60;level=1;particles=false}
  - drainsanity{amount=1} 0.2
  - effect:particles{p=squid_ink;amount=8;hS=0.3;vS=0.5;y=1;speed=0.01}
MA_Visionary_BurstApex:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=NAUSEA;duration=100;level=1;particles=false}
  - potion{type=BLINDNESS;duration=30;level=1;particles=false}
  - potion{type=DARKNESS;duration=60;level=1;particles=false}
  - drainsanity{amount=1} 0.2
  - effect:particles{p=squid_ink;amount=8;hS=0.3;vS=0.5;y=1;speed=0.01}
```

`Skills/door.yml`:

```yaml
MA_Door_Seal:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=SLOWNESS;duration=80;level=1;particles=false}
  - potion{type=DARKNESS;duration=60;level=1;particles=false}
  - effect:particles{p=reverse_portal;amount=40;hS=0.6;vS=1.0;y=1;speed=0.05}
MA_Door_SealApex:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=SLOWNESS;duration=80;level=2;particles=false}
  - potion{type=DARKNESS;duration=60;level=1;particles=false}
  - effect:particles{p=reverse_portal;amount=40;hS=0.6;vS=1.0;y=1;speed=0.05}
```

`Skills/fool.yml`:

```yaml
MA_Fool_Pull:
  TargetConditions:
  - isbeyonder true
  Skills:
  - pull{velocity=0.9}
  - potion{type=SLOWNESS;duration=40;level=1;particles=false}
  - drainsanity{amount=1} 0.2
MA_Fool_PullApex:
  TargetConditions:
  - isbeyonder true
  Skills:
  - pull{velocity=1.2}
  - potion{type=SLOWNESS;duration=40;level=1;particles=false}
  - drainsanity{amount=1} 0.2
```

`Skills/error.yml`:

```yaml
MA_Error_Chaos:
  TargetConditions:
  - isbeyonder true
  Skills:
  - randomskill{skills=MA_Error_Scatter,MA_Error_Weak,MA_Error_Fatigue,MA_Error_Omen}
  - drainsanity{amount=1} 0.1
  - effect:particles{p=smoke;amount=6;hS=0.3;vS=0.5;y=1;speed=0.01}
MA_Error_ChaosApex:
  TargetConditions:
  - isbeyonder true
  Skills:
  - randomskill{skills=MA_Error_Scatter,MA_Error_Weak,MA_Error_Fatigue,MA_Error_Omen}
  - drainsanity{amount=1} 0.1
  - effect:particles{p=smoke;amount=6;hS=0.3;vS=0.5;y=1;speed=0.01}
MA_Error_Scatter:
  Skills:
  - scatter{radius=5}
MA_Error_Weak:
  Skills:
  - potion{type=WEAKNESS;duration=80;level=1;particles=false}
MA_Error_Fatigue:
  Skills:
  - potion{type=MINING_FATIGUE;duration=80;level=1;particles=false}
MA_Error_Omen:
  Skills:
  - potion{type=BAD_OMEN;duration=100;level=1;particles=false}
```

- [ ] **Step 4: Write the six `Mobs/*.yml` files (28 mobs + puppet)**

**Field mapping from each `creatures.yml` entry** (open `src/main/resources/creatures.yml` — it is the authoritative data source; convert all 28 entries mechanically):

| creatures.yml | MythicMobs mob |
|---|---|
| key (e.g. `error_sphinx_9`) | mob name (MUST be identical) |
| `base_entity` | `Type` |
| `display_name` (`§x` codes) | `Display` (convert `§` → `&`) |
| `stats.health` | `Health` |
| `stats.damage` | `Damage` |
| `stats.speed` | `Options.MovementSpeed` |
| `stats.scale` (if ≠ 1.0) | `Options.Scale` |
| `tier: common` / `apex` | `Template: MA_<Pathway>_Common` / `MA_<Pathway>_Apex` |
| `equipment` (if present) | `Equipment` list (vanilla materials only; custom-item equipment currently unused in creatures.yml) |
| `clear_vanilla_drops` | nothing — `PreventOtherDrops: true` in `MA_Base` |
| `loot`, `spawn` | stay in `creatures.yml` (Task 6 slims it) |

Complete worked file `Mobs/error.yml` (pattern for the other five):

```yaml
error_sphinx_9:
  Template: MA_Error_Common
  Type: ENDERMAN
  Display: '&7Сфінкс'
  Health: 24
  Damage: 4
  Options:
    MovementSpeed: 0.25

error_sphinx_8:
  Template: MA_Error_Common
  Type: ENDERMAN
  Display: '&8Великий Сфінкс'
  Health: 40
  Damage: 5
  Options:
    MovementSpeed: 0.26
    Scale: 1.1

error_plague_serpent_7:
  Template: MA_Error_Common
  Type: CAVE_SPIDER
  Display: '&2Чумний Змій'
  Health: 70
  Damage: 7
  Options:
    MovementSpeed: 0.27
    Scale: 1.2
```

Additionally, at the end of `Mobs/fool.yml` add the helper mob (port of the hardcoded puppet in `ControlBehavior`):

```yaml
MA_FoolPuppet:
  Type: ZOMBIE
  Display: '&8Лялька'
  Health: 8
  Options:
    PreventOtherDrops: true
    ShowNameOnDamaged: false
    PreventRandomEquipment: true
```

- [ ] **Step 5: Write `MythicPackInstaller`**

The repo is the single source of truth for pack content: the installer **overwrites** server-side files whenever the shipped bytes differ, then requests one `mm reload`.

```java
package me.vangoo.infrastructure.mythic;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Копіює вшитий MythicMobs-пак у plugins/MythicMobs/Packs/MysteriesAbove.
 * Репозиторій — єдине джерело правди: файли на сервері перезаписуються при розбіжності. */
public final class MythicPackInstaller {

    private static final String[] PACK_FILES = {
            "pack.yml",
            "Mobs/templates.yml",
            "Mobs/error.yml", "Mobs/visionary.yml", "Mobs/door.yml",
            "Mobs/justiciar.yml", "Mobs/whitetower.yml", "Mobs/fool.yml",
            "Skills/error.yml", "Skills/visionary.yml", "Skills/door.yml",
            "Skills/justiciar.yml", "Skills/whitetower.yml", "Skills/fool.yml",
    };

    private final Plugin plugin;

    public MythicPackInstaller(Plugin plugin) {
        this.plugin = plugin;
    }

    /** @return true, якщо хоч один файл було створено/оновлено (потрібен mm reload). */
    public boolean installOrUpdate() {
        Path packRoot = plugin.getDataFolder().toPath()
                .resolveSibling("MythicMobs").resolve("Packs").resolve("MysteriesAbove");
        boolean changed = false;
        for (String rel : PACK_FILES) {
            try (InputStream in = plugin.getResource("mythic-pack/" + rel)) {
                if (in == null) {
                    plugin.getLogger().warning("Mythic pack resource missing in jar: " + rel);
                    continue;
                }
                byte[] data = in.readAllBytes();
                Path target = packRoot.resolve(rel);
                if (!Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), data)) {
                    Files.createDirectories(target.getParent());
                    Files.write(target, data);
                    changed = true;
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to install mythic pack file " + rel + ": " + e);
            }
        }
        return changed;
    }
}
```

- [ ] **Step 6: Run the installer in `onEnable`**

After the Task 2 registration lines:

```java
        boolean packChanged = new me.vangoo.infrastructure.mythic.MythicPackInstaller(this).installOrUpdate();
        if (packChanged) {
            getServer().getScheduler().runTaskLater(this, () ->
                    getServer().dispatchCommand(getServer().getConsoleSender(), "mythicmobs reload"), 20L);
        }
```

- [ ] **Step 7: Build**

Run: `mvn clean package`
Expected: BUILD SUCCESS; verify the pack is in the jar: `unzip -l target/mysteries-above-1.0-SNAPSHOT.jar | grep mythic-pack` shows 14 files.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/mythic-pack src/main/java/me/vangoo/infrastructure/mythic/MythicPackInstaller.java src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(creatures): ship MythicMobs pack with pathway templates and skills"
```

---

### Task 4: Gateway + spawn-path cutover, delete legacy engine

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/mythic/MythicCreatureGateway.java`
- Modify: `src/main/java/me/vangoo/presentation/listeners/NaturalCreatureSpawnListener.java`
- Modify: `src/main/java/me/vangoo/presentation/listeners/StructureCreatureSpawnListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/schedulers/AmbientCreatureSpawner.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (fields 76–96, init 188–214 & 276–281, getters 350–364, onDisable helpers ~414)
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (onDisable ~line 134, registerEvents ~243, registerCommands ~277)
- Modify: `src/main/resources/plugin.yml` (remove `creature` command block, lines ~48–50)
- Delete: `infrastructure/creatures/CreatureSpawner.java`, `CreatureAppearance.java`, `VanillaAppearance.java`, `CreatureAggression.java`, the whole `infrastructure/creatures/behavior/` package (7 files), `presentation/listeners/CreatureLoadListener.java`, `presentation/commands/CreatureCommand.java`

**Interfaces:**
- Consumes: MythicMobs API (`MythicBukkit.inst().getMobManager()`), Task 3 mob names (= `CreatureDefinition.id()`).
- Produces: `MythicCreatureGateway` with exactly:
  - `Optional<LivingEntity> spawn(String mobId, Location loc)`
  - `boolean isCreature(Entity e)`
  - `Optional<String> creatureId(Entity e)`
  Task 5 rewires death/damage listeners onto `isCreature`/`creatureId`.

**What replaces what:** behavior ticking → MythicMobs skills (Task 3); PDC re-attach on chunk load (`CreatureLoadListener`) → MythicMobs' own mob persistence; forced aggro (`CreatureAggression`) → `AITargetSelectors` in `MA_Base`; `/creature spawn` → MythicMobs' `/mm mobs spawn <id>`.

- [ ] **Step 1: Write `MythicCreatureGateway`**

```java
package me.vangoo.infrastructure.mythic;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/** Єдиний вхід до MythicMobs: спавн за internal name та ідентифікація сутностей.
 * Увесь io.lumine-код живе в infrastructure.mythic (ArchitectureTest). */
public final class MythicCreatureGateway {

    private final Plugin plugin;

    public MythicCreatureGateway(Plugin plugin) {
        this.plugin = plugin;
    }

    public Optional<LivingEntity> spawn(String mobId, Location loc) {
        if (loc == null || loc.getWorld() == null) return Optional.empty();
        MythicMob mob = MythicBukkit.inst().getMobManager().getMythicMob(mobId).orElse(null);
        if (mob == null) {
            plugin.getLogger().warning("Unknown MythicMobs mob '" + mobId + "'; skipping spawn");
            return Optional.empty();
        }
        ActiveMob active = mob.spawn(BukkitAdapter.adapt(loc), 1);
        Entity bukkit = active.getEntity().getBukkitEntity();
        return bukkit instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
    }

    public boolean isCreature(Entity e) {
        return e != null && MythicBukkit.inst().getMobManager().isMythicMob(e);
    }

    public Optional<String> creatureId(Entity e) {
        if (e == null) return Optional.empty();
        return MythicBukkit.inst().getMobManager().getActiveMob(e.getUniqueId())
                .map(am -> am.getType().getInternalName());
    }
}
```

- [ ] **Step 2: Rewire the two spawn listeners**

In `NaturalCreatureSpawnListener` and `StructureCreatureSpawnListener`: replace the `CreatureSpawner spawner` field/constructor-param with `me.vangoo.infrastructure.mythic.MythicCreatureGateway gateway`, and replace the spawn call:

```java
// було:  spawner.spawn(pick.get(), event.getLocation());
gateway.spawn(pick.get().id(), event.getLocation());
// (у Structure-лістенері відповідно: gateway.spawn(pick.get().id(), SafeLocations.passableNear(loc));)
```

Everything else (selector, distance gate, convergence bias, chunk cooldown) stays byte-identical.

- [ ] **Step 3: Rewire `AmbientCreatureSpawner`**

Replace constructor params `CreatureSpawner spawner, CreatureCodec codec` with `MythicCreatureGateway gateway`; replace `codec.isCreature(e)` with `gateway.isCreature(e)` in the nearby-count loop, and `spawner.spawn(pick.get(), spot.get())` with `gateway.spawn(pick.get().id(), spot.get())`. Remove the now-unused imports.

- [ ] **Step 4: Rewire `ServiceContainer`**

- Delete fields + init + getters for: `creatureBehaviorFactory`, `creatureBehaviorManager`, `creatureSpawner`, `creatureLoadListener`, `creatureCommand` (lines 88–90, 94, 96, 194–201, 209–210, 213–214, 358, 362, 364).
- Add field + init (in `initializeApplicationServices`, before the listeners that need it) + getter:

```java
this.mythicCreatureGateway = new me.vangoo.infrastructure.mythic.MythicCreatureGateway(plugin);
```

- Update the constructions of `NaturalCreatureSpawnListener`, `StructureCreatureSpawnListener`, `AmbientCreatureSpawner` to pass `mythicCreatureGateway` instead of the deleted spawner/codec args (keep `creatureCodec` construction for now — Task 5 removes it).
- In the container's stop/cleanup path remove the `creatureBehaviorManager` handling (line ~414 area keeps only `ambientCreatureSpawner.stop()`).

- [ ] **Step 5: Clean `MysteriesAbovePlugin`**

- onDisable: delete the `getCreatureBehaviorManager().stopAll()` block (lines 134–136).
- registerEvents: delete the `CreatureLoadListener` registration (line 243).
- registerCommands: delete the two `creature` command lines (277–278).

- [ ] **Step 6: Delete legacy files**

```bash
git rm src/main/java/me/vangoo/infrastructure/creatures/CreatureSpawner.java \
       src/main/java/me/vangoo/infrastructure/creatures/CreatureAppearance.java \
       src/main/java/me/vangoo/infrastructure/creatures/VanillaAppearance.java \
       src/main/java/me/vangoo/infrastructure/creatures/CreatureAggression.java \
       src/main/java/me/vangoo/presentation/listeners/CreatureLoadListener.java \
       src/main/java/me/vangoo/presentation/commands/CreatureCommand.java
git rm -r src/main/java/me/vangoo/infrastructure/creatures/behavior
```

- [ ] **Step 7: Remove the `creature` command from `plugin.yml`** (the 3-line block under `commands:`).

- [ ] **Step 8: Build + tests**

Run: `mvn clean package`
Expected: BUILD SUCCESS, all existing tests green (domain untouched).

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor(creatures): spawn creatures through MythicMobs gateway"
```

Body: `Replaces CreatureSpawner/behavior ticking/PDC re-attach with MythicMobs mobs, skills and persistence; admin spawn moves to /mm mobs spawn.`

---

### Task 5: Identity cutover — death & damage listeners, delete `CreatureCodec`

**Files:**
- Modify: `src/main/java/me/vangoo/presentation/listeners/CreatureDeathListener.java`
- Modify: `src/main/java/me/vangoo/presentation/listeners/CreatureDamageListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`
- Delete: `src/main/java/me/vangoo/infrastructure/creatures/CreatureCodec.java`

**Interfaces:**
- Consumes: `MythicCreatureGateway.isCreature(Entity)`, `MythicCreatureGateway.creatureId(Entity)` from Task 4.
- Produces: loot economy still keyed by `CreatureDefinition` registry (Task 6 relies on `def.loot()` surviving).

- [ ] **Step 1: Rewire `CreatureDeathListener`**

Replace the `CreatureCodec codec` field/param with `MythicCreatureGateway gateway`; the handler keeps `EntityDeathEvent` (drops list is mutable there) and becomes:

```java
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Optional<String> id = gateway.creatureId(event.getEntity());
        if (id.isEmpty()) return;
        CreatureDefinition def = registry.get(id.get());
        if (def == null) return; // Mythic mob without loot rules (e.g. MA_FoolPuppet)

        event.getDrops().clear(); // PreventOtherDrops страхує, це — для певності

        Beyonder killer = null;
        Player p = event.getEntity().getKiller();
        if (p != null) {
            killer = beyonderService.getBeyonder(p.getUniqueId());
        }

        LootTableData loot = def.loot();
        int count = rollCount(loot.minItems(), loot.maxItems());
        List<ItemStack> drops = lootService.generateLoot(loot, count, false, killer);
        event.getDrops().addAll(drops);
    }
```

- [ ] **Step 2: Rewire `CreatureDamageListener`**

Replace `codec.isCreature(...)` with `gateway.isCreature(...)` in `isCreatureSource` (both direct and projectile-shooter branches); swap the field/constructor param accordingly.

- [ ] **Step 3: Rewire `ServiceContainer`**

Pass `mythicCreatureGateway` into both listeners; delete the `creatureCodec` field, its init (line 192) and any getter.

- [ ] **Step 4: Delete the codec**

```bash
git rm src/main/java/me/vangoo/infrastructure/creatures/CreatureCodec.java
```

- [ ] **Step 5: Build + tests**

Run: `mvn clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(creatures): identify creatures via MythicMobs API, drop PDC codec"
```

---

### Task 6: Slim the domain model and `creatures.yml`

**Files:**
- Test: `src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java`
- Modify: `src/main/java/me/vangoo/domain/creatures/CreatureDefinition.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/CreatureConfigLoader.java`
- Modify: `src/main/resources/creatures.yml`

**Interfaces:**
- Produces: `CreatureDefinition(String id, String baseEntityType, CreatureTier tier, LootTableData loot, SpawnRule spawn, String pathway, int sequence)` — consumed by `CreatureSelector` (unchanged logic), `CreatureDeathListener` (`loot()`), `AmbientCreatureSpawner` (`baseEntityType()` for the aquatic check), spawn listeners (`id()`).
- Removed for good: `displayName`, `stats`, `equipment`, `appearance`, `clearVanillaDrops` — now owned by the MythicMobs pack. `baseEntityType` intentionally stays duplicated (join for `AmbientSpawnLocation.isAquatic`); documented in Task 8.

- [ ] **Step 1: Update test fixtures (red)**

In `CreatureSelectorTest` replace the two `def(...)` factory methods and the `biasAgainstNullPathwayCreatureIsNoOp` inline construction:

```java
    private CreatureDefinition def(String id, SpawnRule spawn) {
        return def(id, spawn, "visionary", 9);
    }

    private CreatureDefinition def(String id, SpawnRule spawn, String pathway, int sequence) {
        return new CreatureDefinition(
                id, "GUARDIAN", CreatureTier.COMMON,
                new LootTableData(List.of(), 1, 2),
                spawn, pathway, sequence);
    }
```

and in `biasAgainstNullPathwayCreatureIsNoOp`:

```java
        CreatureDefinition noPathway = new CreatureDefinition("x", "GUARDIAN",
                CreatureTier.COMMON, new LootTableData(List.of(), 1, 2),
                natural(0.3), null, 0);
```

Remove the now-unused `Map` and `CreatureStats` imports.

- [ ] **Step 2: Run to verify red**

Run: `mvn test -Dtest=CreatureSelectorTest`
Expected: COMPILATION ERROR (constructor arity mismatch).

- [ ] **Step 3: Slim the record**

```java
package me.vangoo.domain.creatures;

import me.vangoo.domain.valueobjects.LootTableData;

/**
 * Правила спавну/луту істоти. Контент моба (стати, вигляд, скіли) живе у MythicMobs-паку;
 * {@code id} — internal name MythicMobs-моба (join-ключ), {@code baseEntityType} — базовий
 * ванільний тип (дублює Type у паку; потрібен чистому домену для aquatic-логіки ambient-спавну).
 */
public record CreatureDefinition(
        String id,
        String baseEntityType,
        CreatureTier tier,
        LootTableData loot,
        SpawnRule spawn,
        String pathway,
        int sequence) {}
```

- [ ] **Step 4: Slim `CreatureConfigLoader.parseCreature`**

Delete parsing of `display_name`, `clear_vanilla_drops`, `appearance`, `stats`, `equipment` (and the `parseStats`/`parseEquipment` methods); the return becomes:

```java
        return new CreatureDefinition(id, baseEntity.toUpperCase(Locale.ROOT), tier,
                loot, spawn, pathway, sequence);
```

(pathway/sequence derivation from the id stays exactly as-is).

- [ ] **Step 5: Slim `creatures.yml`**

For each of the 28 entries delete the keys `display_name`, `stats`, `appearance`, `clear_vanilla_drops`, `equipment`; keep `base_entity`, `tier`, `loot`, `spawn`. Update the header comment to say that mob content lives in `src/main/resources/mythic-pack/` and the id must match the MythicMobs internal name. Example result:

```yaml
  error_sphinx_9:
    base_entity: ENDERMAN
    tier: common
    loot:
      min_items: 0
      max_items: 1
      items:
        - { id: sphinx_brain, weight: 50, min: 1, max: 1 }
    spawn:
      natural: { biomes: [DESERT, BADLANDS], replace: [HUSK, ZOMBIE], chance: 0.005 }
      structure: { keys: [], chance: 0.0 }
```

- [ ] **Step 6: Fix the last field consumer**

`CreatureDeathListener` still calls `def.clearVanillaDrops()`? No — Task 5 already replaced it with an unconditional `event.getDrops().clear()`. Search to confirm nothing references removed fields:

Run: `grep -rn "clearVanillaDrops\|\.stats()\|\.equipment()\|\.appearance()\|\.displayName()" src/main/java`
Expected: no matches.

- [ ] **Step 7: Green**

Run: `mvn test`
Expected: all tests pass, including `CreatureSelectorTest` and `ArchitectureTest`.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor(creatures): slim CreatureDefinition to spawn and loot rules"
```

---

### Task 7: ArchUnit — confine `io.lumine` to the bridge package

**Files:**
- Test: `src/test/java/me/vangoo/architecture/ArchitectureTest.java`

**Interfaces:**
- Produces: build-failing guard used by all future creature work.

- [ ] **Step 1: Add the rule**

Append to `ArchitectureTest`:

```java
    @Test
    void mythicMobsApiIsConfinedToBridgePackage() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("me.vangoo");

        noClasses()
                .that().resideOutsideOfPackage("me.vangoo.infrastructure.mythic..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.lumine..")
                .because("увесь код інтеграції з MythicMobs живе за шлюзом infrastructure.mythic")
                .check(classes);
    }
```

- [ ] **Step 2: Run it**

Run: `mvn test -Dtest=ArchitectureTest`
Expected: PASS. If it fails, a listener/scheduler imports `io.lumine` directly — route the call through `MythicCreatureGateway` instead (that is the point of the rule).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/me/vangoo/architecture/ArchitectureTest.java
git commit -m "test(creatures): confine io.lumine imports to mythic bridge package"
```

---

### Task 8: Documentation and rules

**Files:**
- Create: `.claude/rules/mythic-creatures.md`
- Modify: `CLAUDE.md` (architecture `infrastructure` bullet, admin-commands line, build/shading note)
- Modify: `.claude/rules/persistence-and-resources.md` (creatures.yml description, `depend` list, resource-filtering note)

- [ ] **Step 1: Write `.claude/rules/mythic-creatures.md`**

```markdown
# Істоти: MythicMobs

Контент мобів (стати, вигляд, скіли, шаблони) живе у **MythicMobs-паку** `src/main/resources/mythic-pack/`
(розгортається у `plugins/MythicMobs/Packs/MysteriesAbove` через `MythicPackInstaller`, який **перезаписує**
серверні файли — джерело правди тільки репозиторій, не редагуй пак на сервері).
Правила спавну/луту лишаються в коді: `domain.creatures` + `creatures.yml`.

## Join-ключ

`creatures.yml` id == internal name моба в паку (напр. `error_sphinx_9`). Перейменування — завжди в обох місцях.
`base_entity` у `creatures.yml` дублює `Type` пака свідомо (потрібен чистому домену для aquatic ambient-спавну).

## Додати істоту

1. Моб у `mythic-pack/Mobs/<pathway>.yml`: `Template: MA_<Pathway>_Common|Apex`, Type/Display/Health/Damage/Options.
2. Запис у `creatures.yml`: `base_entity`, `tier`, `loot` (тільки інгредієнти), `spawn` (natural/structure).
3. Нова поведінка → метаскіл у `mythic-pack/Skills/<pathway>.yml`; таргет-гейт — умова `isbeyonder`.
4. Файл додано? Впиши його в `MythicPackInstaller.PACK_FILES`.
5. Перевірка in-server: `/mm mobs spawn <id>`, скіли/лут/агро.

## Межі (ArchitectureTest)

- `io.lumine..` — ТІЛЬКИ в `me.vangoo.infrastructure.mythic..` (`mythicMobsApiIsConfinedToBridgePackage`).
  Спавн/ідентифікація для решти коду — через `MythicCreatureGateway` (`spawn(id, loc)`, `isCreature`, `creatureId`).
- Кастомні механіки/умови (`drainsanity`, `scatter`, `isbeyonder`) — в `infrastructure.mythic.components`;
  сервіси беруть зі статичного `MythicBridge` (єдиний дозволений static-виняток: конструктор диктує MythicMobs).
  Після мутації Beyonder у механіці — обовʼязково `beyonderService.updateBeyonder(...)`.
- Балансові числа спавну/луту (шанси, конвергенція, дистанції) — у `domain.creatures` з юніт-тестами,
  НЕ в YAML пака. У паку — тільки хореографія ефектів (аркадні прості атаки, ванільна стилістика).

## Відомі спрощення

- Таргет «найближчий потойбічний» апроксимовано `@NearestPlayer + isbeyonder` (не-потойбічний ближче — тік холостий).
- Саммон ляльок Fool може повторюватися в довгому бою (замість one-shot у старому коді).

## 3D-моделі (майбутнє)

Обирається per-mob у паку: ванільний `Type` (за замовчуванням) або ModelEngine/BetterModel-модель
через їхню інтеграцію з MythicMobs — код плагіна не змінюється.
```

- [ ] **Step 2: Update `CLAUDE.md`**

- In the `infrastructure` bullet add: `mythic` (MythicMobs bridge: `MythicCreatureGateway`, `MythicBridge`, custom components, `MythicPackInstaller`).
- In the creatures-related text state that mob content lives in the MythicMobs pack `src/main/resources/mythic-pack/`, rules in `domain.creatures`/`creatures.yml` (reference `.claude/rules/mythic-creatures.md`).
- Admin commands list: remove `/creature`, mention `/mm mobs spawn <id>` for testing.
- Build note: MythicMobs is a `provided` plugin dependency (`depend: [Citizens, MythicMobs]`), not shaded.

- [ ] **Step 3: Update `.claude/rules/persistence-and-resources.md`**

- Config table: `creatures.yml` → «правила спавну/луту істот (контент мобів — mythic-pack, див. mythic-creatures.md)».
- `depend: [Citizens, MythicMobs]`.
- Resources section: `mythic-pack/**` є **нефільтрованим** ресурсом (не додавай туди `${}`-плейсхолдери Maven).

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md .claude/rules
git commit -m "docs(rules): MythicMobs creature system rules and doc updates"
```

---

### Task 9: Final verification (build + in-server)

**Files:** none (verification only).

- [ ] **Step 1: Full build & tests**

Run: `mvn clean package`
Expected: BUILD SUCCESS, all tests green, shaded jar produced.

- [ ] **Step 2: Server smoke — startup**

Install MythicMobs 5.12.1 + the built jar on the dev server. On first start expect in console: pack files installed, one delayed `mythicmobs reload`, no `Unknown mechanic/condition` errors (that would mean `CustomComponentRegistry` ran after MM compiled skills — if so, move registration earlier in `onEnable`).
Run `/mm mobs list` — all 28 ids + `MA_FoolPuppet` present.

- [ ] **Step 3: Per-archetype behavior check** (spawn via `/mm mobs spawn <id>`, stand nearby as a Beyonder)

- `justiciar_judge_9`: Slowness/Fatigue/Weakness aura within ~12 blocks, WAX_OFF particles; apex (`justiciar_retribution_5`) gives Slowness II.
- `whitetower_manticore_9`: Glowing on you, occasional Blindness, snowball shots ~5s.
- `visionary_manhal_9`: invisible until you approach within 4 blocks, then Nausea/Blindness/Darkness burst; sanity occasionally ticks up (check via your sanity UI/command).
- `door_wanderer_9`: every ~7s Slowness+Darkness + reverse-portal particles.
- `fool_nighthawk_9`: periodic pull toward the mob; drop it below 35% HP → «Лялька» zombie summons.
- `error_sphinx_9`: every ~3.5s one random effect (short teleport / Weakness / Fatigue / Bad Omen).
- Non-beyonder player (fresh account / `/pathway` reset): auras and pulls must NOT affect them (`isbeyonder` gate).
- Fix any YAML attribute-name mismatches found via `/mm mechanics` / `/mm conditions` and re-run; sync fixes back into `src/main/resources/mythic-pack/` (repo is the source of truth), rebuild, confirm installer re-deploys them.

- [ ] **Step 4: Rules integration check**

- Kill a creature as a Beyonder → only ingredient drops from its `creatures.yml` loot table (0–1 items), no vanilla drops.
- Get hit by a creature → damage reduced per `SequenceScaler.creatureDamageReduction` (compare hearts vs a non-beyonder).
- Ambient spawn: stand >2000 blocks from world spawn as a Beyonder in a matching biome, wait several minutes → creature of *your* pathway appears; `/mm mobs list active` confirms it's MythicMobs-tracked.
- Restart the server with a live creature in a loaded chunk → after restart it still has its skills (MythicMobs persistence replaced `CreatureLoadListener`).

- [ ] **Step 5: Commit any YAML fixes from verification**

```bash
git add src/main/resources/mythic-pack
git commit -m "fix(creatures): correct MythicMobs attribute names found in-server"
```

(Skip if nothing needed fixing.)
