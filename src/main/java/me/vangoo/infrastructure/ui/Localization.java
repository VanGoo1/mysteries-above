package me.vangoo.infrastructure.ui;

/**
 * Simple localization utility for UI strings
 */
public class Localization {

    public static String getAbilityFilterName(AbilityMenu.AbilityFilter filter) {
        return switch (filter) {
            case ALL -> "Всі здібності";
            case ACTIVE -> "Активні";
            case TOGGLEABLE_PASSIVE -> "Перемикаються";
            case PERMANENT_PASSIVE -> "Постійні";
        };
    }

    // Add more localization methods as needed
}