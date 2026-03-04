package fr.cldac.anticheat.check;

/**
 * Enum defining all check types with their category and configuration path.
 */
public enum CheckType {

    // Movement
    SPEED_A("SpeedA", CheckCategory.MOVEMENT, "checks.movement.speed-a"),
    SPEED_B("SpeedB", CheckCategory.MOVEMENT, "checks.movement.speed-b"),
    FLY_A("FlyA", CheckCategory.MOVEMENT, "checks.movement.fly-a"),
    FLY_B("FlyB", CheckCategory.MOVEMENT, "checks.movement.fly-b"),
    NOFALL("NoFall", CheckCategory.MOVEMENT, "checks.movement.nofall"),
    JESUS("Jesus", CheckCategory.MOVEMENT, "checks.movement.jesus"),
    PHASE("Phase", CheckCategory.MOVEMENT, "checks.movement.phase"),

    // Combat
    KILLAURA_A("KillAuraA", CheckCategory.COMBAT, "checks.combat.killaura"),
    REACH("Reach", CheckCategory.COMBAT, "checks.combat.reach"),
    AUTOCLICKER("AutoClicker", CheckCategory.COMBAT, "checks.combat.autoclicker"),
    VELOCITY("Velocity", CheckCategory.COMBAT, "checks.combat.velocity"),
    CRITICALS("Criticals", CheckCategory.COMBAT, "checks.combat.criticals"),

    // Player
    NOSLOWDOWN("NoSlowdown", CheckCategory.PLAYER, "checks.player.noslowdown"),
    SCAFFOLD("Scaffold", CheckCategory.PLAYER, "checks.player.scaffold"),
    INVENTORY_MOVE("InventoryMove", CheckCategory.PLAYER, "checks.player.inventorymove"),
    BAD_PACKETS("BadPackets", CheckCategory.PLAYER, "checks.player.badpackets"),
    NAMETAGS("NameTags", CheckCategory.PLAYER, "checks.player.nametags"),
    XRAY("XRay", CheckCategory.PLAYER, "checks.player.xray"),

    // Packet
    PACKET_RATE("PacketRate", CheckCategory.PACKET, "checks.packet.packetrate");

    private final String displayName;
    private final CheckCategory category;
    private final String configPath;

    CheckType(String displayName, CheckCategory category, String configPath) {
        this.displayName = displayName;
        this.category = category;
        this.configPath = configPath;
    }

    public String getDisplayName() { return displayName; }
    public CheckCategory getCategory() { return category; }
    public String getConfigPath() { return configPath; }

    public enum CheckCategory {
        MOVEMENT, COMBAT, PLAYER, PACKET
    }
}
