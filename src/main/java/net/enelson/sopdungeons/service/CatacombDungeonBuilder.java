package net.enelson.sopdungeons.service;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.enelson.sopdungeons.model.Cuboid;
import net.enelson.sopdungeons.model.DungeonDefinition;
import net.enelson.sopdungeons.model.DungeonFloor;
import net.enelson.sopdungeons.model.DungeonType;
import net.enelson.sopdungeons.model.MobSpawnDefinition;
import net.enelson.sopdungeons.model.Point;
import net.enelson.sopdungeons.model.RespawnMode;
import net.enelson.sopdungeons.service.catacomb.CatacombLayoutGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CatacombDungeonBuilder {
    private final JavaPlugin plugin;
    private final int floorSpacing;
    private final int width;
    private final int length;
    private final int height;
    private final int minCorridor;
    private final int maxCorridor;
    private final int minSpacing;
    private final int maxSpacing;
    private final double loopChance;
    private final int bossRoomRadius;
    private final int lightSpacing;
    private final String bossProvider;
    private final String bossMob;
    private final String roomProvider;
    private final String roomMob;
    private final int roomMobAmount;
    private final double roomSpawnRadius;
    private final int roomRespawnSeconds;
    private final int roomSpawnIntervalTicks;
    private final Random seeds = new Random();

    public CatacombDungeonBuilder(JavaPlugin plugin, int floorSpacing) {
        this.plugin = plugin;
        this.floorSpacing = Math.max(128, floorSpacing);
        String root = "generation.catacombs.";
        this.width = plugin.getConfig().getInt(root + "width", 480);
        this.length = plugin.getConfig().getInt(root + "length", 480);
        this.height = Math.max(7, plugin.getConfig().getInt(root + "height", 10));
        this.minCorridor = plugin.getConfig().getInt(root + "corridor.min-width", 5);
        this.maxCorridor = plugin.getConfig().getInt(root + "corridor.max-width", 7);
        this.minSpacing = plugin.getConfig().getInt(root + "spacing.min", 15);
        this.maxSpacing = plugin.getConfig().getInt(root + "spacing.max", 24);
        this.loopChance = plugin.getConfig().getDouble(root + "loop-chance", 0.06);
        this.bossRoomRadius = plugin.getConfig().getInt(root + "boss-room-radius", 9);
        this.lightSpacing = Math.max(6, plugin.getConfig().getInt(root + "visuals.light-spacing", 14));
        this.bossProvider = plugin.getConfig().getString(root + "boss.provider", "VANILLA");
        this.bossMob = plugin.getConfig().getString(root + "boss.mob", "HUSK");
        this.roomProvider = plugin.getConfig().getString(root + "rooms.provider", "VANILLA");
        this.roomMob = plugin.getConfig().getString(root + "rooms.mob", "ZOMBIE");
        this.roomMobAmount = Math.max(0, plugin.getConfig().getInt(root + "rooms.amount", 3));
        this.roomSpawnRadius = Math.max(0.0, plugin.getConfig().getDouble(root + "rooms.radius", 4.0));
        this.roomRespawnSeconds = Math.max(0,
                plugin.getConfig().getInt(root + "rooms.respawn-delay-seconds", 45));
        this.roomSpawnIntervalTicks = Math.max(0,
                plugin.getConfig().getInt(root + "rooms.spawn-interval-ticks", 10));
    }

    public CompletableFuture<DungeonDefinition> build(UUID sessionId, DungeonDefinition source, World bukkitWorld) {
        final CompletableFuture<DungeonDefinition> future = new CompletableFuture<DungeonDefinition>();
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            future.completeExceptionally(new IllegalStateException(
                    "CATACOMBS generation requires FastAsyncWorldEdit to avoid blocking the server thread"));
            return future;
        }
        final com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(bukkitWorld);
        final long seed = seeds.nextLong() ^ sessionId.getMostSignificantBits() ^ sessionId.getLeastSignificantBits();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    DungeonDefinition result = createDefinition(source, bukkitWorld.getName(), world, seed);
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() { future.complete(result); }
                    });
                } catch (Exception exception) {
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() { future.completeExceptionally(exception); }
                    });
                }
            }
        });
        return future;
    }

    private DungeonDefinition createDefinition(DungeonDefinition source, String worldName,
                                                com.sk89q.worldedit.world.World world, long seed) throws Exception {
        DungeonDefinition result = new DungeonDefinition(source.getId(), DungeonType.GENERATED);
        result.setWorld(worldName);
        result.setExit(source.getExit());
        result.setGeneratedFloors(source.getGeneratedFloors());
        result.setNormalRooms(source.getNormalRooms());
        result.setGeneratedBosses(source.hasGeneratedBosses());
        result.setGenerationMode(source.getGenerationMode());
        result.setNaturalSpawning(source.isNaturalSpawning());
        int spacing = Math.max(floorSpacing, length + 32);
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            for (int floorNumber = 1; floorNumber <= source.getGeneratedFloors(); floorNumber++) {
                int originZ = (floorNumber - 1) * spacing;
                CatacombLayoutGenerator.Config config = new CatacombLayoutGenerator.Config(
                        width, length, minCorridor, maxCorridor, minSpacing, maxSpacing,
                        source.getNormalRooms(), loopChance, bossRoomRadius, seed + floorNumber * 31L);
                CatacombLayoutGenerator.Layout layout = new CatacombLayoutGenerator(config).generate();
                pasteFloorStructure(editSession, layout, originZ);
                pasteFloorDetails(editSession, layout, originZ,
                        floorNumber == source.getGeneratedFloors());
                configureFloor(result, source, layout, floorNumber, originZ, worldName);
            }
        }
        return result;
    }

    private void pasteFloorStructure(EditSession editSession, CatacombLayoutGenerator.Layout layout,
                                     int originZ) throws Exception {
        int baseY = 64;
        BlockState floorBlock = BlockTypes.DEEPSLATE_TILES.getDefaultState();
        BlockState ceilingBlock = BlockTypes.DEEPSLATE_BRICKS.getDefaultState();
        BlockState wallBlock = BlockTypes.STONE_BRICKS.getDefaultState();
        BlockState wallBand = BlockTypes.POLISHED_DEEPSLATE.getDefaultState();
        editSession.setBlocks(region(0, baseY, originZ, width - 1, baseY, originZ + length - 1), floorBlock);
        editSession.setBlocks(region(0, baseY + height - 1, originZ,
                width - 1, baseY + height - 1, originZ + length - 1), ceilingBlock);
        buildOuterShell(editSession, originZ, baseY);

        for (int z = 0; z < length; z++) {
            int runStart = -1;
            for (int x = 0; x <= width; x++) {
                boolean solid = x < width && !layout.isFloor(x, z);
                if (solid && runStart < 0) runStart = x;
                if (!solid && runStart >= 0) {
                    editSession.setBlocks(region(runStart, baseY + 2, originZ + z,
                            x - 1, baseY + height - 3, originZ + z), wallBlock);
                    editSession.setBlocks(region(runStart, baseY + 1, originZ + z,
                            x - 1, baseY + 1, originZ + z), wallBand);
                    editSession.setBlocks(region(runStart, baseY + height - 2, originZ + z,
                            x - 1, baseY + height - 2, originZ + z), wallBand);
                    runStart = -1;
                }
            }
        }

    }

    private void pasteFloorDetails(EditSession editSession, CatacombLayoutGenerator.Layout layout,
                                   int originZ, boolean finalFloor) throws Exception {
        int baseY = 64;
        decoratePassages(editSession, layout, originZ, baseY);
        for (CatacombLayoutGenerator.Cell room : layout.getRooms()) {
            placeRoomLight(editSession, room.getX(), originZ + room.getZ(), baseY + height - 1);
        }
        CatacombLayoutGenerator.Cell boss = layout.getBossRoom();
        editSession.setBlocks(region(boss.getX() - 2, baseY, originZ + boss.getZ() - 2,
                boss.getX() + 2, baseY, originZ + boss.getZ() + 2), BlockTypes.GOLD_BLOCK.getDefaultState());
        placeRoomLight(editSession, boss.getX(), originZ + boss.getZ(), baseY + height - 1);
        CatacombLayoutGenerator.Cell entrance = layout.getEntrance();
        CatacombLayoutGenerator.Cell exit = layout.getExit();
        sealPortal(editSession, false, originZ + entrance.getZ(), baseY);
        sealPortal(editSession, true, originZ + exit.getZ(), baseY);
        buildStairPavilion(editSession, false, originZ + entrance.getZ(), baseY);
        if (finalFloor) buildReturnCircle(editSession, originZ + exit.getZ(), baseY);
        else buildStairPavilion(editSession, true, originZ + exit.getZ(), baseY);
    }

    private void buildReturnCircle(EditSession editSession, int centerZ, int baseY) throws Exception {
        int centerX = width - 4;
        editSession.setBlocks(region(centerX - 2, baseY, centerZ - 2,
                centerX + 2, baseY, centerZ + 2), BlockTypes.POLISHED_DEEPSLATE.getDefaultState());
        editSession.setBlocks(region(centerX - 1, baseY, centerZ - 1,
                centerX + 1, baseY, centerZ + 1), BlockTypes.CRYING_OBSIDIAN.getDefaultState());
        editSession.setBlock(BlockVector3.at(centerX - 2, baseY, centerZ - 2),
                BlockTypes.SEA_LANTERN.getDefaultState());
        editSession.setBlock(BlockVector3.at(centerX + 2, baseY, centerZ - 2),
                BlockTypes.SEA_LANTERN.getDefaultState());
        editSession.setBlock(BlockVector3.at(centerX - 2, baseY, centerZ + 2),
                BlockTypes.SEA_LANTERN.getDefaultState());
        editSession.setBlock(BlockVector3.at(centerX + 2, baseY, centerZ + 2),
                BlockTypes.SEA_LANTERN.getDefaultState());
    }

    private void buildOuterShell(EditSession editSession, int originZ, int baseY) throws Exception {
        BlockState floorShell = BlockTypes.DEEPSLATE_TILES.getDefaultState();
        BlockState ceilingShell = BlockTypes.DEEPSLATE_BRICKS.getDefaultState();
        BlockState wallShell = BlockTypes.STONE_BRICKS.getDefaultState();
        editSession.setBlocks(region(-1, baseY - 1, originZ - 1,
                width, baseY - 1, originZ + length), floorShell);
        editSession.setBlocks(region(-1, baseY + height, originZ - 1,
                width, baseY + height, originZ + length), ceilingShell);
        editSession.setBlocks(region(-1, baseY, originZ - 1,
                -1, baseY + height - 1, originZ + length), wallShell);
        editSession.setBlocks(region(width, baseY, originZ - 1,
                width, baseY + height - 1, originZ + length), wallShell);
        editSession.setBlocks(region(0, baseY, originZ - 1,
                width - 1, baseY + height - 1, originZ - 1), wallShell);
        editSession.setBlocks(region(0, baseY, originZ + length,
                width - 1, baseY + height - 1, originZ + length), wallShell);
    }

    private void sealPortal(EditSession editSession, boolean east, int centerZ, int baseY)
            throws Exception {
        int wallX = east ? width - 1 : 0;
        editSession.setBlocks(region(wallX, baseY + 1, centerZ - 1,
                wallX, baseY + height - 2, centerZ + 1), BlockTypes.STONE_BRICKS.getDefaultState());
        editSession.setBlocks(region(wallX, baseY + 1, centerZ - 1,
                wallX, baseY + 1, centerZ + 1), BlockTypes.POLISHED_DEEPSLATE.getDefaultState());
        editSession.setBlocks(region(wallX, baseY + height - 2, centerZ - 1,
                wallX, baseY + height - 2, centerZ + 1), BlockTypes.POLISHED_DEEPSLATE.getDefaultState());
    }

    private void decoratePassages(EditSession editSession, CatacombLayoutGenerator.Layout layout,
                                  int originZ, int baseY) throws Exception {
        BlockState border = BlockTypes.POLISHED_DEEPSLATE.getDefaultState();
        BlockState crackedFloor = BlockTypes.CRACKED_DEEPSLATE_TILES.getDefaultState();
        BlockState crackedWall = BlockTypes.CRACKED_STONE_BRICKS.getDefaultState();
        BlockState mossyWall = BlockTypes.MOSSY_STONE_BRICKS.getDefaultState();
        BlockState chiseledWall = BlockTypes.CHISELED_STONE_BRICKS.getDefaultState();
        BlockState light = BlockTypes.SEA_LANTERN.getDefaultState();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                if (!layout.isFloor(x, z)) continue;
                int hash = detailHash(x, z);
                if (touchesWall(layout, x, z)) {
                    editSession.setBlock(BlockVector3.at(x, baseY, originZ + z), border);
                } else if (hash % 29 == 0) {
                    editSession.setBlock(BlockVector3.at(x, baseY, originZ + z), crackedFloor);
                }
                if (isOpen(layout, x, z, 1) && Math.floorMod(x + z, lightSpacing) == 0) {
                    editSession.setBlock(BlockVector3.at(x, baseY + height - 1, originZ + z), light);
                }
                decorateAdjacentWall(editSession, layout, x, z, originZ, baseY, hash,
                        crackedWall, mossyWall, chiseledWall);
            }
        }
    }

    private void decorateAdjacentWall(EditSession editSession, CatacombLayoutGenerator.Layout layout,
                                      int x, int z, int originZ, int baseY, int hash,
                                      BlockState cracked, BlockState mossy, BlockState chiseled) throws Exception {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int direction = 0; direction < directions.length; direction++) {
            int wallX = x + directions[direction][0];
            int wallZ = z + directions[direction][1];
            if (wallX < 0 || wallZ < 0 || wallX >= width || wallZ >= length
                    || layout.isFloor(wallX, wallZ)) continue;
            int value = hash + direction * 17;
            BlockState detail = value % 41 == 0 ? chiseled : value % 13 == 0 ? mossy
                    : value % 7 == 0 ? cracked : null;
            if (detail != null) {
                editSession.setBlock(BlockVector3.at(wallX, baseY + 2, originZ + wallZ), detail);
            }
        }
    }

    private void placeRoomLight(EditSession editSession, int x, int z, int y) throws Exception {
        BlockState light = BlockTypes.SEA_LANTERN.getDefaultState();
        editSession.setBlock(BlockVector3.at(x, y, z), light);
        editSession.setBlock(BlockVector3.at(x - 1, y, z), light);
        editSession.setBlock(BlockVector3.at(x + 1, y, z), light);
        editSession.setBlock(BlockVector3.at(x, y, z - 1), light);
        editSession.setBlock(BlockVector3.at(x, y, z + 1), light);
    }

    private void buildStairPavilion(EditSession editSession, boolean east, int centerZ, int baseY)
            throws Exception {
        int rise = height - 1;
        int direction = east ? 1 : -1;
        int firstX = east ? width : -1;
        BlockState shell = BlockTypes.DEEPSLATE_BRICKS.getDefaultState();
        BlockState trim = BlockTypes.POLISHED_DEEPSLATE.getDefaultState();
        BlockState air = BlockTypes.AIR.getDefaultState();
        // Both runs ascend west: the entrance rises westward, while the exit descends eastward.
        BlockState stair = stairFacing("west");

        // The entrance rises above the dungeon; the exit descends below it to the next floor.
        for (int step = 0; step < rise; step++) {
            int x = firstX + direction * step;
            int y = east ? baseY - step : baseY + 1 + step;
            editSession.setBlocks(region(x, y - 1, centerZ - 2, x, y + 4, centerZ + 2), shell);
            editSession.setBlocks(region(x, y, centerZ - 1, x, y + 3, centerZ + 1), air);
            editSession.setBlocks(region(x, y, centerZ - 1, x, y, centerZ + 1), stair);
            editSession.setBlock(BlockVector3.at(x, y + 4, centerZ),
                    step % 4 == 2 ? BlockTypes.SEA_LANTERN.getDefaultState() : trim);
        }

        int landingInnerX = firstX + direction * rise;
        int landingOuterX = landingInnerX + direction * 3;
        int landingY = east ? baseY - rise : baseY + rise + 1;
        int minX = Math.min(landingInnerX, landingOuterX);
        int maxX = Math.max(landingInnerX, landingOuterX);
        editSession.setBlocks(region(minX, landingY, centerZ - 2,
                maxX, landingY + 4, centerZ + 2), shell);
        editSession.setBlocks(region(minX, landingY + 1, centerZ - 1,
                maxX, landingY + 3, centerZ + 1), air);
        editSession.setBlocks(region(minX, landingY, centerZ - 1,
                maxX, landingY, centerZ + 1), trim);
        editSession.setBlocks(region(minX, landingY + 4, centerZ - 1,
                maxX, landingY + 4, centerZ + 1), BlockTypes.SEA_LANTERN.getDefaultState());

        int doorwayX = east ? width - 1 : 0;
        editSession.setBlocks(region(doorwayX, baseY + 1, centerZ - 1,
                doorwayX, baseY + 4, centerZ + 1), air);
    }

    private boolean touchesWall(CatacombLayoutGenerator.Layout layout, int x, int z) {
        return !isFloor(layout, x + 1, z) || !isFloor(layout, x - 1, z)
                || !isFloor(layout, x, z + 1) || !isFloor(layout, x, z - 1);
    }

    private boolean isOpen(CatacombLayoutGenerator.Layout layout, int x, int z, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (!isFloor(layout, x + dx, z + dz)) return false;
            }
        }
        return true;
    }

    private boolean isFloor(CatacombLayoutGenerator.Layout layout, int x, int z) {
        return x >= 0 && z >= 0 && x < width && z < length && layout.isFloor(x, z);
    }

    private int detailHash(int x, int z) {
        return Math.floorMod(x * 73428767 ^ z * 912931, 1009);
    }

    @SuppressWarnings("unchecked")
    private BlockState stairFacing(String facing) {
        BlockType stairs = BlockTypes.DEEPSLATE_TILE_STAIRS;
        Property<Object> property = (Property<Object>) stairs.getProperty("facing");
        return stairs.getDefaultState().with(property, property.getValueFor(facing));
    }

    private void configureFloor(DungeonDefinition result, DungeonDefinition source,
                                CatacombLayoutGenerator.Layout layout, int floorNumber,
                                int originZ, String worldName) {
        int baseY = 64;
        int rise = height - 1;
        int entranceLandingOuterX = -1 - rise - 3;
        int exitLandingOuterX = width + rise + 3;
        int entranceLandingY = baseY + rise + 1;
        int exitLandingY = baseY - rise;
        DungeonFloor floor = result.getOrCreateFloor(floorNumber);
        floor.setBounds(new Cuboid(worldName, entranceLandingOuterX, exitLandingY, originZ - 1,
                exitLandingOuterX, entranceLandingY + 4, originZ + length));
        double entranceZ = originZ + layout.getEntrance().getZ() + 0.5;
        double exitZ = originZ + layout.getExit().getZ() + 0.5;
        floor.setArrivalFromUp(new Point(worldName, 2.5, baseY + 1.0, entranceZ, -90.0F, 0.0F));
        floor.setArrivalFromDown(new Point(worldName, width - 3.5, baseY + 1.0, exitZ, 90.0F, 0.0F));
        floor.setUpTrigger(new Cuboid(worldName, -1, baseY + 1, (int) entranceZ - 1,
                0, baseY + 3, (int) entranceZ + 1));
        if (floorNumber < source.getGeneratedFloors()) {
            floor.setDownTrigger(new Cuboid(worldName, width - 1, baseY + 1, (int) exitZ - 1,
                    width, baseY + 3, (int) exitZ + 1));
        } else {
            floor.setDownTrigger(new Cuboid(worldName, width - 5, baseY + 1, (int) exitZ - 1,
                    width - 3, baseY + 3, (int) exitZ + 1));
        }

        int index = 0;
        if (roomMobAmount > 0) {
            for (CatacombLayoutGenerator.Cell room : layout.getRooms()) {
                floor.putMobSpawn(new MobSpawnDefinition("catacomb_room_" + index++, roomProvider, roomMob,
                        new Point(worldName, room.getX() + 0.5, baseY + 1.0,
                                originZ + room.getZ() + 0.5, 0.0F, 0.0F),
                        roomMobAmount, roomSpawnRadius, false,
                        RespawnMode.AFTER_DELAY, roomRespawnSeconds, roomSpawnIntervalTicks));
            }
        }
        if (source.hasGeneratedBosses()) {
            CatacombLayoutGenerator.Cell boss = layout.getBossRoom();
            floor.putMobSpawn(new MobSpawnDefinition("catacomb_boss", bossProvider, bossMob,
                    new Point(worldName, boss.getX() + 0.5, baseY + 1.0,
                            originZ + boss.getZ() + 0.5, 0.0F, 0.0F),
                    1, 0.0, true, RespawnMode.NEVER, 0, 0));
            floor.setBossId(bossMob);
        }
        if (floorNumber == 1) result.setEntrance(floor.getArrivalFromUp());
    }

    private CuboidRegion region(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new CuboidRegion(BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2));
    }
}
