package net.enelson.sopdungeons.listener;

import net.enelson.sopdungeons.config.Messages;
import net.enelson.sopdungeons.model.DungeonFloor;
import net.enelson.sopdungeons.model.DungeonSession;
import net.enelson.sopdungeons.service.DungeonManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TransitionListener implements Listener {
    private final DungeonManager manager;
    private final Messages messages;
    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, Protection> protections = new HashMap<UUID, Protection>();
    private final Map<UUID, Boolean> transitions = new HashMap<UUID, Boolean>();
    private double portalAnimation;

    public TransitionListener(JavaPlugin plugin, DungeonManager manager, Messages messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        startExitPortalEffects();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        updateProtection(event.getPlayer(), to);
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) return;
        Player player = event.getPlayer();
        if (transitions.containsKey(player.getUniqueId())) return;
        DungeonSession session = manager.getInternalSessionByPlayer(player.getUniqueId());
        if (session == null || !session.getWorldName().equals(to.getWorld().getName())) return;
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 1000L) return;
        DungeonFloor floor = manager.findFloor(session, to);
        if (floor == null) return;

        if (floor.getDownTrigger() != null && floor.getDownTrigger().contains(to, session.getWorldName())) {
            DungeonFloor next = session.getDefinition().getFloor(floor.getNumber() + 1);
            if (next == null) {
                transition(player, null, false, new Runnable() {
                    @Override
                    public void run() {
                        manager.leave(player.getUniqueId());
                        messages.send(player, "left");
                    }
                });
                return;
            }
            if (!session.isFloorUnlocked(next.getNumber())) {
                messages.send(player, "floor-locked");
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                return;
            }
            net.enelson.sopdungeons.model.Point arrival = next.getArrivalFromUp() != null
                    ? next.getArrivalFromUp() : next.getArrivalFromDown();
            if (arrival == null) {
                messages.send(player, "missing-arrival", "id", session.getDungeonId());
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                return;
            }
            debug(player, floor.getNumber(), "DOWN", next.getNumber());
            transition(player, arrival.toLocation(session.getWorldName()), true, null);
            return;
        }

        if (floor.getUpTrigger() != null && floor.getUpTrigger().contains(to, session.getWorldName())) {
            if (floor.getNumber() <= 1) {
                transition(player, null, false, new Runnable() {
                    @Override
                    public void run() {
                        manager.leave(player.getUniqueId());
                        messages.send(player, "left");
                    }
                });
                return;
            }
            DungeonFloor previous = session.getDefinition().getFloor(floor.getNumber() - 1);
            if (previous != null) {
                net.enelson.sopdungeons.model.Point arrival = previous.getArrivalFromDown() != null
                        ? previous.getArrivalFromDown() : previous.getArrivalFromUp();
                if (arrival == null) {
                    messages.send(player, "missing-arrival", "id", session.getDungeonId());
                    cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                    return;
                }
                debug(player, floor.getNumber(), "UP", previous.getNumber());
                transition(player, arrival.toLocation(session.getWorldName()), true, null);
            }
        }
    }

    private boolean transition(final Player player, final Location location, final boolean protect,
                               final Runnable insteadOfTeleport) {
        if (location == null && insteadOfTeleport == null) return false;
        final UUID playerId = player.getUniqueId();
        if (transitions.put(playerId, Boolean.TRUE) != null) return false;
        final int beforeTicks = Math.max(0, plugin.getConfig().getInt("transitions.fade-before-ticks", 10));
        final int afterTicks = Math.max(0, plugin.getConfig().getInt("transitions.fade-after-ticks", 20));
        final boolean fadeEnabled = plugin.getConfig().getBoolean("transitions.fade-enabled", true);
        if (fadeEnabled && beforeTicks + afterTicks > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    beforeTicks + afterTicks, 0, false, false));
        }
        Runnable complete = new Runnable() {
            @Override
            public void run() {
                transitions.remove(playerId);
                if (!player.isOnline()) return;
                cooldowns.put(playerId, System.currentTimeMillis());
                if (insteadOfTeleport != null) {
                    insteadOfTeleport.run();
                    return;
                }
                if (protect) grantProtection(player, location);
                player.teleport(location);
            }
        };
        int delay = fadeEnabled ? beforeTicks : 0;
        if (delay == 0) complete.run();
        else Bukkit.getScheduler().runTaskLater(plugin, complete, delay);
        return true;
    }

    public boolean fadeThen(Player player, Runnable action) {
        return transition(player, null, false, action);
    }

    public void finishArrival(Player player) {
        if (!player.isOnline()) return;
        int afterTicks = Math.max(0, plugin.getConfig().getInt("transitions.fade-after-ticks", 20));
        if (plugin.getConfig().getBoolean("transitions.fade-enabled", true) && afterTicks > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    afterTicks, 0, false, false));
        }
        grantProtection(player, player.getLocation());
    }

    private void startExitPortalEffects() {
        long interval = Math.max(1L, plugin.getConfig().getLong("exit-portal.particle-interval-ticks", 4L));
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("exit-portal.particles-enabled", true)) return;
                portalAnimation += 0.22D;
                for (DungeonSession session : manager.getSessions()) showExitPortal(session);
            }
        }, interval, interval);
    }

    private void showExitPortal(DungeonSession session) {
        DungeonFloor floor = session.getDefinition().getFloor(session.getDefinition().getLastFloorNumber());
        if (floor == null || floor.getDownTrigger() == null) return;
        World world = Bukkit.getWorld(session.getWorldName());
        if (world == null) return;
        double centerX = (floor.getDownTrigger().getMinX() + floor.getDownTrigger().getMaxX() + 1.0D) / 2.0D;
        double centerY = floor.getDownTrigger().getMinY() + 0.15D;
        double centerZ = (floor.getDownTrigger().getMinZ() + floor.getDownTrigger().getMaxZ() + 1.0D) / 2.0D;
        double radius = Math.max(0.5D, plugin.getConfig().getDouble("exit-portal.radius", 1.6D));
        double viewDistance = Math.max(8.0D,
                plugin.getConfig().getDouble("exit-portal.view-distance", 48.0D));
        Location center = new Location(world, centerX, centerY, centerZ);
        for (UUID playerId : session.getParticipants()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.getWorld() != world
                    || player.getLocation().distanceSquared(center) > viewDistance * viewDistance) continue;
            for (int point = 0; point < 16; point++) {
                double angle = portalAnimation + Math.PI * 2.0D * point / 16.0D;
                player.spawnParticle(Particle.END_ROD, centerX + Math.cos(angle) * radius, centerY,
                        centerZ + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            player.spawnParticle(Particle.PORTAL, centerX, centerY + 0.35D, centerZ,
                    8, radius * 0.45D, 0.25D, radius * 0.45D, 0.02D);
        }
    }

    private void grantProtection(final Player player, Location arrival) {
        final UUID playerId = player.getUniqueId();
        long durationTicks = Math.max(0L,
                plugin.getConfig().getLong("transitions.invulnerability.duration-ticks", 100L));
        if (durationTicks == 0L) return;
        long now = System.currentTimeMillis();
        long graceTicks = Math.max(0L,
                plugin.getConfig().getLong("transitions.invulnerability.movement-grace-ticks", 10L));
        protections.put(playerId, new Protection(arrival, now + durationTicks * 50L,
                now + graceTicks * 50L));
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                Protection protection = protections.get(playerId);
                if (protection != null && protection.expiresAt <= System.currentTimeMillis()) {
                    protections.remove(playerId);
                }
            }
        }, durationTicks + 1L);
    }

    private void updateProtection(Player player, Location to) {
        Protection protection = protections.get(player.getUniqueId());
        if (protection == null) return;
        long now = System.currentTimeMillis();
        if (now >= protection.expiresAt) {
            protections.remove(player.getUniqueId());
            return;
        }
        if (!plugin.getConfig().getBoolean("transitions.invulnerability.until-first-movement", true)
                || now < protection.movementAllowedAt || to == null
                || !to.getWorld().equals(protection.arrival.getWorld())) return;
        double x = to.getX() - protection.arrival.getX();
        double z = to.getZ() - protection.arrival.getZ();
        if (x * x + z * z > 0.01D) protections.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Protection protection = protections.get(player.getUniqueId());
        if (protection == null) return;
        if (System.currentTimeMillis() >= protection.expiresAt) {
            protections.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        protections.remove(playerId);
        transitions.remove(playerId);
        cooldowns.remove(playerId);
    }

    private void debug(Player player, int from, String direction, int to) {
        if (plugin.getConfig().getBoolean("debug-transitions", false)) {
            player.sendMessage(Messages.color("&8[SopDungeons debug] &7floor &f" + from
                    + " &7trigger &f" + direction + " &7-> floor &f" + to));
        }
    }

    private static final class Protection {
        private final Location arrival;
        private final long expiresAt;
        private final long movementAllowedAt;

        private Protection(Location arrival, long expiresAt, long movementAllowedAt) {
            this.arrival = arrival.clone();
            this.expiresAt = expiresAt;
            this.movementAllowedAt = movementAllowedAt;
        }
    }
}
