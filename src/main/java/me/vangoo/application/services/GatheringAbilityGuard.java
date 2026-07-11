package me.vangoo.application.services;

import java.util.UUID;

/**
 * Дозволяє AbilityExecutor заблокувати каст здібності учаснику зборів,
 * не залежачи напряму від GatheringService. Реалізація фіксує порушення.
 */
public interface GatheringAbilityGuard {
    /** true → здібність заблокувати (гравець зараз на зборах); фіксує порушення. */
    boolean interceptAbility(UUID playerId);
}
