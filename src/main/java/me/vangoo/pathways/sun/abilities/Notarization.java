package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;
import java.util.UUID;

/**
 * Sequence 6: два режими, обрані станом присідання в момент касту (як {@code PowerProhibition}/
 * {@code Verdict}) — окремого per-caster реєстру режиму не потрібно.
 * Присідання + ціль у прицілі → Нуліфікація (глушить здібності цілі); інакше → Ампліфікація
 * (самопідсилення).
 */
public class Notarization extends ActiveAbility {

    private static final double RANGE = 15.0;
    private static final int BASE_SILENCE_SECONDS = 6;
    private static final int BASE_AMPLIFY_SECONDS = 8;
    private static final double AMPLIFY_DAMAGE_MULTIPLIER = 1.25;
    private static final int COOLDOWN = 25;

    @Override
    public String getName() {
        return "Нотаріальність";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int silence = scaleValue(BASE_SILENCE_SECONDS, sequence, SequenceScaler.ScalingStrategy.WEAK);
        int amplify = scaleValue(BASE_AMPLIFY_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fПрисівши й дивлячись на ціль — засвідчує §5Нуліфікацію§f: глушить здібності цілі " +
                        "на %d с.\nБез присідання — §6Ампліфікацію§f: удари завдають ×1.25 шкоди й " +
                        "подвоюють вашу поточну Прудкість на %d с.",
                silence, amplify
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 45;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        if (!context.playerData().isSneaking(context.getCasterId())) return Optional.empty();
        return context.targeting().getTargetedEntity(RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();

        if (context.playerData().isSneaking(casterId)) {
            return nullify(context, casterId, sequence);
        }
        return amplify(context, casterId, sequence);
    }

    private AbilityResult nullify(IAbilityContext context, UUID casterId, Sequence sequence) {
        Optional<Player> targetOpt = context.targeting().getTargetedPlayer(RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Дивіться на гравця, щоб засвідчити Нуліфікацію.");
        }

        Player target = targetOpt.get();
        UUID targetId = target.getUniqueId();
        int silenceSeconds = scaleValue(BASE_SILENCE_SECONDS, sequence, SequenceScaler.ScalingStrategy.WEAK);

        context.cooldown().lockAbilities(targetId, silenceSeconds);

        context.effects().spawnParticle(Particle.END_ROD, target.getEyeLocation(), 20, 0.4, 0.4, 0.4);
        context.effects().playSound(target.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.7f);

        context.messaging().sendMessageToActionBar(targetId,
                Component.text("Ваші здібності засвідчено Нуліфіковані на " + silenceSeconds + " с!", NamedTextColor.LIGHT_PURPLE));
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✎ Бог каже, це неефективно! ", NamedTextColor.GOLD)
                        .append(Component.text(target.getName(), NamedTextColor.YELLOW)));

        return AbilityResult.success();
    }

    private AbilityResult amplify(IAbilityContext context, UUID casterId, Sequence sequence) {
        int amplifySeconds = scaleValue(BASE_AMPLIFY_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int durationTicks = amplifySeconds * 20;

        int speedAmplifier = doubledSpeedAmplifier(casterId);
        context.entity().applyPotionEffect(casterId, PotionEffectType.SPEED, durationTicks, speedAmplifier);

        // Множник на власні ближньобойові удари (нижче) і на прямі damage()-виклики решти
        // здібностей шляху Сонця (кожна консультує context.amplification() перед ударом).
        context.amplification().amplifyDamage(casterId, AMPLIFY_DAMAGE_MULTIPLIER, amplifySeconds);

        context.events().subscribeToTemporaryEvent(casterId,
                EntityDamageByEntityEvent.class,
                event -> isAmplifiedMeleeHit(event, casterId),
                event -> event.setDamage(event.getDamage() * AMPLIFY_DAMAGE_MULTIPLIER),
                durationTicks
        );

        Location casterLoc = context.getCasterLocation();
        context.effects().spawnParticle(Particle.END_ROD, casterLoc.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3);
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✎ Бог каже, це ефективно! (" + amplifySeconds + " с)", NamedTextColor.GOLD));

        return AbilityResult.success();
    }

    private boolean isAmplifiedMeleeHit(EntityDamageByEntityEvent event, UUID casterId) {
        if (!(event.getDamager() instanceof Player attacker)) return false;
        if (!attacker.getUniqueId().equals(casterId)) return false;
        return event.getEntity() instanceof LivingEntity;
    }

    /** Подвоює поточний рівень Прудкості кастера (Прудкість 1 → 2 і т.д.); без активної — базово Прудкість 2. */
    private int doubledSpeedAmplifier(UUID casterId) {
        Player player = Bukkit.getPlayer(casterId);
        PotionEffect current = player != null ? player.getPotionEffect(PotionEffectType.SPEED) : null;
        int currentLevel = current != null ? current.getAmplifier() + 1 : 1;
        return currentLevel * 2 - 1;
    }
}
