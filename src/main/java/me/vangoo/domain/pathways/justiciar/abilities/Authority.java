package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class Authority extends ActiveAbility {

    private static final double RADIUS = 15.0;
    private static final int BUILDUP_TIME_TICKS = 120;
    private static final int SLOWNESS_DURATION_TICKS = 200;
    private static final int SLOWNESS_AMPLIFIER = 0;
    private static final double MOB_DAMAGE = 8.0; // 4 hearts

    @Override
    public String getName() {
        return "Авторитет";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Випромінюєш ауру неоспорюваного авторитету.\n" +
                "§7▪ Радіус: §f" + (int)RADIUS + " блоків\n" +
                "§7▪ Затримка: §f6 секунд\n" +
                "§7▪ Ефект: Викидання предметів + броні\n" +
                "§7▪ Дебаф: Повільність I на 10с\n" +
                "§7▪ Моби: §f8 урону (4 серця)\n" +
                "§8▸ Не чіпає мирних жителів та големів";
    }

    @Override
    public int getSpiritualityCost() {
        return 50;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 30;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        List<Player> nearbyPlayers = context.targeting().getNearbyPlayers(RADIUS);
        List<LivingEntity> nearbyEntities = context.targeting().getNearbyEntities(RADIUS);

        // Фільтруємо список
        nearbyEntities.removeIf(entity ->
                entity instanceof Player ||
                        entity instanceof ArmorStand ||
                        isFriendlyEntity(entity)
        );

        Beyonder caster = context.getCasterBeyonder();
        int casterSequence = caster.getSequenceLevel();

        showAuraActivation(context);

        if (nearbyPlayers.isEmpty() && nearbyEntities.isEmpty()) {
            context.messaging().sendMessage(
                    context.getCasterId(),
                    "§6Аура Авторитету активована! §7Поруч немає ворогів..."
            );
            return AbilityResult.success();
        }

        context.messaging().sendMessage(
                context.getCasterId(),
                "§6Аура Авторитету активована! §7Ефект через 6 секунд..."
        );

        Set<UUID> affectedPlayers = new HashSet<>();
        Set<UUID> resistedPlayers = new HashSet<>();

        scheduleBuildupEffects(context, nearbyPlayers);

        context.scheduling().scheduleDelayed(() -> {
            executeAuthorityEffect(
                    context,
                    nearbyPlayers,
                    casterSequence,
                    affectedPlayers,
                    resistedPlayers
            );

            // Атакуємо тільки ворожих мобів
            for (LivingEntity entity : nearbyEntities) {
                if (entity.isValid() && !entity.isDead()) {
                    context.entity().damage(entity.getUniqueId(), MOB_DAMAGE);
                    showMobDamageEffect(context, entity);
                }
            }

            showResultStatistics(context, affectedPlayers, resistedPlayers, nearbyEntities.size());

        }, BUILDUP_TIME_TICKS);

        return AbilityResult.success();
    }

    /**
     * Перевіряє, чи є сутність мирною/нейтральною
     */
    private boolean isFriendlyEntity(LivingEntity entity) {
        // 1. ПЕРЕВІРКА НА NAMETAG (Бірка)
        // Якщо у моба є ім'я, не чіпаємо його
        if (entity.getCustomName() != null) {
            return true;
        }

        // 2. Жителі та торговці
        if (entity instanceof Villager || entity instanceof WanderingTrader) {
            return true;
        }

        // 3. Захисники
        if (entity instanceof IronGolem || entity instanceof Snowman || entity instanceof CopperGolem) {
            return true;
        }

        // 4. Коні та їздові тварини (ВАЖЛИВО: Ця перевірка має бути ПЕРЕД Tameable)
        // Включає: Horse, SkeletonHorse, ZombieHorse, Donkey, Mule, Llama, Camel
        // Ми ставимо це тут, щоб навіть дикі коні вважалися мирними.
        if (entity instanceof AbstractHorse) {
            return true;
        }

        // 5. Приручені тварини (Вовки, Коти, Папуги)
        // Якщо тварина приручена - вона мирна.
        // Якщо дика (наприклад, дикий вовк) - поверне false і отримає урон.
        if (entity instanceof Tameable) {
            return ((Tameable) entity).isTamed();
        }

        // 6. Мирні тварини (Корови, Свині, Курки, Черепахи тощо)
        // Увага: Вовки теж Animals, але вони перехоплюються вище в Tameable.
        // Тому сюди дійдуть тільки ті Animals, які не Tameable (тобто справді мирна худоба).
        if (entity instanceof Animals) {
            return true;
        }

        return false;
    }

    private void scheduleBuildupEffects(IAbilityContext context, List<Player> targets) {
        context.scheduling().scheduleDelayed(() -> {
            showBuildupWarning(context, targets, 1);
        }, 40L);

        context.scheduling().scheduleDelayed(() -> {
            showBuildupWarning(context, targets, 2);
        }, 80L);

        context.scheduling().scheduleDelayed(() -> {
            showBuildupWarning(context, targets, 3);
        }, 110L);
    }

    private void showBuildupWarning(IAbilityContext context, List<Player> targets, int stage) {
        for (Player target : targets) {
            if (!target.isOnline()) continue;

            Location loc = target.getLocation().add(0, 1, 0);

            int particleCount = stage * 10;
            float soundPitch = 0.5f + (stage * 0.3f);

            context.effects().spawnParticle(
                    Particle.ENCHANT,
                    loc,
                    particleCount,
                    0.5, 0.5, 0.5
            );

            context.effects().spawnParticle(
                    Particle.END_ROD,
                    loc,
                    stage * 3,
                    0.3, 0.3, 0.3
            );

            context.effects().playSound(
                    loc,
                    Sound.BLOCK_BELL_USE,
                    0.5f,
                    soundPitch
            );

            Component warning = switch (stage) {
                case 1 -> Component.text("⚠ Відчуваєш тиск авторитету...", NamedTextColor.YELLOW);
                case 2 -> Component.text("⚠⚠ Важко опиратися...", NamedTextColor.GOLD);
                case 3 -> Component.text("⚠⚠⚠ НЕМОЖЛИВО ОПИРАТИСЯ!", NamedTextColor.RED);
                default -> Component.empty();
            };

            context.messaging().sendMessageToActionBar(target.getUniqueId(), warning);
        }
    }

    private void executeAuthorityEffect(
            IAbilityContext context,
            List<Player> targets,
            int casterSequence,
            Set<UUID> affectedPlayers,
            Set<UUID> resistedPlayers
    ) {
        for (Player target : targets) {
            if (!target.isOnline()) continue;

            UUID targetId = target.getUniqueId();

            Beyonder targetBeyonder = context.beyonder().getBeyonder(targetId);

            if (targetBeyonder != null) {
                int targetSequence = targetBeyonder.getSequenceLevel();

                SequenceBasedSuccessChance successChance =
                        new SequenceBasedSuccessChance(casterSequence, targetSequence);

                if (!successChance.rollSuccess()) {
                    resistedPlayers.add(targetId);
                    showResistanceEffect(context, target, successChance);
                    continue;
                }
            }

            applyAuthorityEffect(context, target);
            affectedPlayers.add(targetId);
        }
    }

    private void applyAuthorityEffect(IAbilityContext context, Player target) {
        UUID targetId = target.getUniqueId();
        Location dropLocation = target.getLocation().add(0, 1.5, 0);

        // Викинути предмети з рук
        ItemStack mainHand = target.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.AIR) {
            context.entity().dropItem(targetId, mainHand.clone());
            target.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        ItemStack offHand = target.getInventory().getItemInOffHand();
        if (offHand.getType() != Material.AIR) {
            context.entity().dropItem(targetId, offHand.clone());
            target.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }

        removeAndDropArmor(context, target, dropLocation);

        // Накласти ефект повільності
        context.entity().applyPotionEffect(
                targetId,
                PotionEffectType.SLOWNESS,
                SLOWNESS_DURATION_TICKS,
                SLOWNESS_AMPLIFIER
        );

        showAuthorityEffect(context, target);

        context.messaging().sendMessage(
                targetId,
                "§cТи не можеш опиратися цьому авторитету!"
        );
    }

    private void removeAndDropArmor(
            IAbilityContext context,
            Player target,
            Location dropLocation
    ) {
        UUID targetId = target.getUniqueId();

        ItemStack helmet = target.getInventory().getHelmet();
        ItemStack chestplate = target.getInventory().getChestplate();
        ItemStack leggings = target.getInventory().getLeggings();
        ItemStack boots = target.getInventory().getBoots();

        target.getInventory().setHelmet(null);
        target.getInventory().setChestplate(null);
        target.getInventory().setLeggings(null);
        target.getInventory().setBoots(null);

        List<ItemStack> armorPieces = Arrays.asList(helmet, chestplate, leggings, boots);

        for (ItemStack armor : armorPieces) {
            if (armor == null || armor.getType() == Material.AIR) {
                continue;
            }

            // Спробувати додати в інвентар, якщо не вмістилося - викинути
            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(armor);

            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    context.entity().dropItem(targetId, drop);
                }
            }
        }
    }

    private void showAuthorityEffect(IAbilityContext context, Player target) {
        Location loc = target.getLocation().add(0, 1, 0);

        context.effects().spawnParticle(
                Particle.EXPLOSION,
                loc,
                1,
                0, 0, 0
        );

        context.effects().spawnParticle(
                Particle.CRIT,
                loc,
                30,
                0.5, 1.0, 0.5
        );

        context.effects().spawnParticle(
                Particle.END_ROD,
                loc,
                20,
                0.3, 0.5, 0.3
        );

        context.effects().playSound(
                loc,
                Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                0.5f,
                1.2f
        );

        context.effects().playSound(
                loc,
                Sound.ENTITY_WITHER_BREAK_BLOCK,
                0.7f,
                0.8f
        );
    }

    private void showMobDamageEffect(IAbilityContext context, LivingEntity entity) {
        Location loc = entity.getLocation().add(0, 1, 0);

        context.effects().spawnParticle(
                Particle.DAMAGE_INDICATOR,
                loc,
                15,
                0.3, 0.5, 0.3
        );

        context.effects().spawnParticle(
                Particle.CRIT,
                loc,
                10,
                0.2, 0.3, 0.2
        );

        context.effects().playSound(
                loc,
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                0.8f,
                0.9f
        );
    }

    private void showResistanceEffect(
            IAbilityContext context,
            Player target,
            SequenceBasedSuccessChance successChance
    ) {
        Location loc = target.getLocation().add(0, 1, 0);

        context.effects().spawnParticle(
                Particle.FIREWORK,
                loc,
                20,
                0.5, 0.5, 0.5
        );

        context.effects().playSound(
                loc,
                Sound.ITEM_SHIELD_BLOCK,
                1.0f,
                1.2f
        );

        context.messaging().sendMessage(
                target.getUniqueId(),
                "§aТи зміг опиратися! §7(Шанс: " + successChance.getFormattedChance() + ")"
        );
    }

    private void showAuraActivation(IAbilityContext context) {
        Location centerLoc = context.getCasterLocation().clone();

        context.effects().playSphereEffect(
                centerLoc.add(0, 1, 0),
                RADIUS,
                Particle.ENCHANT,
                60
        );

        context.effects().playWaveEffect(
                centerLoc.clone(),
                RADIUS,
                Particle.GLOW,
                40
        );

        context.effects().playLineEffect(
                centerLoc.clone(),
                centerLoc.clone().add(0, 5, 0),
                Particle.END_ROD
        );

        context.effects().playSoundForPlayer(
                context.getCasterId(),
                Sound.ENTITY_EVOKER_PREPARE_ATTACK,
                1.0f,
                0.8f
        );

        context.effects().playSoundForPlayer(
                context.getCasterId(),
                Sound.BLOCK_BEACON_ACTIVATE,
                0.8f,
                1.2f
        );

        context.effects().playCircleEffect(
                centerLoc.clone(),
                RADIUS,
                Particle.SOUL_FIRE_FLAME,
                80
        );
    }

    private void showResultStatistics(
            IAbilityContext context,
            Set<UUID> affectedPlayers,
            Set<UUID> resistedPlayers,
            int affectedMobs
    ) {
        int total = affectedPlayers.size() + resistedPlayers.size() + affectedMobs;

        if (total == 0) {
            context.messaging().sendMessage(
                    context.getCasterId(),
                    "§eВсі цілі покинули радіус дії"
            );
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("§6═══ Результат Авторитету ═══\n");

        if (!affectedPlayers.isEmpty()) {
            message.append("§a✓ Гравці підкорено: §f")
                    .append(affectedPlayers.size())
                    .append("\n");
        }

        if (!resistedPlayers.isEmpty()) {
            message.append("§c✗ Гравці опирались: §f")
                    .append(resistedPlayers.size())
                    .append("\n");
        }

        if (affectedMobs > 0) {
            message.append("§e⚔ Моби атаковано: §f")
                    .append(affectedMobs)
                    .append("\n");
        }

        int successRate = (int)((affectedPlayers.size() * 100.0) /
                Math.max(1, affectedPlayers.size() + resistedPlayers.size()));
        message.append("§7Успішність: §e")
                .append(successRate)
                .append("%");

        context.messaging().sendMessage(context.getCasterId(), message.toString());

        Location casterLoc = context.getCasterLocation();

        if (successRate >= 70 || affectedMobs >= 3) {
            context.effects().spawnParticle(
                    Particle.TOTEM_OF_UNDYING,
                    casterLoc.clone().add(0, 2, 0),
                    30,
                    0.5, 0.5, 0.5
            );
            context.effects().playSoundForPlayer(
                    context.getCasterId(),
                    Sound.ENTITY_PLAYER_LEVELUP,
                    1.0f,
                    1.2f
            );
        } else if (successRate >= 40) {
            context.effects().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    casterLoc.clone().add(0, 2, 0),
                    20,
                    0.5, 0.5, 0.5
            );
        } else {
            context.effects().spawnParticle(
                    Particle.SMOKE,
                    casterLoc.clone().add(0, 2, 0),
                    15,
                    0.5, 0.5, 0.5
            );
        }
    }
}