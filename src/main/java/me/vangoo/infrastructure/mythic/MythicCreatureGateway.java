package me.vangoo.infrastructure.mythic;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/** Єдиний вхід до MythicMobs: спавн за internal name та ідентифікація сутностей.
 * Увесь io.lumine-код живе в infrastructure.mythic (ArchitectureTest). */
public final class MythicCreatureGateway {

    private final Plugin plugin;

    public MythicCreatureGateway(Plugin plugin) {
        this.plugin = plugin;
    }

    public Optional<LivingEntity> spawn(String mobId, Location loc) {
        if (loc == null || loc.getWorld() == null) return Optional.empty();
        MythicMob mob = MythicBukkit.inst().getMobManager().getMythicMob(mobId).orElse(null);
        if (mob == null) {
            plugin.getLogger().warning("Unknown MythicMobs mob '" + mobId + "'; skipping spawn");
            return Optional.empty();
        }
        ActiveMob active = mob.spawn(BukkitAdapter.adapt(loc), 1);
        Entity bukkit = active.getEntity().getBukkitEntity();
        return bukkit instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
    }

    public boolean isCreature(Entity e) {
        return e != null && MythicBukkit.inst().getMobManager().isMythicMob(e);
    }

    public Optional<String> creatureId(Entity e) {
        if (e == null) return Optional.empty();
        return MythicBukkit.inst().getMobManager().getActiveMob(e.getUniqueId())
                .map(am -> am.getType().getInternalName());
    }
}
