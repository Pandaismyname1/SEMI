### SEMI
* SEMI (Seasonal EMI) is an unofficial, AI-assisted continuation of EMI by Emi (emilyploszaj). It keeps the `emi` mod id and API; do not install it alongside the original EMI.

### Port
* Ported to Minecraft 26.2 for Fabric and NeoForge (Java 25). Built against Fabric API 0.161.0+26.2, NeoForge 26.2.0.88, the JEI 30.35 API and ModMenu 20.0.2.
* Adapted to the 26.2 client changes: the current screen, overlay and toasts now live on `Minecraft.gui`; the NeoForge sidebar hook uses `ScreenEvent.Render.Foreground`; the recipe screenshot recorder uses the new Blaze3D texture and buffer API; the batched stack renderer stays inert now that vanilla batches item rendering itself.
* Added English names for the item tags that vanilla 26.2 introduced (concrete, concrete powders, glazed terracotta and the sulfur cube tags), so they no longer show up as untranslated in the tag search.

### Tweaks
* JEMI (JEI compatibility) compiles against the JEI 30.35 API.
