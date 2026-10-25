package net.enelson.sopdungeons.model;

public final class ModuleMobSpawn {
    private final String id;
    private final String provider;
    private final String mobId;
    private final RelativePoint location;
    private final int amount;
    private final double radius;
    private final boolean boss;
    private final RespawnMode respawnMode;
    private final int respawnDelaySeconds;
    private final int spawnIntervalTicks;

    public ModuleMobSpawn(String id, String provider, String mobId, RelativePoint location, int amount,
                          double radius, boolean boss, RespawnMode respawnMode, int respawnDelaySeconds,
                          int spawnIntervalTicks) {
        this.id = id.toLowerCase();
        this.provider = provider.toUpperCase();
        this.mobId = mobId;
        this.location = location;
        this.amount = Math.max(1, amount);
        this.radius = Math.max(0.0, radius);
        this.boss = boss;
        this.respawnMode = respawnMode == null ? RespawnMode.NEVER : respawnMode;
        this.respawnDelaySeconds = Math.max(0, respawnDelaySeconds);
        this.spawnIntervalTicks = Math.max(0, spawnIntervalTicks);
    }

    public String getId() { return id; }
    public String getProvider() { return provider; }
    public String getMobId() { return mobId; }
    public RelativePoint getLocation() { return location; }
    public int getAmount() { return amount; }
    public double getRadius() { return radius; }
    public boolean isBoss() { return boss; }
    public RespawnMode getRespawnMode() { return respawnMode; }
    public int getRespawnDelaySeconds() { return respawnDelaySeconds; }
    public int getSpawnIntervalTicks() { return spawnIntervalTicks; }
}
