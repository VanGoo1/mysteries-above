package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.GatekeeperLore;
import me.vangoo.domain.valueobjects.RetinueServant;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.pathways.common.Spirits;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 5: Внутрішній Загробний Світ (A9–A13). Тіло воротаря — клітка для одного духа чи
 * нежиті з власного почту: shift-ПКМ по слузі поглинає його (почет зменшується на одного),
 * shift-ПКМ без цілі (коли хтось уже всередині) — випускає, звичайне ПКМ кличе силу того, хто
 * живе всередині.
 *
 * <p>A12 (ерозія) — обов'язкова ціна: при поглинанні max HP падає на −2…−8 залежно від сили
 * окупанта (рахує сама здібність, разово, при вселенні/виселенні), а поки хтось живе всередині —
 * повільний дренаж розсудку й імунітет до Тління ({@link InternalUnderworldSession}, боон
 * «напівмертвого» тіла з тієї ж вікі-фрази). A13 (одержимість) окремої механіки не отримує:
 * ворожих духів-паразитів у плагіні немає, а ризик уже несе ерозія — лишається в описі.
 *
 * <p>Ціна касту — доплата понад найдешевшого окупанта (патерн {@code SpiritPact.pay()}), кулдаун
 * касту спільний на всіх десятьох (той самий компроміс, що {@code SpiritPact.pactCooldownSeconds}
 * і режими {@code DoorToTheUnderworld}). Поглинання й виселення безкоштовні й без кулдауну —
 * єдиний слот окупанта сам собою не дає їх спамити.
 *
 * <p>М9 додав четверо нових духів Світу Духів (A10/A11/S2/S3): {@code DEATH_ENVOY} і
 * {@code SEA_BEASTS} — бойові активи з власним кастом; {@code PUS_OF_MAN} і {@code PALE_GIRL} —
 * пасивні (castCost 0, каст лише повідомляє, що активного заклинання немає), поведінка яких
 * живе в {@link InternalUnderworldSession} (рятунок від смерті / імунітет до Нудоти-Сліпоти-
 * Темряви), а не тут.
 */
public class InternalUnderworld extends ActiveAbility {

    private static final double ABSORB_RANGE = 4.0;

    private static final int DEATH_KNIGHT_BUFF_TICKS = 20 * 20;
    private static final double PYTHON_RADIUS = 10.0;
    private static final double PYTHON_PULL_STRENGTH = 0.5;
    private static final double SHADOW_STEP_RANGE = 15.0;
    private static final double LAKE_RADIUS = 8.0;
    private static final int LAKE_DURATION_TICKS = 100;
    private static final double SCOUT_RADIUS = 60.0;
    private static final int SCOUT_DURATION_TICKS = 15 * 20;
    private static final double BURST_RADIUS = 5.0;
    private static final int BURST_DAMAGE = 6;
    private static final double ENVOY_RANGE = 25.0;
    private static final int ENVOY_DAMAGE = 10;
    private static final int ENVOY_DEBUFF_TICKS = 4 * 20;
    private static final double SEA_BEASTS_RANGE = 20.0;
    private static final int SEA_BEASTS_DAMAGE = 6;
    private static final int SEA_BEASTS_DURATION_TICKS = 10 * 20;
    private static final int SEA_BEASTS_COUNT = 4;
    private static final double SEA_BEASTS_LANE_SPACING = 0.7;

    enum Occupant {
        DEATH_KNIGHT("Лицар Смерті", "обладунок і сила удару",
                GatekeeperLore.DEATH_KNIGHT_CAST_COST, GatekeeperLore.DEATH_KNIGHT_EROSION_HP),
        SHADOW_PYTHON("Пітон, що ковтає тіні", "знімає невидимість/темряву й тягне ворогів",
                GatekeeperLore.SHADOW_PYTHON_CAST_COST, GatekeeperLore.SHADOW_PYTHON_EROSION_HP),
        LIVING_SHADOW("Жива тінь", "короткий стрибок у тінь цілі",
                GatekeeperLore.LIVING_SHADOW_CAST_COST, GatekeeperLore.LIVING_SHADOW_EROSION_HP),
        LAKE_GODDESS("Богиня Озера", "туманне озеро: ворогам Повільність+Сліпота, союзникам Регенерація",
                GatekeeperLore.LAKE_GODDESS_CAST_COST, GatekeeperLore.LAKE_GODDESS_EROSION_HP),
        WANDERING_SPIRIT("Мандрівний дух", "розвідка: підсвічує живих навколо",
                GatekeeperLore.WANDERING_SPIRIT_CAST_COST, GatekeeperLore.WANDERING_SPIRIT_EROSION_HP),
        RESURRECTED_SERVANT("Воскреслий слуга", "вибух чистої шкоди довкола вас",
                GatekeeperLore.RESURRECTED_SERVANT_CAST_COST, GatekeeperLore.RESURRECTED_SERVANT_EROSION_HP),
        DEATH_ENVOY("Посланець Смерті", "примарна рука на далекій відстані: шкода, Повільність, Сліпота",
                GatekeeperLore.DEATH_ENVOY_CAST_COST, GatekeeperLore.DEATH_ENVOY_EROSION_HP),
        SEA_BEASTS("Морська Примара", "чотири примарні хвилі накочуються на ціль",
                GatekeeperLore.SEA_BEASTS_CAST_COST, GatekeeperLore.SEA_BEASTS_EROSION_HP),
        /** Пасивний (castCost 0): рятує від смертельного удару — діє сама сесія, каст не потрібен. */
        PUS_OF_MAN("Гній Людини", "пасивна: раз на 5 хв рятує від смертельного удару",
                GatekeeperLore.PUS_OF_MAN_CAST_COST, GatekeeperLore.PUS_OF_MAN_EROSION_HP),
        /** Пасивний (castCost 0): імунітет до Нудоти/Сліпоти/Темряви — діє сама сесія. */
        PALE_GIRL("Бліда Дівчинка", "пасивна: імунітет до Нудоти/Сліпоти/Темряви",
                GatekeeperLore.PALE_GIRL_CAST_COST, GatekeeperLore.PALE_GIRL_EROSION_HP);

        final String title;
        final String power;
        final int castCost;
        final double erosionHp;

        Occupant(String title, String power, int castCost, double erosionHp) {
            this.title = title;
            this.power = power;
            this.castCost = castCost;
            this.erosionHp = erosionHp;
        }
    }

    /** Кастер → хто живе всередині (порожньо — нікого). */
    private final Map<UUID, Occupant> occupants = new ConcurrentHashMap<>();
    /** Кастер → сесія проживання (дренаж розсудку, імунітет до Тління). */
    private final Map<UUID, InternalUnderworldSession> sessions = new ConcurrentHashMap<>();

    /** Почет Посл. 6+: спільний реєстр, тому приходить конструктором, як у SpiritChanneling. */
    private final UndeadRetinue retinue;

    public InternalUnderworld(UndeadRetinue retinue) {
        this.retinue = retinue;
    }

    @Override
    public String getName() {
        return "Внутрішній Загробний Світ";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fВаше тіло — клітка для одного духа чи нежиті з власних слуг.\n\n" +
                "§7Shift-ПКМ по слузі — поглинути;\n" +
                "§7Shift-ПКМ без цілі, коли хтось усередині, — випустити;\n" +
                "§7Звичайне ПКМ кличе силу того, хто живе всередині.\n\n" +
                "§7Кожен дає свою унікальну силу — тримайте здібність у руці, щоб " +
                "постійно бачити поточну силу в action-bar.\n\n" +
                "§8Поки хтось усередині: max HP нижче (ерозія), повільний дренаж розсудку, " +
                "але Висушування на вас не діє.";
    }

    @Override
    public int getSpiritualityCost() {
        return GatekeeperLore.UNDERWORLD_CAST_BASE_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return GatekeeperLore.underworldCastCooldownSeconds(sequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }
        UUID casterId = context.getCasterId();

        if (caster.isSneaking()) {
            if (occupants.containsKey(casterId)) {
                return release(context, caster);
            }
            LivingEntity targeted = context.targeting().getTargetedEntity(ABSORB_RANGE).orElse(null);
            if (targeted == null) {
                return AbilityResult.failure("Немає кого поглинути поруч");
            }
            return absorb(context, caster, targeted);
        }

        Occupant occupant = occupants.get(casterId);
        if (occupant == null) {
            return AbilityResult.failure("Усередині нікого немає — поглиньте слугу (shift-ПКМ)");
        }
        return cast(context, caster, occupant);
    }

    /* ===================== Поглинання / виселення ===================== */

    private AbilityResult absorb(IAbilityContext context, Player caster, LivingEntity target) {
        UUID casterId = caster.getUniqueId();

        Occupant occupant = resolveOccupant(casterId, target);
        if (occupant == null) {
            context.messaging().sendMessage(casterId, "§8Ця істота не годиться на роль окупанта.");
            return AbilityResult.deferred();
        }

        if (Spirits.isSpirit(target)) {
            target.remove();
        } else if (!retinue.absorb(casterId, target.getUniqueId())) {
            context.messaging().sendMessage(casterId, "§8Не вдалось забрати цього слугу.");
            return AbilityResult.deferred();
        }

        occupants.put(casterId, occupant);
        applyErosion(caster, occupant.erosionHp);
        startHousingSession(context, casterId, occupant);

        Color color = PathwayBranding.liquidOf("Death");
        context.effects().playVortexEffect(caster.getLocation(), 2.0, 1.0, Particle.SQUID_INK, 20);
        context.effects().playSound(caster.getLocation(), Sound.ENTITY_ALLAY_DEATH, 1.0f, 0.6f);
        context.effects().playSound(caster.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8f, 0.7f);
        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "☠ "
                + occupant.title + " оселився(лась) всередині вас");

        return AbilityResult.deferred();
    }

    private AbilityResult release(IAbilityContext context, Player caster) {
        UUID casterId = caster.getUniqueId();
        Occupant occupant = occupants.remove(casterId);
        if (occupant == null) {
            context.messaging().sendMessage(casterId, "§8Усередині й так нікого немає.");
            return AbilityResult.deferred();
        }

        InternalUnderworldSession session = sessions.remove(casterId);
        if (session != null) session.cancel();

        restoreErosion(caster, occupant.erosionHp);

        context.effects().playFadingAura(caster.getLocation(), PathwayBranding.liquidOf("Death"), 30);
        context.effects().playSound(caster.getLocation(), Sound.ENTITY_ALLAY_DEATH, 0.6f, 1.3f);
        context.messaging().sendMessage(casterId, ChatColor.GRAY + "☠ "
                + occupant.title + " покинув(ла) ваше тіло");

        return AbilityResult.deferred();
    }

    /** Дух — завжди Мандрівний; інакше дивимось у почет і читаємо його тип/тег. */
    private Occupant resolveOccupant(UUID casterId, LivingEntity target) {
        if (Spirits.isSpirit(target)) return Occupant.WANDERING_SPIRIT;

        Optional<RetinueServant> descriptor = retinue.describe(casterId, target.getUniqueId());
        return descriptor.map(InternalUnderworld::occupantFromServant).orElse(null);
    }

    /** Підкорена нежить (SUBJUGATED) окупантом не стає — вона довільний моб без ідентичності. */
    private static Occupant occupantFromServant(RetinueServant servant) {
        if (servant.kind() == RetinueServant.Kind.RESURRECTED) return Occupant.RESURRECTED_SERVANT;
        if (servant.kind() != RetinueServant.Kind.PACT) return null;
        return switch (servant.typeOrTag()) {
            case "death_knight" -> Occupant.DEATH_KNIGHT;
            case "shadow_swallowing_python" -> Occupant.SHADOW_PYTHON;
            case "living_shadow" -> Occupant.LIVING_SHADOW;
            case "lake_goddess" -> Occupant.LAKE_GODDESS;
            case "death_envoy" -> Occupant.DEATH_ENVOY;
            case "sea_beasts" -> Occupant.SEA_BEASTS;
            case "pus_of_man" -> Occupant.PUS_OF_MAN;
            case "pale_girl" -> Occupant.PALE_GIRL;
            default -> null;
        };
    }

    private void startHousingSession(IAbilityContext context, UUID casterId, Occupant occupant) {
        InternalUnderworldSession session = new InternalUnderworldSession(
                casterId, occupant, getName(), context.beyonder(), context.events(), sessions);
        sessions.put(casterId, session);
        var task = context.scheduling().scheduleRepeating(
                session::tick, InternalUnderworldSession.TICK_PERIOD_TICKS,
                InternalUnderworldSession.TICK_PERIOD_TICKS);
        session.bindTask(task);
        session.armDeathWard();
    }

    /** Розраховує ерозію пропорційно поточному відсотку HP — той самий прийом, що PhysicalEnhancement. */
    private static void applyErosion(Player player, double amount) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        double oldMax = attr.getBaseValue();
        double newMax = Math.max(2.0, oldMax - amount);
        double percent = player.getHealth() / oldMax;
        attr.setBaseValue(newMax);
        player.setHealth(Math.max(1.0, Math.min(newMax, newMax * percent)));
    }

    private static void restoreErosion(Player player, double amount) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        double oldMax = attr.getBaseValue();
        double newMax = oldMax + amount;
        double percent = player.getHealth() / oldMax;
        attr.setBaseValue(newMax);
        player.setHealth(Math.min(newMax, newMax * percent));
    }

    /* ===================== Каст сили окупанта ===================== */

    private AbilityResult cast(IAbilityContext context, Player caster, Occupant occupant) {
        if (occupant.castCost <= 0) {
            context.messaging().sendMessage(caster.getUniqueId(), "§8"
                    + occupant.title + " діє сама собою — активного заклинання немає.");
            return AbilityResult.failure("Пасивний окупант");
        }
        if (!pay(context, caster, occupant.castCost)) {
            return AbilityResult.deferred();
        }
        switch (occupant) {
            case DEATH_KNIGHT -> castDeathKnight(context, caster);
            case SHADOW_PYTHON -> castShadowPython(context, caster);
            case LIVING_SHADOW -> castLivingShadow(context, caster);
            case LAKE_GODDESS -> castLakeGoddess(context, caster);
            case WANDERING_SPIRIT -> castWanderingSpirit(context, caster);
            case RESURRECTED_SERVANT -> castResurrectedServant(context, caster);
            case DEATH_ENVOY -> castDeathEnvoy(context, caster);
            case SEA_BEASTS -> castSeaBeasts(context, caster);
            case PUS_OF_MAN, PALE_GIRL -> { /* пасивні — сюди не доходить (castCost 0 вище) */ }
        }
        return AbilityResult.deferred();
    }

    /**
     * Списує ресурси за конкретного окупанта. Базову ціну (і кулдаун) знімає
     * {@code AbilityResourceConsumer}, різницю дорожчого — доплата тут же; загальну
     * достатність перевіряємо ДО списання, щоб не взяти базу й не дати сили.
     */
    private boolean pay(IAbilityContext context, Player caster, int totalCost) {
        Beyonder housing = context.getCasterBeyonder();
        if (!housing.getSpirituality().hasSufficient(totalCost)
                || !AbilityResourceConsumer.consumeResources(this, housing, context)) {
            context.messaging().sendMessage(caster.getUniqueId(), "§cНедостатньо духовності для цієї сили.");
            return false;
        }
        int surcharge = totalCost - getSpiritualityCost();
        if (surcharge > 0) {
            housing.setSpirituality(housing.getSpirituality().decrement(surcharge));
        }
        context.events().publishAbilityUsedEvent(this, housing);
        context.beyonder().updateBeyonder(caster.getUniqueId());
        return true;
    }

    private void castDeathKnight(IAbilityContext context, Player caster) {
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, DEATH_KNIGHT_BUFF_TICKS, 1));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, DEATH_KNIGHT_BUFF_TICKS, 1));
        context.effects().playFadingAura(caster.getLocation(), PathwayBranding.liquidOf("Death"), 40);
        context.effects().playSound(caster.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.7f);
    }

    private void castShadowPython(IAbilityContext context, Player caster) {
        Location center = caster.getLocation();
        for (Entity entity : center.getWorld().getNearbyEntities(center, PYTHON_RADIUS, PYTHON_RADIUS, PYTHON_RADIUS)) {
            if (!(entity instanceof LivingEntity victim) || victim.equals(caster)) continue;
            victim.removePotionEffect(PotionEffectType.INVISIBILITY);
            victim.removePotionEffect(PotionEffectType.DARKNESS);
            pullToward(victim, center, PYTHON_PULL_STRENGTH);
        }
        context.effects().playVortexEffect(center, 2.0, PYTHON_RADIUS / 2.0, Particle.SQUID_INK, 20);
        context.effects().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.6f);
    }

    private void castLivingShadow(IAbilityContext context, Player caster) {
        LivingEntity target = context.targeting().getTargetedEntity(SHADOW_STEP_RANGE).orElse(null);
        if (target == null) {
            context.messaging().sendMessage(caster.getUniqueId(), "§8Немає цілі для стрибка в тінь");
            return;
        }
        Vector behindOffset = target.getLocation().getDirection().setY(0).normalize().multiply(-1.5);
        Location behind = target.getLocation().clone().add(behindOffset);
        behind.setDirection(caster.getLocation().getDirection());

        context.effects().playFadingAura(caster.getLocation(), PathwayBranding.liquidOf("Death"), 20);
        caster.teleport(behind);
        context.effects().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
    }

    private void castLakeGoddess(IAbilityContext context, Player caster) {
        Location center = caster.getLocation();
        caster.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, LAKE_DURATION_TICKS, 0));
        for (Player ally : context.targeting().getNearbyPlayers(LAKE_RADIUS)) {
            ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, LAKE_DURATION_TICKS, 0));
        }
        for (Entity entity : center.getWorld().getNearbyEntities(center, LAKE_RADIUS, LAKE_RADIUS, LAKE_RADIUS)) {
            if (entity instanceof Player || !(entity instanceof LivingEntity enemy)) continue;
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, LAKE_DURATION_TICKS, 1));
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, LAKE_DURATION_TICKS, 0));
        }
        context.effects().playSphereEffect(center, LAKE_RADIUS, Particle.SPLASH, 20);
        context.effects().playSound(center, Sound.AMBIENT_UNDERWATER_ENTER, 0.8f, 1.0f);
    }

    private void castWanderingSpirit(IAbilityContext context, Player caster) {
        UUID casterId = caster.getUniqueId();
        for (LivingEntity entity : context.targeting().getNearbyEntities(SCOUT_RADIUS)) {
            if (entity.equals(caster)) continue;
            context.glowing().setGlowing(entity.getUniqueId(), casterId,
                    PathwayBranding.textOf("Death"), SCOUT_DURATION_TICKS);
        }
        context.effects().playSoundForPlayer(casterId, Sound.PARTICLE_SOUL_ESCAPE, 0.6f, 1.2f);
    }

    private void castResurrectedServant(IAbilityContext context, Player caster) {
        Location center = caster.getLocation();
        for (Entity entity : center.getWorld().getNearbyEntities(center, BURST_RADIUS, BURST_RADIUS, BURST_RADIUS)) {
            if (!(entity instanceof LivingEntity victim) || victim.equals(caster)) continue;
            victim.setHealth(Math.max(0.0, victim.getHealth() - BURST_DAMAGE));
        }
        context.effects().playExplosionRingEffect(center, BURST_RADIUS, Particle.DUST,
                new Particle.DustOptions(PathwayBranding.liquidOf("Death"), 1.2f));
        context.effects().playSound(center, Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.5f);
    }

    /** A10: примарна рука Посланця — б'є на дальність, тягне за собою Повільність+Сліпоту. */
    private void castDeathEnvoy(IAbilityContext context, Player caster) {
        LivingEntity target = context.targeting().getTargetedEntity(ENVOY_RANGE).orElse(null);
        if (target == null) {
            context.messaging().sendMessage(caster.getUniqueId(), "§8Немає цілі для примарної руки");
            return;
        }
        Color color = PathwayBranding.liquidOf("Death");
        context.effects().playTravelingBeam(caster.getEyeLocation(), target.getLocation(), color, () -> {
            target.setHealth(Math.max(0.0, target.getHealth() - ENVOY_DAMAGE));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ENVOY_DEBUFF_TICKS, 3));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ENVOY_DEBUFF_TICKS, 0));
            target.setVelocity(new Vector(0, -0.6, 0));
            context.effects().playSound(target.getLocation(), Sound.ENTITY_ALLAY_HURT, 1.0f, 0.4f);
        });
        context.effects().playSound(caster.getLocation(), Sound.ENTITY_ALLAY_HURT, 0.8f, 0.6f);
    }

    /** S3: чотири примарні хвилі накочуються на ціль паралельними доріжками. */
    private void castSeaBeasts(IAbilityContext context, Player caster) {
        LivingEntity target = context.targeting().getTargetedEntity(SEA_BEASTS_RANGE).orElse(null);
        if (target == null) {
            context.messaging().sendMessage(caster.getUniqueId(), "§8Немає цілі для морських примар");
            return;
        }
        Vector toTarget = target.getLocation().toVector().subtract(caster.getLocation().toVector());
        double distance = Math.min(SEA_BEASTS_RANGE, Math.max(1.0, toTarget.length()));
        Vector direction = toTarget.lengthSquared() > 1e-4
                ? toTarget.normalize() : caster.getLocation().getDirection().setY(0).normalize();
        Vector lateral = new Vector(-direction.getZ(), 0, direction.getX());
        Color color = PathwayBranding.liquidOf("Death");

        for (int i = 0; i < SEA_BEASTS_COUNT; i++) {
            double offset = (i - (SEA_BEASTS_COUNT - 1) / 2.0) * SEA_BEASTS_LANE_SPACING;
            Location lane = caster.getLocation().clone().add(lateral.clone().multiply(offset));
            context.effects().playSurgingWave(lane, direction, distance, 1.0, color, SEA_BEASTS_DURATION_TICKS);
        }
        target.setHealth(Math.max(0.0, target.getHealth() - SEA_BEASTS_DAMAGE));
        context.effects().playSound(caster.getLocation(), Sound.ENTITY_DOLPHIN_ATTACK, 1.0f, 0.6f);
        context.effects().playSound(target.getLocation(), Sound.ENTITY_DROWNED_HURT, 1.0f, 0.7f);
    }

    private static void pullToward(LivingEntity entity, Location target, double strength) {
        Vector pull = target.toVector().subtract(entity.getLocation().toVector());
        if (pull.lengthSquared() < 1e-4) return;
        pull.normalize().multiply(strength);
        pull.setY(Math.max(0.1, pull.getY()));
        entity.setVelocity(pull);
    }

    @Override
    public void cleanUp() {
        sessions.values().forEach(InternalUnderworldSession::cancel);
        sessions.clear();
        occupants.clear();
    }
}
