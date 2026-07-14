package me.vangoo.pathways.abyss;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;

import java.util.List;

/**
 * Abyss Pathway (Безодня) — заготовка під майбутню реалізацію.
 * Здібностей ще немає: наповнюється в {@link #initializeAbilities()}.
 * Група передається з PathwayManager.
 */
public class Abyss extends Pathway {

    public Abyss(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        // Заготовка — здібностей ще немає.
    }
}
