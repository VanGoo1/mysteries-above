package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.pathways.common.Spirits;
import me.vangoo.pathways.common.UndeadEntities;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Death Sequence 6: Знання (Світ Духів).
 *
 * <p>Вікі: провідник «has considerable information about the Spirit World». Знання — не
 * дія, тому пасивка й безкоштовна: досить подивитись на мертву душу, і ви впізнаєте її рід,
 * стан і слабке місце. Живого вона мовчки ігнорує — Світ Духів про живих нічого не каже.
 *
 * <p>Свідомо вужча за {@link UndeadKnowledge} (Посл. 9): та — активна, б'є мітку й читає
 * місце смерті; ця лише називає те, на що ви дивитесь.
 */
public class SpiritWorldKnowledge extends PermanentPassiveAbility {

    /** Раз на секунду: рядок в actionbar живе ~2 с, частіше оновлювати нема сенсу. */
    private static final int SCAN_PERIOD_TICKS = 20;
    private static final double LOOK_RANGE = 16.0;

    /** Рід мертвої душі: як зветься й чим її беруть. */
    private record Kind(String name, String weakness) {}

    @Override
    public String getName() {
        return "Знання Світу Духів";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Ви знаєте Світ Духів: кожна мертва душа сама називає вам себе.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§f✦ Погляд на нежить, духа чи одержимого (до %d бл) показує\n" +
                        "  §7його рід, здоров'я і слабке місце\n",
                (int) LOOK_RANGE);
    }

    @Override
    public void tick(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isValid()) return;
        if (caster.getTicksLived() % SCAN_PERIOD_TICKS != 0) return;

        Optional<LivingEntity> aimed = context.targeting().getTargetedEntity(LOOK_RANGE);
        if (aimed.isEmpty()) return;

        LivingEntity target = aimed.get();
        Kind kind = classify(context, target);
        if (kind == null) return;

        context.messaging().sendMessageToActionBar(caster.getUniqueId(), Component.text(
                String.format("%s✦ %s %s%s §7— %s%.0f ❤ §7| слабке місце: %s%s",
                        ChatColor.DARK_GREEN, kind.name(), ChatColor.WHITE, target.getName(),
                        ChatColor.RED, target.getHealth(), ChatColor.WHITE, kind.weakness())));
    }

    /** Мертва душа й чим її беруть; живе — {@code null}, про нього Світ Духів мовчить. */
    private Kind classify(IAbilityContext context, LivingEntity target) {
        if (Spirits.isSpirit(target)) {
            return new Kind("Дух", "прикликання й накази провідника");
        }
        // getCategory() на 1.21+ кидає UnsupportedOperationException — нежить лише тегом.
        if (UndeadEntities.isUndead(target.getType())) {
            return new Kind("Нежить", "сонячне світло й освячена сталь");
        }
        if (target instanceof Player player && context.rampage().isInRampage(player.getUniqueId())) {
            return new Kind("Одержимий", "розсудок: заспокоєння або смерть");
        }
        return null;
    }
}
