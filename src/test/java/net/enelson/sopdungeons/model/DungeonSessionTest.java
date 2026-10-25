package net.enelson.sopdungeons.model;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DungeonSessionTest {
    @Test
    public void opensUngatedFloorsUntilNextBossGate() {
        DungeonDefinition definition = new DungeonDefinition("test", DungeonType.TEMPLATE);
        definition.getOrCreateFloor(1);
        definition.getOrCreateFloor(2).setBossId("guardian.yml");
        definition.getOrCreateFloor(3);

        DungeonSession session = new DungeonSession(UUID.randomUUID(), definition, "instance", true);

        assertTrue(session.isFloorUnlocked(1));
        assertTrue(session.isFloorUnlocked(2));
        assertFalse(session.isFloorUnlocked(3));

        assertTrue(session.unlockFloor(3));
        session.unlockFollowingUngatedFloors(3);
        assertTrue(session.isFloorUnlocked(3));
    }
}
