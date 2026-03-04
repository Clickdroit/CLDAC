package fr.cldac.anticheat.check;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.impl.combat.*;
import fr.cldac.anticheat.check.impl.movement.*;
import fr.cldac.anticheat.check.impl.player.*;
import fr.cldac.anticheat.data.PlayerData;

import java.util.*;

/**
 * Manages instantiation and dispatching of checks per player.
 */
public class CheckManager {

    private final CLDAC plugin;
    private final Map<UUID, List<Check>> playerChecks = new HashMap<>();

    public CheckManager(CLDAC plugin) {
        this.plugin = plugin;
    }

    /**
     * Create all check instances for a player.
     */
    public void registerPlayer(PlayerData data) {
        List<Check> checks = new ArrayList<>();

        // Movement checks
        checks.add(new SpeedA(plugin, data));
        checks.add(new SpeedB(plugin, data));
        checks.add(new FlyA(plugin, data));
        checks.add(new FlyB(plugin, data));
        checks.add(new NoFall(plugin, data));
        checks.add(new Jesus(plugin, data));
        checks.add(new Phase(plugin, data));

        // Combat checks
        checks.add(new KillAuraA(plugin, data));
        checks.add(new Reach(plugin, data));
        checks.add(new AutoClicker(plugin, data));
        checks.add(new Velocity(plugin, data));
        checks.add(new Criticals(plugin, data));

        // Player checks
        checks.add(new NoSlowdown(plugin, data));
        checks.add(new Scaffold(plugin, data));
        checks.add(new InventoryMove(plugin, data));
        checks.add(new BadPackets(plugin, data));
        checks.add(new NameTags(plugin, data));
        checks.add(new XRay(plugin, data));

        // Packet checks
        checks.add(new PacketRate(plugin, data));

        playerChecks.put(data.getUuid(), checks);
    }

    public void unregisterPlayer(UUID uuid) {
        playerChecks.remove(uuid);
    }

    /**
     * Get all checks for a player.
     */
    public List<Check> getChecks(UUID uuid) {
        return playerChecks.getOrDefault(uuid, Collections.emptyList());
    }

    /**
     * Get a specific check for a player by type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Check> T getCheck(UUID uuid, CheckType type) {
        for (Check check : getChecks(uuid)) {
            if (check.getCheckType() == type) {
                return (T) check;
            }
        }
        return null;
    }

    /**
     * Get all checks of a specific type class for a player.
     */
    @SuppressWarnings("unchecked")
    public <T extends Check> T getCheck(UUID uuid, Class<T> clazz) {
        for (Check check : getChecks(uuid)) {
            if (clazz.isInstance(check)) {
                return (T) check;
            }
        }
        return null;
    }

    /**
     * Run all movement checks for a player.
     * Dispatches to all checks that extend MovementCheck, regardless of category.
     */
    public void runMovementChecks(UUID uuid) {
        for (Check check : getChecks(uuid)) {
            if (check.isEnabled() && check instanceof MovementCheck) {
                ((MovementCheck) check).onMove();
            }
        }
    }

    /**
     * Run packet-rate check.
     */
    public void runPacketChecks(UUID uuid) {
        for (Check check : getChecks(uuid)) {
            if (check.isEnabled() && check.getCheckType().getCategory() == CheckType.CheckCategory.PACKET) {
                if (check instanceof PacketRate) {
                    ((PacketRate) check).onPacket();
                }
            }
        }
    }

    /**
     * Toggle a check type on/off for all players.
     */
    public void toggleCheck(CheckType type) {
        for (List<Check> checks : playerChecks.values()) {
            for (Check check : checks) {
                if (check.getCheckType() == type) {
                    check.setEnabled(!check.isEnabled());
                }
            }
        }
    }

    /**
     * Reload config for all checks.
     */
    public void reloadChecks() {
        for (List<Check> checks : playerChecks.values()) {
            for (Check check : checks) {
                check.loadConfig();
            }
        }
    }
}
