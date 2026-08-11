package me.vangoo.infrastructure.compat;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

/**
 * Підміна скіну й ніка живого гравця (личини Блазня, анонімність ринку).
 *
 * <p>Єдиний спосіб зробити це БЕЗ ручних пакетів — Paper'ів {@code Player#setPlayerProfile},
 * якого немає ні в Spigot-API, ні на сервері проєкту (Arclight). Проєкт компілюється проти
 * Spigot-API, тож типів {@code com.destroystokyo.paper.profile.*} у коді бути не може — цей
 * клас тримає їх виключно рефлексією й віддає назовні непрозорий {@link SkinProfile}.
 *
 * <p><b>Коли API немає — усе тихо no-op'иться</b> (з одним WARNING у лог при першому дотику):
 * личина працює далі, але скін і нік над головою лишаються справжніми. Це свідома деградація,
 * а не поламана механіка: решта личини (ім'я в чаті, поведінка, членство) не залежить від
 * профілю. Повноцінна заміна — власна розсилка player-info через PacketEvents (він уже в
 * shade'і); поки її немає, тримаємо чесний no-op замість крашу.
 */
public final class SkinProfiles {

    /** Непрозорий знімок профілю: усередині — Paper'ів {@code PlayerProfile} або {@code null}. */
    public record SkinProfile(Object handle) {
    }

    private static final String TEXTURES = "textures";

    private static final Method GET_PROFILE = method(Player.class, "getPlayerProfile");
    private static final Method OFFLINE_PROFILE = method(OfflinePlayer.class, "getPlayerProfile");
    private static final Class<?> PROFILE_TYPE = type("com.destroystokyo.paper.profile.PlayerProfile");
    private static final Class<?> PROPERTY_TYPE = type("com.destroystokyo.paper.profile.ProfileProperty");
    private static final Method SET_PROFILE = PROFILE_TYPE == null
            ? null : method(Player.class, "setPlayerProfile", PROFILE_TYPE);
    private static final Method SET_NAME = method(PROFILE_TYPE, "setName", String.class);
    private static final Method SET_PROPERTY = PROPERTY_TYPE == null
            ? null : method(PROFILE_TYPE, "setProperty", PROPERTY_TYPE);
    private static final Method REMOVE_PROPERTY = method(PROFILE_TYPE, "removeProperty", String.class);
    private static final Method GET_PROPERTIES = method(PROFILE_TYPE, "getProperties");
    private static final Method COMPLETE_FROM_CACHE = method(PROFILE_TYPE, "completeFromCache");
    private static final Method PROPERTY_NAME = method(PROPERTY_TYPE, "getName");
    private static final Method PROPERTY_VALUE = method(PROPERTY_TYPE, "getValue");
    private static final Method PROPERTY_SIGNATURE = method(PROPERTY_TYPE, "getSignature");
    private static final Constructor<?> PROPERTY_CTOR =
            constructor(PROPERTY_TYPE, String.class, String.class, String.class);

    private static boolean warned;

    private SkinProfiles() {
    }

    /** Чи вміє цей сервер міняти профіль гравця (тобто чи це справді Paper). */
    public static boolean isSupported() {
        return GET_PROFILE != null && SET_PROFILE != null && PROPERTY_CTOR != null;
    }

    /** Профіль гравця «як зараз» — щоб потім повернути його {@link #restore}. */
    public static SkinProfile snapshot(Player player) {
        if (!isSupported() || player == null) return warnAndReturnEmpty();
        return new SkinProfile(invoke(GET_PROFILE, player));
    }

    /** Повертає раніше знятий профіль. {@code null}-знімок ігнорується. */
    public static void restore(Player player, SkinProfile profile) {
        if (!isSupported() || player == null || !player.isOnline()) return;
        if (profile == null || profile.handle() == null) return;
        invoke(SET_PROFILE, player, profile.handle());
    }

    /**
     * Ставить гравцеві чужий скін і/або нік.
     *
     * @param textureValue значення властивості {@code textures}; {@code null} — скін не чіпаємо
     * @param name         нік над головою й у таб-листі; {@code null} — лишається власний
     */
    public static void disguise(Player player, String textureValue, String signature, String name) {
        if (!isSupported() || player == null || !player.isOnline()) {
            warnAndReturnEmpty();
            return;
        }
        Object profile = invoke(GET_PROFILE, player);
        if (profile == null) return;

        if (name != null && !name.isBlank()) {
            // Імена профілю Mojang обмежені 16 символами — довше клієнт відкидає.
            invoke(SET_NAME, profile, name.length() > 16 ? name.substring(0, 16) : name);
        }
        if (textureValue != null) {
            Object property = newProperty(TEXTURES, textureValue, signature);
            if (property != null) invoke(SET_PROPERTY, profile, property);
        }
        invoke(SET_PROFILE, player, profile);
    }

    /** Личина лише за іменем: нік міняється, скін скидається на дефолтний. */
    public static void disguiseNameOnly(Player player, String name) {
        if (!isSupported() || player == null || !player.isOnline()) {
            warnAndReturnEmpty();
            return;
        }
        Object profile = invoke(GET_PROFILE, player);
        if (profile == null) return;

        if (name != null && !name.isBlank()) {
            invoke(SET_NAME, profile, name.length() > 16 ? name.substring(0, 16) : name);
        }
        invoke(REMOVE_PROPERTY, profile, TEXTURES);
        invoke(SET_PROFILE, player, profile);
    }

    /** Знімає текстури — гравець стає дефолтним скіном (анонімність збору). */
    public static void clearTextures(Player player) {
        if (!isSupported() || player == null || !player.isOnline()) {
            warnAndReturnEmpty();
            return;
        }
        Object profile = invoke(GET_PROFILE, player);
        if (profile == null) return;

        invoke(REMOVE_PROPERTY, profile, TEXTURES);
        invoke(SET_PROFILE, player, profile);
    }

    /**
     * Текстури гравця як пара {@code [value, signature]} — щоб скопіювати їх на іншого.
     *
     * <p>Для офлайн-гравця профіль доповнюється з ЛОКАЛЬНОГО кешу сервера
     * ({@code completeFromCache}), без блокуючого запиту в Mojang: інакше в офлайн-цілі
     * текстур майже завжди немає, навіть якщо гравець колись заходив. Кеша немає — порожньо,
     * і кличучий сам вирішує, що робити (звичайно — личина лише за іменем).
     */
    public static Optional<String[]> textures(OfflinePlayer player) {
        if (!isSupported() || player == null) return Optional.empty();

        Object profile = invoke(OFFLINE_PROFILE, player);
        if (profile == null || !PROFILE_TYPE.isInstance(profile)) return Optional.empty();

        invoke(COMPLETE_FROM_CACHE, profile);
        if (!(invoke(GET_PROPERTIES, profile) instanceof Collection<?> properties)) {
            return Optional.empty();
        }
        for (Object property : properties) {
            if (!TEXTURES.equals(invoke(PROPERTY_NAME, property))) continue;
            Object value = invoke(PROPERTY_VALUE, property);
            if (value == null) continue;
            return Optional.of(new String[]{(String) value, (String) invoke(PROPERTY_SIGNATURE, property)});
        }
        return Optional.empty();
    }

    /**
     * Перевстановлює гравцеві ТОЙ САМИЙ профіль — так сервер робить повний цикл респавну для
     * глядачів (player-info + сутність + метадані). Ідемпотентно й нічого не міняє.
     */
    public static void refresh(Player player) {
        if (!isSupported() || player == null || !player.isOnline()) return;

        Object profile = invoke(GET_PROFILE, player);
        if (profile != null) invoke(SET_PROFILE, player, profile);
    }

    private static Object newProperty(String name, String value, String signature) {
        try {
            return PROPERTY_CTOR.newInstance(name, value, signature);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static SkinProfile warnAndReturnEmpty() {
        if (!isSupported() && !warned) {
            warned = true;
            Bukkit.getLogger().warning("[MysteriesAbove] Player profile API is missing on this server"
                    + " (not Paper) — skin/name disguises stay visually unchanged.");
        }
        return new SkinProfile(null);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null || target == null) return null;
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null) return null;
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException | LinkageError e) {
            return null;
        }
    }

    private static Constructor<?> constructor(Class<?> owner, Class<?>... parameters) {
        if (owner == null) return null;
        try {
            return owner.getConstructor(parameters);
        } catch (NoSuchMethodException | LinkageError e) {
            return null;
        }
    }
}
