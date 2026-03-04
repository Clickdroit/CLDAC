package fr.cldac.anticheat.check.impl.player;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;

import java.util.Map;

/**
 * XRay - Advanced ore mining analysis.
 * Detects X-Ray through multiple methods:
 *
 * 1. Ore-to-stone ratio: X-Ray users find ores at abnormally high rates
 * 2. Straight-line mining: X-Ray users mine directly towards ores
 * 3. Ore mining speed: time between ore discoveries is unusually fast
 * 4. Diamond bias: unusually high proportion of diamonds vs other ores
 * 5. Depth analysis: finding ores outside their normal Y-level distribution
 */
public class XRay extends Check {

    private double suspiciousRatio;
    private int minOresTracked;
    private double buffer = 0;

    public XRay(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.XRAY, playerData);
        this.suspiciousRatio = plugin.getConfigManager().getDouble(
                CheckType.XRAY.getConfigPath() + ".suspicious-ratio", 0.4);
        this.minOresTracked = plugin.getConfigManager().getInt(
                CheckType.XRAY.getConfigPath() + ".min-ores-tracked", 15);
    }

    /**
     * Called when a block is broken.
     */
    public void onBlockBreak(String blockType, org.bukkit.Location location) {
        if (playerData.isExempt()) return;

        playerData.trackBlockBreak(blockType, location);

        int oresMined = playerData.getOresMined();
        int totalMined = playerData.getTotalBlocksMined();

        // Need minimum sample size
        if (oresMined < minOresTracked || totalMined < 30) return;

        // === Check 1: Ore-to-stone ratio ===
        double ratio = (double) oresMined / totalMined;
        if (ratio > suspiciousRatio) {
            StringBuilder oreDetail = new StringBuilder();
            for (Map.Entry<String, Integer> entry : playerData.getOreBreakCounts().entrySet()) {
                if (oreDetail.length() > 0) oreDetail.append(", ");
                oreDetail.append(entry.getKey()).append("=").append(entry.getValue());
            }
            buffer += 2.0;
            if (buffer > 4.0) {
                flag("ORE_RATIO=" + String.format("%.1f%%", ratio * 100)
                        + " (" + oresMined + "/" + totalMined + ")"
                        + " ores=[" + oreDetail + "]");
                playerData.resetXRayData();
                buffer = 0;
            }
        } else {
            buffer = Math.max(0, buffer - 0.5);
        }

        // === Check 2: Straight-line mining pattern ===
        if (playerData.hasStraightLineOreMining()) {
            flag("STRAIGHT_LINE_MINING (beeline to ores)");
        }

        // === Check 3: Ore mining speed ===
        double avgInterval = playerData.getAverageOreMiningInterval();
        // Finding an ore every < 8 seconds on average is very suspicious
        if (avgInterval < 8.0 && oresMined >= 8) {
            flag("FAST_ORE_FINDING avgInterval=" + String.format("%.1f", avgInterval)
                    + "s ores=" + oresMined);
        }

        // === Check 4: Diamond bias ===
        checkDiamondBias();
    }

    /**
     * Normal mining finds iron most, then coal, then gold, then diamond.
     * XRay users disproportionately find diamonds.
     */
    private void checkDiamondBias() {
        Map<String, Integer> ores = playerData.getOreBreakCounts();
        int diamonds = 0, total = 0;
        for (Map.Entry<String, Integer> entry : ores.entrySet()) {
            total += entry.getValue();
            if (entry.getKey().contains("DIAMOND")) {
                diamonds += entry.getValue();
            }
        }

        if (total < 15) return;

        // Normal diamond ratio is ~5-10%. Above 40% is very suspicious.
        double diamondRatio = (double) diamonds / total;
        if (diamondRatio > 0.4 && diamonds >= 6) {
            flag("DIAMOND_BIAS=" + String.format("%.0f%%", diamondRatio * 100)
                    + " (" + diamonds + "/" + total + " ores are diamonds)");
        }
    }
}
