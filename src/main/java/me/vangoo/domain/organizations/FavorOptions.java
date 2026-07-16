package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.List;

/**
 * Need-based прохання до куратора: що гравець МОЖЕ попросити за фавор,
 * залежить від його ситуації, ваги фавора й рангу. Головне правило спеку 6c.
 */
public final class FavorOptions {

    public enum Option { HUNT_INFO, VAULT_INTEL, RECIPE_KNOWLEDGE, INGREDIENTS, CHARACTERISTIC, CLEAR_COOLDOWN, FALSE_PAPERS }

    /**
     * @param knowsNextRecipe гравець уже знає рецепт своєї наступної послідовності
     * @param weight          вага фавора, який він готовий витратити
     * @param rank            ранг (= послідовність) гравця в ордені
     * @param orderHasIntel   орден має свіжі розвіддані бодай однієї церкви
     */
    public record Context(boolean knowsNextRecipe, TaskWeight weight, OrderRank rank,
                          boolean orderHasIntel) {}

    private FavorOptions() {}

    public static List<Option> available(Context ctx) {
        List<Option> options = new ArrayList<>();
        options.add(Option.HUNT_INFO);
        if (ctx.orderHasIntel()) {
            options.add(Option.VAULT_INTEL);
        }
        if (ctx.weight().atLeast(TaskWeight.STANDARD)) {
            if (ctx.knowsNextRecipe()) {
                options.add(Option.INGREDIENTS);
            } else {
                options.add(Option.RECIPE_KNOWLEDGE);
            }
        }
        if (ctx.weight().atLeast(TaskWeight.MAJOR)) {
            if (ctx.knowsNextRecipe() && ctx.rank().atLeast(OrderRank.TRUSTED)) {
                options.add(Option.CHARACTERISTIC);
            }
            options.add(Option.CLEAR_COOLDOWN);
            options.add(Option.FALSE_PAPERS);
        }
        return options;
    }

    /** Мінімальна вага фавора, яку списує кожне прохання. */
    public static TaskWeight requiredWeight(Option option) {
        return switch (option) {
            case HUNT_INFO, VAULT_INTEL -> TaskWeight.LIGHT;
            case RECIPE_KNOWLEDGE, INGREDIENTS -> TaskWeight.STANDARD;
            case CHARACTERISTIC, CLEAR_COOLDOWN, FALSE_PAPERS -> TaskWeight.MAJOR;
        };
    }
}
