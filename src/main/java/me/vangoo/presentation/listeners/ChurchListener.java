package me.vangoo.presentation.listeners;

import me.vangoo.application.services.ChurchService;
import me.vangoo.application.services.RampageManager;
import me.vangoo.infrastructure.citizens.ChurchPriestService;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import me.vangoo.infrastructure.ui.ChurchMenu;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Кліки по священику, kill-прогрес завдань і доручення «Зупинити бушуючого». */
public class ChurchListener implements Listener {

    private final ChurchPriestService priests;
    private final ChurchMenu menu;
    private final ChurchService churchService;
    private final MythicCreatureGateway creatures;
    private final RampageManager rampageManager;

    public ChurchListener(ChurchPriestService priests, ChurchMenu menu,
                          ChurchService churchService, MythicCreatureGateway creatures,
                          RampageManager rampageManager) {
        this.priests = priests;
        this.menu = menu;
        this.churchService = churchService;
        this.creatures = creatures;
        this.rampageManager = rampageManager;
    }

    @EventHandler
    public void onPriestClick(NPCRightClickEvent event) {
        priests.institutionOf(event.getNPC())
                .ifPresent(id -> menu.openFor(event.getClicker(), id));
    }

    @EventHandler
    public void onCreatureDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        creatures.creatureId(event.getEntity())
                .ifPresent(id -> churchService.onCreatureKilled(killer, id));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) {
            return;
        }
        if (rampageManager.isInRampage(event.getEntity().getUniqueId())) {
            churchService.onRampagerKilled(killer);
        }
    }
}
