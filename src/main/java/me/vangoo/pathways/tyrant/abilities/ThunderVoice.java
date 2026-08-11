package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Sequence 5: Громовий Голос. Океанський Співець передає слова на будь-яку відстань
 * у формі блискавки — вона проходить крізь будь-які перепони, а біля адресата знову
 * стає голосом.
 * <p>
 * Каст відкриває пікер гравців ({@code context.ui().openChoiceMenu}) — це безкоштовно
 * (deferred). Після вибору наступний рядок у чат ({@code promptChatInput}, вікно ~30 с)
 * не йде в загальний чат, а прилітає до адресата разом із розрядом і громом. Ресурси
 * й кулдаун списуються лише тоді, коли послання реально відправлено.
 */
public class ThunderVoice extends ActiveAbility {

    private static final int COOLDOWN = 30;
    private static final int SPIRITUALITY_COST = 25;

    @Override
    public String getName() {
        return "Громовий Голос";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fГолос Океанського Співця летить блискавкою крізь будь-які перепони.\n" +
                "Каст відкриває §bсписок гравців§f;\nПісля вибору ваш наступний рядок у чат " +
                "§7(до 30 с)§f не потрапляє в загальний чат, а б'є громом просто біля адресата " +
                "§bна будь-якій відстані§f.";
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
        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }
        UUID casterId = context.getCasterId();

        List<Player> targets = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(casterId))
                .sorted(Comparator.comparing(Player::getName))
                .map(p -> (Player) p)
                .toList();
        if (targets.isEmpty()) {
            return AbilityResult.failure("Нікого кликати — ви самі в цьому світі");
        }

        context.effects().playSoundForPlayer(casterId, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.7f, 1.6f);
        context.ui().openChoiceMenu("⚡ Громовий Голос", targets, this::head,
                chosen -> promptMessage(context, caster, chosen.getUniqueId(), chosen.getName()));
        return AbilityResult.deferred();
    }

    /** Чекаємо рядок у чат і лише після відправлення списуємо ресурси (deferred-потік). */
    private void promptMessage(IAbilityContext context, Player caster, UUID targetId, String targetName) {
        UUID casterId = caster.getUniqueId();
        context.messaging().sendMessage(casterId,
                "§bНапишіть послання для §f" + targetName + " §bв чат §7(до 30 с)§b:");
        context.ui().promptChatInput(casterId, text -> {
            if (text.isBlank()) {
                return;
            }
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isValid()) {
                context.messaging().sendMessage(casterId, "§c" + targetName + " більше не в мережі.");
                return;
            }
            if (!AbilityResourceConsumer.consumeResources(this, context.getCasterBeyonder(), context)) {
                context.messaging().sendMessage(casterId, "§cНедостатньо духовності для послання.");
                return;
            }
            context.events().publishAbilityUsedEvent(this, context.getCasterBeyonder());
            context.beyonder().updateBeyonder(casterId);
            deliver(context, caster, target, text);
        });
    }

    private void deliver(IAbilityContext context, Player caster, Player target, String text) {
        Color color = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());

        // Відправник: блискавка збирається зі спіралі вгору й зривається громом.
        context.effects().playHelixEffect(caster.getLocation(),
                caster.getLocation().clone().add(0, 2.4, 0), Particle.ELECTRIC_SPARK, 24);
        context.effects().playSoundForPlayer(caster.getUniqueId(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);

        // Адресат: розряд поруч + гуркіт, і лише тоді слова.
        context.effects().playLightningBolt(target.getLocation(), color);
        context.effects().playExplosionRingEffect(target.getLocation(), 1.6, Particle.DUST,
                new Particle.DustOptions(color, 1.3f));
        context.effects().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.3f);

        context.messaging().sendMessage(target.getUniqueId(),
                "§b⚡ Громовий Голос §f" + caster.getName() + "§b: §f" + text);
        context.messaging().sendMessage(caster.getUniqueId(),
                "§bПослання долетіло до §f" + target.getName() + "§b.");
    }

    private ItemStack head(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + player.getName());
            meta.setLore(List.of(ChatColor.GRAY + "Надіслати послання блискавкою"));
            item.setItemMeta(meta);
        }
        return item;
    }
}
