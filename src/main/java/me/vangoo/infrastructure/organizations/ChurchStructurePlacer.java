package me.vangoo.infrastructure.organizations;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;

import java.util.Random;

/** Ставить храмову будівлю з mysteries-datapack (mysteries:church_<id>). Нема NBT —
 * warn і false: сайт/NPC працюють і без будівлі (фолбек до появи контенту). */
public class ChurchStructurePlacer {

    private final Plugin plugin;
    private final Random random = new Random();

    public ChurchStructurePlacer(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean place(String institutionId, Location loc) {
        String shortId = institutionId.replaceFirst("^church-", "").replace('-', '_');
        NamespacedKey key = new NamespacedKey("mysteries", "church_" + shortId);
        Structure structure = Bukkit.getStructureManager().loadStructure(key);
        if (structure == null) {
            plugin.getLogger().warning("Church structure " + key + " not found in datapack; "
                    + "placing site without a building");
            return false;
        }
        structure.place(loc, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, random);
        return true;
    }
}
