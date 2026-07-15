package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InstitutionTest {

    @Test
    void fullAccessSupportsEverySequence() {
        PathwayAccess a = PathwayAccess.full("Door");
        assertTrue(a.isFull());
        assertEquals(0, a.minSequence());
        assertTrue(a.supportsSequence(0));
        assertTrue(a.supportsSequence(9));
    }

    @Test
    void partialAccessCapsAtMinSequence() {
        PathwayAccess a = PathwayAccess.partial("Fool", 3);
        assertFalse(a.isFull());
        assertTrue(a.supportsSequence(3));
        assertTrue(a.supportsSequence(9));
        assertFalse(a.supportsSequence(2));
    }

    @Test
    void accessValidatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> PathwayAccess.partial("Fool", -1));
        assertThrows(IllegalArgumentException.class, () -> PathwayAccess.partial("Fool", 10));
        assertThrows(IllegalArgumentException.class, () -> PathwayAccess.full(" "));
    }

    @Test
    void churchRequiresAccessesAndAcceptsPathlessPlayers() {
        assertThrows(IllegalArgumentException.class, () -> new Institution(
                "church-x", InstitutionType.CHURCH, "X", "лор", List.of()));

        Institution church = new Institution("church-evernight", InstitutionType.CHURCH,
                "Церква Богині Вічної Ночі", "лор",
                List.of(PathwayAccess.full("Darkness"), PathwayAccess.partial("Fool", 3)));
        assertTrue(church.acceptsPathway(null));            // без шляху — можна
        assertTrue(church.acceptsPathway("Fool"));          // PARTIAL не блокує вступ
        assertFalse(church.acceptsPathway("Error"));        // чужий шлях
        assertFalse(church.acceptsAnyPathway());
        assertEquals(Optional.of(3),
                church.accessFor("fool").map(PathwayAccess::minSequence)); // case-insensitive
    }

    @Test
    void secretOrderWithNoAccessesAcceptsAnyone() {
        Institution order = new Institution("order-mirror-people", InstitutionType.SECRET_ORDER,
                "Дзеркальні Люди", "лор", List.of());
        assertTrue(order.acceptsAnyPathway());
        assertTrue(order.acceptsPathway("Error"));
        assertTrue(order.acceptsPathway(null));
    }
}
