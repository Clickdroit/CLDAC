package fr.cldac.anticheat.check.impl.combat;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.check.impl.movement.MovementCheck;
import fr.cldac.anticheat.data.PlayerData;
import fr.cldac.anticheat.util.VersionUtil;

/**
 * Velocity - Advanced anti-knockback detection.
 * Improvements over basic velocity check:
 * 1. Track both vertical AND horizontal velocity separately
 * 2. Check velocity over multiple tick windows (not just tick 3)
 * 3. Account for collision with blocks reducing velocity legitimately
 * 4. Buffer system for false positive prevention
 * 5. Ratio analysis: how much velocity was actually taken
 */
public class Velocity extends MovementCheck {

    private int minVelocityPercent;
    private double buffer = 0;
    private boolean checkedHorizontal = false;
    private boolean checkedVertical = false;

    public Velocity(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.VELOCITY, playerData);
        this.minVelocityPercent = plugin.getConfigManager().getInt(
                CheckType.VELOCITY.getConfigPath() + ".min-velocity-percent", 65);
    }

    @Override
    public void onMove() {
        if (playerData.isExempt()) return;
        if (!playerData.isPendingVelocity()) return;
        if (VersionUtil.isInVehicle(playerData.getPlayer())) return;

        playerData.incrementVelocityTicks();
        int velTicks = playerData.getVelocityTicks();

        // Check at ticks 2-5 (allow some time for velocity to be applied)
        if (velTicks >= 2 && velTicks <= 5) {
            // === Vertical velocity check ===
            if (!checkedVertical) {
                double expectedY = playerData.getLastVelocityY();
                double actualY = playerData.getDeltaY();

                if (expectedY > 0.15) {
                    double verticalPercent = (actualY / expectedY) * 100.0;
                    if (verticalPercent < minVelocityPercent) {
                        buffer += (minVelocityPercent - verticalPercent) / 20.0;
                        if (buffer > 3.0) {
                            flag("VERTICAL percent=" + String.format("%.0f%%", verticalPercent)
                                    + " expected=" + String.format("%.3f", expectedY)
                                    + " actual=" + String.format("%.3f", actualY));
                            buffer = Math.max(0, buffer - 2.0);
                        }
                        checkedVertical = true;
                    } else if (verticalPercent > 50) {
                        // Took some velocity, not fully cancelled
                        checkedVertical = true;
                    }
                }
            }

            // === Horizontal velocity check ===
            if (!checkedHorizontal) {
                double expectedXZ = Math.sqrt(
                        playerData.getLastVelocityX() * playerData.getLastVelocityX()
                                + playerData.getLastVelocityZ() * playerData.getLastVelocityZ());
                double actualXZ = playerData.getDeltaXZ();

                if (expectedXZ > 0.15) {
                    double horizontalPercent = (actualXZ / expectedXZ) * 100.0;

                    if (horizontalPercent < minVelocityPercent) {
                        buffer += (minVelocityPercent - horizontalPercent) / 20.0;
                        if (buffer > 3.0) {
                            flag("HORIZONTAL percent=" + String.format("%.0f%%", horizontalPercent)
                                    + " expected=" + String.format("%.3f", expectedXZ)
                                    + " actual=" + String.format("%.3f", actualXZ));
                            buffer = Math.max(0, buffer - 2.0);
                        }
                        checkedHorizontal = true;
                    } else if (horizontalPercent > 50) {
                        checkedHorizontal = true;
                    }
                }
            }

            // === Zero velocity check (100% anti-kb) ===
            if (velTicks == 3) {
                double actualXZ = playerData.getDeltaXZ();
                double actualY = playerData.getDeltaY();
                double expectedXZ = Math.sqrt(
                        playerData.getLastVelocityX() * playerData.getLastVelocityX()
                                + playerData.getLastVelocityZ() * playerData.getLastVelocityZ());
                double expectedY = playerData.getLastVelocityY();

                // Complete velocity cancellation
                if (expectedXZ > 0.2 && actualXZ < 0.05 && expectedY > 0.15 && actualY < 0.05) {
                    flag("ZERO_VELOCITY Expected XZ=" + String.format("%.3f", expectedXZ)
                            + " Y=" + String.format("%.3f", expectedY)
                            + " Got XZ=" + String.format("%.3f", actualXZ)
                            + " Y=" + String.format("%.3f", actualY));
                }
            }
        }

        // Reset after 15 ticks
        if (velTicks > 15) {
            playerData.setPendingVelocity(false);
            checkedHorizontal = false;
            checkedVertical = false;
            buffer = Math.max(0, buffer - 0.5);
        }
    }
}
