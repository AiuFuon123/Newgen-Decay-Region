package newgen.decayregion.gui;

import newgen.decayregion.DecayRegionPlugin;
import newgen.decayregion.region.DecayRegion;
import newgen.decayregion.region.RegionManager;
import newgen.decayregion.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DecayMenuListener implements Listener {

    private final DecayRegionPlugin plugin;
    private final RegionManager regionManager;

    private final Map<UUID, String> renameWaiting = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastMainPage = new ConcurrentHashMap<>();

    public DecayMenuListener(DecayRegionPlugin plugin, RegionManager regionManager) {
        this.plugin = plugin;
        this.regionManager = regionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (title == null) return;

        boolean isOurGui =
                title.startsWith(DecayMenuGUI.TITLE_MAIN_PREFIX)
                        || title.startsWith(DecayMenuGUI.TITLE_REGION_PREFIX)
                        || title.startsWith(DecayMenuGUI.TITLE_TIME_PREFIX)
                        || title.startsWith(DecayMenuGUI.TITLE_CONFIRM_FORCE_PREFIX)
                        || title.startsWith(DecayMenuGUI.TITLE_CONFIRM_SAVE_PREFIX)
                        || title.startsWith(DecayMenuGUI.TITLE_CONFIRM_DELETE_PREFIX);

        if (!isOurGui) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int slot = event.getRawSlot();

        if (title.startsWith(DecayMenuGUI.TITLE_MAIN_PREFIX)) {
            int currentPage = parsePage(title);

            if (slot == 49) {
                click(player);
                player.closeInventory();
                return;
            }

            if (slot == 45 && clicked.getType() == Material.ARROW) {
                click(player);
                int prev = currentPage - 1;
                if (prev < 1) return;
                DecayMenuGUI.openMain(player, plugin, regionManager, prev);
                lastMainPage.put(player.getUniqueId(), prev);
                return;
            }

            if (slot == 53 && clicked.getType() == Material.ARROW) {
                click(player);
                int next = currentPage + 1;
                DecayMenuGUI.openMain(player, plugin, regionManager, next);
                lastMainPage.put(player.getUniqueId(), next);
                return;
            }

            if (clicked.getType() == Material.BOOK) {
                String regionName = stripColor(clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : null);
                if (regionName == null || regionName.isEmpty()) return;

                DecayRegion region = regionManager.getRegion(regionName);
                if (region == null) return;

                lastMainPage.put(player.getUniqueId(), currentPage);

                ClickType ct = event.getClick();
                if (ct == ClickType.RIGHT || ct == ClickType.SHIFT_RIGHT) {
                    click(player);
                    DecayMenuGUI.openDeleteConfirm(player, region);
                } else {
                    click(player);
                    DecayMenuGUI.openRegionManage(player, region);
                }
            }
            return;
        }

        if (title.startsWith(DecayMenuGUI.TITLE_REGION_PREFIX)) {
            String regionName = title.substring(DecayMenuGUI.TITLE_REGION_PREFIX.length());
            DecayRegion region = regionManager.getRegion(regionName);
            if (region == null) {
                DecayMenuGUI.openMain(player, plugin, regionManager, lastMainPage.getOrDefault(player.getUniqueId(), 1));
                return;
            }

            if (slot == 11) {
                click(player);
                DecayMenuGUI.openTimeEditor(player, region);
                return;
            }
            if (slot == 12) {
                click(player);
                DecayMenuGUI.openForceClearConfirm(player, region);
                return;
            }
            if (slot == 13) {
                click(player);
                Location tp = DecayMenuGUI.center(region);
                if (tp != null) {
                    player.teleport(tp);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    MessageUtil.send(player, "&aTeleported to region &e" + region.getName() + "&a.");
                }
                return;
            }
            if (slot == 14) {
                click(player);
                DecayMenuGUI.openSaveConfirm(player, region);
                return;
            }
            if (slot == 15) {
                click(player);
                renameWaiting.put(player.getUniqueId(), region.getName());
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1f, 1f);
                MessageUtil.send(player, "&eType a new name for region &b" + region.getName() + "&e in chat. Type &ccancel&e to cancel.");
                return;
            }

            if (slot == 18) {
                click(player);
                int page = lastMainPage.getOrDefault(player.getUniqueId(), 1);
                DecayMenuGUI.openMain(player, plugin, regionManager, page);
                return;
            }

            return;
        }

        if (title.startsWith(DecayMenuGUI.TITLE_TIME_PREFIX)) {
            String regionName = title.substring(DecayMenuGUI.TITLE_TIME_PREFIX.length());
            DecayRegion region = regionManager.getRegion(regionName);
            if (region == null) {
                DecayMenuGUI.openMain(player, plugin, regionManager, lastMainPage.getOrDefault(player.getUniqueId(), 1));
                return;
            }

            if (slot == 10) {
                click(player);
                region.setDecaySeconds(region.getDecaySeconds() + 1);
                regionManager.saveRegions();
                DecayMenuGUI.openTimeEditor(player, region);
                return;
            }
            if (slot == 11) {
                click(player);
                region.setDecaySeconds(region.getDecaySeconds() + 5);
                regionManager.saveRegions();
                DecayMenuGUI.openTimeEditor(player, region);
                return;
            }
            if (slot == 12) {
                click(player);
                region.setDecaySeconds(region.getDecaySeconds() + 30);
                regionManager.saveRegions();
                DecayMenuGUI.openTimeEditor(player, region);
                return;
            }
            if (slot == 14) {
                click(player);
                region.setDecaySeconds(Math.max(1, region.getDecaySeconds() - 1));
                regionManager.saveRegions();
                DecayMenuGUI.openTimeEditor(player, region);
                return;
            }
            if (slot == 15) {
                click(player);
                region.setDecaySeconds(Math.max(1, region.getDecaySeconds() - 5));
                regionManager.saveRegions();
                DecayMenuGUI.openTimeEditor(player, region);
                return;
            }
            if (slot == 16) {
                click(player);
                region.setDecaySeconds(Math.max(1, region.getDecaySeconds() - 30));
                regionManager.saveRegions();
                DecayMenuGUI.openTimeEditor(player, region);
                return;
            }

            if (slot == 22) {
                click(player);
                DecayMenuGUI.openRegionManage(player, region);
                return;
            }
            return;
        }

        if (title.startsWith(DecayMenuGUI.TITLE_CONFIRM_FORCE_PREFIX)) {
            String regionName = title.substring(DecayMenuGUI.TITLE_CONFIRM_FORCE_PREFIX.length());
            DecayRegion region = regionManager.getRegion(regionName);
            if (region == null) {
                DecayMenuGUI.openMain(player, plugin, regionManager, lastMainPage.getOrDefault(player.getUniqueId(), 1));
                return;
            }

            if (slot == 11) {
                click(player);

                if (plugin.getPlacedDataStore() != null) {
                    plugin.getPlacedDataStore().forceClearRegion(region.getName());
                }
                if (plugin.getSnapshotStore() != null) {
                    plugin.getSnapshotStore().restoreRegion(region);
                }

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                MessageUtil.send(player, "&aForce cleared + restored snapshot for region &e" + region.getName() + "&a.");
                DecayMenuGUI.openRegionManage(player, region);
                return;
            }

            if (slot == 15) {
                click(player);
                player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1f, 1f);
                DecayMenuGUI.openRegionManage(player, region);
                return;
            }

            return;
        }

        if (title.startsWith(DecayMenuGUI.TITLE_CONFIRM_SAVE_PREFIX)) {
            String regionName = title.substring(DecayMenuGUI.TITLE_CONFIRM_SAVE_PREFIX.length());
            DecayRegion region = regionManager.getRegion(regionName);
            if (region == null) {
                DecayMenuGUI.openMain(player, plugin, regionManager, lastMainPage.getOrDefault(player.getUniqueId(), 1));
                return;
            }

            if (slot == 11) {
                click(player);

                boolean ok = plugin.getSnapshotStore() != null && plugin.getSnapshotStore().snapshotRegion(region);
                if (ok) {
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                    MessageUtil.send(player, "&aSaved snapshot for region &e" + region.getName() + "&a.");
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    MessageUtil.send(player, "&cFailed to save snapshot.");
                }

                DecayMenuGUI.openRegionManage(player, region);
                return;
            }

            if (slot == 15) {
                click(player);
                player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1f, 1f);
                DecayMenuGUI.openRegionManage(player, region);
                return;
            }

            return;
        }

        if (title.startsWith(DecayMenuGUI.TITLE_CONFIRM_DELETE_PREFIX)) {
            String regionName = title.substring(DecayMenuGUI.TITLE_CONFIRM_DELETE_PREFIX.length());
            DecayRegion region = regionManager.getRegion(regionName);
            if (region == null) {
                DecayMenuGUI.openMain(player, plugin, regionManager, lastMainPage.getOrDefault(player.getUniqueId(), 1));
                return;
            }

            if (slot == 11) {
                click(player);

                try {
                    if (plugin.getPlacedDataStore() != null) {
                        plugin.getPlacedDataStore().forceClearRegion(region.getName());
                    }
                } catch (Throwable ignored) {}

                boolean removed = regionManager.removeRegion(region.getName());
                regionManager.saveRegions();

                if (removed) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
                    MessageUtil.send(player, "&aDeleted region &e" + region.getName() + "&a.");
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    MessageUtil.send(player, "&cFailed to delete region &e" + region.getName() + "&c.");
                }

                DecayMenuGUI.openMain(player, plugin, regionManager, lastMainPage.getOrDefault(player.getUniqueId(), 1));
                return;
            }

            if (slot == 15) {
                click(player);
                DecayMenuGUI.openMain(player, plugin, regionManager, lastMainPage.getOrDefault(player.getUniqueId(), 1));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String oldName = renameWaiting.get(player.getUniqueId());
        if (oldName == null) return;

        event.setCancelled(true);

        String msg = event.getMessage().trim();
        if (msg.equalsIgnoreCase("cancel")) {
            renameWaiting.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1f, 1f);
                MessageUtil.send(player, "&7Rename cancelled.");
                DecayRegion region = regionManager.getRegion(oldName);
                if (region != null) DecayMenuGUI.openRegionManage(player, region);
                else DecayMenuGUI.openMain(player, plugin, regionManager, lastMainPage.getOrDefault(player.getUniqueId(), 1));
            });
            return;
        }

        String newName = msg.replaceAll("[^a-zA-Z0-9_\\-]", "");
        if (newName.isBlank()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                MessageUtil.send(player, "&cInvalid name. Only letters/numbers/_/- are allowed.");
            });
            return;
        }

        if (regionManager.getRegion(newName) != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                MessageUtil.send(player, "&cName &e" + newName + "&c already exists.");
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            renameWaiting.remove(player.getUniqueId());

            boolean ok = regionManager.renameRegion(oldName, newName);
            if (!ok) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                MessageUtil.send(player, "&cFailed to rename region.");
                DecayMenuGUI.openMain(player, plugin, regionManager, lastMainPage.getOrDefault(player.getUniqueId(), 1));
                return;
            }

            if (plugin.getPlacedDataStore() != null) {
                plugin.getPlacedDataStore().renameRegionKey(oldName, newName);
            }
            if (plugin.getSnapshotStore() != null) {
                plugin.getSnapshotStore().renameSnapshotKey(oldName, newName);
            }

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
            MessageUtil.send(player, "&aRenamed region &e" + oldName + " &a-> &b" + newName + "&a.");

            DecayRegion region = regionManager.getRegion(newName);
            if (region != null) DecayMenuGUI.openRegionManage(player, region);
            else DecayMenuGUI.openMain(player, plugin, regionManager, lastMainPage.getOrDefault(player.getUniqueId(), 1));
        });
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    private static String stripColor(String s) {
        if (s == null) return null;
        return s.replace("§l", "")
                .replace("§k", "")
                .replace("§m", "")
                .replace("§n", "")
                .replace("§o", "")
                .replaceAll("§[0-9a-fA-F]", "")
                .trim();
    }

    private static int parsePage(String title) {
        int idx = title.lastIndexOf('(');
        int idx2 = title.lastIndexOf(')');
        if (idx < 0 || idx2 < 0 || idx2 <= idx) return 1;
        String inside = title.substring(idx + 1, idx2);
        String[] parts = inside.split("/");
        if (parts.length < 1) return 1;
        try {
            return Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {
            return 1;
        }
    }
}
