package me.vangoo.beyonders;

import me.vangoo.pathways.Pathway;

import java.util.UUID;

public class Beyonder {
    private final UUID playerId;
    private Pathway pathway; // class
    private int sequence; // level
    private int mastery; // level progress
    private int spirituality; // mana
    private int maxSpirituality;

    public Beyonder(UUID playerId) {
        this.playerId = playerId;
        this.sequence = -1;
        this.mastery = 0;
        this.spirituality = 0;
        this.maxSpirituality = 0;
    }

    public boolean canAdvance() {
        return mastery >= 100 && sequence > 0;
    }

    public void advance() {
        if (canAdvance()) {
            sequence--;
            mastery = 0;
            updateMaxSpirituality();
        }
    }

    private void updateMaxSpirituality() {
        maxSpirituality = (10 - sequence) * 50;
        spirituality = maxSpirituality;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Pathway getPathway() {
        return pathway;
    }

    public int getSequence() {
        return sequence;
    }

    public int getMastery() {
        return mastery;
    }

    public int getSpirituality() {
        return spirituality;
    }

    public int getMaxSpirituality() {
        return maxSpirituality;
    }

    public void setPathway(Pathway pathway) {
        this.pathway = pathway;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public void setMaxSpirituality(int maxSpirituality) {
        this.maxSpirituality = maxSpirituality;
    }

    public void IncrementSpirituality(int value) {
        this.spirituality += value;
        if (spirituality > maxSpirituality) {
            spirituality = maxSpirituality;
        }
    }

    public void DecrementSpirituality(int value) {
        this.spirituality -= value;
        if (spirituality < 0) {
            spirituality = 0;
        }
    }
}
