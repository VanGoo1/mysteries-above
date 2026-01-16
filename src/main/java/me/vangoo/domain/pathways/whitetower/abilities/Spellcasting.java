
package me.vangoo.domain.pathways.whitetower.abilities;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.pathways.whitetower.abilities.custom.GeneratedSpell;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.mappers.GeneratedSpellSerializer;
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
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class Spellcasting extends ActiveAbility {

    // --- ВНУТРІШНІ КЛАСИ ---
    private static class SpellBuilderState {
        GeneratedSpell.EffectType selectedType = null;
        String spellName = "Невідоме Заклинання";
        boolean canAfford = false;

        // Рівні прокачки (0-5)
        int param1Lvl = 0; // Шкода / Ефект / Дальність
        int param2Lvl = 0; // Радіус / Тривалість
        int param3Lvl = 0; // Кулдаун
        int param4Lvl = 0; // Вартість (Духовність)
        int param5Lvl = 0; // Хіл / Ампліфаєр (Рівень ефекту)

        // Для бафів: індекс вибраного ефекту (0=Швидкість, 1=Сила...)
        int selectedBuffIndex = 0;
    }

    private static class SpellStats {
        double damage = 0;
        double radius = 0;
        double heal = 0;
        int cooldown = 0;
        int spirituality = 0;

        // Для бафів
        PotionEffectType potionType = null;
        int potionDuration = 0;
        int potionAmplifier = 0;
    }

    // --- КОНСТАНТИ GUI ---
    private static final int GUI_ROWS = 6;

    // Слоти вибору типу
    private static final int SLOT_TYPE_PROJECTILE = 10;
    private static final int SLOT_TYPE_AOE = 11;
    private static final int SLOT_TYPE_TELEPORT = 12;
    private static final int SLOT_TYPE_SELF = 13;
    private static final int SLOT_TYPE_BUFF = 14;

    // Слоти модифікаторів (динамічні)
    private static final int SLOT_MOD_1 = 28; // Шкода / Тип ефекту
    private static final int SLOT_MOD_2 = 29; // Радіус / Тривалість
    private static final int SLOT_MOD_3 = 30; // Кулдаун (завжди тут)
    private static final int SLOT_MOD_4 = 31; // Вартість (завжди тут)
    private static final int SLOT_MOD_5 = 32; // Хіл / Сила ефекту

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
        state.spellName = "Магічний Експеримент";
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

        // 2. ВИБІР ТИПУ
        renderTypeButton(gui, state, player, context, SLOT_TYPE_PROJECTILE, GeneratedSpell.EffectType.PROJECTILE,
                Material.AMETHYST_SHARD, "Бойовий Промінь", "Швидкий постріл в одну ціль.");
        renderTypeButton(gui, state, player, context, SLOT_TYPE_AOE, GeneratedSpell.EffectType.AOE,
                Material.TNT, "Вибух (AoE)", "Вражає область навколо.");
        renderTypeButton(gui, state, player, context, SLOT_TYPE_TELEPORT, GeneratedSpell.EffectType.TELEPORT,
                Material.ENDER_PEARL, "Телепортація", "Переміщення в просторі.");
        renderTypeButton(gui, state, player, context, SLOT_TYPE_SELF, GeneratedSpell.EffectType.SELF,
                Material.GHAST_TEAR, "Лікування", "Відновлення здоров'я.");
        renderTypeButton(gui, state, player, context, SLOT_TYPE_BUFF, GeneratedSpell.EffectType.BUFF,
                Material.BLAZE_POWDER, "Фізичне Посилення", "Накладає ефекти (Швидкість, Сила...).");

        // 3. МОДИФІКАТОРИ (ДИНАМІЧНІ)
        if (state.selectedType != null) {
            renderDynamicModifiers(gui, state, player, context);
        }

        // 4. ПРЕВ'Ю І СТВОРЕННЯ
        renderPreviewAndCreate(gui, state, player, context);

        gui.update();
    }

    private void renderDynamicModifiers(Gui gui, SpellBuilderState state, Player player, IAbilityContext context) {
        // Завжди показуємо Кулдаун і Оптимізацію витрат (Слоти 30, 31)
        renderModifierButton(gui, state, player, context, SLOT_MOD_3, Material.REDSTONE,
                "Зменшення Кулдауну", state.param3Lvl, 5, "Частіше використання", "4 Редстоуну", 3);

        renderModifierButton(gui, state, player, context, SLOT_MOD_4, Material.GLOWSTONE_DUST,
                "Оптимізація Витрат", state.param4Lvl, 3, "Менше духовності", "4 Пилу", 4);

        // Специфічні кнопки залежно від типу
        switch (state.selectedType) {
            case PROJECTILE:
            case AOE:
                // Слот 28: Шкода
                renderModifierButton(gui, state, player, context, SLOT_MOD_1, Material.DIAMOND,
                        "Сила Удару", state.param1Lvl, 5, "Збільшує шкоду", "2 Алмази", 1);

                // Слот 29: Радіус (тільки для AOE має сенс радіус вибуху, для снаряда - швидкість/дальність?)
                // Давайте зробимо для обох "Радіус/Дальність"
                renderModifierButton(gui, state, player, context, SLOT_MOD_2, Material.LAPIS_LAZULI,
                        state.selectedType == GeneratedSpell.EffectType.AOE ? "Радіус Вибуху" : "Дальність Польоту",
                        state.param2Lvl, 5, "Збільшує зону", "4 Лазуриту", 2);

                // Слот 32: Пусто (Хіл недоступний)
                renderLockedSlot(gui, SLOT_MOD_5, "Лікування недоступне для бойових чар");
                break;

            case TELEPORT:
                // Слот 28: Шкода (Недоступно)
                renderLockedSlot(gui, SLOT_MOD_1, "Шкода недоступна для телепорту");

                // Слот 29: Дальність стрибка
                renderModifierButton(gui, state, player, context, SLOT_MOD_2, Material.ENDER_EYE,
                        "Дальність Стрибка", state.param2Lvl, 5, "Як далеко телепортує", "2 Перлини", 2);

                // Слот 32: Хіл (Недоступно)
                renderLockedSlot(gui, SLOT_MOD_5, "Лікування недоступне");
                break;

            case SELF: // Лікування
                // Слот 28: Шкода (Недоступно)
                renderLockedSlot(gui, SLOT_MOD_1, "Шкода недоступна для лікування");

                // Слот 29: Радіус (Недоступно, це Self)
                renderLockedSlot(gui, SLOT_MOD_2, "Це заклинання діє лише на вас");

                // Слот 32: Сила Зцілення
                renderModifierButton(gui, state, player, context, SLOT_MOD_5, Material.GOLDEN_APPLE,
                        "Сила Зцілення", state.param5Lvl, 5, "Кількість сердець", "1 Золоте яблуко", 5);
                break;

            case BUFF:
                // Слот 28: ВИБІР ЕФЕКТУ (Спеціальна кнопка перемикач)
                renderBuffSelector(gui, state, player, context);

                // Слот 29: Тривалість
                renderModifierButton(gui, state, player, context, SLOT_MOD_2, Material.CLOCK,
                        "Тривалість Дії", state.param2Lvl, 5, "Час дії ефекту", "4 Редстоуну", 2);

                // Слот 32: Рівень ефекту (Amplifier)
                renderModifierButton(gui, state, player, context, SLOT_MOD_5, Material.NETHER_WART,
                        "Потужність Ефекту", state.param5Lvl, 2, "Рівень (I -> II)", "Рідкісні ресурси", 5);
                break;
        }
    }

    // Спеціальна кнопка для перемикання типу баффу
    private void renderBuffSelector(Gui gui, SpellBuilderState state, Player player, IAbilityContext context) {
        String[] effects = {"Швидкість", "Сила", "Опір", "Регенерація", "Вогнестійкість"};
        Material[] icons = {Material.SUGAR, Material.BLAZE_POWDER, Material.IRON_CHESTPLATE, Material.GHAST_TEAR, Material.MAGMA_CREAM};
        String[] costs = {"2 Цукру", "2 Блейз пудри", "2 Заліза", "1 Сльоза Гаста", "2 Магми"};

        int idx = state.selectedBuffIndex;

        ItemStack item = new ItemStack(icons[idx]);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Ефект: " + ChatColor.WHITE + effects[idx]);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Натисніть для зміни ефекту.");
        lore.add("");
        lore.add(ChatColor.GOLD + "Ціна за активацію: " + ChatColor.WHITE + costs[idx]);
        meta.setLore(lore);
        item.setItemMeta(meta);

        gui.setItem(SLOT_MOD_1, new GuiItem(item, event -> {
            state.selectedBuffIndex = (state.selectedBuffIndex + 1) % effects.length;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
            updateGuiContent(gui, state, player, context);
        }));
    }

    private void renderLockedSlot(Gui gui, int slot, String reason) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Недоступно");
        meta.setLore(Collections.singletonList(ChatColor.GRAY + reason));
        item.setItemMeta(meta);
        gui.setItem(slot, new GuiItem(item)); // Без дії
    }

    private void renderModifierButton(Gui gui, SpellBuilderState state, Player player, IAbilityContext context,
                                      int slot, Material icon, String name, int currentLvl, int maxLvl,
                                      String desc, String costDesc, int paramId) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + desc);
        lore.add("");
        lore.add(ChatColor.WHITE + "Рівень: " + ChatColor.GOLD + currentLvl + "/" + maxLvl);
        lore.add(ChatColor.GRAY + "Вартість покращення: " + costDesc);
        lore.add("");
        lore.add(ChatColor.YELLOW + "ЛКМ: " + ChatColor.GREEN + "+1");
        lore.add(ChatColor.YELLOW + "ПКМ: " + ChatColor.RED + "-1");
        meta.setLore(lore);
        item.setItemMeta(meta);

        gui.setItem(slot, new GuiItem(item, event -> {
            if (event.isLeftClick()) {
                if (currentLvl < maxLvl) {
                    changeParam(state, paramId, 1);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                } else player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            } else if (event.isRightClick()) {
                if (currentLvl > 0) {
                    changeParam(state, paramId, -1);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                }
            }
            updateGuiContent(gui, state, player, context);
        }));
    }

    private void changeParam(SpellBuilderState state, int paramId, int delta) {
        switch (paramId) {
            case 1: state.param1Lvl += delta; break;
            case 2: state.param2Lvl += delta; break;
            case 3: state.param3Lvl += delta; break;
            case 4: state.param4Lvl += delta; break;
            case 5: state.param5Lvl += delta; break;
        }
    }

    // 4. ПРЕВ'Ю І СТВОРЕННЯ
    private void renderPreviewAndCreate(Gui gui, SpellBuilderState state, Player player, IAbilityContext context) {
        // Книга
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta bookMeta = book.getItemMeta();
        bookMeta.setDisplayName(ChatColor.GOLD + ">> Характеристики <<");
        bookMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<String> lore = new ArrayList<>();
        if (state.selectedType == null) {
            lore.add(ChatColor.RED + "Оберіть тип заклинання!");
        } else {
            SpellStats stats = calculateStats(state);
            lore.add(ChatColor.GRAY + "Тип: " + ChatColor.AQUA + getUkrainianTypeName(state.selectedType));
            lore.add("");

            // Відображаємо тільки релевантну статистику
            if (state.selectedType == GeneratedSpell.EffectType.BUFF) {
                lore.add(ChatColor.YELLOW + "Ефект: " + ChatColor.LIGHT_PURPLE + getPotionNameUkr(stats.potionType));
                lore.add(ChatColor.YELLOW + "Потужність: " + ChatColor.WHITE + (stats.potionAmplifier + 1));
                lore.add(ChatColor.YELLOW + "Тривалість: " + ChatColor.WHITE + (stats.potionDuration / 20) + "с");
            } else {
                if (stats.damage > 0) lore.add(ChatColor.WHITE + " Шкода: " + ChatColor.RED + String.format("%.1f", stats.damage));
                if (stats.radius > 0) lore.add(ChatColor.WHITE + " Радіус: " + ChatColor.BLUE + String.format("%.1f", stats.radius));
                if (stats.heal > 0) lore.add(ChatColor.WHITE + " Лікування: " + ChatColor.GREEN + String.format("%.1f", stats.heal));
            }

            lore.add(ChatColor.WHITE + " Кулдаун: " + ChatColor.GRAY + stats.cooldown + "с");
            lore.add(ChatColor.WHITE + " Духовність: " + ChatColor.LIGHT_PURPLE + stats.spirituality);
            lore.add("");

            // Ресурси
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

        // Назва
        ItemStack nameTag = new ItemStack(Material.NAME_TAG);
        ItemMeta nameMeta = nameTag.getItemMeta();
        nameMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Назва: " + ChatColor.WHITE + state.spellName);
        nameMeta.setLore(Arrays.asList(ChatColor.GRAY + "Клік для випадкової назви"));
        nameTag.setItemMeta(nameMeta);
        gui.setItem(SLOT_NAME_RANDOMIZER, new GuiItem(nameTag, event -> {
            state.spellName = generateRandomName(state.selectedType);
            updateGuiContent(gui, state, player, context);
        }));

        // Кнопка створення
        ItemStack createBtn;
        if (state.selectedType != null && state.canAfford) {
            createBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta cm = createBtn.getItemMeta();
            cm.setDisplayName(ChatColor.GREEN + "✔ СТВОРИТИ");
            createBtn.setItemMeta(cm);
        } else {
            createBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta cm = createBtn.getItemMeta();
            cm.setDisplayName(ChatColor.RED + "❌ Нестача ресурсів");
            createBtn.setItemMeta(cm);
        }
        gui.setItem(SLOT_CREATE, new GuiItem(createBtn, event -> {
            if (state.selectedType != null && state.canAfford) {
                handleCreation(context, state, player, gui);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);
            }
        }));
    }

    private void handleCreation(IAbilityContext context, SpellBuilderState state, Player player, Gui gui) {
        // Отримуємо об'єкт Beyonder
        Beyonder casterBeyonder = context.getCasterBeyonder();

        // --- ВИПРАВЛЕННЯ: Споживання ресурсів ---
        // Оскільки метод execute() повернув deferred (через меню),
        // система не зняла ману і не поставила кулдаун автоматично.
        // Ми робимо це тут вручну, перед тим як створити спел.
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            player.sendMessage(ChatColor.RED + "Недостатньо духовності для завершення ритуалу!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return; // Перериваємо, якщо не вистачило мани або кулдаун не пройшов
        }

        // Сповіщаємо систему про подію використання (важливо для логів/статистики)
        context.publishAbilityUsedEvent(this);

        // --- Споживання фізичних предметів (Inventory) ---
        Map<Material, Integer> cost = calculateResourceCost(state);
        for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
            removeItems(player, entry.getKey(), entry.getValue());
        }

        // --- Розрахунок характеристик ---
        SpellStats stats = calculateStats(state);

        // --- Формування опису ---
        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append(ChatColor.GRAY).append("Автор: ").append(ChatColor.WHITE).append(player.getName()).append("\n");

        if (state.selectedType == GeneratedSpell.EffectType.BUFF) {
            descBuilder.append(ChatColor.GRAY).append("Ефект: ").append(ChatColor.GOLD).append(getPotionNameUkr(stats.potionType)).append("\n");
            descBuilder.append(ChatColor.GRAY).append("Сила: ").append(ChatColor.WHITE).append(stats.potionAmplifier + 1).append("\n");
            descBuilder.append(ChatColor.GRAY).append("Час: ").append(ChatColor.WHITE).append(stats.potionDuration / 20).append("с");
        } else {
            if (stats.damage > 0) descBuilder.append(ChatColor.GRAY).append("Шкода: ").append(ChatColor.RED).append(stats.damage).append("\n");
            if (stats.radius > 0) descBuilder.append(ChatColor.GRAY).append("Радіус: ").append(ChatColor.BLUE).append(stats.radius).append("м\n");
            if (stats.heal > 0) descBuilder.append(ChatColor.GRAY).append("Зцілення: ").append(ChatColor.GREEN).append(stats.heal).append("\n");
        }

        // --- Створення об'єкта здібності ---
        GeneratedSpell newSpell = GeneratedSpellSerializer.create(
                state.spellName,
                descBuilder.toString(),
                state.selectedType,
                getParticleForType(state.selectedType),
                stats.damage,
                stats.radius,
                stats.heal,
                stats.potionDuration,
                stats.potionType,
                stats.potionAmplifier,
                stats.spirituality,
                stats.cooldown
        );

        // --- Додавання гравцю ---
        if (casterBeyonder.addOffPathwayAbility(newSpell)) {
            player.sendMessage(ChatColor.GREEN + "Здібність '" + state.spellName + "' створено!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            gui.close(player);
        } else {
            player.sendMessage(ChatColor.RED + "Помилка: Немає місця для нової здібності (макс. ліміт).");
            // Тут теоретично можна повернути ресурси, якщо хочеш,
            // але зазвичай перевірку на вільне місце роблять до відкриття GUI.
        }
    }

    // --- ЛОГІКА РОЗРАХУНКУ СТАТИСТИКИ ---
    private SpellStats calculateStats(SpellBuilderState state) {
        SpellStats stats = new SpellStats();

        // Базові значення
        stats.cooldown = 10 - state.param3Lvl; // Зменшення кулдауну (Redstone)
        if (stats.cooldown < 1) stats.cooldown = 1;

        // Базова вартість магії
        int baseCost = 20;

        switch (state.selectedType) {
            case PROJECTILE:
                stats.damage = 5 + (state.param1Lvl * 2.5); // Param1 = Damage
                stats.radius = 0; // Для прямого пострілу радіус не потрібен (або це швидкість)
                stats.spirituality = 25 + (state.param1Lvl * 5);
                break;
            case AOE:
                stats.damage = 4 + (state.param1Lvl * 2.0); // Param1 = Damage
                stats.radius = 3 + (state.param2Lvl * 1.5); // Param2 = Radius
                stats.spirituality = 40 + (state.param1Lvl * 5) + (state.param2Lvl * 5);
                stats.cooldown += 5; // AoE довше
                break;
            case TELEPORT:
                stats.radius = 10 + (state.param2Lvl * 5); // Param2 = Range
                stats.spirituality = 30 + (state.param2Lvl * 3);
                break;
            case SELF:
                stats.heal = 4 + (state.param5Lvl * 2.0); // Param5 = Heal Amount
                stats.spirituality = 40 + (state.param5Lvl * 8);
                stats.cooldown += 10;
                break;
            case BUFF:
                stats.potionDuration = 100 + (state.param2Lvl * 40); // 5 сек + бонуси (в тіках)
                stats.potionAmplifier = state.param5Lvl; // 0 = Lvl 1, 1 = Lvl 2

                // Визначаємо тип
                switch (state.selectedBuffIndex) {
                    case 0: stats.potionType = PotionEffectType.SPEED; break;
                    case 1: stats.potionType = PotionEffectType.STRENGTH; break;
                    case 2: stats.potionType = PotionEffectType.RESISTANCE; break;
                    case 3: stats.potionType = PotionEffectType.REGENERATION; break;
                    case 4: stats.potionType = PotionEffectType.FIRE_RESISTANCE; break;
                }

                stats.spirituality = 50 + (state.param2Lvl * 5) + (state.param5Lvl * 20);
                stats.cooldown += 30;
                break;
        }

        // Зниження вартості (Param4)
        int reduction = state.param4Lvl * 5;
        stats.spirituality = Math.max(5, stats.spirituality - reduction);

        return stats;
    }

    private Map<Material, Integer> calculateResourceCost(SpellBuilderState state) {
        Map<Material, Integer> cost = new HashMap<>();

        // Базові предмети для типу
        if (state.selectedType == GeneratedSpell.EffectType.PROJECTILE) cost.put(Material.AMETHYST_SHARD, 1);
        else if (state.selectedType == GeneratedSpell.EffectType.AOE) cost.put(Material.TNT, 1);
        else if (state.selectedType == GeneratedSpell.EffectType.TELEPORT) cost.put(Material.ENDER_PEARL, 1);
        else if (state.selectedType == GeneratedSpell.EffectType.SELF) cost.put(Material.GHAST_TEAR, 1);

            // Специфіка для бафів (базова ціна за активацію типу)
        else if (state.selectedType == GeneratedSpell.EffectType.BUFF) {
            switch (state.selectedBuffIndex) {
                case 0: cost.put(Material.SUGAR, 2); break; // Speed
                case 1: cost.put(Material.BLAZE_POWDER, 2); break; // Strength
                case 2: cost.put(Material.IRON_INGOT, 2); break; // Resistance
                case 3: cost.put(Material.GHAST_TEAR, 1); break; // Regen
                case 4: cost.put(Material.MAGMA_CREAM, 2); break; // Fire Res
            }
        }

        // Ресурси за покращення
        if (state.param3Lvl > 0) cost.put(Material.REDSTONE, state.param3Lvl * 4); // Кулдаун
        if (state.param4Lvl > 0) cost.put(Material.GLOWSTONE_DUST, state.param4Lvl * 4); // Вартість

        // Специфічні витрати
        switch (state.selectedType) {
            case PROJECTILE:
            case AOE:
                if (state.param1Lvl > 0) cost.put(Material.DIAMOND, state.param1Lvl * 2); // Шкода
                if (state.param2Lvl > 0) cost.put(Material.LAPIS_LAZULI, state.param2Lvl * 4); // Радіус
                break;
            case TELEPORT:
                if (state.param2Lvl > 0) cost.put(Material.ENDER_PEARL, state.param2Lvl * 2); // Дальність
                break;
            case SELF:
                if (state.param5Lvl > 0) cost.put(Material.GOLDEN_APPLE, state.param5Lvl * 1); // Хіл
                break;
            case BUFF:
                if (state.param2Lvl > 0) cost.put(Material.REDSTONE, state.param2Lvl * 4); // Тривалість
                // Для рівня ефекту дорогі ресурси
                if (state.param5Lvl > 0) {
                    if (state.selectedBuffIndex == 3) cost.put(Material.GHAST_TEAR, state.param5Lvl * 2);
                    else cost.put(Material.BLAZE_ROD, state.param5Lvl * 1);
                }
                break;
        }

        return cost;
    }

    // --- ДОПОМІЖНІ МЕТОДИ ---
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
            // Скидаємо параметри при зміні типу, щоб не було глюків
            state.param1Lvl = 0; state.param2Lvl = 0; state.param5Lvl = 0;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            updateGuiContent(gui, state, player, context);
        }));
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
        if (type == GeneratedSpell.EffectType.PROJECTILE) return Particle.FIREWORK;
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
        if (type == GeneratedSpell.EffectType.BUFF) return "Посилення";
        return "Магія";
    }

    private String getPotionNameUkr(PotionEffectType type) {
        if (type == null) return "Немає";
        if (type.equals(PotionEffectType.SPEED)) return "Швидкість";
        if (type.equals(PotionEffectType.STRENGTH)) return "Сила";
        if (type.equals(PotionEffectType.RESISTANCE)) return "Опір";
        if (type.equals(PotionEffectType.REGENERATION)) return "Регенерація";
        if (type.equals(PotionEffectType.FIRE_RESISTANCE)) return "Вогнестійкість";
        return type.getName();
    }

    private String generateRandomName(GeneratedSpell.EffectType type) {
        String[] adjectives = {"Темний", "Святий", "Древній", "Швидкий", "Кривавий", "Зоряний", "Вогняний", "Крижаний"};
        String[] nouns;
        if (type == GeneratedSpell.EffectType.PROJECTILE) nouns = new String[]{"Промінь", "Спис", "Постріл", "Удар"};
        else if (type == GeneratedSpell.EffectType.AOE) nouns = new String[]{"Вибух", "Шторм", "Гнів", "Хаос"};
        else if (type == GeneratedSpell.EffectType.TELEPORT) nouns = new String[]{"Стрибок", "Крок", "Ривок", "Перехід"};
        else if (type == GeneratedSpell.EffectType.SELF) nouns = new String[]{"Дар", "Подих", "Оберіг"};
        else nouns = new String[]{"Ритуал", "Знак", "Аура", "Благословення"};

        Random rand = new Random();
        return adjectives[rand.nextInt(adjectives.length)] + " " + nouns[rand.nextInt(nouns.length)];
    }
}
