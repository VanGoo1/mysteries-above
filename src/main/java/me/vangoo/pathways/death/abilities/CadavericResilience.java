package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.CorpseCollectorLore;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Death Sequence 9: Стійкість до трупних аур.
 *
 * <p>Вікі: трупозбирач «gains resistance to the Cold, Decay, and Corrosiveness of cadaveric
 * auras». Холод — абсолютний імунітет (тіло вже холодне, мерзнути нічому): freeze-тіки
 * тримаються на нулі й лочаться від ванільного годинника. Тління (Wither) та Їдкість (Poison)
 * згасають прискорено — множник живе в {@link CorpseCollectorLore}.
 */
public class CadavericResilience extends PermanentPassiveAbility {

    @Override
    public String getName() {
        return "Стійкість до трупних аур";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Тіло трупозбирача холодне й напівмертве, тож трупні аури беруть його погано.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§b❄ Повний імунітет до обмороження\n" +
                        "§a☠ Висушування і отравлення згасають у %.1f× швидше",
                CorpseCollectorLore.decayAcceleration(userSequence));
    }

    @Override
    public void onActivate(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null) return;

        player.setFreezeTicks(0);
        player.lockFreezeTicks(true);
    }

    @Override
    public void tick(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) return;

        // Лок не дає ванілі накручувати обмороження, але сторонній код (інший плагін,
        // /data) може виставити тіки напряму — тоді гасимо їх і показуємо, що поглинуто.
        if (player.getFreezeTicks() > 0) {
            player.setFreezeTicks(0);
            context.effects().spawnParticle(Particle.SNOWFLAKE,
                    player.getLocation().add(0, 1.0, 0), 6, 0.3, 0.5, 0.3);
            context.effects().playSoundForPlayer(player.getUniqueId(),
                    Sound.BLOCK_POWDER_SNOW_BREAK, 0.3f, 1.6f);
        }

        // Раз на секунду життя ГРАВЦЯ (не спільний лічильник здібності — екземпляр один
        // на весь шлях, тож власне поле збивало б крок при кількох носіях).
        if (player.getTicksLived() % 20 != 0) return;

        int extraTicks = CorpseCollectorLore.decayExtraTicksPerSecond(
                context.getCasterBeyonder().getSequence());
        UUID playerId = player.getUniqueId();
        context.entity().reducePotionEffectDuration(playerId, PotionEffectType.WITHER, extraTicks);
        context.entity().reducePotionEffectDuration(playerId, PotionEffectType.POISON, extraTicks);
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) return;

        player.lockFreezeTicks(false);
    }
}
