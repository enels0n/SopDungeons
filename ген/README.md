# CatacombGenerator

Pure Java 21 layout generator with deterministic seeds, variable corridor width, thick wall masses, DFS dead ends, sparse loops and chambers.

```java
var config = CatacombGenerator.Config.defaults(12345L);
var layout = new CatacombGenerator(config).generate();
int height = 10;
for (int y = 0; y < height; y++)
    for (int z = 0; z < layout.length(); z++)
        for (int x = 0; x < layout.width(); x++) {
            CatacombPalette.Block block = CatacombPalette.at(layout, x, y, z, height);
            // Translate block.name() to org.bukkit.Material.valueOf(block.name()).
            // Submit block edits via FAWE edit session; don't set millions of blocks on main thread.
        }
```

The code produces a layout and deterministic base materials, **not** a complete FAWE/Paper plugin. Lighting, vaulted geometry, burial alcoves and detailed 3D architectural modules are planned extensions.
