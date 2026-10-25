package net.enelson.sopdungeons.integration;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.regions.CuboidRegion;
import net.enelson.sopdungeons.model.Cuboid;
import net.enelson.sopdungeons.model.DungeonModule;
import net.enelson.sopdungeons.model.RelativePoint;
import org.bukkit.Material;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;

public final class WorldEditBridge {
    public void fill(Cuboid bounds, org.bukkit.World bukkitWorld, Material material) throws IOException {
        World world = BukkitAdapter.adapt(bukkitWorld);
        CuboidRegion region = new CuboidRegion(world,
                BlockVector3.at(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()),
                BlockVector3.at(bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ()));
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            editSession.setBlocks(region, BukkitAdapter.adapt(material.createBlockData()));
        } catch (com.sk89q.worldedit.WorldEditException exception) {
            throw new IOException("WorldEdit could not fill the module envelope", exception);
        }
    }

    public void save(Cuboid bounds, File file) throws IOException {
        org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(bounds.getWorld());
        if (bukkitWorld == null) throw new IOException("Selection world is not loaded");
        World world = BukkitAdapter.adapt(bukkitWorld);
        BlockVector3 minimum = BlockVector3.at(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ());
        CuboidRegion region = new CuboidRegion(world, minimum,
                BlockVector3.at(bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ()));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(minimum);
        ForwardExtentCopy copy = new ForwardExtentCopy(world, region, clipboard, minimum);
        try {
            Operations.complete(copy);
        } catch (com.sk89q.worldedit.WorldEditException exception) {
            throw new IOException("WorldEdit could not copy the selected module", exception);
        }
        ClipboardFormat format = ClipboardFormats.findByAlias("sponge");
        if (format == null) throw new IOException("Sponge schematic format is unavailable");
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (ClipboardWriter writer = format.getWriter(new FileOutputStream(file))) {
            writer.write(clipboard);
        }
    }

    public Placement paste(DungeonModule module, org.bukkit.World bukkitWorld,
                           int entryX, int entryY, int entryZ, int rotation) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(module.getSchematic());
        if (format == null) throw new IOException("Unknown schematic format: " + module.getSchematic().getName());
        try (ClipboardReader reader = format.getReader(new FileInputStream(module.getSchematic()))) {
            Clipboard clipboard = reader.read();
            normalizeOrigin(clipboard);
            Placement placement = calculatePlacement(module, clipboard, bukkitWorld.getName(),
                    entryX, entryY, entryZ, rotation);
            AffineTransform transform = new AffineTransform().rotateY(-rotation);
            World world = BukkitAdapter.adapt(bukkitWorld);
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                holder.setTransform(transform);
                Operation operation = holder.createPaste(editSession)
                        .to(BlockVector3.at(placement.getPasteX(), placement.getPasteY(), placement.getPasteZ()))
                        .ignoreAirBlocks(false)
                        .build();
                try {
                    Operations.complete(operation);
                } catch (com.sk89q.worldedit.WorldEditException exception) {
                    throw new IOException("WorldEdit could not paste " + module.getId(), exception);
                }
            }

            return placement;
        }
    }

    public Placement preview(DungeonModule module, String worldName,
                             int entryX, int entryY, int entryZ, int rotation) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(module.getSchematic());
        if (format == null) throw new IOException("Unknown schematic format: " + module.getSchematic().getName());
        try (ClipboardReader reader = format.getReader(new FileInputStream(module.getSchematic()))) {
            Clipboard clipboard = reader.read();
            normalizeOrigin(clipboard);
            return calculatePlacement(module, clipboard, worldName, entryX, entryY, entryZ, rotation);
        }
    }

    private void normalizeOrigin(Clipboard clipboard) {
        clipboard.setOrigin(clipboard.getMinimumPoint());
    }

    private Placement calculatePlacement(DungeonModule module, Clipboard clipboard, String worldName,
                                         int entryX, int entryY, int entryZ, int rotation) {
        RelativePoint rotatedEntry = module.getEntry().rotate(rotation);
        RelativePoint rotatedExit = module.getExit().rotate(rotation);
        int pasteX = entryX - rotatedEntry.getX();
        int pasteY = entryY - rotatedEntry.getY();
        int pasteZ = entryZ - rotatedEntry.getZ();
        AffineTransform transform = new AffineTransform().rotateY(-rotation);
        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 minimum = clipboard.getMinimumPoint();
        BlockVector3 maximum = clipboard.getMaximumPoint();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x : new int[] {minimum.getBlockX(), maximum.getBlockX()}) {
            for (int y : new int[] {minimum.getBlockY(), maximum.getBlockY()}) {
                for (int z : new int[] {minimum.getBlockZ(), maximum.getBlockZ()}) {
                    Vector3 relative = Vector3.at(x - origin.getBlockX(), y - origin.getBlockY(),
                            z - origin.getBlockZ());
                    Vector3 rotated = transform.apply(relative);
                    int worldX = pasteX + (int) Math.round(rotated.getX());
                    int worldY = pasteY + (int) Math.round(rotated.getY());
                    int worldZ = pasteZ + (int) Math.round(rotated.getZ());
                    minX = Math.min(minX, worldX); minY = Math.min(minY, worldY); minZ = Math.min(minZ, worldZ);
                    maxX = Math.max(maxX, worldX); maxY = Math.max(maxY, worldY); maxZ = Math.max(maxZ, worldZ);
                }
            }
        }
        Cuboid bounds = new Cuboid(worldName, minX, minY, minZ, maxX, maxY, maxZ);
        return new Placement(bounds, pasteX, pasteY, pasteZ,
                pasteX + rotatedExit.getX(), pasteY + rotatedExit.getY(), pasteZ + rotatedExit.getZ());
    }

    public static final class Placement {
        private final Cuboid bounds;
        private final int pasteX;
        private final int pasteY;
        private final int pasteZ;
        private final int exitX;
        private final int exitY;
        private final int exitZ;

        private Placement(Cuboid bounds, int pasteX, int pasteY, int pasteZ,
                          int exitX, int exitY, int exitZ) {
            this.bounds = bounds;
            this.pasteX = pasteX;
            this.pasteY = pasteY;
            this.pasteZ = pasteZ;
            this.exitX = exitX;
            this.exitY = exitY;
            this.exitZ = exitZ;
        }

        public Cuboid getBounds() { return bounds; }
        public int getPasteX() { return pasteX; }
        public int getPasteY() { return pasteY; }
        public int getPasteZ() { return pasteZ; }
        public int getExitX() { return exitX; }
        public int getExitY() { return exitY; }
        public int getExitZ() { return exitZ; }
    }
}
