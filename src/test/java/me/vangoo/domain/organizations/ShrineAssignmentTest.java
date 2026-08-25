package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShrineAssignmentTest {

    private static final List<String> CHURCHES = List.of(
            "church-evernight", "church-lord-of-storms", "church-knowledge-wisdom",
            "church-eternal-sun", "church-fool");

    /**
     * Головна властивість: той самий шрайн завжди веде в той самий храм. На ній тримається
     * те, що реєстру шрайнів у плагіна немає взагалі — відповідь щоразу перераховується.
     */
    @Test
    void sameCoordinatesAlwaysPickTheSameChurch() {
        for (int i = 0; i < 50; i++) {
            assertEquals(ShrineAssignment.pick(CHURCHES, 1234, -5678),
                    ShrineAssignment.pick(CHURCHES, 1234, -5678));
        }
    }

    @Test
    void emptyRegistryPicksNothing() {
        assertTrue(ShrineAssignment.pick(List.of(), 0, 0).isEmpty());
        assertTrue(ShrineAssignment.pick(null, 0, 0).isEmpty());
    }

    @Test
    void alwaysPicksFromTheGivenList() {
        for (int x = -200; x <= 200; x += 7) {
            String picked = ShrineAssignment.pick(CHURCHES, x, x * 3).orElseThrow();
            assertTrue(CHURCHES.contains(picked), picked);
        }
    }

    /**
     * Села стоять на регулярній сітці, тож лінійний хеш роздав би сусіднім селам той самий
     * залишок за модулем і цілі краї карти дістались би одній церкві. Перевіряємо саме на
     * сітці з кроком села (~34 чанки ≈ 544 блоки), а не на випадкових координатах.
     */
    @Test
    void villageGridSpreadsAcrossAllChurches() {
        Map<String, Integer> hits = new HashMap<>();
        for (int gx = 0; gx < 12; gx++) {
            for (int gz = 0; gz < 12; gz++) {
                String picked = ShrineAssignment.pick(CHURCHES, gx * 544, gz * 544).orElseThrow();
                hits.merge(picked, 1, Integer::sum);
            }
        }
        assertEquals(CHURCHES.size(), hits.size(), "кожна церква має трапитись: " + hits);
        int expected = 144 / CHURCHES.size();
        for (Map.Entry<String, Integer> entry : hits.entrySet()) {
            assertTrue(entry.getValue() >= expected / 3,
                    "перекіс розподілу на " + entry.getKey() + ": " + hits);
        }
    }

    /** Один шрайн — одна церква, але сусідні шрайни не мусять вести в той самий храм. */
    @Test
    void neighbouringVillagesDoNotCollapseToOneChurch() {
        long distinct = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> ShrineAssignment.pick(CHURCHES, i * 544, 0).orElseThrow())
                .distinct()
                .count();
        assertTrue(distinct > 1, "вісім сіл поспіль дали одну церкву");
    }
}
