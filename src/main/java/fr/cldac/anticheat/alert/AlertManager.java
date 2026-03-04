package fr.cldac.anticheat.alert;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages sending anti-cheat alerts to staff members.
 */
public class AlertManager {

    private final CLDAC plugin;
    private final Set<UUID> alertsEnabled = new HashSet<>();
    private final Map<String, Long> alertCooldowns = new HashMap<>();

    public AlertManager(CLDAC plugin) {
        this.plugin = plugin;
    }

    /**
     * Toggle alerts for a staff member.
     */
    public boolean toggleAlerts(Player player) {
        UUID uuid = player.getUniqueId();
        if (alertsEnabled.contains(uuid)) {
            alertsEnabled.remove(uuid);
            return false;
        } else {
            alertsEnabled.add(uuid);
            return true;
        }
    }

    /**
     * Check if player has alerts enabled.
     */
    public boolean hasAlertsEnabled(UUID uuid) {
        return alertsEnabled.contains(uuid);
    }

    /**
     * Send an alert to all staff members with alerts enabled.
     */
    public void sendAlert(Player suspect, CheckType checkType, int violations, String detail) {
        // Rate limit alerts per player+check
        String key = suspect.getUniqueId() + ":" + checkType.name();
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfigManager().getAlertCooldown();

        Long lastAlert = alertCooldowns.get(key);
        if (lastAlert != null && (now - lastAlert) < cooldown) {
            return;
        }
        alertCooldowns.put(key, now);

        String prefix = plugin.getConfigManager().getPrefix();
        String message = prefix + ChatColor.RED + suspect.getName()
                + ChatColor.GRAY + " flagged "
                + ChatColor.GOLD + checkType.getDisplayName()
                + ChatColor.GRAY + " [x" + violations + "] "
                + ChatColor.DARK_GRAY + detail;

        // Send to all online staff with alerts enabled
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("cldac.alerts") && alertsEnabled.contains(staff.getUniqueId())) {
                staff.sendMessage(message);
            }
        }

        // Also log to console
        plugin.getLogger().info("[ALERT] " + suspect.getName() + " flagged " + checkType.getDisplayName()
                + " [x" + violations + "] " + detail);
    }

    /**
     * Auto-enable alerts for staff on join.
     */
    public void onStaffJoin(Player player) {
        if (player.hasPermission("cldac.alerts")) {
            alertsEnabled.add(player.getUniqueId());
        }
    }

    public void onPlayerQuit(UUID uuid) {
        alertsEnabled.remove(uuid);
    }
}
