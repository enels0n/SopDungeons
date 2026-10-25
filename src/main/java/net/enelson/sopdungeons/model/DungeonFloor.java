package net.enelson.sopdungeons.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DungeonFloor {
    private final int number;
    private Cuboid bounds;
    private Point arrivalFromUp;
    private Point arrivalFromDown;
    private Cuboid upTrigger;
    private Cuboid downTrigger;
    private String bossId;
    private final Map<String, MobSpawnDefinition> mobSpawns = new LinkedHashMap<String, MobSpawnDefinition>();

    public DungeonFloor(int number) {
        this.number = number;
    }

    public int getNumber() { return number; }
    public Cuboid getBounds() { return bounds; }
    public void setBounds(Cuboid bounds) { this.bounds = bounds; }
    public Point getArrivalFromUp() { return arrivalFromUp; }
    public void setArrivalFromUp(Point arrivalFromUp) { this.arrivalFromUp = arrivalFromUp; }
    public Point getArrivalFromDown() { return arrivalFromDown; }
    public void setArrivalFromDown(Point arrivalFromDown) { this.arrivalFromDown = arrivalFromDown; }
    public Cuboid getUpTrigger() { return upTrigger; }
    public void setUpTrigger(Cuboid upTrigger) { this.upTrigger = upTrigger; }
    public Cuboid getDownTrigger() { return downTrigger; }
    public void setDownTrigger(Cuboid downTrigger) { this.downTrigger = downTrigger; }
    public String getBossId() { return bossId; }
    public void setBossId(String bossId) { this.bossId = bossId == null || bossId.trim().isEmpty() ? null : bossId; }
    public boolean requiresBoss() { return bossId != null; }
    public void putMobSpawn(MobSpawnDefinition spawn) { mobSpawns.put(spawn.getId(), spawn); }
    public MobSpawnDefinition getMobSpawn(String id) { return mobSpawns.get(id.toLowerCase()); }
    public boolean removeMobSpawn(String id) { return mobSpawns.remove(id.toLowerCase()) != null; }
    public Collection<MobSpawnDefinition> getMobSpawns() { return Collections.unmodifiableCollection(mobSpawns.values()); }
}
