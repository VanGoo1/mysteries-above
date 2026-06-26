package me.vangoo.infrastructure.citizens;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

/**
 * Серіалізація знімка інвентаря маріонетки у Base64-рядок та назад.
 *
 * <p>Список — це повний вміст інвентаря зі <b>збереженням слотів</b> ({@code null} = порожній слот).
 * Використовуємо {@link BukkitObjectOutputStream}, бо він коректно записує {@code null}-елементи й
 * зберігає {@link ItemStack} разом із метаданими (зокрема PDC/NBT кастомних предметів плагіна).
 *
 * <p>Один рядок легко й надійно персиститься вбудованим механізмом Citizens (DataKey String),
 * на відміну від спроби покластися на внутрішнє представлення списків ItemStack у Citizens.
 */
public final class MarionetteInventoryCodec {

    private static final Logger LOGGER = Logger.getLogger(MarionetteInventoryCodec.class.getName());

    private MarionetteInventoryCodec() {
    }

    /** Кодує список (зі слотами/null) у Base64. Порожній або null список → порожній рядок. */
    public static String encode(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos)) {
            out.writeInt(items.size());
            for (ItemStack item : items) {
                out.writeObject(item); // null допустимо — слот зберігається порожнім
            }
            out.flush();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOGGER.warning("Не вдалося серіалізувати інвентар маріонетки: " + e.getMessage());
            return "";
        }
    }

    /** Декодує Base64 назад у список зі збереженням слотів. Порожній/некоректний рядок → порожній список. */
    public static List<ItemStack> decode(String data) {
        List<ItemStack> result = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return result;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bais)) {
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                result.add((ItemStack) in.readObject());
            }
        } catch (Exception e) {
            LOGGER.warning("Не вдалося десеріалізувати інвентар маріонетки: " + e.getMessage());
            result.clear();
        }
        return result;
    }
}
