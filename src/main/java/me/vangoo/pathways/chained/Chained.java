package me.vangoo.pathways.chained;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;

import java.util.List;

/**
 * Chained Pathway (Закутий) — заготовка під майбутню реалізацію.
 * Здібностей ще немає: наповнюється в {@link #initializeAbilities()}.
 * Група передається з PathwayManager.
 */
public class Chained extends Pathway {

    public Chained(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        // Заготовка — здібностей ще немає.
    }
}
