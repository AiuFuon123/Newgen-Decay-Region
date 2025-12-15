package newgen.decayregion.manager;

import newgen.decayregion.DecayRegionPlugin;
import newgen.decayregion.region.DecayRegion;
import newgen.decayregion.region.RegionManager;
import newgen.decayregion.util.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockDecayManager {

    private final DecayRegionPlugin plugin;
    private final RegionManager regionManager;
    private final PlacedDataStore placedStore;

    private final boolean formedOnlyFromTrackedFluids;
    private final int maxFluidFloodBlocks;

    // ✅ tracking decays
    private final Set<BlockKey> trackedBlocks = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, BukkitTask> activeDecayTasks = new ConcurrentHashMap<>();

    // ✅ dedupe schedule for fluid sources (bucket + infinite)
    private final Set<BlockKey> scheduledFluidSources = ConcurrentHashMap.newKeySet();

    private final Set<UUID> canTakeWater = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Map<BlockKey, Integer> breakSourceIds = new ConcurrentHashMap<>();
    private final AtomicInteger nextSourceId = new AtomicInteger(1);

    private final int defaultDecaySeconds;

    public BlockDecayManager(DecayRegionPlugin plugin, RegionManager regionManager, PlacedDataStore placedStore) {
        this.plugin = plugin;
        this.regionManager = regionManager;
        this.placedStore = placedStore;

        this.defaultDecaySeconds = plugin.getCfg().getInt("default-decay-seconds", 30);
        this.formedOnlyFromTrackedFluids = plugin.getCfg().getBoolean("decay.formed-blocks-only-from-tracked-fluids", true);
        this.maxFluidFloodBlocks = plugin.getCfg().getInt("decay.fluids.max-flood-blocks", 20000);
    }

    // ========= utils =========

    private int getOrAssignSourceId(BlockKey key) {
        return breakSourceIds.computeIfAbsent(key, k -> {
            int id = nextSourceId.getAndIncrement();
            if (id >= 1_000_000) nextSourceId.set(1);
            return id;
        });
    }

    private Integer getSourceId(BlockKey key) {
        return breakSourceIds.get(key);
    }

    private void clearSourceId(BlockKey key) {
        breakSourceIds.remove(key);
    }

    private boolean isWaterlogged(Block block) {
        if (block == null) return false;
        BlockData data = block.getBlockData();
        return (data instanceof Waterlogged wl) && wl.isWaterlogged();
    }

    private int getDecaySecondsAt(Location loc) {
        DecayRegion region = regionManager.getRegionAt(loc);
        return region != null ? region.getDecaySeconds() : defaultDecaySeconds;
    }

    private void resetBlockDamage(Location loc, int sourceId) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendBlockDamage(loc, 0.0f, sourceId);
        }
    }

    /**
     * ✅ reset/cancel toàn bộ trạng thái decay tại 1 vị trí
     */
    private void clearDecayAt(BlockKey key, Location loc) {
        BukkitTask task = activeDecayTasks.remove(key);
        if (task != null) task.cancel();

        Integer sourceId = getSourceId(key);
        if (sourceId != null) resetBlockDamage(loc, sourceId);

        trackedBlocks.remove(key);
        clearSourceId(key);
    }

    // =========================
    // BLOCK PLACE (solid)
    // =========================
    public void handleBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();

        DecayRegion region = regionManager.getRegionAt(block.getLocation());
        if (region == null) return;
        if (player.isOp()) return;

        Material type = block.getType();
        if (type == Material.WATER || type == Material.LAVA) return;

        // ✅ nếu chỗ này có crack cũ, clear trước
        BlockKey key = BlockKey.fromBlock(block);
        clearDecayAt(key, block.getLocation());

        placedStore.recordBlock(region, block.getLocation());
        startSolidBlockDecay(block);
    }

    // =========================
    // BUCKET EMPTY (WATER/LAVA) - delay 1 tick
    // =========================
    public void handleBucketEmpty(PlayerBucketEmptyEvent event) {
        Block clicked = event.getBlockClicked();
        BlockFace face = event.getBlockFace();
        if (clicked == null || face == null) return;

        Player player = event.getPlayer();
        Location placedLoc = clicked.getRelative(face).getLocation();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Material type = placedLoc.getBlock().getType();
            if (type != Material.WATER && type != Material.LAVA) return;

            DecayRegion region = regionManager.getRegionAt(placedLoc);
            if (region == null) return;
            if (player.isOp()) return;

            // ✅ dedupe schedule
            BlockKey k = BlockKey.fromBlock(placedLoc.getBlock());
            if (!scheduledFluidSources.add(k)) return;

            placedStore.recordFluidSource(region, placedLoc, type);

            int seconds = region.getDecaySeconds();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                floodClearFluid(region.getName(), placedLoc, type);
                placedStore.removeFluidSource(region, placedLoc, type);
                scheduledFluidSources.remove(k);
            }, Math.max(1L, seconds * 20L));
        });
    }

    /**
     * Flood fill xóa sạch fluid (cả flow) bắt đầu từ source.
     * Chỉ xóa trong đúng regionName.
     */
    private void floodClearFluid(String regionName, Location start, Material fluidType) {
        if (start == null || start.getWorld() == null) return;

        Deque<Block> q = new ArrayDeque<>();
        Set<BlockKey> visited = ConcurrentHashMap.newKeySet();

        q.add(start.getBlock());
        int processed = 0;

        while (!q.isEmpty() && processed < maxFluidFloodBlocks) {
            Block b = q.poll();
            BlockKey k = BlockKey.fromBlock(b);
            if (!visited.add(k)) continue;

            DecayRegion now = regionManager.getRegionAt(b.getLocation());
            if (now == null || !now.getName().equalsIgnoreCase(regionName)) continue;

            if (b.getType() != fluidType) continue;

            // ✅ nước/lava: xóa sạch, không drop
            b.setType(Material.AIR, false);
            processed++;

            q.add(b.getRelative(1, 0, 0));
            q.add(b.getRelative(-1, 0, 0));
            q.add(b.getRelative(0, 1, 0));
            q.add(b.getRelative(0, -1, 0));
            q.add(b.getRelative(0, 0, 1));
            q.add(b.getRelative(0, 0, -1));
        }
    }

    // =========================
    // SOLID BLOCK DECAY (drop)
    // =========================
    private void startSolidBlockDecay(Block block) {
        final Material original = block.getType();
        final BlockKey key = BlockKey.fromBlock(block);
        final Location loc = block.getLocation();

        clearDecayAt(key, loc);
        trackedBlocks.add(key);

        final int decaySeconds = getDecaySecondsAt(loc);
        final int steps = 5;
        final long interval = Math.max(1L, (long) decaySeconds * 20L / steps);

        final int sourceId = getOrAssignSourceId(key);

        BukkitTask task = new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!regionManager.isInAnyRegion(loc) || block.getType() != original) {
                    clearDecayAt(key, loc);
                    cancel();
                    return;
                }

                step++;
                float progress = (float) step / (float) steps;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendBlockDamage(loc, progress, sourceId);
                }

                if (step >= steps) {
                    resetBlockDamage(loc, sourceId);

                    // ✅ drop item (trừ nước/lava không đi vào đây)
                    block.breakNaturally();

                    DecayRegion r = regionManager.getRegionAt(loc);
                    if (r != null) placedStore.removeBlock(r, loc);

                    clearDecayAt(key, loc);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, interval, interval);

        activeDecayTasks.put(key, task);
    }

    // =========================
    // FORMED BLOCKS (obsidian/cobble/stone) - delay 1 tick
    // =========================
    public void handleBlockForm(BlockFormEvent event) {
        Location loc = event.getBlock().getLocation();
        DecayRegion region = regionManager.getRegionAt(loc);
        if (region == null) return;

        Material newType = event.getNewState().getType();
        if (newType != Material.OBSIDIAN && newType != Material.COBBLESTONE && newType != Material.STONE) return;

        if (formedOnlyFromTrackedFluids && !placedStore.isNearAnyFluidSource(region, loc, 3)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Block real = loc.getBlock();
            Material t = real.getType();
            if (t != Material.OBSIDIAN && t != Material.COBBLESTONE && t != Material.STONE) return;

            BlockKey key = BlockKey.fromBlock(real);
            clearDecayAt(key, loc);

            placedStore.recordBlock(region, loc);
            startSolidBlockDecay(real);
        });
    }

    // =========================
    // FLOW:
    // - giữ logic cũ azalea
    // - ✅ NEW: detect infinite water source created by flow and schedule decay clear
    // =========================
    public void handleBlockFlow(BlockFromToEvent event) {
        Block from = event.getBlock();

        // --- old azalea rule ---
        if (from.getType() == Material.WATER && regionManager.isInAnyRegion(from.getLocation())) {
            Block west = from.getRelative(-1, 0, 0);
            Block east = from.getRelative(1, 0, 0);
            Block south = from.getRelative(0, 0, 1);
            Block north = from.getRelative(0, 0, -1);
            Block below = from.getRelative(0, -1, 0);

            if (west.getType() == Material.AZALEA
                    || east.getType() == Material.AZALEA
                    || south.getType() == Material.AZALEA
                    || north.getType() == Material.AZALEA
                    || below.getType() == Material.AZALEA) {
                event.setCancelled(true);
                return;
            }
        }

        // --- ✅ NEW infinite water handling ---
        if (from.getType() != Material.WATER) return;

        Location toLoc = event.getToBlock().getLocation();

        // delay 1 tick: đảm bảo block "to" đã update xong
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block to = toLoc.getBlock();
            if (to.getType() != Material.WATER) return;

            // chỉ bắt WATER SOURCE (level 0)
            BlockData bd = to.getBlockData();
            if (!(bd instanceof Levelled lvl)) return;
            if (lvl.getLevel() != 0) return;

            DecayRegion region = regionManager.getRegionAt(toLoc);
            if (region == null) return;

            // Chỉ coi là "vô hạn do người chơi tạo" nếu gần water source do người chơi đặt/đã track
            if (!placedStore.isNearAnyFluidSource(region, toLoc, 2)) return;

            BlockKey k = BlockKey.fromBlock(to);
            if (!scheduledFluidSources.add(k)) return; // tránh schedule nhiều lần

            placedStore.recordFluidSource(region, toLoc, Material.WATER);

            int seconds = region.getDecaySeconds();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                floodClearFluid(region.getName(), toLoc, Material.WATER);
                placedStore.removeFluidSource(region, toLoc, Material.WATER);
                scheduledFluidSources.remove(k);
            }, Math.max(1L, seconds * 20L));
        });
    }

    // =========================
    // INTERACT / BUCKET FILL / BREAK
    // =========================
    public void handleRightClick(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (!regionManager.isInAnyRegion(block.getLocation())) return;
        if (player.isOp()) return;

        boolean mainBucket = player.getInventory().getItemInMainHand().getType() == Material.BUCKET;
        boolean offBucket = player.getInventory().getItemInOffHand().getType() == Material.BUCKET;

        if (isWaterlogged(block) && (mainBucket || offBucket)) {
            canTakeWater.add(player.getUniqueId());
        }
    }

    public void handleBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockClicked();

        if (!regionManager.isInAnyRegion(block.getLocation())) return;
        if (player.isOp()) return;

        if (isWaterlogged(block)) {
            canTakeWater.add(player.getUniqueId());
        }
    }

    public void handleBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (!regionManager.isInAnyRegion(block.getLocation())) return;
        if (player.isOp()) return;

        Material type = block.getType();
        BlockKey key = BlockKey.fromBlock(block);

        if (type != Material.WATER && type != Material.LAVA) {
            if (!trackedBlocks.contains(key)) {
                if (!isWaterlogged(block)) {
                    event.setCancelled(true);
                    return;
                } else {
                    UUID uid = player.getUniqueId();
                    if (!canTakeWater.contains(uid)) {
                        event.setCancelled(true);
                        return;
                    } else {
                        canTakeWater.remove(uid);
                    }
                }
            } else {
                // người chơi đập block đang decay -> clear crack + xóa data
                clearDecayAt(key, block.getLocation());

                DecayRegion r = regionManager.getRegionAt(block.getLocation());
                if (r != null) placedStore.removeBlock(r, block.getLocation());
            }
        }
    }
}
