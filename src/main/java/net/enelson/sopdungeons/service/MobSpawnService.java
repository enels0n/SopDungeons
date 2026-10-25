package net.enelson.sopdungeons.service;

import net.enelson.sopdungeons.config.DungeonRepository;
import net.enelson.sopdungeons.config.Messages;
import net.enelson.sopdungeons.event.DungeonSessionEndEvent;
import net.enelson.sopdungeons.mob.EliteMobsProvider;
import net.enelson.sopdungeons.mob.MobProvider;
import net.enelson.sopdungeons.mob.VanillaMobProvider;
import net.enelson.sopdungeons.model.DungeonDefinition;
import net.enelson.sopdungeons.model.DungeonFloor;
import net.enelson.sopdungeons.model.DungeonSession;
import net.enelson.sopdungeons.model.DungeonType;
import net.enelson.sopdungeons.model.MobSpawnDefinition;
import net.enelson.sopdungeons.model.RespawnMode;
import net.enelson.sopdungeons.model.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class MobSpawnService implements Listener {
    private static final int ENTITY_SPAWN_ATTEMPTS = 8;
    private static final double BOUNDING_BOX_EPSILON = 1.0E-7;

    private final JavaPlugin plugin;
    private final DungeonManager manager;
    private final DungeonRepository repository;
    private final Messages messages;
    private final Map<String, MobProvider> providers = new HashMap<String, MobProvider>();
    private final Map<String, SpawnState> states = new HashMap<String, SpawnState>();
    private final Map<UUID, BossReference> bossEntities = new HashMap<UUID, BossReference>();
    private final Random random = new Random();
    private final NamespacedKey managedMobKey;
    private final boolean distanceDespawnEnabled;
    private final boolean despawnBosses;
    private final double despawnDistanceSquared;
    private final long despawnCheckIntervalMillis;

    public MobSpawnService(JavaPlugin plugin, DungeonManager manager, DungeonRepository repository,
                           Messages messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.repository = repository;
        this.messages = messages;
        this.managedMobKey = new NamespacedKey(plugin, "managed_dungeon_mob");
        this.distanceDespawnEnabled = plugin.getConfig().getBoolean("mobs.despawn.enabled", true);
        this.despawnBosses = plugin.getConfig().getBoolean("mobs.despawn.despawn-bosses", false);
        double despawnDistance = Math.max(1.0,
                plugin.getConfig().getDouble("mobs.despawn.distance", 48.0));
        this.despawnDistanceSquared = despawnDistance * despawnDistance;
        this.despawnCheckIntervalMillis = Math.max(1L,
                plugin.getConfig().getLong("mobs.despawn.check-interval-ticks", 20L)) * 50L;
        registerProvider(new EliteMobsProvider());
        registerProvider(new VanillaMobProvider());
        cleanupTaggedEntities();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() { tick(); }
        }, 1L, 1L);
    }

    public void registerProvider(MobProvider provider) {
        providers.put(provider.getId().toUpperCase(), provider);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Set<String> validKeys = new HashSet<String>();
        for (DungeonSession session : manager.getSessions()) {
            Map<Integer, List<Player>> activePlayers = activePlayers(session);
            for (DungeonFloor floor : session.getDefinition().getFloors()) {
                for (MobSpawnDefinition spawn : floor.getMobSpawns()) {
                    String stateKey = key(session.getId(), floor.getNumber(), spawn.getId());
                    validKeys.add(stateKey);
                    List<Player> players = activePlayers.get(floor.getNumber());
                    if (players == null) players = java.util.Collections.emptyList();
                    process(session, floor, spawn, now, players);
                }
            }
        }
        removeStaleStates(validKeys);
    }

    private Map<Integer, List<Player>> activePlayers(DungeonSession session) {
        Map<Integer, List<Player>> result = new HashMap<Integer, List<Player>>();
        for (UUID playerId : session.getParticipants()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.getWorld() == null
                    || !session.getWorldName().equals(player.getWorld().getName())) continue;
            DungeonFloor floor = manager.findFloor(session, player.getLocation());
            if (floor != null) {
                List<Player> players = result.get(floor.getNumber());
                if (players == null) {
                    players = new ArrayList<Player>();
                    result.put(floor.getNumber(), players);
                }
                players.add(player);
            }
        }
        return result;
    }

    private void process(DungeonSession session, DungeonFloor floor, MobSpawnDefinition spawn, long now,
                         List<Player> activePlayers) {
        String key = key(session.getId(), floor.getNumber(), spawn.getId());
        SpawnState state = states.get(key);
        if (state == null) {
            state = new SpawnState(session.getId());
            states.put(key, state);
        }
        prune(state);
        if (distanceDespawnEnabled && now >= state.nextDespawnCheckAt) {
            int removed = despawnDistantEntities(state, activePlayers, spawn.isBoss());
            if (removed > 0 && !spawn.isAroundPlayers()) resetAfterDistanceDespawn(state);
            state.nextDespawnCheckAt = now + despawnCheckIntervalMillis;
        }
        if (spawn.isAroundPlayers()) {
            processAroundPlayers(session, floor, spawn, state, activePlayers, now,
                    spawn.getSpawnIntervalTicks() * 50L);
            return;
        }
        if (activePlayers.isEmpty()) return;
        if (!spawn.isBoss() && distanceDespawnEnabled
                && !hasPlayerNearSpawn(session, spawn, activePlayers)) return;
        long spawnIntervalMillis = spawn.getSpawnIntervalTicks() * 50L;
        if (!state.initialized) {
            if (now < state.nextEntitySpawnAt) return;
            int requested = spawnIntervalMillis == 0L ? spawn.getAmount() - state.initialSpawned : 1;
            for (int i = 0; i < requested; i++) {
                if (!spawnOneAtPoint(session, floor, spawn, state)) break;
                state.initialSpawned++;
            }
            if (state.initialSpawned >= spawn.getAmount()) {
                state.initialized = true;
                state.nextSpawnAt = 0L;
                state.nextEntitySpawnAt = 0L;
            } else {
                state.nextEntitySpawnAt = now + spawnIntervalMillis;
            }
            return;
        }
        if (spawn.getRespawnMode() == RespawnMode.NEVER) return;
        if (state.refillRemaining > 0) {
            if (now < state.nextEntitySpawnAt) return;
            int requested = spawnIntervalMillis == 0L ? state.refillRemaining : 1;
            for (int i = 0; i < requested; i++) {
                if (!spawnOneAtPoint(session, floor, spawn, state)) break;
                state.refillRemaining--;
            }
            state.nextEntitySpawnAt = now + spawnIntervalMillis;
            return;
        }
        int missing = spawn.getAmount() - state.entities.size();
        boolean readyForTimer = spawn.getRespawnMode() == RespawnMode.WHEN_CLEARED
                ? state.entities.isEmpty() : missing > 0;
        if (!readyForTimer) {
            state.nextSpawnAt = 0L;
            return;
        }
        if (state.nextSpawnAt == 0L) {
            state.nextSpawnAt = now + spawn.getRespawnDelaySeconds() * 1000L;
            return;
        }
        if (now < state.nextSpawnAt) return;
        state.refillRemaining = spawn.getRespawnMode() == RespawnMode.WHEN_CLEARED ? spawn.getAmount() : missing;
        state.nextSpawnAt = 0L;
        state.nextEntitySpawnAt = now;
    }

    private void processAroundPlayers(DungeonSession session, DungeonFloor floor, MobSpawnDefinition spawn,
                                      SpawnState state, List<Player> players, long now,
                                      long spawnIntervalMillis) {
        Set<UUID> activeIds = new HashSet<UUID>();
        for (Player player : players) activeIds.add(player.getUniqueId());

        Iterator<Map.Entry<UUID, UUID>> ownerIterator = state.owners.entrySet().iterator();
        while (ownerIterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = ownerIterator.next();
            if (activeIds.contains(entry.getValue())) continue;
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity != null) entity.remove();
            state.entities.remove(entry.getKey());
            ownerIterator.remove();
        }
        state.seenPlayers.retainAll(activeIds);
        state.playerTimers.keySet().retainAll(activeIds);

        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            int alive = 0;
            for (UUID ownerId : state.owners.values()) if (playerId.equals(ownerId)) alive++;
            int missing = spawn.getAmount() - alive;
            if (missing <= 0) {
                state.playerTimers.remove(playerId);
                continue;
            }
            if (state.seenPlayers.add(playerId)) {
                int spawned = spawnAroundPlayers(floor, spawn, state, player, players,
                        spawnIntervalMillis == 0L ? missing : 1);
                if (spawned < missing) state.playerTimers.put(playerId, now + spawnIntervalMillis);
                continue;
            }
            Long timer = state.playerTimers.get(playerId);
            if (timer == null) {
                state.playerTimers.put(playerId, now + spawn.getRespawnDelaySeconds() * 1000L);
                continue;
            }
            if (now < timer) continue;
            int spawned = spawnAroundPlayers(floor, spawn, state, player, players,
                    spawnIntervalMillis == 0L ? missing : 1);
            if (spawned < missing) state.playerTimers.put(playerId, now + spawnIntervalMillis);
            else state.playerTimers.remove(playerId);
        }
    }

    private int spawnAroundPlayers(DungeonFloor floor, MobSpawnDefinition definition, SpawnState state,
                                   Player owner, List<Player> players, int amount) {
        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            if (!spawnOneAroundPlayer(floor, definition, state, owner, players)) break;
            spawned++;
        }
        return spawned;
    }

    private boolean spawnOneAroundPlayer(DungeonFloor floor, MobSpawnDefinition definition,
                                         SpawnState state, Player owner, List<Player> players) {
        MobProvider provider = providers.get(definition.getProvider().toUpperCase());
        if (provider == null || !provider.isAvailable()) {
            warnOnce(state, "Mob provider is unavailable: " + definition.getProvider());
            return false;
        }
        LivingEntity entity = spawnAroundPlayer(provider, owner, players, floor, definition, state);
        if (entity == null) return false;
        track(state, entity, owner.getUniqueId());
        return true;
    }

    private LivingEntity spawnAroundPlayer(MobProvider provider, Player owner, List<Player> players,
                                            DungeonFloor floor, MobSpawnDefinition definition, SpawnState state) {
        for (int attempt = 0; attempt < ENTITY_SPAWN_ATTEMPTS; attempt++) {
            Location location = findSafeLocation(owner, players, floor.getBounds(), definition, state);
            if (location == null) break;
            try {
                LivingEntity entity = provider.spawn(definition.getMobId(), location, false);
                if (isCollisionFree(entity)) return entity;
                entity.remove();
            } catch (Exception exception) {
                warnOnce(state, "Cannot spawn " + definition.getMobId() + ": " + rootMessage(exception));
                return null;
            }
        }
        warnOnce(state, "No collision-free spawn location for " + definition.getId()
                + " on floor " + floor.getNumber());
        return null;
    }

    private Location findSafeLocation(Player owner, List<Player> players, Cuboid bounds,
                                      MobSpawnDefinition definition, SpawnState state) {
        if (bounds == null) return null;
        World world = owner.getWorld();
        double minSquared = definition.getMinRadius() * definition.getMinRadius();
        double minRadiusSquared = minSquared;
        double maxSquared = definition.getMaxRadius() * definition.getMaxRadius();
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(minSquared + random.nextDouble() * (maxSquared - minSquared));
            int x = (int) Math.floor(owner.getLocation().getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(owner.getLocation().getZ() + Math.sin(angle) * distance);
            if (x < bounds.getMinX() || x > bounds.getMaxX() || z < bounds.getMinZ() || z > bounds.getMaxZ()) continue;
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            int upperY = Math.min(bounds.getMaxY() - 1, owner.getLocation().getBlockY() + 8);
            int lowerY = Math.max(bounds.getMinY() + 1, owner.getLocation().getBlockY() - 8);
            for (int y = upperY; y >= lowerY; y--) {
                Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                if (tooCloseToPlayers(candidate, players, minRadiusSquared) || tooCloseToMobs(candidate, state)) continue;
                Block below = world.getBlockAt(x, y - 1, z);
                if (below.getType().isSolid() && hasBasicClearance(world, x, y, z)) return candidate;
            }
        }
        return null;
    }

    private boolean hasBasicClearance(World world, int x, int feetY, int z) {
        for (int y = feetY; y <= feetY + 1; y++) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.isPassable() || block.isLiquid()) return false;
        }
        return true;
    }

    private boolean isCollisionFree(LivingEntity entity) {
        BoundingBox entityBox = entity.getBoundingBox();
        World world = entity.getWorld();
        int minX = (int) Math.floor(entityBox.getMinX());
        int minY = (int) Math.floor(entityBox.getMinY());
        int minZ = (int) Math.floor(entityBox.getMinZ());
        int maxX = (int) Math.floor(entityBox.getMaxX() - BOUNDING_BOX_EPSILON);
        int maxY = (int) Math.floor(entityBox.getMaxY() - BOUNDING_BOX_EPSILON);
        int maxZ = (int) Math.floor(entityBox.getMaxZ() - BOUNDING_BOX_EPSILON);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.isLiquid()) return false;
                    if (!block.isPassable() && block.getBoundingBox().overlaps(entityBox)) return false;
                }
            }
        }
        return true;
    }

    private boolean tooCloseToPlayers(Location location, List<Player> players, double minimumSquared) {
        for (Player player : players) {
            if (player.getWorld().equals(location.getWorld())
                    && horizontalDistanceSquared(player.getLocation(), location) < minimumSquared) return true;
        }
        return false;
    }

    private boolean tooCloseToMobs(Location location, SpawnState state) {
        for (UUID entityId : state.entities) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.getWorld().equals(location.getWorld())
                    && entity.getLocation().distanceSquared(location) < 4.0) return true;
        }
        return false;
    }

    private double horizontalDistanceSquared(Location first, Location second) {
        double x = first.getX() - second.getX();
        double z = first.getZ() - second.getZ();
        return x * x + z * z;
    }

    private int despawnDistantEntities(SpawnState state, List<Player> players, boolean boss) {
        if (boss && !despawnBosses) return 0;
        int removed = 0;
        Iterator<UUID> iterator = state.entities.iterator();
        while (iterator.hasNext()) {
            UUID entityId = iterator.next();
            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null || hasNearbyPlayer(entity.getLocation(), players)) continue;
            entity.remove();
            state.owners.remove(entityId);
            bossEntities.remove(entityId);
            iterator.remove();
            removed++;
        }
        return removed;
    }

    private void resetAfterDistanceDespawn(SpawnState state) {
        state.initialized = false;
        state.initialSpawned = state.entities.size();
        state.refillRemaining = 0;
        state.nextSpawnAt = 0L;
        state.nextEntitySpawnAt = 0L;
    }

    private boolean hasPlayerNearSpawn(DungeonSession session, MobSpawnDefinition spawn,
                                       List<Player> players) {
        if (spawn.getLocation() == null) return false;
        Location location = spawn.getLocation().toLocation(session.getWorldName());
        return location != null && hasNearbyPlayer(location, players);
    }

    private boolean hasNearbyPlayer(Location location, List<Player> players) {
        for (Player player : players) {
            if (player.getWorld().equals(location.getWorld())
                    && player.getLocation().distanceSquared(location) <= despawnDistanceSquared) return true;
        }
        return false;
    }

    private boolean spawnOneAtPoint(DungeonSession session, DungeonFloor floor,
                                    MobSpawnDefinition definition, SpawnState state) {
        MobProvider provider = providers.get(definition.getProvider().toUpperCase());
        if (provider == null || !provider.isAvailable()) {
            warnOnce(state, "Mob provider is unavailable: " + definition.getProvider());
            return false;
        }
        Location center = definition.getLocation() == null ? null
                : definition.getLocation().toLocation(session.getWorldName());
        if (center == null) {
            warnOnce(state, "Invalid location for spawn " + definition.getId());
            return false;
        }
        LivingEntity entity = spawnAtPoint(provider, definition, center, state);
        if (entity == null) return false;
        track(state, entity, null);
        if (definition.isBoss()) {
            bossEntities.put(entity.getUniqueId(), new BossReference(session.getId(), floor.getNumber(),
                    definition.getMobId()));
        }
        return true;
    }

    private LivingEntity spawnAtPoint(MobProvider provider, MobSpawnDefinition definition, Location center,
                                      SpawnState state) {
        for (int attempt = 0; attempt < ENTITY_SPAWN_ATTEMPTS; attempt++) {
            try {
                LivingEntity entity = provider.spawn(definition.getMobId(),
                        randomLocation(center, definition.getRadius()), definition.isBoss());
                if (isCollisionFree(entity)) return entity;
                entity.remove();
            } catch (Exception exception) {
                warnOnce(state, "Cannot spawn " + definition.getMobId() + ": " + rootMessage(exception));
                return null;
            }
        }
        warnOnce(state, "No collision-free location in radius for " + definition.getId());
        return null;
    }

    private Location randomLocation(Location center, double radius) {
        if (radius <= 0.0) return center.clone();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        return center.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
    }

    private void prune(SpawnState state) {
        Iterator<UUID> iterator = state.entities.iterator();
        while (iterator.hasNext()) {
            UUID entityId = iterator.next();
            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null || !entity.isValid() || entity.isDead()) {
                state.owners.remove(entityId);
                bossEntities.remove(entityId);
                iterator.remove();
            }
        }
    }

    private void track(SpawnState state, LivingEntity entity, UUID ownerId) {
        entity.getPersistentDataContainer().set(managedMobKey, PersistentDataType.BYTE, (byte) 1);
        state.entities.add(entity.getUniqueId());
        if (ownerId != null) state.owners.put(entity.getUniqueId(), ownerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onManagedBossDeath(EntityDeathEvent event) {
        BossReference boss = bossEntities.remove(event.getEntity().getUniqueId());
        if (boss == null || !manager.unlockNextFloor(boss.sessionId, boss.floor, boss.mobId)) return;
        for (DungeonSession session : manager.getSessions()) {
            if (!session.getId().equals(boss.sessionId)) continue;
            for (UUID playerId : session.getParticipants()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) messages.send(player, "boss-unlocked");
            }
            return;
        }
    }

    @EventHandler
    public void onSessionEnd(DungeonSessionEndEvent event) {
        UUID sessionId = event.getSession().getId();
        Iterator<Map.Entry<String, SpawnState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            SpawnState state = iterator.next().getValue();
            if (!state.sessionId.equals(sessionId)) continue;
            for (UUID entityId : new ArrayList<UUID>(state.entities)) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) entity.remove();
                bossEntities.remove(entityId);
            }
            iterator.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL && !reason.name().equals("CHUNK_GEN")) return;
        Location location = event.getLocation();
        DungeonSession session = manager.findByWorld(location.getWorld().getName());
        if (session != null) {
            DungeonDefinition definition = session.getDefinition();
            boolean generatedWorld = definition.getType() == DungeonType.GENERATED;
            boolean disabledInSession = !definition.isNaturalSpawning()
                    && (session.isTemporary() || manager.findFloor(session, location) != null);
            if (generatedWorld || disabledInSession) {
                event.setCancelled(true);
                return;
            }
        }
        for (DungeonDefinition definition : repository.getAll()) {
            if (definition.getType() != DungeonType.STATIC || definition.isNaturalSpawning()
                    || !location.getWorld().getName().equals(definition.getWorld())) continue;
            for (DungeonFloor floor : definition.getFloors()) {
                if (floor.getBounds() != null && floor.getBounds().contains(location, null)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof LivingEntity)
                    || !entity.getPersistentDataContainer().has(managedMobKey, PersistentDataType.BYTE)
                    || isTracked(entity.getUniqueId())) continue;
            entity.remove();
        }
    }

    public void shutdown() {
        for (SpawnState state : states.values()) {
            for (UUID entityId : state.entities) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) entity.remove();
            }
        }
        states.clear();
        bossEntities.clear();
    }

    private void removeStaleStates(Set<String> validKeys) {
        Iterator<Map.Entry<String, SpawnState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SpawnState> entry = iterator.next();
            if (validKeys.contains(entry.getKey())) continue;
            for (UUID entityId : entry.getValue().entities) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) entity.remove();
                bossEntities.remove(entityId);
            }
            iterator.remove();
        }
    }

    private void cleanupTaggedEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!entity.getPersistentDataContainer().has(managedMobKey, PersistentDataType.BYTE)) continue;
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) plugin.getLogger().info("Removed " + removed + " stale dungeon mob(s).");
    }

    private boolean isTracked(UUID entityId) {
        for (SpawnState state : states.values()) if (state.entities.contains(entityId)) return true;
        return false;
    }

    private void warnOnce(SpawnState state, String message) {
        if (state.warned) return;
        state.warned = true;
        plugin.getLogger().warning(message);
    }

    private String key(UUID sessionId, int floor, String spawnId) {
        return sessionId + ":" + floor + ":" + spawnId;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static final class SpawnState {
        private final UUID sessionId;
        private final List<UUID> entities = new ArrayList<UUID>();
        private final Map<UUID, UUID> owners = new HashMap<UUID, UUID>();
        private final Map<UUID, Long> playerTimers = new HashMap<UUID, Long>();
        private final Set<UUID> seenPlayers = new HashSet<UUID>();
        private boolean initialized;
        private boolean warned;
        private long nextSpawnAt;
        private long nextEntitySpawnAt;
        private long nextDespawnCheckAt;
        private int initialSpawned;
        private int refillRemaining;

        private SpawnState(UUID sessionId) { this.sessionId = sessionId; }
    }

    private static final class BossReference {
        private final UUID sessionId;
        private final int floor;
        private final String mobId;

        private BossReference(UUID sessionId, int floor, String mobId) {
            this.sessionId = sessionId;
            this.floor = floor;
            this.mobId = mobId;
        }
    }
}
