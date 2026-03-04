package fr.cldac.anticheat.check;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Abstract base class for all anti-cheat checks.
 * Each check implementation processes player data and calls flag() when suspicious activity is detected.
 */
public abstract class Check {

    protected final CLDAC plugin;
    protected final CheckType checkType;
    protected final PlayerData playerData;
    private boolean enabled;
    private int maxViolations;

    public Check(CLDAC plugin, CheckType checkType, PlayerData playerData) {
        this.plugin = plugin;
        this.checkType = checkType;
        this.playerData = playerData;
        loadConfig();
    }

    /**
     * Load settings from config for this check.
     */
    public void loadConfig() {
        String path = checkType.getConfigPath();
        this.enabled = plugin.getConfigManager().getBoolean(path + ".enabled", true);
        this.maxViolations = plugin.getConfigManager().getInt(path + ".max-violations", 10);
    }

    /**
     * Flag the player for a violation. Called by check implementations.
     * @param detail Additional detail message about the violation
     */
    protected void flag(String detail) {
        Player player = playerData.getPlayer();
        if (player == null || !player.isOnline()) return;
        if (playerData.isExempt()) return;
        if (player.hasPermission("cldac.bypass")) return;

        // Grace period: don't flag within 3 seconds of join
        if (System.currentTimeMillis() - playerData.getJoinTimestamp() < 3000) return;

        // Grace period: don't flag within 1 second of teleport
        if (System.currentTimeMillis() - playerData.getLastTeleportTimestamp() < 1000) return;

        plugin.getViolationManager().addViolation(player, checkType, detail);
    }

    /**
     * Flag with a formatted numeric value.
     */
    protected void flag(String detail, double value) {
        flag(detail + " (value=" + String.format("%.2f", value) + ")");
    }

    public CheckType getCheckType() { return checkType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxViolations() { return maxViolations; }
    public PlayerData getPlayerData() { return playerData; }
}
