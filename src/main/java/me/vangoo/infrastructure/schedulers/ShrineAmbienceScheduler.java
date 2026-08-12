package me.vangoo.infrastructure.schedulers;

import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import me.vangoo.infrastructure.organizations.ChurchAnchor;
import me.vangoo.infrastructure.organizations.ShrineService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Святині «дихають»: над вівтарем кожного шрайна поблизу гравців стоїть висхідна спіраль
 * кольору його церкви й тонке кільце над нею.
 *
 * <p>Обхід іде від гравців, а не від шрайнів: реєстру шрайнів у плагіна немає й не
 * потрібно — вони живуть мітками у світі, тож видно рівно ті, чиї чанки завантажені.
 *
 * <p>Малює {@link IVisualEffectsContext}, а не сам шедулер (див.
 * `.claude/rules/visual-effects-reuse.md`); тривалість ефектів дорівнює періоду, тож
 * анімації змикаються без пауз і без накладань.
 */
public class ShrineAmbienceScheduler {

    private static final double SPIRAL_HEIGHT = 1.6;
    private static final double SPIRAL_RADIUS = 0.55;
    private static final double RING_RADIUS = 0.8;
    private static final double RING_LIFT = 1.8;
    private static final double PAD_PILLAR_HEIGHT = 3.0;
    private static final double PAD_PILLAR_RADIUS = 0.45;

    private final Plugin plugin;
    private final ShrineService shrines;
    private final IVisualEffectsContext visuals;
    private final double radius;
    private final long periodTicks;

    private BukkitTask task;

    public ShrineAmbienceScheduler(Plugin plugin, ShrineService shrines,
                                   IVisualEffectsContext visuals,
                                   double radius, long periodTicks) {
        this.plugin = plugin;
        this.shrines = shrines;
        this.visuals = visuals;
        this.radius = radius;
        this.periodTicks = periodTicks;
    }

    public void start() {
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        Set<UUID> drawn = new HashSet<>();
        boolean anyoneInChurchWorld = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shrines.isChurchWorld(player.getWorld())) {
                anyoneInChurchWorld = true;
                continue; // у самому храмі святинь немає — там світяться камені-виходи
            }
            for (ChurchAnchor shrine : shrines.shrinesNear(player.getLocation(), radius)) {
                // Двоє гравців біля однієї святині не мають подвоювати ефект.
                if (drawn.add(shrine.marker().getUniqueId())) {
                    draw(shrine);
                }
            }
        }
        if (anyoneInChurchWorld) {
            drawExitPads();
        }
    }

    private void draw(ChurchAnchor shrine) {
        String institutionId = shrines.assign(shrine);
        if (institutionId == null) {
            return;
        }
        Location base = shrine.marker().getLocation();
        Color color = shrines.colorOf(institutionId);
        visuals.playRisingSpiral(base, SPIRAL_HEIGHT, SPIRAL_RADIUS, color, (int) periodTicks);
        visuals.playAuraRing(base.clone().add(0, RING_LIFT, 0), RING_RADIUS, color,
                (int) periodTicks);
    }

    /**
     * Камінь-вихід має бути помітним здалеку: у порожньому світі він губиться серед
     * кам'яної облямівки. Стовп світла бачно через увесь двір, кільце в ногах підказує,
     * що саме клацати. Храмів щонайбільше десяток, тож малюємо всі, доки хтось у світі.
     */
    private void drawExitPads() {
        for (Map.Entry<String, Location> pad : shrines.exitPads().entrySet()) {
            Color color = shrines.colorOf(pad.getKey());
            Location base = pad.getValue().clone().add(0.5, 1.0, 0.5);
            visuals.playPillarEffect(base, PAD_PILLAR_HEIGHT, PAD_PILLAR_RADIUS, color,
                    (int) periodTicks);
            visuals.playAuraRing(base, RING_RADIUS, color, (int) periodTicks);
        }
    }
}
