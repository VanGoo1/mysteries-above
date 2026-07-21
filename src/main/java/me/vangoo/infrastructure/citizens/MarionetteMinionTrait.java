package me.vangoo.infrastructure.citizens;

import me.vangoo.application.services.PathwayManager;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.DataKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Citizens2 Trait attached to every Marionettist puppet NPC.
 *
 * Stores:
 *  - The original target's Beyonder data (pathway, sequence, spirituality)
 *  - The original target's inventory snapshot
 *  - Which caster UUID "owns" this marionette
 *
 * The trait is intentionally lightweight — it is a pure data carrier.
 * All logic lives in MarionettistControl.
 */
@TraitName("marionette_minion")
public class MarionetteMinionTrait extends Trait {

    private static final Logger LOGGER = Logger.getLogger(MarionetteMinionTrait.class.getName());

    /**
     * Citizens створює трейт рефлексією (конструктор без аргументів) і викликає {@link #load(DataKey)}
     * під час завантаження NPC. Для регідрації шляху потрібен {@link PathwayManager}, тож подаємо його
     * статичним bootstrap-хуком із плагіна ДО того, як Citizens завантажить NPC.
     */
    private static PathwayManager pathwayManager;

    public static void bindPathwayManager(PathwayManager manager) {
        pathwayManager = manager;
    }

    // ── Captured from the original target ───────────────────────────────────
    private java.util.UUID    ownerCasterId;
    private Pathway           capturedPathway;    // null if target was not a Beyonder
    private Sequence          capturedSequence;   // null if target was not a Beyonder
    private int               capturedSpirituality;
    private int               capturedMaxSpirituality;
    private List<ItemStack>   capturedInventory   = new ArrayList<>();
    private List<Ability>     capturedAbilities   = new ArrayList<>();
    private String            originalPlayerName;  // for skin display
    private String            skinTextureValue;    // null if target had no skin
    private String            skinTextureSignature; // null if target had no skin
    // Тип сутності цілі: PLAYER для гравця, конкретний моб для не-гравця. Визначає,
    // як виглядає свап (гравець-скін vs packet-маска моба) і як відновити NPC на виході.
    private String            marionetteEntityType = org.bukkit.entity.EntityType.PLAYER.name();
    private double            capturedMaxHealth   = 20.0; // макс. HP цілі (з бонусом послідовності)
    private double            capturedHealth      = 20.0; // поточне HP цілі на момент перетворення

    public MarionetteMinionTrait() {
        super("marionette_minion");
    }

    // ── Initialisation ───────────────────────────────────────────────────────

    /**
     * Called once after the NPC is created to inject all captured data.
     */
    public void initialise(
            java.util.UUID ownerCasterId,
            String         originalPlayerName,
            Beyonder        targetBeyonder,        // may be null
            List<ItemStack> capturedInventory
    ) {
        this.ownerCasterId       = ownerCasterId;
        this.originalPlayerName  = originalPlayerName;
        this.capturedInventory   = new ArrayList<>(capturedInventory);

        if (targetBeyonder != null) {
            this.capturedPathway         = targetBeyonder.getPathway();
            this.capturedSequence        = targetBeyonder.getSequence();
            this.capturedSpirituality    = targetBeyonder.getSpiritualityValue();
            this.capturedMaxSpirituality = targetBeyonder.getMaxSpirituality();
            // Deep-copy ability list so mutation of the live beyonder doesn't affect us
            this.capturedAbilities       = new ArrayList<>(targetBeyonder.getAbilities());
        }
    }

    // ── Skin texture ────────────────────────────────────────────────────────

    public void setSkin(String value, String signature) {
        this.skinTextureValue     = value;
        this.skinTextureSignature = signature;
    }

    public String getSkinTextureValue()     { return skinTextureValue; }
    public String getSkinTextureSignature() { return skinTextureSignature; }

    // ── Тип сутності цілі (PLAYER vs моб) ─────────────────────────────────────

    public void setMarionetteEntityType(org.bukkit.entity.EntityType type) {
        if (type != null) this.marionetteEntityType = type.name();
    }

    public org.bukkit.entity.EntityType getMarionetteEntityType() {
        try {
            return org.bukkit.entity.EntityType.valueOf(marionetteEntityType);
        } catch (IllegalArgumentException e) {
            return org.bukkit.entity.EntityType.PLAYER;
        }
    }

    /** True, якщо ціль була не-гравцем (мобом) — свап іде через packet-маску моба. */
    public boolean isMobMarionette() {
        return getMarionetteEntityType() != org.bukkit.entity.EntityType.PLAYER;
    }

    // ── Captured health (HP цілі разом із бонусом від послідовності) ───────────

    public void setCapturedHealth(double current, double max) {
        this.capturedHealth    = current;
        this.capturedMaxHealth = max;
    }

    public double getCapturedHealth()    { return capturedHealth; }
    public double getCapturedMaxHealth() { return capturedMaxHealth; }

    /**
     * Повна заміна знімка інвентаря маріонетки (повна персистентність).
     * Список — це повний вміст інвентаря (storage+броня+друга рука), null = порожній слот.
     */
    public void setCapturedInventory(List<ItemStack> inventory) {
        this.capturedInventory = new ArrayList<>(inventory);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public java.util.UUID    getOwnerCasterId()        { return ownerCasterId; }
    public String            getOriginalPlayerName()    { return originalPlayerName; }
    public Pathway           getCapturedPathway()       { return capturedPathway; }
    public Sequence          getCapturedSequence()      { return capturedSequence; }
    public int               getCapturedSpirituality()  { return capturedSpirituality; }
    public int               getCapturedMaxSpirituality(){ return capturedMaxSpirituality; }
    public List<ItemStack>   getCapturedInventory()     { return capturedInventory; }
    public List<Ability>     getCapturedAbilities()     { return capturedAbilities; }

    /** True only if the original target was a Beyonder. */
    public boolean wasBeyonder() {
        return capturedPathway != null && capturedSequence != null;
    }

    /**
     * Consume the inventory snapshot (drop it at a location).
     * Clears the internal list so items are never dropped twice.
     */
    public List<ItemStack> consumeInventory() {
        List<ItemStack> copy = new ArrayList<>(capturedInventory);
        capturedInventory.clear();
        return copy;
    }

    /**
     * Build a Spirituality value object from the captured pool.
     */
    public Spirituality buildCapturedSpirituality() {
        int current = Math.min(capturedSpirituality, capturedMaxSpirituality);
        return Spirituality.of(current, capturedMaxSpirituality);
    }

    // ── Persistence (Citizens2 saves.yml) ────────────────────────────────────

    @Override
    public void save(DataKey key) {
        if (ownerCasterId != null) key.setString("owner", ownerCasterId.toString());
        if (originalPlayerName != null) key.setString("name", originalPlayerName);
        if (skinTextureValue != null) key.setString("skin.value", skinTextureValue);
        if (skinTextureSignature != null) key.setString("skin.signature", skinTextureSignature);
        key.setString("entityType", marionetteEntityType);

        // Шлях/послідовність зберігаємо як ім'я+рівень (регідрація через PathwayManager).
        if (capturedPathway != null && capturedSequence != null) {
            key.setString("pathway", capturedPathway.getName());
            key.setInt("sequence", capturedSequence.level());
        }
        key.setInt("spirituality", capturedSpirituality);
        key.setInt("maxSpirituality", capturedMaxSpirituality);
        key.setDouble("health", capturedHealth);
        key.setDouble("maxHealth", capturedMaxHealth);

        key.setString("inventory", MarionetteInventoryCodec.encode(capturedInventory));
    }

    @Override
    public void load(DataKey key) {
        String ownerStr = key.getString("owner", "");
        if (!ownerStr.isEmpty()) {
            try {
                ownerCasterId = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Маріонетка має некоректний owner UUID: " + ownerStr);
            }
        }
        originalPlayerName   = emptyToNull(key.getString("name", ""));
        skinTextureValue     = emptyToNull(key.getString("skin.value", ""));
        skinTextureSignature = emptyToNull(key.getString("skin.signature", ""));
        // Старі saves.yml без поля → PLAYER (маріонетки-мобів там ще не було).
        marionetteEntityType = key.getString("entityType", org.bukkit.entity.EntityType.PLAYER.name());

        capturedSpirituality    = key.getInt("spirituality", 0);
        capturedMaxSpirituality = key.getInt("maxSpirituality", 0);
        capturedHealth          = key.getDouble("health", 20.0);
        capturedMaxHealth       = key.getDouble("maxHealth", 20.0);

        // Регідрація шляху: невідомий/відсутній шлях → маріонетка лишається не-Потойбічною.
        String pathwayName = key.getString("pathway", "");
        if (!pathwayName.isEmpty() && pathwayManager != null) {
            Pathway pathway = pathwayManager.getPathway(pathwayName);
            int level = key.getInt("sequence", -1);
            if (pathway != null && level >= 0 && level <= 9) {
                capturedPathway  = pathway;
                capturedSequence = Sequence.of(level);
            } else {
                LOGGER.warning("Не вдалося відновити шлях маріонетки: '" + pathwayName
                        + "' рівень " + level + " — трактуємо як не-Потойбічну.");
            }
        }

        capturedInventory = MarionetteInventoryCodec.decode(key.getString("inventory", ""));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    // ── Trait lifecycle (Citizens2 hooks) ────────────────────────────────────

    @Override
    public void onAttach() {
        // Nothing to do on attach — data is injected via initialise()
    }

    @Override
    public void onDespawn() {
        // Cleanup if needed when NPC despawns (e.g. server shutdown)
    }

    @Override
    public void onRemove() {
        capturedInventory.clear();
        capturedAbilities.clear();
    }
}