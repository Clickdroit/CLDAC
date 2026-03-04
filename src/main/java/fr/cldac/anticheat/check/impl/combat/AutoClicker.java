package fr.cldac.anticheat.check.impl.combat;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;

import java.util.LinkedList;

/**
 * AutoClicker - Advanced click pattern analysis.
 * Detects clicker hacks through multiple analysis methods:
 *
 * 1. CPS threshold: maximum clicks per second
 * 2. Interval consistency: suspiciously even click timing (low standard deviation)
 * 3. Kurtosis analysis: statistical distribution of click intervals
 *    - Real clicks have a normal/slightly skewed distribution
 *    - Autoclickers produce abnormal distributions
 * 4. Double-click patterns: randomization hacks that alternate fast/slow
 */
public class AutoClicker extends Check {

    private int maxCPS;
    private double buffer = 0;

    public AutoClicker(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.AUTOCLICKER, playerData);
        this.maxCPS = plugin.getConfigManager().getInt(
                CheckType.AUTOCLICKER.getConfigPath() + ".max-cps", 18);
    }

    /**
     * Called on arm swing / attack events.
     */
    public void onClick() {
        if (playerData.isExempt()) return;

        playerData.handleClick();

        // === Check 1: CPS threshold ===
        double cps = playerData.getCPS();
        if (cps > maxCPS) {
            buffer += 1.5;
            if (buffer > 3.0) {
                flag("CPS=" + String.format("%.1f", cps) + " max=" + maxCPS);
                buffer = Math.max(0, buffer - 2.0);
            }
        } else {
            buffer = Math.max(0, buffer - 0.5);
        }

        // Only perform statistical analysis with enough data
        LinkedList<Long> clicks = playerData.getClickTimestamps();
        if (clicks.size() < 10) return;

        long[] intervals = getIntervals(clicks);
        if (intervals.length < 8) return;

        // === Check 2: Interval consistency (standard deviation) ===
        checkConsistency(intervals);

        // === Check 3: Kurtosis analysis ===
        checkKurtosis(intervals);

        // === Check 4: Double-click pattern ===
        checkDoubleClick(intervals);
    }

    /**
     * Low standard deviation = suspiciously even timing.
     * Humans have natural variation (usually stdDev > 20ms at moderate CPS).
     */
    private void checkConsistency(long[] intervals) {
        double mean = mean(intervals);
        double stdDev = stdDev(intervals, mean);

        // Very low stdDev with high CPS = bot-like
        if (stdDev < 10 && mean < 80) {
            flag("CONSISTENCY stdDev=" + String.format("%.1f", stdDev)
                    + " mean=" + String.format("%.0f", mean) + "ms");
        }
        // Moderate suspicion
        else if (stdDev < 15 && mean < 70 && intervals.length >= 12) {
            flag("LOW_VARIANCE stdDev=" + String.format("%.1f", stdDev)
                    + " mean=" + String.format("%.0f", mean) + "ms"
                    + " samples=" + intervals.length);
        }
    }

    /**
     * Kurtosis measures the "tailedness" of the distribution.
     * Human clicks: kurtosis ~2-4 (mesokurtic)
     * Autoclickers: kurtosis < 1.5 (platykurtic, too uniform) or > 8 (leptokurtic, too peaked)
     */
    private void checkKurtosis(long[] intervals) {
        double mean = mean(intervals);
        double stdDev = stdDev(intervals, mean);
        if (stdDev < 0.01) return; // Avoid division by zero

        double kurtosis = 0;
        for (long interval : intervals) {
            double diff = (interval - mean) / stdDev;
            kurtosis += diff * diff * diff * diff;
        }
        kurtosis = (kurtosis / intervals.length) - 3.0; // Excess kurtosis

        // Abnormally low kurtosis = too uniform = randomizer autoclicker
        if (kurtosis < -1.5 && intervals.length >= 12) {
            flag("KURTOSIS=" + String.format("%.2f", kurtosis)
                    + " (too uniform, likely randomized autoclicker)");
        }
        // Abnormally high kurtosis = too peaked = basic autoclicker
        if (kurtosis > 8.0 && intervals.length >= 12) {
            flag("KURTOSIS=" + String.format("%.2f", kurtosis)
                    + " (too peaked, likely basic autoclicker)");
        }
    }

    /**
     * Double-click detection: some randomized clickers alternate between
     * two interval values (e.g., 40ms, 60ms, 40ms, 60ms).
     */
    private void checkDoubleClick(long[] intervals) {
        if (intervals.length < 10) return;

        int alternateCount = 0;
        for (int i = 2; i < intervals.length; i++) {
            // Check if interval[i] ≈ interval[i-2] but ≠ interval[i-1]
            long diff02 = Math.abs(intervals[i] - intervals[i - 2]);
            long diff01 = Math.abs(intervals[i] - intervals[i - 1]);
            if (diff02 < 5 && diff01 > 15) {
                alternateCount++;
            }
        }

        if (alternateCount >= 5) {
            flag("DOUBLE_CLICK_PATTERN alternations=" + alternateCount);
        }
    }

    // === Statistical helpers ===

    private long[] getIntervals(LinkedList<Long> clicks) {
        Long[] arr = clicks.toArray(new Long[0]);
        long[] intervals = new long[arr.length - 1];
        for (int i = 1; i < arr.length; i++) {
            intervals[i - 1] = arr[i] - arr[i - 1];
        }
        return intervals;
    }

    private double mean(long[] values) {
        double sum = 0;
        for (long v : values) sum += v;
        return sum / values.length;
    }

    private double stdDev(long[] values, double mean) {
        double variance = 0;
        for (long v : values) variance += (v - mean) * (v - mean);
        return Math.sqrt(variance / values.length);
    }
}
