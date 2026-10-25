package net.enelson.sopdungeons.command;

import net.enelson.sopdungeons.config.DungeonRepository;
import net.enelson.sopdungeons.config.Messages;
import net.enelson.sopdungeons.config.ModuleRepository;
import net.enelson.sopdungeons.editor.SelectionService;
import net.enelson.sopdungeons.model.Cuboid;
import net.enelson.sopdungeons.model.DungeonDefinition;
import net.enelson.sopdungeons.model.DungeonFloor;
import net.enelson.sopdungeons.model.DungeonType;
import net.enelson.sopdungeons.model.Point;
import net.enelson.sopdungeons.model.ModuleType;
import net.enelson.sopdungeons.model.MobSpawnDefinition;
import net.enelson.sopdungeons.model.RespawnMode;
import net.enelson.sopdungeons.model.SpawnPlacement;
import net.enelson.sopdungeons.model.GenerationMode;
import net.enelson.sopdungeons.integration.WorldEditBridge;
import net.enelson.sopdungeons.service.DungeonManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DungeonCommand implements CommandExecutor, TabCompleter {
    private final DungeonRepository repository;
    private final DungeonManager manager;
    private final SelectionService selections;
    private final Messages messages;
    private final ModuleRepository modules;
    private final File dataFolder;
    private final JavaPlugin plugin;

    public DungeonCommand(DungeonRepository repository, DungeonManager manager,
                          SelectionService selections, Messages messages, ModuleRepository modules,
                          File dataFolder, JavaPlugin plugin) {
        this.repository = repository;
        this.manager = manager;
        this.selections = selections;
        this.messages = messages;
        this.modules = modules;
        this.dataFolder = dataFolder;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("leave")) return leave(sender, args);
        if (!sender.hasPermission("sopdungeons.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        try {
            if (action.equals("pos1") || action.equals("pos2")) return position(sender, action);
            if (action.equals("create")) return create(sender, args);
            if (action.equals("delete")) return delete(sender, args);
            if (action.equals("world") || action.equals("template")) return source(sender, args, action);
            if (action.equals("entrance") || action.equals("exit")) return point(sender, args, action);
            if (action.equals("entrance-area")) return entranceArea(sender, args);
            if (action.equals("floor")) return floor(sender, args);
            if (action.equals("module")) return module(sender, args);
            if (action.equals("generation")) return generation(sender, args);
            if (action.equals("prepare")) return prepare(sender, args);
            if (action.equals("natural")) return natural(sender, args);
            if (action.equals("enter")) return enter(sender, args);
            if (action.equals("stop")) return stop(sender, args);
            if (action.equals("list")) return list(sender);
            if (action.equals("info")) return info(sender, args);
            if (action.equals("sessions")) return sessions(sender);
            if (action.equals("debug")) return debug(sender);
            if (action.equals("reload")) {
                manager.invalidateAllPrepared();
                plugin.reloadConfig();
                repository.load();
                modules.load();
                manager.prepareAll(repository.getAll());
                messages.send(sender, "saved");
                return true;
            }
        } catch (Exception exception) {
            sender.sendMessage(Messages.color("&8[&6SopDungeons&8] &c" + exception.getMessage()));
            return true;
        }
        help(sender);
        return true;
    }

    private boolean position(CommandSender sender, String action) {
        Player player = player(sender);
        if (player == null) return true;
        if (action.equals("pos1")) selections.setFirst(player); else selections.setSecond(player);
        sender.sendMessage(Messages.color("&8[&6SopDungeons&8] &a" + action + " установлена."));
        return true;
    }

    private boolean create(CommandSender sender, String[] args) throws IOException {
        require(args, 3, "/sd create <id> <STATIC|TEMPLATE|GENERATED>");
        if (!args[1].matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("ID содержит недопустимые символы.");
        if (repository.get(args[1]) != null) throw new IllegalArgumentException("Такое подземелье уже существует.");
        DungeonDefinition definition = repository.create(args[1], DungeonType.valueOf(args[2].toUpperCase(Locale.ROOT)));
        if (definition.getType() == DungeonType.STATIC && sender instanceof Player) {
            definition.setWorld(((Player) sender).getWorld().getName());
        }
        save(sender);
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) throws IOException {
        require(args, 2, "/sd delete <id>");
        final String id = args[1];
        final DungeonDefinition definition = dungeon(id);
        manager.delete(definition).whenComplete((removed, error) -> Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (error != null || !Boolean.TRUE.equals(removed)) {
                    sender.sendMessage(Messages.color("&8[&6SopDungeons&8] &c"
                            + (error == null ? "Не удалось удалить мир подземелья." : rootMessage(error))));
                    return;
                }
                try {
                    repository.remove(id);
                    save(sender);
                } catch (IOException exception) {
                    sender.sendMessage(Messages.color("&8[&6SopDungeons&8] &c" + exception.getMessage()));
                }
            }
        }));
        return true;
    }

    private boolean source(CommandSender sender, String[] args, String action) throws IOException {
        require(args, 3, "/sd " + action + " <id> <name>");
        DungeonDefinition definition = dungeon(args[1]);
        if (action.equals("world")) definition.setWorld(args[2]); else definition.setTemplate(args[2]);
        save(sender);
        return true;
    }

    private boolean point(CommandSender sender, String[] args, String action) throws IOException {
        require(args, 2, "/sd " + action + " <id>");
        Player player = player(sender);
        if (player == null) return true;
        DungeonDefinition definition = dungeon(args[1]);
        if (action.equals("entrance")) definition.setEntrance(Point.from(player.getLocation()));
        else definition.setExit(Point.from(player.getLocation()));
        save(sender);
        return true;
    }

    private boolean entranceArea(CommandSender sender, String[] args) throws IOException {
        require(args, 2, "/sd entrance-area <id> [clear]");
        DungeonDefinition definition = dungeon(args[1]);
        if (args.length > 2 && args[2].equalsIgnoreCase("clear")) {
            definition.setEntranceTrigger(null);
            save(sender);
            return true;
        }
        Player player = player(sender);
        if (player == null) return true;
        Cuboid selection = selections.getSelection(player);
        if (selection == null) {
            messages.send(sender, "no-selection");
            return true;
        }
        definition.setEntranceTrigger(selection);
        save(sender);
        return true;
    }

    private boolean floor(CommandSender sender, String[] args) throws IOException {
        require(args, 4, "/sd floor <add|bounds|up|down|entrance|return|boss> <id> <number> [boss-id]");
        Player player = sender instanceof Player ? (Player) sender : null;
        String operation = args[1].toLowerCase(Locale.ROOT);
        DungeonDefinition definition = dungeon(args[2]);
        int number = Integer.parseInt(args[3]);
        if (number < 1) throw new IllegalArgumentException("Номер этажа должен быть больше нуля.");
        DungeonFloor floor = definition.getOrCreateFloor(number);
        if (operation.equals("spawn")) return floorSpawn(sender, args, floor);
        if (operation.equals("add")) {
            save(sender);
            return true;
        }
        if (operation.equals("boss")) {
            require(args, 5, "/sd floor boss <id> <number> <EliteMobs-id|none>");
            floor.setBossId(args[4].equalsIgnoreCase("none") ? null : args[4]);
            save(sender);
            return true;
        }
        if (player == null) {
            messages.send(sender, "player-only");
            return true;
        }
        if (operation.equals("arrivalup") || operation.equals("entrance")) floor.setArrivalFromUp(Point.from(player.getLocation()));
        else if (operation.equals("arrivaldown") || operation.equals("return")) floor.setArrivalFromDown(Point.from(player.getLocation()));
        else {
            Cuboid selection = selections.getSelection(player);
            if (selection == null) {
                messages.send(sender, "no-selection");
                return true;
            }
            if (operation.equals("bounds")) floor.setBounds(selection);
            else if (operation.equals("up")) floor.setUpTrigger(selection);
            else if (operation.equals("down")) floor.setDownTrigger(selection);
            else throw new IllegalArgumentException("Неизвестная операция этажа.");
        }
        save(sender);
        return true;
    }

    private boolean floorSpawn(CommandSender sender, String[] args, DungeonFloor floor) throws IOException {
        require(args, 5, "/sd floor spawn <id> <number> <add|add-around|remove|list> ...");
        String action = args[4].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            if (floor.getMobSpawns().isEmpty()) sender.sendMessage(Messages.color("&7Точки мобов не настроены."));
            for (MobSpawnDefinition spawn : floor.getMobSpawns()) {
                sender.sendMessage(Messages.color("&6" + spawn.getId() + " &7- &f" + spawn.getMobId()
                        + " &7x" + spawn.getAmount() + (spawn.isAroundPlayers() ? "/player "
                        + spawn.getMinRadius() + "-" + spawn.getMaxRadius() : " " + spawn.getRespawnMode())
                        + " &7interval=&f" + spawn.getSpawnIntervalTicks() + "t"
                        + (spawn.isBoss() ? " &cBOSS" : "")));
            }
            return true;
        }
        require(args, 6, "/sd floor spawn <id> <number> " + action + " <spawn-id> ...");
        String spawnId = args[5];
        if (action.equals("remove")) {
            MobSpawnDefinition removed = floor.getMobSpawn(spawnId);
            if (removed == null || !floor.removeMobSpawn(spawnId)) throw new IllegalArgumentException("Точка мобов не найдена.");
            if (removed.isBoss() && removed.getMobId().equalsIgnoreCase(floor.getBossId())) floor.setBossId(null);
            save(sender);
            return true;
        }
        if (action.equals("add-around")) {
            require(args, 11, "/sd floor spawn <id> <number> add-around <spawn-id> <mob-id> <amount-per-player> <min-radius> <max-radius> <delay> [spawn-interval-ticks]");
            Player player = player(sender);
            if (player == null) return true;
            int amount = Integer.parseInt(args[7]);
            double minRadius = Double.parseDouble(args[8]);
            double maxRadius = Double.parseDouble(args[9]);
            int delay = Integer.parseInt(args[10]);
            int spawnInterval = args.length > 11 ? Integer.parseInt(args[11]) : 10;
            if (minRadius < 0 || maxRadius <= minRadius) {
                throw new IllegalArgumentException("Максимальный радиус должен быть больше минимального.");
            }
            floor.putMobSpawn(new MobSpawnDefinition(spawnId, "ELITEMOBS", args[6],
                    Point.from(player.getLocation()), amount, maxRadius, false, RespawnMode.AFTER_DELAY, delay,
                    SpawnPlacement.AROUND_PLAYERS, minRadius, maxRadius, spawnInterval));
            save(sender);
            return true;
        }
        if (!action.equals("add")) throw new IllegalArgumentException("Используйте add, add-around, remove или list.");
        require(args, 7, "/sd floor spawn <id> <number> add <spawn-id> <mob-id> [amount] [radius] [mode] [delay] [boss] [spawn-interval-ticks]");
        Player player = player(sender);
        if (player == null) return true;
        if (floor.getBounds() != null && !floor.getBounds().contains(player.getLocation(), null)) {
            throw new IllegalArgumentException("Точка спавна должна находиться внутри bounds этого этажа.");
        }
        int amount = args.length > 7 ? Integer.parseInt(args[7]) : 1;
        double radius = args.length > 8 ? Double.parseDouble(args[8]) : 0.0;
        RespawnMode mode = args.length > 9 ? RespawnMode.valueOf(args[9].toUpperCase(Locale.ROOT)) : RespawnMode.NEVER;
        int delay = args.length > 10 ? Integer.parseInt(args[10]) : 30;
        boolean boss = args.length > 11 && Boolean.parseBoolean(args[11]);
        int spawnInterval = args.length > 12 ? Integer.parseInt(args[12]) : 10;
        if (boss) amount = 1;
        MobSpawnDefinition spawn = new MobSpawnDefinition(spawnId, "ELITEMOBS", args[6],
                Point.from(player.getLocation()), amount, radius, boss, mode, delay, spawnInterval);
        floor.putMobSpawn(spawn);
        if (boss) floor.setBossId(spawn.getMobId());
        save(sender);
        return true;
    }

    private boolean natural(CommandSender sender, String[] args) throws IOException {
        require(args, 3, "/sd natural <id> <true|false>");
        if (!args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Укажите true или false.");
        }
        DungeonDefinition definition = dungeon(args[1]);
        definition.setNaturalSpawning(Boolean.parseBoolean(args[2]));
        save(sender);
        sender.sendMessage(Messages.color("&7Естественный спавн внутри этажей: "
                + (definition.isNaturalSpawning() ? "&aвключен" : "&cвыключен")));
        return true;
    }

    private boolean generation(CommandSender sender, String[] args) throws IOException {
        require(args, 4, "/sd generation <id> <floors> <normal-rooms> [bosses:true|false] [MODULES|CATACOMBS]");
        DungeonDefinition definition = dungeon(args[1]);
        definition.setGeneratedFloors(Integer.parseInt(args[2]));
        definition.setNormalRooms(Integer.parseInt(args[3]));
        if (args.length > 4) definition.setGeneratedBosses(Boolean.parseBoolean(args[4]));
        if (args.length > 5) definition.setGenerationMode(GenerationMode.valueOf(args[5].toUpperCase(Locale.ROOT)));
        save(sender);
        manager.invalidatePrepared(definition.getId());
        prepareAndReport(sender, definition.getId());
        return true;
    }

    private boolean prepare(CommandSender sender, String[] args) {
        require(args, 2, "/sd prepare <id>");
        manager.invalidatePrepared(args[1]);
        prepareAndReport(sender, args[1]);
        return true;
    }

    private void prepareAndReport(final CommandSender sender, final String id) {
        sender.sendMessage(Messages.color("&e\u041f\u043e\u0434\u0433\u043e\u0442\u043e\u0432\u043a\u0430 \u043f\u043e\u0434\u0437\u0435\u043c\u0435\u043b\u044c\u044f &f" + id + "&e \u043d\u0430\u0447\u0430\u043b\u0430\u0441\u044c..."));
        manager.prepare(id).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (error != null) {
                    sender.sendMessage(Messages.color("&8[&6SopDungeons&8] &c" + rootMessage(error)));
                } else {
                    sender.sendMessage(Messages.color("&a\u041f\u043e\u0434\u0437\u0435\u043c\u0435\u043b\u044c\u0435 &f" + id
                            + "&a \u043f\u043e\u0434\u0433\u043e\u0442\u043e\u0432\u043b\u0435\u043d\u043e \u0438 \u0433\u043e\u0442\u043e\u0432\u043e \u043a\u043e \u0432\u0445\u043e\u0434\u0443."));
                }
            }
        }));
    }

    private boolean module(CommandSender sender, String[] args) throws IOException {
        require(args, 4, "/sd module save <id> <ENTRANCE|NORMAL|ELITE|BOSS|TRANSITION|EXIT|STAIRS_UP|STAIRS_DOWN>");
        if (!args[1].equalsIgnoreCase("save")) throw new IllegalArgumentException("Неизвестная операция модуля.");
        if (!args[2].matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("ID содержит недопустимые символы.");
        Player player = player(sender);
        if (player == null) return true;
        Cuboid selection = selections.getSelection(player);
        if (selection == null) {
            messages.send(sender, "no-selection");
            return true;
        }
        int width = selection.getMaxX() - selection.getMinX() + 1;
        int height = selection.getMaxY() - selection.getMinY() + 1;
        int length = selection.getMaxZ() - selection.getMinZ() + 1;
        int moduleSize = plugin.getConfig().getInt("generation.modules.size", 15);
        if (width != moduleSize || length != moduleSize) {
            throw new IllegalArgumentException("Размер модуля по горизонтали должен быть ровно "
                    + moduleSize + "x" + moduleSize + " блоков.");
        }
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null
                && Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            throw new IllegalArgumentException("Для сохранения схематики нужен WorldEdit или FAWE.");
        }
        File schematic = new File(new File(dataFolder, "modules"), args[2] + ".schem");
        new WorldEditBridge().save(selection, schematic);
        modules.create(args[2], ModuleType.valueOf(args[3].toUpperCase(Locale.ROOT)), schematic,
                width, height, length);
        messages.send(sender, "saved");
        return true;
    }

    private boolean enter(final CommandSender sender, String[] args) {
        require(args, 2, "/sd enter <id> [players...]");
        Collection<UUID> players = new ArrayList<UUID>();
        if (args.length == 2 && sender instanceof Player) players.add(((Player) sender).getUniqueId());
        for (int i = 2; i < args.length; i++) {
            Player target = Bukkit.getPlayer(args[i]);
            if (target == null) throw new IllegalArgumentException("Игрок не найден: " + args[i]);
            players.add(target.getUniqueId());
        }
        if (players.isEmpty()) throw new IllegalArgumentException("Укажите хотя бы одного игрока.");
        final String id = args[1];
        DungeonDefinition definition = dungeon(id);
        if (definition.getType() == DungeonType.GENERATED && !manager.isPrepared(id)
                && !manager.hasReusableSession(id)) {
            sender.sendMessage(Messages.color(manager.isPreparing(id)
                    ? "&eПодземелье ещё генерируется. Вход выполнится автоматически после завершения."
                    : "&eПодземелье не подготовлено. Генерация началась, вход выполнится автоматически."));
        }
        manager.start(id, players).whenComplete((session, error) -> {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (error != null) sender.sendMessage(Messages.color("&8[&6SopDungeons&8] &c" + rootMessage(error)));
                    else messages.send(sender, "entered", "id", id);
                }
            });
        });
        return true;
    }

    private boolean leave(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 1 && sender.hasPermission("sopdungeons.admin")) target = Bukkit.getPlayer(args[1]);
        else target = player(sender);
        if (target != null && manager.leave(target.getUniqueId())) messages.send(target, "left");
        return true;
    }

    private boolean stop(CommandSender sender, String[] args) {
        require(args, 2, "/sd stop <session-uuid>");
        manager.stop(UUID.fromString(args[1])).whenComplete((removed, error) -> {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (error != null) sender.sendMessage(Messages.color("&c" + rootMessage(error)));
                    else sender.sendMessage(Messages.color(removed ? "&aСессия остановлена." : "&cСессия не найдена."));
                }
            });
        });
        return true;
    }

    private boolean list(CommandSender sender) {
        List<String> ids = new ArrayList<String>();
        for (DungeonDefinition definition : repository.getAll()) ids.add(definition.getId() + " (" + definition.getType() + ")");
        sender.sendMessage(Messages.color("&6Подземелья: &f" + (ids.isEmpty() ? "нет" : String.join(", ", ids))));
        sender.sendMessage(Messages.color("&6Активных сессий: &f" + manager.getSessions().size()));
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        require(args, 2, "/sd info <id>");
        DungeonDefinition definition = dungeon(args[1]);
        String source = definition.getType() == DungeonType.STATIC ? definition.getWorld() : definition.getTemplate();
        sender.sendMessage(Messages.color("&6" + definition.getId() + " &7- &f" + definition.getType()
                + " &7source: &f" + (source == null ? "не задан" : source)));
        sender.sendMessage(Messages.color("&7Вход: " + mark(definition.getEntrance() != null)
                + " &7выход: " + mark(definition.getExit() != null) + " &7этажей: &f" + definition.getFloors().size()
                + " &7natural spawn: " + mark(definition.isNaturalSpawning())));
        sender.sendMessage(Messages.color("&7Entrance area: " + mark(definition.getEntranceTrigger() != null)));
        if (definition.getType() == DungeonType.GENERATED) {
            sender.sendMessage(Messages.color("&7Генерация: этажей &f" + definition.getGeneratedFloors()
                    + "&7, обычных комнат &f" + definition.getNormalRooms()
                    + "&7, боссы " + mark(definition.hasGeneratedBosses())
                    + "&7, режим &f" + definition.getGenerationMode()));
        }
        for (DungeonFloor floor : definition.getFloors()) {
            sender.sendMessage(Messages.color("&7Этаж &f" + floor.getNumber() + "&7: bounds "
                    + mark(floor.getBounds() != null) + "&7, up " + mark(floor.getUpTrigger() != null)
                    + "&7, down " + mark(floor.getDownTrigger() != null) + "&7, arrivals "
                    + mark(floor.getArrivalFromUp() != null && floor.getArrivalFromDown() != null)
                    + " &7spawns: &f" + floor.getMobSpawns().size()
                    + (floor.getBossId() == null ? "" : " &7boss: &f" + floor.getBossId())));
        }
        return true;
    }

    private boolean sessions(CommandSender sender) {
        if (manager.getSessions().isEmpty()) {
            sender.sendMessage(Messages.color("&7Активных сессий нет."));
            return true;
        }
        for (net.enelson.sopdungeons.model.DungeonSession session : manager.getSessions()) {
            sender.sendMessage(Messages.color("&6" + session.getId() + " &7- &f" + session.getDungeonId()
                    + " &7world: &f" + session.getWorldName() + " &7players: &f" + session.getParticipants().size()));
        }
        return true;
    }

    private boolean debug(CommandSender sender) {
        boolean enabled = !plugin.getConfig().getBoolean("debug-transitions", false);
        plugin.getConfig().set("debug-transitions", enabled);
        plugin.saveConfig();
        sender.sendMessage(Messages.color("&6Отладка переходов: " + (enabled ? "&aвключена" : "&cвыключена")));
        return true;
    }

    private String mark(boolean value) { return value ? "&aда" : "&cнет"; }

    private void save(CommandSender sender) throws IOException {
        repository.save();
        messages.send(sender, "saved");
    }

    private DungeonDefinition dungeon(String id) {
        DungeonDefinition definition = repository.get(id);
        if (definition == null) throw new IllegalArgumentException("Подземелье не найдено: " + id);
        return definition;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player) return (Player) sender;
        messages.send(sender, "player-only");
        return null;
    }

    private void require(String[] args, int count, String usage) {
        if (args.length < count) throw new IllegalArgumentException("Использование: " + usage);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Messages.color("&6SopDungeons &7- /sd create, delete, world, template, entrance, entrance-area, exit"));
        sender.sendMessage(Messages.color("&7/sd floor <add|bounds|up|down|entrance|return|boss|spawn> <id> <n> ..."));
        sender.sendMessage(Messages.color("&7/sd natural <id> <true|false>, module, generation, prepare, enter, leave, stop, info, sessions, debug, reload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], Arrays.asList("create", "delete", "world", "template", "entrance", "entrance-area", "exit", "floor", "natural", "module", "generation", "prepare", "enter", "leave", "stop", "list", "info", "sessions", "debug", "reload", "pos1", "pos2"));
        if (args.length == 2 && usesDungeonId(args[0])) return dungeonIds(args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("entrance-area")) return match(args[2], Collections.singletonList("clear"));
        if (args.length == 2 && args[0].equalsIgnoreCase("floor")) return match(args[1], Arrays.asList("add", "bounds", "up", "down", "entrance", "return", "boss", "spawn"));
        if (args.length == 3 && args[0].equalsIgnoreCase("floor")) return dungeonIds(args[2]);
        if (args.length == 5 && args[0].equalsIgnoreCase("floor") && args[1].equalsIgnoreCase("spawn")) return match(args[4], Arrays.asList("add", "add-around", "remove", "list"));
        if (args.length == 10 && args[0].equalsIgnoreCase("floor") && args[1].equalsIgnoreCase("spawn")) return match(args[9], Arrays.asList("NEVER", "AFTER_DELAY", "WHEN_CLEARED"));
        if (args.length == 12 && args[0].equalsIgnoreCase("floor") && args[1].equalsIgnoreCase("spawn")) return match(args[11], Arrays.asList("false", "true"));
        if (args.length == 3 && args[0].equalsIgnoreCase("natural")) return match(args[2], Arrays.asList("false", "true"));
        if (args.length == 2 && args[0].equalsIgnoreCase("module")) return match(args[1], Collections.singletonList("save"));
        if (args.length == 4 && args[0].equalsIgnoreCase("module")) return match(args[3], Arrays.asList("ENTRANCE", "NORMAL", "ELITE", "BOSS", "TRANSITION", "EXIT", "STAIRS_UP", "STAIRS_DOWN", "DEAD_END"));
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) return match(args[2], Arrays.asList("STATIC", "TEMPLATE", "GENERATED"));
        if (args.length == 5 && args[0].equalsIgnoreCase("generation")) return match(args[4], Arrays.asList("false", "true"));
        if (args.length == 6 && args[0].equalsIgnoreCase("generation")) return match(args[5], Arrays.asList("MODULES", "CATACOMBS"));
        return Collections.emptyList();
    }

    private boolean usesDungeonId(String action) {
        return Arrays.asList("delete", "world", "template", "entrance", "entrance-area", "exit",
                "natural", "generation", "prepare", "enter", "info").contains(action.toLowerCase(Locale.ROOT));
    }

    private List<String> dungeonIds(String token) {
        List<String> ids = new ArrayList<String>();
        for (DungeonDefinition definition : repository.getAll()) ids.add(definition.getId());
        return match(token, ids);
    }

    private List<String> match(String token, List<String> options) {
        List<String> result = new ArrayList<String>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(token.toLowerCase(Locale.ROOT))) result.add(option);
        return result;
    }
}
