package me.vangoo.domain.forage;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Чисте правило мапінгу «оригінальний блок → блок-донор» фореджу. Донор — блок, якого немає
 * в Оверворлді природно; ресурспак малює його «зачарованим». Overrides перемагають дефолти;
 * дефолт обирається за класом цілі (рослина/листя). Імена матеріалів — рядки (без Bukkit);
 * їхню валідність перевіряє завантажувач конфіга.
 */
public final class ForageDonorMap {

    private final String defaultPlantDonor;
    private final String defaultLeavesDonor;
    private final Map<String, String> overrides;

    public ForageDonorMap(String defaultPlantDonor, String defaultLeavesDonor, Map<String, String> overrides) {
        this.defaultPlantDonor = normalize(defaultPlantDonor);
        this.defaultLeavesDonor = normalize(defaultLeavesDonor);
        Map<String, String> copy = new HashMap<>();
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            copy.put(normalize(e.getKey()), normalize(e.getValue()));
        }
        this.overrides = Map.copyOf(copy);
    }

    /** Донор для оригінального блока; {@code leaves} — чи належить ціль до листя. */
    public String donorFor(String originalMaterial, boolean leaves) {
        String override = overrides.get(normalize(originalMaterial));
        if (override != null) return override;
        return leaves ? defaultLeavesDonor : defaultPlantDonor;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }
}
