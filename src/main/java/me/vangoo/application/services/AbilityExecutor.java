package me.vangoo.application.services;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.abilities.AbilityExecutionResult;
import me.vangoo.application.abilities.SanityLossCheckResult;
import me.vangoo.application.abilities.SanityPenalty;
import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
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
                           RampageEffectsHandler rampageEffectsHandler, PassiveAbilityManager passiveAbilityManager, AbilityContextFactory abilityContextFactory) {
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

        // Check if abilities are locked (BEFORE creating context)
        if (abilityLockManager.isLocked(beyonder.getPlayerId())) {
            return AbilityExecutionResult.failure("Здібності заблоковані!");
        }

        // Check sanity loss BEFORE execution
        SanityLossCheckResult sanityCheck = checkSanityLoss(beyonder.getSanityLoss());
        if (!sanityCheck.canExecuteAbility()) {
            // Apply penalty to beyonder
            rampageEffectsHandler.showSanityLossEffects(player, beyonder, sanityCheck);
            beyonderService.updateBeyonder(beyonder);

            return AbilityExecutionResult.failureWithSanityCheck(
                    sanityCheck.message(),
                    sanityCheck
            );
        }

        IAbilityContext context = abilityContextFactory.createContext(player);

        AbilityResult abilityResult = beyonder.useAbility(ability, context);
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
     * Uses domain calculation (SanityLoss.calculateFailureChance)
     */
    private SanityLossCheckResult checkSanityLoss(SanityLoss sanityLoss) {
        int scale = sanityLoss.scale();
        if (sanityLoss.isNegligible()) {
            return SanityLossCheckResult.passed(sanityLoss);
        }

        double failureChance = sanityLoss.calculateFailureChance();
        if (random.nextDouble() >= failureChance) {
            return SanityLossCheckResult.passed(sanityLoss);
        }

        // Failed check - determine penalty and message
        String message = getSanityLossMessage(sanityLoss);
        SanityPenalty penalty = calculatePenalty(sanityLoss);

        return SanityLossCheckResult.failed(sanityLoss, penalty, message);
    }

    private String getSanityLossMessage(SanityLoss sanityLoss) {
        if (sanityLoss.isMinor()) {
            return "Ваші руки злегка тремтять...";
        } else if (sanityLoss.isModerate()) {
            return "Ваші сили відмовляються слухатися!";
        } else if (sanityLoss.isSerious()) {
            return "Хаос у вашій свідомості блокує здібності!";
        } else if (sanityLoss.isCritical()) {
            return "Божевільний шепіт заважає зосередитися!";
        } else if (sanityLoss.isExtreme()) {
            return "ХАОС ПОГЛИНАЄ ВАШУ СВІДОМІСТЬ!";
        }
        return "";
    }

    private SanityPenalty calculatePenalty(SanityLoss sanityLoss) {
        if (sanityLoss.isExtreme()) {
            return SanityPenalty.extreme();
        } else if (sanityLoss.isCritical()) {
            int spiritualityLoss = random.nextInt(10) + 5; // 5-14
            return SanityPenalty.spiritualityLoss(spiritualityLoss);
        } else if (sanityLoss.isSerious()) {
            int damage = 1 + (sanityLoss.scale() - 60) / 10; // 1-3
            return SanityPenalty.damage(damage);
        }
        return SanityPenalty.none();
    }
}
