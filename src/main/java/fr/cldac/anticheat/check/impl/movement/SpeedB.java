package fr.cldac.anticheat.check.impl.movement;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import fr.cldac.anticheat.util.VersionUtil;
import org.bukkit.entity.Player;

import java.util.LinkedList;

/**
 * SpeedB - Timer/Speed detection through movement acceleration and pattern analysis.
 * Detects:
 * - Abnormal acceleration while airborne (vanilla has strict deceleration)
 * - Oscillating speed patterns typical of speed hacks
 * - Ground speed that exceeds vanilla sprint cap consistently
 */
public class SpeedB extends MovementCheck {

    private double buffer = 0;
    private int oscillationCount = 0;
    private double lastAcceleration = 0;

    public SpeedB(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.SPEED_B, playerData);
    }

    @Override
    public void onMove() {
        Player player = playerData.getPlayer();
        if (player == null) return;
        if (player.isFlying() || player.getAllowFlight()) return;
        if (VersionUtil.isInVehicle(player)) return;
        if (playerData.getDeltaXZ() < 0.15) {
            lastAcceleration = 0;
            return;
        }
        if (playerData.isPendingVelocity() && playerData.getVelocityTicks() < 15) return;

        double acceleration = playerData.getAcceleration();

        // === Check 1: Abnormal air acceleration ===
        // In vanilla, horizontal speed always decreases in air (air resistance = 0.02)
        if (!playerData.isOnGround() && playerData.getAirTicks() > 3) {
            if (acceleration > 0.05) {
                buffer += 1.0;
                if (buffer > 3.0) {
                    flag("airAccel=" + String.format("%.3f", acceleration)
                            + " airTicks=" + playerData.getAirTicks()
                            + " deltaXZ=" + String.format("%.3f", playerData.getDeltaXZ()));
                    buffer = Math.max(0, buffer - 2.0);
                }
            } else {
                buffer = Math.max(0, buffer - 0.25);
            }
        }

        // === Check 2: Speed oscillation detection ===
        // Many speed hacks oscillate between fast and slow to try to average out
        if (acceleration > 0 && lastAcceleration < 0) {
            oscillationCount++;
        } else if (acceleration < 0 && lastAcceleration > 0) {
            oscillationCount++;
        } else {
            oscillationCount = Math.max(0, oscillationCount - 1);
        }

        if (oscillationCount > 8 && playerData.getDeltaXZ() > 0.3) {
            flag("oscillation=" + oscillationCount
                    + " deltaXZ=" + String.format("%.3f", playerData.getDeltaXZ()));
            oscillationCount = 0;
        }

        // === Check 3: Consistent high ground speed ===
        // Check if player consistently exceeds sprint speed on ground
        if (playerData.isOnGround() && player.isSprinting() && playerData.getGroundTicks() > 5) {
            LinkedList<Double> history = playerData.getDeltaXZHistory();
            if (history.size() >= 10) {
                int highSpeedTicks = 0;
                for (Double d : history) {
                    if (d > 0.38) highSpeedTicks++; // Slightly above vanilla sprint max
                }
                if (highSpeedTicks >= 8) {
                    flag("consistentHighSpeed ticks=" + highSpeedTicks
                            + " deltaXZ=" + String.format("%.3f", playerData.getDeltaXZ()));
                }
            }
        }

        lastAcceleration = acceleration;
    }
}
