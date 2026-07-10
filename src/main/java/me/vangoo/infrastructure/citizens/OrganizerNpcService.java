package me.vangoo.infrastructure.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

/**
 * NPC-організатор Зборів: підтверджує справжність обмінюваних речей і скуповує їх
 * за фіксованим прайсом. Живе лише під час фази OPEN — spawn на відкритті,
 * despawn (destroy) на закритті; НЕ персиститься Citizens'ом між рестартами.
 */
public class OrganizerNpcService {

    private NPC npc;

    public void spawn(Location location) {
        despawn(); // страховка від подвійного спавну
        npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER,
                ChatColor.DARK_PURPLE + "Посередник");
        npc.setProtected(true); // невразливий до атак
        npc.spawn(location);
    }

    public void despawn() {
        if (npc != null) {
            npc.destroy();
            npc = null;
        }
    }

    public boolean isOrganizer(NPC candidate) {
        return npc != null && candidate != null && npc.getId() == candidate.getId();
    }
}
