package me.vangoo.infrastructure.organizations;

import org.bukkit.plugin.Plugin;

/** Читає секцію orders.* із config.yml; усі значення мають дефолт у коді. */
public record OrderConfig(int invitesBeyonderKills,
                          int tasksRefreshHours, int tasksMaxActive, int tasksSetsPerWindow,
                          int raidChannelSeconds, double raidAlarmChance, double raidAlarmIntelFactor,
                          int raidLootPicks, int raidLootIntelPicks,
                          int raidTempleCooldownHours, int raidZoneRadius, int raidGuards,
                          int assassinationPriestRespawnHours,
                          int sabotageDelayHours, int reconTtlHours,
                          double exposureReconChance, double exposureSabotageChance,
                          double exposureFailedRaidChance,
                          int stashSeedIngredientsPerRecipe, int favorIngredientsPerClaim,
                          int rejoinCooldownDays, int talismanReissueCooldownMinutes) {

    public static OrderConfig load(Plugin plugin) {
        var cfg = plugin.getConfig();
        return new OrderConfig(
                cfg.getInt("orders.invites.beyonder-kills", 3),
                cfg.getInt("orders.tasks.refresh-hours", 24),
                cfg.getInt("orders.tasks.max-active", 2),
                cfg.getInt("orders.tasks.sets-per-window", 5),
                cfg.getInt("orders.raid.channel-seconds", 45),
                cfg.getDouble("orders.raid.alarm-chance", 0.04),
                cfg.getDouble("orders.raid.alarm-intel-factor", 0.5),
                cfg.getInt("orders.raid.loot-picks", 3),
                cfg.getInt("orders.raid.loot-intel-picks", 4),
                cfg.getInt("orders.raid.temple-cooldown-hours", 24),
                cfg.getInt("orders.raid.zone-radius", 24),
                cfg.getInt("orders.raid.guards", 2),
                cfg.getInt("orders.assassination.priest-respawn-hours", 12),
                cfg.getInt("orders.sabotage.delay-hours", 6),
                cfg.getInt("orders.recon.ttl-hours", 48),
                cfg.getDouble("orders.exposure.recon-chance", 0.15),
                cfg.getDouble("orders.exposure.sabotage-chance", 0.35),
                cfg.getDouble("orders.exposure.failed-raid-chance", 0.5),
                cfg.getInt("orders.stash.seed-ingredients-per-recipe", 2),
                cfg.getInt("orders.favor.ingredients-per-claim", 2),
                cfg.getInt("orders.rejoin-cooldown-days", 3),
                cfg.getInt("orders.talisman.reissue-cooldown-minutes", 30));
    }
}
