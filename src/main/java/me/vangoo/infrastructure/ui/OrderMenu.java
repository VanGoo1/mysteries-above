package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import me.vangoo.application.services.ChurchService;
import me.vangoo.application.services.CreatureNamer;
import me.vangoo.application.services.MarketItemNamer;
import me.vangoo.application.services.SecretOrderService;
import me.vangoo.application.services.SecretOrderService.JoinResult;
import me.vangoo.domain.organizations.Favor;
import me.vangoo.domain.organizations.FavorOptions;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.OrderMembership;
import me.vangoo.domain.organizations.OrderTask;
import me.vangoo.domain.organizations.TaskWeight;
import me.vangoo.infrastructure.items.OrderItems;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Талісман-меню таємної організації: вступ за шифрованим посланням, головне меню,
 * завдання (доставка/полювання/храмові й шпигунські операції) та прохання до куратора
 * за фавори. Структура за зразком {@link ChurchMenu}, той самий triumph-gui.
 */
public class OrderMenu {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Куратор] " + ChatColor.RESET;

    private final Plugin plugin;
    private final SecretOrderService secretOrderService;
    private final MarketItemNamer namer;
    private final CreatureNamer creatureNamer;
    private final ConfirmationMenu confirm;
    private final OrderItems orderItems;

    public OrderMenu(Plugin plugin, SecretOrderService secretOrderService, MarketItemNamer namer,
                     CreatureNamer creatureNamer, ConfirmationMenu confirm, OrderItems orderItems) {
        this.plugin = plugin;
        this.secretOrderService = secretOrderService;
        this.namer = namer;
        this.creatureNamer = creatureNamer;
        this.confirm = confirm;
        this.orderItems = orderItems;
    }

    // ── 1. Вступ за шифрованим посланням ────────────────────────────────────

    /** Пагінований список орденів, доступних гравцю (за шляхом і без зречення). */
    public void openJoinPicker(Player player) {
        List<Institution> orders = secretOrderService.joinableOrders(player);
        PaginatedGui gui = paginated("🗝 Таємні ордени", () -> {});
        for (Institution order : orders) {
            ItemStack display = button(Material.KNOWLEDGE_BOOK, ChatColor.LIGHT_PURPLE + order.displayName(),
                    ChatColor.GRAY + order.lore(),
                    "",
                    ChatColor.GREEN + "▸ Клацніть, щоб оцінити пропозицію");
            gui.addItem(new GuiItem(display, e -> {
                e.setCancelled(true);
                runSynced(player, () -> confirmJoin(player, order));
            }));
        }
        gui.open(player);
    }

    /**
     * Вступ теж однобічний, як і в церкві: гравець має бачити ціну ДО кліку.
     * onConfirm списує ОДНЕ шифроване послання з головної руки, а потім кличе
     * {@link SecretOrderService#join}, тож і провал приєднання (кулдаун, чужий шлях
     * тощо), і успіх узгоджені з тим самим предметом, що привів гравця в це меню.
     */
    public void confirmJoin(Player player, Institution order) {
        ItemStack give = button(Material.PAPER, ChatColor.RED + "Шифроване послання",
                ChatColor.RED + "Служити двом орденам не можна",
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "Вихід можливий лише НАЗАВЖДИ:",
                ChatColor.RED + order.displayName() + " більше ніколи вас не прийме",
                ChatColor.GRAY + "а вступ в інший орден відкриється лише через якийсь час");
        ItemStack get = button(Material.ENDER_EYE, ChatColor.GREEN + "Талісман " + order.displayName(),
                ChatColor.GREEN + "Завдання: доставки, полювання, храмові й шпигунські операції",
                ChatColor.GREEN + "Фавори куратора за виконані завдання",
                ChatColor.GRAY + "Прохання до куратора: розвіддані, рецепти, Характеристики...");
        confirm.open(player, give, get, "🗝 Прийняти запрошення?", () -> attemptJoin(player, order));
    }

    private void attemptJoin(Player player, Institution order) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!orderItems.isCipherMessage(inHand)) {
            player.sendMessage(PREFIX + ChatColor.RED + "У вас нема шифрованого послання в руці.");
            return;
        }
        // Послання витрачається ЛИШЕ на успішному вступі — невдалий клік (уже член / кулдаун /
        // без шляху) не має з'їдати рідкісний предмет-луту.
        JoinResult result = secretOrderService.join(player, order.id());
        if (result == JoinResult.OK) {
            if (inHand.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                inHand.setAmount(inHand.getAmount() - 1);
            }
        }
        switch (result) {
            case OK -> {
                player.sendMessage(PREFIX + ChatColor.GREEN + "Вас прийнято. Талісман ордену вже у вас.");
                openMain(player);
            }
            case NO_PATHWAY -> player.sendMessage(PREFIX + ChatColor.RED
                    + "Спершу оберіть свій шлях — орден приймає лише тих, хто вже на ньому.");
            case WRONG_PATHWAY -> player.sendMessage(PREFIX + ChatColor.RED + "Ваш шлях чужий цьому ордену.");
            case COOLDOWN -> player.sendMessage(PREFIX + ChatColor.RED
                    + "Ви нещодавно покинули інший орден — поверніться пізніше.");
            case ALREADY_MEMBER -> player.sendMessage(PREFIX + ChatColor.RED + "Ви вже служите іншому ордену.");
            case ABANDONED -> player.sendMessage(PREFIX + ChatColor.RED
                    + "Ви колись зреклися цього ордену — тут вас більше не приймуть.");
            case UNKNOWN_ORDER -> player.sendMessage(PREFIX + ChatColor.RED + "Цей орден недоступний.");
        }
    }

    // ── 2. Головне меню ──────────────────────────────────────────────────────

    public void openMain(Player player) {
        Optional<Institution> orderOpt = secretOrderService.orderOf(player.getUniqueId());
        if (orderOpt.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ви не служите жодному ордену.");
            return;
        }
        Institution order = orderOpt.get();
        Gui gui = Gui.gui()
                .title(Component.text("🗝 " + order.displayName()).color(NamedTextColor.DARK_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .rows(3)
                .disableAllInteractions()
                .create();
        gui.setItem(2, 2, new GuiItem(button(Material.BOOK, ChatColor.AQUA + "Завдання",
                        "Доставки, полювання, операції"),
                e -> runSynced(player, () -> openTasks(player))));
        gui.setItem(2, 5, new GuiItem(button(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Куратор",
                        "Прохання за фавори"),
                e -> runSynced(player, () -> openCurator(player))));
        gui.setItem(2, 8, new GuiItem(myOrderTile(player, order), e -> e.setCancelled(true)));
        gui.open(player);
    }

    /** Плитка «Мій орден»: куратор, орден і зведення фаворів — усе видно на наведенні. */
    private ItemStack myOrderTile(Player player, Institution order) {
        OrderMembership membership = secretOrderService.membershipOf(player.getUniqueId()).orElse(null);
        if (membership == null) {
            return button(Material.NAME_TAG, ChatColor.YELLOW + "Мій орден", "Ви не член ордену");
        }
        List<Favor> favors = secretOrderService.favorsOf(player.getUniqueId());
        long light = favors.stream().filter(f -> f.weight() == TaskWeight.LIGHT).count();
        long standard = favors.stream().filter(f -> f.weight() == TaskWeight.STANDARD).count();
        long major = favors.stream().filter(f -> f.weight() == TaskWeight.MAJOR).count();
        return button(Material.NAME_TAG, ChatColor.YELLOW + "Мій орден: " + order.displayName(),
                "Куратор: " + membership.curatorName(),
                "Фаворів: " + favors.size() + " (легких: " + light + ", стандартних: " + standard
                        + ", важливих: " + major + ")");
    }

    // ── 3. Завдання ──────────────────────────────────────────────────────────

    public void openTasks(Player player) {
        secretOrderService.ensureFreshTasks(player);
        List<OrderTask> tasks = secretOrderService.tasksOf(player);
        PaginatedGui gui = paginated("🗝 Завдання", () -> openMain(player));
        for (int i = 0; i < tasks.size(); i++) {
            OrderTask task = tasks.get(i);
            int index = i;
            gui.addItem(taskTile(player, task, index));
        }

        secretOrderService.taskPoolStatus(player).ifPresent(status ->
                gui.setItem(6, 5, new GuiItem(quotaTile(status), e -> e.setCancelled(true))));

        gui.open(player);
    }

    private GuiItem taskTile(Player player, OrderTask task, int index) {
        return switch (task.type()) {
            case DELIVER -> {
                String targetLabel = namer.displayName(task.targetKey());
                ItemStack display = button(Material.CHEST, ChatColor.AQUA + "Доставка: " + ChatColor.WHITE
                                + targetLabel,
                        ChatColor.GRAY + "Прогрес: " + task.progress() + "/" + task.required(),
                        weightLine(task.weight()),
                        "",
                        ChatColor.GREEN + "▸ Клацніть, щоб здати з інвентаря");
                yield new GuiItem(display, e -> {
                    e.setCancelled(true);
                    int delivered = secretOrderService.deliverTask(player, index);
                    if (delivered > 0) {
                        player.sendMessage(PREFIX + ChatColor.GREEN + "Здано " + delivered + " од.");
                    } else {
                        player.sendMessage(PREFIX + ChatColor.RED + "Нічого здати.");
                    }
                    runSynced(player, () -> openTasks(player));
                });
            }
            case HUNT -> {
                String targetLabel = creatureNamer.displayName(task.targetKey());
                ItemStack display = button(Material.IRON_SWORD, ChatColor.RED + "Полювання: " + ChatColor.WHITE
                                + targetLabel,
                        ChatColor.GRAY + "Прогрес: " + task.progress() + "/" + task.required(),
                        weightLine(task.weight()));
                appendLore(display, huntSpawnLore(task.targetKey()));
                yield new GuiItem(display, e -> e.setCancelled(true));
            }
            case RAID -> {
                ItemStack display = button(Material.TRIPWIRE_HOOK, ChatColor.DARK_RED + "Злом сховища: "
                                + ChatColor.WHITE + task.targetName(),
                        ChatColor.GRAY + "Прокрадіться в межі храму глупої ночі й почніть злом",
                        ChatColor.GRAY + "Тримайтесь у зоні храму, поки триває канал",
                        weightLine(task.weight()),
                        "",
                        ChatColor.GREEN + "▸ Клацніть, щоб здати здобич зі схованки");
                yield new GuiItem(display, e -> {
                    e.setCancelled(true);
                    int deposited = secretOrderService.depositRaidLoot(player, index);
                    if (deposited > 0) {
                        player.sendMessage(PREFIX + ChatColor.GREEN + "Здано здобичі: " + deposited + " од.");
                    } else {
                        player.sendMessage(PREFIX + ChatColor.RED + "Нема здобичі для здачі за цим завданням.");
                    }
                    runSynced(player, () -> openTasks(player));
                });
            }
            case ASSASSINATE -> {
                ItemStack display = button(Material.IRON_SWORD, ChatColor.DARK_RED + "Замах: " + ChatColor.WHITE
                                + task.targetName(),
                        ChatColor.GRAY + "Знайдіть священика цього храму й нападіть на нього",
                        ChatColor.GRAY + "Здолайте охоронця, що з'явиться на захист",
                        weightLine(task.weight()));
                yield new GuiItem(display, e -> e.setCancelled(true));
            }
            case RECON -> {
                ItemStack display = button(Material.SPYGLASS, ChatColor.LIGHT_PURPLE + "Розвідка: "
                                + ChatColor.WHITE + task.targetName(),
                        ChatColor.GRAY + "Прокрадіться до священика ВЛАСНОЇ церкви й присядьте біля нього",
                        ChatColor.GRAY + "Ризик викриття — вас можуть вигнати з церкви",
                        weightLine(task.weight()));
                yield new GuiItem(display, e -> e.setCancelled(true));
            }
            case SABOTAGE -> {
                ItemStack display = button(Material.REDSTONE, ChatColor.LIGHT_PURPLE + "Саботаж: "
                                + ChatColor.WHITE + task.targetName(),
                        ChatColor.GRAY + "Прокрадіться до священика ВЛАСНОЇ церкви й присядьте біля нього",
                        ChatColor.GRAY + "Затримає чиєсь замовлення зілля в церкві",
                        ChatColor.GRAY + "Ризик викриття — вас можуть вигнати з церкви",
                        weightLine(task.weight()));
                yield new GuiItem(display, e -> e.setCancelled(true));
            }
        };
    }

    private String weightLine(TaskWeight weight) {
        String label = switch (weight) {
            case LIGHT -> "легка";
            case STANDARD -> "стандартна";
            case MAJOR -> "важлива";
        };
        return ChatColor.GOLD + "Нагорода: фавор куратора (" + label + ")";
    }

    /** Де шукати ціль полювання: біоми природного спавну + підказка про структури. */
    private List<String> huntSpawnLore(String creatureId) {
        List<String> lore = new ArrayList<>();
        List<String> biomes = creatureNamer.biomeNames(creatureId);
        lore.add("");
        if (biomes.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "У дикій природі не трапляється");
        } else {
            lore.add(ChatColor.YELLOW + "Біоми: " + ChatColor.GRAY + String.join(", ", biomes));
        }
        if (creatureNamer.spawnsNearStructures(creatureId)) {
            lore.add(ChatColor.YELLOW + "Частіше: " + ChatColor.GRAY + "біля структур");
        }
        return lore;
    }

    /** Годинник квоти наборів завдань — той самий формат, що й у церкви. */
    private ItemStack quotaTile(ChurchService.TaskPoolStatus status) {
        String countdown = "Скидання квоти через " + formatDuration(status.millisUntilReset());
        if (status.quotaExhausted()) {
            return button(Material.CLOCK,
                    ChatColor.RED + "Набори вичерпано: " + status.setsUsed() + "/" + status.setsPerDay(),
                    "Нові завдання — лише після скидання",
                    countdown);
        }
        return button(Material.CLOCK,
                ChatColor.GREEN + "Наборів сьогодні: " + status.setsUsed() + "/" + status.setsPerDay(),
                "Закрийте набір — новий видамо одразу",
                countdown,
                ChatColor.DARK_GRAY + "Незрушені завдання оновляться зі скиданням");
    }

    // ── 4. Куратор: прохання за фавори ──────────────────────────────────────

    public void openCurator(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("🗝 Куратор").color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD))
                .rows(4)
                .disableAllInteractions()
                .create();

        List<Favor> favors = secretOrderService.favorsOf(player.getUniqueId());
        gui.setItem(1, 5, new GuiItem(favorSummaryTile(favors), e -> e.setCancelled(true)));

        TaskWeight best = favors.stream().map(Favor::weight)
                .max(Comparator.comparingInt(Enum::ordinal)).orElse(null);
        List<FavorOptions.Option> options = best == null
                ? List.of() : secretOrderService.favorOptionsFor(player, best);

        int column = 1;
        for (FavorOptions.Option option : options) {
            gui.setItem(3, column, new GuiItem(favorButton(option), e -> {
                e.setCancelled(true);
                runSynced(player, () -> requestFavor(player, option));
            }));
            column++;
            if (column > 9) {
                break; // 7 опцій максимум у FavorOptions — рядок не переповниться, страховка
            }
        }
        gui.open(player);
    }

    private ItemStack favorSummaryTile(List<Favor> favors) {
        long light = favors.stream().filter(f -> f.weight() == TaskWeight.LIGHT).count();
        long standard = favors.stream().filter(f -> f.weight() == TaskWeight.STANDARD).count();
        long major = favors.stream().filter(f -> f.weight() == TaskWeight.MAJOR).count();
        return button(Material.WRITTEN_BOOK, ChatColor.YELLOW + "Ваші фавори: " + favors.size(),
                "Легких: " + light, "Стандартних: " + standard, "Важливих: " + major,
                "", ChatColor.DARK_GRAY + "Прохання списує НАЙДЕШЕВШИЙ фавор, що покриває вагу");
    }

    private ItemStack favorButton(FavorOptions.Option option) {
        return switch (option) {
            case HUNT_INFO -> button(Material.COMPASS, ChatColor.GREEN + "Апекс-здобич",
                    "Куратор шепне, де шукати апекс-ціль вашого домену");
            case VAULT_INTEL -> button(Material.MAP, ChatColor.GREEN + "Розвіддані сховища",
                    "Куратор поділиться свіжим знімком чужого сховища");
            case RECIPE_KNOWLEDGE -> button(Material.BOOK, ChatColor.AQUA + "Знання рецепту",
                    "Рецепт наступної послідовності вашого шляху");
            case INGREDIENTS -> button(Material.HOPPER, ChatColor.AQUA + "Інгредієнти",
                    "Інгредієнти зі схованки ордену під рецепт наступної послідовності");
            case CHARACTERISTIC -> button(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "Характеристика",
                    "Характеристика вашого шляху наступної послідовності зі схованки");
            case CLEAR_COOLDOWN -> button(Material.CLOCK, ChatColor.LIGHT_PURPLE + "Зняти кулдаун",
                    "Куратор владнає кулдаун повторного вступу до церкви");
            case FALSE_PAPERS -> button(Material.PAPER, ChatColor.LIGHT_PURPLE + "Фальшиві документи",
                    "Обхід разового бар'єру ініціації при наступному вступі до церкви");
        };
    }

    private void requestFavor(Player player, FavorOptions.Option option) {
        TaskWeight required = FavorOptions.requiredWeight(option);
        if (required == TaskWeight.MAJOR) {
            confirmMajorFavor(player, option);
            return;
        }
        boolean claimed = claim(player, option);
        if (!claimed) {
            player.sendMessage(PREFIX + ChatColor.RED + "Куратор не зміг виконати прохання.");
        }
        openCurator(player);
    }

    /** Важливий фавор — остання картка гравця в цьому ордені на певний час; ціна мусить бути видна ДО кліку. */
    private void confirmMajorFavor(Player player, FavorOptions.Option option) {
        ItemStack give = button(Material.WRITTEN_BOOK, ChatColor.RED + "Важливий фавор куратора");
        ItemStack get = favorButton(option).clone();
        confirm.open(player, give, get, "🗝 Звернутись до куратора?", () -> {
            boolean claimed = claim(player, option);
            if (!claimed) {
                player.sendMessage(PREFIX + ChatColor.RED + "Куратор не зміг виконати прохання.");
            }
            openCurator(player);
        });
    }

    private boolean claim(Player player, FavorOptions.Option option) {
        return switch (option) {
            case HUNT_INFO -> secretOrderService.claimHuntInfo(player);
            case VAULT_INTEL -> secretOrderService.claimVaultIntel(player);
            case RECIPE_KNOWLEDGE -> secretOrderService.claimRecipe(player);
            case INGREDIENTS -> secretOrderService.claimIngredients(player);
            case CHARACTERISTIC -> secretOrderService.claimCharacteristic(player);
            case CLEAR_COOLDOWN -> secretOrderService.claimClearCooldown(player);
            case FALSE_PAPERS -> secretOrderService.claimFalsePapers(player);
        };
    }

    // ── Хелпери ──────────────────────────────────────────────────────────────

    private PaginatedGui paginated(String title, Runnable back) {
        PaginatedGui gui = Gui.paginated()
                .title(Component.text(title).color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD))
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

    /** «X год Y хв» / «X хв» — спільний формат для всіх відліків ордену (скопійовано з ChurchMenu). */
    private String formatDuration(long millis) {
        long safe = Math.max(0, millis);
        long hours = safe / 3_600_000L;
        long minutes = (safe % 3_600_000L) / 60_000L;
        return hours > 0 ? hours + " год " + minutes + " хв" : minutes + " хв";
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
