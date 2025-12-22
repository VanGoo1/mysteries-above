package me.vangoo.domain.abilities.core;

public enum AbilityType {
    /**
     * Активні здібності - вимагають явної активації через предмет
     */
    ACTIVE,

    /**
     * Пасивні здібності з перемикачем - можна вмикати/вимикати
     */
    TOGGLEABLE_PASSIVE,

    /**
     * Постійні пасивні здібності - завжди активні
     */
    PERMANENT_PASSIVE
}
