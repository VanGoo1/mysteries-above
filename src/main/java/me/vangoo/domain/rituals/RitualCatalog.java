package me.vangoo.domain.rituals;

import java.util.List;
import java.util.Map;

/** Каталог семи ритуалів. Гейт: Посл. 9 — 3 ритуали, 8 — 5, 7 — усі 7. */
public final class RitualCatalog {

    public static final List<RitualRecipe> ALL = List.of(
            new RitualRecipe(RitualType.LUCK_PRAYER, "Молитва удачі",
                    "Прихильність долі на кілька хвилин; зірваний ритуал накличе невдачу",
                    9, 3, Map.of("GOLD_INGOT", 1), false),
            new RitualRecipe(RitualType.SANCTIFICATION, "Освячення предмета",
                    "Трохи відновлює міцність предмета в головній руці",
                    9, 3, Map.of("IRON_INGOT", 15), false),
            new RitualRecipe(RitualType.SACRIFICE, "Жертвопринесення",
                    "Спаліть предмет із головної руки — відновіть духовність",
                    9, 3, Map.of(), true),
            new RitualRecipe(RitualType.BESTOWMENT, "Ритуал дарування",
                    "Шанс отримати інгредієнт зілля наступної послідовності",
                    8, 3, Map.of("NETHERITE_SCRAP", 3), false),
            new RitualRecipe(RitualType.MEDIUMSHIP, "Спіритизм",
                    "Духи розкажуть про минулі події поблизу",
                    8, 3, Map.of("BONE", 1), false),
            new RitualRecipe(RitualType.MIRROR_DIVINATION, "Дзеркальне ворожіння",
                    "Покаже, хто діяв у цьому місці останнім часом",
                    7, 5, Map.of("AMETHYST_SHARD", 1), false),
            new RitualRecipe(RitualType.SPIRIT_WALL, "Стіна духовності",
                    "Захисна зона навколо вівтаря на пів хвилини",
                    7, 5, Map.of("LAPIS_LAZULI", 1), false)
    );

    public static List<RitualRecipe> availableFor(int sequenceLevel) {
        return ALL.stream().filter(r -> r.availableAt(sequenceLevel)).toList();
    }

    public static RitualRecipe of(RitualType type) {
        return ALL.stream().filter(r -> r.type() == type).findFirst().orElseThrow();
    }

    private RitualCatalog() {
    }
}
