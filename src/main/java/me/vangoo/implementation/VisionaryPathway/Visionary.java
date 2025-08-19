package me.vangoo.implementation.VisionaryPathway;

import me.vangoo.implementation.VisionaryPathway.abilities.DangerSense;
import me.vangoo.implementation.VisionaryPathway.abilities.GoodMemory;
import me.vangoo.implementation.VisionaryPathway.abilities.ScanGaze;
import me.vangoo.implementation.VisionaryPathway.abilities.SharpVision;
import me.vangoo.pathways.Pathway;
import me.vangoo.pathways.PathwayGroup;

import java.util.List;

public class Visionary extends Pathway {
    public Visionary(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new GoodMemory(), new ScanGaze(), new SharpVision()));
        sequenceAbilities.put(8, List.of(new DangerSense()));
    }
}
