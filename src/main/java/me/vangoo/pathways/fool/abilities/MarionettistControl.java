package me.vangoo.pathways.fool.abilities;

import com.github.retrooper.packetevents.PacketEvents;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.context.IGlowingContext;
import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Beyonder.BeyonderSnapshot;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.citizens.MarionetteMinionTrait;
import me.vangoo.infrastructure.disguise.SkinDisguiseService;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.plugin.java.JavaPlugin;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
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
    private static final String SWAP_MENU_NBT     = "marionettist_swap_menu";

    // Дистанція контролю: <50% засвоєння → 100 блоків, інакше → 200 (на 5 послідовності).
    private static final double RANGE_LOW   = 100.0;
    private static final double RANGE_HIGH  = 200.0;
    private static final long   STRAND_DEATH_MS = 10L * 60L * 1000L; // 10 хв до остаточної смерті
    private static final int    MONITOR_PERIOD_TICKS = 10;           // моніторинг дистанції
    private static final int    MASTER_PERIOD_TICKS  = 20;           // glow/strand-перевірки

    // Світіння (видиме крізь блоки) — лише для власника:
    private static final ChatColor GLOW_MARIONETTE = ChatColor.LIGHT_PURPLE; // вільна маріонетка
    private static final ChatColor GLOW_BODY       = ChatColor.GREEN;        // твоє основне тіло
    private static final ChatColor GLOW_STRANDED   = ChatColor.RED;          // поза зоною контролю
    public static final AbilityIdentity IDENTITY = AbilityIdentity.of("marionettist_control");

    // Інстанс-реєстри (НЕ static): екземпляр здібності один на пасвей — це і є правильний скоуп
    // спільного стану всіх маріонеток/посесій. Раніше static → витік і неможливість GC.
    private final Map<UUID, List<ItemStack>> originalInventories = new ConcurrentHashMap<>(); // оригінальний інвентар перед входом
    private final Map<UUID, Integer>         currentPossession   = new ConcurrentHashMap<>(); // casterId -> npcId (int)
    private final Map<Integer, UUID>         possessionByNpc     = new ConcurrentHashMap<>(); // npcId (int) -> casterId

    private final Map<UUID, ThreadSession>        activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>>         marionetteNpcs = new ConcurrentHashMap<>(); // casterId -> набір npcId (кілька маріонеток)
    private final Map<UUID, BeyonderSnapshot>     possessions    = new ConcurrentHashMap<>();
    private final Map<UUID, String>               originalDisplayNames = new ConcurrentHashMap<>(); // casterId -> справжній нік до посесії
    private final Map<UUID, Double>               preMaxHealth   = new ConcurrentHashMap<>();         // casterId -> макс. HP основного тіла до посесії
    private final Map<Integer, UUID>              marionetteOwner = new ConcurrentHashMap<>();       // npcId -> власник (завжди)
    private final Map<UUID, BukkitTask>           possessionMonitors = new ConcurrentHashMap<>();    // casterId -> тікер дистанції
    private final Map<Integer, Long>              strandedNpcs    = new ConcurrentHashMap<>();       // npcId -> час остаточної смерті (ms)

    // Глобальні (не прив'язані до кастера) сервіси — безпечно тримати у фоновому таску.
    private volatile IGlowingContext  glowingRef;
    private volatile IBeyonderContext beyonderRef;
    private volatile BukkitTask       masterTask; // glow-рефреш + strand recovery/death

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
        captureRefs(ctx);

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

        int limit = marionetteLimit(ctx.getCasterBeyonder());
        if (countLivingMarionettes(casterId) >= limit)
            return AbilityResult.failure("§cЛіміт маріонеток (" + limit +
                    ") вичерпано. Підвищіть засвоєння або звільніть маріонетку.");

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

        // HP цілі (разом із бонусом від послідовності) — щоб маріонетка успадкувала його.
        double maxHealth = 20.0;
        AttributeInstance mhAttr = target.getAttribute(Attribute.MAX_HEALTH);
        if (mhAttr != null) maxHealth = mhAttr.getValue();
        final double fMaxHealth = maxHealth;
        final double fHealth    = Math.max(1.0, Math.min(target.getHealth(), maxHealth));

        ctx.effects().playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.5f);
        ctx.effects().playSphereEffect(loc.clone().add(0, 1, 0), 2.0, Particle.SOUL_FIRE_FLAME, 40);
        ctx.effects().spawnParticle(Particle.SQUID_INK,
                loc.clone().add(0, 1, 0), 60, 0.6, 0.8, 0.6);

        // Речі цілі вже захоплені в маріонетку — очищаємо інвентар ПЕРЕД смертю,
        // щоб вони не випадали на землю (інакше дюп).
        if (target instanceof Player invOwner) {
            invOwner.getInventory().clear();
            invOwner.getInventory().setArmorContents(null);
            invOwner.getInventory().setItemInOffHand(null);
        }
        ctx.entity().damage(target.getUniqueId(), 10_000.0);

        ctx.scheduling().scheduleDelayed(() -> {
            if (!ctx.playerData().isOnline(casterId)) return;

            // Ім'я NPC = ім'я цілі: інші гравці бачать звичайного гравця, не "Маріонетку".
            // Власник упізнає свої маріонетки лише за світінням-контуром.
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);

            if (isPlayer)
                npc.getOrAddTrait(SkinTrait.class).setSkinName(name, true);

            npc.spawn(loc);

            // Маріонетку можна бити та вбити (Citizens-NPC за замовчуванням невразливі).
            npc.setProtected(false);

            // Маріонетка успадковує HP цілі (разом із бонусом від послідовності).
            Entity npcEntity = npc.getEntity();
            if (npcEntity instanceof LivingEntity le) {
                AttributeInstance attr = le.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) attr.setBaseValue(fMaxHealth);
                le.setHealth(Math.min(fHealth, fMaxHealth));
            }

            MarionetteMinionTrait trait = new MarionetteMinionTrait();
            trait.initialise(casterId, name, tBeyonder, inv);
            trait.setSkin(fSkinValue, fSkinSignature);
            trait.setCapturedHealth(fHealth, fMaxHealth);
            npc.addTrait(trait);

            copyEquipmentToNpc(npc, target);

            marionetteNpcs.computeIfAbsent(casterId, k -> ConcurrentHashMap.newKeySet())
                    .add(npc.getId());
            marionetteOwner.put(npc.getId(), casterId);
            applyGlow(npc, casterId, GLOW_MARIONETTE); // світіння лише для власника
            ensureMasterTask();

            ctx.messaging().sendMessage(casterId,
                    "§5[Маріонетист] §f" + name + " §5перетворена на вашу маріонетку!");
            ctx.messaging().sendMessageToActionBar(casterId,
                    Component.text("Shift+ПКМ — увійти в маріонетку", NamedTextColor.LIGHT_PURPLE));
            // Смерть маріонетки обробляє єдиний постійний MarionetteLifecycleListener
            // (працює і для свіжих, і для відновлених після рестарту маріонеток).
        }, 20L);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Possession toggle (Shift+RightClick)
    // ════════════════════════════════════════════════════════════════════════

    private AbilityResult handlePossessionToggle(IAbilityContext ctx, UUID casterId) {
        // Якщо вже всередині маріонетки — Shift+ПКМ виходить (тоггл).
        if (currentPossession.containsKey(casterId)) {
            swapOut(ctx, casterId, false);
            return AbilityResult.success();
        }

        // Інакше — вселяємось у найближчу живу маріонетку. Вибір конкретної — окрема здібність (меню).
        NPC npc = nearestMarionette(ctx, casterId);
        if (npc == null)
            return AbilityResult.failure("§cУ вас немає маріонетки.");

        MarionetteMinionTrait trait = npc.getTraitNullable(MarionetteMinionTrait.class);
        if (trait == null)
            return AbilityResult.failure("§cДані маріонетки пошкоджені.");

        if (!withinControlRange(ctx, casterId, npc))
            return AbilityResult.failure("§cЗанадто далеко: маріонетка поза радіусом контролю ("
                    + (int) controlRange(ctx.getCasterBeyonder()) + " блоків).");

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
        captureRefs(ctx);

        // Зберігаємо повний знімок інвентаря гравця (storage + броня + друга рука).
        originalInventories.put(casterId, captureFullInventory(ctx.getCasterPlayer()));

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

        // Гравець "стає" тілом маріонетки: переймаємо її макс. HP (з бонусом послідовності цілі).
        // Основне тіло — завжди Fool (Marionettist), а Fool не має HP-пасивів, тож ніщо це не перезапише.
        applyPossessionMaxHealth(ctx.getCasterPlayer(), trait);

        // Тіло-NPC показує спорядження гравця (поки гравець ще у власному тілі) — ДО заміни інвентаря.
        updateNpcInventoryFromPlayer(npc, ctx.getCasterPlayer());

        // Гравець "стає" маріонеткою: вдягаємо її броню+інвентар зі збереженням слотів
        // (меню-айтем лишається у слоті 9, броня вдягається коректно).
        applyFullInventory(ctx.getCasterPlayer(), trait.getCapturedInventory());

        // Позиційний своп
        ctx.entity().teleport(casterId, npcLoc.clone());
        npc.teleport(castLoc.clone(),
                org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);

        // Керування (вихід / перемикання) тепер живе в окремій вкладці меню «Містичні Здібності»,
        // а не у вигляді предметів. Гарантуємо, що предмет-меню присутній (для доступу до вкладки).
        ensureMenuItem(ctx.getCasterPlayer(), originalInventories.get(casterId));

        spawnSwapEffects(ctx, npcLoc, castLoc);

        // Підміна особистості гравця на маріонетку: нік у чаті/ТАБі + скін.
        Player casterPlayer = ctx.getCasterPlayer();
        String marionetteName = trait.getOriginalPlayerName();
        if (casterPlayer != null && marionetteName != null) {
            originalDisplayNames.put(casterId, casterPlayer.getDisplayName());
            casterPlayer.setDisplayName(marionetteName);   // нік у чаті
            casterPlayer.setPlayerListName(marionetteName); // нік у ТАБі (server-side fallback)
        }
        if (casterPlayer != null && trait.getSkinTextureValue() != null) {
            // Скін + ім'я над головою/у ТАБі для інших гравців (PacketEvents).
            SkinDisguiseService.disguise(casterPlayer,
                    trait.getSkinTextureValue(), trait.getSkinTextureSignature(), marionetteName);
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

        // NPC тепер = ваше основне тіло: зелене світіння лише для вас + моніторинг дистанції.
        applyGlow(npc, casterId, GLOW_BODY);
        startPossessionMonitor(ctx, casterId, npcId);
    }

    // ── Swap OUT ──────────────────────────────────────────────────────────────

    // Виправлений swapOut метод
    private void swapOut(IAbilityContext ctx, UUID casterId, boolean forced) {
        stopPossessionMonitor(casterId);

        // Повертаємо власний макс. HP основного тіла — БЕЗУМОВНО і ПЕРШИМ, щоб усі шляхи виходу
        // (меню/предмет/смерть/дисконект/вимкнення) гарантовано скинули "маріонеткове" HP назад.
        restorePossessionMaxHealth(casterId);

        Integer npcId = currentPossession.remove(casterId);  // Integer
        if (npcId != null) possessionByNpc.remove(npcId);

        BeyonderSnapshot snap = possessions.remove(casterId);

        // Видаляємо предмети керування (вихід + меню), щоб вони не зберігались у маріонетці
        removeSwapBackItem(ctx, casterId);
        removeSwapMenuItem(ctx, casterId);

        if (snap == null) return;

        Player casterPlayerForSkin = ctx.getCasterPlayer();
        if (casterPlayerForSkin != null) {
            SkinDisguiseService.undisguise(casterPlayerForSkin);
            // Повертаємо справжній нік у чаті/ТАБі.
            String origName = originalDisplayNames.remove(casterId);
            if (origName != null) casterPlayerForSkin.setDisplayName(origName);
            casterPlayerForSkin.setPlayerListName(null);
        }

        Beyonder caster = ctx.beyonder().getBeyonder(casterId);

        NPC npc = npcId != null ? CitizensAPI.getNPCRegistry().getById(npcId) : null;  // getById(int)
        Location castLoc = ctx.playerData().getCurrentLocation(casterId);

        // ПОВНА ПЕРСИСТЕНТНІСТЬ: поки гравець ще "у" маріонетці — зберігаємо її інвентар та
        // спорядження назад у саму маріонетку (ДО відновлення власного інвентаря гравця).
        if (!forced && npc != null && npc.isSpawned()) {
            MarionetteMinionTrait trait = npc.getTraitNullable(MarionetteMinionTrait.class);
            if (trait != null) {
                trait.setCapturedInventory(captureFullInventory(ctx.getCasterPlayer()));
            }
            updateNpcInventoryFromPlayer(npc, ctx.getCasterPlayer());
        }

        // Відновлюємо власний інвентар гравця (повний знімок зі слотами) та особистість.
        List<ItemStack> originalInv = originalInventories.remove(casterId);
        if (originalInv != null) {
            applyFullInventory(ctx.getCasterPlayer(), originalInv);
        }
        if (caster != null) caster.restoreIdentity(snap);

        if (!forced && npc != null && npc.isSpawned() && castLoc != null) {
            Location npcLoc = npc.getStoredLocation();
            ctx.entity().teleport(casterId, npcLoc != null ? npcLoc.clone() : castLoc);
            npc.teleport(castLoc.clone(),
                    org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);

            spawnSwapEffects(ctx, npcLoc != null ? npcLoc : castLoc, castLoc);
            ctx.messaging().sendMessage(casterId,
                    "§5[Маріонетист] §fВи повернулись у своє тіло.");

            // NPC знову став вільною маріонеткою — повертаємо фіолетове світіння для власника.
            applyGlow(npc, casterId, GLOW_MARIONETTE);
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

    /** Повний знімок інвентаря (storage+броня+друга рука) зі збереженням слотів (null = порожньо). */
    private List<ItemStack> captureFullInventory(Player player) {
        List<ItemStack> items = new ArrayList<>();
        if (player == null) return items;
        for (ItemStack item : player.getInventory().getContents()) {
            items.add(item == null ? null : item.clone());
        }
        return items;
    }

    /** Відновлює повний інвентар гравця зі збереженням слотів (броня вдягається, меню лишається у слоті 9). */
    private void applyFullInventory(Player player, List<ItemStack> contents) {
        if (player == null) return;
        player.getInventory().clear();
        if (contents == null || contents.isEmpty()) return;
        player.getInventory().setContents(contents.toArray(new ItemStack[0]));
    }

    private static boolean isAbilityMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return new NBTBuilder(item).getBoolean(item, "ability_menu_item").orElse(false);
    }

    /**
     * Гарантує, що у гравця є предмет-меню «Містичні Здібності» (доступ до вкладки маріонетки).
     * Якщо у поточному інвентарі його немає — переносить його з власного інвентаря гравця.
     */
    private void ensureMenuItem(Player player, List<ItemStack> ownInventory) {
        if (player == null) return;
        for (ItemStack it : player.getInventory().getContents()) {
            if (isAbilityMenuItem(it)) return; // вже є
        }
        if (ownInventory == null) return;
        for (ItemStack it : ownInventory) {
            if (isAbilityMenuItem(it)) {
                ItemStack displaced = player.getInventory().getItem(9);
                player.getInventory().setItem(9, it.clone());
                if (displaced != null && displaced.getType() != Material.AIR) {
                    player.getInventory().addItem(displaced);
                }
                return;
            }
        }
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

    /** Скидає речі маріонетки на землю, пропускаючи службові предмети (меню/керування/здібності тіла). */
    private void dropMarionetteItems(Location loc, List<ItemStack> items) {
        if (loc == null || loc.getWorld() == null || items == null) return;
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (isAbilityMenuItem(item) || isMainBodyAbilityItem(item)
                    || isSwapBackItem(item) || isSwapMenuItem(item)) continue;
            loc.getWorld().dropItemNaturally(loc, item.clone());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // NPC death listener
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Обробка смерті маріонетки. Викликається єдиним постійним {@code MarionetteLifecycleListener}
     * на {@code NPCDeathEvent} — отже працює і для свіжих, і для відновлених після рестарту маріонеток.
     *
     * @param e        подія смерті NPC-маріонетки
     * @param ownerCtx контекст власника, якщо він онлайн (для swapOut/повідомлення); інакше {@code null}
     */
    public void onMarionetteDeath(NPCDeathEvent e, IAbilityContext ownerCtx) {
        NPC npc = e.getNPC();
        final int npcId = npc.getId();

        e.getDrops().clear();   // прибрати дефолтні (порожні) дропи NPC
        e.setDroppedExp(0);

        Location deathLoc = npc.getStoredLocation();

        // Власник: якщо саме керує — з реєстру посесій; інакше — звичайний власник.
        UUID possessingOwner = possessionByNpc.get(npcId);
        UUID ownerId = (possessingOwner != null) ? possessingOwner : marionetteOwner.get(npcId);

        List<ItemStack> toDrop;
        if (possessingOwner != null) {
            // Маріонетку вбили, поки нею керують — речі зараз в інвентарі гравця.
            Player possessor = Bukkit.getPlayer(possessingOwner);
            toDrop = (possessor != null) ? captureFullInventory(possessor) : new ArrayList<>();
            if (ownerCtx != null) {
                swapOut(ownerCtx, possessingOwner, true); // повертає гравця у власне тіло/інвентар
            }
        } else {
            MarionetteMinionTrait trait = npc.getTraitNullable(MarionetteMinionTrait.class);
            toDrop = (trait != null) ? new ArrayList<>(trait.getCapturedInventory()) : new ArrayList<>();
        }

        // Речі маріонетки випадають на місці смерті.
        dropMarionetteItems(deathLoc, toDrop);

        if (ownerId != null) removeGlow(npcId, ownerId);
        cleanupNpcRecords(npcId, ownerId);
        npc.destroy();

        if (ownerCtx != null && ownerId != null) {
            ownerCtx.messaging().sendMessage(ownerId, "§5[Маріонетист] §7Вашу маріонетку знищено.");
        } else if (ownerId != null) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null) owner.sendMessage("§5[Маріонетист] §7Вашу маріонетку знищено.");
        }
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
        removeTaggedItem(ctx, SWAP_BACK_NBT);
    }

    /** Предмет для відкриття меню перемикання — доступний поки гравець керує маріонеткою. */
    private void giveSwapMenuItem(IAbilityContext ctx, UUID casterId) {
        Player player = ctx.getCasterPlayer();
        if (player == null) return;

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Перемкнути маріонетку");
        meta.setLore(List.of(
                ChatColor.GRAY + "ПКМ — меню ваших маріонеток",
                ChatColor.DARK_GRAY + "Здібність Маріонетиста"
        ));
        item.setItemMeta(meta);

        NBTBuilder nbt = new NBTBuilder(item);
        item = nbt.setBoolean(SWAP_MENU_NBT, true).build();

        // Кладемо в 7 слот (поряд із предметом виходу у 8).
        ItemStack displaced = player.getInventory().getItem(7);
        player.getInventory().setItem(7, item);
        if (displaced != null && displaced.getType() != Material.AIR) {
            player.getInventory().addItem(displaced);
        }
    }

    private void removeSwapMenuItem(IAbilityContext ctx, UUID casterId) {
        removeTaggedItem(ctx, SWAP_MENU_NBT);
    }

    private void removeTaggedItem(IAbilityContext ctx, String nbtKey) {
        Player player = ctx.getCasterPlayer();
        if (player == null) return;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta()) continue;

            NBTBuilder nbt = new NBTBuilder(item);
            if (nbt.getBoolean(item, nbtKey).orElse(false)) {
                player.getInventory().setItem(i, null);
                break;
            }
        }
    }

    public static boolean isSwapMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        NBTBuilder nbt = new NBTBuilder(item);
        return nbt.getBoolean(item, SWAP_MENU_NBT).orElse(false);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Swap menu (triumph-gui через ctx.ui()) — спільне для здібності та предмета
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Відкриває меню вибору маріонетки: ЛКМ — вселитись, ПКМ — відпустити (з підтвердженням).
     * Повертає {@code false}, якщо доступних маріонеток немає (щоб не знімати ресурс/кулдаун).
     */
    public boolean openSwapMenu(IAbilityContext ctx) {
        UUID casterId = ctx.getCasterId();
        Player player = ctx.getCasterPlayer();
        if (player == null) return false;

        List<NPC> marionettes = getSelectableMarionettes(casterId);
        if (marionettes.isEmpty()) {
            ctx.messaging().sendMessage(casterId, "§cУ вас немає доступних маріонеток.");
            return false;
        }

        int rows = Math.min(6, Math.max(1, (marionettes.size() + 8) / 9));
        Gui gui = Gui.gui()
                .title(Component.text("Ваші маріонетки"))
                .rows(rows)
                .disableAllInteractions()
                .create();

        int slot = 0;
        for (NPC npc : marionettes) {
            int npcId = npc.getId();
            gui.setItem(slot++, new GuiItem(buildMarionetteIcon(npc, casterId), event -> {
                event.setCancelled(true);
                player.closeInventory();
                if (event.isRightClick()) {
                    openReleaseConfirm(ctx, npc);     // відпустити (з підтвердженням)
                } else {
                    swapToMarionette(ctx, npcId);     // вселитись
                }
            }));
        }
        gui.open(player);
        return true;
    }

    /** Друге меню — підтвердження звільнення конкретної маріонетки. */
    private void openReleaseConfirm(IAbilityContext ctx, NPC npc) {
        Player player = ctx.getCasterPlayer();
        if (player == null) return;

        MarionetteMinionTrait trait = npc.getTraitNullable(MarionetteMinionTrait.class);
        String name = (trait != null && trait.getOriginalPlayerName() != null)
                ? trait.getOriginalPlayerName() : npc.getName();
        int npcId = npc.getId();

        Gui gui = Gui.gui()
                .title(Component.text("Відпустити " + name + "?"))
                .rows(1)
                .disableAllInteractions()
                .create();

        ItemStack yes = new ItemStack(Material.LIME_WOOL);
        ItemMeta ym = yes.getItemMeta();
        if (ym != null) {
            ym.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Так, відпустити назавжди");
            ym.setLore(List.of(ChatColor.GRAY + "Маріонетку буде знищено остаточно."));
            yes.setItemMeta(ym);
        }

        ItemStack no = new ItemStack(Material.RED_WOOL);
        ItemMeta nm = no.getItemMeta();
        if (nm != null) {
            nm.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Ні, повернутись назад");
            no.setItemMeta(nm);
        }

        gui.setItem(3, new GuiItem(yes, e -> {
            e.setCancelled(true);
            player.closeInventory();
            releaseMarionette(ctx, npcId);
        }));
        gui.setItem(5, new GuiItem(no, e -> {
            e.setCancelled(true);
            player.closeInventory();
            openSwapMenu(ctx);
        }));
        gui.open(player);
    }

    private ItemStack buildMarionetteIcon(NPC npc, UUID casterId) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta == null) return head;

        MarionetteMinionTrait trait = npc.getTraitNullable(MarionetteMinionTrait.class);
        String name = (trait != null) ? trait.getOriginalPlayerName() : npc.getName();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD +
                (name == null ? "Маріонетка" : name));

        List<String> lore = new ArrayList<>();
        if (trait != null && trait.wasBeyonder()) {
            lore.add(ChatColor.GRAY + "Шлях: " + ChatColor.AQUA + trait.getCapturedPathway().getName());
            lore.add(ChatColor.GRAY + "Послідовність: " + ChatColor.AQUA + trait.getCapturedSequence().level());
        }
        if (trait != null) {
            lore.add(ChatColor.GRAY + "HP: " + ChatColor.RED + (int) trait.getCapturedMaxHealth());
        }
        if (isPossessing(casterId, npc.getId())) {
            lore.add(ChatColor.GREEN + "▶ Поточне тіло");
        }
        lore.add(" ");
        lore.add(ChatColor.YELLOW + "ЛКМ — вселитись");
        lore.add(ChatColor.RED + "ПКМ — відпустити (з підтвердженням)");
        meta.setLore(lore);

        head.setItemMeta(meta);
        return head;
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
            // повний вміст зі збереженням слотів (null = порожньо) для повної персистентності
            for (ItemStack i : p.getInventory().getContents()) {
                list.add(i == null ? null : i.clone());
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

    // ════════════════════════════════════════════════════════════════════════
    // Multi-marionette: ліміт, реєстр, дистанція, strand, glow, швидкий свап
    // ════════════════════════════════════════════════════════════════════════

    /** Скільки маріонеток дозволено: засвоєння <50% → 1, <100% → 2, 100% → 3. */
    private int marionetteLimit(Beyonder b) {
        double m = (b == null) ? 0.0 : b.getMasteryValue();
        if (m >= 100.0) return 3;
        if (m >= 50.0)  return 2;
        return 1;
    }

    /** Радіус контролю/свапу відносно основного тіла: <50% → 100 блоків, інакше → 200. */
    private double controlRange(Beyonder b) {
        double m = (b == null) ? 0.0 : b.getMasteryValue();
        return m >= 50.0 ? RANGE_HIGH : RANGE_LOW;
    }

    /** Ліміт рахує ВСІ живі маріонетки (зокрема strand'нуті — вони ще існують). */
    private int countLivingMarionettes(UUID casterId) {
        return getAllAliveMarionettes(casterId).size();
    }

    /** Усі живі маріонетки гравця (зокрема поза зоною контролю); чистить лише по-справжньому мертві записи. */
    public List<NPC> getAllAliveMarionettes(UUID casterId) {
        List<NPC> result = new ArrayList<>();
        Set<Integer> set = marionetteNpcs.get(casterId);
        if (set == null) return result;
        NPCRegistry reg = CitizensAPI.getNPCRegistry();
        set.removeIf(id -> {
            NPC n = reg.getById(id);
            // "Мертва" = знищена (зникла з реєстру). НЕ-спавнена (вивантажений чанк) — ще жива:
            // вона лишається в реєстрі Citizens і повернеться, коли чанк завантажиться. Інакше
            // маріонетку далеко від гравця помилково "загубило б" одразу після відновлення.
            boolean dead = (n == null);
            if (dead) { marionetteOwner.remove(id); strandedNpcs.remove(id); }
            return dead;
        });
        if (set.isEmpty()) {
            marionetteNpcs.remove(casterId);
            return result;
        }
        for (int id : set) {
            NPC n = reg.getById(id);
            if (n != null) result.add(n);
        }
        return result;
    }

    /**
     * Відновлення реєстру після рестарту сервера. Citizens сам відновлює NPC та їхні трейти з
     * {@code saves.yml}; цей метод повертає завантажену маріонетку в рантайм-реєстри здібності
     * (ліміт/меню/glow/смерть знову працюють). Викликається {@code MarionetteRestorer}.
     *
     * <p>Ідемпотентно: повторний виклик для того ж NPC не дублює записи. Strand-статус НЕ
     * відновлюється — після рестарту маріонетка одразу доступна (власник офлайн, відлік несправедливий).
     * Glow вмикається ліниво (майстер-тік), щойно власник наступного разу взаємодіє зі здібністю.
     */
    public void registerLoadedMarionette(int npcId, UUID ownerId) {
        if (ownerId == null) return;
        marionetteNpcs.computeIfAbsent(ownerId, k -> ConcurrentHashMap.newKeySet()).add(npcId);
        marionetteOwner.put(npcId, ownerId);
        ensureMasterTask();
    }

    /** Маріонетки, доступні в меню/для свапу — живі та НЕ поза зоною контролю (strand). */
    public List<NPC> getSelectableMarionettes(UUID casterId) {
        List<NPC> result = new ArrayList<>();
        for (NPC n : getAllAliveMarionettes(casterId)) {
            if (!strandedNpcs.containsKey(n.getId())) result.add(n);
        }
        return result;
    }

    /** Чи перебуває гравець саме в цій маріонетці зараз. */
    public boolean isPossessing(UUID casterId, int npcId) {
        Integer cur = currentPossession.get(casterId);
        return cur != null && cur == npcId;
    }

    /** Чи керує гравець зараз будь-якою маріонеткою (для вкладки в меню). */
    public boolean isPossessing(UUID casterId) {
        return currentPossession.containsKey(casterId);
    }

    /** Живий знімок ОСНОВНОГО ТІЛА під час контролю (особистість+духовність творця маріонетки). */
    public BeyonderSnapshot getMainBodySnapshot(UUID casterId) {
        return possessions.get(casterId);
    }

    /** Оновити знімок основного тіла (після витрати його духовності здібністю з вкладки). */
    public void setMainBodySnapshot(UUID casterId, BeyonderSnapshot snapshot) {
        if (snapshot != null) possessions.put(casterId, snapshot);
    }

    /** NBT-мітка предмета здібності основного тіла (щоб білити духовність тіла, а не маріонетки). */
    public static final String MAIN_BODY_ABILITY_NBT = "marionettist_main_body_ability";

    public static boolean isMainBodyAbilityItem(ItemStack item) {
        return mainBodyAbilityId(item).isPresent();
    }

    public static java.util.Optional<String> mainBodyAbilityId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return java.util.Optional.empty();
        return new NBTBuilder(item).getString(item, MAIN_BODY_ABILITY_NBT);
    }

    /** Знаходить здібність основного тіла за ідентичністю (зі знімка творця). */
    public Ability getMainBodyAbilityByIdentity(UUID casterId, String identityId) {
        BeyonderSnapshot snap = possessions.get(casterId);
        if (snap == null || identityId == null) return null;
        for (Ability a : snap.abilities()) {
            if (a.getIdentity() != null && identityId.equals(a.getIdentity().id())) return a;
        }
        return null;
    }

    /**
     * Виконує здібність ОСНОВНОГО ТІЛА під час контролю маріонетки: тимчасово відновлює
     * особистість+духовність творця, виконує (через {@code executeFn}) — списується духовність
     * основного тіла — зберігає оновлений знімок і повертає особистість маріонетки.
     */
    public AbilityResult useMainBodyAbility(UUID casterId, Ability ability,
                                            java.util.function.Supplier<AbilityResult> executeFn) {
        if (beyonderRef == null) return AbilityResult.failure("§cНемає звʼязку з основним тілом.");
        Beyonder beyonder = beyonderRef.getBeyonder(casterId);
        BeyonderSnapshot mainBody = possessions.get(casterId);
        if (beyonder == null || mainBody == null)
            return AbilityResult.failure("§cНемає звʼязку з основним тілом.");
        if (ability.getType() == AbilityType.ACTIVE
                && mainBody.spirituality().current() < ability.getSpiritualityCost())
            return AbilityResult.failure("§cНедостатньо духовності основного тіла!");

        BeyonderSnapshot marionetteSnap = beyonder.takeSnapshot();
        try {
            beyonder.restoreIdentity(mainBody);            // стаємо основним тілом
            AbilityResult result = executeFn.get();        // витрата духовності тіла
            possessions.put(casterId, beyonder.takeSnapshot()); // зберегти витрату
            return result;
        } finally {
            beyonder.restoreIdentity(marionetteSnap);       // повертаємось у маріонетку
        }
    }

    /** Активні здібності ОСНОВНОГО ТІЛА (творця) — для окремої вкладки маріонетки. */
    public List<Ability> getMainBodyActiveAbilities(UUID casterId) {
        BeyonderSnapshot snap = possessions.get(casterId);
        if (snap == null) return List.of();
        List<Ability> res = new ArrayList<>();
        for (Ability a : snap.abilities()) {
            if (a.getType() != AbilityType.ACTIVE) continue;
            // Виключаємо самі маріонеткові здібності, щоб не плодити рекурсію в меню.
            if (IDENTITY.equals(a.getIdentity()) || MarionetteSwapMenu.IDENTITY.equals(a.getIdentity())) continue;
            res.add(a);
        }
        return res;
    }

    private NPC nearestMarionette(IAbilityContext ctx, UUID casterId) {
        Location from = ctx.playerData().getCurrentLocation(casterId);
        List<NPC> all = getSelectableMarionettes(casterId);
        NPC best = null;
        double bestD = Double.MAX_VALUE;
        for (NPC n : all) {
            Location l = n.getStoredLocation();
            if (l == null || from == null || l.getWorld() != from.getWorld()) continue;
            double d = l.distanceSquared(from);
            if (d < bestD) { bestD = d; best = n; }
        }
        if (best == null && !all.isEmpty()) best = all.get(0); // інший світ — беремо будь-яку
        return best;
    }

    /** Дистанція вимірюється ВІД ОСНОВНОГО ТІЛА: при контролі — від тіла-NPC, інакше — від гравця. */
    private Location mainBodyLocation(IAbilityContext ctx, UUID casterId) {
        Integer cur = currentPossession.get(casterId);
        if (cur != null) {
            NPC body = CitizensAPI.getNPCRegistry().getById(cur);
            if (body != null) {
                Location l = body.getStoredLocation();
                if (l != null) return l;
            }
        }
        return ctx.playerData().getCurrentLocation(casterId);
    }

    /** Перевірка дистанції від основного тіла до маріонетки на момент входу/свапу. */
    private boolean withinControlRange(IAbilityContext ctx, UUID casterId, NPC npc) {
        Location from = mainBodyLocation(ctx, casterId);
        Location to   = npc.getStoredLocation();
        if (from == null || to == null || to.getWorld() != from.getWorld()) return false;
        double range = controlRange(ctx.getCasterBeyonder());
        return from.distance(to) <= range;
    }

    /**
     * Швидкий свап у вказану маріонетку. Якщо гравець уже в іншій — спершу виходить із неї
     * (зі збереженням її стану), потім вселяється у нову. Викликається з меню/предмета.
     */
    public void swapToMarionette(IAbilityContext ctx, int npcId) {
        UUID casterId = ctx.getCasterId();

        Integer cur = currentPossession.get(casterId);
        if (cur != null && cur == npcId) {
            ctx.messaging().sendMessage(casterId, "§7Ви вже керуєте цією маріонеткою.");
            return;
        }

        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null || !npc.isSpawned()) {
            ctx.messaging().sendMessage(casterId, "§cМаріонетку не знайдено.");
            return;
        }
        if (strandedNpcs.containsKey(npcId)) {
            ctx.messaging().sendMessage(casterId, "§cЦя маріонетка поза зоною контролю — підійдіть ближче.");
            return;
        }
        MarionetteMinionTrait trait = npc.getTraitNullable(MarionetteMinionTrait.class);
        if (trait == null) {
            ctx.messaging().sendMessage(casterId, "§cДані маріонетки пошкоджені.");
            return;
        }
        if (!withinControlRange(ctx, casterId, npc)) {
            ctx.messaging().sendMessage(casterId, "§cЗанадто далеко: маріонетка поза радіусом контролю ("
                    + (int) controlRange(ctx.getCasterBeyonder()) + " блоків).");
            return;
        }

        if (cur != null) {
            swapOut(ctx, casterId, false); // повертаємось у тіло, зберігаємо стан поточної
        }

        Location npcLoc  = npc.getStoredLocation();
        Location castLoc = ctx.playerData().getCurrentLocation(casterId);
        if (npcLoc == null || castLoc == null) {
            ctx.messaging().sendMessage(casterId, "§cНеможливо знайти позицію.");
            return;
        }
        swapIn(ctx, casterId, trait, npc, npcLoc, castLoc);
    }

    /** Назавжди звільнити (знищити) маріонетку. Якщо гравець у ній — спершу повертаємо в тіло. */
    public void releaseMarionette(IAbilityContext ctx, int npcId) {
        UUID casterId = ctx.getCasterId();
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);

        if (isPossessing(casterId, npcId)) {
            swapOut(ctx, casterId, false); // повернутись у тіло перед знищенням
            npc = CitizensAPI.getNPCRegistry().getById(npcId);
        }

        removeGlow(npcId, casterId);
        strandedNpcs.remove(npcId);
        marionetteOwner.remove(npcId);
        Set<Integer> set = marionetteNpcs.get(casterId);
        if (set != null) {
            set.remove(npcId);
            if (set.isEmpty()) marionetteNpcs.remove(casterId);
        }
        if (npc != null) npc.destroy();
        ctx.messaging().sendMessage(casterId, "§5[Маріонетист] §7Маріонетку звільнено назавжди.");
    }

    // ── Distance monitor (поки гравець керує маріонеткою) ──────────────────────

    private void startPossessionMonitor(IAbilityContext ctx, UUID casterId, int npcId) {
        stopPossessionMonitor(casterId);
        BukkitTask task = ctx.scheduling().scheduleRepeating(() -> {
            // Контроль ще активний саме цієї маріонетки?
            if (!isPossessing(casterId, npcId)) { stopPossessionMonitor(casterId); return; }
            if (!ctx.playerData().isOnline(casterId)) return;

            NPC body = CitizensAPI.getNPCRegistry().getById(npcId); // npc = ваше основне тіло
            Location bodyLoc = (body != null) ? body.getStoredLocation() : null;
            Location here    = ctx.playerData().getCurrentLocation(casterId);
            if (bodyLoc == null || here == null) return;

            double range = controlRange(ctx.getCasterBeyonder());
            boolean tooFar = (bodyLoc.getWorld() != here.getWorld()) || (here.distance(bodyLoc) > range);
            if (tooFar) {
                stopPossessionMonitor(casterId);
                ctx.messaging().sendMessage(casterId,
                        "§c[Маріонетист] Ви вийшли за межі контролю — вас викинуло з маріонетки!");
                swapOut(ctx, casterId, false);          // повертає у тіло, маріонетка лишається на місці
                strandMarionette(npcId, casterId);      // маріонетка тепер поза зоною
            }
        }, MONITOR_PERIOD_TICKS, MONITOR_PERIOD_TICKS);
        possessionMonitors.put(casterId, task);
    }

    private void stopPossessionMonitor(UUID casterId) {
        BukkitTask t = possessionMonitors.remove(casterId);
        if (t != null && !t.isCancelled()) t.cancel();
    }

    // ── Strand (поза зоною) + майстер-таск відновлення/смерті ──────────────────

    private void strandMarionette(int npcId, UUID ownerId) {
        strandedNpcs.put(npcId, System.currentTimeMillis() + STRAND_DEATH_MS);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            owner.sendMessage("§c[Маріонетист] Маріонетка поза зоною контролю та зникла з меню. " +
                    "Підійдіть ближче радіуса протягом §e10 хв§c, інакше вона загине назавжди.");
        }
        ensureMasterTask();
    }

    /** Майстер-таск (Bukkit напряму): glow-рефреш + відновлення/смерть strand-маріонеток. */
    private void ensureMasterTask() {
        if (masterTask != null && !masterTask.isCancelled()) return;
        MysteriesAbovePlugin plugin = JavaPlugin.getPlugin(MysteriesAbovePlugin.class);
        masterTask = Bukkit.getScheduler().runTaskTimer(plugin, this::masterTick,
                MASTER_PERIOD_TICKS, MASTER_PERIOD_TICKS);
    }

    private void masterTick() {
        if (marionetteOwner.isEmpty()) return;
        NPCRegistry reg = CitizensAPI.getNPCRegistry();
        long now = System.currentTimeMillis();

        for (Map.Entry<Integer, UUID> e : new ArrayList<>(marionetteOwner.entrySet())) {
            int  npcId   = e.getKey();
            UUID ownerId = e.getValue();
            NPC  npc     = reg.getById(npcId);

            if (npc == null) { // знищена (зникла з реєстру) — приберемо записи
                cleanupNpcRecords(npcId, ownerId);
                continue;
            }
            if (!npc.isSpawned()) { // вивантажений чанк — ще жива, просто пропускаємо тік (без glow)
                continue;
            }

            boolean stranded = strandedNpcs.containsKey(npcId);
            boolean possessed = isPossessing(ownerId, npcId);
            Player owner = Bukkit.getPlayer(ownerId);

            // glow-рефреш лише для власника
            if (owner != null && glowingRef != null && npc.getEntity() != null) {
                ChatColor color = stranded ? GLOW_STRANDED : (possessed ? GLOW_BODY : GLOW_MARIONETTE);
                glowingRef.setGlowing(npc.getEntity().getUniqueId(), ownerId, color);
            }

            if (!stranded) continue;

            // відновлення: власник підійшов ближче (радіус − 1)
            Location npcLoc = npc.getStoredLocation();
            if (owner != null && npcLoc != null && owner.getWorld() == npcLoc.getWorld()) {
                double range = controlRange(beyonderRef != null ? beyonderRef.getBeyonder(ownerId) : null);
                if (owner.getLocation().distance(npcLoc) <= range - 1) {
                    strandedNpcs.remove(npcId);
                    owner.sendMessage("§a[Маріонетист] Маріонетка повернулась у зону контролю — знову доступна в меню.");
                    continue;
                }
            }

            // остаточна смерть після таймауту
            Long deadline = strandedNpcs.get(npcId);
            if (deadline != null && now >= deadline) {
                removeGlow(npcId, ownerId);
                cleanupNpcRecords(npcId, ownerId);
                npc.destroy();
                if (owner != null)
                    owner.sendMessage("§4[Маріонетист] Покинута маріонетка загинула назавжди.");
            }
        }
    }

    private void cleanupNpcRecords(int npcId, UUID ownerId) {
        strandedNpcs.remove(npcId);
        marionetteOwner.remove(npcId);
        Set<Integer> set = marionetteNpcs.get(ownerId);
        if (set != null) {
            set.remove(npcId);
            if (set.isEmpty()) marionetteNpcs.remove(ownerId);
        }
    }

    // ── Possession health (макс. HP тіла-маріонетки на час контролю) ───────────

    /** Переймає макс. HP маріонетки на гравця, зберігаючи попередній (для відновлення на виході). */
    private void applyPossessionMaxHealth(Player player, MarionetteMinionTrait trait) {
        if (player == null || trait == null) return;
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        double oldMax = attr.getBaseValue();
        preMaxHealth.put(player.getUniqueId(), oldMax);

        double target = trait.getCapturedMaxHealth();
        if (target < 1.0) target = 20.0;

        double pct = player.getHealth() / Math.max(1.0, oldMax); // зберігаємо відсоток здоров'я
        attr.setBaseValue(target);
        player.setHealth(Math.max(1.0, Math.min(target, target * pct)));
    }

    /** Повертає макс. HP основного тіла після виходу з маріонетки (ідемпотентно). */
    private void restorePossessionMaxHealth(UUID casterId) {
        Double oldMax = preMaxHealth.remove(casterId);
        if (oldMax == null) return;
        Player player = Bukkit.getPlayer(casterId);
        if (player == null) return;
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        double curMax = attr.getBaseValue();
        double pct = player.getHealth() / Math.max(1.0, curMax);
        attr.setBaseValue(oldMax);
        player.setHealth(Math.max(1.0, Math.min(oldMax, oldMax * pct)));
    }

    // ── Glow helpers (видиме крізь блоки, лише для власника) ───────────────────

    private void applyGlow(NPC npc, UUID ownerId, ChatColor color) {
        if (glowingRef == null || npc == null || npc.getEntity() == null) return;
        // Світіння — суто косметика. Воно НЕ має зривати swap-логіку: при виході гравця netty-канал
        // уже руйнується (зникає "packet_handler"), і GlowingEntities кидає NoSuchElementException.
        try {
            glowingRef.setGlowing(npc.getEntity().getUniqueId(), ownerId, color);
        } catch (Exception ignored) {
            // гравець офлайн/канал закривається — світіння просто не застосується
        }
    }

    private void removeGlow(int npcId, UUID ownerId) {
        if (glowingRef == null) return;
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc != null && npc.getEntity() != null) {
            try {
                glowingRef.removeGlowing(ownerId, npc.getEntity().getUniqueId());
            } catch (Exception ignored) {
                // гравець офлайн/канал закривається — нема чого знімати
            }
        }
    }

    private void captureRefs(IAbilityContext ctx) {
        if (glowingRef == null)  glowingRef  = ctx.glowing();
        if (beyonderRef == null) beyonderRef = ctx.beyonder();
    }

    private String entityName(LivingEntity e) {
        if (e instanceof Player p) return p.getName();
        if (e.getCustomName() != null) return ChatColor.stripColor(e.getCustomName());
        return e.getType().name();
    }

    /**
     * НЕ руйнівний. {@code cleanUp()} викликається через {@code Beyonder.cleanUpAbilities()} на
     * <b>кожен вихід гравця</b> (а здібність — спільний інстанс на весь пасвей), тож тут заборонено
     * чіпати NPC чи стан інших гравців: інакше вихід одного Fool-гравця знищив би маріонетки всіх.
     *
     * <p>Персистентність маріонеток забезпечують:
     * <ul>
     *   <li>вихід конкретного гравця з маріонетки при його дисконекті — {@code MarionetteLifecycleListener}
     *       (PlayerQuitEvent → {@link #exitIfPossessing});</li>
     *   <li>коректне вимкнення сервера — {@link #onPluginDisable()} (виклик із {@code onDisable}).</li>
     * </ul>
     */
    @Override
    public void cleanUp() {
        // Навмисно порожньо — див. javadoc вище.
    }

    /** Касти, що ЗАРАЗ керують маріонеткою (копія) — для коректного авто-виходу при вимкненні сервера. */
    public Set<UUID> getPossessingCasters() {
        return new HashSet<>(currentPossession.keySet());
    }

    /**
     * Повне вимкнення плагіна (з {@code onDisable}). Скасовує фонові таски та чистить рантайм-реєстри,
     * але <b>НЕ знищує NPC</b> — маріонетки зберігає Citizens у {@code saves.yml} і відновить при старті.
     * Авто-вихід гравців, що керують маріонетками, виконується ДО цього (в {@code onDisable}), щоб їхнє
     * тіло/інвентар/особистість коректно зберіглись.
     */
    public void onPluginDisable() {
        possessionMonitors.values().forEach(t -> { if (t != null && !t.isCancelled()) t.cancel(); });
        possessionMonitors.clear();
        if (masterTask != null && !masterTask.isCancelled()) masterTask.cancel();
        masterTask = null;
        activeSessions.values().forEach(ThreadSession::cancel); // скасувати завислі тікери фіксації
        activeSessions.clear();
        marionetteNpcs.clear();
        marionetteOwner.clear();
        strandedNpcs.clear();
        possessions.clear();
        currentPossession.clear();
        possessionByNpc.clear();
        originalInventories.clear();
        originalDisplayNames.clear();
        preMaxHealth.clear();
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