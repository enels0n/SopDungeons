package net.enelson.sopdungeons.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class Messages {
    private final JavaPlugin plugin;

    public Messages(JavaPlugin plugin) { this.plugin = plugin; }

    public String get(String key) {
        return color(plugin.getConfig().getString("messages." + key, key));
    }

    public void send(CommandSender sender, String key, String... replacements) {
        String message = get("prefix") + get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        sender.sendMessage(message);
    }

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
