package me.vangoo.domain.pathways.error;

import me.vangoo.domain.pathways.error.abilities.FractureOfRealitiesAbility;
import me.vangoo.domain.pathways.error.abilities.ShadowTheft;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.pathways.error.abilities.SuperiorObservation;
import me.vangoo.domain.pathways.error.abilities.SwindlerCharm;
import me.vangoo.domain.pathways.justiciar.abilities.PhysicalEnhancement;
import me.vangoo.domain.pathways.whitetower.abilities.Agility;
import me.vangoo.domain.pathways.whitetower.abilities.CombatProficiency;
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
