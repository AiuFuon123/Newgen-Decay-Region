package newgen.decayregion.util;

import newgen.decayregion.DecayRegionPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class MessageUtil {

    private static String prefix;

    public static void init(DecayRegionPlugin plugin) {
        prefix = plugin.getConfig().getString("prefix", "&7[&bDecayRegion&7] ");
    }

    public static void send(CommandSender sender, String msg) {
        if (msg == null || msg.isEmpty()) return;
        sender.sendMessage(color(prefix + msg));
    }

    public static String color(String msg) {
        if (msg == null) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static String getPrefix() {
        return prefix;
    }
}
