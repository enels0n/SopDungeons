package net.enelson.sopdungeons.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class GeneratedLayoutPersistenceTest {
    @Test
    public void appliesAndClearsOnlyGeneratedRuntimeLayout() {
        DungeonDefinition source = new DungeonDefinition("crypt", DungeonType.GENERATED);
        Point returnPoint = new Point("spawn", 1, 64, 1, 0, 0);
        source.setExit(returnPoint);
        assertFalse(source.hasGeneratedLayout());

        DungeonDefinition runtime = new DungeonDefinition("crypt", DungeonType.GENERATED);
        runtime.setWorld("sopdungeon_crypt_saved");
        runtime.setEntrance(new Point("sopdungeon_crypt_saved", 5, 80, 5, 0, 0));
        runtime.getOrCreateFloor(1).setBounds(
                new Cuboid("sopdungeon_crypt_saved", 0, 70, 0, 100, 100, 100));

        source.applyGeneratedLayout(runtime);
        assertTrue(source.hasGeneratedLayout());
        assertEquals("sopdungeon_crypt_saved", source.getWorld());
        assertNotNull(source.getFloor(1));
        assertEquals(returnPoint, source.getExit());

        source.clearGeneratedLayout();
        assertFalse(source.hasGeneratedLayout());
        assertEquals(returnPoint, source.getExit());
    }
}
