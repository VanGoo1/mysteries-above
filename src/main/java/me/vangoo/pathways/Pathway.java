package me.vangoo.pathways;

import me.vangoo.abilities.Ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Pathway {
    protected final Map<Integer, List<Ability>> sequenceAbilities;
    private final List<String> sequenceNames;
    private final PathwayGroup group;
    private String name;

    public Pathway(PathwayGroup group, List<String> sequenceNames) {
        name = this.getClass().getName();
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
