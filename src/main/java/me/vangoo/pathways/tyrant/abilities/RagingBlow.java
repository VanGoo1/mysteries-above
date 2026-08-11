package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 8: Нищівний Удар. Страшний удар, що народжується з накопиченої люті.
 * Народ Люті мусить спершу набити «влучань» у ближньому бою; коли гнів дозрів —
 * активація вивільняє його одним фронтальним ударом, що відкидає й трощить усіх попереду.
 * Стан Люті (шал) наповнює гнів удвічі швидше.
 * <p>
 * Лічильник влучань набивається через постійну підписку на {@link EntityDamageByEntityEvent}
 * (той самий патерн, що в CombatProficiency): реєстр — instance-поле, ніколи не static;
 * слухач сам відмирає, коли гравець втрачає цю здібність ({@link #isValidUser}).
 */
public class RagingBlow extends ActiveAbility {

    private static final int THRESHOLD = 8;        // скільки «влучань» треба на удар
    private static final int BASE_DAMAGE = 8;      // шкода фронтального удару (скейлиться)
    private static final double KNOCKBACK = 1.8;
    private static final double HIT_RADIUS = 4.0;
    private static final int COOLDOWN = 10;
    private static final int SPIRITUALITY_COST = 70;
    private static final int PERMANENT_DURATION = Integer.MAX_VALUE;

    private final Set<UUID> registered = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> rage = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Нищівний Удар";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fНакопичуйте гнів, б'ючи ворогів у ближньому бою §7(%d влучань)§f.\nКоли гнів накопичиться, " +
                        "вивільніть його фронтальним ударом на §c%d §fшкоди з потужним відкиданням.\n" +
                        "§8Під час Люті гнів накопичується удвічі швидше.", THRESHOLD, damage);
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

        // Озброюємо лічильник влучань один раз на гравця.
        ensureAccumulator(context, casterId);

        int current = rage.getOrDefault(casterId, 0);
        if (current < THRESHOLD) {
            return AbilityResult.failure(String.format(
                    "§cГнів ще накопичується: §e%d/%d§c — бийте ворогів у ближньому бою.", current, THRESHOLD));
        }

        // Гнів дозрів — вивільняємо удар і скидаємо лічильник.
        rage.put(casterId, 0);
        Sequence sequence = context.getCasterBeyonder().getSequence();
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        Vector direction = player.getLocation().getDirection().normalize();

        for (Entity entity : player.getNearbyEntities(HIT_RADIUS, 2.5, HIT_RADIUS)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (toTarget.lengthSquared() < 1e-4 || direction.dot(toTarget.normalize()) < 0.25) continue;

            target.damage(damage, player);
            Vector push = direction.clone().multiply(KNOCKBACK);
            push.setY(0.5);
            target.setVelocity(push);
        }

        // Візуал: брендоване кільце-вибух + ударна хвиля + важкий гуркіт.
        Color color = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());
        Location feet = player.getLocation();
        context.effects().playExplosionRingEffect(feet, HIT_RADIUS, Particle.DUST, new Particle.DustOptions(color, 2.0f));
        context.effects().playWaveEffect(feet, HIT_RADIUS, Particle.CLOUD, 12);
        context.effects().playSound(feet, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);

        return AbilityResult.success();
    }

    /* ===================== HIT ACCUMULATION ===================== */

    private void ensureAccumulator(IAbilityContext context, UUID playerId) {
        if (!registered.add(playerId)) return; // уже підписані

        context.events().subscribeToTemporaryEvent(
                playerId,
                EntityDamageByEntityEvent.class,
                event -> event.getDamager() instanceof Player p && p.getUniqueId().equals(playerId),
                event -> onMeleeHit(context, playerId),
                PERMANENT_DURATION
        );
    }

    private void onMeleeHit(IAbilityContext context, UUID playerId) {
        if (!isValidUser(context, playerId)) return;

        Player player = context.getCasterPlayer();
        // Під час Люті (є ефект Сили від Wrath) гнів набивається удвічі швидше.
        int gain = (player != null && player.hasPotionEffect(PotionEffectType.STRENGTH)) ? 2 : 1;
        rage.merge(playerId, gain, Integer::sum);
        if (rage.get(playerId) > THRESHOLD) {
            rage.put(playerId, THRESHOLD); // не копимо понад поріг
        }
    }

    /**
     * Слухач сам відмирає, коли гравець офлайн або більше не володіє САМЕ цією інстанцією
     * здібності (зміна шляху/скидання) — точна перевірка за посиланням, як у CombatProficiency.
     */
    private boolean isValidUser(IAbilityContext context, UUID playerId) {
        if (!context.playerData().isOnline(playerId)) return false;
        Beyonder beyonder = context.beyonder().getBeyonder(playerId);
        if (beyonder == null) return false;
        return beyonder.getAbilities().stream().anyMatch(ability -> ability == this);
    }

    @Override
    public void cleanUp() {
        registered.clear();
        rage.clear();
    }
}
