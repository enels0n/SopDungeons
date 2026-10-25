package net.enelson.sopdungeons.mob;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public interface MobProvider {
    String getId();
    boolean isAvailable();
    LivingEntity spawn(String mobId, Location location, boolean boss) throws Exception;
}
