package dev.emi.emi.screen;


import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.mixin.accessor.ItemStackRenderStateAccessor;
import dev.emi.emi.mixin.accessor.LayerRenderStateAccessor;
import dev.emi.emi.runtime.EmiLog;

public class StackBatcher {
	private static MethodHandle sodiumSpriteHandle;

	static {
		try {
			Class<?> clazz = null;
			try {
				clazz = Class.forName("me.jellysquid.mods.sodium.client.render.texture.SpriteUtil");
			} catch (Throwable t) {
			}
			sodiumSpriteHandle = MethodHandles.lookup()
				.findStatic(clazz, "markSpriteActive", MethodType.methodType(void.class, TextureAtlasSprite.class));
			if (sodiumSpriteHandle != null) {
				EmiLog.info("Discovered Sodium");
			}
		} catch (Throwable e) {
		}
	}

	/**
	 * Minimal stand-in for {@code MultiBufferSource}, which 26.2 removed along with the
	 * immediate-mode buffer pipeline (rendering now goes through {@code SubmitNodeCollector} /
	 * {@code FeatureRenderDispatcher}). It only exists to keep the batched rendering API surface
	 * compiling: {@link #isEnabled()} is hard-false, so nothing is ever handed one of these.
	 */
	public interface EmiBufferSource {
		VertexConsumer getBuffer(RenderType renderType);
	}

	public interface Batchable {
		boolean isSideLit();
		boolean isUnbatchable();
		void setUnbatchable();
		void renderForBatch(EmiBufferSource vcp, GuiGraphicsExtractor draw, int x, int y, int z, float delta);
	}

	private final BatcherVertexConsumerProvider imm;
	private final EmiBufferSource unlitFacade;
	private final Map<RenderType, MeshData> buffers = new LinkedHashMap<>();
	private final Set<TextureAtlasSprite> spritesToUpdate = Sets.newHashSet();
	private boolean populated = false;
	private boolean dirty = false;
	private int x;
	private int y;
	private int z;

	public static final List<RenderType> EXTRA_RENDER_LAYERS = Lists.newArrayList();

	/**
	 * Always false since 26.1. EMI's batcher replayed an item's baked quads into a shared mesh, which
	 * the {@code ItemStackRenderState} pipeline no longer allows, and vanilla's {@code GuiRenderer}
	 * caches GUI items in its own {@code GuiItemAtlas} anyway. 26.2 went further and removed
	 * {@code MultiBufferSource} and {@code RenderType#draw(MeshData)} outright, so there is no longer
	 * any way to replay a mesh here at all. The config option is kept so that existing config files
	 * stay valid; see D7 in the port decision log.
	 */
	public static boolean isEnabled() {
		return false;
	}

	public StackBatcher() {
		// Every sidebar ScreenSpace owns a batcher, and the ByteBufferBuilders below are several
		// megabytes of off-heap memory that is never freed. Since nothing is ever batched, skip the
		// allocation entirely and leave this instance inert.
		if (!isEnabled()) {
			imm = null;
			unlitFacade = null;
			return;
		}
		Map<RenderType, ByteBufferBuilder> buffers = new HashMap<>();
		assign(buffers, Sheets.cutoutBlockItemSheet());
		assign(buffers, Sheets.translucentItemSheet());
		// 26.3 has no fixed glint render types left, only per-texture ones, so there is nothing to
		// pre-assign a buffer for. Moot either way: this code is unreachable while the batcher is
		// inert.
		for (RenderType layer : EXTRA_RENDER_LAYERS) {
			assign(buffers, layer);
		}
		imm = new BatcherVertexConsumerProvider(new ByteBufferBuilder(256), buffers);
		unlitFacade = new UnlitFacade(imm);
	}

	private void assign(Map<RenderType, ByteBufferBuilder> buffers, RenderType layer) {
		buffers.put(layer, new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE));
	}

	public boolean isPopulated() {
		return populated;
	}

	public void repopulate() {
		dirty = true;
	}

	public void begin(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
		if (dirty) {
			populated = false;
			dirty = false;
			spritesToUpdate.clear();
		}
	}

	public void render(Batchable batchable, GuiGraphicsExtractor draw, int x, int y, float delta) {
		if (!isEnabled()) {
			return;
		}
		if (!populated) {
			try {
				batchable.renderForBatch(batchable.isSideLit() ? imm : unlitFacade, draw, x-this.x, y+this.y, z, delta);
			} catch (Throwable t) {
				if (EmiConfig.devMode) {
					EmiLog.error("Batchable threw exception during batched rendering. See log for info", t);
				}
				batchable.setUnbatchable();
			}
		}
	}

	public void render(EmiIngredient stack, GuiGraphicsExtractor draw, int x, int y, float delta) {
		render(stack, draw, x, y, delta, -1 ^ EmiIngredient.RENDER_AMOUNT);
	}

	public void render(EmiIngredient stack, GuiGraphicsExtractor draw, int x, int y, float delta, int flags) {
		if (stack instanceof Batchable b && !b.isUnbatchable() && isEnabled() && (flags & EmiIngredient.RENDER_ICON) != 0) {
			if (!populated) {
				try {
					b.renderForBatch(b.isSideLit() ? imm : unlitFacade, draw, x-this.x, y + this.y, z, delta);
					if (sodiumSpriteHandle != null && !stack.isEmpty()) {
						ItemStack is = stack.getEmiStacks().get(0).getItemStack();
						Minecraft client = Minecraft.getInstance();
						ItemStackRenderState renderState = new ItemStackRenderState();
						client.getItemModelResolver().updateForTopItem(renderState, is, ItemDisplayContext.GUI, client.level, null, 0);
						if (((ItemStackRenderStateAccessor) renderState).emi$getActiveLayerCount() > 0) {
							ItemStackRenderState.LayerRenderState layer = ((ItemStackRenderStateAccessor) renderState).emi$getLayers()[0];
							List<BakedQuad> quads = ((LayerRenderStateAccessor) layer).emi$getQuads().all();
							for (BakedQuad quad : quads) {
								if (quad != null) {
									spritesToUpdate.add(quad.materialInfo().sprite());
								}
							}
						}
					}
				} catch (Throwable t) {
					if (EmiConfig.devMode) {
						EmiLog.error("Stack threw exception during batched rendering. See log for info", t);
					}
					b.setUnbatchable();
				}
			}
			stack.render(draw, x, y, delta, flags & (~EmiIngredient.RENDER_ICON));
		} else {
			stack.render(draw, x, y, delta, flags);
		}
	}

	public void draw() {
		if (!isEnabled()) {
			return;
		}
		if (sodiumSpriteHandle != null) {
			try {
				for (TextureAtlasSprite sprite : spritesToUpdate) {
					sodiumSpriteHandle.invoke(sprite);
				}
			} catch (Throwable t) {
			}
		}
		if (!populated) {
			bake();
			populated = true;
		}
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
		Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
		modelViewStack.pushMatrix();
		modelViewStack.translate(x, y, 0);
		// Unreachable: draw() returns above unless isEnabled(), and 26.2 removed
		// RenderType#draw(MeshData) with no replacement that takes a bare mesh.
		buffers.values().forEach(MeshData::close);
		buffers.clear();
		modelViewStack.popMatrix();
	}
	
	private void bake() {
		imm.drawCurrentLayer();
		buffers.values().forEach(MeshData::close);
		buffers.clear();
		for (Map.Entry<RenderType, BufferBuilder> entry : imm.getPendingLayerBuffers().entrySet()) {
			bake(entry.getKey(), entry.getValue());
		}
		imm.getPendingLayerBuffers().clear();
	}

	public void bake(RenderType layer, BufferBuilder bldr) {
		MeshData builtBuffer = bldr.build();
		if (builtBuffer == null) {
			return;
		}
		buffers.put(layer, builtBuffer);
	}

	public static class ClaimedCollection {
		private Set<StackBatcher> claimed = Sets.newHashSet();
		private List<StackBatcher> unclaimed = Lists.newArrayList();

		public StackBatcher claim() {
			synchronized (this) {
				StackBatcher batcher;
				if (unclaimed.isEmpty()) {
					batcher = new StackBatcher();
				} else {
					batcher = unclaimed.remove(unclaimed.size() - 1);
				}
				if (batcher == null) {
					batcher = new StackBatcher();
				}
				claimed.add(batcher);
				return batcher;
			}
		}

		public void unclaim(StackBatcher batcher) {
			synchronized (this) {
				claimed.remove(batcher);
				unclaimed.add(batcher);
			}
		}

		public void unclaimAll() {
			synchronized (this) {
				for (StackBatcher batcher : claimed) {
					unclaimed.add(batcher);
				}
				claimed.clear();
			}
		}
	}

	private static class BatcherVertexConsumerProvider implements EmiBufferSource {
		protected final ByteBufferBuilder fallbackBuffer;
		protected final Map<RenderType, ByteBufferBuilder> layerBuffers;
		protected final Map<RenderType, BufferBuilder> pending = new HashMap<>();
		protected RenderType currentLayer = null;

		protected BatcherVertexConsumerProvider(ByteBufferBuilder fallbackBuffer, Map<RenderType, ByteBufferBuilder> layerBuffers) {
			this.fallbackBuffer = fallbackBuffer;
			this.layerBuffers = layerBuffers;
		}

		@Override
		public VertexConsumer getBuffer(RenderType renderLayer) {
			BufferBuilder bufferBuilder = this.pending.get(renderLayer);

			if (bufferBuilder == null) {
				ByteBufferBuilder allocator = this.layerBuffers.get(renderLayer);
				if (allocator != null) {
					bufferBuilder = new BufferBuilder(allocator, renderLayer.primitiveTopology(), renderLayer.format());
				} else {
					if (this.currentLayer != null) {
						this.draw(this.currentLayer);
					}
					bufferBuilder = new BufferBuilder(this.fallbackBuffer, renderLayer.primitiveTopology(), renderLayer.format());
					this.currentLayer = renderLayer;
				}

				this.pending.put(renderLayer, bufferBuilder);
			}

			return bufferBuilder;
		}

		private ByteBufferBuilder getBufferInternal(RenderType layer) {
			return this.layerBuffers.getOrDefault(layer, this.fallbackBuffer);
		}

		public void drawCurrentLayer() {
			if (this.currentLayer != null) {
				RenderType renderLayer = this.currentLayer;
				if (!this.layerBuffers.containsKey(renderLayer)) {
					this.draw(renderLayer);
				}
				this.currentLayer = null;
			}
		}

		public void draw(RenderType layer) {
			ByteBufferBuilder bufferAllocator = this.getBufferInternal(layer);
			boolean isSameAsCurrentLayer = Objects.equals(this.currentLayer, layer);
			if (!isSameAsCurrentLayer && bufferAllocator == this.fallbackBuffer) {
				return;
			}
			BufferBuilder builder = this.pending.remove(layer);
			if (builder == null) {
				return;
			}
			MeshData buffer = builder.build();
			if (buffer != null) {
				buffer.sortQuads(bufferAllocator, VertexSorting.ORTHOGRAPHIC_Z);
				// See draw(): RenderType#draw(MeshData) is gone in 26.2 and this path is dead.
				buffer.close();
			}
			if (isSameAsCurrentLayer) {
				this.currentLayer = null;
			}
		}

		public Map<RenderType, BufferBuilder> getPendingLayerBuffers() {
			return pending;
		}
	}

	private static class UnlitFacade implements EmiBufferSource {
		private final EmiBufferSource delegate;
		private final IdentityHashMap<VertexConsumer, VertexConsumer> cache = new IdentityHashMap<>();

		public UnlitFacade(EmiBufferSource delegate) {
			this.delegate = delegate;
		}

		@Override
		public VertexConsumer getBuffer(RenderType layer) {
			return cache.computeIfAbsent(delegate.getBuffer(layer), Consumer::new);
		}

		private static final class Consumer implements VertexConsumer {
			private final VertexConsumer delegate;

			private Consumer(VertexConsumer delegate) {
				this.delegate = delegate;
			}

			@Override
			public VertexConsumer setNormal(float x, float y, float z) {
				delegate.setNormal(0, -1, 0);
				return this;
			}

			@Override
			public VertexConsumer addVertex(float x, float y, float z) {
				delegate.addVertex(x, y, z);
				return this;
			}

			@Override
			public VertexConsumer setUv(float u, float v) {
				delegate.setUv(u, v);
				return this;
			}

			@Override
			public VertexConsumer setUv1(int u, int v) {
				delegate.setUv1(u, v);
				return this;
			}

			@Override
			public VertexConsumer setUv3(float u, float v) {
				delegate.setUv3(u, v);
				return this;
			}

			@Override
			public VertexConsumer setUv2(int u, int v) {
				delegate.setUv2(u, v);
				return this;
			}

			@Override
			public VertexConsumer setColor(int r, int g, int b, int a) {
				delegate.setColor(r, g, b, a);
				return this;
			}

			@Override
			public VertexConsumer setColor(int color) {
				delegate.setColor(color);
				return this;
			}

			@Override
			public VertexConsumer setLineWidth(float width) {
				delegate.setLineWidth(width);
				return this;
			}
			
		}
	}

}
