package me.vangoo.pathways.whitetower;

import me.vangoo.pathways.door.abilities.*;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.pathways.door.abilities.Record;
import me.vangoo.pathways.justiciar.abilities.PhysicalEnhancement;
import me.vangoo.pathways.whitetower.abilities.*;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class WhiteTower extends Pathway {
    public WhiteTower(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new DivinationArts(80), new EnhancedMentalAttributes()));
        sequenceAbilities.put(8, List.of());
        sequenceAbilities.put(7, List.of(new CombatProficiency(), new Agility(), new PhysicalEnhancement(
                "Фізичні посилення",
                "Ви отримуєте сильне тіло",
                3), new MysticalReenactment()));
        sequenceAbilities.put(6, List.of(new Analysis()));
        sequenceAbilities.put(5, List.of(new Spellcasting(), new MirrorCurse()));

    }
}
