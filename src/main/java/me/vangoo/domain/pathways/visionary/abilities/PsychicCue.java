package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class PsychicCue extends ActiveAbility {
    private static final int BASE_RANGE = 3;
    private static final int BASE_NAVIGATION_RANGE = 50;
    private static final int DURATION_TICKS = 60 * 20;
    private static final int BASE_COOLDOWN = 60;
    private static final int COST = 150;

    @Override
    public String getName() {
        return "Психічний сигнал";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        if (userSequence.level() <= 6) {
            int navRange = scaleValue(BASE_NAVIGATION_RANGE, userSequence, SequenceScaler.ScalingStrategy.MODERATE);
            return "Навіює цілі психологічні установки через прямий контакт. " +
                    "Діє " + (DURATION_TICKS / 20) + " секунд.\n" +
                    ChatColor.GOLD + "◆ Нова сила: " + ChatColor.WHITE + "Можна задати точку куди буде притягуватися ціль (до " + navRange + " блоків).\n";
        } else {
            return "Навіює цілі психологічні установки через прямий контакт. " +
                    "Діє " + (DURATION_TICKS / 20) + " секунд.\n";
        }
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return (int) (BASE_COOLDOWN / SequenceScaler.calculateMultiplier(
                userSequence.level(), SequenceScaler.ScalingStrategy.MODERATE));
    }

    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.getTargetedEntity(BASE_RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(BASE_RANGE);

        if (targetOpt.isEmpty() || !(targetOpt.get() instanceof Player target)) {
            return AbilityResult.failure("Ціль має бути гравцем поруч");
        }
        Sequence casterSequence = context.getCasterBeyonder().getSequence();

        // Seq 6+ отримує доступ до навігації
        if (casterSequence.level() <= 6) {
            openAdvancedMenu(context, target);
        } else {
            openBasicMenu(context, target);
        }

        // КРИТИЧНО: Повертаємо deferred - ресурси будуть спожиті пізніше
        return AbilityResult.deferred();
    }

    /**
     * Базове меню для послідовностей 7-9
     */
    private void openBasicMenu(IAbilityContext ctx, Player target) {
        ctx.openChoiceMenu(
                "Психічний сигнал",
                Arrays.asList(PsychicCueType.values()),
                this::createMenuItem,
                cue -> applyCue(ctx, target, cue)
        );
    }

    /**
     * Розширене меню для послідовностей 6 і вище
     */
    private void openAdvancedMenu(IAbilityContext ctx, Player target) {
        List<AdvancedCueOption> options = new ArrayList<>();

        // Додаємо базові опції
        for (PsychicCueType type : PsychicCueType.values()) {
            options.add(new AdvancedCueOption(type, false));
        }

        // Додаємо опцію навігації
        options.add(new AdvancedCueOption(null, true));

        ctx.openChoiceMenu(
                "Психічний сигнал (Розширений)",
                options,
                this::createAdvancedMenuItem,
                option -> {
                    if (option.isNavigation) {
                        startNavigationSetup(ctx, target);
                    } else {
                        applyCue(ctx, target, option.cueType);
                    }
                }
        );
    }

    /**
     * Запустити процес встановлення точки навігації
     */
    private void startNavigationSetup(IAbilityContext ctx, Player target) {
        Sequence casterSeq = ctx.getCasterBeyonder().getSequence();
        int maxRange = scaleValue(BASE_NAVIGATION_RANGE, casterSeq, SequenceScaler.ScalingStrategy.MODERATE);

        ctx.sendMessageToCaster(ChatColor.GOLD + "▶ Режим навігації активовано");
        ctx.sendMessageToCaster(ChatColor.GRAY + "Клацніть ПКМ по блоку щоб задати точку призначення");
        ctx.sendMessageToCaster(ChatColor.GRAY + "Максимальна дальність: " + ChatColor.YELLOW + maxRange + " блоків");
        ctx.sendMessageToCaster(ChatColor.GRAY + "Shift + ПКМ для скасування");

        ctx.playSoundToCaster(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        // Підписуємось на клік гравця
        ctx.subscribeToEvent(
                PlayerInteractEvent.class,
                e -> e.getPlayer().equals(ctx.getCaster()) &&
                        (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK ||
                                e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR),
                e -> {
                    e.setCancelled(true);

                    // Скасування - НЕ споживає ресурси
                    if (e.getPlayer().isSneaking()) {
                        ctx.sendMessageToCaster(ChatColor.YELLOW + "✗ Навігація скасована");
                        ctx.playSoundToCaster(Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                        return;
                    }

                    // Встановлення точки
                    if (e.getClickedBlock() != null) {
                        Location destination = e.getClickedBlock().getLocation().add(0.5, 1, 0.5);
                        double distance = destination.distance(ctx.getCasterLocation());

                        if (distance > maxRange) {
                            ctx.sendMessageToCaster(ChatColor.RED + "✗ Занадто далеко! (" +
                                    (int)distance + "/" + maxRange + " блоків)");
                            ctx.playSoundToCaster(Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                            return;
                        }

                        // ТІЛЬКИ ТУТ споживаємо ресурси
                        applyNavigationCue(ctx, target, destination);
                    }
                },
                200 // 10 секунд на вибір точки
        );
    }

    /**
     * Застосувати сигнал навігації
     */
    private void applyNavigationCue(IAbilityContext ctx, Player target, Location destination) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();

        // КРИТИЧНО: Споживаємо ресурси ТІЛЬКИ ЗАРАЗ
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.sendMessageToCaster(ChatColor.RED + "Недостатньо духовності!");
            return;
        }

        // Візуальні ефекти
        ctx.spawnParticle(Particle.WITCH, target.getEyeLocation(), 15, 0.4, 0.4, 0.4);
        ctx.playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.8f);
        ctx.spawnParticle(Particle.END_ROD, destination, 30, 0.3, 0.5, 0.3);
        ctx.playSound(destination, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);

        // Якщо ціль вже в човні/вагонетці — викидаємо
        if (target.isInsideVehicle()) {
            target.leaveVehicle();
        }

        // Повідомлення
        ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "Навіяно: " + ChatColor.GOLD + "Навігаційний імператив");
        ctx.playSoundToCaster(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.6f);

        target.sendMessage(ChatColor.DARK_PURPLE + "✦ Ваша свідомість отримала новий імператив...");
        target.sendMessage(ChatColor.GRAY + "Щось притягує вас до певної точці...");

        // Стан активності
        final boolean[] isActive = {true};

        // Заборона сідати в транспорт
        ctx.subscribeToEvent(
                VehicleEnterEvent.class,
                e -> e.getEntered().getUniqueId().equals(target.getUniqueId()) && isActive[0],
                e -> {
                    e.setCancelled(true);
                    ctx.sendMessage(target.getUniqueId(), ChatColor.ITALIC + "Імператив змушує вас йти пішки...");
                },
                DURATION_TICKS
        );

        // Запускаємо цикл навігації
        startNavigationLoop(ctx, target, destination, isActive);
    }

    /**
     * Цикл навігації - ФІЗИЧНО притягує гравця до точки
     */
    private void startNavigationLoop(IAbilityContext ctx, Player target, Location destination, boolean[] isActive) {
        final int CHECK_INTERVAL = 5;
        final double COMPLETION_RADIUS = 2.0;
        final double PULL_STRENGTH = 0.3;
        final double MIN_DISTANCE_FOR_PULL = 3.0;

        final int[] lastDistanceBracket = {-1};
        final boolean[] lastSlownessApplied = {false};

        ctx.scheduleRepeating(new Runnable() {
            int ticksPassed = 0;
            int pullCounter = 0;

            @Override
            public void run() {
                if (!isActive[0]) {
                    return;
                }

                if (ticksPassed >= DURATION_TICKS) {
                    if (isActive[0]) {
                        isActive[0] = false;
                        target.sendMessage(ChatColor.YELLOW + "✓ Імператив згас...");
                        ctx.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.8f);
                        ctx.removeEffect(target.getUniqueId(), PotionEffectType.SLOWNESS);
                    }
                    return;
                }

                if (!target.isOnline()) {
                    isActive[0] = false;
                    return;
                }

                drawTargetMarker(ctx, destination);
                Location currentLoc = target.getLocation();
                double distance = currentLoc.distance(destination);

                if (distance <= COMPLETION_RADIUS) {
                    if (isActive[0]) {
                        isActive[0] = false;
                        target.sendMessage(ChatColor.GREEN + "✓ Ви досягли пункту призначення");
                        ctx.spawnParticle(Particle.TOTEM_OF_UNDYING, currentLoc, 20, 0.5, 1, 0.5);
                        ctx.playSound(currentLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                        ctx.removeEffect(target.getUniqueId(), PotionEffectType.SLOWNESS);
                    }
                    return;
                }

                pullCounter++;
                if (pullCounter >= 2 && distance > MIN_DISTANCE_FOR_PULL) {
                    pullCounter = 0;

                    Vector direction = destination.toVector()
                            .subtract(currentLoc.toVector())
                            .normalize();

                    double adaptivePullStrength = PULL_STRENGTH;
                    if (distance > 20) {
                        adaptivePullStrength = PULL_STRENGTH * 1.5;
                    } else if (distance < 8) {
                        adaptivePullStrength = PULL_STRENGTH * 0.7;
                    }

                    Vector pullVelocity = direction.multiply(adaptivePullStrength);
                    Vector currentVelocity = target.getVelocity();
                    pullVelocity.setY(currentVelocity.getY());

                    target.setVelocity(pullVelocity);

                    ctx.spawnParticle(Particle.WITCH, currentLoc.clone().add(0, 1, 0), 2, 0.2, 0.2, 0.2);
                    ctx.spawnParticle(Particle.END_ROD,
                            currentLoc.clone().add(direction.multiply(1.5)).add(0, 1, 0),
                            1, 0.1, 0.1, 0.1);
                }

                if (ticksPassed % 80 == 0 && !lastSlownessApplied[0]) {
                    ctx.applyEffect(target.getUniqueId(), PotionEffectType.SLOWNESS, 100, 0);
                    lastSlownessApplied[0] = true;
                } else if (ticksPassed % 80 != 0) {
                    lastSlownessApplied[0] = false;
                }

                int currentBracket = (int)(distance / 10);

                if (ticksPassed % 40 == 0 && currentBracket != lastDistanceBracket[0]) {
                    lastDistanceBracket[0] = currentBracket;

                    Vector direction = destination.toVector().subtract(currentLoc.toVector()).normalize();
                    Location particleLoc = currentLoc.clone().add(direction.multiply(2)).add(0, 1.5, 0);

                    ctx.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 8, 0.2, 0.2, 0.2);
                    ctx.playSound(currentLoc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.5f);
                    ctx.playSound(currentLoc, Sound.ENTITY_VEX_AMBIENT, 0.2f, 0.8f);

                    String arrow = getDirectionArrow(currentLoc, destination);
                    target.sendMessage(ChatColor.DARK_PURPLE + arrow + " " + ChatColor.GRAY +
                            "Імператив тягне вас... (" + (int)distance + "м)");
                }

                ticksPassed += CHECK_INTERVAL;
            }
        }, 0, CHECK_INTERVAL);
    }

    private void drawTargetMarker(IAbilityContext ctx, Location destination) {
        Location floorLoc = destination.clone().subtract(0, 0.8, 0);

        for (double y = 0; y < 2.5; y += 0.8) {
            ctx.spawnParticle(Particle.END_ROD, floorLoc.clone().add(0, y, 0), 1, 0, 0, 0);
        }

        for (int degree = 0; degree < 360; degree += 45) {
            double radians = Math.toRadians(degree);
            double x = Math.cos(radians) * 0.7;
            double z = Math.sin(radians) * 0.7;

            Location point = floorLoc.clone().add(x, 0.1, z);
            ctx.spawnParticle(Particle.WITCH, point, 1, 0, 0, 0);
        }

        ctx.spawnParticle(Particle.DRAGON_BREATH, floorLoc.clone().add(0, 0.2, 0), 1, 0.1, 0.1, 0.1);
    }

    private String getDirectionArrow(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "→" : "←";
        } else {
            return dz > 0 ? "↓" : "↑";
        }
    }

    /**
     * Застосувати базовий сигнал (для всіх послідовностей)
     */
    private void applyCue(IAbilityContext ctx, Player target, PsychicCueType type) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();

        // КРИТИЧНО: Споживаємо ресурси ТІЛЬКИ ЗАРАЗ, коли сигнал застосовується
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.sendMessageToCaster(ChatColor.RED + "Недостатньо духовності!");
            return;
        }

        switch (type) {
            case HUNGER -> ctx.subscribeToEvent(
                    PlayerItemConsumeEvent.class,
                    e -> e.getPlayer().equals(target),
                    e -> {
                        e.setCancelled(true);
                        ctx.sendMessage(target.getUniqueId(),
                                ChatColor.ITALIC + "Їжа викликає огиду...");
                    },
                    DURATION_TICKS
            );

            case SLOTH -> {
                target.setSprinting(false);
                ctx.subscribeToEvent(
                        PlayerMoveEvent.class,
                        e -> e.getPlayer().equals(target) && e.getPlayer().isSprinting(),
                        e -> e.getPlayer().setSprinting(false),
                        DURATION_TICKS
                );
                ctx.subscribeToEvent(
                        PlayerToggleSprintEvent.class,
                        e -> e.getPlayer().equals(target) && e.isSprinting(),
                        e -> {
                            e.setCancelled(true);
                            ctx.sendMessage(target.getUniqueId(),
                                    ChatColor.ITALIC + "Ноги занадто важкі...");
                        },
                        DURATION_TICKS
                );
            }

            case PACIFISM -> ctx.subscribeToEvent(
                    EntityDamageByEntityEvent.class,
                    e -> e.getDamager().equals(target),
                    e -> {
                        e.setCancelled(true);
                        ctx.sendMessage(target.getUniqueId(),
                                ChatColor.ITALIC + "Агресія покинула вас...");
                    },
                    DURATION_TICKS
            );

            case SILENCE -> ctx.subscribeToEvent(
                    AsyncPlayerChatEvent.class,
                    e -> e.getPlayer().equals(target),
                    e -> {
                        e.setCancelled(true);
                        ctx.sendMessage(target.getUniqueId(),
                                ChatColor.ITALIC + "Слова застрягають...");
                    },
                    DURATION_TICKS
            );

            case APATHY -> ctx.subscribeToEvent(
                    PlayerInteractEvent.class,
                    e -> e.getPlayer().equals(target) &&
                            e.hasBlock() &&
                            e.getAction().name().contains("RIGHT"),
                    e -> {
                        e.setCancelled(true);
                        ctx.sendMessage(target.getUniqueId(),
                                ChatColor.ITALIC + "Байдуже до всього...");
                    },
                    DURATION_TICKS
            );
        }

        showSuccessEffects(ctx, target, type);
    }

    private void showSuccessEffects(IAbilityContext ctx, Player target, PsychicCueType type) {
        ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "Навіяно: " + type.getDisplayName());
        ctx.playSoundToCaster(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.6f);
        ctx.spawnParticle(Particle.WITCH, target.getEyeLocation(), 15, 0.4, 0.4, 0.4);
        ctx.playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.8f);
    }

    private ItemStack createMenuItem(PsychicCueType type) {
        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(type.getDisplayName());
            meta.setLore(Collections.singletonList(type.getDescription()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAdvancedMenuItem(AdvancedCueOption option) {
        if (option.isNavigation) {
            ItemStack item = new ItemStack(Material.COMPASS);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Навігаційний імператив");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Змусити ціль йти до точки",
                        ChatColor.DARK_GRAY + "✦ Доступно з послідовності 6"
                ));
                item.setItemMeta(meta);
            }
            return item;
        } else {
            return createMenuItem(option.cueType);
        }
    }

    // ========== ДОПОМІЖНІ КЛАСИ ==========

    private static class AdvancedCueOption {
        final PsychicCueType cueType;
        final boolean isNavigation;

        AdvancedCueOption(PsychicCueType cueType, boolean isNavigation) {
            this.cueType = cueType;
            this.isNavigation = isNavigation;
        }
    }

    enum PsychicCueType {
        HUNGER("Голод", "Заборонити їсти", Material.BREAD, ChatColor.GOLD),
        SLOTH("Млявість", "Заборонити бігати", Material.LEATHER_BOOTS, ChatColor.GRAY),
        PACIFISM("Пацифізм", "Заборонити атакувати", Material.IRON_SWORD, ChatColor.AQUA),
        SILENCE("Оніміння", "Заборонити чат", Material.PAPER, ChatColor.YELLOW),
        APATHY("Апатія", "Заборонити взаємодію", Material.BARRIER, ChatColor.DARK_GRAY);

        private final String name;
        private final String description;
        private final Material icon;
        private final ChatColor color;

        PsychicCueType(String name, String description, Material icon, ChatColor color) {
            this.name = name;
            this.description = description;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplayName() {
            return color + name;
        }

        public String getDescription() {
            return ChatColor.GRAY + description;
        }

        public Material getIcon() {
            return icon;
        }
    }
}