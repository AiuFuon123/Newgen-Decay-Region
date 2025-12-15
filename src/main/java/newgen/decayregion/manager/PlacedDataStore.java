package newgen.decayregion.manager;

import newgen.decayregion.DecayRegionPlugin;
import newgen.decayregion.region.DecayRegion;
import newgen.decayregion.region.RegionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;

import java.io.File;
import java.sql.*;
import java.util.*;

public class PlacedDataStore {

    private final DecayRegionPlugin plugin;
    private final RegionManager regionManager;

    private final File dbFile;
    private Connection conn;

    private volatile boolean dirty = false;
    private volatile boolean schemaReady = false;

    public PlacedDataStore(DecayRegionPlugin plugin, RegionManager regionManager) {
        this.plugin = plugin;
        this.regionManager = regionManager;
        this.dbFile = new File(plugin.getDataFolder(), "data.db");
        reload();
    }

    // =========================
    // LIFECYCLE
    // =========================

    public synchronized void reload() {
        closeQuietly();

        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            // ✅ tạo schema chắc chắn trong autoCommit=true
            this.conn.setAutoCommit(true);
            initPragmas();
            initSchema(); // create tables + indexes (auto-commit)
            this.schemaReady = true;

            // ✅ sau đó chuyển sang batch commit (autoCommit=false)
            this.conn.setAutoCommit(false);

            // Optional one-time migration
            migrateFromYamlIfPresent();

        } catch (Exception e) {
            schemaReady = false;
            plugin.getLogger().severe("[PlacedDataStore] Cannot open/init data.db: " + e.getMessage());
        }

        dirty = false;
    }

    public synchronized void save() {
        flushIfDirty();
    }

    public synchronized void flushIfDirty() {
        if (!dirty) return;
        try {
            ensureSchema();
            if (conn != null) conn.commit();
            dirty = false;
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] Commit failed: " + e.getMessage());
            try { if (conn != null) conn.rollback(); } catch (Exception ignored) {}
        }
    }

    public synchronized void close() {
        flushIfDirty();
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (conn != null) conn.close();
        } catch (Exception ignored) {}
        conn = null;
        schemaReady = false;
    }

    // =========================
    // SCHEMA
    // =========================

    private void initPragmas() {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA temp_store=MEMORY;");
            st.execute("PRAGMA foreign_keys=ON;");
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] PRAGMA failed: " + e.getMessage());
        }
    }

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {

            st.execute("CREATE TABLE IF NOT EXISTS placed_blocks (" +
                    "region TEXT NOT NULL," +
                    "world  TEXT NOT NULL," +
                    "x      INTEGER NOT NULL," +
                    "y      INTEGER NOT NULL," +
                    "z      INTEGER NOT NULL," +
                    "PRIMARY KEY(region, world, x, y, z)" +
                    ");");

            st.execute("CREATE TABLE IF NOT EXISTS placed_entities (" +
                    "region TEXT NOT NULL," +
                    "uuid   TEXT NOT NULL PRIMARY KEY" +
                    ");");

            st.execute("CREATE TABLE IF NOT EXISTS fluid_sources (" +
                    "region TEXT NOT NULL," +
                    "world  TEXT NOT NULL," +
                    "x      INTEGER NOT NULL," +
                    "y      INTEGER NOT NULL," +
                    "z      INTEGER NOT NULL," +
                    "type   TEXT NOT NULL," +
                    "PRIMARY KEY(region, world, x, y, z, type)" +
                    ");");

            st.execute("CREATE INDEX IF NOT EXISTS idx_blocks_region_world_xyz ON placed_blocks(region, world, x, y, z);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_entities_region ON placed_entities(region);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_fluids_region_world_xyz ON fluid_sources(region, world, x, y, z);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_fluids_region_world_type ON fluid_sources(region, world, type);");
        }
    }

    private boolean tableExists(String table) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=? LIMIT 1")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized void ensureSchema() {
        if (conn == null) return;
        if (schemaReady && tableExists("placed_blocks") && tableExists("placed_entities") && tableExists("fluid_sources")) return;

        try {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(true);
            initSchema();
            conn.setAutoCommit(prev);
            schemaReady = true;
            plugin.getLogger().info("[PlacedDataStore] Schema ensured (tables created).");
        } catch (Exception e) {
            schemaReady = false;
            plugin.getLogger().severe("[PlacedDataStore] ensureSchema failed: " + e.getMessage());
        }
    }

    // =========================
    // MIGRATION (legacy YAML -> DB)
    // =========================

    private void migrateFromYamlIfPresent() {
        File legacy = new File(plugin.getDataFolder(), "placed-data.yml");
        if (!legacy.exists()) return;

        // if db already has rows -> skip
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT " +
                     "(SELECT COUNT(*) FROM placed_blocks) + " +
                     "(SELECT COUNT(*) FROM placed_entities) + " +
                     "(SELECT COUNT(*) FROM fluid_sources)")) {
            if (rs.next() && rs.getLong(1) > 0) return;
        } catch (Exception ignored) {}

        YamlConfiguration data = YamlConfiguration.loadConfiguration(legacy);
        if (!data.isConfigurationSection("regions")) return;

        var sec = data.getConfigurationSection("regions");
        if (sec == null) return;

        int importedBlocks = 0, importedEntities = 0, importedFluids = 0;

        try {
            ensureSchema();

            try (PreparedStatement pb = conn.prepareStatement(
                    "INSERT OR REPLACE INTO placed_blocks(region, world, x, y, z) VALUES(?,?,?,?,?)");
                 PreparedStatement pe = conn.prepareStatement(
                         "INSERT OR REPLACE INTO placed_entities(region, uuid) VALUES(?,?)");
                 PreparedStatement pf = conn.prepareStatement(
                         "INSERT OR REPLACE INTO fluid_sources(region, world, x, y, z, type) VALUES(?,?,?,?,?,?)")) {

                for (String regionKey : sec.getKeys(false)) {
                    String r = regionKey.toLowerCase();

                    for (String s : data.getStringList("regions." + regionKey + ".blocks")) {
                        String[] p = s.split(";");
                        if (p.length != 4) continue;
                        try {
                            pb.setString(1, r);
                            pb.setString(2, p[0]);
                            pb.setInt(3, Integer.parseInt(p[1]));
                            pb.setInt(4, Integer.parseInt(p[2]));
                            pb.setInt(5, Integer.parseInt(p[3]));
                            pb.addBatch();
                            importedBlocks++;
                        } catch (Exception ignored) {}
                    }

                    for (String s : data.getStringList("regions." + regionKey + ".entities")) {
                        try {
                            UUID u = UUID.fromString(s);
                            pe.setString(1, r);
                            pe.setString(2, u.toString());
                            pe.addBatch();
                            importedEntities++;
                        } catch (Exception ignored) {}
                    }

                    for (String s : data.getStringList("regions." + regionKey + ".fluidSources")) {
                        String[] p = s.split(";");
                        if (p.length < 5) continue;
                        try {
                            Material t = Material.valueOf(p[4]);
                            if (t != Material.WATER && t != Material.LAVA) continue;

                            pf.setString(1, r);
                            pf.setString(2, p[0]);
                            pf.setInt(3, Integer.parseInt(p[1]));
                            pf.setInt(4, Integer.parseInt(p[2]));
                            pf.setInt(5, Integer.parseInt(p[3]));
                            pf.setString(6, t.name());
                            pf.addBatch();
                            importedFluids++;
                        } catch (Exception ignored) {}
                    }
                }

                pb.executeBatch();
                pe.executeBatch();
                pf.executeBatch();
                conn.commit();
                dirty = false;

                plugin.getLogger().info("[PlacedDataStore] Migrated placed-data.yml -> data.db (blocks="
                        + importedBlocks + ", entities=" + importedEntities + ", fluids=" + importedFluids + ")");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] Migration failed: " + e.getMessage());
            try { conn.rollback(); } catch (Exception ignored) {}
        }
    }

    // =========================
    // RECORD API
    // =========================

    public synchronized void recordBlock(DecayRegion region, Location loc) {
        if (region == null || loc == null || loc.getWorld() == null) return;
        if (conn == null) return;

        ensureSchema();

        String r = region.getName().toLowerCase();

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO placed_blocks(region, world, x, y, z) VALUES(?,?,?,?,?)")) {
            ps.setString(1, r);
            ps.setString(2, loc.getWorld().getName());
            ps.setInt(3, loc.getBlockX());
            ps.setInt(4, loc.getBlockY());
            ps.setInt(5, loc.getBlockZ());
            ps.executeUpdate();
            dirty = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] recordBlock failed: " + e.getMessage());
        }
    }

    public synchronized void removeBlock(DecayRegion region, Location loc) {
        if (region == null || loc == null || loc.getWorld() == null) return;
        if (conn == null) return;

        ensureSchema();

        String r = region.getName().toLowerCase();

        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM placed_blocks WHERE region=? AND world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, r);
            ps.setString(2, loc.getWorld().getName());
            ps.setInt(3, loc.getBlockX());
            ps.setInt(4, loc.getBlockY());
            ps.setInt(5, loc.getBlockZ());
            ps.executeUpdate();
            dirty = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] removeBlock failed: " + e.getMessage());
        }
    }

    public synchronized void recordEntity(DecayRegion region, UUID uuid) {
        if (region == null || uuid == null) return;
        if (conn == null) return;

        ensureSchema();

        String r = region.getName().toLowerCase();

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO placed_entities(region, uuid) VALUES(?,?)")) {
            ps.setString(1, r);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            dirty = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] recordEntity failed: " + e.getMessage());
        }
    }

    public synchronized void removeEntity(DecayRegion region, UUID uuid) {
        if (uuid == null) return;
        if (conn == null) return;

        ensureSchema();

        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM placed_entities WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            dirty = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] removeEntity failed: " + e.getMessage());
        }
    }

    public synchronized void recordFluidSource(DecayRegion region, Location loc, Material type) {
        if (region == null || loc == null || loc.getWorld() == null) return;
        if (type != Material.WATER && type != Material.LAVA) return;
        if (conn == null) return;

        ensureSchema();

        String r = region.getName().toLowerCase();

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO fluid_sources(region, world, x, y, z, type) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, r);
            ps.setString(2, loc.getWorld().getName());
            ps.setInt(3, loc.getBlockX());
            ps.setInt(4, loc.getBlockY());
            ps.setInt(5, loc.getBlockZ());
            ps.setString(6, type.name());
            ps.executeUpdate();
            dirty = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] recordFluidSource failed: " + e.getMessage());
        }
    }

    public synchronized void removeFluidSource(DecayRegion region, Location loc, Material type) {
        if (region == null || loc == null || loc.getWorld() == null) return;
        if (type != Material.WATER && type != Material.LAVA) return;
        if (conn == null) return;

        ensureSchema();

        String r = region.getName().toLowerCase();

        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM fluid_sources WHERE region=? AND world=? AND x=? AND y=? AND z=? AND type=?")) {
            ps.setString(1, r);
            ps.setString(2, loc.getWorld().getName());
            ps.setInt(3, loc.getBlockX());
            ps.setInt(4, loc.getBlockY());
            ps.setInt(5, loc.getBlockZ());
            ps.setString(6, type.name());
            ps.executeUpdate();
            dirty = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] removeFluidSource failed: " + e.getMessage());
        }
    }

    public synchronized boolean isNearAnyFluidSource(DecayRegion region, Location loc, int radius) {
        if (region == null || loc == null || loc.getWorld() == null) return false;
        if (conn == null) return false;

        ensureSchema();

        String r = region.getName().toLowerCase();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        String sql = "SELECT 1 FROM fluid_sources WHERE region=? AND world=? AND " +
                "x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ? LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r);
            ps.setString(2, loc.getWorld().getName());
            ps.setInt(3, bx - radius);
            ps.setInt(4, bx + radius);
            ps.setInt(5, by - radius);
            ps.setInt(6, by + radius);
            ps.setInt(7, bz - radius);
            ps.setInt(8, bz + radius);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] isNearAnyFluidSource failed: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // FORCE CLEAR
    // =========================

    private void floodClearFluid(String regionNameLower, Location start, Material fluidType, int maxFlood) {
        if (start == null || start.getWorld() == null) return;

        Deque<Location> q = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();

        q.add(start);
        int processed = 0;

        while (!q.isEmpty() && processed < maxFlood) {
            Location loc = q.poll();
            if (loc == null || loc.getWorld() == null) continue;

            long packed = pack(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (!visited.add(packed)) continue;

            DecayRegion now = regionManager.getRegionAt(loc);
            if (now == null || !now.getName().equalsIgnoreCase(regionNameLower)) continue;

            if (loc.getBlock().getType() != fluidType) continue;

            loc.getBlock().setType(Material.AIR, false);
            processed++;

            q.add(loc.clone().add(1, 0, 0));
            q.add(loc.clone().add(-1, 0, 0));
            q.add(loc.clone().add(0, 1, 0));
            q.add(loc.clone().add(0, -1, 0));
            q.add(loc.clone().add(0, 0, 1));
            q.add(loc.clone().add(0, 0, -1));
        }

        if (!q.isEmpty() && processed >= maxFlood) {
            plugin.getLogger().warning("[ForceClear] floodClear hit maxFlood=" + maxFlood
                    + " region=" + regionNameLower + " type=" + fluidType
                    + " start=" + start.getWorld().getName() + "," + start.getBlockX() + "," + start.getBlockY() + "," + start.getBlockZ());
        }
    }

    public void forceClearRegion(String regionName) {
        if (regionName == null) return;
        if (conn == null) return;

        ensureSchema();

        String r = regionName.toLowerCase();

        // 1) blocks
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT world,x,y,z FROM placed_blocks WHERE region=?")) {
            ps.setString(1, r);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    World w = Bukkit.getWorld(rs.getString(1));
                    if (w == null) continue;
                    int x = rs.getInt(2), y = rs.getInt(3), z = rs.getInt(4);
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] forceClear blocks failed: " + e.getMessage());
        }

        // 2) entities
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid FROM placed_entities WHERE region=?")) {
            ps.setString(1, r);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid;
                    try { uuid = UUID.fromString(rs.getString(1)); } catch (Exception ex) { continue; }
                    Entity e = null;
                    for (World w : Bukkit.getWorlds()) {
                        e = w.getEntity(uuid);
                        if (e != null) break;
                    }
                    if (e != null && e.isValid()) e.remove();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] forceClear entities failed: " + e.getMessage());
        }

        // 3) fluids
        int maxFlood = plugin.getCfg().getInt("force-clear.max-flood-blocks", 500000);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT world,x,y,z,type FROM fluid_sources WHERE region=?")) {
            ps.setString(1, r);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    World w = Bukkit.getWorld(rs.getString(1));
                    if (w == null) continue;
                    int x = rs.getInt(2), y = rs.getInt(3), z = rs.getInt(4);
                    Material type;
                    try { type = Material.valueOf(rs.getString(5)); } catch (Exception ex) { continue; }
                    if (type != Material.WATER && type != Material.LAVA) continue;

                    floodClearFluid(r, new Location(w, x, y, z), type, maxFlood);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] forceClear fluids failed: " + e.getMessage());
        }

        // 4) wipe rows
        synchronized (this) {
            try (PreparedStatement a = conn.prepareStatement("DELETE FROM placed_blocks WHERE region=?");
                 PreparedStatement b = conn.prepareStatement("DELETE FROM placed_entities WHERE region=?");
                 PreparedStatement c = conn.prepareStatement("DELETE FROM fluid_sources WHERE region=?")) {
                a.setString(1, r);
                b.setString(1, r);
                c.setString(1, r);
                a.executeUpdate();
                b.executeUpdate();
                c.executeUpdate();
                dirty = true;
            } catch (Exception e) {
                plugin.getLogger().warning("[PlacedDataStore] wipe region failed: " + e.getMessage());
            }
        }
    }

    public void forceClearAllOnStartupIfEnabled() {
        if (conn == null) return;
        ensureSchema();

        if (!plugin.getCfg().getBoolean("force-clear-on-startup", true)) return;

        Set<String> regions = new HashSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT region FROM (" +
                     "SELECT region FROM placed_blocks UNION ALL SELECT region FROM placed_entities UNION ALL SELECT region FROM fluid_sources" +
                     ")")) {
            while (rs.next()) {
                String r = rs.getString(1);
                if (r != null && !r.isBlank()) regions.add(r);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] list regions on startup failed: " + e.getMessage());
        }

        for (String r : regions) {
            forceClearRegion(r);
        }

        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM placed_blocks;");
            st.executeUpdate("DELETE FROM placed_entities;");
            st.executeUpdate("DELETE FROM fluid_sources;");
            dirty = true;
            conn.commit();
            dirty = false;
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] wipe tables on startup failed: " + e.getMessage());
        }

        plugin.getLogger().info("Force-cleared blocks/entities/WATER/LAVA from data.db on startup.");
    }

    // =========================
    // RENAME
    // =========================

    public synchronized void renameRegionKey(String oldName, String newName) {
        if (oldName == null || newName == null) return;
        if (conn == null) return;

        ensureSchema();

        String oldKey = oldName.toLowerCase();
        String newKey = newName.toLowerCase();
        if (oldKey.equals(newKey)) return;

        try (PreparedStatement a = conn.prepareStatement("UPDATE placed_blocks SET region=? WHERE region=?");
             PreparedStatement b = conn.prepareStatement("UPDATE placed_entities SET region=? WHERE region=?");
             PreparedStatement c = conn.prepareStatement("UPDATE fluid_sources SET region=? WHERE region=?")) {
            a.setString(1, newKey);
            a.setString(2, oldKey);
            b.setString(1, newKey);
            b.setString(2, oldKey);
            c.setString(1, newKey);
            c.setString(2, oldKey);
            a.executeUpdate();
            b.executeUpdate();
            c.executeUpdate();
            dirty = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[PlacedDataStore] renameRegionKey failed: " + e.getMessage());
        }
    }

    // =========================
    // UTILS
    // =========================

    private static long pack(int x, int y, int z) {
        long lx = (x & 0x3FFFFFFL);
        long lz = (z & 0x3FFFFFFL);
        long ly = (y & 0xFFFL);
        return (lx << 38) | (lz << 12) | ly;
    }
}
