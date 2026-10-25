package net.enelson.sopdungeons.config;

import net.enelson.sopdungeons.model.Cuboid;
import net.enelson.sopdungeons.model.DungeonDefinition;
import net.enelson.sopdungeons.model.DungeonFloor;
import net.enelson.sopdungeons.model.DungeonType;
import net.enelson.sopdungeons.model.GenerationMode;
import net.enelson.sopdungeons.model.Point;
import net.enelson.sopdungeons.model.MobSpawnDefinition;
import net.enelson.sopdungeons.model.RespawnMode;
import net.enelson.sopdungeons.model.SpawnPlacement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DungeonRepository {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, DungeonDefinition> definitions = new LinkedHashMap<String, DungeonDefinition>();

    public DungeonRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dungeons.yml");
    }

    public void load() {
        definitions.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("dungeons");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            try {
                DungeonDefinition definition = new DungeonDefinition(id,
                        DungeonType.valueOf(section.getString("type", "STATIC").toUpperCase()));
                definition.setWorld(section.getString("world"));
                definition.setTemplate(section.getString("template"));
                definition.setEntrance(Point.read(section.getConfigurationSection("entrance")));
                definition.setExit(Point.read(section.getConfigurationSection("exit")));
                definition.setEntranceTrigger(Cuboid.read(section.getConfigurationSection("entrance-trigger")));
                definition.setGeneratedFloors(section.getInt("generation.floors", 1));
                definition.setNormalRooms(section.getInt("generation.normal-rooms", 5));
                definition.setGeneratedBosses(section.getBoolean("generation.bosses", false));
                definition.setGenerationMode(GenerationMode.valueOf(
                        section.getString("generation.mode", "MODULES").toUpperCase()));
                definition.setNaturalSpawning(section.getBoolean("natural-spawning", true));
                ConfigurationSection floors = section.getConfigurationSection("floors");
                if (floors != null) {
                    for (String key : floors.getKeys(false)) {
                        int number = Integer.parseInt(key);
                        ConfigurationSection floorSection = floors.getConfigurationSection(key);
                        DungeonFloor floor = definition.getOrCreateFloor(number);
                        floor.setBounds(Cuboid.read(floorSection.getConfigurationSection("bounds")));
                        floor.setArrivalFromUp(Point.read(floorSection.getConfigurationSection("arrival-from-up")));
                        floor.setArrivalFromDown(Point.read(floorSection.getConfigurationSection("arrival-from-down")));
                        floor.setUpTrigger(Cuboid.read(floorSection.getConfigurationSection("up-trigger")));
                        floor.setDownTrigger(Cuboid.read(floorSection.getConfigurationSection("down-trigger")));
                        floor.setBossId(floorSection.getString("boss"));
                        ConfigurationSection spawns = floorSection.getConfigurationSection("spawns");
                        if (spawns != null) {
                            for (String spawnId : spawns.getKeys(false)) {
                                ConfigurationSection spawn = spawns.getConfigurationSection(spawnId);
                                if (spawn == null) continue;
                                SpawnPlacement placement = SpawnPlacement.valueOf(
                                        spawn.getString("placement", "POINT").toUpperCase());
                                floor.putMobSpawn(new MobSpawnDefinition(spawnId,
                                        spawn.getString("provider", "ELITEMOBS"), spawn.getString("mob"),
                                        Point.read(spawn.getConfigurationSection("location")),
                                        placement == SpawnPlacement.AROUND_PLAYERS
                                                ? spawn.getInt("amount-per-player", spawn.getInt("amount", 1))
                                                : spawn.getInt("amount", 1), spawn.getDouble("radius", 0.0),
                                        spawn.getBoolean("boss", false), RespawnMode.valueOf(
                                        spawn.getString("respawn.mode", "NEVER").toUpperCase()),
                                        spawn.getInt("respawn.delay-seconds", 30), placement,
                                        spawn.getDouble("min-radius", 0.0),
                                        spawn.getDouble("max-radius", spawn.getDouble("radius", 0.0)),
                                        spawn.getInt("spawn-interval-ticks", 10)));
                            }
                        }
                    }
                }
                definitions.put(definition.getId(), definition);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Cannot load dungeon '" + id + "': " + exception.getMessage());
            }
        }
    }

    public void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("dungeons");
        for (DungeonDefinition definition : definitions.values()) {
            ConfigurationSection section = root.createSection(definition.getId());
            section.set("type", definition.getType().name());
            section.set("world", definition.getWorld());
            section.set("template", definition.getTemplate());
            writePoint(section, "entrance", definition.getEntrance());
            writePoint(section, "exit", definition.getExit());
            writeCuboid(section, "entrance-trigger", definition.getEntranceTrigger());
            section.set("generation.floors", definition.getGeneratedFloors());
            section.set("generation.normal-rooms", definition.getNormalRooms());
            section.set("generation.bosses", definition.hasGeneratedBosses());
            section.set("generation.mode", definition.getGenerationMode().name());
            section.set("natural-spawning", definition.isNaturalSpawning());
            ConfigurationSection floors = section.createSection("floors");
            for (DungeonFloor floor : definition.getFloors()) {
                ConfigurationSection floorSection = floors.createSection(String.valueOf(floor.getNumber()));
                writeCuboid(floorSection, "bounds", floor.getBounds());
                writePoint(floorSection, "arrival-from-up", floor.getArrivalFromUp());
                writePoint(floorSection, "arrival-from-down", floor.getArrivalFromDown());
                writeCuboid(floorSection, "up-trigger", floor.getUpTrigger());
                writeCuboid(floorSection, "down-trigger", floor.getDownTrigger());
                floorSection.set("boss", floor.getBossId());
                ConfigurationSection spawns = floorSection.createSection("spawns");
                for (MobSpawnDefinition spawn : floor.getMobSpawns()) {
                    ConfigurationSection spawnSection = spawns.createSection(spawn.getId());
                    spawnSection.set("provider", spawn.getProvider());
                    spawnSection.set("mob", spawn.getMobId());
                    writePoint(spawnSection, "location", spawn.getLocation());
                    spawnSection.set("amount", spawn.getAmount());
                    spawnSection.set("radius", spawn.getRadius());
                    spawnSection.set("boss", spawn.isBoss());
                    spawnSection.set("placement", spawn.getPlacement().name());
                    if (spawn.isAroundPlayers()) {
                        spawnSection.set("amount-per-player", spawn.getAmount());
                        spawnSection.set("min-radius", spawn.getMinRadius());
                        spawnSection.set("max-radius", spawn.getMaxRadius());
                    }
                    spawnSection.set("respawn.mode", spawn.getRespawnMode().name());
                    spawnSection.set("respawn.delay-seconds", spawn.getRespawnDelaySeconds());
                    spawnSection.set("spawn-interval-ticks", spawn.getSpawnIntervalTicks());
                }
            }
        }
        yaml.save(file);
    }

    private void writePoint(ConfigurationSection parent, String key, Point point) {
        if (point != null) point.write(parent.createSection(key));
    }

    private void writeCuboid(ConfigurationSection parent, String key, Cuboid cuboid) {
        if (cuboid != null) cuboid.write(parent.createSection(key));
    }

    public DungeonDefinition create(String id, DungeonType type) {
        DungeonDefinition definition = new DungeonDefinition(id, type);
        definitions.put(definition.getId(), definition);
        return definition;
    }

    public DungeonDefinition get(String id) { return id == null ? null : definitions.get(id.toLowerCase()); }
    public Collection<DungeonDefinition> getAll() { return Collections.unmodifiableCollection(definitions.values()); }
    public boolean remove(String id) { return definitions.remove(id.toLowerCase()) != null; }
}
