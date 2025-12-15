package newgen.decayregion;

import newgen.decayregion.command.DecayRegionCommand;
import newgen.decayregion.gui.DecayMenuListener;
import newgen.decayregion.listener.DecayRegionListener;
import newgen.decayregion.listener.EntityDecayListener;
import newgen.decayregion.manager.BlockDecayManager;
import newgen.decayregion.manager.PlacedDataStore;
import newgen.decayregion.manager.RegionSnapshotStore;
import newgen.decayregion.region.DecayRegion;
import newgen.decayregion.region.RegionManager;
import newgen.decayregion.selection.SelectionManager;
import newgen.decayregion.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class DecayRegionPlugin extends JavaPlugin {

    private static DecayRegionPlugin instance;

    private RegionManager regionManager;
    private BlockDecayManager blockDecayManager;
    private SelectionManager selectionManager;
    private PlacedDataStore placedDataStore;

    private RegionSnapshotStore snapshotStore;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        MessageUtil.init(this);

        createRegionFile();

        regionManager = new RegionManager(this);
        regionManager.loadRegions();

        snapshotStore = new RegionSnapshotStore(this);

        placedDataStore = new PlacedDataStore(this, regionManager);
        placedDataStore.forceClearAllOnStartupIfEnabled();

        for (DecayRegion r : regionManager.getRegions()) {
            snapshotStore.restoreRegion(r);
        }

        selectionManager = new SelectionManager();
        blockDecayManager = new BlockDecayManager(this, regionManager, placedDataStore);

        getServer().getPluginManager().registerEvents(
                new DecayRegionListener(regionManager, blockDecayManager, selectionManager), this
        );
        getServer().getPluginManager().registerEvents(
                new DecayMenuListener(this, regionManager), this
        );
        getServer().getPluginManager().registerEvents(
                new EntityDecayListener(this, regionManager), this
        );

        PluginCommand cmd = getCommand("decay");
        if (cmd != null) {
            DecayRegionCommand executor = new DecayRegionCommand(regionManager, selectionManager, this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        int flushSeconds = getCfg().getInt("placed-data.flush-seconds", 5);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (placedDataStore != null) placedDataStore.flushIfDirty();
        }, flushSeconds * 20L, flushSeconds * 20L);

        getLogger().info("DecayRegion enabled.");
    }

    @Override
    public void onDisable() {
        regionManager.saveRegions();

        if (placedDataStore != null) {
            placedDataStore.flushIfDirty();
            placedDataStore.save();
            try { placedDataStore.close(); } catch (Throwable ignored) {}
        }

        if (snapshotStore != null) {
            try { snapshotStore.close(); } catch (Throwable ignored) {}
        }

        getLogger().info("DecayRegion disabled.");
    }

    private void createRegionFile() {
        File file = new File(getDataFolder(), "decay_region.yml");
        if (!file.exists()) saveResource("decay_region.yml", false);
    }

    public void reloadPlugin() {
        reloadConfig();

        regionManager.loadRegions();

        if (snapshotStore != null) snapshotStore.reload();
        if (placedDataStore != null) placedDataStore.reload();
    }

    public static DecayRegionPlugin getInstance() {
        return instance;
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public BlockDecayManager getBlockDecayManager() {
        return blockDecayManager;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public PlacedDataStore getPlacedDataStore() {
        return placedDataStore;
    }

    public RegionSnapshotStore getSnapshotStore() {
        return snapshotStore;
    }

    public FileConfiguration getCfg() {
        return getConfig();
    }
}
