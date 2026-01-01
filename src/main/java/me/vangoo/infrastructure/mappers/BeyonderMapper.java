package me.vangoo.infrastructure.mappers;

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

    private final PathwayManager pathwayManager;

    public BeyonderMapper(PathwayManager pathwayManager) {
        this.pathwayManager = pathwayManager;
    }

    /**
     * Convert domain model to DTO for persistence
     */
    public BeyonderDTO toDTO(Beyonder beyonder) {
        List<String> offPathwayAbilityIds = beyonder.getOffPathwayActiveAbilities().stream()
                .map(AbilityIdentity::id)
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
        Set<AbilityIdentity> offPathwayAbilityIds = new HashSet<>();
        if (dto.getOffPathwayAbilities() != null) {
            offPathwayAbilityIds = dto.getOffPathwayAbilities().stream()
                    .map(AbilityIdentity::of)
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
}