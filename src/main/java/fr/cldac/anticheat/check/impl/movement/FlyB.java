package fr.cldac.anticheat.check.impl.movement;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import fr.cldac.anticheat.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * FlyB - Detects altitude gain without legitimate source.
 * Flags when player gains altitude consistently while not on ground / no
 * velocity.
 */
public class FlyB extends MovementCheck {

    private int consecutiveGainTicks = 0;

    public FlyB(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.FLY_B, playerData);
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

        // Exempt if in liquid or on climbable
        if (VersionUtil.isLiquid(loc.getBlock()))
            return;
        if (VersionUtil.isClimbable(loc.getBlock().getType()))
            return;

        // Exempt if pending velocity
        if (playerData.isPendingVelocity() && playerData.getVelocityTicks() < 15)
            return;

        // Exempt if gliding (1.9+)
        if (VersionUtil.isGliding(player))
            return;

        double deltaY = playerData.getDeltaY();

        if (deltaY > 0 && !playerData.isOnGround() && playerData.getAirTicks() > 5) {
            consecutiveGainTicks++;
            // After jump apex (>= ~8 ticks in air), gaining altitude is suspicious
            if (consecutiveGainTicks > 5) {
                flag("consecutiveGain=" + consecutiveGainTicks
                        + " deltaY=" + String.format("%.3f", deltaY)
                        + " airTicks=" + playerData.getAirTicks());
            }
        } else {
            consecutiveGainTicks = 0;
        }
    }
}
