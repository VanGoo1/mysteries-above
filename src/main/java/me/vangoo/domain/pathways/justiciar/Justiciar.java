package me.vangoo.domain.pathways.justiciar;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.pathways.door.abilities.AntiDivination;
import me.vangoo.domain.pathways.justiciar.abilities.*;
import me.vangoo.domain.pathways.whitetower.abilities.CombatProficiency;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Justiciar extends Pathway {
    public Justiciar(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new Authority(),new ArbitersGaze(),new CombatProficiency(), new PhysicalEnhancement(
                        "Фізичні посилення",
                        "Ви отримуєте могутнє тіло та надзвичайну швидкість.",
                        4,
                        PotionEffectType.SPEED)));
        sequenceAbilities.put(8, List.of(new AreaOfJurisdiction(), new Recognition(), new Intuition()));
        sequenceAbilities.put(7, List.of(new WhipOfPain(), new PsychicPiercing(), new BrandOfRestraint(), new PsychicLashing()));
        sequenceAbilities.put(6, List.of(new Verdict(), new PowerProhibition(), new SpawnProhibition()));
        sequenceAbilities.put(5, List.of(new Punishment(), new AntiDivination()));
    }
}
