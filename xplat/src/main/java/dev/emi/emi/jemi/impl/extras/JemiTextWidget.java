package dev.emi.emi.jemi.impl.extras;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.api.widget.WidgetHolder;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import mezz.jei.api.gui.widgets.ITextWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public class JemiTextWidget extends JemiPlaceable<ITextWidget> implements ITextWidget {
	// Defaults match JEI's TextWidget: no colour override (its RECIPE_TEXT_WIDGET_TEXT
	// default is 0xFF000000), no shadow, and a line spacing of 2.
	public int color = 0xFF000000;
	public boolean shadow = false;
	public int spacing = 2;
	public HorizontalAlignment horizontal = HorizontalAlignment.LEFT;
	public VerticalAlignment vertical = VerticalAlignment.TOP;
	public List<FormattedText> text;

	public JemiTextWidget(List<FormattedText> text, int width, int height) {
		super(width, height);
		this.text = text;
	}

	/**
	 * Lays the text out roughly the way JEI's own {@code TextWidget} does and emits one
	 * EMI text widget per line: lines are wrapped to {@code width}, clipped to the number
	 * that fit in {@code height}, then aligned within the widget's area.
	 *
	 * <p>The wrapping itself is not identical. JEI wraps with {@code StringUtil.splitLines},
	 * which hyphenates over-long words and reports whether the result was truncated so it
	 * can attach a tooltip showing the full text; EMI uses plain {@link Font#split}, so
	 * long words are broken without a hyphen and nothing is added for clipped lines.
	 */
	public void addWidgets(WidgetHolder holder) {
		if (width > 0 && height > 0 && !text.isEmpty()) {
			Font font = Minecraft.getInstance().font;
			// JEI hardcodes the 9px vanilla line height here rather than asking the font.
			int lineHeight = Math.max(1, 9 + spacing);
			int maxLines = height / lineHeight;
			if (maxLines * lineHeight + 9 <= height) {
				maxLines++;
			}
			List<FormattedCharSequence> lines = Lists.newArrayList();
			outer:
			for (FormattedText line : text) {
				for (FormattedCharSequence wrapped : font.split(line, width)) {
					if (lines.size() >= maxLines) {
						break outer;
					}
					lines.add(wrapped);
				}
			}
			if (!lines.isEmpty()) {
				int textHeight = lineHeight * lines.size() - spacing - 1;
				int lineY = y + vertical.getYPos(height, textHeight);
				for (FormattedCharSequence line : lines) {
					int lineX = x + horizontal.getXPos(width, font.width(line));
					holder.addText(line, lineX, lineY, color, shadow);
					lineY += lineHeight;
				}
			}
		}
		addTooltip(holder);
	}

	@Override
	public ITextWidget setFont(Font font) {
		// Unimplemented, as upstream: EMI's TextWidget always draws with the client font.
		return this;
	}

	@Override
	public ITextWidget setColor(int color) {
		this.color = color;
		return this;
	}

	@Override
	public ITextWidget setLineSpacing(int spacing) {
		this.spacing = spacing;
		return this;
	}

	@Override
	public ITextWidget setShadow(boolean shadow) {
		this.shadow = shadow;
		return this;
	}

	@Override
	public ITextWidget setTextAlignment(HorizontalAlignment horizontalAlignment) {
		this.horizontal = horizontalAlignment;
		return this;
	}

	@Override
	public ITextWidget setTextAlignment(VerticalAlignment verticalAlignment) {
		this.vertical = verticalAlignment;
		return this;
	}

	@Override
	public ITextWidget setTooltip(FormattedText tooltip) {
		setTooltipText(tooltip);
		return this;
	}

	@Override
	public ITextWidget setTooltip(Collection<? extends FormattedText> tooltip) {
		setTooltipText(tooltip);
		return this;
	}

	@Override
	public ITextWidget setTooltip(TooltipComponent tooltip) {
		setTooltipComponent(tooltip);
		return this;
	}

	@Override
	public ITextWidget setTooltip(IRecipeWidgetTooltipCallback tooltipCallback) {
		setTooltipCallback(tooltipCallback);
		return this;
	}

}
