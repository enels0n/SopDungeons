package dev.sop.catacombs;

/** Logical 3D material selection. Translate these values to Bukkit Material in an adapter. */
public final class CatacombPalette {
    public enum Block { AIR, STONE_BRICKS, DEEPSLATE_BRICKS, POLISHED_DEEPSLATE,
                        DEEPSLATE_TILES, CHISELED_DEEPSLATE, CRACKED_STONE_BRICKS }
    private CatacombPalette() {}
    public static Block at(CatacombGenerator.Layout map, int x, int y, int z, int height) {
        if (height < 7 || y < 0 || y >= height) throw new IllegalArgumentException("Invalid height or y");
        if (y == 0) return ((x % 8 == 0) || (z % 8 == 0)) ? Block.POLISHED_DEEPSLATE : Block.DEEPSLATE_TILES;
        if (y == height-1) return ((x % 9 == 0) || (z % 9 == 0)) ? Block.POLISHED_DEEPSLATE : Block.DEEPSLATE_BRICKS;
        if (map.isFloor(x,z)) return Block.AIR;
        if (y == 1 || y == height-2) return Block.POLISHED_DEEPSLATE;
        if ((x % 11 == 0 || z % 11 == 0) && y >= 2 && y <= height-3) return Block.DEEPSLATE_BRICKS;
        if (y == height/2 && (x+z)%13==0) return Block.CHISELED_DEEPSLATE;
        return Block.STONE_BRICKS;
    }
}
