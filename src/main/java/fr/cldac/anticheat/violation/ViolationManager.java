package fr.cldac.anticheat.violation;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks violations per player per check type, handles punishment execution.
 */
public class ViolationManager {

    private final CLDAC plugin;
    // UUID -> (CheckType -> violation count)
    private final Map<UUID, Map<CheckType, Integer>> violations = new ConcurrentHashMap<>();

    public ViolationManager(CLDAC plugin) {
        this.plugin = plugin;
    }

    /**
     * Add a violation for a player/check and send alerts.
     */
    public void addViolation(Player player, CheckType checkType, String detail) {
        UUID uuid = player.getUniqueId();
        Map<CheckType, Integer> playerViolations = violations.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        int current = playerViolations.getOrDefault(checkType, 0) + 1;
        playerViolations.put(checkType, current);

        // Send alert to staff
        plugin.getAlertManager().sendAlert(player, checkType, current, detail);

        // Check if punishment threshold reached
        Check check = plugin.getCheckManager().getCheck(uuid, checkType);
        if (check != null && current >= check.getMaxViolations()) {
            executePunishment(player, checkType);
            // Reset violations after punishment
            playerViolations.put(checkType, 0);
        }
    }

    /**
     * Execute configured punishment command.
     */
    private void executePunishment(Player player, CheckType checkType) {
        String command = plugin.getConfigManager().getPunishCommand(checkType.getConfigPath());
        if (command != null && !command.isEmpty()) {
            String finalCommand = command.replace("%player%", player.getName());
            // Execute on main thread
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
        }
    }

    /**
     * Decay all violations by 1 (called periodically).
     */
    public void decayViolations() {
        for (Map.Entry<UUID, Map<CheckType, Integer>> entry : violations.entrySet()) {
            Map<CheckType, Integer> playerViol = entry.getValue();
            Iterator<Map.Entry<CheckType, Integer>> it = playerViol.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<CheckType, Integer> e = it.next();
                int newVal = e.getValue() - 1;
                if (newVal <= 0) {
                    it.remove();
                } else {
                    e.setValue(newVal);
                }
            }
            if (playerViol.isEmpty()) {
                violations.remove(entry.getKey());
            }
        }
    }

    /**
     * Get current violation count for a player and check type.
     */
    public int getViolations(UUID uuid, CheckType checkType) {
        Map<CheckType, Integer> playerViol = violations.get(uuid);
        if (playerViol == null) return 0;
        return playerViol.getOrDefault(checkType, 0);
    }

    /**
     * Get all violations for a player.
     */
    public Map<CheckType, Integer> getAllViolations(UUID uuid) {
        return violations.getOrDefault(uuid, Collections.emptyMap());
    }

    /**
     * Clear all violations for a player.
     */
    public void clearViolations(UUID uuid) {
        violations.remove(uuid);
    }
}
