# Decision log: SEMI 26.2 port (autopilot, 2026-09-22)

## E1. Same recipe as 26.1: port on top of SEMI's `26.1`, using the community fork's 26.2 delta as the answer key
- **Decision:** branch `26.2` from SEMI's `26.1` (which carries four review/fix rounds the fork never had) and re-implement, hunk by hunk, what `link-fgfgui/emi@26.2` changed between 26.1 and 26.2 (38 source files, +161/-170, plus JEMI fixes for JEI 30 and two NeoForge fixes), instead of merging the fork's branch.
- **Why:** the fork's `26.2` branch forked off its `26.1` before most of its own late fixes and before all of SEMI's; a merge would reintroduce regressions. The delta is small enough to re-apply by hand against the real 26.2 sources.
- **Double-check:** any hunk where the fork deleted functionality (e.g. its `StackBatcher`, `EmiSearchWidget` edits) was adapted, not deleted; see the port worker's report in `AUTOPILOT_26.2_REPORT.md`.

## E2. Toolchain pinned to the newest 26.2 releases available on 2026-09-22
- Minecraft 26.2 (no hotfix), Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, ModMenu 20.0.2, NeoForge 26.2.0.88, JEI API 30.35.0.223. Gradle 9.5.0 + loom-no-remap 1.17.493 kept from 26.1 (Fabric recommends Loom 1.17 / Gradle 9.5.1; the 0.0.1 Gradle bump is not needed for this build).
