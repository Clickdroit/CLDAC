package fr.cldac.anticheat.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import fr.cldac.anticheat.CLDAC;
import fr.cldac.anticheat.check.Check;
import fr.cldac.anticheat.check.CheckType;
import fr.cldac.anticheat.check.impl.combat.AutoClicker;
import fr.cldac.anticheat.check.impl.combat.KillAuraA;
import fr.cldac.anticheat.check.impl.combat.Reach;
import fr.cldac.anticheat.check.impl.player.BadPackets;
import fr.cldac.anticheat.check.impl.player.NameTags;
import fr.cldac.anticheat.check.impl.player.Scaffold;
import fr.cldac.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * PacketEvents listener intercepting network packets for anti-cheat analysis.
 * Handles movement, interaction, and other client->server packets.
 */
public class PacketListener extends PacketListenerAbstract {

    private final CLDAC plugin;

    public PacketListener(CLDAC plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;
        if (data.isExempt()) return;

        UUID uuid = player.getUniqueId();

        // Run packet rate check on every packet
        plugin.getCheckManager().runPacketChecks(uuid);

        // === Movement packets ===
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);

            double x = wrapper.getLocation().getX();
            double y = wrapper.getLocation().getY();
            double z = wrapper.getLocation().getZ();
            float yaw = wrapper.getLocation().getYaw();
            float pitch = wrapper.getLocation().getPitch();
            boolean onGround = wrapper.isOnGround();

            // BadPackets check (validate data before processing)
            BadPackets badPackets = plugin.getCheckManager().getCheck(uuid, BadPackets.class);
            if (badPackets != null && badPackets.isEnabled()) {
                badPackets.onPacketReceive(x, y, z, yaw, pitch, onGround);
            }

            // Update player data with movement
            if (wrapper.hasPositionChanged() || wrapper.hasRotationChanged()) {
                data.handleMovement(x, y, z, yaw, pitch, onGround);

                // Run all movement checks
                plugin.getCheckManager().runMovementChecks(uuid);

                // Run NameTags check periodically (every ~10 ticks to save performance)
                if (data.getAirTicks() % 10 == 0 || data.getGroundTicks() % 10 == 0) {
                    NameTags nameTags = plugin.getCheckManager().getCheck(uuid, NameTags.class);
                    if (nameTags != null && nameTags.isEnabled()) {
                        nameTags.onRotation();
                    }
                }
            }
        }

        // === Interaction packets (attacks) ===
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            if (wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                int entityId = wrapper.getEntityId();

                // Find the target entity on the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Entity target = null;
                    for (Entity entity : player.getWorld().getEntities()) {
                        if (entity.getEntityId() == entityId) {
                            target = entity;
                            break;
                        }
                    }
                    if (target == null) return;

                    // AutoClicker
                    AutoClicker autoClicker = plugin.getCheckManager().getCheck(uuid, AutoClicker.class);
                    if (autoClicker != null && autoClicker.isEnabled()) {
                        autoClicker.onClick();
                    }

                    // KillAura
                    KillAuraA killAura = plugin.getCheckManager().getCheck(uuid, KillAuraA.class);
                    if (killAura != null && killAura.isEnabled()) {
                        killAura.onAttack(player, target);
                    }

                    // Reach
                    Reach reach = plugin.getCheckManager().getCheck(uuid, Reach.class);
                    if (reach != null && reach.isEnabled()) {
                        reach.onAttack(player, target);
                    }

                    data.handleClick();
                });
            }
        }

        // === Block placement ===
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            Scaffold scaffold = plugin.getCheckManager().getCheck(uuid, Scaffold.class);
            if (scaffold != null && scaffold.isEnabled()) {
                scaffold.onBlockPlace(null); // Location resolved from Bukkit event
            }
        }

        // === Animation (arm swing / attack) ===
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            // Additional click tracking for autoclicker
            AutoClicker autoClicker = plugin.getCheckManager().getCheck(uuid, AutoClicker.class);
            if (autoClicker != null && autoClicker.isEnabled()) {
                // Only count if recent attack (within 50ms) to avoid false counting
                // The actual click is handled in INTERACT_ENTITY
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;

        // === Server -> Client: Velocity (knockback) ===
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
            if (wrapper.getEntityId() == player.getEntityId()) {
                double vx = wrapper.getVelocity().getX();
                double vy = wrapper.getVelocity().getY();
                double vz = wrapper.getVelocity().getZ();
                data.setLastVelocity(vx, vy, vz);
            }
        }

        // === Server -> Client: Position (teleport) ===
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
            data.setLastTeleportTimestamp(System.currentTimeMillis());
        }
    }
}
