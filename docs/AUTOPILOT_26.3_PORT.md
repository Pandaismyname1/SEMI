# Autopilot contract: port SEMI to Minecraft 26.3 (Fabric + NeoForge)

Started 2026-09-22 (unattended run). Branch: `26.3` (from `26.2` @ da180158, the released 1.1.24+26.2). Decision log: `docs/AUTOPILOT_26.3_DECISIONS.md`.

## Target
- Minecraft **26.3** (released 2026-09-15), Java 25.
- Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, ModMenu 21.0.0-beta.1; NeoForge 26.3.0.8-beta (no non-beta NeoForge build for 26.3 exists yet); JEI API 31.4.0.19 (`jei-26.3-fabric`).
- Same build layout as 26.2 (`dev.architectury.loom-no-remap` 1.17.493, Gradle 9.5).

## Approach
Apply the community fork's 26.2→26.3 delta (`linkfgfgui/26.3`, commits f0e3931a "port to 26.3" and 2fb0d300 "fix some bug"; 63 files, +719/-520) onto SEMI's `26.2`, resolve against SEMI's own fix rounds, compile-fix against the real 26.3 APIs, update JEMI for the JEI 31 API, verify in-game on both loaders, run adversarial delta reviews until two consecutive rounds find no code defect.

## Checklist
### A. Port
- [ ] A1. Branch `26.3` created from `26.2`; versions bumped in `gradle.properties` / `fabric/build.gradle`.
- [ ] A2. Fork's 26.3 delta applied and reconciled with SEMI's fixes (nothing from SEMI's 26.1/26.2 fix rounds lost).
- [ ] A3. Remaining compile errors fixed against 26.3 sources (xplat, fabric, neoforge).
- [ ] A4. JEMI updated for the JEI 31.x API.
- [ ] A5. Mod metadata: `fabric.mod.json` minecraft `~26.3-`; `neoforge.mods.toml` minecraft `[26.3,26.4)`, neoforge `[26.3,27)`.
- [ ] A6. Names for any item tags vanilla 26.3 added (zero "Untranslated tag" warnings in the dev client).

### B. Completion proof
- [ ] B1. `./gradlew :fabric:build :neoforge:build` succeeds.
- [ ] B2. Fabric client: quick-play into the (upgraded) test world, one reload, index/search/recipe screen/tag icons/recipe screenshot, clean log.
- [ ] B3. NeoForge client: same.
- [ ] B4. Fabric (to the EULA gate) + NeoForge (to "Done") dedicated servers load EMI without errors.
- [ ] B5. No conflict markers / new stubs.

### C. Quality
- [ ] C1. Adversarial delta review (mixin/accessor targets vs 26.3 bytecode, JEMI vs JEI 31, recipe/sync/network paths, input, rendering), findings fixed.
- [ ] C2. Second delta review of the fixes, no code defect.

### D. Delivery
- [ ] D1. Pushed to `origin` as branch `26.3`; README supported-versions table updated on `26.3` and on the default branch.
- [ ] D2. `CHANGELOG.md` for the 26.3 release.
- [ ] D3. Report (`docs/AUTOPILOT_26.3_REPORT.md`) and final message.
