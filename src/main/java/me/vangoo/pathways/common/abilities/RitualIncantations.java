package me.vangoo.pathways.common.abilities;

import me.vangoo.domain.rituals.RitualType;

import java.util.List;
import java.util.Map;

/**
 * Канонічна 4-частинна структура заклинання (вікі LOTM): звертання до сутності →
 * молитва про ласку → прохання одним реченням → підсилення інгредієнтом.
 */
final class RitualIncantations {

    private static final Map<RitualType, List<String>> LINES = Map.of(
            RitualType.LUCK_PRAYER, List.of(
                    "Я молю силу прихованих сутностей удачі,",
                    "я молю про вашу ласку,",
                    "даруйте мені прихильність випадку,",
                    "золото, що сяє в пітьмі, підсили моє слово!"),
            RitualType.SANCTIFICATION, List.of(
                    "Я освячую тебе, знаряддя моє,",
                    "я очищаю тебе від скверни,",
                    "служи мені в цьому ритуалі,",
                    "залізо, вірне й холодне, підсили моє слово!"),
            RitualType.SACRIFICE, List.of(
                    "Я молю увагу прихованого сущого,",
                    "я молю прийняти мій дар,",
                    "прийміть цю жертву на вівтарі,",
                    "полум'я свічок, донеси мою офіру!"),
            RitualType.BESTOWMENT, List.of(
                    "Я молю силу за брамою царства,",
                    "я молю про вашу щедрість,",
                    "даруйте мені частку ваших володінь,",
                    "незериту, темний і вічний, підсили моє слово!"),
            RitualType.MEDIUMSHIP, List.of(
                    "Я кличу духів цього місця,",
                    "я молю про вашу відповідь,",
                    "повідайте, що тут відбулося,",
                    "кістко предків, підсили моє слово!"),
            RitualType.MIRROR_DIVINATION, List.of(
                    "Я молю силу таємниць,",
                    "я молю про одкровення,",
                    "покажіть мені всіх, хто ступав тут,",
                    "кристале, що бачив усе, підсили моє слово!"),
            RitualType.SPIRIT_WALL, List.of(
                    "Я звертаюсь до власної духовності,",
                    "я не кличу нікого, крім себе,",
                    "постань стіною навколо цього вівтаря,",
                    "лазурите, камене неба, підсили моє слово!")
    );

    static List<String> linesFor(RitualType type) {
        return LINES.get(type);
    }

    private RitualIncantations() {
    }
}
