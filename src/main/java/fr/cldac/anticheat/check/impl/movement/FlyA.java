package fr.cldac.anticheat.check.impl.movement;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import fr.cldac.anticheat.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * FlyA - Detects being airborne for too long without legitimate reason.
 * Tracks consecutive air ticks and flags when exceeding threshold.
 */
public class FlyA extends MovementCheck {

    private int maxAirTicks;

    public FlyA(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.FLY_A, playerData);
        this.maxAirTicks = plugin.getConfigManager().getInt(CheckType.FLY_A.getConfigPath() + ".max-air-ticks", 40);
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

        // Check for exempt conditions
        if (isExempt(player))
            return;

        int airTicks = playerData.getAirTicks();

        if (airTicks > maxAirTicks && playerData.getDeltaY() >= -0.1) {
            flag("airTicks=" + airTicks + " deltaY=" + String.format("%.3f", playerData.getDeltaY()));
        }
    }

    private boolean isExempt(Player player) {
        Location loc = playerData.getCurrentLocation();
        if (loc == null)
            return true;
        if (!VersionUtil.isChunkLoaded(loc))
            return true;

        // Check blocks around player for climbable/liquid
        Block current = loc.getBlock();
        Block below = loc.clone().subtract(0, 0.5, 0).getBlock();

        if (VersionUtil.isLiquid(current) || VersionUtil.isLiquid(below))
            return true;
        if (VersionUtil.isClimbable(current.getType()))
            return true;
        if (VersionUtil.isWeb(current.getType()))
            return true;

        // Check for levitation/slow falling (1.13+)
        for (PotionEffect effect : player.getActivePotionEffects()) {
            String effectName = effect.getType().getName();
            if (effectName.equals("LEVITATION") || effectName.equals("SLOW_FALLING")) {
                return true;
            }
        }

        // Pending velocity (knockback)
        if (playerData.isPendingVelocity() && playerData.getVelocityTicks() < 20)
            return true;

        // Riptide/elytra (1.9+)
        if (VersionUtil.isGliding(player))
            return true;

        // Check if near bouncy blocks
        if (VersionUtil.isBouncy(below.getType()))
            return true;

        return false;
    }
}
