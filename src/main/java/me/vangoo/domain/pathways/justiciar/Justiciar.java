package me.vangoo.domain.pathways.justiciar;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.pathways.justiciar.abilities.*;

import java.util.List;

public class Justiciar extends Pathway {
    public Justiciar(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new Authority(),new ArbitersGaze(), new PhysicalEnhancement()));
        sequenceAbilities.put(8, List.of(new AreaOfJurisdiction(), new Recognition(), new Intuition()));
        sequenceAbilities.put(7, List.of(new WhipOfPain(), new PsychicPiercing(), new BrandOfRestraint(), new PsychicLashing()));
        sequenceAbilities.put(6, List.of(new Verdict(), new PowerProhibition(), new SpawnProhibition()));
    }
}
