package me.vangoo.domain;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.managers.AbilityItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public abstract class Ability {
    protected static MysteriesAbovePlugin plugin;

    protected static AbilityItemFactory abilityItemFactory = new AbilityItemFactory();

    public static void setPlugin(MysteriesAbovePlugin plugin) {
        Ability.plugin = plugin;
    }

    public boolean isPassive() {
        return false;
    }

    public abstract String getName();

    public abstract String getDescription();

    public abstract int getSpiritualityCost();

    public abstract boolean execute(Player caster, Beyonder beyonder);

    public abstract ItemStack getItem();

    public abstract int getCooldown();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Ability other = (Ability) obj;

        return Objects.equals(getName(), other.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }
}
