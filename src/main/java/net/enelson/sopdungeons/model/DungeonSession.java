package net.enelson.sopdungeons.model;

import net.enelson.sopdungeons.api.DungeonSessionView;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DungeonSession implements DungeonSessionView {
    private final UUID id;
    private final DungeonDefinition definition;
    private final String worldName;
    private final boolean temporary;
    private final Set<UUID> participants = new HashSet<UUID>();
    private final Set<Integer> unlockedFloors = new HashSet<Integer>();
    private long emptySince;

    public DungeonSession(UUID id, DungeonDefinition definition, String worldName, boolean temporary) {
        this.id = id;
        this.definition = definition;
        this.worldName = worldName;
        this.temporary = temporary;
        unlockedFloors.add(1);
        unlockFollowingUngatedFloors(1);
    }

    public UUID getId() { return id; }
    public String getDungeonId() { return definition.getId(); }
    public String getWorldName() { return worldName; }
    public boolean isTemporary() { return temporary; }
    public Set<UUID> getParticipants() { return Collections.unmodifiableSet(new HashSet<UUID>(participants)); }
    public DungeonDefinition getDefinition() { return definition; }
    public void addParticipant(UUID playerId) { participants.add(playerId); emptySince = 0L; }
    public void removeParticipant(UUID playerId) { participants.remove(playerId); }
    public boolean hasParticipant(UUID playerId) { return participants.contains(playerId); }
    public boolean isFloorUnlocked(int number) { return definition.getType() == DungeonType.STATIC || unlockedFloors.contains(number); }
    public boolean unlockFloor(int number) { return unlockedFloors.add(number); }
    public void unlockFollowingUngatedFloors(int fromFloor) {
        int current = fromFloor;
        while (true) {
            DungeonFloor floor = definition.getFloor(current);
            DungeonFloor next = definition.getFloor(current + 1);
            if (floor == null || next == null || floor.requiresBoss()) return;
            unlockedFloors.add(next.getNumber());
            current++;
        }
    }
    public Set<Integer> getUnlockedFloors() { return Collections.unmodifiableSet(new HashSet<Integer>(unlockedFloors)); }
    public long getEmptySince() { return emptySince; }
    public void setEmptySince(long emptySince) { this.emptySince = emptySince; }
}
