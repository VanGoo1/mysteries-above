package me.vangoo.pathways.fool.abilities;

import com.github.retrooper.packetevents.PacketEvents;
import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Beyonder.BeyonderSnapshot;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.citizens.MarionetteMinionTrait;
import me.vangoo.infrastructure.disguise.SkinDisguiseService;
import me.vangoo.infrastructure.ui.NBTBuilder;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MarionettistControl extends ActiveAbility {

    private static final int    BASE_COST         = 500;
    private static final int    BASE_COOLDOWN_S   = 120;
    private static final double TETHER_RANGE      = 5.0;
    private static final double BASE_SELECT_RANGE = 30.0;
    private static final int    LOCK_TICKS        = 400;
    private static final int    CONVERT_TICKS     = 100;
    private static final int    TICK_INTERVAL     = 4;
    private static final String SWAP_BACK_NBT     = "marionettist_swap_back";
    public static final AbilityIdentity IDENTITY = AbilityIdentity.of("marionettist_control");

    // Інстанс-реєстри (НЕ static): екземпляр здібності один на пасвей — це і є правильний скоуп
    // спільного стану всіх маріонеток/посесій. Раніше static → витік і неможливість GC.
    private final Map<UUID, List<ItemStack>> originalInventories = new ConcurrentHashMap<>(); // оригінальний інвентар перед входом
    private final Map<UUID, Integer>         currentPossession   = new ConcurrentHashMap<>(); // casterId -> npcId (int)
    private final Map<Integer, UUID>         possessionByNpc     = new ConcurrentHashMap<>(); // npcId (int) -> casterId

    private final Map<UUID, ThreadSession>        activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>              marionetteNpcs = new ConcurrentHashMap<>(); // casterId -> npcId (int)
    private final Map<UUID, BeyonderSnapshot>     possessions    = new ConcurrentHashMap<>();

    @Override public String getName() { return "Контроль Маріонетки"; }

    @Override
    public AbilityIdentity getIdentity() {
        return IDENTITY;
    }

    @Override
    public String getDescription(Sequence seq) {
        double range = BASE_SELECT_RANGE * SequenceScaler.calculateMultiplier(
                seq.level(), SequenceScaler.ScalingStrategy.WEAK);
        return "Захоплює Нитки Духовного Тіла та перетворює ціль на живу маріонетку.\n\n" +
                "§7▪ Фаза 1 §f— Фіксація (≤" + (int)TETHER_RANGE + " м, 20 с)\n" +
                "§7▪ Фаза 2 §f— Заціпеніння (§cSlowness X / Nausea / Fatigue§f)\n" +
                "§7▪ Фаза 3 §f— Конверсія через 5 хв — клон з шкірою гравця\n\n" +
                "§eПосвоєння §7(Shift+ПКМ):\n" +
                "§f  Ввійти  §7→ позиції змінюються, ви отримуєте Шлях цілі\n" +
                "§f  Вийти   §7→ ПКМ предметом §e[Вийти з маріонетки]\n" +
                "§cЯкщо клон загине поки ви всередині — ви миттєво повертаєтесь.\n" +
                "§7Дальність: §e" + (int)range + " м";
    }

    @Override public int getSpiritualityCost() { return BASE_COST; }

    @Override
    public int getCooldown(Sequence seq) {
        return (int)(BASE_COOLDOWN_S / SequenceScaler.calculateMultiplier(
                seq.level(), SequenceScaler.ScalingStrategy.WEAK));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Entry point
    // ════════════════════════════════════════════════════════════════════════

    @Override
    protected AbilityResult performExecution(IAbilityContext ctx) {
        UUID casterId = ctx.getCasterId();

        // Перевірка на предмет виходу — НАЙПЕРША
        if (isSwapBackItemInHand(ctx, casterId)) {
            swapOut(ctx, casterId, false);
            return AbilityResult.success();
        }

        if (ctx.playerData().isSneaking(casterId))
            return handlePossessionToggle(ctx, casterId);

        if (activeSessions.containsKey(casterId)) {
            cancelSession(ctx, casterId, "§cВи відпустили Нитки.");
            return AbilityResult.successWithMessage("§cСесію скасовано.");
        }

        double range = BASE_SELECT_RANGE * SequenceScaler.calculateMultiplier(
                ctx.getCasterBeyonder().getSequence().level(),
                SequenceScaler.ScalingStrategy.WEAK);

        Optional<LivingEntity> targetOpt = ctx.targeting().getTargetedEntity(range);
        if (targetOpt.isEmpty())
            return AbilityResult.failure("§cНемає цілі — направте погляд на живу істоту.");

        LivingEntity target = targetOpt.get();
        if (target.getUniqueId().equals(casterId))
            return AbilityResult.failure("§cВи не можете натягнути власні Нитки.");
        if (isMarionetteNpc(target))
            return AbilityResult.failure("§cВи не можете поработити вже існуючу маріонетку.");

        beginThreading(ctx, casterId, target);
        return AbilityResult.success();
    }

    private boolean isSwapBackItemInHand(IAbilityContext ctx, UUID casterId) {
        Player player = ctx.getCasterPlayer();
        if (player == null) return false;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        return isSwapBackItem(mainHand) || isSwapBackItem(offHand);
    }

    public static boolean isSwapBackItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        NBTBuilder nbt = new NBTBuilder(item);
        return nbt.getBoolean(item, SWAP_BACK_NBT).orElse(false);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Phase 1 — threading ticker
    // ════════════════════════════════════════════════════════════════════════

    private void beginThreading(IAbilityContext ctx, UUID casterId, LivingEntity target) {
        ThreadSession session = new ThreadSession(target.getUniqueId());
        activeSessions.put(casterId, session);
        ctx.messaging().sendMessage(casterId,
                "§5[Маріонетист] §fНитки натягуються на §e" + entityName(target) + "§f.");
        ctx.messaging().sendMessageToActionBar(casterId,
                Component.text("Фіксація ниток… тримайтесь поруч!", NamedTextColor.DARK_PURPLE));
        ctx.effects().playSound(ctx.getCasterLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.5f);

        BukkitTask task = ctx.scheduling().scheduleRepeating(() -> {
            ThreadSession s = activeSessions.get(casterId);
            if (s == null) return;

            if (!ctx.playerData().isOnline(casterId)) {
                removeSession(casterId);
                return;
            }

            LivingEntity t = resolveEntity(s.targetId);
            if (t == null || t.isDead()) {
                cancelSession(ctx, casterId, "§eЦіль зникла — Нитки розірвались.");
                return;
            }

            Location cLoc = ctx.playerData().getCurrentLocation(casterId);
            Location tLoc = ctx.playerData().getCurrentLocation(s.targetId);
            if (cLoc == null || tLoc == null) return;

            drawThreadParticles(cLoc, tLoc, s.locked);

            if (!s.locked) {
                if (cLoc.distance(tLoc) > TETHER_RANGE) {
                    s.lockTicks = Math.max(0, s.lockTicks - TICK_INTERVAL * 2);
                    if (s.lockTicks % 80 == 0)
                        ctx.messaging().sendMessageToActionBar(casterId,
                                Component.text("Занадто далеко! Нитки слабшають…", NamedTextColor.RED));
                } else {
                    s.lockTicks += TICK_INTERVAL;
                    if (s.lockTicks % 80 == 0)
                        ctx.messaging().sendMessageToActionBar(casterId,
                                Component.text("Фіксація: §e" + (LOCK_TICKS - s.lockTicks) / 20 +
                                        " §5сек", NamedTextColor.DARK_PURPLE));
                    if (s.lockTicks >= LOCK_TICKS) {
                        s.locked = true;
                        onPhase2Begin(ctx, casterId, t);
                    }
                }
                return;
            }

            s.totalTicks += TICK_INTERVAL;
            if (s.totalTicks % 40 == 0) applyDebuffs(ctx, s.targetId);
            if (s.totalTicks % 100 == 0)
                ctx.messaging().sendMessageToActionBar(casterId,
                        Component.text("Конверсія через §c" +
                                        (CONVERT_TICKS - s.totalTicks) / 20 + " §5сек",
                                NamedTextColor.DARK_PURPLE));
            if (s.totalTicks >= CONVERT_TICKS) {
                removeSession(casterId);
                convertToMarionette(ctx, casterId, t);
            }
        }, 0L, TICK_INTERVAL);
        session.bindTask(task);
    }

    private void onPhase2Begin(IAbilityContext ctx, UUID casterId, LivingEntity t) {
        ctx.messaging().sendMessage(casterId,
                "§5[Маріонетист] §fНитки закріплені! Ціль заціпеніє…");
        ctx.effects().playSound(ctx.getCasterLocation(),
                Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 1.2f, 0.6f);
        Location tLoc = ctx.playerData().getCurrentLocation(t.getUniqueId());
        if (tLoc != null)
            ctx.effects().spawnParticle(Particle.SOUL, tLoc.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4);
        applyDebuffs(ctx, t.getUniqueId());
    }

    private void applyDebuffs(IAbilityContext ctx, UUID id) {
        ctx.entity().applyPotionEffect(id, PotionEffectType.SLOWNESS,       60, 9);
        ctx.entity().applyPotionEffect(id, PotionEffectType.NAUSEA,         60, 0);
        ctx.entity().applyPotionEffect(id, PotionEffectType.MINING_FATIGUE, 60, 2);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Phase 3 — Citizens2 NPC clone
    // ════════════════════════════════════════════════════════════════════════

    private void convertToMarionette(IAbilityContext ctx, UUID casterId, LivingEntity target) {
        Location loc = ctx.playerData().getCurrentLocation(target.getUniqueId());
        if (loc == null) return;

        String   name       = entityName(target);
        boolean  isPlayer   = target instanceof Player;
        Beyonder tBeyonder  = ctx.beyonder().getBeyonder(target.getUniqueId());
        List<ItemStack> inv = captureInventory(target);

        String skinValue = null, skinSignature = null;
        if (target instanceof Player p) {
            try {
                var user = PacketEvents.getAPI().getPlayerManager().getUser(p);
                if (user != null && user.getProfile() != null) {
                    for (var tex : user.getProfile().getTextureProperties()) {
                        if ("textures".equals(tex.getName())) {
                            skinValue = tex.getValue();
                            skinSignature = tex.getSignature();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Текстура залишається null — скін просто не підміниться
            }
        }
        final String fSkinValue     = skinValue;
        final String fSkinSignature = skinSignature;

        ctx.effects().playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.5f);
        ctx.effects().playSphereEffect(loc.clone().add(0, 1, 0), 2.0, Particle.SOUL_FIRE_FLAME, 40);
        ctx.effects().spawnParticle(Particle.SQUID_INK,
                loc.clone().add(0, 1, 0), 60, 0.6, 0.8, 0.6);
        ctx.entity().damage(target.getUniqueId(), 10_000.0);

        ctx.scheduling().scheduleDelayed(() -> {
            if (!ctx.playerData().isOnline(casterId)) return;

            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER,
                    "§5§lМаріонетка §r§7[" + name + "]");

            if (isPlayer)
                npc.getOrAddTrait(SkinTrait.class).setSkinName(name, true);

            npc.spawn(loc);

            MarionetteMinionTrait trait = new MarionetteMinionTrait();
            trait.initialise(casterId, name, tBeyonder, inv);
            trait.setSkin(fSkinValue, fSkinSignature);
            npc.addTrait(trait);

            copyEquipmentToNpc(npc, target);

            marionetteNpcs.put(casterId, npc.getId());

            ctx.messaging().sendMessage(casterId,
                    "§5[Маріонетист] §f" + name + " §5перетворена на вашу маріонетку!");
            ctx.messaging().sendMessageToActionBar(casterId,
                    Component.text("Shift+ПКМ — увійти в маріонетку", NamedTextColor.LIGHT_PURPLE));

            registerNpcDeathListener(ctx, casterId, npc);
        }, 20L);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Possession toggle (Shift+RightClick)
    // ════════════════════════════════════════════════════════════════════════

    private AbilityResult handlePossessionToggle(IAbilityContext ctx, UUID casterId) {
        Integer npcId = marionetteNpcs.get(casterId);
        if (npcId == null)
            return AbilityResult.failure("§cУ вас немає маріонетки.");

        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null || !npc.isSpawned()) {
            marionetteNpcs.remove(casterId);
            return AbilityResult.failure("§cМаріонетку не знайдено.");
        }

        MarionetteMinionTrait trait = npc.getTraitNullable(MarionetteMinionTrait.class);
        if (trait == null)
            return AbilityResult.failure("§cДані маріонетки пошкоджені.");

        Location npcLoc  = npc.getStoredLocation();
        Location castLoc = ctx.playerData().getCurrentLocation(casterId);
        if (npcLoc == null || castLoc == null)
            return AbilityResult.failure("§cНеможливо знайти позицію.");

        swapIn(ctx, casterId, trait, npc, npcLoc, castLoc);
        return AbilityResult.success();
    }

    // ── Swap IN ───────────────────────────────────────────────────────────────

    // Виправлений swapIn метод
    private void swapIn(IAbilityContext ctx, UUID casterId,
                        MarionetteMinionTrait trait, NPC npc,
                        Location npcLoc, Location castLoc) {

        Beyonder caster = ctx.beyonder().getBeyonder(casterId);
        int npcId = npc.getId();  // int, не UUID!

        // Зберігаємо оригінальний інвентар гравця
        originalInventories.put(casterId, capturePlayerInventory(ctx.getCasterPlayer()));

        // Зберігаємо зв'язки - npcId це int
        currentPossession.put(casterId, npcId);
        possessionByNpc.put(npcId, casterId);  // Integer -> UUID

        // Snapshot перед зміною
        possessions.put(casterId, caster.takeSnapshot());

        // Зміна ідентичності
        if (trait.wasBeyonder()) {
            caster.possessIdentity(trait.getCapturedPathway(), trait.getCapturedSequence());
            caster.setSpirituality(trait.buildCapturedSpirituality());
        }

        // Очищаємо інвентар гравця
        ctx.getCasterPlayer().getInventory().clear();

        // Видаємо інвентар маріонетки гравцю
        List<ItemStack> marionetteInv = trait.getCapturedInventory();
        for (ItemStack item : marionetteInv) {
            if (item != null && item.getType() != Material.AIR) {
                ctx.entity().giveItem(casterId, item.clone());
            }
        }

        // Позиційний своп
        ctx.entity().teleport(casterId, npcLoc.clone());
        npc.teleport(castLoc.clone(),
                org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);

        // Оновлюємо екіпіровку NPC
        updateNpcEquipmentFromInventory(npc, originalInventories.get(casterId));

        // Видаємо предмет для виходу
        giveSwapBackItem(ctx, casterId);

        spawnSwapEffects(ctx, npcLoc, castLoc);

        if (trait.getSkinTextureValue() != null) {
            SkinDisguiseService.disguise(ctx.getCasterPlayer(),
                    trait.getSkinTextureValue(), trait.getSkinTextureSignature());
        }

        if (trait.wasBeyonder()) {
            ctx.messaging().sendMessage(casterId,
                    "§5[Маріонетист] §fВи увійшли в §e" + trait.getOriginalPlayerName() +
                            "§f. §7Шлях: §e" + trait.getCapturedPathway().getName() +
                            " §7Посл.: §e" + trait.getCapturedSequence().level());
        } else {
            ctx.messaging().sendMessage(casterId,
                    "§5[Маріонетист] §fВи увійшли в §e" +
                            trait.getOriginalPlayerName() + "§f §7(не Потойбічна).");
        }
        ctx.messaging().sendMessageToActionBar(casterId,
                Component.text("ПКМ [Вийти з маріонетки] — повернутись у своє тіло",
                        NamedTextColor.LIGHT_PURPLE));
    }

    // ── Swap OUT ──────────────────────────────────────────────────────────────

    // Виправлений swapOut метод
    private void swapOut(IAbilityContext ctx, UUID casterId, boolean forced) {
        Integer npcId = currentPossession.remove(casterId);  // Integer
        if (npcId != null) possessionByNpc.remove(npcId);

        BeyonderSnapshot snap = possessions.remove(casterId);

        // Видаляємо предмет виходу
        removeSwapBackItem(ctx, casterId);

        if (snap == null) return;

        Player casterPlayerForSkin = ctx.getCasterPlayer();
        if (casterPlayerForSkin != null) {
            SkinDisguiseService.undisguise(casterPlayerForSkin);
        }

        Beyonder caster = ctx.beyonder().getBeyonder(casterId);

        // Відновлюємо оригінальний інвентар
        List<ItemStack> originalInv = originalInventories.remove(casterId);
        if (originalInv != null && caster != null) {
            ctx.getCasterPlayer().getInventory().clear();
            for (ItemStack item : originalInv) {
                if (item != null && item.getType() != Material.AIR) {
                    ctx.entity().giveItem(casterId, item.clone());
                }
            }
        }

        // Відновлюємо ідентичність
        if (caster != null) caster.restoreIdentity(snap);

        NPC npc = npcId != null ? CitizensAPI.getNPCRegistry().getById(npcId) : null;  // getById(int)
        Location castLoc = ctx.playerData().getCurrentLocation(casterId);

        if (!forced && npc != null && npc.isSpawned() && castLoc != null) {
            Location npcLoc = npc.getStoredLocation();
            ctx.entity().teleport(casterId, npcLoc != null ? npcLoc.clone() : castLoc);
            npc.teleport(castLoc.clone(),
                    org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);

            // Оновлюємо інвентар NPC з поточним інвентарем гравця
            if (caster != null) {
                updateNpcInventoryFromPlayer(npc, ctx.getCasterPlayer());
            }

            spawnSwapEffects(ctx, npcLoc != null ? npcLoc : castLoc, castLoc);
            ctx.messaging().sendMessage(casterId,
                    "§5[Маріонетист] §fВи повернулись у своє тіло.");
        } else {
            if (castLoc != null) {
                ctx.effects().spawnParticle(Particle.SOUL_FIRE_FLAME,
                        castLoc.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4);
                ctx.effects().playSound(castLoc, Sound.ENTITY_WITHER_HURT, 1.2f, 0.5f);
            }
            ctx.messaging().sendMessage(casterId,
                    "§5[Маріонетист] §cМаріонетку знищено! §fВи повернулись у своє тіло.");
            ctx.messaging().sendMessageToActionBar(casterId,
                    Component.text("Ваша маріонетка загинула!", NamedTextColor.DARK_RED));
        }
        ctx.messaging().sendMessageToActionBar(casterId,
                Component.text("Ваш Шлях відновлено.", NamedTextColor.GREEN));
    }

    /** Direct exit entry for the swap-back item listener — no cost/cooldown (bypasses execute()). */
    public boolean exitIfPossessing(IAbilityContext ctx) {
        java.util.UUID casterId = ctx.getCasterId();
        if (!currentPossession.containsKey(casterId)) {
            return false;
        }
        swapOut(ctx, casterId, false);
        return true;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Inventory helpers
    // ════════════════════════════════════════════════════════════════════════

    private List<ItemStack> capturePlayerInventory(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        return items;
    }

    private void updateNpcEquipmentFromInventory(NPC npc, List<ItemStack> inventory) {
        Equipment eq = npc.getOrAddTrait(Equipment.class);

        ItemStack helmet = null, chestplate = null, leggings = null, boots = null;
        ItemStack mainHand = null, offHand = null;

        for (ItemStack item : inventory) {
            if (item == null) continue;
            Material type = item.getType();
            String name = type.name();

            if (name.contains("HELMET")) helmet = item.clone();
            else if (name.contains("CHESTPLATE")) chestplate = item.clone();
            else if (name.contains("LEGGINGS")) leggings = item.clone();
            else if (name.contains("BOOTS")) boots = item.clone();
            else if (name.contains("SWORD") || name.contains("AXE") || name.contains("TRIDENT") || name.contains("BOW")) {
                if (mainHand == null) mainHand = item.clone();
            } else if (name.contains("SHIELD")) {
                offHand = item.clone();
            }
        }

        eq.set(Equipment.EquipmentSlot.HELMET, helmet);
        eq.set(Equipment.EquipmentSlot.CHESTPLATE, chestplate);
        eq.set(Equipment.EquipmentSlot.LEGGINGS, leggings);
        eq.set(Equipment.EquipmentSlot.BOOTS, boots);
        if (mainHand != null) eq.set(Equipment.EquipmentSlot.HAND, mainHand);
        if (offHand != null) eq.set(Equipment.EquipmentSlot.OFF_HAND, offHand);
    }

    private void updateNpcInventoryFromPlayer(NPC npc, Player player) {
        Equipment eq = npc.getOrAddTrait(Equipment.class);

        eq.set(Equipment.EquipmentSlot.HAND, safeClone(player.getInventory().getItemInMainHand()));
        eq.set(Equipment.EquipmentSlot.OFF_HAND, safeClone(player.getInventory().getItemInOffHand()));
        eq.set(Equipment.EquipmentSlot.HELMET, safeClone(player.getInventory().getHelmet()));
        eq.set(Equipment.EquipmentSlot.CHESTPLATE, safeClone(player.getInventory().getChestplate()));
        eq.set(Equipment.EquipmentSlot.LEGGINGS, safeClone(player.getInventory().getLeggings()));
        eq.set(Equipment.EquipmentSlot.BOOTS, safeClone(player.getInventory().getBoots()));
    }

    // ════════════════════════════════════════════════════════════════════════
    // NPC death listener
    // ════════════════════════════════════════════════════════════════════════

    private void registerNpcDeathListener(IAbilityContext ctx, UUID casterId, NPC npc) {
        Entity npcEntity = npc.getEntity();
        if (npcEntity == null) return;
        UUID npcEntityId = npcEntity.getUniqueId();

        ctx.events().subscribeToTemporaryEvent(
                casterId,
                EntityDeathEvent.class,
                e -> e.getEntity().getUniqueId().equals(npcEntityId),
                e -> {
                    e.getDrops().clear();
                    e.setDroppedExp(0);

                    UUID ownerId = possessionByNpc.remove(npc.getId());
                    if (ownerId != null) {
                        currentPossession.remove(ownerId);
                        originalInventories.remove(ownerId);
                        possessions.remove(ownerId);
                        swapOut(ctx, ownerId, true);
                    }

                    marionetteNpcs.remove(casterId);
                    npc.destroy();

                    ctx.messaging().sendMessage(casterId,
                            "§5[Маріонетист] §7Вашу маріонетку знищено.");
                },
                Integer.MAX_VALUE
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // Swap-back item helpers
    // ════════════════════════════════════════════════════════════════════════

    private void giveSwapBackItem(IAbilityContext ctx, UUID casterId) {
        Player player = ctx.getCasterPlayer();
        if (player == null) return;

        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Вийти з маріонетки");
        meta.setLore(List.of(
                ChatColor.GRAY + "ПКМ — повернутись у своє тіло",
                ChatColor.DARK_GRAY + "Здібність Маріонетиста"
        ));
        item.setItemMeta(meta);

        NBTBuilder nbt = new NBTBuilder(item);
        item = nbt.setBoolean(SWAP_BACK_NBT, true).build();

        // Кладемо в 8 слот
        ItemStack displaced = player.getInventory().getItem(8);
        player.getInventory().setItem(8, item);
        if (displaced != null && displaced.getType() != Material.AIR) {
            player.getInventory().addItem(displaced);
        }
    }

    private void removeSwapBackItem(IAbilityContext ctx, UUID casterId) {
        Player player = ctx.getCasterPlayer();
        if (player == null) return;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta()) continue;

            NBTBuilder nbt = new NBTBuilder(item);
            if (nbt.getBoolean(item, SWAP_BACK_NBT).orElse(false)) {
                player.getInventory().setItem(i, null);
                break;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════

    /** Прибирає сесію фіксації з реєстру і скасовує її тікер (інакше таск завис би назавжди). */
    private ThreadSession removeSession(UUID casterId) {
        ThreadSession s = activeSessions.remove(casterId);
        if (s != null) s.cancel();
        return s;
    }

    private void cancelSession(IAbilityContext ctx, UUID casterId, String msg) {
        ThreadSession s = removeSession(casterId);
        if (s == null) return;
        ctx.entity().removeAllPotionEffects(s.targetId);
        ctx.messaging().sendMessageToActionBar(casterId,
                Component.text(msg, NamedTextColor.YELLOW));
        ctx.effects().playSound(ctx.getCasterLocation(),
                Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.7f);
    }

    private void copyEquipmentToNpc(NPC npc, LivingEntity src) {
        EntityEquipment eq = src.getEquipment();
        if (eq == null) return;
        Equipment ne = npc.getOrAddTrait(Equipment.class);
        ne.set(Equipment.EquipmentSlot.HAND,       safeClone(eq.getItemInMainHand()));
        ne.set(Equipment.EquipmentSlot.OFF_HAND,   safeClone(eq.getItemInOffHand()));
        ne.set(Equipment.EquipmentSlot.HELMET,     safeClone(eq.getHelmet()));
        ne.set(Equipment.EquipmentSlot.CHESTPLATE, safeClone(eq.getChestplate()));
        ne.set(Equipment.EquipmentSlot.LEGGINGS,   safeClone(eq.getLeggings()));
        ne.set(Equipment.EquipmentSlot.BOOTS,      safeClone(eq.getBoots()));
    }

    private ItemStack safeClone(ItemStack i) {
        return (i != null && i.getType() != Material.AIR) ? i.clone() : null;
    }

    private List<ItemStack> captureInventory(LivingEntity e) {
        List<ItemStack> list = new ArrayList<>();
        if (e instanceof Player p) {
            for (ItemStack i : p.getInventory().getContents()) {
                if (i != null && i.getType() != Material.AIR) list.add(i.clone());
            }
        }
        return list;
    }

    private void drawThreadParticles(Location from, Location to, boolean locked) {
        Particle.DustOptions dust = new Particle.DustOptions(
                locked ? Color.fromRGB(80, 0, 80) : Color.fromRGB(30, 30, 30), 0.6f);
        Vector dir = to.clone().subtract(from).toVector();
        double len = dir.length();
        if (len < 0.1) return;
        dir.normalize();
        for (double d = 0.5; d < len; d += 0.5) {
            Location pt = from.clone().add(dir.clone().multiply(d)).add(0, 1, 0);
            if (pt.getWorld() != null)
                pt.getWorld().spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);
        }
    }

    private void spawnSwapEffects(IAbilityContext ctx, Location a, Location b) {
        ctx.effects().spawnParticle(Particle.PORTAL, a.clone().add(0, 1, 0), 50, 0.5, 0.8, 0.5);
        ctx.effects().spawnParticle(Particle.PORTAL, b.clone().add(0, 1, 0), 50, 0.5, 0.8, 0.5);
        ctx.effects().playSound(a, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
        ctx.effects().playSound(b, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
        ctx.effects().playSound(a, Sound.ENTITY_WARDEN_HEARTBEAT,  0.6f, 1.4f);
    }

    private LivingEntity resolveEntity(UUID id) {
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntities())
                if (e.getUniqueId().equals(id) && e instanceof LivingEntity le) return le;
        return null;
    }

    private boolean isMarionetteNpc(LivingEntity e) {
        if (!CitizensAPI.hasImplementation()) return false;
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(e);
        return npc != null && npc.hasTrait(MarionetteMinionTrait.class);
    }

    private String entityName(LivingEntity e) {
        if (e instanceof Player p) return p.getName();
        if (e.getCustomName() != null) return ChatColor.stripColor(e.getCustomName());
        return e.getType().name();
    }

    @Override
    public void cleanUp() {
        for (UUID possessingCaster : currentPossession.keySet()) {
            Player p = Bukkit.getPlayer(possessingCaster);
            if (p != null) {
                SkinDisguiseService.undisguise(p);
            }
        }

        NPCRegistry reg = CitizensAPI.getNPCRegistry();
        marionetteNpcs.values().forEach(id -> {
            NPC n = reg.getById(id);
            if (n != null) n.destroy();
        });
        activeSessions.values().forEach(ThreadSession::cancel); // скасувати завислі тікери фіксації
        activeSessions.clear();
        marionetteNpcs.clear();
        possessions.clear();
        currentPossession.clear();
        possessionByNpc.clear();
        originalInventories.clear();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Inner state
    // ════════════════════════════════════════════════════════════════════════

    private static final class ThreadSession {
        final UUID targetId;
        int     lockTicks  = 0;
        int     totalTicks = 0;
        boolean locked     = false;
        private BukkitTask task;
        ThreadSession(UUID t) { targetId = t; }

        void bindTask(BukkitTask task) { this.task = task; }

        /** Зупиняє тікер фази фіксації. Ідемпотентно. */
        void cancel() {
            if (task != null && !task.isCancelled()) task.cancel();
        }
    }
}