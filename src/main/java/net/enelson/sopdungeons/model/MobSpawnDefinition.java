package net.enelson.sopdungeons.model;

public final class MobSpawnDefinition {
    private final String id;
    private String provider;
    private String mobId;
    private Point location;
    private int amount;
    private double radius;
    private boolean boss;
    private RespawnMode respawnMode;
    private int respawnDelaySeconds;
    private final SpawnPlacement placement;
    private final double minRadius;
    private final double maxRadius;
    private final int spawnIntervalTicks;

    public MobSpawnDefinition(String id, String provider, String mobId, Point location, int amount,
                              double radius, boolean boss, RespawnMode respawnMode, int respawnDelaySeconds) {
        this(id, provider, mobId, location, amount, radius, boss, respawnMode, respawnDelaySeconds,
                SpawnPlacement.POINT, 0.0, radius, 10);
    }

    public MobSpawnDefinition(String id, String provider, String mobId, Point location, int amount,
                              double radius, boolean boss, RespawnMode respawnMode, int respawnDelaySeconds,
                              int spawnIntervalTicks) {
        this(id, provider, mobId, location, amount, radius, boss, respawnMode, respawnDelaySeconds,
                SpawnPlacement.POINT, 0.0, radius, spawnIntervalTicks);
    }

    public MobSpawnDefinition(String id, String provider, String mobId, Point location, int amount,
                              double radius, boolean boss, RespawnMode respawnMode, int respawnDelaySeconds,
                              SpawnPlacement placement, double minRadius, double maxRadius) {
        this(id, provider, mobId, location, amount, radius, boss, respawnMode, respawnDelaySeconds,
                placement, minRadius, maxRadius, 10);
    }

    public MobSpawnDefinition(String id, String provider, String mobId, Point location, int amount,
                              double radius, boolean boss, RespawnMode respawnMode, int respawnDelaySeconds,
                              SpawnPlacement placement, double minRadius, double maxRadius,
                              int spawnIntervalTicks) {
        this.id = id.toLowerCase();
        this.provider = provider.toUpperCase();
        this.mobId = mobId;
        this.location = location;
        this.amount = Math.max(1, amount);
        this.radius = Math.max(0.0, radius);
        this.boss = boss;
        this.respawnMode = respawnMode;
        this.respawnDelaySeconds = Math.max(0, respawnDelaySeconds);
        this.placement = placement == null ? SpawnPlacement.POINT : placement;
        this.minRadius = Math.max(0.0, minRadius);
        this.maxRadius = Math.max(this.minRadius, maxRadius);
        this.spawnIntervalTicks = Math.max(0, spawnIntervalTicks);
    }

    public String getId() { return id; }
    public String getProvider() { return provider; }
    public String getMobId() { return mobId; }
    public Point getLocation() { return location; }
    public int getAmount() { return amount; }
    public double getRadius() { return radius; }
    public boolean isBoss() { return boss; }
    public RespawnMode getRespawnMode() { return respawnMode; }
    public int getRespawnDelaySeconds() { return respawnDelaySeconds; }
    public SpawnPlacement getPlacement() { return placement; }
    public double getMinRadius() { return minRadius; }
    public double getMaxRadius() { return maxRadius; }
    public int getSpawnIntervalTicks() { return spawnIntervalTicks; }
    public boolean isAroundPlayers() { return placement == SpawnPlacement.AROUND_PLAYERS; }
}
