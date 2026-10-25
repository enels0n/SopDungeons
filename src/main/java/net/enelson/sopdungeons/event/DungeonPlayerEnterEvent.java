package net.enelson.sopdungeons.event;

import net.enelson.sopdungeons.api.DungeonSessionView;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DungeonPlayerEnterEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonSessionView session;
    private final Player player;

    public DungeonPlayerEnterEvent(DungeonSessionView session, Player player) {
        this.session = session;
        this.player = player;
    }

    public DungeonSessionView getSession() { return session; }
    public Player getPlayer() { return player; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
