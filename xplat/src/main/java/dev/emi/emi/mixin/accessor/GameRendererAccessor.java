package dev.emi.emi.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
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
	 * {@code GuiRenderer} always draws into whatever {@code GameRenderer.mainRenderTarget}
	 * returns, so recipe screenshots point it at their own target for the duration of one render
	 * and put the real one back afterwards. The field moved here from {@code Minecraft} in 26.2
	 * and is final, hence {@code @Mutable}.
	 */
	@Accessor("mainRenderTarget")
	@Mutable
	void emi$setMainRenderTarget(RenderTarget target);

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
