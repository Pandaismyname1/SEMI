package dev.emi.emi.screen.tooltip;

import dev.emi.emi.EmiPort;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

public interface EmiTooltipComponent extends ClientTooltipComponent {

	default void drawTooltip(EmiDrawContext context, TooltipRenderData tooltip) {
	}

	default void drawTooltipText(TextRenderData text) {
	}

	@Override
	default void extractImage(Font textRenderer, int x, int y, int width, int height, GuiGraphicsExtractor raw) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		context.push();
		context.translate(x, y);
		drawTooltip(context, new TooltipRenderData(textRenderer, x, y));
		context.pop();
	}

	@Override
	default void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		drawTooltipText(new TextRenderData(graphics, font, x, y));
	}

	public static class TextRenderData {
		public final GuiGraphicsExtractor graphics;
		public final Font renderer;
		public final int x, y;
		
		public TextRenderData(GuiGraphicsExtractor graphics, Font renderer, int x, int y) {
			this.graphics = graphics;
			this.renderer = renderer;
			this.x = x;
			this.y = y;
		}

		public void draw(String text, int x, int y, int color, boolean shadow) {
			draw(EmiPort.literal(text), x, y, color, shadow);
		}

		public void draw(Component text, int x, int y, int color, boolean shadow) {
			graphics.text(renderer, text, x + this.x, y + this.y, EmiDrawContext.opaqueColor(color), shadow);
		}
	}

	public static class TooltipRenderData {
		public final Font text;
		public final int x, y;

		public TooltipRenderData(Font text, int x, int y) {
			this.text = text;
			this.x = x;
			this.y = y;
		}
	}
}
