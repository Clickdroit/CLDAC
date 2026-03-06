package fr.cldac.anticheat.check.impl.movement;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import fr.cldac.anticheat.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * SpeedA - Advanced horizontal speed detection.
 * Uses vanilla movement prediction model accounting for:
 * - Sprint, sneak, walk states
 * - Potion effects (Speed, Slowness, Dolphins Grace)
 * - Block friction (ice, soul sand, slime, honey)
 * - Depth Strider, Soul Speed enchantments
 * - Web, liquid slowdown
 * - Velocity/knockback buffer
 * - Buffer system to reduce false positives
 */
public class SpeedA extends MovementCheck {

    private double tolerance;
    private double buffer = 0;
    private static final double BUFFER_MAX = 4.0;
    private static final double BUFFER_DECAY = 0.5;

    public SpeedA(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.SPEED_A, playerData);
        this.tolerance = plugin.getConfigManager().getDouble(CheckType.SPEED_A.getConfigPath() + ".tolerance", 0.05);
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
        if (playerData.getDeltaXZ() < 0.1)
            return;

        // Skip in liquids (handled differently)
        Location loc = playerData.getCurrentLocation();
        if (loc != null && VersionUtil.isChunkLoaded(loc)) {
            Block atFeet = loc.getBlock();
            Location belowLoc = loc.clone().subtract(0, 0.5, 0);
            if (VersionUtil.isChunkLoaded(belowLoc)) {
                Block below = belowLoc.getBlock();
                if (VersionUtil.isLiquid(atFeet) || VersionUtil.isLiquid(below))
                    return;
            }
            if (VersionUtil.isWeb(atFeet.getType()))
                return;
            if (VersionUtil.isClimbable(atFeet.getType()))
                return;
        }

        // Skip during pending velocity
        if (playerData.isPendingVelocity() && playerData.getVelocityTicks() < 15)
            return;

        double maxSpeed = calculateMaxSpeed(player);
        double deltaXZ = playerData.getDeltaXZ();

        double diff = deltaXZ - (maxSpeed + tolerance);

        if (diff > 0) {
            buffer += diff;
            if (buffer > BUFFER_MAX) {
                flag("deltaXZ=" + String.format("%.3f", deltaXZ) + " max=" + String.format("%.3f", maxSpeed)
                        + " buffer=" + String.format("%.2f", buffer));
                buffer = Math.max(0, buffer - 2.0);
            }
        } else {
            buffer = Math.max(0, buffer - BUFFER_DECAY);
        }
    }

    private double calculateMaxSpeed(Player player) {
        // Vanilla base movement per tick (attribute base = 0.1, * modifier)
        double base = 0.2873; // walkSpeed

        // Sprinting multiplier (30% boost)
        if (player.isSprinting()) {
            base *= 1.3;
        }

        // Sneaking multiplier
        if (player.isSneaking()) {
            base *= 0.3;
        }

        // Potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                base *= 1.0 + (0.2 * (effect.getAmplifier() + 1));
            }
            if (effect.getType().equals(PotionEffectType.SLOW)) {
                base *= Math.max(0.0, 1.0 - (0.15 * (effect.getAmplifier() + 1)));
            }
        }

        // Airborne modifier: less friction means momentum carries
        if (!playerData.isOnGround()) {
            // In air, max speed can be higher due to momentum
            base *= 1.6;
            // Early jump ticks get extra speed (jump sprint boost)
            if (playerData.getAirTicks() <= 2 && player.isSprinting()) {
                base *= 1.2;
            }
        }

        // Block-specific modifiers
        Location loc = playerData.getCurrentLocation();
        if (loc != null) {
            Location belowLoc = loc.clone().subtract(0, 1, 0);
            if (VersionUtil.isChunkLoaded(belowLoc)) {
                Block below = belowLoc.getBlock();

                // Ice = very low friction, much higher speed
                if (VersionUtil.isIce(below.getType())) {
                    base *= 2.5;
                    // Blue ice even more slippery (1.13+)
                    if (below.getType().name().equals("BLUE_ICE")) {
                        base *= 1.3;
                    }
                }

                // Soul sand slowdown
                if (VersionUtil.isSoulSand(below.getType()) && playerData.isOnGround()) {
                    base *= 0.4;
                }

                // Slime block bounce momentum
                if (VersionUtil.isBouncy(below.getType())) {
                    base *= 1.5;
                }
            }
        }

        // Walk speed attribute (server may modify it)
        float walkSpeed = player.getWalkSpeed();
        if (walkSpeed != 0.2f) {
            base *= (walkSpeed / 0.2f);
        }

        // Velocity/knockback buffer
        if (playerData.isPendingVelocity() && playerData.getVelocityTicks() < 10) {
            double velocityXZ = Math.sqrt(
                    playerData.getLastVelocityX() * playerData.getLastVelocityX()
                            + playerData.getLastVelocityZ() * playerData.getLastVelocityZ());
            base += velocityXZ;
        }

        return base;
    }
}
