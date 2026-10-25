package net.enelson.sopdungeons.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MobSpawnDefinitionTest {
    @Test
    public void normalizesUnsafeNumericValues() {
        MobSpawnDefinition spawn = new MobSpawnDefinition("GROUP", "elitemobs", "mob.yml", null,
                0, -5.0, false, RespawnMode.WHEN_CLEARED, -10);
        assertEquals("group", spawn.getId());
        assertEquals("ELITEMOBS", spawn.getProvider());
        assertEquals(1, spawn.getAmount());
        assertEquals(0.0, spawn.getRadius(), 0.0);
        assertEquals(0, spawn.getRespawnDelaySeconds());
    }

    @Test
    public void normalizesPlayerRadiusRange() {
        MobSpawnDefinition spawn = new MobSpawnDefinition("ambient", "ELITEMOBS", "mob.yml", null,
                3, 30.0, false, RespawnMode.AFTER_DELAY, 20,
                SpawnPlacement.AROUND_PLAYERS, 32.0, 12.0, -5);
        assertEquals(SpawnPlacement.AROUND_PLAYERS, spawn.getPlacement());
        assertEquals(32.0, spawn.getMinRadius(), 0.0);
        assertEquals(32.0, spawn.getMaxRadius(), 0.0);
        assertEquals(3, spawn.getAmount());
        assertEquals(0, spawn.getSpawnIntervalTicks());
    }
}
