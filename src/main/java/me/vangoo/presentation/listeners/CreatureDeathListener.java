package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.LootTableData;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import me.vangoo.infrastructure.structures.LootGenerationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Дроп кастомної істоти: повна заміна ванільних дропів кастомними інгредієнтами з лут-таблиці
 * істоти. Характеристики неможливі — LootGenerationService.createItemFromId відхиляє characteristic:.
 */
public class CreatureDeathListener implements Listener {

    private final MythicCreatureGateway gateway;
    private final Map<String, CreatureDefinition> registry;
    private final LootGenerationService lootService;
    private final BeyonderService beyonderService;
    private final Random random = new Random();

    public CreatureDeathListener(MythicCreatureGateway gateway,
                                 Map<String, CreatureDefinition> registry,
                                 LootGenerationService lootService,
                                 BeyonderService beyonderService) {
        this.gateway = gateway;
        this.registry = registry;
        this.lootService = lootService;
        this.beyonderService = beyonderService;
    }

    // HIGHEST: щоб лут додавався після того, як MythicMobs очистить дропи за PreventOtherDrops.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        Optional<String> id = gateway.creatureId(event.getEntity());
        if (id.isEmpty()) return;
        CreatureDefinition def = registry.get(id.get());
        if (def == null) return; // Mythic mob without loot rules (e.g. MA_FoolPuppet)

        event.getDrops().clear(); // PreventOtherDrops страхує, це — для певності

        Beyonder killer = null;
        Player p = event.getEntity().getKiller();
        if (p != null) {
            killer = beyonderService.getBeyonder(p.getUniqueId());
        }

        LootTableData loot = def.loot();
        int count = rollCount(loot.minItems(), loot.maxItems());
        List<ItemStack> drops = lootService.generateLoot(loot, count, false, killer);
        event.getDrops().addAll(drops);
    }

    private int rollCount(int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }
}
