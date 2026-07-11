package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import me.vangoo.application.services.GatheringService;
import me.vangoo.application.services.MarketItemNamer;
import me.vangoo.application.services.WalletService;
import me.vangoo.domain.market.MarketSession.BuyOrder;
import me.vangoo.domain.market.MarketSession.Lot;
import me.vangoo.domain.market.MarketSession.NegotiationView;
import me.vangoo.domain.market.PoundMoney;
import me.vangoo.presentation.listeners.ChatPromptService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Меню підпільного ринку (фаза OPEN): Лоти / Замовлення / Мої угоди.
 * Виставлення лота й оферта — предметом у головній руці; ціни — чат-промптом.
 */
public class MarketMenu {

    private static final String PRICE_HINT =
            ChatColor.GOLD + "Напишіть ціну в чат: «<фунти> <коппети>», напр. «2 15» або «7»";

    private final Plugin plugin;
    private final GatheringService gatheringService;
    private final WalletService walletService;
    private final ChatPromptService prompts;
    private final MarketItemNamer namer;

    public MarketMenu(Plugin plugin, GatheringService gatheringService,
                      WalletService walletService, ChatPromptService prompts,
                      MarketItemNamer namer) {
        this.plugin = plugin;
        this.gatheringService = gatheringService;
        this.walletService = walletService;
        this.prompts = prompts;
        this.namer = namer;
    }

    // ── Головне меню ─────────────────────────────────────────────────────────

    public void openMain(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("🕯 Підпільний ринок").color(NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .rows(3)
                .disableAllInteractions()
                .create();
        gui.setItem(2, 2, new GuiItem(button(Material.CHEST, ChatColor.GOLD + "Лоти",
                        "Купити виставлене іншими"),
                e -> runSynced(player, () -> openLots(player))));
        gui.setItem(2, 4, new GuiItem(button(Material.WRITABLE_BOOK, ChatColor.AQUA + "Замовлення",
                        "Розмістити запит або відповісти на чужий"),
                e -> runSynced(player, () -> openOrders(player))));
        gui.setItem(2, 6, new GuiItem(button(Material.LECTERN, ChatColor.YELLOW + "Мої угоди",
                        "Активні торги: прийняти / зустрічна / відмова"),
                e -> runSynced(player, () -> openNegotiations(player))));
        gui.setItem(2, 8, new GuiItem(button(Material.GOLD_NUGGET, ChatColor.GREEN + "Виставити лот",
                        "Продати предмет із ГОЛОВНОЇ РУКИ за вашу ціну"),
                e -> promptListLot(player)));
        gui.setItem(3, 5, new GuiItem(button(Material.SUNFLOWER,
                ChatColor.GOLD + "Ваш гаманець",
                walletService.countPounds(player) + " ф "
                        + walletService.countCoppets(player) + " к (монетами)")));
        gui.setItem(1, 5, new GuiItem(button(Material.KNOWLEDGE_BOOK,
                        ChatColor.LIGHT_PURPLE + "Принципи торгівлі",
                        "Правила й порядок торгів (як розповів Посередник)"),
                e -> runSynced(player, () -> openPrinciples(player))));
        gui.open(player);
    }

    private void openPrinciples(Player player) {
        if (!gatheringService.hasBeenBriefed(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "[Збори] Спершу вислухайте Посередника.");
            runSynced(player, () -> openMain(player));
            return;
        }
        Gui gui = Gui.gui()
                .title(Component.text("🕯 Принципи торгівлі").color(NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .rows(6)
                .disableAllInteractions()
                .create();
        ItemStack scroll = button(Material.WRITTEN_BOOK, ChatColor.GOLD + "Порядок торгів");
        appendLore(scroll, List.of(
                "",
                ChatColor.WHITE + "• Лот: виставте річ із ГОЛОВНОЇ РУКИ за свою ціну.",
                ChatColor.WHITE + "• Замовлення: попросіть потрібне — продавці дадуть оферти.",
                ChatColor.WHITE + "• Торг: приймайте, давайте зустрічну ціну або відмовляйтесь.",
                ChatColor.WHITE + "• Скупка: принесіть непотріб Посереднику за монету.",
                ChatColor.WHITE + "• Комісія: з кожної угоди Посередник бере частку.",
                ChatColor.WHITE + "• Анонімність: усі бачать лише «Незнайомець №N».",
                "",
                ChatColor.RED + "• Насильство й здібності тут заборонені."));
        gui.setItem(3, 5, new GuiItem(scroll, e -> e.setCancelled(true)));
        gui.setItem(6, 1, new GuiItem(button(Material.BARRIER, ChatColor.GRAY + "◄ Назад"),
                e -> Bukkit.getScheduler().runTask(plugin, () -> openMain(player))));
        gui.open(player);
    }

    // ── Лоти ─────────────────────────────────────────────────────────────────

    private void openLots(Player player) {
        PaginatedGui gui = paginated("🕯 Лоти", () -> openMain(player));
        for (Lot lot : gatheringService.activeLots()) {
            ItemStack display = gatheringService.escrowStack(lot.lotId());
            if (display == null) {
                continue;
            }
            boolean own = lot.sellerId().equals(player.getUniqueId());
            List<String> lore = new ArrayList<>(List.of(
                    "",
                    ChatColor.GOLD + "Ціна: " + lot.price().money().format(),
                    ChatColor.DARK_GRAY + "Продавець: " + gatheringService.aliasOf(lot.sellerId()),
                    ""));
            lore.add(own ? ChatColor.GRAY + "Це ваш лот (повернеться після збору, якщо не продано)"
                    : ChatColor.GREEN + "▸ Клацніть, щоб купити");
            appendLore(display, lore);
            gui.addItem(new GuiItem(display, e -> {
                e.setCancelled(true);
                if (own) {
                    return;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                gatheringService.buyLot(player, lot.lotId());
                runSynced(player, () -> openLots(player)); // оновити список
            }));
        }
        gui.open(player);
    }

    private void promptListLot(Player player) {
        prompts.prompt(player, PRICE_HINT + ChatColor.GRAY + " — за предмет у вашій руці",
                withPrice(player, price -> gatheringService.listLotFromHand(player, price)));
    }

    // ── Замовлення ───────────────────────────────────────────────────────────

    private void openOrders(Player player) {
        PaginatedGui gui = paginated("🕯 Замовлення", () -> openMain(player));
        // Кнопка «створити» — фіксований слот унизу
        gui.setItem(6, 5, new GuiItem(button(Material.NETHER_STAR,
                        ChatColor.GREEN + "Створити замовлення",
                        "Обрати інгредієнт із відомих вам рецептів"),
                e -> runSynced(player, () -> openKnownIngredients(player))));
        for (BuyOrder order : gatheringService.openOrders()) {
            boolean own = order.buyerId().equals(player.getUniqueId());
            ItemStack display = button(Material.PAPER,
                    ChatColor.AQUA + "Шукають: " + ChatColor.WHITE
                            + namer.displayName(order.itemKey()) + " ×" + order.amount(),
                    ChatColor.DARK_GRAY + "Замовник: " + gatheringService.aliasOf(order.buyerId()));
            appendLore(display, List.of("", own
                    ? ChatColor.GRAY + "Це ваше замовлення — чекайте на пропозиції"
                    : ChatColor.GREEN + "▸ Клацніть із предметом у РУЦІ, щоб запропонувати ціну"));
            gui.addItem(new GuiItem(display, e -> {
                e.setCancelled(true);
                if (own) {
                    return;
                }
                prompts.prompt(player, PRICE_HINT + ChatColor.GRAY + " — ваша ціна за це замовлення",
                        withPrice(player, price ->
                                gatheringService.offerFromHand(player, order.orderId(), price)));
            }));
        }
        gui.open(player);
    }

    private void openKnownIngredients(Player player) {
        PaginatedGui gui = paginated("🕯 Інгредієнти ваших рецептів", () -> openOrders(player));
        for (ItemStack sample : gatheringService.knownIngredientStacks(player)) {
            String itemKey = gatheringService.classifyKey(sample).orElse(null);
            if (itemKey == null) {
                continue;
            }
            ItemStack display = sample.clone();
            appendLore(display, List.of("", ChatColor.GREEN + "▸ Клацніть, щоб замовити"));
            gui.addItem(new GuiItem(display, e -> {
                e.setCancelled(true);
                prompts.prompt(player, ChatColor.GOLD + "Напишіть кількість (1–64):", input -> {
                    int amount = parseAmount(input);
                    if (amount <= 0) {
                        player.sendMessage(ChatColor.RED + "Невірна кількість.");
                        return;
                    }
                    gatheringService.placeOrder(player, itemKey, amount);
                });
            }));
        }
        gui.open(player);
    }

    // ── Мої угоди (торг) ─────────────────────────────────────────────────────

    private void openNegotiations(Player player) {
        PaginatedGui gui = paginated("🕯 Мої угоди", () -> openMain(player));
        UUID me = player.getUniqueId();
        for (NegotiationView view : gatheringService.negotiationsOf(me)) {
            boolean myTurn = view.turnOf().equals(me);
            boolean iAmSeller = view.sellerId().equals(me);
            UUID other = iAmSeller ? view.buyerId() : view.sellerId();
            ItemStack display = button(Material.BELL,
                    ChatColor.YELLOW + (iAmSeller ? "Ви продаєте: " : "Ви купуєте: ")
                            + ChatColor.WHITE + namer.displayName(view.itemKey()) + " ×" + view.amount(),
                    ChatColor.GOLD + "Поточна ціна: " + view.currentPrice().money().format());
            List<String> lore = new ArrayList<>(List.of(
                    ChatColor.DARK_GRAY + "Інша сторона: " + gatheringService.aliasOf(other), ""));
            if (myTurn) {
                lore.add(ChatColor.GREEN + "▸ ЛКМ — прийняти ціну");
                lore.add(ChatColor.AQUA + "▸ ПКМ — зустрічна ціна");
            } else {
                lore.add(ChatColor.GRAY + "Хід іншої сторони...");
            }
            lore.add(ChatColor.RED + "▸ Shift+ПКМ — відмовитись");
            appendLore(display, lore);
            gui.addItem(new GuiItem(display, e -> {
                e.setCancelled(true);
                if (e.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                    gatheringService.withdraw(player, view.negotiationId());
                    runSynced(player, () -> openNegotiations(player));
                } else if (myTurn && e.getClick() == org.bukkit.event.inventory.ClickType.LEFT) {
                    gatheringService.accept(player, view.negotiationId());
                    runSynced(player, () -> openNegotiations(player));
                } else if (myTurn && e.getClick() == org.bukkit.event.inventory.ClickType.RIGHT) {
                    prompts.prompt(player, PRICE_HINT + ChatColor.GRAY + " — ваша зустрічна ціна",
                            withPrice(player, price ->
                                    gatheringService.counter(player, view.negotiationId(), price)));
                }
            }));
        }
        gui.open(player);
    }

    // ── Хелпери ──────────────────────────────────────────────────────────────

    private Consumer<String> withPrice(Player player, Consumer<PoundMoney> action) {
        return input -> {
            PoundMoney price = ChatPromptService.parsePrice(input);
            if (price == null || price.isZero()) {
                player.sendMessage(ChatColor.RED + "Невірна ціна. Формат: «2 15» або «7».");
                return;
            }
            action.accept(price);
        };
    }

    private int parseAmount(String input) {
        try {
            int amount = Integer.parseInt(input.trim());
            return (amount >= 1 && amount <= 64) ? amount : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private PaginatedGui paginated(String title, Runnable back) {
        PaginatedGui gui = Gui.paginated()
                .title(Component.text(title).color(NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .rows(6)
                .pageSize(36)
                .disableAllInteractions()
                .create();
        gui.setItem(6, 1, new GuiItem(button(Material.BARRIER, ChatColor.GRAY + "◄ Назад"),
                e -> Bukkit.getScheduler().runTask(plugin, back)));
        gui.setItem(6, 3, new GuiItem(button(Material.ARROW, ChatColor.GRAY + "◄ Попередня"),
                e -> gui.previous()));
        gui.setItem(6, 7, new GuiItem(button(Material.ARROW, ChatColor.GRAY + "Наступна ►"),
                e -> gui.next()));
        return gui;
    }

    private void runSynced(Player player, Runnable action) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.1f);
        Bukkit.getScheduler().runTask(plugin, action);
    }

    private ItemStack button(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(ChatColor.GRAY + line);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void appendLore(ItemStack item, List<String> lines) {
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.addAll(lines);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }
}
