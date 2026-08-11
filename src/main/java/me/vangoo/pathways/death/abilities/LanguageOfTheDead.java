package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SpiritGuideLore;
import me.vangoo.pathways.common.SoulWard;
import me.vangoo.pathways.fool.abilities.MarionettistControl;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death Sequence 6: Мова мертвих.
 *
 * <p>Вікі: «By speaking a mystical language, they can urge a target's Spirit to leave a body,
 * bypassing the physical protection provided by flesh and blood to target the Spirit Body.»
 * Броні в грі не «обходять» числом — душу виймають із тіла цілком, і поки її немає, броня
 * не важить нічого. Механіку тримає {@link SoulRipSession}.
 *
 * <p>Реєстр — за ЖЕРТВОЮ, а не за кастером: двічі вирвати ту саму душу не можна, а от два
 * провідники можуть тримати двох різних жертв.
 */
public class LanguageOfTheDead extends ActiveAbility {

    /** Жертва → її вирвана душа. Інстанс-поле, ніколи не static. */
    private final Map<UUID, SoulRipSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Мова мертвих";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Ви кажете кілька слів мовою, якої не знають живі, — і душа цілі виходить із тіла.\n" +
                        "§7Поточні бонуси:\n" +
                        "§a☠ Дальність: %d блоків\n" +
                        "§b✦ Душа поза тілом: %d с (жертва лише дивиться, сили запечатані)\n" +
                        "§f⚔ Тіло вразливе; перший удар по ньому повертає душу\n" +
                        "§8(на істотах — тіло просто ціпеніє)",
                (int) SpiritGuideLore.languageRange(userSequence),
                SpiritGuideLore.languageDurationSeconds(userSequence));
    }

    @Override
    public int getSpiritualityCost() {
        return SpiritGuideLore.LANGUAGE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return SpiritGuideLore.languageCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Player caster = context.getCasterPlayer();
        if (caster == null) {
            return AbilityResult.failure("Ви не в світі");
        }

        Sequence sequence = context.getCasterBeyonder().getSequence();
        LivingEntity target = context.targeting()
                .getTargetedEntity(SpiritGuideLore.languageRange(sequence))
                .orElse(null);
        if (target == null) {
            return AbilityResult.failure("Ніхто не в прицілі");
        }
        if (target.getUniqueId().equals(casterId)) {
            return AbilityResult.failure("Власну душу не кличуть");
        }
        if (sessions.containsKey(target.getUniqueId())) {
            return AbilityResult.failure("Ця душа вже поза тілом");
        }
        // Обмін духом (той самий тір) підмінив душу слугою — кликати нема кого.
        if (SoulWard.isWarded(target)) {
            return AbilityResult.failure("Душа цілі підмінена нежиттю");
        }
        // Маріонетка «вважається мертвою» й піддається; решта NPC (жреці, посередники) — ні.
        if (CitizensAPI.hasImplementation() && CitizensAPI.getNPCRegistry().isNPC(target)
                && !MarionettistControl.isMarionetteNpc(target)) {
            return AbilityResult.failure("У цього тіла немає душі");
        }

        int duration = SpiritGuideLore.languageDurationSeconds(sequence);
        SoulRipSession session = new SoulRipSession(
                target.getUniqueId(), casterId, duration,
                PathwayBranding.liquidOf("Death"),
                context.events(), context.effects(), sessions);

        if (!session.start(target)) {
            return AbilityResult.failure("Душа не піддалась");
        }
        sessions.put(target.getUniqueId(), session);

        BukkitTask task = context.scheduling().scheduleRepeating(session::tick,
                SoulRipSession.TICK_PERIOD_TICKS, SoulRipSession.TICK_PERIOD_TICKS);
        session.bindTask(task);

        // Печатка живе рівно стільки, скільки задумана крадіжка тіла; дострокове повернення
        // її не знімає (ICooldownContext вміє лише ставити) — 5 с без сил ціна прийнятна.
        if (target instanceof Player victim) {
            context.cooldown().lockAbilities(victim.getUniqueId(), duration);
        }

        context.messaging().sendMessage(casterId, ChatColor.DARK_GREEN + "☠ Душа "
                + ChatColor.WHITE + target.getName() + ChatColor.DARK_GREEN + " вирвана з тіла"
                + ChatColor.GRAY + " (" + duration + " с)");
        return AbilityResult.success();
    }

    /**
     * Тут {@code cleanUp()} НЕ деструктивний, на відміну від почту: він повертає душі в тіла.
     * Ранній кінець на чужий вихід із гри нікому не шкодить, а от NPC-тіло, що пережило
     * вимкнення плагіна, лишилось би в Citizens назавжди разом із вічним спостерігачем.
     */
    @Override
    public void cleanUp() {
        List.copyOf(sessions.values()).forEach(SoulRipSession::end);
    }
}
