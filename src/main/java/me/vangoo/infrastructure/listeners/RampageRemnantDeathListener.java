package me.vangoo.infrastructure.listeners;

import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.infrastructure.items.CharacteristicExtractor;
import me.vangoo.infrastructure.items.WardenRemnantCodec;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;

/**
 * Виплачує Характеристику, коли вбито Бешаного Warden, у який трансформувався потойбічний при
 * втраті контролю. Есенцію (шлях+seq) несе сам Warden — її туди записує {@link RampageEventListener}
 * під час трансформації; тут вона конденсується у предмет на місці смерті.
 */
public class RampageRemnantDeathListener implements Listener {

    private final WardenRemnantCodec remnantCodec;
    private final CharacteristicExtractor extractor;

    public RampageRemnantDeathListener(WardenRemnantCodec remnantCodec, CharacteristicExtractor extractor) {
        this.remnantCodec = remnantCodec;
        this.extractor = extractor;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Optional<Characteristic> remnant = remnantCodec.read(event.getEntity());
        if (remnant.isEmpty()) {
            return;
        }
        Characteristic c = remnant.get();
        extractor.extractTo(event.getEntity().getLocation(), c.pathwayName(), c.sequence());
    }
}
