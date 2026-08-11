package me.vangoo.pathways.death;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.valueobjects.CorpseCollectorLore;
import me.vangoo.domain.valueobjects.GatekeeperLore;
import me.vangoo.domain.valueobjects.GravediggerLore;
import me.vangoo.domain.valueobjects.SpiritGuideLore;
import me.vangoo.domain.valueobjects.SpiritMediumLore;
import me.vangoo.pathways.common.abilities.PhysicalEnhancement;
import me.vangoo.pathways.common.abilities.RitualMagic;
import me.vangoo.pathways.death.abilities.CadavericResilience;
import me.vangoo.pathways.death.abilities.CorpseGuise;
import me.vangoo.pathways.death.abilities.DeathSight;
import me.vangoo.pathways.death.abilities.DoorToTheUnderworld;
import me.vangoo.pathways.death.abilities.EyeOfDeath;
import me.vangoo.pathways.death.abilities.GatekeeperStep;
import me.vangoo.pathways.death.abilities.GloomyPresence;
import me.vangoo.pathways.death.abilities.InternalUnderworld;
import me.vangoo.pathways.death.abilities.LanguageOfTheDead;
import me.vangoo.pathways.death.abilities.LingeringSouls;
import me.vangoo.pathways.death.abilities.Resurrection;
import me.vangoo.pathways.death.abilities.SpiritChanneling;
import me.vangoo.pathways.death.abilities.SpiritCommunication;
import me.vangoo.pathways.death.abilities.SpiritPact;
import me.vangoo.pathways.death.abilities.SpiritPerception;
import me.vangoo.pathways.death.abilities.SpiritVision;
import me.vangoo.pathways.death.abilities.SpiritWorldKnowledge;
import me.vangoo.pathways.death.abilities.UndeadRetinue;
import me.vangoo.pathways.death.abilities.UndeadKnowledge;
import me.vangoo.pathways.death.abilities.VoiceOfTheDead;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Death Pathway (Смерть).
 * Група передається з PathwayManager.
 */
public class Death extends Pathway {

    /** Спільний identity фізики шляху: сильніша версія ЗАМІНЮЄ слабшу, а не стакається. */
    private static final String DEATH_PHYSIQUE = "death_physique";

    private UndeadRetinue retinue;

    public Death(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    /** Потрібен ззовні для відновлення почту при вході на сервер — див. {@code RetinueRestoreListener}. */
    public UndeadRetinue getRetinue() {
        return retinue;
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(
                new UndeadKnowledge(),
                new CadavericResilience(),
                new GloomyPresence(),
                new DeathSight(),
                new SpiritVision(),
                new PhysicalEnhancement(
                        DEATH_PHYSIQUE,
                        "Тіло Трупозбирача",
                        "Ваше тіло холодніє, а присутність стає похмурою",
                        CorpseCollectorLore.PHYSIQUE_HP_BASE)));

        sequenceAbilities.put(8, List.of(
                new SpiritCommunication(),
                new EyeOfDeath(),
                new PhysicalEnhancement(
                        DEATH_PHYSIQUE,
                        "Тіло Могильника",
                        "Тіло могильника лишається холодним, але вже не важким: " +
                                "могили копаються легко, а кроки стають швидкими.",
                        GravediggerLore.PHYSIQUE_HP_BASE,
                        PotionEffectType.SPEED)));

        // Реєстр душ — спільний об'єкт двох здібностей Посл. 7 (пасивка пише, Голос мертвих читає),
        // тому створюється тут, а не в ServiceContainer.
        LingeringSouls souls = new LingeringSouls();
        // Почет — спільний стан входів Посл. 6 (Воскресіння, Підкорення, прикликання істот),
        // тому теж простий об'єкт, а не сервіс. Створюється тут, бо Прикликання духів
        // (Посл. 7) відкриває його розділ, доростаючи до Посл. 6. Персистентність (RetinueStore)
        // проставляється пізніше сеттером — ServiceContainer.
        retinue = new UndeadRetinue();

        sequenceAbilities.put(7, List.of(
                new SpiritPerception(souls),
                new VoiceOfTheDead(souls),
                new SpiritChanneling(retinue),
                new CorpseGuise(),
                new RitualMagic(),
                new PhysicalEnhancement(
                        DEATH_PHYSIQUE,
                        "Тіло Медіума",
                        "Тіло медіума майже не міцніє понад могильникове — сила тепер " +
                                "не в м'язах, а в духовності.",
                        SpiritMediumLore.PHYSIQUE_HP_BASE,
                        PotionEffectType.SPEED)));

        sequenceAbilities.put(6, List.of(
                new SpiritWorldKnowledge(),
                new LanguageOfTheDead(),
                new Resurrection(souls, retinue),
                new SpiritPact(retinue),
                new PhysicalEnhancement(
                        DEATH_PHYSIQUE,
                        "Тіло Провідника",
                        "Тіло провідника носить у собі чужу смерть: холод глибший, " +
                                "а рухи легші за живі.",
                        SpiritGuideLore.PHYSIQUE_HP_BASE,
                        PotionEffectType.SPEED)));

        sequenceAbilities.put(5, List.of(
                new GatekeeperStep(),
                new DoorToTheUnderworld(retinue),
                new InternalUnderworld(retinue),
                new PhysicalEnhancement(
                        DEATH_PHYSIQUE,
                        "Тіло Вартового",
                        "Тіло воротаря стоїть на межі життя й смерті: сила зростає, а " +
                                "кроки лишаються швидкими, як у провідника.",
                        GatekeeperLore.PHYSIQUE_HP_BASE,
                        PotionEffectType.SPEED)));
    }
}
