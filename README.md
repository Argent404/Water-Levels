# Water Levels

Per-block water surface heights in eighths of a block, for Fabric 1.20.1.
No new block states: blocks keep vanilla `waterlogged=true`, and the fluid is
still `minecraft:water`, so Iris shaderpacks treat it as normal water.

This is primarily designed for use with Conquest Reforged, for the ArdaCraft modpack. Thousands of blocks that are waterloggable can now have their displayed water level adjusted - a feature that should dramatically improve the look of hand-made builds involving water. Some tested uses include differential water heights in oceans, lakes, ponds, sloping rivers in layered terrain, more complex river side geometry, and adjustable bath/basin fullness. Compatibility outside of those contexts (Conquest Reforged Modpack, ArdaCraft Modpack) will have minimal extra support.

This function is designed to be used with flowing/ticking water off, the same version must be installed on both server and client.



## Using it

**Water Level Wand** (Tools tab, or `/ww`) The first time you hold it each session, the controls
show above the hotbar; `/wandhelp` shows them again. Keys are configurable
- **Right-click:** adds water with the brush, then sets the chosen level on the water at the
  clicked height. The brush is a bowl (the lower half of a sphere, radius 1 = just the clicked
  block): every waterloggable block in it (slab, stairs, fence...) is waterlogged, and air on the
  top layer becomes still water. Placed water doesn't start flowing.
- **Left Shift + right-click** only cycles the level: 1/8 → … → 8/8 → vanilla (normal water, no
  level; 8/9 of a block).
- **Left-click:** the exact opposite: waterlogged blocks are drained and water
  on the top layer becomes air.
- **Left Ctrl + scroll** changes the radius, 1 (just the targeted block) to 5.


`/waterlevels fill <level> <radius>` sets the surface water of every column within radius (up to
256) of you, in loaded chunks. Level 0 sets it back to vanilla (normal water).


**Visual outlines** while holding the wand. The "Water outlines" key (have to add a keybind)
cycles off → flow view → level view:

- **Flow View:** flowing water (orange), and still water that slopes because the water beside it is at
  another height (yellow);

- **Level View:** leveled tiles in green (darker = lower) with their level (1 to 8) written over them within 12
  blocks; vanilla tiles in dark blue.

Outlines are drawn very similar to WorldEdit CUI selections, though culled by unexposed terrain.



**Floating blocks:** blocks in the `#waterlevels:floats` tag sit on leveled water instead of
hovering above it (visual only; their hitbox stays put). Included: vanilla lily pads, and
Conquest's water lilies (big, white), duckweed, alpine bearberry and the four loose sphagnum
mosses. A datapack can add more to `data/waterlevels/tags/blocks/floats.json`.

`/waterlevels fill <level> <radius>` sets the surface water of every column within radius (up to
256) of you, in loaded chunks. Level 0 sets it back to vanilla (normal water).
This will be undone before 1.0.0

**Permissions** (LuckPerms, through fabric-permissions-api. otherwise opped users only):
- `waterlevels.wand`: change water levels with the wand, and `/ww`
- `waterlevels.fill`: `/waterlevels fill`

Wand edits *should* also respect adventure mode, spawn protection and protection mods that use vanilla's
"can this player modify here" check generally.



## How levels are stored

A level is a number 1..8 per block position: the water surface sits at level/8 of the block.
It means something only where there is still water with no water above it (the one case where
vanilla's height is 8/9); anywhere else it is ignored.

Levels are kept in the chunk's own NBT in the region (`.mca`) file, next to vanilla's data:

```
waterlevels:levels: {
    Y: int[]    the block heights (Y) that hold any level, one entry per layer
    L: byte[]   128 bytes per entry of Y: that 16x16 layer, two levels per byte
}
```

Cell `(x & 15) + 16 * (z & 15)` of a layer is the low nibble (even cells) or high nibble (odd
cells) of byte `cell / 2`. Chunks without levels get no key at all. A typical river chunk with one
leveled layer adds about 170 bytes.

Without the mod (a vanilla server or a vanilla client) the key is unknown and simply ignored:
the world loads and plays as plain vanilla water, and the levels are still there when the mod
comes back. Only the server reads and writes it; clients get levels over the network.

## Design

> A chunk may store a level 1..8 per block. It applies only where there is still
> water with nothing but air above it. Everywhere else it is ignored.

- **Storage:** one field on each chunk, null unless it has levels. A water surface is a
  16x16 layer, so only the Y-layers that hold levels are stored, two levels per byte:
  128 bytes per layer. The data is immutable
  and the same arrays are used in memory, in the chunk's NBT (`waterlevels:levels`) and
  on the network, so saving and sending copy nothing. Unloading a chunk discards it.
- **Lookups** read straight from the loaded chunk with vanilla's non-blocking
  `getWorldChunk`. On the client it is a
  lock-free array read, safe from Sodium's mesh threads** probably.
- **Hooks:** three height hooks (gameplay, vanilla renderer, Sodium renderer) all call
  the same `Levels.adjust`. Its first line returns unless vanilla's answer was exactly
  the still-water height, so submerged, flowing and non-water blocks pay one float
  compare.
- **Sync:** one packet type, sent just before the vanilla chunk packet and again after
  each edit. The client holds it until the chunk arrives and attaches it as the chunk
  loads, so chunks and their neighbours' edges are meshed with the right heights the
  first time (no seams at chunk borders). A one-layer chunk is about 165 bytes. Edits are limited to one per player every 2
  ticks (might need adjustment after more long term group testing), need the `waterlevels.wand` permission, and re-mesh only the changed cells plus
  a one-block margin, so neighbouring chunks' edges follow.
- **Water a block fully encloses isn't drawn** (with Sodium). When an opaque block's
  shape covers the water up to its level (layers 5 holding level 5, a bottom slab holding
  4 or less), the water's top surface is skipped, so it can't flicker against the block
  top or poke through it with shader waves.
- **Nothing is cleared on block changes.** Until the next server restart a position remembers
  its level: break a waterlogged slab, put it back, and the height returns. Use "vanilla" on the
  wand to remove a level. This avoids hooking every block change in the game.
- **Cleaned up on restart.** The first time a chunk with levels loads after a restart, levels
  stored where there's no water any more are dropped, so only water carries height data. This should help limit the accidental inclusions around river work when brush size radius is high, or when temp testing was removed)

## Performance

**Client**
Water Levels 0.5.x compared with the same modpack without it. Measured with a small benchmark
mod (`pwbench`) that flies the camera at a fixed speed and records every frame.

**Setup:** Ryzen 7 9800X3D, RTX 3080, 64 GB, FPS uncapped, Sodium 0.5.13. Worst case on purpose:
a superflat Water World where every surface water tile has a random level 1–8. Each flight is
60 s at 80 blocks/s (40 over the waterlogged strip).

**Method:** each flight gives one sample, its average frame time. Values are mean ± standard
deviation over the flights. FPS loss is 1 − FPS(Water Levels) / FPS(vanilla), with a 95%
confidence interval (Welch's t).

| Scenario | Vanilla | Water Levels | FPS loss (95% CI) |
|---|---|---|---|
| Flight, 16 chunks | 879 fps, 1.137 ± 0.060 ms (n=4) | 899 fps, 1.112 ± 0.063 ms (n=6) | −2.3% (−10.8 to +6.2) |
| Flight, 16 chunks, shaders | 157 fps, 6.358 ± 0.043 ms (n=6) | 156 fps, 6.414 ± 0.032 ms (n=6) | **+0.9%** (+0.1 to +1.6) |
| Dense waterlogged strip | 963 fps, 1.038 ± 0.057 ms (n=3) | 922 fps, 1.085 ± 0.074 ms (n=3) | +4.3% (−9.4 to +18.0) |
| Low flight, 10 blocks above water | 755 fps, 1.325 ± 0.055 ms (n=3) | 821 fps, 1.218 ± 0.074 ms (n=3) | −8.8% (−22.3 to +4.7) |
| Low flight, holding the wand in level view | 755 fps, 1.325 ± 0.055 ms (n=3) | 423 fps, 2.364 ± 0.040 ms (n=3) | +44.0% (+38.9 to +49.0) |

Negative loss in above tests indicates performance degradation is very minimal, and falls within noise.

- Only the shader flight shows a loss distinguishable from zero: about 1%, or 0.06 ms per frame.
- Without shaders, every confidence interval includes zero; the spread between flights of the same
  build (up to 12%) is larger than any effect of the mod.
- Holding the wand with the level view on costs about 1 ms per frame (3,721 outlined tiles). This
  was measured before the outlines moved to a GPU buffer, and only applies while the wand is held
  with a view on.


## Server

Profiled with spark on the ArdaCraft dev server (Random water level changing activity, 2 players, 5 minutes):
Water Levels code took **0.09%** of the server thread. By action, all of this server thread (measurably) was dued to a single max radius fill command. Regular behaviour fell out of diagnostic range in spark. TPS/MSPT not statistically different.


## Storage

Measured by reading every chunk of the benchmark worlds' region files and comparing each one with
and without the levels tag. Two worst-case worlds, 63,249 chunks each, with every surface water
tile given a random level 1–8 (random levels compress worst) In situ layers would neither be as ubiquitous, frequent, or scattered in level. Expected effective real gain is ~+1-2% at most:

| World | Leveled chunks | Chunk data, vanilla | Chunk data, Water Levels | Region files on disk, vanilla | Region files on disk, Water Levels |
|---|---|---|---|---|---|
| Superflat water | 22,209 (5.5 million leveled tiles) | 41.74 MB | 45.40 MB (+8.8%) | 260.24 MB | 260.24 MB (+0.00%) |
| Dense waterlogged strip | 22,209 (30,507 randomly leveled layers) | 108.14 MB | 111.86 MB (+3.4%) | 264.89 MB | 283.44 MB (+7.0%) |

- Each leveled layer (16×16 tiles) adds **165 bytes** on average after compression (range 33–197).
  A layer where every tile has the same level compresses to about 35 bytes.
- Region files store each chunk in 4 KiB sectors, so the file only grows when the extra bytes push
  a chunk over a sector boundary:
  - Superflat water: chunks are about 0.9 KB, so not one of the 22,209 crossed a boundary and the
    region files did not grow at all.
  - Dense waterlogged strip: chunks are about 4.0 KB (median 3,983 bytes), right at the boundary,
    so 4,287 of them (19%) needed a second sector. That is the whole 7% growth.
- Chunks without levels are not changed.

## Files

| File | Job |
|---|---|
| `Levels` | The rule, lookups, editing, NBT format, sending |
| `WaterLevels` | The wand item and the server side of a wand click |
| `WaterLevelsClient` | Wand clicks and receiving level data |
| `MouseHandlerMixin` | Scroll wheel sets the wand radius |
| `ChunkAccessMixin` + `duck/WLChunk` | The per-chunk field |
| `ChunkSerializerMixin` | Save and load |
| `ChunkMapMixin` | Send with each chunk |
| `LevelSync` | Client side: attach levels as chunks load, re-mesh edits |
| `FlowingFluidMixin` | Gameplay height and raycast shape |
| `LiquidBlockRendererMixin`, `SodiumFluidRendererMixin` | Rendered height (the Sodium one is skipped when Sodium is absent) |
| `BobbyChunkSerializerMixin` | Keeps levels in Bobby's client-side chunk cache (skipped when Bobby is absent) |
| `FloatingBlockMixin`, `SodiumBlockRendererMixin` | Floating blocks sit on leveled water (visual only; the Sodium one is skipped when Sodium is absent) |
| `FlowHighlight` | The wand's outline views |
| `WandKeys` | The wand's key bindings |
| `FillCommand`, `WandCommand`, `Perms` | `/waterlevels fill`, `/ww`, permission checks |


## Compatibility

Leveled water **is** vanilla water. The mod adds no blocks, block states or fluids: a leveled
block is still `minecraft:water`, or a vanilla block with `waterlogged=true`. The level itself is
a separate number stored next to the chunk, and the only thing it changes is how high the
surface sits. So everything that works with water keeps working:

- **Shaders (Iris):** the shader pack sees plain water so all effects in all shaders *should* be unchanged.
- **Resource packs:** water textures, colours and biome tint are unchanged.
- **Gameplay:** swimming, boats, drowning, buckets, fishing and water flow all behave as normal;
  they just use the new surface height.
- **Other mods** that look for water or waterlogged blocks find exactly what they expect.
- **Without the mod** (a vanilla client or server), the same world loads as ordinary full
  water, and the levels are still there when the mod comes back.

Tested with Sodium 0.5, Iris, Indium, Bobby and Create contraptions. Distant Horizons draws its
far-away terrain itself, so leveled water shows at full height in its LODs.*

## Known limits

- Water still spreads out of a waterlogged block that touches air, like vanilla if flow is not off.
- WorldEdit and schematics do not copy levels.
- An edit re-sends that chunk's levels and re-meshes the changed cells plus one block
  around them (usually one or two sections)."
- Axiom? initial attempts were FUBAR.
- DH/Voxy distant rendering follows their standard behaviour - meaning, often incorrect height/fill in LODS.

## Build

Needs JDK 17 (Loom 1.6 and Gradle 8.7)

## Acknowledgements

Claude Opus 5.5 high was used during development: to compile against the game and
library jars, to move the project from Yarn to Mojang mappings, to run the automated benchmarks, and especially for the
rendering work: the Sodium fluid-renderer hooks and the outline overlay pipeline.

Tool design is based off of existing functionality in the Paintbrush mod developed by the ArdaCraft Team.
