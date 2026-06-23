package me.vangoo.pathways.door.abilities;

import me.vangoo.MysteriesAbovePlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Жива сесія «Лозошукання» — один активний стрижень одного власника.
 * <p>
 * Незмінні параметри (власник, ціль, найближчий блок, колір стрілки, ліміт часу) + власний
 * життєвий цикл: щотіку малює стрілку-вказівник до цілі, завершується при підході на ≤6 блоків
 * або по таймауту. Тікає сама через Bukkit — жодного захопленого {@code IAbilityContext}.
 * <p>
 * До рефактора цей таск жив у локальних {@code holder[]}/{@code completed[]} усередині здібності
 * й ніде не трекався — тобто при вимкненні плагіна (чи повторному касті) лишався завислим.
 * Тепер його тримає інстанс-реєстр у {@link DivinationArts}, а {@link #cancel()} викликається
 * з {@code cleanUp()}.
 */
public final class DiviningRodSession {

    private final UUID ownerId;
    private final String targetName;
    private final Location nearest;
    private final Color arrowColor;
    private final int maxTicks;

    private BukkitTask task;
    private int ticks = 0;
    private boolean completed = false;

    public DiviningRodSession(UUID ownerId, String targetName, Location nearest, Color arrowColor, int maxTicks) {
        this.ownerId = ownerId;
        this.targetName = targetName;
        this.nearest = nearest.clone();
        this.arrowColor = arrowColor;
        this.maxTicks = maxTicks;
    }

    /** Прив'язує повторюваний таск, щоб сесія могла скасувати саму себе. */
    public void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** Зупиняє стрижень і його таск. Ідемпотентно. */
    public void cancel() {
        completed = true;
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public UUID ownerId() {
        return ownerId;
    }

    public void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || completed) {
            cancel();
            return;
        }

        double distance = owner.getLocation().distance(nearest);

        // Успішне завершення в радіусі 6 блоків.
        if (distance <= 6.0) {
            onArrival(owner);
            return;
        }

        // Таймаут — час вийшов.
        if (ticks++ >= maxTicks) {
            owner.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GRAY + "Лозошукання завершено (час вийшов)"));
            cancel();
            return;
        }

        drawArrow(owner, distance);
    }

    private void onArrival(Player owner) {
        cancel();

        owner.sendMessage(ChatColor.GREEN + "════════════════════════");
        owner.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ ВИ В РАЙОНІ ЦІЛІ!");
        owner.sendMessage(ChatColor.GRAY + "Шукане: " + ChatColor.GOLD + targetName);
        owner.sendMessage(ChatColor.YELLOW + "📍 Ресурс знаходиться в радіусі ~6 блоків");
        owner.sendMessage(ChatColor.GREEN + "════════════════════════");
        playSound(owner, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
        playSound(owner, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        // Пульсуючий ефект навколо гравця.
        MysteriesAbovePlugin plugin = JavaPlugin.getPlugin(MysteriesAbovePlugin.class);
        Particle.DustOptions finalDust = new Particle.DustOptions(Color.LIME, 2.0f);
        for (int i = 0; i < 5; i++) {
            final int iteration = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location playerCenter = owner.getLocation();
                World world = owner.getWorld();
                for (int angle = 0; angle < 360; angle += 10) {
                    double rad = Math.toRadians(angle);
                    double radius = 2.0 + iteration * 0.5; // Зростаючий радіус
                    double x = Math.cos(rad) * radius;
                    double z = Math.sin(rad) * radius;
                    Location particleLoc = playerCenter.clone().add(x, 0, z);
                    world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, finalDust);
                }
                playSound(owner, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f + (iteration * 0.2f));
            }, i * 5L);
        }
    }

    private void drawArrow(Player owner, double distance) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(arrowColor, 1.2f);

        Vector direction = nearest.clone().add(0.5, 0.5, 0.5).toVector()
                .subtract(owner.getEyeLocation().toVector()).normalize();
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
        right.normalize();
        Vector thickness = right.clone().multiply(0.15);

        World world = owner.getWorld();
        double sideOffset = 0.5;
        double forwardOffset = 0.5;
        double upOffset = -0.1;

        Location arrowStart = owner.getEyeLocation().clone()
                .add(direction.clone().multiply(forwardOffset))
                .add(right.clone().multiply(sideOffset))
                .add(0, upOffset, 0);

        // Тіло стрілки.
        for (int i = 0; i < 10; i++) {
            double offset = i * 0.25;
            Location point = arrowStart.clone().add(direction.clone().multiply(offset));
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dustOptions);
            world.spawnParticle(Particle.DUST, point.clone().add(thickness), 1, 0, 0, 0, 0, dustOptions);
            world.spawnParticle(Particle.DUST, point.clone().subtract(thickness), 1, 0, 0, 0, 0, dustOptions);
        }

        // Наконечник стрілки.
        Location tipBase = arrowStart.clone().add(direction.clone().multiply(2.5));
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        for (int i = 0; i < 5; i++) {
            double t = i / 5.0;
            Location tipPoint = tipBase.clone()
                    .add(direction.clone().multiply(-0.4 * t))
                    .add(perpendicular.clone().multiply(0.4 * (1 - t)));
            world.spawnParticle(Particle.DUST, tipPoint, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
        }

        for (int i = 0; i < 5; i++) {
            double t = i / 5.0;
            Location tipPoint = tipBase.clone()
                    .add(direction.clone().multiply(-0.4 * t))
                    .subtract(perpendicular.clone().multiply(0.4 * (1 - t)));
            world.spawnParticle(Particle.DUST, tipPoint, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
        }

        // Світловий ефект на кінчику.
        if (ticks % 2 == 0) {
            Location glowPoint = arrowStart.clone().add(direction.clone().multiply(1.5));
            world.spawnParticle(Particle.DUST, glowPoint, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.WHITE, 0.5f));
        }

        // Звуковий індикатор відстані (раз на секунду).
        if (ticks % 20 == 0) {
            if (distance <= 15) {
                playSound(owner, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 2.0f);
            } else if (distance <= 30) {
                playSound(owner, Sound.BLOCK_NOTE_BLOCK_BELL, 0.3f, 1.5f);
            }
        }
    }

    private static void playSound(Player owner, Sound sound, float volume, float pitch) {
        owner.playSound(owner.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }
}
