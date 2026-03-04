package fr.cldac.anticheat.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Stores per-player data used by all anti-cheat checks.
 * Updated on every movement packet and relevant events.
 */
public class PlayerData {

    private final UUID uuid;
    private final Player player;

    // === Position tracking ===
    private Location currentLocation;
    private Location lastLocation;
    private Location lastGroundLocation;

    // === Movement data ===
    private double deltaX, deltaY, deltaZ;
    private double deltaXZ; // horizontal distance
    private double lastDeltaXZ;
    private double lastDeltaY;
    private boolean onGround;
    private boolean lastOnGround;
    private boolean serverOnGround;
    private int airTicks;
    private int groundTicks;
    private float lastYaw, lastPitch;
    private float deltaYaw, deltaPitch;
    private float rawDeltaYaw, rawDeltaPitch; // Signed deltas for GCD analysis

    // === Movement history (for prediction) ===
    private final LinkedList<Double> deltaXZHistory = new LinkedList<>();
    private final LinkedList<Double> deltaYHistory = new LinkedList<>();
    private static final int MOVEMENT_HISTORY_SIZE = 20;

    // === Rotation history (for GCD / aimbot analysis) ===
    private final LinkedList<Float> yawDeltaHistory = new LinkedList<>();
    private final LinkedList<Float> pitchDeltaHistory = new LinkedList<>();
    private static final int ROTATION_HISTORY_SIZE = 40;

    // === Velocity / Knockback ===
    private long lastVelocityTimestamp;
    private double lastVelocityX, lastVelocityY, lastVelocityZ;
    private boolean pendingVelocity;
    private int velocityTicks;
    private final LinkedList<long[]> velocityHistory = new LinkedList<>(); // [timestamp, vx*1000, vy*1000, vz*1000]

    // === Teleport tracking ===
    private long lastTeleportTimestamp;

    // === Combat data ===
    private long lastAttackTimestamp;
    private final LinkedList<Long> clickTimestamps = new LinkedList<>();
    private Location lastTargetLocation;
    private int lastTargetEntityId;
    private int attacksSinceSwitch;
    private int targetSwitchCount;
    private long lastTargetSwitchTimestamp;
    private final LinkedList<Float> attackYawDeltas = new LinkedList<>();
    private final LinkedList<Float> attackPitchDeltas = new LinkedList<>();
    private static final int ATTACK_ROTATION_HISTORY = 20;

    // === Block interaction ===
    private long lastBlockPlaceTimestamp;
    private int blockPlaceCount;
    private long lastBlockBreakTimestamp;
    private final LinkedList<Long> blockPlaceTimestamps = new LinkedList<>();
    private Location lastBlockPlaceLocation;

    // === Inventory ===
    private boolean inventoryOpen;
    private long lastInventoryOpenTimestamp;

    // === Using item (bow, food, etc.) ===
    private boolean usingItem;
    private long itemUseStartTimestamp;

    // === Fall damage ===
    private double fallDistance;
    private boolean expectingFallDamage;

    // === Packet data ===
    private long lastPacketTimestamp;
    private int packetCount;
    private long packetCountResetTimestamp;
    private final LinkedList<Long> packetTimestamps = new LinkedList<>();
    private static final int PACKET_HISTORY_SIZE = 100;

    // === Sprinting tracking ===
    private boolean sprinting;
    private boolean sneaking;

    // === XRay tracking ===
    private int oresMined;
    private int totalBlocksMined;
    private final Map<String, Integer> oreBreakCounts = new HashMap<>();
    private final LinkedList<long[]> oreBreakHistory = new LinkedList<>(); // [timestamp, x, y, z]
    private static final int ORE_HISTORY_SIZE = 30;

    // === NoFall tracking ===
    private int groundSpoofTicks;
    private double maxFallDistance; // Max fall distance in current fall

    // === Timer detection ===
    private long lastMovementTimestamp;
    private final LinkedList<Long> movementIntervals = new LinkedList<>();
    private static final int MOVEMENT_INTERVAL_HISTORY = 50;

    // === Join time ===
    private final long joinTimestamp;

    // === Exempt flags ===
    private boolean exempt;

    public PlayerData(Player player) {
        this.uuid = player.getUniqueId();
        this.player = player;
        this.joinTimestamp = System.currentTimeMillis();
        this.currentLocation = player.getLocation().clone();
        this.lastLocation = player.getLocation().clone();
        this.lastGroundLocation = player.getLocation().clone();
        this.packetCountResetTimestamp = System.currentTimeMillis();
        this.lastYaw = player.getLocation().getYaw();
        this.lastPitch = player.getLocation().getPitch();
        this.lastMovementTimestamp = System.currentTimeMillis();
    }

    /**
     * Called on every position/look packet to update all movement data.
     */
    public void handleMovement(double x, double y, double z, float yaw, float pitch, boolean clientOnGround) {
        long now = System.currentTimeMillis();

        this.lastLocation = this.currentLocation != null ? this.currentLocation.clone() : null;
        this.currentLocation = new Location(player.getWorld(), x, y, z, yaw, pitch);

        this.lastDeltaXZ = this.deltaXZ;

        if (this.lastLocation != null) {
            this.deltaX = x - lastLocation.getX();
            this.deltaY = y - lastLocation.getY();
            this.deltaZ = z - lastLocation.getZ();
            this.deltaXZ = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        }

        // Signed rotation deltas (important for GCD analysis)
        this.rawDeltaYaw = yaw - this.lastYaw;
        this.rawDeltaPitch = pitch - this.lastPitch;
        // Handle yaw wrapping for signed
        if (this.rawDeltaYaw > 180.0f) this.rawDeltaYaw -= 360.0f;
        if (this.rawDeltaYaw < -180.0f) this.rawDeltaYaw += 360.0f;

        this.deltaYaw = Math.abs(this.rawDeltaYaw);
        this.deltaPitch = Math.abs(this.rawDeltaPitch);

        this.lastOnGround = this.onGround;
        this.onGround = clientOnGround;

        if (this.onGround) {
            this.groundTicks++;
            this.airTicks = 0;
            this.lastGroundLocation = this.currentLocation.clone();
            if (this.fallDistance > this.maxFallDistance) {
                this.maxFallDistance = this.fallDistance;
            }
            this.fallDistance = 0;
            this.groundSpoofTicks = 0;
        } else {
            this.airTicks++;
            this.groundTicks = 0;
            if (this.deltaY < 0) {
                this.fallDistance += Math.abs(this.deltaY);
            }
        }

        // Track movement history
        addToHistory(deltaXZHistory, deltaXZ, MOVEMENT_HISTORY_SIZE);
        addToHistory(deltaYHistory, deltaY, MOVEMENT_HISTORY_SIZE);

        // Track rotation history for GCD analysis
        if (deltaYaw > 0.01f) addToFloatHistory(yawDeltaHistory, rawDeltaYaw, ROTATION_HISTORY_SIZE);
        if (deltaPitch > 0.01f) addToFloatHistory(pitchDeltaHistory, rawDeltaPitch, ROTATION_HISTORY_SIZE);

        // Track movement intervals for Timer detection
        long interval = now - lastMovementTimestamp;
        if (interval > 0 && interval < 200) { // Filter out outliers
            addToLongHistory(movementIntervals, interval, MOVEMENT_INTERVAL_HISTORY);
        }
        this.lastMovementTimestamp = now;

        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.lastDeltaY = this.deltaY;
    }

    public void handleClick() {
        long now = System.currentTimeMillis();
        clickTimestamps.addLast(now);
        while (clickTimestamps.size() > 20) {
            clickTimestamps.removeFirst();
        }
        this.lastAttackTimestamp = now;
    }

    /**
     * Track attack rotations for aimbot analysis.
     */
    public void handleAttackRotation(float yawDelta, float pitchDelta) {
        addToFloatHistory(attackYawDeltas, yawDelta, ATTACK_ROTATION_HISTORY);
        addToFloatHistory(attackPitchDeltas, pitchDelta, ATTACK_ROTATION_HISTORY);
    }

    /**
     * Returns the current clicks-per-second measured from recent click history.
     */
    public double getCPS() {
        if (clickTimestamps.size() < 2) return 0;
        long oldest = clickTimestamps.getFirst();
        long newest = clickTimestamps.getLast();
        double seconds = (newest - oldest) / 1000.0;
        if (seconds <= 0) return clickTimestamps.size();
        return (clickTimestamps.size() - 1) / seconds;
    }

    /**
     * Computes the Greatest Common Divisor of rotation deltas.
     * Aimbot/smooth aura will have a very consistent GCD matching their mouse sensitivity.
     * Legitimate players will have more variable GCD.
     */
    public double getRotationGCD(LinkedList<Float> deltas) {
        if (deltas.size() < 4) return -1;

        List<Double> gcds = new ArrayList<>();
        Float[] arr = deltas.toArray(new Float[0]);
        for (int i = 1; i < arr.length && i < 20; i++) {
            double prev = Math.abs(arr[i - 1]);
            double curr = Math.abs(arr[i]);
            if (prev > 0.001 && curr > 0.001) {
                double gcd = gcd(prev, curr);
                if (gcd > 0.001) {
                    gcds.add(gcd);
                }
            }
        }

        if (gcds.size() < 3) return -1;

        // Calculate how consistent the GCDs are
        double sum = 0;
        for (double g : gcds) sum += g;
        double mean = sum / gcds.size();

        double varianceSum = 0;
        for (double g : gcds) varianceSum += (g - mean) * (g - mean);
        double stdDev = Math.sqrt(varianceSum / gcds.size());

        // Return coefficient of variation (lower = more consistent = more suspicious)
        return mean > 0 ? stdDev / mean : -1;
    }

    private double gcd(double a, double b) {
        // Euclidean GCD adapted for floating point
        for (int i = 0; i < 50; i++) {
            if (Math.abs(b) < 0.001) return a;
            double temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    /**
     * Calculate the average movement interval (for Timer detection).
     * Vanilla sends ~50ms per tick. Timer hacks reduce this.
     */
    public double getAverageMovementInterval() {
        if (movementIntervals.size() < 5) return 50.0;
        long sum = 0;
        for (long interval : movementIntervals) sum += interval;
        return (double) sum / movementIntervals.size();
    }

    /**
     * Get movement acceleration (deltaXZ difference between ticks).
     */
    public double getAcceleration() {
        return deltaXZ - lastDeltaXZ;
    }

    public void incrementPacketCount() {
        long now = System.currentTimeMillis();
        if (now - packetCountResetTimestamp >= 1000) {
            this.packetCount = 0;
            this.packetCountResetTimestamp = now;
        }
        this.packetCount++;
        this.lastPacketTimestamp = now;

        addToLongHistory(packetTimestamps, now, PACKET_HISTORY_SIZE);
    }

    public void trackBlockBreak(String blockType, Location location) {
        this.totalBlocksMined++;
        this.lastBlockBreakTimestamp = System.currentTimeMillis();

        String upper = blockType.toUpperCase();
        if (upper.contains("ORE")) {
            this.oresMined++;
            oreBreakCounts.merge(upper, 1, Integer::sum);

            // Track ore positions for direction analysis
            long[] entry = new long[]{
                    System.currentTimeMillis(),
                    (long) location.getX(),
                    (long) location.getY(),
                    (long) location.getZ()
            };
            oreBreakHistory.addLast(entry);
            while (oreBreakHistory.size() > ORE_HISTORY_SIZE) {
                oreBreakHistory.removeFirst();
            }
        }
    }

    /**
     * Analyzes ore mining for straight-line mining patterns (XRay indicator).
     * Returns true if the player seems to mine in a straight line towards ores.
     */
    public boolean hasStraightLineOreMining() {
        if (oreBreakHistory.size() < 5) return false;

        // Check if consecutive ore breaks form roughly straight lines
        long[][] entries = oreBreakHistory.toArray(new long[0][]);
        int straightLineCount = 0;

        for (int i = 2; i < entries.length; i++) {
            double dx1 = entries[i][1] - entries[i - 1][1];
            double dy1 = entries[i][2] - entries[i - 1][2];
            double dz1 = entries[i][3] - entries[i - 1][3];
            double dx2 = entries[i - 1][1] - entries[i - 2][1];
            double dy2 = entries[i - 1][2] - entries[i - 2][2];
            double dz2 = entries[i - 1][3] - entries[i - 2][3];

            double len1 = Math.sqrt(dx1 * dx1 + dy1 * dy1 + dz1 * dz1);
            double len2 = Math.sqrt(dx2 * dx2 + dy2 * dy2 + dz2 * dz2);

            if (len1 > 0.5 && len2 > 0.5) {
                double dot = (dx1 * dx2 + dy1 * dy2 + dz1 * dz2) / (len1 * len2);
                // Cosine similarity > 0.8 = roughly same direction
                if (dot > 0.8) straightLineCount++;
            }
        }

        return straightLineCount >= 3;
    }

    /**
     * Calculate average time between ore breaks (very fast = suspicious).
     */
    public double getAverageOreMiningInterval() {
        if (oreBreakHistory.size() < 3) return 999999;
        long[] first = oreBreakHistory.getFirst();
        long[] last = oreBreakHistory.getLast();
        double totalSeconds = (last[0] - first[0]) / 1000.0;
        return totalSeconds > 0 ? totalSeconds / (oreBreakHistory.size() - 1) : 999999;
    }

    public void resetXRayData() {
        this.oresMined = 0;
        this.totalBlocksMined = 0;
        this.oreBreakCounts.clear();
        this.oreBreakHistory.clear();
    }

    public void addBlockPlaceTimestamp(long timestamp) {
        blockPlaceTimestamps.addLast(timestamp);
        while (blockPlaceTimestamps.size() > 20) blockPlaceTimestamps.removeFirst();
    }

    /**
     * Get blocks placed per second in recent history.
     */
    public double getBlockPlacesPerSecond() {
        if (blockPlaceTimestamps.size() < 2) return 0;
        long oldest = blockPlaceTimestamps.getFirst();
        long newest = blockPlaceTimestamps.getLast();
        double seconds = (newest - oldest) / 1000.0;
        if (seconds <= 0) return blockPlaceTimestamps.size();
        return (blockPlaceTimestamps.size() - 1) / seconds;
    }

    // === History helpers ===

    private void addToHistory(LinkedList<Double> list, double value, int maxSize) {
        list.addLast(value);
        while (list.size() > maxSize) list.removeFirst();
    }

    private void addToFloatHistory(LinkedList<Float> list, float value, int maxSize) {
        list.addLast(value);
        while (list.size() > maxSize) list.removeFirst();
    }

    private void addToLongHistory(LinkedList<Long> list, long value, int maxSize) {
        list.addLast(value);
        while (list.size() > maxSize) list.removeFirst();
    }

    // ========================
    // Getters & Setters
    // ========================

    public UUID getUuid() { return uuid; }
    public Player getPlayer() { return player; }
    public Location getCurrentLocation() { return currentLocation; }
    public Location getLastLocation() { return lastLocation; }
    public Location getLastGroundLocation() { return lastGroundLocation; }
    public double getDeltaX() { return deltaX; }
    public double getDeltaY() { return deltaY; }
    public double getDeltaZ() { return deltaZ; }
    public double getDeltaXZ() { return deltaXZ; }
    public double getLastDeltaXZ() { return lastDeltaXZ; }
    public double getLastDeltaY() { return lastDeltaY; }
    public boolean isOnGround() { return onGround; }
    public boolean isLastOnGround() { return lastOnGround; }
    public boolean isServerOnGround() { return serverOnGround; }
    public void setServerOnGround(boolean serverOnGround) { this.serverOnGround = serverOnGround; }
    public int getAirTicks() { return airTicks; }
    public int getGroundTicks() { return groundTicks; }
    public float getLastYaw() { return lastYaw; }
    public float getLastPitch() { return lastPitch; }
    public float getDeltaYaw() { return deltaYaw; }
    public float getDeltaPitch() { return deltaPitch; }
    public float getRawDeltaYaw() { return rawDeltaYaw; }
    public float getRawDeltaPitch() { return rawDeltaPitch; }
    public LinkedList<Float> getYawDeltaHistory() { return yawDeltaHistory; }
    public LinkedList<Float> getPitchDeltaHistory() { return pitchDeltaHistory; }
    public LinkedList<Double> getDeltaXZHistory() { return deltaXZHistory; }
    public LinkedList<Double> getDeltaYHistory() { return deltaYHistory; }

    public long getLastVelocityTimestamp() { return lastVelocityTimestamp; }
    public void setLastVelocityTimestamp(long t) { this.lastVelocityTimestamp = t; }
    public double getLastVelocityX() { return lastVelocityX; }
    public double getLastVelocityY() { return lastVelocityY; }
    public double getLastVelocityZ() { return lastVelocityZ; }
    public void setLastVelocity(double x, double y, double z) {
        this.lastVelocityX = x;
        this.lastVelocityY = y;
        this.lastVelocityZ = z;
        this.pendingVelocity = true;
        this.velocityTicks = 0;
        this.lastVelocityTimestamp = System.currentTimeMillis();
    }
    public boolean isPendingVelocity() { return pendingVelocity; }
    public void setPendingVelocity(boolean v) { this.pendingVelocity = v; }
    public int getVelocityTicks() { return velocityTicks; }
    public void incrementVelocityTicks() { this.velocityTicks++; }

    public long getLastTeleportTimestamp() { return lastTeleportTimestamp; }
    public void setLastTeleportTimestamp(long t) { this.lastTeleportTimestamp = t; }

    public long getLastAttackTimestamp() { return lastAttackTimestamp; }
    public LinkedList<Long> getClickTimestamps() { return clickTimestamps; }
    public Location getLastTargetLocation() { return lastTargetLocation; }
    public void setLastTargetLocation(Location loc) { this.lastTargetLocation = loc; }
    public int getLastTargetEntityId() { return lastTargetEntityId; }
    public void setLastTargetEntityId(int id) { this.lastTargetEntityId = id; }
    public int getAttacksSinceSwitch() { return attacksSinceSwitch; }
    public void setAttacksSinceSwitch(int c) { this.attacksSinceSwitch = c; }
    public void incrementAttacksSinceSwitch() { this.attacksSinceSwitch++; }
    public int getTargetSwitchCount() { return targetSwitchCount; }
    public void incrementTargetSwitchCount() { this.targetSwitchCount++; }
    public void resetTargetSwitchCount() { this.targetSwitchCount = 0; }
    public long getLastTargetSwitchTimestamp() { return lastTargetSwitchTimestamp; }
    public void setLastTargetSwitchTimestamp(long t) { this.lastTargetSwitchTimestamp = t; }
    public LinkedList<Float> getAttackYawDeltas() { return attackYawDeltas; }
    public LinkedList<Float> getAttackPitchDeltas() { return attackPitchDeltas; }

    public long getLastBlockPlaceTimestamp() { return lastBlockPlaceTimestamp; }
    public void setLastBlockPlaceTimestamp(long t) { this.lastBlockPlaceTimestamp = t; }
    public int getBlockPlaceCount() { return blockPlaceCount; }
    public void setBlockPlaceCount(int c) { this.blockPlaceCount = c; }
    public void incrementBlockPlaceCount() { this.blockPlaceCount++; }
    public long getLastBlockBreakTimestamp() { return lastBlockBreakTimestamp; }
    public Location getLastBlockPlaceLocation() { return lastBlockPlaceLocation; }
    public void setLastBlockPlaceLocation(Location loc) { this.lastBlockPlaceLocation = loc; }

    public boolean isInventoryOpen() { return inventoryOpen; }
    public void setInventoryOpen(boolean open) {
        this.inventoryOpen = open;
        if (open) this.lastInventoryOpenTimestamp = System.currentTimeMillis();
    }
    public long getLastInventoryOpenTimestamp() { return lastInventoryOpenTimestamp; }

    public boolean isUsingItem() { return usingItem; }
    public void setUsingItem(boolean using) {
        this.usingItem = using;
        if (using) this.itemUseStartTimestamp = System.currentTimeMillis();
    }
    public long getItemUseStartTimestamp() { return itemUseStartTimestamp; }

    public double getFallDistance() { return fallDistance; }
    public void setFallDistance(double d) { this.fallDistance = d; }
    public double getMaxFallDistance() { return maxFallDistance; }
    public void resetMaxFallDistance() { this.maxFallDistance = 0; }
    public boolean isExpectingFallDamage() { return expectingFallDamage; }
    public void setExpectingFallDamage(boolean e) { this.expectingFallDamage = e; }
    public int getGroundSpoofTicks() { return groundSpoofTicks; }
    public void incrementGroundSpoofTicks() { this.groundSpoofTicks++; }

    public int getPacketCount() { return packetCount; }

    public boolean isSprinting() { return sprinting; }
    public void setSprinting(boolean sprinting) { this.sprinting = sprinting; }
    public boolean isSneaking() { return sneaking; }
    public void setSneaking(boolean sneaking) { this.sneaking = sneaking; }

    public int getOresMined() { return oresMined; }
    public int getTotalBlocksMined() { return totalBlocksMined; }
    public Map<String, Integer> getOreBreakCounts() { return oreBreakCounts; }
    public LinkedList<long[]> getOreBreakHistory() { return oreBreakHistory; }

    public long getJoinTimestamp() { return joinTimestamp; }
    public boolean isExempt() { return exempt; }
    public void setExempt(boolean exempt) { this.exempt = exempt; }
}
