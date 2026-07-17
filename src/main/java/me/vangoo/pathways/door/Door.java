package me.vangoo.pathways.door;

import me.vangoo.pathways.door.abilities.*;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.pathways.common.abilities.RitualMagic;
import me.vangoo.pathways.door.abilities.Record;
import me.vangoo.pathways.justiciar.abilities.PhysicalEnhancement;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Door extends Pathway {
    public Door(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new DoorOpening(), new RitualMagic()));
        sequenceAbilities.put(8, List.of(new Flash(), new EscapeTrick(), new Burning(), new ElectricShock()));
        sequenceAbilities.put(7, List.of(new DivinationArts(), new SpiritualVision(), new SpiritualIntuition(), new AntiDivination()));
        sequenceAbilities.put(6, List.of(new Record(), new DecryptPatterns(), new PhysicalEnhancement(
                "Фізичні посилення",
                "Ви отримуєте сильне тіло та надзвичайну швидкість.",
                3,
                PotionEffectType.SPEED)));
        sequenceAbilities.put(5, List.of(new TravellersDoor(), new Blink()));
    }
}
