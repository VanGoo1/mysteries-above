package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    private static final int DURATION_SECONDS = 15;
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
        return context.targeting().getTargetedEntity(BASE_RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(BASE_RANGE);

        if (targetOpt.isEmpty() || !(targetOpt.get() instanceof Player target)) {
            return AbilityResult.failure("Ціль має бути гравцем поруч");
        }
        Sequence casterSequence = context.getCasterBeyonder().getSequence();

        // Seq 6+ отримує доступ до навігації
        if (casterSequence.level() <= 6) {
            openAdvancedMenu(context, target.getUniqueId());
        } else {
            openBasicMenu(context, target.getUniqueId());
        }

        // КРИТИЧНО: Повертаємо deferred - ресурси будуть спожиті пізніше
        return AbilityResult.deferred();
    }

    /**
     * Базове меню для послідовностей 7-9
     */
    private void openBasicMenu(IAbilityContext ctx, UUID targetId) {
        ctx.ui().openChoiceMenu(
                "Психічний сигнал",
                Arrays.asList(PsychicCueType.values()),
                this::createMenuItem,
                cue -> applyCue(ctx, targetId, cue)
        );
    }

    /**
     * Розширене меню для послідовностей 6 і вище
     */
    private void openAdvancedMenu(IAbilityContext ctx, UUID targetId) {
        List<AdvancedCueOption> options = new ArrayList<>();

        // Додаємо базові опції
        for (PsychicCueType type : PsychicCueType.values()) {
            options.add(new AdvancedCueOption(type, false));
        }

        // Додаємо опцію навігації
        options.add(new AdvancedCueOption(null, true));

        ctx.ui().openChoiceMenu(
                "Психічний сигнал (Розширений)",
                options,
                this::createAdvancedMenuItem,
                option -> {
                    if (option.isNavigation) {
                        startNavigationSetup(ctx, targetId);
                    } else {
                        applyCue(ctx, targetId, option.cueType);
                    }
                }
        );
    }

    /**
     * Запустити процес встановлення точки навігації
     */
    private void startNavigationSetup(IAbilityContext ctx, UUID targetId) {
        Sequence casterSeq = ctx.getCasterBeyonder().getSequence();
        int maxRange = scaleValue(BASE_NAVIGATION_RANGE, casterSeq, SequenceScaler.ScalingStrategy.MODERATE);

        // Відправляємо кастеру action-bar повідомлення
        ctx.messaging().sendMessageToActionBar(ctx.getCasterId(), LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.GOLD + "▶ Режим навігації активовано"));
        ctx.messaging().sendMessageToActionBar(ctx.getCasterId(), LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.GRAY + "Клацніть ПКМ по блоку щоб задати точку призначення"));
        ctx.messaging().sendMessageToActionBar(ctx.getCasterId(), LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.GRAY + "Максимальна дальність: " + ChatColor.YELLOW + maxRange + " блоків"));
        ctx.messaging().sendMessageToActionBar(ctx.getCasterId(), LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.GRAY + "Shift + ПКМ для скасування"));

        ctx.effects().playSoundForPlayer(ctx.getCasterId(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        // Підписуємось на клік гравця
        ctx.events().subscribeToTemporaryEvent(ctx.getCasterId(),
                PlayerInteractEvent.class,
                e -> e.getPlayer().equals(ctx.getCasterPlayer()) &&
                        (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK ||
                                e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR),
                e -> {
                    e.setCancelled(true);

                    // Скасування - НЕ споживає ресурси
                    if (e.getPlayer().isSneaking()) {
                        ctx.messaging().sendMessageToActionBar(ctx.getCasterId(), Component.text("✗ Навігація скасована").color(NamedTextColor.YELLOW));
                        ctx.effects().playSoundForPlayer(ctx.getCasterId(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                        return;
                    }

                    // Встановлення точки
                    if (e.getClickedBlock() != null) {
                        Location destination = e.getClickedBlock().getLocation().add(0.5, 1, 0.5);
                        double distance = destination.distance(ctx.getCasterLocation());

                        if (distance > maxRange) {
                            ctx.messaging().sendMessageToActionBar(ctx.getCasterId(), Component.text("✗ Занадто далеко! (" +
                                    (int) distance + "/" + maxRange + " блоків)").color(NamedTextColor.RED));
                            ctx.effects().playSoundForPlayer(ctx.getCasterId(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                            return;
                        }

                        // ТІЛЬКИ ТУТ споживаємо ресурси
                        applyNavigationCue(ctx, targetId, destination);
                    }
                },
                200 // 10 секунд на вибір точки
        );
    }

    /**
     * Застосувати сигнал навігації
     */
    private void applyNavigationCue(IAbilityContext ctx, UUID targetId, Location destination) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();

        // КРИТИЧНО: Споживаємо ресурси ТІЛЬКИ ЗАРАЗ
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.messaging().sendMessageToActionBar(ctx.getCasterId(), Component.text("Недостатньо духовності!").color(NamedTextColor.RED));
            return;
        }
        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        // Візуальні ефекти
        ctx.effects().spawnParticle(Particle.WITCH, ctx.playerData().getEyeLocation(targetId), 15, 0.4, 0.4, 0.4);
        ctx.effects().playSound(ctx.playerData().getCurrentLocation(targetId), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.8f);
        ctx.effects().spawnParticle(Particle.END_ROD, destination, 30, 0.3, 0.5, 0.3);
        ctx.effects().playSound(destination, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);

        // Якщо ціль вже в човні/вагонетці — викидаємо
        if (ctx.playerData().isInsideVehicle(targetId)) {
            ctx.entity().leaveVehicle(targetId);
        }

        // Повідомлення (кастер)
        ctx.messaging().sendMessageToActionBar(ctx.getCasterId(), Component.text("Навіяно: ").color(NamedTextColor.DARK_PURPLE).append(Component.text("Навігаційний імператив").color(NamedTextColor.GOLD)));
        ctx.effects().playSoundForPlayer(targetId, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.6f);

        // Повідомлення цілі (action bar)
        ctx.messaging().sendMessageToActionBar(targetId, LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.DARK_PURPLE + "✦ Ваша свідомість отримала новий імператив..."));
        ctx.messaging().sendMessageToActionBar(targetId, LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.GRAY + "Щось притягує вас до певної точці..."));

        // Стан активності
        final boolean[] isActive = {true};

        // Заборона сідати в транспорт
        ctx.events().subscribeToTemporaryEvent(targetId,
                VehicleEnterEvent.class,
                e -> e.getEntered().getUniqueId().equals(targetId) && isActive[0],
                e -> {
                    e.setCancelled(true);
                    ctx.messaging().sendMessageToActionBar(targetId, LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.ITALIC + "Імператив змушує вас йти пішки..."));
                },
                DURATION_TICKS
        );

        // Запускаємо цикл навігації
        startNavigationLoop(ctx, targetId, destination, isActive);
    }

    /**
     * Цикл навігації - ФІЗИЧНО притягує гравця до точки
     */
    private void startNavigationLoop(IAbilityContext ctx, UUID targetId, Location destination, boolean[] isActive) {
        final int CHECK_INTERVAL = 5;
        final double COMPLETION_RADIUS = 2.0;
        final double PULL_STRENGTH = 0.3;
        final double MIN_DISTANCE_FOR_PULL = 3.0;

        final int[] lastDistanceBracket = {-1};
        final boolean[] lastSlownessApplied = {false};

        ctx.scheduling().scheduleRepeating(new Runnable() {
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
                        ctx.messaging().sendMessageToActionBar(targetId, LegacyComponentSerializer.legacySection().deserialize(
                                ChatColor.YELLOW + "✓ Імператив згас..."));
                        ctx.effects().playSoundForPlayer(targetId, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.8f);
                        ctx.entity().removePotionEffect(targetId, PotionEffectType.SLOWNESS);
                    }
                    return;
                }

                if (!ctx.playerData().isOnline(targetId)) {
                    isActive[0] = false;
                    return;
                }

                drawTargetMarker(ctx, destination);
                Location currentLoc = ctx.playerData().getCurrentLocation(targetId);
                double distance = currentLoc.distance(destination);

                if (distance <= COMPLETION_RADIUS) {
                    if (isActive[0]) {
                        isActive[0] = false;
                        ctx.messaging().sendMessageToActionBar(targetId, LegacyComponentSerializer.legacySection().deserialize(
                                ChatColor.GREEN + "✓ Ви досягли пункту призначення"));
                        ctx.effects().spawnParticle(Particle.TOTEM_OF_UNDYING, currentLoc, 20, 0.5, 1, 0.5);
                        ctx.effects().playSoundForPlayer(targetId, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                        ctx.entity().removePotionEffect(targetId, PotionEffectType.SLOWNESS);
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
                    Vector currentVelocity = ctx.playerData().getVelocity(targetId);
                    pullVelocity.setY(currentVelocity.getY());

                    ctx.entity().setVelocity(targetId, pullVelocity);

                    ctx.effects().spawnParticle(Particle.WITCH, currentLoc.clone().add(0, 1, 0), 2, 0.2, 0.2, 0.2);
                    ctx.effects().spawnParticle(Particle.END_ROD,
                            currentLoc.clone().add(direction.multiply(1.5)).add(0, 1, 0),
                            1, 0.1, 0.1, 0.1);
                }

                if (ticksPassed % 80 == 0 && !lastSlownessApplied[0]) {
                    ctx.entity().applyPotionEffect(targetId, PotionEffectType.SLOWNESS, 100, 0);
                    lastSlownessApplied[0] = true;
                } else if (ticksPassed % 80 != 0) {
                    lastSlownessApplied[0] = false;
                }

                int currentBracket = (int) (distance / 10);

                if (ticksPassed % 40 == 0 && currentBracket != lastDistanceBracket[0]) {
                    lastDistanceBracket[0] = currentBracket;

                    Vector direction = destination.toVector().subtract(currentLoc.toVector()).normalize();
                    Location particleLoc = currentLoc.clone().add(direction.multiply(2)).add(0, 1.5, 0);

                    ctx.effects().spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 8, 0.2, 0.2, 0.2);
                    ctx.effects().playSound(currentLoc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.5f);
                    ctx.effects().playSound(currentLoc, Sound.ENTITY_VEX_AMBIENT, 0.2f, 0.8f);

                    String arrow = getDirectionArrow(currentLoc, destination);
                    ctx.messaging().sendMessageToActionBar(targetId, LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.DARK_PURPLE + arrow + " " + ChatColor.GRAY +
                                    "Імператив тягне вас... (" + (int) distance + "м)"));
                }

                ticksPassed += CHECK_INTERVAL;
            }
        }, 0, CHECK_INTERVAL);
    }

    private void drawTargetMarker(IAbilityContext ctx, Location destination) {
        Location floorLoc = destination.clone().subtract(0, 0.8, 0);

        for (double y = 0; y < 2.5; y += 0.8) {
            ctx.effects().spawnParticle(Particle.END_ROD, floorLoc.clone().add(0, y, 0), 1, 0, 0, 0);
        }

        for (int degree = 0; degree < 360; degree += 45) {
            double radians = Math.toRadians(degree);
            double x = Math.cos(radians) * 0.7;
            double z = Math.sin(radians) * 0.7;

            Location point = floorLoc.clone().add(x, 0.1, z);
            ctx.effects().spawnParticle(Particle.WITCH, point, 1, 0, 0, 0);
        }

        ctx.effects().playAlertHalo(floorLoc.clone().add(0, 0.8, 0), Color.RED);
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
    private void applyCue(IAbilityContext ctx, UUID targetId, PsychicCueType type) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();
        UUID casterId = ctx.getCasterId();

        // КРИТИЧНО: Споживаємо ресурси ТІЛЬКИ ЗАРАЗ, коли сигнал застосовується
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.messaging().sendMessageToActionBar(ctx.getCasterId(), Component.text("Недостатньо духовності!").color(NamedTextColor.RED));
            return;
        }
        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        switch (type) {
            case HUNGER -> ctx.events().subscribeToTemporaryEvent(targetId,
                    PlayerItemConsumeEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(targetId),
                    e -> {
                        e.setCancelled(true);
                        ctx.messaging().sendMessageToActionBar(targetId, Component.text("Їжа викликає огиду..."));
                    },
                    DURATION_TICKS
            );

            case SLOTH -> {
                // 1. Механічне скидання спринту
                ctx.entity().setSprinting(targetId, false);

                // 2. Накладання ефекту сповільнення (рівень 5+ повністю блокує можливість спринту в клієнті)
                // Це найнадійніший спосіб, який не залежить від пінгів
                ctx.entity().applyPotionEffect(targetId, PotionEffectType.SLOWNESS, DURATION_TICKS, 4);

                // 3. Блокування спроби почати біг (для вірності)
                ctx.events().subscribeToTemporaryEvent(casterId,
                        PlayerToggleSprintEvent.class,
                        e -> e.getPlayer().getUniqueId().equals(targetId) && e.isSprinting(),
                        e -> {
                            e.setCancelled(true);
                            ctx.messaging().sendMessageToActionBar(targetId, Component.text("Ноги занадто важкі..."));
                        },
                        DURATION_TICKS
                );
            }

            case PACIFISM -> ctx.events().subscribeToTemporaryEvent(targetId,
                    EntityDamageByEntityEvent.class,
                    e -> e.getDamager().getUniqueId().equals(targetId),
                    e -> {
                        e.setCancelled(true);
                        ctx.messaging().sendMessageToActionBar(targetId, Component.text("Агресія покинула вас..."));
                    },
                    DURATION_TICKS
            );

            case SILENCE -> {
                Optional<LivingEntity> targetOpt = ctx.targeting().getTargetedEntity(BASE_RANGE);
                LivingEntity target = targetOpt.get();
                if (target instanceof Player) {
                    ctx.cooldown().lockAbilities(targetId, DURATION_SECONDS);
                }
            }

            case APATHY -> ctx.events().subscribeToTemporaryEvent(targetId,
                    PlayerInteractEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(targetId) &&
                            e.hasBlock() &&
                            e.getAction().name().contains("RIGHT"),
                    e -> {
                        e.setCancelled(true);
                        ctx.messaging().sendMessageToActionBar(targetId, Component.text("Байдуже до всього..."));
                    },
                    DURATION_TICKS
            );
        }

        showSuccessEffects(ctx, type);
    }

    private void showSuccessEffects(IAbilityContext ctx, PsychicCueType type) {
        ctx.messaging().sendMessageToActionBar(ctx.getCasterId(), Component.text("Навіяно: " + type.getDisplayName()).color(NamedTextColor.DARK_PURPLE));
        ctx.effects().playSoundForPlayer(ctx.getCasterId(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.6f);
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
        SILENCE("Тиша", "Заблокувати здібності (15с)", Material.SCULK_SHRIEKER, ChatColor.DARK_AQUA),
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
