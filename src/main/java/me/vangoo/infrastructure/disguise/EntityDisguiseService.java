package me.vangoo.infrastructure.disguise;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Робить живого гравця видимим для ІНШИХ гравців як довільного моба, повторно
 * пересилаючи сутність гравця <b>під тим самим entity id</b>, але типу моба.
 *
 * <p>Ключовий трюк: підмінена сутність спавниться з тим самим entity id, що й
 * гравець, тож усі наступні рухові/поворотні пакети, які сервер шле для гравця,
 * автоматично рухають моба — окремої синхронізації позиції не потрібно.</p>
 *
 * <p>Обмеження (як і в {@link SkinDisguiseService}): це best-effort одноразовий
 * resend. Гравець, що заходить у зону видимості пізніше (новий чанк/релог),
 * побачить справжнього гравця, поки маску не переслати знову. Себе гравець мобом
 * не бачить (рендер від першої особи не підміняється).</p>
 *
 * <p>Сервіс stateless: лише ВІДПРАВЛЯЄ пакети, не мутуючи кеш PacketEvents —
 * тому справжній вигляд завжди доступний для відновлення.</p>
 */
public final class EntityDisguiseService {

    private EntityDisguiseService() {
    }

    /** Показує {@code player} усім ІНШИМ гравцям як моба типу {@code bukkitType}. Без ніка над головою. */
    public static void disguiseAsMob(Player player, org.bukkit.entity.EntityType bukkitType) {
        if (bukkitType == null) {
            return;
        }
        EntityType peType = SpigotConversionUtil.fromBukkitEntityType(bukkitType);
        if (peType == null) {
            return;
        }
        int entityId = player.getEntityId();
        Location loc = player.getLocation();
        Vector3d pos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());

        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer.equals(player)) {
                continue;
            }
            try {
                // 1. Прибрати справжню сутність гравця у глядача.
                sendPacket(viewer, new WrapperPlayServerDestroyEntities(entityId));
                // 2. Заспавнити моба ПІД ТИМ САМИМ entity id → рухові пакети гравця рухають його.
                WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                        entityId,
                        Optional.of(player.getUniqueId()),
                        peType,
                        pos,
                        loc.getPitch(),
                        loc.getYaw(),
                        loc.getYaw(),
                        0,
                        Optional.of(new Vector3d(0.0, 0.0, 0.0)));
                sendPacket(viewer, spawn);
                // 3. Спорядження моба = спорядження гравця (рука/броня).
                sendEquipment(viewer, player, entityId);
            } catch (Exception ignored) {
                // Один зламаний глядач не повинен ламати решту.
            }
        }
    }

    /**
     * Повертає справжній вигляд {@code player} (знову як гравця) для всіх глядачів.
     *
     * <p>Відновлення робить САМ СЕРВЕР — жодних ручних пакетів; механізм описано в
     * {@link PlayerVisibilityRefresher}. Фейкова сутність моба висить під ID гравця,
     * тож її прибирає саме серверний hide/show, а не {@code DestroyEntities}.</p>
     */
    public static void undisguise(Player player) {
        // Механізм спільний із виходом із маріонетки-гравця — тримаємо його в одному місці.
        PlayerVisibilityRefresher.resync(player);
    }

    private static void sendPacket(Player viewer, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private static void sendEquipment(Player viewer, Player player, int entityId) {
        EntityEquipment eq = player.getEquipment();
        if (eq == null) {
            return;
        }
        List<Equipment> equipment = new ArrayList<>();
        equipment.add(toEquipment(EquipmentSlot.MAIN_HAND, eq.getItemInMainHand()));
        equipment.add(toEquipment(EquipmentSlot.OFF_HAND, eq.getItemInOffHand()));
        equipment.add(toEquipment(EquipmentSlot.BOOTS, eq.getBoots()));
        equipment.add(toEquipment(EquipmentSlot.LEGGINGS, eq.getLeggings()));
        equipment.add(toEquipment(EquipmentSlot.CHEST_PLATE, eq.getChestplate()));
        equipment.add(toEquipment(EquipmentSlot.HELMET, eq.getHelmet()));

        WrapperPlayServerEntityEquipment packet =
                new WrapperPlayServerEntityEquipment(entityId, equipment);
        sendPacket(viewer, packet);
    }

    private static Equipment toEquipment(EquipmentSlot slot, ItemStack bukkitItem) {
        return new Equipment(slot, SpigotConversionUtil.fromBukkitItemStack(bukkitItem));
    }
}
