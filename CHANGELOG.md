### SEMI
* SEMI (Seasonal EMI) is an unofficial, AI-assisted continuation of EMI by Emi (emilyploszaj). It keeps the `emi` mod id and API; do not install it alongside the original EMI.

### Port
* Ported to Minecraft 26.1.2 for Fabric and NeoForge (Mojang mappings, Java 25). Built on the MIT community port by link-fgfgui, merged with full attribution.
* The cross-platform artifact is now published only as `emi-xplat-mojmap`; there is no intermediary artifact for 26.1+ because Minecraft is no longer obfuscated.
* Recipes are received from the server (Fabric recipe synchronization API / NeoForge `RecipesReceivedEvent`) since the client no longer holds a recipe manager.

### Additions
* Added a configurable keybind (default Ctrl+Y) to display all recipes.
* Added `EmiApi.displayRecipesForWorkstation`.
* Tag search now queries all registries instead of just items.

### Tweaks
* JEMI (JEI compatibility) updated for the JEI 29.40 API.
* The NeoForge network channel now declares a version (`registrar("1")`). Only relevant if you mix EMI builds: an older EMI on one side and this build on the other will refuse to connect instead of silently degrading.
