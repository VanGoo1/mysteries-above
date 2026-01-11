package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class Alteration extends ActiveAbility {
    // КОНСТАНТИ
    private static final int RANGE = 5;
    private static final int BASE_COST = 250;
    private static final int BASE_COOLDOWN_SECONDS = 90;
    private static final int MODIFICATION_DURATION_TICKS = 3600; // 3 хвилини
    private static final int EFFECT_DURATION_TICKS = 200; // 15 секунд
    private static final int MAX_MODIFICATIONS_PER_TARGET = 2;

    // СТАН
    private final Map<UUID, List<ModificationData>> activeModifications = new HashMap<>();

    @Override
    public String getName() {
        return "Видозміна";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Непомітно змінює реакцію цілі на певні дії. " +
                "Коли ціль виконує тригер, з нею стається щось незвичайне.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        double multiplier = SequenceScaler.calculateMultiplier(userSequence.level(), SequenceScaler.ScalingStrategy.WEAK);
        return Math.max(10, (int) (BASE_COOLDOWN_SECONDS / multiplier));
    }

    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.getTargetedEntity(RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(RANGE);

        if (targetOpt.isEmpty() || !(targetOpt.get() instanceof Player target)) {
            return AbilityResult.failure("Ціль має бути гравцем поруч");
        }

        openMainMenu(context, target);
        // Return deferred - spirituality will be consumed when modification is actually applied
        return AbilityResult.deferred();
    }

    // ==========================================
    // ГОЛОВНЕ МЕНЮ
    // ==========================================
    private void openMainMenu(IAbilityContext ctx, Player target) {
        UUID targetId = target.getUniqueId();
        List<ModificationData> mods = activeModifications.getOrDefault(targetId, new ArrayList<>());
        List<MainMenuOption> options = new ArrayList<>();

        options.add(MainMenuOption.VIEW_LIST);
        if (mods.size() < MAX_MODIFICATIONS_PER_TARGET) options.add(MainMenuOption.ADD_NEW);
        if (!mods.isEmpty()) options.add(MainMenuOption.REMOVE);
        options.add(MainMenuOption.CANCEL);

        ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "→ Видозміна: " + target.getName());
        ctx.openChoiceMenu("Видозміна", options, this::createMainMenuMenuItem,
                opt -> handleMainMenuChoice(ctx, target, opt));
    }

    private void handleMainMenuChoice(IAbilityContext ctx, Player target, MainMenuOption option) {
        switch (option) {
            case ADD_NEW -> openModificationCreationMenu(ctx, target);
            case VIEW_LIST -> {
                openModificationListMenu(ctx, target);
                // Viewing doesn't consume resources - just show info
            }
            case REMOVE -> {
                openRemovalMenu(ctx, target);
                // Removing doesn't consume resources
            }
            case CANCEL -> {
                ctx.sendMessageToCaster(ChatColor.GRAY + "Скасовано");
                // Cancelling doesn't consume resources
            }
        }
    }

    // ==========================================
    // ПЕРЕГЛЯД СПИСКУ
    // ==========================================
    private void openModificationListMenu(IAbilityContext ctx, Player target) {
        UUID targetId = target.getUniqueId();
        List<ModificationData> mods = activeModifications.getOrDefault(targetId, new ArrayList<>());

        if (mods.isEmpty()) {
            ctx.sendMessageToCaster(ChatColor.YELLOW + "→ Немає активних модифікацій на цій цілі");
            ctx.playSoundToCaster(Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }

        ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "═══════════════════════════════");
        ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "АКТИВНІ ВИДОЗМІНИ");
        ctx.sendMessageToCaster(ChatColor.GRAY + "Ціль: " + ChatColor.WHITE + target.getName());

        for (int i = 0; i < mods.size(); i++) {
            ModificationData mod = mods.get(i);
            ctx.sendMessageToCaster("");
            ctx.sendMessageToCaster(ChatColor.GOLD + "" + ChatColor.BOLD + "Модифікація #" + (i + 1) + ":");
            ctx.sendMessageToCaster(ChatColor.GRAY + "  Тригер: " + mod.trigger.getColor() + mod.trigger.getDisplayName());
            ctx.sendMessageToCaster(ChatColor.GRAY + "  Наслідок: " + mod.effect.getColor() + mod.effect.getDisplayName());

            long remainingMs = mod.expirationTime - System.currentTimeMillis();
            int remainingSeconds = (int) (Math.max(0, remainingMs) / 1000);
            if (remainingSeconds > 0) {
                ctx.sendMessageToCaster(ChatColor.GRAY + "  Залишилось: " + ChatColor.YELLOW + remainingSeconds + "с");
            }
        }
        ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "═══════════════════════════════");
    }

    // ==========================================
    // ВИДАЛЕННЯ
    // ==========================================
    private void openRemovalMenu(IAbilityContext ctx, Player target) {
        UUID targetId = target.getUniqueId();
        List<ModificationData> mods = activeModifications.getOrDefault(targetId, new ArrayList<>());

        if (mods.isEmpty()) {
            ctx.sendMessageToCaster(ChatColor.YELLOW + "→ Немає модифікацій для видалення");
            return;
        }

        List<String> options = new ArrayList<>();
        for (int i = 0; i < mods.size(); i++) {
            ModificationData mod = mods.get(i);
            options.add("Модифікація #" + (i + 1) + ": " + mod.trigger.getDisplayName() + " → " + mod.effect.getDisplayName());
        }
        options.add("Видалити всі");
        options.add("Скасувати");

        ctx.openChoiceMenu("Видалення модифікацій", options,
                optionText -> {
                    ItemStack item = new ItemStack(Material.BARRIER);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ChatColor.RED + optionText);
                        item.setItemMeta(meta);
                    }
                    return item;
                },
                optionText -> handleRemovalChoice(ctx, target, optionText, options)
        );
    }

    private void handleRemovalChoice(IAbilityContext ctx, Player target, String choice, List<String> options) {
        UUID targetId = target.getUniqueId();
        List<ModificationData> mods = activeModifications.getOrDefault(targetId, new ArrayList<>());

        if (choice.equals("Скасувати")) {
            ctx.sendMessageToCaster(ChatColor.GRAY + "Скасовано");
            return;
        }

        if (choice.equals("Видалити всі")) {
            mods.clear();
            activeModifications.remove(targetId);
            ctx.sendMessageToCaster(ChatColor.GREEN + "✓ Всі модифікації видалено");
            ctx.playSoundToCaster(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
            return;
        }

        for (int i = 0; i < options.size() - 2; i++) {
            if (choice.equals(options.get(i))) {
                if (i < mods.size()) {
                    ModificationData removed = mods.remove(i);
                    if (mods.isEmpty()) activeModifications.remove(targetId);

                    ctx.sendMessageToCaster(ChatColor.GREEN + "✓ Видалено: " + removed.trigger.getDisplayName());
                    ctx.playSoundToCaster(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
                break;
            }
        }
    }

    // ==========================================
    // СТВОРЕННЯ
    // ==========================================
    private void openModificationCreationMenu(IAbilityContext ctx, Player target) {
        ctx.openChoiceMenu("Видозміна: Тригер", Arrays.asList(TriggerType.values()),
                this::createTriggerMenuItem,
                trigger -> openEffectSelectionMenu(ctx, target, trigger));
    }

    private void openEffectSelectionMenu(IAbilityContext ctx, Player target, TriggerType trigger) {
        ctx.openChoiceMenu("Видозміна: Наслідок", Arrays.asList(EffectType.values()),
                this::createEffectMenuItem,
                effect -> applyModification(ctx, target, trigger, effect));
    }

    // ==========================================
    // ЗАСТОСУВАННЯ (З ВИПРАВЛЕННЯМ КУЛДАУНУ)
    // ==========================================
    private void applyModification(IAbilityContext ctx, Player target, TriggerType trigger, EffectType effect) {
        UUID targetId = target.getUniqueId();
        Player caster = ctx.getCaster();
        Beyonder casterBeyonder = ctx.getCasterBeyonder();

        caster.closeInventory();

        List<ModificationData> mods = activeModifications.computeIfAbsent(targetId, k -> new ArrayList<>());

        // Перевірка ліміту
        if (mods.size() >= MAX_MODIFICATIONS_PER_TARGET) {
            ctx.sendMessageToCaster(ChatColor.RED + "✗ Досягнуто ліміт модифікацій на цій цілі!");
            ctx.playSoundToCaster(Sound.BLOCK_ANVIL_LAND, 1f, 2f);
            return;
        }

        // КРИТИЧНО: Споживаємо ресурси ТІЛЬКИ ЗАРАЗ, коли модифікація справді створюється
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.sendMessageToCaster(ChatColor.RED + "Недостатньо духовності для створення модифікації!");
            return;
        }

        // Створюємо модифікацію
        long expirationTime = System.currentTimeMillis() + (MODIFICATION_DURATION_TICKS * 50L);
        ModificationData mod = new ModificationData(ctx.getCasterId(), trigger, effect, expirationTime);
        mods.add(mod);

        subscribeTriggerEvent(ctx, targetId, trigger, effect, mod);

        // Таймер на видалення модифікації через 3 хвилини
        ctx.scheduleDelayed(() -> removeModification(targetId, mod), MODIFICATION_DURATION_TICKS);

        // Фідбек
        ctx.sendMessageToCaster(ChatColor.GREEN + "✓ Видозміну успішно створено!");
        ctx.playSoundToCaster(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.5f);

        ctx.getCaster().spawnParticle(Particle.WITCH, target.getEyeLocation().add(0, 0.5, 0), 10, 0.2, 0.2, 0.2, 0);
    }

    // ==========================================
    // ДОПОМІЖНІ МЕТОДИ
    // ==========================================
    private void removeModification(UUID targetId, ModificationData mod) {
        List<ModificationData> mods = activeModifications.get(targetId);
        if (mods != null) {
            mods.remove(mod);
            if (mods.isEmpty()) activeModifications.remove(targetId);
        }
    }

    private void subscribeTriggerEvent(IAbilityContext ctx, UUID targetId, TriggerType trigger, EffectType effect, ModificationData mod) {
        switch (trigger) {
            case PICKUP_ITEM -> ctx.subscribeToEvent(EntityPickupItemEvent.class,
                    e -> e.getEntity().getUniqueId().equals(targetId),
                    e -> applyEffect(ctx, targetId, effect, mod), MODIFICATION_DURATION_TICKS);
            case TOUCH_WATER -> ctx.subscribeToEvent(PlayerMoveEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(targetId) && e.getPlayer().isInWater(),
                    e -> applyEffect(ctx, targetId, effect, mod), MODIFICATION_DURATION_TICKS);
            case ATTACK -> ctx.subscribeToEvent(org.bukkit.event.entity.EntityDamageByEntityEvent.class,
                    e -> e.getDamager() != null && e.getDamager().getUniqueId().equals(targetId),
                    e -> applyEffect(ctx, targetId, effect, mod), MODIFICATION_DURATION_TICKS);
            case BREAK_BLOCK -> ctx.subscribeToEvent(org.bukkit.event.block.BlockBreakEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(targetId),
                    e -> applyEffect(ctx, targetId, effect, mod), MODIFICATION_DURATION_TICKS);
            case OPEN_CHEST -> ctx.subscribeToEvent(PlayerInteractEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(targetId) && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.CHEST,
                    e -> applyEffect(ctx, targetId, effect, mod), MODIFICATION_DURATION_TICKS);
            case EAT_FOOD -> ctx.subscribeToEvent(PlayerItemConsumeEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(targetId),
                    e -> applyEffect(ctx, targetId, effect, mod), MODIFICATION_DURATION_TICKS);
            case SNEAK -> ctx.subscribeToEvent(PlayerToggleSneakEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(targetId) && e.isSneaking(),
                    e -> applyEffect(ctx, targetId, effect, mod), MODIFICATION_DURATION_TICKS);
            case TAKE_DAMAGE -> ctx.subscribeToEvent(org.bukkit.event.entity.EntityDamageEvent.class,
                    e -> e.getEntity() != null && e.getEntity().getUniqueId().equals(targetId),
                    e -> applyEffect(ctx, targetId, effect, mod), MODIFICATION_DURATION_TICKS);
            case CHAT -> ctx.subscribeToEvent(AsyncPlayerChatEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(targetId),
                    e -> Bukkit.getScheduler().runTask(ctx.getCaster().getServer().getPluginManager().getPlugin("Mysteries-Above"),
                            () -> applyEffect(ctx, targetId, effect, mod)), MODIFICATION_DURATION_TICKS);
        }
    }

    private void applyEffect(IAbilityContext ctx, UUID targetId, EffectType effect, ModificationData mod) {
        List<ModificationData> activeList = activeModifications.get(targetId);
        if (activeList == null || !activeList.contains(mod)) {
            return;
        }
        if (System.currentTimeMillis() - mod.lastTriggerTime < 3000) {
            return;
        }
        mod.lastTriggerTime = System.currentTimeMillis();
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) return;

        switch (effect) {
            case BLINDNESS -> {
                ctx.applyEffect(targetId, PotionEffectType.BLINDNESS, EFFECT_DURATION_TICKS, 0);
                ctx.sendMessage(targetId, ChatColor.GRAY + "Раптова темрява...");
            }
            case WEAKNESS -> {
                ctx.applyEffect(targetId, PotionEffectType.WEAKNESS, EFFECT_DURATION_TICKS, 1);
                ctx.sendMessage(targetId, ChatColor.GRAY + "Сили покидають вас...");
            }
            case HUNGER -> {
                ctx.applyEffect(targetId, PotionEffectType.HUNGER, EFFECT_DURATION_TICKS, 1);
                ctx.sendMessage(targetId, ChatColor.GOLD + "Раптовий голод...");
            }
            case DROP_ITEM -> {
                ItemStack hand = target.getInventory().getItemInMainHand();
                if (hand != null && hand.getType() != Material.AIR) {
                    target.getWorld().dropItem(target.getLocation(), hand.clone());
                    target.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    ctx.sendMessage(targetId, ChatColor.RED + "Предмет випадає з рук...");
                }
            }
            case CONFUSION -> {
                ctx.applyEffect(targetId, PotionEffectType.NAUSEA, EFFECT_DURATION_TICKS, 0);
                ctx.sendMessage(targetId, ChatColor.DARK_GRAY + "Голова крутиться...");
            }
            case FREEZE -> {
                ctx.applyEffect(targetId, PotionEffectType.SLOWNESS, EFFECT_DURATION_TICKS, 255);
                ctx.sendMessage(targetId, ChatColor.BLUE + "Тіло застигає...");
            }
            case DAMAGE -> {
                ctx.damage(targetId, 2.0);
                ctx.sendMessage(targetId, ChatColor.RED + "Різкий біль...");
            }
        }
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
    }

    private ItemStack createMainMenuMenuItem(MainMenuOption option) {
        ItemStack stack = new ItemStack(option.getIcon());
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(option.getColor() + option.getDisplayName());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createTriggerMenuItem(TriggerType type) {
        ItemStack stack = new ItemStack(type.getIcon());
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(type.getColor() + type.getDisplayName());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createEffectMenuItem(EffectType type) {
        ItemStack stack = new ItemStack(type.getIcon());
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(type.getColor() + type.getDisplayName());
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public void cleanUp() {
        activeModifications.clear();
    }

    // ==========================================
    // ВНУТРІШНІ КЛАСИ ТА ENUMS
    // ==========================================

    private static class ModificationData {
        final UUID casterId;
        final TriggerType trigger;
        final EffectType effect;
        final long expirationTime;
        long lastTriggerTime = 0;

        ModificationData(UUID casterId, TriggerType trigger, EffectType effect, long expirationTime) {
            this.casterId = casterId;
            this.trigger = trigger;
            this.effect = effect;
            this.expirationTime = expirationTime;
        }
    }

    enum MainMenuOption {
        ADD_NEW("Додати нову", "Створити нову модифікацію", Material.WRITABLE_BOOK, ChatColor.GREEN),
        VIEW_LIST("Переглянути список", "Показати всі активні модифікації", Material.BOOK, ChatColor.AQUA),
        REMOVE("Видалити", "Видалити модифікацію", Material.BARRIER, ChatColor.RED),
        CANCEL("Скасувати", "Закрити меню", Material.OAK_DOOR, ChatColor.GRAY);

        private final String displayName;
        private final String description;
        private final Material icon;
        private final ChatColor color;

        MainMenuOption(String displayName, String description, Material icon, ChatColor color) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Material getIcon() { return icon; }
        public ChatColor getColor() { return color; }
    }

    enum TriggerType {
        PICKUP_ITEM("Підбір предмета", "Коли ціль підбирає предмет", Material.DIAMOND, ChatColor.AQUA),
        TOUCH_WATER("Контакт з водою", "Коли ціль торкається води", Material.WATER_BUCKET, ChatColor.BLUE),
        ATTACK("Атака", "Коли ціль атакує когось", Material.IRON_SWORD, ChatColor.RED),
        BREAK_BLOCK("Розбиття блоку", "Коли ціль розбиває блок", Material.DIAMOND_PICKAXE, ChatColor.GRAY),
        OPEN_CHEST("Відкриття скрині", "Коли ціль відкриває скриню", Material.CHEST, ChatColor.GOLD),
        EAT_FOOD("Поїдання їжі", "Коли ціль їсть", Material.COOKED_BEEF, ChatColor.YELLOW),
        SNEAK("Присідання", "Коли ціль присідає", Material.LEATHER_BOOTS, ChatColor.DARK_GRAY),
        TAKE_DAMAGE("Отримання шкоди", "Коли ціль отримує шкоду", Material.SHIELD, ChatColor.RED),
        CHAT("Написання в чат", "Коли ціль пише в чат", Material.PAPER, ChatColor.WHITE);

        private final String displayName;
        private final String description;
        private final Material icon;
        private final ChatColor color;

        TriggerType(String displayName, String description, Material icon, ChatColor color) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Material getIcon() { return icon; }
        public ChatColor getColor() { return color; }
    }

    enum EffectType {
        BLINDNESS("Сліпота", "Тимчасова сліпота", Material.INK_SAC, ChatColor.BLACK),
        WEAKNESS("Слабкість", "Втрата сил", Material.ROTTEN_FLESH, ChatColor.GRAY),
        HUNGER("Голод", "Різка втрата насичення", Material.POISONOUS_POTATO, ChatColor.GOLD),
        DROP_ITEM("Випадання предмета", "Випускає предмет з рук", Material.DROPPER, ChatColor.RED),
        CONFUSION("Запаморочення", "Нудота та дезорієнтація", Material.SPIDER_EYE, ChatColor.DARK_GREEN),
        FREEZE("Заморожування", "Тимчасово не може рухатись", Material.ICE, ChatColor.BLUE),
        DAMAGE("Шкода", "Отримує фізичну шкоду", Material.TNT, ChatColor.DARK_RED);

        private final String displayName;
        private final String description;
        private final Material icon;
        private final ChatColor color;

        EffectType(String displayName, String description, Material icon, ChatColor color) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Material getIcon() { return icon; }
        public ChatColor getColor() { return color; }
    }
}