package net.enelson.sopdungeons.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModuleMobSpawnTest {
    @Test
    public void normalizesModuleSpawnSettings() {
        ModuleMobSpawn spawn = new ModuleMobSpawn("BOSS", "vanilla", "HUSK",
                new RelativePoint(1, 2, 3), 0, -2.0, true, RespawnMode.NEVER, -1, -5);
        assertEquals("boss", spawn.getId());
        assertEquals("VANILLA", spawn.getProvider());
        assertEquals(1, spawn.getAmount());
        assertEquals(0.0, spawn.getRadius(), 0.0);
        assertEquals(0, spawn.getRespawnDelaySeconds());
        assertEquals(0, spawn.getSpawnIntervalTicks());
        assertTrue(spawn.isBoss());
    }
}
