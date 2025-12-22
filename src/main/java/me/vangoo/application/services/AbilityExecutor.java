package me.vangoo.application.services;

import fr.skytasul.glowingentities.GlowingEntities;
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
    private final MysteriesAbovePlugin plugin;
    private final CooldownManager cooldownManager;
    private final BeyonderService beyonderService;
    private final AbilityLockManager abilityLockManager;
    private final RampageEffectsHandler rampageEffectsHandler;
    private final GlowingEntities glowingEntities;
    private final PassiveAbilityManager passiveAbilityManager;

    public AbilityExecutor(MysteriesAbovePlugin plugin, CooldownManager cooldownManager, BeyonderService beyonderService, AbilityLockManager abilityLockManager, RampageEffectsHandler rampageEffectsHandler, GlowingEntities glowingEntities, PassiveAbilityManager passiveAbilityManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.beyonderService = beyonderService;
        this.abilityLockManager = abilityLockManager;
        this.rampageEffectsHandler = rampageEffectsHandler;
        this.glowingEntities = glowingEntities;
        this.passiveAbilityManager = passiveAbilityManager;
    }


    public AbilityExecutionResult execute(Beyonder beyonder, Ability ability) {
        Player player = getPlayer(beyonder.getPlayerId());
        if (player == null) {
            return AbilityExecutionResult.failure("Player not found");
        }

        // 1. Check if abilities are locked (BEFORE creating context)
        if (abilityLockManager.isLocked(beyonder.getPlayerId())) {
            return AbilityExecutionResult.failure("Здібності заблоковані!");
        }

        // 3. Check sanity loss BEFORE execution
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

        // 4. Create context and execute ability
        IAbilityContext context = new BukkitAbilityContext(
                player, plugin, cooldownManager, beyonderService, abilityLockManager, glowingEntities
        );

        AbilityResult abilityResult = ability.execute(context);

        if (!abilityResult.isSuccess()) {
            return AbilityExecutionResult.failure(abilityResult.getMessage());
        }

        // 5. Update beyonder state (domain)
        if (ability.getType() == AbilityType.ACTIVE) {
            try {
                beyonder.useAbility(ability, ability.getSpiritualityCost());
                beyonderService.updateBeyonder(beyonder);
            } catch (IllegalStateException e) {
                return AbilityExecutionResult.failure(e.getMessage());
            }
        } else if (ability.getType() == AbilityType.TOGGLEABLE_PASSIVE) {
            passiveAbilityManager.toggleAbility(beyonder.getPlayerId(), (ToggleablePassiveAbility) ability, context);
        }

        return AbilityExecutionResult.success();
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
