package me.vangoo.domain.entities;

import me.vangoo.domain.abilities.core.Ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Pathway {
    /** Кожен шлях має рівно 10 послідовностей (0 = найсильніша → 9 = найслабша). */
    public static final int SEQUENCE_COUNT = 10;

    protected final Map<Integer, List<Ability>> sequenceAbilities;
    private final List<String> sequenceNames;
    private final PathwayGroup group;
    private final String name;

    public Pathway(PathwayGroup group, List<String> sequenceNames) {
        name = this.getClass().getSimpleName();
        if (sequenceNames.size() != SEQUENCE_COUNT) {
            throw new IllegalArgumentException("Pathway " + name + " must declare " + SEQUENCE_COUNT
                    + " sequence names (0-9), got " + sequenceNames.size());
        }
        this.group = group;
        this.sequenceNames = sequenceNames;
        sequenceAbilities = new HashMap<>();
        initializeAbilities();
    }

    public String getSequenceName(int sequence) {
        return sequenceNames.get(sequence);
    }

    protected abstract void initializeAbilities();

    public List<Ability> GetAbilitiesForSequence(int sequence) {
        return sequenceAbilities.getOrDefault(sequence, new ArrayList<>());
    }

    public PathwayGroup getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }
}
