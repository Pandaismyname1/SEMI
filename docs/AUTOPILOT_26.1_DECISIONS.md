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

## D-F2. Recipe / sync / gameplay regression pass (branch `fix/f2-recipes`)

### D-F2.1 `/give` fallback rebuilt by hand — 26.1 deleted `ItemInput#serialize`
- **Decision:** `EmiPort.serializeItemArgument(ItemStack, HolderLookup.Provider)` rebuilds `namespace:id[component=snbt,!removed]` from `ItemStack#typeHolder`/`getComponentsPatch`, `BuiltInRegistries.DATA_COMPONENT_TYPE`, `TypedDataComponent#encodeValue` and `ItemParser`'s syntax constants. `EmiScreenManager.give` uses it instead of `"give @s " + is.getItem()`.
- **Why:** `javap` of 26.1.2 `net.minecraft.commands.arguments.item.ItemInput` shows a record with only `createItemStack(int)` — the `serialize(HolderLookup.Provider)` upstream used is gone, and no method with descriptor `(HolderLookup$Provider)String` exists anywhere in the jar. `Item#toString()` drops both the namespace and every component.
- **Double-check:** in-game, cheat a written book / dyed leather / enchanted item with EMI installed **client-side only** (no EMI on the server) and confirm the command round-trips.

### D-F2.2 `EmiSuspiciousStewRecipe` / `EmiMapCloningRecipe` deleted rather than re-registered
- **Why:** 26.1.2 has neither `SuspiciousStewRecipe` nor `MapCloningRecipe` (full listing of `net/minecraft/world/item/crafting/`). `data/minecraft/recipe/suspicious_stew_from_*.json` are now plain `minecraft:crafting_shapeless` recipes (17 of them, one per flower) and `map_cloning.json` is a `minecraft:crafting_transmute`, so the existing `ShapelessRecipe` and `TransmuteRecipe` branches already display them from vanilla's own data.

### D-F2.3 Custom-recipe shapeless heuristic: unknown shapes are displayed shapeless
- **Why:** upstream's `recipe.fits(w, h)` does not exist on 26.1's `Recipe`, and `PlacementInfo` only exposes a flat ingredient list plus a slot map that cannot recover a width. So: recipes with more than 9 ingredients are skipped (replacing `fits(3, 3)`), `ShapedRecipe`s that reach the generic branch are skipped (they are the oversized ones the branch above rejected), and everything else is emitted as a shapeless recipe.
- **Double-check:** a modded crafting recipe that is shaped but does not extend `ShapedRecipe` will now be shown as shapeless. There is no information left in the API to do better.

### D-F2.4 `ItemTags.DYEABLE` stays `ItemTags.CAULDRON_CAN_REMOVE_DYE`
- **Why:** `ItemTags` in 26.1.2 has no `DYEABLE` field; `data/minecraft/tags/item/cauldron_can_remove_dye.json` contains exactly the old dyeable set (4 leather armor pieces, leather horse armor, wolf armor).

### D-F2.5 One reload per world join, on both loaders
- **Decision:** recipes call only `EmiReloadManager.reloadRecipes()`, tags call only `reloadTags()`. Fabric tags come from `CommonLifecycleEvents.TAGS_LOADED` filtered to `client == true`; NeoForge tags come from `TagsUpdatedEvent.ClientPacketReceived`.
- **Why:** both loaders were calling both halves from a single event, so the two-phase mask in `EmiReloadManager` could never gate anything. Verified in the 26.1.2 jars: `TagsUpdatedEvent$ClientPacketReceived` is posted from `ClientConfigurationPacketListenerImpl` (join) and `ClientPacketListener` (`/reload`), while `ServerDataLoad` comes from `ReloadableServerResources`; Fabric fires `TAGS_LOADED` from the same two client listeners plus `ReloadableServerResources` with `client == false`.
- **Double-check:** the `client.level != null` guard had to be removed from the NeoForge tag listener, because tags arrive during the configuration phase before a level exists. The reload worker still refuses to run without a level.

### D-F2.6 NeoForge only requests the recipe payload when someone can receive EMI packets
- **Why:** `OnDatapackSyncEvent#sendRecipes` records recipe types for every player the event syncs to, so it cannot be filtered per player; `getRelevantPlayers().anyMatch(p -> p.connection.hasChannel(EmiNetwork.PING))` is the finest granularity the API allows.

### D-F2.7 NeoForge chess payload registered bidirectionally under `emi:chess`
- **Why:** `EmiPacketHandler` registered `emi:chess_s2c`/`emi:chess_c2s` while `EmiChessPacket#type()` returns `emi:chess`, so the channel was never registered and `hasChannel` always failed. `registrar(...)` takes a protocol version, not a namespace, so it is now `"1"`.
