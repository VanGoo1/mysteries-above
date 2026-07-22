package me.vangoo.pathways.fool.abilities;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.PaperThrowDamage;
import me.vangoo.domain.valueobjects.PaperWeaponType;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.ui.NBTBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 7: Magician — Drawing Paper As Weapons (Папір як зброя).
 *
 * <p>Еволюція {@link PaperCutter} (спільний {@link AbilityIdentity}, заміняє
 * пасивку при просуванні 8→7). Пасивний кидок паперу лишається; ПЛЮС активний
 * режим: каст відкриває меню створення зброї з паперу (бита/тростина/цегла),
 * вартість 32–64 паперу за {@link PaperWeaponType}. Ефект удару зброї застосовує
 * {@code FoolCombatListener}.
 */
public class PaperWeaponry extends ActiveAbility implements PaperThrower {

    public static final String WEAPON_TYPE_NBT = "paper_weapon_type";
    public static final String WEAPON_USES_NBT = "paper_weapon_uses";
    /**
     * Унікальний штамп кожного екземпляра — тримає зброю НЕСТАКОВНОЮ. Без нього дві
     * однакові біти злипались у стак, а {@link #degradeWeapon} списував заряд (і врешті
     * знищував) увесь стак одним ударом.
     */
    public static final String WEAPON_UID_NBT = "paper_weapon_uid";

    private static final int BASE_COST = 30;
    private static final int BASE_COOLDOWN = 3;

    private final Map<UUID, Long> lastThrowTick = new ConcurrentHashMap<>();
    // Ключ єдиної широкої підписки на удар паперовою зброєю (ставиться при першому створенні зброї).
    private volatile UUID hitTrackingKey;

    @Override
    public String getName() {
        return "Папір як зброя";
    }

    @Override
    public AbilityIdentity getIdentity() {
        return PaperCutter.IDENTITY;
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int damage = PaperThrowDamage.damageFor(userSequence);
        return "Тримаючи звичайний папір, кидайте його (ПКМ) — кинджал завдає " + damage + " шкоди. " +
                "Каст здібності відкриває меню: створіть із паперу биту, тростину чи цеглу " +
                "(32–64 паперу).";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    public boolean throwPaper(IAbilityContext context) {
        return PaperThrows.throwOnce(context, lastThrowTick);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null) return AbilityResult.failure("Гравець недоступний");
        openWeaponMenu(context, player);
        return AbilityResult.deferred();
    }

    private void openWeaponMenu(IAbilityContext context, Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("Створити з паперу"))
                .rows(1)
                .disableAllInteractions()
                .create();

        int slot = 2;
        for (PaperWeaponType type : PaperWeaponType.values()) {
            gui.setItem(slot, new GuiItem(buildIcon(type), event -> {
                event.setCancelled(true);
                player.closeInventory();
                createWeapon(context, player, type);
            }));
            slot += 2;
        }
        gui.open(player);
    }

    private ItemStack buildIcon(PaperWeaponType type) {
        ItemStack icon = new ItemStack(iconMaterial(type));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + type.displayName());
            meta.setLore(List.of(
                    ChatColor.GRAY + "Вартість: " + ChatColor.WHITE + type.paperCost() + " паперу",
                    ChatColor.GRAY + "Бонус-урон: " + ChatColor.WHITE + type.bonusDamage(),
                    ChatColor.GRAY + "Витримує ударів: " + ChatColor.WHITE + type.uses(),
                    ChatColor.YELLOW + "ЛКМ — створити"
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private void createWeapon(IAbilityContext context, Player player, PaperWeaponType type) {
        UUID casterId = context.getCasterId();
        if (countPaper(player) < type.paperCost()) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "✗ Потрібно " + type.paperCost() + " паперу");
            return;
        }
        removePaper(player, type.paperCost());

        ItemStack weapon = new ItemStack(iconMaterial(type));
        ItemMeta meta = weapon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + type.displayName());
            meta.setLore(List.of(
                    ChatColor.GRAY + "Зброя з паперу (" + type.uses() + " ударів)",
                    ChatColor.DARK_GRAY + "Здібність Фокусника"
            ));
            weapon.setItemMeta(meta);
        }
        NBTBuilder nbt = new NBTBuilder(weapon);
        weapon = nbt.setString(WEAPON_TYPE_NBT, type.name())
                .setInt(WEAPON_USES_NBT, type.uses())
                .setString(WEAPON_UID_NBT, UUID.randomUUID().toString())
                .build();

        player.getInventory().addItem(weapon);
        ensureHitTracking(context);
        context.effects().playSoundForPlayer(casterId, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.4f);
        context.messaging().sendMessage(casterId,
                ChatColor.GREEN + "✦ Створено: " + ChatColor.WHITE + type.displayName());

        // Deferred → списуємо ресурси й ставимо кулдаун лише тепер, коли зброю зроблено.
        AbilityResourceConsumer.consumeResources(this, context.getCasterBeyonder(), context);
    }

    /**
     * Ставить (один раз) власну широку підписку на удар паперовою зброєю — ефект залежить
     * від предмета (NBT), не від кастера, тож це не per-caster підписка. Окремий ключ; живе
     * до вимкнення плагіна. Ставиться при першому створенні зброї (раніше зброї не існує).
     */
    private void ensureHitTracking(IAbilityContext context) {
        if (hitTrackingKey != null) return;
        synchronized (this) {
            if (hitTrackingKey != null) return;
            UUID key = UUID.randomUUID();
            context.events().subscribeToTemporaryEvent(key,
                    EntityDamageByEntityEvent.class,
                    e -> e.getDamager() instanceof Player p
                            && weaponType(p.getInventory().getItemInMainHand()) != null,
                    this::applyWeaponHit,
                    Integer.MAX_VALUE
            );
            hitTrackingKey = key;
        }
    }

    private void applyWeaponHit(EntityDamageByEntityEvent event) {
        Player attacker = (Player) event.getDamager();
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        PaperWeaponType type = weaponType(hand);
        if (type == null) return;

        event.setDamage(event.getDamage() + type.bonusDamage());

        Entity victim = event.getEntity();
        if (victim instanceof LivingEntity living) {
            if (type.knockback() > 0) {
                Vector dir = victim.getLocation().toVector()
                        .subtract(attacker.getLocation().toVector()).setY(0);
                if (dir.lengthSquared() > 0.01) {
                    dir.normalize().multiply(type.knockback()).setY(0.35);
                    living.setVelocity(dir);
                }
            }
            if (type.slowTicks() > 0) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, type.slowTicks(), 2));
            }
        }
        if (victim.getWorld() != null) {
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2);
        }
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.3f);
        degradeWeapon(attacker, hand, type);
    }

    private void degradeWeapon(Player attacker, ItemStack hand, PaperWeaponType type) {
        int uses = new NBTBuilder(hand).getInt(hand, WEAPON_USES_NBT).orElse(1) - 1;
        if (uses <= 0) {
            attacker.getInventory().setItemInMainHand(null);
            attacker.getWorld().spawnParticle(Particle.SMOKE, attacker.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3);
            attacker.playSound(attacker.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 0.6f);
            attacker.sendActionBar(Component.text("📜 " + type.displayName() + " розсипалась"));
        } else {
            ItemStack updated = new NBTBuilder(hand).setInt(WEAPON_USES_NBT, uses).build();
            attacker.getInventory().setItemInMainHand(updated);
        }
    }

    private Material iconMaterial(PaperWeaponType type) {
        return switch (type) {
            case BAT -> Material.BLAZE_ROD;
            case CANE -> Material.STICK;
            case BRICK -> Material.BRICK;
        };
    }

    private int countPaper(Player player) {
        int total = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == Material.PAPER) total += it.getAmount();
        }
        return total;
    }

    private void removePaper(Player player, int amount) {
        int remaining = amount;
        for (ItemStack it : player.getInventory().getContents()) {
            if (remaining <= 0) break;
            if (it == null || it.getType() != Material.PAPER) continue;
            int take = Math.min(it.getAmount(), remaining);
            it.setAmount(it.getAmount() - take);
            remaining -= take;
        }
    }

    // ── API для FoolCombatListener (ефект удару зброї) ───────────────────────

    /** Тип паперової зброї у предметі, або null. */
    public static PaperWeaponType weaponType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return new NBTBuilder(item).getString(item, WEAPON_TYPE_NBT)
                .map(name -> {
                    try {
                        return PaperWeaponType.valueOf(name);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }).orElse(null);
    }

    @Override
    public void cleanUp() {
        lastThrowTick.clear();
    }
}
