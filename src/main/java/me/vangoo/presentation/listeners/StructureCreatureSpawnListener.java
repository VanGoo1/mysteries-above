package me.vangoo.presentation.listeners;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureSelector;
import me.vangoo.infrastructure.creatures.CreatureSpawner;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Спавнить apex-істот біля кастомних/ванільних структур, реагуючи на генерацію їхнього луту
 * (LootGenerateEvent). Окремий лістенер: спавн ІСТОТИ — не генерація предмета (тому не в
 * VanillaStructureLootListener).
 *
 * <p>Захист від надмірного спавну: LootGenerateEvent спрацьовує для кожного контейнера (скрині),
 * тому щільна структура може роллити шанс багато разів. Per-chunk cooldown гарантує, що в одному
 * чанку протягом {@link #SPAWN_COOLDOWN_MS} мс з'явиться не більше однієї істоти.
 */
public class StructureCreatureSpawnListener implements Listener {

    private static final long SPAWN_COOLDOWN_MS = 300_000L; // 5 хвилин

    private final CreatureSelector selector;
    private final CreatureSpawner spawner;
    private final Random random = new Random();
    private final Map<Long, Long> lastSpawnByChunk = new HashMap<>();

    public StructureCreatureSpawnListener(CreatureSelector selector, CreatureSpawner spawner) {
        this.selector = selector;
        this.spawner = spawner;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        Location loc = event.getLootContext().getLocation();
        if (loc == null || loc.getWorld() == null) return;

        // Per-chunk cooldown: не більше одного спавну на чанк за SPAWN_COOLDOWN_MS мс
        // getChunkKey() недоступний у 1.21.1 — обчислюємо вручну (ті самі біти)
        long chunkKey = ((long) loc.getChunk().getX() << 32) | (loc.getChunk().getZ() & 0xFFFFFFFFL);
        long now = System.currentTimeMillis();
        if (now - lastSpawnByChunk.getOrDefault(chunkKey, 0L) < SPAWN_COOLDOWN_MS) return;

        String key = event.getLootTable().getKey().toString();
        Optional<CreatureDefinition> pick = selector.pickForStructure(key, random.nextDouble());
        if (pick.isEmpty()) return;

        spawner.spawn(pick.get(), safeSpawnLocation(loc));

        // Прибираємо застарілі записи перед вставкою, щоб карта не росла безмежно
        if (lastSpawnByChunk.size() > 256) {
            lastSpawnByChunk.values().removeIf(t -> now - t > SPAWN_COOLDOWN_MS);
        }
        lastSpawnByChunk.put(chunkKey, now);
    }

    /**
     * Шукає вільне місце (2 блоки заввишки) поряд із контейнером, щоб не заспавнити істоту
     * всередині стіни або скрині.
     */
    private Location safeSpawnLocation(Location origin) {
        int[][] offsets = { {0, 1, 0}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {2, 1, 0}, {0, 1, 2} };
        for (int[] o : offsets) {
            Location cand = origin.clone().add(o[0] + 0.5, o[1], o[2] + 0.5);
            if (cand.getBlock().isPassable() && cand.clone().add(0, 1, 0).getBlock().isPassable()) {
                return cand;
            }
        }
        return origin.clone().add(0.5, 1.0, 0.5);
    }
}
