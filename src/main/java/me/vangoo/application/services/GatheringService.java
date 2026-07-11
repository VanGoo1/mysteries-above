package me.vangoo.application.services;

import me.vangoo.domain.market.GatheringPhase;
import me.vangoo.domain.market.MarketSession;
import me.vangoo.domain.market.MarketSession.AcceptResult;
import me.vangoo.domain.market.MarketSession.BuyOrder;
import me.vangoo.domain.market.MarketSession.Lot;
import me.vangoo.domain.market.MarketSession.MarketException;
import me.vangoo.domain.market.MarketSession.NegotiationView;
import me.vangoo.domain.market.MarketSession.Refund;
import me.vangoo.domain.market.MarketSession.Settlement;
import me.vangoo.domain.market.PoundMoney;
import me.vangoo.domain.valueobjects.UnlockedRecipe;
import me.vangoo.infrastructure.citizens.OrganizerNpcService;
import me.vangoo.infrastructure.market.GatheringAnonymizer;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository.EscrowItem;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository.ParticipantHome;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository.Snapshot;
import me.vangoo.infrastructure.market.GatheringVenueProvider;
import me.vangoo.infrastructure.market.MarketConfig;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Оркестратор Зборів Потойбічних: фази (IDLE→ANNOUNCED→OPEN→CLOSING→IDLE),
 * телепорт/анонімність/NPC, ескроу реальних стаків і виконання Settlement/Refund
 * команд чистої MarketSession. Після кожної мутації — снепшот на диск.
 */
public class GatheringService {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Збори] " + ChatColor.RESET;

    private record EscrowEntry(UUID ownerId, ItemStack stack) {}

    private final Plugin plugin;
    private final MarketConfig config;
    private final WalletService walletService;
    private final MarketItemClassifier classifier;
    private final GatheringVenueProvider venueProvider;
    private final GatheringAnonymizer anonymizer;
    private final GatheringSnapshotRepository snapshotRepository;
    private final OrganizerNpcService organizerNpc;
    private final BeyonderService beyonderService;
    private final RecipeUnlockService recipeUnlockService;
    private final PotionManager potionManager;

    private volatile GatheringPhase phase = GatheringPhase.IDLE;
    private MarketSession session;
    /**
     * Потокобезпечне дзеркало учасників відкритого збору для читання з async-потоку
     * (обробник AsyncPlayerChatEvent). Мутується лише в головному потоці (open/close);
     * решта стану (session/aliases) — не чіпати поза головним потоком.
     */
    private final Set<UUID> openParticipantIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, EscrowEntry> escrow = new HashMap<>();
    private final Set<UUID> joined = new LinkedHashSet<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();
    private final Map<UUID, ParticipantHome> crashHomes = new HashMap<>();
    private final Set<UUID> bannedFromNext = new HashSet<>();
    private long nextGatheringMillis;
    private long openAtMillis;
    private final List<BukkitTask> phaseTasks = new ArrayList<>();

    public GatheringService(Plugin plugin, MarketConfig config, WalletService walletService,
                            MarketItemClassifier classifier, GatheringVenueProvider venueProvider,
                            GatheringAnonymizer anonymizer, GatheringSnapshotRepository snapshotRepository,
                            OrganizerNpcService organizerNpc, BeyonderService beyonderService,
                            RecipeUnlockService recipeUnlockService, PotionManager potionManager) {
        this.plugin = plugin;
        this.config = config;
        this.walletService = walletService;
        this.classifier = classifier;
        this.venueProvider = venueProvider;
        this.anonymizer = anonymizer;
        this.snapshotRepository = snapshotRepository;
        this.organizerNpc = organizerNpc;
        this.beyonderService = beyonderService;
        this.recipeUnlockService = recipeUnlockService;
        this.potionManager = potionManager;
    }

    // ── Відновлення після рестарту/крашу ────────────────────────────────────

    /** Викликати з onEnable. Незакрита сесія НЕ продовжується — все повертається власникам. */
    public void initializeFromSnapshot() {
        Optional<Snapshot> loaded = snapshotRepository.load();
        if (loaded.isEmpty()) {
            nextGatheringMillis = System.currentTimeMillis() + intervalMillis();
            persist();
            return;
        }
        Snapshot snapshot = loaded.get();
        nextGatheringMillis = snapshot.nextGatheringEpochMillis();
        if (snapshot.bannedFromNext() != null) {
            snapshot.bannedFromNext().forEach(id -> bannedFromNext.add(UUID.fromString(id)));
        }
        for (EscrowItem item : snapshot.pendingReturns()) {
            queueReturn(UUID.fromString(item.ownerId()),
                    GatheringSnapshotRepository.decodeStack(item.base64Stack()));
        }
        // Краш посеред збору: ескроу → повернення, доми учасників → на телепорт при вході
        for (EscrowItem item : snapshot.escrow()) {
            queueReturn(UUID.fromString(item.ownerId()),
                    GatheringSnapshotRepository.decodeStack(item.base64Stack()));
        }
        for (ParticipantHome home : snapshot.participants()) {
            crashHomes.put(UUID.fromString(home.playerId()), home);
        }
        if (!snapshot.escrow().isEmpty() || !snapshot.participants().isEmpty()) {
            plugin.getLogger().warning("Gathering session was interrupted by restart; "
                    + "escrow queued for return to " + snapshot.escrow().size() + " owners");
        }
        persist();
    }

    // ── Фази ─────────────────────────────────────────────────────────────────

    public GatheringPhase phase() {
        return phase;
    }

    public long nextGatheringMillis() {
        return nextGatheringMillis;
    }

    /** Оголошення збору (планувальник або /gathering start). */
    public void announce() {
        if (!phase.canTransitionTo(GatheringPhase.ANNOUNCED)) {
            return;
        }
        phase = GatheringPhase.ANNOUNCED;
        joined.clear();
        nextGatheringMillis = System.currentTimeMillis() + intervalMillis();
        persist();

        TextComponent invite = new TextComponent(PREFIX + ChatColor.LIGHT_PURPLE
                + "Шепіт у пітьмі: сьогодні Потойбічні збираються в таємному місці... ");
        TextComponent button = new TextComponent(ChatColor.GREEN + "" + ChatColor.BOLD + "[Прийти]");
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gathering join"));
        button.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GRAY + "Погодитись піти на Збори").create()));
        invite.addExtra(button);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (beyonderService.getBeyonder(player.getUniqueId()) != null) {
                player.spigot().sendMessage(invite);
                player.sendMessage(PREFIX + ChatColor.GRAY + "Вікно згоди — "
                        + config.joinWindowMinutes() + " хв. Візьміть речі на обмін із собою.");
            }
        }
        long joinWindowTicks = config.joinWindowMinutes() * 60L * 20L;
        openAtMillis = System.currentTimeMillis() + config.joinWindowMinutes() * 60L * 1000L;
        phaseTasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::announceCountdown, 20L * 60L, 20L * 60L));
        schedule(() -> open(), joinWindowTicks);
    }

    private void announceCountdown() {
        if (phase != GatheringPhase.ANNOUNCED) {
            return;
        }
        long remainingMillis = openAtMillis - System.currentTimeMillis();
        long minutes = Math.round(remainingMillis / 60000.0);
        String when = minutes <= 1 ? "менше ніж за хвилину" : "за " + minutes + " хв";
        for (UUID id : joined) {
            notify(id, PREFIX + ChatColor.LIGHT_PURPLE + "Збори розпочнуться " + when + ".");
        }
    }

    public boolean join(Player player) {
        if (phase != GatheringPhase.ANNOUNCED) {
            player.sendMessage(PREFIX + ChatColor.RED + "Зараз немає відкритого запрошення.");
            return false;
        }
        if (beyonderService.getBeyonder(player.getUniqueId()) == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Збори — лише для Потойбічних.");
            return false;
        }
        if (joined.add(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.GREEN
                    + "Ви відчуваєте тяжіння... Не опирайтесь, коли настане час.");
        }
        return true;
    }

    private void open() {
        if (phase != GatheringPhase.ANNOUNCED) {
            return;
        }
        cancelPhaseTasks();
        List<Player> attendees = new ArrayList<>();
        for (UUID id : joined) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                attendees.add(player);
            }
        }
        if (attendees.isEmpty()) {
            phase = GatheringPhase.IDLE;
            cancelPhaseTasks();
            persist();
            return;
        }
        phase = GatheringPhase.OPEN;
        session = new MarketSession(config.commissionRate(), new Random());
        Location venue = venueProvider.venueSpawn();
        for (Player player : attendees) {
            session.registerParticipant(player.getUniqueId());
            openParticipantIds.add(player.getUniqueId());
            returnLocations.put(player.getUniqueId(), player.getLocation());
            player.teleport(venue);
            anonymizer.mask(player, session.aliasOf(player.getUniqueId()));
        }
        organizerNpc.spawn(venue);
        broadcastToParticipants(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                + "Посередник: Вітаю в місці, якого немає на жодній мапі. Тут ніхто не має імені.");
        broadcastToParticipants(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                + "Посередник: Я ручаюся за справжність кожної речі. Торгуйте — /gathering menu. "
                + "Маєте непотріб — принесіть мені, скуплю.");
        broadcastToParticipants(PREFIX + ChatColor.GRAY + "Збір триватиме "
                + config.durationMinutes() + " хв.");
        long durationTicks = config.durationMinutes() * 60L * 20L;
        if (config.durationMinutes() > 5) {
            schedule(() -> broadcastToParticipants(PREFIX + ChatColor.YELLOW
                    + "Збір закінчиться за 5 хвилин!"), durationTicks - 5 * 60L * 20L);
        }
        schedule(() -> broadcastToParticipants(PREFIX + ChatColor.YELLOW
                + "Збір закінчиться за 1 хвилину!"), durationTicks - 60L * 20L);
        schedule(this::close, durationTicks);
        persist();
    }

    private void close() {
        if (phase != GatheringPhase.OPEN) {
            return;
        }
        phase = GatheringPhase.CLOSING;
        cancelPhaseTasks();
        for (Refund refund : session.close()) {
            releaseEscrow(refund);
        }
        // Захист: якщо в ескроу щось лишилось (не мало б) — теж повернути
        for (Map.Entry<UUID, EscrowEntry> orphan : Map.copyOf(escrow).entrySet()) {
            escrow.remove(orphan.getKey());
            deliverItem(orphan.getValue().ownerId(), orphan.getValue().stack());
        }
        for (UUID id : Set.copyOf(returnLocations.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                anonymizer.unmask(player);
                player.teleport(returnLocations.remove(id));
                player.sendMessage(PREFIX + ChatColor.GRAY
                        + "Збір завершено. Ви знову там, звідки прийшли.");
            }
            // офлайн: локація лишається — handleJoin поверне при вході
        }
        anonymizer.unmaskAll();
        organizerNpc.despawn();
        session = null;
        joined.clear();
        openParticipantIds.clear();
        phase = GatheringPhase.IDLE;
        persist();
    }

    /** /gathering stop та onDisable: коректно закрити активний збір. */
    public void forceCloseIfActive() {
        if (phase == GatheringPhase.OPEN) {
            close();
        } else if (phase == GatheringPhase.ANNOUNCED) {
            phase = GatheringPhase.IDLE;
            cancelPhaseTasks();
            joined.clear();
            persist();
        }
    }

    // ── Ринкові операції (усі вимагають OPEN + учасник) ──────────────────────

    public boolean listLotFromHand(Player seller, PoundMoney price) {
        return guarded(seller, () -> {
            ItemStack hand = requireHandItem(seller);
            var classified = classifier.classify(hand).orElseThrow(() -> new MarketException(
                    "Це не потойбічна річ — на ринку їй не місце (інгредієнти, Характеристики, книги рецептів)"));
            UUID lotId = session.listLot(seller.getUniqueId(), classified.itemKey(), hand.getAmount(), price);
            escrow.put(lotId, new EscrowEntry(seller.getUniqueId(), hand.clone()));
            seller.getInventory().setItemInMainHand(null);
            seller.sendMessage(PREFIX + ChatColor.GREEN + "Лот виставлено: "
                    + describe(hand) + ChatColor.GREEN + " за " + price.format());
            persist();
        });
    }

    public boolean buyLot(Player buyer, UUID lotId) {
        return guarded(buyer, () -> {
            Settlement s = session.buyLot(buyer.getUniqueId(), lotId,
                    walletService.countPounds(buyer), walletService.countCoppets(buyer));
            settle(buyer, s);
            buyer.sendMessage(PREFIX + ChatColor.GREEN + "Куплено за " + s.price().format() + ".");
        });
    }

    public boolean placeOrder(Player buyer, String itemKey, int amount) {
        return guarded(buyer, () -> {
            session.placeOrder(buyer.getUniqueId(), itemKey, amount, knownIngredientKeys(buyer));
            buyer.sendMessage(PREFIX + ChatColor.GREEN
                    + "Замовлення розміщено. Чекайте на пропозиції продавців.");
            persist();
        });
    }

    public boolean offerFromHand(Player seller, UUID orderId, PoundMoney price) {
        return guarded(seller, () -> {
            BuyOrder order = session.openOrders().stream()
                    .filter(o -> o.orderId().equals(orderId)).findFirst()
                    .orElseThrow(() -> new MarketException("Замовлення вже недоступне"));
            ItemStack hand = requireHandItem(seller);
            var classified = classifier.classify(hand).orElseThrow(
                    () -> new MarketException("Це не потойбічна річ"));
            if (!classified.itemKey().equals(order.itemKey()) || hand.getAmount() != order.amount()) {
                throw new MarketException("У руці має бути саме те, що замовлено: потрібна кількість — "
                        + order.amount());
            }
            UUID negotiationId = session.offerOnOrder(seller.getUniqueId(), orderId, price);
            escrow.put(negotiationId, new EscrowEntry(seller.getUniqueId(), hand.clone()));
            seller.getInventory().setItemInMainHand(null);
            seller.sendMessage(PREFIX + ChatColor.GREEN + "Пропозицію зроблено: " + price.format());
            notify(order.buyerId(), PREFIX + ChatColor.YELLOW + session.aliasOf(seller.getUniqueId())
                    + " пропонує ваше замовлення за " + price.format()
                    + ChatColor.GRAY + " — див. «Мої угоди»");
            persist();
        });
    }

    public boolean counter(Player actor, UUID negotiationId, PoundMoney price) {
        return guarded(actor, () -> {
            session.counter(actor.getUniqueId(), negotiationId, price);
            actor.sendMessage(PREFIX + ChatColor.GREEN + "Зустрічна ціна: " + price.format());
            otherParty(negotiationId, actor.getUniqueId()).ifPresent(other -> notify(other,
                    PREFIX + ChatColor.YELLOW + session.aliasOf(actor.getUniqueId())
                            + " дає зустрічну ціну " + price.format()
                            + ChatColor.GRAY + " — див. «Мої угоди»"));
            persist();
        });
    }

    public boolean accept(Player actor, UUID negotiationId) {
        return guarded(actor, () -> {
            NegotiationView view = session.negotiationsOf(actor.getUniqueId()).stream()
                    .filter(n -> n.negotiationId().equals(negotiationId)).findFirst()
                    .orElseThrow(() -> new MarketException("Цей торг уже завершено"));
            Player buyer = Bukkit.getPlayer(view.buyerId());
            if (buyer == null || !buyer.isOnline()) {
                Refund refund = session.withdraw(actor.getUniqueId(), negotiationId);
                releaseEscrow(refund);
                persist();
                throw new MarketException("Покупець покинув збір — торг скасовано");
            }
            AcceptResult result = session.accept(actor.getUniqueId(), negotiationId,
                    walletService.countPounds(buyer), walletService.countCoppets(buyer));
            for (Refund released : result.releasedEscrows()) {
                releaseEscrow(released);
                notify(released.ownerId(), PREFIX + ChatColor.GRAY
                        + "Замовлення закрили без вас — вашу річ повернуто.");
            }
            settle(buyer, result.settlement());
            actor.sendMessage(PREFIX + ChatColor.GREEN + "Угоду укладено: "
                    + result.settlement().price().format());
        });
    }

    public boolean withdraw(Player actor, UUID negotiationId) {
        return guarded(actor, () -> {
            Refund refund = session.withdraw(actor.getUniqueId(), negotiationId);
            releaseEscrow(refund);
            actor.sendMessage(PREFIX + ChatColor.GRAY + "Торг скасовано.");
            otherPartyOfClosed(refund, actor.getUniqueId());
            persist();
        });
    }

    /** Скупка організатором: предмет із руки згорає, монети з'являються (емісія). */
    public boolean buybackFromHand(Player seller) {
        return guarded(seller, () -> {
            ItemStack hand = requireHandItem(seller);
            var classified = classifier.classify(hand).orElseThrow(
                    () -> new MarketException("Посередник: «Таке я не скуповую»"));
            PoundMoney unit = config.buyback().unitPriceFor(
                    classified.category(), classified.sequence(), classified.itemKey());
            if (unit.isZero()) {
                throw new MarketException("Посередник: «За таке я не дам і коппета»");
            }
            PoundMoney total = unit.times(hand.getAmount());
            seller.getInventory().setItemInMainHand(null);
            walletService.give(seller, total);
            seller.sendMessage(PREFIX + ChatColor.GREEN + "Посередник забрав "
                    + describe(hand) + ChatColor.GREEN + " і відсипав " + total.format());
        });
    }

    // ── В'ю для GUI ──────────────────────────────────────────────────────────

    public boolean isOpenParticipant(Player player) {
        return phase == GatheringPhase.OPEN && session != null
                && session.isParticipant(player.getUniqueId());
    }

    /**
     * Потокобезпечний варіант для async-контексту (чат зали): читає лише volatile-фазу
     * і concurrent-set учасників, не торкаючись session/aliases.
     */
    public boolean isOpenParticipant(UUID playerId) {
        return phase == GatheringPhase.OPEN && openParticipantIds.contains(playerId);
    }

    public List<Lot> activeLots() {
        return session == null ? List.of() : session.activeLots();
    }

    public List<BuyOrder> openOrders() {
        return session == null ? List.of() : session.openOrders();
    }

    public List<NegotiationView> negotiationsOf(UUID playerId) {
        return session == null ? List.of() : session.negotiationsOf(playerId);
    }

    public String aliasOf(UUID playerId) {
        return session == null ? "?" : session.aliasOf(playerId);
    }

    /** Клон ескроу-стака для рендера в GUI (оригінал лишається у сховищі). */
    public ItemStack escrowStack(UUID escrowRef) {
        EscrowEntry entry = escrow.get(escrowRef);
        return entry == null ? null : entry.stack().clone();
    }

    /** itemKey предмета, якщо він валідний для ринку (делегат для GUI). */
    public Optional<String> classifyKey(ItemStack stack) {
        return classifier.classify(stack).map(c -> c.itemKey());
    }

    /** itemKey-и інгредієнтів усіх розблокованих гравцем рецептів. */
    public Set<String> knownIngredientKeys(Player player) {
        Set<String> keys = new HashSet<>();
        for (ItemStack stack : knownIngredientStacks(player)) {
            classifier.classify(stack).ifPresent(c -> keys.add(c.itemKey()));
        }
        return keys;
    }

    /** Стаки-зразки інгредієнтів відомих рецептів (для меню створення замовлення). */
    public List<ItemStack> knownIngredientStacks(Player player) {
        List<ItemStack> stacks = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (UnlockedRecipe recipe : recipeUnlockService.getUnlockedRecipes(player.getUniqueId())) {
            potionManager.getPotionsPathway(recipe.pathwayName()).ifPresent(potions -> {
                ItemStack[] ingredients = potions.getIngredients(recipe.sequence());
                if (ingredients == null) {
                    return;
                }
                for (ItemStack ingredient : ingredients) {
                    classifier.classify(ingredient).ifPresent(c -> {
                        if (seenKeys.add(c.itemKey())) {
                            ItemStack sample = ingredient.clone();
                            sample.setAmount(1);
                            stacks.add(sample);
                        }
                    });
                }
            });
        }
        return stacks;
    }

    // ── Вхід/вихід гравців ───────────────────────────────────────────────────

    public void handleJoin(Player player) {
        UUID id = player.getUniqueId();
        // 1) черга повернень (предмети/монети з минулих сесій)
        List<ItemStack> queued = pendingReturns.remove(id);
        if (queued != null) {
            for (ItemStack stack : queued) {
                player.getInventory().addItem(stack).values()
                        .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            }
            player.sendMessage(PREFIX + ChatColor.GREEN + "Вам повернуто речі зі Зборів.");
            persist();
        }
        // 2) застряг у світі-заглушці (краш/вихід під час збору) → додому
        if (venueProvider.isVenueWorld(player.getWorld()) && !isOpenParticipant(player)) {
            Location home = returnLocations.remove(id);
            if (home == null) {
                ParticipantHome crashHome = crashHomes.remove(id);
                if (crashHome != null && Bukkit.getWorld(crashHome.world()) != null) {
                    home = new Location(Bukkit.getWorld(crashHome.world()), crashHome.x(),
                            crashHome.y(), crashHome.z(), crashHome.yaw(), crashHome.pitch());
                }
            }
            player.teleport(home != null ? home
                    : Bukkit.getWorlds().get(0).getSpawnLocation());
            persist();
        }
    }

    public void handleQuit(Player player) {
        if (!isOpenParticipant(player)) {
            return;
        }
        // Його відкриті торги скасовуються (ескроу продавцям), лоти лишаються висіти
        for (NegotiationView view : session.negotiationsOf(player.getUniqueId())) {
            try {
                Refund refund = session.withdraw(player.getUniqueId(), view.negotiationId());
                releaseEscrow(refund);
            } catch (MarketException ignored) {
                // торг уже закрився паралельно — нічого повертати
            }
        }
        anonymizer.unmask(player);
        persist();
    }

    // ── Приватні хелпери ─────────────────────────────────────────────────────

    private void settle(Player buyer, Settlement s) {
        walletService.charge(buyer, s.price()).orElseThrow(
                () -> new IllegalStateException("Wallet changed between check and charge"));
        EscrowEntry entry = escrow.remove(s.escrowRef());
        if (entry != null) {
            deliverItem(s.payerId(), entry.stack());
        }
        deliverMoney(s.payeeId(), s.sellerProceeds());
        notify(s.payeeId(), PREFIX + ChatColor.GREEN + "Вашу річ продано: +"
                + s.sellerProceeds().format() + ChatColor.GRAY + " (комісія посередника: "
                + s.commissionPaid().format() + ")");
        persist();
    }

    private void releaseEscrow(Refund refund) {
        EscrowEntry entry = escrow.remove(refund.escrowRef());
        if (entry != null) {
            deliverItem(refund.ownerId(), entry.stack());
        }
    }

    private void deliverItem(UUID ownerId, ItemStack stack) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            owner.getInventory().addItem(stack).values()
                    .forEach(rest -> owner.getWorld().dropItem(owner.getLocation(), rest));
        } else {
            queueReturn(ownerId, stack);
        }
    }

    private void deliverMoney(UUID ownerId, PoundMoney money) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            walletService.give(owner, money);
        } else {
            walletService.asStacks(money).forEach(stack -> queueReturn(ownerId, stack));
        }
    }

    private void queueReturn(UUID ownerId, ItemStack stack) {
        pendingReturns.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(stack);
    }

    private void notify(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    private Optional<UUID> otherParty(UUID negotiationId, UUID actorId) {
        return session.negotiationsOf(actorId).stream()
                .filter(n -> n.negotiationId().equals(negotiationId))
                .map(n -> n.sellerId().equals(actorId) ? n.buyerId() : n.sellerId())
                .findFirst();
    }

    private void otherPartyOfClosed(Refund refund, UUID actorId) {
        if (!refund.ownerId().equals(actorId)) {
            notify(refund.ownerId(), PREFIX + ChatColor.GRAY
                    + "Торг скасовано — вашу річ повернуто.");
        }
    }

    private ItemStack requireHandItem(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            throw new MarketException("Візьміть предмет у головну руку");
        }
        return hand;
    }

    private boolean guarded(Player player, Runnable action) {
        if (!isOpenParticipant(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ринок зараз не відкритий для вас.");
            return false;
        }
        try {
            action.run();
            return true;
        } catch (MarketException e) {
            player.sendMessage(PREFIX + ChatColor.RED + e.getMessage());
            return false;
        }
    }

    private void broadcastToParticipants(String message) {
        if (session == null) {
            return;
        }
        for (UUID id : returnLocations.keySet()) {
            notify(id, message);
        }
    }

    private void schedule(Runnable action, long delayTicks) {
        if (delayTicks > 0) {
            phaseTasks.add(Bukkit.getScheduler().runTaskLater(plugin, action, delayTicks));
        }
    }

    private void cancelPhaseTasks() {
        phaseTasks.forEach(BukkitTask::cancel);
        phaseTasks.clear();
    }

    private long intervalMillis() {
        return config.intervalDays() * 24L * 60L * 60L * 1000L;
    }

    private String describe(ItemStack stack) {
        String name = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                ? stack.getItemMeta().getDisplayName()
                : stack.getType().name().toLowerCase().replace('_', ' ');
        return name + ChatColor.RESET + " ×" + stack.getAmount();
    }

    private void persist() {
        List<ParticipantHome> homes = new ArrayList<>();
        returnLocations.forEach((id, loc) -> homes.add(new ParticipantHome(id.toString(),
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch())));
        List<EscrowItem> escrowItems = new ArrayList<>();
        escrow.forEach((ref, entry) -> escrowItems.add(new EscrowItem(
                entry.ownerId().toString(), GatheringSnapshotRepository.encodeStack(entry.stack()))));
        List<EscrowItem> returns = new ArrayList<>();
        pendingReturns.forEach((owner, stacks) -> stacks.forEach(stack -> returns.add(
                new EscrowItem(owner.toString(), GatheringSnapshotRepository.encodeStack(stack)))));
        List<String> banned = new ArrayList<>();
        bannedFromNext.forEach(id -> banned.add(id.toString()));
        snapshotRepository.save(new Snapshot(nextGatheringMillis, homes, escrowItems, returns, banned));
    }
}
