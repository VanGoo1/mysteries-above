package me.vangoo.domain.abilities.context;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public interface IBeyonderContext {
    void updateSanityLoss(UUID playerId, int change);

    Beyonder getBeyonder(UUID entityId);

    boolean isBeyonder(UUID entityId);

    boolean isAbilityActivated(UUID entityId, AbilityIdentity abilityIdentity);

    void removeOffPathwayAbility(AbilityIdentity identity, UUID playerId);

    int getUnlockedRecipesCount(UUID playerId,String pathwayName);

    List<ItemStack> getIngredientsForPotion(Pathway pathway, Sequence sequence);
}
