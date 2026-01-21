package me.vangoo.application.services.context;

import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.effect.*;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

public class VisualEffectsContext implements IVisualEffectsContext {

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
}