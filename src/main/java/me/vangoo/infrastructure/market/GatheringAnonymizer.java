package me.vangoo.infrastructure.market;

import me.vangoo.infrastructure.compat.SkinProfiles;
import me.vangoo.infrastructure.compat.SkinProfiles.SkinProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Анонімність на час OPEN: усім один дефолтний скін (профіль без властивості textures —
 * через {@link SkinProfiles}), нік-таблички сховані scoreboard-командою, табліст маскується.
 * Профіль НЕ персистентний — релогін/рестарт повертає справжній вигляд сам собою.
 * На сервері без API профілів скін лишається справжнім, решта маскування працює.
 */
public class GatheringAnonymizer {

    private static final String TEAM_NAME = "ma_gathering";

    private final Map<UUID, SkinProfile> savedProfiles = new HashMap<>();

    public void mask(Player player, String alias) {
        savedProfiles.putIfAbsent(player.getUniqueId(), SkinProfiles.snapshot(player));
        SkinProfiles.clearTextures(player); // без текстур → дефолтний скін у всіх
        player.setPlayerListName(ChatColor.DARK_GRAY + alias);
        team().addEntry(player.getName());
    }

    public void unmask(Player player) {
        SkinProfile original = savedProfiles.remove(player.getUniqueId());
        if (original != null) {
            SkinProfiles.restore(player, original);
        }
        player.setPlayerListName(null);
        team().removeEntry(player.getName());
    }

    public void unmaskAll() {
        for (UUID id : Map.copyOf(savedProfiles).keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                unmask(player);
            } else {
                savedProfiles.remove(id); // офлайн: релогін і так поверне справжній профіль
            }
        }
    }

    private Team team() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(TEAM_NAME);
        if (team == null) {
            team = board.registerNewTeam(TEAM_NAME);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        return team;
    }
}
