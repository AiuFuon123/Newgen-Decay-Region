package newgen.decayregion.listener;

import newgen.decayregion.DecayRegionPlugin;
import newgen.decayregion.region.DecayRegion;
import newgen.decayregion.region.RegionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class EntityDecayListener implements Listener {

    private final DecayRegionPlugin plugin;
    private final RegionManager regionManager;

    private final boolean denyBoats;
    private final boolean denyMinecarts;
    private final boolean denyEndCrystals;

    public EntityDecayListener(DecayRegionPlugin plugin, RegionManager regionManager) {
        this.plugin = plugin;
        this.regionManager = regionManager;

        FileConfiguration cfg = plugin.getCfg();
        this.denyBoats = cfg.getBoolean("deny-place.boats", true);
        this.denyMinecarts = cfg.getBoolean("deny-place.minecarts", true);
        this.denyEndCrystals = cfg.getBoolean("deny-place.end-crystals", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Entity entity = event.getEntity();

        boolean isBoat = (entity instanceof Boat) || (entity instanceof ChestBoat);
        boolean isMinecart = (entity instanceof Minecart);
        boolean isCrystal = (entity instanceof EnderCrystal);

        if (!isBoat && !isMinecart && !isCrystal) return;

        DecayRegion region = regionManager.getRegionAt(entity.getLocation());
        if (region == null) return;

        // deny
        if ((isBoat && denyBoats) || (isMinecart && denyMinecarts) || (isCrystal && denyEndCrystals)) {
            event.setCancelled(true);
            return;
        }

        UUID uuid = entity.getUniqueId();
        plugin.getPlacedDataStore().recordEntity(region, uuid);

        int seconds = region.getDecaySeconds();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity current = null;
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                current = w.getEntity(uuid);
                if (current != null) break;
            }

            if (current == null || !current.isValid()) {
                plugin.getPlacedDataStore().removeEntity(region, uuid);
                return;
            }

            DecayRegion now = regionManager.getRegionAt(current.getLocation());
            if (now == null || !now.getName().equalsIgnoreCase(region.getName())) {
                plugin.getPlacedDataStore().removeEntity(region, uuid);
                return;
            }

            if (current instanceof Vehicle v) v.eject();

            // ✅ drop item trước khi remove
            dropEntityItem(current);

            current.remove();
            plugin.getPlacedDataStore().removeEntity(region, uuid);

        }, Math.max(1L, seconds * 20L));
    }

    private void dropEntityItem(Entity e) {
        if (e.getWorld() == null) return;

        Material drop = null;

        // ChestBoat / Boat
        if (e instanceof ChestBoat cb) {
            String type = cb.getBoatType().name(); // OAK, SPRUCE...
            drop = Material.matchMaterial(type + "_CHEST_BOAT");
        } else if (e instanceof Boat b) {
            String type = b.getBoatType().name();
            drop = Material.matchMaterial(type + "_BOAT");
        }

        // Minecart variants
        else if (e instanceof Minecart mc) {
            if (mc instanceof StorageMinecart) drop = Material.CHEST_MINECART;
            else if (mc instanceof HopperMinecart) drop = Material.HOPPER_MINECART;
            else if (mc instanceof ExplosiveMinecart) drop = Material.TNT_MINECART;
            else if (mc instanceof PoweredMinecart) drop = Material.MINECART;   // furnace minecart item thường không dùng -> fallback
            else if (mc instanceof CommandMinecart) drop = Material.MINECART;   // fallback
            else drop = Material.MINECART;
        }

        // End Crystal
        else if (e instanceof EnderCrystal) {
            drop = Material.END_CRYSTAL;
        }

        if (drop != null) {
            e.getWorld().dropItemNaturally(e.getLocation(), new ItemStack(drop, 1));
        }
    }
}
