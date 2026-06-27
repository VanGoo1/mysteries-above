package me.vangoo.presentation.listeners;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureSelector;
import me.vangoo.infrastructure.creatures.CreatureSpawner;
import me.vangoo.infrastructure.creatures.SafeLocations;
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
    private final Map<String, Long> lastSpawnByChunk = new HashMap<>();

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
        org.bukkit.Chunk chunk = loc.getChunk();
        long chunkXZ = ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
        String chunkKey = loc.getWorld().getUID() + "@" + chunkXZ;
        long now = System.currentTimeMillis();
        if (now - lastSpawnByChunk.getOrDefault(chunkKey, 0L) < SPAWN_COOLDOWN_MS) return;

        String key = event.getLootTable().getKey().toString();
        Optional<CreatureDefinition> pick = selector.pickForStructure(key, random.nextDouble());
        if (pick.isEmpty()) return;

        spawner.spawn(pick.get(), SafeLocations.passableNear(loc));

        // Прибираємо застарілі записи перед вставкою, щоб карта не росла безмежно
        if (lastSpawnByChunk.size() > 256) {
            lastSpawnByChunk.values().removeIf(t -> now - t > SPAWN_COOLDOWN_MS);
        }
        lastSpawnByChunk.put(chunkKey, now);
    }
}
