package me.vangoo.application.services;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.PathwayAccess;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import me.vangoo.infrastructure.organizations.DuelArenaProvider;
import me.vangoo.infrastructure.organizations.DuelBriefing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Оркестратор дуелі ініціації: телепорт в арену, діалог священика, спавн істоти
 * Seq-9 чужої групи, наслідок (перемога → вибір шляху / поразка → повернення живим).
 * Стан — лише instance-поля. Провід — ServiceContainer.
 */
public class ChurchDuelService {

    private static final String PREFIX = ChatColor.GOLD + "[Церква] " + ChatColor.RESET;

    private final Plugin plugin;
    private final ChurchService churchService;
    private final PathwayManager pathwayManager;
    private final MythicCreatureGateway gateway;
    private final DuelArenaProvider arena;
    private final Map<String, CreatureDefinition> creatureRegistry;
    private final Random random = new Random();

    private final Map<UUID, DuelSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, DuelBriefing> briefings = new ConcurrentHashMap<>();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    private BiConsumer<Player, String> trialChoiceOpener;

    public ChurchDuelService(Plugin plugin, ChurchService churchService, PathwayManager pathwayManager,
                             MythicCreatureGateway gateway, DuelArenaProvider arena,
                             Map<String, CreatureDefinition> creatureRegistry) {
        this.plugin = plugin;
        this.churchService = churchService;
        this.pathwayManager = pathwayManager;
        this.gateway = gateway;
        this.arena = arena;
        this.creatureRegistry = creatureRegistry;
    }

    public void setTrialChoiceOpener(BiConsumer<Player, String> opener) {
        this.trialChoiceOpener = opener;
    }

    public boolean hasActiveDuel(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean isFrozen(UUID playerId) {
        return frozen.contains(playerId);
    }

    // ── Старт ────────────────────────────────────────────────────────────────

    public void startTrial(Player player, String institutionId) {
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id) || frozen.contains(id)) {
            return;
        }
        if (!churchService.canStartTrial(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Випробування зараз недоступне.");
            return;
        }
        Institution church = churchService.registry().byId(institutionId).orElse(null);
        if (church == null) {
            return;
        }
        Optional<String> opponentId = pickOpponent(church);
        if (opponentId.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Гідного суперника не знайдено.");
            return;
        }
        Location back = player.getLocation().clone();
        GameMode prevMode = player.getGameMode();
        player.teleport(arena.arenaSpawn());
        player.setGameMode(GameMode.SURVIVAL);
        frozen.add(id);
        DuelBriefing briefing = new DuelBriefing(plugin, () -> audienceOf(id),
                () -> onBriefingDone(id, institutionId, opponentId.get(), back, prevMode));
        briefings.put(id, briefing);
        briefing.start();
    }

    private List<Player> audienceOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return (p != null && p.isOnline()) ? List.of(p) : List.of();
    }

    private void onBriefingDone(UUID id, String institutionId, String opponentId,
                                Location back, GameMode prevMode) {
        briefings.remove(id);
        frozen.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            return; // гравець вийшов під час доповіді — нічого не спавнимо
        }
        Optional<LivingEntity> opponent = gateway.spawn(opponentId, arena.opponentSpawn());
        if (opponent.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Суперник не з'явився. Спробуйте пізніше.");
            player.teleport(back);
            player.setGameMode(prevMode);
            return;
        }
        DuelSession session = new DuelSession(id, institutionId, opponent.get().getUniqueId(),
                back, prevMode, () -> onTimeout(id));
        sessions.put(id, session);
        session.start(plugin);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Дуель почалась. Здолай створіння!");
    }

    // ── Наслідки ───────────────────────────────────────────────────────────────

    /** Викликає DuelListener на EntityDeathEvent опонента. */
    public void opponentDied(UUID entityUuid) {
        for (DuelSession s : sessions.values()) {
            if (entityUuid.equals(s.opponentUuid())) {
                win(s);
                return;
            }
        }
    }

    private void win(DuelSession s) {
        UUID id = s.playerId();
        sessions.remove(id);
        s.cancel();
        churchService.markTrialPassed(id);
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) {
            return;
        }
        p.teleport(s.returnLocation());
        p.setGameMode(s.previousGameMode());
        p.sendMessage(PREFIX + ChatColor.GREEN + "Ви здолали створіння! Оберіть свій шлях у священика.");
        if (trialChoiceOpener != null) {
            String inst = s.institutionId();
            Bukkit.getScheduler().runTask(plugin, () -> trialChoiceOpener.accept(p, inst));
        }
    }

    /** Викликає DuelListener, коли смертельний удар по гравцю у дуель-світі скасовано. */
    public void onPlayerLost(Player player) {
        DuelSession s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        frozen.remove(player.getUniqueId());
        s.cancel();
        healToFull(player);
        player.teleport(s.returnLocation());
        player.setGameMode(s.previousGameMode());
        player.sendMessage(PREFIX + ChatColor.RED + "Ви програли дуель. Поверніться, коли будете готові.");
    }

    private void onTimeout(UUID id) {
        DuelSession s = sessions.remove(id);
        if (s == null) {
            return;
        }
        frozen.remove(id);
        s.cancel();
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline()) {
            healToFull(p);
            p.teleport(s.returnLocation());
            p.setGameMode(s.previousGameMode());
            p.sendMessage(PREFIX + ChatColor.RED + "Час вичерпано. Дуель завершено.");
        }
    }

    // ── Життєвий цикл (quit / crash / disable) ────────────────────────────────

    public void abandon(UUID id) {
        frozen.remove(id);
        DuelBriefing b = briefings.remove(id);
        if (b != null) {
            b.cancel();
        }
        DuelSession s = sessions.remove(id);
        if (s != null) {
            s.cancel();
        }
    }

    public void handleStrandedOnJoin(Player player) {
        if (arena.isDuelWorld(player.getWorld()) && !sessions.containsKey(player.getUniqueId())) {
            Location fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
            player.teleport(fallback);
            player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Дуель перервалась. Вас повернуто у світ.");
        }
    }

    public void endAll() {
        for (DuelSession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId());
            if (p != null && p.isOnline()) {
                p.teleport(s.returnLocation());
                p.setGameMode(s.previousGameMode());
            }
            s.cancel();
        }
        sessions.clear();
        for (DuelBriefing b : briefings.values()) {
            b.cancel();
        }
        briefings.clear();
        frozen.clear();
    }

    // ── Хелпери ────────────────────────────────────────────────────────────────

    private void healToFull(Player player) {
        double max = player.getAttribute(Attribute.MAX_HEALTH) != null
                ? player.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
        player.setHealth(max);
        player.setFireTicks(0);
    }

    /** Seq-9 істота з групи, ЧУЖОЇ домену церкви; фолбек — будь-яка Seq-9. */
    private Optional<String> pickOpponent(Institution church) {
        Set<PathwayGroup> domainGroups = church.accesses().stream()
                .map(PathwayAccess::pathwayName)
                .map(pathwayManager::getPathway)
                .filter(Objects::nonNull)
                .map(Pathway::getGroup)
                .collect(Collectors.toCollection(HashSet::new));
        List<String> foreign = new ArrayList<>();
        List<String> anySeq9 = new ArrayList<>();
        for (CreatureDefinition c : creatureRegistry.values()) {
            if (c.sequence() != 9) {
                continue;
            }
            anySeq9.add(c.id());
            Pathway p = pathwayManager.getPathway(c.pathway());
            if (p != null && !domainGroups.contains(p.getGroup())) {
                foreign.add(c.id());
            }
        }
        List<String> pool = !foreign.isEmpty() ? foreign : anySeq9;
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }
}
