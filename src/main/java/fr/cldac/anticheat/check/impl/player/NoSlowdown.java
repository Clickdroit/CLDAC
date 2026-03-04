package fr.cldac.anticheat.check.impl.player;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.check.impl.movement.MovementCheck;
import fr.cldac.anticheat.data.PlayerData;
import fr.cldac.anticheat.util.VersionUtil;
import org.bukkit.entity.Player;

/**
 * NoSlowdown - Detects moving at full speed while using items (eating, blocking, drawing bow).
 * Using items should reduce speed by 80%.
 */
public class NoSlowdown extends MovementCheck {

    public NoSlowdown(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.NOSLOWDOWN, playerData);
    }

    @Override
    public void onMove() {
        Player player = playerData.getPlayer();
        if (player == null) return;
        if (player.isFlying() || player.getAllowFlight()) return;
        if (VersionUtil.isInVehicle(player)) return;
        if (!playerData.isUsingItem()) return;

        // When using an item, speed should be reduced to ~20% of normal
        double deltaXZ = playerData.getDeltaXZ();

        // Normal sprint max ~0.28, with item use should be ~0.056
        // We allow generous threshold to avoid false positives
        double threshold = 0.16;

        if (player.isSprinting()) {
            threshold = 0.18;
        }

        if (deltaXZ > threshold && playerData.isOnGround()) {
            long usingTime = System.currentTimeMillis() - playerData.getItemUseStartTimestamp();
            // Only flag after using item for at least 300ms (initial movement buffer)
            if (usingTime > 300) {
                flag("deltaXZ=" + String.format("%.3f", deltaXZ)
                        + " usingItem=true for " + usingTime + "ms");
            }
        }
    }
}
