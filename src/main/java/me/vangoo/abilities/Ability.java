package me.vangoo.abilities;

import me.vangoo.beyonders.Beyonder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface Ability {
    String getName();

    String getDescription();

    int getSpiritualityCost();

    void execute(Player caster, Beyonder beyonder);

    public ItemStack getItem();

}
