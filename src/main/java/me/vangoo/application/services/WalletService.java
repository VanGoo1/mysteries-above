package me.vangoo.application.services;

import me.vangoo.domain.market.CoinChange;
import me.vangoo.domain.market.PoundMoney;
import me.vangoo.infrastructure.items.CurrencyCodec;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Фізичний гаманець гравця: рахує/знімає/видає монети в інвентарі.
 * Математика розміну — чистий CoinChange; тут лише рух стаків.
 */
public class WalletService {

    private final CurrencyCodec codec;

    public WalletService(CurrencyCodec codec) {
        this.codec = codec;
    }

    public int countPounds(Player player) {
        return count(player, codec::isPound);
    }

    public int countCoppets(Player player) {
        return count(player, codec::isCoppet);
    }

    private int count(Player player, Predicate<ItemStack> isCoin) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isCoin.test(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /**
     * Знімає ціну з розміном (жадібно: коппети → фунти) і видає здачу коппетами.
     * @return empty, якщо монет не вистачає (інвентар не змінюється).
     */
    public Optional<CoinChange> charge(Player player, PoundMoney price) {
        Optional<CoinChange> change =
                CoinChange.make(countPounds(player), countCoppets(player), price);
        if (change.isEmpty()) {
            return Optional.empty();
        }
        removeCoins(player, codec::isCoppet, change.get().takeCoppets());
        removeCoins(player, codec::isPound, change.get().takePounds());
        if (change.get().changeCoppets() > 0) {
            give(player, PoundMoney.ofCoppets(change.get().changeCoppets()));
        }
        return change;
    }

    /** Видає суму монетами: фунти + решта коппетами; надлишок — дропом під ноги. */
    public void give(Player player, PoundMoney money) {
        for (ItemStack stack : asStacks(money)) {
            player.getInventory().addItem(stack).values()
                    .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        }
    }

    /** Сума як стаки монет (використовується чергою повернень для офлайн-гравців). */
    public List<ItemStack> asStacks(PoundMoney money) {
        List<ItemStack> stacks = new ArrayList<>();
        addStacks(stacks, codec.createPounds(1), money.wholePounds());
        addStacks(stacks, codec.createCoppets(1), money.remainderCoppets());
        return stacks;
    }

    private void addStacks(List<ItemStack> out, ItemStack prototype, int totalAmount) {
        int remaining = totalAmount;
        while (remaining > 0) {
            int batch = Math.min(remaining, prototype.getMaxStackSize());
            ItemStack stack = prototype.clone();
            stack.setAmount(batch);
            out.add(stack);
            remaining -= batch;
        }
    }

    private void removeCoins(Player player, Predicate<ItemStack> isCoin, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !isCoin.test(item)) {
                continue;
            }
            int take = Math.min(item.getAmount(), remaining);
            remaining -= take;
            if (take == item.getAmount()) {
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - take);
            }
        }
    }
}
