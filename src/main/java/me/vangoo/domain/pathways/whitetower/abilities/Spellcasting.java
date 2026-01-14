package me.vangoo.domain.pathways.whitetower.abilities;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.pathways.whitetower.abilities.custom.GeneratedSpell;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class Spellcasting extends ActiveAbility {

    // --- ВНУТРІШНІ КЛАСИ ---
    private static class SpellBuilderState {
        GeneratedSpell.EffectType selectedType = null;
        String spellName = "Невідоме Заклинання";
        boolean canAfford = false;

        int damageLvl = 0;
        int radiusLvl = 0;
        int cooldownLvl = 0;
        int costLvl = 0;
        int healLvl = 0;
    }

    private static class SpellStats {
        double damage = 0;
        double radius = 0;
        double heal = 0;
        int cooldown = 0;
        int spirituality = 0;
    }

    // --- КОНСТАНТИ GUI ---
    private static final int GUI_ROWS = 6;
    private static final int SLOT_TYPE_PROJECTILE = 10;
    private static final int SLOT_TYPE_AOE = 11;
    private static final int SLOT_TYPE_TELEPORT = 12;
    private static final int SLOT_TYPE_SELF = 13;
    private static final int SLOT_TYPE_BUFF = 14;

    private static final int SLOT_MOD_DAMAGE = 28;
    private static final int SLOT_MOD_RADIUS = 29;
    private static final int SLOT_MOD_COOLDOWN = 30;
    private static final int SLOT_MOD_COST = 31;
    private static final int SLOT_MOD_HEAL = 32;

    private static final int SLOT_INFO_PREVIEW = 16;
    private static final int SLOT_CREATE = 43;
    private static final int SLOT_NAME_RANDOMIZER = 49;

    @Override
    public String getName() {
        return "Створення заклинань";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Відкриває конструктор заклинань.";
    }

    @Override
    public int getSpiritualityCost() {
        return 300;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 120;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        SpellBuilderState state = new SpellBuilderState();
        state.spellName = "Магічний Експеримент"; // Стартова назва
        openBuilderGui(context, state);
        return AbilityResult.deferred();
    }

    private void openBuilderGui(IAbilityContext context, SpellBuilderState state) {
        Player player = context.getCaster();
        Gui gui = Gui.gui()
                .title(Component.text("Конструктор Заклинань"))
                .rows(GUI_ROWS)
                .disableAllInteractions()
                .create();

        updateGuiContent(gui, state, player, context);
        gui.open(player);
    }

    private void updateGuiContent(Gui gui, SpellBuilderState state, Player player, IAbilityContext context) {
        // 1. ФОН
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        gui.getFiller().fill(new GuiItem(filler));

        // 2. ТИПИ (Оновлено: Стріла -> Промінь)
        renderTypeButton(gui, state, player, context, SLOT_TYPE_PROJECTILE, GeneratedSpell.EffectType.PROJECTILE,
                Material.AMETHYST_SHARD, "Магічний Промінь", "Випускає швидкий пучок енергії");

        renderTypeButton(gui, state, player, context, SLOT_TYPE_AOE, GeneratedSpell.EffectType.AOE,
                Material.TNT, "Вибух (AoE)", "Створює зону ураження навколо");

        renderTypeButton(gui, state, player, context, SLOT_TYPE_TELEPORT, GeneratedSpell.EffectType.TELEPORT,
                Material.ENDER_PEARL, "Телепорт", "Миттєве переміщення");

        renderTypeButton(gui, state, player, context, SLOT_TYPE_SELF, GeneratedSpell.EffectType.SELF,
                Material.GHAST_TEAR, "Лікування", "Відновлює здоров'я");

        renderTypeButton(gui, state, player, context, SLOT_TYPE_BUFF, GeneratedSpell.EffectType.BUFF,
                Material.BLAZE_POWDER, "Посилення", "Дає тимчасові ефекти");

        // 3. МОДИФІКАТОРИ
        if (state.selectedType != null) {
            renderModifierButton(gui, state, player, context, SLOT_MOD_DAMAGE, Material.DIAMOND, "Сила / Шкода", state.damageLvl, 5, "Збільшує пошкодження.", "Ціна: 2 Алмази");
            renderModifierButton(gui, state, player, context, SLOT_MOD_RADIUS, Material.LAPIS_LAZULI, "Радіус / Дальність", state.radiusLvl, 5, "Збільшує зону ураження.", "Ціна: 4 Лазуриту");
            renderModifierButton(gui, state, player, context, SLOT_MOD_COOLDOWN, Material.REDSTONE, "Зменшення Кулдауну", state.cooldownLvl, 5, "Дозволяє чаклувати частіше.", "Ціна: 4 Редстоуну");
            renderModifierButton(gui, state, player, context, SLOT_MOD_COST, Material.GLOWSTONE_DUST, "Оптимізація Витрат", state.costLvl, 3, "Зменшує витрати духовності.", "Ціна: 4 Пилу");

            if (state.selectedType == GeneratedSpell.EffectType.SELF) {
                renderModifierButton(gui, state, player, context, SLOT_MOD_HEAL, Material.GOLDEN_APPLE, "Сила зцілення", state.healLvl, 3, "Більше сердець.", "Ціна: 1 Золоте яблуко");
            }
        }

        // 4. ПРЕВ'Ю
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta bookMeta = book.getItemMeta();
        bookMeta.setDisplayName(ChatColor.GOLD + ">> Характеристики <<");
        bookMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        List<String> lore = new ArrayList<>();
        if (state.selectedType == null) {
            lore.add(ChatColor.RED + "Оберіть тип заклинання зверху!");
        } else {
            SpellStats stats = calculateStats(state);
            lore.add(ChatColor.GRAY + "Тип: " + ChatColor.AQUA + getUkrainianTypeName(state.selectedType));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Статистика:");
            if (stats.damage > 0) lore.add(ChatColor.WHITE + " Шкода: " + ChatColor.RED + String.format("%.1f", stats.damage));
            if (stats.radius > 0) lore.add(ChatColor.WHITE + " Радіус: " + ChatColor.BLUE + String.format("%.1f", stats.radius));
            if (stats.heal > 0) lore.add(ChatColor.WHITE + " Лікування: " + ChatColor.GREEN + String.format("%.1f", stats.heal));
            lore.add(ChatColor.WHITE + " Кулдаун: " + ChatColor.GRAY + stats.cooldown + "с");
            lore.add(ChatColor.WHITE + " Духовність: " + ChatColor.LIGHT_PURPLE + stats.spirituality);
            lore.add("");
            lore.add(ChatColor.GOLD + "Необхідні ресурси:");
            Map<Material, Integer> cost = calculateResourceCost(state);
            boolean canAfford = true;
            for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
                int playerHas = getAmountInInventory(player, entry.getKey());
                String color = (playerHas >= entry.getValue()) ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
                lore.add(color + "- " + formatMaterialName(entry.getKey()) + ": " + playerHas + "/" + entry.getValue());
                if (playerHas < entry.getValue()) canAfford = false;
            }
            state.canAfford = canAfford;
        }
        bookMeta.setLore(lore);
        book.setItemMeta(bookMeta);
        gui.setItem(SLOT_INFO_PREVIEW, new GuiItem(book));

        // 5. НАЗВА (Генератор)
        ItemStack nameTag = new ItemStack(Material.NAME_TAG);
        ItemMeta nameMeta = nameTag.getItemMeta();
        nameMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Назва: " + ChatColor.WHITE + state.spellName);
        nameMeta.setLore(Arrays.asList(ChatColor.GRAY + "Натисніть для генерації", ChatColor.GRAY + "нової назви (Укр)."));
        nameTag.setItemMeta(nameMeta);
        gui.setItem(SLOT_NAME_RANDOMIZER, new GuiItem(nameTag, event -> {
            state.spellName = generateRandomName(state.selectedType);
            updateGuiContent(gui, state, player, context);
        }));

        // 6. КНОПКА СТВОРЕННЯ
        ItemStack createBtn;
        if (state.selectedType != null && state.canAfford) {
            createBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta cm = createBtn.getItemMeta();
            cm.setDisplayName(ChatColor.GREEN + "✔ СТВОРИТИ");
            createBtn.setItemMeta(cm);
        } else {
            createBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta cm = createBtn.getItemMeta();
            cm.setDisplayName(ChatColor.RED + "❌ Неможливо створити");
            createBtn.setItemMeta(cm);
        }

        gui.setItem(SLOT_CREATE, new GuiItem(createBtn, event -> {
            if (state.selectedType != null && state.canAfford) {
                handleCreation(context, state, player, gui);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);
            }
        }));

        gui.update();
    }

    private void renderTypeButton(Gui gui, SpellBuilderState state, Player player, IAbilityContext context,
                                  int slot, GeneratedSpell.EffectType type, Material icon, String name, String desc) {
        boolean isSelected = state.selectedType == type;
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((isSelected ? ChatColor.GREEN + "▶ " : ChatColor.YELLOW) + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + desc);
        lore.add("");
        lore.add(isSelected ? ChatColor.GREEN + "✔ ВИБРАНО" : ChatColor.GRAY + "Натисніть для вибору");
        meta.setLore(lore);
        if (isSelected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);

        gui.setItem(slot, new GuiItem(item, event -> {
            state.selectedType = type;
            state.spellName = generateRandomName(type);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            updateGuiContent(gui, state, player, context);
        }));
    }

    private void renderModifierButton(Gui gui, SpellBuilderState state, Player player, IAbilityContext context,
                                      int slot, Material icon, String name, int currentLvl, int maxLvl, String desc, String costDesc) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + desc);
        lore.add("");
        lore.add(ChatColor.WHITE + "Рівень: " + ChatColor.GOLD + currentLvl + "/" + maxLvl);
        lore.add(ChatColor.GRAY + costDesc);
        lore.add("");
        lore.add(ChatColor.YELLOW + "ЛКМ: " + ChatColor.GREEN + "+1");
        lore.add(ChatColor.YELLOW + "ПКМ: " + ChatColor.RED + "-1");
        meta.setLore(lore);
        item.setItemMeta(meta);

        gui.setItem(slot, new GuiItem(item, event -> {
            if (event.isLeftClick()) {
                if (currentLvl < maxLvl) {
                    incrementState(state, icon);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                }
            } else if (event.isRightClick()) {
                if (currentLvl > 0) {
                    decrementState(state, icon);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                }
            }
            updateGuiContent(gui, state, player, context);
        }));
    }

    private void incrementState(SpellBuilderState state, Material type) {
        if (type == Material.DIAMOND) state.damageLvl++;
        else if (type == Material.LAPIS_LAZULI) state.radiusLvl++;
        else if (type == Material.REDSTONE) state.cooldownLvl++;
        else if (type == Material.GLOWSTONE_DUST) state.costLvl++;
        else if (type == Material.GOLDEN_APPLE) state.healLvl++;
    }

    private void decrementState(SpellBuilderState state, Material type) {
        if (type == Material.DIAMOND) state.damageLvl--;
        else if (type == Material.LAPIS_LAZULI) state.radiusLvl--;
        else if (type == Material.REDSTONE) state.cooldownLvl--;
        else if (type == Material.GLOWSTONE_DUST) state.costLvl--;
        else if (type == Material.GOLDEN_APPLE) state.healLvl--;
    }

    private void handleCreation(IAbilityContext context, SpellBuilderState state, Player player, Gui gui) {
        Map<Material, Integer> cost = calculateResourceCost(state);
        for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
            removeItems(player, entry.getKey(), entry.getValue());
        }

        SpellStats stats = calculateStats(state);

        // Генеруємо детальний опис (Lore) українською
        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append(ChatColor.GRAY).append("Автор: ").append(ChatColor.WHITE).append(player.getName()).append("\n");
        if (stats.damage > 0) descBuilder.append(ChatColor.GRAY).append("Шкода: ").append(ChatColor.RED).append(stats.damage).append("\n");
        if (stats.radius > 0) descBuilder.append(ChatColor.GRAY).append("Радіус: ").append(ChatColor.BLUE).append(stats.radius).append("м\n");
        if (stats.heal > 0) descBuilder.append(ChatColor.GRAY).append("Зцілення: ").append(ChatColor.GREEN).append(stats.heal).append("\n");
        descBuilder.append(ChatColor.GRAY).append("Кулдаун: ").append(ChatColor.WHITE).append(stats.cooldown).append("с");
        // Духовність зазвичай показується окремо системою, але можна додати:
        // descBuilder.append("\n").append(ChatColor.LIGHT_PURPLE).append("Дух: ").append(stats.spirituality);

        GeneratedSpell newSpell = new GeneratedSpell(
                AbilityIdentity.of(UUID.randomUUID().toString()),
                state.spellName,
                descBuilder.toString(), // Передаємо детальний опис
                stats.spirituality,
                stats.cooldown,
                state.selectedType,
                getParticleForType(state.selectedType),
                stats.damage,
                stats.radius,
                stats.heal,
                200,
                null, 0
        );

        Beyonder beyonder = context.getCasterBeyonder();
        boolean added = beyonder.addOffPathwayAbility(newSpell);

        if (added) {
            player.sendMessage(ChatColor.GREEN + "Здібність '" + state.spellName + "' створено!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            gui.close(player);
        } else {
            player.sendMessage(ChatColor.RED + "Помилка: Немає місця для нової здібності.");
        }
    }

    private SpellStats calculateStats(SpellBuilderState state) {
        SpellStats stats = new SpellStats();

        if (state.selectedType == GeneratedSpell.EffectType.PROJECTILE) {
            stats.damage = 5; stats.cooldown = 3; stats.spirituality = 25; // Промінь швидший і сильніший
        } else if (state.selectedType == GeneratedSpell.EffectType.AOE) {
            stats.damage = 6; stats.radius = 4; stats.cooldown = 15; stats.spirituality = 50;
        } else if (state.selectedType == GeneratedSpell.EffectType.TELEPORT) {
            stats.radius = 12; stats.cooldown = 8; stats.spirituality = 30;
        } else if (state.selectedType == GeneratedSpell.EffectType.SELF) {
            stats.heal = 4; stats.cooldown = 20; stats.spirituality = 40;
        } else if (state.selectedType == GeneratedSpell.EffectType.BUFF) {
            stats.cooldown = 40; stats.spirituality = 60;
        }

        stats.damage += state.damageLvl * 2.5; // Трохи більше шкоди від алмазів
        stats.radius += state.radiusLvl * 1.5;
        stats.heal += state.healLvl * 2.0;
        stats.cooldown = Math.max(1, stats.cooldown - (state.cooldownLvl * 1));

        int powerCostAdded = (state.damageLvl * 5) + (state.radiusLvl * 3) + (state.healLvl * 8);
        int reduction = state.costLvl * 8;
        stats.spirituality = Math.max(5, (stats.spirituality + powerCostAdded) - reduction);

        return stats;
    }

    private Map<Material, Integer> calculateResourceCost(SpellBuilderState state) {
        Map<Material, Integer> cost = new HashMap<>();

        Material coreMat = Material.AIR;
        if (state.selectedType == GeneratedSpell.EffectType.PROJECTILE) coreMat = Material.AMETHYST_SHARD; // Змінено
        else if (state.selectedType == GeneratedSpell.EffectType.AOE) coreMat = Material.TNT;
        else if (state.selectedType == GeneratedSpell.EffectType.TELEPORT) coreMat = Material.ENDER_PEARL;
        else if (state.selectedType == GeneratedSpell.EffectType.SELF) coreMat = Material.GHAST_TEAR;
        else if (state.selectedType == GeneratedSpell.EffectType.BUFF) coreMat = Material.BLAZE_POWDER;

        cost.put(coreMat, 1);

        if (state.damageLvl > 0) cost.put(Material.DIAMOND, state.damageLvl * 2);
        if (state.radiusLvl > 0) cost.put(Material.LAPIS_LAZULI, state.radiusLvl * 4);
        if (state.cooldownLvl > 0) cost.put(Material.REDSTONE, state.cooldownLvl * 4);
        if (state.costLvl > 0) cost.put(Material.GLOWSTONE_DUST, state.costLvl * 4);
        if (state.healLvl > 0) cost.put(Material.GOLDEN_APPLE, state.healLvl * 1);

        return cost;
    }

    private int getAmountInInventory(Player player, Material material) {
        int amount = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == material) amount += is.getAmount();
        }
        return amount;
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == material) {
                if (is.getAmount() > remaining) {
                    is.setAmount(is.getAmount() - remaining);
                    remaining = 0;
                } else {
                    remaining -= is.getAmount();
                    is.setAmount(0);
                }
            }
            if (remaining <= 0) break;
        }
    }

    private Particle getParticleForType(GeneratedSpell.EffectType type) {
        if (type == GeneratedSpell.EffectType.PROJECTILE) return Particle.FIREWORK; // Промінь
        if (type == GeneratedSpell.EffectType.AOE) return Particle.EXPLOSION;
        if (type == GeneratedSpell.EffectType.TELEPORT) return Particle.PORTAL;
        if (type == GeneratedSpell.EffectType.SELF) return Particle.HEART;
        return Particle.SOUL;
    }

    private String formatMaterialName(Material mat) {
        return mat.name().toLowerCase().replace("_", " ");
    }

    private String getUkrainianTypeName(GeneratedSpell.EffectType type) {
        if (type == GeneratedSpell.EffectType.PROJECTILE) return "Промінь";
        if (type == GeneratedSpell.EffectType.AOE) return "Вибух";
        if (type == GeneratedSpell.EffectType.TELEPORT) return "Телепорт";
        if (type == GeneratedSpell.EffectType.SELF) return "Зцілення";
        return "Магія";
    }

    // --- ГЕНЕРАТОР НАЗВ (УКРАЇНСЬКА) ---
    private String generateRandomName(GeneratedSpell.EffectType type) {
        // Прикметники (чоловічий рід переважно, для узгодження з "Промінь", "Вибух", "Удар")
        String[] adjectives = {
                "Темний", "Святий", "Древній", "Швидкий", "Кривавий",
                "Пустий", "Зоряний", "Вогняний", "Крижаний", "Містичний"
        };

        String[] nouns;

        if (type == GeneratedSpell.EffectType.PROJECTILE) {
            nouns = new String[]{"Промінь", "Спис", "Постріл", "Спалах", "Удар", "Потік"};
        } else if (type == GeneratedSpell.EffectType.AOE) {
            nouns = new String[]{"Вибух", "Шторм", "Гнів", "Хаос", "Розлом", "Катаклізм"};
        } else if (type == GeneratedSpell.EffectType.TELEPORT) {
            nouns = new String[]{"Стрибок", "Крок", "Ривок", "Зсув", "Перехід"};
        } else if (type == GeneratedSpell.EffectType.SELF) {
            nouns = new String[]{"Дар", "Захист", "Оберіг", "Подих", "Ритм"};
        } else {
            nouns = new String[]{"Ефект", "Ритуал", "Знак"};
        }

        Random rand = new Random();
        return adjectives[rand.nextInt(adjectives.length)] + " " + nouns[rand.nextInt(nouns.length)];
    }
}