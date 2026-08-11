package me.vangoo.pathways.death.abilities;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.domain.abilities.core.IAbilityContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Меню наказів почту (Death, Посл. 6): звичайне ПКМ {@link SpiritPact}, коли гравець не
 * дивиться на істоту Світу Духів для домовленості (shift-ПКМ обходить це меню зовсім).
 * Тримає лише UI; мутації йдуть через {@link SpiritPact}, спільний {@link UndeadRetinue}.
 */
class SpiritPactMenu {

    private static final int FOLLOW_SLOT = 11;
    private static final int ATTACK_SLOT = 13;
    private static final int GUARD_SLOT = 15;
    private static final int HIDE_SLOT = 20;
    private static final int DISBAND_SLOT = 24;

    private final SpiritPact ability;

    SpiritPactMenu(SpiritPact ability) {
        this.ability = ability;
    }

    void open(IAbilityContext context, Player caster) {
        Gui gui = Gui.gui()
                .title(Component.text("☠ Домовленість з духами", NamedTextColor.DARK_AQUA))
                .rows(3)
                .disableAllInteractions()
                .create();
        gui.getFiller().fill(new GuiItem(named(Material.GRAY_STAINED_GLASS_PANE, " ")));

        gui.setItem(FOLLOW_SLOT, new GuiItem(
                order(Material.BONE, ChatColor.DARK_GREEN + "☠ За мною",
                        "Слуги йдуть за вами й нікого не чіпають."), event -> {
            gui.close(caster);
            ability.orderFollow(context, caster);
        }));
        gui.setItem(ATTACK_SLOT, new GuiItem(
                order(Material.IRON_SWORD, ChatColor.RED + "⚔ Убити ціль",
                        "Дивіться на ціль: слуги кинуться на неї."), event -> {
            gui.close(caster);
            ability.orderAttack(context, caster);
        }));
        gui.setItem(GUARD_SLOT, new GuiItem(
                order(Material.SHIELD, ChatColor.GOLD + "⛨ Стерегти місце",
                        "Слуги лишаються там, де ви стоїте."), event -> {
            gui.close(caster);
            ability.orderGuard(context, caster);
        }));

        boolean hidden = ability.retinueHidden(caster.getUniqueId());
        gui.setItem(HIDE_SLOT, new GuiItem(hideIcon(hidden), event -> {
            gui.close(caster);
            ability.toggleHide(context, caster);
        }));
        gui.setItem(DISBAND_SLOT, new GuiItem(
                order(Material.BONE_MEAL, ChatColor.GRAY + "✖ Розпустити",
                        "Слуги розсипаються на порох."), event -> {
            gui.close(caster);
            ability.disbandRetinue(context, caster);
        }));

        gui.open(caster);
    }

    private ItemStack hideIcon(boolean hidden) {
        String name = hidden
                ? ChatColor.DARK_GRAY + "👁 Показати"
                : ChatColor.DARK_PURPLE + "🌑 Приховати";
        String hint = hidden
                ? "Слуги зараз розчинені у тінях."
                : "Слуги розчиняться в тінях — невидимі, поки не покажете знову.";
        return order(hidden ? Material.GLOW_INK_SAC : Material.INK_SAC, name, hint);
    }

    private ItemStack order(Material material, String name, String hint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(ChatColor.GRAY + hint, " ",
                ChatColor.YELLOW + "ЛКМ " + ChatColor.GRAY + "— наказати"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
