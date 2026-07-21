package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.WallClimbRules;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 8: Clown — Clown Agility (Спритність клоуна).
 *
 * <p>Реворк: прибрано Стрибок +1; лишилась Швидкість. Додано акробатику:
 * повний імунітет до шкоди від падіння та лазіння по стінах (притиснувшись до
 * вертикальної стіни в присіді, клоун повільно дереться вгору). Швидкість
 * лазіння — {@link WallClimbRules}.
 */
public class ClownAgility extends PermanentPassiveAbility {

    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    // Підписка під ВЛАСНИМ ключем (не casterId) — щоб unsubscribeAll іншої здібності її не стер.
    private final Map<UUID, UUID> fallSubscriptions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Спритність клоуна";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Надзвичайна спритність акробата. Швидкість +1. Повний імунітет до " +
                "шкоди від падіння. Притиснувшись до стіни в присіді — дертеся вгору.";
    }

    @Override
    public AbilityIdentity getIdentity() {
        return AbilityIdentity.of("fool_agility");
    }

    @Override
    public void onActivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.put(casterId, 0);

        // Повний імунітет до шкоди від падіння — власна підписка під окремим ключем
        // (патерн SpiritualIntuition/TravellersDoor: unsubscribeAll(ключ) чіпає лише її).
        UUID subKey = UUID.randomUUID();
        fallSubscriptions.put(casterId, subKey);
        context.events().subscribeToTemporaryEvent(subKey,
                EntityDamageEvent.class,
                e -> e.getEntity().getUniqueId().equals(casterId)
                        && e.getCause() == EntityDamageEvent.DamageCause.FALL,
                e -> {
                    e.setCancelled(true);
                    Location loc = e.getEntity().getLocation();
                    context.effects().spawnParticle(Particle.CLOUD, loc, 8, 0.4, 0.1, 0.4);
                    context.effects().playSound(loc, Sound.ENTITY_HORSE_LAND, 0.8f, 1.2f);
                },
                Integer.MAX_VALUE
        );
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.remove(casterId);
        UUID subKey = fallSubscriptions.remove(casterId);
        if (subKey != null) context.events().unsubscribeAll(subKey);
        context.entity().removePotionEffect(casterId, PotionEffectType.SPEED);
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        int counter = tickCounters.getOrDefault(casterId, 0) + 1;
        tickCounters.put(casterId, counter);

        // Оновлення Швидкості кожні 2 секунди.
        if (counter % 40 == 0) {
            context.entity().applyPotionEffect(casterId, PotionEffectType.SPEED, 60, 0);
        }

        // Лазіння по стінах — кожен тік у присіді біля вертикальної стіни.
        handleWallClimb(context, casterId);
    }

    private void handleWallClimb(IAbilityContext context, UUID casterId) {
        Player player = context.getCasterPlayer();
        if (player == null) return;
        if (!player.isSneaking()) return;
        if (player.isFlying() || player.isGliding()) return;

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Не лазимо у воді/над лавою — акробатика лишень.
        if (player.isInWater()) return;

        Vector horiz = loc.getDirection().setY(0);
        if (horiz.lengthSquared() < 0.01) return;
        horiz.normalize().multiply(WallClimbRules.WALL_REACH);

        Block atFeet = loc.clone().add(horiz).getBlock();
        Block atHead = loc.clone().add(0, 1, 0).add(horiz).getBlock();
        boolean wall = atFeet.getType().isSolid() || atHead.getType().isSolid();
        if (!wall) return;

        double climb = WallClimbRules.climbSpeed(context.getCasterBeyonder().getSequence());
        Vector velocity = player.getVelocity();
        // Тримаємось стіни: гасимо падіння й піднімаємось; легкий притиск уперед.
        velocity.setY(climb);
        Vector stick = loc.getDirection().setY(0);
        if (stick.lengthSquared() > 0.01) {
            stick.normalize().multiply(0.05);
            velocity.setX(stick.getX());
            velocity.setZ(stick.getZ());
        }
        player.setVelocity(velocity);
        player.setFallDistance(0f);

        if (tickCounters.getOrDefault(casterId, 0) % 4 == 0) {
            context.effects().spawnParticle(Particle.CRIT,
                    loc.clone().add(0, 1, 0), 2, 0.1, 0.2, 0.1);
        }
    }

    @Override
    public void cleanUp() {
        tickCounters.clear();
    }
}
