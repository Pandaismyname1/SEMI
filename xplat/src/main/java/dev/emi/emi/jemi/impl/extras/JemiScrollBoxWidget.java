package dev.emi.emi.jemi.impl.extras;

import java.util.List;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IScrollBoxWidget;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.FormattedText;

public class JemiScrollBoxWidget implements IScrollBoxWidget {
	/** Matches JEI's {@code AbstractScrollWidget.getScrollBoxScrollbarExtraWidth()}. */
	private static final int SCROLL_BAR_WIDTH = 16;
	public int x, y;
	public int width, height;

	/**
	 * @param width the TOTAL width of the box, scrollbar included. JEI's
	 *  {@code ScrollBoxRecipeWidget} uses the width passed to
	 *  {@code addScrollBoxWidget} as its whole area and subtracts the scrollbar to
	 *  get the content width, so adding the scrollbar here would double count it.
	 */
	public JemiScrollBoxWidget(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	@Override
	public ScreenPosition getPosition() {
		return new ScreenPosition(x, y);
	}

	@Override
	public ScreenRectangle getArea() {
		return new ScreenRectangle(getPosition(), width, height);
	}

	@Override
	public int getContentAreaWidth() {
		return width - SCROLL_BAR_WIDTH;
	}

	@Override
	public int getContentAreaHeight() {
		return height;
	}

	@Override
	public IScrollBoxWidget setContents(IDrawable contents) {
		// Unimplemented
		return this;
	}

	@Override
	public IScrollBoxWidget setContents(List<FormattedText> text) {
		// Unimplemented
		return this;
	}
	
}
