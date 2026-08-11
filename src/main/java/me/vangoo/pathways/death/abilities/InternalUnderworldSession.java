package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.context.IEventContext;
import me.vangoo.domain.valueobjects.GatekeeperLore;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Проживання окупанта у Внутрішньому Загробному Світі (Death, Посл. 5, A9/A12) — доки хтось
 * живе всередині: WITHER-імунітет (боон «напівмертвого» тіла з тієї ж вікі-фрази, що й ерозія)
 * і повільний дренаж розсудку — усім окупантам порівну. Двоє пасивних (М9) додають власну
 * поведінку понад це: {@link InternalUnderworld.Occupant#PALE_GIRL} — стрипає Нудоту/Сліпоту/
 * Темряву кожен тік; {@link InternalUnderworld.Occupant#PUS_OF_MAN} — раз на 5 хв рятує від
 * смертельного удару ({@link #armDeathWard()}, підписка на власний ключ, як коса
 * {@link FrostShadowSession}). Ерозію max HP рахує сама здібність один раз при вселенні/
 * виселенні — це разова зміна атрибута, а не те, що варто перераховувати щотакту.
 *
 * <p>На відміну від {@link FrostShadowSession} чи {@link UnderworldDescentSession} — НЕ
 * скасовується, коли власник офлайн: окупант живе В ТІЛІ, а не у світі, тож вихід із гри його
 * не розвіює. Тік просто нічого не робить, поки власника немає, і продовжує з наступним
 * логіном (сесія в пам'яті переживає relog, але НЕ рестарт — Bukkit-таск гине разом із
 * плагіном; це свідома межа М7, як і в інших сесіях Смерті).
 */
final class InternalUnderworldSession {

    static final long TICK_PERIOD_TICKS = 20L;

    /** Підписка Гною Людини живе весь час проживання; ліміт тіків тут зняв би рятунок завчасно. */
    private static final int PERMANENT_DURATION = Integer.MAX_VALUE;

    private final UUID ownerId;
    private final InternalUnderworld.Occupant occupant;
    private final String abilityName;
    private final IBeyonderContext beyonders;
    private final IEventContext events;
    private final UUID subscriptionKey = UUID.randomUUID();
    private final Map<UUID, InternalUnderworldSession> sessions;

    private int secondsSinceDrain;
    private long pusOfManReadyAtMillis;
    private BukkitTask task;

    InternalUnderworldSession(UUID ownerId, InternalUnderworld.Occupant occupant, String abilityName,
                              IBeyonderContext beyonders, IEventContext events,
                              Map<UUID, InternalUnderworldSession> sessions) {
        this.ownerId = ownerId;
        this.occupant = occupant;
        this.abilityName = abilityName;
        this.beyonders = beyonders;
        this.events = events;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** A11: рятує від смертельного удару раз на {@code PUS_OF_MAN_WARD_COOLDOWN_SECONDS}. */
    void armDeathWard() {
        if (occupant != InternalUnderworld.Occupant.PUS_OF_MAN) return;

        events.subscribeToTemporaryEvent(
                subscriptionKey,
                EntityDamageEvent.class,
                event -> event.getEntity() instanceof Player player
                        && player.getUniqueId().equals(ownerId)
                        && System.currentTimeMillis() >= pusOfManReadyAtMillis
                        && event.getFinalDamage() >= player.getHealth(),
                event -> surviveLethalBlow((Player) event.getEntity(), event),
                PERMANENT_DURATION
        );
    }

    private void surviveLethalBlow(Player owner, EntityDamageEvent event) {
        event.setCancelled(true);
        owner.setHealth(1.0);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                GatekeeperLore.PUS_OF_MAN_INVISIBILITY_SECONDS * 20, 0));
        owner.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                GatekeeperLore.PUS_OF_MAN_WEAKNESS_SECONDS * 20, 0));
        pusOfManReadyAtMillis = System.currentTimeMillis()
                + GatekeeperLore.PUS_OF_MAN_WARD_COOLDOWN_SECONDS * 1000L;

        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_ALLAY_HURT, 1.0f, 0.4f);
    }

    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !owner.isValid()) {
            return;
        }

        owner.removePotionEffect(PotionEffectType.WITHER);
        if (occupant == InternalUnderworld.Occupant.PALE_GIRL) {
            owner.removePotionEffect(PotionEffectType.NAUSEA);
            owner.removePotionEffect(PotionEffectType.BLINDNESS);
            owner.removePotionEffect(PotionEffectType.DARKNESS);
        }

        if (++secondsSinceDrain >= GatekeeperLore.UNDERWORLD_SANITY_DRAIN_PERIOD_SECONDS) {
            secondsSinceDrain = 0;
            beyonders.updateSanityLoss(ownerId, GatekeeperLore.UNDERWORLD_SANITY_DRAIN_AMOUNT);
        }

        if (isHoldingAbilityItem(owner)) {
            owner.sendActionBar(Component.text("☠ " + occupant.title + " — " + occupant.power,
                    NamedTextColor.DARK_PURPLE));
        }
    }

    /** Опис окупанта більше не в лорі здібності — щоб побачити поточну силу, тримай здібність у руці. */
    private boolean isHoldingAbilityItem(Player owner) {
        return matchesAbilityItem(owner.getInventory().getItemInMainHand())
                || matchesAbilityItem(owner.getInventory().getItemInOffHand());
    }

    private boolean matchesAbilityItem(ItemStack item) {
        if (!AbilityItemFactory.isAbilityItem(item)) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName()
                && ChatColor.stripColor(meta.getDisplayName()).equalsIgnoreCase(abilityName);
    }

    /** Окупант звільнений: сесія гасне. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId, this);
        events.unsubscribeAll(subscriptionKey);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
