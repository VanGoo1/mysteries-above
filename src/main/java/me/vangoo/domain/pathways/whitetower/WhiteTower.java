package me.vangoo.domain.pathways.whitetower;

import me.vangoo.domain.pathways.door.abilities.*;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.pathways.door.abilities.Record;
import me.vangoo.domain.pathways.whitetower.abilities.EnhancedMentalAttributes;

import java.util.List;

public class WhiteTower extends Pathway {
    public WhiteTower(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new DivinationArts(60), new EnhancedMentalAttributes()));
    }
}
