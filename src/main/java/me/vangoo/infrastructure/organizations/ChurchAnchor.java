package me.vangoo.infrastructure.organizations;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;

import java.util.Optional;
import java.util.Set;

/**
 * Якір усередині храмової будівлі: сутність `minecraft:marker` зі скорборд-тегами,
 * яку датапак приносить у світ разом зі структурою.
 *
 * Bukkit не дає доступу до кастомного NBT сутності, тож увесь контракт «датапак →
 * плагін» тримається на тегах і на власному повороті мітки:
 *
 * <pre>
 *   Tags: ["lotm_anchor", "lotm_role_priest", "lotm_church_eternal_sun"]
 *   Rotation: [180f, 0f]        ← куди дивиться NPC
 * </pre>
 *
 * `lotm_church_*` обов'язковий для ролі `priest`: тип храму плагін дізнається лише
 * з мітки. Для ролі `shrine` його навмисно немає — шрайн приходить із датапаку
 * «нічийним», а церкву йому призначає плагін і дописує тег назад (див.
 * {@link ShrineService}). Тому {@code institutionId} тут може бути {@code null}.
 */
public record ChurchAnchor(String institutionId, String role, Marker marker,
                           ChurchSiteRepository.Box box) {

    public static final String TAG_ANCHOR = "lotm_anchor";
    private static final String TAG_ROLE_PREFIX = "lotm_role_";
    private static final String TAG_CHURCH_PREFIX = "lotm_church_";
    private static final String TAG_BOX_PREFIX = "lotm_box_";

    public static final String ROLE_PRIEST = "priest";
    public static final String ROLE_SHRINE = "shrine";

    public static Optional<ChurchAnchor> of(Entity entity) {
        if (!(entity instanceof Marker marker)) {
            return Optional.empty();
        }
        Set<String> tags = marker.getScoreboardTags();
        if (!tags.contains(TAG_ANCHOR)) {
            return Optional.empty();
        }
        String role = tagValue(tags, TAG_ROLE_PREFIX);
        if (role == null) {
            return Optional.empty();
        }
        String church = tagValue(tags, TAG_CHURCH_PREFIX);
        return Optional.of(new ChurchAnchor(
                church == null ? null : institutionIdOf(church), role, marker,
                parseBox(tagValue(tags, TAG_BOX_PREFIX))));
    }

    /** `eternal_sun` (суфікс тега й імені NBT) → `church-eternal-sun` (id інституції). */
    private static String institutionIdOf(String churchTagValue) {
        return "church-" + churchTagValue.replace('_', '-');
    }

    /**
     * Зворотний бік {@link #institutionIdOf}: тег, яким плагін ШТАМПУЄ призначену церкву
     * на мітку шрайна. Живе тут, щоб літерал префікса лишався в одному класі.
     */
    public static String churchTag(String institutionId) {
        return TAG_CHURCH_PREFIX + institutionId.replaceFirst("^church-", "").replace('-', '_');
    }

    /**
     * `lotm_box_<півширина>_<вниз>_<вгору>` — габарити будівлі відносно мітки, які
     * інструмент рахує з розміру NBT. Півширина квадратна, бо worldgen повертає
     * структуру випадково. Тег може бути відсутній (стара структура) — тоді храм
     * просто не отримає захисту від поламки, і це не помилка.
     */
    private static ChurchSiteRepository.Box parseBox(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("_");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new ChurchSiteRepository.Box(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String tagValue(Set<String> tags, String prefix) {
        return tags.stream()
                .filter(tag -> tag.startsWith(prefix))
                .map(tag -> tag.substring(prefix.length()))
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse(null);
    }

    /** Мітка священика без тега церкви — брак у структурі: заявляти нічого. */
    public boolean isPriest() {
        return ROLE_PRIEST.equals(role) && institutionId != null;
    }

    public boolean isShrine() {
        return ROLE_SHRINE.equals(role);
    }
}
