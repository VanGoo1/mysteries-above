package me.vangoo.domain;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public interface IItemResolver {
    Optional<ItemStack> createItemStack(String id);
}
