package me.vangoo.application.services.context;

import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.effect.*;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class VisualEffectsContext implements IVisualEffectsContext {

    /** Період перемальовування нерухомих міток/слідів — пилинка живе приблизно стільки ж. */
    private static final long MARK_REDRAW_PERIOD = 10L;
    private static final double TRAIL_STEP = 0.7;
    private static final int MAX_TRAIL_POINTS = 80;

    private final EffectManager effectManager;
    private final MysteriesAbovePlugin plugin;

    public VisualEffectsContext(EffectManager effectManager, MysteriesAbovePlugin plugin) {
        this.effectManager = effectManager;
        this.plugin = plugin;
    }

    @Override
    public void playSound(Location loc, org.bukkit.Sound sound, float volume, float pitch) {
        if (loc == null || loc.getWorld() == null || sound == null) return;
        loc.getWorld().playSound(loc, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSoundForPlayer(UUID playerId, Sound sound, float volume, float pitch) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || sound == null) return;

        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void spawnParticle(Particle type, Location loc, int count) {
        if (loc.getWorld() == null) return;
        Class<?> dataType = type.getDataType();
        if (dataType == Float.class) {
            loc.getWorld().spawnParticle(type, loc, count, 0.0f);
        } else if (dataType == Integer.class) {
            loc.getWorld().spawnParticle(type, loc, count, 0);
        } else {
            // Standard particles
            try {
                loc.getWorld().spawnParticle(type, loc, count);
            } catch (Exception ignored) {
                // Failsafe for complex types like Redstone/Item/Block that need explicit data
            }
        }
    }

    @Override
    public void spawnParticle(Particle type, Location loc, int count, double offsetX, double offsetY, double offsetZ) {
        if (loc.getWorld() == null) return;

        Class<?> dataType = type.getDataType();

        if (dataType == Float.class) {
            loc.getWorld().spawnParticle(type, loc, count, offsetX, offsetY, offsetZ, 0, 0.0f);
        } else if (dataType == Integer.class) {
            loc.getWorld().spawnParticle(type, loc, count, offsetX, offsetY, offsetZ, 0, 0);
        } else {
            // Standard particles
            try {
                loc.getWorld().spawnParticle(type, loc, count, offsetX, offsetY, offsetZ);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void spawnParticleForPlayer(UUID receiverId, Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ) {
        Player player = Bukkit.getPlayer(receiverId);

        // Перевіряємо, чи гравець онлайн, інакше нема кому показувати
        if (player != null && player.isOnline()) {
            // Останній параметр (0.0) - це "extra" (швидкість частинок).
            // Для більшості ефектів 0 підходить ідеально, щоб вони не розліталися.
            player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, 0.0);
        }
    }

    @Override
    public void playSphereEffect(Location location, double radius, Particle particle, int durationTicks) {
        SphereEffect effect = new SphereEffect(effectManager);
        effect.setLocation(location);
        effect.radius = (float) radius;
        effect.particle = particle;
        effect.particles = 50;
        effect.iterations = durationTicks;
        effect.period = 1;
        effect.start();
    }

    @Override
    public void playHelixEffect(Location start, Location end, Particle particle, int durationTicks) {
        HelixEffect effect = new HelixEffect(effectManager);
        effect.setLocation(start);
        effect.setTarget(end);
        effect.particle = particle;
        effect.strands = 3;
        effect.radius = 0.5f;
        effect.curve = 10;
        effect.rotation = Math.PI / 4;
        effect.iterations = durationTicks;
        effect.period = 1;
        effect.start();
    }

    @Override
    public void playCircleEffect(Location location, double radius, Particle particle, int durationTicks) {
        CircleEffect effect = new CircleEffect(effectManager);
        effect.setLocation(location);
        effect.radius = (float) radius;
        effect.particle = particle;
        effect.particles = 30;
        effect.iterations = durationTicks;
        effect.period = 1;
        effect.start();
    }

    @Override
    public void playLineEffect(Location start, Location end, Particle particle) {
        LineEffect effect = new LineEffect(effectManager);
        effect.setLocation(start);
        effect.setTarget(end);
        effect.particle = particle;
        effect.particles = 20;
        effect.start();
    }

    @Override
    public void playConeEffect(Location apex, Vector direction, double angle, double length, Particle particle, int durationTicks) {
        ConeEffect effect = new ConeEffect(effectManager);
        effect.setLocation(apex);
        effect.particle = particle;
        effect.lengthGrow = (float) (length / durationTicks);
        effect.radiusGrow = (float) (Math.tan(Math.toRadians(angle / 2)) * length / durationTicks);
        effect.particles = 30;
        effect.iterations = durationTicks;
        effect.period = 1;

        Location target = apex.clone().add(direction.normalize().multiply(length));
        effect.setTarget(target);

        effect.start();
    }

    @Override
    public void playVortexEffect(Location location, double height, double radius, Particle particle, int durationTicks) {
        VortexEffect effect = new VortexEffect(effectManager);
        effect.setLocation(location);
        effect.particle = particle;
        effect.radius = (float) radius;
        effect.grow = (float) height / durationTicks;
        effect.radials = 0.1f;
        effect.circles = 10;
        effect.helixes = 3;
        effect.iterations = durationTicks;
        effect.period = 1;
        effect.start();
    }

    @Override
    public void playWaveEffect(Location center, double radius, Particle particle, int durationTicks) {
        CircleEffect effect = new CircleEffect(effectManager);
        effect.setLocation(center);
        effect.particle = particle;
        effect.particles = 40;
        effect.iterations = durationTicks;
        effect.period = 1;

        final double radiusPerTick = radius / durationTicks;

        // EffectLib запускає своє завдання, а ми запускаємо своє для зміни радіуса
        effect.start();

        new BukkitRunnable() {
            int currentIteration = 0;

            @Override
            public void run() {
                if (currentIteration++ >= durationTicks) {
                    this.cancel();
                    return;
                }
                // Динамічно змінюємо параметр ефекту EffectLib
                effect.radius = (float) (radiusPerTick * currentIteration);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playCubeEffect(Location location, double size, Particle particle, int durationTicks) {
        CubeEffect effect = new CubeEffect(effectManager);
        effect.setLocation(location);
        effect.particle = particle;
        effect.edgeLength = (float) size;
        effect.particles = 8;
        effect.iterations = durationTicks;
        effect.period = 1;
        effect.start();
    }

    @Override
    public void playOrbitingMotes(UUID entityId, List<Color> colors, double radius, int durationTicks) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null || colors == null || colors.isEmpty()) return;

        final int motes = Math.max(3, colors.size() * 2);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= durationTicks || !entity.isValid()) {
                    this.cancel();
                    return;
                }
                Location base = entity.getLocation().add(0, 1.0, 0);
                World world = base.getWorld();
                if (world == null) {
                    this.cancel();
                    return;
                }
                double spin = ticks * 0.25;
                for (int i = 0; i < motes; i++) {
                    double angle = spin + 2 * Math.PI * i / motes;
                    double y = Math.sin(spin + i) * 0.5;
                    Location p = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(colors.get(i % colors.size()), 1.0f));
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @Override
    public void playTrailEffect(UUID entityId, Particle particle, int durationTicks) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null) return;

        new BukkitRunnable() {
            int ticksRemaining = durationTicks;

            @Override
            public void run() {
                // Перевіряємо, чи валідна сутність та чи не сплив час
                if (ticksRemaining <= 0 || !entity.isValid()) {
                    this.cancel();
                    return;
                }

                Location loc = entity.getLocation().add(0, 0.5, 0);
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(particle, loc, 3, 0.2, 0.2, 0.2, 0);
                }

                // Зменшуємо лічильник на період таймера (2 тіки)
                ticksRemaining -= 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @Override
    public void playBeamEffect(Location start, Location end, Particle particle, double width, int durationTicks) {
        LineEffect effect = new LineEffect(effectManager);
        effect.setLocation(start);
        effect.setTarget(end);
        effect.particle = particle;
        effect.particles = (int) (start.distance(end) * 5);
        effect.iterations = durationTicks;
        effect.period = 1;
        effect.start();
    }

    // Доданий метод без залежності від SchedulingContext
    public void playExplosionRingEffect(Location center, double radius, Particle particle, Particle.DustOptions options) {
        final double radiusStep = radius / 20;

        new BukkitRunnable() {
            double currentRadius = 0.1;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20) {
                    this.cancel();
                    return;
                }

                if (center.getWorld() == null) {
                    this.cancel();
                    return;
                }

                for (int i = 0; i < 50; i++) {
                    double angle = 2 * Math.PI * i / 50;
                    double x = currentRadius * Math.cos(angle);
                    double z = currentRadius * Math.sin(angle);

                    Location particleLoc = center.clone().add(x, 0, z);
                    center.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0, options);
                }

                currentRadius += radiusStep;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    @Override
    public void playTravelingBeam(Location start, Location end, Color color, Runnable onArrival) {
        World world = start.getWorld();
        if (world == null || world != end.getWorld()) {
            if (onArrival != null) onArrival.run();
            return;
        }

        Vector dir = end.toVector().subtract(start.toVector());
        double distance = dir.length();
        if (distance < 0.01) {
            if (onArrival != null) onArrival.run();
            return;
        }
        dir.normalize();

        final Particle.DustOptions core = new Particle.DustOptions(color, 0.6f); // тонке ядро
        final Particle.DustOptions glow = new Particle.DustOptions(Color.fromRGB(255, 250, 235), 0.4f); // м'який білий
        final double speed = 1.2; // блоків/тік — плавний, не миттєвий

        new BukkitRunnable() {
            double traveled = 0;

            @Override
            public void run() {
                double segEnd = Math.min(traveled + speed, distance);
                for (double d = traveled; d <= segEnd; d += 0.25) {
                    Location p = start.clone().add(dir.clone().multiply(d));
                    world.spawnParticle(Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0, core);
                    world.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0, glow);
                }
                traveled = segEnd;
                if (traveled >= distance) {
                    this.cancel();
                    if (onArrival != null) onArrival.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playGlowingDust(Location center, Color color) {
        if (center.getWorld() == null) return;
        final World world = center.getWorld();

        // М'який, але помітний спалах святого світла (нульова швидкість — не розлітається)
        world.spawnParticle(Particle.END_ROD, center, 25, 0.5, 0.5, 0.5, 0.0);
        world.spawnParticle(Particle.DUST, center, 20, 0.5, 0.5, 0.5, 0.0,
                new Particle.DustOptions(Color.fromRGB(255, 250, 235), 1.2f));

        // Одне широке золоте кільце по землі — чіткий орієнтир влучання
        for (int i = 0; i < 24; i++) {
            double a = 2 * Math.PI * i / 24;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * 1.4, 0.1, Math.sin(a) * 1.4),
                    1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.4f));
        }

        // Золотий пил, що повільно дрейфує вгору й згасає
        new BukkitRunnable() {
            int tick = 0;
            final int duration = 45; // ~2.25 с

            @Override
            public void run() {
                if (tick >= duration || center.getWorld() == null) {
                    this.cancel();
                    return;
                }
                int count = Math.max(2, 10 - tick / 5);            // згасання: усе менше пилинок
                float size = 1.6f - (tick / (float) duration) * 0.9f; // усе дрібніші
                Particle.DustOptions dust = new Particle.DustOptions(color, size);

                for (int i = 0; i < count; i++) {
                    double r = 1.3 * Math.random();                // ширша хмара довкола
                    double a = Math.random() * 2 * Math.PI;
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    double y = 0.3 + tick * 0.03;                  // повільний дрейф угору
                    world.spawnParticle(Particle.DUST, center.clone().add(x, y, z),
                            1, 0.05, 0.05, 0.05, 0, dust);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playDustMark(Location center, Color color, double spread, float size, int count,
                             int durationTicks) {
        World world = center.getWorld();
        if (world == null || color == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        if (durationTicks <= 0) {
            world.spawnParticle(Particle.DUST, center, count, spread, spread, spread, 0, dust);
            return;
        }
        // Пилинка живе ~1 с, тож «висіти» мітка може лише через перемальовування.
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick > durationTicks) {
                    this.cancel();
                    return;
                }
                world.spawnParticle(Particle.DUST, center, count, spread, spread, spread, 0, dust);
                tick += MARK_REDRAW_PERIOD;
            }
        }.runTaskTimer(plugin, 0L, MARK_REDRAW_PERIOD);
    }

    @Override
    public void playGroundTrail(Location from, Location to, Color color, int durationTicks) {
        World world = from.getWorld();
        if (world == null || color == null || world != to.getWorld()) return;

        Vector step = to.toVector().subtract(from.toVector());
        double distance = step.length();
        if (distance < 1.0) return;
        int points = Math.max(1, Math.min(MAX_TRAIL_POINTS, (int) (distance / TRAIL_STEP)));
        step.multiply(1.0 / points);

        // Прив'язка до землі дорога (пошук блоків), а слід нерухомий — рахуємо один раз.
        List<Location> path = new ArrayList<>();
        for (int i = 1; i <= points; i++) {
            path.add(groundUnder(from.clone().add(step.clone().multiply(i))));
        }

        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick > durationTicks) {
                    this.cancel();
                    return;
                }
                for (Location point : path) {
                    world.spawnParticle(Particle.DUST, point, 2, 0.08, 0.02, 0.08, 0, dust);
                }
                tick += MARK_REDRAW_PERIOD;
            }
        }.runTaskTimer(plugin, 0L, MARK_REDRAW_PERIOD);
    }

    @Override
    public void playDustMarkFor(UUID viewerId, Location center, Color color, double spread,
                                float size, int count, int durationTicks) {
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || color == null || center.getWorld() == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        if (durationTicks <= 0) {
            viewer.spawnParticle(Particle.DUST, center, count, spread, spread, spread, 0, dust);
            return;
        }
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(viewerId);
                if (p == null || !p.isOnline() || tick > durationTicks) {
                    this.cancel();
                    return;
                }
                p.spawnParticle(Particle.DUST, center, count, spread, spread, spread, 0, dust);
                tick += MARK_REDRAW_PERIOD;
            }
        }.runTaskTimer(plugin, 0L, MARK_REDRAW_PERIOD);
    }

    @Override
    public void playGroundTrailFor(UUID viewerId, Location from, Location to, Color color,
                                   int durationTicks) {
        World world = from.getWorld();
        if (world == null || color == null || world != to.getWorld()) return;
        if (Bukkit.getPlayer(viewerId) == null) return;

        Vector step = to.toVector().subtract(from.toVector());
        double distance = step.length();
        if (distance < 1.0) return;
        int points = Math.max(1, Math.min(MAX_TRAIL_POINTS, (int) (distance / TRAIL_STEP)));
        step.multiply(1.0 / points);

        List<Location> path = new ArrayList<>();
        for (int i = 1; i <= points; i++) {
            path.add(groundUnder(from.clone().add(step.clone().multiply(i))));
        }

        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(viewerId);
                if (p == null || !p.isOnline() || tick > durationTicks) {
                    this.cancel();
                    return;
                }
                for (Location point : path) {
                    p.spawnParticle(Particle.DUST, point, 2, 0.08, 0.02, 0.08, 0, dust);
                }
                tick += MARK_REDRAW_PERIOD;
            }
        }.runTaskTimer(plugin, 0L, MARK_REDRAW_PERIOD);
    }

    /** Поверхня під точкою (до 6 блоків униз), щоб слід лежав по землі, а не висів у повітрі. */
    private Location groundUnder(Location point) {
        Location probe = point.clone();
        for (int i = 0; i < 6; i++) {
            if (probe.getBlock().getType().isSolid()) return probe.add(0, 1.15, 0);
            probe.add(0, -1, 0);
        }
        return point.clone().add(0, 0.15, 0);
    }

    @Override
    public void playRisingSpiral(Location base, double height, double radius,
                                 Color color, int durationTicks) {
        final World world = base.getWorld();
        if (world == null) return;
        final Location center = base.clone();

        new BukkitRunnable() {
            int tick = 0;
            double phase = 0;

            @Override
            public void run() {
                if (tick++ >= durationTicks || center.getWorld() == null) {
                    this.cancel();
                    return;
                }
                phase = (phase + 0.06) % 1.0; // «повзання» вгору
                drawSpiralFrame(world, center, height, radius, color, phase);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawSpiralFrame(World world, Location base, double height, double radius,
                                 Color color, double phaseFraction) {
        final int points = 14;    // пилинок у спіралі на кадр
        final double turns = 2.0; // витків по висоті стовпа
        final Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);

        for (int i = 0; i < points; i++) {
            // фаза зсуває висоту кожної пилинки — так вони «повзуть» угору й обертаються в низ
            double frac = ((double) i / points + phaseFraction) % 1.0;
            double y = frac * height;
            double angle = frac * turns * 2 * Math.PI;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            world.spawnParticle(Particle.DUST, base.clone().add(x, y, z), 1, 0, 0, 0, 0, dust);
        }

        // Другий шар: золота іскра END_ROD, що злітає вгору вздовж спіралі
        double sparkAngle = phaseFraction * turns * 2 * Math.PI;
        double sx = Math.cos(sparkAngle) * radius;
        double sz = Math.sin(sparkAngle) * radius;
        world.spawnParticle(Particle.END_ROD,
                base.clone().add(sx, height * 0.5, sz), 1, 0.02, 0.05, 0.02, 0.01);
    }

    @Override
    public void playFadingAura(Location base, Color color, int durationTicks) {
        final World world = base.getWorld();
        if (world == null) return;
        final Location center = base.clone();

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= durationTicks || center.getWorld() == null) {
                    this.cancel();
                    return;
                }
                float progress = tick / (float) durationTicks;
                float size = Math.max(0.5f, 1.4f - progress);   // згасає: пилинки дрібнішають
                int count = Math.max(3, 12 - tick);             // ... і рідшають
                final double radius = 0.9;
                final Particle.DustOptions dust = new Particle.DustOptions(color, size);

                for (int i = 0; i < count; i++) {
                    double theta = Math.random() * 2 * Math.PI;
                    double y = Math.random() * 2.0;             // по всій висоті тіла
                    double x = Math.cos(theta) * radius;
                    double z = Math.sin(theta) * radius;
                    world.spawnParticle(Particle.DUST, center.clone().add(x, y, z), 1, 0, 0, 0, 0, dust);
                }
                // Другий шар: рідкі золоті іскри END_ROD
                if (tick % 2 == 0) {
                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.0, 0),
                            2, radius * 0.6, 0.6, radius * 0.6, 0.0);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playPillarEffect(Location base, double height, double radius,
                                 Color color, int durationTicks) {
        final World world = base.getWorld();
        if (world == null) return;
        final Location origin = base.clone();
        final Particle.DustOptions core = new Particle.DustOptions(color, 1.6f);
        final int levels = 14;
        final int sparks = 6;

        new BukkitRunnable() {
            int tick = 0;
            double phase = 0;

            @Override
            public void run() {
                if (tick >= durationTicks || origin.getWorld() == null) {
                    this.cancel();
                    return;
                }
                phase += 0.2;

                // Об'ємне ядро: два кільця точок на кожному рівні висоти, що обертаються
                for (int lvl = 0; lvl < levels; lvl++) {
                    double y = height * lvl / (double) (levels - 1);
                    for (int ring = 0; ring < 2; ring++) {
                        double ringRadius = radius * (ring == 0 ? 0.3 : 0.7);
                        int points = ring == 0 ? 5 : 7;
                        for (int i = 0; i < points; i++) {
                            double angle = phase * (ring == 0 ? 1 : -1) + (2 * Math.PI * i / points);
                            double x = Math.cos(angle) * ringRadius;
                            double z = Math.sin(angle) * ringRadius;
                            world.spawnParticle(Particle.DUST, origin.clone().add(x, y, z), 1, 0, 0, 0, 0, core);
                        }
                    }
                }

                // Висхідні іскри вздовж зовнішнього краю для руху й об'єму
                for (int i = 0; i < sparks; i++) {
                    double angle = -phase * 1.5 + (2 * Math.PI * i / sparks);
                    double y = ((tick * 0.6) + i * (height / (double) sparks)) % height;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    world.spawnParticle(Particle.END_ROD, origin.clone().add(x, y, z), 1, 0.02, 0.02, 0.02, 0.01);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playAlertHalo(Location location, Color color) {
        if (location == null || location.getWorld() == null) return;

        // Налаштування "чорнил" для частинок
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);

        // Центр над головою
        Location center = location.clone().add(0, 2.2, 0);
        double radius = 0.4; // Компактний радіус

        // Малюємо коло
        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // Основне кільце
            location.getWorld().spawnParticle(
                    Particle.DUST,
                    center.clone().add(x, 0, z),
                    1, // Кількість 1
                    0, 0, 0, // Offset 0 (щоб не розлітались)
                    0, // Speed 0 (щоб стояли на місці)
                    dustOptions
            );

            // Друге кільце трохи вище і ширше (ефект пульсації)
            if (i % 2 == 0) { // Менша щільність
                location.getWorld().spawnParticle(
                        Particle.DUST,
                        center.clone().add(x * 1.2, 0.2, z * 1.2),
                        1, 0, 0, 0, 0, dustOptions
                );
            }
        }
    }

    @Override
    public BukkitTask playPersistentHalo(UUID entityId, Color color) {
        final Particle.DustOptions ring = new Particle.DustOptions(color, 0.65f);
        final Particle.DustOptions glow = new Particle.DustOptions(Color.fromRGB(255, 250, 220), 0.45f);
        final int points = 20;
        final double radius = 0.25;

        return new BukkitRunnable() {
            double phase = 0;

            @Override
            public void run() {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity == null || !entity.isValid()) {
                    this.cancel();
                    return;
                }
                World world = entity.getWorld();
                if (world == null) return;
                Location center = entity.getLocation().add(0, 2, 0);
                phase = (phase + 0.03) % 1.0;

                for (int i = 0; i < points; i++) {
                    double angle = phase * 2 * Math.PI + 2 * Math.PI * i / points;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    world.spawnParticle(Particle.DUST, center.clone().add(x, 0, z), 1, 0, 0, 0, 0, ring);
                }

                // Другий шар: дві протифазні іскри для об'єму й мерехтіння
                double sparkAngle = phase * 4 * Math.PI;
                world.spawnParticle(Particle.DUST,
                        center.clone().add(Math.cos(sparkAngle) * radius * 0.5, 0.12, Math.sin(sparkAngle) * radius * 0.5),
                        1, 0, 0, 0, 0, glow);
                world.spawnParticle(Particle.DUST,
                        center.clone().add(Math.cos(sparkAngle + Math.PI) * radius * 0.5, -0.12, Math.sin(sparkAngle + Math.PI) * radius * 0.5),
                        1, 0, 0, 0, 0, glow);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @Override
    public void playScriptureAura(Location center, Color color, int durationTicks) {
        final World world = center.getWorld();
        if (world == null || color == null) return; // null колір → DUST кидає щотіка й таск спамить вічно
        final Location origin = center.clone();
        final Particle.DustOptions glyph = new Particle.DustOptions(color, 1.1f);
        final int glyphs = 6;        // упорядковані знаки, а не хмара
        final double radius = 1.0;

        new BukkitRunnable() {
            int tick = 0;
            double phase = 0;

            @Override
            public void run() {
                if (tick >= durationTicks || origin.getWorld() == null) {
                    this.cancel();
                    return;
                }
                phase += 0.12;
                double y = 0.4 + (tick / (double) durationTicks) * 1.6; // повільно піднімаються
                for (int i = 0; i < glyphs; i++) {
                    double angle = phase + (2 * Math.PI * i / glyphs);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location p = origin.clone().add(x, y, z);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, glyph);
                    // «письмо»: мерехтіння чарівних символів на кожному знаку
                    world.spawnParticle(Particle.ENCHANT, p, 2, 0.05, 0.05, 0.05, 0.0);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playDescendingSunPillar(Location target, Color color) {
        final World world = target.getWorld();
        if (world == null || color == null) return; // null колір → DUST кидає щотіка й таск спамить вічно
        final Location ground = target.clone();
        final Particle.DustOptions core = new Particle.DustOptions(color, 1.6f);
        final double height = 12.0;
        final double radius = 0.8;
        final int descendTicks = 16;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick > descendTicks || ground.getWorld() == null) {
                    this.cancel();
                    return;
                }
                // Голова стовпа падає згори вниз
                double headY = height * (1.0 - tick / (double) descendTicks);
                for (int ring = 0; ring < 2; ring++) {
                    double r = radius * (ring == 0 ? 0.4 : 1.0);
                    int points = ring == 0 ? 5 : 8;
                    for (int i = 0; i < points; i++) {
                        double angle = 2 * Math.PI * i / points + tick * 0.3;
                        double x = Math.cos(angle) * r;
                        double z = Math.sin(angle) * r;
                        world.spawnParticle(Particle.DUST, ground.clone().add(x, headY, z), 1, 0, 0, 0, 0, core);
                    }
                }
                // Тонкий згасаючий слід нижче голови
                world.spawnParticle(Particle.END_ROD, ground.clone().add(0, headY, 0), 2, 0.15, 0.4, 0.15, 0.0);

                if (tick == descendTicks) {
                    world.spawnParticle(Particle.EXPLOSION, ground, 1); // НЕ FLASH: на 1.21.11 вимагає data org.bukkit.Color
                    world.spawnParticle(Particle.END_ROD, ground.clone().add(0, 0.3, 0), 30, 0.6, 0.2, 0.6, 0.05);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playBrokenSunDisc(UUID entityId, Color color, int durationTicks) {
        if (color == null) return; // null колір → DUST кидає щотіка й таск спамить вічно
        final Particle.DustOptions disc = new Particle.DustOptions(color, 0.8f);
        final int points = 22;
        final double radius = 0.35;
        final int gapStart = 7;   // розрив у кільці — диск «зламаний»
        final int gapEnd = 12;

        new BukkitRunnable() {
            int tick = 0;
            double wobble = 0;

            @Override
            public void run() {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity == null || !entity.isValid() || tick >= durationTicks) {
                    this.cancel();
                    return;
                }
                World world = entity.getWorld();
                if (world != null) {
                    wobble += 0.15;
                    double tilt = Math.sin(wobble) * 0.12; // диск похитується
                    Location center = entity.getLocation().add(0, 2.2, 0);
                    for (int i = 0; i < points; i++) {
                        if (i >= gapStart && i <= gapEnd) continue; // тріщина
                        double angle = 2 * Math.PI * i / points;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        double y = tilt * Math.sin(angle);
                        world.spawnParticle(Particle.DUST, center.clone().add(x, y, z), 1, 0, 0, 0, 0, disc);
                    }
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @Override
    public void playHolyLightning(Location location) {
        playLightningBolt(location, Color.fromRGB(255, 215, 0));
        World world = location.getWorld();
        if (world != null) {
            world.playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.6f); // церемоніальний акцент кари
        }
    }

    @Override
    public void playLightningBolt(Location location, Color color) {
        final World world = location.getWorld();
        if (world == null) return;
        world.strikeLightningEffect(location); // лише візуал: без вогню й шкоди
        world.spawnParticle(Particle.EXPLOSION, location.clone().add(0, 1, 0), 1); // НЕ FLASH: на 1.21.11 вимагає data org.bukkit.Color
        world.spawnParticle(Particle.END_ROD, location.clone().add(0, 1, 0), 40, 0.3, 1.2, 0.3, 0.05);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        world.spawnParticle(Particle.DUST, location.clone().add(0, 1, 0), 30, 0.5, 1.0, 0.5, 0, dust);
        world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.5f);
    }

    @Override
    public void playSurgingWave(Location origin, Vector direction, double length,
                                double width, Color color, int durationTicks) {
        World world = origin.getWorld();
        if (world == null || direction.lengthSquared() < 1e-6 || durationTicks <= 0) return;

        final Vector forward = direction.clone().setY(0).normalize();
        // перпендикуляр у горизонтальній площині — ширина завіси
        final Vector side = new Vector(-forward.getZ(), 0, forward.getX());
        final Particle.DustOptions crest = new Particle.DustOptions(color, 1.4f);
        final Particle.DustOptions foam = new Particle.DustOptions(Color.fromRGB(235, 245, 255), 0.9f);
        final double step = length / durationTicks;
        final int columns = Math.max(3, (int) Math.round(width * 2));

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick++ >= durationTicks) {
                    this.cancel();
                    return;
                }
                Location front = origin.clone().add(forward.clone().multiply(step * tick));
                for (int i = 0; i <= columns; i++) {
                    double offset = (i / (double) columns - 0.5) * width;
                    Location base = front.clone().add(side.clone().multiply(offset));
                    // вертикальна завіса: гребінь угорі, піна/бризки внизу
                    world.spawnParticle(Particle.DUST, base.clone().add(0, 1.4, 0), 1, 0.05, 0.15, 0.05, 0, crest);
                    world.spawnParticle(Particle.DUST, base.clone().add(0, 0.7, 0), 1, 0.05, 0.15, 0.05, 0, crest);
                    world.spawnParticle(Particle.SPLASH, base.clone().add(0, 0.2, 0), 2, 0.1, 0.1, 0.1, 0);
                    world.spawnParticle(Particle.DUST, base.clone().add(0, 0.1, 0), 1, 0.1, 0.05, 0.1, 0, foam);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playStandingCurtain(Location center, Vector facing, double width, double height,
                                    Color color, int durationTicks) {
        World world = center.getWorld();
        if (world == null || facing.lengthSquared() < 1e-6 || durationTicks <= 0) return;

        final Vector forward = facing.clone().setY(0).normalize();
        final Vector side = new Vector(-forward.getZ(), 0, forward.getX());
        final Particle.DustOptions body = new Particle.DustOptions(color, 1.3f);
        final Particle.DustOptions foam = new Particle.DustOptions(Color.fromRGB(235, 245, 255), 0.8f);
        final Location origin = center.clone();
        final int columns = Math.max(4, (int) Math.round(width * 2));
        final int levels = Math.max(3, (int) Math.round(height * 2));

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick++ >= durationTicks) {
                    this.cancel();
                    return;
                }
                double phase = tick * 0.35;
                for (int i = 0; i <= columns; i++) {
                    double offset = (i / (double) columns - 0.5) * width;
                    // брижі: колона хитається вперед-назад по фазі, тож завіса «тече»
                    double ripple = Math.sin(phase + offset * 1.2) * 0.18;
                    Location base = origin.clone()
                            .add(side.clone().multiply(offset))
                            .add(forward.clone().multiply(ripple));
                    for (int lvl = 0; lvl <= levels; lvl++) {
                        double y = height * lvl / (double) levels;
                        world.spawnParticle(Particle.DUST, base.clone().add(0, y, 0),
                                1, 0.04, 0.06, 0.04, 0, body);
                    }
                    world.spawnParticle(Particle.DUST, base.clone().add(0, 0.05, 0),
                            1, 0.1, 0.05, 0.1, 0, foam);
                    if (tick % 3 == 0) {
                        world.spawnParticle(Particle.SPLASH, base.clone().add(0, height * 0.5, 0),
                                1, 0.1, 0.3, 0.1, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playUnderworldGate(Location center, Vector facing, double width, double height,
                                   Color color, int durationTicks) {
        World world = center.getWorld();
        if (world == null || facing.lengthSquared() < 1e-6 || durationTicks <= 0) return;

        final Vector forward = facing.clone().setY(0).normalize();
        final Vector side = new Vector(-forward.getZ(), 0, forward.getX());
        final Particle.DustOptions frame = new Particle.DustOptions(color, 1.4f);
        final Particle.DustOptions ring = new Particle.DustOptions(color, 0.9f);
        final Location origin = center.clone();
        final int columns = Math.max(6, (int) Math.round(width * 3));
        final int levels = Math.max(6, (int) Math.round(height * 3));
        final double ringRadius = Math.min(width, height) / 2.0;
        final int ringCount = 3;
        final int ringPeriodTicks = 10;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick++ >= durationTicks) {
                    this.cancel();
                    return;
                }

                // Рамка — контур прямокутника (верх/низ + боки).
                for (int i = 0; i <= columns; i++) {
                    double x = (i / (double) columns - 0.5) * width;
                    Location column = origin.clone().add(side.clone().multiply(x));
                    world.spawnParticle(Particle.DUST, column, 1, 0, 0, 0, 0, frame);
                    world.spawnParticle(Particle.DUST, column.clone().add(0, height, 0), 1, 0, 0, 0, 0, frame);
                }
                for (int lvl = 0; lvl <= levels; lvl++) {
                    double y = height * lvl / (double) levels;
                    world.spawnParticle(Particle.DUST,
                            origin.clone().add(side.clone().multiply(-width / 2)).add(0, y, 0),
                            1, 0, 0, 0, 0, frame);
                    world.spawnParticle(Particle.DUST,
                            origin.clone().add(side.clone().multiply(width / 2)).add(0, y, 0),
                            1, 0, 0, 0, 0, frame);
                }

                // Темна утроба — розсіяний дим/чорнило, не суцільна заливка.
                Location core = origin.clone().add(0, height / 2.0, 0);
                world.spawnParticle(Particle.SQUID_INK, core, 3, width * 0.3, height * 0.3, 0.05, 0);
                world.spawnParticle(Particle.SMOKE, core, 2, width * 0.25, height * 0.25, 0.05, 0.01);

                // Кільця всмоктування — стягуються до центру щопівсекунди, зі зсувом фази.
                for (int r = 0; r < ringCount; r++) {
                    double phase = ((tick + r * ringPeriodTicks / ringCount) % ringPeriodTicks)
                            / (double) ringPeriodTicks;
                    double radius = (1.0 - phase) * ringRadius;
                    for (int a = 0; a < 12; a++) {
                        double angle = Math.PI * 2 * a / 12;
                        Location point = core.clone()
                                .add(side.clone().multiply(Math.cos(angle) * radius))
                                .add(0, Math.sin(angle) * radius, 0);
                        world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, ring);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playGraspingHands(Location target, Color color, int durationTicks) {
        final World world = target.getWorld();
        if (world == null) return;
        final Location center = target.clone();
        final int hands = 4;
        final int armSegments = 4;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= durationTicks || center.getWorld() == null) {
                    this.cancel();
                    return;
                }
                // Пульс стискання: долоні то сходяться до цілі, то трохи попускають.
                double squeeze = 0.35 + 0.65 * Math.abs(Math.cos(tick * 0.12));
                double radius = 1.1 * squeeze;
                final Particle.DustOptions dust = new Particle.DustOptions(color, 1.1f);

                for (int h = 0; h < hands; h++) {
                    double angle = (2 * Math.PI / hands) * h + tick * 0.02; // повільне обертання
                    // Передпліччя: тягнеться з-під землі вгору, нахиляючись до цілі.
                    for (int seg = 0; seg <= armSegments; seg++) {
                        double frac = (double) seg / armSegments;
                        double r = radius * (1.0 - frac * 0.45);
                        world.spawnParticle(Particle.DUST,
                                center.clone().add(Math.cos(angle) * r, frac * 1.3, Math.sin(angle) * r),
                                1, 0, 0, 0, 0, dust);
                    }
                    // Другий шар: пальці — душевні іскри на вершині, зігнуті всередину.
                    double fingerRadius = radius * 0.55;
                    for (int finger = -1; finger <= 1; finger++) {
                        double fingerAngle = angle + finger * 0.22;
                        world.spawnParticle(Particle.SCULK_SOUL,
                                center.clone().add(Math.cos(fingerAngle) * fingerRadius, 1.35,
                                        Math.sin(fingerAngle) * fingerRadius),
                                1, 0.02, 0.02, 0.02, 0.0);
                    }
                }

                // Третій шар: кільце на землі — ціль тримає не лише руки, а й сама земля.
                if (tick % 4 == 0) {
                    final int ringPoints = 10;
                    for (int i = 0; i < ringPoints; i++) {
                        double a = (2 * Math.PI / ringPoints) * i;
                        world.spawnParticle(Particle.DUST,
                                center.clone().add(Math.cos(a) * 1.2, 0.1, Math.sin(a) * 1.2),
                                1, 0, 0, 0, 0, dust);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void playPhantomBlocks(UUID viewerId, Location center, Material material,
                                  int count, double radius, int durationTicks) {
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || material == null || center.getWorld() == null) return;
        if (!center.getWorld().equals(viewer.getWorld())) return;

        final org.bukkit.block.data.BlockData fake = material.createBlockData();
        final List<Location> faked = new ArrayList<>();
        final int r = (int) Math.ceil(radius);

        // ponytail: випадкові проби замість повного сканування куба — досить для ілюзії
        for (int attempt = 0; attempt < count * 20 && faked.size() < count; attempt++) {
            Location loc = center.clone().add(
                    ThreadLocalRandom.current().nextInt(-r, r + 1),
                    ThreadLocalRandom.current().nextInt(-r, r + 1),
                    ThreadLocalRandom.current().nextInt(-r, r + 1));
            // підміняємо лише порожнечу і ніколи біля самого глядача (жодного задушення в ілюзії)
            if (!loc.getBlock().getType().isAir()) continue;
            if (loc.distanceSquared(viewer.getLocation()) < 4.0) continue;

            faked.add(loc);
            viewer.sendBlockChange(loc, fake);
        }
        if (faked.isEmpty()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(viewerId);
            // після реконекту клієнт і так отримує справжні чанки — повертати нема чого
            if (p == null || !p.isOnline()) return;
            faked.forEach(loc -> p.sendBlockChange(loc, loc.getBlock().getBlockData()));
        }, durationTicks);
    }

    @Override
    public void playWardingShell(UUID entityId, Color color, double radius, int durationTicks) {
        if (color == null) return;
        final Particle.DustOptions shell = new Particle.DustOptions(color, 0.9f);
        final Particle.DustOptions floor = new Particle.DustOptions(color, 1.3f);
        final int rings = 5;
        final int pointsPerRing = 12;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                Entity entity = Bukkit.getEntity(entityId);
                if (ticks >= durationTicks || entity == null || !entity.isValid()) {
                    this.cancel();
                    return;
                }
                World world = entity.getWorld();
                Location center = entity.getLocation().add(0, 1.0, 0);
                double spin = ticks * 0.12;

                // Паралелі купола: від пояса вниз (-60°) до маківки (+90°)
                for (int r = 0; r <= rings; r++) {
                    double lat = -Math.PI / 3 + (Math.PI / 2 + Math.PI / 3) * r / rings;
                    double y = Math.sin(lat) * radius;
                    double ringRadius = Math.cos(lat) * radius;
                    int points = Math.max(4, (int) Math.round(pointsPerRing * Math.cos(lat)));
                    for (int i = 0; i < points; i++) {
                        double angle = spin + 2 * Math.PI * i / points;
                        world.spawnParticle(Particle.DUST,
                                center.clone().add(Math.cos(angle) * ringRadius, y, Math.sin(angle) * ringRadius),
                                1, 0, 0, 0, 0, shell);
                    }
                }

                // Другий шар: щільніше кільце по землі — оболонка «стоїть», а не висить
                Location base = entity.getLocation().add(0, 0.1, 0);
                for (int i = 0; i < pointsPerRing; i++) {
                    double angle = -spin + 2 * Math.PI * i / pointsPerRing;
                    world.spawnParticle(Particle.DUST,
                            base.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                            1, 0, 0, 0, 0, floor);
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @Override
    public void playSoulWisp(UUID viewerId, Location base, Color color) {
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || !viewer.isOnline() || base == null || base.getWorld() == null) return;
        if (!viewer.getWorld().equals(base.getWorld())) return;

        // Стовпчик душі в людський зріст — читається як силует, а не як хмарка
        for (int step = 0; step < 6; step++) {
            viewer.spawnParticle(Particle.SOUL, base.clone().add(0, 0.25 + step * 0.28, 0),
                    1, 0.09, 0.05, 0.09, 0.0);
        }

        // Німб над силуетом: другий шар, кольором шляху
        Particle.DustOptions halo = new Particle.DustOptions(color, 0.9f);
        Location crown = base.clone().add(0, 1.95, 0);
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            viewer.spawnParticle(Particle.DUST,
                    crown.clone().add(Math.cos(angle) * 0.3, 0, Math.sin(angle) * 0.3),
                    1, 0, 0, 0, 0, halo);
        }
    }
}