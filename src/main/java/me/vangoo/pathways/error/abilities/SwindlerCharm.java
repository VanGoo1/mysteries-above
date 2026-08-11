package me.vangoo.pathways.error.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SwindlerInfluence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Посл. 8: риса «Charm» — сама присутність Афериста обеззброює.
 *
 * <p>Пасивка, не активна здібність: вороже створіння з шансом не може взяти
 * кастера на приціл (залізні големи — ніколи), а селяни торгують дешевше.
 * Знижку дає ванільний «Герой селища» (репутація селянина — Paper-only API,
 * див. {@code .claude/rules/minecraft-version.md}), тож ціни рахує сам сервер
 * і діють вони лише на цього гравця.
 */
public class SwindlerCharm extends PermanentPassiveAbility {

    private static final long FEEDBACK_INTERVAL_MS = 2000;
    /** Скільки триває торгова прихильність після дотику до селянина (тіки). */
    private static final int CHARM_TRADE_TICKS = 1200;
    /** Від цієї знижки й вище дається другий рівень «Героя селища». */
    private static final double DEEP_DISCOUNT = 0.25;

    // Підписки під ВЛАСНИМ ключем (не casterId) — щоб unsubscribeAll іншої здібності їх не стер.
    private final Map<UUID, UUID> subscriptions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Шарм Афериста";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ваша чарівність обеззброює.\nВороже створіння в радіусі "
                + (int) SwindlerInfluence.CHARM_AGGRO_RANGE + " блоків з шансом "
                + percent(SwindlerInfluence.charmAggroCancelChance(userSequence))
                + "% не може взяти вас на приціл, а залізні големи лишаються байдужими.\n"
                + "Селяни торгують із вами охочіше — знижка близько "
                + percent(SwindlerInfluence.charmTradeDiscount(userSequence)) + "%.";
    }

    @Override
    public void onActivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        UUID subKey = UUID.randomUUID();
        subscriptions.put(casterId, subKey);

        context.events().subscribeToTemporaryEvent(subKey,
                EntityTargetLivingEntityEvent.class,
                e -> e.getTarget() != null && casterId.equals(e.getTarget().getUniqueId()),
                e -> charmAggro(context, casterId, e),
                Integer.MAX_VALUE
        );

        context.events().subscribeToTemporaryEvent(subKey,
                PlayerInteractEntityEvent.class,
                e -> casterId.equals(e.getPlayer().getUniqueId()) && e.getRightClicked() instanceof Villager,
                e -> charmVillager(context, casterId, (Villager) e.getRightClicked()),
                Integer.MAX_VALUE
        );
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        UUID subKey = subscriptions.remove(casterId);
        if (subKey != null) context.events().unsubscribeAll(subKey);
        lastFeedback.remove(casterId);
    }

    @Override
    public void tick(IAbilityContext context) {
        // Пасивка керується подіями — тікати нічого не треба.
    }

    private void charmAggro(IAbilityContext context, UUID casterId, EntityTargetLivingEntityEvent event) {
        Beyonder caster = context.getCasterBeyonder();
        Location casterLoc = context.getCasterLocation();
        if (caster == null || casterLoc == null) return;

        Location mobLoc = event.getEntity().getLocation();
        if (!mobLoc.getWorld().equals(casterLoc.getWorld())) return;
        double range = SwindlerInfluence.CHARM_AGGRO_RANGE;
        if (mobLoc.distanceSquared(casterLoc) > range * range) return;

        boolean golem = event.getEntity() instanceof IronGolem;
        if (!golem && ThreadLocalRandom.current().nextDouble()
                >= SwindlerInfluence.charmAggroCancelChance(caster.getSequence())) {
            return;
        }

        event.setCancelled(true);
        if (event.getEntity() instanceof Mob mob) mob.setTarget(null);
        charmFeedback(context, casterId, mobLoc);
    }

    private void charmVillager(IAbilityContext context, UUID casterId, Villager villager) {
        Beyonder caster = context.getCasterBeyonder();
        Player player = context.getCasterPlayer();
        if (caster == null || player == null) return;

        // Репутація селянина (MAJOR_POSITIVE) — Paper-only API, якого на сервері проєкту немає.
        // Ту саму роль грає ванільний «Герой селища»: знижку рахує сам сервер, ефект особистий
        // і на інших гравців не поширюється. Ціна заміни — ефект діє на ВСІХ селян, а не лише
        // на того, кого зачарували.
        int amplifier = SwindlerInfluence.charmTradeDiscount(caster.getSequence()) >= DEEP_DISCOUNT ? 1 : 0;
        PotionEffect current = player.getPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
        if (current != null && current.getAmplifier() >= amplifier
                && current.getDuration() > CHARM_TRADE_TICKS / 2) {
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE,
                CHARM_TRADE_TICKS, amplifier, false, false, true));
        charmFeedback(context, casterId, villager.getLocation());
    }

    /** Спільний відгук на спрацювання шарму — з тротлінгом, бо подій багато. */
    private void charmFeedback(IAbilityContext context, UUID casterId, Location location) {
        long now = System.currentTimeMillis();
        Long last = lastFeedback.get(casterId);
        if (last != null && now - last < FEEDBACK_INTERVAL_MS) return;
        lastFeedback.put(casterId, now);

        context.effects().playFadingAura(location, PathwayBranding.liquidOf("Error"), 20);
        context.effects().spawnParticle(Particle.HEART, location.clone().add(0, 1.6, 0), 5, 0.3, 0.3, 0.3);
        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_VILLAGER_YES, 0.6f, 1.2f);
    }

    private static int percent(double fraction) {
        return (int) Math.round(fraction * 100);
    }
}
