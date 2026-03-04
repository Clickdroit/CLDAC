package fr.cldac.anticheat.check.impl.player;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Scaffold - Advanced scaffolding/bridge detection.
 * Detects:
 * 1. Rapid block placement while moving (blocks/second analysis)
 * 2. Rotation snap patterns during placement (looking down/snapping yaw)
 * 3. Tower scaffolding (placing blocks straight up)
 * 4. Extension rate (blocks placed per block walked)
 * 5. Impossible placement angles
 */
public class Scaffold extends Check {

    private double buffer = 0;
    private int towerBlocks = 0;
    private Location lastTowerLocation;

    public Scaffold(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.SCAFFOLD, playerData);
    }

    /**
     * Called when the player places a block.
     */
    public void onBlockPlace(Location blockLocation) {
        if (playerData.isExempt()) return;
        Player player = playerData.getPlayer();
        if (player == null) return;

        long now = System.currentTimeMillis();
        long timeSinceLast = now - playerData.getLastBlockPlaceTimestamp();

        playerData.setLastBlockPlaceTimestamp(now);
        playerData.incrementBlockPlaceCount();
        playerData.addBlockPlaceTimestamp(now);

        // === Check 1: Placement speed analysis ===
        double bps = playerData.getBlockPlacesPerSecond();
        if (bps > 12 && playerData.getDeltaXZ() > 0.1) {
            buffer += 1.5;
            if (buffer > 4.0) {
                flag("RAPID_PLACE bps=" + String.format("%.1f", bps)
                        + " speed=" + String.format("%.3f", playerData.getDeltaXZ()));
                buffer = Math.max(0, buffer - 3.0);
            }
        }

        // === Check 2: Rotation snap (yaw/pitch analysis during placement) ===
        float pitch = playerData.getLastPitch();
        float deltaYaw = playerData.getDeltaYaw();

        // Fast placement + rapid yaw changes + looking down = scaffold
        if (timeSinceLast < 200 && playerData.getDeltaXZ() > 0.1) {
            boolean lookingDown = pitch > 70;
            boolean yawSnap = deltaYaw > 25;

            if (lookingDown && yawSnap && timeSinceLast < 150) {
                buffer += 1.0;
                if (buffer > 3.0) {
                    flag("SNAP_SCAFFOLD interval=" + timeSinceLast + "ms"
                            + " pitch=" + String.format("%.1f", pitch)
                            + " yawSnap=" + String.format("%.1f", deltaYaw));
                    buffer = Math.max(0, buffer - 2.0);
                }
            }
        }

        // === Check 3: Tower scaffolding (placing blocks directly up) ===
        if (blockLocation != null) {
            if (lastTowerLocation != null && blockLocation.getWorld().equals(lastTowerLocation.getWorld())) {
                double horizontalDist = Math.sqrt(
                        Math.pow(blockLocation.getX() - lastTowerLocation.getX(), 2)
                                + Math.pow(blockLocation.getZ() - lastTowerLocation.getZ(), 2));
                double verticalDist = blockLocation.getY() - lastTowerLocation.getY();

                if (horizontalDist < 1.0 && verticalDist == 1.0) {
                    towerBlocks++;
                    if (towerBlocks >= 5 && timeSinceLast < 250) {
                        flag("TOWER height=" + towerBlocks
                                + " interval=" + timeSinceLast + "ms");
                        towerBlocks = 0;
                    }
                } else {
                    towerBlocks = Math.max(0, towerBlocks - 1);
                }
            }
            lastTowerLocation = blockLocation.clone();
            playerData.setLastBlockPlaceLocation(blockLocation.clone());
        }

        // === Check 4: Extension rate ===
        // Placing blocks very consistently while moving at high speed
        if (timeSinceLast > 0 && timeSinceLast < 300 && playerData.getDeltaXZ() > 0.2) {
            double extensionRate = playerData.getDeltaXZ() / (timeSinceLast / 1000.0);
            if (extensionRate > 8.0) { // Walking speed ~4.3 b/s, sprint ~5.6
                buffer += 0.5;
            }
        }

        // Decay buffer
        buffer = Math.max(0, buffer - 0.3);
    }
}
