package me.vangoo.presentation.listeners;

import me.vangoo.application.services.PathwayManager;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.infrastructure.citizens.MarionetteMinionTrait;
import me.vangoo.pathways.fool.abilities.MarionettistControl;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.event.CitizensReloadEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Відбудовує рантайм-реєстри {@link MarionettistControl} після того, як Citizens завантажив NPC із
 * {@code saves.yml} (на старті сервера або після {@code /citizens reload}).
 *
 * <p>Citizens сам відновлює NPC-оболонки та персистентні трейти, але здібність тримає власні
 * in-memory мапи (ліміт, glow, меню, обробка смерті), які треба наповнити заново. Скануємо реєстр і
 * для кожного NPC із {@link MarionetteMinionTrait} повертаємо його у здібність. Скан ідемпотентний.
 */
public class MarionetteRestorer implements Listener {

    private static final Logger LOGGER = Logger.getLogger(MarionetteRestorer.class.getName());

    private final PathwayManager pathwayManager;

    public MarionetteRestorer(PathwayManager pathwayManager) {
        this.pathwayManager = pathwayManager;
    }

    @EventHandler
    public void onCitizensEnable(CitizensEnableEvent event) {
        restoreNow();
    }

    @EventHandler
    public void onCitizensReload(CitizensReloadEvent event) {
        restoreNow();
    }

    /**
     * Сканує реєстр Citizens і повертає маріонетки у здібність. Ідемпотентно — можна викликати
     * багато разів (з {@link CitizensEnableEvent}, {@link CitizensReloadEvent} та відкладеного
     * фолбек-таску в {@code onEnable}), щоб не залежати від точного часу завантаження NPC.
     */
    public void restoreNow() {
        Ability a = pathwayManager.findAbilityInAllPathways(MarionettistControl.IDENTITY);
        if (!(a instanceof MarionettistControl mc)) {
            return;
        }
        if (!CitizensAPI.hasImplementation()) {
            return;
        }

        int total = 0, withTrait = 0, restored = 0, noOwner = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            total++;
            if (npc == null || !npc.hasTrait(MarionetteMinionTrait.class)) {
                continue;
            }
            withTrait++;
            MarionetteMinionTrait trait = npc.getTraitNullable(MarionetteMinionTrait.class);
            UUID ownerId = (trait != null) ? trait.getOwnerCasterId() : null;
            if (ownerId == null) {
                noOwner++;
                LOGGER.warning("Маріонетка #" + npc.getId() + " (" + npc.getName()
                        + ") без власника у saves.yml — пропускаємо.");
                continue;
            }
            mc.registerLoadedMarionette(npc.getId(), ownerId);
            restored++;
        }
        if (withTrait > 0 || restored > 0) {
            LOGGER.info("Скан маріонеток: NPC=" + total + ", з трейтом=" + withTrait
                    + ", відновлено=" + restored + ", без власника=" + noOwner);
        }
    }
}
