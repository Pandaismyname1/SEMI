package dev.emi.emi.mixin.accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.3 replaced {@code LayerRenderState#prepareQuadList} with a pre-split {@link ItemQuads}
 * record held in a private field.
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {

	@Accessor("quads")
	ItemQuads emi$getQuads();
}
