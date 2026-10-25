package net.enelson.sopdungeons.api;

import java.util.Set;
import java.util.UUID;

public interface DungeonSessionView {
    UUID getId();
    String getDungeonId();
    String getWorldName();
    boolean isTemporary();
    Set<UUID> getParticipants();
    Set<Integer> getUnlockedFloors();
}
