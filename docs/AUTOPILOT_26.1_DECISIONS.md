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

## D7. JEMI: info recipes register synchronously again, with the font work deferred instead
- **Decision:** `JemiPlugin.addInfoRecipes` registers on the reload thread (as upstream) and wraps each one in a new `dev.emi.emi.jemi.JemiInfoRecipe`, which only builds the real `EmiInfoRecipe` the first time EMI asks for its size or widgets.
- **Why:** the fork's `Minecraft.getInstance().execute(() -> registry.addRecipe(...))` (commit `fd7eaa89`) ran after the reload had already baked, so JEI "information" recipes were dropped or mutated the recipe lists concurrently. The crash it was working around is real but narrower than it looks: on 26.1 `Font`'s width provider resolves each code point through `GlyphSource.getGlyph`, which returns a *baked* glyph, so measuring text stitches glyph textures and asserts the render thread. `EmiInfoRecipe`'s constructor word-wraps with `Minecraft.getInstance().font.split(...)`, so only that call was unsafe.
- **Rejected:** copying `EmiInfoRecipe` into `jemi/**`; editing `EmiInfoRecipe` (owned by another agent in this port).
- **Double-check:** the real fix belongs in `dev.emi.emi.api.recipe.EmiInfoRecipe` — keep the raw `List<Component>` and wrap it behind a lazy accessor used by `getDisplayHeight()`/`addWidgets()`. `EmiRecipes.bake()` builds the data-driven `emi:info` recipes from `EmiData.recipes` on the same worker thread and hits the identical trap.

## D8. JEMI: the fork's AE2 Inscriber catalyst hack is deleted, not generalised
- **Decision:** `JemiCatalystDetector` (reflection on `appeng.recipes.handlers.InscriberRecipe`) and its use in `JemiRecipe` are gone; slot roles come from JEI again.
- **Why:** one hardcoded mod class and two hardcoded slot names in a generic adapter. AE2 marks those slots `RecipeIngredientRole.INPUT`; if that is wrong it is an AE2/JEI-side issue.
- **Double-check:** with AE2 installed, Inscriber "inscribe" recipes now list the press as an input rather than a catalyst again.

## D9. JEMI: behaviour that upstream had and JEI 29.40 no longer offers is left dropped, not re-invented
- `IRecipeCategory.handleInput` is gone, so `JemiRecipe.JemiWidget.mouseClicked/keyPressed` cannot forward clicks. JEI replaced it with `IRecipeExtrasBuilder.addInputHandler`/`addGuiEventListener`; `JemiRecipeExtrasBuilder` collects both but nothing dispatches to them (upstream never did either). Wiring them needs an `IJeiUserInput`/`InputWithModifiers` implementation — a feature, not a port fix.
- `IRecipeCategory.getBackground()` is gone, so the background draw upstream did before `category.draw` has no equivalent.
- `IRecipeSlotTooltipCallback` is gone (only the rich variant remains), so `JemiSlotWidget`'s legacy tooltip branch cannot exist.
- `EmiDrawContext.enableBlend()`/`resetColor()` are no-ops or per-wrapper on 26.1, and the 2-D `Matrix3x2fStack` has no Z, so upstream's blend/colour/z-offset calls carry no behaviour.
- `IIngredientManager.getTypedIngredientByUid` is gone; `JemiStackSerializer` scans the type's ingredients instead (same result, O(n) per lookup).
