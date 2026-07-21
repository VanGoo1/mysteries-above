package me.vangoo.presentation.commands;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.SecretOrderService;
import me.vangoo.application.services.SecretOrderService.JoinResult;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.organizations.Favor;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.Invitation;
import me.vangoo.domain.organizations.OrderMembership;
import me.vangoo.domain.organizations.OrderRank;
import me.vangoo.domain.organizations.OrderTask;
import me.vangoo.domain.organizations.TaskWeight;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /order — гравецька команда таємної організації (патерн {@link ChurchCommand}).
 * Вступ за шифрованим посланням лишається в {@code OrderMenu} (пікер орденів); тут —
 * прийняття вчинкового запрошення, старт злому, вихід і зведена довідка.
 *
 * <p>Конструктор бере {@link BeyonderService} на додачу до брифу задачі 15 (літерально —
 * {@code OrderCommand(SecretOrderService)}): {@code /order info} мусить показувати ранг
 * ({@link OrderRank#of(int)} від послідовності гравця), а {@code SecretOrderService} не
 * віддає цей розрахунок публічно (той самий приватний {@code rankOf} усередині сервісу).
 * Той самий випадок, що {@code priestService} у {@code SecretOrderService} — Task 16 підганяє
 * виклик конструктора під цей клас. {@code SecretOrderService.registry()} (новий геттер,
 * дзеркалить {@code ChurchService.registry()}) резолвить displayName запрошених орденів
 * у {@code /order invites} — без нього гравець бачив би лише сирий {@code institutionId}.</p>
 */
public class OrderCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Куратор] " + ChatColor.RESET;

    private final SecretOrderService secretOrderService;
    private final BeyonderService beyonderService;

    public OrderCommand(SecretOrderService secretOrderService, BeyonderService beyonderService) {
        this.secretOrderService = secretOrderService;
        this.beyonderService = beyonderService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Ця команда доступна лише гравцям.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(PREFIX + ChatColor.GRAY
                    + "Використання: /order <invites|accept|raid|leave|info>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "invites" -> handleInvites(player);
            case "accept" -> handleAccept(player, args);
            case "raid" -> handleRaid(player);
            case "leave" -> handleLeave(player, args);
            case "info" -> handleInfo(player);
            default -> player.sendMessage(PREFIX + ChatColor.GRAY
                    + "Використання: /order <invites|accept|raid|leave|info>");
        }
        return true;
    }

    private void handleInvites(Player player) {
        List<Invitation> invites = secretOrderService.invitationsOf(player.getUniqueId());
        if (invites.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Запрошень немає.");
            return;
        }
        player.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Ваші запрошення:");
        for (Invitation invite : invites) {
            String name = secretOrderService.registry().byId(invite.institutionId())
                    .map(Institution::displayName).orElse(invite.institutionId());
            player.sendMessage(ChatColor.GRAY + "  - " + ChatColor.AQUA + name
                    + ChatColor.GRAY + " (" + invite.institutionId() + "): " + invite.reason());
        }
        player.sendMessage(PREFIX + ChatColor.GRAY + "Прийняти: /order accept <id>");
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Використання: /order accept <institutionId>");
            return;
        }
        List<Invitation> invites = secretOrderService.invitationsOf(player.getUniqueId());
        Optional<Invitation> matched = invites.stream()
                .filter(inv -> inv.institutionId().equalsIgnoreCase(args[1]))
                .findFirst();
        if (matched.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "У вас немає такого запрошення. Перевірте /order invites.");
            return;
        }
        JoinResult result = secretOrderService.join(player, matched.get().institutionId());
        switch (result) {
            case OK -> player.sendMessage(PREFIX + ChatColor.GREEN + "Вас прийнято.");
            case NO_PATHWAY -> player.sendMessage(PREFIX + ChatColor.RED
                    + "Спершу оберіть свій шлях — орден приймає лише тих, хто вже на ньому.");
            case WRONG_PATHWAY -> player.sendMessage(PREFIX + ChatColor.RED + "Ваш шлях чужий цьому ордену.");
            case COOLDOWN -> player.sendMessage(PREFIX + ChatColor.RED
                    + "Ви нещодавно покинули інший орден — поверніться пізніше.");
            case ALREADY_MEMBER -> player.sendMessage(PREFIX + ChatColor.RED
                    + "Ви вже служите іншому ордену.");
            case ABANDONED -> player.sendMessage(PREFIX + ChatColor.RED
                    + "Ви колись зреклися цього ордену — тут вас більше не приймуть.");
            case UNKNOWN_ORDER -> player.sendMessage(PREFIX + ChatColor.RED + "Цей орден недоступний.");
        }
    }

    /**
     * Старт злому сховища храму. Ціль і всі гейти (ніч, зона, кулдаун храму) перевіряє
     * {@code startRaid} — сюди гравець потрапляє або з кнопки автопропозиції, або набравши
     * команду сам, коли вирішив, що момент слушний.
     */
    private void handleRaid(Player player) {
        if (secretOrderService.membershipOf(player.getUniqueId()).isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ви не служите жодному ордену.");
            return;
        }
        if (!secretOrderService.startRaid(player)) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Зараз злом неможливий.");
        }
    }

    /** Вихід необоротний, тож команда двокрокова: перший виклик лише попереджає. */
    private void handleLeave(Player player, String[] args) {
        Optional<Institution> orderOpt = secretOrderService.orderOf(player.getUniqueId());
        if (orderOpt.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ви не служите жодному ордену.");
            return;
        }
        String orderName = orderOpt.get().displayName();
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            player.sendMessage(PREFIX + ChatColor.RED + "" + ChatColor.BOLD + "Покинути "
                    + orderName + " — двері зачиняться назавжди.");
            player.sendMessage(PREFIX + ChatColor.RED + "Завдання й фавори згорять, а назад вас "
                    + "більше ніколи не приймуть.");
            player.sendMessage(PREFIX + ChatColor.GRAY + "Певні? Тоді: /order leave confirm");
            return;
        }
        if (secretOrderService.leave(player)) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Ви покинули " + orderName
                    + ". Ці двері зачинені для вас назавжди.");
        }
    }

    private void handleInfo(Player player) {
        Optional<OrderMembership> membershipOpt = secretOrderService.membershipOf(player.getUniqueId());
        if (membershipOpt.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ви не служите жодному ордену.");
            return;
        }
        OrderMembership membership = membershipOpt.get();
        UUID id = player.getUniqueId();
        String orderName = secretOrderService.orderOf(id).map(Institution::displayName)
                .orElse(membership.institutionId());
        Beyonder beyonder = beyonderService.getBeyonder(id);
        int seq = beyonder == null ? 9 : beyonder.getSequenceLevel();
        OrderRank rank = OrderRank.of(seq);

        player.sendMessage(PREFIX + ChatColor.GOLD + orderName);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Куратор: " + membership.curatorName());
        player.sendMessage(PREFIX + ChatColor.AQUA + "Ранг: " + rank.displayName());

        List<Favor> favors = secretOrderService.favorsOf(id);
        if (favors.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Фаворів наразі немає.");
        } else {
            Map<TaskWeight, Long> byWeight = new EnumMap<>(TaskWeight.class);
            for (Favor favor : favors) {
                byWeight.merge(favor.weight(), 1L, Long::sum);
            }
            player.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Фаворів: " + favors.size()
                    + " (" + summarizeFavors(byWeight) + ")");
        }

        List<OrderTask> tasks = secretOrderService.tasksOf(player);
        if (tasks.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Активних завдань немає.");
        } else {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Активні завдання:");
            for (OrderTask task : tasks) {
                player.sendMessage(ChatColor.GRAY + "  - " + taskKindName(task.type()) + ": "
                        + task.targetName() + " (" + task.progress() + "/" + task.required() + ")");
            }
        }
    }

    private static String summarizeFavors(Map<TaskWeight, Long> byWeight) {
        List<String> parts = new ArrayList<>();
        for (TaskWeight weight : TaskWeight.values()) {
            Long count = byWeight.get(weight);
            if (count != null && count > 0) {
                parts.add(weightName(weight) + " x" + count);
            }
        }
        return String.join(", ", parts);
    }

    private static String weightName(TaskWeight weight) {
        return switch (weight) {
            case LIGHT -> "легкий";
            case STANDARD -> "стандартний";
            case MAJOR -> "важливий";
        };
    }

    private static String taskKindName(OrderTask.Type type) {
        return switch (type) {
            case DELIVER -> "Доставка";
            case HUNT -> "Полювання";
            case RAID -> "Злом сховища";
            case ASSASSINATE -> "Замах";
            case RECON -> "Розвідка";
            case SABOTAGE -> "Саботаж";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            return List.of("invites", "accept", "raid", "leave", "info").stream()
                    .filter(o -> o.startsWith(lower))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("leave")) {
            return "confirm".startsWith(args[1].toLowerCase()) ? List.of("confirm") : List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accept") && sender instanceof Player player) {
            String lower = args[1].toLowerCase();
            return secretOrderService.invitationsOf(player.getUniqueId()).stream()
                    .map(Invitation::institutionId)
                    .filter(idStr -> idStr.toLowerCase().startsWith(lower))
                    .toList();
        }
        return List.of();
    }
}
