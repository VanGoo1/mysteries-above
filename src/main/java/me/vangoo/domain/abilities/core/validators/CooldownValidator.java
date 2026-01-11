package me.vangoo.domain.abilities.core.validators;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityType;
import me.vangoo.domain.abilities.core.BaseAbilityValidator;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;

import java.util.Optional;

/**
 * Validates that the ability is not on cooldown.
 */
public class CooldownValidator extends BaseAbilityValidator {

    @Override
    protected Optional<String> doValidate(Ability ability, Beyonder beyonder, IAbilityContext context) {
        if (ability.getType() == AbilityType.ACTIVE && context.hasCooldown(ability)) {
            return Optional.of("Cooldown: " + context.getRemainingCooldownSeconds(ability) + "с");
        }
        return Optional.empty();
    }
}
