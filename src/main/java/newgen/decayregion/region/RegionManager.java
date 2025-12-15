package newgen.decayregion.region;

import newgen.decayregion.DecayRegionPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RegionManager {

    private final DecayRegionPlugin plugin;
    private final Map<String, DecayRegion> regions = new HashMap<>();
    private final File regionFile;
    private YamlConfiguration regionConfig;

    public RegionManager(DecayRegionPlugin plugin) {
        this.plugin = plugin;
        this.regionFile = new File(plugin.getDataFolder(), "decay_region.yml");
        this.regionConfig = YamlConfiguration.loadConfiguration(regionFile);
    }

    public void loadRegions() {
        regions.clear();
        regionConfig = YamlConfiguration.loadConfiguration(regionFile);

        ConfigurationSection root = regionConfig.getConfigurationSection("regions");
        if (root == null) return;

        int defaultDecay = plugin.getConfig().getInt("default-decay-seconds", 30);

        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) continue;

            String world = sec.getString("world");
            if (world == null) continue;

            ConfigurationSection min = sec.getConfigurationSection("min");
            ConfigurationSection max = sec.getConfigurationSection("max");
            if (min == null || max == null) continue;

            int minX = min.getInt("x");
            int minY = min.getInt("y");
            int minZ = min.getInt("z");
            int maxX = max.getInt("x");
            int maxY = max.getInt("y");
            int maxZ = max.getInt("z");

            int decaySeconds = sec.getInt("decay-seconds", defaultDecay);

            DecayRegion region = new DecayRegion(name, world, minX, minY, minZ, maxX, maxY, maxZ, decaySeconds);
            regions.put(name.toLowerCase(), region);
        }

        plugin.getLogger().info("Loaded " + regions.size() + " decay regions.");
    }

    public void saveRegions() {
        regionConfig = new YamlConfiguration();

        for (DecayRegion region : regions.values()) {
            String path = "regions." + region.getName();
            regionConfig.set(path + ".world", region.getWorldName());

            regionConfig.set(path + ".min.x", region.getMinX());
            regionConfig.set(path + ".min.y", region.getMinY());
            regionConfig.set(path + ".min.z", region.getMinZ());

            regionConfig.set(path + ".max.x", region.getMaxX());
            regionConfig.set(path + ".max.y", region.getMaxY());
            regionConfig.set(path + ".max.z", region.getMaxZ());

            regionConfig.set(path + ".decay-seconds", region.getDecaySeconds());
        }

        try {
            regionConfig.save(regionFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save decay_region.yml");
            e.printStackTrace();
        }
    }

    public void addRegion(DecayRegion region) {
        regions.put(region.getName().toLowerCase(), region);
        saveRegions();
    }

    public boolean removeRegion(String name) {
        DecayRegion removed = regions.remove(name.toLowerCase());
        if (removed == null) return false;
        saveRegions();
        return true;
    }

    public DecayRegion getRegion(String name) {
        if (name == null) return null;
        return regions.get(name.toLowerCase());
    }

    public Collection<DecayRegion> getRegions() {
        return regions.values();
    }

    public boolean isInAnyRegion(Location loc) {
        return getRegionAt(loc) != null;
    }

    public DecayRegion getRegionAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (DecayRegion region : regions.values()) {
            if (region.contains(loc)) return region;
        }
        return null;
    }

    // ✅ Overlap check
    public boolean isOverlapping(DecayRegion candidate) {
        if (candidate == null) return false;

        for (DecayRegion existing : regions.values()) {
            if (existing.getName().equalsIgnoreCase(candidate.getName())) continue;
            if (regionsOverlap(existing, candidate)) return true;
        }
        return false;
    }

    private boolean regionsOverlap(DecayRegion a, DecayRegion b) {
        if (!a.getWorldName().equalsIgnoreCase(b.getWorldName())) return false;

        boolean x = a.getMinX() <= b.getMaxX() && a.getMaxX() >= b.getMinX();
        boolean y = a.getMinY() <= b.getMaxY() && a.getMaxY() >= b.getMinY();
        boolean z = a.getMinZ() <= b.getMaxZ() && a.getMaxZ() >= b.getMinZ();
        return x && y && z;
    }

    // ✅ NEW: rename region (chỉ đổi key + name, không đổi coords)
    public boolean renameRegion(String oldName, String newName) {
        if (oldName == null || newName == null) return false;

        String oldKey = oldName.toLowerCase();
        String newKey = newName.toLowerCase();

        if (!regions.containsKey(oldKey)) return false;
        if (regions.containsKey(newKey)) return false;

        DecayRegion region = regions.remove(oldKey);
        if (region == null) return false;

        region.setName(newName);
        regions.put(newKey, region);

        saveRegions();
        return true;
    }
}
