package me.vangoo.domain.abilities.core;

import me.vangoo.domain.entities.Beyonder;

import java.util.Optional;

/**
 * Base implementation of AbilityValidator providing chain functionality.
 */
public abstract class BaseAbilityValidator implements AbilityValidator {
    private AbilityValidator next;

    @Override
    public AbilityValidator setNext(AbilityValidator next) {
        this.next = next;
        return next;
    }

    @Override
    public AbilityValidator getNext() {
        return next;
    }

    @Override
    public Optional<String> validate(Ability ability, Beyonder beyonder, IAbilityContext context) {
        // Perform this validator's check
        Optional<String> result = doValidate(ability, beyonder, context);

        // If validation fails, return the error
        if (result.isPresent()) {
            return result;
        }

        // If validation passes and there's a next validator, continue the chain
        if (next != null) {
            return next.validate(ability, beyonder, context);
        }

        // All validations passed
        return Optional.empty();
    }

    /**
     * Perform the actual validation logic.
     * Subclasses should implement their specific validation here.
     *
     * @param ability The ability to validate
     * @param beyonder The beyonder attempting to use the ability
     * @param context The execution context
     * @return Optional.empty() if validation passes, Optional with error message if fails
     */
    protected abstract Optional<String> doValidate(Ability ability, Beyonder beyonder, IAbilityContext context);
}
