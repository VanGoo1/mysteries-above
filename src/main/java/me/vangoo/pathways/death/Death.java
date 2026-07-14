package me.vangoo.pathways.death;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;

import java.util.List;

/**
 * Death Pathway (Смерть) — заготовка під майбутню реалізацію.
 * Здібностей ще немає: наповнюється в {@link #initializeAbilities()}.
 * Група передається з PathwayManager.
 */
public class Death extends Pathway {

    public Death(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        // Заготовка — здібностей ще немає.
    }
}
