package newgen.decayregion.manager;

import newgen.decayregion.DecayRegionPlugin;
import newgen.decayregion.region.DecayRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

public class RegionSnapshotStore {

    private final DecayRegionPlugin plugin;
    private final File dbFile;
    private Connection conn;

    public RegionSnapshotStore(DecayRegionPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "data.db");
        reload();
    }

    // =========================
    // LIFECYCLE
    // =========================

    public synchronized void reload() {
        close();

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            conn.setAutoCommit(true);

            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA synchronous=NORMAL;");
                st.execute("PRAGMA temp_store=MEMORY;");
            }

            initSchema();

            conn.setAutoCommit(false);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[SnapshotStore] Cannot open data.db", e);
            conn = null;
        }
    }

    public synchronized void close() {
        try {
            if (conn != null) {
                conn.commit();
                conn.close();
            }
        } catch (Exception ignored) {}
        conn = null;
    }

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS region_snapshots (
                    region TEXT NOT NULL,
                    world  TEXT NOT NULL,
                    x      INTEGER NOT NULL,
                    y      INTEGER NOT NULL,
                    z      INTEGER NOT NULL,
                    type   TEXT NOT NULL,
                    data   INTEGER NOT NULL,
                    PRIMARY KEY(region, world, x, y, z)
                );
            """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_snapshot_region ON region_snapshots(region);");
        }
    }

    public synchronized boolean snapshotRegion(DecayRegion region) {
        if (conn == null || region == null) return false;

        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) return false;

        long volume =
                (long) (region.getMaxX() - region.getMinX() + 1)
                        * (long) (region.getMaxY() - region.getMinY() + 1)
                        * (long) (region.getMaxZ() - region.getMinZ() + 1);

        long maxVolume = plugin.getCfg().getLong("snapshot.max-volume", 200_000L);
        if (volume > maxVolume) {
            plugin.getLogger().warning("[SnapshotStore] Snapshot refused: region "
                    + region.getName() + " volume=" + volume + " > " + maxVolume);
            return false;
        }

        boolean nonAirOnly = plugin.getCfg().getBoolean("snapshot.save-non-air-only", false);
        String key = region.getName().toLowerCase();

        try {

            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM region_snapshots WHERE region=?")) {
                del.setString(1, key);
                del.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO region_snapshots(region, world, x, y, z, type, data) VALUES(?,?,?,?,?,?,?)")) {

                for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
                    for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                        for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {

                            Block b = world.getBlockAt(x, y, z);
                            Material mat = b.getType();

                            if (nonAirOnly && mat == Material.AIR) continue;

                            ps.setString(1, key);
                            ps.setString(2, world.getName());
                            ps.setInt(3, x);
                            ps.setInt(4, y);
                            ps.setInt(5, z);
                            ps.setString(6, mat.name());
                            ps.setInt(7, b.getBlockData().getAsString().hashCode());
                            ps.addBatch();
                        }
                    }
                }

                ps.executeBatch();
                conn.commit();
                return true;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[SnapshotStore] snapshotRegion failed: " + region.getName(), e);
            try { conn.rollback(); } catch (Exception ignored) {}
            return false;
        }
    }

    public synchronized void restoreRegion(DecayRegion region) {
        if (conn == null || region == null) return;

        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) return;

        String key = region.getName().toLowerCase();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT x,y,z,type FROM region_snapshots WHERE region=?")) {

            ps.setString(1, key);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int x = rs.getInt(1);
                    int y = rs.getInt(2);
                    int z = rs.getInt(3);
                    Material mat;

                    try {
                        mat = Material.valueOf(rs.getString(4));
                    } catch (Exception e) {
                        continue;
                    }

                    world.getBlockAt(x, y, z).setType(mat, false);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[SnapshotStore] restoreRegion failed: " + region.getName(), e);
        }
    }

    public synchronized void renameSnapshotKey(String oldName, String newName) {
        if (conn == null) return;

        String oldKey = oldName.toLowerCase();
        String newKey = newName.toLowerCase();

        if (oldKey.equals(newKey)) return;

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE region_snapshots SET region=? WHERE region=?")) {

            ps.setString(1, newKey);
            ps.setString(2, oldKey);
            ps.executeUpdate();
            conn.commit();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[SnapshotStore] renameSnapshotKey failed", e);
        }
    }
}
