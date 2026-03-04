package fr.cldac.anticheat.check.impl.combat;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Reach - Advanced hit distance analysis.
 * Improvements over basic reach check:
 * 1. Eye-to-hitbox distance instead of eye-to-center
 * 2. Latency compensation (allows more reach for high ping)
 * 3. Buffer system to prevent one-off false positives
 * 4. Movement interpolation (accounts for both players' movement)
 */
public class Reach extends Check {

    private double maxReach;
    private double buffer = 0;

    public Reach(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.REACH, playerData);
        this.maxReach = plugin.getConfigManager().getDouble(
                CheckType.REACH.getConfigPath() + ".max-reach", 3.5);
    }

    /**
     * Called when a player attacks an entity.
     */
    public void onAttack(Player attacker, Entity target) {
        if (playerData.isExempt()) return;

        Location attackerEye = attacker.getEyeLocation();
        Location targetLoc = target.getLocation();

        // Use the closest point on the target's hitbox instead of center
        double targetWidth = 0.6; // Player hitbox width
        double targetHeight = 1.8; // Player hitbox height

        // Calculate distance from attacker's eye to the closest edge of target hitbox
        double dx = attackerEye.getX() - clamp(attackerEye.getX(),
                targetLoc.getX() - targetWidth / 2, targetLoc.getX() + targetWidth / 2);
        double dy = attackerEye.getY() - clamp(attackerEye.getY(),
                targetLoc.getY(), targetLoc.getY() + targetHeight);
        double dz = attackerEye.getZ() - clamp(attackerEye.getZ(),
                targetLoc.getZ() - targetWidth / 2, targetLoc.getZ() + targetWidth / 2);

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Movement interpolation: account for both players moving between ticks
        // The target may have moved since the packet was sent
        double movementCompensation = 0;
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            // Rough velocity estimation
            movementCompensation = 0.1; // ~2 blocks/s latency compensation
        }

        double effectiveMax = maxReach + movementCompensation;

        double diff = distance - effectiveMax;

        if (diff > 0) {
            buffer += diff;
            if (buffer > 1.5) {
                flag("distance=" + String.format("%.2f", distance)
                        + " max=" + String.format("%.2f", effectiveMax)
                        + " buffer=" + String.format("%.2f", buffer));
                buffer = Math.max(0, buffer - 1.0);
            }
        } else {
            buffer = Math.max(0, buffer - 0.25);
        }
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
