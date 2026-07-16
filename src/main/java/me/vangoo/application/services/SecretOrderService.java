package me.vangoo.application.services;

import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureTier;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.organizations.Favor;
import me.vangoo.domain.organizations.FavorOptions;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.InstitutionRegistry;
import me.vangoo.domain.organizations.InstitutionType;
import me.vangoo.domain.organizations.Invitation;
import me.vangoo.domain.organizations.InvitationRules;
import me.vangoo.domain.organizations.OrderMembership;
import me.vangoo.domain.organizations.OrderRank;
import me.vangoo.domain.organizations.OrderStash;
import me.vangoo.domain.organizations.OrderTask;
import me.vangoo.domain.organizations.OrderTaskGenerator;
import me.vangoo.domain.organizations.PathwayAccess;
import me.vangoo.domain.organizations.RaidPlanner;
import me.vangoo.domain.organizations.TaskQuota;
import me.vangoo.domain.organizations.TaskWeight;
import me.vangoo.infrastructure.citizens.ChurchPriestService;
import me.vangoo.infrastructure.items.CharacteristicCodec;
import me.vangoo.infrastructure.items.OrderItems;
import me.vangoo.infrastructure.items.RecipeBookFactory;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import me.vangoo.infrastructure.organizations.ChurchSiteRepository;
import me.vangoo.infrastructure.organizations.ChurchSiteService;
import me.vangoo.infrastructure.organizations.JSONOrderMembershipRepository;
import me.vangoo.infrastructure.organizations.JSONOrderMembershipRepository.FavorRecord;
import me.vangoo.infrastructure.organizations.JSONOrderMembershipRepository.InvitationRecord;
import me.vangoo.infrastructure.organizations.JSONOrderMembershipRepository.MembershipRecord;
import me.vangoo.infrastructure.organizations.JSONOrderMembershipRepository.PlayerOrderData;
import me.vangoo.infrastructure.organizations.JSONOrderMembershipRepository.TaskRecord;
import me.vangoo.infrastructure.organizations.OrderConfig;
import me.vangoo.infrastructure.organizations.OrderStateRepository;
import me.vangoo.infrastructure.organizations.OrderStateRepository.IntelRecord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Оркестратор таємних організацій: членство, куратор, запрошення за вчинки, завдання
 * (полювання/доставка + храмові/шпигунські операції з Task 13), фавори-нагороди,
 * схованка ордену, таліни розвідданих і кулдауни храмів. Bukkit-залежний
 * application-сервіс, написаний за зразком {@link ChurchService} — доменна математика
 * (Tasks 1-8) лишається чистою і вже покрита юніт-тестами.
 *
 * <p>Стан персиститься після КОЖНОЇ мутації: {@link #persist()} — членства/запрошення/
 * кулдауни, {@link #persistState()} — схованки/розвіддані/кулдауни храмів і священиків.
 */
public class SecretOrderService {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Куратор] " + ChatColor.RESET;

    private static final List<String> CURATOR_NAMES = List.of(
            "Пан у сірому", "Тінь за свічкою", "Безіменний брат", "Сестра Шепіт", "Речник Мовчання");

    public enum JoinResult { OK, ALREADY_MEMBER, NO_PATHWAY, WRONG_PATHWAY, COOLDOWN, ABANDONED, UNKNOWN_ORDER }

    /** Знімок розвіданого сховища церкви: itemKey → кількість, з терміном дії. */
    public record IntelState(Map<String, Integer> manifest, long expiresAt) {}

    private final Plugin plugin;
    private final OrderConfig config;
    private final InstitutionRegistry registry;
    private final BeyonderService beyonderService;
    private final PathwayManager pathwayManager;
    private final RecipeUnlockService recipeUnlockService;
    private final CustomItemService customItemService;
    private final CharacteristicCodec characteristicCodec;
    private final MarketItemClassifier classifier;
    private final MarketItemNamer namer;
    private final CreatureNamer creatureNamer;
    private final Map<String, CreatureDefinition> creatureRegistry;
    private final Map<String, Map<Integer, RecipeDefinition>> potionRecipeConfig;
    private final ChurchService churchService;
    private final ChurchSiteService churchSiteService;
    private final MythicCreatureGateway mythicGateway;
    private final PotionManager potionManager;
    private final RecipeBookFactory recipeBookFactory;
    private final OrderItems orderItems;
    private final JSONOrderMembershipRepository membershipRepository;
    private final OrderStateRepository stateRepository;
    /**
     * НЕ входить у літеральний список конструктора з брифу задачі 12 — доданий свідомо,
     * бо власна "Ключова логіка" брифу (tick()) вимагає ChurchPriestService.spawn для
     * респавну священика після закриття храму, а ChurchSiteService не віддає посилання
     * на priest-сервіс назовні. Task 16 підганяє виклик конструктора під цей клас.
     */
    private final ChurchPriestService priestService;

    private final OrderTaskGenerator taskGenerator = new OrderTaskGenerator();
    private final Random random = new Random();

    // ── Стан (instance-поля; гідруються в конструкторі) ────────────────────
    private final Map<UUID, OrderMembership> memberships = new HashMap<>();
    private final Map<UUID, Long> rejoinCooldownUntil = new HashMap<>();
    private final Map<UUID, Set<String>> abandonedOrders = new HashMap<>();
    private final Map<UUID, List<Invitation>> invitations = new HashMap<>();
    private final Map<UUID, Integer> beyonderKills = new HashMap<>();
    private final Map<UUID, Long> talismanReissueAfter = new HashMap<>();
    private final Set<UUID> falsePapers = new HashSet<>();
    // Тека рейдової здобичі — не читається/не пишеться логікою Task 12 (то приналежність
    // Task 13), але переноситься 1:1 при (де)гідрації, щоб персист цього сервісу не стирав
    // дані, які запише майбутній RAID-функціонал.
    private final Map<UUID, Map<String, Integer>> pendingRaidLoot = new HashMap<>();
    private final Map<String, OrderStash> stashes = new HashMap<>();
    private final Map<String, IntelState> intel = new HashMap<>();
    private final Map<String, Long> templeCooldownUntil = new HashMap<>();
    private final Map<String, Long> priestClosedUntil = new HashMap<>();
    // Живий стан операцій (транзієнтний, як реєстр сесій дуелей): рейдові сесії й
    // охоронці замахів гинуть на рестарті разом зі спавненими мобами — не персиститься.
    private final Map<UUID, RaidSession> raids = new HashMap<>();
    private final Map<UUID, UUID> assassinationGuards = new HashMap<>(); // guardUuid → assassin

    public SecretOrderService(Plugin plugin, OrderConfig config, InstitutionRegistry registry,
                              BeyonderService beyonderService, PathwayManager pathwayManager,
                              RecipeUnlockService recipeUnlockService, CustomItemService customItemService,
                              CharacteristicCodec characteristicCodec, MarketItemClassifier classifier,
                              MarketItemNamer namer, CreatureNamer creatureNamer,
                              Map<String, CreatureDefinition> creatureRegistry,
                              Map<String, Map<Integer, RecipeDefinition>> potionRecipeConfig,
                              ChurchService churchService, ChurchSiteService churchSiteService,
                              MythicCreatureGateway mythicGateway, PotionManager potionManager,
                              RecipeBookFactory recipeBookFactory, OrderItems orderItems,
                              JSONOrderMembershipRepository membershipRepository,
                              OrderStateRepository stateRepository, ChurchPriestService priestService) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.beyonderService = beyonderService;
        this.pathwayManager = pathwayManager;
        this.recipeUnlockService = recipeUnlockService;
        this.customItemService = customItemService;
        this.characteristicCodec = characteristicCodec;
        this.classifier = classifier;
        this.namer = namer;
        this.creatureNamer = creatureNamer;
        this.creatureRegistry = creatureRegistry;
        this.potionRecipeConfig = potionRecipeConfig;
        this.churchService = churchService;
        this.churchSiteService = churchSiteService;
        this.mythicGateway = mythicGateway;
        this.potionManager = potionManager;
        this.recipeBookFactory = recipeBookFactory;
        this.orderItems = orderItems;
        this.membershipRepository = membershipRepository;
        this.stateRepository = stateRepository;
        this.priestService = priestService;
        hydrate();
    }

    // ── Гідрація / персистентність (калька ChurchService.hydrate/persist/persistState) ──

    private void hydrate() {
        membershipRepository.load().ifPresent(model -> {
            if (model.players() == null) {
                return;
            }
            model.players().forEach((key, data) -> {
                UUID playerId = parseUuid(key);
                if (playerId == null || data == null) {
                    return;
                }
                rejoinCooldownUntil.put(playerId, data.rejoinCooldownUntilEpochMillis());
                if (data.abandonedOrders() != null && !data.abandonedOrders().isEmpty()) {
                    abandonedOrders.put(playerId, new HashSet<>(data.abandonedOrders()));
                }
                if (data.invitations() != null && !data.invitations().isEmpty()) {
                    List<Invitation> restored = new ArrayList<>();
                    for (InvitationRecord r : data.invitations()) {
                        restored.add(toInvitation(r));
                    }
                    invitations.put(playerId, restored);
                }
                if (data.beyonderKills() != 0) {
                    beyonderKills.put(playerId, data.beyonderKills());
                }
                if (data.talismanReissueAfterEpochMillis() != 0) {
                    talismanReissueAfter.put(playerId, data.talismanReissueAfterEpochMillis());
                }
                if (data.pendingRaidLoot() != null && !data.pendingRaidLoot().isEmpty()) {
                    pendingRaidLoot.put(playerId, new HashMap<>(data.pendingRaidLoot()));
                }
                if (data.falsePapers()) {
                    falsePapers.add(playerId);
                }
                MembershipRecord mr = data.membership();
                if (mr == null) {
                    return;
                }
                OrderMembership membership = new OrderMembership(playerId, mr.institutionId(), mr.curatorName());
                membership.setLastTaskRefreshEpochMillis(mr.lastTaskRefreshEpochMillis());
                membership.restoreTaskSetsUsed(mr.taskSetsUsed());
                if (mr.tasks() != null) {
                    membership.setTasks(mr.tasks().stream().map(SecretOrderService::toTask).toList());
                }
                if (mr.favors() != null) {
                    membership.restoreFavors(mr.favors().stream().map(SecretOrderService::toFavor).toList());
                }
                memberships.put(playerId, membership);
            });
        });
        stateRepository.load().ifPresent(model -> {
            if (model.stashes() != null) {
                model.stashes().forEach((orderId, snapshot) -> {
                    OrderStash stash = new OrderStash();
                    stash.restore(snapshot);
                    stashes.put(orderId, stash);
                });
            }
            if (model.intel() != null) {
                model.intel().forEach((key, rec) ->
                        intel.put(key, new IntelState(rec.manifest(), rec.expiresAtEpochMillis())));
            }
            if (model.templeCooldownUntil() != null) {
                templeCooldownUntil.putAll(model.templeCooldownUntil());
            }
            if (model.priestClosedUntil() != null) {
                priestClosedUntil.putAll(model.priestClosedUntil());
            }
        });
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Записує усі членства + кулдауни + запрошення + фальшиві документи у order-memberships.json. */
    private void persist() {
        Set<UUID> allIds = new HashSet<>();
        allIds.addAll(memberships.keySet());
        allIds.addAll(rejoinCooldownUntil.keySet());
        allIds.addAll(abandonedOrders.keySet());
        allIds.addAll(invitations.keySet());
        allIds.addAll(beyonderKills.keySet());
        allIds.addAll(talismanReissueAfter.keySet());
        allIds.addAll(falsePapers);
        allIds.addAll(pendingRaidLoot.keySet());
        Map<String, PlayerOrderData> players = new HashMap<>();
        for (UUID id : allIds) {
            OrderMembership membership = memberships.get(id);
            MembershipRecord mr = membership == null ? null : new MembershipRecord(
                    membership.institutionId(), membership.curatorName(),
                    membership.lastTaskRefreshEpochMillis(), membership.taskSetsUsed(),
                    membership.tasks().stream().map(SecretOrderService::toRecord).toList(),
                    membership.favors().stream().map(SecretOrderService::toRecord).toList());
            List<InvitationRecord> invRecords = invitations.getOrDefault(id, List.of()).stream()
                    .map(SecretOrderService::toRecord).toList();
            players.put(id.toString(), new PlayerOrderData(mr,
                    rejoinCooldownUntil.getOrDefault(id, 0L),
                    List.copyOf(abandonedOrders.getOrDefault(id, Set.of())),
                    invRecords,
                    beyonderKills.getOrDefault(id, 0),
                    talismanReissueAfter.getOrDefault(id, 0L),
                    pendingRaidLoot.getOrDefault(id, Map.of()),
                    falsePapers.contains(id)));
        }
        membershipRepository.save(new JSONOrderMembershipRepository.Model(players));
    }

    /** Записує схованки орденів, розвіддані та кулдауни храмів/священиків у orders-state.json. */
    private void persistState() {
        Map<String, Map<String, Integer>> stashSnapshot = new HashMap<>();
        stashes.forEach((orderId, stash) -> stashSnapshot.put(orderId, stash.snapshot()));
        Map<String, IntelRecord> intelSnapshot = new HashMap<>();
        intel.forEach((key, state) -> intelSnapshot.put(key, new IntelRecord(state.manifest(), state.expiresAt())));
        stateRepository.save(new OrderStateRepository.Model(stashSnapshot, intelSnapshot,
                new HashMap<>(templeCooldownUntil), new HashMap<>(priestClosedUntil)));
    }

    private static OrderTask toTask(TaskRecord r) {
        if (r == null) {
            return null;
        }
        return new OrderTask(OrderTask.Type.valueOf(r.type()), TaskWeight.valueOf(r.weight()),
                r.targetKey(), r.targetName(), r.required(), r.progress());
    }

    private static TaskRecord toRecord(OrderTask t) {
        return new TaskRecord(t.type().name(), t.weight().name(), t.targetKey(), t.targetName(),
                t.required(), t.progress());
    }

    private static Favor toFavor(FavorRecord r) {
        return new Favor(TaskWeight.valueOf(r.weight()), r.earnedAtEpochMillis());
    }

    private static FavorRecord toRecord(Favor f) {
        return new FavorRecord(f.weight().name(), f.earnedAtEpochMillis());
    }

    private static Invitation toInvitation(InvitationRecord r) {
        return new Invitation(r.institutionId(), r.reason(), r.createdAtEpochMillis());
    }

    private static InvitationRecord toRecord(Invitation i) {
        return new InvitationRecord(i.institutionId(), i.reason(), i.createdAtEpochMillis());
    }

    // ── Членство ──────────────────────────────────────────────────────────

    public JoinResult join(Player player, String institutionId) {
        UUID id = player.getUniqueId();
        if (memberships.containsKey(id)) {
            return JoinResult.ALREADY_MEMBER;
        }
        if (hasAbandoned(id, institutionId)) {
            return JoinResult.ABANDONED;
        }
        Long cooldownUntil = rejoinCooldownUntil.get(id);
        if (cooldownUntil != null && System.currentTimeMillis() < cooldownUntil) {
            return JoinResult.COOLDOWN;
        }
        Optional<Institution> institutionOpt = registry.byId(institutionId);
        if (institutionOpt.isEmpty() || institutionOpt.get().type() != InstitutionType.SECRET_ORDER) {
            return JoinResult.UNKNOWN_ORDER;
        }
        Institution order = institutionOpt.get();
        String pathway = pathwayNameOf(player);
        if (pathway == null) {
            return JoinResult.NO_PATHWAY;
        }
        if (!order.acceptsPathway(pathway)) {
            return JoinResult.WRONG_PATHWAY;
        }
        OrderMembership membership = new OrderMembership(id, institutionId, randomCurator());
        memberships.put(id, membership);
        seedStashIfAbsent(institutionId);
        orderItems.createTalisman().ifPresent(item -> giveItem(player, item));
        List<Invitation> pending = invitations.get(id);
        if (pending != null) {
            pending.removeIf(inv -> inv.institutionId().equals(institutionId));
            if (pending.isEmpty()) {
                invitations.remove(id);
            }
        }
        ensureFreshTasks(player);
        persist();
        return JoinResult.OK;
    }

    public boolean leave(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership removed = memberships.remove(id);
        if (removed == null) {
            return false;
        }
        abandonedOrders.computeIfAbsent(id, k -> new HashSet<>()).add(removed.institutionId());
        rejoinCooldownUntil.put(id, System.currentTimeMillis() + config.rejoinCooldownDays() * 86_400_000L);
        persist();
        return true;
    }

    public boolean hasAbandoned(UUID playerId, String institutionId) {
        return abandonedOrders.getOrDefault(playerId, Set.of()).contains(institutionId);
    }

    public Optional<OrderMembership> membershipOf(UUID playerId) {
        return Optional.ofNullable(memberships.get(playerId));
    }

    public Optional<Institution> orderOf(UUID playerId) {
        OrderMembership membership = memberships.get(playerId);
        return membership == null ? Optional.empty() : registry.byId(membership.institutionId());
    }

    public List<Institution> joinableOrders(Player player) {
        UUID id = player.getUniqueId();
        String pathway = pathwayNameOf(player);
        return registry.all().stream()
                .filter(o -> o.type() == InstitutionType.SECRET_ORDER)
                .filter(o -> o.acceptsPathway(pathway))
                .filter(o -> !hasAbandoned(id, o.id()))
                .toList();
    }

    /** null = гравець без шляху (пасивний Beyonder-профіль ще не існує або без Pathway). */
    private String pathwayNameOf(Player player) {
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null || beyonder.getPathway() == null) {
            return null;
        }
        return beyonder.getPathway().getName();
    }

    private String randomCurator() {
        return CURATOR_NAMES.get(random.nextInt(CURATOR_NAMES.size()));
    }

    // ── Запрошення за вчинки ─────────────────────────────────────────────────

    public List<Invitation> invitationsOf(UUID playerId) {
        return invitations.getOrDefault(playerId, List.of());
    }

    public void onApexKilled(Player player, String creaturePathway) {
        tryInvite(player, InvitationRules.DeedType.APEX_KILL, creaturePathway,
                "Апекс-здобич не минає непоміченою — за вами стежать.");
    }

    public void onBeyonderKilled(Player killer) {
        UUID id = killer.getUniqueId();
        int kills = beyonderKills.getOrDefault(id, 0) + 1;
        if (kills >= config.invitesBeyonderKills()) {
            beyonderKills.put(id, 0);
            persist();
            tryInvite(killer, InvitationRules.DeedType.BEYONDER_KILLS, null,
                    "Кров кличе кров — хтось у тінях це запам'ятав.");
        } else {
            beyonderKills.put(id, kills);
            persist();
        }
    }

    public void onRampagerStopped(Player player) {
        tryInvite(player, InvitationRules.DeedType.RAMPAGER_STOPPED, null,
                "Ви втримали лють на кордоні — Порядок це цінує.");
    }

    private void tryInvite(Player player, InvitationRules.DeedType deed, String deedPathwayOrNull, String reason) {
        UUID id = player.getUniqueId();
        if (memberships.containsKey(id)) {
            return; // вже у ордені — не засипаємо запрошеннями
        }
        String playerPathway = pathwayNameOf(player);
        Map<String, String> pathwayToGroup = pathwayToGroupMap();
        List<Institution> candidates = registry.all().stream()
                .filter(o -> o.type() == InstitutionType.SECRET_ORDER)
                .filter(o -> !hasAbandoned(id, o.id()))
                .toList();
        InvitationRules.pickOrder(deed, deedPathwayOrNull, playerPathway, candidates, pathwayToGroup, random)
                .ifPresent(order -> addInvitation(player, order, reason));
    }

    private void addInvitation(Player player, Institution order, String reason) {
        UUID id = player.getUniqueId();
        List<Invitation> list = invitations.computeIfAbsent(id, k -> new ArrayList<>());
        boolean already = list.stream().anyMatch(inv -> inv.institutionId().equals(order.id()));
        if (!already) {
            list.add(new Invitation(order.id(), reason, System.currentTimeMillis()));
        }
        persist();
        if (player.isOnline()) {
            player.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + order.displayName()
                    + ChatColor.GRAY + " шле знак: " + reason);
        }
    }

    /** Мапа "назва шляху (обидва регістри) → назва PathwayGroup" — join-ключ до creatures.yml. */
    private Map<String, String> pathwayToGroupMap() {
        Map<String, String> map = new HashMap<>();
        for (String name : pathwayManager.getAllPathwayNames()) {
            Pathway pathway = pathwayManager.getPathway(name);
            if (pathway != null) {
                String group = pathway.getGroup().name();
                map.put(name, group);
                map.put(name.toLowerCase(Locale.ROOT), group);
            }
        }
        return map;
    }

    // ── Талісман ──────────────────────────────────────────────────────────

    public boolean reissueTalisman(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long after = talismanReissueAfter.get(id);
        if (after != null && now < after) {
            return false;
        }
        Optional<ItemStack> item = orderItems.createTalisman();
        if (item.isEmpty()) {
            return false;
        }
        giveItem(player, item.get());
        talismanReissueAfter.put(id, now + config.talismanReissueCooldownMinutes() * 60_000L);
        persist();
        return true;
    }

    // ── Завдання (калька ChurchService.ensureFreshTasks/deliverTask/onCreatureKilled) ──

    public TaskQuota taskQuota() {
        return new TaskQuota(config.tasksRefreshHours() * 3_600_000L, config.tasksSetsPerWindow());
    }

    public void ensureFreshTasks(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return;
        }
        long now = System.currentTimeMillis();
        TaskQuota quota = taskQuota();
        boolean changed = false;

        if (quota.windowExpired(membership.lastTaskRefreshEpochMillis(), now)) {
            membership.startTaskWindow(now);
            List<OrderTask> started = membership.tasks().stream()
                    .filter(t -> t.progress() > 0)
                    .toList();
            if (started.size() != membership.tasks().size()) {
                membership.setTasks(started);
            }
            changed = true;
        }

        if (membership.tasks().isEmpty() && quota.canGenerate(membership.taskSetsUsed())) {
            if (generateTaskSet(player, membership)) {
                membership.consumeTaskSet();
                changed = true;
            }
        }

        if (changed) {
            persist();
        }
    }

    /** @return true, якщо набір справді згенеровано (інакше квота не витрачається). */
    private boolean generateTaskSet(Player player, OrderMembership membership) {
        Institution order = registry.byId(membership.institutionId()).orElse(null);
        if (order == null) {
            return false;
        }
        Map<String, String> pathwayToGroup = pathwayToGroupMap();
        List<OrderTask> tasks = taskGenerator.generate(config.tasksMaxActive(), order, pathwayToGroup,
                creatureCandidates(), ingredientCandidatesFor(player), raidableChurches(),
                doubleAgentChurchOf(player), rankOf(player), random);
        if (tasks.isEmpty()) {
            return false;
        }
        membership.setTasks(tasks);
        return true;
    }

    private List<OrderTaskGenerator.CreatureCandidate> creatureCandidates() {
        return creatureRegistry.values().stream()
                .map(c -> new OrderTaskGenerator.CreatureCandidate(c.id(), c.pathway(), c.sequence()))
                .toList();
    }

    /** Інгредієнти рецептів ШЛЯХУ ГРАВЦЯ (усі seq), бо схованка живить фавори саме його прогресу. */
    private List<OrderTaskGenerator.IngredientCandidate> ingredientCandidatesFor(Player player) {
        String pathway = pathwayNameOf(player);
        if (pathway == null) {
            return List.of();
        }
        Map<Integer, RecipeDefinition> bySeq = potionRecipeConfig.get(pathway);
        if (bySeq == null) {
            return List.of();
        }
        List<OrderTaskGenerator.IngredientCandidate> out = new ArrayList<>();
        for (Map.Entry<Integer, RecipeDefinition> entry : bySeq.entrySet()) {
            int seq = entry.getKey();
            for (String rawId : allIds(entry.getValue())) {
                if (rawId.startsWith("vanilla:")) {
                    continue;
                }
                String key = ingredientKey(rawId);
                out.add(new OrderTaskGenerator.IngredientCandidate(key, namer.displayName(key), seq));
            }
        }
        return out;
    }

    /** Церкви, чий храм зараз можна рейдити (не на кулдауні й священик не закритий). */
    private List<OrderTaskGenerator.ChurchTarget> raidableChurches() {
        return churchSiteService.sites().stream()
                .map(ChurchSiteRepository.Site::institutionId)
                .distinct()
                .filter(chId -> !isTempleOnCooldown(chId) && !isTempleClosed(chId))
                .map(chId -> new OrderTaskGenerator.ChurchTarget(chId, churchNameOf(chId)))
                .toList();
    }

    private OrderTaskGenerator.ChurchTarget doubleAgentChurchOf(Player player) {
        return churchService.churchOf(player.getUniqueId())
                .map(church -> new OrderTaskGenerator.ChurchTarget(church.id(), church.displayName()))
                .orElse(null);
    }

    private OrderRank rankOf(Player player) {
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        int seq = beyonder == null ? 9 : beyonder.getSequenceLevel();
        return OrderRank.of(seq);
    }

    private String churchNameOf(String churchId) {
        return registry.byId(churchId).map(Institution::displayName).orElse(churchId);
    }

    public List<OrderTask> tasksOf(Player player) {
        OrderMembership membership = memberships.get(player.getUniqueId());
        return membership == null ? List.of() : membership.tasks();
    }

    public Optional<ChurchService.TaskPoolStatus> taskPoolStatus(Player player) {
        OrderMembership membership = memberships.get(player.getUniqueId());
        if (membership == null) {
            return Optional.empty();
        }
        TaskQuota quota = taskQuota();
        return Optional.of(new ChurchService.TaskPoolStatus(membership.taskSetsUsed(), quota.setsPerDay(),
                quota.millisUntilReset(membership.lastTaskRefreshEpochMillis(), System.currentTimeMillis())));
    }

    public int deliverTask(Player player, int taskIndex) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return 0;
        }
        List<OrderTask> tasks = membership.tasks();
        if (taskIndex < 0 || taskIndex >= tasks.size()) {
            return 0;
        }
        OrderTask task = tasks.get(taskIndex);
        if (task.type() != OrderTask.Type.DELIVER) {
            return 0;
        }
        int needed = task.required() - task.progress();
        if (needed <= 0) {
            return 0;
        }
        int have = countMatching(player, task.targetKey());
        int toDeliver = Math.min(have, needed);
        if (toDeliver <= 0) {
            return 0;
        }
        List<ItemStack> removed = removeMatching(player, task.targetKey(), toDeliver);
        int delivered = removed.stream().mapToInt(ItemStack::getAmount).sum();
        if (delivered <= 0) {
            return 0;
        }
        stashOf(membership.institutionId()).add(task.targetKey(), delivered);

        OrderTask updated = task.withProgress(task.progress() + delivered);
        List<OrderTask> newTasks = new ArrayList<>(tasks);
        if (updated.isComplete()) {
            newTasks.remove(taskIndex);
            completeTask(player, membership, updated);
        } else {
            newTasks.set(taskIndex, updated);
        }
        membership.setTasks(newTasks);
        persist();
        persistState();
        return delivered;
    }

    public void onCreatureKilled(Player killer, String creatureId) {
        UUID id = killer.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return;
        }
        boolean changed = false;
        List<OrderTask> updatedTasks = new ArrayList<>();
        for (OrderTask t : membership.tasks()) {
            if (t.type() == OrderTask.Type.HUNT && t.targetKey().equals(creatureId)) {
                OrderTask updated = t.withProgress(t.progress() + 1);
                changed = true;
                if (updated.isComplete()) {
                    completeTask(killer, membership, updated);
                } else {
                    updatedTasks.add(updated);
                }
            } else {
                updatedTasks.add(t);
            }
        }
        if (changed) {
            membership.setTasks(updatedTasks);
            persist();
        }
    }

    /**
     * Завершення завдання = борг вдячності куратора. LIGHT має 0.35 шанс піти БЕЗ фавора
     * ("Куратор лише кивнув") — розминкові завдання не мають гарантовано конвертуватись
     * у валюту прохань. Рандом сервіса, юніт-тестом не пінитьcя (див. task-12-brief).
     */
    private void completeTask(Player player, OrderMembership membership, OrderTask task) {
        creditCompletion(membership, task, player.getUniqueId());
    }

    /**
     * Нараховує фавор за завершене завдання й повідомляє гравця, якщо він онлайн
     * (замах/шпигунство можуть завершитись, коли виконавець уже вийшов). LIGHT має
     * 0.35 шанс піти без фавора — див. {@link #completeTask}.
     */
    private void creditCompletion(OrderMembership membership, OrderTask task, UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (task.weight() == TaskWeight.LIGHT && random.nextDouble() < 0.35) {
            if (online != null) {
                online.sendMessage(PREFIX + ChatColor.GRAY + "Куратор лише кивнув.");
            }
            return;
        }
        membership.addFavor(new Favor(task.weight(), System.currentTimeMillis()));
        if (online != null) {
            online.sendMessage(PREFIX + ChatColor.GREEN + "Куратор запам'ятав це.");
        }
    }

    /** Позначає завдання за індексом виконаним, прибирає його зі списку й нараховує фавор. */
    private void completeTaskAt(OrderMembership membership, int index, UUID playerId) {
        List<OrderTask> tasks = new ArrayList<>(membership.tasks());
        OrderTask done = tasks.get(index).withProgress(tasks.get(index).required());
        tasks.remove(index);
        membership.setTasks(tasks);
        creditCompletion(membership, done, playerId);
    }

    // ── Фавори: прохання до куратора ─────────────────────────────────────────

    public List<Favor> favorsOf(UUID playerId) {
        OrderMembership membership = memberships.get(playerId);
        return membership == null ? List.of() : membership.favors();
    }

    public List<FavorOptions.Option> favorOptionsFor(Player player, TaskWeight weight) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return List.of();
        }
        String pathway = pathwayNameOf(player);
        Beyonder beyonder = beyonderService.getBeyonder(id);
        boolean knowsNext = false;
        if (pathway != null && beyonder != null) {
            int nextSeq = beyonder.getSequenceLevel() - 1;
            if (nextSeq >= 0) {
                knowsNext = recipeUnlockService.canCraftPotion(id, pathway, nextSeq);
            }
        }
        OrderRank rank = rankOf(player);
        boolean hasIntel = orderHasIntel(membership.institutionId());
        return FavorOptions.available(new FavorOptions.Context(knowsNext, weight, rank, hasIntel));
    }

    public boolean claimHuntInfo(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        FavorOptions.Option option = FavorOptions.Option.HUNT_INFO;
        if (!favorOptionsFor(player, TaskWeight.LIGHT).contains(option)) {
            return false;
        }
        Optional<Favor> spentOpt = membership.consumeFavor(FavorOptions.requiredWeight(option));
        if (spentOpt.isEmpty()) {
            return false;
        }
        String pathway = pathwayNameOf(player);
        Map<String, String> pathwayToGroup = pathwayToGroupMap();
        String group = pathway == null ? null : pathwayToGroup.get(pathway);
        List<CreatureDefinition> apexes = group == null ? List.of() : creatureRegistry.values().stream()
                .filter(c -> c.tier() == CreatureTier.APEX)
                .filter(c -> group.equals(pathwayToGroup.get(c.pathway())))
                .toList();
        if (apexes.isEmpty()) {
            membership.addFavor(spentOpt.get());
            persist();
            player.sendMessage(PREFIX + ChatColor.GRAY + "Куратор мовчить — нема апекс-цілей вашого домену.");
            return false;
        }
        player.sendMessage(PREFIX + ChatColor.GOLD + "Куратор шепоче про апекс-здобич вашого домену:");
        for (CreatureDefinition c : apexes) {
            String name = creatureNamer.displayName(c.id());
            List<String> biomes = creatureNamer.biomeNames(c.id());
            StringBuilder line = new StringBuilder(ChatColor.GRAY.toString()).append("  ").append(name);
            if (!biomes.isEmpty()) {
                line.append(ChatColor.DARK_GRAY).append(" (").append(String.join(", ", biomes)).append(")");
            }
            if (creatureNamer.spawnsNearStructures(c.id())) {
                line.append(ChatColor.DARK_GRAY).append(" — біля структур");
            }
            player.sendMessage(PREFIX + line);
        }
        persist();
        return true;
    }

    public boolean claimVaultIntel(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        FavorOptions.Option option = FavorOptions.Option.VAULT_INTEL;
        if (!favorOptionsFor(player, TaskWeight.LIGHT).contains(option)) {
            return false;
        }
        Optional<Favor> spentOpt = membership.consumeFavor(FavorOptions.requiredWeight(option));
        if (spentOpt.isEmpty()) {
            return false;
        }
        String prefix = membership.institutionId() + "|";
        long now = System.currentTimeMillis();
        Optional<Map.Entry<String, IntelState>> fresh = intel.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix) && e.getValue().expiresAt() > now)
                .findFirst();
        if (fresh.isEmpty()) {
            membership.addFavor(spentOpt.get());
            persist();
            player.sendMessage(PREFIX + ChatColor.GRAY + "Куратор не має свіжих розвідданих.");
            return false;
        }
        String churchId = fresh.get().getKey().substring(prefix.length());
        Map<String, Integer> manifest = fresh.get().getValue().manifest();
        player.sendMessage(PREFIX + ChatColor.GOLD + "Розвіддані по " + churchNameOf(churchId) + ":");
        manifest.forEach((key, amount) -> player.sendMessage(PREFIX + ChatColor.GRAY + "  "
                + namer.displayName(key) + " x" + amount));
        persist();
        return true;
    }

    public boolean claimRecipe(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        FavorOptions.Option option = FavorOptions.Option.RECIPE_KNOWLEDGE;
        if (!favorOptionsFor(player, TaskWeight.STANDARD).contains(option)) {
            return false;
        }
        Optional<Favor> spentOpt = membership.consumeFavor(FavorOptions.requiredWeight(option));
        if (spentOpt.isEmpty()) {
            return false;
        }
        String pathway = pathwayNameOf(player);
        Beyonder beyonder = beyonderService.getBeyonder(id);
        int nextSeq = (pathway == null || beyonder == null) ? -1 : beyonder.getSequenceLevel() - 1;
        boolean unlocked = nextSeq >= 0 && recipeUnlockService.unlockRecipe(id, pathway, nextSeq);
        if (!unlocked) {
            membership.addFavor(spentOpt.get());
            persist();
            return false;
        }
        persist();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Куратор передав знання рецепту наступної послідовності.");
        return true;
    }

    public boolean claimIngredients(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        FavorOptions.Option option = FavorOptions.Option.INGREDIENTS;
        if (!favorOptionsFor(player, TaskWeight.STANDARD).contains(option)) {
            return false;
        }
        Optional<Favor> spentOpt = membership.consumeFavor(FavorOptions.requiredWeight(option));
        if (spentOpt.isEmpty()) {
            return false;
        }
        String pathway = pathwayNameOf(player);
        Beyonder beyonder = beyonderService.getBeyonder(id);
        int nextSeq = (pathway == null || beyonder == null) ? -1 : beyonder.getSequenceLevel() - 1;
        RecipeDefinition def = nextSeq < 0 ? null
                : Optional.ofNullable(potionRecipeConfig.get(pathway)).map(m -> m.get(nextSeq)).orElse(null);
        if (def == null) {
            membership.addFavor(spentOpt.get());
            persist();
            return false;
        }
        List<String> keys = new ArrayList<>();
        for (String rawId : allIds(def)) {
            if (rawId.startsWith("vanilla:")) {
                continue;
            }
            String key = ingredientKey(rawId);
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        OrderStash stash = stashOf(membership.institutionId());
        List<String> given = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (given.size() >= config.favorIngredientsPerClaim()) {
                break;
            }
            if (stash.take(key, 1)) {
                given.add(key);
            } else {
                missing.add(key);
            }
        }
        if (given.isEmpty()) {
            membership.addFavor(spentOpt.get());
            persist();
            String missingText = missing.stream().map(namer::displayName).collect(Collectors.joining(", "));
            player.sendMessage(PREFIX + ChatColor.GRAY + "У схованці бракує: " + missingText);
            return false;
        }
        for (String key : given) {
            String rawId = key.substring("custom:".length());
            customItemService.createItemStack(rawId).ifPresent(item -> giveItem(player, item));
        }
        persist();
        persistState();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Куратор передав інгредієнти зі схованки.");
        return true;
    }

    public boolean claimCharacteristic(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        FavorOptions.Option option = FavorOptions.Option.CHARACTERISTIC;
        if (!favorOptionsFor(player, TaskWeight.MAJOR).contains(option)) {
            return false;
        }
        Optional<Favor> spentOpt = membership.consumeFavor(FavorOptions.requiredWeight(option));
        if (spentOpt.isEmpty()) {
            return false;
        }
        String pathway = pathwayNameOf(player);
        Beyonder beyonder = beyonderService.getBeyonder(id);
        int nextSeq = (pathway == null || beyonder == null) ? -1 : beyonder.getSequenceLevel() - 1;
        if (nextSeq < 0) {
            membership.addFavor(spentOpt.get());
            persist();
            return false;
        }
        String key = "characteristic:" + pathway + ":" + nextSeq;
        OrderStash stash = stashOf(membership.institutionId());
        if (!stash.take(key, 1)) {
            membership.addFavor(spentOpt.get());
            persist();
            player.sendMessage(PREFIX + ChatColor.GRAY + "У схованці бракує: " + namer.displayName(key));
            return false;
        }
        giveItem(player, characteristicCodec.create(pathway, nextSeq, 1));
        persist();
        persistState();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Куратор передав Характеристику зі схованки.");
        return true;
    }

    public boolean claimClearCooldown(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        FavorOptions.Option option = FavorOptions.Option.CLEAR_COOLDOWN;
        if (!favorOptionsFor(player, TaskWeight.MAJOR).contains(option)) {
            return false;
        }
        Optional<Favor> spentOpt = membership.consumeFavor(FavorOptions.requiredWeight(option));
        if (spentOpt.isEmpty()) {
            return false;
        }
        rejoinCooldownUntil.remove(id);
        churchService.clearRejoinCooldown(id);
        persist();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Куратор владнав питання з кулдауном вступу.");
        return true;
    }

    public boolean claimFalsePapers(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        FavorOptions.Option option = FavorOptions.Option.FALSE_PAPERS;
        if (!favorOptionsFor(player, TaskWeight.MAJOR).contains(option)) {
            return false;
        }
        Optional<Favor> spentOpt = membership.consumeFavor(FavorOptions.requiredWeight(option));
        if (spentOpt.isEmpty()) {
            return false;
        }
        falsePapers.add(id);
        persist();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Куратор підготував фальшиві документи на наступний вступ.");
        return true;
    }

    /** Спалює прапор фальшивих документів, якщо активний. Викликає ChurchService через FalsePapersCheck. */
    public boolean consumeFalsePapersIfActive(UUID playerId) {
        boolean active = falsePapers.remove(playerId);
        if (active) {
            persist();
        }
        return active;
    }

    // ── Схованка ордену ──────────────────────────────────────────────────────

    /**
     * Ордени з непорожніми доступами (реалізовані шляхи) — по {@code config.stashSeedIngredientsPerRecipe()}
     * інгредієнтів Seq-9 рецепта кожного доступного шляху. Ордени «будь-хто» (порожні доступи,
     * {@link Institution#acceptsAnyPathway()}) — по 1x інгредієнти Seq-9 КОЖНОГО реалізованого шляху.
     */
    public void seedStashIfAbsent(String orderId) {
        if (stashes.containsKey(orderId)) {
            return;
        }
        Optional<Institution> opt = registry.byId(orderId);
        if (opt.isEmpty()) {
            return;
        }
        Institution order = opt.get();
        OrderStash stash = new OrderStash();
        if (order.acceptsAnyPathway()) {
            for (String pathwayName : pathwayManager.getAllPathwayNames()) {
                Pathway pathway = pathwayManager.getPathway(pathwayName);
                if (pathway == null || !pathway.hasAnyAbility()) {
                    continue;
                }
                seedIngredientsForSeq9(stash, pathwayName, 1);
            }
        } else {
            for (PathwayAccess access : order.accesses()) {
                Pathway pathway = pathwayManager.getPathway(access.pathwayName());
                if (pathway == null || !pathway.hasAnyAbility()) {
                    continue;
                }
                seedIngredientsForSeq9(stash, access.pathwayName(), config.stashSeedIngredientsPerRecipe());
            }
        }
        stashes.put(orderId, stash);
        persistState();
    }

    private void seedIngredientsForSeq9(OrderStash stash, String pathwayName, int multiplier) {
        Map<Integer, RecipeDefinition> bySeq = potionRecipeConfig.get(pathwayName);
        if (bySeq == null) {
            return;
        }
        RecipeDefinition def = bySeq.get(9);
        if (def == null) {
            return;
        }
        for (String rawId : allIds(def)) {
            if (rawId.startsWith("vanilla:")) {
                continue;
            }
            stash.add(ingredientKey(rawId), multiplier);
        }
    }

    public OrderStash stashOf(String orderId) {
        return stashes.computeIfAbsent(orderId, k -> new OrderStash());
    }

    public boolean orderHasIntel(String orderId) {
        String prefix = orderId + "|";
        long now = System.currentTimeMillis();
        return intel.entrySet().stream().anyMatch(e -> e.getKey().startsWith(prefix) && e.getValue().expiresAt() > now);
    }

    public Optional<Map<String, Integer>> intelManifest(String orderId, String churchId) {
        IntelState state = intel.get(orderId + "|" + churchId);
        if (state == null || state.expiresAt() <= System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(state.manifest());
    }

    // ── Тик ───────────────────────────────────────────────────────────────

    /** Хвилинний тик: респавн священиків із минулим priestClosedUntil, чистка простроченого intel. */
    public void tick() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        List<String> toRespawn = priestClosedUntil.entrySet().stream()
                .filter(e -> e.getValue() <= now)
                .map(Map.Entry::getKey)
                .toList();
        for (String churchId : toRespawn) {
            boolean respawned = false;
            Optional<ChurchSiteRepository.Site> siteOpt = churchSiteService.siteOf(churchId);
            if (siteOpt.isPresent()) {
                ChurchSiteRepository.Site site = siteOpt.get();
                World world = Bukkit.getWorld(site.world());
                if (world != null) {
                    priestService.spawn(churchId, new Location(world, site.x(), site.y(), site.z(),
                            site.yaw(), site.pitch()));
                    respawned = true;
                }
            }
            if (respawned) {
                priestClosedUntil.remove(churchId);
                changed = true;
            }
        }
        if (intel.entrySet().removeIf(e -> e.getValue().expiresAt() <= now)) {
            changed = true;
        }
        if (changed) {
            persistState();
        }
    }

    public boolean isTempleClosed(String churchId) {
        Long until = priestClosedUntil.get(churchId);
        return until != null && until > System.currentTimeMillis();
    }

    public boolean isTempleOnCooldown(String churchId) {
        Long until = templeCooldownUntil.get(churchId);
        return until != null && until > System.currentTimeMillis();
    }

    // ── Рейд на сховище храму ────────────────────────────────────────────────

    /**
     * Старт злому сховища храму-цілі. Гейти: активна RAID-задача; ніч
     * ({@code world.getTime()} в [13000,23000]); гравець у радіусі сайту цілі; храм не
     * закритий і не на кулдауні; нема активної рейд-сесії. Фавор за RAID НЕ дається тут —
     * лише після здачі здобичі в схованку ({@link #depositRaidLoot}).
     */
    public boolean startRaid(Player player) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null || raids.containsKey(id)) {
            return false;
        }
        int raidIdx = indexOfTask(membership.tasks(), OrderTask.Type.RAID, null);
        if (raidIdx < 0) {
            return false;
        }
        String churchId = membership.tasks().get(raidIdx).targetKey();
        long time = player.getWorld().getTime();
        if (time < 13000 || time > 23000) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Рейдувати храм можна лише глупої ночі.");
            return false;
        }
        if (isTempleClosed(churchId) || isTempleOnCooldown(churchId)) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Храм цілі зараз не рейдувати.");
            return false;
        }
        Optional<ChurchSiteRepository.Site> siteOpt = churchSiteService.siteOf(churchId);
        if (siteOpt.isEmpty()) {
            return false;
        }
        ChurchSiteRepository.Site site = siteOpt.get();
        World siteWorld = Bukkit.getWorld(site.world());
        if (siteWorld == null) {
            return false;
        }
        Location center = new Location(siteWorld, site.x(), site.y(), site.z());
        if (!siteWorld.equals(player.getWorld())
                || player.getLocation().distance(center) > config.raidZoneRadius()) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Ви надто далеко від храму цілі.");
            return false;
        }
        boolean hasIntel = intelManifest(membership.institutionId(), churchId).isPresent();
        double alarmChance = RaidPlanner.alarmChancePerSecond(config.raidAlarmChance(), hasIntel,
                config.raidAlarmIntelFactor());
        RaidSession session = new RaidSession(id, churchId, center, config.raidChannelSeconds(),
                alarmChance, config.raidZoneRadius(),
                () -> raidAlarm(id, churchId),
                () -> raidSucceeded(id, churchId, hasIntel),
                () -> raidFailed(id));
        raids.put(id, session);
        session.start(plugin, random);
        player.sendMessage(PREFIX + ChatColor.DARK_RED + "Ви почали злом сховища. Лишайтесь у зоні храму.");
        return true;
    }

    /** Тривога: спавн варти домену церкви біля рейдера + сповіщення онлайн-членам церкви. */
    private void raidAlarm(UUID playerId, String churchId) {
        Player player = Bukkit.getPlayer(playerId);
        int x = 0;
        int z = 0;
        if (player != null) {
            Location at = player.getLocation();
            x = at.getBlockX();
            z = at.getBlockZ();
            List<CreatureDefinition> pool = domainCreaturePool(churchId, 7);
            for (int i = 0; i < config.raidGuards() && !pool.isEmpty(); i++) {
                CreatureDefinition guard = pool.get(random.nextInt(pool.size()));
                mythicGateway.spawn(guard.id(), at);
            }
        }
        churchService.broadcastToChurch(churchId, ChatColor.RED + "На храм " + churchNameOf(churchId)
                + " напад! (" + x + ", " + z + ")");
    }

    /** Успіх злому: здобич зі сховища церкви гравцю в руки + запис у {@code pendingRaidLoot}. */
    private void raidSucceeded(UUID playerId, String churchId, boolean hasIntel) {
        raids.remove(playerId);
        long now = System.currentTimeMillis();
        int picks = hasIntel ? config.raidLootIntelPicks() : config.raidLootPicks();
        Map<String, Integer> rolled = RaidPlanner.rollLoot(churchService.vaultSnapshot(churchId),
                picks, hasIntel, random);
        Map<String, Integer> taken = churchService.stealFromVault(churchId, rolled);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && !taken.isEmpty()) {
            giveRaidLoot(player, taken);
            pendingRaidLoot.put(playerId, new HashMap<>(taken));
            player.sendMessage(PREFIX + ChatColor.GREEN + "Злом успішний! Здобич у вас — здайте її в "
                    + "схованку через меню завдань, щоб куратор зарахував рейд.");
        } else if (player != null) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Сховище храму виявилось порожнім.");
        }
        templeCooldownUntil.put(churchId, now + config.raidTempleCooldownHours() * 3_600_000L);
        persist();
        persistState();
    }

    /** Провал злому (вихід із зони / смерть / таймаут): кулдаун храму, ризик викриття, RAID-задача згорає. */
    private void raidFailed(UUID playerId) {
        RaidSession session = raids.remove(playerId);
        if (session == null) {
            return; // вже оброблено (напр. onRaiderDied уже викликав)
        }
        String churchId = session.churchId();
        long now = System.currentTimeMillis();
        templeCooldownUntil.put(churchId, now + config.raidTempleCooldownHours() * 3_600_000L);
        OrderMembership membership = memberships.get(playerId);
        if (membership != null) {
            List<OrderTask> tasks = new ArrayList<>(membership.tasks());
            tasks.removeIf(t -> t.type() == OrderTask.Type.RAID && t.targetKey().equals(churchId));
            membership.setTasks(tasks);
        }
        Player player = Bukkit.getPlayer(playerId);
        boolean ownChurch = churchService.churchOf(playerId).map(c -> c.id().equals(churchId)).orElse(false);
        if (ownChurch && player != null && random.nextDouble() < config.exposureFailedRaidChance()) {
            churchService.expelExposedSpy(player);
        } else if (player != null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Рейд провалено.");
        }
        persist();
        persistState();
    }

    /** Смерть рейдера: провал сесії; якщо вбивця — член церкви-цілі, це захист храму. */
    public void onRaiderDied(Player raider, Player killerOrNull) {
        RaidSession session = raids.get(raider.getUniqueId());
        if (session == null) {
            return;
        }
        String churchId = session.churchId();
        session.cancel();
        raidFailed(raider.getUniqueId());
        if (killerOrNull != null) {
            churchService.onTempleDefended(killerOrNull, churchId);
        }
    }

    /** Здача рейдової здобичі в схованку ордену — саме тут зараховується RAID-задача (фавор). */
    public int depositRaidLoot(Player player, int taskIndex) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return 0;
        }
        List<OrderTask> tasks = membership.tasks();
        if (taskIndex < 0 || taskIndex >= tasks.size()) {
            return 0;
        }
        OrderTask task = tasks.get(taskIndex);
        if (task.type() != OrderTask.Type.RAID) {
            return 0;
        }
        Map<String, Integer> loot = pendingRaidLoot.get(id);
        if (loot == null || loot.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "У вас нема рейдової здобичі для здачі.");
            return 0;
        }
        OrderStash stash = stashOf(membership.institutionId());
        int deposited = 0;
        Map<String, Integer> remaining = new HashMap<>(loot);
        for (Map.Entry<String, Integer> entry : loot.entrySet()) {
            String key = entry.getKey();
            int want = entry.getValue();
            List<ItemStack> removed = removeMatching(player, key, want);
            int got = removed.stream().mapToInt(ItemStack::getAmount).sum();
            if (got > 0) {
                stash.add(key, got);
                deposited += got;
            }
            int left = want - got;
            if (left <= 0) {
                remaining.remove(key);
            } else {
                remaining.put(key, left);
            }
        }
        if (deposited <= 0) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Здобичі рейду нема у ваших руках.");
            return 0;
        }
        if (remaining.isEmpty()) {
            pendingRaidLoot.remove(id);
            int idx = indexOfTask(membership.tasks(), OrderTask.Type.RAID, task.targetKey());
            if (idx >= 0) {
                completeTaskAt(membership, idx, id);
            }
        } else {
            pendingRaidLoot.put(id, remaining);
        }
        persist();
        persistState();
        return deposited;
    }

    // ── Замах на священика ────────────────────────────────────────────────────

    /**
     * Старт замаху: гравець має ASSASSINATE-задачу на цю церкву. Священик деспавниться, храм
     * закривається до респавну ({@code priest-respawn-hours}) навіть якщо охоронця не здолано
     * (священик фізично зник — {@code isTempleClosed} має це відображати), спавниться охоронець
     * найсильнішої доступної послідовності домену церкви.
     */
    public boolean startAssassination(Player player, String priestInstitutionId) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        if (indexOfTask(membership.tasks(), OrderTask.Type.ASSASSINATE, priestInstitutionId) < 0) {
            return false;
        }
        priestService.despawnAt(priestInstitutionId, player.getLocation());
        long now = System.currentTimeMillis();
        priestClosedUntil.put(priestInstitutionId, now + config.assassinationPriestRespawnHours() * 3_600_000L);
        CreatureDefinition guardDef = strongestDomainCreature(priestInstitutionId);
        if (guardDef != null) {
            mythicGateway.spawn(guardDef.id(), player.getLocation())
                    .ifPresent(guard -> assassinationGuards.put(guard.getUniqueId(), id));
        }
        churchService.broadcastToChurch(priestInstitutionId,
                ChatColor.DARK_RED + "На священика скоєно замах!");
        player.sendMessage(PREFIX + ChatColor.DARK_RED + "Ви напали на священика. Здолайте охоронця, "
                + "щоб завершити замах.");
        persistState();
        return true;
    }

    /** Смерть охоронця замаху = замах зараховано: фавор, храм зачинено, розголос членам церкви. */
    public void onGuardKilled(UUID guardUuid, Player killer) {
        UUID assassin = assassinationGuards.remove(guardUuid);
        if (assassin == null) {
            return;
        }
        OrderMembership membership = memberships.get(assassin);
        if (membership == null) {
            return;
        }
        int idx = indexOfTask(membership.tasks(), OrderTask.Type.ASSASSINATE, null);
        if (idx < 0) {
            return;
        }
        String churchId = membership.tasks().get(idx).targetKey();
        long now = System.currentTimeMillis();
        completeTaskAt(membership, idx, assassin);
        priestClosedUntil.put(churchId, now + config.assassinationPriestRespawnHours() * 3_600_000L);
        churchService.broadcastToChurch(churchId, ChatColor.DARK_RED + "Священика вбито! Храм зачинено.");
        persist();
        persistState();
    }

    // ── Шпигунство подвійного агента ─────────────────────────────────────────

    /**
     * Sneak-клік по священику ВЛАСНОЇ церкви. Активна RECON → знімок сховища церкви в
     * розвіддані ордену; активна SABOTAGE → затримка чужого замовлення. Кожна дія: прогрес
     * задачі → фавор + ролл викриття (викриття = необоротне вигнання з церкви).
     *
     * @return true, якщо шпигунська дія відбулась (лістенер тоді НЕ відкриває меню священика).
     */
    public boolean performSpyAction(Player player, String priestInstitutionId) {
        UUID id = player.getUniqueId();
        OrderMembership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        boolean ownChurch = churchService.churchOf(id)
                .map(c -> c.id().equals(priestInstitutionId)).orElse(false);
        if (!ownChurch) {
            return false;
        }
        int reconIdx = indexOfTask(membership.tasks(), OrderTask.Type.RECON, priestInstitutionId);
        if (reconIdx >= 0) {
            long now = System.currentTimeMillis();
            Map<String, Integer> snapshot = churchService.vaultSnapshot(priestInstitutionId);
            intel.put(membership.institutionId() + "|" + priestInstitutionId,
                    new IntelState(snapshot, now + config.reconTtlHours() * 3_600_000L));
            completeTaskAt(membership, reconIdx, id);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Ви зняли розвіддані сховища церкви.");
            persist();
            persistState();
            rollExposure(player, config.exposureReconChance());
            return true;
        }
        int sabotageIdx = indexOfTask(membership.tasks(), OrderTask.Type.SABOTAGE, priestInstitutionId);
        if (sabotageIdx >= 0) {
            churchService.delayRandomBrewingOrder(priestInstitutionId,
                    config.sabotageDelayHours() * 3_600_000L, id);
            completeTaskAt(membership, sabotageIdx, id);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Ви підклали свиню варильні церкви.");
            persist();
            rollExposure(player, config.exposureSabotageChance());
            return true;
        }
        return false;
    }

    private void rollExposure(Player player, double chance) {
        if (random.nextDouble() < chance) {
            churchService.expelExposedSpy(player);
        }
    }

    public void endAllRaids() {
        for (RaidSession session : raids.values()) {
            session.cancel();
        }
        raids.clear();
        assassinationGuards.clear();
    }

    // ── Хелпери операцій ──────────────────────────────────────────────────────

    /** Перший незавершений індекс завдання типу {@code type}; {@code targetKey==null} — будь-яка ціль. */
    private int indexOfTask(List<OrderTask> tasks, OrderTask.Type type, String targetKey) {
        for (int i = 0; i < tasks.size(); i++) {
            OrderTask t = tasks.get(i);
            if (t.type() == type && !t.isComplete()
                    && (targetKey == null || t.targetKey().equals(targetKey))) {
                return i;
            }
        }
        return -1;
    }

    /** Групи {@link me.vangoo.domain.entities.PathwayGroup} доменів церкви (за доступами інституції). */
    private Set<String> churchGroups(String churchId) {
        Map<String, String> pathwayToGroup = pathwayToGroupMap();
        return registry.byId(churchId)
                .map(inst -> inst.accesses().stream()
                        .map(a -> pathwayToGroup.get(a.pathwayName()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    /** Варта тривоги: істоти домену церкви з {@code sequence <= maxSeq}; фолбек — будь-які. */
    private List<CreatureDefinition> domainCreaturePool(String churchId, int maxSeqInclusive) {
        Set<String> groups = churchGroups(churchId);
        Map<String, String> pathwayToGroup = pathwayToGroupMap();
        List<CreatureDefinition> capped = creatureRegistry.values().stream()
                .filter(c -> c.sequence() <= maxSeqInclusive)
                .toList();
        List<CreatureDefinition> domain = capped.stream()
                .filter(c -> groups.contains(pathwayToGroup.get(c.pathway())))
                .toList();
        if (!domain.isEmpty()) {
            return domain;
        }
        if (!capped.isEmpty()) {
            return capped;
        }
        return new ArrayList<>(creatureRegistry.values());
    }

    /** Охоронець замаху: найсильніша (мінімальна {@code sequence}) істота домену; фолбек — будь-яка. */
    private CreatureDefinition strongestDomainCreature(String churchId) {
        Set<String> groups = churchGroups(churchId);
        Map<String, String> pathwayToGroup = pathwayToGroupMap();
        return creatureRegistry.values().stream()
                .filter(c -> groups.contains(pathwayToGroup.get(c.pathway())))
                .min(Comparator.comparingInt(CreatureDefinition::sequence))
                .or(() -> creatureRegistry.values().stream()
                        .min(Comparator.comparingInt(CreatureDefinition::sequence)))
                .orElse(null);
    }

    private void giveRaidLoot(Player player, Map<String, Integer> loot) {
        loot.forEach((key, amount) -> {
            if (key.startsWith("custom:")) {
                customItemService.createItemStack(key.substring("custom:".length())).ifPresent(item -> {
                    item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
                    giveItem(player, item);
                });
            } else if (key.startsWith("recipe:")) {
                String[] parts = key.split(":");
                if (parts.length == 3) {
                    int seq = parseSeq(parts[2]);
                    if (seq >= 0) {
                        potionManager.getPotionsPathway(parts[1]).ifPresent(pp -> {
                            for (int i = 0; i < amount; i++) {
                                giveItem(player, recipeBookFactory.createRecipeBook(pp, seq));
                            }
                        });
                    }
                }
            } else if (key.startsWith("characteristic:")) {
                String[] parts = key.split(":");
                if (parts.length == 3) {
                    int seq = parseSeq(parts[2]);
                    if (seq >= 0) {
                        giveItem(player, characteristicCodec.create(parts[1], seq, amount));
                    }
                }
            }
        });
    }

    private static int parseSeq(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ── Приватні хелпери (скопійовано з ChurchService — вони там приватні) ────

    private static List<String> allIds(RecipeDefinition def) {
        List<String> ids = new ArrayList<>(def.mainIds());
        ids.addAll(def.auxIds());
        return ids;
    }

    private static String ingredientKey(String id) {
        return id.startsWith("custom:") ? id : "custom:" + id;
    }

    private void giveItem(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    /** Патерн GatheringService.countMatching / ChurchService.countMatching. */
    private int countMatching(Player player, String itemKey) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (classifier.classify(stack).map(c -> c.itemKey().equals(itemKey)).orElse(false)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Патерн GatheringService.removeMatching / ChurchService.removeMatching. */
    private List<ItemStack> removeMatching(Player player, String itemKey, int amount) {
        List<ItemStack> removed = new ArrayList<>();
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!classifier.classify(stack).map(c -> c.itemKey().equals(itemKey)).orElse(false)) {
                continue;
            }
            int take = Math.min(stack.getAmount(), remaining);
            remaining -= take;
            ItemStack chunk = stack.clone();
            chunk.setAmount(take);
            removed.add(chunk);
            if (take == stack.getAmount()) {
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        return removed;
    }
}
