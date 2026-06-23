package me.vangoo.pathways.whitetower.abilities.custom;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.spells.SpellCodec;
import me.vangoo.domain.spells.SpellRecipe;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;

/**
 * Тонка здібність-адаптер для гравцем-створеного заклинання.
 * <p>
 * Уся специфіка — у {@link SpellRecipe} (дані/правила) та {@link SpellEffectRunner} (ефекти).
 * Сам клас лише з'єднує їх із пайплайном {@code Ability}: ім'я/ціна/кулдаун беруться з рецепта,
 * виконання делегується раннеру.
 */
public final class GeneratedSpell extends ActiveAbility {

    private final SpellRecipe recipe;

    public GeneratedSpell(SpellRecipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("recipe cannot be null");
        }
        this.recipe = recipe;
    }

    @Override
    public AbilityIdentity getIdentity() {
        return AbilityIdentity.of(SpellCodec.encode(recipe));
    }

    @Override
    public String getName() {
        return recipe.name();
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return SpellCodec.describe(recipe);
    }

    @Override
    public int getSpiritualityCost() {
        return recipe.spiritualityCost();
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return recipe.cooldownSeconds();
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        return SpellEffectRunner.cast(recipe, context);
    }
}
