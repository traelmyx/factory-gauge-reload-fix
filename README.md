# Create: Factory Gauge Reload Fix

Fixes [Create issue #8190](https://github.com/Creators-of-Create/Create/issues/8190): factory gauges order items that are already in stock right after a world, dimension or chunk loads.

When things load, a packager only finds the inventory it sits on during its own first tick, and the network's stock summary is cached for 20 ticks. A factory gauge that ticks earlier sees too little in stock and sends an order, sometimes for every gauge in the factory at once. This mod makes a freshly loaded gauge wait 2 seconds before it may order, then makes it read a fresh stock summary.

- Server side (or singleplayer). Requires Create 6.0 for Minecraft 1.21.1 on NeoForge.
- `config/gauge_reload_fix.properties`: `enabled`, `graceTicks` (default 40), `keepOnNewerCreate`. Create's 6.0.11 milestone lists this issue, so on Create 6.0.11 or newer the mod turns itself off unless `keepOnNewerCreate=true`.

Tests: a GameTest builds a restocking gauge on a packager whose chest already holds what it asks for, reloads the gauge and packager from their saved data the way a chunk load does (gauge first), and checks that nothing is ordered. A second test runs the same reload with the fix off and checks that the bug does happen, so the first test can't pass by accident. A client smoke test starts the game with Create, builds a gauge and takes a screenshot.

Not affiliated with the Create team. MIT licensed.
