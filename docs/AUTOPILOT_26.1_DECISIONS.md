# Decision log: EMI 26.1 port (autopilot, 2026-09-22)

Each entry: decision, why, alternatives rejected, what a reviewer should double-check.

## D1. Build on the MIT community port `link-fgfgui/emi@26.1` via a real `git merge`
- **Decision:** branch `26.1` = upstream `1.21` HEAD (81f6453c) + merge of `link-fgfgui/emi` branch `26.1` (63e8aef1, 95 commits, MIT, same copyright notice), then re-port the upstream commits the fork lacks.
- **Why:** 323 xplat source files, the Yarn to Mojang mapping switch, and about ten breaking Minecraft versions (1.21.2 recipe/client rework, 1.21.4 item models, 1.21.5 components, 1.21.6 GUI render-state rewrite, 1.21.9/11, 26.1 unobfuscation) make a from-scratch port far riskier than building on a port that already compiles, ships on CurseForge, and has months of user bug fixes. The fork's merge-base with upstream is 35392222 and it already cherry-picked upstream up to 1.1.24, so only 8 small upstream commits remain to re-port. The fork was built locally (JDK 25, 2m13s, both loaders) before being adopted.
- **Rejected:** (a) from-scratch port with agents: highest bug risk, weeks of equivalent effort; (b) `Dabolus/MEMI` as base: targets 26.1.1 only, 2 commits, less tested, though its Fabric-Loom + ModDevGradle build layout was noted; (c) squashing the fork into one commit: loses per-commit authorship, while a merge keeps attribution intact and lets upstream `1.21` be merged forward later.
- **Double-check:** licensing/attribution is satisfied by git history plus the unchanged LICENSE; if the maintainer prefers a squash, `git merge --squash` is trivial later.

## D2. Keep `dev.architectury.loom-no-remap` (the fork's build) instead of Fabric Loom + ModDevGradle
- **Why:** it is proven on this exact codebase (fork CI + local build) and keeps the xplat/fabric/neoforge layout and the `transformProduction*` configurations untouched. MEMI's Fabric-Loom + MDG layout is the "official" toolchain, but switching is a build-system rewrite unrelated to the port's correctness.
- **Double-check:** `loom-no-remap` is a SNAPSHOT plugin line; pin to a release (`1.17.x`) if reproducibility matters.

## D3. Upstream's `ProxyRecipeManager` abstraction wins over the fork's `EmiPort.getRecipeMap()` helpers
- **Why:** upstream's last commit ("Introduce some abstractions") was written precisely to isolate recipe-manager access for this port. Keeping upstream's API keeps future merges from `1.21` clean. The implementation delegates to the fork's `EmiAgnos.getRecipeMap()` (Fabric: `ClientRecipeSynchronizedEvent`; NeoForge: `RecipesReceivedEvent`).
- **Double-check:** `ProxyRecipeManager.isAvailable()` on 26.1 means "a RecipeMap has been received from the server", not "world != null".

## D4. Restore upstream versioning (`-SNAPSHOT` unless `RELEASE`); do not bump `mod_version`
- **Why:** the fork embeds the git short hash in the version for its own CI releases. Upstream's convention is simpler and expected by the release workflow. Version/release numbering is the maintainer's call.

## D5. Drop the fork's dev-only `localRuntime` mod lists and its CurseForge auto-publish workflow
- **Why:** about 30 cursemaven/modrinth test mods slow every build and belong to the fork author's workflow; the workflow publishes to *their* CurseForge project (id 1544558). Upstream's `build.yml`/`release.yml` are kept and only updated to JDK 25.

## D6. Dependency versions bumped to the latest 26.1.2-compatible releases available on 2026-09-22
- **Why:** users run the newest loader/API builds; compiling against the latest catches API drift now instead of at runtime. Exact versions are recorded in `gradle.properties`; any bump that failed to compile is noted in the morning report.

## D-PR. Push/PR target
- **Decision:** never push to or open a PR against `emilyploszaj/emi` (origin). That is the upstream maintainer's public repo and the port issue there was closed by the maintainer. Push only to a remote the user owns, if one is configured and authenticated; otherwise leave the branch local and say so.
