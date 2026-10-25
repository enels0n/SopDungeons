package net.enelson.sopdungeons.event;

import net.enelson.sopdungeons.api.DungeonSessionView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DungeonSessionEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonSessionView session;
    private final Set<UUID> participants;

    public DungeonSessionEndEvent(DungeonSessionView session) {
        this.session = session;
        this.participants = Collections.unmodifiableSet(new HashSet<UUID>(session.getParticipants()));
    }
    public DungeonSessionView getSession() { return session; }
    public Set<UUID> getParticipants() { return participants; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
