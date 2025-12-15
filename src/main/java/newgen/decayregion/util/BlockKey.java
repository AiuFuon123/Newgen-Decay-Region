package newgen.decayregion.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;
import java.util.UUID;

public class BlockKey {

    private final UUID world;
    private final int x, y, z;

    public BlockKey(World world, int x, int y, int z) {
        this.world = world.getUID();
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockKey fromBlock(Block block) {
        return new BlockKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey fromLocation(Location loc) {
        return new BlockKey(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockKey)) return false;
        BlockKey that = (BlockKey) o;
        return x == that.x && y == that.y && z == that.z &&
                world.equals(that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }
}
