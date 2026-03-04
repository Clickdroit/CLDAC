package fr.cldac.anticheat.check.impl.combat;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Criticals - Detects illegal critical hits.
 * Critical hits require the player to be falling (deltaY < 0, not on ground).
 * Flags when a critical hit occurs while player is on the ground or moving upward.
 */
public class Criticals extends Check {

    public Criticals(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.CRITICALS, playerData);
    }

    /**
     * Called when a player lands a critical hit.
     */
    public void onCriticalHit(Player attacker, Entity target) {
        if (playerData.isExempt()) return;

        // Vanilla critical hit requirements:
        // - Player must be falling (deltaY < 0)
        // - Player must not be on ground
        // - Player must not be in liquid
        // - Player must not be on ladder
        // - Player must not be blind

        boolean onGround = playerData.isOnGround();
        double deltaY = playerData.getDeltaY();

        // If the player claims to not be on ground but delta is basically zero
        // or slightly positive, they might be spoofing
        if (!onGround && Math.abs(deltaY) < 0.005 && playerData.getAirTicks() < 2) {
            flag("microJump deltaY=" + String.format("%.4f", deltaY)
                    + " airTicks=" + playerData.getAirTicks());
        }

        // If on ground and still got critical
        if (onGround && playerData.getGroundTicks() > 2) {
            flag("onGround=true groundTicks=" + playerData.getGroundTicks());
        }
    }
}
