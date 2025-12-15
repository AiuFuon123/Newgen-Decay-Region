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
            MessageUtil.send(sender, "&cChỉ dùng trong game.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("decayregion.admin")) {
            MessageUtil.send(player, "&cBạn không có quyền sử dụng lệnh này.");
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
                MessageUtil.send(player, "&aĐã reload config + regions + placed-data + snapshots.");
            }

            case "reset" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "&cDùng: &e/decay reset <tên>");
                    return true;
                }
                resetRegion(player, args[1]);
            }

            case "create" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "&cDùng: &e/decay create <tên>");
                    return true;
                }
                createRegion(player, args[1]);
            }

            case "list" -> listRegions(player);

            case "remove" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "&cDùng: &e/decay remove <tên>");
                    return true;
                }
                removeRegion(player, args[1]);
            }

            case "menu" -> {
                // ✅ GUI CŨ: mở main menu trang 1
                DecayMenuGUI.openMain(player, plugin, regionManager, 1);
            }

            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        MessageUtil.send(player, "&e/decay wand &7- Nhận wand blaze_rod để chọn vùng.");
        MessageUtil.send(player, "&7   Left-click block: đặt pos1");
        MessageUtil.send(player, "&7   Right-click block: đặt pos2");
        MessageUtil.send(player, "&e/decay create <tên> &7- Tạo khu vực decay từ pos1-pos2.");
        MessageUtil.send(player, "&e/decay list &7- Xem danh sách khu vực.");
        MessageUtil.send(player, "&e/decay remove <tên> &7- Xóa khu vực.");
        MessageUtil.send(player, "&e/decay menu &7- Mở GUI quản lý region.");
        MessageUtil.send(player, "&e/decay reset <tên> &7- ForceClear + Restore snapshot (như restart).");
        MessageUtil.send(player, "&e/decay reload &7- Reload config + dữ liệu plugin.");
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
        MessageUtil.send(player, "&aĐã cho bạn &bDecayRegion Wand&a.");
    }

    private void resetRegion(Player player, String name) {
        DecayRegion region = regionManager.getRegion(name);
        if (region == null) {
            MessageUtil.send(player, "&cKhông tìm thấy khu vực &e" + name + "&c.");
            return;
        }

        if (plugin.getPlacedDataStore() != null) {
            plugin.getPlacedDataStore().forceClearRegion(region.getName());
        }
        if (plugin.getSnapshotStore() != null) {
            plugin.getSnapshotStore().restoreRegion(region);
        }

        MessageUtil.send(player, "&aĐã reset region &e" + region.getName() + "&a.");
    }

    private void createRegion(Player player, String name) {
        if (regionManager.getRegion(name) != null) {
            MessageUtil.send(player, "&cTên khu vực &e" + name + "&c đã tồn tại.");
            return;
        }

        UUID uid = player.getUniqueId();
        Location p1 = selectionManager.getPos1(uid);
        Location p2 = selectionManager.getPos2(uid);

        if (p1 == null || p2 == null) {
            MessageUtil.send(player, "&cBạn cần chọn cả pos1 và pos2 bằng wand trước.");
            return;
        }

        if (p1.getWorld() == null || p2.getWorld() == null
                || !p1.getWorld().getName().equals(p2.getWorld().getName())) {
            MessageUtil.send(player, "&cPos1 và pos2 phải cùng 1 world.");
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
            MessageUtil.send(player, "&cKhông thể tạo region &e" + name + "&c vì đang trùng với một region khác.");
            return;
        }

        regionManager.addRegion(region);

        if (plugin.getSnapshotStore() != null) {
            plugin.getSnapshotStore().snapshotRegion(region);
        }

        MessageUtil.send(player, "&aĐã tạo khu vực decay &e" + name + " &atrong world &e" + world.getName() + "&a.");

        String title = plugin.getConfig().getString("create-region-title", "&aTạo khu vực thành công!");
        String subtitle = plugin.getConfig().getString("create-region-subtitle", "&7Tên: &e%name%");
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
            MessageUtil.send(player, "&7Hiện chưa có khu vực nào.");
            return;
        }
        MessageUtil.send(player, "&aDanh sách khu vực decay:");
        regionManager.getRegions().forEach(r ->
                MessageUtil.send(player, "&e- " + r.getName() + " &7(world: " + r.getWorldName() + ", decay: " + r.getDecaySeconds() + "s)")
        );
    }

    private void removeRegion(Player player, String name) {
        boolean removed = regionManager.removeRegion(name);
        if (removed) {
            MessageUtil.send(player, "&aĐã xóa khu vực &e" + name + "&a.");
        } else {
            MessageUtil.send(player, "&cKhông tìm thấy khu vực &e" + name + "&c.");
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
