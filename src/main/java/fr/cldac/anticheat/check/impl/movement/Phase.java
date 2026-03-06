package fr.cldac.anticheat.check.impl.movement;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import fr.cldac.anticheat.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Phase - Detects no-clip/phase through solid blocks.
 * Checks if the player's path between two positions intersects with solid
 * blocks.
 */
public class Phase extends MovementCheck {

    public Phase(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.PHASE, playerData);
    }

    @Override
    public void onMove() {
        Player player = playerData.getPlayer();
        if (player == null)
            return;
        if (player.isFlying())
            return;
        if (VersionUtil.isInVehicle(player))
            return;

        Location from = playerData.getLastLocation();
        Location to = playerData.getCurrentLocation();
        if (from == null || to == null)
            return;
        if (!from.getWorld().equals(to.getWorld()))
            return;

        // Only check meaningful movements
        double distSq = from.distanceSquared(to);
        if (distSq < 0.25 || distSq > 100)
            return; // Too small or likely teleport

        // Sample points along the path and check for solid blocks
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        direction.normalize();

        int steps = Math.max(2, (int) Math.ceil(length / 0.5));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double x = from.getX() + direction.getX() * length * t;
            double y = from.getY() + direction.getY() * length * t + 0.5; // Check at body height
            double z = from.getZ() + direction.getZ() * length * t;

            // Skip if chunk not loaded to avoid async chunk loading
            int chunkX = (int) Math.floor(x) >> 4;
            int chunkZ = (int) Math.floor(z) >> 4;
            if (!from.getWorld().isChunkLoaded(chunkX, chunkZ))
                continue;

            Block block = from.getWorld().getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            if (block.getType().isSolid() && !VersionUtil.isSlab(block.getType())
                    && !VersionUtil.isStair(block.getType()) && !VersionUtil.isBed(block.getType())) {
                // Check that the block is actually in the way (not a door/gate/fence)
                String name = block.getType().name();
                if (name.contains("DOOR") || name.contains("GATE") || name.contains("FENCE")
                        || name.contains("SIGN") || name.contains("BANNER") || name.contains("PRESSURE")
                        || name.contains("BUTTON") || name.contains("TORCH") || name.contains("CARPET")) {
                    continue;
                }
                flag("phase through " + block.getType().name()
                        + " at " + block.getX() + "," + block.getY() + "," + block.getZ());
                return;
            }
        }
    }
}
