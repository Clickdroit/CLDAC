package fr.cldac.anticheat.check.impl.player;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NameTags - Advanced ESP/Tracers/NameTags detection.
 * Detects players tracking others through walls using multiple methods:
 *
 * 1. Direct look-at tracking: Looking precisely at hidden players
 * 2. Head-tracking consistency: Consistently following a hidden player's movement
 * 3. Pre-aim: Rotating toward a player before they appear in line of sight
 * 4. Tracking multiple hidden targets (multi-ESP)
 */
public class NameTags extends Check {

    private double maxTrackingDistance;
    private double buffer = 0;

    // Per-target tracking history: targetUUID -> [trackCount, lastTrackTime]
    private final Map<UUID, long[]> trackHistory = new HashMap<>();
    private long lastCleanup = 0;

    public NameTags(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.NAMETAGS, playerData);
        this.maxTrackingDistance = plugin.getConfigManager().getDouble(
                CheckType.NAMETAGS.getConfigPath() + ".max-tracking-distance", 30.0);
    }

    /**
     * Called periodically to check for tracking behavior.
     */
    public void onRotation() {
        if (playerData.isExempt()) return;
        Player player = playerData.getPlayer();
        if (player == null || !player.isOnline()) return;

        long now = System.currentTimeMillis();

        // Cleanup old tracking history every 30s
        if (now - lastCleanup > 30000) {
            trackHistory.entrySet().removeIf(e -> now - e.getValue()[1] > 30000);
            lastCleanup = now;
        }

        // Only check when player is actually rotating
        if (playerData.getDeltaYaw() < 1.0f && playerData.getDeltaPitch() < 1.0f) return;

        Location eyeLocation = player.getEyeLocation();
        int hiddenTracked = 0;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) continue;
            if (!target.getWorld().equals(player.getWorld())) continue;

            Location targetLoc = target.getLocation().add(0, 1, 0);
            double distance = eyeLocation.distance(targetLoc);

            if (distance < 8 || distance > maxTrackingDistance) continue;

            // Skip if player can see the target
            if (player.hasLineOfSight(target)) continue;

            // Calculate angle between look direction and direction to target
            org.bukkit.util.Vector toTarget = targetLoc.toVector().subtract(eyeLocation.toVector()).normalize();
            org.bukkit.util.Vector lookDir = eyeLocation.getDirection().normalize();

            double dot = lookDir.dot(toTarget);
            double angle = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));

            // === Check 1: Direct tracking (< 5 degrees) ===
            if (angle < 5.0) {
                hiddenTracked++;
                UUID targetId = target.getUniqueId();
                long[] history = trackHistory.computeIfAbsent(targetId, k -> new long[]{0, 0});
                history[0]++;
                history[1] = now;

                // === Check 2: Persistent tracking of same target ===
                if (history[0] >= 5) {
                    buffer += 2.0;
                    if (buffer > 5.0) {
                        flag("TRACKING " + target.getName()
                                + " dist=" + String.format("%.0f", distance)
                                + " angle=" + String.format("%.1f", angle) + "°"
                                + " trackCount=" + history[0]
                                + " noLOS=true");
                        buffer = Math.max(0, buffer - 4.0);
                        history[0] = 0;
                    }
                }
            }

            // === Check 3: Pre-aim detection ===
            // Player is rotating TOWARD a hidden player (delta between frames approaches 0)
            if (angle < 15.0 && angle > 3.0) {
                // Check if the player's rotation is converging on the target
                // (getting closer to looking at them each tick)
                float rotationTowardTarget = getRotationTowardTarget(eyeLocation, targetLoc);
                if (Math.abs(playerData.getRawDeltaYaw()) > 2.0
                        && Math.abs(rotationTowardTarget - playerData.getRawDeltaYaw()) < 5.0) {
                    buffer += 0.5;
                }
            }
        }

        // === Check 4: Multi-ESP (tracking multiple hidden targets) ===
        if (hiddenTracked >= 2) {
            buffer += 1.5;
            if (buffer > 4.0) {
                flag("MULTI_ESP tracking " + hiddenTracked + " hidden players simultaneously");
                buffer = Math.max(0, buffer - 3.0);
            }
        }

        // Decay buffer
        buffer = Math.max(0, buffer - 0.15);
    }

    /**
     * Calculate the yaw change needed to look at a target.
     */
    private float getRotationTowardTarget(Location from, Location target) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        float targetYaw = (float) -Math.toDegrees(Math.atan2(dx, dz));
        float currentYaw = from.getYaw();
        float delta = targetYaw - currentYaw;
        if (delta > 180) delta -= 360;
        if (delta < -180) delta += 360;
        return delta;
    }
}
