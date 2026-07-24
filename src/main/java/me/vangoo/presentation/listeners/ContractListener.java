package me.vangoo.presentation.listeners;

import me.vangoo.application.services.ContractService;
import me.vangoo.domain.contracts.Contract;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Bukkit-глюй контрактів Sun: виявлення порушення {@code PEACE} (удар між сторонами),
 * <b>виконання {@code DEBT}</b> (боржник sneak-клікає кредитора з обумовленим предметом →
 * {@code settle} без кари), плюс періодична перевірка дедлайну {@code DEBT}. Порушення =
 * {@link ContractService#breach} (стан + persist + Божественна кара {@code DivinePunishment}).
 */
public class ContractListener implements Listener {

    private static final long DEBT_CHECK_PERIOD_TICKS = 200L; // 10 с

    private final ContractService contractService;

    public ContractListener(JavaPlugin plugin, ContractService contractService) {
        this.contractService = contractService;
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiredDebts,
                DEBT_CHECK_PERIOD_TICKS, DEBT_CHECK_PERIOD_TICKS);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        contractService.findActivePeace(attacker.getUniqueId(), victim.getUniqueId()).ifPresent(peace -> {
            event.setCancelled(true);
            contractService.breach(peace.id(), attacker.getUniqueId());
        });
    }

    /**
     * Виконання Клятви: боржник (partyB) присідає й правою кнопкою клікає кредитора (partyA),
     * тримаючи обумовлену кількість предмета. Предмет переходить кредитору, контракт —
     * {@code SETTLED} без Божественної кари. Збіг предмета — за матеріалом (як і зберігала
     * Клятва), NBT кастом-предметів не звіряється.
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // подія двоїться на дві руки
        if (!(event.getRightClicked() instanceof Player creditor)) return;
        Player debtor = event.getPlayer();
        if (!debtor.isSneaking()) return;

        contractService.findActiveDebt(debtor.getUniqueId(), creditor.getUniqueId()).ifPresent(debt -> {
            Material material;
            int amount;
            try {
                material = Material.valueOf(debt.params().get("item"));
                amount = Integer.parseInt(debt.params().get("amount"));
            } catch (IllegalArgumentException | NullPointerException e) {
                return; // зіпсовані параметри — ігноруємо, дедлайн-перевірка розрулить
            }
            event.setCancelled(true);

            ItemStack required = new ItemStack(material, amount);
            if (!debtor.getInventory().containsAtLeast(required, amount)) {
                debtor.sendMessage(ChatColor.RED + "Для виконання Клятви потрібно " + amount + "× " +
                        prettyName(material) + " у інвентарі.");
                return;
            }

            debtor.getInventory().removeItem(required);
            giveOrDrop(creditor, new ItemStack(material, amount));
            contractService.settle(debt.id());

            debtor.getWorld().playSound(debtor.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.0f, 1.4f);
            debtor.sendMessage(ChatColor.GOLD + "☀ Клятву виконано — контракт завершено.");
            creditor.sendMessage(ChatColor.GOLD + "☀ " + debtor.getName() + " виконав Клятву: отримано " +
                    amount + "× " + prettyName(material) + ".");
        });
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private String prettyName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    private void checkExpiredDebts() {
        long now = System.currentTimeMillis();
        List<Contract> debts = contractService.getActiveDebts();
        for (Contract debt : debts) {
            long deadline = Long.parseLong(debt.params().get("deadlineEpochMillis"));
            if (now >= deadline) {
                contractService.breach(debt.id(), debt.partyB());
            }
        }
    }

}
