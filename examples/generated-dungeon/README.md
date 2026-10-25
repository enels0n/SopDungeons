# Sample generated dungeon modules

1. Copy `modules.yml` to `plugins/SopDungeons/modules.yml`.
2. Copy all `.schem` files from `modules/` to `plugins/SopDungeons/modules/`.
3. Run `/sd reload`.
4. Create a test dungeon with `/sd create sample GENERATED`.
5. Set its return point with `/sd exit sample`.
6. Configure it with `/sd generation sample 3 5 true`; this also starts preparing the instance.
7. Disable unrelated vanilla natural spawning with `/sd natural sample false`.
8. Wait for the ready message, then start it with `/sd enter sample`.

Use `/sd prepare sample` to discard the waiting instance and generate a fresh layout. The sample set includes straight and corner corridors, T and four-way junctions, rooms, dead ends and shape-matched boss rooms, so generated floors cannot collapse into a straight line.

The MODULES generator first builds a connected grid maze, then selects an exact schematic for every cell: straight corridor, corner, T junction, cross junction, room, dead end, boss room, staircase or final return portal. The bundled set contains 18 modules with separate non-overlapping 15x15 cells; regular modules are 7 blocks high and stairs are 12. Intermediate floors end with descending stairs, while the final floor ends with an animated return circle. All modules are carved inside a solid stone envelope.

The sample uses vanilla zombies and a husk boss. Replace `provider: VANILLA` and `mob`
with `provider: ELITEMOBS` and your custom boss filename when moving from the example to production.
