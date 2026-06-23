package me.vangoo.infrastructure.citizens;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

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

    // ── Captured from the original target ───────────────────────────────────
    private java.util.UUID    ownerCasterId;
    private Pathway           capturedPathway;    // null if target was not a Beyonder
    private Sequence          capturedSequence;   // null if target was not a Beyonder
    private int               capturedSpirituality;
    private int               capturedMaxSpirituality;
    private List<ItemStack>   capturedInventory   = new ArrayList<>();
    private List<Ability>     capturedAbilities   = new ArrayList<>();
    private String            originalPlayerName;  // for skin display

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