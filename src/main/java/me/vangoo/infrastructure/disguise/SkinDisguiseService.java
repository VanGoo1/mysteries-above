package me.vangoo.infrastructure.disguise;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.entity.Player;

/**
 * Підміна скіну <b>та ніка над головою</b> живого гравця для всіх глядачів.
 *
 * <p>Механізм — серверний Paper {@code setPlayerProfile} (той самий, що в
 * {@code Shapeshifting} і {@code GatheringAnonymizer}), а НЕ ручні пакети.
 * Раніше сервіс сам розсилав {@code PlayerInfoRemove/Update + Destroy + SpawnEntity};
 * ім'я над головою клієнт бере з профілю в player-info, і ручний ADD_PLAYER
 * програвав серверному трекеру — над маріонеткою лишався справжній нік кастера.
 * Paper робить повний цикл респавну (player-info + сутність + метадані + спорядження)
 * коректно й для тих глядачів, що тільки-но зайшли в зону видимості.</p>
 *
 * <p>Сервіс stateless: оригінальний профіль тримає викликач і віддає його в
 * {@link #undisguise(Player, PlayerProfile)}. Профіль НЕ персистентний — релогін
 * повертає справжній вигляд сам.</p>
 */
public final class SkinDisguiseService {

    private static final String TEXTURES_PROPERTY = "textures";

    private SkinDisguiseService() {
    }

    /**
     * Робить {@code player} видимим із заданим скіном та ніком {@code disguiseName}
     * для всіх глядачів (нік над головою + запис у tab-листі беруться з імені профілю).
     *
     * @param textureValue     текстури личини; {@code null} → лишається власний скін
     * @return профіль ДО маскування — збережіть його для {@link #undisguise}
     */
    public static PlayerProfile disguise(Player player, String textureValue,
                                         String textureSignature, String disguiseName) {
        PlayerProfile original = player.getPlayerProfile();

        PlayerProfile masked = player.getPlayerProfile();
        if (disguiseName != null && !disguiseName.isBlank()) {
            masked.setName(trimToProfileName(disguiseName));
        }
        if (textureValue != null) {
            masked.setProperty(new ProfileProperty(TEXTURES_PROPERTY, textureValue, textureSignature));
        }
        player.setPlayerProfile(masked);
        return original;
    }

    /** Повертає справжній вигляд і нік {@code player}. {@code null}-профіль ігнорується. */
    public static void undisguise(Player player, PlayerProfile originalProfile) {
        if (player == null || !player.isOnline() || originalProfile == null) {
            return;
        }
        player.setPlayerProfile(originalProfile);
    }

    /** Імена профілю Mojang обмежені 16 символами — обрізаємо, щоб клієнт не відкинув профіль. */
    private static String trimToProfileName(String name) {
        return name.length() > 16 ? name.substring(0, 16) : name;
    }
}
