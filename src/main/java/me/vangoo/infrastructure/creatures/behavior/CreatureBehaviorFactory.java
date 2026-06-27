package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureTier;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/** Обирає поведінку за шляхом істоти. Поки повертає null для всіх — реальні архетипи додаються
 * наступними задачами. */
public final class CreatureBehaviorFactory {

    private final BeyonderService beyonderService;
    private final Plugin plugin;

    public CreatureBehaviorFactory(BeyonderService beyonderService, Plugin plugin) {
        this.beyonderService = beyonderService;
        this.plugin = plugin;
    }

    public CreatureBehavior create(CreatureDefinition def) {
        String pathway = def.pathway() == null ? "" : def.pathway().toLowerCase(Locale.ROOT);
        boolean apex = def.tier() == CreatureTier.APEX;
        return switch (pathway) {
            // архетипи додаються наступними задачами (Task 4-9)
            case "justiciar" -> new VerdictBehavior(apex);
            case "whitetower" -> new RevealBehavior(apex);
            case "visionary" -> new AmbushBehavior(apex, beyonderService);
            case "fool" -> new ControlBehavior(apex, beyonderService);
            default -> null;
        };
    }
}
