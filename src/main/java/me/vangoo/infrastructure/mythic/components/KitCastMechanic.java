package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import me.vangoo.domain.creatures.AbilityCastPlanner;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@MythicMechanic(author = "mysteries-above", name = "kitcast",
        description = "Casts the next ready ability of the creature's sequence kit (GCD + priority)")
public class KitCastMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private static final long MILLIS_PER_TICK = 50L;

    private final List<AbilityCastPlanner.KitEntry> kit;
    private final long gcdMillis;
    // per-entity стан темпу кастів; мертві записи вичищаються ліниво в purgeDeadEntities
    private final Map<UUID, AbilityCastPlanner> planners = new ConcurrentHashMap<>();

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public KitCastMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.gcdMillis = event.getConfig().getInteger(new String[]{"gcd", "g"}, 120) * MILLIS_PER_TICK;
        this.kit = parseKit(event.getConfig().getString(new String[]{"skills", "s"}, ""));
    }

    // Формат: skills=Name:кулдаунТіки,Name:кулдаунТіки — порядок = пріоритет
    private static List<AbilityCastPlanner.KitEntry> parseKit(String raw) {
        List<AbilityCastPlanner.KitEntry> entries = new ArrayList<>();
        for (String part : raw.split(",")) {
            String[] pair = part.trim().split(":");
            if (pair.length != 2) continue;
            try {
                entries.add(new AbilityCastPlanner.KitEntry(pair[0].trim(),
                        Long.parseLong(pair[1].trim()) * MILLIS_PER_TICK));
            } catch (NumberFormatException e) {
                Bukkit.getLogger().warning("[MysteriesAbove] kitcast: invalid cooldown in '"
                        + part.trim() + "' — entry skipped");
            }
        }
        return List.copyOf(entries);
    }

    // Виконує метаскіли, що чіпають Bukkit — тільки main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (kit.isEmpty()) return SkillResult.INVALID_CONFIG;
        Entity caster = data.getCaster().getEntity().getBukkitEntity();
        AbilityCastPlanner planner = planners.computeIfAbsent(caster.getUniqueId(),
                id -> new AbilityCastPlanner(kit, gcdMillis));
        Optional<String> next = planner.pickNext(System.currentTimeMillis());
        if (next.isEmpty()) return SkillResult.CONDITION_FAILED;
        MythicBukkit.inst().getAPIHelper().castSkill(caster, next.get(), target.getBukkitEntity(),
                caster.getLocation(), List.of(target.getBukkitEntity()), List.of(), 1.0f);
        purgeDeadEntities();
        return SkillResult.SUCCESS;
    }

    // Зрідка вичищаємо записи істот, яких уже немає (SYNC_ONLY → Bukkit.getEntity безпечний)
    private void purgeDeadEntities() {
        if (ThreadLocalRandom.current().nextInt(50) != 0) return;
        planners.keySet().removeIf(id -> Bukkit.getEntity(id) == null);
    }
}
