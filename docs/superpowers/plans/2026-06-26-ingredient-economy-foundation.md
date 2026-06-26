# Ingredient Economy — Foundation (Spec 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Характеристика[pathway, Seq]" item type and config-driven potion recipes with main/auxiliary tags, so a brew can be made either the classic way (all main + all auxiliary) or by substituting one Характеристика for all main ingredients.

**Architecture:** A pure `me.vangoo.domain.brewing` package owns the matching rule (`BrewRecipe.matches` + `BrewMatcher`, unit-tested, no Bukkit). Recipes move from hardcoded Java into `potion-recipes.yml` (loaded by `PotionRecipeConfigLoader` into `RecipeDefinition`), populating `PathwayPotions`. The Характеристика item is built/read by `CharacteristicCodec` (NBT). `PotionCraftingService` extracts string keys from cauldron items and delegates to the matcher; recipe-knowledge gating via `RecipeUnlockService` stays for both brew paths. An admin command grants Характеристики for testing; loot generation refuses `characteristic:` ids.

**Tech Stack:** Java 21, Spigot/Bukkit 1.21 API, Maven (maven-shade), JUnit 5 + ArchUnit. No DI framework — manual wiring in `ServiceContainer`.

## Global Constraints

- User-facing strings are **Ukrainian** (match existing copy).
- `domain` must not import `org.bukkit.*`, `dev.triumphteam.*`, `net.kyori.*` for the pure packages; `domain` must never depend on `me.vangoo.pathways..` (pinned by `ArchitectureTest`).
- No DI framework: every new service is constructed and exposed via getter in `me.vangoo.infrastructure.di.ServiceContainer`, and wired from there.
- Ingredient identity key format already in use: `custom:<id>` for custom items, `vanilla:<MATERIAL>` for vanilla. New: `characteristic:<pathwayName>:<sequence>`.
- Pathway names (canonical, used as config keys and in keys): `Error`, `Visionary`, `Door`, `Justiciar`, `WhiteTower`, `Fool`.
- NBT access goes through `me.vangoo.infrastructure.ui.NBTBuilder` (clone-on-write; `build()` returns the new stack).
- Config files load from the plugin data folder with `saveResource(name, false)` fallback (see `LootTableConfigLoader`).
- Admin commands use permission `mysteriesabove.admin`.

---

## File Structure

**New:**
- `src/main/java/me/vangoo/domain/brewing/RecipeDefinition.java` — record `(List<String> mainIds, List<String> auxIds)`.
- `src/main/java/me/vangoo/domain/brewing/Characteristic.java` — record `(String pathwayName, int sequence)` + `itemKey()`.
- `src/main/java/me/vangoo/domain/brewing/BrewRecipe.java` — recipe VO + `matches(Map<String,Integer>)`.
- `src/main/java/me/vangoo/domain/brewing/BrewMatcher.java` — `findMatch(Collection<BrewRecipe>, Map<String,Integer>)`.
- `src/test/java/me/vangoo/domain/brewing/BrewMatcherTest.java` — unit tests.
- `src/main/java/me/vangoo/infrastructure/items/CharacteristicCodec.java` — build/read the item.
- `src/main/java/me/vangoo/infrastructure/items/PotionRecipeConfigLoader.java` — load `potion-recipes.yml`.
- `src/main/resources/potion-recipes.yml` — recipes with `main`/`auxiliary`.
- `src/main/java/me/vangoo/presentation/commands/CharacteristicCommand.java` — `/characteristic give …`.

**Modified:**
- `src/main/java/me/vangoo/domain/PathwayPotions.java`
- `src/main/java/me/vangoo/pathways/{error/ErrorPotions,visionary/VisionaryPotions,door/DoorPotions,justiciar/JusticiarPotions,whitetower/WhiteTowerPotions,fool/FoolPotions}.java`
- `src/main/java/me/vangoo/application/services/PotionManager.java`
- `src/main/java/me/vangoo/application/services/PotionCraftingService.java`
- `src/main/java/me/vangoo/infrastructure/structures/LootGenerationService.java`
- `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`
- `src/main/java/me/vangoo/MysteriesAbovePlugin.java`
- `src/main/resources/plugin.yml`
- `src/test/java/me/vangoo/architecture/ArchitectureTest.java`

---

## Task 1: Pure brewing matcher (domain.brewing)

**Files:**
- Create: `src/main/java/me/vangoo/domain/brewing/RecipeDefinition.java`
- Create: `src/main/java/me/vangoo/domain/brewing/Characteristic.java`
- Create: `src/main/java/me/vangoo/domain/brewing/BrewRecipe.java`
- Create: `src/main/java/me/vangoo/domain/brewing/BrewMatcher.java`
- Test: `src/test/java/me/vangoo/domain/brewing/BrewMatcherTest.java`
- Modify: `src/test/java/me/vangoo/architecture/ArchitectureTest.java:21-25`

**Interfaces:**
- Produces:
  - `record RecipeDefinition(List<String> mainIds, List<String> auxIds)`
  - `record Characteristic(String pathwayName, int sequence)` with `String itemKey()`
  - `record BrewRecipe(String pathwayName, int sequence, Map<String,Integer> mainCounts, Map<String,Integer> auxCounts)` with `String characteristicKey()` and `boolean matches(Map<String,Integer> provided)`
  - `class BrewMatcher` with `Optional<BrewRecipe> findMatch(Collection<BrewRecipe> recipes, Map<String,Integer> provided)`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/me/vangoo/domain/brewing/BrewMatcherTest.java`:

```java
package me.vangoo.domain.brewing;

import me.vangoo.domain.brewing.BrewMatcher;
import me.vangoo.domain.brewing.BrewRecipe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BrewMatcherTest {

    // Fool Seq 5: main = puppeteer_heartstring; aux = spirit_thread_spool, wraithhold_essence
    private BrewRecipe foolSeq5() {
        return new BrewRecipe(
                "Fool", 5,
                Map.of("custom:puppeteer_heartstring", 1),
                Map.of("custom:spirit_thread_spool", 1, "custom:wraithhold_essence", 1)
        );
    }

    private Map<String, Integer> counts(String... keys) {
        java.util.HashMap<String, Integer> m = new java.util.HashMap<>();
        for (String k : keys) m.merge(k, 1, Integer::sum);
        return m;
    }

    @Test
    void classicExactMatchSucceeds() {
        assertTrue(foolSeq5().matches(counts(
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void missingAuxiliaryFails() {
        assertFalse(foolSeq5().matches(counts(
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool")));
    }

    @Test
    void extraUnknownItemFails() {
        assertFalse(foolSeq5().matches(counts(
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence",
                "vanilla:DIRT")));
    }

    @Test
    void wrongCountFails() {
        assertFalse(foolSeq5().matches(counts(
                "custom:puppeteer_heartstring",
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void characteristicReplacesAllMainsSucceeds() {
        assertTrue(foolSeq5().matches(counts(
                "characteristic:Fool:5",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void characteristicWrongPathwayFails() {
        assertFalse(foolSeq5().matches(counts(
                "characteristic:Door:5",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void characteristicWrongSequenceFails() {
        assertFalse(foolSeq5().matches(counts(
                "characteristic:Fool:6",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void characteristicPlusLeftoverMainFails() {
        assertFalse(foolSeq5().matches(counts(
                "characteristic:Fool:5",
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence")));
    }

    @Test
    void characteristicMissingAuxiliaryFails() {
        assertFalse(foolSeq5().matches(counts(
                "characteristic:Fool:5",
                "custom:spirit_thread_spool")));
    }

    @Test
    void recipeWithNoAuxiliary_singleCharacteristicSucceeds_doubleFails() {
        BrewRecipe noAux = new BrewRecipe("Error", 9,
                Map.of("custom:sphinx_brain", 1), Map.of());
        assertTrue(noAux.matches(counts("characteristic:Error:9")));
        assertFalse(noAux.matches(counts("characteristic:Error:9", "characteristic:Error:9")));
    }

    @Test
    void matcherReturnsFirstMatchingRecipe() {
        BrewMatcher matcher = new BrewMatcher();
        Optional<BrewRecipe> match = matcher.findMatch(
                List.of(foolSeq5()),
                counts("custom:puppeteer_heartstring",
                        "custom:spirit_thread_spool",
                        "custom:wraithhold_essence"));
        assertTrue(match.isPresent());
        assertEquals("Fool", match.get().pathwayName());
        assertEquals(5, match.get().sequence());
    }

    @Test
    void matcherReturnsEmptyWhenNothingMatches() {
        BrewMatcher matcher = new BrewMatcher();
        assertTrue(matcher.findMatch(List.of(foolSeq5()), counts("vanilla:DIRT")).isEmpty());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=BrewMatcherTest`
Expected: FAIL — compilation error / `package me.vangoo.domain.brewing does not exist`.

- [ ] **Step 3: Create `RecipeDefinition`**

```java
package me.vangoo.domain.brewing;

import java.util.List;

/** Сирий опис рецепта з конфіга: id основних та допоміжних інгредієнтів (без Bukkit). */
public record RecipeDefinition(List<String> mainIds, List<String> auxIds) {
}
```

- [ ] **Step 4: Create `Characteristic`**

```java
package me.vangoo.domain.brewing;

/** Кристалічна есенція сили, ключована шляхом+послідовністю. */
public record Characteristic(String pathwayName, int sequence) {
    /** Канонічний ключ-інгредієнт Характеристики. */
    public String itemKey() {
        return "characteristic:" + pathwayName + ":" + sequence;
    }
}
```

- [ ] **Step 5: Create `BrewRecipe`**

```java
package me.vangoo.domain.brewing;

import java.util.HashMap;
import java.util.Map;

/**
 * Правило зіставлення рецепта (чисте, без Bukkit). Інгредієнти — рядкові ключі у форматі
 * {@code custom:<id>} / {@code vanilla:<MATERIAL>}; Характеристика — {@code characteristic:<шлях>:<seq>}.
 */
public record BrewRecipe(
        String pathwayName,
        int sequence,
        Map<String, Integer> mainCounts,
        Map<String, Integer> auxCounts) {

    /** Ключ Характеристики цього (шлях, seq). */
    public String characteristicKey() {
        return "characteristic:" + pathwayName + ":" + sequence;
    }

    /** Чи відповідає наданий набір ключів цьому рецепту (класично АБО через Характеристику). */
    public boolean matches(Map<String, Integer> provided) {
        return matchesClassic(provided) || matchesViaCharacteristic(provided);
    }

    private boolean matchesClassic(Map<String, Integer> provided) {
        Map<String, Integer> required = new HashMap<>(mainCounts);
        auxCounts.forEach((k, v) -> required.merge(k, v, Integer::sum));
        return provided.equals(required);
    }

    private boolean matchesViaCharacteristic(Map<String, Integer> provided) {
        // 1× Характеристика замість УСІХ основних + ті самі допоміжні, нічого зайвого.
        Map<String, Integer> required = new HashMap<>(auxCounts);
        required.merge(characteristicKey(), 1, Integer::sum);
        return provided.equals(required);
    }
}
```

- [ ] **Step 6: Create `BrewMatcher`**

```java
package me.vangoo.domain.brewing;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Підбирає перший рецепт із набору, що збігається з наданими інгредієнтами. */
public final class BrewMatcher {

    public Optional<BrewRecipe> findMatch(Collection<BrewRecipe> recipes, Map<String, Integer> provided) {
        for (BrewRecipe recipe : recipes) {
            if (recipe.matches(provided)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 7: Add `domain.brewing` to the ArchUnit pure scope**

In `src/test/java/me/vangoo/architecture/ArchitectureTest.java`, change the `PURE_DOMAIN` array:

```java
    private static final String[] PURE_DOMAIN = {
            "me.vangoo.domain.entities",
            "me.vangoo.domain.services",
            "me.vangoo.domain.spells",
            "me.vangoo.domain.brewing"
    };
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `mvn test -Dtest=BrewMatcherTest,ArchitectureTest`
Expected: PASS (all `BrewMatcherTest` + both `ArchitectureTest` rules green).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/me/vangoo/domain/brewing src/test/java/me/vangoo/domain/brewing/BrewMatcherTest.java src/test/java/me/vangoo/architecture/ArchitectureTest.java
git commit -m "feat(brewing): pure domain BrewRecipe/BrewMatcher with main/aux + characteristic substitution"
```

---

## Task 2: CharacteristicCodec (build/read the item)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/items/CharacteristicCodec.java`

**Interfaces:**
- Consumes: `me.vangoo.domain.brewing.Characteristic`, `me.vangoo.infrastructure.ui.NBTBuilder`.
- Produces:
  - `ItemStack create(String pathwayName, int sequence, int amount)`
  - `boolean isCharacteristic(ItemStack item)`
  - `Optional<Characteristic> read(ItemStack item)`
  - constants `NBT_PATHWAY`, `NBT_SEQUENCE`

> No headless unit test (Bukkit `ItemStack`/PDC require a server); verified by compile here and in-server in Task 8 — consistent with the project's "effects verified in-server" convention.

- [ ] **Step 1: Create the codec**

```java
package me.vangoo.infrastructure.items;

import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;

/**
 * Будує предмет «Характеристика» та читає його назад. Шлях+послідовність зберігаються у NBT через
 * {@link NBTBuilder} (та сама техніка, що для кастомних предметів).
 */
public final class CharacteristicCodec {

    public static final String NBT_PATHWAY = "characteristic_pathway";
    public static final String NBT_SEQUENCE = "characteristic_sequence";

    /** Будує стак Характеристики для (шлях, seq). */
    public ItemStack create(String pathwayName, int sequence, int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Характеристика: " + pathwayName
                + ChatColor.GRAY + " [Seq " + sequence + "]");
        meta.setLore(List.of(
                "",
                ChatColor.GRAY + "Кристалічна есенція сили.",
                ChatColor.DARK_GRAY + "Замінює всі основні інгредієнти рецепта."
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);

        return new NBTBuilder(item)
                .setString(NBT_PATHWAY, pathwayName)
                .setInt(NBT_SEQUENCE, sequence)
                .build();
    }

    /** Чи є предмет Характеристикою (має NBT-мітку шляху). */
    public boolean isCharacteristic(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getItemMeta() == null) {
            return false;
        }
        return new NBTBuilder(item).getString(item, NBT_PATHWAY).isPresent();
    }

    /** Читає (шлях, seq) з предмета, якщо це Характеристика. */
    public Optional<Characteristic> read(ItemStack item) {
        if (!isCharacteristic(item)) {
            return Optional.empty();
        }
        NBTBuilder builder = new NBTBuilder(item);
        Optional<String> pathway = builder.getString(item, NBT_PATHWAY);
        Optional<Integer> sequence = builder.getInt(item, NBT_SEQUENCE);
        if (pathway.isEmpty() || sequence.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Characteristic(pathway.get(), sequence.get()));
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn -q -o clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/items/CharacteristicCodec.java
git commit -m "feat(brewing): CharacteristicCodec to build and read the Характеристика item"
```

---

## Task 3: potion-recipes.yml + PotionRecipeConfigLoader

**Files:**
- Create: `src/main/resources/potion-recipes.yml`
- Create: `src/main/java/me/vangoo/infrastructure/items/PotionRecipeConfigLoader.java`

**Interfaces:**
- Consumes: `me.vangoo.domain.brewing.RecipeDefinition`.
- Produces: `Map<String, Map<Integer, RecipeDefinition>> load()` (outer key = pathway name, inner key = sequence).

> The main/auxiliary split below is the initial draft derived from the existing recipes (membership preserved exactly). It is editable data — adjust freely in the YAML later.

- [ ] **Step 1: Create `potion-recipes.yml`**

Create `src/main/resources/potion-recipes.yml`:

```yaml
# potion-recipes.yml
# Рецепти зілля: основні (main) та допоміжні (auxiliary) інгредієнти на кожен шлях × послідовність.
# Зварити можна класично (усі main + усі auxiliary) АБО замінивши УСІ main на 1× Характеристику
# відповідного шляху+рівня (auxiliary при цьому все одно потрібні).
recipes:
  Error:
    9:
      main: [sphinx_brain]
      auxiliary: [black_mosquito]
    8:
      main: [sphinx_brain]
      auxiliary: [lavar_octopus_crystal, black_mosquito]
    7:
      main: [plague_serpent_bile]
      auxiliary: [black_mosquito]

  Visionary:
    9:
      main: [manhal_fish_eyeball, goat_horned_blackfish_blood]
      auxiliary: [autumn_crocus_essence, elf_flower_petals]
    8:
      main: [thought_essence]
      auxiliary: [crystalline_moon_orchid, iridescent_pearl_dust]
    7:
      main: [mirror_dragon_eyes, mirror_dragon_blood]
      auxiliary: [tree_of_elders_fruit]
    6:
      main: [black_hunting_lizard_spinal_fluid, split_personality_essence]
      auxiliary: [illusory_chime_tree_fruit, mind_crystal_powder]
    5:
      main: [dream_catcher_heart, adult_mind_dragon_blood]
      auxiliary: [mind_illusion_crystal]

  Door:
    9:
      main: [dimensional_wanderer_eye]
      auxiliary: [ever_shifting_lotus]
    8:
      main: [spirit_eater_pouch, deep_sea_marlin_blood]
      auxiliary: [string_grass_powder, red_chestnut_flower]
    7:
      main: [lavos_squid_blood]
      auxiliary: [meteorite_crystal, starfaced_stone]
    6:
      main: [asmann_complete_brain]
      auxiliary: [cursed_wraith_artifact, imbued_ink_sac]
    5:
      main: [void_drifter_heart]
      auxiliary: [astral_mist_essence, wayfinder_tree_root]

  Justiciar:
    9:
      main: [heart_of_unquestioned_command, eye_of_judicial_insight]
      auxiliary: [bone_of_the_unyielding_frame]
    8:
      main: [pair_of_terror_demon_worms_eyes, silver_war_bears_right_palm]
      auxiliary: [eye_of_perfect_recognition, thread_of_supernatural_intuition]
    7:
      main: [horn_of_a_flash_patterned_black_snake]
      auxiliary: [dust_of_lake_spirit, ashes_of_a_broken_oath_seal]
    6:
      main: [heart_of_the_silent_verdict, fragment_of_a_sealed_domain]
      auxiliary: [ashes_of_a_forbidden_decree, chains_of_collective_fear]
    5:
      main: [core_of_retributive_authority, brand_of_the_unforgiving_oath]
      auxiliary: [ashes_of_a_public_execution, shackles_of_the_guilty]

  WhiteTower:
    9:
      main: [manticore_bird_pituitary_gland, light_antelope_blood]
      auxiliary: [rock_crystal_powder, peppermint_extract]
    8:
      main: [crystallized_cave_monkey_brain]
      auxiliary: [memory_flower_petals, tree_of_wisdom_sap, yellow_amber_powder]
    7:
      main: [phantom_python_vertical_eye, steel_toothed_wolf_heart]
      auxiliary: [mica_powder]
    6:
      main: [prismatic_chameleon_heart]
      auxiliary: [liquid_silver_phantom_residue, mimicry_moss_spores, devastated_blank_mask]
    5:
      main: [void_beholder_petrified_eye]
      auxiliary: [condensed_astral_nebula, ancient_rune_powder, pure_spirituality_tear]

  Fool:
    9:
      main: [nighthawk_eyeball]
      auxiliary: [stellar_aqua_crystal, dragon_savageland_pollen, crimson_star]
    8:
      main: [joker_blood_essence]
      auxiliary: [marbled_ivory_shard, chameleon_slime]
    7:
      main: [phantom_ink]
      auxiliary: [flamingo_crystal, meteorite_fragment]
    6:
      main: [shapeshifter_gland]
      auxiliary: [depths_shadow_essence, mirror_silver_dust, soul_wax]
    5:
      main: [puppeteer_heartstring]
      auxiliary: [spirit_thread_spool, wraithhold_essence]
```

- [ ] **Step 2: Create `PotionRecipeConfigLoader`**

```java
package me.vangoo.infrastructure.items;

import me.vangoo.domain.brewing.RecipeDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Завантажує рецепти зілля з {@code potion-recipes.yml} (тека плагіна, із fallback на ресурс JAR).
 * Повертає мапу: назва шляху → (послідовність → {@link RecipeDefinition}).
 */
public class PotionRecipeConfigLoader {

    private static final String CONFIG_FILE = "potion-recipes.yml";
    private static final String ROOT_KEY = "recipes";

    private final Plugin plugin;

    public PotionRecipeConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, Map<Integer, RecipeDefinition>> load() {
        Map<String, Map<Integer, RecipeDefinition>> result = new HashMap<>();

        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE, false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection recipes = config.getConfigurationSection(ROOT_KEY);
        if (recipes == null) {
            plugin.getLogger().warning("No '" + ROOT_KEY + "' section found in " + CONFIG_FILE);
            return result;
        }

        int loaded = 0;
        for (String pathway : recipes.getKeys(false)) {
            ConfigurationSection sequences = recipes.getConfigurationSection(pathway);
            if (sequences == null) {
                continue;
            }
            Map<Integer, RecipeDefinition> perSequence = new HashMap<>();
            for (String seqKey : sequences.getKeys(false)) {
                int sequence;
                try {
                    sequence = Integer.parseInt(seqKey);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid sequence '" + seqKey + "' for pathway " + pathway
                            + " in " + CONFIG_FILE);
                    continue;
                }
                ConfigurationSection recipeSection = sequences.getConfigurationSection(seqKey);
                if (recipeSection == null) {
                    continue;
                }
                List<String> main = recipeSection.getStringList("main");
                List<String> aux = recipeSection.getStringList("auxiliary");
                perSequence.put(sequence, new RecipeDefinition(main, aux));
                loaded++;
            }
            result.put(pathway, perSequence);
        }

        plugin.getLogger().info("Loaded potion recipes for " + result.size() + " pathways (" + loaded + " recipes)");
        return result;
    }
}
```

- [ ] **Step 3: Compile**

Run: `mvn -q -o clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/potion-recipes.yml src/main/java/me/vangoo/infrastructure/items/PotionRecipeConfigLoader.java
git commit -m "feat(brewing): config-driven potion recipes (potion-recipes.yml + loader)"
```

---

## Task 4: Populate PathwayPotions from config

**Files:**
- Modify: `src/main/java/me/vangoo/domain/PathwayPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/error/ErrorPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/visionary/VisionaryPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/door/DoorPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/justiciar/JusticiarPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/whitetower/WhiteTowerPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/fool/FoolPotions.java`
- Modify: `src/main/java/me/vangoo/application/services/PotionManager.java`

**Interfaces:**
- Consumes: `RecipeDefinition`, `IItemResolver`.
- Produces (on `PathwayPotions`):
  - `void loadRecipes(Map<Integer, RecipeDefinition> defs)`
  - `ItemStack[] getMainIngredients(int sequence)`
  - `ItemStack[] getAuxiliaryIngredients(int sequence)`
  - `ItemStack[] getIngredients(int sequence)` (union of main+aux, or `null` if neither present)
  - new constructor param on each `*Potions`: `Map<Integer, RecipeDefinition> recipes`
  - `PotionManager(PathwayManager, PotionItemFactory, CustomItemService, Map<String, Map<Integer, RecipeDefinition>>)`

- [ ] **Step 1: Replace `PathwayPotions` body**

Replace the whole file `src/main/java/me/vangoo/domain/PathwayPotions.java` with:

```java
package me.vangoo.domain;

import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class PathwayPotions {
    protected HashMap<Integer, ItemStack[]> ingredientsPerSequence;       // основні
    protected HashMap<Integer, ItemStack[]> auxIngredientsPerSequence;    // допоміжні

    private final Pathway pathway;
    private final Color potionColor;
    private final ChatColor nameColor;
    private final List<String> description;
    protected final IItemResolver itemResolver;

    public PathwayPotions(Pathway pathway, Color potionColor, ChatColor nameColor, List<String> description, IItemResolver itemResolver) {
        this.potionColor = potionColor;
        this.pathway = pathway;
        this.nameColor = nameColor;
        this.description = description;
        this.itemResolver = itemResolver;
        this.ingredientsPerSequence = new HashMap<>();
        this.auxIngredientsPerSequence = new HashMap<>();
    }

    /** Наповнює рецепти з конфіга: резолвить id у предмети й розкладає на основні/допоміжні. */
    protected void loadRecipes(Map<Integer, RecipeDefinition> defs) {
        if (defs == null) {
            return;
        }
        for (Map.Entry<Integer, RecipeDefinition> entry : defs.entrySet()) {
            int sequence = entry.getKey();
            List<ItemStack> main = resolveAll(entry.getValue().mainIds());
            List<ItemStack> aux = resolveAll(entry.getValue().auxIds());
            addIngredientsRecipe(sequence, main, aux);
        }
    }

    private List<ItemStack> resolveAll(List<String> ids) {
        List<ItemStack> out = new ArrayList<>();
        if (ids == null) {
            return out;
        }
        for (String id : ids) {
            itemResolver.createItemStack(id).ifPresent(out::add);
        }
        return out;
    }

    /** Старий API: усе основне, 0 допоміжних (зберігається для зворотної сумісності). */
    protected void addIngredientsRecipe(int sequence, ItemStack... ingredients) {
        ingredientsPerSequence.put(sequence, ingredients);
    }

    /** Новий API: окремо основні та допоміжні. */
    protected void addIngredientsRecipe(int sequence, List<ItemStack> main, List<ItemStack> aux) {
        ingredientsPerSequence.put(sequence, main.toArray(new ItemStack[0]));
        auxIngredientsPerSequence.put(sequence, aux.toArray(new ItemStack[0]));
    }

    public ItemStack[] getMainIngredients(int sequence) {
        return ingredientsPerSequence.get(sequence);
    }

    public ItemStack[] getAuxiliaryIngredients(int sequence) {
        return auxIngredientsPerSequence.get(sequence);
    }

    /** Об'єднання основних+допоміжних (для відображення). {@code null}, якщо рецепта немає. */
    public ItemStack[] getIngredients(int sequence) {
        ItemStack[] main = ingredientsPerSequence.get(sequence);
        ItemStack[] aux = auxIngredientsPerSequence.get(sequence);
        if (main == null && aux == null) {
            return null;
        }
        List<ItemStack> all = new ArrayList<>();
        if (main != null) all.addAll(Arrays.asList(main));
        if (aux != null) all.addAll(Arrays.asList(aux));
        return all.toArray(new ItemStack[0]);
    }

    public Pathway getPathway() {
        return pathway;
    }

    public Color getPotionColor() {
        return potionColor;
    }

    public ChatColor getNameColor() {
        return nameColor;
    }

    public List<String> getDescription() {
        return description;
    }
}
```

- [ ] **Step 2: Replace `ErrorPotions`**

```java
package me.vangoo.pathways.error;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;
import java.util.Map;

public class ErrorPotions extends PathwayPotions {
    public ErrorPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver,
                        Map<Integer, RecipeDefinition> recipes) {
        super(pathway, potionColor, ChatColor.RED, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
```

- [ ] **Step 3: Replace `VisionaryPotions`**

```java
package me.vangoo.pathways.visionary;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;
import java.util.Map;

public class VisionaryPotions extends PathwayPotions {
    public VisionaryPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver,
                            Map<Integer, RecipeDefinition> recipes) {
        super(pathway, potionColor, ChatColor.GRAY, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
```

- [ ] **Step 4: Replace `DoorPotions`**

```java
package me.vangoo.pathways.door;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;
import java.util.Map;

public class DoorPotions extends PathwayPotions {
    public DoorPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver,
                       Map<Integer, RecipeDefinition> recipes) {
        super(pathway, potionColor, ChatColor.RED, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
```

- [ ] **Step 5: Replace `JusticiarPotions`**

```java
package me.vangoo.pathways.justiciar;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;
import java.util.Map;

public class JusticiarPotions extends PathwayPotions {
    public JusticiarPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver,
                            Map<Integer, RecipeDefinition> recipes) {
        super(pathway, potionColor, ChatColor.GOLD, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
```

- [ ] **Step 6: Replace `WhiteTowerPotions`**

```java
package me.vangoo.pathways.whitetower;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;
import java.util.Map;

public class WhiteTowerPotions extends PathwayPotions {
    public WhiteTowerPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver,
                             Map<Integer, RecipeDefinition> recipes) {
        super(pathway, potionColor, ChatColor.AQUA, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
```

- [ ] **Step 7: Replace `FoolPotions`**

```java
package me.vangoo.pathways.fool;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;
import java.util.Map;

/**
 * Potion recipes for the Fool pathway. Інгредієнти беруться з potion-recipes.yml.
 */
public class FoolPotions extends PathwayPotions {
    public FoolPotions(Pathway pathway, Color potionColor, IItemResolver itemResolver,
                       Map<Integer, RecipeDefinition> recipes) {
        super(pathway, potionColor, ChatColor.DARK_PURPLE, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
```

- [ ] **Step 8: Update `PotionManager` to thread the recipe config**

In `src/main/java/me/vangoo/application/services/PotionManager.java`:

Add imports near the top (after existing imports):

```java
import me.vangoo.domain.brewing.RecipeDefinition;
import java.util.Map;
```

Replace the constructor and `initializePotions` (lines 28-73) with:

```java
    public PotionManager(
            PathwayManager pathwayManager,
            PotionItemFactory potionItemFactory,
            CustomItemService customItemService,
            Map<String, Map<Integer, RecipeDefinition>> recipeConfig) {
        this.pathwayManager = pathwayManager;
        this.potionItemFactory = potionItemFactory;
        this.potions = new ArrayList<>();
        initializePotions(customItemService, recipeConfig);
    }

    private void initializePotions(CustomItemService customItemService,
                                   Map<String, Map<Integer, RecipeDefinition>> recipeConfig) {
        potions.add(new ErrorPotions(
                pathwayManager.getPathway("Error"),
                Color.fromRGB(26, 0, 181),
                customItemService,
                recipeConfig.getOrDefault("Error", Map.of())
        ));

        potions.add(new VisionaryPotions(
                pathwayManager.getPathway("Visionary"),
                Color.fromRGB(128, 128, 128),
                customItemService,
                recipeConfig.getOrDefault("Visionary", Map.of())
        ));

        potions.add(new DoorPotions(
                pathwayManager.getPathway("Door"),
                Color.fromRGB(0, 0, 115),
                customItemService,
                recipeConfig.getOrDefault("Door", Map.of())
        ));

        potions.add(new JusticiarPotions(
                pathwayManager.getPathway("Justiciar"),
                Color.fromRGB(255, 255, 0),
                customItemService,
                recipeConfig.getOrDefault("Justiciar", Map.of())
        ));

        potions.add(new WhiteTowerPotions(
                pathwayManager.getPathway("WhiteTower"),
                Color.fromRGB(255, 0, 50),
                customItemService,
                recipeConfig.getOrDefault("WhiteTower", Map.of())
        ));

        potions.add(new FoolPotions(
                pathwayManager.getPathway("Fool"),
                Color.fromRGB(128, 0, 128),
                customItemService,
                recipeConfig.getOrDefault("Fool", Map.of())
        ));
    }
```

> Note: `ServiceContainer` (Task 5) supplies the `recipeConfig` argument. This task will not compile standalone until Task 5 updates the `PotionManager` construction call — implement Task 4 and Task 5 together, compiling at the end of Task 5.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/me/vangoo/domain/PathwayPotions.java src/main/java/me/vangoo/pathways src/main/java/me/vangoo/application/services/PotionManager.java
git commit -m "refactor(brewing): populate PathwayPotions from recipe config, split main/aux"
```

---

## Task 5: Brewing integration + wiring

**Files:**
- Modify: `src/main/java/me/vangoo/application/services/PotionCraftingService.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`

**Interfaces:**
- Consumes: `BrewRecipe`, `BrewMatcher`, `CharacteristicCodec`, `PotionRecipeConfigLoader`, `RecipeDefinition`.
- Produces: `ServiceContainer.getCharacteristicCodec()`; `PotionCraftingService(PotionManager, RecipeUnlockService, CustomItemService, CharacteristicCodec)`.

- [ ] **Step 1: Replace `PotionCraftingService` body**

Replace the whole file `src/main/java/me/vangoo/application/services/PotionCraftingService.java` with:

```java
package me.vangoo.application.services;

import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.BrewMatcher;
import me.vangoo.domain.brewing.BrewRecipe;
import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.items.CharacteristicCodec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Application Service: Handles potion crafting logic.
 * Зіставлення (правило) делегується в чистий {@link BrewMatcher}/{@link BrewRecipe}; цей сервіс лише
 * витягує рядкові ключі з предметів і гейтить за знанням рецепта.
 */
public class PotionCraftingService {
    private final PotionManager potionManager;
    private final RecipeUnlockService recipeUnlockService;
    private final CustomItemService customItemService;
    private final CharacteristicCodec characteristicCodec;
    private final BrewMatcher brewMatcher = new BrewMatcher();

    public PotionCraftingService(
            PotionManager potionManager,
            RecipeUnlockService recipeUnlockService,
            CustomItemService customItemService,
            CharacteristicCodec characteristicCodec) {
        this.potionManager = potionManager;
        this.recipeUnlockService = recipeUnlockService;
        this.customItemService = customItemService;
        this.characteristicCodec = characteristicCodec;
    }

    public record CraftingResult(
            boolean success,
            String pathwayName,
            int sequence,
            ItemStack potion,
            String message
    ) {
        public static CraftingResult success(String pathwayName, int sequence, ItemStack potion) {
            return new CraftingResult(true, pathwayName, sequence, potion, "Зілля успішно створено!");
        }

        public static CraftingResult failure(String message) {
            return new CraftingResult(false, null, -1, null, message);
        }
    }

    /** Try to craft potion from ingredients. */
    public CraftingResult trycraft(UUID playerId, List<ItemStack> thrownItems) {
        if (thrownItems.isEmpty()) {
            return CraftingResult.failure("Недостатньо інгредієнтів");
        }

        Map<String, Integer> provided = countItems(thrownItems);

        // Збираємо лише рецепти, які гравець РОЗБЛОКУВАВ (гейт знання для обох шляхів зварювання).
        List<BrewRecipe> unlocked = new ArrayList<>();
        for (PathwayPotions pathwayPotions : potionManager.getPotions()) {
            String pathwayName = pathwayPotions.getPathway().getName();
            for (int sequence = 9; sequence >= 0; sequence--) {
                ItemStack[] main = pathwayPotions.getMainIngredients(sequence);
                ItemStack[] aux = pathwayPotions.getAuxiliaryIngredients(sequence);
                if ((main == null || main.length == 0) && (aux == null || aux.length == 0)) {
                    continue;
                }
                if (!recipeUnlockService.canCraftPotion(playerId, pathwayName, sequence)) {
                    continue;
                }
                Map<String, Integer> mainCounts = countItems(main == null ? List.of() : Arrays.asList(main));
                Map<String, Integer> auxCounts = countItems(aux == null ? List.of() : Arrays.asList(aux));
                unlocked.add(new BrewRecipe(pathwayName, sequence, mainCounts, auxCounts));
            }
        }

        Optional<BrewRecipe> match = brewMatcher.findMatch(unlocked, provided);
        if (match.isEmpty()) {
            return CraftingResult.failure("Інгредієнти не підходять для жодного рецепту");
        }

        BrewRecipe recipe = match.get();
        ItemStack potion = potionManager.createPotionItem(recipe.pathwayName(), Sequence.of(recipe.sequence()));
        return CraftingResult.success(recipe.pathwayName(), recipe.sequence(), potion);
    }

    /** Count items by key (custom item / vanilla / characteristic). */
    private Map<String, Integer> countItems(List<ItemStack> items) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            counts.merge(getItemKey(item), item.getAmount(), Integer::sum);
        }
        return counts;
    }

    /** Unique key for an item. Характеристика перевіряється ПЕРШОЮ (вона не custom item). */
    private String getItemKey(ItemStack item) {
        Optional<Characteristic> characteristic = characteristicCodec.read(item);
        if (characteristic.isPresent()) {
            return characteristic.get().itemKey();
        }
        if (customItemService.isCustomItem(item)) {
            return customItemService.getCustomItem(item)
                    .map(customItem -> "custom:" + customItem.id())
                    .orElse("vanilla:" + item.getType().name());
        }
        return "vanilla:" + item.getType().name();
    }
}
```

- [ ] **Step 2: Wire loader, codec, and recipe config in `ServiceContainer`**

In `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`:

Add imports (with the other `me.vangoo` imports near the top):

```java
import me.vangoo.domain.brewing.RecipeDefinition;
```
(The `me.vangoo.infrastructure.items.*` wildcard import already covers `CharacteristicCodec` and `PotionRecipeConfigLoader`.)

Add fields (near the other infrastructure service fields, around line 52):

```java
    private CharacteristicCodec characteristicCodec;
```

In `initializeInfrastructure()`, replace the PotionManager construction line (currently line 147):

```java
        this.potionManager = new PotionManager(pathwayManager, potionItemFactory, customItemService);
```

with:

```java
        PotionRecipeConfigLoader recipeConfigLoader = new PotionRecipeConfigLoader(plugin);
        java.util.Map<String, java.util.Map<Integer, RecipeDefinition>> recipeConfig = recipeConfigLoader.load();
        this.characteristicCodec = new CharacteristicCodec();
        this.potionManager = new PotionManager(pathwayManager, potionItemFactory, customItemService, recipeConfig);
```

In `initializeApplicationServices(...)`, replace the `potionCraftingService` construction (currently lines 186-190):

```java
        this.potionCraftingService = new PotionCraftingService(
                potionManager,
                recipeUnlockService,
                customItemService
        );
```

with:

```java
        this.potionCraftingService = new PotionCraftingService(
                potionManager,
                recipeUnlockService,
                customItemService,
                characteristicCodec
        );
```

Add a getter (with the other getters, near line 262):

```java
    public CharacteristicCodec getCharacteristicCodec() { return characteristicCodec; }
```

- [ ] **Step 3: Compile (covers Task 4 + Task 5)**

Run: `mvn -q -o clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Run the existing test suite**

Run: `mvn -q -o test`
Expected: BUILD SUCCESS (BrewMatcherTest + ArchitectureTest + any existing tests pass).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/application/services/PotionCraftingService.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(brewing): match cauldron brews via BrewMatcher with characteristic substitution"
```

---

## Task 6: Loot invariant guard

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/structures/LootGenerationService.java:170-176`

**Interfaces:**
- Consumes: existing `createItemFromId(String)`.

- [ ] **Step 1: Refuse `characteristic:` ids in loot generation**

In `createItemFromId`, add the guard as the first lines of the method:

```java
    public ItemStack createItemFromId(String itemId) {
        if (itemId.startsWith("characteristic:")) {
            plugin.getLogger().warning("Refused to generate Характеристика from loot table: " + itemId
                    + " (characteristics must come only from apex creatures / Beyonder death)");
            return null;
        }
        if (itemId.startsWith("potion:")) return createPotion(itemId);
        if (itemId.startsWith("recipe:")) return createRecipeBook(itemId);

        String actualId = itemId.startsWith("custom:") ? itemId.substring(7) : itemId;
        return customItemService.createItemStack(actualId).orElse(null);
    }
```

- [ ] **Step 2: Compile**

Run: `mvn -q -o clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/structures/LootGenerationService.java
git commit -m "feat(brewing): loot never yields Характеристика (defensive guard)"
```

---

## Task 7: Admin command `/characteristic give`

**Files:**
- Create: `src/main/java/me/vangoo/presentation/commands/CharacteristicCommand.java`
- Modify: `src/main/resources/plugin.yml:42` (add command block before `permissions:`)
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (registerCommands)

**Interfaces:**
- Consumes: `CharacteristicCodec`, `PotionManager` (validate pathway, list names), `ServiceContainer.getCharacteristicCodec()`.

- [ ] **Step 1: Create `CharacteristicCommand`**

```java
package me.vangoo.presentation.commands;

import me.vangoo.application.services.PotionManager;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.infrastructure.items.CharacteristicCodec;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /characteristic give <player> <pathway> <seq> [amount]
 * Тимчасовий адмін-канал видачі Характеристик (до появи джерел: апекс-істоти / смерть Beyonder).
 */
public class CharacteristicCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.LIGHT_PURPLE + "[Characteristic] " + ChatColor.RESET;

    private final CharacteristicCodec characteristicCodec;
    private final PotionManager potionManager;

    public CharacteristicCommand(CharacteristicCodec characteristicCodec, PotionManager potionManager) {
        this.characteristicCodec = characteristicCodec;
        this.potionManager = potionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Використання: /characteristic give <гравець> <шлях> <seq> [кількість]");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Використання: /characteristic give <гравець> <шлях> <seq> [кількість]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Гравець не онлайн: " + args[1]);
            return true;
        }

        PathwayPotions pathway = potionManager.getPotionsPathway(args[2]).orElse(null);
        if (pathway == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Невідомий шлях: " + args[2]);
            return true;
        }
        String pathwayName = pathway.getPathway().getName();

        int sequence;
        try {
            sequence = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Невірна послідовність: " + args[3]);
            return true;
        }
        if (sequence < 0 || sequence > 9) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Послідовність має бути від 0 до 9");
            return true;
        }

        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Невірна кількість: " + args[4]);
                return true;
            }
            if (amount < 1 || amount > 64) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Кількість має бути від 1 до 64");
                return true;
            }
        }

        ItemStack item = characteristicCodec.create(pathwayName, sequence, amount);
        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItem(target.getLocation(), item);
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Інвентар повний! Предмет випав на землю.");
        } else {
            target.getInventory().addItem(item);
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN + String.format(
                "Видано %dx Характеристика[%s, Seq %d] гравцю %s", amount, pathwayName, sequence, target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("give"));
        }
        if (args.length == 2) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }
        if (args.length == 3) {
            return filter(args[2], potionManager.getPotions().stream()
                    .map(p -> p.getPathway().getName()).collect(Collectors.toList()));
        }
        if (args.length == 4) {
            return filter(args[3], Arrays.asList("9", "8", "7", "6", "5", "4", "3", "2", "1", "0"));
        }
        if (args.length == 5) {
            return filter(args[4], Arrays.asList("1", "8", "16", "32", "64"));
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase();
        return new ArrayList<>(options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList()));
    }
}
```

- [ ] **Step 2: Register the command in `plugin.yml`**

In `src/main/resources/plugin.yml`, add this block under `commands:` (e.g. right after the `recipe:` block, before `permissions:`):

```yaml
  characteristic:
    description: Grant Characteristic crystals (admin/testing)
    usage: /characteristic give <player> <pathway> <seq> [amount]
    permission: mysteriesabove.admin
    permission-message: "§cYou do not have permission to use this command."
```

- [ ] **Step 3: Wire the command in `MysteriesAbovePlugin.registerCommands()`**

In `src/main/java/me/vangoo/MysteriesAbovePlugin.java`, inside `registerCommands()`, after the `recipe` binding (`getCommand("recipe").setTabCompleter(recipeBookCommand);`), add:

```java
        CharacteristicCommand characteristicCommand = new CharacteristicCommand(
                services.getCharacteristicCodec(), services.getPotionManager());
        getCommand("characteristic").setExecutor(characteristicCommand);
        getCommand("characteristic").setTabCompleter(characteristicCommand);
```

Add the import at the top of `MysteriesAbovePlugin.java` (with the other command imports):

```java
import me.vangoo.presentation.commands.CharacteristicCommand;
```

> If command imports are wildcard (`me.vangoo.presentation.commands.*`), skip the explicit import line.

- [ ] **Step 4: Build the shaded jar**

Run: `mvn -q -o clean package`
Expected: BUILD SUCCESS; jar produced in `target/`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/presentation/commands/CharacteristicCommand.java src/main/resources/plugin.yml src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(brewing): /characteristic give admin command for testing"
```

---

## Task 8: In-server verification

**Files:** none (manual verification).

> Bukkit effects are verified in-server per project convention. Run on a 1.21 Paper/Spigot server with Citizens (plugin dependency).

- [ ] **Step 1: Build and deploy**

Run: `mvn -q -o clean package`
Copy `target/*.jar` to the server `plugins/` folder, start the server.

- [ ] **Step 2: Verify config + startup**

Confirm `plugins/Mysteries-Above/potion-recipes.yml` was written on first start, and the log shows `Loaded potion recipes for 6 pathways`.

- [ ] **Step 3: Classic brew still works**

As admin: unlock a recipe (`/recipe give <you> Fool 5` or equivalent), build an activated cauldron (water cauldron over a lit soul campfire), drop the exact main+auxiliary ingredients for Fool Seq 5 (`puppeteer_heartstring`, `spirit_thread_spool`, `wraithhold_essence`). Expected: potion is produced.

- [ ] **Step 4: Characteristic substitution works**

Run `/characteristic give <you> Fool 5`. With the recipe still unlocked, drop into the cauldron: the Характеристика + the auxiliaries (`spirit_thread_spool`, `wraithhold_essence`) — WITHOUT the main. Expected: potion is produced.

- [ ] **Step 5: Knowledge gate holds**

Lock/clear the recipe (use a fresh player without the unlock). Attempt either brew path. Expected: "Інгредієнти не підходять для жодного рецепту" — no potion.

- [ ] **Step 6: Wrong characteristic fails**

Give `Door:5` characteristic, attempt Fool Seq 5 brew with it. Expected: failure.

- [ ] **Step 7: Loot guard**

Temporarily add a `characteristic:Fool:5` entry to `global_loot.yml`, reload/generate loot. Expected: a warning in console and the item is never produced. Remove the test entry afterward.

- [ ] **Step 8: Final verification commit (if any docs/notes updated)**

If you recorded results in the spec's as-built notes, commit them; otherwise no commit needed.

---

## Self-Review Notes

- **Spec coverage:** Характеристика item (Task 2), config recipes + main/aux (Tasks 3-4), optional substitution matcher (Task 1, integrated Task 5), knowledge gate retained (Task 5 `canCraftPotion`), loot invariant (Task 6), admin command (Task 7), unit tests (Task 1), in-server checks (Task 8). All spec sections mapped.
- **Type consistency:** `RecipeDefinition(mainIds, auxIds)`, `BrewRecipe(pathwayName, sequence, mainCounts, auxCounts)`, `Characteristic(pathwayName, sequence).itemKey()`, `CharacteristicCodec.{create,isCharacteristic,read}`, `PotionRecipeConfigLoader.load()`, `PotionManager(.., recipeConfig)`, `PotionCraftingService(.., characteristicCodec)`, `ServiceContainer.getCharacteristicCodec()` are used identically across tasks.
- **Cross-task compile note:** Task 4 and Task 5 are co-dependent (PotionManager signature ↔ ServiceContainer call); compile is run at the end of Task 5.
