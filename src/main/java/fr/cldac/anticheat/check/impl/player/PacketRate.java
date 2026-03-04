package fr.cldac.anticheat.check.impl.player;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;

/**
 * PacketRate - Advanced timer/speed hack detection.
 * Detects:
 * 1. Excessive packet rate (raw packets/second exceeding threshold)
 * 2. Timer hack: movement packets sent faster than 50ms interval (20 TPS)
 *    - Analyzes average movement interval to detect timer multiplier
 * 3. Slow timer: movement packets sent slower than expected (anti-timer)
 */
public class PacketRate extends Check {

    private int maxPacketsPerSecond;
    private double buffer = 0;
    private double timerBuffer = 0;

    public PacketRate(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.PACKET_RATE, playerData);
        this.maxPacketsPerSecond = plugin.getConfigManager().getInt(
                CheckType.PACKET_RATE.getConfigPath() + ".max-packets-per-second", 500);
    }

    /**
     * Called on every packet received.
     */
    public void onPacket() {
        if (playerData.isExempt()) return;

        playerData.incrementPacketCount();
        int count = playerData.getPacketCount();

        // === Check 1: Raw packet rate ===
        if (count > maxPacketsPerSecond) {
            buffer += 1.0;
            if (buffer > 3.0) {
                flag("PACKET_FLOOD count=" + count + "/s max=" + maxPacketsPerSecond);
                buffer = Math.max(0, buffer - 2.0);
            }
        } else {
            buffer = Math.max(0, buffer - 0.3);
        }

        // === Check 2: Timer detection (movement interval analysis) ===
        double avgInterval = playerData.getAverageMovementInterval();

        // Normal = ~50ms (20 TPS). Timer 1.2x = ~42ms. Timer 2x = ~25ms.
        if (avgInterval > 0 && avgInterval < 40) {
            double timerMultiplier = 50.0 / avgInterval;
            timerBuffer += (timerMultiplier - 1.0);

            if (timerBuffer > 3.0) {
                flag("TIMER multiplier=" + String.format("%.2f", timerMultiplier)
                        + "x avgInterval=" + String.format("%.1f", avgInterval) + "ms");
                timerBuffer = Math.max(0, timerBuffer - 2.0);
            }
        } else {
            timerBuffer = Math.max(0, timerBuffer - 0.1);
        }

        // === Check 3: Slow timer (sending packets too slowly) ===
        // Some hacks slow down the game tick rate to gain reaction time
        if (avgInterval > 70 && count < 15) {
            // This could also be lag, so use very high threshold
            double slowMultiplier = avgInterval / 50.0;
            if (slowMultiplier > 1.5) {
                flag("SLOW_TIMER multiplier=" + String.format("%.2f", slowMultiplier)
                        + "x avgInterval=" + String.format("%.1f", avgInterval) + "ms");
            }
        }
    }
}
