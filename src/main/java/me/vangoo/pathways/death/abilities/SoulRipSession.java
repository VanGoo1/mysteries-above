package me.vangoo.pathways.death.abilities;

import com.github.retrooper.packetevents.PacketEvents;
import me.vangoo.domain.abilities.context.IEventContext;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import me.vangoo.pathways.fool.abilities.MarionettistControl;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Вирвана душа однієї жертви (Death, Посл. 6 — Мова мертвих).
 *
 * <p>Броню в Minecraft не «пробити повз плоть» числом, тож вікіне «bypassing the physical
 * protection provided by flesh and blood to target the Spirit Body» зроблено буквально: гравця
 * ВИЙМАЮТЬ із тіла. Тіло лишається стояти NPC-ом Citizens зі скіном і спорядженням жертви
 * (техніка {@code MarionettistControl}), гравець іде в {@code SPECTATOR} за власну спину й
 * щотіка телепортується назад — рухатись він не може, здібності запечатані.
 *
 * <p>Перший удар по тілу повертає душу достроково: тіло без душі — не мішень для добивання,
 * а важіль. Моб душі не має куди дівати, тож у нього просто вимикається AI: тіло стоїть.
 *
 * <p>Сесія тримає ЛИШЕ глобальні сервіси ({@code IEventContext}, {@code IVisualEffectsContext}),
 * ніколи не {@code IAbilityContext} кастера — правило сесій, п. 3.
 */
final class SoulRipSession {

    /** Щотіка: рідше — і гравець встигне відлетіти в режимі спостерігача. */
    static final long TICK_PERIOD_TICKS = 1L;

    /** Підписки живуть рівно стільки, скільки сесія, і знімаються в {@link #end()}. */
    private static final int PERMANENT_DURATION = Integer.MAX_VALUE;
    /** Наскільки душа стоїть за спиною власного тіла. */
    private static final double BEHIND_DISTANCE = 2.0;

    private final UUID victimId;
    private final UUID casterId;
    private final long endsAtMillis;
    private final Color color;
    private final IEventContext events;
    private final IVisualEffectsContext effects;
    private final Map<UUID, SoulRipSession> sessions;
    private final UUID subscriptionKey = UUID.randomUUID();

    private Location bodyLocation;
    private Location viewpoint;
    private GameMode previousMode;
    private NPC body;
    private Mob frozenMob;
    private NPC frozenPuppet;
    private BukkitTask task;
    private boolean ended;

    SoulRipSession(UUID victimId, UUID casterId, int durationSeconds, Color color,
                   IEventContext events, IVisualEffectsContext effects,
                   Map<UUID, SoulRipSession> sessions) {
        this.victimId = victimId;
        this.casterId = casterId;
        this.endsAtMillis = System.currentTimeMillis() + durationSeconds * 1000L;
        this.color = color;
        this.events = events;
        this.effects = effects;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** Виймає душу. {@code false} — ціль зникла між кастом і стартом. */
    boolean start(LivingEntity victim) {
        bodyLocation = victim.getLocation().clone();

        // Маріонетка перевіряється ПЕРШОЮ: її NPC — теж {@code Player}, і гілка гравця
        // спробувала б відправити в режим спостерігача сутність, за якою нікого немає.
        NPC puppet = marionetteOf(victim);
        if (puppet != null) {
            frozenPuppet = puppet;
            puppet.getNavigator().setPaused(true);
            if (victim instanceof Mob mob) mob.setAI(false);
        } else if (victim instanceof Player player) {
            if (!ripPlayer(player)) return false;
        } else if (victim instanceof Mob mob) {
            frozenMob = mob;
            mob.setAI(false);
        } else {
            return false;
        }

        armEarlyReturn(bodyId());
        playRipEffects(victim);
        return true;
    }

    void tick() {
        if (ended) return;
        if (System.currentTimeMillis() >= endsAtMillis) {
            end();
            return;
        }

        if (frozenPuppet != null) {
            if (!frozenPuppet.isSpawned()) end();
            return;
        }
        if (frozenMob != null) {
            if (frozenMob.isDead() || !frozenMob.isValid()) end();
            return;
        }

        Player player = Bukkit.getPlayer(victimId);
        if (player == null || !player.isOnline()) {
            end();
            return;
        }
        // Тіло стоїть — душа теж: щотіка повертаємо її на місце за спиною.
        if (viewpoint != null && player.getLocation().distanceSquared(viewpoint) > 0.01) {
            player.teleport(viewpoint);
        }
    }

    /** Повертає душу в тіло. Ідемпотентно: і за таймером, і від першого удару. */
    void end() {
        if (ended) return;
        ended = true;

        sessions.remove(victimId, this);
        events.unsubscribeAll(subscriptionKey);

        if (frozenMob != null && frozenMob.isValid()) {
            frozenMob.setAI(true);
        }
        if (frozenPuppet != null && frozenPuppet.isSpawned()) {
            frozenPuppet.getNavigator().setPaused(false);
            if (frozenPuppet.getEntity() instanceof Mob mob) mob.setAI(true);
        }

        Location returnTo = body != null && body.isSpawned()
                ? body.getStoredLocation().clone()
                : bodyLocation;
        if (body != null) {
            body.destroy();
        }

        Player player = Bukkit.getPlayer(victimId);
        if (player != null && player.isOnline() && previousMode != null) {
            player.setGameMode(previousMode);
            if (returnTo != null) player.teleport(returnTo);
            effects.playFadingAura(player.getLocation(), color, 20);
            player.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 0.6f);
            player.sendMessage(ChatColor.DARK_GREEN + "☠ Душа повернулась у тіло");
        }

        Player caster = Bukkit.getPlayer(casterId);
        if (caster != null && caster.isOnline()) {
            caster.sendMessage(ChatColor.GRAY + "☠ Душа повернулась у тіло");
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /* ===================== гравець: тіло-NPC і душа-спостерігач ===================== */

    private boolean ripPlayer(Player player) {
        body = createBody(player);
        if (body == null) return false;

        previousMode = player.getGameMode();
        viewpoint = behind(bodyLocation);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(viewpoint);
        player.sendMessage(ChatColor.DARK_GREEN
                + "☠ Ваша душа вирвана з тіла — ви лише дивитесь");
        return true;
    }

    /** Тіло: NPC-гравець з іменем, скіном і спорядженням жертви; його МОЖНА бити. */
    private NPC createBody(Player player) {
        String[] textures = capturePlayerTextures(player);
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, player.getName());
        if (npc == null) return null;

        // Скін — текстурами й лише з вимкненим авто-оновленням ДО setName: інакше Citizens
        // резолвить профіль живого гравця за ніком і забирає в нього tablist-запис.
        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
        skin.setShouldUpdateSkins(false);
        npc.setName(player.getName());
        if (textures != null && textures[0] != null) {
            skin.setSkinPersistent(player.getName() + "_soulrip", textures[1], textures[0]);
        }

        npc.spawn(bodyLocation);
        npc.setProtected(false);  // тіло без душі мусить бути вразливим
        copyEquipment(npc, player);
        return npc;
    }

    private void copyEquipment(NPC npc, Player player) {
        Equipment equipment = npc.getOrAddTrait(Equipment.class);
        equipment.set(Equipment.EquipmentSlot.HAND, clone(player.getInventory().getItemInMainHand()));
        equipment.set(Equipment.EquipmentSlot.OFF_HAND, clone(player.getInventory().getItemInOffHand()));
        equipment.set(Equipment.EquipmentSlot.HELMET, clone(player.getInventory().getHelmet()));
        equipment.set(Equipment.EquipmentSlot.CHESTPLATE, clone(player.getInventory().getChestplate()));
        equipment.set(Equipment.EquipmentSlot.LEGGINGS, clone(player.getInventory().getLeggings()));
        equipment.set(Equipment.EquipmentSlot.BOOTS, clone(player.getInventory().getBoots()));
    }

    private static ItemStack clone(ItemStack item) {
        return item == null ? null : item.clone();
    }

    /** Текстури скіну з кешу PacketEvents: {@code [value, signature]}; {@code null} — якщо нема. */
    private static String[] capturePlayerTextures(Player player) {
        try {
            var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user == null || user.getProfile() == null) return null;
            for (var texture : user.getProfile().getTextureProperties()) {
                if ("textures".equals(texture.getName())) {
                    return new String[]{texture.getValue(), texture.getSignature()};
                }
            }
        } catch (Exception ignored) {
            // Скін просто не підміниться — не привід зривати виривання душі.
        }
        return null;
    }

    /* ===================== спільне ===================== */

    /** Маріонетка Маріонетиста (Fool) або {@code null}: вона «вважається мертвою», тож піддається. */
    private static NPC marionetteOf(LivingEntity victim) {
        if (!CitizensAPI.hasImplementation() || !MarionettistControl.isMarionetteNpc(victim)) {
            return null;
        }
        return CitizensAPI.getNPCRegistry().getNPC(victim);
    }

    /** UUID того, кого б'ють: тіла-NPC, замороженої маріонетки або замороженого моба. */
    private UUID bodyId() {
        if (frozenPuppet != null) {
            Entity puppet = frozenPuppet.getEntity();
            return puppet == null ? null : puppet.getUniqueId();
        }
        if (frozenMob != null) return frozenMob.getUniqueId();
        Entity entity = body == null ? null : body.getEntity();
        return entity == null ? null : entity.getUniqueId();
    }

    /**
     * Перший удар по тілу повертає душу. Плюс вихід жертви з гри: {@code PlayerQuitEvent} ще
     * тримає живий {@code Player}, тож режим гри вдається повернути — інакше гравець зайшов би
     * назад вічним спостерігачем.
     */
    private void armEarlyReturn(UUID bodyId) {
        if (bodyId != null) {
            events.subscribeToTemporaryEvent(
                    subscriptionKey,
                    EntityDamageEvent.class,
                    event -> bodyId.equals(event.getEntity().getUniqueId()),
                    event -> end(),
                    PERMANENT_DURATION);
        }
        events.subscribeToTemporaryEvent(
                subscriptionKey,
                PlayerQuitEvent.class,
                event -> victimId.equals(event.getPlayer().getUniqueId()),
                event -> {
                    event.getPlayer().setGameMode(previousMode == null ? GameMode.SURVIVAL : previousMode);
                    end();
                },
                PERMANENT_DURATION);
    }

    private void playRipEffects(LivingEntity victim) {
        Location eyes = victim.getEyeLocation();
        effects.playRisingSpiral(bodyLocation, 2.6, 0.8, color, 40);
        effects.playGraspingHands(bodyLocation, color, 40);
        effects.playFadingAura(eyes, color, 30);
        victim.getWorld().playSound(bodyLocation, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.6f);
    }

    /** Точка за спиною тіла, обличчям до нього. */
    private static Location behind(Location body) {
        Location spot = body.clone().subtract(body.getDirection().setY(0).normalize()
                .multiply(BEHIND_DISTANCE));
        spot.setY(body.getY() + 1.0);
        spot.setDirection(body.toVector().subtract(spot.toVector()));
        return spot;
    }
}
