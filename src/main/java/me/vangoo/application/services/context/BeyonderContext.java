package me.vangoo.application.services.context;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.PassiveAbilityManager;
import me.vangoo.application.services.PathwayManager;
import me.vangoo.application.services.PotionManager;
import me.vangoo.application.services.RecipeUnlockService;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.UnlockedRecipe;
import me.vangoo.infrastructure.items.CustomItemFactory;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import me.vangoo.infrastructure.theft.TheftLedger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class BeyonderContext implements IBeyonderContext {

    private final BeyonderService beyonderService;
    private final PassiveAbilityManager passiveAbilityManager;
    private final RecipeUnlockService recipeUnlockService;
    private final PotionManager potionManager;
    private final TheftLedger theftLedger;
    private final PathwayManager pathwayManager;
    private final MythicCreatureGateway mythicCreatureGateway;
    private final Map<String, CreatureDefinition> creatureRegistry;

    public BeyonderContext(BeyonderService beyonderService, PassiveAbilityManager passiveAbilityManager, RecipeUnlockService recipeUnlockService, PotionManager potionManager, TheftLedger theftLedger, PathwayManager pathwayManager, MythicCreatureGateway mythicCreatureGateway, Map<String, CreatureDefinition> creatureRegistry) {
        this.beyonderService = beyonderService;
        this.passiveAbilityManager = passiveAbilityManager;
        this.recipeUnlockService = recipeUnlockService;
        this.potionManager = potionManager;
        this.theftLedger = theftLedger;
        this.pathwayManager = pathwayManager;
        this.mythicCreatureGateway = mythicCreatureGateway;
        this.creatureRegistry = creatureRegistry;
    }

    @Override
    public Optional<Beyonder> getCreatureBeyonder(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null) {
            return Optional.empty();
        }
        CreatureDefinition definition = mythicCreatureGateway.creatureId(entity)
                .map(creatureRegistry::get)
                .orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        Pathway pathway = pathwayManager.getPathway(definition.pathway());
        if (pathway == null || !pathway.hasAnyAbility()) {
            return Optional.empty();
        }
        return Optional.of(new Beyonder(entityId, Sequence.of(definition.sequence()), pathway));
    }

    @Override
    public void stealAbility(UUID thiefId, UUID victimId, AbilityIdentity identity) {
        theftLedger.add(thiefId, victimId, identity);
    }

    @Override
    public void stealAbility(UUID thiefId, UUID victimId, AbilityIdentity identity,
                             long reserveHoldMillis, long activeHoldMillis, long suppressionMillis) {
        theftLedger.add(thiefId, victimId, identity, reserveHoldMillis, activeHoldMillis, suppressionMillis);
    }

    @Override
    public boolean hasStolenAbility(UUID thiefId) {
        return theftLedger.grantOf(thiefId).isPresent();
    }

    @Override
    public boolean holdsStolen(UUID thiefId, AbilityIdentity identity) {
        return theftLedger.grantOf(thiefId)
                .filter(theft -> theft.ability().equals(identity.id()))
                .isPresent();
    }

    @Override
    public void releaseStolen(UUID thiefId) {
        theftLedger.release(thiefId);
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
    public int getUnlockedRecipesCount(UUID playerId, String pathwayName) {
        Set<UnlockedRecipe> recipes = recipeUnlockService
                .getUnlockedRecipes(playerId);

        return (int) recipes.stream()
                .filter(r -> r.pathwayName().equalsIgnoreCase(pathwayName))
                .count();
    }

    @Override
    public List<ItemStack> getIngredientsForPotion(Pathway pathway, Sequence sequence) {
        Optional<PathwayPotions> pathwayPotionsOpt = potionManager.getPotionsPathway(pathway.getName());
        if (pathwayPotionsOpt.isEmpty())
            return List.of();

        PathwayPotions pathwayPotions = pathwayPotionsOpt.get();
        ItemStack[] ingredients = pathwayPotions.getIngredients(sequence.level());

        // Перевірка на null перед створенням списку
        if (ingredients == null) {
            return List.of();
        }

        // Використовуємо Stream для безпеки (фільтруємо можливі null всередині масиву)
        return Arrays.stream(ingredients)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<UnlockedRecipe> findRecipesUsing(ItemStack ingredient) {
        Optional<String> id = CustomItemFactory.getCustomItemId(ingredient);
        if (id.isEmpty()) {
            return List.of();
        }
        List<UnlockedRecipe> found = new ArrayList<>();
        for (PathwayPotions potions : potionManager.getPotions()) {
            for (int sequence = 0; sequence <= 9; sequence++) {
                ItemStack[] ingredients = potions.getIngredients(sequence);
                if (ingredients == null) {
                    continue;
                }
                boolean uses = Arrays.stream(ingredients)
                        .anyMatch(i -> CustomItemFactory.getCustomItemId(i).equals(id));
                if (uses) {
                    found.add(UnlockedRecipe.of(potions.getPathway().getName(), sequence));
                }
            }
        }
        return found;
    }

    @Override
    public Optional<UnlockedRecipe> stealRecipe(UUID victimId, UUID thiefId) {
        Set<UnlockedRecipe> thiefRecipes = recipeUnlockService.getUnlockedRecipes(thiefId);
        // Найсильніше з того, чого злодій ще не знає: менша послідовність = цінніший рецепт.
        Optional<UnlockedRecipe> prize = recipeUnlockService.getUnlockedRecipes(victimId).stream()
                .filter(recipe -> !thiefRecipes.contains(recipe))
                .min(Comparator.comparingInt(UnlockedRecipe::sequence));
        if (prize.isEmpty()) {
            return Optional.empty();
        }

        UnlockedRecipe recipe = prize.get();
        recipeUnlockService.revokeRecipe(victimId, recipe.pathwayName(), recipe.sequence());
        recipeUnlockService.unlockRecipe(thiefId, recipe.pathwayName(), recipe.sequence());
        return prize;
    }

    @Override
    public void setOverride(UUID playerId, Beyonder override) {
        beyonderService.setOverride(playerId, override);
    }

    @Override
    public void removeOverride(UUID playerId) {
        beyonderService.removeOverride(playerId);
    }

    @Override
    public void updateBeyonder(UUID playerId) {
        Beyonder beyonder = beyonderService.getBeyonder(playerId);
        if (beyonder != null) {
            beyonderService.updateBeyonder(beyonder);
        }
    }
}
