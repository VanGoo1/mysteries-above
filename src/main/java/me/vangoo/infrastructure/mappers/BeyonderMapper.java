package me.vangoo.infrastructure.mappers;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.OneTimeUseAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Mastery;
import me.vangoo.domain.valueobjects.SanityLoss;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.dto.BeyonderDTO;
import me.vangoo.application.services.PathwayManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BeyonderMapper {

    private static final String ONE_TIME_PREFIX = "onetime:";
    private final PathwayManager pathwayManager;

    public BeyonderMapper(PathwayManager pathwayManager) {
        this.pathwayManager = pathwayManager;
    }

    /**
     * Convert domain model to DTO for persistence
     */
    public BeyonderDTO toDTO(Beyonder beyonder) {
        List<String> offPathwayAbilityIds = beyonder.getOffPathwayActiveAbilities().stream()
                .map(a -> a.getIdentity().id())
                .toList();
        return new BeyonderDTO(
                beyonder.getPlayerId(),
                beyonder.getPathway().getName(),
                beyonder.getSequenceLevel(),
                beyonder.getMasteryValue(),
                beyonder.getSpiritualityValue(),
                beyonder.getSanityLossScale(),
                offPathwayAbilityIds
        );
    }

    /**
     * Convert DTO to domain model
     */
    public Beyonder toDomain(BeyonderDTO dto) {
        Pathway pathway = pathwayManager.getPathway(dto.getPathwayName());
        if (pathway == null) {
            throw new IllegalArgumentException("Unknown pathway: " + dto.getPathwayName());
        }
        Set<Ability> offPathwayAbilityIds = new HashSet<>();
        if (dto.getOffPathwayAbilities() != null) {
            offPathwayAbilityIds = dto.getOffPathwayAbilities().stream()
                    .map(this::resolveAbility)
                    .collect(Collectors.toSet());
        }
        Beyonder beyonder = Beyonder.restore(
                dto.getPlayerId(),
                pathway,
                Sequence.of(dto.getSequence()),
                Mastery.of(dto.getMastery()),
                dto.getSpirituality(),
                SanityLoss.of(dto.getSanityLossScale()),
                offPathwayAbilityIds
        );

        beyonder.initializeTransientFields();

        return beyonder;
    }

    private Ability resolveAbility(String abilityId) {
        boolean isOneTime = abilityId.startsWith(ONE_TIME_PREFIX);
        String searchId = abilityId;

        if (isOneTime) {
            searchId = abilityId.substring(ONE_TIME_PREFIX.length());
        }

        Ability baseAbility = pathwayManager.findAbilityInAllPathways(AbilityIdentity.of(searchId));
        if (baseAbility == null) {
            System.err.println("Warning: Could not restore off-pathway ability with ID: " + searchId);
            return null;
        }

        if (isOneTime) {
            if (baseAbility instanceof ActiveAbility) {
                return new OneTimeUseAbility((ActiveAbility) baseAbility);
            } else {
                System.err.println("Error: OneTimeUse wrapper cannot apply to non-active ability: " + searchId);
                return null;
            }
        }

        return baseAbility;
    }
}