package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SpiritGuideLore;
import me.vangoo.domain.valueobjects.SpiritMediumLore;
import me.vangoo.pathways.common.SpiritWorldCreatures;
import me.vangoo.pathways.common.Spirits;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death Sequence 7: Сприйняття духів.
 *
 * <p>Вікі зводить сюди три записи тіру: «Their Spiritual Perception becomes extremely strong»
 * (духів видно навіть із заплющеними очима — тобто крізь стіни), «Their Spirit Vision becomes
 * more powerful» (якісний стрибок дає саме ця пасивка) і Spirit Affinity — «those deceased and
 * natural Spirits will even take the initiative to pass on information to them», що в грі
 * читається як шепіт про небезпеку за спиною.
 *
 * <p>Плюс службова роль: саме ця пасивка ЗАПИСУЄ смерті у {@link LingeringSouls} — душа
 * висить на місці смерті 30 хв і видима лише медіуму. Голос мертвих (Посл. 7) потім її допитує.
 *
 * <p>Свідомо НЕ зливається з {@code DeathSight} (Посл. 9): та показує невидиме впритул і нічого
 * не пам'ятає, ця — духів крізь стіни, душі й шепіт. Спільного identity в них немає, вони
 * співіснують.
 *
 * <p>{@code cleanUp()} тут деструктивним бути не може: екземпляр здібності спільний на шлях,
 * а {@code cleanUp()} кличеться на вихід БУДЬ-ЯКОГО гравця — стерши реєстр, ми забрали б душі
 * в усіх медіумів сервера.
 *
 * <p>Death Sequence 5 (Воротар, T2) підсилює ту саму пасивку: коли Послідовність кастера ≤ 5,
 * тік додатково стежить, чи не зайшла у радіус нежить (Tag.ENTITY_TYPES_UNDEAD) чи істота
 * Світу Духів ({@link SpiritWorldCreatures#isSpiritWorldCreature}) — і попереджає про
 * НОВОприбулих, а не про кожного, хто вже стоїть поруч. Поведінка Посл. 7/6 (радіус, душі,
 * тіла, шепіт про небезпеку) цим не зачіпається — це окрема гілка, а не заміна існуючої.
 */
public class SpiritPerception extends PermanentPassiveAbility {

    /** Підписка живе всю сесію гравця; умову перевіряємо всередині обробника. */
    private static final int PERMANENT_DURATION = Integer.MAX_VALUE;
    /** Контур духа тримається трохи довше за період сканування — щоб не блимав. */
    private static final int GLOW_DURATION_TICKS = 30;
    /** Дух шепоче лише про те, що вже близько: половина радіуса сприйняття. */
    private static final double WHISPER_RANGE_FACTOR = 0.5;
    /** Як часто духи можуть шепнути про небезпеку (сек). Темп подачі, не сила — тому стала. */
    private static final int WHISPER_COOLDOWN_SECONDS = 10;
    /** Радіус, у якому Посл. ≤5 відчуває нежить/істот Світу Духів, що заходять іззовні. */
    private static final double UNDERWORLD_APPROACH_RADIUS = 40.0;
    /** Темп попереджень про новоприбулих (сек); тих, хто вже стояв поруч, не сповіщає повторно. */
    private static final int UNDERWORLD_APPROACH_COOLDOWN_SECONDS = 10;

    /**
     * Кастер → ВЛАСНИЙ ключ підписки (не {@code casterId}): {@code unsubscribeAll(casterId)}
     * чужої здібності стер би наш слухач смертей, і душі тихо перестали б записуватись.
     */
    private final Map<UUID, UUID> subscriptions = new ConcurrentHashMap<>();
    /** Кастер → момент, до якого духи мовчать (щоб шепіт не йшов щосекунди). */
    private final Map<UUID, Long> whisperSilentUntil = new ConcurrentHashMap<>();
    /** Кастер (Посл. ≤5) → UUID нежиті/істот Світу Духів, уже видимих у радіусі підходу. */
    private final Map<UUID, Set<UUID>> knownUnderworldIntruders = new ConcurrentHashMap<>();
    /** Кастер (Посл. ≤5) → момент, до якого попередження про новоприбулих мовчить. */
    private final Map<UUID, Long> underworldApproachSilentUntil = new ConcurrentHashMap<>();

    private final LingeringSouls souls;

    public SpiritPerception(LingeringSouls souls) {
        this.souls = souls;
    }

    @Override
    public String getName() {
        return "Сприйняття духів";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        String base = String.format(
                "Духи для вас — свої: ви відчуваєте їх навіть із заплющеними очима, " +
                        "а вони самі підказують вам про небезпеку.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§f✦ Духи світяться крізь стіни в радіусі %d блоків\n" +
                        "§f✦ Душі загиблих видно на місці смерті ще %d хв\n" +
                        "§f✦ Тіла загиблих (і мобів) видно ще %d хв\n" +
                        "§f✦ Духи шепочуть про ворога за спиною (не частіше ніж раз на %d с)\n",
                (int) SpiritMediumLore.perceptionRadius(userSequence),
                SpiritMediumLore.SOUL_TTL_SECONDS / 60,
                SpiritGuideLore.CORPSE_TTL_SECONDS / 60,
                WHISPER_COOLDOWN_SECONDS);

        if (userSequence.level() > 5) return base;

        return base + String.format(
                "\n§f✦ Ви відчуваєте нежить і істот Загробного Світу, що заходяться у радіусі " +
                        "%d блоків",
                (int) UNDERWORLD_APPROACH_RADIUS);
    }

    @Override
    public void onActivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (casterId == null) return;

        UUID subKey = UUID.randomUUID();
        subscriptions.put(casterId, subKey);

        // Реєстр — глобальний об'єкт шляху, не стан кастера, тож тримати його в замиканні можна.
        LingeringSouls registry = this.souls;
        context.events().subscribeToTemporaryEvent(
                subKey,
                PlayerDeathEvent.class,
                event -> true,
                event -> {
                    Player victim = event.getEntity();
                    registry.record(victim.getUniqueId(), victim.getName(), victim.getLocation());
                },
                PERMANENT_DURATION
        );

        // Тіла — під тим самим ключем: одна відписка знімає обидва слухачі.
        // PlayerDeathEvent теж прилітає сюди (він — EntityDeathEvent), тож гравець лишає і душу,
        // і тіло. Духи тіл не лишають: підняти дух як зомбі нічого не значить.
        context.events().subscribeToTemporaryEvent(
                subKey,
                EntityDeathEvent.class,
                // Слуга почту тіла не лишає: інакше та сама тушка підіймалась би без кінця.
                event -> !Spirits.isSpirit(event.getEntity())
                        && !UndeadRetinue.isServant(event.getEntity()),
                event -> {
                    LivingEntity victim = event.getEntity();
                    registry.recordCorpse(victim.getUniqueId(), victim.getName(),
                            victim.getLocation(), isSkeletal(victim));
                },
                PERMANENT_DURATION
        );
    }

    /** Кістяк лишає кістяк: скелети всіх мастей і гравці підіймаються скелетами, решта — зомбі. */
    private static boolean isSkeletal(LivingEntity victim) {
        return victim instanceof Player || victim instanceof AbstractSkeleton;
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (casterId == null) return;

        // Підписка без TTL: без цієї відписки кожен повторний вхід додавав би ще один слухач.
        UUID subKey = subscriptions.remove(casterId);
        if (subKey != null) context.events().unsubscribeAll(subKey);
        whisperSilentUntil.remove(casterId);
        knownUnderworldIntruders.remove(casterId);
        underworldApproachSilentUntil.remove(casterId);
    }

    @Override
    public void tick(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isValid()) return;
        // Раз на секунду життя гравця; лічильник у полі був би спільним на всіх носіїв.
        if (caster.getTicksLived() % 20 != 0) return;

        UUID casterId = caster.getUniqueId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        double radius = SpiritMediumLore.perceptionRadius(sequence);

        drawSouls(context, caster, casterId, radius);
        drawCorpses(context, caster, casterId, radius);
        revealSpirits(context, caster, casterId, radius);
        whisperAboutDanger(context, caster, casterId, radius);

        if (sequence.level() <= 5) {
            senseUnderworldApproach(context, caster, casterId);
        }
    }

    /** Душі на місцях смерті — силует бачить лише медіум. */
    private void drawSouls(IAbilityContext context, Player caster, UUID casterId, double radius) {
        var found = souls.near(caster.getLocation(), radius, casterId);
        if (found.isEmpty()) return;

        for (LingeringSouls.Soul soul : found) {
            context.effects().playSoulWisp(casterId, soul.location(),
                    PathwayBranding.liquidOf("Death"));
        }
        // Звук — рідший за малюнок: щосекундне зітхання душі втомлювало б.
        if (caster.getTicksLived() % 100 == 0) {
            context.effects().playSoundForPlayer(casterId, Sound.PARTICLE_SOUL_ESCAPE, 0.3f, 0.7f);
        }
    }

    /** Тіла на місцях смерті — низький попіл біля землі, теж лише для носія пасивки. */
    private void drawCorpses(IAbilityContext context, Player caster, UUID casterId, double radius) {
        for (LingeringSouls.Corpse corpse : souls.corpsesNear(caster.getLocation(), radius, casterId)) {
            Location ground = corpse.location().clone().add(0, 0.2, 0);
            context.effects().spawnParticleForPlayer(casterId, Particle.ASH, ground, 6, 0.4, 0.05, 0.4);
            context.effects().spawnParticleForPlayer(casterId, Particle.SMOKE, ground, 2, 0.2, 0.02, 0.2);
        }
    }

    /** Духи поблизу — контуром крізь стіни, «навіть із заплющеними очима». */
    private void revealSpirits(IAbilityContext context, Player caster, UUID casterId, double radius) {
        for (LivingEntity entity : context.targeting().getNearbyEntities(radius)) {
            if (!Spirits.isSpirit(entity)) continue;
            context.glowing().setGlowing(entity.getUniqueId(), casterId,
                    PathwayBranding.textOf("Death"), GLOW_DURATION_TICKS);
        }
    }

    /** Spirit Affinity: духи самі попереджають про ворога, що підбирається ззаду. */
    private void whisperAboutDanger(IAbilityContext context, Player caster, UUID casterId,
                                    double radius) {
        Long silentUntil = whisperSilentUntil.get(casterId);
        if (silentUntil != null && System.currentTimeMillis() < silentUntil) return;

        double whisperRange = radius * WHISPER_RANGE_FACTOR;
        LivingEntity danger = findDanger(context, caster, whisperRange);
        if (danger == null) return;

        whisperSilentUntil.put(casterId, System.currentTimeMillis()
                + WHISPER_COOLDOWN_SECONDS * 1000L);
        context.messaging().sendMessageToActionBar(casterId, Component.text(
                "Духи шепочуть: за спиною — " + danger.getName(), NamedTextColor.DARK_AQUA));
        context.effects().playSoundForPlayer(casterId, Sound.PARTICLE_SOUL_ESCAPE, 0.5f, 0.5f);
    }

    /**
     * Небезпека — це або той, хто вже веде кастера в ціль, або ворожа істота, що зайшла за спину.
     * Мирних мобів і гравців тут немає навмисно: шепіт про кожну корову за спиною — не підказка.
     */
    private LivingEntity findDanger(IAbilityContext context, Player caster, double range) {
        Location eye = caster.getEyeLocation();
        Vector facing = eye.getDirection();

        for (LivingEntity entity : context.targeting().getNearbyEntities(range)) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) continue;
            if (entity instanceof Mob mob && caster.equals(mob.getTarget())) return entity;
            if (!(entity instanceof Monster)) continue;

            Vector toEntity = entity.getLocation().toVector().subtract(eye.toVector());
            if (toEntity.lengthSquared() > 0 && facing.dot(toEntity.normalize()) < 0) return entity;
        }
        return null;
    }

    /**
     * T2 (Посл. ≤5): попереджає про нежить/істот Світу Духів, що ЩОЙНО зайшли в радіус —
     * той, хто вже стояв поруч, повторного попередження не викликає. Хто саме зайшов ПЕРШИМ
     * за цей тік — байдуже, головне подати сигнал, не спам про кожного.
     */
    private void senseUnderworldApproach(IAbilityContext context, Player caster, UUID casterId) {
        Set<UUID> known = knownUnderworldIntruders.computeIfAbsent(casterId,
                id -> ConcurrentHashMap.newKeySet());
        Set<UUID> currentlyNear = new HashSet<>();
        LivingEntity newcomer = null;

        for (LivingEntity entity : context.targeting().getNearbyEntities(UNDERWORLD_APPROACH_RADIUS)) {
            if (entity.getUniqueId().equals(casterId)) continue;
            boolean tracked = SpiritWorldCreatures.isSpiritWorldCreature(entity)
                    || Tag.ENTITY_TYPES_UNDEAD.isTagged(entity.getType());
            if (!tracked) continue;

            currentlyNear.add(entity.getUniqueId());
            if (newcomer == null && known.add(entity.getUniqueId())) {
                newcomer = entity;
            }
        }
        // Хто пішов з радіуса — забуваємо, щоб повернення знову вважалось новим підходом.
        known.retainAll(currentlyNear);

        if (newcomer == null) return;

        Long silentUntil = underworldApproachSilentUntil.get(casterId);
        if (silentUntil != null && System.currentTimeMillis() < silentUntil) return;

        underworldApproachSilentUntil.put(casterId, System.currentTimeMillis()
                + UNDERWORLD_APPROACH_COOLDOWN_SECONDS * 1000L);
        context.messaging().sendMessageToActionBar(casterId, Component.text(
                "Загробний Світ ворушиться: наближається " + newcomer.getName(),
                NamedTextColor.DARK_PURPLE));
        context.effects().playSoundForPlayer(casterId, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.5f, 0.6f);
    }
}
