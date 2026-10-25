package net.enelson.sopdungeons.integration;

import net.enelson.sopdungeons.config.Messages;
import net.enelson.sopdungeons.model.DungeonFloor;
import net.enelson.sopdungeons.model.DungeonSession;
import net.enelson.sopdungeons.service.DungeonManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class EliteMobsBridge {
    private EliteMobsBridge() {}

    @SuppressWarnings("unchecked")
    public static boolean register(JavaPlugin plugin, final DungeonManager manager, final Messages messages) {
        if (Bukkit.getPluginManager().getPlugin("EliteMobs") == null) return false;
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName("com.magmaguy.elitemobs.api.EliteMobDeathEvent");
            Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {}, EventPriority.MONITOR,
                    new EventExecutor() {
                        @Override
                        public void execute(Listener listener, Event event) throws EventException {
                            handle(event, manager, messages);
                        }
                    }, plugin, true);
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("EliteMobs integration could not be enabled: " + exception.getMessage());
            return false;
        }
    }

    private static void handle(Event event, DungeonManager manager, Messages messages) throws EventException {
        try {
            Object elite = event.getClass().getMethod("getEliteEntity").invoke(event);
            Method getLocation = elite.getClass().getMethod("getLocation");
            Location location = (Location) getLocation.invoke(elite);
            if (location == null || location.getWorld() == null) return;
            DungeonSession session = manager.findByWorld(location.getWorld().getName());
            if (session == null || !session.isTemporary()) return;
            DungeonFloor floor = manager.findFloor(session, location);
            if (floor == null || !floor.requiresBoss()) return;
            String bossId = readBossId(elite);
            if (!sameId(floor.getBossId(), bossId)) return;
            if (manager.unlockNextFloor(session.getId(), floor.getNumber(), bossId)) {
                for (java.util.UUID id : session.getParticipants()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) messages.send(player, "boss-unlocked");
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new EventException(exception);
        }
    }

    private static String readBossId(Object elite) throws ReflectiveOperationException {
        try {
            Object fields = elite.getClass().getMethod("getCustomBossesConfigFields").invoke(elite);
            return fields == null ? null : String.valueOf(fields.getClass().getMethod("getFilename").invoke(fields));
        } catch (NoSuchMethodException exception) {
            return String.valueOf(elite.getClass().getMethod("getName").invoke(elite));
        }
    }

    private static boolean sameId(String configured, String actual) {
        if (configured == null || actual == null) return false;
        return trimExtension(configured).equalsIgnoreCase(trimExtension(actual));
    }

    private static String trimExtension(String value) {
        return value.toLowerCase().endsWith(".yml") ? value.substring(0, value.length() - 4) : value;
    }
}
