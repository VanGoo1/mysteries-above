package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import me.vangoo.application.services.ChurchDuelService;
import me.vangoo.application.services.ChurchService;
import me.vangoo.application.services.ChurchService.JoinResult;
import me.vangoo.application.services.ChurchService.OrderOffer;
import me.vangoo.application.services.ChurchService.OrderQuote;
import me.vangoo.application.services.MarketItemNamer;
import me.vangoo.domain.market.PoundMoney;
import me.vangoo.domain.organizations.ChurchRank;
import me.vangoo.domain.organizations.ChurchTask;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.Membership;
import me.vangoo.domain.organizations.PotionOrder;
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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Меню церкви: привітання не-члена / головне меню / завдання / замовлення зілля /
 * пожертви / мій ранг. Структура за зразком {@link MarketMenu}.
 */
public class ChurchMenu {

    private static final String PREFIX = ChatColor.GOLD + "[Церква] " + ChatColor.RESET;

    private final Plugin plugin;
    private final ChurchService churchService;
    private final MarketItemNamer namer;
    private final ConfirmationMenu confirm;
    private final MoneyPicker moneyPicker;
    private ChurchDuelService duelService;

    public ChurchMenu(Plugin plugin, ChurchService churchService, MarketItemNamer namer, ConfirmationMenu confirm) {
        this.plugin = plugin;
        this.churchService = churchService;
        this.namer = namer;
        this.confirm = confirm;
        this.moneyPicker = new MoneyPicker(plugin);
    }

    public void setDuelService(ChurchDuelService duelService) {
        this.duelService = duelService;
    }

    // ── Роутер ───────────────────────────────────────────────────────────────

    /** Не член → привітання; член цієї церкви → головне меню; член іншої → лор-відмова у чат. */
    public void openFor(Player player, String institutionId) {
        Optional<Membership> membership = churchService.membershipOf(player.getUniqueId());
        if (membership.isEmpty()) {
            openGreeting(player, institutionId);
            return;
        }
        if (membership.get().institutionId().equals(institutionId)) {
            openMain(player, institutionId);
            return;
        }
        Institution other = churchService.registry().byId(institutionId).orElse(null);
        if (other != null) {
            player.sendMessage(PREFIX + ChatColor.GRAY + other.lore());
        }
        player.sendMessage(PREFIX + ChatColor.RED + "Ви вже служите іншій церкві.");
    }

    // ── 1. Привітання не-члена ──────────────────────────────────────────────

    private void openGreeting(Player player, String institutionId) {
        Institution church = churchService.registry().byId(institutionId).orElse(null);
        if (church == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ця церква недоступна.");
            return;
        }
        Gui gui = Gui.gui()
                .title(Component.text("⛪ " + church.displayName()).color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD))
                .rows(3)
                .disableAllInteractions()
                .create();
        gui.setItem(2, 4, new GuiItem(button(Material.PAPER, ChatColor.GOLD + church.displayName(),
                church.lore()), e -> e.setCancelled(true)));
        gui.setItem(2, 6, new GuiItem(button(Material.EMERALD, ChatColor.GREEN + "[Вступити]",
                        "Приєднатися до церкви"),
                e -> runSynced(player, () -> attemptJoin(player, institutionId))));
        gui.open(player);
    }

    private void attemptJoin(Player player, String institutionId) {
        JoinResult result = churchService.join(player, institutionId);
        switch (result) {
            case OK -> {
                player.sendMessage(PREFIX + ChatColor.GREEN + "Вас прийнято вірянином цієї церкви.");
                openMain(player, institutionId);
            }
            case WRONG_PATHWAY -> player.sendMessage(PREFIX + ChatColor.RED + "Ваш шлях чужий цій церкві.");
            case COOLDOWN -> player.sendMessage(PREFIX + ChatColor.RED
                    + "Ви нещодавно покинули іншу церкву — поверніться пізніше.");
            case ALREADY_MEMBER -> player.sendMessage(PREFIX + ChatColor.RED + "Ви вже служите іншій церкві.");
            case UNKNOWN_CHURCH -> player.sendMessage(PREFIX + ChatColor.RED + "Ця церква недоступна.");
        }
    }

    // ── 2. Головне меню ──────────────────────────────────────────────────────

    private void openMain(Player player, String institutionId) {
        Gui gui = Gui.gui()
                .title(Component.text("⛪ Церква").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .rows(3)
                .disableAllInteractions()
                .create();
        gui.setItem(2, 2, new GuiItem(button(Material.BOOK, ChatColor.AQUA + "Завдання",
                        "Полювання та доставки"),
                e -> runSynced(player, () -> openTasks(player, institutionId))));
        gui.setItem(2, 4, new GuiItem(button(Material.BREWING_STAND, ChatColor.LIGHT_PURPLE + "Замовлення зілля",
                        "Замовити зілля зі сховища церкви"),
                e -> runSynced(player, () -> openOrder(player, institutionId))));
        gui.setItem(2, 6, new GuiItem(button(Material.GOLD_INGOT, ChatColor.GOLD + "Пожертви",
                        "Пожертвувати предмет або монети"),
                e -> runSynced(player, () -> openDonations(player, institutionId))));
        gui.setItem(2, 8, new GuiItem(rankTile(player), e -> e.setCancelled(true)));
        gui.setItem(3, 5, new GuiItem(button(Material.BARRIER, ChatColor.RED + "Покинути церкву"),
                e -> runSynced(player, () -> confirmLeave(player))));
        gui.open(player);
    }

    private void confirmLeave(Player player) {
        ItemStack give = button(Material.PAPER, ChatColor.RED + "Ваш вклад згорить");
        ItemStack get = button(Material.PAPER, ChatColor.GREEN + "Свобода");
        confirm.open(player, give, get, "⛪ Покинути церкву", () -> {
            if (churchService.leave(player)) {
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Ви покинули церкву.");
            }
        });
    }

    /** Плитка рангу з усією інформацією в lore (некликабельна, все видно на наведенні). */
    private ItemStack rankTile(Player player) {
        Membership membership = churchService.membershipOf(player.getUniqueId()).orElse(null);
        if (membership == null) {
            return button(Material.NAME_TAG, ChatColor.YELLOW + "Мій ранг", "Ви не член церкви");
        }
        int[] thresholds = churchService.rankThresholds();
        ChurchRank rank = membership.rank(thresholds);
        ChurchRank[] ranks = ChurchRank.values();
        int nextIndex = rank.ordinal() + 1;
        String nextLine;
        if (nextIndex < ranks.length) {
            int needed = Math.max(0, thresholds[nextIndex] - membership.lifetimeContribution());
            nextLine = "До " + ranks[nextIndex].displayName() + ": ще " + needed + " очок вкладу";
        } else {
            nextLine = "Це найвищий ранг.";
        }
        return button(Material.NAME_TAG, ChatColor.YELLOW + "Ранг: " + rank.displayName(),
                "Вклад за весь час: " + membership.lifetimeContribution() + " очок",
                "Баланс: " + membership.balance() + " очок",
                nextLine);
    }

    // ── 3. Завдання ──────────────────────────────────────────────────────────

    private void openTasks(Player player, String institutionId) {
        churchService.ensureFreshTasks(player);
        List<ChurchTask> tasks = churchService.tasksOf(player);
        PaginatedGui gui = paginated("⛪ Завдання", () -> openMain(player, institutionId));
        for (int i = 0; i < tasks.size(); i++) {
            ChurchTask task = tasks.get(i);
            int index = i;
            boolean hunt = task.type() == ChurchTask.Type.HUNT;
            Material icon = hunt ? Material.IRON_SWORD : Material.CHEST;
            String targetLabel = hunt ? humanize(task.targetName()) : namer.displayName(task.targetKey());
            ItemStack display = button(icon,
                    (hunt ? ChatColor.RED + "Полювання: " : ChatColor.AQUA + "Доставка: ")
                            + ChatColor.WHITE + targetLabel,
                    ChatColor.GRAY + "Прогрес: " + task.progress() + "/" + task.required(),
                    ChatColor.GOLD + "Нагорода: " + task.rewardPoints() + " очок");
            if (hunt) {
                gui.addItem(new GuiItem(display, e -> e.setCancelled(true)));
            } else {
                appendLore(display, List.of("", ChatColor.GREEN + "▸ Клацніть, щоб здати з інвентаря"));
                gui.addItem(new GuiItem(display, e -> {
                    e.setCancelled(true);
                    int delivered = churchService.deliverTask(player, index);
                    if (delivered > 0) {
                        player.sendMessage(PREFIX + ChatColor.GREEN + "Здано " + delivered + " од.");
                    } else {
                        player.sendMessage(PREFIX + ChatColor.RED + "Нічого здати.");
                    }
                    runSynced(player, () -> openTasks(player, institutionId));
                }));
            }
        }

        if (churchService.canStartTrial(player)) {
            gui.setItem(6, 9, new GuiItem(button(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "[Випробування шляху]",
                            "Дуель зі створінням 9 послідовності.",
                            ChatColor.RED + "Смертельно небезпечно — добре підготуйтесь!",
                            "Перемога відкриє вибір шляху домену."),
                    e -> runSynced(player, () -> confirmTrial(player, institutionId))));
        } else if (churchService.hasPassedTrial(player.getUniqueId())) {
            gui.setItem(6, 9, new GuiItem(button(Material.NETHER_STAR, ChatColor.GREEN + "[Обрати шлях]",
                            "Ви здолали випробування — оберіть свій шлях"),
                    e -> runSynced(player, () -> openTrialPathwayChoice(player, institutionId))));
        }
        gui.open(player);
    }

    private void confirmTrial(Player player, String institutionId) {
        if (duelService == null) {
            return;
        }
        ItemStack give = button(Material.IRON_SWORD, ChatColor.RED + "Ви ризикуєте життям");
        ItemStack get = button(Material.NETHER_STAR, ChatColor.GREEN + "Право обрати шлях домену");
        confirm.open(player, give, get, "⛪ Випробування шляху",
                () -> duelService.startTrial(player, institutionId));
    }

    /** Пікер шляху домену після перемоги в дуелі. */
    public void openTrialPathwayChoice(Player player, String institutionId) {
        List<String> choices = churchService.initiationPathwayChoices(player);
        if (choices.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Церква не пропонує шляхів для вибору.");
            return;
        }
        openPathwayPicker(player, "⛪ Оберіть свій шлях", choices,
                () -> openMain(player, institutionId),
                pathwayName -> {
                    if (!churchService.completeTrialInitiation(player, pathwayName)) {
                        player.sendMessage(PREFIX + ChatColor.RED + "Не вдалося завершити ініціацію.");
                    }
                    openMain(player, institutionId);
                });
    }

    // ── 4. Замовлення зілля ──────────────────────────────────────────────────

    private void openOrder(Player player, String institutionId) {
        Optional<PotionOrder> activeOrder = churchService.orderOf(player.getUniqueId());
        if (activeOrder.isPresent()) {
            openOrderStatus(player, institutionId, activeOrder.get());
            return;
        }
        if (churchService.pathwayNameOf(player) != null) {
            showOrderQuote(player, institutionId);
        } else {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Спершу оберіть шлях через Випробування шляху.");
            runSynced(player, () -> openMain(player, institutionId));
        }
    }

    private void openOrderStatus(Player player, String institutionId, PotionOrder order) {
        Gui gui = Gui.gui()
                .title(Component.text("⛪ Замовлення зілля").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .rows(3)
                .disableAllInteractions()
                .create();
        boolean ready = order.isReady(System.currentTimeMillis());
        if (ready) {
            gui.setItem(2, 5, new GuiItem(button(Material.POTION, ChatColor.GREEN + "[Забрати]",
                            "Зілля " + order.pathwayName() + " Посл. " + order.sequence() + " готове"),
                    e -> runSynced(player, () -> {
                        churchService.claimOrder(player);
                        openMain(player, institutionId);
                    })));
        } else {
            long remainingMillis = order.readyAtEpochMillis() - System.currentTimeMillis();
            long hours = Math.max(0, remainingMillis / 3_600_000L);
            long minutes = Math.max(0, (remainingMillis % 3_600_000L) / 60_000L);
            gui.setItem(2, 5, new GuiItem(button(Material.BREWING_STAND,
                    ChatColor.YELLOW + "Вариться, лишилось " + hours + " год " + minutes + " хв"),
                    e -> e.setCancelled(true)));
        }
        gui.setItem(3, 5, new GuiItem(button(Material.BARRIER, ChatColor.GRAY + "◄ Назад"),
                e -> runSynced(player, () -> openMain(player, institutionId))));
        gui.open(player);
    }

    private void showOrderQuote(Player player, String institutionId) {
        OrderOffer offer = churchService.quoteOrder(player);
        if (!offer.isAvailable()) {
            player.sendMessage(PREFIX + ChatColor.RED + denialText(player, offer));
            runSynced(player, () -> openMain(player, institutionId));
            return;
        }
        OrderQuote quote = offer.quote();
        if (!quote.missing().isEmpty()) {
            String missingText = quote.missing().keySet().stream()
                    .map(namer::displayName)
                    .collect(Collectors.joining(", "));
            player.sendMessage(PREFIX + ChatColor.RED + "Сховищу церкви бракує: " + missingText);
            runSynced(player, () -> openMain(player, institutionId));
            return;
        }
        ItemStack give = button(Material.PAPER, ChatColor.GOLD + "" + quote.price() + " очок вкладу");
        ItemStack get = button(Material.POTION, ChatColor.LIGHT_PURPLE
                + "Зілля " + quote.pathwayName() + " Посл. " + quote.sequence());
        confirm.open(player, give, get, "⛪ Замовлення зілля", () -> {
            if (churchService.placeOrder(player)) {
                player.sendMessage(PREFIX + ChatColor.GREEN + "Замовлення прийнято — зілля вариться.");
            } else {
                player.sendMessage(PREFIX + ChatColor.RED + "Не вдалося оформити замовлення.");
            }
        });
    }

    /** Пояснення відмови для гравця: числа беремо з членства й профілю, а не з тексту причини. */
    private String denialText(Player player, OrderOffer offer) {
        if (offer.denial() == null) {
            return "Замовлення зілля наразі недоступне.";
        }
        return switch (offer.denial()) {
            case RANK_TOO_LOW -> "Ваш ранг не дозволяє замовити зілля цієї послідовності.";
            case MASTERY_INCOMPLETE -> {
                double mastery = churchService.masteryPercentOf(player.getUniqueId());
                yield mastery < 0
                        ? "Спершу доведіть засвоєння до 100%."
                        : String.format("Засвоєння неповне: %.2f%% зі 100%% — зілля вам не зварять.", mastery);
            }
            case ALREADY_ORDERED -> "Ви вже замовляли це зілля в цій церкві.";
            case NOT_ENOUGH_POINTS -> {
                int balance = churchService.membershipOf(player.getUniqueId())
                        .map(Membership::balance).orElse(0);
                int price = offer.quote() == null ? 0 : offer.quote().price();
                yield "Недостатньо очок вкладу: " + balance + "/" + price + ".";
            }
            case UNAVAILABLE -> "Замовлення зілля наразі недоступне.";
        };
    }

    // ── 5. Пожертви ──────────────────────────────────────────────────────────

    private void openDonations(Player player, String institutionId) {
        Gui gui = Gui.gui()
                .title(Component.text("⛪ Пожертви").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .rows(3)
                .disableAllInteractions()
                .create();
        gui.setItem(2, 4, new GuiItem(button(Material.CHEST, ChatColor.GREEN + "Пожертвувати предмет у руці",
                        "Інгредієнт, книга рецептів чи Характеристика"),
                e -> runSynced(player, () -> {
                    int points = churchService.donateFromHand(player);
                    if (points > 0) {
                        player.sendMessage(PREFIX + ChatColor.GREEN + "+" + points + " очок вкладу");
                    } else {
                        player.sendMessage(PREFIX + ChatColor.RED + "Церква не прийме це.");
                    }
                    openDonations(player, institutionId);
                })));
        gui.setItem(2, 6, new GuiItem(button(Material.GOLD_INGOT, ChatColor.GOLD + "Пожертвувати монети",
                        "Фунти й коппети"),
                e -> runSynced(player, () -> moneyPicker.open(player, "Пожертва монет", false,
                        (PoundMoney money) -> {
                            int points = churchService.donateCoins(player, money);
                            if (points > 0) {
                                player.sendMessage(PREFIX + ChatColor.GREEN + "+" + points + " очок вкладу");
                            } else {
                                player.sendMessage(PREFIX + ChatColor.RED + "Не вдалося прийняти пожертву.");
                            }
                            openMain(player, institutionId);
                        },
                        () -> openDonations(player, institutionId)))));
        gui.setItem(3, 5, new GuiItem(button(Material.BARRIER, ChatColor.GRAY + "◄ Назад"),
                e -> runSynced(player, () -> openMain(player, institutionId))));
        gui.open(player);
    }

    // ── Хелпери ──────────────────────────────────────────────────────────────

    /** Пікер шляху (для ініціації або замовлення без шляху) — по item на шлях. */
    private void openPathwayPicker(Player player, String title, List<String> choices,
                                   Runnable onCancel, Consumer<String> onChoose) {
        PaginatedGui gui = paginated(title, onCancel);
        for (String pathwayName : choices) {
            ItemStack display = button(Material.BOOK, ChatColor.AQUA + pathwayName,
                    ChatColor.GREEN + "▸ Клацніть, щоб обрати");
            gui.addItem(new GuiItem(display, e -> {
                e.setCancelled(true);
                runSynced(player, () -> onChoose.accept(pathwayName));
            }));
        }
        gui.open(player);
    }

    private PaginatedGui paginated(String title, Runnable back) {
        PaginatedGui gui = Gui.paginated()
                .title(Component.text(title).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
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

    private static String humanize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String spaced = raw.replace('_', ' ').trim();
        if (spaced.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
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
