package me.vangoo.presentation.listeners;

import me.vangoo.application.services.AbilityContextFactory;
import me.vangoo.application.services.PathwayManager;
import me.vangoo.infrastructure.items.CharacteristicExtractor;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.infrastructure.citizens.MarionetteMinionTrait;
import me.vangoo.pathways.fool.abilities.MarionettistControl;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
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
    private final CharacteristicExtractor characteristicExtractor;

    public MarionetteLifecycleListener(AbilityContextFactory abilityContextFactory,
                                       PathwayManager pathwayManager,
                                       CharacteristicExtractor characteristicExtractor) {
        this.abilityContextFactory = abilityContextFactory;
        this.pathwayManager = pathwayManager;
        this.characteristicExtractor = characteristicExtractor;
    }

    @EventHandler
    public void onNpcDeath(NPCDeathEvent event) {
        NPC npc = event.getNPC();
        if (npc == null || !npc.hasTrait(MarionetteMinionTrait.class)) {
            return;
        }

        // Якщо маріонетка несла особистість потойбічного — її Характеристика вилучається на місці смерті.
        MarionetteMinionTrait remnantTrait = npc.getTraitNullable(MarionetteMinionTrait.class);
        if (remnantTrait != null && remnantTrait.getCapturedPathway() != null
                && remnantTrait.getCapturedSequence() != null) {
            characteristicExtractor.extractTo(
                    npc.getStoredLocation(),
                    remnantTrait.getCapturedPathway().getName(),
                    remnantTrait.getCapturedSequence().level());
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

    /**
     * Попередження в action bar, коли NPC-маріонетку (вільну або як основне тіло під час
     * контролю) атакують — з іменем того, хто б'є ({@link MarionettistControl#onMarionetteDamaged}).
     */
    @EventHandler(ignoreCancelled = true)
    public void onMarionetteDamaged(EntityDamageByEntityEvent event) {
        if (!CitizensAPI.hasImplementation()) return;
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getEntity());
        if (npc == null || !npc.hasTrait(MarionetteMinionTrait.class)) return;
        MarionettistControl mc = resolveControl();
        if (mc == null) return;
        mc.onMarionetteDamaged(npc, event.getDamager());
    }

    /** Керуючи маріонеткою-мобом, гравець нічого не підбирає (тіло-моб має пустий інвентар). */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        MarionettistControl mc = resolveControl();
        if (mc == null) return;
        if (mc.isControllingMobMarionette(player.getUniqueId())) {
            event.setCancelled(true);
        }
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
