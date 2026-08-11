package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.GatekeeperLore;
import me.vangoo.domain.valueobjects.RetinuePoint;
import me.vangoo.domain.valueobjects.RetinueServant;
import me.vangoo.domain.valueobjects.RetinueSnapshot;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SpiritGuideLore;
import me.vangoo.infrastructure.retinue.RetinueStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реєстр почтів нежиті (Death, Посл. 6) — спільний стан ТРЬОХ входів: Воскресіння підіймає
 * труп, Підкорення забирає чужу нежить (M5), прикликання кличе істот Світу Духів (M6). Усі
 * троє поповнюють ОДИН почет власника й рахуються в одну стелю — {@link GatekeeperLore#retinueCap}
 * (10 для Посл. 6+, 12 від Посл. 5, за поточною Послідовністю власника).
 *
 * <p>Простий об'єкт, а не сервіс — точно як {@link LingeringSouls}: створює
 * {@code Death.initializeAbilities()} і роздає конструктором здібностям одного шляху. Ані
 * {@code static}, ані запису в {@code ServiceContainer} тут не треба.
 *
 * <p>Персистентність (relog/рестарт) — виняток: {@link #setStore} проставляється сеттером із
 * {@code ServiceContainer} (той самий прийом, що {@code ChurchService.setFalsePapersCheck}),
 * бо {@link RetinueStore} — справжній інфраструктурний сервіс, а {@code Death}-конструктор не
 * може отримати його до {@code super()} (там і викликається {@code initializeAbilities()}). Див.
 * {@code .claude/rules/lingering-souls.md}.
 */
public final class UndeadRetinue {

    /** Ванільний scoreboard-тег слуги: за ним його впізнають чужі підсистеми (напр. трупи). */
    public static final String TAG = "ma_retinue";

    private final Map<UUID, UndeadRetinueSession> sessions = new ConcurrentHashMap<>();
    private RetinueStore store;

    /** Чи ця сутність — чийсь слуга. Літерал тега живе лише тут. */
    public static boolean isServant(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }

    public void setStore(RetinueStore store) {
        this.store = store;
    }

    public int size(UUID ownerId) {
        UndeadRetinueSession session = sessions.get(ownerId);
        return session == null ? 0 : session.size();
    }

    /** Стеля залежить від Послідовності власника (10 для Посл. 6+, 12 від Посл. 5). */
    public boolean isFull(UUID ownerId, Sequence sequence) {
        return size(ownerId) >= GatekeeperLore.retinueCap(sequence);
    }

    /** Чи веде цю істоту хтось із провідників (щоб не забрати чужого слугу). */
    public boolean isLed(UUID entityId) {
        return sessions.values().stream().anyMatch(session -> session.owns(entityId));
    }

    /** Чи належить ця істота САМЕ цьому власнику (на відміну від {@link #isLed} — будь-кому). */
    public boolean isOwnedBy(UUID ownerId, UUID entityId) {
        UndeadRetinueSession session = sessions.get(ownerId);
        return session != null && session.owns(entityId);
    }

    /** Тип/тег живого слуги — Внутрішній Загробний Світ визначає окупанта перед поглинанням. */
    public Optional<RetinueServant> describe(UUID ownerId, UUID entityId) {
        UndeadRetinueSession session = sessions.get(ownerId);
        return session == null ? Optional.empty() : session.describe(entityId);
    }

    /**
     * Забирає конкретного слугу з почту (Внутрішній Загробний Світ, поглинання): почет
     * зменшується на одного, решта лишається. {@code false} — цей слуга не в почеті власника.
     */
    public boolean absorb(UUID ownerId, UUID entityId) {
        UndeadRetinueSession session = sessions.get(ownerId);
        return session != null && session.absorb(entityId);
    }

    /**
     * Зараховує істоту в почет власника, відкриваючи сесію першим слугою.
     * Повертає розмір почту після зарахування, або 0, якщо стеля вже вибрана.
     */
    public int enroll(IAbilityContext context, UUID ownerId, Mob servant, RetinueServant.Kind kind,
                      Sequence sequence) {
        if (isFull(ownerId, sequence)) return 0;

        UndeadRetinueSession session = sessions.computeIfAbsent(ownerId, id -> newSession(context, id));
        session.enroll(servant, kind);
        return session.size();
    }

    private UndeadRetinueSession newSession(IAbilityContext context, UUID ownerId) {
        // Сесія бере лише ГЛОБАЛЬНІ сервіси контексту, ніколи сам контекст кастера.
        UndeadRetinueSession session = new UndeadRetinueSession(ownerId, sessions, store,
                context.beyonder(), context.effects());
        BukkitTask task = context.scheduling().scheduleRepeating(session::tick,
                UndeadRetinueSession.TICK_PERIOD_TICKS, UndeadRetinueSession.TICK_PERIOD_TICKS);
        session.bindTask(task);
        return session;
    }

    /** Віддає одного слугу замість власника (Обмін духом). {@code false} — почту немає. */
    public boolean sacrificeOne(UUID ownerId) {
        UndeadRetinueSession session = sessions.get(ownerId);
        return session != null && session.sacrificeOne();
    }

    /** Наказ «за мною» (він же — стан за замовчуванням). */
    public boolean follow(UUID ownerId) {
        UndeadRetinueSession session = sessions.get(ownerId);
        if (session == null) return false;
        session.follow();
        return true;
    }

    /** Наказ «убити цю ціль». */
    public boolean attack(UUID ownerId, UUID targetId) {
        UndeadRetinueSession session = sessions.get(ownerId);
        if (session == null) return false;
        session.attack(targetId);
        return true;
    }

    /** Наказ «стерегти це місце». */
    public boolean guard(UUID ownerId, Location where) {
        UndeadRetinueSession session = sessions.get(ownerId);
        if (session == null) return false;
        session.guard(where);
        return true;
    }

    /** Відпускає весь почет власника (каст у присіданні, обмін духом тощо) — назавжди. */
    public boolean release(UUID ownerId) {
        UndeadRetinueSession session = sessions.remove(ownerId);
        if (store != null) store.remove(ownerId);
        if (session == null) return false;
        session.cancel();
        return true;
    }

    /** Приховання/поява почту: повний деспавн/респавн, а не невидимість. {@code false} — почту немає. */
    public boolean toggleHidden(IAbilityContext context, UUID ownerId) {
        UndeadRetinueSession session = sessions.get(ownerId);
        if (session == null) return false;
        session.toggleHidden(context);
        return true;
    }

    public boolean isHidden(UUID ownerId) {
        UndeadRetinueSession session = sessions.get(ownerId);
        return session != null && session.isHidden();
    }

    /**
     * Відновлює почет власника з диску при вході на сервер. Немає збереженого — no-op; жива
     * сесія вже є (не мало б статись при вході) — теж no-op, щоб не задвоїти почет.
     */
    public void restore(IAbilityContext context, Player owner) {
        UUID ownerId = owner.getUniqueId();
        if (sessions.containsKey(ownerId) || store == null) return;

        RetinueSnapshot snapshot = store.get(ownerId).orElse(null);
        if (snapshot == null || snapshot.servants().isEmpty()) return;

        Sequence sequence = context.getCasterBeyonder().getSequence();
        for (RetinueServant record : snapshot.servants()) {
            Mob mob = spawnFromRecord(context, record);
            if (mob == null) continue;
            enroll(context, ownerId, mob, record.kind(), sequence);
        }
        // Заспавнили видимо й одразу ховаємо — простіше, ніж окремий шлях заведення
        // прихованої сесії, а ефекти появи/зникнення бачить лише щойно залогінений власник.
        if (snapshot.hidden()) {
            toggleHidden(context, ownerId);
        }
        if (snapshot.guardPost() != null) {
            Location post = toLocation(snapshot.guardPost());
            if (post != null) guard(ownerId, post);
        }
    }

    /** Відновлює одного слугу з запису: MythicMobs-тег (PACT) або ванільний EntityType. */
    static Mob spawnFromRecord(IAbilityContext context, RetinueServant record) {
        Location at = toLocation(record.at());
        if (at == null) return null;

        if (record.kind() == RetinueServant.Kind.PACT) {
            UUID id = context.entity().summonCreature(record.typeOrTag(), at).orElse(null);
            return id != null && Bukkit.getEntity(id) instanceof Mob mob ? mob : null;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(record.typeOrTag());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!(at.getWorld().spawnEntity(at, type) instanceof Mob mob)) return null;

        if (record.customName() != null) {
            mob.setCustomName(record.customName());
            mob.setCustomNameVisible(true);
        }
        if (record.kind() == RetinueServant.Kind.RESURRECTED) {
            if (mob instanceof Zombie zombie) zombie.setShouldBurnInDay(false);
            if (mob instanceof Skeleton skeleton) skeleton.setShouldBurnInDay(false);
        }
        mob.setCanPickupItems(false);
        return mob;
    }

    static RetinuePoint toPoint(Location location) {
        return new RetinuePoint(location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw());
    }

    static Location toLocation(RetinuePoint point) {
        World world = Bukkit.getWorld(point.world());
        return world == null ? null : new Location(world, point.x(), point.y(), point.z(), point.yaw(), 0);
    }
}
