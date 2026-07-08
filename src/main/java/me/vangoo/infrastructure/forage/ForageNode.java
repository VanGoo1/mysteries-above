package me.vangoo.infrastructure.forage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

/**
 * «Зачарований» блок фореджу: оригінальну вегетацію фізично підмінено на блок-донор, який
 * ресурспак малює зачарованим. Нода пам'ятає оригінальний BlockData (для двоблокової флори —
 * обидві половини) і вміє відновитись. Життєвий цикл: place -> (збір через BlockBreakEvent
 * у лістенері | restore за TTL/нештатною подією).
 */
public final class ForageNode {

    private final Block block;              // нижній/єдиний блок (донор)
    private final BlockData originalLower;
    private final BlockData originalUpper;  // null, якщо флора одноблокова
    private final Material donor;
    private final String ingredientId;
    private final long createdAtMillis;

    private ForageNode(Block block, BlockData originalLower, BlockData originalUpper,
                       Material donor, String ingredientId) {
        this.block = block;
        this.originalLower = originalLower;
        this.originalUpper = originalUpper;
        this.donor = donor;
        this.ingredientId = ingredientId;
        this.createdAtMillis = System.currentTimeMillis();
    }

    /** Підмінити блок на донора. Верхня половина двоблокової флори нормалізується вниз. */
    public static ForageNode place(Block target, Material donor, String ingredientId) {
        Block lower = normalizeToLower(target);
        BlockData originalLower = lower.getBlockData();
        Block above = lower.getRelative(BlockFace.UP);
        BlockData originalUpper = isUpperHalfOf(originalLower, above) ? above.getBlockData() : null;

        if (originalUpper != null) above.setType(Material.AIR, false);
        lower.setType(donor, false); // без фізики, щоб донор не «стрельнув» одразу
        if (lower.getBlockData() instanceof Leaves leavesData) {
            leavesData.setPersistent(true); // листя-донор не має осипатися
            lower.setBlockData(leavesData, false);
        }
        return new ForageNode(lower, originalLower, originalUpper, donor, ingredientId);
    }

    private static Block normalizeToLower(Block b) {
        if (b.getBlockData() instanceof Bisected bis && bis.getHalf() == Bisected.Half.TOP) {
            return b.getRelative(BlockFace.DOWN);
        }
        return b;
    }

    private static boolean isUpperHalfOf(BlockData lowerData, Block above) {
        return lowerData instanceof Bisected
                && above.getType() == lowerData.getMaterial()
                && above.getBlockData() instanceof Bisected bis
                && bis.getHalf() == Bisected.Half.TOP;
    }

    /** Блок досі є донором (його не знесли фізикою чи іншим шляхом повз лістенер). */
    public boolean isIntact() {
        return block.getType() == donor;
    }

    public long ageMillis() {
        return System.currentTimeMillis() - createdAtMillis;
    }

    public Block getBlock() {
        return block;
    }

    public Location particleLocation() {
        return block.getLocation().add(0.5, 0.7, 0.5);
    }

    public String getIngredientId() {
        return ingredientId;
    }

    public BlockData originalLower() {
        return originalLower;
    }

    public BlockData originalUpper() {
        return originalUpper;
    }

    /** Повернути оригінальний блок (обидві половини для двоблокової флори). */
    public void restore() {
        if (!isIntact()) return; // блок уже знесено інакше — не воскрешаємо рослину з повітря
        block.setBlockData(originalLower, false);
        if (originalUpper != null) {
            block.getRelative(BlockFace.UP).setBlockData(originalUpper, false);
        }
    }
}
