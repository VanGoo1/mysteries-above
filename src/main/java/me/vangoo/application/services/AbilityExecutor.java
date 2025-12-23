package me.vangoo.application.services;

import me.vangoo.application.abilities.AbilityExecutionResult;
import me.vangoo.application.abilities.SanityLossCheckResult;
import me.vangoo.application.abilities.SanityPenalty;
import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.SanityLoss;
import org.bukkit.entity.Player;

import java.util.Random;

import static org.bukkit.Bukkit.getPlayer;

public class AbilityExecutor {
    private final Random random = new Random();

    private final BeyonderService beyonderService;
    private final AbilityLockManager abilityLockManager;
    private final RampageEffectsHandler rampageEffectsHandler;
    private final PassiveAbilityManager passiveAbilityManager;
    private final AbilityContextFactory abilityContextFactory;

    public AbilityExecutor(BeyonderService beyonderService, AbilityLockManager abilityLockManager,
                           RampageEffectsHandler rampageEffectsHandler, PassiveAbilityManager passiveAbilityManager,
                           AbilityContextFactory abilityContextFactory) {
        this.beyonderService = beyonderService;
        this.abilityLockManager = abilityLockManager;
        this.rampageEffectsHandler = rampageEffectsHandler;
        this.passiveAbilityManager = passiveAbilityManager;
        this.abilityContextFactory = abilityContextFactory;
    }

    public AbilityExecutionResult execute(Beyonder beyonder, Ability ability) {
        Player player = getPlayer(beyonder.getPlayerId());
        if (player == null) {
            return AbilityExecutionResult.failure("Player not found");
        }

        // Check if abilities are locked
        if (abilityLockManager.isLocked(beyonder.getPlayerId())) {
            return AbilityExecutionResult.failure("Здібності заблоковані!");
        }

        IAbilityContext context = abilityContextFactory.createContext(player);

        // *** Check cooldown BEFORE sanity check to prevent penalty spam ***
        if (ability.getType() == AbilityType.ACTIVE && context.hasCooldown(ability)) {
            return AbilityExecutionResult.failure(
                    "Cooldown: " + context.getRemainingCooldownSeconds(ability) + "с"
            );
        }

        // *** Check sanity loss BEFORE executing ability ***
        SanityLossCheckResult sanityCheck = checkSanityLoss(beyonder);
        if (!sanityCheck.canExecuteAbility()) {
            // *** Set cooldown even on sanity failure to prevent spam ***
            if (ability.getType() == AbilityType.ACTIVE) {
                context.setCooldown(ability, ability.getCooldown());
            }

            // Apply penalty
            rampageEffectsHandler.showSanityLossEffects(player, beyonder, sanityCheck);
            beyonderService.updateBeyonder(beyonder);

            return AbilityExecutionResult.failureWithSanityCheck(
                    sanityCheck.message(),
                    sanityCheck
            );
        }

        // Execute ability normally
        AbilityResult abilityResult = beyonder.useAbility(ability, context);

        if (abilityResult.isCooldownFailure()) {
            return AbilityExecutionResult.failure(abilityResult.getMessage());
        }

        beyonderService.updateBeyonder(beyonder);

        if (ability.getType() == AbilityType.TOGGLEABLE_PASSIVE) {
            boolean res = passiveAbilityManager.toggleAbility(beyonder.getPlayerId(), (ToggleablePassiveAbility) ability, context);
            return res ? AbilityExecutionResult.success() : AbilityExecutionResult.failure("cant activate passive ability");
        }

        if (abilityResult.isSuccess()) {
            return AbilityExecutionResult.success();
        } else {
            return AbilityExecutionResult.failure(abilityResult.getMessage());
        }
    }

    /**
     * Check if sanity loss causes ability failure
     */
    private SanityLossCheckResult checkSanityLoss(Beyonder beyonder) {
        SanityLoss sanityLoss = beyonder.getSanityLoss();

        if (sanityLoss.isNegligible()) {
            return SanityLossCheckResult.passed(sanityLoss);
        }

        double failureChance = sanityLoss.calculateFailureChance();

        // Roll for failure
        if (random.nextDouble() >= failureChance) {
            return SanityLossCheckResult.passed(sanityLoss);
        }

        // Failed check - determine penalty
        String message = getSanityLossMessage(sanityLoss);
        SanityPenalty penalty = calculatePenalty(beyonder);

        return SanityLossCheckResult.failed(sanityLoss, penalty, message);
    }

    private String getSanityLossMessage(SanityLoss sanityLoss) {
        if (sanityLoss.isMinor()) {
            return "Ваші руки злегка тремтять...";
        } else if (sanityLoss.isModerate()) {
            return "Ваші сили відмовляються слухатися!";
        } else if (sanityLoss.isSerious()) {
            return "Хаос у вашій свідомості блокує здібності!";
        } else if (sanityLoss.isSevere()) {
            return "Втрата контролю посилюється!";
        } else if (sanityLoss.isCritical()) {
            return "Божевільний шепіт заважає зосередитися!";
        } else if (sanityLoss.isExtreme()) {
            return "ХАОС ПОГЛИНАЄ ВАШУ СВІДОМІСТЬ!";
        }
        return "Щось не так...";
    }

    private SanityPenalty calculatePenalty(Beyonder beyonder) {
        SanityLoss sanityLoss = beyonder.getSanityLoss();
        int sequence = beyonder.getSequenceLevel();

        // EXTREME: 96-100 → Death
        if (sanityLoss.isExtreme()) {
            return SanityPenalty.extreme();
        }

        // CRITICAL: 81-95 → Large penalties
        if (sanityLoss.isCritical()) {
            if (random.nextBoolean()) {
                // Spirituality loss
                int spiritualityLoss = SequenceScaler.scaleSpiritualityLoss(
                        beyonder.getMaxSpirituality(),
                        sequence
                );
                return SanityPenalty.spiritualityLoss(spiritualityLoss);
            } else {
                // Damage
                int damage = SequenceScaler.scaleDamagePenalty(sequence);
                return SanityPenalty.damage(damage);
            }
        }

        if (sanityLoss.isSevere()) {
            if (random.nextBoolean()) {
                // Medium spirituality loss
                int spiritualityLoss = SequenceScaler.scaleSpiritualityLoss(
                        beyonder.getMaxSpirituality(),
                        sequence
                ) * 2 / 3; // 66% of full penalty
                return SanityPenalty.spiritualityLoss(Math.max(5, spiritualityLoss));
            } else {
                // Damage
                int damage = SequenceScaler.scaleDamagePenalty(sequence);
                return SanityPenalty.damage(damage);
            }
        }

        // SERIOUS: 41-60 → Small penalties
        if (sanityLoss.isSerious()) {
            if (random.nextDouble() < 0.7) { // 70% damage
                int damage = SequenceScaler.scaleDamagePenalty(sequence);
                return SanityPenalty.damage(Math.max(1, damage / 2)); // Half damage
            } else { // 30% spirituality loss
                int spiritualityLoss = SequenceScaler.scaleSpiritualityLoss(
                        beyonder.getMaxSpirituality(),
                        sequence
                ) / 3; // 33% of full penalty
                return SanityPenalty.spiritualityLoss(Math.max(3, spiritualityLoss));
            }
        }

        // MODERATE: 21-40 → Rare minor damage
        if (sanityLoss.isModerate()) {
            if (random.nextDouble() < 0.3) { // 30% chance
                return SanityPenalty.damage(1);
            }
        }

        // MINOR: 11-20 → No penalty (just blocks ability)
        // NEGLIGIBLE: 0-10 → Never fails

        return SanityPenalty.none();
    }
}