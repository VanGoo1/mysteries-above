package me.vangoo.pathways.error.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SwindlerInfluence;
import me.vangoo.domain.entities.Beyonder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Посл. 8: «Красномовність» — кілька фраз, і ціль більше не бачить у вас ворога.
 *
 * <p>Нуль шкоди: ціль просто не може вас атакувати, а створіння ще й обертає
 * агресію на найближчого монстра. Ефект зникає тієї ж миті, коли ви б'єте самі —
 * аферист втрачає довіру, щойно піднімає руку.
 */
public class Eloquence extends ActiveAbility {

    @Override
    public String getName() {
        return "Красномовність";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ви заговорюєте ціль у радіусі " + (int) SwindlerInfluence.ELOQUENCE_RANGE
                + " блоків: близько " + SwindlerInfluence.eloquenceDurationTicks(userSequence) / 20
                + " с вона не здатна вас атакувати, а створіння б'ється за вас.\n"
                + "Ефект зникає, щойно ви вдарите першим.";
    }

    @Override
    public int getSpiritualityCost() {
        return SwindlerInfluence.ELOQUENCE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return SwindlerInfluence.ELOQUENCE_COOLDOWN_SECONDS;
    }

    @Override
    protected Optional<UUID> getSequenceCheckTarget(IAbilityContext context) {
        return context.targeting().getTargetedEntity(SwindlerInfluence.ELOQUENCE_RANGE).map(LivingEntity::getUniqueId);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(SwindlerInfluence.ELOQUENCE_RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("✖ Ви не дивитесь на живу ціль");
        }

        LivingEntity target = targetOpt.get();
        UUID casterId = context.getCasterId();
        Beyonder caster = context.getCasterBeyonder();
        int duration = SwindlerInfluence.eloquenceDurationTicks(caster.getSequence());

        // Ключ підписок — власний, не casterId: чужий unsubscribeAll не зніме умовляння.
        UUID subKey = UUID.randomUUID();

        // 1. Ціль не може дістати кастера.
        context.events().subscribeToTemporaryEvent(subKey,
                EntityDamageByEntityEvent.class,
                e -> casterId.equals(e.getEntity().getUniqueId())
                        && isFrom(e.getDamager(), target.getUniqueId()),
                e -> e.setCancelled(true),
                duration
        );

        // 2. Кастер ударив — довіра зникає разом з усіма підписками.
        context.events().subscribeToTemporaryEvent(subKey,
                EntityDamageByEntityEvent.class,
                e -> isFrom(e.getDamager(), casterId),
                e -> {
                    context.events().unsubscribeAll(subKey);
                    context.messaging().sendMessageToActionBar(casterId,
                            Component.text("✗ Ваші слова більше не тримають").color(NamedTextColor.GRAY));
                },
                duration
        );

        if (target instanceof Mob mob) {
            // 3. Створіння не бере кастера на приціл і б'ється за нього.
            context.events().subscribeToTemporaryEvent(subKey,
                    EntityTargetLivingEntityEvent.class,
                    e -> e.getEntity().getUniqueId().equals(mob.getUniqueId())
                            && e.getTarget() != null && casterId.equals(e.getTarget().getUniqueId()),
                    e -> e.setCancelled(true),
                    duration
            );
            mob.setTarget(findAlly(context, target));
        } else if (target instanceof Player victim) {
            context.effects().playSoundForPlayer(victim.getUniqueId(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f);
            context.messaging().sendMessageToActionBar(victim.getUniqueId(),
                    Component.text("🗣 Слова обеззброюють вас…").color(NamedTextColor.LIGHT_PURPLE));
        }

        playEffects(context, target);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("🗣 Ви заговорили ціль").color(NamedTextColor.LIGHT_PURPLE));
        return AbilityResult.success();
    }

    /** Найближчий монстр, на якого можна перевести агресію (крім самої цілі). */
    private LivingEntity findAlly(IAbilityContext context, LivingEntity target) {
        return context.targeting().getNearbyEntities(SwindlerInfluence.ELOQUENCE_RANGE).stream()
                .filter(e -> e instanceof Monster && !e.getUniqueId().equals(target.getUniqueId()))
                .min((a, b) -> Double.compare(
                        a.getLocation().distanceSquared(target.getLocation()),
                        b.getLocation().distanceSquared(target.getLocation())))
                .orElse(null);
    }

    /** Пряма атака сутності або її снаряд. */
    private static boolean isFrom(Entity damager, UUID sourceId) {
        if (sourceId.equals(damager.getUniqueId())) return true;
        return damager instanceof Projectile proj
                && proj.getShooter() instanceof Entity shooter
                && sourceId.equals(shooter.getUniqueId());
    }

    private void playEffects(IAbilityContext context, LivingEntity target) {
        context.effects().playHelixEffect(
                context.getCasterPlayer().getEyeLocation(),
                target.getEyeLocation(),
                Particle.ENCHANT,
                30
        );
        context.effects().playFadingAura(target.getLocation(), PathwayBranding.liquidOf("Error"), 40);
        context.effects().playSound(context.getCasterLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.3f);
        context.effects().playSound(target.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 0.9f);
    }
}
