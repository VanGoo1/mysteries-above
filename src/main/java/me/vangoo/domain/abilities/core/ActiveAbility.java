package me.vangoo.domain.abilities.core;


public abstract class ActiveAbility extends Ability {
    @Override
    public final AbilityType getType() {
        return AbilityType.ACTIVE;
    }
}
