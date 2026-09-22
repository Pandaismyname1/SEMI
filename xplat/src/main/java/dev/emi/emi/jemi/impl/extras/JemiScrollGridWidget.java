package dev.emi.emi.jemi.impl.extras;

import java.util.List;
import java.util.Optional;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
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
	public static final int SCROLL_BAR_WIDTH = 18;
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
		if (slots == null) {
			return;
		}
		for (int i = 0; i < slots.size(); i++) {
			int col = i % gridWidth;
			int row = i / gridWidth;
			int cellX = x + col * SLOT_SIZE;
			int cellY = y + row * SLOT_SIZE;
			slots.get(i).setPosition(cellX + 1, cellY + 1);
		}
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

	/** Number of rows actually occupied by the managed slots. */
	public int getOccupiedRows() {
		if (slots == null || slots.isEmpty() || gridWidth <= 0) {
			return 0;
		}
		return (slots.size() + gridWidth - 1) / gridWidth;
	}
}
