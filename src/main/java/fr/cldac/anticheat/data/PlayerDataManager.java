package fr.cldac.anticheat.data;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final Map<UUID, PlayerData> dataMap = new ConcurrentHashMap<>();

    public PlayerData getPlayerData(Player player) {
        return dataMap.get(player.getUniqueId());
    }

    public PlayerData getPlayerData(UUID uuid) {
        return dataMap.get(uuid);
    }

    public PlayerData createPlayerData(Player player) {
        PlayerData data = new PlayerData(player);
        dataMap.put(player.getUniqueId(), data);
        return data;
    }

    public void removePlayerData(UUID uuid) {
        dataMap.remove(uuid);
    }

    public Map<UUID, PlayerData> getAllData() {
        return dataMap;
    }
}
