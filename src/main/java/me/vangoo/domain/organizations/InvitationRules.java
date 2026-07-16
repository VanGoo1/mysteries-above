package me.vangoo.domain.organizations;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * За який вчинок який орден виходить на гравця. Гравцю без шляху запрошення
 * не надходять — орденам потрібна сила (ранг = послідовність).
 */
public final class InvitationRules {

    public enum DeedType { APEX_KILL, BEYONDER_KILLS, RAMPAGER_STOPPED }

    private static final List<String> BLOOD_ORDERS =
            List.of("order-blood-sanctify", "order-demoness-sect", "order-bliss-society");
    private static final List<String> ORDER_ORDERS =
            List.of("order-shadow-of-order", "order-truth-school");

    private InvitationRules() {}

    public static Optional<Institution> pickOrder(DeedType deed, String deedPathwayOrNull,
                                                  String playerPathwayOrNull,
                                                  List<Institution> secretOrders,
                                                  Map<String, String> pathwayToGroup,
                                                  Random random) {
        if (playerPathwayOrNull == null) {
            return Optional.empty();
        }
        List<Institution> accepting = secretOrders.stream()
                .filter(o -> o.type() == InstitutionType.SECRET_ORDER)
                .filter(o -> o.acceptsPathway(playerPathwayOrNull))
                .toList();
        if (accepting.isEmpty()) {
            return Optional.empty();
        }
        List<Institution> preferred = switch (deed) {
            case APEX_KILL -> {
                String group = deedPathwayOrNull == null ? null : pathwayToGroup.get(deedPathwayOrNull);
                yield group == null ? List.of() : accepting.stream()
                        .filter(o -> o.accesses().stream()
                                .map(a -> pathwayToGroup.get(a.pathwayName()))
                                .filter(Objects::nonNull)
                                .anyMatch(group::equals))
                        .toList();
            }
            case BEYONDER_KILLS -> accepting.stream()
                    .filter(o -> BLOOD_ORDERS.contains(o.id())).toList();
            case RAMPAGER_STOPPED -> accepting.stream()
                    .filter(o -> ORDER_ORDERS.contains(o.id())).toList();
        };
        List<Institution> pool = preferred.isEmpty() ? accepting : preferred;
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }
}
