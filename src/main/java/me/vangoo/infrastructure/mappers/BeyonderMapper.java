package me.vangoo.infrastructure.mappers;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.valueobjects.Mastery;
import me.vangoo.domain.valueobjects.SanityLoss;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.dto.BeyonderDTO;
import me.vangoo.application.services.PathwayManager;

public class BeyonderMapper {

    private final PathwayManager pathwayManager;

    public BeyonderMapper(PathwayManager pathwayManager) {
        this.pathwayManager = pathwayManager;
    }

    /**
     * Convert domain model to DTO for persistence
     */
    public BeyonderDTO toDTO(Beyonder beyonder) {
        return new BeyonderDTO(
                beyonder.getPlayerId(),
                beyonder.getPathway().getName(),
                beyonder.getSequenceLevel(),
                beyonder.getMasteryValue(),
                beyonder.getSpiritualityValue(),
                beyonder.getSanityLossScale()
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

        Beyonder beyonder = Beyonder.restore(
                dto.getPlayerId(),
                pathway,
                Sequence.of(dto.getSequence()),
                Mastery.of(dto.getMastery()),
                dto.getSpirituality(),
                SanityLoss.of(dto.getSanityLossScale())
        );

        beyonder.initializeTransientFields();

        return beyonder;
    }
}