package me.vangoo.domain.abilities.context;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.UUID;

public interface IVisualEffectsContext {
    void playSound(Location loc, Sound sound, float volume, float pitch);

    void playSoundForPlayer(UUID playerId, Sound sound, float volume, float pitch);

    void spawnParticle(Particle type, Location loc, int count);

    void spawnParticle(Particle type, Location loc, int count,
                       double offsetX, double offsetY, double offsetZ);


    /**
     * Create a sphere effect at location
     *
     * @param location      Center of sphere
     * @param radius        Radius of sphere
     * @param particle      Particle type to use
     * @param durationTicks How long effect lasts
     */
    void playSphereEffect(Location location, double radius, Particle particle, int durationTicks);

    /**
     * Create a helix/spiral effect between two points
     *
     * @param start         Start location
     * @param end           End location
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playHelixEffect(Location start, Location end, Particle particle, int durationTicks);

    /**
     * Create a circle effect at location
     *
     * @param location      Center of circle
     * @param radius        Radius
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playCircleEffect(Location location, double radius, Particle particle, int durationTicks);

    /**
     * Create a line effect between two points
     *
     * @param start    Start location
     * @param end      End location
     * @param particle Particle type
     */
    void playLineEffect(Location start, Location end, Particle particle);

    /**
     * Create a cone effect (useful for directional abilities)
     *
     * @param apex          Tip of cone
     * @param direction     Direction cone points
     * @param angle         Cone opening angle in degrees
     * @param length        Length of cone
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playConeEffect(Location apex, org.bukkit.util.Vector direction, double angle,
                        double length, Particle particle, int durationTicks);

    /**
     * Create a vortex/tornado effect
     *
     * @param location      Center location
     * @param height        Height of vortex
     * @param radius        Base radius
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playVortexEffect(Location location, double height, double radius,
                          Particle particle, int durationTicks);

    /**
     * Create a wave effect emanating from location
     *
     * @param center        Center point
     * @param radius        Wave radius
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playWaveEffect(Location center, double radius, Particle particle, int durationTicks);

    /**
     * Create a cube outline effect
     *
     * @param location      Center of cube
     * @param size          Size of cube edges
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playCubeEffect(Location location, double size, Particle particle, int durationTicks);

    /**
     * Create an animated trail effect following an entity
     *
     * @param entityId      Entity to follow
     * @param particle      Particle type
     * @param durationTicks Duration
     */
    void playTrailEffect(UUID entityId, Particle particle, int durationTicks);

    /**
     * Create a beam effect between two locations (laser-like)
     *
     * @param start         Start location
     * @param end           End location
     * @param particle      Particle type
     * @param width         Beam width
     * @param durationTicks Duration
     */
    void playBeamEffect(Location start, Location end, Particle particle,
                        double width, int durationTicks);

    /**
     * Create an explosion ring effect
     *
     * @param center   Center of explosion
     * @param radius   Ring radius
     * @param particle Particle type
     */
    void playExplosionRingEffect(Location center, double radius, Particle particle, Particle.DustOptions options);
}
