package me.vangoo.infrastructure.citizens;

import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.InstitutionRegistry;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** NPC-священики церков: по одному на сайт; SHOULD_SAVE=false — респавняться на старті
 * з church-sites.json (як Посередник ринку, не персистяться Citizens'ом). */
public class ChurchPriestService {

    private final InstitutionRegistry registry;
    private final Map<Integer, String> npcToInstitution = new HashMap<>();

    public ChurchPriestService(InstitutionRegistry registry) {
        this.registry = registry;
    }

    public void spawn(String institutionId, Location location) {
        String name = registry.byId(institutionId)
                .map(Institution::displayName).orElse(institutionId);
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER,
                ChatColor.GOLD + "Жрець — " + name);
        npc.data().set(NPC.Metadata.SHOULD_SAVE, false);
        npc.setProtected(true);
        npc.spawn(location);
        npcToInstitution.put(npc.getId(), institutionId);
    }

    public Optional<String> institutionOf(NPC npc) {
        return npc == null ? Optional.empty()
                : Optional.ofNullable(npcToInstitution.get(npc.getId()));
    }

    public void despawnAt(String institutionId, Location near) {
        npcToInstitution.entrySet().removeIf(e -> {
            if (!e.getValue().equals(institutionId)) return false;
            NPC npc = CitizensAPI.getNPCRegistry().getById(e.getKey());
            if (npc == null) return true;
            boolean close = npc.isSpawned() && npc.getEntity().getWorld() == near.getWorld()
                    && npc.getEntity().getLocation().distance(near) <= 32;
            if (close) npc.destroy();
            return close;
        });
    }

    public void despawnAll() {
        npcToInstitution.keySet().forEach(id -> {
            NPC npc = CitizensAPI.getNPCRegistry().getById(id);
            if (npc != null) npc.destroy();
        });
        npcToInstitution.clear();
    }
}
