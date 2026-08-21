package me.vangoo.infrastructure.compat;

import com.github.retrooper.packetevents.util.mappings.SynchronizedRegistriesHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Гард лізе рефлексією в приватне поле PacketEvents — тест ловить його перейменування
 * при підйомі версії (інакше гард зникне мовчки, лише з WARNING у лог сервера).
 *
 * <p>Далі поля тест не йде: реєстри PacketEvents ініціалізуються лише коли
 * {@code PacketEvents.getAPI()} уже виставлено, а headless його немає.
 */
class PacketEventsModdedRegistryGuardTest {

    @Test
    void registryKeysFieldStillExists() throws NoSuchFieldException {
        Field field = SynchronizedRegistriesHandler.class.getDeclaredField("REGISTRY_KEYS");
        assertTrue(Map.class.isAssignableFrom(field.getType()));
    }
}
