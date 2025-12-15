package newgen.decayregion.command;

import newgen.decayregion.DecayRegionPlugin;
import newgen.decayregion.gui.DecayMenuGUI;
import newgen.decayregion.region.DecayRegion;
import newgen.decayregion.region.RegionManager;
import newgen.decayregion.selection.SelectionManager;
import newgen.decayregion.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DecayRegionCommand implements CommandExecutor, TabCompleter {

    private final RegionManager regionManager;
    private final SelectionManager selectionManager;
    private final DecayRegionPlugin plugin;

    public DecayRegionCommand(RegionManager regionManager,
                              SelectionManager selectionManager,
                              DecayRegionPlugin plugin) {
        this.regionManager = regionManager;
        this.selectionManager = selectionManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, "&cIn-game only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("decayregion.admin")) {
            MessageUtil.send(player, "&cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help" -> sendHelp(player);
            case "wand" -> giveWand(player);

            case "reload" -> {
                plugin.reloadPlugin();
                MessageUtil.send(player, "&aReloaded config + regions + placed-data + snapshots.");
            }

            case "reset" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "&cUsage: &e/decay reset <name>");
                    return true;
                }
                resetRegion(player, args[1]);
            }

            case "create" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "&cUsage: &e/decay create <name>");
                    return true;
                }
                createRegion(player, args[1]);
            }

            case "list" -> listRegions(player);

            case "remove" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "&cUsage: &e/decay remove <name>");
                    return true;
                }
                removeRegion(player, args[1]);
            }

            case "menu" -> DecayMenuGUI.openMain(player, plugin, regionManager, 1);

            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        MessageUtil.send(player, "&e/decay wand &7- Get a blaze_rod wand to select a region.");
        MessageUtil.send(player, "&7   Left-click block: set pos1");
        MessageUtil.send(player, "&7   Right-click block: set pos2");
        MessageUtil.send(player, "&e/decay create <name> &7- Create a decay region from pos1-pos2.");
        MessageUtil.send(player, "&e/decay list &7- View all regions.");
        MessageUtil.send(player, "&e/decay remove <name> &7- Remove a region.");
        MessageUtil.send(player, "&e/decay menu &7- Open the region management GUI.");
        MessageUtil.send(player, "&e/decay reset <name> &7- ForceClear + Restore snapshot (like restart).");
        MessageUtil.send(player, "&e/decay reload &7- Reload config + plugin data.");
    }

    private void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bDecayRegion Wand");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            wand.setItemMeta(meta);
        }

        player.getInventory().addItem(wand);
        MessageUtil.send(player, "&aYou have received &bDecayRegion Wand&a.");
    }

    private void resetRegion(Player player, String name) {
        DecayRegion region = regionManager.getRegion(name);
        if (region == null) {
            MessageUtil.send(player, "&cRegion not found: &e" + name + "&c.");
            return;
        }

        if (plugin.getPlacedDataStore() != null) {
            plugin.getPlacedDataStore().forceClearRegion(region.getName());
        }
        if (plugin.getSnapshotStore() != null) {
            plugin.getSnapshotStore().restoreRegion(region);
        }

        MessageUtil.send(player, "&aRegion reset: &e" + region.getName() + "&a.");
    }

    private void createRegion(Player player, String name) {
        if (regionManager.getRegion(name) != null) {
            MessageUtil.send(player, "&cRegion name &e" + name + "&c already exists.");
            return;
        }

        UUID uid = player.getUniqueId();
        Location p1 = selectionManager.getPos1(uid);
        Location p2 = selectionManager.getPos2(uid);

        if (p1 == null || p2 == null) {
            MessageUtil.send(player, "&cYou must select both pos1 and pos2 with the wand first.");
            return;
        }

        if (p1.getWorld() == null || p2.getWorld() == null
                || !p1.getWorld().getName().equals(p2.getWorld().getName())) {
            MessageUtil.send(player, "&cPos1 and pos2 must be in the same world.");
            return;
        }

        World world = p1.getWorld();

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        int defaultDecay = plugin.getConfig().getInt("default-decay-seconds", 30);

        DecayRegion region = new DecayRegion(
                name,
                world.getName(),
                minX, minY, minZ,
                maxX, maxY, maxZ,
                defaultDecay
        );

        if (regionManager.isOverlapping(region)) {
            MessageUtil.send(player, "&cCannot create region &e" + name + "&c because it overlaps another region.");
            return;
        }

        regionManager.addRegion(region);

        if (plugin.getSnapshotStore() != null) {
            plugin.getSnapshotStore().snapshotRegion(region);
        }

        MessageUtil.send(player, "&aCreated decay region &e" + name + " &ain world &e" + world.getName() + "&a.");

        String title = plugin.getConfig().getString("create-region-title", "&aRegion created!");
        String subtitle = plugin.getConfig().getString("create-region-subtitle", "&7Name: &e%name%");
        subtitle = subtitle.replace("%name%", name);

        player.sendTitle(
                MessageUtil.color(title),
                MessageUtil.color(subtitle),
                10, 60, 10
        );

        String soundName = plugin.getConfig().getString("create-region-sound", "minecraft:entity.player.levelup");

        try {
            NamespacedKey key = NamespacedKey.fromString(soundName);
            if (key != null) {
                Sound sound = Registry.SOUNDS.get(key);
                if (sound != null) {
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        } catch (Exception ex) {
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            } catch (Throwable ignored) {}
        }
    }

    private void listRegions(Player player) {
        if (regionManager.getRegions().isEmpty()) {
            MessageUtil.send(player, "&7There are no regions yet.");
            return;
        }
        MessageUtil.send(player, "&aDecay regions:");
        regionManager.getRegions().forEach(r ->
                MessageUtil.send(player, "&e- " + r.getName() + " &7(world: " + r.getWorldName() + ", decay: " + r.getDecaySeconds() + "s)")
        );
    }

    private void removeRegion(Player player, String name) {
        boolean removed = regionManager.removeRegion(name);
        if (removed) {
            MessageUtil.send(player, "&aRemoved region &e" + name + "&a.");
        } else {
            MessageUtil.send(player, "&cRegion not found: &e" + name + "&c.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {
        List<String> list = new ArrayList<>();

        if (!(sender instanceof Player)) return list;
        if (!sender.hasPermission("decayregion.admin")) return list;

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            String[] subs = {"help", "wand", "create", "list", "remove", "menu", "reload", "reset"};
            for (String s : subs) if (s.startsWith(prefix)) list.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("reset"))) {
            String prefix = args[1].toLowerCase();
            for (DecayRegion r : regionManager.getRegions()) {
                if (r.getName().toLowerCase().startsWith(prefix)) list.add(r.getName());
            }
        }

        return list;
    }
}
