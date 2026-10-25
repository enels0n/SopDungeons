package net.enelson.sopdungeons.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public final class Cuboid {
    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Cuboid between(Location first, Location second) {
        if (first.getWorld() == null || second.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            throw new IllegalArgumentException("Selection points must be in the same world");
        }
        return new Cuboid(first.getWorld().getName(), first.getBlockX(), first.getBlockY(), first.getBlockZ(),
                second.getBlockX(), second.getBlockY(), second.getBlockZ());
    }

    public static Cuboid read(ConfigurationSection section) {
        if (section == null) return null;
        return new Cuboid(section.getString("world"), section.getInt("min.x"), section.getInt("min.y"),
                section.getInt("min.z"), section.getInt("max.x"), section.getInt("max.y"), section.getInt("max.z"));
    }

    public void write(ConfigurationSection section) {
        section.set("world", world);
        section.set("min.x", minX);
        section.set("min.y", minY);
        section.set("min.z", minZ);
        section.set("max.x", maxX);
        section.set("max.y", maxY);
        section.set("max.z", maxZ);
    }

    public boolean contains(Location location, String worldOverride) {
        if (location == null || location.getWorld() == null) return false;
        String expectedWorld = worldOverride == null ? world : worldOverride;
        return expectedWorld != null && expectedWorld.equals(location.getWorld().getName())
                && location.getX() >= minX && location.getX() < maxX + 1.0
                && location.getY() >= minY && location.getY() < maxY + 1.0
                && location.getZ() >= minZ && location.getZ() < maxZ + 1.0;
    }

    public String getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
}
