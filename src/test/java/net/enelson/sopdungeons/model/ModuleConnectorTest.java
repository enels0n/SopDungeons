package net.enelson.sopdungeons.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ModuleConnectorTest {
    @Test
    public void rotatesCardinalDirection() {
        ModuleConnector connector = new ModuleConnector("entry", ModuleConnector.Kind.ENTRY,
                new RelativePoint(0, 1, 0), "WEST");

        assertEquals("WEST", connector.getRotatedDirection(0));
        assertEquals("NORTH", connector.getRotatedDirection(90));
        assertEquals("EAST", connector.getRotatedDirection(180));
        assertEquals("SOUTH", connector.getRotatedDirection(270));
        assertEquals("NORTH", connector.getRotatedDirection(450));
    }
}
