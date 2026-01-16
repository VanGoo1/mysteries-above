package me.vangoo.domain.abilities.context;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.AbilityIdentity;

import java.util.UUID;

public interface IBeyonderContext {
    void updateSanityLoss(UUID playerId, int change);

    Beyonder getBeyonder(UUID entityId);

    boolean isBeyonder(UUID entityId);

    boolean isAbilityActivated(UUID entityId, AbilityIdentity abilityIdentity);

    void removeOffPathwayAbility(AbilityIdentity identity, UUID playerId);

    int getUnlockedRecipesCount(UUID playerId,String pathwayName);
}
