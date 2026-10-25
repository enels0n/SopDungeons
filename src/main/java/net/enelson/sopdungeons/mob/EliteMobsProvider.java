package net.enelson.sopdungeons.mob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Method;

public final class EliteMobsProvider implements MobProvider {
    @Override
    public String getId() { return "ELITEMOBS"; }

    @Override
    public boolean isAvailable() { return Bukkit.getPluginManager().getPlugin("EliteMobs") != null; }

    @Override
    public LivingEntity spawn(String mobId, Location location, boolean boss) throws Exception {
        if (!isAvailable()) throw new IllegalStateException("EliteMobs is not installed");
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (!location.getWorld().isChunkLoaded(chunkX, chunkZ)) location.getWorld().loadChunk(chunkX, chunkZ);
        Class<?> type = Class.forName("com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity");
        Method create = type.getMethod("createCustomBossEntity", String.class);
        Object elite = create.invoke(null, mobId);
        if (elite == null) throw new IllegalArgumentException("EliteMobs mob not found: " + mobId);
        // EliteMobs uses true to suppress global spawn announcements. Keep announcements for configured bosses only.
        type.getMethod("spawn", Location.class, boolean.class).invoke(elite, location, !boss);
        LivingEntity entity = (LivingEntity) type.getMethod("getLivingEntity").invoke(elite);
        if (entity == null) throw new IllegalStateException("EliteMobs did not spawn: " + mobId);
        return entity;
    }
}
