# Newgen Decay Region
## A lightweight, admin-friendly decay & reset region system for Paper/Spigot servers. Perfect for minigames, arenas, event maps, build zones, and temporary worlds where you want players to place blocks/entities freely — and have everything automatically cleaned up later.

## ✨ What this plugin does
  - Newgen Decay Region lets you define custom regions where:

  - Placed blocks are tracked and will decay (auto-remove) after the region’s configured time.

  - Placed entities are tracked (boats, minecarts, end crystals) and will also auto-remove after the decay time.

  - Fluids can be controlled to prevent uncontrolled spreading and block formation.

  - You can snapshot & restore regions to instantly reset an arena back to its original state.

  - Everything is designed to be safe, fast, and manageable in-game.

## Video Preview: https://www.youtube.com/watch?v=h5WoG0wjPjM

## 🧩 Core Features
### ✅ Region creation using a selection wand (pos1 / pos2)
### ✅ Per-region decay time (editable anytime)
### ✅ GUI management system:

  - Main region list (with paging)

  - Region management menu

  - Decay time editor

  - Confirm GUIs for destructive actions (save / delete / force clear)

### ✅ Region snapshot system
  
  - Save a snapshot of the region

  - Restore snapshot on reset/restart

  - Configurable snapshot volume limits

### ✅ ForceClear & Restore

  - Clears tracked blocks/entities/fluids

  - Restores the region snapshot (arena reset style)

### ✅ Fluid safety controls

  - Optional protection to prevent new fluid-generated blocks unless near tracked fluid sources

  - Flood-limit protections for clear operations

### ✅ Config toggles for denying entity placement

  - Boats

  - Minecarts

  - End Crystals

### ✅ Fast storage

  - Uses database storage for plugin data

  - Auto-flush interval configurable

## 🕹 Commands & Permissions

Permission: decayregion.admin

Commands:

  - /decay wand — Get the selection wand

  - /decay create <name> — Create a region from pos1-pos2

  - /decay list — List regions

  - /decay remove <name> — Remove a region

  - /decay reset <name> — ForceClear + Restore snapshot (like a restart)

  - /decay menu — Open the GUI

  - /decay reload — Reload config + plugin data

## ⚙️ Configuration Highlights

```
# Prefix used by the plugin when sending chat messages
prefix: "&7[&bDecayRegion&7] "

# Title shown to the player when creating a region successfully
create-region-title: "&aRegion created successfully!"
# Subtitle shown to the player when creating a region successfully
# Use %name% to insert the region name
create-region-subtitle: "&7Name: &e%name%"

# Sound played when a region is created (namespaced Minecraft sound key)
create-region-sound: "minecraft:entity.player.levelup"

placed-data:
  # How often (in seconds) the placed-data database should be flushed/committed
  flush-seconds: 5

decay:
  # If true: only decay blocks formed from tracked fluids (not all fluids)
  formed-blocks-only-from-tracked-fluids: true
  fluids:
    # Maximum flood-fill operations when clearing fluids generated/spread from sources
    max-flood-blocks: 500000

deny-place:
  # If true: prevent placing boats inside decay regions
  boats: false
  # If true: prevent placing minecarts inside decay regions
  minecarts: false
  # If true: prevent placing end crystals inside decay regions
  end-crystals: false

# If true: when the server starts, the plugin will force-clear tracked blocks/entities/fluids (from the database)
force-clear-on-startup: true

# Default decay time (seconds) for newly created regions
default-decay-seconds: 30

force-clear:
  # Maximum flood-fill operations when force-clearing fluids for a region
  max-flood-blocks: 2000000

snapshot:
  # Maximum allowed region volume (blocks) to snapshot; larger regions will be refused
  max-volume: 200000
  # If true: snapshot only non-air blocks (smaller snapshot size, faster)
  save-non-air-only: false
```


### ✅ Best Use Cases

  - Battle Royale arenas

  - PvP / Parkour maps

  - Event build zones

  - Temporary survival islands

  - Any area where you want automatic cleanup + quick reset

*If you want a short version (for Modrinth summary field) or a feature banner-style version with emojis/format tuned exactly to Modrinth markdown, tell me and I’ll format it to match.*
