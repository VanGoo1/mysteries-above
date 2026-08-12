package me.vangoo.domain.organizations;

import java.util.List;
import java.util.Optional;

/**
 * Кому належить шрайн, що виріс у селі. Датапак приносить шрайни «нічийними» (один NBT
 * на всі церкви), бо інакше довелось би тримати окремий NBT і окремий елемент пулу на
 * кожну церкву, а розподіл усе одно лишався б випадковим — з провалами, де якоїсь церкви
 * немає в радіусі тисяч блоків.
 *
 * <p>Замість цього церкву обирає ця чиста функція за координатами шрайна: розподіл
 * рівномірний по всіх увімкнених церквах і — головне — ДЕТЕРМІНОВАНИЙ, тобто той самий
 * шрайн завжди веде в той самий храм, навіть якщо плагін забув, що вже його бачив.
 */
public final class ShrineAssignment {

    private ShrineAssignment() {
    }

    /**
     * @param churchIds увімкнені церкви (порядок важливий — він частина відображення)
     * @param blockX    координата шрайна
     * @param blockZ    координата шрайна
     * @return id церкви або порожньо, якщо жодної увімкненої церкви немає
     */
    public static Optional<String> pick(List<String> churchIds, int blockX, int blockZ) {
        if (churchIds == null || churchIds.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.floorMod(scatter(blockX, blockZ), churchIds.size());
        return Optional.of(churchIds.get(index));
    }

    /**
     * Лавинний змішувач координат (splitmix64-фіналізатор). Сирий {@code x * 31 + z} тут
     * не годиться: села стоять на регулярній сітці, і лінійний хеш роздав би сусіднім
     * селам той самий залишок за модулем — цілі краї карти діставались би одній церкві.
     */
    private static int scatter(int blockX, int blockZ) {
        long h = blockX * 0x9E3779B97F4A7C15L ^ (blockZ + 0x165667B19E3779F9L) * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (int) h;
    }
}
