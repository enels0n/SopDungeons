package net.enelson.sopdungeons.service.module;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class ModuleMazeLayoutGeneratorTest {
    @Test
    public void createsConnectedMazeWithEndpointsAndBranching() {
        for (int target : new int[] {20, 30, 60, 250}) {
            int seeds = target >= 250 ? 20 : 100;
            for (long seed = 0; seed < seeds; seed++) {
                assertValidMaze(new ModuleMazeLayoutGenerator(target, 0.12, seed).generate(), target);
            }
        }
    }

    private void assertValidMaze(ModuleMazeLayoutGenerator.Layout layout, int target) {
        assertTrue(layout.getCells().size() >= target);
        assertEquals(1, layout.getEntrance().getOpenings().size());
        assertEquals(1, layout.getExit().getOpenings().size());
        assertNotNull(layout.getBoss());

        Set<String> visited = new HashSet<String>();
        ArrayDeque<ModuleMazeLayoutGenerator.Cell> queue = new ArrayDeque<ModuleMazeLayoutGenerator.Cell>();
        queue.add(layout.getEntrance());
        while (!queue.isEmpty()) {
            ModuleMazeLayoutGenerator.Cell cell = queue.removeFirst();
            if (!visited.add(key(cell))) continue;
            for (ModuleMazeLayoutGenerator.Direction direction : cell.getOpenings()) {
                for (ModuleMazeLayoutGenerator.Cell candidate : layout.getCells()) {
                    if (candidate.getX() == cell.getX() + direction.getDx()
                            && candidate.getZ() == cell.getZ() + direction.getDz()) queue.add(candidate);
                }
            }
        }
        assertEquals(layout.getCells().size(), visited.size());
        assertTrue(shortestDistance(layout, layout.getEntrance(), layout.getExit())
                >= Math.max(8, layout.getCells().size() / 3));
        boolean branch = false;
        boolean turn = false;
        for (ModuleMazeLayoutGenerator.Cell cell : layout.getCells()) {
            branch |= cell.getOpenings().size() >= 3;
            turn |= cell.getOpenings().size() == 2
                    && !(cell.getOpenings().contains(ModuleMazeLayoutGenerator.Direction.NORTH)
                    && cell.getOpenings().contains(ModuleMazeLayoutGenerator.Direction.SOUTH))
                    && !(cell.getOpenings().contains(ModuleMazeLayoutGenerator.Direction.EAST)
                    && cell.getOpenings().contains(ModuleMazeLayoutGenerator.Direction.WEST));
        }
        assertTrue(branch);
        assertTrue(turn);
    }

    private String key(ModuleMazeLayoutGenerator.Cell cell) {
        return cell.getX() + ":" + cell.getZ();
    }

    private int shortestDistance(ModuleMazeLayoutGenerator.Layout layout,
                                 ModuleMazeLayoutGenerator.Cell start,
                                 ModuleMazeLayoutGenerator.Cell target) {
        ArrayDeque<ModuleMazeLayoutGenerator.Cell> queue = new ArrayDeque<ModuleMazeLayoutGenerator.Cell>();
        java.util.Map<String, Integer> distances = new java.util.HashMap<String, Integer>();
        queue.add(start);
        distances.put(key(start), 0);
        while (!queue.isEmpty()) {
            ModuleMazeLayoutGenerator.Cell cell = queue.removeFirst();
            if (cell == target) return distances.get(key(cell));
            for (ModuleMazeLayoutGenerator.Direction direction : cell.getOpenings()) {
                for (ModuleMazeLayoutGenerator.Cell candidate : layout.getCells()) {
                    if (candidate.getX() != cell.getX() + direction.getDx()
                            || candidate.getZ() != cell.getZ() + direction.getDz()
                            || distances.containsKey(key(candidate))) continue;
                    distances.put(key(candidate), distances.get(key(cell)) + 1);
                    queue.add(candidate);
                }
            }
        }
        return -1;
    }
}
