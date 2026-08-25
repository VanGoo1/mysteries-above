package me.vangoo.application.services;

import java.util.UUID;

/**
 * Місце, де сили Потойбічного мовчать: підпільні збори, храм у кишеньковому світі тощо.
 * Дозволяє {@link AbilityExecutor} заблокувати каст, не знаючи про самі ці підсистеми.
 *
 * <p>Реалізація може не лише відповісти «ні», а й зафіксувати порушення — так робить
 * {@code GatheringService}, для якого спроба касту на зборах є проступком.
 */
public interface AbilityGuard {

    /** true → здібність заблокувати. */
    boolean interceptAbility(UUID playerId);
}
