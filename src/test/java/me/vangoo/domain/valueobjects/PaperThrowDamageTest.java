package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperThrowDamageTest {

    @Test
    void damageGrowsAsSequenceDrops() {
        int weak = PaperThrowDamage.damageFor(Sequence.of(9));
        int strong = PaperThrowDamage.damageFor(Sequence.of(5));
        assertEquals(PaperThrowDamage.BASE_DAMAGE, weak, "Seq 9 = базова шкода без скейлу");
        assertTrue(strong > weak, "нижча послідовність б'є сильніше");
    }

    @Test
    void cooldownIsHalfSecond() {
        assertEquals(10, PaperThrowDamage.THROW_COOLDOWN_TICKS);
    }
}
