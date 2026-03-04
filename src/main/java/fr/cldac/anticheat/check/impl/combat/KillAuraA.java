package fr.cldac.anticheat.check.impl.combat;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.LinkedList;

/**
 * KillAuraA - Advanced combat hack detection.
 * Detects KillAura, AimAssist, TriggerBot via multiple analysis methods:
 *
 * 1. Rotation snap detection: abnormally fast yaw changes when attacking
 * 2. GCD rotation analysis: aimbot sensitivity detection via Greatest Common Divisor
 * 3. Multi-aura detection: switching between multiple targets impossibly fast
 * 4. Aim consistency: suspiciously perfect pitch alignment (locked aim)
 * 5. Attack angle analysis: hitting targets outside FOV
 */
public class KillAuraA extends Check {

    private double maxYawRate;
    private double buffer = 0;
    private int targetsSwitched = 0;
    private long lastSwitchReset = 0;

    // Analysis sub-buffers
    private double snapBuffer = 0;
    private double gcdBuffer = 0;
    private double consistencyBuffer = 0;

    public KillAuraA(CLDAC plugin, PlayerData playerData) {
        super(plugin, CheckType.KILLAURA_A, playerData);
        this.maxYawRate = plugin.getConfigManager().getDouble(
                CheckType.KILLAURA_A.getConfigPath() + ".max-yaw-rate", 50.0);
    }

    /**
     * Called when a player attacks an entity.
     */
    public void onAttack(Player attacker, Entity target) {
        if (playerData.isExempt()) return;

        float deltaYaw = playerData.getDeltaYaw();
        float deltaPitch = playerData.getDeltaPitch();
        long now = System.currentTimeMillis();

        // Track attack rotation deltas
        playerData.handleAttackRotation(playerData.getRawDeltaYaw(), playerData.getRawDeltaPitch());

        // === Check 1: Rotation snap (instant aim) ===
        checkRotationSnap(deltaYaw, deltaPitch, now);

        // === Check 2: GCD Analysis (aimbot sensitivity fingerprint) ===
        checkGCDConsistency();

        // === Check 3: Multi-aura (fast target switching) ===
        checkMultiAura(target, now);

        // === Check 4: Aim consistency (locked/perfect pitch) ===
        checkAimConsistency(attacker, target, deltaPitch);

        // === Check 5: Attack angle (hitting outside FOV) ===
        checkAttackAngle(attacker, target);

        // Decay buffers
        snapBuffer = Math.max(0, snapBuffer - 0.2);
        gcdBuffer = Math.max(0, gcdBuffer - 0.15);
        consistencyBuffer = Math.max(0, consistencyBuffer - 0.1);
    }

    /**
     * Detection 1: Snap rotations — instant aim to targets
     */
    private void checkRotationSnap(float deltaYaw, float deltaPitch, long now) {
        // Large snap combined with very small pitch change = aimbot snap
        if (deltaYaw > maxYawRate && deltaPitch < 5.0f) {
            snapBuffer += 1.5;
        }
        // Very large snap in general
        else if (deltaYaw > maxYawRate * 1.5) {
            snapBuffer += 1.0;
        }
        // Perfectly zero pitch change during yaw movement (locked aim)
        if (deltaPitch < 0.01f && deltaYaw > 15.0f) {
            snapBuffer += 0.5;
        }

        if (snapBuffer > 5.0) {
            flag("SNAP deltaYaw=" + String.format("%.1f", deltaYaw)
                    + " deltaPitch=" + String.format("%.1f", deltaPitch)
                    + " buffer=" + String.format("%.1f", snapBuffer));
            snapBuffer = Math.max(0, snapBuffer - 3.0);
        }
    }

    /**
     * Detection 2: GCD analysis.
     * Legitimate mouse input has a consistent GCD based on sensitivity.
     * Aimbots compute rotations mathematically, producing either:
     * - Very low GCD variation (perfect aimbot)
     * - Zero GCD (direct angle calculation)
     */
    private void checkGCDConsistency() {
        LinkedList<Float> attackYaws = playerData.getAttackYawDeltas();
        LinkedList<Float> attackPitchs = playerData.getAttackPitchDeltas();

        if (attackYaws.size() < 8) return;

        // Check yaw GCD consistency
        double yawGcdCV = playerData.getRotationGCD(attackYaws);
        double pitchGcdCV = playerData.getRotationGCD(attackPitchs);

        // Very low coefficient of variation = suspiciously consistent = aimbot
        if (yawGcdCV >= 0 && yawGcdCV < 0.05 && pitchGcdCV >= 0 && pitchGcdCV < 0.05) {
            gcdBuffer += 2.0;
        } else if (yawGcdCV >= 0 && yawGcdCV < 0.1) {
            gcdBuffer += 0.5;
        }

        if (gcdBuffer > 4.0) {
            flag("GCD yawCV=" + String.format("%.4f", yawGcdCV)
                    + " pitchCV=" + String.format("%.4f", pitchGcdCV));
            gcdBuffer = Math.max(0, gcdBuffer - 3.0);
        }
    }

    /**
     * Detection 3: Multi-aura — switching between multiple targets too fast.
     */
    private void checkMultiAura(Entity target, long now) {
        // Reset counter every 5 seconds
        if (now - lastSwitchReset > 5000) {
            targetsSwitched = 0;
            lastSwitchReset = now;
        }

        int currentTargetId = target.getEntityId();
        if (currentTargetId != playerData.getLastTargetEntityId()) {
            long timeSinceLastAttack = now - playerData.getLastAttackTimestamp();

            // Switching targets very rapidly
            if (timeSinceLastAttack < 150) {
                targetsSwitched++;
            }

            playerData.setLastTargetEntityId(currentTargetId);
            playerData.setAttacksSinceSwitch(0);
        } else {
            playerData.incrementAttacksSinceSwitch();
        }

        // 4+ rapid target switches in 5 seconds = multi-aura
        if (targetsSwitched >= 4) {
            flag("MULTI_AURA switches=" + targetsSwitched + " in 5s");
            targetsSwitched = 0;
        }

        playerData.setLastTargetLocation(target.getLocation().clone());
    }

    /**
     * Detection 4: Aim consistency — suspiciously perfect vertical tracking.
     * Legitimate players have natural jitter in their pitch; aimbots don't.
     */
    private void checkAimConsistency(Player attacker, Entity target, float deltaPitch) {
        if (!(target instanceof Player)) return;
        Player targetPlayer = (Player) target;

        // Calculate expected pitch to target center
        Location eye = attacker.getEyeLocation();
        Location targetCenter = targetPlayer.getLocation().add(0, 0.9, 0);
        double dx = targetCenter.getX() - eye.getX();
        double dy = targetCenter.getY() - eye.getY();
        double dz = targetCenter.getZ() - eye.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float expectedPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));

        float actualPitch = attacker.getLocation().getPitch();
        float pitchError = Math.abs(actualPitch - expectedPitch);

        // If pitch consistently matches the perfect angle (< 1 degree error) over multiple attacks
        if (pitchError < 1.0f && playerData.getAttacksSinceSwitch() > 3) {
            consistencyBuffer += 0.8;
        }

        if (consistencyBuffer > 3.0) {
            flag("AIM_LOCK pitchError=" + String.format("%.2f", pitchError)
                    + " attacks=" + playerData.getAttacksSinceSwitch());
            consistencyBuffer = Math.max(0, consistencyBuffer - 2.0);
        }
    }

    /**
     * Detection 5: Attack angle — hitting targets outside normal field of view.
     */
    private void checkAttackAngle(Player attacker, Entity target) {
        Location eye = attacker.getEyeLocation();
        Location targetLoc = target.getLocation().add(0, 0.9, 0);

        org.bukkit.util.Vector toTarget = targetLoc.toVector().subtract(eye.toVector()).normalize();
        org.bukkit.util.Vector lookDir = eye.getDirection().normalize();

        double dot = lookDir.dot(toTarget);
        double angle = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));

        // Vanilla FOV is ~110 degrees, but you can only hit in ~60 degrees
        // Hitting targets at > 90 degrees (behind you) is highly suspicious
        if (angle > 90) {
            flag("BEHIND_ATTACK angle=" + String.format("%.1f", angle) + "°");
        } else if (angle > 60) {
            buffer += 0.5;
            if (buffer > 3.0) {
                flag("WIDE_ANGLE angle=" + String.format("%.1f", angle) + "°"
                        + " buffer=" + String.format("%.1f", buffer));
                buffer = Math.max(0, buffer - 2.0);
            }
        } else {
            buffer = Math.max(0, buffer - 0.2);
        }
    }
}
