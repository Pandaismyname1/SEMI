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

## D8. NeoForge dev run: register the generated `GlobalMixin` output dir as a loom mod file
- **Decision:** `fabric/build.gradle` and `neoforge/build.gradle` add `modFiles.from(layout.buildDirectory.dir("generated-class"))` to `loom.mods.main` (and the NeoForge block now also lists its own `sourceSets.main`, as the Fabric block already did).
- **Why:** upstream's `generateClassLists` task emits `dev/emi/emi/mixin/GlobalMixin.class` into an extra source-set output dir. `loom-no-remap` 1.17 only hands classes/resources dirs to FML's `InDevFolderLocator`, so on NeoForge the class was outside the mod module and `runClient` died at bootstrap with `NoClassDefFoundError: dev/emi/emi/mixin/GlobalMixin` (the packaged jar was unaffected because `jar` copies the dir). Verified: after the change the NeoForge client joins a world and EMI reloads (3727 recipes).
- **Rejected:** writing the generated class into `build/classes/java/main` (fights `compileJava`'s stale-class cleanup) or into resources (fights `processResources`).

## D9. Resource-level fixes from the runtime review (verified against 26.1.2 / NeoForge 26.1.2.109 bytecode)
- `neoforge/.../accesstransformer.cfg`: removed three `PotionBrewing` field entries that used 1.20.4 names (`POTION_MIXES`, ...) and silently matched nothing (EMI reaches those fields through `BrewingRecipeRegistryAccessor`), and the `Screen#addWidget` entry; `emi.accesswidener` likewise drops `Screen#addRenderableWidget`. All remaining call sites are `this.` calls inside `Screen` subclasses, so `protected` access suffices; AW and AT now widen the same members.
- `neoforge/src/main/resources/pack.mcmeta` deleted: it still declared `pack_format 8` (1.18) and NeoForge computes the mod's pack as incompatible; with the file absent NeoForge synthesises a compatible section (the Fabric jar ships none).
- `assets/emi/recipe/defaults/emi.json`: `minecraft:chain` no longer exists in 26.1 (split into `iron_chain`/`copper_chain`); both new ids listed.
- `assets/emi/tag/exclusions/emi.json`: `minecraft:occludes_vibration_signals` is a block tag only; removed from the item list.
- `assets/emi/lang/de_de.json`: duplicate `key.emi.default_stack` key (second one was the tooltip text) renamed to `config.emi.tooltip.binds.default_stack`.
- `mixin/accessor/SmithingTrimRecipeAccessor` (empty `@Mixin` interface, no members, no references) deleted with its `emi.mixins.json` entry.
- **Left as is (pre-existing upstream behaviour, noted for the maintainer):** `EmiPersistentData` writes `emi.json` to the process working directory; `EmiPacketHandler` passes `"emi"` where NeoForge expects a protocol version (works, cosmetic); the `conversion.*` mixins are Fabric-only so `EmiStackConvertible#emi()` is unavailable on NeoForge (parity hole that predates the port); `StackBatcher`'s Sodium hook still names the pre-rename `me.jellysquid` package (batcher is inactive on 26.1, see D7).

