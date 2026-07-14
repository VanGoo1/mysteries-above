package me.vangoo.pathways.stub;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;

import java.util.List;

/**
 * Шлях-заглушка: канонічні назви послідовностей і група, але БЕЗ здібностей.
 * Дає зілля/Характеристики (через {@link StubPotions}) і робить церкви цього
 * домену функціональними до повної реалізації шляху.
 */
public class StubPathway extends Pathway {

    public StubPathway(PathwayGroup group, String name, List<String> sequenceNames) {
        super(group, name, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        // Заглушка — здібностей ще немає.
    }
}
