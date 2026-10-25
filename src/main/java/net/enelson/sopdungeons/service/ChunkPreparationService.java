package net.enelson.sopdungeons.service;

import net.enelson.sopdungeons.model.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ChunkPreparationService {
    private final JavaPlugin plugin;
    private final int maxConcurrent;
    private final long timeoutTicks;

    public ChunkPreparationService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.maxConcurrent = Math.max(1,
                plugin.getConfig().getInt("generation.chunk-preparation.max-concurrent", 4));
        long timeoutSeconds = Math.max(10L,
                plugin.getConfig().getLong("generation.chunk-preparation.timeout-seconds", 120L));
        this.timeoutTicks = timeoutSeconds * 20L;
    }

    public CompletableFuture<PreparedChunks> prepare(World world, Cuboid bounds) {
        if (!plugin.getConfig().getBoolean("generation.chunk-preparation.enabled", true)) {
            return CompletableFuture.completedFuture(new PreparedChunks(plugin, world,
                    Collections.<ChunkCoordinate>emptyList()));
        }
        try {
            Method method = world.getClass().getMethod("getChunkAtAsync",
                    Integer.TYPE, Integer.TYPE, Boolean.TYPE);
            Preparation preparation = new Preparation(world, method, coordinates(bounds));
            preparation.start();
            return preparation.result;
        } catch (NoSuchMethodException exception) {
            CompletableFuture<PreparedChunks> failed = new CompletableFuture<PreparedChunks>();
            failed.completeExceptionally(new IllegalStateException(
                    "Generated dungeons require Paper's asynchronous chunk loading API", exception));
            return failed;
        }
    }

    static List<ChunkCoordinate> coordinates(Cuboid bounds) {
        List<ChunkCoordinate> result = new ArrayList<ChunkCoordinate>();
        int minX = bounds.getMinX() >> 4;
        int maxX = bounds.getMaxX() >> 4;
        int minZ = bounds.getMinZ() >> 4;
        int maxZ = bounds.getMaxZ() >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) result.add(new ChunkCoordinate(x, z));
        }
        return result;
    }

    static final class ChunkCoordinate {
        private final int x;
        private final int z;

        ChunkCoordinate(int x, int z) {
            this.x = x;
            this.z = z;
        }

        int getX() { return x; }
        int getZ() { return z; }
    }

    public static final class PreparedChunks {
        private final JavaPlugin plugin;
        private final World world;
        private final List<ChunkCoordinate> tickets;
        private boolean released;

        private PreparedChunks(JavaPlugin plugin, World world, List<ChunkCoordinate> tickets) {
            this.plugin = plugin;
            this.world = world;
            this.tickets = tickets;
        }

        public void release() {
            if (released) return;
            released = true;
            for (ChunkCoordinate coordinate : tickets) {
                world.removePluginChunkTicket(coordinate.x, coordinate.z, plugin);
            }
        }
    }

    private final class Preparation {
        private final World world;
        private final Method asyncChunkMethod;
        private final ArrayDeque<ChunkCoordinate> remaining;
        private final List<ChunkCoordinate> tickets = new ArrayList<ChunkCoordinate>();
        private final CompletableFuture<PreparedChunks> result = new CompletableFuture<PreparedChunks>();
        private int active;
        private BukkitTask timeout;

        private Preparation(World world, Method asyncChunkMethod, List<ChunkCoordinate> coordinates) {
            this.world = world;
            this.asyncChunkMethod = asyncChunkMethod;
            this.remaining = new ArrayDeque<ChunkCoordinate>(coordinates);
        }

        private void start() {
            timeout = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    fail(new IllegalStateException("Timed out while preparing dungeon chunks"));
                }
            }, timeoutTicks);
            pump();
        }

        private void pump() {
            if (result.isDone()) return;
            while (active < maxConcurrent && !remaining.isEmpty()) {
                ChunkCoordinate coordinate = remaining.removeFirst();
                active++;
                request(coordinate);
            }
            if (active == 0 && remaining.isEmpty()) {
                timeout.cancel();
                result.complete(new PreparedChunks(plugin, world,
                        new ArrayList<ChunkCoordinate>(tickets)));
            }
        }

        @SuppressWarnings("unchecked")
        private void request(final ChunkCoordinate coordinate) {
            try {
                Object requested = asyncChunkMethod.invoke(world, coordinate.x, coordinate.z, Boolean.TRUE);
                if (!(requested instanceof CompletableFuture)) {
                    fail(new IllegalStateException("Paper getChunkAtAsync returned an unsupported result"));
                    return;
                }
                ((CompletableFuture<Chunk>) requested).whenComplete((chunk, error) -> runOnPrimary(new Runnable() {
                    @Override
                    public void run() {
                        complete(coordinate, chunk, error);
                    }
                }));
            } catch (IllegalAccessException exception) {
                fail(exception);
            } catch (InvocationTargetException exception) {
                fail(exception.getCause() == null ? exception : exception.getCause());
            }
        }

        private void complete(ChunkCoordinate coordinate, Chunk chunk, Throwable error) {
            if (result.isDone()) return;
            if (error != null || chunk == null) {
                fail(error == null ? new IllegalStateException("Paper returned no chunk") : error);
                return;
            }
            if (world.addPluginChunkTicket(coordinate.x, coordinate.z, plugin)) tickets.add(coordinate);
            active--;
            pump();
        }

        private void fail(Throwable error) {
            runOnPrimary(new Runnable() {
                @Override
                public void run() {
                    if (result.isDone()) return;
                    if (timeout != null) timeout.cancel();
                    new PreparedChunks(plugin, world, tickets).release();
                    result.completeExceptionally(error);
                }
            });
        }

        private void runOnPrimary(Runnable action) {
            if (Bukkit.isPrimaryThread()) action.run();
            else Bukkit.getScheduler().runTask(plugin, action);
        }
    }
}
