package me.vangoo.abilities;

import me.vangoo.LotmPlugin;
import me.vangoo.beyonders.Beyonder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.Date;

public abstract class Ability {
    protected static LotmPlugin plugin;
    protected LocalDateTime lastUse = LocalDateTime.now();

    public LocalDateTime getLastUse() {
        return lastUse;
    }

    public void updateLastUse() {
        lastUse = LocalDateTime.now();
    }

    public static void setPlugin(LotmPlugin plugin) {
        Ability.plugin = plugin;
    }

    public abstract String getName();

    public abstract String getDescription();

    public abstract int getSpiritualityCost();

    public abstract boolean execute(Player caster, Beyonder beyonder);

    public abstract ItemStack getItem();

    public abstract int getCooldown();
}
