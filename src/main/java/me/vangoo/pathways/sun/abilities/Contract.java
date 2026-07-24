package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Sequence 6 «Магічне засвідчення»: дивлячись на гравця — відкриває Золотий Сувій
 * ({@link ContractMenu}), що пропонує Мир / Обмін / Клятву / Засвідчення вміння (і
 * розірвання чинного контракту). Уся UI/ефект-логіка — у {@code ContractMenu}; тут лише
 * тонкий вхід. Ресурси списуються не тут, а в момент реального ефекту (deferred-потік).
 */
public class Contract extends ActiveAbility {

    private static final double RANGE = 15.0;

    private final ContractMenu menu = new ContractMenu();

    @Override
    public String getName() {
        return "Магічне засвідчення";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fДивлячись на гравця — відкриває §6Золотий Сувій§f:\n" +
                "§bМир§f (заборона взаємної " + "шкоди)\n" +
                "§6Обмін§f (безпечний обмін предметом)\n" +
                "§eКлятва§f (обіцянка дії до строку)\n" +
                "§dЗасвідчення вміння§f (благословляє удар союзника).\n" +
                "§cПорушення карається Божественною карою.";
    }

    @Override
    public int getSpiritualityCost() {
        return 80;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return 30;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Optional<Player> targetOpt = context.targeting().getTargetedPlayer(RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Дивіться на гравця, щоб відкрити Золотий Сувій.");
        }
        Player target = targetOpt.get();
        if (target.getUniqueId().equals(casterId)) {
            return AbilityResult.failure("Не можна укласти контракт із собою.");
        }
        Player caster = Bukkit.getPlayer(casterId);
        if (caster == null) {
            return AbilityResult.failure("Гравець офлайн.");
        }

        menu.openScroll(context, this, caster, target);
        return AbilityResult.deferred();
    }

    @Override
    public void cleanUp() {
        menu.clear();
    }
}
