package net.enelson.sopdungeons.service;

import net.enelson.sopdungeons.model.Cuboid;
import net.enelson.sopdungeons.model.DungeonDefinition;
import net.enelson.sopdungeons.model.DungeonSession;
import net.enelson.sopdungeons.model.DungeonType;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DungeonPresenceTest {
    @Test
    public void checksWorldAndConfiguredFloorBounds() {
        DungeonDefinition definition = new DungeonDefinition("test", DungeonType.STATIC);
        definition.getOrCreateFloor(1).setBounds(new Cuboid("dungeon", 0, 0, 0, 20, 20, 20));
        definition.getOrCreateFloor(2).setBounds(new Cuboid("dungeon", 100, 0, 100, 120, 20, 120));
        DungeonSession session = new DungeonSession(UUID.randomUUID(), definition, "dungeon", false);
        DungeonManager manager = new DungeonManager(null, null, null, null, null);

        assertTrue(manager.isInside(session, new Location(world("dungeon"), 10, 10, 10)));
        assertTrue(manager.isInside(session, new Location(world("dungeon"), 110, 10, 110)));
        assertFalse(manager.isInside(session, new Location(world("dungeon"), 50, 10, 50)));
        assertFalse(manager.isInside(session, new Location(world("spawn"), 10, 10, 10)));
    }

    @Test
    public void treatsWholeSessionWorldAsInsideWhenNoBoundsExist() {
        DungeonDefinition definition = new DungeonDefinition("legacy", DungeonType.STATIC);
        DungeonSession session = new DungeonSession(UUID.randomUUID(), definition, "dungeon", false);
        DungeonManager manager = new DungeonManager(null, null, null, null, null);

        assertTrue(manager.isInside(session, new Location(world("dungeon"), 1000, 200, 1000)));
        assertFalse(manager.isInside(session, new Location(world("spawn"), 0, 64, 0)));
    }

    private World world(final String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return name;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == float.class) return 0.0F;
                    if (method.getReturnType() == double.class) return 0.0D;
                    return null;
                });
    }
}
