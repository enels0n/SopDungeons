package net.enelson.sopdungeons.model;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

public final class DungeonDefinition {
    private final String id;
    private DungeonType type;
    private String world;
    private String template;
    private Point entrance;
    private Point exit;
    private Cuboid entranceTrigger;
    private int generatedFloors = 1;
    private int normalRooms = 5;
    private boolean generatedBosses;
    private GenerationMode generationMode = GenerationMode.MODULES;
    private boolean naturalSpawning = true;
    private final Map<Integer, DungeonFloor> floors = new TreeMap<Integer, DungeonFloor>();

    public DungeonDefinition(String id, DungeonType type) {
        this.id = id.toLowerCase();
        this.type = type;
    }

    public DungeonFloor getOrCreateFloor(int number) {
        DungeonFloor floor = floors.get(number);
        if (floor == null) {
            floor = new DungeonFloor(number);
            floors.put(number, floor);
        }
        return floor;
    }

    public String getId() { return id; }
    public DungeonType getType() { return type; }
    public void setType(DungeonType type) { this.type = type; }
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public Point getEntrance() { return entrance; }
    public void setEntrance(Point entrance) { this.entrance = entrance; }
    public Point getExit() { return exit; }
    public void setExit(Point exit) { this.exit = exit; }
    public Cuboid getEntranceTrigger() { return entranceTrigger; }
    public void setEntranceTrigger(Cuboid entranceTrigger) { this.entranceTrigger = entranceTrigger; }
    public int getGeneratedFloors() { return generatedFloors; }
    public void setGeneratedFloors(int generatedFloors) { this.generatedFloors = Math.max(1, generatedFloors); }
    public int getNormalRooms() { return normalRooms; }
    public void setNormalRooms(int normalRooms) { this.normalRooms = Math.max(0, normalRooms); }
    public boolean hasGeneratedBosses() { return generatedBosses; }
    public void setGeneratedBosses(boolean generatedBosses) { this.generatedBosses = generatedBosses; }
    public GenerationMode getGenerationMode() { return generationMode; }
    public void setGenerationMode(GenerationMode generationMode) {
        this.generationMode = generationMode == null ? GenerationMode.MODULES : generationMode;
    }
    public boolean isNaturalSpawning() { return naturalSpawning; }
    public void setNaturalSpawning(boolean naturalSpawning) { this.naturalSpawning = naturalSpawning; }
    public DungeonFloor getFloor(int number) { return floors.get(number); }
    public Collection<DungeonFloor> getFloors() { return floors.values(); }
    public int getLastFloorNumber() { return floors.isEmpty() ? 0 : ((TreeMap<Integer, DungeonFloor>) floors).lastKey(); }

    public boolean hasGeneratedLayout() {
        return type == DungeonType.GENERATED && world != null && entrance != null && !floors.isEmpty();
    }

    public void applyGeneratedLayout(DungeonDefinition generated) {
        world = generated.world;
        entrance = generated.entrance;
        floors.clear();
        floors.putAll(generated.floors);
    }

    public void clearGeneratedLayout() {
        if (type != DungeonType.GENERATED) return;
        world = null;
        entrance = null;
        floors.clear();
    }
}
