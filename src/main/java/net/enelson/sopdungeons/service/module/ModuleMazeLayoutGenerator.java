package net.enelson.sopdungeons.service.module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class ModuleMazeLayoutGenerator {
    private final int targetCells;
    private final double loopChance;
    private final Random random;

    public ModuleMazeLayoutGenerator(int targetCells, double loopChance, long seed) {
        this.targetCells = Math.max(8, targetCells);
        this.loopChance = Math.max(0.0, Math.min(0.35, loopChance));
        this.random = new Random(seed);
    }

    public Layout generate() {
        Layout fallback = null;
        for (int attempt = 0; attempt < 64; attempt++) {
            Layout layout = generateCandidate();
            fallback = layout;
            if (hasBranch(layout) && hasTurn(layout) && hasLongRoute(layout)) return layout;
        }
        return fallback;
    }

    private Layout generateCandidate() {
        int width = Math.max(5, (int) Math.ceil(Math.sqrt(targetCells * 1.25)));
        int height = Math.max(5, (int) Math.ceil(targetCells / (double) width));
        Map<Position, Cell> cells = new LinkedHashMap<Position, Cell>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) add(cells, x, z);
        }
        Cell entrance = cells.get(new Position(0, height / 2));
        carveDepthFirst(cells, entrance);

        Cell protectedExit = farthestDeadEnd(cells, entrance);
        addLoops(cells, entrance, protectedExit);
        Cell boss = neighbour(protectedExit, cells);
        return new Layout(cells.values(), entrance, protectedExit, boss);
    }

    private void carveDepthFirst(Map<Position, Cell> cells, Cell entrance) {
        Set<Cell> visited = new HashSet<Cell>();
        Deque<Cell> stack = new ArrayDeque<Cell>();
        visited.add(entrance);
        stack.push(entrance);
        while (!stack.isEmpty()) {
            Cell current = stack.peek();
            List<Direction> available = new ArrayList<Direction>();
            if (current != entrance || current.openings.isEmpty()) {
                for (Direction direction : Direction.values()) {
                    Cell neighbour = cell(cells, current, direction);
                    if (neighbour != null && !visited.contains(neighbour)) available.add(direction);
                }
            }
            if (available.isEmpty()) {
                stack.pop();
                continue;
            }
            Direction direction = available.get(random.nextInt(available.size()));
            Cell next = cell(cells, current, direction);
            connect(current, next, direction);
            visited.add(next);
            stack.push(next);
        }
        if (visited.size() != cells.size()) throw new IllegalStateException("Module maze grid is disconnected");
    }

    private boolean hasBranch(Layout layout) {
        for (Cell cell : layout.cells) if (cell.openings.size() >= 3) return true;
        return false;
    }

    private boolean hasTurn(Layout layout) {
        for (Cell cell : layout.cells) {
            if (cell.openings.size() != 2) continue;
            boolean vertical = cell.openings.contains(Direction.NORTH)
                    && cell.openings.contains(Direction.SOUTH);
            boolean horizontal = cell.openings.contains(Direction.EAST)
                    && cell.openings.contains(Direction.WEST);
            if (!vertical && !horizontal) return true;
        }
        return false;
    }

    private boolean hasLongRoute(Layout layout) {
        Map<Position, Cell> cells = new HashMap<Position, Cell>();
        for (Cell cell : layout.cells) cells.put(new Position(cell.x, cell.z), cell);
        Integer distance = distances(cells, layout.entrance).get(layout.exit);
        return distance != null && distance >= Math.max(8, layout.cells.size() / 3);
    }

    private void addLoops(Map<Position, Cell> cells, Cell entrance, Cell exit) {
        for (Cell cell : cells.values()) {
            for (Direction direction : new Direction[] {Direction.EAST, Direction.SOUTH}) {
                Cell neighbour = cell(cells, cell, direction);
                if (neighbour == null || cell.openings.contains(direction)
                        || cell == entrance || neighbour == entrance
                        || cell == exit || neighbour == exit) continue;
                if (random.nextDouble() >= loopChance) continue;
                connect(cell, neighbour, direction);
                Integer route = distances(cells, entrance).get(exit);
                if (route == null || route < Math.max(8, cells.size() / 3)) {
                    disconnect(cell, neighbour, direction);
                }
            }
        }
    }

    private void disconnect(Cell first, Cell second, Direction direction) {
        first.openings.remove(direction);
        second.openings.remove(direction.opposite());
    }

    private Cell cell(Map<Position, Cell> cells, Cell origin, Direction direction) {
        return cells.get(new Position(origin.x + direction.dx, origin.z + direction.dz));
    }

    private Cell farthestDeadEnd(Map<Position, Cell> cells, Cell entrance) {
        final Map<Cell, Integer> distances = distances(cells, entrance);
        List<Cell> candidates = new ArrayList<Cell>();
        for (Cell cell : cells.values()) {
            if (cell != entrance && cell.openings.size() == 1) candidates.add(cell);
        }
        if (candidates.isEmpty()) {
            for (Cell cell : cells.values()) if (cell != entrance) candidates.add(cell);
        }
        Collections.sort(candidates, new Comparator<Cell>() {
            @Override
            public int compare(Cell first, Cell second) {
                return distances.get(second) - distances.get(first);
            }
        });
        return candidates.get(0);
    }

    private Map<Cell, Integer> distances(Map<Position, Cell> cells, Cell entrance) {
        Map<Cell, Integer> result = new HashMap<Cell, Integer>();
        Deque<Cell> queue = new ArrayDeque<Cell>();
        result.put(entrance, 0);
        queue.add(entrance);
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            for (Direction direction : cell.openings) {
                Cell next = cells.get(new Position(cell.x + direction.dx, cell.z + direction.dz));
                if (next == null || result.containsKey(next)) continue;
                result.put(next, result.get(cell) + 1);
                queue.addLast(next);
            }
        }
        return result;
    }

    private Cell neighbour(Cell cell, Map<Position, Cell> cells) {
        Direction direction = cell.openings.iterator().next();
        return cells.get(new Position(cell.x + direction.dx, cell.z + direction.dz));
    }

    private Cell add(Map<Position, Cell> cells, int x, int z) {
        Cell cell = new Cell(x, z);
        cells.put(new Position(x, z), cell);
        return cell;
    }

    private void connect(Cell first, Cell second, Direction direction) {
        first.openings.add(direction);
        second.openings.add(direction.opposite());
    }

    public enum Direction {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        private final int dx;
        private final int dz;

        Direction(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public Direction opposite() { return values()[(ordinal() + 2) % 4]; }
        public int getDx() { return dx; }
        public int getDz() { return dz; }
    }

    public static final class Cell {
        private final int x;
        private final int z;
        private final EnumSet<Direction> openings = EnumSet.noneOf(Direction.class);

        private Cell(int x, int z) { this.x = x; this.z = z; }
        public int getX() { return x; }
        public int getZ() { return z; }
        public EnumSet<Direction> getOpenings() { return EnumSet.copyOf(openings); }
    }

    public static final class Layout {
        private final List<Cell> cells;
        private final Cell entrance;
        private final Cell exit;
        private final Cell boss;

        private Layout(Collection<Cell> cells, Cell entrance, Cell exit, Cell boss) {
            this.cells = Collections.unmodifiableList(new ArrayList<Cell>(cells));
            this.entrance = entrance;
            this.exit = exit;
            this.boss = boss;
        }

        public List<Cell> getCells() { return cells; }
        public Cell getEntrance() { return entrance; }
        public Cell getExit() { return exit; }
        public Cell getBoss() { return boss; }
    }

    private static final class Position {
        private final int x;
        private final int z;

        private Position(int x, int z) { this.x = x; this.z = z; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Position)) return false;
            Position position = (Position) other;
            return x == position.x && z == position.z;
        }
        @Override public int hashCode() { return 31 * x + z; }
    }
}
