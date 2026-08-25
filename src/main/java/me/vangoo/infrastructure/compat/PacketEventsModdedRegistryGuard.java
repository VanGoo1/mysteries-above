package me.vangoo.infrastructure.compat;

import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.mappings.SynchronizedRegistriesHandler;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Вимикає в PacketEvents синхронізацію реєстру чарів, бо на модованому сервері вона
 * все одно падає.
 *
 * <p>PacketEvents 2.8.0 читає пакет {@code registry_data} своїм внутрішнім лістенером і
 * для кожного чару декодує {@code supported_items} через
 * {@code ItemTypes.getRegistry().getByNameOrThrow(...)}. У реєстрі предметів PacketEvents
 * лише ванільні предмети, тож перший же модований (тут — {@code create:potato_cannon})
 * кидає {@code IllegalArgumentException}, і на КОЖНОМУ вході гравця в лог летить
 * стек-трейс «PacketEvents caught an unhandled exception while calling your listener».
 *
 * <p>Синхронізація чарів після цього винятку однаково не відбувається — тому прибрати
 * її явно поведінки не міняє, лише прибирає спам. Плагін чарами в пакетах не оперує:
 * PacketEvents тут потрібен для личин, нейм-плейтів і метаданих сутностей.
 *
 * <p>Реєстр registry-key'ів у PacketEvents приватний, публічного способу зняти запис
 * немає — звідси рефлексія. {@code handleRegistry} для невідомого ключа просто виходить,
 * тобто видалення запису — це і є «ігноруй цей реєстр».
 */
public final class PacketEventsModdedRegistryGuard {

    private PacketEventsModdedRegistryGuard() {
    }

    /**
     * @return {@code true}, якщо запис реєстру чарів було знято (повторний виклик — {@code false})
     */
    public static boolean disableEnchantmentSync(Logger logger) {
        ResourceLocation key = EnchantmentTypes.getRegistry().getRegistryKey();
        try {
            Field field = SynchronizedRegistriesHandler.class.getDeclaredField("REGISTRY_KEYS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<ResourceLocation, ?> registryKeys = (Map<ResourceLocation, ?>) field.get(null);
            return registryKeys.remove(key) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.warning("PacketEvents registry guard: could not disable " + key
                    + " sync, expect a stack trace per player join: " + e);
            return false;
        }
    }
}
