package me.vangoo.application.services;

import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.SanityPenalty;
import me.vangoo.domain.valueobjects.Spirituality;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Application Service: Застосовує ПРОСТІ штрафи за втрату здорового глузду
 * <p>
 * Відповідальності:
 * - Застосувати урон (DAMAGE)
 * - Застосувати втрату духовності (SPIRITUALITY_LOSS)
 * - Показати повідомлення про штраф
 * <p>
 * НЕ відповідає за:
 * - Rampage трансформацію (це RampageManager)
 * - Візуальні ефекти rampage (це RampageEventListener)
 */
public class SanityPenaltyHandler {

    /**
     * Застосувати простий штраф (не rampage)
     *
     * @param player   Гравець
     * @param beyonder Beyonder entity
     * @param penalty  Штраф (DAMAGE або SPIRITUALITY_LOSS)
     */
    public void applySimplePenalty(Player player, Beyonder beyonder, SanityPenalty penalty) {
        if (!penalty.hasEffect()) {
            return;
        }

        // EXTREME penalty не обробляється тут - він запускає rampage
        if (penalty.type() == SanityPenalty.PenaltyType.EXTREME) {
            throw new IllegalArgumentException(
                    "EXTREME penalty має оброблятися через RampageManager, не тут!"
            );
        }

        // Показати повідомлення
        String message = getPenaltyMessage(penalty);
        showActionBarMessage(player, message);

        // Застосувати ефект
        switch (penalty.type()) {
            case DAMAGE -> applyDamage(player, penalty.amount());
            case SPIRITUALITY_LOSS -> applySpiritualityLoss(player, beyonder, penalty.amount());
        }
    }

    // ==========================================
    // ПРИВАТНІ МЕТОДИ
    // ==========================================

    private String getPenaltyMessage(SanityPenalty penalty) {
        return switch (penalty.type()) {
            case DAMAGE -> {
                if (penalty.amount() <= 2) yield "Ваші руки злегка тремтять...";
                else if (penalty.amount() <= 5) yield "Ваші сили відмовляються слухатися!";
                else if (penalty.amount() <= 10) yield "Хаос у вашій свідомості завдає шкоди!";
                else yield "Втрата контролю посилюється!";
            }
            case SPIRITUALITY_LOSS -> {
                if (penalty.amount() <= 10) yield "Хаос викрадає вашу духовність...";
                else if (penalty.amount() <= 50) yield "Божевільний шепіт виснажує вас!";
                else yield "Втрата контролю вкрала велику частину духовності!";
            }
            default -> "Щось не так...";
        };
    }

    private void showActionBarMessage(Player player, String message) {
        ChatColor color = ChatColor.YELLOW;

        if (message.contains("Божевільний") || message.contains("посилюється")) {
            color = ChatColor.DARK_RED;
        } else if (message.contains("Хаос") || message.contains("шкоди")) {
            color = ChatColor.RED;
        } else if (message.contains("відмовляються")) {
            color = ChatColor.GOLD;
        }

        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(color + message)
        );
    }

    private void applyDamage(Player player, int amount) {
        player.damage(amount);
        player.sendMessage(ChatColor.RED + "Хаос завдає вам " + amount + " шкоди!");
    }

    private void applySpiritualityLoss(Player player, Beyonder beyonder, int amount) {
        Spirituality current = beyonder.getSpirituality();
        Spirituality reduced = current.decrement(amount);
        beyonder.setSpirituality(reduced);

        player.sendMessage(
                ChatColor.DARK_RED + "Втрата контролю вкрала " + amount + " духовності!"
        );
    }
}