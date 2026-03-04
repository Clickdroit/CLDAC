package fr.cldac.anticheat.check.impl.movement;

import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.data.PlayerData;

/**
 * Marker interface (abstract class) for movement-based checks.
 */
public abstract class MovementCheck extends Check {

    public MovementCheck(CLDAC plugin, CheckType checkType, PlayerData playerData) {
        super(plugin, checkType, playerData);
    }

    /**
     * Called on every movement packet.
     */
    public abstract void onMove();
}
