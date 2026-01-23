package me.vangoo.application.services;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.SanityPenalty;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import static org.bukkit.Bukkit.getPlayer;

public class AbilityExecutor {
    private final BeyonderService beyonderService;
    private final AbilityLockManager abilityLockManager;
    private final RampageManager rampageManager;
    private final PassiveAbilityManager passiveAbilityManager;
    private final AbilityContextFactory abilityContextFactory;
    private final SanityPenaltyHandler sanityPenaltyHandler;
    private final DomainEventPublisher eventPublisher;

    public AbilityExecutor(BeyonderService beyonderService, AbilityLockManager abilityLockManager,
                           RampageManager rampageManager, PassiveAbilityManager passiveAbilityManager,
                           AbilityContextFactory abilityContextFactory, SanityPenaltyHandler sanityPenaltyHandler, DomainEventPublisher eventPublisher) {
        this.beyonderService = beyonderService;
        this.abilityLockManager = abilityLockManager;
        this.rampageManager = rampageManager;
        this.passiveAbilityManager = passiveAbilityManager;
        this.abilityContextFactory = abilityContextFactory;
        this.sanityPenaltyHandler = sanityPenaltyHandler;
        this.eventPublisher = eventPublisher;
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
        if (ability.getType() == AbilityType.ACTIVE && context.cooldown().hasCooldown(beyonder,ability)) {
            return AbilityResult.failure(
                    "Cooldown: " + context.cooldown().getRemainingCooldownSeconds(beyonder, ability) + "с"
            );
        }

        // Delegate to domain - all business logic happens here
        AbilityResult result = beyonder.useAbility(ability, context);

        // КРИТИЧНО: НЕ встановлювати кулдаун якщо результат deferred!
        // Deferred означає що здібність ще не виконана (показує меню, чекає вибору)
        if (result.isDeferred()) {
            // Здібність відкладена - ресурси будуть спожиті пізніше
            return result;
        }

        // Set cooldown ONLY if ability succeeded AND is not deferred
        if (ability.getType() == AbilityType.ACTIVE && result.isSuccess()) {
            // Кулдаун вже встановлений через AbilityResourceConsumer
            // Тому тут нічого не робимо

            boolean isOffPathway = beyonder.getOffPathwayActiveAbilities()
                    .stream()
                    .anyMatch(a -> a.getIdentity().equals(ability.getIdentity()));

            eventPublisher.publishAbility(
                    new AbilityDomainEvent.AbilityUsed(
                            beyonder.getPlayerId(),
                            ability.getName(),
                            beyonder.getPathway().getName(),
                            beyonder.getSequenceLevel(),
                            isOffPathway
                    )
            );
        }

        // Handle toggleable passives
        if (ability.getType() == AbilityType.TOGGLEABLE_PASSIVE && result.isSuccess()) {
            boolean isEnabled = passiveAbilityManager.toggleAbility(
                    beyonder.getPlayerId(),
                    (ToggleablePassiveAbility) ability,
                    context
            );
            String message = isEnabled
                    ? ChatColor.GREEN + ability.getName() + ChatColor.GRAY + " увімкнена."
                    : ChatColor.YELLOW + ability.getName() + ChatColor.GRAY + " вимкнена.";
            return AbilityResult.successWithMessage(message);
        }

        // Apply visual effects if there's a penalty
        if (result.hasSanityPenalty()) {
            handleSanityPenalty(player, beyonder, result.getSanityPenalty());
        }

        // Update and return domain result directly
        beyonderService.updateBeyonder(beyonder);
        return result;
    }

    /**
     * Обробити штраф за втрату здорового глузду
     */
    private void handleSanityPenalty(Player player, Beyonder beyonder, SanityPenalty penalty) {
        switch (penalty.type()) {
            case DAMAGE, SPIRITUALITY_LOSS -> {
                // Прості штрафи - застосувати негайно
                sanityPenaltyHandler.applySimplePenalty(player, beyonder, penalty);
            }
            case EXTREME -> {
                // Екстремальний штраф - запустити rampage трансформацію
                boolean started = rampageManager.startRampage(
                        player.getUniqueId(),
                        beyonder,
                        20 // 20 секунд до трансформації
                );

                if (!started) {
                    // Гравець вже в rampage - показати повідомлення
                    player.sendMessage(ChatColor.DARK_RED +
                            "Хаос вже поглинає вашу свідомість!");
                }
            }
        }
    }
}