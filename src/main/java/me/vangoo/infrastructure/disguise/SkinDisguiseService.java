package me.vangoo.infrastructure.disguise;

import me.vangoo.infrastructure.compat.SkinProfiles;
import me.vangoo.infrastructure.compat.SkinProfiles.SkinProfile;
import org.bukkit.entity.Player;

/**
 * Підміна скіну <b>та ніка над головою</b> живого гравця для всіх глядачів.
 *
 * <p>Механізм — серверна підміна профілю ({@link SkinProfiles}), а НЕ ручні пакети.
 * Раніше сервіс сам розсилав {@code PlayerInfoRemove/Update + Destroy + SpawnEntity};
 * ім'я над головою клієнт бере з профілю в player-info, і ручний ADD_PLAYER
 * програвав серверному трекеру — над маріонеткою лишався справжній нік кастера.
 * Сервер робить повний цикл респавну (player-info + сутність + метадані + спорядження)
 * коректно й для тих глядачів, що тільки-но зайшли в зону видимості.</p>
 *
 * <p>Сервіс stateless: оригінальний профіль тримає викликач і віддає його в
 * {@link #undisguise(Player, SkinProfile)}. Профіль НЕ персистентний — релогін
 * повертає справжній вигляд сам.</p>
 *
 * <p><b>Сервер без API профілів</b> (Arclight — саме такий): підміна тихо no-op'иться,
 * личина працює далі, але скін і нік лишаються справжніми — див. {@link SkinProfiles}.</p>
 */
public final class SkinDisguiseService {

    private SkinDisguiseService() {
    }

    /**
     * Робить {@code player} видимим із заданим скіном та ніком {@code disguiseName}
     * для всіх глядачів (нік над головою + запис у tab-листі беруться з імені профілю).
     *
     * @param textureValue     текстури личини; {@code null} → лишається власний скін
     * @return профіль ДО маскування — збережіть його для {@link #undisguise}
     */
    public static SkinProfile disguise(Player player, String textureValue,
                                       String textureSignature, String disguiseName) {
        SkinProfile original = SkinProfiles.snapshot(player);
        SkinProfiles.disguise(player, textureValue, textureSignature, disguiseName);
        return original;
    }

    /** Повертає справжній вигляд і нік {@code player}. {@code null}-профіль ігнорується. */
    public static void undisguise(Player player, SkinProfile originalProfile) {
        SkinProfiles.restore(player, originalProfile);
    }
}
