package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Waypoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 7: Морська Пам'ять. Підсилений розум Мореплавця запам'ятовує до 10 місць,
 * що переживають рестарт сервера (сховище — {@code WaypointStore}, через
 * {@code context.waypoints()}).
 * <p>
 * Звичайний каст відкриває меню ({@link SeaMemoryMenu}): створити / перейменувати /
 * видалити мітку або обрати мітку — тоді екшн-бар веде до неї (напрямок + відстань +
 * водяний слід), поки не дійдеш. Каст у присіді (Shift+ПКМ) скасовує активний показ.
 * <p>
 * Реєстр провідників — instance-поле (ніколи не static). Меню/сховище відкривають
 * deferred-потік: ресурси й кулдаун списуються лише коли реально стартує провідник
 * ({@link #beginGuidance}), керування мітками безкоштовне.
 */
public class SeaMemory extends ActiveAbility {

    private static final int COOLDOWN = 8;
    private static final int SPIRITUALITY_COST = 15;
    private static final int ARRIVAL_RADIUS = 3;
    private static final int GUIDE_PERIOD_TICKS = 10;
    private static final int MAX_GUIDE_TICKS = 6000; // запобіжник ~5 хв, якщо ціль недосяжна

    // Стрілка й підпис ВІДНОСНО погляду гравця (як компас у HUD): 0 = ціль просто попереду.
    private static final String[] REL_ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] REL_NAMES = {
            "вперед", "вперед-праворуч", "праворуч", "назад-праворуч",
            "назад", "назад-ліворуч", "ліворуч", "вперед-ліворуч"};

    private final SeaMemoryMenu menu = new SeaMemoryMenu(this);
    private final Map<UUID, Guide> guides = new ConcurrentHashMap<>();

    /** Активний показ маршруту одного гравця. */
    private static final class Guide {
        final Location target;
        final String name;
        BukkitTask task;
        int elapsedTicks;

        Guide(Location target, String name) {
            this.target = target;
            this.name = name;
        }
    }

    @Override
    public String getName() {
        return "Морська Пам'ять";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fПідсилений розум Мореплавця запам'ятовує місця. Каст відкриває §bменю міток§f: " +
                "створюйте, перейменовуйте й видаляйте.\n" +
                "Оберіть мітку — і вона вас §bведе до неї§f (напрямок, відстань, водяний слід), поки " +
                "не дійдете.\n§eShift+ПКМ§f скасовує показ — ви ніколи не загубитесь.";
    }

    @Override
    public int getSpiritualityCost() {
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }
        UUID id = context.getCasterId();

        // Shift+ПКМ під час активного показу — скасовує його. Інакше відкриваємо меню.
        if (player.isSneaking() && guides.containsKey(id)) {
            stopGuide(id);
            context.messaging().sendMessage(id, "§7Показ маршруту скасовано.");
            return AbilityResult.deferred();
        }

        menu.open(context, player);
        return AbilityResult.deferred();
    }

    /** Стартує показ маршруту до конкретної мітки. Повний каст: ресурси + кулдаун + засвоєння. */
    void beginGuidance(IAbilityContext context, Player player, Waypoint wp) {
        UUID id = player.getUniqueId();
        World world = Bukkit.getWorld(wp.world());
        if (world == null) {
            context.messaging().sendMessage(id, "§cСвіт мітки недоступний.");
            return;
        }
        Beyonder caster = context.getCasterBeyonder();
        if (!AbilityResourceConsumer.consumeResources(this, caster, context)) {
            context.messaging().sendMessage(id, "§cНедостатньо духовності, щоб прокласти маршрут.");
            return;
        }
        context.events().publishAbilityUsedEvent(this, caster);
        context.beyonder().updateBeyonder(id); // персистимо витрату духовності (deferred-потік)

        stopGuide(id);
        Guide guide = new Guide(new Location(world, wp.x(), wp.y(), wp.z()), wp.name());
        guide.task = context.scheduling().scheduleRepeating(() -> tickGuide(id), 0L, GUIDE_PERIOD_TICKS);
        guides.put(id, guide);

        context.effects().playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_ENTER, 0.8f, 0.8f);
        context.messaging().sendMessage(id,
                "§bПрокладено маршрут до мітки §f" + wp.name() + "§b. Shift+ПКМ — скасувати.");
    }

    /** Один тік провідника — стан читаємо напряму з Bukkit, не із захопленого контексту. */
    private void tickGuide(UUID id) {
        Guide guide = guides.get(id);
        if (guide == null) {
            return;
        }
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isValid()) {
            stopGuide(id);
            return;
        }
        guide.elapsedTicks += GUIDE_PERIOD_TICKS;
        Location loc = p.getLocation();
        Location target = guide.target;

        if (loc.getWorld() == null || !loc.getWorld().equals(target.getWorld())) {
            p.sendActionBar(Component.text("Мітка «" + guide.name + "» в іншому світі", NamedTextColor.RED));
            if (guide.elapsedTicks >= MAX_GUIDE_TICKS) {
                stopGuide(id);
            }
            return;
        }

        double dx = target.getX() - loc.getX();
        double dz = target.getZ() - loc.getZ();
        double dy = target.getY() - loc.getY();
        double dist = Math.sqrt(dx * dx + dz * dz + dy * dy);

        if (dist <= ARRIVAL_RADIUS) {
            p.sendActionBar(Component.text("✔ Ви дісталися мітки «" + guide.name + "»", NamedTextColor.AQUA));
            p.playSound(loc, Sound.AMBIENT_UNDERWATER_EXIT, 0.8f, 1.4f);
            stopGuide(id);
            return;
        }

        int i = relativeArrowIndex(p, dx, dz);
        p.sendActionBar(Component.text(
                REL_ARROWS[i] + " " + REL_NAMES[i] + "  " + Math.round(dist) + " м → " + guide.name,
                NamedTextColor.AQUA));

        // Водяний слід у бік мітки.
        Vector dir = new Vector(dx, 0, dz);
        if (dir.lengthSquared() > 1e-4) {
            dir.normalize();
            Location eye = p.getEyeLocation();
            for (double d = 1.0; d <= 4.0; d += 1.0) {
                p.getWorld().spawnParticle(Particle.BUBBLE,
                        eye.clone().add(dir.clone().multiply(d)), 3, 0.1, 0.1, 0.1, 0.0);
            }
        }

        if (guide.elapsedTicks >= MAX_GUIDE_TICKS) {
            stopGuide(id);
        }
    }

    private void stopGuide(UUID id) {
        Guide guide = guides.remove(id);
        if (guide != null && guide.task != null) {
            guide.task.cancel();
        }
    }

    /**
     * Індекс стрілки ВІДНОСНО того, куди дивиться гравець (як компас у HUD):
     * 0 = ціль просто попереду (↑), 2 = праворуч (→), 4 = позаду (↓), 6 = ліворуч (←).
     * Bearing цілі й yaw гравця беремо в одній конвенції Bukkit: yaw = atan2(-x, z).
     */
    private int relativeArrowIndex(Player player, double dx, double dz) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = targetYaw - player.getLocation().getYaw();
        relative = ((relative % 360) + 360) % 360;
        return (int) Math.round(relative / 45.0) % 8;
    }

    @Override
    public void cleanUp() {
        // Скасовуємо лише живі провідники; збережені мітки лишаються у WaypointStore.
        guides.values().forEach(g -> {
            if (g.task != null) {
                g.task.cancel();
            }
        });
        guides.clear();
    }
}
