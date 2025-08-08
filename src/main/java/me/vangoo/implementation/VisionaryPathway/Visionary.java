package me.vangoo.implementation.VisionaryPathway;

import me.vangoo.pathways.Pathway;
import me.vangoo.pathways.PathwayGroup;

import java.util.List;

public class Visionary extends Pathway {
    public Visionary(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of());

    }
}
