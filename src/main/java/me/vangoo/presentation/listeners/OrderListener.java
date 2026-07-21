package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.RampageManager;
import me.vangoo.application.services.SecretOrderService;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureTier;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.organizations.Invitation;
import me.vangoo.infrastructure.citizens.ChurchPriestService;
import me.vangoo.infrastructure.items.OrderItems;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import me.vangoo.infrastructure.ui.OrderMenu;
import net.citizensnpcs.api.event.NPCDamageByEntityEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bukkit-глюй таємних організацій: шифроване послання в руці, sneak-клік і удар
 * по священику (шпигунство/замах), kill-прогрес завдань (полювання/апекс/охоронець замаху),
 * провал/успіх рейдера й нагадування про вчинкові запрошення. Патерн — {@link ChurchListener}.
 */
public class OrderListener implements Listener {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Куратор] " + ChatColor.RESET;

    private final SecretOrderService secretOrderService;
    private final OrderMenu orderMenu;
    private final OrderItems orderItems;
    private final MythicCreatureGateway mythicGateway;
    private final ChurchPriestService priests;
    private final BeyonderService beyonderService;
    private final RampageManager rampageManager;
    private final Map<String, CreatureDefinition> creatureRegistry;

    public OrderListener(SecretOrderService secretOrderService, OrderMenu orderMenu, OrderItems orderItems,
                         MythicCreatureGateway mythicGateway, ChurchPriestService priests,
                         BeyonderService beyonderService, RampageManager rampageManager,
                         Map<String, CreatureDefinition> creatureRegistry) {
        this.secretOrderService = secretOrderService;
        this.orderMenu = orderMenu;
        this.orderItems = orderItems;
        this.mythicGateway = mythicGateway;
        this.priests = priests;
        this.beyonderService = beyonderService;
        this.rampageManager = rampageManager;
        this.creatureRegistry = creatureRegistry;
    }

    /**
     * Шифроване послання: члену — підказка, іншим — пікер вступу. Подія скасовується
     * (не даємо ПКМ провалитись у блок/повітря під предметом). Талісмана тут більше нема —
     * головне меню ордену відкривається вкладкою в меню Містичних Здібностей, а злом
     * стартує {@code /order raid} (вручну або з кнопки автопропозиції).
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // подія стріляє двічі (обидві руки) — беремо лише головну
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (orderItems.isCipherMessage(hand)) {
            event.setCancelled(true);
            if (secretOrderService.membershipOf(player.getUniqueId()).isPresent()) {
                player.sendMessage(PREFIX + ChatColor.GRAY
                        + "Ви вже служите ордену — це послання вам більше нінащо.");
            } else {
                orderMenu.openJoinPicker(player);
            }
        }
    }

    /** Sneak-клік по священику — шпигунство подвійного агента (розвіддані/саботаж). */
    @EventHandler
    public void onPriestSneakClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        if (!player.isSneaking()) {
            return; // звичайний клік — священик церкви (ChurchListener)
        }
        priests.institutionOf(event.getNPC())
                .ifPresent(id -> secretOrderService.performSpyAction(player, id));
    }

    /** Удар по священику Player'ом — старт замаху. */
    @EventHandler
    public void onPriestDamage(NPCDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player player)) {
            return;
        }
        priests.institutionOf(event.getNPC())
                .ifPresent(id -> secretOrderService.startAssassination(player, id));
    }

    @EventHandler
    public void onCreatureDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            Optional<String> idOpt = mythicGateway.creatureId(event.getEntity());
            if (idOpt.isPresent()) {
                String id = idOpt.get();
                secretOrderService.onCreatureKilled(killer, id);
                CreatureDefinition def = creatureRegistry.get(id);
                if (def != null && def.tier() == CreatureTier.APEX) {
                    secretOrderService.onApexKilled(killer, def.pathway());
                }
            }
        }
        // Охоронець замаху — окремий трек від HUNT-задач; безпечний no-op, якщо не охоронець.
        secretOrderService.onGuardKilled(event.getEntity().getUniqueId(), killer);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        secretOrderService.onRaiderDied(victim, killer);
        if (killer == null || killer.equals(victim)) {
            return;
        }
        Beyonder victimBeyonder = beyonderService.getBeyonder(victim.getUniqueId());
        if (victimBeyonder != null && victimBeyonder.getPathway() != null) {
            secretOrderService.onBeyonderKilled(killer);
        }
        if (rampageManager.isInRampage(victim.getUniqueId())) {
            secretOrderService.onRampagerStopped(killer);
        }
    }

    /** Нагадування про вчинкові запрошення при вході — клікабельне «/order invites». */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<Invitation> pending = secretOrderService.invitationsOf(player.getUniqueId());
        if (pending.isEmpty()) {
            return;
        }
        TextComponent message = new TextComponent(PREFIX + ChatColor.LIGHT_PURPLE
                + "На вас чекає " + pending.size() + " запрошення від таємних орденів. ");
        TextComponent button = new TextComponent(ChatColor.GREEN + "" + ChatColor.BOLD + "[/order invites]");
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/order invites"));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GRAY + "Переглянути запрошення").create()));
        message.addExtra(button);
        player.spigot().sendMessage(message);
    }
}
