package net.enelson.sopdungeons.service;

import net.enelson.sopdungeons.config.ModuleRepository;
import net.enelson.sopdungeons.event.DungeonModulePasteEvent;
import net.enelson.sopdungeons.integration.WorldEditBridge;
import net.enelson.sopdungeons.model.Cuboid;
import net.enelson.sopdungeons.model.DungeonDefinition;
import net.enelson.sopdungeons.model.DungeonFloor;
import net.enelson.sopdungeons.model.DungeonModule;
import net.enelson.sopdungeons.model.DungeonType;
import net.enelson.sopdungeons.model.MobSpawnDefinition;
import net.enelson.sopdungeons.model.ModuleConnector;
import net.enelson.sopdungeons.model.ModuleMobSpawn;
import net.enelson.sopdungeons.model.ModuleType;
import net.enelson.sopdungeons.model.Point;
import net.enelson.sopdungeons.model.RelativePoint;
import net.enelson.sopdungeons.service.module.ModuleMazeLayoutGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GeneratedDungeonBuilder {
    private final JavaPlugin plugin;
    private final ModuleRepository modules;
    private final Random random = new Random();
    private final int floorSpacing;
    private final double loopChance;
    private final int minimumMazeCells;
    private final int cellsPerRoom;
    private final int envelopePadding;
    private final Material envelopeMaterial;
    private final int moduleSize;
    private final ChunkPreparationService chunkPreparation;

    public GeneratedDungeonBuilder(JavaPlugin plugin, ModuleRepository modules, int floorSpacing) {
        this.plugin = plugin;
        this.modules = modules;
        this.floorSpacing = Math.max(128, floorSpacing);
        this.loopChance = Math.max(0.0, Math.min(0.35,
                plugin.getConfig().getDouble("generation.modules.loop-chance", 0.12)));
        this.minimumMazeCells = Math.max(12,
                plugin.getConfig().getInt("generation.modules.minimum-cells", 30));
        this.cellsPerRoom = Math.max(1,
                plugin.getConfig().getInt("generation.modules.cells-per-room", 5));
        this.moduleSize = plugin.getConfig().getInt("generation.modules.size", 15);
        if (moduleSize < 5 || moduleSize % 2 == 0) {
            throw new IllegalArgumentException("generation.modules.size must be an odd number of at least 5");
        }
        this.envelopePadding = Math.max(1,
                plugin.getConfig().getInt("generation.modules.envelope-padding", 5));
        Material configuredMaterial = Material.matchMaterial(plugin.getConfig().getString(
                "generation.modules.envelope-material", "STONE"));
        this.envelopeMaterial = configuredMaterial == null ? Material.STONE : configuredMaterial;
        this.chunkPreparation = new ChunkPreparationService(plugin);
    }

    public CompletableFuture<DungeonDefinition> build(UUID sessionId, DungeonDefinition source, World world) {
        final CompletableFuture<DungeonDefinition> future = new CompletableFuture<DungeonDefinition>();
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null
                && Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            future.completeExceptionally(new IOException(
                    "WorldEdit or FastAsyncWorldEdit is required for GENERATED dungeons"));
            return future;
        }
        DungeonDefinition result = new DungeonDefinition(source.getId(), DungeonType.GENERATED);
        result.setWorld(world.getName());
        result.setExit(source.getExit());
        result.setGeneratedFloors(source.getGeneratedFloors());
        result.setNormalRooms(source.getNormalRooms());
        result.setGeneratedBosses(source.hasGeneratedBosses());
        result.setGenerationMode(source.getGenerationMode());
        result.setNaturalSpawning(source.isNaturalSpawning());
        new GenerationJob(sessionId, source, result, world, future).runTaskTimer(plugin, 1L, 1L);
        return future;
    }

    private final class GenerationJob extends BukkitRunnable {
        private final UUID sessionId;
        private final DungeonDefinition source;
        private final DungeonDefinition result;
        private final World world;
        private final CompletableFuture<DungeonDefinition> future;
        private final WorldEditBridge worldEdit = new WorldEditBridge();
        private final Map<String, Integer> uses = new HashMap<String, Integer>();
        private List<PlannedModule> plan;
        private DungeonFloor floor;
        private int number = 1;
        private int moduleIndex;
        private int startY;
        private int firstExitX;
        private int firstExitY;
        private int firstExitZ;
        private int lastEntryX;
        private int lastEntryY;
        private int lastEntryZ;
        private int exitPortalX;
        private int exitPortalY;
        private int exitPortalZ;
        private ModuleMazeLayoutGenerator.Direction entranceDirection;
        private ModuleMazeLayoutGenerator.Direction exitDirection;
        private Cuboid floorBounds;
        private Cuboid rockEnvelope;
        private boolean envelopeFilled;
        private int envelopeY;
        private CompletableFuture<ChunkPreparationService.PreparedChunks> chunkPreparationFuture;
        private ChunkPreparationService.PreparedChunks preparedChunks;

        private GenerationJob(UUID sessionId, DungeonDefinition source, DungeonDefinition result, World world,
                              CompletableFuture<DungeonDefinition> future) {
            this.sessionId = sessionId;
            this.source = source;
            this.result = result;
            this.world = world;
            this.future = future;
        }

        @Override
        public void run() {
            try {
                if (plan == null) prepareFloor();
                if (!envelopeFilled) {
                    if (!chunksReady()) return;
                    if (envelopeY <= rockEnvelope.getMaxY()) {
                        worldEdit.fill(new Cuboid(world.getName(), rockEnvelope.getMinX(), envelopeY,
                                rockEnvelope.getMinZ(), rockEnvelope.getMaxX(), envelopeY,
                                rockEnvelope.getMaxZ()), world, envelopeMaterial);
                        envelopeY++;
                        return;
                    }
                    envelopeFilled = true;
                }
                pasteNext();
                if (moduleIndex < plan.size()) return;
                finishFloor();
                number++;
                plan = null;
                envelopeFilled = false;
                if (number <= source.getGeneratedFloors()) return;
                future.complete(result);
                cancel();
            } catch (Exception exception) {
                releasePreparedChunks();
                future.completeExceptionally(exception);
                cancel();
            }
        }

        private boolean chunksReady() throws Exception {
            if (preparedChunks != null) return true;
            if (chunkPreparationFuture == null) {
                chunkPreparationFuture = chunkPreparation.prepare(world, rockEnvelope);
                return false;
            }
            if (!chunkPreparationFuture.isDone()) return false;
            preparedChunks = chunkPreparationFuture.get();
            return true;
        }

        private void prepareFloor() throws IOException {
            startY = 80;
            int cells = Math.max(minimumMazeCells, source.getNormalRooms() * cellsPerRoom);
            int gridSide = Math.max(5, (int) Math.ceil(Math.sqrt(cells * 2.0)));
            int stride = Math.max(floorSpacing, (gridSide + 3) * moduleSize);
            int originX = 0;
            int originZ = (number - 1) * stride;
            floorBounds = null;
            moduleIndex = 0;
            chunkPreparationFuture = null;
            preparedChunks = null;
            floor = result.getOrCreateFloor(number);

            ModuleMazeLayoutGenerator.Layout layout = new ModuleMazeLayoutGenerator(
                    cells, loopChance, random.nextLong()).generate();
            List<PlannedModule> candidate = new ArrayList<PlannedModule>();
            Map<ModuleMazeLayoutGenerator.Cell, PlannedModule> placements =
                    new HashMap<ModuleMazeLayoutGenerator.Cell, PlannedModule>();
            for (ModuleMazeLayoutGenerator.Cell cell : layout.getCells()) {
                ModuleType type = typeFor(cell, layout);
                EnumSet<ModuleMazeLayoutGenerator.Direction> required = cell.getOpenings();
                boolean endpoint = cell == layout.getEntrance() || cell == layout.getExit();
                ModuleMazeLayoutGenerator.Direction inside = cell.getOpenings().iterator().next();
                ModuleConnector.Kind anchorKind = ModuleConnector.Kind.ENTRY;
                Option option;
                try {
                    option = choose(type, required, anchorKind,
                            endpoint ? inside : null);
                } catch (IOException missingStairs) {
                    ModuleType fallback = fallbackTypeFor(cell, layout);
                    if (fallback == type) throw missingStairs;
                    EnumSet<ModuleMazeLayoutGenerator.Direction> fallbackRequired = cell.getOpenings();
                    fallbackRequired.add(inside.opposite());
                    option = choose(fallback, fallbackRequired, null, null);
                }
                int cellX = originX + cell.getX() * moduleSize;
                int cellZ = originZ + cell.getZ() * moduleSize;
                ModuleConnector anchor = connector(option.module, anchorKind);
                String anchorDirection = anchor.getRotatedDirection(option.rotation);
                int[] target = connectorAt(cellX, startY, cellZ, direction(anchorDirection));
                int[] entry = entryForAnchor(option.module, anchor, option.rotation, target);
                WorldEditBridge.Placement placement = worldEdit.preview(option.module, world.getName(),
                        entry[0], entry[1], entry[2], option.rotation);
                validateFootprint(option.module, placement);
                PlannedModule planned = new PlannedModule(option.module, option.rotation, placement,
                        entry[0], entry[1], entry[2]);
                candidate.add(planned);
                placements.put(cell, planned);
                increment(uses, option.module.getId());
            }
            plan = candidate;
            rememberEndpoints(layout, placements);
            rockEnvelope = envelope(candidate);
            envelopeY = rockEnvelope.getMinY();
        }

        private ModuleType typeFor(ModuleMazeLayoutGenerator.Cell cell,
                                   ModuleMazeLayoutGenerator.Layout layout) {
            if (cell == layout.getEntrance()) return ModuleType.STAIRS_UP;
            if (cell == layout.getExit()) {
                return number == source.getGeneratedFloors() ? ModuleType.EXIT : ModuleType.STAIRS_DOWN;
            }
            if (source.hasGeneratedBosses() && cell == layout.getBoss()) return ModuleType.BOSS;
            return cell.getOpenings().size() == 1 ? ModuleType.DEAD_END : ModuleType.NORMAL;
        }

        private ModuleType fallbackTypeFor(ModuleMazeLayoutGenerator.Cell cell,
                                           ModuleMazeLayoutGenerator.Layout layout) {
            if (cell == layout.getEntrance()) return number == 1 ? ModuleType.ENTRANCE : ModuleType.TRANSITION;
            if (cell == layout.getExit()) {
                return number == source.getGeneratedFloors() ? ModuleType.EXIT : ModuleType.TRANSITION;
            }
            return typeFor(cell, layout);
        }

        private Option choose(ModuleType type, Set<ModuleMazeLayoutGenerator.Direction> required,
                              ModuleConnector.Kind requiredKind,
                              ModuleMazeLayoutGenerator.Direction requiredDirection)
                throws IOException {
            List<Option> options = new ArrayList<Option>();
            Set<String> requiredNames = names(required);
            for (DungeonModule module : modules.getAll()) {
                int used = uses.containsKey(module.getId()) ? uses.get(module.getId()) : 0;
                if (module.getType() != type || module.getMinDepth() > number
                        || (module.getMaxUses() > 0 && used >= module.getMaxUses())
                        || !module.getSchematic().isFile()) continue;
                for (int rotation : module.getRotations()) {
                    if (!connectorDirections(module, rotation).equals(requiredNames)) continue;
                    if (requiredDirection != null && !hasConnector(module, rotation, requiredKind,
                            requiredDirection.name())) continue;
                    for (int weight = 0; weight < Math.min(20, module.getWeight()); weight++) {
                        options.add(new Option(module, rotation));
                    }
                }
            }
            if (options.isEmpty()) {
                throw new IOException("No " + type + " module matches openings " + requiredNames
                        + ". Install the bundled maze modules or add a compatible schematic.");
            }
            Collections.shuffle(options, random);
            return options.get(0);
        }

        private boolean hasConnector(DungeonModule module, int rotation, ModuleConnector.Kind kind,
                                     String direction) {
            for (ModuleConnector connector : module.getConnectors()) {
                if (connector.getKind() == kind
                        && connector.getRotatedDirection(rotation).equals(direction)) return true;
            }
            return false;
        }

        private ModuleConnector connector(DungeonModule module, ModuleConnector.Kind kind) {
            for (ModuleConnector connector : module.getConnectors()) {
                if (connector.getKind() == kind) return connector;
            }
            throw new IllegalStateException("Module " + module.getId() + " has no " + kind + " connector");
        }

        private int[] entryForAnchor(DungeonModule module, ModuleConnector anchor, int rotation,
                                     int[] target) {
            RelativePoint entry = module.getEntryConnector().getPoint().rotate(rotation);
            RelativePoint point = anchor.getPoint().rotate(rotation);
            return new int[] {target[0] - point.getX() + entry.getX(),
                    target[1] - point.getY() + entry.getY(),
                    target[2] - point.getZ() + entry.getZ()};
        }

        private void validateFootprint(DungeonModule module, WorldEditBridge.Placement placement)
                throws IOException {
            Cuboid bounds = placement.getBounds();
            int width = bounds.getMaxX() - bounds.getMinX() + 1;
            int length = bounds.getMaxZ() - bounds.getMinZ() + 1;
            if (width != moduleSize || length != moduleSize) {
                throw new IOException("Module " + module.getId() + " must have a " + moduleSize + "x"
                        + moduleSize + " footprint, found " + width + "x" + length);
            }
        }

        private Set<String> connectorDirections(DungeonModule module, int rotation) {
            Set<String> result = new HashSet<String>();
            for (ModuleConnector connector : module.getConnectors()) {
                result.add(connector.getRotatedDirection(rotation));
            }
            return result;
        }

        private Set<String> names(Set<ModuleMazeLayoutGenerator.Direction> directions) {
            Set<String> result = new HashSet<String>();
            for (ModuleMazeLayoutGenerator.Direction direction : directions) result.add(direction.name());
            return result;
        }

        private int[] connectorAt(int minX, int y, int minZ,
                                  ModuleMazeLayoutGenerator.Direction direction) {
            int center = moduleSize / 2;
            if (direction == ModuleMazeLayoutGenerator.Direction.WEST) return new int[] {minX, y + 1, minZ + center};
            if (direction == ModuleMazeLayoutGenerator.Direction.EAST) return new int[] {minX + moduleSize - 1, y + 1, minZ + center};
            if (direction == ModuleMazeLayoutGenerator.Direction.NORTH) return new int[] {minX + center, y + 1, minZ};
            return new int[] {minX + center, y + 1, minZ + moduleSize - 1};
        }

        private void rememberEndpoints(ModuleMazeLayoutGenerator.Layout layout,
                                       Map<ModuleMazeLayoutGenerator.Cell, PlannedModule> placements) {
            ModuleMazeLayoutGenerator.Cell entrance = layout.getEntrance();
            entranceDirection = entrance.getOpenings().iterator().next();
            PlannedModule first = placements.get(entrance);
            int[] inside = connector(first, entranceDirection);
            firstExitX = inside[0]; firstExitY = inside[1]; firstExitZ = inside[2];

            ModuleMazeLayoutGenerator.Cell exit = layout.getExit();
            exitDirection = exit.getOpenings().iterator().next();
            PlannedModule last = placements.get(exit);
            int[] lastInside = connector(last, exitDirection);
            lastEntryX = lastInside[0]; lastEntryY = lastInside[1]; lastEntryZ = lastInside[2];
            Cuboid bounds = last.placement.getBounds();
            exitPortalX = (bounds.getMinX() + bounds.getMaxX()) / 2;
            exitPortalY = lastEntryY;
            exitPortalZ = (bounds.getMinZ() + bounds.getMaxZ()) / 2;
        }

        private int[] connector(PlannedModule module, ModuleMazeLayoutGenerator.Direction direction) {
            for (ModuleConnector connector : module.module.getConnectors()) {
                if (!connector.getRotatedDirection(module.rotation).equals(direction.name())) continue;
                RelativePoint point = connector.getPoint().rotate(module.rotation);
                return new int[] {module.placement.getPasteX() + point.getX(),
                        module.placement.getPasteY() + point.getY(),
                        module.placement.getPasteZ() + point.getZ()};
            }
            throw new IllegalStateException("Module " + module.module.getId() + " lacks " + direction + " connector");
        }

        private Cuboid envelope(List<PlannedModule> modules) {
            Cuboid bounds = null;
            for (PlannedModule module : modules) bounds = merge(bounds, module.placement.getBounds(), world.getName());
            return new Cuboid(world.getName(), bounds.getMinX() - envelopePadding,
                    bounds.getMinY() - envelopePadding, bounds.getMinZ() - envelopePadding,
                    bounds.getMaxX() + envelopePadding, bounds.getMaxY() + envelopePadding,
                    bounds.getMaxZ() + envelopePadding);
        }

        private void pasteNext() throws IOException {
            PlannedModule planned = plan.get(moduleIndex);
            WorldEditBridge.Placement placement = worldEdit.paste(planned.module, world,
                    planned.entryX, planned.entryY, planned.entryZ, planned.rotation);
            Bukkit.getPluginManager().callEvent(new DungeonModulePasteEvent(sessionId, source.getId(),
                    world.getName(), number, planned.module.getId(), planned.module.getType(), placement.getBounds()));
            floorBounds = merge(floorBounds, placement.getBounds(), world.getName());
            addMobSpawns(planned.module, placement, planned.rotation, moduleIndex);
            moduleIndex++;
        }

        private void addMobSpawns(DungeonModule module, WorldEditBridge.Placement placement,
                                  int rotation, int index) {
            for (ModuleMobSpawn spawn : module.getMobSpawns()) {
                RelativePoint point = spawn.getLocation().rotate(rotation);
                Point location = new Point(world.getName(), placement.getPasteX() + point.getX() + 0.5,
                        placement.getPasteY() + point.getY(), placement.getPasteZ() + point.getZ() + 0.5,
                        0.0F, 0.0F);
                String id = "f" + number + "_m" + index + "_" + spawn.getId();
                floor.putMobSpawn(new MobSpawnDefinition(id, spawn.getProvider(), spawn.getMobId(), location,
                        spawn.isBoss() ? 1 : spawn.getAmount(), spawn.getRadius(), spawn.isBoss(),
                        spawn.getRespawnMode(), spawn.getRespawnDelaySeconds(), spawn.getSpawnIntervalTicks()));
                if (spawn.isBoss()) floor.setBossId(spawn.getMobId());
            }
        }

        private void finishFloor() {
            floor.setBounds(floorBounds);
            if (source.hasGeneratedBosses() && !floor.requiresBoss()) {
                plugin.getLogger().warning("Generated floor " + number
                        + " has a BOSS module without a boss spawn; the next floor will remain unlocked.");
            }
            floor.setArrivalFromUp(new Point(world.getName(),
                    firstExitX + entranceDirection.getDx() * 2.5,
                    firstExitY + 0.1,
                    firstExitZ + entranceDirection.getDz() * 2.5,
                    yaw(entranceDirection.getDx(), entranceDirection.getDz()), 0));
            floor.setArrivalFromDown(new Point(world.getName(),
                    lastEntryX + exitDirection.getDx() * 2.5,
                    lastEntryY + 0.1,
                    lastEntryZ + exitDirection.getDz() * 2.5,
                    yaw(exitDirection.getDx(), exitDirection.getDz()), 0));
            floor.setUpTrigger(transitionTrigger(world.getName(), firstExitX, firstExitY, firstExitZ,
                    entranceDirection));
            if (number < source.getGeneratedFloors()) {
                floor.setDownTrigger(transitionTrigger(world.getName(), lastEntryX, lastEntryY, lastEntryZ,
                        exitDirection));
            } else {
                floor.setDownTrigger(new Cuboid(world.getName(), exitPortalX - 1, exitPortalY,
                        exitPortalZ - 1, exitPortalX + 1, exitPortalY + 2, exitPortalZ + 1));
            }
            if (number == 1) result.setEntrance(floor.getArrivalFromUp());
            releasePreparedChunks();
        }

        private void releasePreparedChunks() {
            if (preparedChunks != null) preparedChunks.release();
            preparedChunks = null;
            chunkPreparationFuture = null;
        }

    }

    private ModuleMazeLayoutGenerator.Direction direction(String name) {
        return ModuleMazeLayoutGenerator.Direction.valueOf(name.toUpperCase());
    }

    private void increment(Map<String, Integer> values, String key) {
        values.put(key, values.containsKey(key) ? values.get(key) + 1 : 1);
    }

    private Cuboid merge(Cuboid first, Cuboid second, String world) {
        if (first == null) return second;
        return new Cuboid(world, Math.min(first.getMinX(), second.getMinX()),
                Math.min(first.getMinY(), second.getMinY()), Math.min(first.getMinZ(), second.getMinZ()),
                Math.max(first.getMaxX(), second.getMaxX()), Math.max(first.getMaxY(), second.getMaxY()),
                Math.max(first.getMaxZ(), second.getMaxZ()));
    }

    private Cuboid transitionTrigger(String world, int x, int y, int z,
                                     ModuleMazeLayoutGenerator.Direction direction) {
        if (direction == ModuleMazeLayoutGenerator.Direction.EAST
                || direction == ModuleMazeLayoutGenerator.Direction.WEST) {
            return new Cuboid(world, x, y, z - 1, x, y + 2, z + 1);
        }
        return new Cuboid(world, x - 1, y, z, x + 1, y + 2, z);
    }

    private float yaw(double x, double z) { return (float) Math.toDegrees(Math.atan2(-x, z)); }

    private static final class Option {
        private final DungeonModule module;
        private final int rotation;
        private Option(DungeonModule module, int rotation) { this.module = module; this.rotation = rotation; }
    }

    private static final class PlannedModule {
        private final DungeonModule module;
        private final int rotation;
        private final WorldEditBridge.Placement placement;
        private final int entryX;
        private final int entryY;
        private final int entryZ;

        private PlannedModule(DungeonModule module, int rotation, WorldEditBridge.Placement placement,
                              int entryX, int entryY, int entryZ) {
            this.module = module;
            this.rotation = rotation;
            this.placement = placement;
            this.entryX = entryX;
            this.entryY = entryY;
            this.entryZ = entryZ;
        }
    }
}
