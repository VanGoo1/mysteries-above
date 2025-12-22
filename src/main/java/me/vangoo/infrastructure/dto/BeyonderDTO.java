package me.vangoo.infrastructure.dto;

import com.google.gson.annotations.Expose;

import java.util.UUID;

public class BeyonderDTO {
    @Expose
    private UUID playerId;

    @Expose
    private String pathwayName;

    @Expose
    private int sequence;

    @Expose
    private int mastery;

    @Expose
    private int spirituality;

    @Expose
    private int sanityLossScale;

    // Default constructor for GSON
    public BeyonderDTO() {
    }

    public BeyonderDTO(
            UUID playerId,
            String pathwayName,
            int sequence,
            int mastery,
            int spirituality,
            int sanityLossScale
    ) {
        this.playerId = playerId;
        this.pathwayName = pathwayName;
        this.sequence = sequence;
        this.mastery = mastery;
        this.spirituality = spirituality;
        this.sanityLossScale = sanityLossScale;
    }

    // Getters and setters
    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public String getPathwayName() {
        return pathwayName;
    }

    public void setPathwayName(String pathwayName) {
        this.pathwayName = pathwayName;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public int getMastery() {
        return mastery;
    }

    public void setMastery(int mastery) {
        this.mastery = mastery;
    }

    public int getSpirituality() {
        return spirituality;
    }

    public void setSpirituality(int spirituality) {
        this.spirituality = spirituality;
    }

    public int getSanityLossScale() {
        return sanityLossScale;
    }

    public void setSanityLossScale(int sanityLossScale) {
        this.sanityLossScale = sanityLossScale;
    }
}