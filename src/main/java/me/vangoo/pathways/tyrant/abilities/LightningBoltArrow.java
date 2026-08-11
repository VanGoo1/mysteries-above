package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sequence 5: Стріла-Блискавка. Океанський Співець ще не володіє блискавкою без
 * посередника — окрім Удару Блискавки, — тож розряд треба покласти на носій: стрілу.
 * Каст «заряджає» наступні кілька пострілів: волосся здіймається, розряд сходить із
 * нього на стрілу, і та летить утричі швидше, сріблясто-біла, а при влучанні лягає
 * повноцінним ударом блискавки. Лук під час касту тримати не треба — заряд лежить на
 * стрілах, а лічильник пострілів живе в самій підписці.
 */
public class LightningBoltArrow extends ActiveAbility {

    private static final int BASE_DAMAGE = 22;
    private static final double VELOCITY_MULTIPLIER = 3.0;
    private static final int ARMED_SHOTS = 5;
    private static final int ARMING_TICKS = 1200;  // 60 с на 5 пострілів
    private static final int FLIGHT_TICKS = 200;   // скільки чекаємо влучання
    private static final int COOLDOWN = 25;
    private static final int SPIRITUALITY_COST = 120;

    @Override
    public String getName() {
        return "Стріла-Блискавка";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fБлискавка сходить із волосся на стріли: наступні §b%d §fпострілів " +
                        "протягом §b%d с §fлетять §b×%.0f §fшвидше й б'ють §c%d §fшкоди " +
                        "ударом блискавки §7(×2 по нежиті, параліч)§f.",
                ARMED_SHOTS, ARMING_TICKS / 20, VELOCITY_MULTIPLIER, damage);
    }

    @Override
    public int getSpiritualityCost() {
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }
        UUID casterId = context.getCasterId();
        Color color = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());
        int damage = scaleValue(BASE_DAMAGE, context.getCasterBeyonder().getSequence(),
                SequenceScaler.ScalingStrategy.MODERATE);
        AtomicInteger charges = new AtomicInteger(ARMED_SHOTS);

        // Лук у руці тримати не треба: заряд лягає на стріли, а не на зброю.
        context.events().subscribeToTemporaryEvent(
                casterId,
                EntityShootBowEvent.class,
                event -> charges.get() > 0
                        && event.getEntity().getUniqueId().equals(casterId)
                        && event.getProjectile() instanceof Projectile,
                event -> {
                    if (charges.decrementAndGet() < 0) return;
                    Projectile arrow = (Projectile) event.getProjectile();
                    arrow.setVelocity(arrow.getVelocity().multiply(VELOCITY_MULTIPLIER));
                    context.effects().playTrailEffect(arrow.getUniqueId(), Particle.ELECTRIC_SPARK, FLIGHT_TICKS);
                    context.effects().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.4f);
                    context.messaging().sendMessageToActionBar(casterId,
                            Component.text("⚡ Заряджених стріл: " + charges.get(), NamedTextColor.AQUA));
                    onArrowFlight(context, player, arrow, damage, color);
                },
                ARMING_TICKS
        );

        // «Волосся здіймається, розряд танцює»: спіраль розряду + гуркіт грому й морок.
        context.effects().playRisingSpiral(player.getLocation(), 2.4, 0.7, color, 30);
        context.effects().playFadingAura(player.getLocation(), color, 30);
        context.effects().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 2.5, 0), 25, 1.5, 0.5, 1.5);
        context.effects().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.3f);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("⚡ Наступні " + ARMED_SHOTS + " стріл — блискавки", NamedTextColor.AQUA));
        return AbilityResult.success();
    }

    /** Чекає влучання зарядженої стріли й кладе в точку удару повноцінний розряд. */
    private void onArrowFlight(IAbilityContext context, Player caster, Projectile arrow, int damage, Color color) {
        context.events().subscribeToTemporaryEvent(
                context.getCasterId(),
                ProjectileHitEvent.class,
                hit -> hit.getEntity().getUniqueId().equals(arrow.getUniqueId()),
                hit -> {
                    if (hit.getHitEntity() instanceof LivingEntity victim) {
                        LightningStrike.smite(context, caster, victim, damage, color);
                    } else {
                        context.effects().playLightningBolt(hit.getEntity().getLocation(), color);
                        context.effects().playExplosionRingEffect(hit.getEntity().getLocation(), 2.0,
                                Particle.DUST, new Particle.DustOptions(color, 1.4f));
                    }
                },
                FLIGHT_TICKS
        );
    }
}
