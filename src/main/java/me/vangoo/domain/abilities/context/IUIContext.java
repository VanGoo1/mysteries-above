package me.vangoo.domain.abilities.context;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public interface IUIContext {
    <T> void openChoiceMenu(String title, List<T> choices,
                            Function<T, ItemStack> itemMapper,
                            Consumer<T> onSelect);


    /**
     * Стежить за гравцем протягом певного часу.
     *
     * @param targetId      кого перевіряємо
     * @param durationTicks скільки часу чекаємо (у тіках)
     * @param callback      викликається з true, якщо гравець присів, і false, якщо час вийшов
     */
    void monitorSneaking(UUID targetId, int durationTicks, Consumer<Boolean> callback);
}
