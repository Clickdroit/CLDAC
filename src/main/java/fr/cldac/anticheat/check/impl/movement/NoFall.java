package fr.cldac.anticheat.check.impl.movement;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import fr.cldac.anticheat.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * NoFall - Advanced fall damage avoidance detection.
 * Detects:
 * 1. Ground spoof: Client claims on ground when not physically touching ground
 * 2. Fall distance mismatch: Large fall without corresponding fall damage
 * 3. Mid-air ground toggle: Rapidly alternating onGround in mid-air
 * 4. Consistent small falls: Micro-jumps to reset fall distance
 */
public class NoFall extends MovementCheck {

    private double buffer = 0;
    private int groundToggleCount = 0;

    public NoFall(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.NOFALL, playerData);
    }

    @Override
    public void onMove() {
        Player player = playerData.getPlayer();
        if (player == null) return;
        if (player.isFlying() || player.getAllowFlight()) return;
        if (VersionUtil.isInVehicle(player)) return;

        Location loc = playerData.getCurrentLocation();
        if (loc == null) return;

        // Exempt conditions
        if (VersionUtil.isLiquid(loc.getBlock())) return;
        if (VersionUtil.isLiquid(loc.clone().subtract(0, 0.5, 0).getBlock())) return;
        if (VersionUtil.isClimbable(loc.getBlock().getType())) return;
        if (VersionUtil.isWeb(loc.getBlock().getType())) return;
        if (VersionUtil.isBouncy(loc.clone().subtract(0, 1, 0).getBlock().getType())) return;
        if (VersionUtil.isGliding(player)) return;

        // === Check 1: Ground spoof detection ===
        // Player says they're on ground, but there's no solid block below
        if (playerData.isOnGround() && !playerData.isLastOnGround()) {
            Location below = loc.clone().subtract(0, 0.1, 0);
            Location belowMore = loc.clone().subtract(0, 0.5, 0);

            boolean hasSolidBelow = VersionUtil.isSolid(below.getBlock())
                    || VersionUtil.isSolid(belowMore.getBlock());

            if (!hasSolidBelow && playerData.getFallDistance() > 2.0) {
                buffer += 2.0;
                if (buffer > 4.0) {
                    flag("GROUND_SPOOF fallDist=" + String.format("%.2f", playerData.getFallDistance())
                            + " noSolidBelow=true");
                    buffer = Math.max(0, buffer - 3.0);
                }
            }
        }

        // === Check 2: Mid-air ground toggle ===
        // NoFall hacks that rapidly alternate ground state while falling
        if (playerData.getAirTicks() > 3 && playerData.getDeltaY() < -0.1) {
            if (playerData.isOnGround() != playerData.isLastOnGround()) {
                groundToggleCount++;
                if (groundToggleCount >= 4) {
                    flag("GROUND_TOGGLE count=" + groundToggleCount
                            + " deltaY=" + String.format("%.3f", playerData.getDeltaY())
                            + " airTicks=" + playerData.getAirTicks());
                    groundToggleCount = 0;
                }
            }
        } else {
            groundToggleCount = Math.max(0, groundToggleCount - 1);
        }

        // === Check 3: Fall distance mismatch ===
        // Player falls > 3 blocks but claims ground without proper landing
        if (playerData.isOnGround() && playerData.getFallDistance() > 3.5) {
            // Set flag - BukkitListener will verify if actual damage event occurs
            playerData.setExpectingFallDamage(true);
        }

        // === Check 4: Consistent microjumps to avoid fall death ===
        // Pattern: ground -> tiny jump -> ground -> tiny jump (never accumulating enough fall distance)
        if (playerData.isOnGround() && playerData.isLastOnGround()
                && playerData.getLastDeltaY() > 0 && playerData.getLastDeltaY() < 0.01
                && playerData.getAirTicks() == 0) {
            playerData.incrementGroundSpoofTicks();
            if (playerData.getGroundSpoofTicks() > 6) {
                flag("MICRO_JUMP spoofTicks=" + playerData.getGroundSpoofTicks());
            }
        }

        // Decay
        buffer = Math.max(0, buffer - 0.2);
    }
}
