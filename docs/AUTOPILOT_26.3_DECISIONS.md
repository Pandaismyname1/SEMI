# Decision log: SEMI 26.3 port (autopilot, 2026-09-22)

## F1. Same recipe as 26.2: port on top of SEMI's `26.2`, using the community fork's 26.3 delta as the answer key
- **Decision:** branch `26.3` from SEMI's released `26.2` and re-implement, hunk by hunk, what `link-fgfgui/emi@26.3` changed between its 26.2 and 26.3 branches (63 files, +719/-520: tool-item and brewing accessors, input handling, the two C2S packets, JEMI for JEI 31, config screens, the screenshot recorder), instead of merging the fork's branch.
- **Why:** the fork's branches do not carry SEMI's review/fix rounds; a merge would reintroduce regressions. The delta is three times the size of the 26.2 one, so the port worker gets the fork's per-commit patches plus the first build's compile-error list as its work list.

## F2. Toolchain pinned to the newest 26.3 releases available on 2026-09-22
- Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, ModMenu 21.0.0-beta.1 (the only ModMenu line for 26.3), NeoForge 26.3.0.8-beta (NeoForge has published only beta builds for 26.3 so far; the fork used 26.3.0.1-beta), JEI API 31.4.0.19 (the fork used 31.0.0.5). Gradle 9.5.0 + loom-no-remap 1.17.493 kept.
- **Double-check:** when a non-beta NeoForge 26.3 build appears, bump `neoforge_version` and re-run the NeoForge client; the `[26.3,27)` dependency range already accepts it.
