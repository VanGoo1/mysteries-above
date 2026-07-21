package me.vangoo.infrastructure.disguise;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Не дає scoreboard-командам Citizens ховати нік ЖИВОГО гравця.
 *
 * <p><b>Проблема.</b> Citizens тримає одну команду на NPC ({@code CIT-<uuid>}) і НАКОПИЧУЄ в ній
 * усі імена, які цей NPC колись мав, ніколи не прибираючи старі. Маріонетист дає тілу-NPC нік
 * кастера, тож у команді назавжди осідає ім'я живого гравця. На виході з маріонетки-моба
 * NPC знову ховає свій нік — команда перемикається на {@code NEVER} — і разом із NPC ховає
 * табличку гравця, чиє ім'я лишилось у списку. Звідси симптом «вийшов у своє тіло, нік блимнув
 * і зник», із плаваючою затримкою (Citizens шле команди своїм тіком).</p>
 *
 * <p><b>Чому саме перехоплення.</b> Ці команди існують ЛИШЕ в пакетах — Citizens шле їх повз
 * Bukkit-скорборд, тому {@code Scoreboard#getEntryTeam} їх не бачить і полагодити стан через
 * Bukkit API неможливо. Одноразове видалення входу теж не тримається: Citizens періодично
 * пересилає {@code mode=CREATE} з повним накопиченим списком і затирає будь-яку правку.
 * Єдина надійна точка — вихідний пакет.</p>
 *
 * <p><b>Межа втручання.</b> Чіпаємо виключно команди з префіксом {@code CIT-} (Citizens) і лише
 * ті, що ховають нік. Команда зборів {@code ma_gathering} теж ховає ніки живих гравців, але
 * робить це НАВМИСНО (анонімність) — під фільтр вона не потрапляє.</p>
 */
public final class CitizensNameplateGuard extends PacketListenerAbstract implements Listener {

    private static final String CITIZENS_TEAM_PREFIX = "CIT-";

    /** Ніки онлайн-гравців. Власний кеш, бо пакети йдуть у netty-потоці, а Bukkit-API не потокобезпечний. */
    private final Set<String> onlineNames = ConcurrentHashMap.newKeySet();

    public CitizensNameplateGuard() {
        super(PacketListenerPriority.HIGH);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        onlineNames.add(event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onlineNames.remove(event.getPlayer().getName());
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.TEAMS) {
            return;
        }
        WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);

        String teamName = packet.getTeamName();
        if (teamName == null || !teamName.startsWith(CITIZENS_TEAM_PREFIX)) {
            return; // не Citizens — не наша справа (зокрема ma_gathering: там анонімність навмисна)
        }

        Collection<String> entries = packet.getPlayers();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<String> cleaned = new ArrayList<>(entries.size());
        for (String entry : entries) {
            if (!onlineNames.contains(entry)) {
                cleaned.add(entry); // UUID NPC та колишні назви лишаються — вони й мають ховатись
            }
        }
        if (cleaned.size() == entries.size()) {
            return; // живих гравців у списку немає
        }
        if (cleaned.isEmpty() && packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.ADD_ENTITIES) {
            event.setCancelled(true); // додавати більше нічого — пакет зайвий
            return;
        }
        packet.setPlayers(cleaned);
        event.markForReEncode(true);
    }
}
