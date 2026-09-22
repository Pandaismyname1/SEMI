# Autopilot contract: port EMI to Minecraft 26.1 (Fabric + NeoForge)

Started 2026-09-22 (unattended run). Branch: `26.1` (from upstream `1.21` @ 81f6453c).
Decision log: `docs/AUTOPILOT_26.1_DECISIONS.md`.

## Target
- Minecraft **26.1.2** (last 26.1 hotfix; the 26.1 line requires Java 25, is unobfuscated, Yarn is discontinued, so everything moves to Mojang mappings).
- Loaders: **Fabric** (loader 0.19.x, Fabric API 0.15x+26.1.2) and **NeoForge** (26.1.2.x).
- Build: Gradle 9.x, JDK 25, `dev.architectury.loom-no-remap` (classic Architectury Loom cannot do 26.1+).

## Approach (see decision D1)
Merge the MIT-licensed community port `link-fgfgui/emi@26.1` (remote `linkfgfgui`) into upstream HEAD, then re-port the upstream commits the fork lacks, then clean up, bump toolchain, verify, review.

## Checklist (definition of done)
### A. Branch + merge
- [x] A1. Branch `26.1` created from `origin/1.21` (81f6453c).
- [x] A2. `linkfgfgui/26.1` (63e8aef1) merged; all 34 UU + 5 AA conflicts resolved (fork side wins for mojmap; upstream semantics re-applied).
- [x] A3. Upstream delta re-ported on mojmap: 81f6453c "Introduce some abstractions" (ProxyRecipeManager, EmiDrawContext.translate, EmiPort.playClickSound), 4c22e3b0 tag-query registries, 5b49fb13 display-all-recipes keybind, 92038d8e displayRecipesForWorkstation, 94c6ad88/1c553693/c0b57231 lang fixes, ec96b21a README maven URL.
- [x] A4. `EmiPort.getId/getRecipe` + `EmiRecipes.recipeIds` removed in favour of `ProxyRecipeManager` (no duplicate id maps left).

### B. Repo hygiene / toolchain
- [x] B1. Version scheme restored to upstream convention (`-SNAPSHOT` unless `RELEASE`), `mod_version` unchanged (1.1.24).
- [x] B2. Fork-specific dev `localRuntime` mod lists (cursemaven/modrinth test mods) removed from fabric/neoforge build files.
- [x] B3. GitHub workflows: upstream `build.yml`/`release.yml` kept, JDK 25 + temurin; the fork's auto-release-to-their-CurseForge workflow is NOT carried over.
- [x] B4. Dependencies bumped to latest 26.1.2-compatible (NeoForge 26.1.2.x latest, Fabric API latest +26.1.2, Fabric Loader latest stable, JEI latest that compiles), verified by build.
- [x] B5. `.gitignore`, README developer section updated for 26.1 (no remap: `compileOnly`/`localRuntime`).
- [x] B6. `xplat/mojmap` subproject removed (everything is mojmap now); publication artifactIds documented.
- [x] B7. `neoforge.mods.toml` neoforge dependency range `[26.1,)`; `fabric.mod.json` deps updated for Fabric API on 26.1 (no `fabric` mod id).

### C. Completion proof
- [x] C1. `./gradlew :fabric:build` succeeds (JDK 25).
- [x] C2. `./gradlew :neoforge:build` succeeds (JDK 25).
- [ ] C3. No leftover conflict markers, no `TODO(port)` or stubbed functionality introduced by this branch.
- [x] C4. Fabric client launches (runClient), reaches a world, EMI index/UI renders in the inventory (screenshot), log free of mixin apply failures / EMI errors. Verified 2026-09-22: quick-play into saved world, 3731 recipes baked in 495 ms, index/favorites/search/recipe screen/crafting recipe all rendered, clean disconnect. Re-verified on the merged branch (25295fa8): exactly one reload per join (tags then recipes), tag icons render (logs/coals tag models), recipe tree screen opens.
- [x] C5. NeoForge client launches likewise (screenshot, clean log). Verified 2026-09-22 after the D8 dev-classpath fix: quick-play into the saved world, 3727 recipes baked in 715 ms, index/search/recipe screen rendered, clean disconnect. Re-verified on the merged branch (25295fa8): exactly one reload per join via TagsUpdatedEvent.ClientPacketReceived + RecipesReceivedEvent, 3727 recipes, tag icons render.
- [x] C6. Fabric dedicated server (runServer) loads EMI (43 mods) with no errors and stops at the EULA gate (EULA deliberately not accepted on the user's behalf; bounded check).
- [x] C7. NeoForge dedicated server (runServer) loads EMI and reaches `Done (1.539s)!` (the NeoForge dev launcher does not gate on the EULA); no EMI warnings/errors; killed after the 60 s idle pause.

### D. Quality (multi-agent adversarial review, Opus)
- [x] D1. Review round 1: merge-resolution correctness (upstream delta fully re-applied, nothing from the fork lost), findings verified + fixed (branch fix/f2-recipes, fix/f3-jemi).
- [x] D2. Review round: port regressions vs upstream 1.21 behaviour (dropped features, API breaks, mixin coverage), findings verified + fixed (33-finding audit + JEI-adapter review + runtime/mixin review; fixes in fix/f1-rendering, fix/f2-recipes, fix/f3-jemi and 476e9745).
- [ ] D3. Two consecutive clean review rounds.

### E. Delivery
- [x] E1. Commits on `26.1` with attribution preserved (fork commits kept via a real merge).
- [x] E2. `CHANGELOG.md` entry for the port.
- [ ] E3. Push + PR (only to a remote the user owns; never to `emilyploszaj/emi` upstream, see D-PR).
- [ ] E4. Morning report.

### F. Regression fix round (from the fork-port audit, see decisions D7+)
- [x] F1. Rendering/UI regressions fixed: tag icons (custom tag models), global tint (BoM tree colours, jeb_ search bar), text alpha, screenshot transparency, top-effects text, deferred-tooltip robustness, loader fluid sprite APIs, `EmiTooltipComponent.getWidth` API restored.
- [x] F2. Recipe/sync regressions fixed: cheat `give` keeps components + namespace, JEMI info recipes registered synchronously, suspicious stew / map cloning recipes, missing-recipe-map warning, recipe-serializer sync load order, `dyeable` tag, effect-screen base class, NeoForge double reload, AE2 hack removed, JEMI display overrides.
- [x] F3. Batched renderer: decision recorded (superseded by vanilla GUI item atlas or flagged as perf regression).

## Known bounds / not in scope
- Legacy `forge/` (1.20.4 Forge) module stays excluded from `settings.gradle`, untouched beyond merge fallout.
- Runtime verification is limited to what can be automated locally (launch, inventory screenshot, logs); no full manual QA of every recipe category.
