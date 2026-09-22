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
