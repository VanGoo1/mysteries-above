package me.vangoo.utils;

import me.vangoo.LotmPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.function.Function;


public class NBTBuilder {

    private final ItemStack itemStack;
    private final ItemMeta itemMeta;
    private final PersistentDataContainer container;
    private static LotmPlugin plugin;

    public NBTBuilder(ItemStack itemStack) {
        if (itemStack == null) {
            throw new IllegalArgumentException("ItemStack не може бути null");
        }
        if (itemStack.getItemMeta() == null) {
            throw new IllegalArgumentException("ItemStack повинен мати ItemMeta");
        }

        this.itemStack = itemStack.clone();

        this.itemMeta = this.itemStack.getItemMeta();
        this.container = this.itemMeta.getPersistentDataContainer();
    }

    public static void setPlugin(LotmPlugin plugin){
        NBTBuilder.plugin = plugin;
    }

    private static NamespacedKey createKey(String key) {
        return new NamespacedKey(plugin, key);
    }

    public NBTBuilder remove(String key) {
        container.remove(createKey(key));
        return this;
    }

    public NBTBuilder setString(String key, String value) {
        container.set(createKey(key), PersistentDataType.STRING, value);
        return this;
    }

    public NBTBuilder setInt(String key, int value) {
        container.set(createKey(key), PersistentDataType.INTEGER, value);
        return this;
    }

    public NBTBuilder setDouble(String key, double value) {
        container.set(createKey(key), PersistentDataType.DOUBLE, value);
        return this;
    }

    public NBTBuilder setBoolean(String key, boolean value) {
        container.set(createKey(key), PersistentDataType.BYTE, (byte) (value ? 1 : 0));
        return this;
    }
    public Optional<String> getString(ItemStack itemStack, String key) {
        return getValue(itemStack, key, PersistentDataType.STRING);
    }

    public Optional<Boolean> getBoolean(ItemStack itemStack, String key) {
        return getValue(itemStack, key, PersistentDataType.BYTE)
                .map(b -> b == 1);
    }

    public Optional<Double> getDouble(ItemStack itemStack, String key) {
        return getValue(itemStack, key, PersistentDataType.DOUBLE);
    }

    public Optional<Integer> getInt(ItemStack itemStack, String key) {
        return getValue(itemStack, key, PersistentDataType.INTEGER);
    }

    public <T> Optional<T> withContainer(ItemStack itemStack, Function<PersistentDataContainer, T> function) {
        ItemMeta meta = itemStack.getItemMeta();

        if (meta == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(function.apply(meta.getPersistentDataContainer()));
    }

    private <T> Optional<T> getValue(ItemStack itemStack, String key, PersistentDataType<?, T> dataType) {
        return withContainer(itemStack, container ->
                container.get(createKey(key), dataType));
    }

    public static <T> boolean hasKey(ItemStack itemStack, String key, PersistentDataType<?, T> dataType) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(createKey(key), dataType);
    }

    public ItemStack build() {
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }
}
