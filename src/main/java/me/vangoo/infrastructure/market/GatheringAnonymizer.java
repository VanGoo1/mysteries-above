package me.vangoo.infrastructure.market;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Анонімність на час OPEN: усім один дефолтний скін (Paper setPlayerProfile без
 * властивості textures), нік-таблички сховані scoreboard-командою, табліст маскується.
 * Профіль НЕ персистентний — релогін/рестарт повертає справжній вигляд сам собою.
 */
public class GatheringAnonymizer {

    private static final String TEAM_NAME = "ma_gathering";

    private final Map<UUID, PlayerProfile> savedProfiles = new HashMap<>();

    public void mask(Player player, String alias) {
        savedProfiles.putIfAbsent(player.getUniqueId(), player.getPlayerProfile());
        PlayerProfile masked = player.getPlayerProfile();
        masked.removeProperty("textures"); // без текстур → дефолтний скін у всіх
        player.setPlayerProfile(masked);
        player.setPlayerListName(ChatColor.DARK_GRAY + alias);
        team().addEntry(player.getName());
    }

    public void unmask(Player player) {
        PlayerProfile original = savedProfiles.remove(player.getUniqueId());
        if (original != null) {
            player.setPlayerProfile(original);
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
