# Report: SEMI port to Minecraft 26.3 (autopilot, 2026-09-22)

Branch `26.3` of <https://github.com/Pandaismyname1/SEMI>, based on the released `26.2` (1.1.24+26.2). Contract: `AUTOPILOT_26.3_PORT.md`; decisions: `AUTOPILOT_26.3_DECISIONS.md` (F1–F10).

## What was done
- Toolchain: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, ModMenu 21.0.0-beta.1, NeoForge 26.3.0.8-beta (only beta NeoForge builds exist for 26.3 so far), JEI API 31.4.0.19; Gradle 9.5 / loom-no-remap 1.17.493 unchanged.
- The community fork's 26.2→26.3 delta (63 files) was re-applied hunk by hunk on SEMI's `26.2` and compile-fixed against the real 26.3 sources. Areas: input (SDL scancodes, `InputConstants.KEY_*`, mouse buttons 1/2/3, `KeyEvent.key()/keycode()`), rendering (`com.mojang.renderpearl` GPU API, `TextureTarget(name,w,h,color,depth)`, `GuiGraphicsExtractor.tooltip(..., boolean)`, `VertexConsumer.setUv3`, `ItemQuads`), items (data-driven `block_transformer` registry for axe/hoe/shovel, brewing as `RecipeType.BREWING`, `cooking_fuel`/`compostable` components with `ResolvableInt`, `BonemealSource`, `Inventory.placeItemBackInInventory(…, Prediction)`, `DataComponentPatch.split()`), recipes (`RecipeMap.create(HolderLookup)`; Fabric builds a `MappedRegistry` from the synchronized recipes), JEMI (`ContextMap.builder().buildAndValidate`), loader hooks.
- Deliberate deviations from the fork (F3–F6): `EmiRegistry#getRecipeMap()` and `RecipeMap` kept (no API break); tooltip spacing kept as in 26.2; the configurable "display all recipes" bind kept; the batched renderer stays inert; fluid tags named too.
- New: resolved fuel/composting values are sent server→client in the configuration phase (`emi:context_int_values`, F9), because the `context_int_provider` registry is reloadable-only and never synchronized by vanilla.
- Fixes found by the in-game runs and the reviews: dedicated servers could not load `EmiPort` (client-only `IntegratedServer` in a verifier-relevant assignment; F7), `TransmuteRecipe` with `KEEP_INPUT_ITEM` threw during baking (F8), `KeyboardMixin` compared against the GLFW repeat value, `EmiBind.isHeld` used the GLFW unknown-key value and had no bounds guard, the provider evaluator covered 5 of 23 provider types, the block-transform predicate heuristic ignored offsets, stale warning text.
- English names for the 13 item tags and 4 fluid tags vanilla 26.3 added; README, CHANGELOG, contract and decision log updated.

## Verification (evidence)
| Check | Result |
|-------|--------|
| `./gradlew :fabric:build :neoforge:build -x test` | BUILD SUCCESSFUL after every fix round (clean build verified by the worker) |
| Fabric 26.3 client, quick-play into the upgraded `emitest` world | one reload, 4030 recipes; index, search, left click (recipes), right click (uses), R and U keys, recipe screenshot (`fabric/run/screenshots/emi/recipes/minecraft/torch_2.png`), Fuel tab (coal: 8 items), composting (wheat seeds 30 %), stripping (World Interaction), brewing (Awkward Potion), tooltip box identical to 26.2; second run after the fixes: one reload, no exceptions, map cloning shows 2 filled maps |
| NeoForge 26.3 client, same world | one reload, 4018 recipes; sidebars, search, recipe screen, right-click uses, Fuel tab, recipe screenshot (`neoforge/run/screenshots/emi/recipes/minecraft/torch_3.png`) |
| NeoForge client on a NeoForge **dedicated server** (localhost, offline mode) | joins, one reload, no "burn time unknown"/"compost chance unknown" warnings; Fuel tab shows coal at 8 items, composting 30 %, shulker box recolouring transmute renders — the configuration-phase sync works end to end |
| NeoForge dedicated server | before F7: crashed at mod construction (`IntegratedServer` not present); after: reaches "Done", accepts the client |
| Fabric dedicated server | before F7: crashed in the `main` entrypoint; after: loads EMI and stops at the Minecraft EULA gate (not accepted on the user's behalf, same as 26.1/26.2) |
| Review round 1 | no severe defect; 2 moderate (key repeat, warning text), 3 minor, 5 informational; all actionable ones fixed |
| Review round 2 | see below |

## Delta review
**Round 1** (port commits against the 26.3 bytecode, NeoForge 26.3.0.8-beta sources, Fabric API 0.161.0+26.3, JEI 31.4): confirmed every mixin/accessor/AW/AT target, the input constant mapping, the tooltip flag, the screenshot recorder against vanilla's own screenshot path, the block transformer plumbing, brewing/fuel/composting components, `Prediction.SERVER_ONLY`, `DataComponentPatch.split()`, the Fabric `MappedRegistry` recipe map, JEMI against JEI 31.4, ModMenu 21 and the tag names (236 item + 10 fluid tags, none unnamed). It recommended the server→client value sync that became F9 over reading the vanilla data files on the client (which would show wrong numbers on servers with datapacks and nothing for mods).

**Round 2** (fix commits): see the section appended below once the round completed.

## Please double-check
- F9 adds a configuration-phase payload on EMI's channel; it is guarded by `canSend`/`hasChannel`, so vanilla clients and servers without SEMI are unaffected, and it was tested only NeoForge↔NeoForge (the Fabric server is EULA-gated in this environment).
- The tag names (F-decisions E3-style) and the provider-evaluation heuristics (dispatcher default branch, conditional false branch, weighted mean) are my choices.
- NeoForge is a beta build; re-run the NeoForge client when a release build appears (bump `neoforge_version`, the `[26.3,27)` range already accepts it).

## Needs you
- Run the **Release** workflow on branch `26.3` (channel `release`, or `beta` if you prefer while NeoForge itself is beta). It builds `semi-1.1.24+26.3+fabric.jar` / `…+neoforge.jar`, creates the GitHub release from `CHANGELOG.md` and uploads to Modrinth and CurseForge with game version 26.3.
- Optional: `eula=true` in `fabric/run/eula.txt` to run the Fabric dedicated server to "Done" yourself.
