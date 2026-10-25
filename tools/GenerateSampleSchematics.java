import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NamedTag;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GenerateSampleSchematics {
    private static final int DATA_VERSION_1_16_5 = 2586;
    private static final int SIZE = 15;
    private static final int HEIGHT = 7;

    private GenerateSampleSchematics() {}

    public static void main(String[] args) throws Exception {
        File output = new File(args.length == 0 ? "examples/generated-dungeon/modules" : args[0]);
        if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("Cannot create " + output);

        write(output, "maze_entrance.schem", "minecraft:stone_bricks", "minecraft:lime_wool", 4, "WE");
        write(output, "maze_transition.schem", "minecraft:stone_bricks", "minecraft:lapis_block", 4, "WE");
        write(output, "maze_exit.schem", "minecraft:stone_bricks", "minecraft:yellow_wool", 4, "WE");
        write(output, "maze_return_portal.schem", "minecraft:deepslate_bricks", "minecraft:crying_obsidian", 5, "W");
        writeStairs(output, "maze_stairs_up.schem", "minecraft:lapis_block");
        writeStairs(output, "maze_stairs_down.schem", "minecraft:gold_block");
        write(output, "maze_corridor_straight.schem", "minecraft:stone_bricks", "minecraft:polished_andesite", 2, "WE");
        write(output, "maze_corridor_corner.schem", "minecraft:stone_bricks", "minecraft:polished_andesite", 2, "WS");
        write(output, "maze_junction_t.schem", "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks", 3, "WES");
        write(output, "maze_junction_cross.schem", "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks", 3, "WENS");
        write(output, "maze_room_straight.schem", "minecraft:mossy_stone_bricks", "minecraft:polished_andesite", 5, "WE");
        write(output, "maze_room_corner.schem", "minecraft:mossy_stone_bricks", "minecraft:polished_andesite", 5, "WS");
        write(output, "maze_room_t.schem", "minecraft:mossy_stone_bricks", "minecraft:polished_andesite", 5, "WES");
        write(output, "maze_dead_end.schem", "minecraft:mossy_stone_bricks", "minecraft:cobblestone", 4, "W");
        write(output, "maze_boss_straight.schem", "minecraft:nether_bricks", "minecraft:gold_block", 5, "WE");
        write(output, "maze_boss_corner.schem", "minecraft:nether_bricks", "minecraft:gold_block", 5, "WS");
        write(output, "maze_boss_t.schem", "minecraft:nether_bricks", "minecraft:gold_block", 5, "WES");
        write(output, "maze_boss_cross.schem", "minecraft:nether_bricks", "minecraft:gold_block", 5, "WENS");
        System.out.println("Generated and verified maze schematics in " + output.getAbsolutePath());
    }

    private static void write(File output, String name, String shell, String marker,
                              int hubRadius, String openings) throws Exception {
        Map<String, Integer> palette = new LinkedHashMap<String, Integer>();
        int air = palette(palette, "minecraft:air");
        int shellId = palette(palette, shell);
        int markerId = palette(palette, marker);
        int floorId = palette(palette, "minecraft:stone_bricks");
        int accentId = palette(palette, "minecraft:polished_andesite");
        int lightId = palette(palette, "minecraft:sea_lantern");
        byte[] blocks = new byte[SIZE * HEIGHT * SIZE];
        int center = SIZE / 2;

        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean walkable = Math.abs(x - center) <= hubRadius && Math.abs(z - center) <= hubRadius;
                if (openings.indexOf('W') >= 0 && x <= center && Math.abs(z - center) <= 2) walkable = true;
                if (openings.indexOf('E') >= 0 && x >= center && Math.abs(z - center) <= 2) walkable = true;
                if (openings.indexOf('N') >= 0 && z <= center && Math.abs(x - center) <= 2) walkable = true;
                if (openings.indexOf('S') >= 0 && z >= center && Math.abs(x - center) <= 2) walkable = true;
                for (int y = 0; y < HEIGHT; y++) {
                    int block = shellId;
                    if (walkable) {
                        if (y == 0) block = ((x + z) % 6 == 0) ? accentId : floorId;
                        else if (y < HEIGHT - 1) block = air;
                    }
                    set(blocks, x, y, z, block);
                }
            }
        }

        for (int x = center - 1; x <= center + 1; x++) {
            for (int z = center - 1; z <= center + 1; z++) set(blocks, x, 0, z, markerId);
        }
        set(blocks, center, HEIGHT - 1, center, lightId);
        if (hubRadius >= 4) {
            set(blocks, center - 3, HEIGHT - 1, center - 3, lightId);
            set(blocks, center + 3, HEIGHT - 1, center - 3, lightId);
            set(blocks, center - 3, HEIGHT - 1, center + 3, lightId);
            set(blocks, center + 3, HEIGHT - 1, center + 3, lightId);
        }

        writeSchematic(new File(output, name), palette, blocks, HEIGHT);
    }

    private static void writeStairs(File output, String name, String marker) throws Exception {
        final int height = 12;
        Map<String, Integer> palette = new LinkedHashMap<String, Integer>();
        int air = palette(palette, "minecraft:air");
        int shell = palette(palette, "minecraft:stone_bricks");
        int floor = palette(palette, "minecraft:polished_andesite");
        int stairs = palette(palette,
                "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]");
        int markerId = palette(palette, marker);
        int light = palette(palette, "minecraft:sea_lantern");
        byte[] blocks = new byte[SIZE * height * SIZE];
        Arrays.fill(blocks, (byte) shell);
        int center = SIZE / 2;
        for (int x = 0; x < SIZE; x++) {
            int floorY = 7 - ((x + 1) / 2);
            for (int z = center - 2; z <= center + 2; z++) {
                set(blocks, x, floorY, z, x % 2 == 1 ? stairs : floor);
                for (int y = floorY + 1; y <= floorY + 3; y++) set(blocks, x, y, z, air);
            }
            if (x % 4 == 0) set(blocks, x, floorY + 4, center, light);
        }
        set(blocks, 1, 8, center - 3, markerId);
        set(blocks, 13, 1, center + 3, markerId);
        writeSchematic(new File(output, name), palette, blocks, height);
    }

    private static void writeSchematic(File target, Map<String, Integer> palette, byte[] blocks,
                                       int height) throws Exception {
        try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(target)))) {
            data.writeByte(10);
            data.writeUTF("Schematic");
            intTag(data, "Version", 2);
            intTag(data, "DataVersion", DATA_VERSION_1_16_5);
            shortTag(data, "Width", SIZE);
            shortTag(data, "Height", height);
            shortTag(data, "Length", SIZE);
            intArrayTag(data, "Offset", new int[] {0, 0, 0});
            compoundStart(data, "Metadata");
            stringTag(data, "Name", target.getName());
            stringTag(data, "Author", "SopDungeons");
            data.writeByte(0);
            intTag(data, "PaletteMax", palette.size());
            compoundStart(data, "Palette");
            for (Map.Entry<String, Integer> entry : palette.entrySet()) intTag(data, entry.getKey(), entry.getValue());
            data.writeByte(0);
            byteArrayTag(data, "BlockData", blocks);
            emptyCompoundList(data, "BlockEntities");
            emptyCompoundList(data, "Entities");
            data.writeByte(0);
        }
        verify(target, height);
    }

    private static void verify(File file, int height) throws Exception {
        try (NBTInputStream input = new NBTInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            NamedTag named = input.readNamedTag();
            if (!"Schematic".equals(named.getName()) || !(named.getTag() instanceof CompoundTag)) {
                throw new IllegalStateException("Invalid schematic root: " + file.getName());
            }
            CompoundTag root = (CompoundTag) named.getTag();
            if (root.getInt("Version") != 2 || root.getShort("Width") != SIZE
                    || root.getShort("Height") != height || root.getShort("Length") != SIZE
                    || root.getByteArray("BlockData").length != SIZE * height * SIZE) {
                throw new IllegalStateException("Invalid schematic data: " + file.getName());
            }
        }
    }

    private static int palette(Map<String, Integer> palette, String block) {
        Integer existing = palette.get(block);
        if (existing != null) return existing;
        int id = palette.size();
        palette.put(block, id);
        return id;
    }

    private static void set(byte[] blocks, int x, int y, int z, int id) {
        blocks[x + z * SIZE + y * SIZE * SIZE] = (byte) id;
    }

    private static void compoundStart(DataOutputStream data, String name) throws Exception { data.writeByte(10); data.writeUTF(name); }
    private static void shortTag(DataOutputStream data, String name, int value) throws Exception { data.writeByte(2); data.writeUTF(name); data.writeShort(value); }
    private static void intTag(DataOutputStream data, String name, int value) throws Exception { data.writeByte(3); data.writeUTF(name); data.writeInt(value); }
    private static void stringTag(DataOutputStream data, String name, String value) throws Exception { data.writeByte(8); data.writeUTF(name); data.writeUTF(value); }
    private static void byteArrayTag(DataOutputStream data, String name, byte[] value) throws Exception { data.writeByte(7); data.writeUTF(name); data.writeInt(value.length); data.write(value); }
    private static void intArrayTag(DataOutputStream data, String name, int[] value) throws Exception { data.writeByte(11); data.writeUTF(name); data.writeInt(value.length); for (int number : value) data.writeInt(number); }
    private static void emptyCompoundList(DataOutputStream data, String name) throws Exception { data.writeByte(9); data.writeUTF(name); data.writeByte(10); data.writeInt(0); }
}
