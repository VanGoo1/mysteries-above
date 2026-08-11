package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.CorpseCollectorLore;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.pathways.common.Spirits;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death Sequence 9: Похмура присутність.
 *
 * <p>Вікі: «Their gloomy presence and lower body temperature will prevent a Corpse Collector
 * from being attacked by Undead creatures and Spirits» — нежить не бачить у трупозбирачі
 * живого. Реалізовано двома шарами: скасуванням {@link EntityTargetLivingEntityEvent} (нежить
 * не бере ціль) і щосекундним зняттям УЖЕ взятої цілі поблизу (агро, набране до випитого
 * зілля, інакше висіло б вічно — подія на нього більше не спрацює).
 *
 * <p>Ретрибуція зберігається: ударив нежить — вона пам'ятає це
 * {@link CorpseCollectorLore#PROVOKE_MEMORY_SECONDS} секунд і агриться як на будь-кого.
 */
public class GloomyPresence extends PermanentPassiveAbility {

    /** Підписка живе всю сесію гравця; фактичну умову перевіряємо всередині обробника. */
    private static final int PERMANENT_DURATION = Integer.MAX_VALUE;

    /**
     * З якої Послідовності духи теж перестають нападати. Вікі Посл. 9 обіцяло «Undead creatures
     * AND Spirits», але духи — не нежить і бачать у трупозбирачі просто живого: говорити з ними
     * вміє лише могильник (Посл. 8, Спілкування з духами), і саме тому вони його не чіпають.
     */
    private static final int SPIRIT_PEACE_MAX_SEQUENCE = 8;

    /**
     * Причини таргетингу, які означають «гравець сам винен» — їх не скасовуємо.
     * {@link TargetReason#CUSTOM} лишаємо теж: так націлюють MythicMobs-скіли й наші власні
     * здібності, і глушити їх похмурою присутністю було б поламкою чужих механік.
     */
    private static final Set<TargetReason> PROVOKED_REASONS = Set.of(
            TargetReason.TARGET_ATTACKED_ENTITY,
            TargetReason.TARGET_ATTACKED_OWNER,
            TargetReason.TARGET_ATTACKED_NEARBY_ENTITY,
            TargetReason.REINFORCEMENT_TARGET,
            TargetReason.CUSTOM
    );

    /**
     * Кастер → ВЛАСНИЙ ключ підписки (не {@code casterId}): {@code unsubscribeAll(casterId)}
     * іншої здібності — а так роблять {@code AirCushion}, {@code StormHeart} і ще кілька —
     * стер би наші слухачі, і похмура присутність тихо перестала б працювати.
     */
    private final Map<UUID, UUID> subscriptions = new ConcurrentHashMap<>();
    /** Кастер → момент (мілісекунди), до якого нежить пам'ятає його удар. */
    private final Map<UUID, Long> provokedUntil = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Похмура присутність";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        String spiritLine = userSequence.level() <= SPIRIT_PEACE_MAX_SEQUENCE
                ? "\n§b✦ Духи теж бачать у вас свого й не нападають"
                : "";
        return String.format(
                "Нежить не відчуває у вас живого й не нападає першою.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§a☠ Нежить забуває вас у радіусі %d блоків%s\n" +
                        "§c⚔ Ударите першим — пам'ятає це %d с",
                (int) CorpseCollectorLore.gloomRadius(userSequence),
                spiritLine,
                CorpseCollectorLore.PROVOKE_MEMORY_SECONDS);
    }

    @Override
    public void onActivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (casterId == null) return;

        UUID subKey = UUID.randomUUID();
        subscriptions.put(casterId, subKey);
        registerEvents(context, casterId, subKey);
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (casterId == null) return;

        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) return;

        // Раз на секунду життя гравця; лічильник у полі здібності був би спільним на всіх носіїв.
        if (player.getTicksLived() % 20 != 0) return;
        if (isProvoked(casterId)) return;

        releaseLockedOn(context, player);
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (casterId == null) return;

        // Підписки не мають TTL (PERMANENT_DURATION), тож без цієї відписки кожен
        // повторний вхід гравця додавав би ще одну пару слухачів.
        UUID subKey = subscriptions.remove(casterId);
        if (subKey != null) context.events().unsubscribeAll(subKey);
        provokedUntil.remove(casterId);
    }

    private void registerEvents(IAbilityContext context, UUID casterId, UUID subKey) {
        // Глобальний сервіс, не прив'язаний до кастера — його можна тримати в замиканні,
        // на відміну від самого IAbilityContext.
        IBeyonderContext beyonderContext = context.beyonder();

        // Нежить (а з Посл. 8 і дух) намагається взяти кастера в ціль.
        context.events().subscribeToTemporaryEvent(
                subKey,
                EntityTargetLivingEntityEvent.class,
                event -> isTargetingCaster(event, casterId)
                        && ignoresCaster(beyonderContext, casterId, event.getEntity()),
                event -> {
                    if (!isProvoked(casterId) && !PROVOKED_REASONS.contains(event.getReason())) {
                        event.setCancelled(true);
                    }
                },
                PERMANENT_DURATION
        );

        // Кастер ударив нежить (чи духа) — вони мають право злитись.
        context.events().subscribeToTemporaryEvent(
                subKey,
                EntityDamageByEntityEvent.class,
                event -> event.getDamager() instanceof Player p
                        && p.getUniqueId().equals(casterId)
                        && (isUndead(event.getEntity()) || Spirits.isSpirit(event.getEntity())),
                event -> provokedUntil.put(casterId,
                        System.currentTimeMillis() + CorpseCollectorLore.PROVOKE_MEMORY_SECONDS * 1000L),
                PERMANENT_DURATION
        );
    }

    /**
     * Знімає кастера з прицілу тих, хто вже вів його (нежить завжди, духи з Посл. 8), і
     * показує це партиклом душі.
     */
    private void releaseLockedOn(IAbilityContext context, Player player) {
        double radius = CorpseCollectorLore.gloomRadius(context.getCasterBeyonder().getSequence());
        boolean released = false;

        for (LivingEntity nearby : context.targeting().getNearbyEntities(radius)) {
            if (!(nearby instanceof Mob mob)
                    || !ignoresCaster(context.beyonder(), player.getUniqueId(), mob)) continue;
            if (mob.getTarget() == null || !player.getUniqueId().equals(mob.getTarget().getUniqueId())) continue;

            mob.setTarget(null);
            context.effects().spawnParticle(Particle.SOUL,
                    mob.getEyeLocation(), 4, 0.2, 0.2, 0.2);
            released = true;
        }

        if (released) {
            context.effects().playSoundForPlayer(player.getUniqueId(),
                    Sound.PARTICLE_SOUL_ESCAPE, 0.4f, 0.6f);
        }
    }

    private boolean isTargetingCaster(EntityTargetLivingEntityEvent event, UUID casterId) {
        return event.getTarget() instanceof Player p && p.getUniqueId().equals(casterId);
    }

    /**
     * Ванільна класифікація: покриває і модовану нежить, і нашу MythicMobs на undead-базі.
     * getCategory() на 1.21+ кидає UnsupportedOperationException — тільки тег.
     */
    private boolean isUndead(org.bukkit.entity.Entity entity) {
        return entity instanceof LivingEntity && Tag.ENTITY_TYPES_UNDEAD.isTagged(entity.getType());
    }

    /**
     * Чи ця істота має «не бачити» в кастері живого: нежить — завжди, дух — лише з
     * {@link #SPIRIT_PEACE_MAX_SEQUENCE} (духи не нежить, і мовчазної присутності їм замало).
     */
    private boolean ignoresCaster(IBeyonderContext beyonderContext, UUID casterId,
                                  org.bukkit.entity.Entity entity) {
        if (isUndead(entity)) return true;
        if (!Spirits.isSpirit(entity)) return false;

        Beyonder beyonder = beyonderContext.getBeyonder(casterId);
        return beyonder != null && beyonder.getSequence().level() <= SPIRIT_PEACE_MAX_SEQUENCE;
    }

    private boolean isProvoked(UUID casterId) {
        Long until = provokedUntil.get(casterId);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) return true;

        provokedUntil.remove(casterId);
        return false;
    }
}
