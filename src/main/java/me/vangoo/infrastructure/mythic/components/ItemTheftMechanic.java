package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Кишенькова крадіжка істот Помилки: виймає в гравця частину випадкового стака й кидає
 * її під ноги мобу. Річ НЕ зникає — гравець може її підібрати, але мусить для цього
 * підійти до злодія, тож крадіжка створює тиск, а не безповоротну втрату.
 */
@MythicMechanic(author = "mysteries-above", name = "stealitem",
        description = "Takes part of a random stack from the target player and drops it at the caster")
public class ItemTheftMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final int amount;

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public ItemTheftMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.amount = event.getConfig().getInteger(new String[]{"amount", "a"}, 1);
    }

    // Скіл-клок MythicMobs асинхронний; мутація інвентаря — тільки на main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!(target.getBukkitEntity() instanceof Player victim)) return SkillResult.INVALID_TARGET;

        // Тільки основний інвентар (0..35): броня й офхенд лишаються при гравцеві,
        // інакше крадіжка перетворюється на роздягання посеред бою.
        ItemStack[] storage = victim.getInventory().getStorageContents();
        List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] != null && !storage[i].getType().isAir()) filled.add(i);
        }
        if (filled.isEmpty()) return SkillResult.CONDITION_FAILED;

        int slot = filled.get(ThreadLocalRandom.current().nextInt(filled.size()));
        ItemStack stack = storage[slot];
        int taken = Math.min(amount, stack.getAmount());
        ItemStack loot = stack.clone();
        loot.setAmount(taken);
        stack.setAmount(stack.getAmount() - taken);
        victim.getInventory().setItem(slot, stack.getAmount() > 0 ? stack : null);

        Entity thief = data.getCaster().getEntity().getBukkitEntity();
        thief.getWorld().dropItemNaturally(thief.getLocation(), loot);
        victim.playSound(victim.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.6f);
        victim.sendMessage(ChatColor.DARK_PURPLE + "Щось витягло з ваших кишень "
                + ChatColor.GRAY + loot.getType().name().toLowerCase().replace('_', ' ')
                + ChatColor.DARK_PURPLE + " ×" + taken + ".");
        return SkillResult.SUCCESS;
    }
}
