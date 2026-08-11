package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.GravediggerLore;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.pathways.common.Spirits;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death Sequence 8: Спілкування з духами.
 *
 * <p>Вікі: «able to communicate with nearby Spirits, allowing them to provide the Beyonder with
 * help… can command them to do simple actions such as tightly grasping onto a target to
 * immobilize them». Могильник ще не ЧЕРПАЄ духів (це Посл. 7) — він домовляється: дух ходить
 * поруч, сам не б'ється, і лише коли могильник ударив когось — іде й тримає ту ціль.
 *
 * <p>Дві гілки касту: якщо поблизу блукає вільний дух — вербуємо його (дешевше); якщо нікого —
 * кличемо зі Світу Духів (дорожче). Каст у присіданні відпускає весь почет. Почет живий, поки
 * духів не вбили й власник онлайн — цим володіє {@link SpiritCompanionSession}.
 */
public class SpiritCommunication extends ActiveAbility {

    /** Моб із мітка-пака; дух не належить жодному шляху, тож живе у Mobs/spirits.yml. */
    private static final String SPIRIT_MOB_ID = "spirit_wandering";
    /** Надбавка за прикликання: базову ціну (вербування) знімає Beyonder сам. */
    private static final int SUMMON_SURCHARGE =
            GravediggerLore.SPIRIT_SUMMON_COST - GravediggerLore.SPIRIT_RECRUIT_COST;

    private final Map<UUID, SpiritCompanionSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Призив простого духа";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Дух кружляє над вами й сам не бʼється, " +
                        "але коли ви когось ударили, то летить та тримає ціль.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§a☠ Слуги: до %d духів\n" +
                        "§b✋ Хват: Повільність %d на %d с (кожен ваш удар поновлює)\n" +
                        "§d✦ Вербування духа поблизу (%d бл.): %d духовності\n" +
                        "§d✦ Прикликання зі Світу Духів: %d духовності\n" +
                        "§7⇩ Каст у присіданні відпускає всіх слуг",
                GravediggerLore.spiritLimit(userSequence),
                GravediggerLore.GRASP_SLOWNESS_AMPLIFIER + 1,
                GravediggerLore.graspSeconds(userSequence),
                (int) GravediggerLore.spiritRecruitRadius(userSequence),
                GravediggerLore.SPIRIT_RECRUIT_COST,
                GravediggerLore.SPIRIT_SUMMON_COST);
    }

    @Override
    public int getSpiritualityCost() {
        // Базова ціна = вербування; прикликання доплачує надбавку всередині performExecution.
        return GravediggerLore.SPIRIT_RECRUIT_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return GravediggerLore.spiritCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Player caster = context.getCasterPlayer();
        if (caster == null) {
            return AbilityResult.failure("Ви не в світі");
        }
        Sequence sequence = context.getCasterBeyonder().getSequence();

        // Присідання — відпустити почет. Нічого не коштує, кулдауну не ставить.
        if (caster.isSneaking()) {
            return releaseAll(context, casterId);
        }

        SpiritCompanionSession session = sessions.get(casterId);
        int limit = GravediggerLore.spiritLimit(sequence);
        if (session != null && session.size() >= limit) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY
                    + "✦ Більше духів вас не слухає (" + limit + "/" + limit + ")");
            return AbilityResult.deferred();
        }

        UUID spiritId = recruitNearbySpirit(context, sequence);
        boolean recruited = spiritId != null;
        if (!recruited) {
            spiritId = summonSpirit(context, caster);
            if (spiritId == null) {
                return AbilityResult.failure("Світ Духів не відповів");
            }
        }

        if (session == null) {
            session = openSession(context, casterId, sequence);
        }
        session.enroll(spiritId);

        announce(context, casterId, caster, recruited, session.size(), limit);
        return AbilityResult.success();
    }

    private AbilityResult releaseAll(IAbilityContext context, UUID casterId) {
        SpiritCompanionSession active = sessions.remove(casterId);
        if (active == null) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "✦ Вас ніхто не супроводжує");
            return AbilityResult.deferred();
        }
        active.cancel();
        return AbilityResult.deferred();
    }

    /** Найближчий вільний дух у радіусі — той, кого ще ніхто не веде. */
    private UUID recruitNearbySpirit(IAbilityContext context, Sequence sequence) {
        double radius = GravediggerLore.spiritRecruitRadius(sequence);
        Location casterLocation = context.getCasterLocation();

        LivingEntity nearest = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (LivingEntity candidate : context.targeting().getNearbyEntities(radius)) {
            if (!Spirits.isSpirit(candidate)) continue;
            if (isAlreadyLed(candidate.getUniqueId())) continue;

            double distanceSquared = candidate.getLocation().distanceSquared(casterLocation);
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                nearest = candidate;
            }
        }
        if (nearest == null) return null;

        context.effects().playRisingSpiral(nearest.getLocation(), 2.0, 0.6, liquidColor(), 30);
        context.effects().playCircleEffect(nearest.getLocation(), 1.0, Particle.SOUL, 20);
        return nearest.getUniqueId();
    }

    /**
     * Кличе духа зі Світу Духів: списує надбавку понад базову ціну й спавнить моба з пака.
     * Дух літає, тож окремого пошуку «безпечної» точки не потрібно — з'являється при кастері.
     */
    private UUID summonSpirit(IAbilityContext context, Player caster) {
        Beyonder beyonder = context.getCasterBeyonder();
        if (beyonder.getSpirituality().current() < GravediggerLore.SPIRIT_SUMMON_COST) {
            context.messaging().sendMessage(caster.getUniqueId(), ChatColor.RED
                    + "✗ Поблизу немає духів, а на прикликання потрібно "
                    + GravediggerLore.SPIRIT_SUMMON_COST + " духовності");
            return null;
        }

        Location spot = caster.getLocation().clone().add(0, 1.0, 0);
        UUID spiritId = context.entity().summonCreature(SPIRIT_MOB_ID, spot).orElse(null);
        if (spiritId == null) return null;

        beyonder.setSpirituality(beyonder.getSpirituality().decrement(SUMMON_SURCHARGE));

        context.effects().playRisingSpiral(spot, 2.4, 0.8, liquidColor(), 40);
        context.effects().playExplosionRingEffect(caster.getLocation(), 1.6, Particle.DUST,
                new Particle.DustOptions(liquidColor(), 1.2f));
        context.effects().playSound(caster.getLocation(), Sound.BLOCK_SOUL_SOIL_BREAK, 0.9f, 0.5f);
        return spiritId;
    }

    private SpiritCompanionSession openSession(IAbilityContext context, UUID casterId, Sequence sequence) {
        SpiritCompanionSession session = new SpiritCompanionSession(
                casterId,
                GravediggerLore.graspSeconds(sequence),
                liquidColor(),
                context.events(),
                context.effects(),
                sessions);
        BukkitTask task = context.scheduling().scheduleRepeating(
                session::tick,
                SpiritCompanionSession.TICK_PERIOD_TICKS,
                SpiritCompanionSession.TICK_PERIOD_TICKS);
        session.bindTask(task);
        session.armGraspTrigger();
        sessions.put(casterId, session);
        return session;
    }

    private void announce(IAbilityContext context, UUID casterId, Player caster,
                          boolean recruited, int size, int limit) {
        context.effects().playSoundForPlayer(casterId, Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 0.7f);
        context.effects().playFadingAura(caster.getLocation(), liquidColor(), 20);
        context.messaging().sendMessage(casterId, ChatColor.DARK_GREEN + "✦ "
                + (recruited ? "Дух поблизу пристав до вас" : "Дух прийшов зі Світу Духів")
                + ChatColor.GRAY + " (" + size + "/" + limit + ")");
    }

    /** Чи цього духа вже веде хтось із могильників. */
    private boolean isAlreadyLed(UUID spiritId) {
        return sessions.values().stream().anyMatch(session -> session.owns(spiritId));
    }

    private org.bukkit.Color liquidColor() {
        return PathwayBranding.liquidOf("Death");
    }
}

// Свідомо БЕЗ cleanUp(): він кличеться на КОЖНИЙ вихід будь-якого гравця на спільному
// екземплярі здібності (PassiveAbilityManager.cleanupPlayer), тож відпустив би почет усім
// іншим. Сесія сама помічає, що власник офлайн, і розсіює своїх духів наступним тактом.
