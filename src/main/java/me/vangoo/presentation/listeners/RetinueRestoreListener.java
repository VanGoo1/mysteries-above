package me.vangoo.presentation.listeners;

import me.vangoo.application.services.AbilityContextFactory;
import me.vangoo.application.services.PathwayManager;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.pathways.death.Death;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Death, Посл. 6: почет нежиті переживає relog і рестарт сервера — при вході респавнить
 * збережений почет (див. {@code RetinueStore}, {@code .claude/rules/lingering-souls.md}).
 */
public class RetinueRestoreListener implements Listener {

    private final PathwayManager pathwayManager;
    private final AbilityContextFactory contextFactory;

    public RetinueRestoreListener(PathwayManager pathwayManager, AbilityContextFactory contextFactory) {
        this.pathwayManager = pathwayManager;
        this.contextFactory = contextFactory;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!(pathwayManager.getPathway("Death") instanceof Death death)) return;

        Player player = event.getPlayer();
        IAbilityContext context = contextFactory.createContext(player);
        death.getRetinue().restore(context, player);
    }
}
