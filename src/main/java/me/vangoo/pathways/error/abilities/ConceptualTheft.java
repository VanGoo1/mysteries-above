package me.vangoo.pathways.error.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.DreamStealerTheft;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Посл. 5 (Крадій снів): «Концептуальна крадіжка» — Крадіжці більше не потрібна річ.
 * Ідеал, сон, чужий удар, здатність ходити чи саме серце стають предметами,
 * які можна підняти.
 *
 * <p>Режим → (ціль, якщо режим її потребує) → спроба. Шанс той самий, що й у Крадіжки сили
 * (знання шляху жертви піднімає, сильніша жертва опускає), невдача теж коштує духовності
 * й відкату.
 *
 * <p>Миттєві режими — «Ідеал» і «Атака» — слот вкраденого не займають; «Атака»
 * цілі не питає: чужий удар крадуть у себе. Тривалі («Сон», «Ходити», «Літати»,
 * «Говорити») лягають у той самий єдиний слот {@code TheftLedger}, що й Крадіжка сили,
 * під синтетичною міткою {@code Mode.slot} (відбирати нема чого, тож звірка слот просто
 * звільняє). «Серце» піде тим самим шляхом.
 */
public class ConceptualTheft extends ActiveAbility {

    /** Скільки цілей влазить у меню — далі список ріжеться по відстані. */
    private static final int MAX_TARGETS = 27;
    /** Скільки тіків тримається мітка над головою цілі, поки летить промінь. */
    private static final int MARK_DURATION_TICKS = 40;
    /** Ключ транзієнтного модифікатора здоров'я режиму «Серце» — по ньому ж і знімається. */
    private static final NamespacedKey HEART_KEY =
            NamespacedKey.fromString("mysteriesabove:conceptual_heart");

    private record Target(UUID id, String name, Player player, Beyonder beyonder, Location location) {}

    /** Поглинений чужий удар, що чекає на свій вихід: величина + німб, який його показує. */
    private record Charge(double amount, BukkitTask halo) {}

    /** Заряди живуть довше за один каст, тож реєстр — інстанс-поле, ніколи не static. */
    private final Map<UUID, Charge> charges = new ConcurrentHashMap<>();

    private enum Mode {
        IDEAL("Ідеал", Material.WITHER_ROSE, true, null,
                "Ціль на " + DreamStealerTheft.IDEAL_APATHY_SECONDS + " с втрачає волю:",
                "без бігу, без досвіду, з млявими руками.",
                "Частина її духовності перетікає до вас."),
        ATTACK("Атака", Material.SHIELD, false, null,
                DreamStealerTheft.ATTACK_WINDOW_SECONDS + " с оболонки: перший чужий удар",
                "гасне цілком, разом із побічними ефектами,",
                "а його сила лягає у ваш наступний удар."),
        DREAM("Сон", Material.PHANTOM_MEMBRANE, true, "conceptual:dream",
                "Ціль " + DreamStealerTheft.DREAM_HOLD_MILLIS / 60_000
                        + " хв не засне й не побачить віщого,",
                "а її сон лишається у вашому слоті.",
                "Наступний каст вливає той сон у будь-кого."),
        WALK("Ходити", Material.IRON_BOOTS, true, "conceptual:walk",
                "Ціль " + DreamStealerTheft.GENERAL_ABILITY_SECONDS + " с не зрушить із місця —",
                "хоч руки їй і лишились.",
                "Її крок ці секунди ваш."),
        FLY("Літати", Material.FEATHER, true, "conceptual:fly",
                "Політ і елітри цілі гаснуть на "
                        + DreamStealerTheft.GENERAL_ABILITY_SECONDS + " с —",
                "вона падає туди, де стояла.",
                "Ці секунди летите ви."),
        SPEAK("Говорити", Material.GOAT_HORN, true, "conceptual:speak",
                "Ціль " + DreamStealerTheft.GENERAL_ABILITY_SECONDS
                        + " с не вимовить закляття:",
                "жодної здібності, доки слово у вас.",
                "У чат вона пише, як писала."),
        HEART("Серце", Material.HEART_OF_THE_SEA, true, "conceptual:heart",
                (int) DreamStealerTheft.HEART_HEALTH_TRANSFER / 2 + " серця максимального здоров'я",
                "переходять від цілі до вас на "
                        + DreamStealerTheft.HEART_DURATION_SECONDS + " с.",
                "У слабкої жертви беруть менше — щоб було кому жити.");

        private final String title;
        private final Material icon;
        /** Чи потрібна режиму ціль: чужий удар крадуть у себе, не в когось. */
        private final boolean needsTarget;
        /** Мітка «тривалої» крадіжки в єдиному слоті; null — миттєвий режим, слот не займає. */
        private final AbilityIdentity slot;
        private final List<String> lore;

        Mode(String title, Material icon, boolean needsTarget, String slotKey, String... lore) {
            this.title = title;
            this.icon = icon;
            this.needsTarget = needsTarget;
            this.slot = slotKey == null ? null : AbilityIdentity.of(slotKey);
            this.lore = List.of(lore);
        }
    }

    @Override
    public String getName() {
        return "Концептуальна крадіжка";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Крадіжці більше не потрібна річ: у радіусі "
                + (int) DreamStealerTheft.CONCEPTUAL_RANGE + " блоків ви виймаєте з цілі саму суть.\n"
                + "§7«Ідеал» — ціль на " + DreamStealerTheft.IDEAL_APATHY_SECONDS
                + " с втрачає волю, а частина її духовності перетікає до вас.\n"
                + "§7«Атака» — " + DreamStealerTheft.ATTACK_WINDOW_SECONDS
                + " с оболонки: перший чужий удар гасне цілком, а його сила лягає у ваш наступний.\n"
                + "§7«Сон» — ціль " + DreamStealerTheft.DREAM_HOLD_MILLIS / 60_000
                + " хв не засне, а вкрадений сон ви вливаєте наступним кастом у будь-кого.\n"
                + "§7«Ходити», «Літати», «Говорити» — на "
                + DreamStealerTheft.GENERAL_ABILITY_SECONDS
                + " с сама здатність переходить від цілі до вас.\n"
                + "§7«Серце» — " + (int) DreamStealerTheft.HEART_HEALTH_TRANSFER / 2
                + " серця максимального здоров'я цілі б'ються у вас "
                + DreamStealerTheft.HEART_DURATION_SECONDS + " с.\n"
                + "§7Чим краще ви знаєте шлях жертви, тим легше взяти саме те, що треба.\n"
                + "§cНевдача теж забирає духовність і ставить відкат.";
    }

    @Override
    public int getSpiritualityCost() {
        return DreamStealerTheft.CONCEPTUAL_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return DreamStealerTheft.CONCEPTUAL_COOLDOWN_SECONDS;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        context.ui().openChoiceMenu(
                "Концептуальна крадіжка: що взяти",
                List.of(Mode.values()),
                this::createModeIcon,
                mode -> chooseTarget(context, mode));
        return AbilityResult.deferred();
    }

    /** «Атака» крадеться в себе — ціль питаємо лише в тих режимів, яким вона потрібна. */
    private void chooseTarget(IAbilityContext context, Mode mode) {
        if (!mode.needsTarget) {
            openAbsorptionWindow(context);
            return;
        }
        UUID casterId = context.getCasterId();
        boolean infusing = infusing(context, mode);
        if (mode.slot != null && !infusing && context.beyonder().hasStolenAbility(casterId)) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "Слот зайнятий чужим — украдене нікуди покласти.");
            return;
        }
        List<Target> targets = collectTargets(context);
        if (targets.isEmpty()) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "Поблизу немає в кого красти.");
            return;
        }
        context.ui().openChoiceMenu(
                infusing ? "Концептуальна крадіжка: кому віддати сон" : "Концептуальна крадіжка: ціль",
                targets,
                this::createTargetIcon,
                target -> attemptTheft(context, target, mode));
    }

    /** Другий каст «Сну» не краде, а віддає: у слоті вже лежить чужий сон. */
    private boolean infusing(IAbilityContext context, Mode mode) {
        return mode == Mode.DREAM && context.beyonder().holdsStolen(context.getCasterId(), Mode.DREAM.slot);
    }

    /** Будь-хто живий поруч: суть є і в гравця, і в створіння — Потойбічний він чи ні. */
    private List<Target> collectTargets(IAbilityContext context) {
        Location casterLocation = context.getCasterLocation();
        List<Target> targets = new ArrayList<>();
        for (LivingEntity entity : context.targeting().getNearbyEntities(DreamStealerTheft.CONCEPTUAL_RANGE)) {
            Player player = entity instanceof Player p ? p : null;
            if (player != null && player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            targets.add(new Target(entity.getUniqueId(),
                    ChatColor.stripColor(entity.getName()),
                    player,
                    context.beyonder().getBeyonder(entity.getUniqueId()),
                    entity.getLocation()));
        }
        targets.sort(Comparator.comparingDouble(t -> t.location().distanceSquared(casterLocation)));
        return targets.size() > MAX_TARGETS ? targets.subList(0, MAX_TARGETS) : targets;
    }

    private void attemptTheft(IAbilityContext context, Target target, Mode mode) {
        UUID casterId = context.getCasterId();
        Beyonder caster = context.getCasterBeyonder();
        Location victimLocation = context.playerData().getCurrentLocation(target.id());
        if (victimLocation == null) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Ціль зникла.");
            return;
        }
        if (!AbilityResourceConsumer.consumeResources(this, caster, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, caster);

        Location casterLocation = context.getCasterLocation();
        Color errorColor = PathwayBranding.liquidOf("Error");
        context.effects().playDustMark(victimLocation.clone().add(0, 2.2, 0),
                errorColor, 0.35, 1.0f, 14, MARK_DURATION_TICKS);
        context.effects().playSound(casterLocation, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.9f, 0.8f);
        context.effects().playSound(casterLocation, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8f, 0.7f);

        // Вливання сну нічого не виймає — опиратись нема чому, тож і кидка нема.
        boolean infusing = infusing(context, mode);
        if (!infusing && !roll(context, target)) {
            context.effects().playExplosionRingEffect(casterLocation, 1.5, Particle.DUST,
                    new Particle.DustOptions(errorColor, 1.2f));
            context.effects().playSound(casterLocation, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            context.messaging().sendMessage(casterId, ChatColor.RED + "Суть вислизнула — ви взяли порожнечу.");
            return;
        }

        Location from = (infusing ? casterLocation : victimLocation).clone().add(0, 1.4, 0);
        Location to = (infusing ? victimLocation : casterLocation).clone().add(0, 1.4, 0);
        context.effects().playTravelingBeam(from, to, errorColor,
                () -> {
                    switch (mode) {
                        case IDEAL -> stealIdeal(context, target);
                        case DREAM -> {
                            if (infusing) {
                                infuseDream(context, target);
                            } else {
                                stealDream(context, target);
                            }
                        }
                        case WALK, FLY, SPEAK -> stealGeneral(context, target, mode);
                        case HEART -> stealHeart(context, target);
                        case ATTACK -> { /* ціль не потрібна — сюди не доходить */ }
                    }
                    context.beyonder().updateSanityLoss(casterId, DreamStealerTheft.CONCEPTUAL_CORRUPTION);
                });
    }

    /** Не-Потойбічному нема чим опиратись концепції — опір рахуємо лише жертві зі шляхом. */
    private boolean roll(IAbilityContext context, Target target) {
        Beyonder victim = target.beyonder();
        if (victim == null) {
            return true;
        }
        int knownRecipes = context.beyonder()
                .getUnlockedRecipesCount(context.getCasterId(), victim.getPathway().getName());
        return Math.random() < DreamStealerTheft.successChance(knownRecipes, victim.getSequence());
    }

    /**
     * B1.7: без ідеалу немає й волі — апатія (слабкість, млявість, ні бігу, ні досвіду),
     * а частка ПОТОЧНОЇ духовності жертви перетікає до злодія (надлишок згорає).
     */
    private void stealIdeal(IAbilityContext context, Target target) {
        UUID casterId = context.getCasterId();
        UUID victimId = target.id();
        int ticks = DreamStealerTheft.IDEAL_APATHY_SECONDS * 20;

        context.entity().applyPotionEffect(victimId, PotionEffectType.WEAKNESS, ticks, 1);
        context.entity().applyPotionEffect(victimId, PotionEffectType.MINING_FATIGUE, ticks, 1);
        context.entity().setSprinting(victimId, false);
        if (target.player() != null) {
            context.events().subscribeToTemporaryEvent(victimId, PlayerToggleSprintEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(victimId) && e.isSprinting(),
                    e -> e.setCancelled(true), ticks);
            context.events().subscribeToTemporaryEvent(victimId, PlayerExpChangeEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(victimId),
                    e -> e.setAmount(0), ticks);
        }

        int transferred = drainSpirituality(context, target);

        Location casterLocation = context.getCasterLocation();
        Location victimLocation = context.playerData().getCurrentLocation(victimId);
        Color errorColor = PathwayBranding.liquidOf("Error");
        if (victimLocation != null) {
            context.effects().playVortexEffect(victimLocation.clone().add(0, 2.5, 0),
                    -2.5, 1.0, Particle.SMOKE, 40);
            context.effects().playFadingAura(victimLocation, Color.GRAY, 50);
            context.effects().playAlertHalo(victimLocation, errorColor);
            context.effects().playSound(victimLocation, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.5f);
        }
        context.effects().playGlowingDust(casterLocation.clone().add(0, 1, 0), errorColor);
        context.effects().playSound(casterLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.LIGHT_PURPLE
                + "Чужий ідеал згас" + (transferred > 0 ? " (+" + transferred + " духовності)" : "")));
        if (target.player() != null) {
            context.messaging().sendMessage(victimId, ChatColor.DARK_PURPLE
                    + "Ви більше не пам'ятаєте, заради чого все це.");
        }
    }

    /** Не-Потойбічному духовності не мати — тоді лишається сама апатія. */
    private int drainSpirituality(IAbilityContext context, Target target) {
        Beyonder victim = target.beyonder();
        Beyonder caster = context.getCasterBeyonder();
        if (victim == null) {
            return 0;
        }
        int transferred = DreamStealerTheft.idealSpiritualityTransfer(
                victim.getSpiritualityValue(),
                caster.getMaxSpirituality() - caster.getSpiritualityValue());
        if (transferred <= 0) {
            return 0;
        }
        victim.setSpirituality(victim.getSpirituality().decrement(transferred));
        caster.setSpirituality(caster.getSpirituality().increment(transferred));
        context.beyonder().updateBeyonder(target.id());
        context.beyonder().updateBeyonder(context.getCasterId());
        return transferred;
    }

    /**
     * B1.1, перша половина: сон вийнято — жертва не лягає в ліжко, доки він у чужому слоті.
     *
     * <p>Віщування снів ламається саме собою: {@code DreamTraversal} і {@code Guidance}
     * вимагають сплячої цілі, тож окремого гейта на дивінацію тут навмисно немає.
     */
    private void stealDream(IAbilityContext context, Target target) {
        UUID casterId = context.getCasterId();
        UUID victimId = target.id();
        context.beyonder().stealAbility(casterId, victimId, Mode.DREAM.slot,
                DreamStealerTheft.DREAM_HOLD_MILLIS, 0L, DreamStealerTheft.DREAM_HOLD_MILLIS);

        if (target.player() != null) {
            int blockTicks = (int) (DreamStealerTheft.DREAM_HOLD_MILLIS / 50L);
            context.events().subscribeToTemporaryEvent(victimId, PlayerBedEnterEvent.class,
                    e -> e.getPlayer().getUniqueId().equals(victimId),
                    e -> {
                        e.setCancelled(true);
                        context.messaging().sendMessage(victimId, ChatColor.DARK_PURPLE
                                + "Сон не приходить — його у вас забрали.");
                    }, blockTicks);
            context.messaging().sendMessage(victimId, ChatColor.DARK_PURPLE
                    + "Щось вийняли з вашої голови разом зі сном.");
        }

        Location casterLocation = context.getCasterLocation();
        Location victimLocation = context.playerData().getCurrentLocation(victimId);
        Color errorColor = PathwayBranding.liquidOf("Error");
        if (victimLocation != null) {
            context.effects().playRisingSpiral(victimLocation, 2.4, 0.7, errorColor, 40);
            context.effects().playSound(victimLocation, Sound.ENTITY_PHANTOM_AMBIENT, 0.8f, 0.7f);
        }
        context.effects().playOrbitingMotes(casterId, List.of(errorColor, Color.WHITE), 0.7, 60);
        context.effects().playSound(casterLocation, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 0.8f);
        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.LIGHT_PURPLE
                + "Чужий сон у вас — наступний каст «Сну» віддасть його"));
    }

    /**
     * B1.1, друга половина: вкрадений сон вливається в третю особу сценою, яку та не обирала
     * (вікі: «infuse scenes within the Dream»). Слот звільняється — сон витрачено.
     */
    private void infuseDream(IAbilityContext context, Target target) {
        UUID casterId = context.getCasterId();
        UUID victimId = target.id();
        context.beyonder().releaseStolen(casterId);

        int ticks = DreamStealerTheft.DREAM_INFUSION_SECONDS * 20;
        context.entity().applyPotionEffect(victimId, PotionEffectType.NAUSEA, ticks, 0);
        context.entity().applyPotionEffect(victimId, PotionEffectType.BLINDNESS, ticks, 0);
        context.entity().applyPotionEffect(victimId, PotionEffectType.SLOWNESS, ticks, 0);

        Location casterLocation = context.getCasterLocation();
        Location victimLocation = context.playerData().getCurrentLocation(victimId);
        Color errorColor = PathwayBranding.liquidOf("Error");
        if (victimLocation != null) {
            context.effects().playRisingSpiral(victimLocation, 2.4, 0.7, errorColor, 40);
            context.effects().playVortexEffect(victimLocation.clone().add(0, 2.5, 0),
                    -2.5, 1.0, Particle.SMOKE, 40);
            context.effects().playSound(victimLocation, Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.6f);
            context.effects().playSound(victimLocation, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 0.7f);
        }
        context.effects().playGlowingDust(casterLocation.clone().add(0, 1, 0), errorColor);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text(ChatColor.LIGHT_PURPLE + "Чужий сон перелито — слот вільний"));
        if (target.player() != null) {
            context.messaging().sendMessage(victimId, ChatColor.DARK_PURPLE
                    + "Наяву вас накриває чужий сон — і ви в ньому не господар.");
        }
    }

    /**
     * B1.3: у цілі виймають саму здатність — ходити, літати або промовляти закляття
     * (вікі: «the ability to walk, fly, speak»). Забране лягає в той самий єдиний слот
     * («those as well will take one of the Theft slots»), а ці секунди ним володіє злодій.
     *
     * <p>Слот звільняє звірка {@code TheftLedger} (сама здібність за синтетичною міткою не
     * шукається), а ефекти на жертві самозгасні — потион-ефекти й {@code AbilityLockManager}
     * тримають власний строк.
     */
    private void stealGeneral(IAbilityContext context, Target target, Mode mode) {
        UUID casterId = context.getCasterId();
        UUID victimId = target.id();
        int seconds = DreamStealerTheft.GENERAL_ABILITY_SECONDS;
        int ticks = seconds * 20;
        long holdMillis = seconds * 1000L;
        context.beyonder().stealAbility(casterId, victimId, mode.slot, holdMillis, 0L, holdMillis);

        switch (mode) {
            case WALK -> {
                context.entity().applyPotionEffect(victimId, PotionEffectType.SLOWNESS, ticks, 255);
                context.entity().applyPotionEffect(victimId, PotionEffectType.JUMP_BOOST, ticks, 250);
                context.entity().applyPotionEffect(casterId, PotionEffectType.SPEED, ticks, 1);
            }
            case FLY -> {
                denyFlight(context, victimId, ticks);
                grantFlight(context, casterId, ticks);
            }
            case SPEAK -> context.cooldown().lockAbilities(victimId, seconds);
            default -> { /* решта режимів сюди не доходить */ }
        }

        Location casterLocation = context.getCasterLocation();
        Location victimLocation = context.playerData().getCurrentLocation(victimId);
        Color errorColor = PathwayBranding.liquidOf("Error");
        if (victimLocation != null) {
            context.effects().playGroundTrail(victimLocation, casterLocation, errorColor, 60);
            context.effects().playCircleEffect(victimLocation, 1.2, Particle.SMOKE, 40);
            context.effects().playSound(victimLocation, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.6f);
        }
        context.effects().playGlowingDust(casterLocation.clone().add(0, 1, 0), errorColor);
        context.effects().playSound(casterLocation, Sound.ITEM_TRIDENT_RETURN, 0.9f, 1.1f);
        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.LIGHT_PURPLE
                + "Чужа здатність «" + mode.title.toLowerCase() + "» тепер ваша (" + seconds + " с)"));
        if (target.player() != null) {
            context.messaging().sendMessage(victimId, ChatColor.DARK_PURPLE + switch (mode) {
                case WALK -> "Ноги вас не слухають — ходити більше не ваше.";
                case FLY -> "Небо вас не тримає — політ у вас забрали.";
                default -> "Слова закляття не даються — говорити більше не ваше.";
            });
        }
    }

    /**
     * B1.6: органів у Майнкрафті немає, тож найближче до «частини життя» — сам максимум
     * здоров'я. Серце жертви на хвилину б'ється в чужих грудях.
     *
     * <p>Модифікатор навмисно ТРАНЗІЄНТНИЙ: базове значення лишається за
     * {@code PhysicalEnhancement} (воно рухає {@code setBaseValue}), а те, що транзієнтне
     * не персиститься, робить рестарт власним запобіжником — залишок зникає сам.
     */
    private void stealHeart(IAbilityContext context, Target target) {
        UUID casterId = context.getCasterId();
        UUID victimId = target.id();
        AttributeInstance victimHealth = maxHealth(victimId);
        AttributeInstance casterHealth = maxHealth(casterId);
        if (victimHealth == null || casterHealth == null) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Серця в цілі не намацати.");
            return;
        }
        double taken = DreamStealerTheft.heartTransfer(victimHealth.getValue());
        if (taken <= 0) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "У цілі вже нема чого виймати.");
            return;
        }

        int seconds = DreamStealerTheft.HEART_DURATION_SECONDS;
        long holdMillis = seconds * 1000L;
        context.beyonder().stealAbility(casterId, victimId, Mode.HEART.slot, holdMillis, 0L, holdMillis);
        shiftMaxHealth(victimId, -taken);
        shiftMaxHealth(casterId, taken);
        context.scheduling().scheduleDelayed(() -> {
            restoreMaxHealth(victimId);
            restoreMaxHealth(casterId);
        }, seconds * 20);

        Location casterLocation = context.getCasterLocation();
        Location victimLocation = context.playerData().getCurrentLocation(victimId);
        Color errorColor = PathwayBranding.liquidOf("Error");
        Color heartColor = Color.fromRGB(180, 20, 30);
        if (victimLocation != null) {
            context.effects().playAlertHalo(victimLocation, heartColor);
            context.effects().playDustMark(victimLocation.clone().add(0, 2.2, 0),
                    heartColor, 0.35, 1.0f, 14, MARK_DURATION_TICKS);
            context.effects().playSound(victimLocation, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
            context.effects().playSound(victimLocation, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.8f, 0.7f);
        }
        context.effects().playGlowingDust(casterLocation.clone().add(0, 1, 0), errorColor);
        context.effects().playSound(casterLocation, Sound.ITEM_TRIDENT_RETURN, 0.9f, 0.9f);
        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.LIGHT_PURPLE
                + "Чуже серце б'ється у вас (+" + String.format("%.0f", taken / 2) + " сердець, "
                + seconds + " с)"));
        if (target.player() != null) {
            context.messaging().sendMessage(victimId, ChatColor.DARK_PURPLE
                    + "У грудях порожньо — щось живе з вас вийняли.");
        }
    }

    private AttributeInstance maxHealth(UUID entityId) {
        return Bukkit.getEntity(entityId) instanceof LivingEntity living
                ? living.getAttribute(Attribute.MAX_HEALTH) : null;
    }

    private void shiftMaxHealth(UUID entityId, double delta) {
        AttributeInstance attribute = maxHealth(entityId);
        if (attribute == null) {
            return;
        }
        attribute.addTransientModifier(
                new AttributeModifier(HEART_KEY, delta, AttributeModifier.Operation.ADD_NUMBER));
        clampHealth(entityId, attribute);
    }

    /** Ідемпотентно: якщо модифікатора вже нема (релог, рестарт), знімати нема чого. */
    private void restoreMaxHealth(UUID entityId) {
        AttributeInstance attribute = maxHealth(entityId);
        if (attribute == null) {
            return;
        }
        attribute.getModifiers().stream()
                .filter(modifier -> HEART_KEY.equals(modifier.getKey()))
                .toList()
                .forEach(attribute::removeModifier);
        clampHealth(entityId, attribute);
    }

    private void clampHealth(UUID entityId, AttributeInstance attribute) {
        if (Bukkit.getEntity(entityId) instanceof LivingEntity living) {
            living.setHealth(Math.min(living.getHealth(), attribute.getValue()));
        }
    }

    /**
     * У жертви забирають САМ ПОЛІТ, а не дозвіл на нього: чужі здібності (напр. «Політ на
     * вітрі» Тирана) повертають собі {@code allowFlight} кожні два тіки, тож одноразовий
     * {@code setAllowFlight(false)} не важить нічого — гравець злітав пробілом далі.
     * Тому гасимо клієнтський зліт ({@link PlayerToggleFlightEvent}), елітри й той політ,
     * що ввімкнули сервером. Дозволу не чіпаємо взагалі — отже й повертати нема чого.
     */
    private void denyFlight(IAbilityContext context, UUID victimId, int ticks) {
        Player victim = Bukkit.getPlayer(victimId);
        if (victim == null || !managesFlight(victim)) {
            return;
        }
        victim.setFlying(false);
        victim.setGliding(false);
        context.events().subscribeToTemporaryEvent(victimId, PlayerToggleFlightEvent.class,
                e -> e.getPlayer().getUniqueId().equals(victimId) && e.isFlying(),
                e -> e.setCancelled(true), ticks);
        context.events().subscribeToTemporaryEvent(victimId, EntityToggleGlideEvent.class,
                e -> e.getEntity().getUniqueId().equals(victimId) && e.isGliding(),
                e -> e.setCancelled(true), ticks);
        BukkitTask clamp = context.scheduling().scheduleRepeating(() -> {
            Player online = Bukkit.getPlayer(victimId);
            if (online != null && online.isFlying() && managesFlight(online)) {
                online.setFlying(false);
            }
        }, 1L, 1L);
        context.scheduling().scheduleDelayed(clamp::cancel, ticks);
    }

    /**
     * Політ злодія — рівно на строк і БЕЗ «відновлення того, що було»: повернути наосліп
     * чужий {@code allowFlight} означало б подарувати політ назавжди тому, хто платив за
     * нього духовністю щосекунди. Хто політ давав, той його й поверне власним тіком.
     */
    private void grantFlight(IAbilityContext context, UUID casterId, int ticks) {
        Player caster = Bukkit.getPlayer(casterId);
        if (caster == null || !managesFlight(caster)) {
            return;
        }
        caster.setAllowFlight(true);
        context.scheduling().scheduleDelayed(() -> {
            Player online = Bukkit.getPlayer(casterId);
            if (online != null && managesFlight(online)) {
                online.setFlying(false);
                online.setAllowFlight(false);
            }
        }, ticks);
    }

    /** У креативі/спостерігачі політ вбудований у режим — його не крадуть і не дарують. */
    private boolean managesFlight(Player player) {
        GameMode gameMode = player.getGameMode();
        return gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR;
    }

    /**
     * B1.4: удар крадуть у самого удару — оболонка на {@value DreamStealerTheft#ATTACK_WINDOW_SECONDS}
     * с гасить перший вхідний урон цілком (побічні ефекти йдуть разом із ним), а його силу
     * тримає до наступного вашого удару.
     */
    private void openAbsorptionWindow(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Beyonder caster = context.getCasterBeyonder();
        if (!AbilityResourceConsumer.consumeResources(this, caster, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, caster);

        int windowTicks = DreamStealerTheft.ATTACK_WINDOW_SECONDS * 20;
        context.effects().playWardingShell(casterId, PathwayBranding.liquidOf("Error"), 1.4, windowTicks);
        context.effects().playSound(context.getCasterLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.9f, 1.3f);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text(ChatColor.LIGHT_PURPLE + "Оболонка чекає на чужий удар"));

        AtomicBoolean absorbed = new AtomicBoolean();
        context.events().subscribeToTemporaryEvent(casterId, EntityDamageEvent.class,
                e -> e.getEntity().getUniqueId().equals(casterId) && !e.isCancelled() && !absorbed.get(),
                e -> {
                    if (absorbed.compareAndSet(false, true)) {
                        absorb(context, e);
                    }
                },
                windowTicks);
    }

    private void absorb(IAbilityContext context, EntityDamageEvent event) {
        UUID casterId = context.getCasterId();
        double banked = Math.min(event.getFinalDamage(), DreamStealerTheft.ATTACK_CHARGE_CAP);
        event.setCancelled(true);

        Location casterLocation = context.getCasterLocation();
        Color errorColor = PathwayBranding.liquidOf("Error");
        context.effects().playExplosionRingEffect(casterLocation, 1.6, Particle.DUST,
                new Particle.DustOptions(errorColor, 1.2f));
        context.effects().playGlowingDust(casterLocation.clone().add(0, 1.2, 0), errorColor);
        context.effects().playSound(casterLocation, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.7f);
        context.effects().playSound(casterLocation, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);

        Charge previous = charges.put(casterId, new Charge(banked, context.effects().playPersistentHalo(casterId, errorColor)));
        if (previous != null) {
            previous.halo().cancel();
        }
        context.beyonder().updateSanityLoss(casterId, DreamStealerTheft.CONCEPTUAL_CORRUPTION);
        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.LIGHT_PURPLE
                + "Удар украдено (" + String.format("%.1f", banked) + ") — віддайте його"));

        int chargeTicks = DreamStealerTheft.ATTACK_CHARGE_SECONDS * 20;
        context.events().subscribeToTemporaryEvent(casterId, EntityDamageByEntityEvent.class,
                e -> e.getDamager().getUniqueId().equals(casterId) && charges.containsKey(casterId),
                e -> release(context, e),
                chargeTicks);
        // Заряд тримається рівно стільки, скільки живе підписка — далі розсипається сам
        context.scheduling().scheduleDelayed(() -> {
            Charge stale = charges.remove(casterId);
            if (stale != null) {
                stale.halo().cancel();
                context.messaging().sendMessageToActionBar(casterId,
                        Component.text(ChatColor.GRAY + "Украдений удар розсипався"));
            }
        }, chargeTicks);
    }

    private void release(IAbilityContext context, EntityDamageByEntityEvent event) {
        Charge charge = charges.remove(context.getCasterId());
        if (charge == null) {
            return;
        }
        charge.halo().cancel();
        event.setDamage(event.getDamage() + charge.amount());

        Location casterLocation = context.getCasterLocation();
        context.effects().playSurgingWave(casterLocation,
                context.getCasterEyeLocation().getDirection().setY(0).normalize(),
                4.0, 2.0, PathwayBranding.liquidOf("Error"), 10);
        context.effects().playSound(casterLocation, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.2f);
        context.messaging().sendMessageToActionBar(context.getCasterId(), Component.text(ChatColor.LIGHT_PURPLE
                + "Чужий удар повернувся (+" + String.format("%.1f", charge.amount()) + ")"));
    }

    private ItemStack createTargetIcon(Target target) {
        boolean isPlayer = target.player() != null;
        ItemStack icon = new ItemStack(isPlayer ? Material.PLAYER_HEAD : Material.SKELETON_SKULL);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }
        if (meta instanceof SkullMeta skullMeta && isPlayer) {
            skullMeta.setOwningPlayer(target.player());
        }
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + target.name());
        Beyonder beyonder = target.beyonder();
        meta.setLore(beyonder == null
                ? List.of(ChatColor.GRAY + "Звичайна жива істота")
                : List.of(PathwayBranding.textOf(beyonder.getPathway().getName())
                        + beyonder.getPathway().getName()
                        + ChatColor.GRAY + ", Посл. " + beyonder.getSequence().level()));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createModeIcon(Mode mode) {
        ItemStack item = new ItemStack(mode.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + mode.title);
        List<String> lore = new ArrayList<>();
        mode.lore.forEach(line -> lore.add(ChatColor.GRAY + line));
        lore.add("");
        lore.add(ChatColor.YELLOW + "▶ Натисніть, щоб украсти");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
