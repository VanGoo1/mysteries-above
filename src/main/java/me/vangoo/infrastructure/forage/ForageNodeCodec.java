package me.vangoo.infrastructure.forage;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Серіалізація живих нод фореджу в PDC чанка — страховка від крешу: якщо сервер упав,
 * записи лишаються в чанку, і при його завантаженні блоки відновлюються (healChunk).
 * Формат значення: ноди через '\n', поля через '|': x|y|z|lowerBlockData|upperBlockData
 * ('' якщо верхньої половини нема). BlockData-рядки не містять цих символів.
 */
public final class ForageNodeCodec {

    private final NamespacedKey nodesKey;

    public ForageNodeCodec(Plugin plugin) {
        this.nodesKey = new NamespacedKey(plugin, "forage_chunk_nodes");
    }

    /** Один запис ноди в PDC чанка. {@code upperData} — null для одноблокової флори. */
    public record StoredNode(int x, int y, int z, String lowerData, String upperData) {

        private String serialize() {
            return x + "|" + y + "|" + z + "|" + lowerData + "|" + (upperData == null ? "" : upperData);
        }

        private static StoredNode parse(String line) {
            String[] p = line.split("\\|", 5);
            if (p.length < 5) return null;
            try {
                return new StoredNode(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                        Integer.parseInt(p[2]), p[3], p[4].isEmpty() ? null : p[4]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public void add(Chunk chunk, StoredNode node) {
        List<StoredNode> all = read(chunk);
        all.removeIf(n -> n.x() == node.x() && n.y() == node.y() && n.z() == node.z());
        all.add(node);
        write(chunk, all);
    }

    public void remove(Chunk chunk, int x, int y, int z) {
        List<StoredNode> all = read(chunk);
        if (all.removeIf(n -> n.x() == x && n.y() == y && n.z() == z)) {
            write(chunk, all);
        }
    }

    public List<StoredNode> read(Chunk chunk) {
        String raw = chunk.getPersistentDataContainer().get(nodesKey, PersistentDataType.STRING);
        List<StoredNode> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String line : raw.split("\n")) {
            StoredNode n = StoredNode.parse(line);
            if (n != null) result.add(n);
        }
        return result;
    }

    public void clear(Chunk chunk) {
        chunk.getPersistentDataContainer().remove(nodesKey);
    }

    private void write(Chunk chunk, List<StoredNode> all) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (all.isEmpty()) {
            pdc.remove(nodesKey);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (StoredNode n : all) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(n.serialize());
        }
        pdc.set(nodesKey, PersistentDataType.STRING, sb.toString());
    }
}
