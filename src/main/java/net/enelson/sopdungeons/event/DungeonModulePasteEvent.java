package net.enelson.sopdungeons.event;

import net.enelson.sopdungeons.model.Cuboid;
import net.enelson.sopdungeons.model.ModuleType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

public final class DungeonModulePasteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String dungeonId;
    private final UUID sessionId;
    private final String worldName;
    private final int floor;
    private final String moduleId;
    private final ModuleType moduleType;
    private final Cuboid bounds;

    public DungeonModulePasteEvent(UUID sessionId, String dungeonId, String worldName, int floor, String moduleId,
                                   ModuleType moduleType, Cuboid bounds) {
        this.sessionId = sessionId;
        this.dungeonId = dungeonId;
        this.worldName = worldName;
        this.floor = floor;
        this.moduleId = moduleId;
        this.moduleType = moduleType;
        this.bounds = bounds;
    }

    public String getDungeonId() { return dungeonId; }
    public UUID getSessionId() { return sessionId; }
    public String getWorldName() { return worldName; }
    public int getFloor() { return floor; }
    public String getModuleId() { return moduleId; }
    public ModuleType getModuleType() { return moduleType; }
    public Cuboid getBounds() { return bounds; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
