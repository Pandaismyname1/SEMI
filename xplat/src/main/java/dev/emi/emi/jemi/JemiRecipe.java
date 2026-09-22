package dev.emi.emi.jemi;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.jemi.impl.JemiIngredientAcceptor;
import dev.emi.emi.jemi.impl.JemiRecipeLayoutBuilder;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import dev.emi.emi.jemi.impl.JemiRecipeSlotBuilder;
import dev.emi.emi.jemi.impl.JemiRecipeSlotDrawablesView;
import dev.emi.emi.jemi.impl.JemiRecipeSlotsView;
import dev.emi.emi.jemi.impl.JemiTooltipBuilder;
import dev.emi.emi.jemi.impl.extras.JemiRecipeExtrasBuilder;
import dev.emi.emi.jemi.impl.extras.JemiScrollGridWidget;
import dev.emi.emi.jemi.impl.extras.JemiTextWidget;
import dev.emi.emi.jemi.impl.extras.JemiWidgetBuilder;
import dev.emi.emi.jemi.widget.JemiSlotWidget;
import dev.emi.emi.jemi.widget.JemiTankWidget;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.ProxyRecipeManager;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class JemiRecipe<T> implements EmiRecipe {
	public List<EmiIngredient> inputs = Lists.newArrayList();
	public List<EmiIngredient> catalysts = Lists.newArrayList();
	public List<EmiStack> outputs = Lists.newArrayList();
	public EmiRecipeCategory recipeCategory;
	public Identifier originalId, id;
	public IRecipeCategory<T> category;
	public T recipe;
	public boolean allowTree = true;
	public IRecipeSlotsView cachedSlotsView;

	public JemiRecipe(EmiRecipeCategory recipeCategory, IRecipeCategory<T> category, T recipe) {
		this.recipeCategory = recipeCategory;
		this.category = category;
		this.recipe = recipe;
		this.originalId = category.getIdentifier(recipe);
		if (this.originalId != null) {
			this.id = EmiPort.id("jei", "/" + EmiUtil.subId(this.originalId));
		}
		JemiRecipeLayoutBuilder builder = new JemiRecipeLayoutBuilder();
		category.setRecipe(builder, recipe, JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
		for (JemiRecipeSlotBuilder jrsb : builder.slots) {
			jrsb.acceptor.coerceStacks(jrsb.richTooltipCallback, jrsb.renderers);
		}
		this.cachedSlotsView = new JemiRecipeSlotsView(builder.slots.stream().map(JemiRecipeSlot::new).toList());
		for (JemiIngredientAcceptor acceptor : builder.ingredients) {
			EmiIngredient stack = acceptor.build();
			if (acceptor.role == RecipeIngredientRole.INPUT) {
				inputs.add(stack);
			} else if (acceptor.role == RecipeIngredientRole.RENDER_ONLY) {
				catalysts.add(stack);
			} else if (acceptor.role == RecipeIngredientRole.OUTPUT) {
				if (stack.getEmiStacks().size() > 1) {
					allowTree = false;
				}
				outputs.addAll(stack.getEmiStacks());
			}
		}
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return recipeCategory;
	}

	@Override
	public @Nullable RecipeHolder<?> getBackingRecipe() {
		return ProxyRecipeManager.getRecipeEntry(originalId);
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return inputs;
	}

	@Override
	public List<EmiIngredient> getCatalysts() {
		return catalysts;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return outputs;
	}

	@Override
	public int getDisplayWidth() {
		return category.getWidth();
	}

	@Override
	public int getDisplayHeight() {
		return category.getHeight();
	}

	@Override
	public boolean supportsRecipeTree() {
		return allowTree && EmiRecipe.super.supportsRecipeTree();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void addWidgets(WidgetHolder widgets) {
		Optional<IRecipeLayoutDrawable<T>> opt = JemiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe, JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
		JemiRecipeLayoutBuilder builder = new JemiRecipeLayoutBuilder();
		category.setRecipe(builder, recipe, JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
		for (JemiRecipeSlotBuilder jrsb : builder.slots) {
			jrsb.acceptor.coerceStacks(jrsb.richTooltipCallback, jrsb.renderers);
		}
		// Build the slot drawables up front. createRecipeExtras (run below) can
		// reposition output slots — e.g. a JEI scroll grid arranges them into
		// cells — so the slots must exist before extras are applied, and the
		// JemiSlotWidgets must be created afterwards so they pick up the final
		// coordinates instead of the default (0, 0).
		List<JemiRecipeSlot> slots = builder.slots.stream().map(JemiRecipeSlot::new).toList();
		if (opt.isPresent()) {
			widgets.add(new JemiWidget(0, 0, getDisplayWidth(), getDisplayHeight(), opt.get()));
		}
		try {
			JemiRecipeExtrasBuilder extras = new JemiRecipeExtrasBuilder(new JemiRecipeSlotDrawablesView(slots));
			category.createRecipeExtras(extras, recipe, JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
			for (JemiWidgetBuilder b : extras.widgets) {
				b.addWidgets(widgets);
			}
			for (JemiTextWidget t : extras.textWidgets) {
				t.addWidgets(widgets);
			}
			for (JemiScrollGridWidget grid : extras.scrollGrids) {
				addScrollGridBackground(widgets, grid);
			}
		} catch(Throwable t) {
			EmiLog.error("Exception adding JEMI extras", t);
		}
		if (opt.isPresent()) {
			for (JemiRecipeSlot slot : slots) {
				if (slot.tankInfo != null && !slot.getIngredients(JemiUtil.getFluidType()).toList().isEmpty()) {
					widgets.add(new JemiTankWidget(slot, this));
				} else {
					widgets.add(new JemiSlotWidget(slot, this));
				}
			}
		}
	}

	/**
	 * Draws the 18x18 slot cell backgrounds for a JEI scroll grid. JEI's own
	 * {@code ScrollGridRecipeWidget} draws these; EMI renders slot contents via
	 * {@link JemiSlotWidget}, so the cell backgrounds are emitted here as plain
	 * textures underneath the slot widgets.
	 */
	private void addScrollGridBackground(WidgetHolder widgets, JemiScrollGridWidget grid) {
		if (grid.slots == null) {
			return;
		}
		int count = grid.slots.size();
		int cellSize = 18;
		for (int i = 0; i < count; i++) {
			int col = i % grid.gridWidth;
			int row = i / grid.gridWidth;
			widgets.addTexture(EmiTexture.SLOT, grid.x + col * cellSize, grid.y + row * cellSize);
		}
	}

	public class JemiWidget extends Widget {

		private final IRecipeLayoutDrawable<T> recipeLayoutDrawable;
		private final Bounds bounds;
		private final int x, y;

		public JemiWidget(int x, int y, int w, int h, IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
			this.recipeLayoutDrawable = recipeLayoutDrawable;
			this.bounds = new Bounds(x, y, w, h);
			this.x = x;
			this.y = y;
		}

		@Override
		public Bounds getBounds() {
			return bounds;
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
			EmiDrawContext context = EmiDrawContext.wrap(draw);
			context.push();
			context.translate(x, y);
			category.draw(recipe, recipeLayoutDrawable.getRecipeSlotsView(), context.raw(), mouseX, mouseY);
			context.pop();
		}

		@Override
		public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
			JemiTooltipBuilder builder = new JemiTooltipBuilder();
			category.getTooltip(builder, recipe, recipeLayoutDrawable.getRecipeSlotsView(), mouseX, mouseY);
			return builder.tooltip;
		}

		// Upstream routed both of these to IRecipeCategory.handleInput, which JEI 29.40
		// removed. Its replacement is IRecipeExtrasBuilder.addInputHandler /
		// addGuiEventListener; JemiRecipeExtrasBuilder collects those but nothing
		// dispatches to them yet, so plugin-defined clicks and keys are inert.
		@Override
		public boolean mouseClicked(int mouseX, int mouseY, int button) {
			return false;
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			return false;
		}
	}
}
