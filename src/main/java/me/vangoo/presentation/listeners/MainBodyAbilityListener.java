package me.vangoo.presentation.listeners;

import me.vangoo.application.services.AbilityExecutor;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.PathwayManager;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.pathways.fool.abilities.MarionettistControl;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Обробляє використання предметів-здібностей ОСНОВНОГО ТІЛА під час контролю маріонетки.
 * Такі предмети помічені NBT-міткою {@link MarionettistControl#MAIN_BODY_ABILITY_NBT};
 * їх виконання витрачає духовність основного тіла (творця), а не маріонетки.
 */
public class MainBodyAbilityListener implements Listener {

    private final BeyonderService beyonderService;
    private final AbilityExecutor abilityExecutor;
    private final PathwayManager pathwayManager;

    public MainBodyAbilityListener(BeyonderService beyonderService,
                                   AbilityExecutor abilityExecutor,
                                   PathwayManager pathwayManager) {
        this.beyonderService = beyonderService;
        this.abilityExecutor = abilityExecutor;
        this.pathwayManager = pathwayManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand  = player.getInventory().getItemInOffHand();

        ItemStack item = MarionettistControl.isMainBodyAbilityItem(mainHand) ? mainHand
                : MarionettistControl.isMainBodyAbilityItem(offHand) ? offHand : null;
        if (item == null) return;

        event.setCancelled(true);

        UUID casterId = player.getUniqueId();
        Beyonder beyonder = beyonderService.getBeyonder(casterId);
        if (beyonder == null) return;

        Ability a = pathwayManager.findAbilityInAllPathways(MarionettistControl.IDENTITY);
        if (!(a instanceof MarionettistControl mc) || !mc.isPossessing(casterId)) {
            sendActionBar(player, ChatColor.RED + "Ці здібності доступні лише в маріонетці.");
            return;
        }

        Optional<String> id = MarionettistControl.mainBodyAbilityId(item);
        if (id.isEmpty()) return;

        Ability ability = mc.getMainBodyAbilityByIdentity(casterId, id.get());
        if (ability == null) {
            sendActionBar(player, ChatColor.RED + "Здібність основного тіла недоступна.");
            return;
        }

        AbilityResult result = mc.useMainBodyAbility(
                casterId, ability, () -> abilityExecutor.execute(beyonder, ability));

        if (result != null && !result.isSuccess()) {
            sendActionBar(player, ChatColor.RED + (result.getMessage() != null
                    ? result.getMessage() : "Не вдалося використати здібність"));
        }
    }

    private void sendActionBar(Player player, String msg) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }
}
