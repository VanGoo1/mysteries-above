package me.vangoo.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 8 Arbiter: Сфера Юрисдикції — територія закону навколо власника.
 * <p>
 * ЕТАЛОН шва «правила vs ефекти» в шарі поведінки ({@code me.vangoo.pathways}):
 * <ul>
 *   <li><b>Через контекст лишається те, що є справжнім сервісом або доменним хендлом:</b>
 *       {@code getCasterBeyonder()} (агрегат для правила радіуса від Sequence),
 *       {@code getCasterId()}/{@code getCasterLocation()} (хендл кастера) та
 *       {@code scheduling()} (реальний сервіс — трекає таск для скасування).</li>
 *   <li><b>Прямим Bukkit викликається те, що було лише 1:1-обгорткою ефекту:</b>
 *       партикли, звуки, повідомлення — тут Bukkit дозволено, тож обгортки
 *       {@code effects()}/{@code messaging()} нічого не додавали (див. анти-патерн у CLAUDE.md).
 *       Це узгоджує здібність із її ж {@link JurisdictionSession}, яка теж ходить у Bukkit напряму.</li>
 * </ul>
 * Реєстр сесій — звичайне ІНСТАНС-поле (а не static): екземпляр здібності й так спільний
 * для всіх гравців свого Sequence, тож це і є правильний скоуп «усіх активних доменів».
 */
public class AreaOfJurisdiction extends ActiveAbility {

    private static final int COST = 80;
    private static final int COOLDOWN = 70;
    private static final int BASE_DOMAIN_RADIUS = 50;
    private static final long TICK_PERIOD_TICKS = 20L; // раз на секунду

    private final Map<UUID, JurisdictionSession> activeDomains = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Сфера Юрисдикції";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int radius = scaleValue(BASE_DOMAIN_RADIUS, userSequence, SequenceScaler.ScalingStrategy.DIVINE);
        return "Встановлює територію закону (радіус " + radius + " блоків) у вашій поточній позиції.\n" +
                "У цій зоні ви отримуєте " + ChatColor.GRAY + "Опір I" + ChatColor.RESET +
                " та " + ChatColor.YELLOW + "Квапливість I" + ChatColor.RESET + ".\n";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Beyonder beyonder = context.getCasterBeyonder();
        Location center = context.getCasterLocation();

        if (center == null || beyonder == null) {
            return AbilityResult.failure("Не вдалося визначити позицію");
        }

        // Чисте правило: радіус залежить від Sequence.
        int radius = scaleValue(BASE_DOMAIN_RADIUS, beyonder.getSequence(),
                SequenceScaler.ScalingStrategy.DIVINE);

        // Один домен на власника: новий каст замінює попередній.
        JurisdictionSession previous = activeDomains.remove(casterId);
        if (previous != null) {
            previous.cancel();
        }

        // Жива сесія веде власний тік; scheduling() — справжній сервіс (трекає таск), лишається.
        JurisdictionSession session = new JurisdictionSession(casterId, center, radius);
        BukkitTask task = context.scheduling().scheduleRepeating(session::tick, 0L, TICK_PERIOD_TICKS);
        session.bindTask(task);
        activeDomains.put(casterId, session);

        // Ефекти — прямий Bukkit (шар ефектів, обгортки не потрібні).
        playCreationEffects(center, radius);
        announceToOwner(casterId, radius);

        return AbilityResult.success();
    }

    @Override
    public void cleanUp() {
        activeDomains.values().forEach(JurisdictionSession::cancel);
        activeDomains.clear();
    }

    private void playCreationEffects(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return;

        world.playSound(center, Sound.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.MASTER, 1.0f, 0.5f);

        double visualRadius = Math.min(radius * 0.1, 10.0);
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            double x = Math.cos(angle) * visualRadius;
            double z = Math.sin(angle) * visualRadius;
            Location point = center.clone().add(x, 0.1, z);
            world.spawnParticle(Particle.FLAME, point, 1, 0, 0, 0);
            world.spawnParticle(Particle.CRIT, point, 1, 0, 0, 0);
        }
        // НЕ Particle.FLASH: на 1.21.11 він вимагає data-об'єкт org.bukkit.Color, і виклик
        // без нього кидає IllegalArgumentException("missing required data") просто в каст.
        // EXPLOSION того самого «спалаху» не потребує даних узагалі.
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 1, 0, 0, 0);
    }

    private void announceToOwner(UUID casterId, int radius) {
        Player owner = Bukkit.getPlayer(casterId);
        if (owner == null || !owner.isOnline()) return;

        owner.sendMessage(ChatColor.GOLD + "⚖ Ви встановили свою Територію " +
                ChatColor.GRAY + "(" + radius + " блоків)");
        owner.sendMessage(ChatColor.YELLOW + "Закон на вашому боці.");
        owner.playSound(owner.getLocation(), Sound.BLOCK_ANVIL_LAND, SoundCategory.MASTER, 0.8f, 0.8f);
    }
}
