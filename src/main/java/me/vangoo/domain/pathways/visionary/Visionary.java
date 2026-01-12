package me.vangoo.domain.pathways.visionary;

import me.vangoo.domain.pathways.justiciar.abilities.PhysicalEnhancement;
import me.vangoo.domain.pathways.visionary.abilities.*;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Visionary extends Pathway {
    public Visionary(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new GoodMemory(), new ScanGaze(), new SharpVision()));
        sequenceAbilities.put(8, List.of(new DangerSense(), new IntentReader()));
        sequenceAbilities.put(7, List.of(new SurgeOfInsanity(), new Appeasement(), new Telepathy(), new PhysicalEnhancement(
                "Фізичні посилення",
                "Ви отримуєте сильне тіло та надзвичайну швидкість.",
                3,
                PotionEffectType.SPEED), new ScanGazePassive(), new PsychicCue()));
        sequenceAbilities.put(6, List.of(new PsychologicalInvisibility(), new BattleHypnotism(), new DragonScale()));
        sequenceAbilities.put(5, List.of(new Alteration(), new Guidance(), new DreamTraversal()));

    }
}
