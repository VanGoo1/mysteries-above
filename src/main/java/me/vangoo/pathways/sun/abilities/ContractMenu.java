package me.vangoo.pathways.sun.abilities;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.contracts.Contract;
import me.vangoo.domain.contracts.ContractTerm;
import me.vangoo.domain.entities.Beyonder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Золотий Сувій — GUI-шар здібності «Магічне засвідчення» (Sun, Посл. 6). Тримає лише
 * ефекти/UI: рендер сувою, церемонію підпису й потоки чотирьох дій. Доменні переходи —
 * через {@code context.contracts()}; ресурси — через {@link AbilityResourceConsumer}.
 *
 * <p><b>Обмін — без застави (validate-at-commit).</b> Жоден предмет не покидає інвентар до
 * фінальної синхронної транзакції: пропозиція зберігає лише знімок запропонованого стака
 * (в пам'яті {@link #pendingOffers}), а перевірка й переміщення відбуваються одним кроком у
 * момент підтвердження цілі. Немає застави → дублювання й втрата предметів структурно
 * неможливі, окрема сесія/таск не потрібні.
 */
public class ContractMenu {

    private static final int RANGE = 15;
    private static final int CONSENT_SECONDS = 8;
    private static final long OATH_DAYS = 3;
    private static final int CEREMONY_TICKS = 20;

    // Сертифікація (підтримка союзника)
    private static final double CERTIFY_MULTIPLIER = 1.3;
    private static final int CERTIFY_WINDOW_SECONDS = 10;
    private static final double CERTIFY_HOLY_BONUS = 4.0;
    private static final int CERTIFY_FIRE_TICKS = 60;

    private static final Color SUN_GOLD = PathwayBranding.liquidOf("Sun");

    /** Незавершені пропозиції обміну (без застави): proposer → offer. */
    private final Map<UUID, TradeOffer> pendingOffers = new ConcurrentHashMap<>();

    private record TradeOffer(UUID proposer, UUID target, ItemStack offered) {
    }

    /** Скидає незавершені пропозиції (нічого повертати — застави немає). Кличе {@code cleanUp()}. */
    public void clear() {
        pendingOffers.clear();
    }

    public void openScroll(IAbilityContext context, ActiveAbility ability, Player caster, Player target) {
        Gui gui = Gui.gui()
                .title(Component.text("☀ Золотий Сувій", NamedTextColor.GOLD))
                .rows(3)
                .disableAllInteractions()
                .create();

        ItemStack filler = named(Material.YELLOW_STAINED_GLASS_PANE, " ");
        gui.getFiller().fill(new GuiItem(filler));

        gui.setItem(10, new GuiItem(tile(Material.WHITE_BANNER, ChatColor.AQUA + "Угода про Мир",
                        "Сторони не можуть шкодити одна одній.", "Порушення — Божественна кара."),
                e -> { gui.close(caster); beginPeace(context, ability, caster, target); }));

        gui.setItem(12, new GuiItem(tile(Material.CHEST, ChatColor.GOLD + "Угода про Обмін",
                        "Безпечний обмін предметом у руці.", "Обидва отримують одночасно."),
                e -> { gui.close(caster); beginTrade(context, ability, caster, target); }));

        gui.setItem(14, new GuiItem(tile(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Клятва Дії",
                        "Ціль обіцяє віддати предмет до строку (3 дні).", "Виконати — присівши, ПКМ по вас із предметом.",
                        "Строк вийшов без виконання — Божественна кара."),
                e -> { gui.close(caster); beginOath(context, ability, caster, target); }));

        gui.setItem(16, new GuiItem(tile(Material.GOLDEN_SWORD, ChatColor.LIGHT_PURPLE + "Засвідчення Вміння",
                        "Благословляє наступний удар союзника:", "+шкода, святий та вогняний урон."),
                e -> { gui.close(caster); certifyAlly(context, ability, caster, target); }));

        Optional<Contract> existing = context.contracts().findActiveBetween(caster.getUniqueId(), target.getUniqueId());
        if (existing.isPresent()) {
            gui.setItem(22, new GuiItem(tile(Material.SHEARS, ChatColor.RED + "Розірвати контракт",
                            "Завершити чинний контракт (" + termLabel(existing.get().term()) + ")", "за згодою сторін."),
                    e -> { gui.close(caster); beginSettlement(context, caster, target, existing.get()); }));
        }

        gui.open(caster);
    }

    // ---------------------------------------------------------------- Мир / Клятва

    private void beginPeace(IAbilityContext context, ActiveAbility ability, Player caster, Player target) {
        Contract proposed = context.contracts().propose(
                caster.getUniqueId(), target.getUniqueId(), ContractTerm.PEACE, Map.of());
        bindWithConsent(context, ability, caster, target, proposed, "Мир",
                "Умова: сторони не можуть шкодити одна одній. Порушення — Божественна кара.", null);
    }

    private void beginOath(IAbilityContext context, ActiveAbility ability, Player caster, Player target) {
        promptHeldItem(context, caster, "предмет-зобов'язання", obligation -> {
            long deadline = System.currentTimeMillis() + OATH_DAYS * 24L * 60 * 60 * 1000;
            Map<String, String> params = new HashMap<>();
            params.put("amount", String.valueOf(obligation.getAmount()));
            params.put("item", obligation.getType().name());
            params.put("deadlineEpochMillis", String.valueOf(deadline));

            Contract proposed = context.contracts().propose(
                    caster.getUniqueId(), target.getUniqueId(), ContractTerm.DEBT, params);
            String itemLabel = itemLabel(obligation);
            String detail = "Умова: ви зобов'язуєтесь віддати " + obligation.getAmount() + "× " + itemLabel +
                    " протягом " + OATH_DAYS + " днів. Не виконаєте до строку — Божественна кара.";
            String hint = "☀ Клятва: віддайте " + obligation.getAmount() + "× " +
                    itemLabel + " кредитору до строку — присівши, ПКМ по ньому з предметом.";
            bindWithConsent(context, ability, caster, target, proposed, "Клятва", detail, hint);
        });
    }

    /**
     * Дає кастеру вікно, щоб <b>перекласти</b> потрібний предмет у руку (при відкритті сувою в
     * руці ще диск-тригер здібності) й підтвердити присіданням. Захоплює предмет у момент
     * присідання, а не кліку в меню.
     */
    private void promptHeldItem(IAbilityContext context, Player caster, String what,
                                java.util.function.Consumer<ItemStack> onConfirm) {
        UUID casterId = caster.getUniqueId();
        context.messaging().sendMessage(casterId, ChatColor.GOLD + "☀ Візьміть " + what +
                " у руку й присядьте (Shift) протягом " + CONSENT_SECONDS + " с, щоб підтвердити.");
        context.ui().monitorSneaking(casterId, CONSENT_SECONDS * 20, accepted -> {
            Player c = Bukkit.getPlayer(casterId);
            if (!accepted || c == null) {
                context.messaging().sendMessage(casterId, ChatColor.RED + "Скасовано — предмет не підтверджено.");
                return;
            }
            ItemStack held = c.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                context.messaging().sendMessage(casterId, ChatColor.RED + "У руці нічого немає — контракт скасовано.");
                return;
            }
            onConfirm.accept(held.clone());
        });
    }

    /** Пропонує термін цілі; згода — присідання ({@code monitorSneaking}); підпис — після церемонії.
     * {@code debtorHint} (nullable) шлеться цілі після підпису — для Клятви пояснює, як виконати. */
    private void bindWithConsent(IAbilityContext context, ActiveAbility ability,
                                 Player caster, Player target, Contract proposed, String label,
                                 String proposalDetail, String debtorHint) {
        UUID casterId = caster.getUniqueId();
        UUID targetId = target.getUniqueId();
        int waitTicks = CONSENT_SECONDS * 20;
        String casterName = context.playerData().getName(casterId);

        context.effects().playSound(target.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.4f);
        target.sendMessage(ChatColor.GOLD + "☀ " + casterName + " пропонує контракт (" + label + ").");
        if (proposalDetail != null) {
            target.sendMessage(ChatColor.YELLOW + proposalDetail);
        }
        target.sendMessage(ChatColor.GOLD + "Присядьте (Shift) протягом " + CONSENT_SECONDS + " с, щоб засвідчити.");
        target.showTitle(Title.title(
                Component.text("Пропозиція контракту", NamedTextColor.GOLD),
                Component.text(casterName + " • " + label, NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✎ Пропозицію надіслано: " + target.getName(), NamedTextColor.GOLD));

        context.ui().monitorSneaking(targetId, waitTicks, accepted -> {
            if (!accepted) {
                context.contracts().cancelProposal(proposed.id());
                notifyBoth(context, casterId, targetId, "✎ Пропозицію контракту відхилено.");
                return;
            }
            if (!charge(context, ability)) {
                context.contracts().cancelProposal(proposed.id());
                context.messaging().sendMessage(casterId,
                        ChatColor.RED + "Недостатньо духовності, щоб засвідчити контракт.");
                return;
            }
            runSigningCeremony(context, casterId, targetId, () -> {
                context.contracts().sign(proposed.id());
                notifyBoth(context, casterId, targetId, "✎ Контракт засвідчено (" + label + ")!");
                if (debtorHint != null) {
                    context.messaging().sendMessage(targetId, ChatColor.GOLD + debtorHint);
                }
            });
        });
    }

    private void beginSettlement(IAbilityContext context, Player caster, Player target, Contract existing) {
        UUID casterId = caster.getUniqueId();
        UUID targetId = target.getUniqueId();
        String casterName = context.playerData().getName(casterId);

        context.effects().playSound(target.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
        target.sendMessage(ChatColor.GOLD + "☀ " + casterName + " пропонує розірвати контракт (" +
                termLabel(existing.term()) + "). Присядьте (Shift) протягом " + CONSENT_SECONDS + " с, щоб погодитись.");
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✎ Пропозицію розірвання надіслано: " + target.getName(), NamedTextColor.GOLD));

        context.ui().monitorSneaking(targetId, CONSENT_SECONDS * 20, accepted -> {
            if (accepted) {
                context.contracts().settle(existing.id());
                notifyBoth(context, casterId, targetId, "✎ Контракт розірвано за згодою сторін.");
            } else {
                notifyBoth(context, casterId, targetId, "✎ Розірвання контракту відхилено.");
            }
        });
    }

    // ---------------------------------------------------------------- Обмін (validate-at-commit)

    private void beginTrade(IAbilityContext context, ActiveAbility ability, Player caster, Player target) {
        promptHeldItem(context, caster, "предмет для обміну", offered -> {
            UUID casterId = caster.getUniqueId();
            UUID targetId = target.getUniqueId();
            pendingOffers.put(casterId, new TradeOffer(casterId, targetId, offered.clone()));

            String casterName = context.playerData().getName(casterId);
            target.sendMessage(ChatColor.GOLD + "☀ " + casterName + " пропонує обмін: " +
                    ChatColor.YELLOW + offered.getAmount() + "× " + itemLabel(offered) +
                    ChatColor.GOLD + ". Візьміть свій предмет у руку й присядьте (Shift) за " + CONSENT_SECONDS + " с.");
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("✎ Пропозицію обміну надіслано: " + target.getName(), NamedTextColor.GOLD));

            context.ui().monitorSneaking(targetId, CONSENT_SECONDS * 20, accepted -> {
                TradeOffer offer = pendingOffers.remove(casterId);
                if (offer == null) return;
                if (!accepted) {
                    notifyBoth(context, casterId, targetId, "✎ Обмін відхилено.");
                    return;
                }
                completeTrade(context, ability, casterId, targetId, offer.offered());
            });
        });
    }

    /** Єдина синхронна транзакція: перевірка → зняття з обох → видача обом. Без застави — атомарно. */
    private void completeTrade(IAbilityContext context, ActiveAbility ability,
                               UUID casterId, UUID targetId, ItemStack offered) {
        Player caster = Bukkit.getPlayer(casterId);
        Player target = Bukkit.getPlayer(targetId);
        if (caster == null || target == null) {
            abortTrade(context, casterId, targetId, "Один з учасників офлайн — обмін скасовано.");
            return;
        }
        ItemStack targetItem = target.getInventory().getItemInMainHand();
        if (targetItem == null || targetItem.getType().isAir()) {
            abortTrade(context, casterId, targetId, "Ціль не тримала предмет — обмін скасовано.");
            return;
        }
        if (!caster.getInventory().containsAtLeast(offered, offered.getAmount())) {
            abortTrade(context, casterId, targetId, "Запропонований предмет зник — обмін скасовано.");
            return;
        }
        if (!charge(context, ability)) {
            abortTrade(context, casterId, targetId, "Недостатньо духовності — обмін скасовано.");
            return;
        }

        ItemStack targetGives = targetItem.clone();
        caster.getInventory().removeItem(offered.clone());
        target.getInventory().setItemInMainHand(null);
        giveOrDrop(caster, targetGives);
        giveOrDrop(target, offered.clone());

        runSigningCeremony(context, casterId, targetId, () ->
                notifyBoth(context, casterId, targetId, "✎ Обмін засвідчено й завершено."));
    }

    private void abortTrade(IAbilityContext context, UUID casterId, UUID targetId, String reason) {
        notifyBoth(context, casterId, targetId, "✎ " + reason);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover); // не втрата — падає під ноги
        }
    }

    // ---------------------------------------------------------------- Засвідчення Вміння (союзник)

    private void certifyAlly(IAbilityContext context, ActiveAbility ability, Player caster, Player target) {
        UUID casterId = caster.getUniqueId();
        UUID allyId = target.getUniqueId();
        if (allyId.equals(casterId)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Засвідчити можна лише союзника, не себе.");
            return;
        }
        if (!charge(context, ability)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності для засвідчення.");
            return;
        }

        // Множник для здібностей союзника-Sun (ті консультують amplification); мілі-удар — нижче.
        context.amplification().amplifyDamage(allyId, CERTIFY_MULTIPLIER, CERTIFY_WINDOW_SECONDS);
        context.events().subscribeToTemporaryEvent(allyId,
                EntityDamageByEntityEvent.class,
                event -> event.getDamager() instanceof Player p && p.getUniqueId().equals(allyId),
                event -> {
                    event.setDamage(event.getDamage() * CERTIFY_MULTIPLIER + CERTIFY_HOLY_BONUS);
                    if (event.getEntity() instanceof LivingEntity victim) {
                        victim.setFireTicks(Math.max(victim.getFireTicks(), CERTIFY_FIRE_TICKS));
                    }
                },
                CERTIFY_WINDOW_SECONDS * 20);

        context.effects().playScriptureAura(target.getLocation(), SUN_GOLD, 30);
        context.effects().playSound(target.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.0f, 1.6f);
        context.effects().playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);

        context.messaging().sendMessage(allyId,
                ChatColor.GOLD + "☀ Ваш наступний удар засвідчено силою Сонця!");
        context.messaging().sendMessage(casterId,
                ChatColor.GOLD + "☀ Ви засвідчили вміння: " + ChatColor.YELLOW + target.getName());
    }

    // ---------------------------------------------------------------- Церемонія підпису

    /**
     * Ритуал засвідчення: коротко знерухомлює обидві сторони, повертає їх обличчям одна до
     * одної, малює золоті письмена й Сонячний стовп, дзвонить небесним дзвоном — і лише по
     * завершенню викликає {@code onComplete} (де контракт стає активним).
     */
    private void runSigningCeremony(IAbilityContext context, UUID aId, UUID bId, Runnable onComplete) {
        Player a = Bukkit.getPlayer(aId);
        Player b = Bukkit.getPlayer(bId);
        if (a == null || b == null) {
            onComplete.run();
            return;
        }
        faceEachOther(a, b);
        immobilize(context, aId);
        immobilize(context, bId);

        context.effects().playScriptureAura(a.getLocation(), SUN_GOLD, CEREMONY_TICKS + 5);
        context.effects().playScriptureAura(b.getLocation(), SUN_GOLD, CEREMONY_TICKS + 5);
        context.effects().playDescendingSunPillar(a.getLocation(), SUN_GOLD);
        context.effects().playDescendingSunPillar(b.getLocation(), SUN_GOLD);
        context.effects().playSound(a.getLocation(), Sound.BLOCK_BELL_USE, 1.2f, 0.9f);
        context.effects().playSound(b.getLocation(), Sound.BLOCK_BELL_USE, 1.2f, 0.9f);
        context.effects().playSound(a.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);

        context.scheduling().scheduleDelayed(() -> {
            Location mid = Bukkit.getPlayer(aId) != null ? Bukkit.getPlayer(aId).getLocation() : a.getLocation();
            context.effects().playSound(mid, Sound.BLOCK_BELL_RESONATE, 1.2f, 1.2f);
            context.effects().spawnParticle(org.bukkit.Particle.EXPLOSION, mid, 1); // НЕ FLASH: на 1.21.11 вимагає data org.bukkit.Color
            onComplete.run();
        }, CEREMONY_TICKS);
    }

    private void faceEachOther(Player a, Player b) {
        Location la = a.getLocation();
        Location lb = b.getLocation();
        la.setDirection(lb.toVector().subtract(la.toVector()));
        lb.setDirection(la.toVector().subtract(lb.toVector()));
        a.teleport(la);
        b.teleport(lb);
    }

    /** Коротке знерухомлення (~1 с): сильна Повільність + заборона стрибка. */
    private void immobilize(IAbilityContext context, UUID id) {
        context.entity().applyPotionEffect(id, PotionEffectType.SLOWNESS, CEREMONY_TICKS + 5, 6);
        context.entity().applyPotionEffect(id, PotionEffectType.JUMP_BOOST, CEREMONY_TICKS + 5, 128);
    }

    // ---------------------------------------------------------------- helpers

    private boolean charge(IAbilityContext context, ActiveAbility ability) {
        Beyonder caster = context.getCasterBeyonder();
        if (!AbilityResourceConsumer.consumeResources(ability, caster, context)) return false;
        context.events().publishAbilityUsedEvent(ability, caster);
        return true;
    }

    private void notifyBoth(IAbilityContext context, UUID a, UUID b, String text) {
        context.messaging().sendMessage(a, ChatColor.GOLD + text);
        context.messaging().sendMessage(b, ChatColor.GOLD + text);
    }

    private ItemStack tile(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(loreLines).stream().map(l -> ChatColor.GRAY + l).toList());
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

    private String prettyName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    /** Назва предмета для гравця: кастомна назва (плагінні предмети сидять на дисках, тож
     * матеріал показав би «music disc …»), інакше — назва матеріалу. */
    private String itemLabel(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName());
        }
        return prettyName(item.getType());
    }

    static String termLabel(ContractTerm term) {
        return switch (term) {
            case PEACE -> "Мир";
            case DEBT -> "Клятва";
        };
    }
}
