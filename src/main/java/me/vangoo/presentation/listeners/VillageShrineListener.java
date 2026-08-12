package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.organizations.VillageShrinePlacer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Святині з'являються разом із селами — у момент, коли світ уперше генерує їхній чанк.
 *
 * <p>Гейт {@code isNewChunk} не косметичний: без нього перевірка ганялась би на КОЖНЕ
 * завантаження чанка все життя сервера, хоча ставити вже давно нема чого.
 */
public class VillageShrineListener implements Listener {

    private final VillageShrinePlacer placer;

    public VillageShrineListener(VillageShrinePlacer placer) {
        this.placer = placer;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            placer.considerChunk(event.getChunk());
        }
    }
}
