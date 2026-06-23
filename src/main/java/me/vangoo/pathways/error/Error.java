package me.vangoo.pathways.error;

import me.vangoo.pathways.error.abilities.FractureOfRealitiesAbility;
import me.vangoo.pathways.error.abilities.ShadowTheft;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.pathways.error.abilities.SuperiorObservation;
import me.vangoo.pathways.error.abilities.SwindlerCharm;
import me.vangoo.pathways.justiciar.abilities.PhysicalEnhancement;
import me.vangoo.pathways.whitetower.abilities.Agility;
import me.vangoo.pathways.whitetower.abilities.CombatProficiency;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Error extends Pathway {
    public Error(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new ShadowTheft(), new SuperiorObservation() , new CombatProficiency(), new PhysicalEnhancement(
                "Фізичні посилення",
                "Ви отримуєте сильне тіло",
                3)));
        sequenceAbilities.put(8, List.of(new Agility(), new SwindlerCharm()));
    }
}
