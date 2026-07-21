package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.IllusionKind;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sequence 7: Magician — Illusion Creation (Створення ілюзій).
 *
 * <p>Обраний режим виконується звичайним ПКМ, а Shift+ПКМ лише <b>перемикає</b>
 * режим (без витрат). Режими — три локальні ілюзії ({@link IllusionKind}: вибух/
 * кроки/пожежа — звук + партикли + дим у точці погляду) та проєкція фальшивого
 * звуку «нізвідки» на гравця-ціль для дезорієнтації.
 */
public class IllusionCreation extends ActiveAbility {

    private static final int BASE_COST = 30;
    private static final int BASE_COOLDOWN = 5;
    private static final double LOOK_RANGE = 20.0;
    private static final double TARGET_RANGE = 20.0;

    // Режими: 0..N-1 — локальні ілюзії IllusionKind, останній — проєкція фальшивого звуку.
    private static final int MODE_COUNT = IllusionKind.values().length + 1;
    private static final int SOUND_MODE = IllusionKind.values().length;

    private final Map<UUID, Integer> selectedMode = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Створення ілюзій";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "ПКМ — виконати обраний режим ілюзії. Shift+ПКМ — перемкнути режим: " +
                "локальна ілюзія в точці погляду (вибух/кроки/пожежа: звук, колір, дим, " +
                "які інші приймають за справжні) або фальшивий звук «нізвідки» на гравця-ціль " +
                "для дезорієнтації.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        // Shift+ПКМ — безкоштовне перемикання режиму (deferred → без витрат і кулдауну).
        if (context.playerData().isSneaking(casterId)) {
            cycleMode(context, casterId);
            return AbilityResult.deferred();
        }
        int mode = selectedMode.getOrDefault(casterId, 0);
        if (mode == SOUND_MODE) {
            return soundProjection(context, casterId);
        }
        return localIllusion(context, casterId, IllusionKind.values()[mode]);
    }

    private void cycleMode(IAbilityContext context, UUID casterId) {
        int next = Math.floorMod(selectedMode.getOrDefault(casterId, 0) + 1, MODE_COUNT);
        selectedMode.put(casterId, next);
        context.effects().playSoundForPlayer(casterId, Sound.UI_BUTTON_CLICK, 0.7f, 1.5f);
        context.messaging().sendMessageToActionBar(casterId,
                net.kyori.adventure.text.Component.text("🎭 Режим: " + modeName(next)));
    }

    private String modeName(int mode) {
        if (mode == SOUND_MODE) return "Фальшивий звук (на ціль)";
        return IllusionKind.values()[mode].displayName();
    }

    private AbilityResult localIllusion(IAbilityContext context, UUID casterId, IllusionKind kind) {
        Player player = context.getCasterPlayer();
        if (player == null) return AbilityResult.failure("Гравець недоступний");

        Location target = illusionLocation(player);
        World world = target.getWorld();
        if (world == null) return AbilityResult.failure("Немає світу");

        switch (kind) {
            case EXPLOSION -> {
                world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 1.0f);
                world.spawnParticle(Particle.EXPLOSION, target, 3, 0.4, 0.4, 0.4);
                world.spawnParticle(Particle.LARGE_SMOKE, target, 30, 1.0, 1.0, 1.0);
            }
            case FOOTSTEPS -> {
                world.playSound(target, Sound.BLOCK_GRAVEL_STEP, 1.2f, 1.0f);
                world.playSound(target, Sound.BLOCK_GRAVEL_STEP, 1.2f, 0.9f);
                world.spawnParticle(Particle.ASH, target, 10, 0.3, 0.1, 0.3);
            }
            case FIRE -> {
                world.playSound(target, Sound.BLOCK_FIRE_AMBIENT, 2.0f, 1.0f);
                world.spawnParticle(Particle.FLAME, target, 40, 0.6, 0.6, 0.6, 0.02);
                world.spawnParticle(Particle.LARGE_SMOKE, target, 15, 0.5, 0.6, 0.5);
            }
        }

        context.messaging().sendMessageToActionBar(casterId,
                net.kyori.adventure.text.Component.text("🎭 " + kind.displayName()));
        return AbilityResult.success();
    }

    private AbilityResult soundProjection(IAbilityContext context, UUID casterId) {
        Optional<Player> targetOpt = context.targeting().getTargetedPlayer(TARGET_RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Немає гравця-цілі в радіусі " + (int) TARGET_RANGE + " блоків");
        }
        Player target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        // Звук лунає для цілі з випадкового боку — ніби «нізвідки».
        Location around = target.getLocation().add(
                ThreadLocalRandom.current().nextDouble(-6, 6), 0,
                ThreadLocalRandom.current().nextDouble(-6, 6));
        // playSoundForPlayer прив'язаний до позиції гравця-приймача; імітуємо напрям звуком.
        context.effects().playSoundForPlayer(targetId, Sound.ENTITY_ZOMBIE_AMBIENT, 1.5f, 1.0f);
        context.effects().playSoundForPlayer(targetId, Sound.BLOCK_GRAVEL_STEP, 1.2f, 0.9f);

        context.messaging().sendMessageToActionBar(casterId,
                net.kyori.adventure.text.Component.text("🎭 Ви послали фальшивий звук " + target.getName()));
        return AbilityResult.success();
    }

    private Location illusionLocation(Player player) {
        Block target = player.getTargetBlockExact((int) LOOK_RANGE);
        if (target != null) {
            return target.getLocation().add(0.5, 1, 0.5);
        }
        return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(8));
    }

    @Override
    public void cleanUp() {
        selectedMode.clear();
    }
}
