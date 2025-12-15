package newgen.decayregion.region;

import org.bukkit.Location;

public class DecayRegion {

    private String name;
    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    private int decaySeconds;

    public DecayRegion(String name, String worldName,
                       int minX, int minY, int minZ,
                       int maxX, int maxY, int maxZ,
                       int decaySeconds) {
        this.name = name;
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.decaySeconds = decaySeconds;
    }

    public boolean contains(Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public String getName() { return name; }
    public String getWorldName() { return worldName; }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }

    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public int getDecaySeconds() { return decaySeconds; }

    // ✅ needed for rename + GUI time editor
    public void setName(String name) { this.name = name; }
    public void setDecaySeconds(int decaySeconds) { this.decaySeconds = decaySeconds; }
}
