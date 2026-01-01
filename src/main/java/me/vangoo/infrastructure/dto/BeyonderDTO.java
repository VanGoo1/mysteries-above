package me.vangoo.infrastructure.dto;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;
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

    @Expose
    private List<String> offPathwayAbilities;

    // Default constructor for GSON
    public BeyonderDTO() {
        this.offPathwayAbilities = new ArrayList<>();
    }

    public BeyonderDTO(
            UUID playerId,
            String pathwayName,
            int sequence,
            int mastery,
            int spirituality,
            int sanityLossScale,
            List<String> offPathwayAbilities
    ) {
        this.playerId = playerId;
        this.pathwayName = pathwayName;
        this.sequence = sequence;
        this.mastery = mastery;
        this.spirituality = spirituality;
        this.sanityLossScale = sanityLossScale;
        this.offPathwayAbilities = offPathwayAbilities != null ? offPathwayAbilities : new ArrayList<>();
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

    public List<String> getOffPathwayAbilities() {
        return offPathwayAbilities;
    }

    public void setOffPathwayAbilities(List<String> offPathwayAbilities) {
        this.offPathwayAbilities = offPathwayAbilities;
    }
}