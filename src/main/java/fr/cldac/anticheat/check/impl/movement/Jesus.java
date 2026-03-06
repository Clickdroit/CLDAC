package fr.cldac.anticheat.check.impl.movement;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import fr.cldac.anticheat.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Jesus - Detects walking on water/lava.
 * Flags when player moves horizontally on top of liquid without being in a
 * boat.
 */
public class Jesus extends MovementCheck {

    private int waterTicks = 0;

    public Jesus(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.JESUS, playerData);
    }

    @Override
    public void onMove() {
        Player player = playerData.getPlayer();
        if (player == null)
            return;
        if (player.isFlying() || player.getAllowFlight())
            return;
        if (VersionUtil.isInVehicle(player))
            return;

        Location loc = playerData.getCurrentLocation();
        if (loc == null)
            return;
        if (!VersionUtil.isChunkLoaded(loc))
            return;

        Block below = loc.clone().subtract(0, 0.3, 0).getBlock();
        Block atFeet = loc.getBlock();

        // Check if player is on top of liquid (liquid below, not in liquid)
        if (VersionUtil.isLiquid(below) && !VersionUtil.isLiquid(atFeet)) {
            // Player appears to be standing on water/lava
            if (playerData.isOnGround() && playerData.getDeltaXZ() > 0.05) {
                waterTicks++;
                if (waterTicks > 5) {
                    flag("waterTicks=" + waterTicks + " onLiquid=true deltaXZ="
                            + String.format("%.3f", playerData.getDeltaXZ()));
                }
            }
        } else {
            waterTicks = Math.max(0, waterTicks - 2);
        }
    }
}
