package fr.cldac.anticheat.check.impl.player;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;

/**
 * BadPackets - Detects invalid packet data (impossible values).
 * Checks for invalid pitch, NaN positions, or invalid ground state.
 */
public class BadPackets extends Check {

    public BadPackets(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.BAD_PACKETS, playerData);
    }

    /**
     * Called on movement packets to validate data.
     */
    public void onPacketReceive(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        if (playerData.isExempt()) return;

        // Check for NaN/Infinity positions
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            flag("NaN/Infinite position x=" + x + " y=" + y + " z=" + z);
            return;
        }

        // Check for NaN/Infinity rotation
        if (Float.isNaN(yaw) || Float.isNaN(pitch)
                || Float.isInfinite(yaw) || Float.isInfinite(pitch)) {
            flag("NaN/Infinite rotation yaw=" + yaw + " pitch=" + pitch);
            return;
        }

        // Invalid pitch (vanilla clamps to -90 to 90)
        if (pitch > 90.1f || pitch < -90.1f) {
            flag("invalidPitch=" + String.format("%.2f", pitch));
            return;
        }

        // Extremely large position values
        if (Math.abs(x) > 3.0E7 || Math.abs(z) > 3.0E7 || Math.abs(y) > 1000) {
            flag("extremePosition x=" + String.format("%.0f", x)
                    + " y=" + String.format("%.0f", y)
                    + " z=" + String.format("%.0f", z));
        }
    }
}
