package me.vangoo.application.services.context;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.PassiveAbilityManager;
import me.vangoo.application.services.RecipeUnlockService;
import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.UnlockedRecipe;

import java.util.Set;
import java.util.UUID;

public class BeyonderContext implements IBeyonderContext {

    private final BeyonderService beyonderService;
    private final PassiveAbilityManager passiveAbilityManager;
    private final RecipeUnlockService recipeUnlockService;
    public BeyonderContext(BeyonderService beyonderService, PassiveAbilityManager passiveAbilityManager, RecipeUnlockService recipeUnlockService) {
        this.beyonderService = beyonderService;
        this.passiveAbilityManager = passiveAbilityManager;
        this.recipeUnlockService = recipeUnlockService;
    }


    @Override
    public void updateSanityLoss(UUID playerId, int change) {
        var beyonder = getBeyonder(playerId);
        if (beyonder != null) {
            if (change > 0) {
                beyonder.increaseSanityLoss(change);
            } else if (change < 0) {
                beyonder.decreaseSanityLoss(-change);
            }
            beyonderService.updateBeyonder(beyonder);
        }
    }

    @Override
    public Beyonder getBeyonder(UUID entityId) {
        return beyonderService.getBeyonder(entityId);
    }

    @Override
    public boolean isBeyonder(UUID entityId) {
        return getBeyonder(entityId) != null;
    }

    @Override
    public boolean isAbilityActivated(UUID entityId, AbilityIdentity abilityIdentity) {
        return passiveAbilityManager.isToggleableEnabled(entityId, abilityIdentity);

    }

    @Override
    public void removeOffPathwayAbility(AbilityIdentity identity, UUID playerId) {
        Beyonder beyonder = beyonderService.getBeyonder(playerId);
        if (beyonder != null) {
            beyonder.removeAbility(identity);
            beyonderService.updateBeyonder(beyonder);
        }
    }

    @Override
    public int getUnlockedRecipesCount(UUID playerId,String pathwayName) {
        Set<UnlockedRecipe> recipes = recipeUnlockService
                .getUnlockedRecipes(playerId);

        return (int) recipes.stream()
                .filter(r -> r.pathwayName().equalsIgnoreCase(pathwayName))
                .count();
    }
}
