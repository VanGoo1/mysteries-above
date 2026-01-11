package me.vangoo.domain.abilities.core;

import me.vangoo.domain.entities.Beyonder;

import java.util.Optional;

/**
 * Validator interface for ability execution using Chain of Responsibility pattern.
 * Each validator checks a specific condition and can pass to the next validator.
 */
public interface AbilityValidator {

    /**
     * Validate if the ability can be executed.
     *
     * @param ability The ability to validate
     * @param beyonder The beyonder attempting to use the ability
     * @param context The execution context
     * @return Optional.empty() if validation passes, Optional with error message if fails
     */
    Optional<String> validate(Ability ability, Beyonder beyonder, IAbilityContext context);

    /**
     * Set the next validator in the chain.
     *
     * @param next The next validator
     * @return This validator for chaining
     */
    AbilityValidator setNext(AbilityValidator next);

    /**
     * Get the next validator in the chain.
     *
     * @return The next validator, or null if this is the last one
     */
    AbilityValidator getNext();
}
