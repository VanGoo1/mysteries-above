package me.vangoo.pathways.fool;

import me.vangoo.pathways.door.abilities.Burning;
import me.vangoo.pathways.fool.abilities.*;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.pathways.common.abilities.RitualMagic;

import java.util.List;

/**
 * Fool Pathway (Шлях Блазня)
 *
 * The Fool pathway belongs to the Lord of Mysteries group.
 * It progresses from divination and information-gathering (Seer)
 * through physical enhancement and trickery (Clown, Magician)
 * to identity manipulation (Faceless) and spirit control (Marionettist).
 *
 * Sequences:
 *   9: Seer (Провидець) — divination, spirit vision, enhanced danger intuition
 *      (premonition, see-behind-door, action prediction, lethal dodge)
 *   8: Clown (Клоун) — passive paper throw (PaperCutter), expression mask (toggle drain),
 *      clown agility (fall immunity + wall climb)
 *   7: Magician (Фокусник) — flame jump (fire-immune landing), paper doll substitution,
 *      air bullet, damage-transfer heal, paper-as-weapons (evolves PaperCutter),
 *      underwater breathing, illusion creation
 *   6: Faceless (Безликий) — shapeshifting, graceful descent (replaces agility)
 *   5: Marionettist (Маріонетник) — marionette control (mastery-based fixation,
 *      break-on-damage, mob swap), swap menu, thread sight (see invisibles)
 *
 * Each ability owns its own event reactions via context subscriptions under a DEDICATED
 * subscription key (the SpiritualIntuition/TravellersDoor pattern), so one ability's
 * unsubscribeAll never clobbers another's: fall immunity (ClownAgility), lethal dodge
 * (DangerIntuition), doll absorb (PaperSubstitution), recent-damage tracking for heal
 * (DamageTransfer, one broad sub), paper-weapon on-hit (PaperWeaponry, one broad sub).
 * The only Fool listener is PaperThrowListener — a single-purpose input handler for the
 * "hold plain paper + right-click to throw" mechanic, which has no ability-cast entry point.
 */
public class Fool extends Pathway {

    public Fool(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        // Sequence 9: Seer (Провидець)
        sequenceAbilities.put(9, List.of(
                new DivinationArts(),
                new SeerSpiritVision(),
                new DangerIntuition(),
                new RitualMagic()
        ));

        // Sequence 8: Clown (Клоун)
        sequenceAbilities.put(8, List.of(
                new PaperCutter(),
                new ExpressionControl(),
                new ClownAgility()
        ));

        // Sequence 7: Magician (Фокусник)
        sequenceAbilities.put(7, List.of(
                new FlameJump(),
                new PaperSubstitution(),
                new AirBullet(),
                new DamageTransfer(),
                new PaperWeaponry(),   // еволюція PaperCutter (спільний identity) — заміняє Seq8-версію
                new AquaticBreath(),
                new IllusionCreation(),
                new Burning()
        ));

        // Sequence 6: Faceless (Безликий)
        sequenceAbilities.put(6, List.of(
                new Shapeshifting(),
                new GracefulDescent()  // Replaces ClownAgility via shared AbilityIdentity
        ));

        // Sequence 5: Marionettist (Маріонетник)
        // Обидві здібності поділяють той самий MarionettistControl (спільний реєстр маріонеток).
        MarionettistControl marionettistControl = new MarionettistControl();
        sequenceAbilities.put(5, List.of(
                marionettistControl,
                new MarionetteSwapMenu(marionettistControl),
                new ThreadSight()
        ));
    }
}
