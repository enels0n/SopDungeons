package net.enelson.sopdungeons.config;

import net.enelson.sopdungeons.model.DungeonModule;
import net.enelson.sopdungeons.model.ModuleType;
import net.enelson.sopdungeons.model.ModuleConnector;
import net.enelson.sopdungeons.model.RelativePoint;
import net.enelson.sopdungeons.model.ModuleMobSpawn;
import net.enelson.sopdungeons.model.RespawnMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class ModuleRepository {
    private final JavaPlugin plugin;
    private final List<DungeonModule> modules = new ArrayList<DungeonModule>();
    private final int moduleSize;

    public ModuleRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.moduleSize = configuredModuleSize(plugin);
    }

    private int configuredModuleSize(JavaPlugin plugin) {
        int size = plugin.getConfig().getInt("generation.modules.size", 15);
        if (size < 5 || size % 2 == 0) {
            throw new IllegalArgumentException("generation.modules.size must be an odd number of at least 5");
        }
        return size;
    }

    public void load() {
        modules.clear();
        File configFile = new File(plugin.getDataFolder(), "modules.yml");
        ConfigurationSection root = YamlConfiguration.loadConfiguration(configFile).getConfigurationSection("modules");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            try {
                File schematic = new File(new File(plugin.getDataFolder(), "modules"),
                        section.getString("schematic", id + ".schem"));
                List<Integer> rotations = section.getIntegerList("rotations");
                if (rotations.isEmpty()) rotations = Collections.singletonList(0);
                modules.add(new DungeonModule(id, ModuleType.valueOf(section.getString("type").toUpperCase()),
                        schematic, readConnectors(section), section.getInt("weight", 1),
                        section.getInt("min-depth", 1), section.getInt("max-uses", 0), rotations,
                        section.getString("boss"), readMobSpawns(section)));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Cannot load module '" + id + "': " + exception.getMessage());
            }
        }
    }

    private List<ModuleConnector> readConnectors(ConfigurationSection section) {
        List<ModuleConnector> result = new ArrayList<ModuleConnector>();
        for (Map<?, ?> map : section.getMapList("connectors")) {
            ModuleConnector.Kind kind = ModuleConnector.Kind.valueOf(String.valueOf(map.get("kind")).toUpperCase());
            String direction = String.valueOf(map.get("direction")).toUpperCase();
            result.add(new ModuleConnector(String.valueOf(map.get("id")), kind,
                    connectorPoint(direction, number(map.get("y"))), direction));
        }
        if (result.isEmpty()) {
            ConfigurationSection entry = section.getConfigurationSection("entry");
            ConfigurationSection exit = section.getConfigurationSection("exit");
            if (entry == null || exit == null) throw new IllegalArgumentException("connectors are required");
            result.add(new ModuleConnector("entry", ModuleConnector.Kind.ENTRY,
                    connectorPoint("WEST", entry.getInt("y")), "WEST"));
            result.add(new ModuleConnector("exit", ModuleConnector.Kind.EXIT,
                    connectorPoint("EAST", exit.getInt("y")), "EAST"));
        }
        return result;
    }

    private RelativePoint connectorPoint(String direction, int y) {
        int center = moduleSize / 2;
        int maximum = moduleSize - 1;
        if ("WEST".equals(direction)) return new RelativePoint(0, y, center);
        if ("EAST".equals(direction)) return new RelativePoint(maximum, y, center);
        if ("NORTH".equals(direction)) return new RelativePoint(center, y, 0);
        if ("SOUTH".equals(direction)) return new RelativePoint(center, y, maximum);
        throw new IllegalArgumentException("Unknown connector direction: " + direction);
    }

    private List<ModuleMobSpawn> readMobSpawns(ConfigurationSection section) {
        List<ModuleMobSpawn> result = new ArrayList<ModuleMobSpawn>();
        ConfigurationSection spawns = section.getConfigurationSection("spawns");
        if (spawns == null) return result;
        for (String id : spawns.getKeys(false)) {
            ConfigurationSection spawn = spawns.getConfigurationSection(id);
            if (spawn == null || spawn.getString("mob") == null) continue;
            result.add(new ModuleMobSpawn(id, spawn.getString("provider", "ELITEMOBS"),
                    spawn.getString("mob"), new RelativePoint(spawn.getInt("x"), spawn.getInt("y"),
                    spawn.getInt("z")), spawn.getInt("amount", 1), spawn.getDouble("radius", 0.0),
                    spawn.getBoolean("boss", false), RespawnMode.valueOf(
                    spawn.getString("respawn.mode", "NEVER").toUpperCase()),
                    spawn.getInt("respawn.delay-seconds", 30), spawn.getInt("spawn-interval-ticks", 10)));
        }
        return result;
    }

    private int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }

    public Collection<DungeonModule> getAll() { return Collections.unmodifiableCollection(modules); }

    public void create(String id, ModuleType type, File schematic, int width, int height, int length) throws IOException {
        if (width != moduleSize || length != moduleSize) {
            throw new IllegalArgumentException("Module footprint must be exactly " + moduleSize + "x"
                    + moduleSize + " blocks");
        }
        File configFile = new File(plugin.getDataFolder(), "modules.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection root = yaml.getConfigurationSection("modules");
        if (root == null) root = yaml.createSection("modules");
        ConfigurationSection section = root.getConfigurationSection(id);
        if (section == null) section = root.createSection(id);
        section.set("type", type.name());
        section.set("schematic", schematic.getName());
        section.set("weight", 1);
        section.set("min-depth", 1);
        section.set("max-uses", 0);
        section.set("rotations", Collections.singletonList(0));
        section.set("entry", null);
        section.set("exit", null);
        List<Map<String, Object>> connectors = new ArrayList<Map<String, Object>>();
        connectors.add(connector("entry", "ENTRY", Math.min(1, Math.max(0, height - 1)), "WEST"));
        connectors.add(connector("exit", "EXIT", Math.min(1, Math.max(0, height - 1)), "EAST"));
        section.set("connectors", connectors);
        yaml.save(configFile);
        load();
    }

    private Map<String, Object> connector(String id, String kind, int y, String direction) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        map.put("kind", kind);
        map.put("y", y);
        map.put("direction", direction);
        return map;
    }
}
