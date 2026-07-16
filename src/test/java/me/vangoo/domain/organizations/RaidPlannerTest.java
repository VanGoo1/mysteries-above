package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RaidPlannerTest {

    private static final Map<String, Integer> VAULT = Map.of(
            "custom:herb", 10,
            "custom:blood", 4,
            "recipe:Door:9", 1,
            "characteristic:Door:9", 2);

    @Test
    void intelLowersAlarmChance() {
        assertEquals(0.04, RaidPlanner.alarmChancePerSecond(0.04, false, 0.5), 1e-9);
        assertEquals(0.02, RaidPlanner.alarmChancePerSecond(0.04, true, 0.5), 1e-9);
    }

    @Test
    void lootNeverExceedsVaultAmounts() {
        for (int seed = 0; seed < 50; seed++) {
            Map<String, Integer> loot = RaidPlanner.rollLoot(VAULT, 5, true, new Random(seed));
            loot.forEach((key, amount) -> {
                assertTrue(amount > 0);
                assertTrue(amount <= VAULT.get(key), key + " over-looted");
            });
        }
    }

    @Test
    void characteristicsAreJackpotOnlyWithIntel() {
        for (int seed = 0; seed < 200; seed++) {
            Map<String, Integer> loot = RaidPlanner.rollLoot(VAULT, 5, false, new Random(seed));
            assertTrue(loot.keySet().stream().noneMatch(k -> k.startsWith("characteristic:")),
                    "characteristic looted without intel, seed=" + seed);
        }
    }

    @Test
    void emptyVaultYieldsEmptyLoot() {
        assertTrue(RaidPlanner.rollLoot(Map.of(), 3, true, new Random(1)).isEmpty());
    }

    @Test
    void picksBoundTotalDraws() {
        for (int seed = 0; seed < 50; seed++) {
            Map<String, Integer> loot = RaidPlanner.rollLoot(VAULT, 2, true, new Random(seed));
            int totalDrawn = loot.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(totalDrawn <= 2 * 2, "each pick draws at most 2 units");
            assertTrue(loot.size() <= 2);
        }
    }
}
