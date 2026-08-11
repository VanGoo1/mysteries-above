package me.vangoo.infrastructure.compat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import io.github.retrooper.packetevents.injector.SpigotChannelInjector;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ServerChannel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.logging.Logger;

/**
 * Знімає серверний хендлер PacketEvents із каналів, які не є TCP-слухачами (UDP тощо).
 *
 * <p>PacketEvents вішає свій {@code ServerChannelHandler} на КОЖЕН канал зі списку
 * серверного з'єднання, а той у першому ж рядку {@code channelRead} робить
 * {@code (Channel) msg} — бо для {@link ServerChannel} читання віддає прийняте
 * дочірнє з'єднання. Мод/плагін, що прив'язав до того ж списку UDP-канал (тут — Sable),
 * отримує на читанні {@code DatagramPacket}, і каст падає з {@code ClassCastException}
 * ДО {@code fireChannelRead}: чужі датаграми не доходять взагалі, а на кожен пакет
 * летить стек-трейс. На UDP-каналі цей хендлер не робить нічого корисного ні нам, ні
 * PacketEvents, тож просто прибираємо його звідти.
 *
 * <p>Полагоджено у PacketEvents 2.13.0 (перевірено на байткоді: там {@code channelRead}
 * починається з {@code instanceof Channel} і виходить, якщо це не канал), але проєкт
 * навмисно тримає 2.8.0 — див. {@code .claude/rules/minecraft-version.md}. Цей клас
 * відтворює той самий гард ззовні, без підняття версії.
 *
 * <p>Прохід ідемпотентний і робиться двічі: одразу після {@code PacketEvents.init()}
 * (ловить канали, прив'язані до старту плагінів) і на {@link ServerLoadEvent} (ловить
 * прив'язані рештою старту). Обидві точки — реальні події життєвого циклу, не таймери.
 */
public final class PacketEventsUdpGuard implements Listener {

    private final Logger logger;

    public PacketEventsUdpGuard(Logger logger) {
        this.logger = logger;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        sweep();
    }

    /**
     * @return скільки каналів очищено цим проходом (0 — нема чого чистити)
     */
    public int sweep() {
        ChannelInjector injector = PacketEvents.getAPI().getInjector();
        if (!(injector instanceof SpigotChannelInjector spigot)) return 0;

        Channel[] injected;
        try {
            // Копія: сет — звичайний HashSet, і прив'язка нового каналу може долити в
            // нього з іншого потоку просто під час обходу.
            injected = spigot.injectedConnectionChannels.toArray(new Channel[0]);
        } catch (RuntimeException e) {
            logger.warning("PacketEvents UDP guard: could not read injected channels: " + e);
            return 0;
        }

        int cleaned = 0;
        for (Channel channel : injected) {
            if (channel == null || channel instanceof ServerChannel) continue;

            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(PacketEvents.CONNECTION_HANDLER_NAME) == null) continue;

            try {
                pipeline.remove(PacketEvents.CONNECTION_HANDLER_NAME);
            } catch (RuntimeException e) {
                logger.warning("PacketEvents UDP guard: failed to detach from "
                        + channel.getClass().getName() + ": " + e);
                continue;
            }
            // Прибираємо і з реєстру інжектора, інакше uninject() на вимкненні логуватиме
            // "handler not found" для каналу, який ми ж і звільнили.
            spigot.injectedConnectionChannels.remove(channel);
            cleaned++;
            logger.info("PacketEvents UDP guard: detached server channel handler from "
                    + channel.getClass().getSimpleName() + " (" + channel.localAddress()
                    + ") — it is not a TCP listener");
        }
        return cleaned;
    }
}
