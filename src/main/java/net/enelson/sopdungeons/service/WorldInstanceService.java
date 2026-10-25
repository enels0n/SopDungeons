package net.enelson.sopdungeons.service;

import net.enelson.sopdungeons.model.DungeonDefinition;
import net.enelson.sopdungeons.model.DungeonType;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class WorldInstanceService {
    private final JavaPlugin plugin;
    private final String prefix;

    public WorldInstanceService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.prefix = plugin.getConfig().getString("instance-world-prefix", "sopdungeon_");
    }

    public CompletableFuture<World> create(DungeonDefinition definition) {
        final CompletableFuture<World> future = new CompletableFuture<World>();
        final String worldName = prefix + definition.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
        if (definition.getType() == DungeonType.GENERATED) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    World world = new WorldCreator(worldName).generator(new EmptyChunkGenerator()).createWorld();
                    if (world == null) future.completeExceptionally(new IllegalStateException("Cannot create world " + worldName));
                    else {
                        configureGeneratedWorld(world);
                        future.complete(world);
                    }
                }
            });
            return future;
        }

        final File source = new File(plugin.getDataFolder(), "templates" + File.separator + definition.getTemplate());
        final File target = new File(Bukkit.getWorldContainer(), worldName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    if (!source.isDirectory()) throw new IOException("Template does not exist: " + source.getPath());
                    copyDirectory(source.toPath(), target.toPath());
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            World world = new WorldCreator(worldName).createWorld();
                            if (world == null) future.completeExceptionally(new IllegalStateException("Cannot load world " + worldName));
                            else future.complete(world);
                        }
                    });
                } catch (Exception exception) {
                    future.completeExceptionally(exception);
                }
            }
        });
        return future;
    }

    public CompletableFuture<World> loadGenerated(final String worldName) {
        final CompletableFuture<World> future = new CompletableFuture<World>();
        World loaded = Bukkit.getWorld(worldName);
        if (loaded != null) {
            configureGeneratedWorld(loaded);
            future.complete(loaded);
            return future;
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                File directory = new File(Bukkit.getWorldContainer(), worldName);
                if (!directory.isDirectory()) {
                    future.completeExceptionally(new IllegalStateException(
                            "Generated dungeon world does not exist: " + worldName));
                    return;
                }
                World world = new WorldCreator(worldName).generator(new EmptyChunkGenerator()).createWorld();
                if (world == null) {
                    future.completeExceptionally(new IllegalStateException("Cannot load world " + worldName));
                    return;
                }
                configureGeneratedWorld(world);
                future.complete(world);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> remove(final String worldName) {
        final CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                World world = Bukkit.getWorld(worldName);
                if (world != null && !Bukkit.unloadWorld(world, false)) {
                    future.complete(false);
                    return;
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            File target = new File(Bukkit.getWorldContainer(), worldName);
                            Path root = Bukkit.getWorldContainer().getCanonicalFile().toPath();
                            Path resolved = target.getCanonicalFile().toPath();
                            if (!worldName.startsWith(prefix) || !resolved.startsWith(root)) {
                                throw new IOException("Refusing to delete unsafe world path: " + resolved);
                            }
                            deleteDirectory(resolved);
                            future.complete(true);
                        } catch (Exception exception) {
                            future.completeExceptionally(exception);
                        }
                    }
                });
            }
        });
        return future;
    }

    private void configureGeneratedWorld(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setTime(plugin.getConfig().getLong("generation.generated-world.time", 6000L));
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
    }

    public boolean removeImmediately(String worldName) {
        try {
            World world = Bukkit.getWorld(worldName);
            if (world != null && !Bukkit.unloadWorld(world, false)) return false;
            File target = new File(Bukkit.getWorldContainer(), worldName);
            Path root = Bukkit.getWorldContainer().getCanonicalFile().toPath();
            Path resolved = target.getCanonicalFile().toPath();
            if (!worldName.startsWith(prefix) || !resolved.startsWith(root)) return false;
            deleteDirectory(resolved);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Cannot remove instance world " + worldName + ": " + exception.getMessage());
            return false;
        }
    }

    public void cleanupOrphans(Collection<DungeonDefinition> definitions) {
        Set<String> retained = new HashSet<String>();
        for (DungeonDefinition definition : definitions) {
            if (definition.getType() == DungeonType.GENERATED && definition.getWorld() != null) {
                retained.add(definition.getWorld());
            }
        }
        File[] directories = Bukkit.getWorldContainer().listFiles(file -> file.isDirectory() && file.getName().startsWith(prefix));
        if (directories == null) return;
        for (File directory : directories) {
            if (retained.contains(directory.getName())) continue;
            World loaded = Bukkit.getWorld(directory.getName());
            if (loaded != null) Bukkit.unloadWorld(loaded, false);
            if (removeImmediately(directory.getName())) plugin.getLogger().info("Removed orphan instance world " + directory.getName());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(path -> {
                try {
                    String name = path.getFileName() == null ? "" : path.getFileName().toString();
                    if (name.equals("uid.dat") || name.equals("session.lock")) return;
                    Path destination = target.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) Files.createDirectories(destination);
                    else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException exception) {
                    throw new CopyFailure(exception);
                }
            });
        } catch (CopyFailure failure) {
            throw failure.cause;
        }
    }

    private void deleteDirectory(Path target) throws IOException {
        if (!Files.exists(target)) return;
        try (Stream<Path> paths = Files.walk(target)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new CopyFailure(exception);
                }
            });
        } catch (CopyFailure failure) {
            throw failure.cause;
        }
    }

    private static final class CopyFailure extends RuntimeException {
        private final IOException cause;
        private CopyFailure(IOException cause) { super(cause); this.cause = cause; }
    }
}
