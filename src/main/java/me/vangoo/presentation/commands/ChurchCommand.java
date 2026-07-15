package me.vangoo.presentation.commands;

import me.vangoo.application.services.ChurchService;
import me.vangoo.domain.organizations.ChurchRank;
import me.vangoo.domain.organizations.ChurchTask;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.Membership;
import me.vangoo.domain.organizations.PotionOrder;
import me.vangoo.infrastructure.organizations.ChurchSiteService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /church bind|unbind — адмінам (перевірка права в коді: команда відкрита для всіх);
 * /church leave|info — гравцям.
 */
public class ChurchCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.GOLD + "[Церква] " + ChatColor.RESET;

    private final ChurchService churchService;
    private final ChurchSiteService siteService;

    public ChurchCommand(ChurchService churchService, ChurchSiteService siteService) {
        this.churchService = churchService;
        this.siteService = siteService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Використання: /church <bind|unbind|leave|info>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "bind" -> handleBind(sender, args);
            case "unbind" -> handleUnbind(sender);
            case "leave" -> handleLeave(sender, args);
            case "info" -> handleInfo(sender);
            default -> sender.sendMessage(PREFIX + ChatColor.GRAY
                    + "Використання: /church <bind|unbind|leave|info>");
        }
        return true;
    }

    private void handleBind(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mysteriesabove.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Недостатньо прав.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Ця команда доступна лише гравцям.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Використання: /church bind <institutionId>");
            return;
        }
        String institutionId = args[1];
        Optional<Institution> institution = churchService.registry().byId(institutionId);
        if (institution.isEmpty() || institution.get().type() != me.vangoo.domain.organizations.InstitutionType.CHURCH) {
            player.sendMessage(PREFIX + ChatColor.RED + "Невідома церква: " + institutionId);
            return;
        }
        siteService.bind(institutionId, player.getLocation());
        player.sendMessage(PREFIX + ChatColor.GREEN + "Храм \"" + institution.get().displayName()
                + "\" прив'язано тут.");
    }

    private void handleUnbind(CommandSender sender) {
        if (!sender.hasPermission("mysteriesabove.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Недостатньо прав.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Ця команда доступна лише гравцям.");
            return;
        }
        if (siteService.unbindNearest(player.getLocation())) {
            player.sendMessage(PREFIX + ChatColor.GREEN + "Храм відв'язано.");
        } else {
            player.sendMessage(PREFIX + ChatColor.RED + "Поруч немає храму (радіус 16).");
        }
    }

    /** Вихід необоротний, тож команда двокрокова: перший виклик лише попереджає. */
    private void handleLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Ця команда доступна лише гравцям.");
            return;
        }
        Optional<Institution> churchOpt = churchService.churchOf(player.getUniqueId());
        if (churchOpt.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ви не член церкви.");
            return;
        }
        String churchName = churchOpt.get().displayName();
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            player.sendMessage(PREFIX + ChatColor.RED + "" + ChatColor.BOLD + "Покинути "
                    + churchName + " — це НАЗАВЖДИ.");
            player.sendMessage(PREFIX + ChatColor.RED + "Вклад і ранг згорять, історія замовлень зникне,");
            player.sendMessage(PREFIX + ChatColor.RED + "і назад вас більше ніколи не приймуть.");
            player.sendMessage(PREFIX + ChatColor.GRAY + "Певні? Тоді: /church leave confirm");
            return;
        }
        if (churchService.leave(player)) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Ви покинули " + churchName
                    + ". Ці двері зачинені для вас назавжди.");
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Ця команда доступна лише гравцям.");
            return;
        }
        Optional<Membership> membershipOpt = churchService.membershipOf(player.getUniqueId());
        if (membershipOpt.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ви не член церкви.");
            return;
        }
        Membership membership = membershipOpt.get();
        String churchName = churchService.registry().byId(membership.institutionId())
                .map(Institution::displayName).orElse(membership.institutionId());
        ChurchRank rank = membership.rank(churchService.rankThresholds());

        player.sendMessage(PREFIX + ChatColor.GOLD + churchName);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Ранг: " + rank.displayName());
        player.sendMessage(PREFIX + ChatColor.AQUA + "Вклад за весь час: " + membership.lifetimeContribution()
                + " очок, баланс: " + membership.balance() + " очок");

        List<ChurchTask> tasks = churchService.tasksOf(player);
        if (tasks.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Активних завдань немає.");
        } else {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Активні завдання:");
            for (ChurchTask task : tasks) {
                String kind = task.type() == ChurchTask.Type.HUNT ? "Полювання" : "Доставка";
                player.sendMessage(ChatColor.GRAY + "  - " + kind + ": " + task.targetName()
                        + " (" + task.progress() + "/" + task.required() + ") +" + task.rewardPoints() + " очок");
            }
        }

        Optional<PotionOrder> order = churchService.orderOf(player.getUniqueId());
        if (order.isPresent()) {
            PotionOrder o = order.get();
            if (o.isReady(System.currentTimeMillis())) {
                player.sendMessage(PREFIX + ChatColor.GREEN + "Замовлене зілля готове — заберіть у священика.");
            } else {
                player.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Замовлення: " + o.pathwayName()
                        + " Посл. " + o.sequence() + " (вариться)");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("mysteriesabove.admin");
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("leave", "info"));
            if (admin) {
                options.add("bind");
                options.add("unbind");
            }
            String lower = args[0].toLowerCase();
            return options.stream().filter(o -> o.startsWith(lower)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("leave")) {
            return "confirm".startsWith(args[1].toLowerCase()) ? List.of("confirm") : List.of();
        }
        if (args.length == 2 && admin && args[0].equalsIgnoreCase("bind")) {
            String lower = args[1].toLowerCase();
            return churchService.registry().churches().stream()
                    .map(Institution::id)
                    .filter(id -> id.toLowerCase().startsWith(lower))
                    .toList();
        }
        return List.of();
    }
}
