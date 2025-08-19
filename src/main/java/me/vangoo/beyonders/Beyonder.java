package me.vangoo.beyonders;

import me.vangoo.abilities.Ability;
import me.vangoo.pathways.Pathway;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Beyonder {
    private final UUID playerId;
    private Pathway pathway; // class
    private int sequence; // level
    private int mastery; // level progress
    private int spirituality; // mana
    private int maxSpirituality;
    private int sanityLossScale;
    private List<Ability> abilities;

    public Beyonder(UUID playerId) {
        this.playerId = playerId;
        this.sequence = -1;
        this.sanityLossScale = 0;
        this.mastery = 0;
        this.spirituality = 0;
        this.maxSpirituality = 0;
    }

    public Beyonder(UUID playerId, List<Ability> abilities) {
        this(playerId);
        this.abilities = new ArrayList<>(abilities);
    }

    public boolean canAdvance() {
        return mastery >= 100 && sequence > 0;
    }

    public void advance() {
        if (canAdvance()) {
            sequence--;
            mastery = 0;
            updateMaxSpirituality();
            abilities.addAll(pathway.GetAbilitiesForSequence(sequence));
        }
    }

    public void updateMaxSpirituality() {
        if (sequence < 0) {
            this.maxSpirituality = 0;
            return;
        }

        int seq = Math.min(9, this.sequence);
        int idx = 9 - seq;

        int[] minValues = {100, 300, 600, 1000, 1600, 2280, 3100, 4049, 5126, 6331};
        int[] maxValues = {200, 500, 900, 1500, 2000, 2700, 3460, 4306, 5237, 7000};

        int min = minValues[idx];
        int max = maxValues[idx];

        if (max < min) {
            max = min;
        }

        float t = Math.max(0, Math.min(100, this.mastery)) / 100.0f;

        this.maxSpirituality = Math.round(min + (max - min) * t);
    }

    public void setSpirituality(int spirituality) {
        this.spirituality = Math.min(spirituality, maxSpirituality);
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

    public void setMastery(int mastery) {
        if (mastery > 100)
            mastery = 100;
        else if (mastery < 0)
            mastery = 0;

        this.mastery = mastery;
    }

    public int getSpirituality() {
        return spirituality;
    }

    public int getMaxSpirituality() {
        return maxSpirituality;
    }

    public List<Ability> getAbilities() {
        return abilities;
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
