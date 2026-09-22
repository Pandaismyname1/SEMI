# Autopilot contract: port SEMI to Minecraft 26.2 (Fabric + NeoForge)

Started 2026-09-22 (unattended run). Branch: `26.2` (from `26.1` @ 64271f4a). Decision log: `docs/AUTOPILOT_26.2_DECISIONS.md`.

## Target
- Minecraft **26.2** (no hotfix at the time of writing), Java 25.
- Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, ModMenu 20.0.2; NeoForge 26.2.0.88; JEI API 30.35.0.223 (`jei-26.2-fabric`).
- Same build layout as 26.1 (`dev.architectury.loom-no-remap` 1.17.493, Gradle 9.5).

## Approach
Apply the community fork's 26.1→26.2 delta (`linkfgfgui/26.2`, commits 812a9e19..b60b8f8f, +161/-170 in 38 source files plus JEMI/NeoForge fixes) onto SEMI's `26.1`, resolve against SEMI's own fix rounds, then compile-fix against the real 26.2 APIs, update JEMI for the JEI 30 API, verify in-game on both loaders, and run adversarial delta reviews.

## Checklist
### A. Port
- [ ] A1. Branch `26.2` created from `26.1`; versions bumped in `gradle.properties` / `fabric/build.gradle`.
- [ ] A2. Fork's 26.2 delta applied and reconciled with SEMI's 26.1 fixes (nothing from SEMI's fix rounds lost).
- [ ] A3. Remaining compile errors fixed against 26.2 sources (xplat, fabric, neoforge).
- [ ] A4. JEMI updated for JEI 30.x API.
- [ ] A5. Mod metadata: `fabric.mod.json` minecraft `~26.2-`; `neoforge.mods.toml` minecraft `[26.2,26.3)`, neoforge `[26.2,27)`.

### B. Completion proof
- [ ] B1. `./gradlew :fabric:build :neoforge:build` succeeds.
- [ ] B2. Fabric client: quick-play into the (upgraded) test world, one reload, index/search/recipe screen/tag icons/recipe screenshot, clean log.
- [ ] B3. NeoForge client: same.
- [ ] B4. Fabric + NeoForge dedicated servers load EMI (server side) without errors.
- [ ] B5. No conflict markers / new stubs.

### C. Quality
- [ ] C1. Adversarial delta review (mixin targets vs 26.2 bytecode, JEMI vs JEI 30, recipe/sync paths, rendering), findings fixed.
- [ ] C2. Second delta review of the fixes.

### D. Delivery
- [ ] D1. Pushed to `origin` (Pandaismyname1/SEMI) as branch `26.2`; README supported-versions table updated on both branches as appropriate.
- [ ] D2. `CHANGELOG.md` for the 26.2 release.
- [ ] D3. Report (`docs/AUTOPILOT_26.2_REPORT.md`) and final message.
