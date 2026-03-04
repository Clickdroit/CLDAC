package fr.cldac.anticheat.config;

import fr.cldac.anticheat.CLDAC;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages the plugin's configuration.
 */
public class ConfigManager {

    private final CLDAC plugin;
    private FileConfiguration config;

    public ConfigManager(CLDAC plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("general.prefix", "&8[&c&lCLDAC&8] &7"));
    }

    public double getMinTPS() {
        return config.getDouble("general.min-tps", 18.0);
    }

    public long getAlertCooldown() {
        return config.getLong("general.alert-cooldown", 3000);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public String getPunishCommand(String checkConfigPath) {
        return config.getString(checkConfigPath + ".punish-command", "");
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
