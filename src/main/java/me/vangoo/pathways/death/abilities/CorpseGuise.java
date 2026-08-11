package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.CorpseCollectorLore;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SpiritMediumLore;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death Sequence 7: Трупна личина.
 *
 * <p>Вікі: «They can also Disguise themselves as a Zombie in order to better endure the erosion
 * of Decay, Cold, Death and other auras». Личина справжня — інші гравці бачать зомбі
 * ({@code EntityDisguiseService}), гниття/голод/мороз медіума не беруть, а нежить губить його
 * з прицілу навіть після удару. Розплата — сонце: труп горить удень.
 *
 * <p>Тогл зроблено тим самим патерном, що {@link SpiritVision} цього ж шляху (активна
 * здібність + сесія), а не {@code ToggleablePassiveAbility}: тогл-пасивка не вміє вимкнути
 * себе сама, а личина мусить спадати, щойно скінчилась духовність.
 */
public class CorpseGuise extends ActiveAbility {

    private final Map<UUID, CorpseGuiseSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Трупна личина";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Ви прикидаєтесь мерцем: інші бачать перед собою зомбі, а аури гниття й " +
                        "холоду обходять вас стороною.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§a☠ Зотління, отрута й голод не діють, мороз не намерзає\n" +
                        "§a☠ Нежить у радіусі %d блоків губить вас із прицілу навіть після удару\n" +
                        "§c☀ Під відкритим сонцем ви горите, як справжній зомбі\n",
                (int) CorpseCollectorLore.gloomRadius(userSequence),
                SpiritMediumLore.GUISE_PERIODIC_COST);
    }

    @Override
    public int getSpiritualityCost() {
        return SpiritMediumLore.GUISE_ACTIVATION_COST;
    }

    @Override
    public int getPeriodicCost() {
        return SpiritMediumLore.GUISE_PERIODIC_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return SpiritMediumLore.GUISE_COOLDOWN_SECONDS;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Тогл: зняття личини нічого не коштує й не ставить кулдаун.
        CorpseGuiseSession active = sessions.remove(casterId);
        if (active != null) {
            active.cancel();
            return AbilityResult.deferred();
        }

        CorpseGuiseSession session = new CorpseGuiseSession(
                casterId, SpiritMediumLore.GUISE_PERIODIC_COST,
                context.beyonder(), context.effects(), sessions);
        BukkitTask task = context.scheduling().scheduleRepeating(
                session::tick, CorpseGuiseSession.TICK_PERIOD_TICKS, CorpseGuiseSession.TICK_PERIOD_TICKS);
        session.bindTask(task);
        sessions.put(casterId, session);
        session.applyNow(); // маска надівається з першої ж миті, не через чверть секунди

        Player caster = context.getCasterPlayer();
        if (caster != null) {
            context.effects().playFadingAura(caster.getLocation(), PathwayBranding.liquidOf("Death"), 25);
            context.effects().playRisingSpiral(caster.getLocation(), 2.2, 0.6,
                    PathwayBranding.liquidOf("Death"), 25);
        }
        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ZOMBIE_AMBIENT, 0.7f, 0.5f);
        context.messaging().sendMessage(casterId, ChatColor.DARK_GREEN + "☠ Ви прикинулись мерцем");
        return AbilityResult.success();
    }
}

// Свідомо БЕЗ cleanUp(): він кличеться на КОЖНИЙ вихід гравця на спільному екземплярі
// здібності, тож зняв би личину всім іншим. Сесія гасне сама наступним тактом, коли власник
// офлайн, а сама маска — лише пакети: релогін повертає справжній вигляд.
