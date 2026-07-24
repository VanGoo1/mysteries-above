package me.vangoo.pathways.sun;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.pathways.common.abilities.RitualMagic;
import me.vangoo.pathways.justiciar.abilities.PhysicalEnhancement;
import me.vangoo.pathways.sun.abilities.Blessing;
import me.vangoo.pathways.sun.abilities.CleaveOfPurification;
import me.vangoo.pathways.sun.abilities.Contract;
import me.vangoo.pathways.sun.abilities.Daytime;
import me.vangoo.pathways.sun.abilities.EvilDetection;
import me.vangoo.pathways.sun.abilities.FireOfLight;
import me.vangoo.pathways.sun.abilities.HolyLightSummoning;
import me.vangoo.pathways.sun.abilities.HolyOath;
import me.vangoo.pathways.sun.abilities.HolyWave;
import me.vangoo.pathways.sun.abilities.NightVision;
import me.vangoo.pathways.sun.abilities.Notarization;
import me.vangoo.pathways.sun.abilities.ResistanceToHorror;
import me.vangoo.pathways.sun.abilities.Singing;
import me.vangoo.pathways.sun.abilities.SunHalo;
import me.vangoo.pathways.sun.abilities.SunHolyWater;
import me.vangoo.pathways.sun.abilities.Sunshine;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Sun extends Pathway {

    public Sun(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new Singing(), new PhysicalEnhancement(
                "Фізичні посилення",
                "Світло наповнює ваше тіло, зміцнюючи його та прискорюючи рухи.",
                3,
                PotionEffectType.SPEED)));
        sequenceAbilities.put(8, List.of(new Sunshine(), new Blessing(), new RitualMagic(), new EvilDetection(),
                new Daytime(), new NightVision()));
        sequenceAbilities.put(7, List.of(new HolyLightSummoning(), new SunHalo(),
                new CleaveOfPurification(), new ResistanceToHorror(), new SunHolyWater(),
                new HolyOath(), new FireOfLight()));
        sequenceAbilities.put(6, List.of(new Notarization(), new Contract()));
        sequenceAbilities.put(5, List.of(new HolyWave()));
    }
}
