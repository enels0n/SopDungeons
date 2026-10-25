package net.enelson.sopdungeons.mob;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

public final class VanillaMobProvider implements MobProvider {
    @Override
    public String getId() { return "VANILLA"; }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public LivingEntity spawn(String mobId, Location location, boolean boss) {
        EntityType type = EntityType.valueOf(mobId.toUpperCase(Locale.ROOT));
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (!(entity instanceof LivingEntity)) {
            entity.remove();
            throw new IllegalArgumentException("Not a living entity type: " + mobId);
        }
        return (LivingEntity) entity;
    }
}
