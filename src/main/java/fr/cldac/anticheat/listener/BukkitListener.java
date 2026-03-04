package fr.cldac.anticheat.listener;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.check.impl.combat.Criticals;
import fr.cldac.anticheat.check.impl.player.XRay;
import fr.cldac.anticheat.data.PlayerData;
import fr.cldac.anticheat.util.VersionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

/**
 * Bukkit event listener for game events used by anti-cheat checks.
 */
public class BukkitListener implements Listener {

    private final CLDAC plugin;

    public BukkitListener(CLDAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().createPlayerData(player);
        plugin.getCheckManager().registerPlayer(data);
        plugin.getAlertManager().onStaffJoin(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getCheckManager().unregisterPlayer(player.getUniqueId());
        plugin.getPlayerDataManager().removePlayerData(player.getUniqueId());
        plugin.getViolationManager().clearViolations(player.getUniqueId());
        plugin.getAlertManager().onPlayerQuit(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (event.isCancelled()) return;

        Player attacker = (Player) event.getDamager();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(attacker);
        if (data == null) return;

        // Criticals check: if this is a critical hit
        if (event.getDamage(EntityDamageEvent.DamageModifier.BASE) > 0) {
            // In vanilla, critical hits do 1.5x damage when falling
            // We check the Criticals check if the damage appears to be a critical
            boolean isCritical = !attacker.isOnGround()
                    && attacker.getFallDistance() > 0
                    && !attacker.isInsideVehicle()
                    && !attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);

            if (isCritical) {
                Criticals criticals = plugin.getCheckManager().getCheck(
                        attacker.getUniqueId(), Criticals.class);
                if (criticals != null && criticals.isEnabled()) {
                    criticals.onCriticalHit(attacker, event.getEntity());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;

        // NoFall verification: player took fall damage, so NoFall is not active
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            data.setExpectingFallDamage(false);
            data.setFallDistance(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;

        Material type = event.getBlock().getType();

        // XRay check
        XRay xray = plugin.getCheckManager().getCheck(player.getUniqueId(), XRay.class);
        if (xray != null && xray.isEnabled()) {
            xray.onBlockBreak(type.name(), event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data != null) {
            data.setInventoryOpen(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data != null) {
            data.setInventoryOpen(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(event.getPlayer());
        if (data != null) {
            data.setUsingItem(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;

        // Detect item use start (food, bow, shield, etc.)
        if (event.getAction().name().contains("RIGHT")) {
            if (player.getItemInHand() != null) {
                Material type = player.getItemInHand().getType();
                String name = type.name();
                if (name.contains("BOW") || name.contains("FOOD") || name.contains("APPLE")
                        || name.contains("POTION") || name.contains("SHIELD")
                        || name.contains("BREAD") || name.contains("BEEF")
                        || name.contains("PORK") || name.contains("CHICKEN")
                        || name.contains("MUTTON") || name.contains("FISH")
                        || name.contains("CARROT") || name.contains("POTATO")
                        || name.contains("STEW") || name.contains("COOKIE")
                        || name.contains("MELON") || name.contains("CHORUS")) {
                    data.setUsingItem(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(event.getPlayer());
        if (data != null) {
            data.setLastTeleportTimestamp(System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(event.getPlayer());
        if (data != null) {
            data.setLastVelocity(
                    event.getVelocity().getX(),
                    event.getVelocity().getY(),
                    event.getVelocity().getZ()
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(event.getPlayer());
        if (data != null) {
            // Exempt creative/spectator players
            String newMode = event.getNewGameMode().name();
            data.setExempt(newMode.equals("CREATIVE") || newMode.equals("SPECTATOR"));
        }
    }
}
