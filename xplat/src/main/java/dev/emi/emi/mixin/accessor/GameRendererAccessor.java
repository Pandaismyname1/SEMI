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
}
