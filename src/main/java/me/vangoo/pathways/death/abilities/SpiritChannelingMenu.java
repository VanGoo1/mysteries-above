package me.vangoo.pathways.death.abilities;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.GatekeeperLore;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SpiritGuideLore;
import me.vangoo.domain.valueobjects.SpiritMediumLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Меню вибору духа (Death, Посл. 7): «only 1 Spirit» — тож клік по духу і є саме прикликанням.
 * Тримає лише UI; ресурси списує {@link SpiritChanneling}, разові ефекти малює
 * {@link SpiritChannelingRunner}, а Морозна тінь живе в {@link FrostShadowSession}.
 */
class SpiritChannelingMenu {

    private static final int EYE_SLOT = 11;
    private static final int FROST_SLOT = 13;
    private static final int EARTH_SLOT = 15;

    // Розділ почту (Посл. 6): третій ряд — підкорення, обмін, зліт. Істот Світу Духів і
    // накази почту тепер веде окрема здібність — «Домовленість з духами» (SpiritPactMenu).
    private static final int SUBJUGATE_SLOT = 22;
    private static final int SWAP_SLOT = 20;
    private static final int SOAR_SLOT = 24;

    private final SpiritChanneling ability;

    SpiritChannelingMenu(SpiritChanneling ability) {
        this.ability = ability;
    }

    void open(IAbilityContext context, Player caster) {
        Sequence sequence = context.getCasterBeyonder().getSequence();
        boolean guide = sequence.level() <= SpiritChanneling.GUIDE_SEQUENCE;

        Gui gui = Gui.gui()
                .title(Component.text("☠ Прикликання духів", NamedTextColor.DARK_AQUA))
                .rows(3)
                .disableAllInteractions()
                .create();
        gui.getFiller().fill(new GuiItem(named(Material.GRAY_STAINED_GLASS_PANE, " ")));

        gui.setItem(EYE_SLOT, new GuiItem(illusoryEyeIcon(sequence), event -> {
            gui.close(caster);
            ability.channelIllusoryEye(context, caster);
        }));

        gui.setItem(FROST_SLOT, new GuiItem(frostShadowIcon(sequence), event -> {
            gui.close(caster);
            ability.channelFrostShadow(context, caster);
        }));

        gui.setItem(EARTH_SLOT, new GuiItem(earthSpiritIcon(sequence), event -> {
            gui.close(caster);
            ability.channelEarthSpirit(context, caster);
        }));

        if (guide) {
            addRetinueSection(gui, context, caster, sequence);
        }

        gui.open(caster);
    }

    /** Розділ почту нежиті — лише Провіднику духів (Посл. 6 і сильніше). */
    private void addRetinueSection(Gui gui, IAbilityContext context, Player caster,
                                   Sequence sequence) {
        int size = ability.retinueSize(caster.getUniqueId());

        gui.setItem(SUBJUGATE_SLOT, new GuiItem(subjugateIcon(sequence, size), event -> {
            gui.close(caster);
            ability.subjugateUndead(context, caster);
        }));
        gui.setItem(SWAP_SLOT, new GuiItem(spiritSwapIcon(sequence), event -> {
            gui.close(caster);
            ability.spiritSwap(context, caster);
        }));

        gui.setItem(SOAR_SLOT, new GuiItem(
                order(Material.PHANTOM_MEMBRANE, ChatColor.DARK_AQUA + "✦ Зліт",
                        "Невидима сила підкидає вас угору; падіння мʼяке. "
                                + SpiritGuideLore.SOAR_COST + " духовності."), event -> {
            gui.close(caster);
            ability.soar(context, caster);
        }));
    }

    private ItemStack subjugateIcon(Sequence sequence, int size) {
        List<String> lore = List.of(
                ChatColor.GRAY + "Уся безхазяйна нежить довкола схиляє голову",
                ChatColor.GRAY + "й стає до вашими слугами.",
                " ",
                ChatColor.GRAY + "Радіус: " + ChatColor.WHITE
                        + (int) SpiritGuideLore.subjugationRadius(sequence) + " блоків",
                ChatColor.GRAY + "Почет: " + ChatColor.WHITE + size + "/"
                        + GatekeeperLore.retinueCap(sequence),
                ChatColor.GRAY + "Духовність: " + ChatColor.WHITE
                        + SpiritGuideLore.SUBJUGATION_COST,
                " ",
                ChatColor.DARK_GRAY + "Чужих слуг забрати не можна",
                ChatColor.YELLOW + "ЛКМ " + ChatColor.GRAY + "— підкорити");

        ItemStack item = new ItemStack(Material.SKELETON_SKULL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GREEN + "☠ Підкорення нежиті");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack spiritSwapIcon(Sequence sequence) {
        List<String> lore = List.of(
                ChatColor.GRAY + "Ви міняєтесь духом зі слугою: усе, що б'є",
                ChatColor.GRAY + "вас по душі - нежить отримає замість вас.",
                " ",
                ChatColor.GRAY + "Щит: " + ChatColor.WHITE
                        + SpiritGuideLore.spiritSwapWardSeconds(sequence) + " с",
                ChatColor.GRAY + "Ціна: " + ChatColor.WHITE + SpiritGuideLore.SPIRIT_SWAP_COST
                        + " духовності + " + SpiritGuideLore.SPIRIT_SWAP_SERVANT_COST + " слуга",
                " ",
                ChatColor.DARK_GRAY + "Слуга розсипається на місці",
                ChatColor.YELLOW + "ЛКМ " + ChatColor.GRAY + "— помінятись");

        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_AQUA + "✦ Обмін духом");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
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

    private ItemStack illusoryEyeIcon(Sequence sequence) {
        List<String> lore = List.of(
                ChatColor.GRAY + "Дух-споглядач із прозорим оком висить над вами",
                ChatColor.GRAY + "й не змигує, доки ви дивитесь його очима.",
                " ",
                ChatColor.GRAY + "Радіус: " + ChatColor.WHITE
                        + (int) SpiritMediumLore.illusoryEyeRadius(sequence) + " блоків",
                ChatColor.GRAY + "Тримається: " + ChatColor.WHITE
                        + SpiritMediumLore.illusoryEyeDurationSeconds(sequence) + " с",
                ChatColor.GRAY + "Духовність: " + ChatColor.WHITE
                        + SpiritMediumLore.ILLUSORY_EYE_COST,
                " ",
                ChatColor.DARK_GRAY + "Контури бачите тільки ви",
                ChatColor.YELLOW + "ЛКМ " + ChatColor.GRAY + "— покликати духа");

        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_AQUA + "👁 Ілюзорне око");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack frostShadowIcon(Sequence sequence) {
        List<String> lore = List.of(
                ChatColor.GRAY + "Тінь приносить із собою холод, крижаний обладунок",
                ChatColor.GRAY + "і величезну кристалічну косу — і ходить за вами.",
                " ",
                ChatColor.GRAY + "Домен: " + ChatColor.WHITE
                        + (int) SpiritMediumLore.frostRadius(sequence) + " блоків",
                ChatColor.GRAY + "Тримається: " + ChatColor.WHITE
                        + SpiritMediumLore.frostDurationSeconds(sequence) + " с",
                ChatColor.GRAY + "Холод: " + ChatColor.WHITE + String.format("%.1f",
                        SpiritMediumLore.frostTickDamage(sequence)) + " урону/с + Повільність",
                ChatColor.GRAY + "Коса: " + ChatColor.WHITE + String.format("×%.1f",
                        SpiritMediumLore.scytheDamageMultiplier(sequence)) + " до ближнього удару",
                ChatColor.GRAY + "Духовність: " + ChatColor.WHITE
                        + SpiritMediumLore.FROST_SHADOW_COST,
                " ",
                ChatColor.DARK_GRAY + "Свій почет духів домен не морозить",
                ChatColor.YELLOW + "ЛКМ " + ChatColor.GRAY + "— покликати духа");

        ItemStack item = new ItemStack(Material.BLUE_ICE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "❄ Морозна тінь");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack earthSpiritIcon(Sequence sequence) {
        List<String> lore = List.of(
                ChatColor.GRAY + "Пара вивітрених кам'яних рук розм'якшує землю,",
                ChatColor.GRAY + "мов болото, і затягує вашу ціль униз.",
                " ",
                ChatColor.GRAY + "Дальність: " + ChatColor.WHITE
                        + (int) SpiritMediumLore.earthRange(sequence) + " блоків",
                ChatColor.GRAY + "Тримає: " + ChatColor.WHITE
                        + SpiritMediumLore.earthHoldSeconds(sequence) + " с",
                ChatColor.GRAY + "Болото довкола: " + ChatColor.WHITE
                        + (int) SpiritMediumLore.EARTH_SWAMP_RADIUS + " блоків",
                ChatColor.GRAY + "Духовність: " + ChatColor.WHITE
                        + SpiritMediumLore.EARTH_SPIRIT_COST,
                ChatColor.GRAY + "Забирає: " + ChatColor.RED
                        + SpiritMediumLore.EARTH_SPIRIT_BLOOD_HP + " HP",
                " ",
                ChatColor.DARK_GRAY + "Дивіться на ціль перед вибором",
                ChatColor.YELLOW + "ЛКМ " + ChatColor.GRAY + "— покликати духа");

        ItemStack item = new ItemStack(Material.MUD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GREEN + "🖐 Земляний дух");
        meta.setLore(lore);
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
