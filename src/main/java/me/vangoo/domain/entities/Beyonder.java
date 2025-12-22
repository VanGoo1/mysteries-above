package me.vangoo.domain.entities;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.services.SpiritualityCalculator;
import me.vangoo.domain.valueobjects.Mastery;
import me.vangoo.domain.valueobjects.SanityLoss;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Beyonder {
    private final UUID playerId;
    private Pathway pathway;
    private Sequence sequence;
    private Mastery mastery;
    private Spirituality spirituality;
    private SanityLoss sanityLoss;

    private transient List<Ability> abilities;
    private transient SpiritualityCalculator spiritualityCalculator;

    public Beyonder(UUID playerId, Sequence sequence, Pathway pathway) {
        if (playerId == null) {
            throw new IllegalArgumentException("Player ID cannot be null");
        }
        if (pathway == null) {
            throw new IllegalArgumentException("Pathway cannot be null");
        }
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence cannot be null");
        }

        this.playerId = playerId;
        this.sequence = sequence;
        this.pathway = pathway;
        this.mastery = Mastery.zero();
        this.sanityLoss = SanityLoss.none();
        this.abilities = new ArrayList<>();
        this.spiritualityCalculator = new SpiritualityCalculator();

        // Calculate initial spirituality
        updateMaximumSpirituality();
        this.spirituality = Spirituality.empty(spirituality.maximum());

        // Load abilities
        loadAbilities();
    }

    private void updateMaximumSpirituality() {
        int newMax = spiritualityCalculator.calculateMaximumSpirituality(sequence, mastery);

        if (spirituality == null) {
            spirituality = Spirituality.empty(newMax);
        } else {
            spirituality = spirituality.withNewMaximum(newMax);
        }
    }

    private void loadAbilities() {
        abilities = new ArrayList<>();
        for (int seq : sequence.getAllSequencesUpToCurrent()) {
            abilities.addAll(pathway.GetAbilitiesForSequence(seq));
        }
    }

    /**
     * Initialize transient fields after deserialization
     */
    public void initializeTransientFields() {
        if (spiritualityCalculator == null) {
            spiritualityCalculator = new SpiritualityCalculator();
        }
        if (abilities == null || abilities.isEmpty()) {
            loadAbilities();
        }
    }

    /**
     * Restore beyonder from persistence (for deserialization)
     */
    public static Beyonder restore(
            UUID playerId,
            Pathway pathway,
            Sequence sequence,
            Mastery mastery,
            int currentSpirituality,
            SanityLoss sanityLoss) {
        Beyonder beyonder = new Beyonder(playerId, sequence, pathway);
        beyonder.mastery = mastery;
        beyonder.sanityLoss = sanityLoss;
        beyonder.updateMaximumSpirituality();
        beyonder.spirituality = Spirituality.of(
                Math.min(currentSpirituality, beyonder.spirituality.maximum()),
                beyonder.spirituality.maximum());
        return beyonder;
    }

    public boolean canAdvance() {
        return mastery.canAdvance() && sequence.canAdvance();
    }

    public boolean canConsumePotion(Pathway potionPathway, Sequence potionSequence) {
        // Must be same pathway group
        if (potionPathway.getGroup() != pathway.getGroup()) {
            return false;
        }

        // Must be next sequence (potion sequence + 1 = current sequence)
        if (sequence.level() != potionSequence.level() + 1) {
            return false;
        }

        return mastery.canAdvance();
    }

    public void cleanUpAbilities() {
        if (abilities != null) {
            for (Ability ability : abilities) {
                ability.cleanUp();
            }
        }
    }

    public void advance() {
        if (!canAdvance()) {
            throw new IllegalStateException(
                    "Cannot advance: sequence=" + sequence + ", mastery=" + mastery);
        }

        sequence = sequence.advance();
        mastery = mastery.reset();
        updateMaximumSpirituality();
        loadAbilities();
    }

    public void useAbility(Ability ability, int spiritualityCost) {
        if (!spirituality.hasSufficient(spiritualityCost)) {
            throw new IllegalStateException(
                    "Insufficient spirituality: required=" + spiritualityCost +
                            ", available=" + spirituality.current());
        }
        if (!abilities.contains(ability))
            throw new IllegalStateException("Beyonder does not have ability: " + ability);

        spirituality = spirituality.decrement(spiritualityCost);

        // Increment mastery when using abilities
        mastery = mastery.increment();
        updateMaximumSpirituality();

        // Check for critical spirituality and increase sanity loss
        if (spirituality.isCritical()) {
            increaseSanityLoss(1);
        }
    }

    public void regenerateSpirituality() {
        if (!spirituality.isFull()) {
            int regenRate = spiritualityCalculator.calculateRegenerationRate(sequence);
            spirituality = spirituality.regenerate(regenRate);
        }
    }

    public void setSpirituality(Spirituality spirituality) {
        this.spirituality = spirituality;
    }

    public void increaseSanityLoss(int amount) {
        sanityLoss = sanityLoss.increase(amount);
    }

    public void decreaseSanityLoss(int amount) {
        sanityLoss = sanityLoss.decrease(amount);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Pathway getPathway() {
        return pathway;
    }

    public Sequence getSequence() {
        return sequence;
    }

    public int getSequenceLevel() {
        return sequence.level();
    }

    public Mastery getMastery() {
        return mastery;
    }

    public int getMasteryValue() {
        return mastery.value();
    }

    public void setMastery(Mastery mastery) {
        this.mastery = mastery;
        updateMaximumSpirituality();
    }

    public Spirituality getSpirituality() {
        return spirituality;
    }

    public int getSpiritualityValue() {
        return spirituality.current();
    }

    public int getMaxSpirituality() {
        return spirituality.maximum();
    }

    public SanityLoss getSanityLoss() {
        return sanityLoss;
    }

    public int getSanityLossScale() {
        return sanityLoss.scale();
    }

    public List<Ability> getAbilities() {
        return abilities != null ? Collections.unmodifiableList(abilities) : Collections.emptyList();
    }
}
