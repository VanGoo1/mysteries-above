package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.organizations.ChurchSiteService;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.List;

/**
 * Заявка храму за якорем-міткою: перший заявлений храм свого типу отримує сайт,
 * сховище й NPC-священика, повторна заявка тієї ж церкви — no-op.
 *
 * Слухач не знає й не мусить знати, ЯК будівля потрапила у світ: він реагує на мітку,
 * яку несе сам NBT храму. Тому він переживає зміну підходу до появи храмів — досить,
 * щоб мітка з'явилась як сутність. Див. `.claude/rules/church-structures.md`.
 */
public class ChurchSpawnListener implements Listener {

    private final ChurchSiteService sites;

    public ChurchSpawnListener(ChurchSiteService sites) {
        this.sites = sites;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        scan(event.getEntities());
    }

    /**
     * Друга точка входу навмисно, і тільки для щойно згенерованого чанка: сутності
     * структури приходять у світ разом із самим чанком, і `EntitiesLoadEvent` на них
     * може не спрацювати. Подвійний прохід безпечний — мітка одноразова, а `claim`
     * ідемпотентний. Гейт `isNewChunk` тут не косметичний: без нього `getEntities()`
     * смикав би завантаження сутностей на КОЖЕН завантажений чанк.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            scan(List.of(event.getChunk().getEntities()));
        }
    }

    /**
     * Заявка й прибирання мітки живуть у `ChurchSiteService.claimAnchors` — тією самою
     * операцією користується `ChurchWorldProvider` після пасти храмів.
     *
     * Мітки шрайнів тут навмисно не чіпаємо: вони лишаються у світі назавжди (це і є
     * їхня персистентність), а церкву їм призначає `ShrineService` при першому дотику.
     */
    private void scan(List<Entity> entities) {
        sites.claimAnchors(entities);
    }
}
