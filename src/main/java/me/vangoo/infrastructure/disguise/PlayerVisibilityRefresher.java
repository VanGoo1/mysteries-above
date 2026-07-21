package me.vangoo.infrastructure.disguise;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Примусова пересинхронізація того, як глядачі бачать живого гравця.
 *
 * <p>Потрібна там, де сутність гравця вже перемальовували (личина маріонетки) і/або
 * телепортували в тому ж тіку: серверний трекер розсилає respawn зі СТАРОЇ позиції,
 * тож глядачі біля НОВОЇ лишаються без сутності — гравець для них невидимий.</p>
 *
 * <p>Метод <b>ідемпотентний і не знає про личини</b>: він перевстановлює той профіль,
 * який на гравцеві зараз. Тому його безпечно кликати і після зняття маски, і посеред
 * ланцюгового свапу — він лише повторно стверджує поточний вигляд, ніколи його не міняє.</p>
 *
 * <p>Два кроки, рознесені в часі (в одному тіку сервер згорнув би їх у no-op):
 * {@code hidePlayer}→{@code showPlayer} прибирає з клієнта залишкову сутність, а
 * {@code setPlayerProfile} тим самим профілем форсує повний цикл Paper
 * (player-info + сутність + метадані + спорядження), без якого клієнт малює гравця
 * з порожнім профілем — тіло без таблички з ніком.</p>
 */
public final class PlayerVisibilityRefresher {

    private PlayerVisibilityRefresher() {
    }

    /** Пересинхронізує вигляд {@code player} для всіх інших гравців. Безпечно для offline. */
    public static void resync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Plugin plugin = JavaPlugin.getProvidingPlugin(PlayerVisibilityRefresher.class);

        List<Player> viewers = new ArrayList<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) {
                continue;
            }
            viewers.add(viewer);
            viewer.hidePlayer(plugin, player);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (Player viewer : viewers) {
                if (viewer.isOnline()) {
                    viewer.showPlayer(plugin, player);
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.setPlayerProfile(player.getPlayerProfile());
                }
            }, 1L);
        }, 2L);
    }
}
