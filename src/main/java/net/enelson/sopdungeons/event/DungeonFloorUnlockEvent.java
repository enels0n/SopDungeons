package net.enelson.sopdungeons.event;

import net.enelson.sopdungeons.api.DungeonSessionView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DungeonFloorUnlockEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonSessionView session;
    private final int completedFloor;
    private final int unlockedFloor;
    private final String bossId;

    public DungeonFloorUnlockEvent(DungeonSessionView session, int completedFloor, int unlockedFloor, String bossId) {
        this.session = session;
        this.completedFloor = completedFloor;
        this.unlockedFloor = unlockedFloor;
        this.bossId = bossId;
    }

    public DungeonSessionView getSession() { return session; }
    public int getCompletedFloor() { return completedFloor; }
    public int getUnlockedFloor() { return unlockedFloor; }
    public String getBossId() { return bossId; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
