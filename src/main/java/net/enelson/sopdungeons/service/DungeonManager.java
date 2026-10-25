package net.enelson.sopdungeons.service;

import net.enelson.sopdungeons.api.DungeonSessionView;
import net.enelson.sopdungeons.api.SopDungeonsApi;
import net.enelson.sopdungeons.config.DungeonRepository;
import net.enelson.sopdungeons.event.DungeonFloorUnlockEvent;
import net.enelson.sopdungeons.event.DungeonSessionStartEvent;
import net.enelson.sopdungeons.event.DungeonSessionEndEvent;
import net.enelson.sopdungeons.event.DungeonPlayerEnterEvent;
import net.enelson.sopdungeons.event.DungeonPlayerLeaveEvent;
import net.enelson.sopdungeons.model.DungeonDefinition;
import net.enelson.sopdungeons.model.DungeonFloor;
import net.enelson.sopdungeons.model.DungeonSession;
import net.enelson.sopdungeons.model.DungeonType;
import net.enelson.sopdungeons.model.GenerationMode;
import net.enelson.sopdungeons.model.Point;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DungeonManager implements SopDungeonsApi {
    private final JavaPlugin plugin;
    private final DungeonRepository repository;
    private final WorldInstanceService worlds;
    private final GeneratedDungeonBuilder generator;
    private final CatacombDungeonBuilder catacombGenerator;
    private final Map<UUID, DungeonSession> sessions = new HashMap<UUID, DungeonSession>();
    private final Map<UUID, UUID> playerSessions = new HashMap<UUID, UUID>();
    private final Map<String, UUID> staticSessions = new HashMap<String, UUID>();
    private final Map<String, CompletableFuture<Void>> preparations = new HashMap<String, CompletableFuture<Void>>();

    public DungeonManager(JavaPlugin plugin, DungeonRepository repository, WorldInstanceService worlds,
                          GeneratedDungeonBuilder generator, CatacombDungeonBuilder catacombGenerator) {
        this.plugin = plugin;
        this.repository = repository;
        this.worlds = worlds;
        this.generator = generator;
        this.catacombGenerator = catacombGenerator;
    }

    @Override
    public CompletableFuture<DungeonSessionView> start(String dungeonId, final Collection<UUID> players) {
        if (!Bukkit.isPrimaryThread()) {
            final CompletableFuture<DungeonSessionView> scheduled = new CompletableFuture<DungeonSessionView>();
            final Collection<UUID> playerCopy = players == null ? Collections.<UUID>emptyList()
                    : new ArrayList<UUID>(players);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    start(dungeonId, playerCopy).whenComplete((session, error) -> complete(scheduled, session, error));
                }
            });
            return scheduled;
        }
        final DungeonDefinition definition = repository.get(dungeonId);
        if (definition == null) return failed(new IllegalArgumentException("Unknown dungeon: " + dungeonId));
        String problem = validate(definition);
        if (problem != null) return failed(new IllegalStateException(problem));
        DungeonSession active = findActiveSession(definition.getId(), players);
        if (active != null) return CompletableFuture.completedFuture((DungeonSessionView) active);

        if (definition.getType() == DungeonType.STATIC) {
            DungeonSession existing = staticSessions.containsKey(definition.getId())
                    ? sessions.get(staticSessions.get(definition.getId())) : null;
            if (existing == null) {
                existing = new DungeonSession(UUID.randomUUID(), definition, definition.getWorld(), false);
                sessions.put(existing.getId(), existing);
                staticSessions.put(definition.getId(), existing.getId());
                Bukkit.getPluginManager().callEvent(new DungeonSessionStartEvent(existing));
            }
            addPlayers(existing, players);
            return CompletableFuture.completedFuture((DungeonSessionView) existing);
        }

        if (definition.getType() == DungeonType.GENERATED) {
            if (definition.hasGeneratedLayout() && Bukkit.getWorld(definition.getWorld()) != null) {
                DungeonSession existing = staticSessions.containsKey(definition.getId())
                        ? sessions.get(staticSessions.get(definition.getId())) : null;
                if (existing == null) {
                    existing = new DungeonSession(UUID.randomUUID(), definition, definition.getWorld(), false);
                    sessions.put(existing.getId(), existing);
                    staticSessions.put(definition.getId(), existing.getId());
                    Bukkit.getPluginManager().callEvent(new DungeonSessionStartEvent(existing));
                }
                addPlayers(existing, players);
                return CompletableFuture.completedFuture((DungeonSessionView) existing);
            }
            final CompletableFuture<DungeonSessionView> waiting = new CompletableFuture<DungeonSessionView>();
            prepare(dungeonId).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (error != null) waiting.completeExceptionally(error);
                    else start(dungeonId, players).whenComplete((session, startError) ->
                            complete(waiting, session, startError));
                }
            }));
            return waiting;
        }

        final CompletableFuture<DungeonSessionView> result = new CompletableFuture<DungeonSessionView>();
        worlds.create(definition).whenComplete((world, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    try {
                        final UUID sessionId = UUID.randomUUID();
                        finishTemporaryStart(sessionId, definition, world, players, result);
                    } catch (Exception exception) {
                        worlds.remove(world.getName());
                        result.completeExceptionally(exception);
                    }
                }
            });
        });
        return result;
    }

    public CompletableFuture<Void> prepare(final String dungeonId) {
        if (!Bukkit.isPrimaryThread()) {
            final CompletableFuture<Void> scheduled = new CompletableFuture<Void>();
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    prepare(dungeonId).whenComplete((ignored, error) -> complete(scheduled, ignored, error));
                }
            });
            return scheduled;
        }
        DungeonDefinition definition = repository.get(dungeonId);
        if (definition == null) return failed(new IllegalArgumentException("Unknown dungeon: " + dungeonId));
        if (definition.getType() != DungeonType.GENERATED) {
            return failed(new IllegalArgumentException("Only GENERATED dungeons can be prepared"));
        }
        CompletableFuture<Void> running = preparations.get(dungeonId);
        if (running != null) return running;

        final CompletableFuture<Void> result = new CompletableFuture<Void>();
        preparations.put(dungeonId, result);
        if (definition.hasGeneratedLayout()) {
            worlds.loadGenerated(definition.getWorld()).whenComplete((world, error) -> {
                preparations.remove(dungeonId);
                if (error == null) result.complete(null);
                else result.completeExceptionally(error);
            });
            return result;
        }
        worlds.create(definition).whenComplete((world, worldError) -> {
            if (worldError != null) {
                preparations.remove(dungeonId);
                plugin.getLogger().warning("Cannot create world for dungeon " + dungeonId + ": "
                        + rootMessage(worldError));
                result.completeExceptionally(worldError);
                return;
            }
            final UUID sessionId = UUID.randomUUID();
            buildGenerated(sessionId, definition, world).whenComplete((runtime, buildError) -> {
                preparations.remove(dungeonId);
                if (buildError != null) {
                    worlds.remove(world.getName());
                    plugin.getLogger().warning("Cannot prepare generated dungeon " + dungeonId + ": "
                            + rootMessage(buildError));
                    result.completeExceptionally(buildError);
                    return;
                }
                try {
                    definition.applyGeneratedLayout(runtime);
                    repository.save();
                    plugin.getLogger().info("Generated dungeon " + dungeonId + " is ready in persistent world "
                            + world.getName() + ".");
                    result.complete(null);
                } catch (Exception saveError) {
                    worlds.remove(world.getName());
                    definition.clearGeneratedLayout();
                    result.completeExceptionally(saveError);
                }
            });
        });
        return result;
    }

    public boolean isPrepared(String dungeonId) {
        DungeonDefinition definition = repository.get(dungeonId);
        return definition != null && definition.hasGeneratedLayout()
                && Bukkit.getWorld(definition.getWorld()) != null;
    }

    public boolean isPreparing(String dungeonId) {
        return preparations.containsKey(dungeonId);
    }

    public boolean hasReusableSession(String dungeonId) {
        DungeonDefinition definition = repository.get(dungeonId);
        return definition != null && definition.hasGeneratedLayout();
    }

    private CompletableFuture<DungeonDefinition> buildGenerated(UUID sessionId,
                                                                  DungeonDefinition definition, World world) {
        return definition.getGenerationMode() == GenerationMode.CATACOMBS
                ? catacombGenerator.build(sessionId, definition, world)
                : generator.build(sessionId, definition, world);
    }

    public void prepareAll(Collection<DungeonDefinition> definitions) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (DungeonDefinition definition : definitions) {
            if (definition.getType() != DungeonType.GENERATED) continue;
            final String id = definition.getId();
            chain = chain.thenCompose(ignored -> prepare(id)).exceptionally(error -> {
                plugin.getLogger().warning("Cannot prepare generated dungeon " + id + ": "
                        + rootMessage(error));
                return null;
            });
        }
    }

    public void invalidatePrepared(String dungeonId) {
        requirePrimaryThread("invalidatePrepared");
    }

    public void invalidateAllPrepared() {
        requirePrimaryThread("invalidateAllPrepared");
    }

    public CompletableFuture<Boolean> delete(DungeonDefinition definition) {
        requirePrimaryThread("delete");
        List<CompletableFuture<Boolean>> stops = new ArrayList<CompletableFuture<Boolean>>();
        for (DungeonSession session : new ArrayList<DungeonSession>(sessions.values())) {
            if (!session.getDungeonId().equals(definition.getId())) continue;
            if (!evacuateToServerSpawn(session)) {
                return CompletableFuture.completedFuture(false);
            }
            stops.add(stop(session.getId()));
        }
        final String generatedWorld = definition.hasGeneratedLayout() ? definition.getWorld() : null;
        CompletableFuture<Void> stopped = CompletableFuture.allOf(
                stops.toArray(new CompletableFuture<?>[stops.size()]));
        return stopped.thenCompose(ignored -> generatedWorld == null
                ? CompletableFuture.completedFuture(true) : worlds.remove(generatedWorld));
    }

    private boolean evacuateToServerSpawn(DungeonSession session) {
        World destination = null;
        for (World world : Bukkit.getWorlds()) {
            if (!world.getName().equals(session.getWorldName())) {
                destination = world;
                break;
            }
        }
        if (destination == null) return session.getParticipants().isEmpty();
        Location spawn = destination.getSpawnLocation();
        for (UUID playerId : new ArrayList<UUID>(session.getParticipants())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && !player.teleport(spawn)) return false;
            leaveFromSession(session, playerId, false);
        }
        return true;
    }

    private void finishTemporaryStart(UUID sessionId, DungeonDefinition definition, World world,
                                      Collection<UUID> players, CompletableFuture<DungeonSessionView> result) {
        DungeonSession session = new DungeonSession(sessionId, definition, world.getName(), true);
        sessions.put(session.getId(), session);
        addPlayers(session, players);
        Bukkit.getPluginManager().callEvent(new DungeonSessionStartEvent(session));
        result.complete(session);
    }

    private String validate(DungeonDefinition definition) {
        if (definition.getType() != DungeonType.GENERATED && definition.getEntrance() == null) return "Dungeon has no entrance";
        if (definition.getType() == DungeonType.STATIC) {
            if (definition.getWorld() == null || Bukkit.getWorld(definition.getWorld()) == null) return "Static world is not loaded";
        } else if (definition.getType() == DungeonType.TEMPLATE && (definition.getTemplate() == null || definition.getTemplate().isEmpty())) {
            return "Dungeon has no template";
        }
        return null;
    }

    private void addPlayers(DungeonSession session, Collection<UUID> players) {
        for (UUID playerId : players == null ? Collections.<UUID>emptyList() : players) {
            leave(playerId);
            session.addParticipant(playerId);
            playerSessions.put(playerId, session.getId());
            Player player = Bukkit.getPlayer(playerId);
            Location entrance = session.getDefinition().getEntrance().toLocation(session.getWorldName());
            if (player != null && entrance != null) player.teleport(entrance);
            if (player != null) Bukkit.getPluginManager().callEvent(new DungeonPlayerEnterEvent(session, player));
        }
    }

    private DungeonSession findActiveSession(String dungeonId, Collection<UUID> players) {
        DungeonSession result = null;
        if (players == null || players.isEmpty()) return null;
        for (UUID playerId : players) {
            UUID sessionId = playerSessions.get(playerId);
            DungeonSession session = sessionId == null ? null : sessions.get(sessionId);
            if (session == null || !session.getDungeonId().equals(dungeonId)) return null;
            if (result != null && !result.getId().equals(session.getId())) return null;
            result = session;
        }
        return result;
    }

    @Override
    public CompletableFuture<Boolean> stop(UUID sessionId) {
        if (!Bukkit.isPrimaryThread()) {
            final CompletableFuture<Boolean> scheduled = new CompletableFuture<Boolean>();
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    stop(sessionId).whenComplete((removed, error) -> complete(scheduled, removed, error));
                }
            });
            return scheduled;
        }
        DungeonSession session = sessions.remove(sessionId);
        if (session == null) return CompletableFuture.completedFuture(false);
        staticSessions.remove(session.getDungeonId());
        DungeonSessionEndEvent endEvent = new DungeonSessionEndEvent(session);
        for (UUID playerId : new ArrayList<UUID>(session.getParticipants())) leaveFromSession(session, playerId);
        Bukkit.getPluginManager().callEvent(endEvent);
        return session.isTemporary() ? worlds.remove(session.getWorldName()) : CompletableFuture.completedFuture(true);
    }

    @Override
    public boolean leave(UUID playerId) {
        requirePrimaryThread("leave");
        UUID sessionId = playerSessions.get(playerId);
        DungeonSession session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) return false;
        leaveFromSession(session, playerId, true);
        return true;
    }

    public boolean detach(UUID playerId) {
        requirePrimaryThread("detach");
        UUID sessionId = playerSessions.get(playerId);
        DungeonSession session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) return false;
        leaveFromSession(session, playerId, false);
        return true;
    }

    private void leaveFromSession(DungeonSession session, UUID playerId) {
        leaveFromSession(session, playerId, true);
    }

    private void leaveFromSession(DungeonSession session, UUID playerId, boolean teleportToExit) {
        session.removeParticipant(playerId);
        playerSessions.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        if (teleportToExit) {
            Point configuredExit = session.getDefinition().getExit();
            Location exit = configuredExit == null ? null : configuredExit.toLocation(null);
            if (exit == null) {
                World fallback = Bukkit.getWorlds().get(0);
                exit = fallback.getSpawnLocation();
            }
            player.teleport(exit);
        }
        Bukkit.getPluginManager().callEvent(new DungeonPlayerLeaveEvent(session, player));
    }

    public boolean isInside(DungeonSession session, Location location) {
        if (location == null || location.getWorld() == null
                || !session.getWorldName().equals(location.getWorld().getName())) return false;
        boolean hasBounds = false;
        for (DungeonFloor floor : session.getDefinition().getFloors()) {
            if (floor.getBounds() == null) continue;
            hasBounds = true;
            if (floor.getBounds().contains(location, session.getWorldName())) return true;
        }
        return !hasBounds;
    }

    @Override
    public boolean unlockNextFloor(UUID sessionId, int completedFloor) {
        requirePrimaryThread("unlockNextFloor");
        return unlockNextFloor(sessionId, completedFloor, null);
    }

    public boolean unlockNextFloor(UUID sessionId, int completedFloor, String bossId) {
        requirePrimaryThread("unlockNextFloor");
        DungeonSession session = sessions.get(sessionId);
        if (session == null) return false;
        int next = completedFloor + 1;
        if (session.getDefinition().getFloor(next) == null || !session.unlockFloor(next)) return false;
        session.unlockFollowingUngatedFloors(next);
        Bukkit.getPluginManager().callEvent(new DungeonFloorUnlockEvent(session, completedFloor, next, bossId));
        return true;
    }

    public DungeonSession findByWorld(String worldName) {
        for (DungeonSession session : sessions.values()) if (session.getWorldName().equals(worldName)) return session;
        return null;
    }

    public DungeonFloor findFloor(DungeonSession session, Location location) {
        for (DungeonFloor floor : session.getDefinition().getFloors()) {
            if (floor.getBounds() != null && floor.getBounds().contains(location, session.getWorldName())) return floor;
        }
        return null;
    }

    @Override
    public Optional<DungeonSessionView> getSession(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId)).map(value -> (DungeonSessionView) value);
    }

    public DungeonSession getInternalSessionByPlayer(UUID playerId) {
        UUID id = playerSessions.get(playerId);
        return id == null ? null : sessions.get(id);
    }

    @Override
    public Optional<DungeonSessionView> getSessionByPlayer(UUID playerId) {
        return Optional.ofNullable(getInternalSessionByPlayer(playerId)).map(value -> (DungeonSessionView) value);
    }

    public Collection<DungeonSession> getSessions() { return Collections.unmodifiableCollection(sessions.values()); }

    public void stopAll() {
        for (UUID id : new ArrayList<UUID>(sessions.keySet())) stop(id);
    }

    public void shutdown() {
        for (DungeonSession session : new ArrayList<DungeonSession>(sessions.values())) {
            sessions.remove(session.getId());
            DungeonSessionEndEvent endEvent = new DungeonSessionEndEvent(session);
            for (UUID playerId : new ArrayList<UUID>(session.getParticipants())) leaveFromSession(session, playerId);
            if (session.isTemporary()) worlds.removeImmediately(session.getWorldName());
            Bukkit.getPluginManager().callEvent(endEvent);
        }
        staticSessions.clear();
        playerSessions.clear();
        preparations.clear();
    }

    private static <T> CompletableFuture<T> failed(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(throwable);
        return future;
    }

    private static <T> void complete(CompletableFuture<T> future, T value, Throwable error) {
        if (error == null) future.complete(value); else future.completeExceptionally(error);
    }

    private void requirePrimaryThread(String operation) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException(operation + " must be called on the server thread");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

}
