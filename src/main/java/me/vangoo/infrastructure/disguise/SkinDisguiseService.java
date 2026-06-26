package me.vangoo.infrastructure.disguise;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Робить живого гравця видимим з довільним скіном для ІНШИХ гравців, повторно
 * пересилаючи сутність гравця з підробленим профілем через PacketEvents.
 *
 * <p>Сервіс stateless (без полів): він лише ВІДПРАВЛЯЄ пакети, не мутуючи кеш
 * {@code User} у PacketEvents — тому реальний скін завжди доступний для відновлення.</p>
 */
public final class SkinDisguiseService {

    private SkinDisguiseService() {
    }

    /** Робить {@code player} видимим із заданим скіном для всіх ІНШИХ гравців, що його бачать. */
    public static void disguise(Player player, String textureValue, String textureSignature) {
        disguise(player, textureValue, textureSignature, null);
    }

    /**
     * Робить {@code player} видимим із заданим скіном <b>та ніком</b> {@code disguiseName}
     * для всіх ІНШИХ гравців. Нік підмінює запис у tab-листі і табличку над головою
     * (вони беруться клієнтом з імені профілю). {@code null} → лишається справжній нік.
     */
    public static void disguise(Player player, String textureValue, String textureSignature,
                                String disguiseName) {
        if (textureValue == null) {
            return; // нема що підміняти
        }
        String name = (disguiseName != null && !disguiseName.isBlank())
                ? trimToProfileName(disguiseName)
                : player.getName();
        UserProfile spoofed = new UserProfile(player.getUniqueId(), name);
        List<TextureProperty> props = new ArrayList<>();
        props.add(new TextureProperty("textures", textureValue, textureSignature));
        spoofed.setTextureProperties(props);
        resend(player, spoofed);
    }

    /** Імена профілю Mojang обмежені 16 символами — обрізаємо, щоб клієнт не відкинув пакет. */
    private static String trimToProfileName(String name) {
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    /** Відновлює реальний скін {@code player} для всіх глядачів. */
    public static void undisguise(Player player) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null || user.getProfile() == null) {
            return; // користувач уже від'єднався — нема кому/що відновлювати
        }
        resend(player, user.getProfile());
    }

    private static void resend(Player player, UserProfile profile) {
        int entityId = player.getEntityId();
        Location loc = player.getLocation();

        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer.equals(player)) {
                continue;
            }
            try {
                // 1. Прибрати запис із tab/profile.
                WrapperPlayServerPlayerInfoRemove remove =
                        new WrapperPlayServerPlayerInfoRemove(profile.getUUID());
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, remove);

                // 2. Заново додати гравця з (можливо) підробленим профілем.
                WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
                        new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                                profile,            // профіль (з нашими текстурами)
                                true,               // listed
                                0,                  // latency
                                GameMode.SURVIVAL,  // gamemode
                                null,               // display name
                                null);              // remote chat session
                WrapperPlayServerPlayerInfoUpdate update =
                        new WrapperPlayServerPlayerInfoUpdate(
                                EnumSet.of(
                                        WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                                info);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, update);

                // 3. Знищити поточну сутність гравця у глядача.
                WrapperPlayServerDestroyEntities destroy =
                        new WrapperPlayServerDestroyEntities(entityId);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroy);

                // 4. Знову заспавнити сутність гравця.
                WrapperPlayServerSpawnPlayer spawn =
                        new WrapperPlayServerSpawnPlayer(
                                entityId,
                                profile.getUUID(),
                                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                                loc.getYaw(),
                                loc.getPitch(),
                                Collections.<EntityData<?>>emptyList());
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawn);

                // 5. Повторно надіслати екіпіровку, щоб броня/предмети знову з'явились.
                sendEquipment(viewer, player, entityId);
            } catch (Exception ignored) {
                // Один зламаний глядач не повинен ламати решту.
            }
        }
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
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private static Equipment toEquipment(EquipmentSlot slot, ItemStack bukkitItem) {
        return new Equipment(slot, SpigotConversionUtil.fromBukkitItemStack(bukkitItem));
    }
}
