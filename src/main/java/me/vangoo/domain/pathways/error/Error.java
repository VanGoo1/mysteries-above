package me.vangoo.domain.pathways.error;

import me.vangoo.domain.pathways.error.abilities.FractureOfRealitiesAbility;
import me.vangoo.domain.pathways.error.abilities.ShadowTheft;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;

import java.util.List;

public class Error extends Pathway {
    public Error(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new ShadowTheft(), new FractureOfRealitiesAbility()));
    }
}
