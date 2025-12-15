package newgen.decayregion.listener;

import newgen.decayregion.manager.BlockDecayManager;
import newgen.decayregion.region.RegionManager;
import newgen.decayregion.selection.SelectionManager;
import newgen.decayregion.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class DecayRegionListener implements Listener {

    private final RegionManager regionManager;
    private final BlockDecayManager blockDecayManager;
    private final SelectionManager selectionManager;

    public DecayRegionListener(
            RegionManager regionManager,
            BlockDecayManager blockDecayManager,
            SelectionManager selectionManager
    ) {
        this.regionManager = regionManager;
        this.blockDecayManager = blockDecayManager;
        this.selectionManager = selectionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        blockDecayManager.handleBlockPlace(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        blockDecayManager.handleBucketEmpty(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isDecayWand(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }
        blockDecayManager.handleBlockBreak(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        blockDecayManager.handleBlockFlow(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        blockDecayManager.handleBlockForm(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        if (isDecayWand(player.getInventory().getItemInMainHand())) {
            if (event.getClickedBlock() == null) return;

            switch (event.getAction()) {
                case LEFT_CLICK_BLOCK -> {
                    selectionManager.setPos1(
                            player.getUniqueId(),
                            event.getClickedBlock().getLocation()
                    );
                    MessageUtil.send(player, "&aPos1 has been set.");
                    event.setCancelled(true);
                }
                case RIGHT_CLICK_BLOCK -> {
                    selectionManager.setPos2(
                            player.getUniqueId(),
                            event.getClickedBlock().getLocation()
                    );
                    MessageUtil.send(player, "&aPos2 has been set.");
                    event.setCancelled(true);
                }
                default -> {}
            }
            return;
        }

        blockDecayManager.handleRightClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        blockDecayManager.handleBucketFill(event);
    }

    private boolean isDecayWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;

        String stripped = ChatColor.stripColor(meta.getDisplayName());
        return stripped != null && stripped.equalsIgnoreCase("DecayRegion Wand");
    }
}
