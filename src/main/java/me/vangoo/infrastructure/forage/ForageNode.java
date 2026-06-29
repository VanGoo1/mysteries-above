package me.vangoo.infrastructure.forage;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/**
 * Логічна нода фореджу = видимий ItemDisplay (3D-модель інгредієнта) + невидимий ArmorStand
 * (клікабельний хітбокс для ЛКМ). Обидві сутності тегуються ForageNodeCodec і прибираються парою.
 */
public final class ForageNode {

    private final ItemDisplay display;
    private final ArmorStand hitbox;
    private final String ingredientId;
    private final long createdAtMillis;

    private ForageNode(ItemDisplay display, ArmorStand hitbox, String ingredientId, long createdAtMillis) {
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
            d.setGlowing(true);
            d.setPersistent(false);
        });
        ArmorStand hitbox = world.spawn(loc.clone().subtract(0, 0.4, 0), ArmorStand.class, a -> {
            a.setVisible(false);
            a.setGravity(false);
            a.setMarker(false);
            a.setSmall(true);
            a.setBasePlate(false);
            a.setArms(false);
            a.setPersistent(false);
            a.setCanPickupItems(false);
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
        if (display != null && !display.isDead()) display.remove();
        if (hitbox != null && !hitbox.isDead()) hitbox.remove();
    }
}
