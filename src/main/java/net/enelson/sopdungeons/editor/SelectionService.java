package net.enelson.sopdungeons.editor;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import net.enelson.sopdungeons.model.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SelectionService {
    private final Map<UUID, Location> first = new HashMap<UUID, Location>();
    private final Map<UUID, Location> second = new HashMap<UUID, Location>();

    public void setFirst(Player player) { first.put(player.getUniqueId(), player.getLocation()); }
    public void setSecond(Player player) { second.put(player.getUniqueId(), player.getLocation()); }

    public Cuboid getSelection(Player player) {
        Cuboid worldEditSelection = getWorldEditSelection(player);
        if (worldEditSelection != null) return worldEditSelection;
        Location one = first.get(player.getUniqueId());
        Location two = second.get(player.getUniqueId());
        return one == null || two == null ? null : Cuboid.between(one, two);
    }

    private Cuboid getWorldEditSelection(Player player) {
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null
                && Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) return null;
        try {
            com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
            Region region = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(actor.getWorld());
            return new Cuboid(player.getWorld().getName(), region.getMinimumPoint().getBlockX(),
                    region.getMinimumPoint().getBlockY(), region.getMinimumPoint().getBlockZ(),
                    region.getMaximumPoint().getBlockX(), region.getMaximumPoint().getBlockY(),
                    region.getMaximumPoint().getBlockZ());
        } catch (IncompleteRegionException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
