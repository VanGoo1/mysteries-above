package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.RetinuePoint;
import me.vangoo.domain.valueobjects.RetinueServant;
import me.vangoo.domain.valueobjects.RetinueSnapshot;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SpiritGuideLore;
import me.vangoo.infrastructure.retinue.RetinueStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Живий почет нежиті одного провідника (Death, Посл. 6) — той самий патерн, що
 * {@link SpiritCompanionSession}: власний Bukkit-таск, ведення за власником, самознищення,
 * щойно власник вийшов зі світу.
 *
 * <p>Відмінність від почту духів принципова: духи НІКОЛИ не б'ються, а слуги б'ються — але
 * лише за наказом. «No will or vitality» вікі виражене тим, що в слуги знімаються ЦІЛЬОВІ
 * гоали ({@code GoalType.TARGET}): сам він ворога не вибирає й на удар не відповідає, а от
 * ближній бій по виставленій цілі працює ванільно (наказ приходить у Посл.-6 меню, M5).
 *
 * <p>Реєстр сесій — інстанс-поле {@link UndeadRetinue}, не {@code static}: почет належить
 * гравцеві, а екземпляр здібності спільний на шлях.
 */
final class UndeadRetinueSession {

    /** Пів секунди: слуги ходять ногами, частіше перераховувати маршрут нема сенсу. */
    static final long TICK_PERIOD_TICKS = 10L;

    /** Ближче за це слуга просто стоїть біля власника, далі — йде до нього. */
    private static final double FOLLOW_DISTANCE = 4.0;
    /** Далі за це (або інший світ) — перескок: стіни й вода інакше губили б слугу. */
    private static final double TELEPORT_DISTANCE = 24.0;
    /** Швидкість ходи до власника; трохи більша за 1.0, щоб не відставали в бігу. */
    private static final double FOLLOW_SPEED = 1.25;

    /** Що почет робить зараз. Наказ один на весь почет — окремих загонів вікі не знає. */
    enum Mode {FOLLOW, ATTACK, GUARD}

    /** Перевірки бунту й утечі — раз на секунду, тобто кожен другий такт сесії. */
    private static final int SECOND_EVERY = 2;
    /** Розсудок, який провідник втрачає щосекунди поруч із сильнішим Беєндером Смерті. */
    private static final int REBELLION_SANITY_PER_SECOND = 1;
    /** Нижче цієї частки здоров'я нежить сама тягне господаря в тінь. */
    private static final double ESCAPE_HEALTH_RATIO = 0.25;
    /** Власний кулдаун утечі (мс): рятунок, а не спосіб пересування. */
    private static final long ESCAPE_COOLDOWN_MILLIS = 120_000L;
    /** Скільки триває невидимість утечі (тіки) і як далеко шукати темний закут. */
    private static final int ESCAPE_INVISIBILITY_TICKS = 100;
    private static final int ESCAPE_SEARCH_RADIUS = 8;
    private static final int ESCAPE_SEARCH_TRIES = 16;
    /** Темрява — рівень світла блоку, нижчий за цей. */
    private static final int DARK_LIGHT_LEVEL = 7;

    private final UUID ownerId;
    private final Map<UUID, UndeadRetinueSession> sessions;
    private final RetinueStore store;
    private final IBeyonderContext beyonders;
    private final IVisualEffectsContext effects;
    private final Random random = new Random();

    private int runs;
    private long escapeReadyAtMillis;

    private Mode mode = Mode.FOLLOW;
    private UUID victimId;
    private Location post;
    private boolean hidden;

    /**
     * Почет тримається ЖИВИМИ посиланнями, а не UUID — з тієї ж причини, що в
     * {@link SpiritCompanionSession}: при переході між світами пошук за старим id губить моба.
     */
    private final List<Mob> servants = new ArrayList<>();

    /** Статичний опис (тип/тег/ім'я) кожного ЖИВОГО слуги — на випадок приховання чи relog. */
    private final Map<UUID, RetinueServant> descriptors = new HashMap<>();

    /** Приховані слуги: описані повністю (з останньою позицією), фізично видалені зі світу. */
    private final List<RetinueServant> hiddenRoster = new ArrayList<>();

    private BukkitTask task;

    UndeadRetinueSession(UUID ownerId, Map<UUID, UndeadRetinueSession> sessions, RetinueStore store,
                         IBeyonderContext beyonders, IVisualEffectsContext effects) {
        this.ownerId = ownerId;
        this.sessions = sessions;
        this.store = store;
        this.beyonders = beyonders;
        this.effects = effects;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    /**
     * Бере слугу під команду: без власної волі, не деспавниться й носить мітку почту. Якщо
     * почет зараз прихований — слуга одразу приєднується до тіней, а не показується на мить.
     */
    void enroll(Mob servant, RetinueServant.Kind kind) {
        RetinueServant descriptor = describe(servant, kind, servant.getLocation());
        if (hidden) {
            hiddenRoster.add(descriptor);
            servant.remove();
            return;
        }

        servants.add(servant);
        descriptors.put(servant.getUniqueId(), descriptor);
        tagAsServant(servant);

        servant.getWorld().spawnParticle(Particle.SCULK_SOUL, servant.getEyeLocation(),
                12, 0.3, 0.4, 0.3);
    }

    /** «Без волі»: знімає лише ЦІЛЬОВІ гоали й позначає слугу міткою почту. */
    private void tagAsServant(Mob servant) {
        servant.addScoreboardTag(UndeadRetinue.TAG);
        servant.setTarget(null);
        servant.setRemoveWhenFarAway(false);
        // Знімаємо лише ЦІЛЬОВІ гоали: слуга більше не шукає ворога сам і не мститься за удар,
        // але ванільний ближній бій по виставленій цілі лишається — інакше наказ «атакувати»
        // не мав би чим виконуватись.
        Bukkit.getMobGoals().removeAllGoals(servant, com.destroystokyo.paper.entity.ai.GoalType.TARGET);
    }

    /** Тип/тег + ім'я слуги; PACT читає internal id пака з другого тега, решта — EntityType. */
    private RetinueServant describe(Mob servant, RetinueServant.Kind kind, Location at) {
        String typeOrTag = kind == RetinueServant.Kind.PACT
                ? SpiritPact.SpiritWorldCreature.fromEntity(servant).map(k -> k.tag).orElse(servant.getType().name())
                : servant.getType().name();
        return new RetinueServant(kind, typeOrTag, servant.getCustomName(), UndeadRetinue.toPoint(at));
    }

    /**
     * Віддає одного слугу замість власника (Обмін духом): той розсипається на місці.
     * {@code false} — віддавати нема кого.
     */
    boolean sacrificeOne() {
        pruneLost();
        if (servants.isEmpty()) return false;

        Mob servant = servants.remove(0);
        descriptors.remove(servant.getUniqueId());
        servant.getWorld().spawnParticle(Particle.SCULK_SOUL, servant.getEyeLocation(),
                24, 0.4, 0.6, 0.4);
        servant.getWorld().playSound(servant.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.5f);
        servant.remove();
        return true;
    }

    boolean owns(UUID entityId) {
        return servants.stream().anyMatch(servant -> servant.getUniqueId().equals(entityId));
    }

    /** Статичний опис живого слуги (тип/тег) — Внутрішній Загробний Світ визначає окупанта. */
    Optional<RetinueServant> describe(UUID entityId) {
        return Optional.ofNullable(descriptors.get(entityId));
    }

    /**
     * Забирає КОНКРЕТНОГО слугу з почту без розсипання решти (Внутрішній Загробний Світ
     * поглинає його). На відміну від {@link #sacrificeOne()} (перший-ліпший, ціна обміну духом)
     * тут caller уже знає, кого саме хоче. {@code false} — цей слуга не в почеті.
     */
    boolean absorb(UUID entityId) {
        Mob servant = servants.stream()
                .filter(candidate -> candidate.getUniqueId().equals(entityId))
                .findFirst().orElse(null);
        if (servant == null) return false;

        servants.remove(servant);
        descriptors.remove(entityId);
        servant.remove();
        return true;
    }

    /** Живі й приховані разом — приховання не звільняє слот у стелі почту. */
    int size() {
        pruneLost();
        return servants.size() + hiddenRoster.size();
    }

    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !owner.isValid()) {
            persistOnLogout();
            cancel();
            return;
        }

        pruneLost();
        if (servants.isEmpty() && hiddenRoster.isEmpty()) {
            if (store != null) store.remove(ownerId);
            cancel();
            return;
        }

        if (++runs % SECOND_EVERY == 0) {
            rollRebellion(owner);
            if (servants.isEmpty()) return;
            dragIntoShadowsIfDying(owner);
        }

        LivingEntity victim = mode == Mode.ATTACK ? resolveVictim() : null;
        if (mode == Mode.ATTACK && victim == null) {
            follow();  // жертва мертва або зникла — почет повертається до власника
        }

        for (Mob servant : List.copyOf(servants)) {
            switch (mode) {
                case ATTACK -> servant.setTarget(victim);
                case GUARD -> {
                    servant.setTarget(null);
                    walkTo(servant, post);
                }
                // Волі немає: ціль, підхоплену AI між тіками, щотакту скидаємо.
                case FOLLOW -> {
                    servant.setTarget(null);
                    walkTo(servant, owner.getLocation());
                }
            }
        }
    }

    /** Наказ «за мною». Він же — стан за замовчуванням і повернення після смерті жертви. */
    void follow() {
        mode = Mode.FOLLOW;
        victimId = null;
        post = null;
    }

    /** Наказ «убити цю ціль»: єдиний випадок, коли слуга взагалі має ціль. */
    void attack(UUID targetId) {
        mode = Mode.ATTACK;
        victimId = targetId;
        post = null;
    }

    /** Наказ «стояти тут»: почет лишається на місці й нікого не чіпає. */
    void guard(Location where) {
        mode = Mode.GUARD;
        victimId = null;
        post = where.clone();
    }

    /**
     * Приховання/поява (S1b «without being discovered»): почет розчиняється в тінях
     * ПОВНІСТЮ — не невидимий, а фізично зникає зі світу — і повертається на останній
     * відомій позиції кожного слуги, коли власник покаже його знову.
     */
    void toggleHidden(IAbilityContext context) {
        hidden = !hidden;
        if (hidden) {
            for (Mob servant : List.copyOf(servants)) {
                RetinueServant descriptor = descriptors.remove(servant.getUniqueId());
                if (descriptor != null) {
                    hiddenRoster.add(new RetinueServant(descriptor.kind(), descriptor.typeOrTag(),
                            descriptor.customName(), UndeadRetinue.toPoint(servant.getLocation())));
                }
                servant.getWorld().spawnParticle(Particle.SCULK_SOUL, servant.getLocation(),
                        10, 0.3, 0.4, 0.3);
                servant.remove();
            }
            servants.clear();
        } else {
            for (RetinueServant descriptor : hiddenRoster) {
                Mob respawned = UndeadRetinue.spawnFromRecord(context, descriptor);
                if (respawned == null) continue;
                servants.add(respawned);
                descriptors.put(respawned.getUniqueId(), descriptor);
                tagAsServant(respawned);
            }
            hiddenRoster.clear();
        }
    }

    boolean isHidden() {
        return hidden;
    }

    /**
     * Пише знімок на диск при виході з серверу: живі й приховані слуги разом, лише
     * {@code RESURRECTED}/{@code PACT} (підкорена нежить — довільний ванільний моб без
     * ідентичності, не переживає relog). Порожній почет — просто прибирає файл.
     */
    private void persistOnLogout() {
        if (store == null) return;
        pruneLost();

        List<RetinueServant> savable = new ArrayList<>();
        for (Mob servant : servants) {
            RetinueServant descriptor = descriptors.get(servant.getUniqueId());
            if (descriptor == null || descriptor.kind() == RetinueServant.Kind.SUBJUGATED) continue;
            savable.add(new RetinueServant(descriptor.kind(), descriptor.typeOrTag(),
                    descriptor.customName(), UndeadRetinue.toPoint(servant.getLocation())));
        }
        for (RetinueServant descriptor : hiddenRoster) {
            if (descriptor.kind() == RetinueServant.Kind.SUBJUGATED) continue;
            savable.add(descriptor);
        }

        if (savable.isEmpty()) {
            store.remove(ownerId);
            return;
        }
        RetinuePoint guardPost = mode == Mode.GUARD && post != null ? UndeadRetinue.toPoint(post) : null;
        store.put(ownerId, new RetinueSnapshot(savable, hidden, guardPost));
    }

    /* ===================== D3: бунт біля сильнішого ===================== */

    /**
     * «When facing Higher Sequence Beyonders of the Death Pathway, their slaves are very prone to
     * rebelling, and so are they themselves»: поруч із сильнішим провідником нежить щосекунди
     * може перекинутись, а сам господар втрачає розсудок.
     */
    private void rollRebellion(Player owner) {
        Beyonder guide = beyonders.getBeyonder(ownerId);
        if (guide == null) return;

        Sequence rival = strongestDeathRivalNear(owner, guide.getSequence());
        if (rival == null) return;

        beyonders.updateSanityLoss(ownerId, REBELLION_SANITY_PER_SECOND);
        double chance = SpiritGuideLore.rebellionChancePerSecond(guide.getSequence(), rival);
        for (Mob servant : List.copyOf(servants)) {
            if (random.nextDouble() < chance) {
                defect(servant, owner);
            }
        }
    }

    /** Найсильніша поруч Послідовність Смерті, сильніша за господаря, або {@code null}. */
    private Sequence strongestDeathRivalNear(Player owner, Sequence guideSequence) {
        Sequence strongest = null;
        for (Entity nearby : owner.getNearbyEntities(SpiritGuideLore.REBELLION_RADIUS,
                SpiritGuideLore.REBELLION_RADIUS, SpiritGuideLore.REBELLION_RADIUS)) {
            if (!(nearby instanceof Player other)) continue;

            Beyonder beyonder = beyonders.getBeyonder(other.getUniqueId());
            if (beyonder == null || beyonder.getPathway() == null) continue;
            if (!"Death".equals(beyonder.getPathway().getName())) continue;
            if (beyonder.getSequence().level() >= guideSequence.level()) continue;

            if (strongest == null || beyonder.getSequence().level() < strongest.level()) {
                strongest = beyonder.getSequence();
            }
        }
        return strongest;
    }

    /**
     * Слуга виходить із-під контролю: мітка почту знімається, ціллю стає колишній господар.
     * Цільові гоали йому не повертаються — перекинувшись, він б'є того, на кого його наведено,
     * і сам нову жертву не шукає.
     */
    private void defect(Mob servant, Player owner) {
        servants.remove(servant);
        descriptors.remove(servant.getUniqueId());
        servant.removeScoreboardTag(UndeadRetinue.TAG);
        servant.setTarget(owner);
        servant.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, servant.getEyeLocation(),
                6, 0.3, 0.3, 0.3);
        servant.getWorld().playSound(servant.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
                0.8f, 0.6f);
        owner.sendMessage(ChatColor.RED + "☠ Слуга вийшов з-під вашої волі");
    }

    /* ===================== S1k-1: нежить тягне в тінь ===================== */

    /**
     * «They can drag the Spirit Guide into the shadows, disappearing without a trace, even if
     * their master is knocked unconscious» — тому це автоматика, а не кнопка. Ціна — слуга.
     */
    private void dragIntoShadowsIfDying(Player owner) {
        long now = System.currentTimeMillis();
        if (now < escapeReadyAtMillis) return;

        double max = owner.getAttribute(Attribute.MAX_HEALTH) == null
                ? 20.0 : owner.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (owner.getHealth() > max * ESCAPE_HEALTH_RATIO) return;
        if (!sacrificeOne()) return;

        escapeReadyAtMillis = now + ESCAPE_COOLDOWN_MILLIS;
        Location from = owner.getLocation().clone();
        Location shadow = findDarkSpot(from);

        owner.teleport(shadow);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                ESCAPE_INVISIBILITY_TICKS, 0, false, false, true));

        effects.playVortexEffect(from, 2.5, 1.2, Particle.SCULK_SOUL, 30);
        effects.playFadingAura(shadow, PathwayBranding.liquidOf("Death"), 30);
        owner.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        owner.sendMessage(ChatColor.DARK_GREEN + "☠ Нежить утягла вас у тінь");
    }

    /** Темний безпечний закут поблизу; не знайшовся — лишаємось на місці. */
    private Location findDarkSpot(Location from) {
        for (int i = 0; i < ESCAPE_SEARCH_TRIES; i++) {
            int dx = random.nextInt(ESCAPE_SEARCH_RADIUS * 2 + 1) - ESCAPE_SEARCH_RADIUS;
            int dz = random.nextInt(ESCAPE_SEARCH_RADIUS * 2 + 1) - ESCAPE_SEARCH_RADIUS;
            Location candidate = from.clone().add(dx, 0, dz);
            Block ground = candidate.getBlock();

            if (!ground.isPassable() || !ground.getRelative(0, 1, 0).isPassable()) continue;
            if (!ground.getRelative(0, -1, 0).getType().isSolid()) continue;
            if (ground.getLightLevel() >= DARK_LIGHT_LEVEL) continue;

            candidate.setDirection(from.getDirection());
            return candidate;
        }
        return from;
    }

    /** Жива жертва наказу або {@code null}, якщо її вже немає. */
    private LivingEntity resolveVictim() {
        if (victimId == null) return null;
        if (!(Bukkit.getEntity(victimId) instanceof LivingEntity victim) || victim.isDead()) {
            return null;
        }
        return victim;
    }

    /** Відпускає весь почет: слуги розсипаються, таск гасне. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId, this);

        for (Mob servant : List.copyOf(servants)) {
            servant.getWorld().spawnParticle(Particle.SCULK_SOUL, servant.getLocation(),
                    16, 0.3, 0.5, 0.3);
            servant.getWorld().playSound(servant.getLocation(), Sound.BLOCK_SOUL_SAND_BREAK, 0.7f, 0.6f);
            servant.remove();
        }
        servants.clear();
        descriptors.clear();
        hiddenRoster.clear();

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(ChatColor.GRAY + "☠ Почет розсипався на порох");
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /** Веде слугу до якоря (власник або пост): далеко — перескок, близько — стоїть. */
    private void walkTo(Mob servant, Location anchor) {
        if (anchor == null) return;

        Location servantLocation = servant.getLocation();
        if (servantLocation.getWorld() != anchor.getWorld()
                || servantLocation.distance(anchor) > TELEPORT_DISTANCE) {
            servant.teleport(anchor);
            return;
        }
        if (servantLocation.distance(anchor) > FOLLOW_DISTANCE) {
            servant.getPathfinder().moveTo(anchor, FOLLOW_SPEED);
        }
    }

    /** Прибирає слуг, яких уже немає у світі (убили, {@code /kill}). */
    private void pruneLost() {
        servants.removeIf(servant -> {
            boolean gone = servant.isDead() || !servant.isValid();
            if (gone) descriptors.remove(servant.getUniqueId());
            return gone;
        });
    }
}
