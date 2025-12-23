package me.vangoo.domain.abilities.core;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import org.bukkit.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

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
            return AbilityResult.cooldownFailure(
                    context.getRemainingCooldownSeconds(this)
            );
        }
        if (!canExecute(context))
            return AbilityResult.failure("can't execute this ability");
        preExecution(context);

        // SEQUENCE-BASED SUCCESS CHECK (Optional)
        AbilityResult sequenceCheckResult  = performSequenceBasedSuccessCheck(context);
        if (sequenceCheckResult  != null) {
            // Ability uses sequence-based success and check failed
            context.setCooldown(this, getCooldown());
            return sequenceCheckResult ;
        }

        // MAIN EXECUTION
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

    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return Optional.empty(); // Default: no sequence check
    }

    /**
     * Override this method to enable sequence-based success checking for this ability.
     *
     * @param context Ability context
     * @return Optional with target entity if this ability should check sequence-based success,
     *         empty Optional if ability doesn't use sequence-based success
     */
    @Nullable
    private AbilityResult performSequenceBasedSuccessCheck(IAbilityContext context) {
        // Get target for sequence check
        Optional<LivingEntity> targetOpt = getSequenceCheckTarget(context);

        if (targetOpt.isEmpty()) {
            return null; // No sequence check for this ability
        }

        LivingEntity target = targetOpt.get();

        // Get target's beyonder (if they are one)
        Beyonder targetBeyonder = context.getBeyonderFromEntity(target.getUniqueId());

        if (targetBeyonder == null) {
            return null; // Target is not a beyonder, no resistance
        }

        // Get caster sequence
        Beyonder casterBeyonder = context.getCasterBeyonder();
        int casterSequence = casterBeyonder.getSequenceLevel();
        int targetSequence = targetBeyonder.getSequenceLevel();

        // Calculate and roll success chance
        SequenceBasedSuccessChance successChance =
                new SequenceBasedSuccessChance(casterSequence, targetSequence);

        if (!successChance.rollSuccess()) {
            return AbilityResult.sequenceResistance(successChance);
        }

        return null; // Success check passed
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
