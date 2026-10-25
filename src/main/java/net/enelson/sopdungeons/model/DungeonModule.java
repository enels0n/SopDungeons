package net.enelson.sopdungeons.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DungeonModule {
    private final String id;
    private final ModuleType type;
    private final File schematic;
    private final List<ModuleConnector> connectors;
    private final int weight;
    private final int minDepth;
    private final int maxUses;
    private final List<Integer> rotations;
    private final String bossId;
    private final List<ModuleMobSpawn> mobSpawns;

    public DungeonModule(String id, ModuleType type, File schematic, List<ModuleConnector> connectors,
                         int weight, int minDepth, int maxUses, List<Integer> rotations, String bossId,
                         List<ModuleMobSpawn> mobSpawns) {
        this.id = id;
        this.type = type;
        this.schematic = schematic;
        this.connectors = Collections.unmodifiableList(new ArrayList<ModuleConnector>(connectors));
        this.weight = Math.max(1, weight);
        this.minDepth = Math.max(1, minDepth);
        this.maxUses = Math.max(0, maxUses);
        this.rotations = Collections.unmodifiableList(new ArrayList<Integer>(rotations));
        this.bossId = bossId;
        this.mobSpawns = Collections.unmodifiableList(new ArrayList<ModuleMobSpawn>(mobSpawns));
    }

    public String getId() { return id; }
    public ModuleType getType() { return type; }
    public File getSchematic() { return schematic; }
    public List<ModuleConnector> getConnectors() { return connectors; }
    public RelativePoint getEntry() { return connector(ModuleConnector.Kind.ENTRY).getPoint(); }
    public RelativePoint getExit() {
        ModuleConnector exit = findConnector(ModuleConnector.Kind.EXIT);
        return (exit == null ? getEntryConnector() : exit).getPoint();
    }
    public ModuleConnector getEntryConnector() { return connector(ModuleConnector.Kind.ENTRY); }
    public ModuleConnector getExitConnector() {
        ModuleConnector exit = findConnector(ModuleConnector.Kind.EXIT);
        return exit == null ? getEntryConnector() : exit;
    }
    public int getWeight() { return weight; }
    public int getMinDepth() { return minDepth; }
    public int getMaxUses() { return maxUses; }
    public List<Integer> getRotations() { return rotations; }
    public String getBossId() { return bossId; }
    public List<ModuleMobSpawn> getMobSpawns() { return mobSpawns; }

    public List<ModuleConnector> getGenericConnectors() {
        List<ModuleConnector> result = new ArrayList<ModuleConnector>();
        for (ModuleConnector connector : connectors) {
            if (connector.getKind() == ModuleConnector.Kind.GENERIC) result.add(connector);
        }
        return Collections.unmodifiableList(result);
    }

    private ModuleConnector connector(ModuleConnector.Kind kind) {
        ModuleConnector connector = findConnector(kind);
        if (connector != null) return connector;
        throw new IllegalStateException("Module " + id + " has no " + kind + " connector");
    }

    private ModuleConnector findConnector(ModuleConnector.Kind kind) {
        for (ModuleConnector connector : connectors) if (connector.getKind() == kind) return connector;
        return null;
    }
}
