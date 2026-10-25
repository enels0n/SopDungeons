package net.enelson.sopdungeons.model;

import org.junit.Test;

import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class DungeonModuleTest {
    @Test
    public void allowsSingleConnectorStairModules() {
        ModuleConnector inside = new ModuleConnector("inside", ModuleConnector.Kind.ENTRY,
                new RelativePoint(14, 2, 7), "EAST");
        DungeonModule module = new DungeonModule("stairs", ModuleType.STAIRS_UP,
                new File("stairs.schem"), Collections.singletonList(inside), 1, 1, 0,
                Collections.singletonList(0), null, Collections.emptyList());

        assertEquals(inside, module.getEntryConnector());
        assertEquals(inside, module.getExitConnector());
        assertEquals(inside.getPoint(), module.getExit());
    }
}
