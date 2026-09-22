package dev.emi.emi.jemi.impl.extras;

import java.util.List;
import java.util.Optional;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IScrollGridWidget;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * EMI-side stand-in for JEI's {@code ScrollGridRecipeWidget}.
 *
 * <p>JEI's scroll grid owns a list of output {@link IRecipeSlotDrawable}s and
 * arranges them into a {@code columns} wide grid, repositioning each slot via
 * {@link IRecipeSlotDrawable#setPosition}. EMI renders slot contents through
 * {@link dev.emi.emi.jemi.widget.JemiSlotWidget}, which reads the slot's
 * {@code x}/{@code y} at construction time, so this widget repositions the
 * shared slot objects here (before the slot widgets are built) and leaves the
 * actual cell-background drawing to a companion EMI widget added by
 * {@link dev.emi.emi.jemi.JemiRecipe}.
 */
public class JemiScrollGridWidget implements IScrollGridWidget {
	private static final int SLOT_SIZE = 18;
	/** Matches JEI's {@code AbstractScrollWidget.getScrollBoxScrollbarExtraWidth()}. */
	public static final int SCROLL_BAR_WIDTH = 16;
	public List<IRecipeSlotDrawable> slots;
	public int x, y;
	public int gridWidth, gridHeight;
	public int width, height;

	public JemiScrollGridWidget(List<IRecipeSlotDrawable> slots, int x, int y, int gridWidth, int gridHeight) {
		this.slots = slots;
		this.x = x;
		this.y = y;
		this.gridWidth = gridWidth;
		this.gridHeight = gridHeight;
		this.width = gridWidth * SLOT_SIZE + SCROLL_BAR_WIDTH;
		this.height = gridHeight * SLOT_SIZE;
		layoutSlots();
	}

	/**
	 * Reposition every managed slot into its grid cell. Mirrors JEI's
	 * {@code ScrollGridRecipeWidget.drawContents}, which calls
	 * {@code slot.setPosition(x + 1, y + 1)} per cell so the slot content sits
	 * one pixel inside the 18x18 cell background.
	 */
	private void layoutSlots() {
		int count = getVisibleSlotCount();
		for (int i = 0; i < count; i++) {
			int col = i % gridWidth;
			int row = i / gridWidth;
			int cellX = x + col * SLOT_SIZE;
			int cellY = y + row * SLOT_SIZE;
			slots.get(i).setPosition(cellX + 1, cellY + 1);
		}
	}

	/**
	 * Number of slots that actually fit in the grid. JEI only lays out (and draws)
	 * the {@code columns * visibleRows} window it is currently scrolled to; EMI has
	 * no scrolling here, so only the first window is ever shown and everything past
	 * it is left at its original position rather than spilling out below the grid.
	 */
	public int getVisibleSlotCount() {
		if (slots == null || gridWidth <= 0 || gridHeight <= 0) {
			return 0;
		}
		return Math.min(slots.size(), gridWidth * gridHeight);
	}

	@Override
	public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
		// Unimplemented
		return Optional.empty();
	}

	@Override
	public ScreenPosition getPosition() {
		return new ScreenPosition(x, y);
	}

	@Override
	public IScrollGridWidget setPosition(int xPos, int yPos) {
		this.x = xPos;
		this.y = yPos;
		// Re-layout now that the grid origin moved, so slot coordinates track
		// the new position before JemiSlotWidgets read them.
		layoutSlots();
		return this;
	}

	@Override
	public IScrollGridWidget setPosition(int areaX, int areaY, int areaWidth, int areaHeight,
			HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
		int xPos = areaX + horizontalAlignment.getXPos(areaWidth, getWidth());
		int yPos = areaY + verticalAlignment.getYPos(areaHeight, getHeight());
		return setPosition(xPos, yPos);
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public ScreenRectangle getScreenRectangle() {
		return new ScreenRectangle(getPosition(), getWidth(), getHeight());
	}

	/** Number of rows actually occupied by the visible slots, never more than {@code gridHeight}. */
	public int getOccupiedRows() {
		int count = getVisibleSlotCount();
		if (count <= 0) {
			return 0;
		}
		return (count + gridWidth - 1) / gridWidth;
	}
}
