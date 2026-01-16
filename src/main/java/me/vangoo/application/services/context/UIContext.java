package me.vangoo.application.services.context;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.context.IUIContext;
import me.vangoo.infrastructure.ui.ChoiceMenuFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class UIContext implements IUIContext {

    private final Player caster;
    private final MysteriesAbovePlugin plugin;

    public UIContext(Player caster, MysteriesAbovePlugin plugin) {
        this.caster = caster;
        this.plugin = plugin;
    }

    @Override
    public <T> void openChoiceMenu(String title, List<T> choices, Function<T, ItemStack> itemMapper, Consumer<T> onSelect) {
        ChoiceMenuFactory.openChoiceMenu(caster, title, choices, itemMapper, onSelect);
    }

    @Override
    public void monitorSneaking(UUID targetId, int durationTicks, Consumer<Boolean> callback) {
        new BukkitRunnable() {
            int currentTick = 0;

            @Override
            public void run() {
                Player player = Bukkit.getPlayer(targetId);

                if (player == null || !player.isOnline()) {
                    callback.accept(false);
                    this.cancel();
                    return;
                }

                if (player.isSneaking()) {
                    callback.accept(true);
                    this.cancel();
                    return;
                }

                currentTick += 5;
                if (currentTick >= durationTicks) {
                    callback.accept(false);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}
