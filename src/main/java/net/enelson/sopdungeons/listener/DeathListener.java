package net.enelson.sopdungeons.listener;

import net.enelson.sopdungeons.model.DeathMode;
import net.enelson.sopdungeons.model.DungeonFloor;
import net.enelson.sopdungeons.model.DungeonSession;
import net.enelson.sopdungeons.model.Point;
import net.enelson.sopdungeons.service.DungeonManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DeathListener implements Listener {
    private final JavaPlugin plugin;
    private final DungeonManager manager;
    private final Map<UUID, Location> respawns = new HashMap<UUID, Location>();

    public DeathListener(JavaPlugin plugin, DungeonManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DungeonSession session = manager.getInternalSessionByPlayer(player.getUniqueId());
        if (session == null) return;
        DeathMode mode;
        try {
            mode = DeathMode.valueOf(plugin.getConfig().getString("death-mode", "DUNGEON_ENTRANCE").toUpperCase());
        } catch (IllegalArgumentException exception) {
            mode = DeathMode.DUNGEON_ENTRANCE;
        }
        if (mode == DeathMode.SERVER_RESPAWN) {
            manager.leave(player.getUniqueId());
            return;
        }
        Point point = session.getDefinition().getEntrance();
        if (mode == DeathMode.CURRENT_FLOOR) {
            DungeonFloor floor = manager.findFloor(session, player.getLocation());
            if (floor != null && floor.getArrivalFromUp() != null) point = floor.getArrivalFromUp();
        }
        Location target = point == null ? null : point.toLocation(session.getWorldName());
        if (target != null) respawns.put(player.getUniqueId(), target);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location target = respawns.remove(event.getPlayer().getUniqueId());
        if (target != null) event.setRespawnLocation(target);
    }
}
