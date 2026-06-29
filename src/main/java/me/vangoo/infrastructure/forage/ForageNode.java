package me.vangoo.infrastructure.forage;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/**
 * Логічна нода фореджу = видимий ItemDisplay (3D-модель інгредієнта) + Interaction-сутність
 * (клікабельний хітбокс для ПКМ). Обидві сутності тегуються ForageNodeCodec і прибираються парою.
 */
public final class ForageNode {

    /** Розмір кубічного хітбокса Interaction (блоки) — трохи більший за видиму модель. */
    private static final float HITBOX_SIZE = 0.7f;

    private final ItemDisplay display;
    private final Interaction hitbox;
    private final String ingredientId;
    private final long createdAtMillis;

    private ForageNode(ItemDisplay display, Interaction hitbox, String ingredientId, long createdAtMillis) {
        this.display = display;
        this.hitbox = hitbox;
        this.ingredientId = ingredientId;
        this.createdAtMillis = createdAtMillis;
    }

    public static ForageNode spawn(Location loc, ItemStack model, String ingredientId, ForageNodeCodec codec) {
        World world = loc.getWorld();
        if (world == null) throw new IllegalArgumentException("Location has no world");

        ItemDisplay display = world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(model);
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
        });

        // Локація Interaction = нижній центр його хітбокса; опускаємо на пів-висоти, щоб коробка
        // огорнула видиму модель і ПКМ влучав саме по ній.
        Location hitboxLoc = loc.clone().subtract(0, HITBOX_SIZE / 2.0, 0);
        Interaction hitbox = world.spawn(hitboxLoc, Interaction.class, in -> {
            in.setInteractionWidth(HITBOX_SIZE);
            in.setInteractionHeight(HITBOX_SIZE);
            in.setResponsive(true);
            in.setPersistent(false);
        });

        codec.tag(hitbox, ingredientId, display.getUniqueId());
        codec.tag(display, ingredientId, hitbox.getUniqueId());
        return new ForageNode(display, hitbox, ingredientId, System.currentTimeMillis());
    }

    public boolean isAlive() {
        return display.isValid() && hitbox.isValid();
    }

    public long ageMillis() { return System.currentTimeMillis() - createdAtMillis; }

    public Location getLocation() { return hitbox.getLocation(); }

    public String getIngredientId() { return ingredientId; }

    public void remove() {
        if (!display.isDead()) display.remove();
        if (!hitbox.isDead()) hitbox.remove();
    }
}
