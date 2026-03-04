package fr.cldac.anticheat;

import com.github.retrooper.packetevents.PacketEvents;
import fr.cldac.anticheat.alert.AlertManager;
import fr.cldac.anticheat.check.CheckManager;
import fr.cldac.anticheat.command.CLDACCommand;
import fr.cldac.anticheat.config.ConfigManager;
import fr.cldac.anticheat.data.PlayerDataManager;
import fr.cldac.anticheat.listener.BukkitListener;
import fr.cldac.anticheat.listener.PacketListener;
import fr.cldac.anticheat.violation.ViolationManager;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class CLDAC extends JavaPlugin {

    private static CLDAC instance;

    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private CheckManager checkManager;
    private ViolationManager violationManager;
    private AlertManager alertManager;

    @Override
    public void onLoad() {
        instance = this;
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(true)
                .checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        // Initialize config
        this.configManager = new ConfigManager(this);

        // Initialize managers
        this.playerDataManager = new PlayerDataManager();
        this.violationManager = new ViolationManager(this);
        this.alertManager = new AlertManager(this);
        this.checkManager = new CheckManager(this);

        // Register PacketEvents listener
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener(this));
        PacketEvents.getAPI().init();

        // Register Bukkit listeners
        Bukkit.getPluginManager().registerEvents(new BukkitListener(this), this);

        // Register commands
        getCommand("cldac").setExecutor(new CLDACCommand(this));

        // Schedule violation decay
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> violationManager.decayViolations(), 20L * 30, 20L * 30);

        getLogger().info("CLDAC Anti-Cheat enabled! Protecting your server.");
        getLogger().info("Server version: " + Bukkit.getVersion());
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        getLogger().info("CLDAC Anti-Cheat disabled.");
    }

    public static CLDAC getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public ViolationManager getViolationManager() {
        return violationManager;
    }

    public AlertManager getAlertManager() {
        return alertManager;
    }
}
