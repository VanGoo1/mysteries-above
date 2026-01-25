package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Appeasement extends ActiveAbility {

    public enum AppeasementMode {
        SELF("Особистий", "Лише на собі"),
        AREA("Масовий", "В радіусі на всіх");

        private final String displayName;
        private final String description;

        AppeasementMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public AppeasementMode toggle() {
            return this == SELF ? AREA : SELF;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    private static final Map<UUID, AppeasementMode> playerModes = new ConcurrentHashMap<>();

    private static final int BASE_RANGE = 4;
    private static final int COOLDOWN = 40;
    private static final int BASE_SANITY_REDUCTION = 15;
    private static final int BASE_REGEN_SECONDS = 7;

    @Override
    public String getName() {
        return "Умиротворення";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int range = scaleValue(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int regen = scaleValue(BASE_REGEN_SECONDS, sequence, SequenceScaler.ScalingStrategy.WEAK);
        int sanity = scaleValue(BASE_SANITY_REDUCTION, sequence, SequenceScaler.ScalingStrategy.WEAK);

        return String.format(
                "§fОчищає розум, знімає негативні ефекти, " +
                        "дає Регенерацію I (%d с) та зменшує Sanity на %d.\n" +
                        "§7Shift + ПКМ: Переключити режим\n" +
                        "§7ПКМ: Застосувати (Радіус: %d бл.)",
                regen, sanity, range
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 80;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        boolean isSneaking = context.playerData().isSneaking(casterId);

        if (isSneaking) {
            return switchMode(context, casterId);
        }

        AppeasementMode mode = getCurrentMode(casterId);
        return mode == AppeasementMode.SELF ? executeSelfMode(context) : executeAreaMode(context);
    }

    private AbilityResult switchMode(IAbilityContext context, UUID casterId) {
        AppeasementMode currentMode = getCurrentMode(casterId);
        AppeasementMode newMode = currentMode.toggle();
        playerModes.put(casterId, newMode);

        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);

        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text("✦ Режим Умиротворення: ", NamedTextColor.GOLD)
                        .append(Component.text(newMode.displayName + " - " + newMode.description, NamedTextColor.YELLOW))
        );

        Location casterLoc = context.getCasterLocation();
        context.effects().spawnParticle(Particle.ENCHANT, casterLoc.add(0, 1, 0), 20, 0.3, 0.5, 0.3);

        return AbilityResult.deferred();
    }

    private AbilityResult executeSelfMode(IAbilityContext context) {
        Sequence seq = context.getCasterBeyonder().getSequence();
        
        int sanityReduction = scaleValue(BASE_SANITY_REDUCTION, seq, SequenceScaler.ScalingStrategy.WEAK);
        int regenSeconds = scaleValue(BASE_REGEN_SECONDS, seq, SequenceScaler.ScalingStrategy.WEAK);
        int regenTicks = regenSeconds * 20;

        Location center = context.getCasterLocation();
        Player caster = context.getCasterPlayer();
        UUID casterId = caster.getUniqueId();

        context.effects().spawnParticle(Particle.END_ROD, center, 40, 0.5, 1.0, 0.5);
        context.effects().spawnParticle(Particle.SOUL, center, 20, 0.3, 0.8, 0.3);
        context.effects().spawnParticle(Particle.HAPPY_VILLAGER, center, 30, 0.5, 1.2, 0.5);

        context.effects().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.3f);
        context.effects().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);

        context.beyonder().updateSanityLoss(casterId, -sanityReduction);
        removeNegativeEffects(context, casterId);
        context.entity().applyPotionEffect(casterId, PotionEffectType.REGENERATION, regenTicks, 0);

        boolean selfRescued = context.rampage().rescueFromRampage(casterId, casterId);

        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text(
                        selfRescued
                                ? "✦ Розум стабілізовано"
                                : "✦ Внутрішній спокій відновлено"
                )
        );

        if (selfRescued) {
            showRescueEffects(context, caster);
        }

        return AbilityResult.success();
    }

    private AbilityResult executeAreaMode(IAbilityContext context) {
        Sequence seq = context.getCasterBeyonder().getSequence();

        int range = scaleValue(BASE_RANGE, seq, SequenceScaler.ScalingStrategy.MODERATE);
        int sanityReduction = scaleValue(BASE_SANITY_REDUCTION, seq, SequenceScaler.ScalingStrategy.WEAK);
        int regenSeconds = scaleValue(BASE_REGEN_SECONDS, seq, SequenceScaler.ScalingStrategy.WEAK);
        int regenTicks = regenSeconds * 20;

        Location center = context.getCasterLocation();
        Player caster = context.getCasterPlayer();
        UUID casterId = caster.getUniqueId();

        context.effects().spawnParticle(Particle.END_ROD, center, 80, range, 1.0, range);
        context.effects().spawnParticle(Particle.SOUL, center, 40, range * 0.7, 0.8, range * 0.7);
        context.effects().spawnParticle(Particle.HAPPY_VILLAGER, center, 60, range, 1.2, range);

        context.effects().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.3f);
        context.effects().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);

        context.beyonder().updateSanityLoss(casterId, -sanityReduction);
        removeNegativeEffects(context, casterId);
        context.entity().applyPotionEffect(casterId, PotionEffectType.REGENERATION, regenTicks, 0);

        boolean selfRescued = context.rampage().rescueFromRampage(casterId, casterId);

        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text(
                        selfRescued
                                ? "✦ Розум стабілізовано"
                                : "✦ Внутрішній спокій відновлено"
                )
        );

        if (selfRescued) {
            showRescueEffects(context, caster);
        }

        List<LivingEntity> entities = context.targeting().getNearbyEntities(range);
        int affected = 0;

        for (LivingEntity entity : entities) {
            if (entity.getUniqueId().equals(casterId)) continue;

            removeNegativeEffects(context, entity.getUniqueId());
            context.entity().applyPotionEffect(entity.getUniqueId(), PotionEffectType.REGENERATION, regenTicks, 0);

            Location loc = entity.getLocation();
            context.effects().spawnParticle(Particle.HAPPY_VILLAGER, loc.add(0, 1, 0), 12, 0.5, 0.5, 0.5);

            if (entity instanceof Player player) {
                context.beyonder().updateSanityLoss(player.getUniqueId(), -sanityReduction);

                boolean rescued = context.rampage().rescueFromRampage(casterId, player.getUniqueId());
                if (rescued) {
                    showRescueEffects(context, player);
                }

                context.messaging().sendMessageToActionBar(
                        player.getUniqueId(),
                        Component.text("✦ Розум очищено • +" + sanityReduction + " Sanity")
                );

                context.effects().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
            }

            affected++;
        }

        context.messaging().sendMessageToActionBar(
                casterId,
                Component.text("✦ Умиротворення • Цілей: " + affected)
        );

        return AbilityResult.success();
    }

    private void removeNegativeEffects(IAbilityContext context, UUID entityId) {
        PotionEffectType[] negativeEffects = {
                PotionEffectType.POISON,
                PotionEffectType.WITHER,
                PotionEffectType.SLOWNESS,
                PotionEffectType.MINING_FATIGUE,
                PotionEffectType.INSTANT_DAMAGE,
                PotionEffectType.WEAKNESS,
                PotionEffectType.HUNGER,
                PotionEffectType.NAUSEA,
                PotionEffectType.BLINDNESS,
                PotionEffectType.DARKNESS,
                PotionEffectType.LEVITATION,
                PotionEffectType.UNLUCK
        };

        for (PotionEffectType type : negativeEffects) {
            context.entity().removePotionEffect(entityId, type);
        }
    }

    private void showRescueEffects(IAbilityContext context, Player player) {
        Location loc = player.getLocation();
        context.effects().spawnParticle(Particle.END_ROD, loc.add(0, 1, 0), 25, 0.4, 0.8, 0.4);
        context.effects().spawnParticle(Particle.SOUL, loc, 20, 0.3, 0.5, 0.3);
        context.effects().playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }

    private AppeasementMode getCurrentMode(UUID playerId) {
        return playerModes.getOrDefault(playerId, AppeasementMode.SELF);
    }

    @Override
    public void cleanUp() {
        playerModes.clear();
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }
}
