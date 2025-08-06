package me.vangoo.implementation.ErrorPathway;

import me.vangoo.implementation.ErrorPathway.abilities.FractureOfRealitiesAbility;
import me.vangoo.implementation.ErrorPathway.abilities.ShadowTheft;
import me.vangoo.pathways.Pathway;
import me.vangoo.pathways.PathwayGroup;

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
