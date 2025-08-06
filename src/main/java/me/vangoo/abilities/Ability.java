package me.vangoo.abilities;

import me.vangoo.LotmPlugin;
import me.vangoo.beyonders.Beyonder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public abstract class Ability {
    protected static LotmPlugin plugin;

    public static void setPlugin(LotmPlugin plugin) {
        Ability.plugin = plugin;
    }

    public abstract String getName();

    public abstract String getDescription();

    public abstract int getSpiritualityCost();

    public abstract void execute(Player caster, Beyonder beyonder);

    public abstract ItemStack getItem();
}
