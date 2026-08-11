package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.CorpseCollectorLore;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Death Sequence 9: Бачення духів.
 *
 * <p>Вікі (запис без офіційної назви): трупозбирач «is able to directly see spiritual bodies,
 * including a portion of Evil Spirits and restless Wraiths, even WITHOUT activating their
 * Spirit Vision». Тому — постійна, безкоштовна й свідомо вужча за тогл {@code SpiritVision}:
 * показує лише невидиме, і лише впритул.
 *
 * <p>Показує «духовне тіло», а не саму людину: димок душі малюється ЛИШЕ кастеру
 * ({@code spawnParticleForPlayer}), а стан видимості цілі не чіпається. Викликати
 * {@code showPlayerToTarget} тут не можна — це скасувало б чужу невидимість назовсім
 * і почало б перетягування канату з тими здібностями, що ховають гравця щотіка
 * ({@code PsychologicalInvisibility}, {@code Flash}, {@code BattleHypnotism}).
 */
public class DeathSight extends PermanentPassiveAbility {

    /** Раз на пів секунди: димок мусить встигати за ціллю, що біжить. */
    private static final int SCAN_PERIOD_TICKS = 10;
    private static final int GLOW_DURATION_TICKS = 20;

    @Override
    public String getName() {
        return "Бачення духів";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Ваша природа сама бачить духовні тіла — без жодного напруження.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§f✦ Невидиме видає себе димком душі в радіусі %d блоків.\n",
                (int) CorpseCollectorLore.deathSightRange(userSequence));
    }

    @Override
    public void tick(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isValid()) return;
        if (caster.getTicksLived() % SCAN_PERIOD_TICKS != 0) return;

        UUID casterId = caster.getUniqueId();
        double range = CorpseCollectorLore.deathSightRange(context.getCasterBeyonder().getSequence());

        for (LivingEntity entity : context.targeting().getNearbyEntities(range)) {
            if (entity.getUniqueId().equals(casterId)) continue;
            if (!isHiddenFrom(caster, entity)) continue;

            revealSpiritBody(context, casterId, entity);
        }
    }

    /**
     * Ховається від кастера — через зілля/прапорець невидимості або через packet-приховування
     * ({@code hidePlayerFromTarget}), яке видно лише з боку глядача, звідси {@code canSee}.
     */
    private boolean isHiddenFrom(Player caster, LivingEntity entity) {
        if (entity.hasPotionEffect(PotionEffectType.INVISIBILITY) || entity.isInvisible()) {
            return true;
        }
        return entity instanceof Player target && !caster.canSee(target);
    }

    private void revealSpiritBody(IAbilityContext context, UUID casterId, LivingEntity entity) {
        Location body = entity.getEyeLocation();

        // Димок душі — тільки для кастера; працює навіть коли сутність не надіслана клієнту.
        context.effects().spawnParticleForPlayer(casterId, Particle.SOUL, body, 3, 0.15, 0.3, 0.15);
        context.effects().spawnParticleForPlayer(casterId, Particle.SCULK_SOUL,
                body.clone().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1);

        // Контур поверх димка — лише якщо клієнт узагалі знає про сутність (зілля-невидимість).
        context.glowing().setGlowing(entity.getUniqueId(), casterId,
                PathwayBranding.textOf("Death"), GLOW_DURATION_TICKS);
    }
}
