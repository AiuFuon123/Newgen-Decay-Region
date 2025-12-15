package newgen.decayregion.manager;

import newgen.decayregion.DecayRegionPlugin;
import newgen.decayregion.region.DecayRegion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.sql.*;

/**
 * Snapshot region -> lưu toàn bộ block (material + blockdata string) vào SQLite (data.db).
 * Restore region -> đọc DB và set lại block.
 *
 * Tối ưu:
 * - WAL + synchronous NORMAL
 * - Batch insert
 * - Xóa snapshot cũ trước khi insert snapshot mới
 *
 * Tương thích 1.21 -> 1.21.8:
 * - Khi restore: ưu tiên parse blockdata string
 * - Nếu blockdata không parse được (khác version, ví dụ leaf_litter): fallback về Material
 * - Nếu Material cũng không tồn tại ở version đó: AIR
 */
public class RegionSnapshotStore {

    private final DecayRegionPlugin plugin;
    private final File dbFile;
    private Connection conn;

    private volatile boolean schemaReady = false;

    public RegionSnapshotStore(DecayRegionPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "data.db"); // dùng chung file DB
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
            this.conn.setAutoCommit(true);

            initPragmas();
            initSchema();
            schemaReady = true;

            // snapshot thường insert nhiều -> chuyển sang autoCommit=false để batch commit
            this.conn.setAutoCommit(false);

        } catch (Exception e) {
            schemaReady = false;
            plugin.getLogger().severe("[RegionSnapshotStore] Cannot open/init data.db: " + e.getMessage());
        }
    }

    public synchronized void close() {
        try {
            if (conn != null && !conn.getAutoCommit()) {
                conn.commit();
            }
        } catch (Exception ignored) {}
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
    // PRAGMA + SCHEMA
    // =========================

    private void initPragmas() {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA temp_store=MEMORY;");
            st.execute("PRAGMA foreign_keys=ON;");
        } catch (Exception e) {
            plugin.getLogger().warning("[RegionSnapshotStore] PRAGMA failed: " + e.getMessage());
        }
    }

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(
                    "CREATE TABLE IF NOT EXISTS region_snapshots (" +
                            "region TEXT NOT NULL," +
                            "world  TEXT NOT NULL," +
                            "x      INTEGER NOT NULL," +
                            "y      INTEGER NOT NULL," +
                            "z      INTEGER NOT NULL," +
                            "material TEXT NOT NULL," +
                            "blockdata TEXT," +
                            "PRIMARY KEY(region, world, x, y, z)" +
                            ");"
            );
            st.execute("CREATE INDEX IF NOT EXISTS idx_snap_region_world ON region_snapshots(region, world);");
        }
    }

    private boolean tableExists(String table) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=? LIMIT 1"
        )) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private synchronized void ensureSchema() {
        if (conn == null) return;
        if (schemaReady && tableExists("region_snapshots")) return;

        try {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(true);
            initSchema();
            conn.setAutoCommit(prev);
            schemaReady = true;
            plugin.getLogger().info("[RegionSnapshotStore] Schema ensured (region_snapshots created).");
        } catch (Exception e) {
            schemaReady = false;
            plugin.getLogger().severe("[RegionSnapshotStore] ensureSchema failed: " + e.getMessage());
        }
    }

    // =========================
    // API
    // =========================

    /** Alias để tương thích code GUI cũ có gọi saveRegion(...) */
    public boolean saveRegion(DecayRegion region) {
        return snapshotRegion(region);
    }

    /**
     * Chụp snapshot toàn bộ region và lưu vào DB.
     * Trả false nếu region/world không hợp lệ hoặc quá lớn.
     */
    public synchronized boolean snapshotRegion(DecayRegion region) {
        if (region == null) return false;
        if (conn == null) return false;

        ensureSchema();

        World w = Bukkit.getWorld(region.getWorldName());
        if (w == null) return false;

        long sizeX = (long) region.getMaxX() - region.getMinX() + 1L;
        long sizeY = (long) region.getMaxY() - region.getMinY() + 1L;
        long sizeZ = (long) region.getMaxZ() - region.getMinZ() + 1L;
        long volume = sizeX * sizeY * sizeZ;

        long maxVolume = plugin.getCfg().getLong("snapshot.max-volume", 300_000L);
        if (volume > maxVolume) {
            plugin.getLogger().warning("[RegionSnapshotStore] Snapshot refused (too large) region=" + region.getName()
                    + " volume=" + volume + " > max-volume=" + maxVolume);
            return false;
        }


        String r = region.getName().toLowerCase();

        try (PreparedStatement del = conn.prepareStatement("DELETE FROM region_snapshots WHERE region=?");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT OR REPLACE INTO region_snapshots(region, world, x, y, z, material, blockdata) VALUES(?,?,?,?,?,?,?)"
             )) {
            // xóa snapshot cũ
            del.setString(1, r);
            del.executeUpdate();

            int batch = 0;
            final int BATCH_SIZE = 1000;

            for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
                for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                    for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                        Block b = w.getBlockAt(x, y, z);
                        Material mat = b.getType();

                        // lưu cả blockdata để giữ waterlogged/orientation...
                        String data = null;
                        try {
                            BlockData bd = b.getBlockData();
                            if (bd != null) data = bd.getAsString();
                        } catch (Throwable ignored) {}

                        ins.setString(1, r);
                        ins.setString(2, w.getName());
                        ins.setInt(3, x);
                        ins.setInt(4, y);
                        ins.setInt(5, z);
                        ins.setString(6, mat.name());
                        ins.setString(7, data);
                        ins.addBatch();

                        if (++batch >= BATCH_SIZE) {
                            ins.executeBatch();
                            batch = 0;
                        }
                    }
                }
            }

            if (batch > 0) ins.executeBatch();

            conn.commit();
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("[RegionSnapshotStore] snapshotRegion failed: " + e.getMessage());
            try { conn.rollback(); } catch (Exception ignored) {}
            return false;
        }
    }

    /**
     * Restore region từ snapshot trong DB.
     * An toàn cross-version: blockdata parse fail -> fallback Material -> AIR.
     */
    public synchronized void restoreRegion(DecayRegion region) {
        if (region == null) return;
        if (conn == null) return;

        ensureSchema();

        World w = Bukkit.getWorld(region.getWorldName());
        if (w == null) return;

        String r = region.getName().toLowerCase();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT x,y,z,material,blockdata FROM region_snapshots WHERE region=? AND world=?"
        )) {
            ps.setString(1, r);
            ps.setString(2, w.getName());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int x = rs.getInt(1);
                    int y = rs.getInt(2);
                    int z = rs.getInt(3);
                    String matName = rs.getString(4);
                    String data = rs.getString(5);

                    Block b = w.getBlockAt(x, y, z);

                    // 1) ưu tiên blockdata
                    if (data != null && !data.isBlank()) {
                        try {
                            BlockData bd = Bukkit.createBlockData(data);
                            b.setBlockData(bd, false);
                            continue;
                        } catch (Throwable ignored) {
                            // fallback xuống material
                        }
                    }

                    // 2) fallback material (chống lỗi khác version, ví dụ leaf_litter ở 1.21.1)
                    Material mat = Material.AIR;
                    if (matName != null) {
                        try { mat = Material.valueOf(matName); } catch (Exception ignored) {}
                    }
                    b.setType(mat, false);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[RegionSnapshotStore] restoreRegion failed: " + e.getMessage());
        }
    }

    public synchronized void deleteSnapshot(String regionName) {
        if (regionName == null) return;
        if (conn == null) return;
        ensureSchema();

        String r = regionName.toLowerCase();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM region_snapshots WHERE region=?")) {
            ps.setString(1, r);
            ps.executeUpdate();
            conn.commit();
        } catch (Exception e) {
            plugin.getLogger().warning("[RegionSnapshotStore] deleteSnapshot failed: " + e.getMessage());
            try { conn.rollback(); } catch (Exception ignored) {}
        }
    }

    public synchronized void renameSnapshotKey(String oldName, String newName) {
        if (oldName == null || newName == null) return;
        if (conn == null) return;
        ensureSchema();

        String oldKey = oldName.toLowerCase();
        String newKey = newName.toLowerCase();
        if (oldKey.equals(newKey)) return;

        try (PreparedStatement ps = conn.prepareStatement("UPDATE region_snapshots SET region=? WHERE region=?")) {
            ps.setString(1, newKey);
            ps.setString(2, oldKey);
            ps.executeUpdate();
            conn.commit();
        } catch (Exception e) {
            plugin.getLogger().warning("[RegionSnapshotStore] renameSnapshotKey failed: " + e.getMessage());
            try { conn.rollback(); } catch (Exception ignored) {}
        }
    }
}
