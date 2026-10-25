package dev.sop.catacombs;

import java.util.*;

/** Pure Java procedural catacomb layout generator. No Bukkit dependency. */
public final class CatacombGenerator {
    public record Config(int width, int length, int minCorridor, int maxCorridor,
                         int minSpacing, int maxSpacing, int rooms, double loopChance, long seed) {
        public Config {
            if (width < 50 || length < 50) throw new IllegalArgumentException("Map must be at least 50x50");
            if (minCorridor < 3 || maxCorridor < minCorridor) throw new IllegalArgumentException("Invalid corridor widths");
            if (minSpacing < maxCorridor + 4 || maxSpacing < minSpacing) throw new IllegalArgumentException("Spacing must leave solid walls");
            if (rooms < 0 || loopChance < 0 || loopChance > 1) throw new IllegalArgumentException("Invalid rooms or loops");
        }
        public static Config defaults(long seed) {
            return new Config(480, 480, 5, 7, 15, 24, 12, .06, seed);
        }
    }
    public record Point(int x, int z) {}
    public record Layout(boolean[][] floor, Point entrance, Point exit, List<Point> rooms) {
        public int width() { return floor[0].length; }
        public int length() { return floor.length; }
        public boolean isFloor(int x, int z) { return floor[z][x]; }
    }
    private record Edge(int a, int b) {}
    private final Config cfg;
    private final Random random;
    private boolean[][] floor;
    private List<Integer> xs, zs;
    private int columns, rows;

    public CatacombGenerator(Config config) {
        cfg = Objects.requireNonNull(config);
        random = new Random(config.seed());
    }
    public Layout generate() {
        floor = new boolean[cfg.length()][cfg.width()];
        xs = positions(cfg.width());
        zs = positions(cfg.length());
        columns = xs.size(); rows = zs.size();
        if (columns < 2 || rows < 2) throw new IllegalArgumentException("Map too small for spacing");
        List<Edge> tree = spanningTree();
        Set<Long> existing = new HashSet<>();
        int[] degree = new int[columns * rows];
        for (Edge e : tree) {
            existing.add(key(e.a(), e.b()));
            degree[e.a()]++; degree[e.b()]++;
            connect(e.a(), e.b());
        }
        for (int z = 0; z < rows; z++) for (int x = 0; x < columns; x++) {
            int a = id(x, z);
            if (x + 1 < columns) maybeLoop(a, id(x + 1, z), existing);
            if (z + 1 < rows) maybeLoop(a, id(x, z + 1), existing);
        }
        List<Integer> roomCandidates = new ArrayList<>();
        for (int i = 0; i < degree.length; i++) if (degree[i] >= 2) roomCandidates.add(i);
        Collections.shuffle(roomCandidates, random);
        List<Point> rooms = new ArrayList<>();
        for (int i = 0; i < Math.min(cfg.rooms(), roomCandidates.size()); i++) {
            Point center = point(roomCandidates.get(i));
            int halfX = randomBetween(4, Math.max(4, cfg.minSpacing() / 2 - 2));
            int halfZ = randomBetween(4, Math.max(4, cfg.minSpacing() / 2 - 2));
            carve(center.x() - halfX, center.z() - halfZ,
                  center.x() + halfX + 1, center.z() + halfZ + 1);
            rooms.add(center);
        }
        Point start = point(id(0, 0));
        Point end = point(id(columns - 1, rows - 1));
        carve(0, start.z() - cfg.minCorridor()/2, start.x() + 1,
              start.z() + (cfg.minCorridor()+1)/2);
        carve(end.x(), end.z() - cfg.minCorridor()/2, cfg.width(),
              end.z() + (cfg.minCorridor()+1)/2);
        return new Layout(floor, new Point(0, start.z()),
                          new Point(cfg.width()-1, end.z()), List.copyOf(rooms));
    }
    private List<Integer> positions(int size) {
        List<Integer> out = new ArrayList<>();
        int p = cfg.maxSpacing();
        while (p < size - cfg.maxSpacing()) {
            out.add(p);
            p += randomBetween(cfg.minSpacing(), cfg.maxSpacing());
        }
        return out;
    }
    private int id(int x, int z) { return z * columns + x; }
    private Point point(int id) { return new Point(xs.get(id % columns), zs.get(id / columns)); }
    private static long key(int a, int b) {
        return ((long)Math.min(a,b) << 32) | (Math.max(a,b) & 0xffffffffL);
    }
    private List<Edge> spanningTree() {
        int count = columns * rows;
        boolean[] visited = new boolean[count];
        Deque<Integer> stack = new ArrayDeque<>();
        List<Edge> edges = new ArrayList<>();
        visited[0] = true; stack.push(0);
        while (!stack.isEmpty()) {
            int current = stack.peek();
            int x = current % columns, z = current / columns;
            List<Integer> options = new ArrayList<>(4);
            if (x > 0 && !visited[id(x-1,z)]) options.add(id(x-1,z));
            if (x+1 < columns && !visited[id(x+1,z)]) options.add(id(x+1,z));
            if (z > 0 && !visited[id(x,z-1)]) options.add(id(x,z-1));
            if (z+1 < rows && !visited[id(x,z+1)]) options.add(id(x,z+1));
            if (options.isEmpty()) { stack.pop(); continue; }
            int next = options.get(random.nextInt(options.size()));
            visited[next] = true;
            edges.add(new Edge(current,next));
            stack.push(next);
        }
        return edges;
    }
    private void maybeLoop(int a, int b, Set<Long> existing) {
        if (!existing.contains(key(a,b)) && random.nextDouble() < cfg.loopChance()) connect(a,b);
    }
    private void connect(int a, int b) {
        Point u = point(a), v = point(b);
        int width = randomBetween(cfg.minCorridor(), cfg.maxCorridor());
        int lo = width / 2, hi = width - lo;
        if (u.x() == v.x()) carve(u.x()-lo, Math.min(u.z(),v.z())-lo,
                                  u.x()+hi, Math.max(u.z(),v.z())+hi);
        else carve(Math.min(u.x(),v.x())-lo, u.z()-lo,
                   Math.max(u.x(),v.x())+hi, u.z()+hi);
    }
    private void carve(int x0,int z0,int x1,int z1) {
        for (int z = Math.max(0,z0); z < Math.min(cfg.length(),z1); z++)
            Arrays.fill(floor[z], Math.max(0,x0), Math.min(cfg.width(),x1), true);
    }
    private int randomBetween(int min,int max) { return min + random.nextInt(max-min+1); }
}
