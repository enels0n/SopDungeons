package net.enelson.sopdungeons.event;

import net.enelson.sopdungeons.api.DungeonSessionView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DungeonSessionStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonSessionView session;

    public DungeonSessionStartEvent(DungeonSessionView session) { this.session = session; }
    public DungeonSessionView getSession() { return session; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
