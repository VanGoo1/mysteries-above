package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Sequence 8: Лють. Народ Люті на мить впадає в шал — тіло наливається силою, а рухи
 * пришвидшуються, тож удари виходять за звичайні межі. Короткий сплеск Сили + Швидкості, чия
 * тривалість і рівень ростуть із Sequence (Seq 8 — Сила I / Швидкість II на 8 с, Seq 5 —
 * Сила II / Швидкість III на 11 с). Цей стан «оп'яніння яростю» живить Нищівний Удар.
 */
public class Wrath extends ActiveAbility {

    private static final int BASE_DURATION_TICKS = 140; // MODERATE-скейл: Seq 8 ≈ 8 с, Seq 5 ≈ 11 с
    private static final int COOLDOWN = 20;
    private static final int SPIRITUALITY_COST = 50;
    /** «Очі палають яростю» — вогняний акцент ефекту, не колір шляху. */
    private static final Color RAGE_EYES = Color.fromRGB(255, 70, 40);

    @Override
    public String getName() {
        return "Лють";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fВи стаєте шаленим: очі палають яростю, м'язи набрякають, тіло наливається " +
                "§cсилою " + (strengthAmplifier(sequence) + 1) + "§f, а рухи стають §bстрімкими " + "§f. Триває §b" +
                (durationTicks(sequence) / 20) + " с§f.";
    }

    private int durationTicks(Sequence sequence) {
        return scaleValue(BASE_DURATION_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
    }

    /** Seq 8 — Сила I, Seq 5 — Сила II, Seq 2 — Сила III. */
    private int strengthAmplifier(Sequence sequence) {
        return SequenceScaler.getSequencePower(sequence.level()) / 3;
    }

    /** Seq 8 — Швидкість II, Seq 5 — Швидкість III, Seq 1 — Швидкість IV. */
    private int speedAmplifier(Sequence sequence) {
        return 1 + SequenceScaler.getSequencePower(sequence.level()) / 4;
    }

    @Override
    public int getSpiritualityCost() {
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }

        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        int duration = durationTicks(sequence);
        context.entity().applyPotionEffect(casterId, PotionEffectType.STRENGTH, duration, strengthAmplifier(sequence));
        context.entity().applyPotionEffect(casterId, PotionEffectType.SPEED, duration, speedAmplifier(sequence));

        // Візуал: штормова спіраль від ніг + аура кольору шляху + палаючі очі + хвиля «набряклих м'язів» + рев.
        Color color = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());
        Location feet = player.getLocation();
        context.effects().playRisingSpiral(feet, 2.5, 0.8, color, 30);
        context.effects().playFadingAura(feet, color, 30);
        context.effects().playGlowingDust(player.getEyeLocation(), RAGE_EYES);
        context.effects().playExplosionRingEffect(feet.clone().add(0, 1.0, 0), 1.6,
                Particle.DUST, new Particle.DustOptions(color, 1.4f));
        context.effects().playSound(feet, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.9f);

        return AbilityResult.success();
    }
}
