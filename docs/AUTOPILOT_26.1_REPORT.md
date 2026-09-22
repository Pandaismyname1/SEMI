# Morning report: EMI port to Minecraft 26.1.2 (branch `26.1`)

Run date: 2026-09-22 (autopilot). Contract: `docs/AUTOPILOT_26.1_PORT.md`. Decisions: `docs/AUTOPILOT_26.1_DECISIONS.md`.

## Done and verified

- Branch `26.1` exists locally, based on upstream `1.21` HEAD (81f6453c). It merges the MIT community port `link-fgfgui/emi@26.1` with full commit attribution, re-ports the 8 upstream commits that fork lacked, bumps to the newest 26.1.2 toolchain, and layers four fix passes driven by adversarial reviews.
- Both loaders build with JDK 25 (`./gradlew :fabric:build :neoforge:build`): `fabric/build/libs/emi-1.1.24-SNAPSHOT+26.1.2+fabric.jar`, `neoforge/build/libs/emi-1.1.24-SNAPSHOT+26.1.2+neoforge.jar`.
- Fabric client (loader 0.19.5, Fabric API 0.155.3): quick-play into a saved world, one EMI reload per join (tags, then recipes), 3731 recipes baked, index/favorites/search/recipe screen/recipe tree/crafting recipes render, tag icons render (coals half-and-half icon, logs tag as oak log item), recipe screenshot button produces a real transparent PNG, clean disconnect.
- NeoForge client (26.1.2.109): same checks, one reload per join, 3727 recipes, no errors; the NeoForge dev run needed a loom mod-file fix for the generated `GlobalMixin` (D8).
- Dedicated servers: Fabric loads EMI and stops at the EULA gate (not accepted on your behalf); NeoForge's dev launcher runs the server fully (`Done (1.5s)!`), no EMI errors.
- Reviews: one 33-finding regression audit of the fork's port, three targeted round-1 reviews (merge correctness, JEI adapter, mixins/runtime), three round-2 delta reviews, two round-3 delta reviews, one round-4 delta review. Every SEVERE/MODERATE finding was fixed and re-built; the round-4 result is recorded at the end of this file.

## Decisions to sanity-check first (details in the decision log)

- D1: building on the MIT fork via a real merge instead of a from-scratch port.
- D2: keeping `dev.architectury.loom-no-remap` instead of Fabric Loom + ModDevGradle.
- D7: the batched item renderer is inert on 26.1 because vanilla's `GuiRenderer` now caches GUI items in an atlas.
- D-F2b / D-F4b: how EMI reloads when the server does not synchronize recipes (empty recipe map plus one warning) on each loader.
- D-R1 / D-R6: tag icons are resolved from model json to textures or an inherited vanilla item, no baked models.
- The NeoForge network channel now declares a version (`registrar("1")`); mixed EMI builds refuse to connect.

## Not fully verifiable here (needs your hands)

- EMI as a client-only mod on a vanilla/Paper server on either loader (the fallback path with the empty recipe map). Reviewed against loader bytecode, not run.
- Stonecutter recipe fill selecting the right variant for tag inputs; JEI plugin categories (scroll grids, text widgets, tooltips, crafting-station slots) with a real JEI plugin mod.
- Recipe screenshot lighting consistency across light levels; the BoM tree tints with ingredients in inventory.
- Chess between mixed loaders; `/reload` behaviour on a live server.

## Blocked / needs you

- Push and PR: `origin` is the upstream maintainer's repository and the port issue there was closed by the maintainer (D-PR); your GitHub account has no fork of `emi`. The branch is local only. To publish it, create a fork and run `git push <your-fork> 26.1`.
- Minecraft server EULA was not accepted for the Fabric dev server.

## Round-4 review outcome

The round-4 delta review of the last two fix commits found one serious regression (the NeoForge vanilla-packet fallback double-fired, see D-F4c) and eight smaller items; all were applied directly on `26.1` and both loaders rebuilt and re-run (NeoForge: one reload per join, no errors). No review round came back fully clean, so the "two consecutive clean rounds" bar was not reached; the last delta (D-F4c) was verified by build and a client run only. The reviewers' remaining MINOR/INFO notes are listed under D-F4c.
