
package me.vangoo.pathways.whitetower.abilities;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.pathways.whitetower.abilities.custom.GeneratedSpell;
import me.vangoo.domain.spells.SpellBlueprint;
import me.vangoo.domain.spells.SpellRecipe;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
        SpellRecipe.Shape selectedType = null;
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

        // Індекс бафа в GUI -> доменний enum (порядок збігається з renderBuffSelector)
        static final SpellRecipe.Buff[] BUFFS = {
                SpellRecipe.Buff.SPEED,
                SpellRecipe.Buff.STRENGTH,
                SpellRecipe.Buff.RESISTANCE,
                SpellRecipe.Buff.REGENERATION,
                SpellRecipe.Buff.FIRE_RESISTANCE
        };

        SpellBlueprint toBlueprint() {
            return new SpellBlueprint(
                    spellName, selectedType,
                    param1Lvl, param2Lvl, param5Lvl, param3Lvl, param4Lvl,
                    BUFFS[selectedBuffIndex]);
        }
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
        Player player = Bukkit.getPlayer(context.getCasterId());
        if (player == null) return AbilityResult.failure("Player not online.");
        openBuilderGui(context, state, player);
        return AbilityResult.deferred();
    }

    private void openBuilderGui(IAbilityContext context, SpellBuilderState state, Player player) {
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
        renderTypeButton(gui, state, player, context, SLOT_TYPE_PROJECTILE, SpellRecipe.Shape.PROJECTILE,
                Material.AMETHYST_SHARD, "Бойовий Промінь", "Швидкий постріл в одну ціль.");
        renderTypeButton(gui, state, player, context, SLOT_TYPE_AOE, SpellRecipe.Shape.AOE,
                Material.TNT, "Вибух (AoE)", "Вражає область навколо.");
        renderTypeButton(gui, state, player, context, SLOT_TYPE_TELEPORT, SpellRecipe.Shape.TELEPORT,
                Material.ENDER_PEARL, "Телепортація", "Переміщення в просторі.");
        renderTypeButton(gui, state, player, context, SLOT_TYPE_SELF, SpellRecipe.Shape.SELF,
                Material.GHAST_TEAR, "Лікування", "Відновлення здоров'я.");
        renderTypeButton(gui, state, player, context, SLOT_TYPE_BUFF, SpellRecipe.Shape.BUFF,
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
                        state.selectedType == SpellRecipe.Shape.AOE ? "Радіус Вибуху" : "Дальність Польоту",
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
            context.effects().playSoundForPlayer(player.getUniqueId(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
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
                    context.effects().playSoundForPlayer(player.getUniqueId(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                } else context.effects().playSoundForPlayer(player.getUniqueId(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            } else if (event.isRightClick()) {
                if (currentLvl > 0) {
                    changeParam(state, paramId, -1);
                    context.effects().playSoundForPlayer(player.getUniqueId(), Sound.UI_BUTTON_CLICK, 1f, 1f);
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
            SpellRecipe recipe = SpellRecipe.fromBlueprint(state.toBlueprint());
            lore.add(ChatColor.GRAY + "Тип: " + ChatColor.AQUA + getUkrainianTypeName(state.selectedType));
            lore.add("");

            // Відображаємо тільки релевантну статистику
            if (state.selectedType == SpellRecipe.Shape.BUFF) {
                lore.add(ChatColor.YELLOW + "Ефект: " + ChatColor.LIGHT_PURPLE + getBuffNameUkr(recipe.buff()));
                lore.add(ChatColor.YELLOW + "Потужність: " + ChatColor.WHITE + (recipe.buffAmplifier() + 1));
                lore.add(ChatColor.YELLOW + "Тривалість: " + ChatColor.WHITE + (recipe.durationTicks() / 20) + "с");
            } else {
                if (recipe.damage() > 0) lore.add(ChatColor.WHITE + " Шкода: " + ChatColor.RED + String.format("%.1f", recipe.damage()));
                if (recipe.radius() > 0) lore.add(ChatColor.WHITE + " Радіус: " + ChatColor.BLUE + String.format("%.1f", recipe.radius()));
                if (recipe.heal() > 0) lore.add(ChatColor.WHITE + " Лікування: " + ChatColor.GREEN + String.format("%.1f", recipe.heal()));
            }

            lore.add(ChatColor.WHITE + " Кулдаун: " + ChatColor.GRAY + recipe.cooldownSeconds() + "с");
            lore.add(ChatColor.WHITE + " Духовність: " + ChatColor.LIGHT_PURPLE + recipe.spiritualityCost());
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
                context.effects().playSoundForPlayer(player.getUniqueId(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);
            }
        }));
    }

    private void handleCreation(IAbilityContext context, SpellBuilderState state, Player player, Gui gui) {
        final UUID casterId = player.getUniqueId();
        // Отримуємо об'єкт Beyonder
        Beyonder casterBeyonder = context.beyonder().getBeyonder(casterId);

        // --- ВИПРАВЛЕННЯ: Споживання ресурсів ---
        // Оскільки метод execute() повернув deferred (через меню),
        // система не зняла ману і не поставила кулдаун автоматично.
        // Ми робимо це тут вручну, перед тим як створити спел.
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності для завершення ритуалу!");
            context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return; // Перериваємо, якщо не вистачило мани або кулдаун не пройшов
        }

        // Сповіщаємо систему про подію використання (важливо для логів/статистики)
        context.events().publishAbilityUsedEvent(this, casterBeyonder);

        // --- Споживання фізичних предметів (Inventory) ---
        Map<Material, Integer> cost = calculateResourceCost(state);
        for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
            removeItems(player, entry.getKey(), entry.getValue());
        }

        // --- Розрахунок характеристик (доменна математика) ---
        SpellRecipe recipe = SpellRecipe.fromBlueprint(state.toBlueprint());

        // --- Створення об'єкта здібності ---
        // Опис генерується з рецепта (SpellCodec.describe) у самій здібності.
        GeneratedSpell newSpell = new GeneratedSpell(recipe);

        // --- Додавання гравцю ---
        if (casterBeyonder.addOffPathwayAbility(newSpell)) {
            context.messaging().sendMessage(casterId, ChatColor.GREEN + "Здібність '" + state.spellName + "' створено!");
            context.effects().playSoundForPlayer(casterId, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            gui.close(player);
        } else {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Помилка: Немає місця для нової здібності (макс. ліміт).");
            // Тут теоретично можна повернути ресурси, якщо хочеш,
            // але зазвичай перевірку на вільне місце роблять до відкриття GUI.
        }
    }

    // Баланс заклинань (calculateStats) переїхав у доменний SpellRecipe.fromBlueprint —
    // тут лишилася тільки Bukkit-специфіка: ресурси-предмети та рендеринг GUI.

    private Map<Material, Integer> calculateResourceCost(SpellBuilderState state) {
        Map<Material, Integer> cost = new HashMap<>();

        // Базові предмети для типу
        if (state.selectedType == SpellRecipe.Shape.PROJECTILE) cost.put(Material.AMETHYST_SHARD, 1);
        else if (state.selectedType == SpellRecipe.Shape.AOE) cost.put(Material.TNT, 1);
        else if (state.selectedType == SpellRecipe.Shape.TELEPORT) cost.put(Material.ENDER_PEARL, 1);
        else if (state.selectedType == SpellRecipe.Shape.SELF) cost.put(Material.GHAST_TEAR, 1);

            // Специфіка для бафів (базова ціна за активацію типу)
        else if (state.selectedType == SpellRecipe.Shape.BUFF) {
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
                                  int slot, SpellRecipe.Shape type, Material icon, String name, String desc) {
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
            context.effects().playSoundForPlayer(player.getUniqueId(), Sound.UI_BUTTON_CLICK, 1f, 1f);
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

    private String formatMaterialName(Material mat) {
        return mat.name().toLowerCase().replace("_", " ");
    }

    private String getUkrainianTypeName(SpellRecipe.Shape type) {
        if (type == SpellRecipe.Shape.PROJECTILE) return "Промінь";
        if (type == SpellRecipe.Shape.AOE) return "Вибух";
        if (type == SpellRecipe.Shape.TELEPORT) return "Телепорт";
        if (type == SpellRecipe.Shape.SELF) return "Зцілення";
        if (type == SpellRecipe.Shape.BUFF) return "Посилення";
        return "Магія";
    }

    private String getBuffNameUkr(SpellRecipe.Buff buff) {
        if (buff == null) return "Немає";
        return switch (buff) {
            case SPEED -> "Швидкість";
            case STRENGTH -> "Сила";
            case RESISTANCE -> "Опір";
            case REGENERATION -> "Регенерація";
            case FIRE_RESISTANCE -> "Вогнестійкість";
        };
    }

    private String generateRandomName(SpellRecipe.Shape type) {
        String[] adjectives = {"Темний", "Святий", "Древній", "Швидкий", "Кривавий", "Зоряний", "Вогняний", "Крижаний"};
        String[] nouns;
        if (type == SpellRecipe.Shape.PROJECTILE) nouns = new String[]{"Промінь", "Спис", "Постріл", "Удар"};
        else if (type == SpellRecipe.Shape.AOE) nouns = new String[]{"Вибух", "Шторм", "Гнів", "Хаос"};
        else if (type == SpellRecipe.Shape.TELEPORT) nouns = new String[]{"Стрибок", "Крок", "Ривок", "Перехід"};
        else if (type == SpellRecipe.Shape.SELF) nouns = new String[]{"Дар", "Подих", "Оберіг"};
        else nouns = new String[]{"Ритуал", "Знак", "Аура", "Благословення"};

        Random rand = new Random();
        return adjectives[rand.nextInt(adjectives.length)] + " " + nouns[rand.nextInt(nouns.length)];
    }
}
