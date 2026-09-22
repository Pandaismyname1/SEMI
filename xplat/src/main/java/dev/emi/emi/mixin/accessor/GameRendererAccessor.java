package dev.emi.emi.mixin.accessor;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

	/**
	 * The renderer that turns the game's {@code GuiRenderState} into draw calls. Recipe
	 * screenshots reuse it instead of building their own, which would duplicate its buffers and
	 * lose its picture-in-picture renderers and item atlas.
	 */
	@Accessor("guiRenderer")
	GuiRenderer emi$getGuiRenderer();

	/**
	 * Only needed for the empty {@code FogMode.NONE} buffer that GUI draws are given.
	 */
	@Accessor("fogRenderer")
	FogRenderer emi$getFogRenderer();

	/**
	 * GUI draws are lit by the flat UI lightmap instead of the world's.
	 * {@code GameRenderer.render} raises this around its own GUI pass and lowers it again
	 * afterwards, so a standalone screenshot render has to do the same or the items in the shot are
	 * lit by wherever the player happens to be standing. The field is not final, so no
	 * {@code @Mutable} is needed.
	 */
	@Accessor("useUiLightmap")
	void emi$setUseUiLightmap(boolean useUiLightmap);

	@Accessor("useUiLightmap")
	boolean emi$getUseUiLightmap();
}
