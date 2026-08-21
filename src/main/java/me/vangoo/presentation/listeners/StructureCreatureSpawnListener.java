package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.creatures.ApexGate;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureSelector;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.creatures.SafeLocations;
import me.vangoo.infrastructure.creatures.SpawnDistance;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.plugin.Plugin;

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
 *
 * <p>Три правила проти «ваншоту зі скрині», на які скаржились гравці:
 * <ol>
 *   <li>apex приходить лише до гравця, що вже підійшов до Посл. 5 ({@link ApexGate});</li>
 *   <li>місце спавну мусить бути валідним і не впритул — інакше істота не з'являється взагалі
 *       (раніше фолбек ставив її в стіну);</li>
 *   <li>поява телеграфиться звуком і повідомленням за {@link #TELEGRAPH_TICKS} тіків, щоб гравець
 *       устиг відступити.</li>
 * </ol>
 */
public class StructureCreatureSpawnListener implements Listener {

    private static final long SPAWN_COOLDOWN_MS = 300_000L; // 5 хвилин
    private static final int MIN_SPAWN_RADIUS = 5;
    private static final int MAX_SPAWN_RADIUS = 10;
    private static final long TELEGRAPH_TICKS = 80L; // 4 секунди

    private final Plugin plugin;
    private final CreatureSelector selector;
    private final MythicCreatureGateway gateway;
    private final BeyonderService beyonderService;
    private final double minSpawnDistance;
    private final Random random = new Random();
    private final Map<String, Long> lastSpawnByChunk = new HashMap<>();

    public StructureCreatureSpawnListener(Plugin plugin, CreatureSelector selector,
                                          MythicCreatureGateway gateway, BeyonderService beyonderService,
                                          double minSpawnDistance) {
        this.plugin = plugin;
        this.selector = selector;
        this.gateway = gateway;
        this.beyonderService = beyonderService;
        this.minSpawnDistance = minSpawnDistance;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        Location loc = event.getLootContext().getLocation();
        if (loc == null || loc.getWorld() == null) return;

        if (!SpawnDistance.isFarEnough(loc, minSpawnDistance)) return;

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

        Player opener = event.getEntity() instanceof Player p ? p : null;
        if (!ApexGate.allows(pick.get().tier(), sequenceOf(opener))) return;

        // Шукаємо місце навколо ГРАВЦЯ, а не скрині: мінімальний радіус має означати саме
        // «не впритул до того, хто відкрив».
        Location origin = opener != null ? opener.getLocation() : loc;
        Optional<Location> spot = SafeLocations.spawnableNear(origin, MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS);
        if (spot.isEmpty()) return;

        telegraph(opener);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!spot.get().getChunk().isLoaded()) return;
            gateway.spawn(pick.get().id(), spot.get());
        }, TELEGRAPH_TICKS);

        // Прибираємо застарілі записи перед вставкою, щоб карта не росла безмежно
        if (lastSpawnByChunk.size() > 256) {
            lastSpawnByChunk.values().removeIf(t -> now - t > SPAWN_COOLDOWN_MS);
        }
        lastSpawnByChunk.put(chunkKey, now);
    }

    /** Послідовність гравця, або null — якщо він не потойбічний (тоді apex до нього не прийде). */
    private Integer sequenceOf(Player player) {
        if (player == null) return null;
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        return beyonder == null ? null : beyonder.getSequenceLevel();
    }

    private void telegraph(Player player) {
        if (player == null) return;
        player.sendMessage(ChatColor.DARK_RED + "Щось прокинулось поруч — здобич була не безкоштовна.");
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSER, 1.0f, 0.7f);
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.5f);
    }
}
