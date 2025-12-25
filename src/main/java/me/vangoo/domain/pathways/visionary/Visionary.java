package me.vangoo.domain.pathways.visionary;

import me.vangoo.domain.pathways.visionary.abilities.*;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.valueobjects.Sequence;

import java.util.List;

public class Visionary extends Pathway {
    public Visionary(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new GoodMemory(), new ScanGaze(), new SharpVision()));
        sequenceAbilities.put(8, List.of(new DangerSense()));
        sequenceAbilities.put(7, List.of(new SurgeOfInsanity(), new Appeasement()));
    }
}
