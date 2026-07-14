package me.vangoo.pathways.moon;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;

import java.util.List;

/**
 * Moon Pathway (Місяць) — заготовка під майбутню реалізацію.
 * Здібностей ще немає: наповнюється в {@link #initializeAbilities()}.
 * Група передається з PathwayManager.
 */
public class Moon extends Pathway {

    public Moon(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        // Заготовка — здібностей ще немає.
    }
}
