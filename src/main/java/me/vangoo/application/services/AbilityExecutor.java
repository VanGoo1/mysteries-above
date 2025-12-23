package me.vangoo.application.services;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.entity.Player;

import static org.bukkit.Bukkit.getPlayer;

public class AbilityExecutor {
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

    public AbilityResult execute(Beyonder beyonder, Ability ability) {
        Player player = getPlayer(beyonder.getPlayerId());
        if (player == null) {
            return AbilityResult.failure("Player not found");
        }

        // Check ability locks
        if (abilityLockManager.isLocked(beyonder.getPlayerId())) {
            return AbilityResult.failure("Здібності заблоковані!");
        }

        IAbilityContext context = abilityContextFactory.createContext(player);

        // Check cooldowns (for active abilities)
        if (ability.getType() == AbilityType.ACTIVE && context.hasCooldown(ability)) {
            return AbilityResult.failure(
                    "Cooldown: " + context.getRemainingCooldownSeconds(ability) + "с"
            );
        }

        // Delegate to domain - all business logic happens here
        AbilityResult result = beyonder.useAbility(ability, context);

        // Set cooldown if ability succeeded
        if (ability.getType() == AbilityType.ACTIVE && result.isSuccess()) {
            context.setCooldown(ability, ability.getCooldown());
        }

        // Handle toggleable passives
        if (ability.getType() == AbilityType.TOGGLEABLE_PASSIVE && result.isSuccess()) {
            passiveAbilityManager.toggleAbility(
                    beyonder.getPlayerId(),
                    (ToggleablePassiveAbility) ability,
                    context
            );
        }

        // Apply visual effects if there's a penalty
        if (result.hasSanityPenalty()) {
            rampageEffectsHandler.applySanityPenalty(player, beyonder, result.getSanityPenalty());
        }

        // Update and return domain result directly
        beyonderService.updateBeyonder(beyonder);
        return result;
    }
}