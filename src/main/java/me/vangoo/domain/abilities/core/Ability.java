package me.vangoo.domain.abilities.core;

import java.util.Objects;

public abstract class Ability {
    public abstract String getName();

    public abstract String getDescription();

    public abstract int getSpiritualityCost();

    /**
     * Cooldown in seconds
     */
    public abstract int getCooldown();

    public abstract AbilityType getType();

    public final AbilityResult execute(IAbilityContext context) {
        if (getType() == AbilityType.ACTIVE && context.hasCooldown(this)) {
            return AbilityResult.failure(
                    "Cooldown: " + context.getRemainingCooldownSeconds(this) + "с"
            );
        }
        if (!canExecute(context))
            return AbilityResult.failure("can't execute this ability");
        preExecution(context);
        AbilityResult result = performExecution(context);
        if (result.isSuccess()) {
            postExecution(context);
            if (getType() == AbilityType.ACTIVE) {
                context.setCooldown(this, getCooldown());
            }
        }
        return result;
    }

    protected boolean canExecute(IAbilityContext context) {
        return true; // Override for additional checks
    }

    protected void preExecution(IAbilityContext context) {
    }

    protected abstract AbilityResult performExecution(IAbilityContext context);

    protected void postExecution(IAbilityContext context) {
        // Override for effects after execution
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Ability other = (Ability) obj;
        return Objects.equals(getName(), other.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }

    public void cleanUp() {
        // Default: no cleanup needed
    }
}
