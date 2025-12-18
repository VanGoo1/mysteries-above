package me.vangoo.domain;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Beyonder {
    @Expose private final UUID playerId;
    @Expose private Pathway pathway; // class
    @Expose private int sequence; // level
    @Expose private int mastery; // level progress
    @Expose private int spirituality; // mana
    @Expose private int maxSpirituality;
    @Expose private int sanityLossScale;
    private final List<Ability> abilities;

    public Beyonder(UUID playerId, int sequence, Pathway pathway) {
        abilities = new ArrayList<>();
        this.playerId = playerId;
        this.sequence = sequence;
        this.pathway = pathway;
        this.mastery = 0;
        this.spirituality = 0;
        this.sanityLossScale = 0;
        updateMaxSpirituality();
        for (int i = 9; i >= sequence; i--) {
            abilities.addAll(pathway.GetAbilitiesForSequence(i));
        }
    }

    public boolean canAdvance() {
        return mastery >= 100 && sequence > 0;
    }

    public boolean canConsumePotion(Pathway pathwayOfPotion, int potionSequence) {
        // Перевіряємо чи той самий шлях та чи тієї ж групи
        if (pathwayOfPotion.getGroup() != pathway.getGroup()) {
            return false;
        }

        // Перевіряємо чи наступна послідовність (у зворотному порядку)
        if (getSequence() != potionSequence + 1) {
            return false; // Не та послідовність
        }

        // Перевіряємо чи засвоєння 100%
        return getMastery() >= 100;
    }

    public void cleanUpAbilities() {
        for (Ability ability : abilities) {
            ability.cleanUp();
        }
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
        this.mastery = Math.clamp(mastery, 0, 100);
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

    public int getSanityLossScale() {
        return sanityLossScale;
    }

    public void setSanityLossScale(int sanityLossScale) {
        this.sanityLossScale = Math.clamp(sanityLossScale, 0, 100);
    }
}
