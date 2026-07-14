package me.vangoo.pathways.sun;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;

import java.util.List;

/**
 * Sun Pathway (Сонце) — заготовка під майбутню реалізацію.
 * Здібностей ще немає: наповнюється в {@link #initializeAbilities()}.
 * Група передається з PathwayManager.
 */
public class Sun extends Pathway {

    public Sun(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        // Заготовка — здібностей ще немає.
    }
}
