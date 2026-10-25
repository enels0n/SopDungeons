package net.enelson.sopdungeons;

import net.enelson.sopdungeons.api.SopDungeonsApi;
import net.enelson.sopdungeons.command.DungeonCommand;
import net.enelson.sopdungeons.config.DungeonRepository;
import net.enelson.sopdungeons.config.Messages;
import net.enelson.sopdungeons.config.ModuleRepository;
import net.enelson.sopdungeons.editor.SelectionService;
import net.enelson.sopdungeons.integration.EliteMobsBridge;
import net.enelson.sopdungeons.listener.DeathListener;
import net.enelson.sopdungeons.listener.EntranceListener;
import net.enelson.sopdungeons.listener.PresenceListener;
import net.enelson.sopdungeons.listener.TransitionListener;
import net.enelson.sopdungeons.model.DungeonSession;
import net.enelson.sopdungeons.service.DungeonManager;
import net.enelson.sopdungeons.service.GeneratedDungeonBuilder;
import net.enelson.sopdungeons.service.WorldInstanceService;
import net.enelson.sopdungeons.service.MobSpawnService;
import net.enelson.sopdungeons.service.CatacombDungeonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public final class SopDungeonsPlugin extends JavaPlugin {
    private static final String[] BUNDLED_MODULES = {
            "maze_entrance.schem", "maze_transition.schem", "maze_exit.schem", "maze_return_portal.schem",
            "maze_stairs_up.schem", "maze_stairs_down.schem",
            "maze_corridor_straight.schem", "maze_corridor_corner.schem",
            "maze_junction_t.schem", "maze_junction_cross.schem",
            "maze_room_straight.schem", "maze_room_corner.schem", "maze_room_t.schem",
            "maze_dead_end.schem", "maze_boss_straight.schem", "maze_boss_corner.schem",
            "maze_boss_t.schem", "maze_boss_cross.schem"
    };

    private DungeonManager manager;
    private MobSpawnService mobSpawns;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        copyResource("dungeons.yml");
        copyResource("modules.yml");
        new File(getDataFolder(), "templates").mkdirs();
        new File(getDataFolder(), "modules").mkdirs();
        installBundledModules();

        DungeonRepository repository = new DungeonRepository(this);
        repository.load();
        ModuleRepository moduleRepository = new ModuleRepository(this);
        moduleRepository.load();
        Messages messages = new Messages(this);
        GeneratedDungeonBuilder generator = new GeneratedDungeonBuilder(this, moduleRepository,
                getConfig().getInt("generation.floor-spacing", 512));
        CatacombDungeonBuilder catacombGenerator = new CatacombDungeonBuilder(this,
                getConfig().getInt("generation.floor-spacing", 512));
        WorldInstanceService worldInstances = new WorldInstanceService(this);
        worldInstances.cleanupOrphans(repository.getAll());
        manager = new DungeonManager(this, repository, worldInstances, generator, catacombGenerator);
        SelectionService selections = new SelectionService();

        DungeonCommand executor = new DungeonCommand(repository, manager, selections, messages,
                moduleRepository, getDataFolder(), this);
        PluginCommand command = getCommand("sopdungeons");
        if (command == null) throw new IllegalStateException("Command sopdungeons is missing from plugin.yml");
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        TransitionListener transitions = new TransitionListener(this, manager, messages);
        Bukkit.getPluginManager().registerEvents(transitions, this);
        Bukkit.getPluginManager().registerEvents(new EntranceListener(this, repository, manager, messages,
                transitions), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(this, manager), this);
        Bukkit.getPluginManager().registerEvents(new PresenceListener(manager), this);
        mobSpawns = new MobSpawnService(this, manager, repository, messages);
        Bukkit.getPluginManager().registerEvents(mobSpawns, this);
        mobSpawns.start();
        Bukkit.getServicesManager().register(SopDungeonsApi.class, manager, this, ServicePriority.Normal);
        if (EliteMobsBridge.register(this, manager, messages)) getLogger().info("EliteMobs integration enabled.");
        startSessionCleanup();
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() { manager.prepareAll(repository.getAll()); }
        }, 20L);
        getLogger().info("SopDungeons enabled. Loaded " + repository.getAll().size() + " dungeon(s).");
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (mobSpawns != null) mobSpawns.shutdown();
        if (manager != null) manager.shutdown();
    }

    private void copyResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
    }

    private void installBundledModules() {
        File configFile = new File(getDataFolder(), "modules.yml");
        ConfigurationSection modules = YamlConfiguration.loadConfiguration(configFile)
                .getConfigurationSection("modules");
        if (modules == null || modules.getKeys(false).isEmpty() || isOldBundledModuleSet(modules)) {
            saveResource("modules.yml", true);
        }
        for (String schematic : BUNDLED_MODULES) {
            copyResource("modules/" + schematic);
        }
    }

    private boolean isOldBundledModuleSet(ConfigurationSection modules) {
        if (modules.getKeys(false).isEmpty()) return false;
        for (String id : modules.getKeys(false)) {
            if (!id.startsWith("sample_") && !id.startsWith("maze_")) return false;
        }
        if (!modules.contains("maze_stairs_up") || !modules.contains("maze_stairs_down")
                || !modules.contains("maze_return_portal")
                || modules.getMapList("maze_stairs_up.connectors").size() != 1
                || modules.getMapList("maze_stairs_down.connectors").size() != 1) return true;
        Map<?, ?> connector = modules.getMapList("maze_stairs_up.connectors").get(0);
        return connector.containsKey("x") || connector.containsKey("z");
    }

    private void startSessionCleanup() {
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long timeout = Math.max(30L, getConfig().getLong("empty-session-timeout-seconds", 300L)) * 1000L;
                for (DungeonSession session : new ArrayList<DungeonSession>(manager.getSessions())) {
                    if (!session.isTemporary()) continue;
                    boolean online = false;
                    for (UUID id : session.getParticipants()) {
                        Player player = Bukkit.getPlayer(id);
                        if (player != null && player.isOnline()) {
                            online = true;
                            break;
                        }
                    }
                    if (online) session.setEmptySince(0L);
                    else if (session.getEmptySince() == 0L) session.setEmptySince(now);
                    else if (now - session.getEmptySince() >= timeout) manager.stop(session.getId());
                }
            }
        }, 200L, 200L);
    }
}
