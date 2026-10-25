package net.enelson.sopdungeons.service.catacomb;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class CatacombLayoutGenerator {
    public static final class Config {
        private final int width;
        private final int length;
        private final int minCorridor;
        private final int maxCorridor;
        private final int minSpacing;
        private final int maxSpacing;
        private final int rooms;
        private final double loopChance;
        private final int bossRoomRadius;
        private final long seed;

        public Config(int width, int length, int minCorridor, int maxCorridor,
                      int minSpacing, int maxSpacing, int rooms, double loopChance,
                      int bossRoomRadius, long seed) {
            if (width < 50 || length < 50) throw new IllegalArgumentException("Map must be at least 50x50");
            if (minCorridor < 3 || maxCorridor < minCorridor) {
                throw new IllegalArgumentException("Invalid corridor widths");
            }
            if (minSpacing < maxCorridor + 4 || maxSpacing < minSpacing) {
                throw new IllegalArgumentException("Spacing must leave solid walls");
            }
            if (width < maxSpacing * 2 + minSpacing || length < maxSpacing * 2 + minSpacing) {
                throw new IllegalArgumentException("Map is too small for the configured spacing");
            }
            if (rooms < 0 || loopChance < 0.0 || loopChance > 1.0 || bossRoomRadius < 4) {
                throw new IllegalArgumentException("Invalid rooms, loops or boss room radius");
            }
            this.width = width;
            this.length = length;
            this.minCorridor = minCorridor;
            this.maxCorridor = maxCorridor;
            this.minSpacing = minSpacing;
            this.maxSpacing = maxSpacing;
            this.rooms = rooms;
            this.loopChance = loopChance;
            this.bossRoomRadius = bossRoomRadius;
            this.seed = seed;
        }
    }

    public static final class Cell {
        private final int x;
        private final int z;

        public Cell(int x, int z) { this.x = x; this.z = z; }
        public int getX() { return x; }
        public int getZ() { return z; }
    }

    public static final class Layout {
        private final boolean[][] floor;
        private final Cell entrance;
        private final Cell exit;
        private final List<Cell> rooms;
        private final Cell bossRoom;

        private Layout(boolean[][] floor, Cell entrance, Cell exit, List<Cell> rooms, Cell bossRoom) {
            this.floor = floor;
            this.entrance = entrance;
            this.exit = exit;
            this.rooms = Collections.unmodifiableList(new ArrayList<Cell>(rooms));
            this.bossRoom = bossRoom;
        }

        public int getWidth() { return floor[0].length; }
        public int getLength() { return floor.length; }
        public boolean isFloor(int x, int z) { return floor[z][x]; }
        public Cell getEntrance() { return entrance; }
        public Cell getExit() { return exit; }
        public List<Cell> getRooms() { return rooms; }
        public Cell getBossRoom() { return bossRoom; }
    }

    private static final class Edge {
        private final int a;
        private final int b;
        private Edge(int a, int b) { this.a = a; this.b = b; }
    }

    private final Config config;
    private final Random random;
    private boolean[][] floor;
    private List<Integer> xs;
    private List<Integer> zs;
    private int columns;
    private int rows;
    private int[] parents;

    public CatacombLayoutGenerator(Config config) {
        this.config = config;
        this.random = new Random(config.seed);
    }

    public Layout generate() {
        floor = new boolean[config.length][config.width];
        xs = positions(config.width);
        zs = positions(config.length);
        columns = xs.size();
        rows = zs.size();
        if (columns < 2 || rows < 2) throw new IllegalArgumentException("Map too small for spacing");

        List<Edge> tree = spanningTree();
        Set<Long> existing = new HashSet<Long>();
        int[] degree = new int[columns * rows];
        for (Edge edge : tree) {
            existing.add(key(edge.a, edge.b));
            degree[edge.a]++;
            degree[edge.b]++;
            connect(edge.a, edge.b);
        }
        for (int z = 0; z < rows; z++) {
            for (int x = 0; x < columns; x++) {
                int first = id(x, z);
                if (x + 1 < columns && maybeLoop(first, id(x + 1, z), existing)) {
                    degree[first]++;
                    degree[id(x + 1, z)]++;
                }
                if (z + 1 < rows && maybeLoop(first, id(x, z + 1), existing)) {
                    degree[first]++;
                    degree[id(x, z + 1)]++;
                }
            }
        }

        int exitId = id(columns - 1, rows - 1);
        int bossId = parents[exitId] < 0 ? exitId : parents[exitId];
        Cell bossRoom = point(bossId);
        carveRoom(bossRoom, config.bossRoomRadius, config.bossRoomRadius);

        List<Integer> roomCandidates = new ArrayList<Integer>();
        for (int i = 0; i < degree.length; i++) {
            if (degree[i] >= 2 && i != 0 && i != exitId && i != bossId) roomCandidates.add(i);
        }
        Collections.shuffle(roomCandidates, random);
        List<Cell> rooms = new ArrayList<Cell>();
        for (int i = 0; i < Math.min(config.rooms, roomCandidates.size()); i++) {
            Cell center = point(roomCandidates.get(i));
            int halfX = randomBetween(4, Math.max(4, config.minSpacing / 2 - 2));
            int halfZ = randomBetween(4, Math.max(4, config.minSpacing / 2 - 2));
            carveRoom(center, halfX, halfZ);
            rooms.add(center);
        }

        Cell start = point(0);
        Cell end = point(exitId);
        carve(0, start.z - config.minCorridor / 2, start.x + 1,
                start.z + (config.minCorridor + 1) / 2);
        carve(end.x, end.z - config.minCorridor / 2, config.width,
                end.z + (config.minCorridor + 1) / 2);
        return new Layout(floor, new Cell(0, start.z),
                new Cell(config.width - 1, end.z), rooms, bossRoom);
    }

    private void carveRoom(Cell center, int halfX, int halfZ) {
        carve(center.x - halfX, center.z - halfZ, center.x + halfX + 1, center.z + halfZ + 1);
    }

    private List<Integer> positions(int size) {
        List<Integer> result = new ArrayList<Integer>();
        int firstPosition = config.maxSpacing;
        int finalPosition = size - config.maxSpacing;
        int distance = finalPosition - firstPosition;
        int segments = (distance + config.maxSpacing - 1) / config.maxSpacing;
        if (distance < segments * config.minSpacing) {
            throw new IllegalArgumentException("Map axis cannot satisfy the configured spacing");
        }
        int[] steps = new int[segments];
        Arrays.fill(steps, config.minSpacing);
        int remaining = distance - segments * config.minSpacing;
        while (remaining > 0) {
            int index = random.nextInt(segments);
            if (steps[index] >= config.maxSpacing) continue;
            steps[index]++;
            remaining--;
        }
        int position = firstPosition;
        result.add(position);
        for (int step : steps) {
            position += step;
            result.add(position);
        }
        return result;
    }

    private int id(int x, int z) { return z * columns + x; }
    private Cell point(int id) { return new Cell(xs.get(id % columns), zs.get(id / columns)); }

    private static long key(int a, int b) {
        return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xffffffffL);
    }

    private List<Edge> spanningTree() {
        int count = columns * rows;
        boolean[] visited = new boolean[count];
        parents = new int[count];
        Arrays.fill(parents, -1);
        Deque<Integer> stack = new ArrayDeque<Integer>();
        List<Edge> edges = new ArrayList<Edge>();
        visited[0] = true;
        stack.push(0);
        while (!stack.isEmpty()) {
            int current = stack.peek();
            int x = current % columns;
            int z = current / columns;
            List<Integer> options = new ArrayList<Integer>(4);
            if (x > 0 && !visited[id(x - 1, z)]) options.add(id(x - 1, z));
            if (x + 1 < columns && !visited[id(x + 1, z)]) options.add(id(x + 1, z));
            if (z > 0 && !visited[id(x, z - 1)]) options.add(id(x, z - 1));
            if (z + 1 < rows && !visited[id(x, z + 1)]) options.add(id(x, z + 1));
            if (options.isEmpty()) {
                stack.pop();
                continue;
            }
            int next = options.get(random.nextInt(options.size()));
            visited[next] = true;
            parents[next] = current;
            edges.add(new Edge(current, next));
            stack.push(next);
        }
        return edges;
    }

    private boolean maybeLoop(int a, int b, Set<Long> existing) {
        long key = key(a, b);
        if (existing.contains(key) || random.nextDouble() >= config.loopChance) return false;
        existing.add(key);
        connect(a, b);
        return true;
    }

    private void connect(int first, int second) {
        Cell from = point(first);
        Cell to = point(second);
        int width = randomBetween(config.minCorridor, config.maxCorridor);
        int low = width / 2;
        int high = width - low;
        if (from.x == to.x) {
            carve(from.x - low, Math.min(from.z, to.z) - low,
                    from.x + high, Math.max(from.z, to.z) + high);
        } else {
            carve(Math.min(from.x, to.x) - low, from.z - low,
                    Math.max(from.x, to.x) + high, from.z + high);
        }
    }

    private void carve(int x0, int z0, int x1, int z1) {
        for (int z = Math.max(0, z0); z < Math.min(config.length, z1); z++) {
            Arrays.fill(floor[z], Math.max(0, x0), Math.min(config.width, x1), true);
        }
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
