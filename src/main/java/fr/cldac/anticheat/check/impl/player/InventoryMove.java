package fr.cldac.anticheat.check.impl.player;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.check.impl.movement.MovementCheck;
import fr.cldac.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * InventoryMove - Detects significant movement while the inventory is open.
 * Legitimate players cannot move while interacting with inventory on vanilla.
 */
public class InventoryMove extends MovementCheck {

    public InventoryMove(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.INVENTORY_MOVE, playerData);
    }

    @Override
    public void onMove() {
        Player player = playerData.getPlayer();
        if (player == null) return;
        if (!playerData.isInventoryOpen()) return;
        if (player.isFlying()) return;

        double deltaXZ = playerData.getDeltaXZ();

        // Allow small movements (head rotation causes slight position adjustments)
        if (deltaXZ > 0.1) {
            long timeSinceOpen = System.currentTimeMillis() - playerData.getLastInventoryOpenTimestamp();
            // Only check if inventory has been open for a bit (avoid race conditions)
            if (timeSinceOpen > 200) {
                flag("deltaXZ=" + String.format("%.3f", deltaXZ)
                        + " inventoryOpen=true for " + timeSinceOpen + "ms");
            }
        }
    }
}
