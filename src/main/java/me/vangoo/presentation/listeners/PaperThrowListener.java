package me.vangoo.presentation.listeners;

import me.vangoo.application.services.AbilityContextFactory;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.CustomItemService;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.pathways.fool.abilities.PaperThrower;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Presentation Listener: пасивний кидок паперу шляху Блазня.
 *
 * <p>Коли Fool-Beyonder із родини «Паперовий різак» тримає <b>звичайний</b> папір
 * і робить ПКМ — кидається паперовий снаряд ({@link PaperThrower#throwPaper}).
 * «Звичайний» = {@link Material#PAPER}, що НЕ є ability-item, кастом-предметом
 * (інгредієнтом) чи іменованим службовим папером — інакше гравець випадково
 * «викинув» би інгредієнт або предмет здібності.
 */
public class PaperThrowListener implements Listener {

    private final AbilityContextFactory abilityContextFactory;
    private final BeyonderService beyonderService;
    private final CustomItemService customItemService;

    public PaperThrowListener(AbilityContextFactory abilityContextFactory,
                              BeyonderService beyonderService,
                              CustomItemService customItemService) {
        this.abilityContextFactory = abilityContextFactory;
        this.beyonderService = beyonderService;
        this.customItemService = customItemService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isPlainPaper(item)) return;

        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) return;

        PaperThrower thrower = findThrower(beyonder);
        if (thrower == null) return;

        IAbilityContext ctx = abilityContextFactory.createContext(player);
        if (thrower.throwPaper(ctx)) {
            event.setCancelled(true);
        }
    }

    private PaperThrower findThrower(Beyonder beyonder) {
        for (Ability ability : beyonder.getAbilities()) {
            if (ability instanceof PaperThrower pt) {
                return pt;
            }
        }
        return null;
    }

    /** «Чистий» папір: PAPER, не ability-item, не кастом-предмет, без службового NBT/імені. */
    private boolean isPlainPaper(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        if (AbilityItemFactory.isAbilityItem(item)) return false;
        if (customItemService.isCustomItem(item)) return false;
        // Іменований/лорений папір трактуємо як службовий — не кидаємо.
        if (item.hasItemMeta() && (item.getItemMeta().hasDisplayName() || item.getItemMeta().hasLore())) {
            return false;
        }
        return true;
    }
}
