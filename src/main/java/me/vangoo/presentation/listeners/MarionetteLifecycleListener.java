package me.vangoo.presentation.listeners;

import me.vangoo.application.services.AbilityContextFactory;
import me.vangoo.application.services.PathwayManager;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.infrastructure.citizens.MarionetteMinionTrait;
import me.vangoo.pathways.fool.abilities.MarionettistControl;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Єдиний постійний лістенер життєвого циклу маріонеток. Замінює тимчасові per-possession підписки,
 * щоб логіка працювала і для свіжих, і для відновлених після рестарту маріонеток.
 *
 * <ul>
 *   <li><b>{@link NPCDeathEvent}</b> — смерть маріонетки: дроп речей, авто-вихід власника (якщо керує),
 *       чистка реєстрів і знищення NPC ({@link MarionettistControl#onMarionetteDeath}).</li>
 *   <li><b>{@link PlayerQuitEvent}</b> — гравець виходить, керуючи маріонеткою: повертаємо йому тіло,
 *       інвентар, особистість і скін, щоб усе коректно зберіглось; маріонетка лишається й переживе
 *       рестарт ({@link MarionettistControl#exitIfPossessing}).</li>
 * </ul>
 */
public class MarionetteLifecycleListener implements Listener {

    private final AbilityContextFactory abilityContextFactory;
    private final PathwayManager pathwayManager;

    public MarionetteLifecycleListener(AbilityContextFactory abilityContextFactory,
                                       PathwayManager pathwayManager) {
        this.abilityContextFactory = abilityContextFactory;
        this.pathwayManager = pathwayManager;
    }

    @EventHandler
    public void onNpcDeath(NPCDeathEvent event) {
        NPC npc = event.getNPC();
        if (npc == null || !npc.hasTrait(MarionetteMinionTrait.class)) {
            return;
        }
        MarionettistControl mc = resolveControl();
        if (mc == null) return;

        // Контекст власника потрібен лише якщо він онлайн (для swapOut/повідомлення).
        IAbilityContext ownerCtx = null;
        MarionetteMinionTrait trait = npc.getTraitNullable(MarionetteMinionTrait.class);
        UUID ownerId = (trait != null) ? trait.getOwnerCasterId() : null;
        if (ownerId != null) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null) {
                ownerCtx = abilityContextFactory.createContext(owner);
            }
        }
        mc.onMarionetteDeath(event, ownerCtx);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        MarionettistControl mc = resolveControl();
        if (mc == null) return;
        // Дешево: якщо гравець не керує маріонеткою — метод одразу поверне false.
        if (!mc.isPossessing(event.getPlayer().getUniqueId())) {
            return;
        }
        IAbilityContext ctx = abilityContextFactory.createContext(event.getPlayer());
        mc.exitIfPossessing(ctx);
    }

    private MarionettistControl resolveControl() {
        Ability a = pathwayManager.findAbilityInAllPathways(MarionettistControl.IDENTITY);
        return (a instanceof MarionettistControl mc) ? mc : null;
    }
}
