package me.vangoo.application.services;

import me.vangoo.domain.brewing.BrewRecipe;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.market.CoinChange;
import me.vangoo.domain.market.MarketItemCategory;
import me.vangoo.domain.market.PoundMoney;
import me.vangoo.domain.organizations.ChurchRank;
import me.vangoo.domain.organizations.ChurchTask;
import me.vangoo.domain.organizations.ChurchTaskGenerator;
import me.vangoo.domain.organizations.ChurchVault;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.InstitutionRegistry;
import me.vangoo.domain.organizations.InstitutionType;
import me.vangoo.domain.organizations.Membership;
import me.vangoo.domain.organizations.PathwayAccess;
import me.vangoo.domain.organizations.PotionOrder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.organizations.ChurchConfig;
import me.vangoo.infrastructure.organizations.ChurchStateRepository;
import me.vangoo.infrastructure.organizations.JSONMembershipRepository;
import me.vangoo.infrastructure.organizations.JSONMembershipRepository.MembershipRecord;
import me.vangoo.infrastructure.organizations.JSONMembershipRepository.OrderRecord;
import me.vangoo.infrastructure.organizations.JSONMembershipRepository.PlayerChurchData;
import me.vangoo.infrastructure.organizations.JSONMembershipRepository.TaskRecord;
import me.vangoo.pathways.stub.StubPathway;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Оркестратор церков: членство, завдання (полювання/доставка), пожертви, ініціація
 * без шляху, замовлення зілль зі сховища церкви. Bukkit-залежний application-сервіс —
 * доменна математика (Task 1-5) лишається чистою і вже покрита юніт-тестами.
 * Стан персиститься після КОЖНОЇ мутації: {@link #persist()} — членства,
 * {@link #persistState()} — сховища.
 */
public class ChurchService {

    private static final String PREFIX = ChatColor.GOLD + "[Церква] " + ChatColor.RESET;

    public static final int RAMPAGER_REWARD_POINTS = 150;

    public enum JoinResult { OK, ALREADY_MEMBER, WRONG_PATHWAY, COOLDOWN, UNKNOWN_CHURCH }

    public record OrderQuote(String pathwayName, int sequence, int price, Map<String, Integer> missing) {}

    private final Plugin plugin;
    private final ChurchConfig config;
    private final InstitutionRegistry registry;
    private final BeyonderService beyonderService;
    private final PathwayManager pathwayManager;
    private final PotionManager potionManager;
    private final Map<String, Map<Integer, RecipeDefinition>> potionRecipeConfig;
    private final RecipeUnlockService recipeUnlockService;
    private final MarketItemClassifier classifier;
    private final MarketItemNamer namer;
    private final WalletService walletService;
    private final Map<String, CreatureDefinition> creatureRegistry;
    private final JSONMembershipRepository membershipRepository;
    private final ChurchStateRepository stateRepository;

    private final ChurchTaskGenerator taskGenerator = new ChurchTaskGenerator();
    private final Random random = new Random();

    // ── Стан (instance-поля; гідруються в конструкторі) ────────────────────
    private final Map<UUID, Membership> memberships = new HashMap<>();
    private final Map<UUID, Long> rejoinCooldownUntil = new HashMap<>();
    private final Set<UUID> initiationUsed = new HashSet<>();
    private final Set<UUID> trialPassed = new HashSet<>();
    private final Map<String, ChurchVault> vaults = new HashMap<>();
    private final Set<UUID> notifiedReadyOrders = new HashSet<>();

    public ChurchService(Plugin plugin, ChurchConfig config, InstitutionRegistry registry,
                         BeyonderService beyonderService, PathwayManager pathwayManager,
                         PotionManager potionManager,
                         Map<String, Map<Integer, RecipeDefinition>> potionRecipeConfig,
                         RecipeUnlockService recipeUnlockService,
                         MarketItemClassifier classifier, MarketItemNamer namer,
                         WalletService walletService,
                         Map<String, CreatureDefinition> creatureRegistry,
                         JSONMembershipRepository membershipRepository,
                         ChurchStateRepository stateRepository) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.beyonderService = beyonderService;
        this.pathwayManager = pathwayManager;
        this.potionManager = potionManager;
        this.potionRecipeConfig = potionRecipeConfig;
        this.recipeUnlockService = recipeUnlockService;
        this.classifier = classifier;
        this.namer = namer;
        this.walletService = walletService;
        this.creatureRegistry = creatureRegistry;
        this.membershipRepository = membershipRepository;
        this.stateRepository = stateRepository;
        hydrate();
    }

    // ── Гідрація / персистентність ──────────────────────────────────────────

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
                if (data.initiationUsed()) {
                    initiationUsed.add(playerId);
                }
                if (data.trialPassed()) {
                    trialPassed.add(playerId);
                }
                MembershipRecord mr = data.membership();
                if (mr == null) {
                    return;
                }
                Membership membership = new Membership(playerId, mr.institutionId());
                if (mr.lifetimeContribution() > 0) {
                    membership.addContribution(mr.lifetimeContribution());
                    int spent = mr.lifetimeContribution() - mr.balance();
                    if (spent > 0) {
                        membership.spend(spent);
                    }
                }
                membership.setLastTaskRefreshEpochMillis(mr.lastTaskRefreshEpochMillis());
                if (mr.tasks() != null) {
                    membership.setTasks(mr.tasks().stream().map(ChurchService::toTask).toList());
                }
                if (mr.initiationTask() != null) {
                    membership.setInitiation(toTask(mr.initiationTask()), mr.initiationPathway());
                }
                if (mr.activeOrder() != null) {
                    membership.setActiveOrder(toOrder(mr.activeOrder()));
                }
                memberships.put(playerId, membership);
            });
        });
        stateRepository.load().ifPresent(model -> {
            if (model.vaults() == null) {
                return;
            }
            model.vaults().forEach((institutionId, snapshot) -> {
                ChurchVault vault = new ChurchVault();
                vault.restore(snapshot);
                vaults.put(institutionId, vault);
            });
        });
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Записує усі членства (+ кулдаун вступу + флаг ініціації) у memberships.json. */
    private void persist() {
        Set<UUID> allIds = new HashSet<>();
        allIds.addAll(memberships.keySet());
        allIds.addAll(rejoinCooldownUntil.keySet());
        allIds.addAll(initiationUsed);
        allIds.addAll(trialPassed);
        Map<String, PlayerChurchData> players = new HashMap<>();
        for (UUID id : allIds) {
            Membership membership = memberships.get(id);
            MembershipRecord mr = membership == null ? null : new MembershipRecord(
                    membership.institutionId(), membership.lifetimeContribution(), membership.balance(),
                    membership.lastTaskRefreshEpochMillis(),
                    membership.tasks().stream().map(ChurchService::toRecord).toList(),
                    toRecord(membership.initiationTask()), membership.initiationPathway(),
                    toRecord(membership.activeOrder()));
            players.put(id.toString(), new PlayerChurchData(mr,
                    rejoinCooldownUntil.getOrDefault(id, 0L), initiationUsed.contains(id),
                    trialPassed.contains(id)));
        }
        membershipRepository.save(new JSONMembershipRepository.Model(players));
    }

    /** Записує вміст усіх сховищ церков у churches-state.json. */
    private void persistState() {
        Map<String, Map<String, Integer>> snapshot = new HashMap<>();
        vaults.forEach((institutionId, vault) -> snapshot.put(institutionId, vault.snapshot()));
        stateRepository.save(new ChurchStateRepository.Model(snapshot));
    }

    private static ChurchTask toTask(TaskRecord r) {
        if (r == null) {
            return null;
        }
        return new ChurchTask(ChurchTask.Type.valueOf(r.type()), r.targetKey(), r.targetName(),
                r.required(), r.progress(), r.rewardPoints());
    }

    private static TaskRecord toRecord(ChurchTask t) {
        if (t == null) {
            return null;
        }
        return new TaskRecord(t.type().name(), t.targetKey(), t.targetName(),
                t.required(), t.progress(), t.rewardPoints());
    }

    private static PotionOrder toOrder(OrderRecord r) {
        if (r == null) {
            return null;
        }
        return new PotionOrder(r.pathwayName(), r.sequence(), r.readyAtEpochMillis(), r.pointsPaid());
    }

    private static OrderRecord toRecord(PotionOrder o) {
        if (o == null) {
            return null;
        }
        return new OrderRecord(o.pathwayName(), o.sequence(), o.readyAtEpochMillis(), o.pointsPaid());
    }

    // ── Членство (правила 2, 3) ──────────────────────────────────────────────

    public JoinResult join(Player player, String institutionId) {
        UUID id = player.getUniqueId();
        if (memberships.containsKey(id)) {
            return JoinResult.ALREADY_MEMBER;
        }
        Long cooldownUntil = rejoinCooldownUntil.get(id);
        if (cooldownUntil != null && System.currentTimeMillis() < cooldownUntil) {
            return JoinResult.COOLDOWN;
        }
        Optional<Institution> institutionOpt = registry.byId(institutionId);
        if (institutionOpt.isEmpty() || institutionOpt.get().type() != InstitutionType.CHURCH) {
            return JoinResult.UNKNOWN_CHURCH;
        }
        Institution institution = institutionOpt.get();
        if (!institution.acceptsPathway(pathwayNameOf(player))) {
            return JoinResult.WRONG_PATHWAY;
        }
        memberships.put(id, new Membership(id, institutionId));
        ensureFreshTasks(player);
        persist();
        return JoinResult.OK;
    }

    public boolean leave(Player player) {
        UUID id = player.getUniqueId();
        Membership removed = memberships.remove(id);
        if (removed == null) {
            return false;
        }
        rejoinCooldownUntil.put(id, System.currentTimeMillis() + config.rejoinCooldownDays() * 86_400_000L);
        notifiedReadyOrders.remove(id);
        persist();
        return true;
    }

    public Optional<Membership> membershipOf(UUID playerId) {
        return Optional.ofNullable(memberships.get(playerId));
    }

    public Optional<Institution> churchOf(UUID playerId) {
        Membership membership = memberships.get(playerId);
        return membership == null ? Optional.empty() : registry.byId(membership.institutionId());
    }

    /** null = гравець без шляху (пасивний Beyonder-профіль ще не існує або без Pathway). */
    public String pathwayNameOf(Player player) {
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null || beyonder.getPathway() == null) {
            return null;
        }
        return beyonder.getPathway().getName();
    }

    // ── Завдання (правила 4, 5, 6) ───────────────────────────────────────────

    public void ensureFreshTasks(Player player) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - membership.lastTaskRefreshEpochMillis() < config.tasksRefreshHours() * 3_600_000L) {
            return;
        }
        Institution church = registry.byId(membership.institutionId()).orElse(null);
        if (church == null) {
            return;
        }
        Map<String, String> pathwayToGroup = new HashMap<>();
        for (String name : pathwayManager.getAllPathwayNames()) {
            Pathway pathway = pathwayManager.getPathway(name);
            if (pathway != null) {
                String group = pathway.getGroup().name();
                // Церкви посилаються на шлях у CamelCase ("Darkness"), а
                // CreatureDefinition.pathway() приходить у нижньому регістрі
                // ("darkness" — CreatureConfigLoader лоуеркейсить). Кладемо
                // обидві форми, щоб HUNT-кандидати резолвились у групу.
                pathwayToGroup.put(name, group);
                pathwayToGroup.put(name.toLowerCase(Locale.ROOT), group);
            }
        }
        List<ChurchTaskGenerator.CreatureCandidate> creatures = creatureRegistry.values().stream()
                .map(c -> new ChurchTaskGenerator.CreatureCandidate(c.id(), c.pathway(), c.sequence()))
                .toList();
        List<ChurchTaskGenerator.IngredientCandidate> ingredients = new ArrayList<>();
        for (PathwayAccess access : church.accesses()) {
            if (pathwayManager.getPathway(access.pathwayName()) == null) {
                continue;
            }
            Map<Integer, RecipeDefinition> bySeq = potionRecipeConfig.get(access.pathwayName());
            if (bySeq == null) {
                continue;
            }
            for (Map.Entry<Integer, RecipeDefinition> entry : bySeq.entrySet()) {
                int seq = entry.getKey();
                if (!access.supportsSequence(seq)) {
                    continue;
                }
                for (String rawId : allIds(entry.getValue())) {
                    if (rawId.startsWith("vanilla:")) {
                        continue;
                    }
                    String key = ingredientKey(rawId);
                    ingredients.add(new ChurchTaskGenerator.IngredientCandidate(key, namer.displayName(key), seq));
                }
            }
        }
        List<ChurchTask> tasks = taskGenerator.generate(config.tasksMaxActive(), church, pathwayToGroup,
                creatures, ingredients, random);
        membership.setTasks(tasks);
        membership.setLastTaskRefreshEpochMillis(now);
        persist();
    }

    public List<ChurchTask> tasksOf(Player player) {
        Membership membership = memberships.get(player.getUniqueId());
        return membership == null ? List.of() : membership.tasks();
    }

    public int deliverTask(Player player, int taskIndex) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null) {
            return 0;
        }
        List<ChurchTask> tasks = membership.tasks();
        if (taskIndex < 0 || taskIndex >= tasks.size()) {
            return 0;
        }
        ChurchTask task = tasks.get(taskIndex);
        if (task.type() != ChurchTask.Type.DELIVER) {
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
        vaultOf(membership.institutionId()).add(task.targetKey(), delivered);

        int sequence = seqOf(removed.get(0));
        int unitPoints = config.donationIngredientPointsBySeq().getOrDefault(sequence, 0);
        int donationPoints = unitPoints * delivered;
        if (donationPoints > 0) {
            membership.addContribution(donationPoints);
        }

        ChurchTask updated = task.withProgress(task.progress() + delivered);
        List<ChurchTask> newTasks = new ArrayList<>(tasks);
        if (updated.isComplete()) {
            newTasks.remove(taskIndex);
            membership.addContribution(updated.rewardPoints());
            player.sendMessage(PREFIX + ChatColor.GREEN + "Завдання доставки виконано: "
                    + updated.targetName() + "! +" + updated.rewardPoints() + " очок.");
        } else {
            newTasks.set(taskIndex, updated);
        }
        membership.setTasks(newTasks);
        persist();
        persistState();
        return delivered;
    }

    /** HUNT-завдання (звичайне і ініціаційне) реагують на вбивство істоти гравцем. */
    public void onCreatureKilled(Player killer, String creatureId) {
        UUID id = killer.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null) {
            return;
        }
        boolean changed = false;

        List<ChurchTask> updatedTasks = new ArrayList<>();
        for (ChurchTask t : membership.tasks()) {
            if (t.type() == ChurchTask.Type.HUNT && t.targetKey().equals(creatureId)) {
                ChurchTask updated = t.withProgress(t.progress() + 1);
                changed = true;
                if (updated.isComplete()) {
                    membership.addContribution(updated.rewardPoints());
                    killer.sendMessage(PREFIX + ChatColor.GREEN + "Завдання полювання виконано: "
                            + updated.targetName() + "! +" + updated.rewardPoints() + " очок.");
                } else {
                    updatedTasks.add(updated);
                }
            } else {
                updatedTasks.add(t);
            }
        }
        if (changed) {
            membership.setTasks(updatedTasks);
        }

        if (changed) {
            persist();
        }
    }

    public void onRampagerKilled(Player killer) {
        UUID id = killer.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null) {
            return;
        }
        membership.addContribution(RAMPAGER_REWARD_POINTS);
        killer.sendMessage(PREFIX + ChatColor.GREEN + "Церква цінує ваш подвиг: +"
                + RAMPAGER_REWARD_POINTS + " очок.");
        persist();
    }

    // ── Пожертви (правила 7, 8) ──────────────────────────────────────────────

    public int donateFromHand(Player player) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null) {
            return 0;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<MarketItemClassifier.ClassifiedItem> classified = classifier.classify(hand);
        if (classified.isEmpty()) {
            return 0;
        }
        MarketItemClassifier.ClassifiedItem item = classified.get();
        Institution church = registry.byId(membership.institutionId()).orElse(null);
        if (church == null) {
            return 0;
        }
        if (item.category() != MarketItemCategory.INGREDIENT) {
            String pathway = pathwayFromKey(item.itemKey());
            if (pathway == null || church.accessFor(pathway).isEmpty()) {
                return 0;
            }
        }
        int amount = hand.getAmount();
        Map<Integer, Integer> table = switch (item.category()) {
            case INGREDIENT -> config.donationIngredientPointsBySeq();
            case RECIPE_BOOK -> config.donationRecipePointsBySeq();
            case CHARACTERISTIC -> config.donationCharacteristicPointsBySeq();
        };
        int points = table.getOrDefault(item.sequence(), 0) * amount;

        player.getInventory().setItemInMainHand(null);
        vaultOf(membership.institutionId()).add(item.itemKey(), amount);
        if (points > 0) {
            membership.addContribution(points);
        }
        persist();
        persistState();
        return points;
    }

    public int donateCoins(Player player, PoundMoney money) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null) {
            return 0;
        }
        Optional<CoinChange> change = walletService.charge(player, money);
        if (change.isEmpty()) {
            return 0;
        }
        int points = money.coppets() * config.pointsPerCoppet();
        if (points > 0) {
            membership.addContribution(points);
        }
        persist();
        return points;
    }

    // ── Випробування шляху (дуель) ───────────────────────────────────────────

    public boolean canStartTrial(Player player) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        return pathwayNameOf(player) == null
                && !initiationUsed.contains(id)
                && !trialPassed.contains(id)
                && !initiationPathwayChoices(player).isEmpty();
    }

    public boolean hasPassedTrial(UUID playerId) {
        return trialPassed.contains(playerId);
    }

    /** Позначає, що гравець здолав дуель і може обрати шлях домену. */
    public void markTrialPassed(UUID playerId) {
        if (memberships.containsKey(playerId)) {
            trialPassed.add(playerId);
            persist();
        }
    }

    public List<String> initiationPathwayChoices(Player player) {
        Membership membership = memberships.get(player.getUniqueId());
        if (membership == null) {
            return List.of();
        }
        Institution church = registry.byId(membership.institutionId()).orElse(null);
        if (church == null) {
            return List.of();
        }
        return church.accesses().stream()
                .map(PathwayAccess::pathwayName)
                .filter(name -> {
                    Pathway pathway = pathwayManager.getPathway(name);
                    return pathway != null && !(pathway instanceof StubPathway);
                })
                .toList();
    }

    /** Після перемоги в дуелі: видає Seq-9 зілля обраного шляху + знання рецепту. */
    public boolean completeTrialInitiation(Player player, String pathwayName) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null || !trialPassed.contains(id)) {
            return false;
        }
        if (pathwayNameOf(player) != null || initiationUsed.contains(id)) {
            return false;
        }
        if (!initiationPathwayChoices(player).contains(pathwayName)) {
            return false;
        }
        giveItem(player, potionManager.createPotionItem(pathwayName, Sequence.of(9)));
        recipeUnlockService.unlockRecipe(id, pathwayName, 9);
        initiationUsed.add(id);
        trialPassed.remove(id);
        persist();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Вітаємо! Ви ступили на шлях " + pathwayName + ".");
        return true;
    }

    // ── Замовлення зілль (правило 10) ────────────────────────────────────────

    public Optional<OrderQuote> quoteOrder(Player player, String pathwayNameForPathless) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null) {
            return Optional.empty();
        }
        Institution church = registry.byId(membership.institutionId()).orElse(null);
        if (church == null) {
            return Optional.empty();
        }
        String pathwayName = pathwayNameOf(player);
        String effectivePathway;
        int targetSeq;
        if (pathwayName != null) {
            Beyonder beyonder = beyonderService.getBeyonder(id);
            if (beyonder == null) {
                return Optional.empty();
            }
            targetSeq = beyonder.getSequenceLevel() - 1;
            if (targetSeq < 0) {
                return Optional.empty();
            }
            effectivePathway = pathwayName;
        } else {
            return Optional.empty();
        }
        Optional<PathwayAccess> accessOpt = church.accessFor(effectivePathway);
        if (accessOpt.isEmpty()) {
            return Optional.empty();
        }
        PathwayAccess access = accessOpt.get();
        ChurchRank rank = membership.rank(config.rankThresholds());
        if (targetSeq < rank.lowestOrderableSequence(access)) {
            return Optional.empty();
        }
        Integer price = config.orderPointsBySeq().get(targetSeq);
        if (price == null) {
            return Optional.empty();
        }
        BrewRecipe recipe = brewRecipeFor(effectivePathway, targetSeq);
        if (recipe == null) {
            return Optional.empty();
        }
        ChurchVault vault = vaultOf(membership.institutionId());
        Map<String, Integer> missing = new LinkedHashMap<>(vault.missingFor(recipe));
        if (!vault.hasRecipeKnowledge(effectivePathway, targetSeq)) {
            missing.put("recipe:" + effectivePathway + ":" + targetSeq, 1);
        }
        return Optional.of(new OrderQuote(effectivePathway, targetSeq, price, missing));
    }

    public boolean placeOrder(Player player, String pathwayNameForPathless) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null || membership.activeOrder() != null) {
            return false;
        }
        Optional<OrderQuote> quoteOpt = quoteOrder(player, pathwayNameForPathless);
        if (quoteOpt.isEmpty()) {
            return false;
        }
        OrderQuote quote = quoteOpt.get();
        if (!quote.missing().isEmpty()) {
            return false;
        }
        if (!membership.spend(quote.price())) {
            return false;
        }
        BrewRecipe recipe = brewRecipeFor(quote.pathwayName(), quote.sequence());
        ChurchVault vault = vaultOf(membership.institutionId());
        if (recipe == null || !vault.consumeFor(recipe)) {
            return false;
        }
        membership.setActiveOrder(new PotionOrder(quote.pathwayName(), quote.sequence(),
                System.currentTimeMillis() + config.orderBrewHours() * 3_600_000L, quote.price()));
        persist();
        persistState();
        return true;
    }

    public boolean claimOrder(Player player) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        PotionOrder order = membership.activeOrder();
        if (order == null || !order.isReady(System.currentTimeMillis())) {
            return false;
        }
        giveItem(player, potionManager.createPotionItem(order.pathwayName(), Sequence.of(order.sequence())));
        membership.clearActiveOrder();
        notifiedReadyOrders.remove(id);
        persist();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Ви забрали замовлене зілля.");
        return true;
    }

    public Optional<PotionOrder> orderOf(UUID playerId) {
        Membership membership = memberships.get(playerId);
        return membership == null ? Optional.empty() : Optional.ofNullable(membership.activeOrder());
    }

    // ── Сховище церкви (правило 11) ──────────────────────────────────────────

    public void seedVaultIfAbsent(String institutionId) {
        ChurchVault existing = vaults.get(institutionId);
        // Непорожнє сховище завжди містить неспоживні ключі "recipe:<p>:<seq>" —
        // це надійна ознака "вже засіяно"; порожнє могло з'явитись лише через
        // ліниву вставку в vaultOf() і мусить бути засіяне тут.
        if (existing != null && !existing.snapshot().isEmpty()) {
            return;
        }
        Institution church = registry.byId(institutionId).orElse(null);
        if (church == null) {
            return;
        }
        ChurchVault vault = existing != null ? existing : new ChurchVault();
        for (PathwayAccess access : church.accesses()) {
            if (pathwayManager.getPathway(access.pathwayName()) == null) {
                continue;
            }
            Map<Integer, RecipeDefinition> bySeq = potionRecipeConfig.get(access.pathwayName());
            if (bySeq == null) {
                continue;
            }
            for (int seq = 9; seq >= access.minSequence(); seq--) {
                RecipeDefinition def = bySeq.get(seq);
                if (def == null) {
                    continue;
                }
                vault.add("recipe:" + access.pathwayName() + ":" + seq, 1);
                for (String rawId : allIds(def)) {
                    if (rawId.startsWith("vanilla:")) {
                        continue;
                    }
                    vault.add(ingredientKey(rawId), config.vaultSeedBrewsPerRecipe());
                }
                vault.add("characteristic:" + access.pathwayName() + ":" + seq,
                        config.vaultSeedCharacteristicsPerSeq());
            }
        }
        vaults.put(institutionId, vault);
        persistState();
    }

    public ChurchVault vaultOf(String institutionId) {
        return vaults.computeIfAbsent(institutionId, k -> new ChurchVault());
    }

    // ── Тик (правило 12) ──────────────────────────────────────────────────────

    public void tickOrders() {
        long now = System.currentTimeMillis();
        for (Membership membership : memberships.values()) {
            PotionOrder order = membership.activeOrder();
            if (order == null || !order.isReady(now)) {
                continue;
            }
            UUID id = membership.playerId();
            if (notifiedReadyOrders.contains(id)) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.sendMessage(PREFIX + ChatColor.GREEN + "Ваше зілля готове — заберіть у священика.");
            notifiedReadyOrders.add(id);
        }
    }

    // ── Довідники ────────────────────────────────────────────────────────────

    public int[] rankThresholds() {
        return config.rankThresholds();
    }

    public InstitutionRegistry registry() {
        return registry;
    }

    // ── Приватні хелпери ─────────────────────────────────────────────────────

    /** Ambiguity resolution #1: класика рецепта без vanilla:-інгредієнтів (див. task-8-brief). */
    private BrewRecipe brewRecipeFor(String pathwayName, int sequence) {
        Map<Integer, RecipeDefinition> bySeq = potionRecipeConfig.get(pathwayName);
        if (bySeq == null) {
            return null;
        }
        RecipeDefinition def = bySeq.get(sequence);
        if (def == null) {
            return null;
        }
        return new BrewRecipe(pathwayName, sequence, frequency(def.mainIds()), frequency(def.auxIds()));
    }

    private static Map<String, Integer> frequency(List<String> ids) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String rawId : ids) {
            if (rawId.startsWith("vanilla:")) {
                continue;
            }
            counts.merge(ingredientKey(rawId), 1, Integer::sum);
        }
        return counts;
    }

    private static List<String> allIds(RecipeDefinition def) {
        List<String> ids = new ArrayList<>(def.mainIds());
        ids.addAll(def.auxIds());
        return ids;
    }

    private static String ingredientKey(String id) {
        return id.startsWith("custom:") ? id : "custom:" + id;
    }

    private static String pathwayFromKey(String itemKey) {
        String[] parts = itemKey.split(":");
        return parts.length == 3 ? parts[1] : null;
    }

    private String describeMissing(Map<String, Integer> missing) {
        return missing.entrySet().stream()
                .map(e -> namer.displayName(e.getKey()) + " x" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private int seqOf(ItemStack stack) {
        return classifier.classify(stack).map(MarketItemClassifier.ClassifiedItem::sequence).orElse(-1);
    }

    private void giveItem(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    /** Патерн GatheringService.countMatching (application/services/GatheringService.java:880-891). */
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

    /** Патерн GatheringService.removeMatching (application/services/GatheringService.java:894+). */
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
