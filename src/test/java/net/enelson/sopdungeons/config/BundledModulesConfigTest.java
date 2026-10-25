package net.enelson.sopdungeons.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public final class BundledModulesConfigTest {
    @Test
    public void loadsAllBundledMazeModulesAndYamlAliases() {
        InputStream stream = getClass().getResourceAsStream("/modules.yml");
        assertNotNull(stream);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection modules = yaml.getConfigurationSection("modules");
        assertNotNull(modules);
        assertEquals(18, modules.getKeys(false).size());
        assertEquals(2, modules.getMapList("maze_transition.connectors").size());
        assertEquals(3, modules.getMapList("maze_room_t.connectors").size());
        assertNotNull(modules.getConfigurationSection("maze_room_corner.spawns.monsters"));
        assertNotNull(modules.getConfigurationSection("maze_boss_cross.spawns.boss"));
        assertEquals(1, modules.getMapList("maze_stairs_up.connectors").size());
        assertEquals(1, modules.getMapList("maze_stairs_down.connectors").size());
        assertEquals(1, ((Number) modules.getMapList("maze_stairs_up.connectors").get(0).get("y")).intValue());
        assertEquals(8, ((Number) modules.getMapList("maze_stairs_down.connectors").get(0).get("y")).intValue());
        assertFalse(modules.getMapList("maze_stairs_up.connectors").get(0).containsKey("x"));
        assertFalse(modules.getMapList("maze_stairs_up.connectors").get(0).containsKey("z"));
        assertEquals(1, modules.getMapList("maze_return_portal.connectors").size());
    }
}
