package me.vangoo.infrastructure.citizens;

import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.InstitutionRegistry;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * NPC-священики церков: по одному на сайт; SHOULD_SAVE=false — респавняться на
 * старті
 * з church-sites.json (як Посередник ринку, не персистяться Citizens'ом).
 */
public class ChurchPriestService {

    /** Наскільки піднімати священика, що опинився в підлозі. Далі — уже не «мітка нижче». */
    private static final int MAX_LIFT = 3;

    private final InstitutionRegistry registry;
    private final Map<Integer, String> npcToInstitution = new HashMap<>();

    public ChurchPriestService(InstitutionRegistry registry) {
        this.registry = registry;
    }

    /**
     * Священик у церкви рівно один. Гард не косметичний: точок виклику три (заявка
     * храму, `spawnAllNpcs` на старті, відродження після замаху ордену), і будь-який
     * повторний виклик без нього залишав би в світі стос NPC на одному місці.
     */
    public void spawn(String institutionId, Location location) {
        despawn(institutionId);
        String name = registry.byId(institutionId)
                .map(Institution::displayName).orElse(institutionId);
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER,
                ChatColor.GOLD + "Жрець — " + name);
        npc.data().set(NPC.Metadata.SHOULD_SAVE, false);
        npc.setProtected(true);
        applyGaze(npc);
        npc.spawn(standingSpot(location));
        npcToInstitution.put(npc.getId(), institutionId);
    }

    /**
     * Священик стежить очима за гравцем, і кожен глядач бачить погляд на СЕБЕ:
     * {@code setPerPlayer} шле поворот голови пакетами окремо на кожного, тож двоє поруч
     * не б'ються за одну ротацію сутності.
     *
     * <p>{@code setHeadOnly(false)} — повертається все тіло, як у звичайного гравця:
     * сама лише голова, що крутиться на нерухомому тулубі, читається неприродно.
     */
    private static void applyGaze(NPC npc) {
        LookClose look = npc.getOrAddTrait(LookClose.class);
        look.lookClose(true);
        look.setPerPlayer(true);
        look.setHeadOnly(false);
        look.setRealisticLooking(true);
        look.setRange(12);
    }

    /**
     * Мітка в NBT може стояти на самому блоці підлоги (у `church_lord_of_storms` вона на
     * `y=0` — нижньому шарі структури), і NPC тоді вростає в підлогу по пояс. Тому точку
     * піднімаємо, доки не звільниться повний зріст.
     *
     * <p>Виправляти це в самих NBT було б крихкіше: помилку легко повторити при кожному
     * перезбереженні, а тут вона гаситься для всіх храмів одразу — і теперішніх, і майбутніх.
     */
    private static Location standingSpot(Location location) {
        Location spot = location.clone();
        for (int lift = 0; lift < MAX_LIFT; lift++) {
            if (!spot.getBlock().getType().isSolid()
                    && !spot.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                return spot;
            }
            spot.add(0, 1, 0);
        }
        return location.clone();
    }

    public Optional<String> institutionOf(NPC npc) {
        return npc == null ? Optional.empty()
                : Optional.ofNullable(npcToInstitution.get(npc.getId()));
    }

    /** Прибирає священика цієї церкви, де б він не стояв. */
    public void despawn(String institutionId) {
        npcToInstitution.entrySet().removeIf(e -> {
            if (!e.getValue().equals(institutionId)) {
                return false;
            }
            NPC npc = CitizensAPI.getNPCRegistry().getById(e.getKey());
            if (npc != null) {
                npc.destroy();
            }
            return true;
        });
    }

    public void despawnAt(String institutionId, Location near) {
        npcToInstitution.entrySet().removeIf(e -> {
            if (!e.getValue().equals(institutionId))
                return false;
            NPC npc = CitizensAPI.getNPCRegistry().getById(e.getKey());
            if (npc == null)
                return true;
            boolean close = npc.isSpawned() && npc.getEntity().getWorld() == near.getWorld()
                    && npc.getEntity().getLocation().distance(near) <= 32;
            if (close)
                npc.destroy();
            return close;
        });
    }

    public void despawnAll() {
        npcToInstitution.keySet().forEach(id -> {
            NPC npc = CitizensAPI.getNPCRegistry().getById(id);
            if (npc != null)
                npc.destroy();
        });
        npcToInstitution.clear();
    }
}
