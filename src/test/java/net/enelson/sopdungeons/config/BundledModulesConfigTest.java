package net.enelson.sopdungeons.config;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NamedTag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class BundledModulesConfigTest {
    @Test
    public void loadsAllBundledMazeModulesAndYamlAliases() {
        InputStream stream = getClass().getResourceAsStream("/modules.yml");
        assertNotNull(stream);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection modules = yaml.getConfigurationSection("modules");
        assertNotNull(modules);
        assertEquals(17, modules.getKeys(false).size());
        assertEquals(2, modules.getMapList("transition.connectors").size());
        assertEquals(3, modules.getMapList("room_t.connectors").size());
        assertNotNull(modules.getConfigurationSection("room_corner.spawns.monsters"));
        assertNotNull(modules.getConfigurationSection("boss_cross.spawns.boss"));
        assertEquals(1, modules.getMapList("stairs_up.connectors").size());
        assertEquals(1, modules.getMapList("stairs_down.connectors").size());
        assertEquals(1, ((Number) modules.getMapList("stairs_up.connectors").get(0).get("y")).intValue());
        assertEquals(6, ((Number) modules.getMapList("stairs_down.connectors").get(0).get("y")).intValue());
        assertFalse(modules.getMapList("stairs_up.connectors").get(0).containsKey("x"));
        assertFalse(modules.getMapList("stairs_up.connectors").get(0).containsKey("z"));
        assertEquals(1, modules.getMapList("exit.connectors").size());
    }

    @Test
    public void everyBundledSchematicExistsAndUsesConfiguredFootprint() throws IOException {
        InputStream configStream = getClass().getResourceAsStream("/modules.yml");
        assertNotNull(configStream);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(configStream, StandardCharsets.UTF_8));
        ConfigurationSection modules = yaml.getConfigurationSection("modules");
        assertNotNull(modules);
        for (String id : modules.getKeys(false)) {
            String fileName = modules.getString(id + ".schematic");
            InputStream schematic = getClass().getResourceAsStream("/modules/" + fileName);
            assertNotNull("Missing schematic for " + id + ": " + fileName, schematic);
            try (NBTInputStream input = new NBTInputStream(new GZIPInputStream(schematic))) {
                NamedTag named = input.readNamedTag();
                assertNotNull("Invalid NBT root for " + id, named);
                CompoundTag root = (CompoundTag) named.getTag();
                CompoundTag schematicRoot = root.getValue().get("Schematic") instanceof CompoundTag
                        ? (CompoundTag) root.getValue().get("Schematic") : root;
                assertEquals("Invalid width for " + id, 15, schematicRoot.getShort("Width"));
                assertEquals("Invalid length for " + id, 15, schematicRoot.getShort("Length"));
                int height = schematicRoot.getShort("Height");
                for (Map<?, ?> connector : modules.getMapList(id + ".connectors")) {
                    int y = ((Number) connector.get("y")).intValue();
                    assertTrue("Connector outside schematic height for " + id,
                            y >= 0 && y < height);
                }
            }
        }
    }
}
