### SEMI
* SEMI (Seasonal EMI) is an unofficial, AI-assisted continuation of EMI by Emi (emilyploszaj). It keeps the `emi` mod id and API; do not install it alongside the original EMI.

### Port
* Ported to Minecraft 26.3 for Fabric and NeoForge (Java 25). Built against Fabric API 0.161.0+26.3, NeoForge 26.3.0.8-beta (NeoForge has only published beta builds for 26.3 so far; any 26.3.x is accepted), the JEI 31.4 API and ModMenu 21.0.0-beta.1, on top of the MIT community port by link-fgfgui, merged with full attribution.
* Input: 26.3 switched to SDL scancodes and renumbered the mouse buttons. EMI's binds, mouse handling and key repeat were re-mapped; existing config files keep working because binds are stored by key name. Plugin authors: `Widget#keyPressed` and `Widget#mouseClicked` now receive SDL scancodes/keycodes and mouse buttons 1/2/3, so compare against `InputConstants` constants, never GLFW ones.
* Brewing recipes now come from the server's recipe synchronization, because brewing is a recipe type in 26.3. Their ids moved from `emi:/brewing/...` to `minecraft:brewing/...`, so brewing recipes saved as favourites or defaults need re-adding.
* Fuel burn times and composting chances now come from the `cooking_fuel` and `compostable` item components. Their numbers live in a server-side registry that vanilla does not send to clients, so SEMI sends the resolved values from the server during the connection handshake when the server runs SEMI too; in singleplayer they are read directly. On a server without SEMI the Fuel category and the composting recipes are empty (crafting recipes already needed a synchronizing server since 26.1).
* Stripping, tilling and path-making recipes come from the data-driven block transformers, which are synchronized, so they work on any server. The extra drop of tilling rooted dirt is no longer shown (it is a server-side loot table now).
* Rendering moved to Minecraft's new renderpearl GPU API; the recipe screenshot recorder and the item rendering hooks were adapted.
* Named the 13 item tags and 4 fluid tags vanilla 26.3 added, so they no longer show as untranslated.

### Fixes
* Transmute recipes that keep the input item (map cloning) no longer fail to bake, and transmute outputs such as recoloured shulker boxes are assembled from a real crafting grid.
* Tooltip spacing matches 26.2 (the community port's 26.3 branch cuts multi-line tooltip backgrounds two pixels short).
* Dedicated servers: no client-only class is referenced from the common code any more.

### Tweaks
* JEMI (JEI compatibility) compiles against the JEI 31.4 API.
* The "server does not synchronize recipes" warning now says exactly what is unavailable (crafting and brewing recipes; fuel and composting values unless the server runs SEMI).
