package net.enelson.sopdungeons.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CuboidTest {
    @Test
    public void normalizesCoordinates() {
        Cuboid cuboid = new Cuboid("world", 10, 20, 30, -5, 2, 4);
        assertEquals(-5, cuboid.getMinX());
        assertEquals(2, cuboid.getMinY());
        assertEquals(4, cuboid.getMinZ());
        assertEquals(10, cuboid.getMaxX());
        assertEquals(20, cuboid.getMaxY());
        assertEquals(30, cuboid.getMaxZ());
    }
}
