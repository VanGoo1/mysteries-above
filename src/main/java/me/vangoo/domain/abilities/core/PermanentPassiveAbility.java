package me.vangoo.domain.abilities.core;

public abstract class PermanentPassiveAbility extends Ability {
    @Override
    public final AbilityType getType() {
        return AbilityType.PERMANENT_PASSIVE;
    }

    @Override
    public final int getSpiritualityCost() {
        return 0; // Permanent passives don't cost spirituality
    }

    @Override
    public final int getCooldown() {
        return 0; // No cooldown for permanent passives
    }

    /**
     * Called every tick automatically
     * @param context Ability execution context
     */
    public abstract void tick(IAbilityContext context);

    /**
     * Permanent passives don't have execution - they're always running
     */
    @Override
    protected final AbilityResult performExecution(IAbilityContext context) {
        return AbilityResult.failure("Permanent passives cannot be executed manually");
    }

    /**
     * Called when player logs in or gets this ability (sequence advancement).
     * Override to add initialization logic.
     *
     * @param context Ability execution context
     */
    public void onActivate(IAbilityContext context) {
        // Default: do nothing
    }

    /**
     * Called when player logs out or loses this ability.
     * Override to add cleanup logic.
     *
     * @param context Ability execution context
     */
    public void onDeactivate(IAbilityContext context) {
        // Default: do nothing
    }
}
