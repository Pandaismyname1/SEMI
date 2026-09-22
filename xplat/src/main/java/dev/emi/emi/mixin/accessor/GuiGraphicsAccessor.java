package dev.emi.emi.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {

	@Invoker("blitSprite")
	void invokeBlitSprite(RenderPipeline renderPipeline, TextureAtlasSprite sprite, int spriteWidth, int spriteHeight, int offsetX, int offsetY, int x, int y, int width, int height, int color);
}
