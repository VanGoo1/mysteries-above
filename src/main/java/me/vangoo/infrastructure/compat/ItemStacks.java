package me.vangoo.infrastructure.compat;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Серіалізація предмета в байти й назад.
 *
 * <p>Paper'ові {@code ItemStack.serializeAsBytes()} / {@code deserializeBytes(byte[])} (NBT)
 * на Spigot-API немає, тож використовується ванільний {@code BukkitObjectOutputStream} — та
 * сама {@code ConfigurationSerializable}-форма, що й у yml-конфігах.
 *
 * <p>Формати НЕ сумісні між собою: байти, писані Paper-NBT, цим читачем не відкриються.
 * Це стосується лише ескроу підпільного ринку (`gathering-state.json`) — стан на кілька
 * хвилин, тож {@code null} на нечитабельному записі є прийнятною поведінкою, і кличучий
 * код мусить його пережити.
 */
public final class ItemStacks {

    private ItemStacks() {
    }

    /** Предмет → байти. {@code null} — предмета немає або серіалізація не вдалась. */
    public static byte[] toBytes(ItemStack stack) {
        if (stack == null) return null;

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(buffer)) {
            out.writeObject(stack);
        } catch (IOException e) {
            return null;
        }
        return buffer.toByteArray();
    }

    /** Байти → предмет. {@code null} — запис порожній, чужого формату або побитий. */
    public static ItemStack fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;

        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject() instanceof ItemStack stack ? stack : null;
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            return null;
        }
    }
}
