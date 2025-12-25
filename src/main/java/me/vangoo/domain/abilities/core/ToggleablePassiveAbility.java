package me.vangoo.domain.abilities.core;

import me.vangoo.domain.valueobjects.Sequence;

public abstract class ToggleablePassiveAbility extends Ability {
    @Override
    public final AbilityType getType() {
        return AbilityType.TOGGLEABLE_PASSIVE;
    }

    @Override
    public final int getSpiritualityCost() {
        return 0; // Toggleable abilities don't cost spirituality
    }

    @Override
    public final int getCooldown(Sequence userSequence) {
        return 0; // No cooldown for toggling
    }

    /**
     * Called every tick while ability is enabled
     * @param context Ability execution context
     */
    public abstract void tick(IAbilityContext context);

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        // This method is called to trigger the toggle
        return AbilityResult.success();
    }

    public abstract void onEnable(IAbilityContext context);

    public abstract void onDisable(IAbilityContext context);
}
