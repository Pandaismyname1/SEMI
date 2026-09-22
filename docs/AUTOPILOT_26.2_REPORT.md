# Report: SEMI port to Minecraft 26.2 (autopilot, 2026-09-22)

Branch `26.2` of <https://github.com/Pandaismyname1/SEMI>, based on `26.1`. Contract: `AUTOPILOT_26.2_PORT.md`; decisions: `AUTOPILOT_26.2_DECISIONS.md`.

## What was done
- Toolchain bumped to Minecraft 26.2, Fabric API 0.161.0+26.2, NeoForge 26.2.0.88, JEI API 30.35.0.223, ModMenu 20.0.2 (Java 25, Gradle 9.5, loom-no-remap 1.17.493 unchanged).
- The community fork's 26.1→26.2 delta (`link-fgfgui/emi`, 38 source files) was re-applied hunk by hunk on top of SEMI's `26.1` (which carries four review/fix rounds the fork never had), then compile-fixed against the real 26.2 sources. Main API moves: screen/HUD access via `Minecraft.gui`, `Language.getInstance().has` for translation checks, `TextColor.fromLegacyFormat` for formatting colours, `Pair` instead of `Tuple`, `ColorCollection.asList()` for the colored block sets, the Blaze3D 26.2 texture/buffer API in the recipe screenshot recorder, `GuiRenderer.render()` without arguments, `MultiBufferSource` gone (the batched stack renderer stays inert, vanilla batches item rendering itself), NeoForge `ScreenEvent.Render.Foreground` for the sidebar hook, `iconFile` in `neoforge.mods.toml`.
- Mixins, access wideners/transformers re-targeted and checked against the 26.2 bytecode (`MinecraftClientMixin` → `reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)`, `GameRendererAccessor` → `mainRenderTarget`, `MinecraftAccessor` removed, `Hud.getMobEffectSprite`).
- JEMI compiles against the JEI 30.35 API (byte-identical to the 29.40 API SEMI 26.1 targets).
- English names for the 17 item tags vanilla 26.2 introduced (decision E3).
- README supported-versions table, `CHANGELOG.md`, port contract and decision log updated.

## Verification (evidence)
| Check | Result |
|-------|--------|
| `./gradlew :fabric:build :neoforge:build -x test` | BUILD SUCCESSFUL (twice: after the port, after the tag names) |
| Fabric 26.2 client, quick-play into the upgraded `emitest` world | one EMI reload, 3809 recipes, no EMI or mixin errors; index, search, recipe screen, coals tag icon and the recipe screenshot button (`fabric/run/screenshots/emi/recipes/minecraft/crafting_table.png`, 252×124 RGBA) all work; the user also played a session and confirmed it works; second run after the review fixes: reload with zero tag warnings, torch recipe screenshot correct |
| NeoForge 26.2 client, same world | one EMI reload, 3805 recipes (same 4-recipe gap to Fabric as on 26.1), no errors; sidebars at the correct position with the new `Render.Foreground` hook, search → torch recipe, recipe screenshot (`neoforge/run/screenshots/emi/recipes/minecraft/torch.png`); second run after the review fixes (new `bannerFile`, removed setup line, depth clear): loads, zero tag warnings, torch recipe screenshot correct |
| Fabric dedicated server | loads EMI 1.1.24-SNAPSHOT+26.2+fabric with 44 mods, mixin subsystem up, initializer runs; stops at the Minecraft EULA gate (not accepted on the user's behalf, decision E4) |
| NeoForge dedicated server | loads SEMI 1.1.24-SNAPSHOT+26.2+neoforge, upgrades the world, reaches "Done (0.220s)", no errors; stopped by the run timeout |
| Delta review | round 1: 8 findings, 5 fixed, 3 informational; round 2: see below |

## Delta review
**Round 1** (adversarial review of the port commits `9c24aade`, `c4eced31`, `6e978064` against the 26.2 bytecode, the NeoForge 26.2.0.88 sources and the JEI 30.35 API): 8 findings, none a crash.
1. Recipe screenshot depth clear (`1.0`) does not match 26.2's reversed depth range: fixed, now `0.0` like `GameRenderer.render` (decision E5). Re-tested in-game on both loaders after the fix: `fabric/run/screenshots/emi/recipes/minecraft/torch.png` and `neoforge/run/screenshots/emi/recipes/minecraft/torch_2.png` render correctly.
2. NeoForge draws EMI in a later stratum than Fabric on recipe-book screens: accepted and documented (decision E6).
3. `logoFile` → `iconFile` dropped the NeoForge mod-list banner: `bannerFile` added (decision E7).
4. Dead `NeoForgeRenderTypes` pre-build at client setup: removed (E8).
5. `Minecraft.gui` dereferenced with only `Minecraft` null-checked: hardened (E8).
6.–8. Informational (`partialTick` semantics, screenshot fog uniform, NeoForge version range): reviewed, no change (E8).
Everything else the reviewer checked came back clean: every mixin target, accessor, access widener and access transformer against the 26.2 bytecode; the `Gui` moves; the screenshot recorder's texture, buffer and readback path; the `Language`/`TextColor`/`ColorCollection`/`Pair` replacements; `StackBatcher` being inert on every live path; the JEI 30.35 API being byte-identical to 29.40 (209 classes, `javap` diff empty); vanilla GUI sprites (29 additions, no removals); loader metadata ranges.

**Round 2** (review of the fix commits `7db7d9d6` and `decdf6c6`): see below.

## Please double-check
- The tag names in decision E3 are my wording.
- The recipe screenshot path was verified on both loaders in the dev client; the NeoForge sidebar position (the fork dropped the `leftPos/topPos` translate when moving to `ScreenEvent.Render.Foreground`) was verified visually.

## Needs you
- Run the **Release** workflow on branch `26.2` (channel `release`); it builds `semi-1.1.24+26.2+fabric.jar` and `semi-1.1.24+26.2+neoforge.jar`, creates the GitHub release and uploads to Modrinth and CurseForge with game version `26.2`.
- If you want the Fabric dedicated server run to "Done", set `eula=true` in `fabric/run/eula.txt` and run `./gradlew :fabric:runServer`.
