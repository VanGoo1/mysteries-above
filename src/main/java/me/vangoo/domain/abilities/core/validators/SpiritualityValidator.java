package me.vangoo.domain.abilities.core.validators;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.BaseAbilityValidator;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;

import java.util.Optional;

/**
 * Validates that the beyonder has enough spirituality to use the ability.
 */
public class SpiritualityValidator extends BaseAbilityValidator {

    @Override
    protected Optional<String> doValidate(Ability ability, Beyonder beyonder, IAbilityContext context) {
        int cost = ability.getSpiritualityCost();
        if (beyonder.getSpirituality().current() < cost) {
            return Optional.of("Недостатньо духовності!");
        }
        return Optional.empty();
    }
}
