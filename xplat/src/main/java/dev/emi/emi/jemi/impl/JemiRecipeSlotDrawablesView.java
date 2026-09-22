package dev.emi.emi.jemi.impl;

import java.util.List;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView;

/**
 * Backs {@link mezz.jei.api.gui.widgets.IRecipeExtrasBuilder#getRecipeSlots()}
 * with the already-built {@link JemiRecipeSlot}s so that JEI categories calling
 * {@code getRecipeSlots().getSlots(role)} (e.g. to feed a scroll grid) receive
 * real drawables that can be repositioned instead of throwing an NPE.
 */
public class JemiRecipeSlotDrawablesView implements IRecipeSlotDrawablesView {
	private final List<IRecipeSlotDrawable> slots;

	public JemiRecipeSlotDrawablesView(List<? extends IRecipeSlotDrawable> slots) {
		this.slots = List.copyOf(slots);
	}

	@Override
	public List<IRecipeSlotDrawable> getSlots() {
		return slots;
	}
}
