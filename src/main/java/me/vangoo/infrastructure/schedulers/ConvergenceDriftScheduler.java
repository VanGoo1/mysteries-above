package me.vangoo.infrastructure.schedulers;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.PathwayManager;
import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.domain.creatures.ConvergencePull;
import me.vangoo.domain.creatures.ConvergenceSource;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.PullResult;
import me.vangoo.domain.creatures.ResonantBeyonder;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.infrastructure.items.CharacteristicCodec;
import me.vangoo.infrastructure.items.WardenRemnantCodec;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Закон Конвергенції як приховане тяжіння. Періодично, для кожного онлайн-потойбічного, збирає
 * резонансні НЕ-гравцеві джерела в радіусі (впалі Характеристики, рештки, міфічні істоти) і дає
 * кожному дуже слабкий нудж до найближчого кревного Beyonder'а. Кого й наскільки сильно вирішує
 * чисте {@link ConvergencePull}. Гравців ніколи не рухаємо; єдиний прояв — рідкісний тихий звук.
 * Дзеркалить життєвий цикл {@link AmbientCreatureSpawner} (start/stop + один BukkitTask).
 */
public final class ConvergenceDriftScheduler {

    private final MysteriesAbovePlugin plugin;
    private final BeyonderService beyonderService;
    private final PathwayManager pathwayManager;
    private final MythicCreatureGateway gateway;
    private final java.util.Map<String, CreatureDefinition> registry;
    private final CharacteristicCodec characteristicCodec;
    private final WardenRemnantCodec wardenRemnantCodec;
    private final ConvergencePull pull = new ConvergencePull();

    private final long intervalTicks;
    private final double radius;
    private final double itemDriftSpeed;
    private final double mobNudgeChance;
    private final double whisperChance;
    private final Random random = new Random();

    private BukkitTask task;

    public ConvergenceDriftScheduler(MysteriesAbovePlugin plugin, BeyonderService beyonderService,
                                     PathwayManager pathwayManager, MythicCreatureGateway gateway,
                                     java.util.Map<String, CreatureDefinition> registry,
                                     CharacteristicCodec characteristicCodec,
                                     WardenRemnantCodec wardenRemnantCodec,
                                     long intervalTicks, double radius, double itemDriftSpeed,
                                     double mobNudgeChance, double whisperChance) {
        this.plugin = plugin;
        this.beyonderService = beyonderService;
        this.pathwayManager = pathwayManager;
        this.gateway = gateway;
        this.registry = registry;
        this.characteristicCodec = characteristicCodec;
        this.wardenRemnantCodec = wardenRemnantCodec;
        this.intervalTicks = Math.max(20L, intervalTicks);
        this.radius = radius;
        this.itemDriftSpeed = itemDriftSpeed;
        this.mobNudgeChance = mobNudgeChance;
        this.whisperChance = whisperChance;
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
        plugin.getLogger().info("ConvergenceDriftScheduler started");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
            plugin.getLogger().info("ConvergenceDriftScheduler stopped");
        }
    }

    private void tick() {
        List<ResonantBeyonder> magnets = collectMagnets();
        if (magnets.isEmpty()) return;

        Set<UUID> handled = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                driftAround(player, magnets, handled);
            } catch (Exception e) {
                plugin.getLogger().warning("Convergence drift error for " + player.getName() + ": " + e);
            }
        }
    }

    /** Усі онлайн-Beyonder'и як кандидати-магніти. */
    private List<ResonantBeyonder> collectMagnets() {
        List<ResonantBeyonder> magnets = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Beyonder b = beyonderService.getBeyonder(player.getUniqueId());
                if (b == null) continue;
                String pathway = b.getPathway().getName();
                String group = groupOf(pathway);
                if (group == null) continue;
                Location loc = player.getLocation();
                magnets.add(new ResonantBeyonder(player.getUniqueId(), pathway, group,
                        b.getSequenceLevel(), loc.getX(), loc.getZ()));
            } catch (Exception e) {
                plugin.getLogger().warning("Convergence magnet collection error for " + player.getName() + ": " + e);
            }
        }
        return magnets;
    }

    private void driftAround(Player center, List<ResonantBeyonder> magnets, Set<UUID> handled) {
        for (Entity e : center.getNearbyEntities(radius, radius, radius)) {
            if (!handled.add(e.getUniqueId())) continue; // dedup across scan centers

            Optional<ConvergenceSource> source = describeSource(e);
            if (source.isEmpty()) continue;

            Optional<PullResult> result = pull.computePull(source.get(), magnets, radius);
            if (result.isEmpty()) continue;

            Player target = Bukkit.getPlayer(result.get().targetId());
            if (target == null) continue;

            applyNudge(e, target.getLocation(), result.get().strength());
            maybeWhisper(target);
        }
    }

    /** Розпізнає резонансне джерело з сутності; empty якщо не джерело або невідомий шлях. */
    private Optional<ConvergenceSource> describeSource(Entity e) {
        Characteristic c = null;
        if (e instanceof Item item) {
            c = characteristicCodec.read(item.getItemStack()).orElse(null);
        } else if (wardenRemnantCodec.isRemnant(e)) {
            c = wardenRemnantCodec.read(e).orElse(null);
        } else {
            String id = gateway.creatureId(e).orElse(null);
            if (id != null) {
                CreatureDefinition def = registry.get(id);
                if (def != null && def.pathway() != null) {
                    c = new Characteristic(def.pathway(), def.sequence());
                }
            }
        }
        if (c == null || c.pathwayName() == null) return Optional.empty();
        String group = groupOf(c.pathwayName());
        if (group == null) return Optional.empty();
        Location loc = e.getLocation();
        return Optional.of(new ConvergenceSource(c.pathwayName(), group, c.sequence(),
                loc.getX(), loc.getZ()));
    }

    private void applyNudge(Entity e, Location targetLoc, double strength) {
        Vector dir = targetLoc.toVector().subtract(e.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-6) return;
        dir.normalize();

        if (e instanceof Item item) {
            Vector v = item.getVelocity().add(dir.multiply(itemDriftSpeed * strength));
            item.setVelocity(v);
        } else if (e instanceof Mob mob) {
            if (random.nextDouble() < mobNudgeChance * strength) {
                mob.getPathfinder().moveTo(targetLoc);
            }
        }
    }

    private void maybeWhisper(Player target) {
        if (random.nextDouble() >= whisperChance) return;
        target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.AMBIENT, 0.15f, 1.4f);
    }

    private String groupOf(String pathwayName) {
        Pathway p = pathwayManager.getPathway(pathwayName);
        return p == null ? null : p.getGroup().name();
    }
}
