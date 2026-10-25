package net.enelson.sopdungeons.listener;

import net.enelson.sopdungeons.config.DungeonRepository;
import net.enelson.sopdungeons.config.Messages;
import net.enelson.sopdungeons.model.Cuboid;
import net.enelson.sopdungeons.model.DungeonDefinition;
import net.enelson.sopdungeons.service.DungeonManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class EntranceListener implements Listener {
    private final JavaPlugin plugin;
    private final DungeonRepository repository;
    private final DungeonManager manager;
    private final Messages messages;
    private final TransitionListener transitions;
    private final Set<UUID> pending = new HashSet<UUID>();

    public EntranceListener(JavaPlugin plugin, DungeonRepository repository, DungeonManager manager,
                            Messages messages, TransitionListener transitions) {
        this.plugin = plugin;
        this.repository = repository;
        this.manager = manager;
        this.messages = messages;
        this.transitions = transitions;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) return;
        final Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!player.hasPermission("sopdungeons.use") || pending.contains(playerId)
                || manager.getInternalSessionByPlayer(playerId) != null) return;

        for (DungeonDefinition definition : repository.getAll()) {
            Cuboid trigger = definition.getEntranceTrigger();
            if (trigger == null || !trigger.contains(to, null) || trigger.contains(from, null)) continue;
            enter(player, definition.getId());
            return;
        }
    }

    private void enter(final Player player, final String dungeonId) {
        final UUID playerId = player.getUniqueId();
        if (!pending.add(playerId)) return;
        if (!transitions.fadeThen(player, new Runnable() {
            @Override
            public void run() {
                start(player, dungeonId);
            }
        })) pending.remove(playerId);
    }

    private void start(final Player player, final String dungeonId) {
        final UUID playerId = player.getUniqueId();
        manager.start(dungeonId, Collections.singleton(playerId)).whenComplete((session, error) ->
                runSync(new Runnable() {
                    @Override
                    public void run() {
                        pending.remove(playerId);
                        if (!player.isOnline()) return;
                        if (error == null) {
                            transitions.finishArrival(player);
                            messages.send(player, "entered", "id", dungeonId);
                        } else {
                            player.sendMessage(Messages.color("&8[&6SopDungeons&8] &c" + rootMessage(error)));
                        }
                    }
                }));
    }

    private void runSync(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, action);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
