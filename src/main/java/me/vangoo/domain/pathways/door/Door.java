package me.vangoo.domain.pathways.door;

import me.vangoo.domain.pathways.door.abilities.*;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.pathways.door.abilities.Record;

import java.util.List;

public class Door extends Pathway {
    public Door(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new DoorOpening()));
        sequenceAbilities.put(8, List.of(new Flash(), new EscapeTrick(), new Burning(), new ElectricShock()));
        sequenceAbilities.put(7, List.of(new DivinationArts(), new SpiritualVision(), new SpiritualIntuition(), new AntiDivination()));
        sequenceAbilities.put(6, List.of(new Record(), new DecryptPatterns()));
    }
}
