package fr.cldac.anticheat.command;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Main command handler for /cldac (aliases: /ac)
 */
public class CLDACCommand implements CommandExecutor {

    private final CLDAC plugin;

    public CLDACCommand(CLDAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cldac.admin")) {
            sender.sendMessage(prefix() + ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "alerts":
                handleAlerts(sender);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            case "toggle":
                handleToggle(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleAlerts(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix() + ChatColor.RED + "Only players can toggle alerts.");
            return;
        }
        Player player = (Player) sender;
        boolean enabled = plugin.getAlertManager().toggleAlerts(player);
        sender.sendMessage(prefix() + (enabled
                ? ChatColor.GREEN + "Alerts enabled."
                : ChatColor.RED + "Alerts disabled."));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.RED + "Usage: /cldac info <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        Map<CheckType, Integer> violations = plugin.getViolationManager().getAllViolations(target.getUniqueId());

        sender.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage(prefix() + ChatColor.WHITE + "Violations for " + ChatColor.GOLD + target.getName());
        sender.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (violations.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  No violations recorded.");
        } else {
            for (Map.Entry<CheckType, Integer> entry : violations.entrySet()) {
                ChatColor color;
                int count = entry.getValue();
                if (count >= 10) color = ChatColor.DARK_RED;
                else if (count >= 5) color = ChatColor.RED;
                else if (count >= 3) color = ChatColor.YELLOW;
                else color = ChatColor.GREEN;

                sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey().getDisplayName()
                        + ": " + color + "x" + count);
            }
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.RED + "Usage: /cldac toggle <check>");
            sender.sendMessage(prefix() + ChatColor.GRAY + "Available checks:");
            for (CheckType type : CheckType.values()) {
                sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + type.getDisplayName()
                        + ChatColor.GRAY + " (" + type.getCategory().name().toLowerCase() + ")");
            }
            return;
        }

        String checkName = args[1].toLowerCase();
        CheckType found = null;
        for (CheckType type : CheckType.values()) {
            if (type.getDisplayName().equalsIgnoreCase(checkName) || type.name().equalsIgnoreCase(checkName)) {
                found = type;
                break;
            }
        }

        if (found == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "Unknown check: " + args[1]);
            return;
        }

        plugin.getCheckManager().toggleCheck(found);
        sender.sendMessage(prefix() + ChatColor.GOLD + found.getDisplayName()
                + ChatColor.GREEN + " toggled.");
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        plugin.getCheckManager().reloadChecks();
        sender.sendMessage(prefix() + ChatColor.GREEN + "Configuration reloaded successfully.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage(prefix() + ChatColor.WHITE + "CLDAC Anti-Cheat v" + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage(ChatColor.GOLD + " /cldac alerts " + ChatColor.GRAY + "- Toggle anti-cheat alerts");
        sender.sendMessage(ChatColor.GOLD + " /cldac info <player> " + ChatColor.GRAY + "- View player violations");
        sender.sendMessage(ChatColor.GOLD + " /cldac toggle <check> " + ChatColor.GRAY + "- Enable/disable a check");
        sender.sendMessage(ChatColor.GOLD + " /cldac reload " + ChatColor.GRAY + "- Reload configuration");
        sender.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private String prefix() {
        return plugin.getConfigManager().getPrefix();
    }
}
