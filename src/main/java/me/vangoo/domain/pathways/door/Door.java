package me.vangoo.domain.pathways.door;

import me.vangoo.domain.pathways.door.abilities.DoorOpening;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;

import java.util.List;

public class Door extends Pathway {
    public Door(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new DoorOpening()));
    }
}
