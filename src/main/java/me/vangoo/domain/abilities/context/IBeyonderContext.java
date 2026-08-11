package me.vangoo.domain.abilities.context;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.UnlockedRecipe;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IBeyonderContext {
    void updateSanityLoss(UUID playerId, int change);

    Beyonder getBeyonder(UUID entityId);

    boolean isBeyonder(UUID entityId);

    boolean isAbilityActivated(UUID entityId, AbilityIdentity abilityIdentity);

    void removeOffPathwayAbility(AbilityIdentity identity, UUID playerId);

    int getUnlockedRecipesCount(UUID playerId,String pathwayName);

    List<ItemStack> getIngredientsForPotion(Pathway pathway, Sequence sequence);

    /** Рецепти зілль (шлях+послідовність), у які входить цей інгредієнт; порожньо — якщо ніде. */
    List<UnlockedRecipe> findRecipesUsing(ItemStack ingredient);

    /**
     * Голос мертвих (Смерть, Посл. 7): ОДНА транзакція «забрати в жертви — віддати злодію».
     * Рецепт саме ПЕРЕЇЖДЖАЄ, не копіюється, тож ферма рецептів неможлива.
     *
     * @return що саме переїхало; порожньо, якщо в жертви немає нічого нового для злодія
     */
    Optional<UnlockedRecipe> stealRecipe(UUID victimId, UUID thiefId);

    void setOverride(UUID playerId, Beyonder override);

    void removeOverride(UUID playerId);

    /** Персистить поточний стан beyonder'а (напр. після прямої мутації в deferred-потоці). */
    void updateBeyonder(UUID playerId);

    /**
     * Записати крадіжку сили (Помилка, Посл. 6): злодій тримає здібність 10 хв, жертва не
     * кастує її 30 хв. Час — абсолютний, тож вікно переживає релог і рестарт.
     */
    void stealAbility(UUID thiefId, UUID victimId, AbilityIdentity identity);

    /**
     * Те саме, але з власними строками тіру (Посл. 5 і концептуальні крадіжки).
     *
     * @param reserveHoldMillis скільки сила висить у злодія, доки він не покличе її вперше
     * @param activeHoldMillis  на скільки перший каст перерахує володіння; 0 — резервного строку нема
     * @param suppressionMillis скільки жертва не кличе вкрадене
     */
    void stealAbility(UUID thiefId, UUID victimId, AbilityIdentity identity,
                      long reserveHoldMillis, long activeHoldMillis, long suppressionMillis);

    /** Чи зайнятий у злодія єдиний слот вкраденої сили. */
    boolean hasStolenAbility(UUID thiefId);

    /** Чи лежить у слоті саме це вкрадене (у т.ч. синтетичне, як {@code conceptual:dream}). */
    boolean holdsStolen(UUID thiefId, AbilityIdentity identity);

    /** Звільнити слот достроково: вкрадене витрачено (напр. сон віддано третій особі). */
    void releaseStolen(UUID thiefId);

    /**
     * Міфічна істота як Потойбічний свого шляху і послідовності — щоб красти силу і в неї,
     * не лише в гравців. Порожньо, якщо сутність не істота плагіна або її шлях без здібностей.
     */
    Optional<Beyonder> getCreatureBeyonder(UUID entityId);
}
