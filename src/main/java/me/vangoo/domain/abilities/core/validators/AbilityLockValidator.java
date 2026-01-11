package me.vangoo.domain.abilities.core.validators;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.BaseAbilityValidator;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.application.services.AbilityLockManager;

import java.util.Optional;

/**
 * Validates that abilities are not locked for this beyonder.
 */
public class AbilityLockValidator extends BaseAbilityValidator {
    private final AbilityLockManager lockManager;

    public AbilityLockValidator(AbilityLockManager lockManager) {
        this.lockManager = lockManager;
    }

    @Override
    protected Optional<String> doValidate(Ability ability, Beyonder beyonder, IAbilityContext context) {
        if (lockManager.isLocked(beyonder.getPlayerId())) {
            return Optional.of("Здібності заблоковані!");
        }
        return Optional.empty();
    }
}
