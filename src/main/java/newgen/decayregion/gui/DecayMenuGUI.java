package newgen.decayregion.gui;

import newgen.decayregion.DecayRegionPlugin;
import newgen.decayregion.region.DecayRegion;
import newgen.decayregion.region.RegionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DecayMenuGUI {

    public static final String TITLE_MAIN_PREFIX = ChatColor.AQUA + "Decay Regions";
    public static final String TITLE_REGION_PREFIX = ChatColor.AQUA + "Manage: ";
    public static final String TITLE_TIME_PREFIX = ChatColor.AQUA + "DecayTime: ";
    public static final String TITLE_CONFIRM_FORCE_PREFIX = ChatColor.RED + "ForceClear: ";
    public static final String TITLE_CONFIRM_SAVE_PREFIX = ChatColor.GREEN + "SaveRegion: ";
    public static final String TITLE_CONFIRM_DELETE_PREFIX = ChatColor.DARK_RED + "DeleteRegion: ";

    private static final int MAIN_SIZE = 54;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_CLOSE = 49;
    private static final int REGION_START = 0;
    private static final int REGION_END = 44;

    private static final int MANAGE_SIZE = 27;
    private static final int TIME_SIZE = 27;
    private static final int CONFIRM_SIZE = 27;

    public static void openMain(Player player, DecayRegionPlugin plugin, RegionManager regionManager, int page) {
        List<DecayRegion> list = new ArrayList<>(regionManager.getRegions());
        list.sort(Comparator.comparing(r -> r.getName().toLowerCase()));

        int perPage = (REGION_END - REGION_START + 1);
        int maxPage = Math.max(1, (int) Math.ceil(list.size() / (double) perPage));
        if (page < 1) page = 1;
        if (page > maxPage) page = maxPage;

        String title = TITLE_MAIN_PREFIX + ChatColor.GRAY + " (" + page + "/" + maxPage + ")";
        Inventory inv = Bukkit.createInventory(null, MAIN_SIZE, title);

        for (int i = 46; i <= 52; i++) inv.setItem(i, glass(" "));

        boolean hasPrev = page > 1;
        boolean hasNext = page < maxPage;

        inv.setItem(SLOT_PREV, hasPrev ? arrow("&ePrevious page") : glass(" "));
        inv.setItem(SLOT_NEXT, hasNext ? arrow("&eNext page") : glass(" "));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "&cClose", lore("&7Close the menu")));

        int start = (page - 1) * perPage;
        int end = Math.min(list.size(), start + perPage);

        int slot = REGION_START;
        for (int i = start; i < end; i++) {
            DecayRegion r = list.get(i);
            inv.setItem(slot++, regionBook(r));
        }

        player.openInventory(inv);
        click(player);
    }

    public static void openRegionManage(Player player, DecayRegion region) {
        Inventory inv = Bukkit.createInventory(null, MANAGE_SIZE, TITLE_REGION_PREFIX + region.getName());

        for (int i = 0; i < MANAGE_SIZE; i++) inv.setItem(i, glass(" "));

        inv.setItem(11, button(Material.CLOCK, "&bDecayTime", lore("&7Edit the decay time")));
        inv.setItem(12, button(Material.TNT, "&cForceClear", lore("&7Force clear + restore snapshot")));
        inv.setItem(13, button(Material.ENDER_PEARL, "&dTeleport", lore("&7Teleport to the region")));
        inv.setItem(14, button(Material.WRITABLE_BOOK, "&aSaveRegion", lore("&7Save the region snapshot")));
        inv.setItem(15, button(Material.NAME_TAG, "&eRename", lore("&7Rename the region (chat input)")));

        inv.setItem(18, button(Material.ARROW, "&eBack", lore("&7Back to the region list")));
        inv.setItem(26, regionInfo(region));

        player.openInventory(inv);
        click(player);
    }

    public static void openTimeEditor(Player player, DecayRegion region) {
        Inventory inv = Bukkit.createInventory(null, TIME_SIZE, TITLE_TIME_PREFIX + region.getName());

        for (int i = 0; i < TIME_SIZE; i++) inv.setItem(i, glass(" "));

        inv.setItem(10, button(Material.LIME_DYE, "&a+1s", lore("&7Increase by 1 second")));
        inv.setItem(11, button(Material.LIME_DYE, "&a+5s", lore("&7Increase by 5 seconds")));
        inv.setItem(12, button(Material.LIME_DYE, "&a+30s", lore("&7Increase by 30 seconds")));

        inv.setItem(13, button(Material.BOOK, "&bInfo", lore(
                "&7Region: &e" + region.getName(),
                "&7Decay: &6" + region.getDecaySeconds() + "s",
                "&7World: &f" + region.getWorldName()
        )));

        inv.setItem(14, button(Material.RED_DYE, "&c-1s", lore("&7Decrease by 1 second")));
        inv.setItem(15, button(Material.RED_DYE, "&c-5s", lore("&7Decrease by 5 seconds")));
        inv.setItem(16, button(Material.RED_DYE, "&c-30s", lore("&7Decrease by 30 seconds")));

        inv.setItem(22, button(Material.ARROW, "&eBack", lore("&7Back to region management")));

        player.openInventory(inv);
        click(player);
    }

    public static void openForceClearConfirm(Player player, DecayRegion region) {
        Inventory inv = Bukkit.createInventory(null, CONFIRM_SIZE, TITLE_CONFIRM_FORCE_PREFIX + region.getName());
        for (int i = 0; i < CONFIRM_SIZE; i++) inv.setItem(i, glass(" "));

        inv.setItem(11, button(Material.RED_WOOL, "&cCONFIRM", lore("&7ForceClear this region")));
        inv.setItem(13, button(Material.PAPER, "&eInfo", lore(
                "&7Clears placed blocks/entities/fluids",
                "&7Then restores the snapshot"
        )));
        inv.setItem(15, button(Material.GREEN_WOOL, "&aCANCEL", lore("&7Cancel")));

        player.openInventory(inv);
        click(player);
    }

    public static void openSaveConfirm(Player player, DecayRegion region) {
        Inventory inv = Bukkit.createInventory(null, CONFIRM_SIZE, TITLE_CONFIRM_SAVE_PREFIX + region.getName());
        for (int i = 0; i < CONFIRM_SIZE; i++) inv.setItem(i, glass(" "));

        inv.setItem(11, button(Material.LIME_WOOL, "&aCONFIRM", lore("&7Save this region snapshot")));
        inv.setItem(13, button(Material.PAPER, "&eInfo", lore(
                "&7Snapshots are used to restore on reset/restart",
                "&7Save after the region is stable"
        )));
        inv.setItem(15, button(Material.RED_WOOL, "&cCANCEL", lore("&7Cancel")));

        player.openInventory(inv);
        click(player);
    }

    public static void openDeleteConfirm(Player player, DecayRegion region) {
        Inventory inv = Bukkit.createInventory(null, CONFIRM_SIZE, TITLE_CONFIRM_DELETE_PREFIX + region.getName());
        for (int i = 0; i < CONFIRM_SIZE; i++) inv.setItem(i, glass(" "));

        inv.setItem(11, button(Material.RED_WOOL, "&cCONFIRM DELETE", lore(
                "&7Delete region: &e" + region.getName(),
                "&4This cannot be undone!"
        )));
        inv.setItem(13, button(Material.PAPER, "&eInfo", lore(
                "&7The region will be removed from the list",
                "&7Placed-data for this region will be cleared"
        )));
        inv.setItem(15, button(Material.GREEN_WOOL, "&aCANCEL", lore("&7Cancel")));

        player.openInventory(inv);
        click(player);
    }

    private static ItemStack regionBook(DecayRegion r) {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&b" + r.getName()));
            meta.setLore(lore(
                    "&7World: &f" + r.getWorldName(),
                    "&7Decay: &6" + r.getDecaySeconds() + "s",
                    "",
                    "&eLeft-click: &7Manage",
                    "&cRight-click: &7Delete (confirm)"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack regionInfo(DecayRegion r) {
        return button(Material.BOOK, "&bRegion Info", lore(
                "&7Name: &e" + r.getName(),
                "&7World: &f" + r.getWorldName(),
                "&7Decay: &6" + r.getDecaySeconds() + "s",
                "&7Min: &f" + r.getMinX() + ", " + r.getMinY() + ", " + r.getMinZ(),
                "&7Max: &f" + r.getMaxX() + ", " + r.getMaxY() + ", " + r.getMaxZ()
        ));
    }

    private static ItemStack arrow(String name) {
        return button(Material.ARROW, name, lore("&7Click to change pages"));
    }

    private static ItemStack glass(String name) {
        return button(Material.GRAY_STAINED_GLASS_PANE, name, List.of());
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(colorLore(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static List<String> lore(String... lines) {
        List<String> l = new ArrayList<>();
        for (String s : lines) l.add(color(s));
        return l;
    }

    private static List<String> colorLore(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String s : lines) out.add(color(s));
        return out;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static void click(Player p) {
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.15f); } catch (Throwable ignored) {}
    }

    public static void success(Player p) {
        try { p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f); } catch (Throwable ignored) {}
    }

    public static void fail(Player p) {
        try { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); } catch (Throwable ignored) {}
    }

    public static Location center(DecayRegion region) {
        World w = Bukkit.getWorld(region.getWorldName());
        if (w == null) return null;

        double x = (region.getMinX() + region.getMaxX()) / 2.0 + 0.5;
        double z = (region.getMinZ() + region.getMaxZ()) / 2.0 + 0.5;

        int y = w.getHighestBlockYAt((int) x, (int) z);
        y = Math.max(region.getMinY(), Math.min(region.getMaxY(), y));
        return new Location(w, x, y + 1.0, z);
    }
}
