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

    public DecayRegionListener(RegionManager regionManager, BlockDecayManager blockDecayManager, SelectionManager selectionManager) {
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

    /**
     * ✅ FIX: Nếu đang cầm Decay wand thì CHẶN phá block (để left-click chỉ dùng chọn pos1)
     */
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

    /**
     * ✅ FIX: Decay wand selection
     * - Left-click block -> pos1
     * - Right-click block -> pos2
     *
     * Lưu ý: ignoreCancelled = false để wand vẫn ăn nếu nơi khác cancel interact.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        // ✅ FIX 1.21.8: event bắn 2 lần (MAIN_HAND + OFF_HAND) -> chỉ xử lý MAIN_HAND
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // 1) wand first
        if (isDecayWand(player.getInventory().getItemInMainHand())) {
            if (event.getClickedBlock() == null) return;

            switch (event.getAction()) {
                case LEFT_CLICK_BLOCK -> {
                    selectionManager.setPos1(player.getUniqueId(), event.getClickedBlock().getLocation());
                    MessageUtil.send(player, "&aĐã đặt &bpos1&a.");
                    event.setCancelled(true);
                }
                case RIGHT_CLICK_BLOCK -> {
                    selectionManager.setPos2(player.getUniqueId(), event.getClickedBlock().getLocation());
                    MessageUtil.send(player, "&aĐã đặt &bpos2&a.");
                    event.setCancelled(true);
                }
                default -> {
                    // ignore
                }
            }
            return;
        }

        // 2) các logic khác
        blockDecayManager.handleRightClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        blockDecayManager.handleBucketFill(event);
    }

    /**
     * Wand đúng theo file của bạn:
     * - Material.BLAZE_ROD
     * - DisplayName: "§bDecayRegion Wand"
     */
    private boolean isDecayWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;

        String name = meta.getDisplayName();
        String stripped = ChatColor.stripColor(name);
        return stripped != null && stripped.equalsIgnoreCase("DecayRegion Wand");
    }
}
