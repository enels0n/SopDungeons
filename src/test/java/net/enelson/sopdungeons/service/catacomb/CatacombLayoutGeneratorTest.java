package net.enelson.sopdungeons.service.catacomb;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertTrue;

public class CatacombLayoutGeneratorTest {
    @Test
    public void connectsEntranceExitAndBossRoom() {
        int maxSpacing = 20;
        CatacombLayoutGenerator.Config config = new CatacombLayoutGenerator.Config(
                140, 140, 5, 7, 14, maxSpacing, 8, 0.08, 8, 12345L);
        CatacombLayoutGenerator.Layout layout = new CatacombLayoutGenerator(config).generate();

        assertTrue(reachable(layout, layout.getEntrance(), layout.getExit()));
        assertTrue(layout.isFloor(layout.getBossRoom().getX(), layout.getBossRoom().getZ()));
        int bossDistance = Math.abs(layout.getExit().getX() - layout.getBossRoom().getX())
                + Math.abs(layout.getExit().getZ() - layout.getBossRoom().getZ());
        assertTrue("Boss room should be on the final maze edge", bossDistance <= maxSpacing * 2 + 2);
    }

    private boolean reachable(CatacombLayoutGenerator.Layout layout,
                              CatacombLayoutGenerator.Cell start,
                              CatacombLayoutGenerator.Cell target) {
        boolean[][] visited = new boolean[layout.getLength()][layout.getWidth()];
        Queue<int[]> queue = new ArrayDeque<int[]>();
        queue.add(new int[] {start.getX(), start.getZ()});
        visited[start.getZ()][start.getX()] = true;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] current = queue.remove();
            if (current[0] == target.getX() && current[1] == target.getZ()) return true;
            for (int[] direction : directions) {
                int x = current[0] + direction[0];
                int z = current[1] + direction[1];
                if (x < 0 || z < 0 || x >= layout.getWidth() || z >= layout.getLength()
                        || visited[z][x] || !layout.isFloor(x, z)) continue;
                visited[z][x] = true;
                queue.add(new int[] {x, z});
            }
        }
        return false;
    }
}
