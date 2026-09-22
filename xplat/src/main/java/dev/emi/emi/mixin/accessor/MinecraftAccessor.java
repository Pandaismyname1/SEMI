package dev.emi.emi.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

	/**
	 * {@code GuiRenderer} always draws into whatever {@code Minecraft.getMainRenderTarget}
	 * returns, so recipe screenshots point it at their own target for the duration of one render
	 * and put the real one back afterwards.
	 */
	@Mutable
	@Accessor("mainRenderTarget")
	void emi$setMainRenderTarget(RenderTarget target);
}
