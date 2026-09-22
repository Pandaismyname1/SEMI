package dev.emi.emi.runtime;

import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3x2fStack;

public class EmiDrawContext {
	private final Minecraft client = Minecraft.getInstance();
	private final GuiGraphicsExtractor context;
	/**
	 * The current global tint, standing in for 1.21's {@code RenderSystem.setShaderColor}.
	 * GUI rendering is single threaded, so a single static value behaves like the old global
	 * shader color: every wrapper, including ones created deeper in the call stack from a raw
	 * {@link GuiGraphicsExtractor}, observes the tint set by an outer wrapper.
	 * Note that items go through {@code ItemStackRenderState} in 26.1 and cannot be tinted.
	 */
	private static int color = -1;
	private static final List<Runnable> DEFERRED_TOOLTIPS = new ArrayList<>();
	/**
	 * The last raw context seen by {@link #wrap}. Vanilla allocates a fresh
	 * {@link GuiGraphicsExtractor} every frame, so a change here means a new frame (or a
	 * detached context, like the one used for recipe screenshots) has started, and any tint or
	 * deferred tooltip left behind by the previous one is stale.
	 */
	private static GuiGraphicsExtractor lastContext = null;

	private EmiDrawContext(GuiGraphicsExtractor context) {
		this.context = context;
	}

	public static EmiDrawContext wrap(GuiGraphicsExtractor context) {
		if (context != lastContext) {
			lastContext = context;
			color = -1;
			DEFERRED_TOOLTIPS.clear();
		}
		return new EmiDrawContext(context);
	}

	public GuiGraphicsExtractor raw() {
		return context;
	}

	public Matrix3x2fStack matrices() {
		return context.pose();
	}

	public void push() {
		matrices().pushMatrix();
	}

	public void pop() {
		matrices().popMatrix();
	}

	public void scale(float x, float y) {
		matrices().scale(x, y);
	}

	public void translate(float x, float y) {
		matrices().translate(x, y);
	}

	public void drawTexture(Identifier texture, int x, int y, int u, int v, int width, int height) {
		drawTexture(texture, x, y, width, height, u, v, width, height, 256, 256);
	}

	public void drawTexture(Identifier texture, int x, int y, float u, float v, int width, int height) {
		drawTexture(texture, x, y, u, v, width, height, 256, 256);
	}

	public void drawTexture(Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
	}

	public void drawTexture(Identifier texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
		context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color);
	}

	public void fill(int x, int y, int width, int height, int color) {
		context.fill(RenderPipelines.GUI, x, y, x + width, y + height, color);
	}

	public void fill(int x, int y, int width, int height) {
		context.fill(RenderPipelines.GUI, x, y, x + width, y + height, color);
	}

	public void drawText(Component text, int x, int y) {
		drawText(text, x, y, -1);
	}

	public void drawText(Component text, int x, int y, int color) {
		context.text(client.font, text, x, y, opaqueColor(color), false);
	}

	public void drawText(FormattedCharSequence text, int x, int y, int color) {
		context.text(client.font, text, x, y, opaqueColor(color), false);
	}

	public void drawTextWithShadow(Component text, int x, int y) {
		drawTextWithShadow(text, x, y, -1);
	}

	public void drawTextWithShadow(Component text, int x, int y, int color) {
		context.text(client.font, text, x, y, opaqueColor(color), true);
	}

	public void drawTextWithShadow(FormattedCharSequence text, int x, int y, int color) {
		context.text(client.font, text, x, y, opaqueColor(color), true);
	}

	public void drawCenteredText(Component text, int x, int y) {
		drawCenteredText(text, x, y, -1);
	}

	public void drawCenteredText(Component text, int x, int y, int color) {
		context.text(client.font, text, x - client.font.width(text) / 2, y, opaqueColor(color), false);
	}

	public void drawCenteredTextWithShadow(Component text, int x, int y) {
		drawCenteredTextWithShadow(text, x, y, -1);
	}

	public void drawCenteredTextWithShadow(Component text, int x, int y, int color) {
		context.centeredText(client.font, text.getVisualOrderText(), x, y, opaqueColor(color));
	}

	/**
	 * Vanilla's convention for text colors: a color with no meaningful alpha (the top six bits
	 * are all zero) is treated as fully opaque, anything else keeps the alpha it was given.
	 */
	public static int opaqueColor(int color) {
		if ((color & 0xFC000000) == 0) {
			return color | 0xFF000000;
		}
		return color;
	}

	public void enableDepthTest() {
	}

	public void disableDepthTest() {
	}

	public void enableBlend() {
	}

	public void disableBlend() {
	}

	public void resetColor() {
		setColor(1f, 1f, 1f, 1f);
	}

	public void setColor(float r, float g, float b) {
		setColor(r, g, b, 1f);
	}

	public void setColor(float r, float g, float b, float a) {
		int ri = (int)(r * 255) & 0xFF;
		int gi = (int)(g * 255) & 0xFF;
		int bi = (int)(b * 255) & 0xFF;
		int ai = (int)(a * 255) & 0xFF;
		EmiDrawContext.color = (ai << 24) | (ri << 16) | (gi << 8) | bi;
	}

	public void drawStack(EmiIngredient stack, int x, int y) {
		stack.render(raw(), x, y, client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	public void drawStack(EmiIngredient stack, int x, int y, int flags) {
		drawStack(stack, x, y, client.getDeltaTracker().getGameTimeDeltaPartialTick(false), flags);
	}

	public void drawStack(EmiIngredient stack, int x, int y, float delta, int flags) {
		stack.render(raw(), x, y, delta, flags);
	}

	public void deferTooltip(Runnable tooltipRenderer) {
		DEFERRED_TOOLTIPS.add(tooltipRenderer);
	}

	public void flushDeferredTooltips() {
		if (!DEFERRED_TOOLTIPS.isEmpty()) {
			context.nextStratum();
			List<Runnable> tooltips = List.copyOf(DEFERRED_TOOLTIPS);
			DEFERRED_TOOLTIPS.clear();
			for (Runnable r : tooltips) {
				r.run();
			}
		}
	}
}
