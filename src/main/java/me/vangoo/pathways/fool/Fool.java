package me.vangoo.pathways.fool;

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
 *   9: Seer (Провидець) — divination, spirit vision, danger intuition
 *   8: Clown (Клоун) — paper daggers, expression mask, enhanced agility
 *   7: Magician (Фокусник) — flame jump, paper substitution, air bullet, damage transfer
 *   6: Faceless (Безликий) — shapeshifting, graceful descent (replaces agility)
 *   5: Marionettist (Маріонетник) — spirit thread control, marionette summon
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
                new DamageTransfer()
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
                new MarionetteSwapMenu(marionettistControl)
        ));
    }
}
