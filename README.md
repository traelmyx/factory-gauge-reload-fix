# Create: Factory Gauge Reload Fix

Fixes [Create issue #8190](https://github.com/Creators-of-Create/Create/issues/8190): factory gauges order items that are already in stock right after a world, dimension or chunk loads.

When things load, a packager only finds the inventory it sits on during its own first tick, and the network's stock summary is cached for 20 ticks. A factory gauge that ticks earlier sees too little in stock and sends an order, sometimes for every gauge in the factory at once. This mod makes a freshly loaded gauge wait 2 seconds before it may order, then makes it read a fresh stock summary.

- Server side (or singleplayer). Requires Create 6.0 for Minecraft 1.21.1 on NeoForge.
- `config/gauge_reload_fix.properties`: `enabled`, `graceTicks` (default 40).
- If a Create update changes the code this hooks into, the fix switches itself off and logs a warning; it never stops the game from loading.

Tests: a GameTest sets up a factory gauge that makes stone from cobblestone on a network whose chest already holds 64 of each, reloads the gauge and the chest's packager from their saved data the way a chunk load does (gauge first, stock caches expired), and checks that nothing is ordered. The same reload with the fix off must over-order, so the test can't pass by accident, and with the chest emptied the gauge must still order. A client smoke test starts the game with Create, builds a gauge and takes a screenshot.

Not affiliated with the Create team. MIT licensed.
