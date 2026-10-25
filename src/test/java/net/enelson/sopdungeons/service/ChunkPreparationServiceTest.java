package net.enelson.sopdungeons.service;

import net.enelson.sopdungeons.model.Cuboid;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class ChunkPreparationServiceTest {
    @Test
    public void calculatesEveryIntersectingChunkIncludingNegativeCoordinates() {
        List<ChunkPreparationService.ChunkCoordinate> chunks = ChunkPreparationService.coordinates(
                new Cuboid("world", -17, 0, -1, 16, 10, 31));

        assertEquals(12, chunks.size());
        assertEquals(-2, chunks.get(0).getX());
        assertEquals(-1, chunks.get(0).getZ());
        assertEquals(1, chunks.get(chunks.size() - 1).getX());
        assertEquals(1, chunks.get(chunks.size() - 1).getZ());
    }
}
