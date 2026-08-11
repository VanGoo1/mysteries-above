package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.CorpseCollectorLore;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death Sequence 9: Духовний зір.
 *
 * <p>Вікі: трупозбирач «can use Spirit Vision to see non-physical things, such as ghosts and
 * specters… deduce a person's health and emotions». На відміну від постійного
 * {@link DeathSight} (лише невидиме, впритул, безкоштовно) — цей зір треба ВВІМКНУТИ кастом,
 * він ширший і тягне духовність щосекунди через {@link SpiritVisionSession}. Повторний каст
 * вимикає; скінчилась духовність — зір гасне сам.
 *
 * <p>Свідомо не копія тезок з інших шляхів: {@code SpiritualVision} (door) і
 * {@code SeerSpiritVision} (fool) читають ГРАВЦІВ (аура здоров'я, розкриття шляху), а тут
 * фокус на нежиті крізь стіни та на невидимому — і звіт про стан того, на кого дивишся.
 */
public class SpiritVision extends ActiveAbility {

    private static final int COOLDOWN = 3;
    /** Лише для тексту опису; саме бачення духів гейтить {@code SpiritVisionSession}. */
    private static final int SPIRIT_SIGHT_SEQUENCE = 8;

    private final Map<UUID, SpiritVisionSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Духовний зір";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        String spiritLine = userSequence.level() <= SPIRIT_SIGHT_SEQUENCE
                ? "\n§b✦ Зір поглибшав: духи теж світяться крізь стіни"
                : "";
        return String.format(
                "Ви дивитесь на світ як на кладовище: нежить світиться крізь стіни.\n" +
                        "Невидиме видає себе, а ціль під поглядом розкриває свій стан.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§a☠ Нежить і невидиме в радіусі %d блоків%s\n" +
                        "§b👁 Звіт про ціль під поглядом\n" +
                        "§d✦ Підтримка: %d духовності за секунду · повторний каст вимикає",
                (int) CorpseCollectorLore.spiritVisionRange(userSequence),
                spiritLine,
                CorpseCollectorLore.SPIRIT_VISION_PERIODIC_COST);
    }

    @Override
    public int getSpiritualityCost() {
        return CorpseCollectorLore.SPIRIT_VISION_PERIODIC_COST; // ціна першого вікна при активації
    }

    @Override
    public int getPeriodicCost() {
        return CorpseCollectorLore.SPIRIT_VISION_PERIODIC_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Тогл: повторний каст гасить зір і нічого не коштує.
        SpiritVisionSession active = sessions.remove(casterId);
        if (active != null) {
            active.cancel();
            return AbilityResult.deferred();
        }

        double range = CorpseCollectorLore.spiritVisionRange(context.getCasterBeyonder().getSequence());
        SpiritVisionSession session = new SpiritVisionSession(
                casterId, range, CorpseCollectorLore.SPIRIT_VISION_PERIODIC_COST,
                PathwayBranding.textOf("Death"), context.beyonder(), context.glowing(), sessions);
        BukkitTask task = context.scheduling().scheduleRepeating(
                session::tick, SpiritVisionSession.TICK_PERIOD_TICKS, SpiritVisionSession.TICK_PERIOD_TICKS);
        session.bindTask(task);
        sessions.put(casterId, session);
        session.applyNow(); // нежить світиться з першої ж миті, не через секунду

        Player caster = context.getCasterPlayer();
        if (caster != null) {
            context.effects().playFadingAura(caster.getLocation(), PathwayBranding.liquidOf("Death"), 20);
        }
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8f, 0.7f);
        context.messaging().sendMessage(casterId, ChatColor.DARK_GREEN + "✦ Духовний зір розкрився");
        return AbilityResult.success();
    }
}

// Свідомо БЕЗ cleanUp(): він кличеться на КОЖНИЙ вихід гравця на спільному екземплярі
// здібності, тож скасував би зір усім іншим. Сесія сама гасне наступним тактом, коли
// власник офлайн, і сама прибирає себе з реєстру; стійкого стану (ефектів, NPC) немає.
