# Decision log: SEMI 26.2 port (autopilot, 2026-09-22)

## E1. Same recipe as 26.1: port on top of SEMI's `26.1`, using the community fork's 26.2 delta as the answer key
- **Decision:** branch `26.2` from SEMI's `26.1` (which carries four review/fix rounds the fork never had) and re-implement, hunk by hunk, what `link-fgfgui/emi@26.2` changed between 26.1 and 26.2 (38 source files, +161/-170, plus JEMI fixes for JEI 30 and two NeoForge fixes), instead of merging the fork's branch.
- **Why:** the fork's `26.2` branch forked off its `26.1` before most of its own late fixes and before all of SEMI's; a merge would reintroduce regressions. The delta is small enough to re-apply by hand against the real 26.2 sources.
- **Double-check:** any hunk where the fork deleted functionality (e.g. its `StackBatcher`, `EmiSearchWidget` edits) was adapted, not deleted; see the port worker's report in `AUTOPILOT_26.2_REPORT.md`.

## E2. Toolchain pinned to the newest 26.2 releases available on 2026-09-22
- Minecraft 26.2 (no hotfix), Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, ModMenu 20.0.2, NeoForge 26.2.0.88, JEI API 30.35.0.223. Gradle 9.5.0 + loom-no-remap 1.17.493 kept from 26.1 (Fabric recommends Loom 1.17 / Gradle 9.5.1; the 0.0.1 Gradle bump is not needed for this build).

## E3. Name the new vanilla 26.2 item tags instead of excluding them
- **Decision:** add English names (`tag.minecraft.*` keys, the style upstream uses for post-1.20 tags) for the 17 item tags vanilla 26.2 introduced: `concrete`, `concrete_powders`, `glazed_terracotta`, `sulfur_cube_food`, `sulfur_cube_swallowable` and the twelve `sulfur_cube_archetype/*` tags.
- **Why:** EMI logs every displayed tag without a translation as a reload warning (17 warnings on both loaders in the dev client), and upstream's convention is to translate vanilla tags, including behaviour tags such as `axolotl_tempt_items`, rather than to exclude them. Excluding the sulfur cube tags would hide them from `#` searches for no gain.
- **Double-check:** the archetype names ("Bouncy Sulfur Cube Blocks", …) are my wording; vanilla has no display names for tags. Rename freely.

## E4. Fabric dedicated server: verified up to the EULA gate only
- **Decision:** the Fabric server run stops at "You need to agree to the EULA" (`fabric/run/eula.txt` stays `eula=false`), exactly as in the 26.1 run. Accepting the Minecraft EULA is the user's call, so it is not done in an unattended run. NeoForge's dev server run does not gate on the EULA and is run to "Done".
- **What it still proves on Fabric:** the server-side jar loads, all mixins apply and EMI's common initializer runs before the EULA check; the shared server code is the same as on NeoForge, which runs fully.

## E5. Recipe screenshot depth clear follows vanilla's reversed depth range (review round 1, finding 1)
- **Decision:** `EmiScreenshotRecorder` now clears the depth texture of its off-screen target to `0.0` instead of `1.0`.
- **Why:** 26.2 flipped the GUI to a reversed depth range: `DepthStencilState.DEFAULT` compares with `GREATER_THAN_OR_EQUAL`, `GameRenderer.render` clears depth to `0.0` before its GUI pass, and `GuiRenderer.draw` only clears depth itself in the blur path. Matching vanilla's clear value is the only defensible choice.
- **Double-check:** both recipe screenshots taken before this change (`crafting_table.png` on Fabric, `torch.png` on NeoForge) came out correct with `1.0`, which the reviewer's analysis says should not have happened. The change was re-tested in-game after the fix (see the report); if the screenshots had gone blank with `0.0` the change would have been reverted.

## E6. NeoForge sidebar hook stays on `ScreenEvent.Render.Foreground` (review round 1, finding 2)
- **Decision:** keep the event-based hook the community fork chose. Accepted difference: on recipe-book screens (inventory, crafting table, furnaces) NeoForge posts the event after the recipe book component and a `nextStratum()`, so EMI's panels draw above the recipe book there, while Fabric's `HandledScreenMixin` draws them inside `extractContents`, below it. On plain container screens both loaders draw at the same point.
- **Why:** EMI already keeps its panels out of the recipe book's area, so the order only matters where they overlap; drawing EMI on top there is if anything the better of the two. Replacing NeoForge's event with a mixin into NeoForge's patched `AbstractContainerScreen` would trade a documented ordering difference for a fragile injection.
- **Double-check:** if a user reports EMI drawing over the recipe book on NeoForge only, this is why.

## E7. NeoForge mod list: keep both the icon and the banner (review round 1, finding 3)
- **Decision:** `neoforge.mods.toml` declares `iconFile = "icon.png"` and `bannerFile = "icon.png"`. The fork only replaced the deprecated `logoFile` with `iconFile`, which drops the banner in the mod list details pane that 26.1 still showed.
- **Why:** `DefaultModDisplayInfo` reads `iconFile` for the list icon and `bannerFile` (falling back to the deprecated `logoFile`) for the banner; NeoForge's `LogoFileWarningsHandler` explicitly recommends `bannerFile` and/or `iconFile`.

## E8. Minor review items (round 1, findings 4 and 5)
- Removed the NeoForge client-setup line that pre-built all `NeoForgeRenderTypes` into `StackBatcher.EXTRA_RENDER_LAYERS`: the batcher has been inert since 26.1 and nothing reads the list on the live path.
- `EmiPortClient.focus` now also null-checks `Minecraft.gui` (a final field assigned during the `Minecraft` constructor) before reading the current screen from it.
- Findings 6 to 8 (the `partialTick` value now being the tick delta, the screenshot fog uniform inheriting the previous frame's value, the `[26.2,27)` NeoForge range) were reviewed and left as they are: the delta is never used for interpolation, vanilla's own screenshot path has the same fog dependency, and the Minecraft range gates the loader version.
