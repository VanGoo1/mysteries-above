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
        this(group, null, sequenceNames);
    }

    public Pathway(PathwayGroup group, String explicitName, List<String> sequenceNames) {
        String resolved = explicitName != null ? explicitName : this.getClass().getSimpleName();
        if (sequenceNames.size() != SEQUENCE_COUNT) {
            throw new IllegalArgumentException("Pathway " + resolved + " must declare " + SEQUENCE_COUNT
                    + " sequence names (0-9), got " + sequenceNames.size());
        }
        this.name = resolved;
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

    /** true, якщо бодай одна послідовність має зареєстровану здібність. */
    public boolean hasAnyAbility() {
        return sequenceAbilities.values().stream().anyMatch(list -> !list.isEmpty());
    }

    public PathwayGroup getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }
}
