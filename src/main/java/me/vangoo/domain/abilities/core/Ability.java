package me.vangoo.domain.abilities.core;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public abstract class Ability {
    public abstract String getName();

    public abstract String getDescription(Sequence userSequence);

    public abstract int getSpiritualityCost();

    /**
     * Get periodic spirituality cost (per second) for channeled/toggle abilities.
     * Return 0 for instant abilities.
     *
     * @return Spirituality cost per second, or 0 if not applicable
     */
    public int getPeriodicCost() {
        return 0; // Default: no periodic cost
    }

    /**
     * Cooldown in seconds
     */
    public abstract int getCooldown(Sequence userSequence);

    public abstract AbilityType getType();

    /**
     * Get the unique identity of this ability.
     * Abilities with the same identity are considered the same conceptual ability
     * across different sequences (e.g., ScanGaze active and ScanGaze passive).
     *
     * @return Unique identity for this ability
     */
    public AbilityIdentity getIdentity() {
        return AbilityIdentity.of(getName());
    }

    /**
     * Check if this ability can replace another ability.
     * Used during sequence advancement to replace old versions with new ones.
     *
     * @param other Other ability
     * @return true if this ability should replace the other
     */
    public boolean canReplace(Ability other) {
        return getIdentity().canReplace(other.getIdentity());
    }

    public final AbilityResult execute(IAbilityContext context) {
        var beyonder = context.getCasterBeyonder();
        if (getType() == AbilityType.ACTIVE && context.cooldown().hasCooldown(beyonder, this)) {
            return AbilityResult.cooldownFailure(
                    context.cooldown().getRemainingCooldownSeconds(beyonder, this)
            );
        }
        if (!canExecute(context))
            return AbilityResult.failure("can't execute this ability");
        preExecution(context);

        // SEQUENCE-BASED SUCCESS CHECK (Optional)
        AbilityResult sequenceCheckResult = performSequenceBasedSuccessCheck(context);
        if (sequenceCheckResult != null) {
            context.messaging().sendMessage(context.getCasterId(), sequenceCheckResult.getMessage());
            context.cooldown().setCooldown(this, context.getCasterId());
            return sequenceCheckResult;
        }

        // MAIN EXECUTION
        AbilityResult result = performExecution(context);

        // КРИТИЧНО: НЕ встановлювати кулдаун якщо результат deferred!
        if (result.isDeferred()) {
            return result;
        }

        if (result.isSuccess()) {
            postExecution(context);
            // Кулдаун встановлюється пізніше через AbilityResourceConsumer
            // для deferred здібностей, або тут для звичайних
            if (getType() == AbilityType.ACTIVE) {
                // НЕ встановлюємо кулдаун тут - це робить AbilityResourceConsumer
                // context.setCooldown(this, getCooldown(context.getCasterBeyonder().getSequence()));
            }
        }
        return result;
    }

    protected int scaleValue(int baseValue, Sequence userSequence,
                             SequenceScaler.ScalingStrategy strategy) {
        double multiplier = SequenceScaler.calculateMultiplier(
                userSequence.level(),
                strategy
        );
        return (int) Math.ceil(baseValue * multiplier);
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
     * empty Optional if ability doesn't use sequence-based success
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
        Beyonder targetBeyonder = context.beyonder().getBeyonder(target.getUniqueId());

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
