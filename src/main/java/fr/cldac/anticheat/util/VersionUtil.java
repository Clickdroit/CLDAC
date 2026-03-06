package fr.cldac.anticheat.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Utility class for version-specific operations.
 */
public final class VersionUtil {

    private static final int MAJOR_VERSION;
    private static final int MINOR_VERSION;

    static {
        String version = Bukkit.getBukkitVersion(); // e.g., "1.20.4-R0.1-SNAPSHOT"
        String[] parts = version.split("-")[0].split("\\.");
        MAJOR_VERSION = Integer.parseInt(parts[0]);
        MINOR_VERSION = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
    }

    private VersionUtil() {
    }

    public static int getMajorVersion() {
        return MAJOR_VERSION;
    }

    public static int getMinorVersion() {
        return MINOR_VERSION;
    }

    /**
     * Checks if the server is running at least a given Minecraft version.
     */
    public static boolean isAtLeast(int major, int minor) {
        if (MAJOR_VERSION > major)
            return true;
        return MAJOR_VERSION == major && MINOR_VERSION >= minor;
    }

    /**
     * Returns whether a block is liquid, handling version differences.
     */
    public static boolean isLiquid(Block block) {
        Material type = block.getType();
        String name = type.name();
        return name.equals("WATER") || name.equals("STATIONARY_WATER")
                || name.equals("LAVA") || name.equals("STATIONARY_LAVA");
    }

    /**
     * Check if a material is climbable (ladder/vine).
     */
    public static boolean isClimbable(Material material) {
        String name = material.name();
        return name.equals("LADDER") || name.equals("VINE")
                || name.contains("SCAFFOLDING") || name.contains("TWISTING_VINES")
                || name.contains("WEEPING_VINES") || name.contains("CAVE_VINES");
    }

    /**
     * Check if a block is solid (can be stood on).
     */
    public static boolean isSolid(Block block) {
        return block.getType().isSolid();
    }

    /**
     * Check if a material is an ice block.
     */
    public static boolean isIce(Material material) {
        String name = material.name();
        return name.equals("ICE") || name.equals("PACKED_ICE") || name.equals("BLUE_ICE")
                || name.equals("FROSTED_ICE");
    }

    /**
     * Check if a material is a slab.
     */
    public static boolean isSlab(Material material) {
        return material.name().contains("SLAB") || material.name().contains("STEP");
    }

    /**
     * Check if a material is a staircase.
     */
    public static boolean isStair(Material material) {
        return material.name().contains("STAIRS");
    }

    /**
     * Check if the material is a bed.
     */
    public static boolean isBed(Material material) {
        return material.name().contains("BED");
    }

    /**
     * Check if a material is a soul sand variant.
     */
    public static boolean isSoulSand(Material material) {
        String name = material.name();
        return name.equals("SOUL_SAND") || name.equals("SOUL_SOIL");
    }

    /**
     * Check if a material is a web.
     */
    public static boolean isWeb(Material material) {
        String name = material.name();
        return name.equals("WEB") || name.equals("COBWEB");
    }

    /**
     * Check if player is in a vehicle/boat.
     */
    public static boolean isInVehicle(Player player) {
        return player.isInsideVehicle();
    }

    /**
     * Check if the chunk at the given location is already loaded.
     * This MUST be called before using Location.getBlock() from async threads
     * (e.g. packet listeners) to avoid AsyncCatcher exceptions.
     */
    public static boolean isChunkLoaded(Location loc) {
        if (loc == null)
            return false;
        World world = loc.getWorld();
        if (world == null)
            return false;
        return world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    /**
     * Check if material is a slime block or honey block.
     */
    public static boolean isBouncy(Material material) {
        String name = material.name();
        return name.equals("SLIME_BLOCK") || name.equals("HONEY_BLOCK");
    }

    /**
     * Check if a material is an ore type.
     */
    public static boolean isOre(Material material) {
        return material.name().toUpperCase().contains("ORE");
    }

    /**
     * Check if a player is gliding (elytra). Uses reflection for 1.8 compatibility.
     */
    public static boolean isGliding(Player player) {
        if (!isAtLeast(1, 9))
            return false;
        try {
            java.lang.reflect.Method method = player.getClass().getMethod("isGliding");
            return (boolean) method.invoke(player);
        } catch (Exception e) {
            return false;
        }
    }
}
