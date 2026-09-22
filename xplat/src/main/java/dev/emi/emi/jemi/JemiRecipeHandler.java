package dev.emi.emi.jemi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.jemi.impl.JemiRecipeLayoutBuilder;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import dev.emi.emi.jemi.impl.JemiRecipeSlotBuilder;
import dev.emi.emi.jemi.impl.JemiRecipeSlotsView;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.ProxyRecipeManager;
import dev.emi.emi.screen.EmiScreenManager;
import mezz.jei.api.constants.ModIds;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public class JemiRecipeHandler<T extends AbstractContainerMenu, R> implements EmiRecipeHandler<T> {
	private static final Identifier UNIVERSAL_RECIPE_TRANSFER_ID = Identifier.fromNamespaceAndPath(ModIds.JEI_ID, "universal_recipe_transfer_handler");

	private final IRecipeType<R> type;
	private final boolean isUniversal;
	public IRecipeTransferHandler<T, R> handler;
	// Synthesizing a slots view runs the JEI category's setRecipe and coerces every stack, and
	// render()/canCraft() ask for one every frame, so the result is memoized. The memo has to be
	// static: JemiPlugin.getRecipeHandler builds a new handler for every call, so a per-instance
	// cache never hit and the whole layout was rebuilt each frame. The keys are weak so a recipe
	// that goes away with a reload does not pin its layout, and the maps are cleared outright when
	// JEMI reloads. "positioned" views were built with EMI's laid-out widgets, which the fallback
	// path reads slot positions off; a bare view must not be reused when those are available.
	private static final Map<EmiRecipe, CachedView> POSITIONED_VIEWS = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<EmiRecipe, CachedView> BARE_VIEWS = Collections.synchronizedMap(new WeakHashMap<>());
	// Recipes whose view could not be built, so the failure is reported once instead of every frame.
	private static final Set<EmiRecipe> FAILED_VIEWS = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

	private static record CachedView(IRecipeType<?> type, JemiRecipeSlotsView view) {
	}

	/** Called when JEMI reloads, since every cached view describes the old JEI runtime. */
	public static void clearCaches() {
		POSITIONED_VIEWS.clear();
		BARE_VIEWS.clear();
		FAILED_VIEWS.clear();
	}

	public JemiRecipeHandler(IRecipeTransferHandler<T, R> handler) {
		this.handler = handler;
		this.type = handler.getRecipeType();
		this.isUniversal = isUniversalType(type);
	}

	private static boolean isUniversalType(IRecipeType<?> type) {
		if (type == null) {
			return false;
		}
		try {
			return UNIVERSAL_RECIPE_TRANSFER_ID.equals(type.getUid());
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public boolean alwaysDisplaySupport(EmiRecipe recipe) {
		return type != null;
	}

	@Override
	public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
		List<EmiStack> stacks = new ArrayList<>();
		T menu = screen.getMenu();
		for (Slot slot : menu.slots) {
			try {
				if (slot.container instanceof Inventory) {
					ItemStack item = slot.getItem();
					if (!item.isEmpty()) {
						stacks.add(EmiStack.of(item));
					}
				}
			} catch (Exception e) {
			}
		}
		return new EmiPlayerInventory(stacks);
	}

	@Override
	public boolean supportsRecipe(EmiRecipe recipe) {
		if (isUniversal) {
			return recipe.supportsRecipeTree();
		}
		return (type == null || getRawRecipe(recipe) != null) && recipe.supportsRecipeTree();
	}

	@Override
	public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
		IRecipeTransferError err = jeiCraft(recipe, context, false, null);
		return err == null || err.getType().allowsTransfer;
	}

	@Override
	public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
		IRecipeTransferError err = jeiCraft(recipe, context, true, null);
		if (err == null || err.getType().allowsTransfer) {
			Minecraft.getInstance().setScreen(context.getScreen());
		}
		return err == null || err.getType().allowsTransfer;
	}

	@Override
	public void render(EmiRecipe recipe, EmiCraftContext<T> context, List<Widget> widgets, GuiGraphicsExtractor raw) {
		EmiDrawContext draw = EmiDrawContext.wrap(raw);
		R rawRecipe = getRawRecipe(recipe);
		JemiRecipeSlotsView view = getSlotsView(recipe, rawRecipe, widgets);
		IRecipeTransferError err = jeiCraft(recipe, context, false, view);
		if (err != null) {
			if (err.getType() == IRecipeTransferError.Type.COSMETIC) {
				for (Widget widget : widgets) {
					if (widget instanceof RecipeFillButtonWidget) {
						Bounds b = widget.getBounds();
						draw.fill(b.left(), b.top(), b.width(), b.height(), err.getButtonHighlightColor());
					}
				}
			}
			if (view != null) {
				view.getSlotViews().forEach(v -> {
					if (v instanceof JemiRecipeSlot jrs) {
						jrs.highlight = 0;
					}
				});
				// The matrix is pushed outside the try so that a third-party error
				// implementation which throws cannot leave the scale(0, 0) / translate
				// behind on the stack for the rest of the frame.
				draw.push();
				try {
					draw.matrices().translate(-100000, -100000);
					draw.matrices().scale(0, 0);
					err.showError(raw, EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY, view, 0, 0);
				} catch (Exception e) {
					EmiLog.error("Error showing JEI transfer error", e);
				} finally {
					try {
						draw.pop();
					} catch (Exception e) {
						// A third-party showError that pushed and popped unevenly leaves the stack
						// in a state pop() rejects. Nothing useful can be done about it, but it
						// must not escape render() and take the whole screen down with it.
						EmiLog.error("Error restoring the matrix after a JEI transfer error", e);
					}
				}
				view.getSlotViews().forEach(v -> {
					if (v instanceof JemiRecipeSlot jrs && jrs.highlight != 0 && !jrs.isEmpty()) {
						draw.fill(jrs.x, jrs.y, 18, 18, jrs.highlight);
					}
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private IRecipeTransferError jeiCraft(EmiRecipe recipe, EmiCraftContext<T> context, boolean craft, JemiRecipeSlotsView view) {
		try {
			Minecraft client = Minecraft.getInstance();
			R rawRecipe = getRawRecipe(recipe);

			if (view == null) {
				view = getSlotsView(recipe, rawRecipe, List.of());
			}

			if (view == null) {
				return () -> IRecipeTransferError.Type.INTERNAL;
			}

			R recipeArg;
			if (rawRecipe != null) {
				recipeArg = rawRecipe;
			} else if (isUniversal) {
				recipeArg = (R) (Object) recipe;
			} else {
				return () -> IRecipeTransferError.Type.INTERNAL;
			}

			return handler.transferRecipe(context.getScreenHandler(), recipeArg, view, client.player, context.getAmount() > 1, craft);
		} catch (Exception e) {
			EmiLog.error("Error executing JEI craft", e);
		}
		return () -> IRecipeTransferError.Type.INTERNAL;
	}

	private JemiRecipeSlotsView getSlotsView(EmiRecipe recipe, R rawRecipe, List<Widget> widgets) {
		boolean positioned = !widgets.isEmpty();
		CachedView cached = POSITIONED_VIEWS.get(recipe);
		if ((cached == null || cached.type() != type) && !positioned) {
			cached = BARE_VIEWS.get(recipe);
		}
		if (cached != null && cached.type() == type) {
			return cached.view();
		}
		JemiRecipeSlotsView view = createSlotsView(recipe, rawRecipe, type, widgets);
		if (view != null) {
			(positioned ? POSITIONED_VIEWS : BARE_VIEWS).put(recipe, new CachedView(type, view));
		}
		return view;
	}

	public static <R> JemiRecipeSlotsView createSlotsView(EmiRecipe recipe, R rawRecipe, IRecipeType<R> type, List<Widget> widgets) {
		if (recipe instanceof JemiRecipe jr && jr.cachedSlotsView != null) {
			if (jr.cachedSlotsView instanceof JemiRecipeSlotsView jrsv) {
				return jrsv;
			}
		}

		JemiRecipeLayoutBuilder builder = null;
		IRecipeCategory<?> category = null;
		if (recipe instanceof JemiRecipe jr && jr.category != null) {
			category = jr.category;
		} else {
			category = JemiPlugin.getJeiCategory(recipe.getCategory());
		}
		// getRawRecipe falls back to the id-matched vanilla RecipeHolder unconditionally,
		// which is right for transferRecipe but not here: handing the category a recipe
		// it does not accept only throws a ClassCastException out of setRecipe, once per
		// frame. Only drive the category when the recipe really is of its type.
		if (rawRecipe != null && category != null && type != null && type.getRecipeClass() != null
				&& type.getRecipeClass().isInstance(rawRecipe) && isOfHolderType(type, rawRecipe)) {
			try {
				builder = new JemiRecipeLayoutBuilder();
				@SuppressWarnings("unchecked")
				IRecipeCategory<Object> casted = (IRecipeCategory<Object>) category;
				casted.setRecipe(builder, rawRecipe, JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
				for (JemiRecipeSlotBuilder jrsb : builder.slots) {
					jrsb.acceptor.coerceStacks(jrsb.richTooltipCallback, jrsb.renderers);
				}
				if (builder.slots.isEmpty()) {
					builder = null;
				}
			} catch (Exception e) {
				if (FAILED_VIEWS.add(recipe)) {
					EmiLog.error("Error building JEI slots view from category", e);
				}
				builder = null;
			}
		}

		if (builder == null) {
			List<SlotWidget> slotWidgets = widgets.stream().filter(w -> w instanceof SlotWidget).map(w -> (SlotWidget) w).toList();
			builder = new JemiRecipeLayoutBuilder();
			addIngredients(builder, slotWidgets, recipe.getOutputs(), RecipeIngredientRole.OUTPUT);
			int blankedSlots = 0;
			if (recipe instanceof EmiCraftingRecipe ecr) {
				if (ecr.shapeless) {
					int inputSize = recipe.getInputs().size();
					if (inputSize == 1) {
						addBlankIngredients(builder, slotWidgets, 4, RecipeIngredientRole.INPUT);
						blankedSlots += 4;
						addIngredients(builder, slotWidgets, recipe.getInputs(), RecipeIngredientRole.INPUT);
					} else if (inputSize < 5) {
						int wrap = 0;
						for (EmiIngredient i : recipe.getInputs()) {
							addIngredients(builder, slotWidgets, List.of(i), RecipeIngredientRole.INPUT);
							wrap++;
							if (wrap >= 2) {
								wrap = 0;
								addBlankIngredients(builder, slotWidgets, 1, RecipeIngredientRole.INPUT);
								blankedSlots += 1;
							}
						}
					} else {
						addIngredients(builder, slotWidgets, recipe.getInputs(), RecipeIngredientRole.INPUT);
					}
				} else {
					if (ecr.canFit(1, 3)) {
						addBlankIngredients(builder, slotWidgets, 1, RecipeIngredientRole.INPUT);
						blankedSlots += 1;
					} else if (ecr.canFit(3, 1) || (ecr.canFit(3, 2) && !ecr.canFit(2, 2))) {
						addBlankIngredients(builder, slotWidgets, 3, RecipeIngredientRole.INPUT);
						blankedSlots += 3;
					}
					addIngredients(builder, slotWidgets, recipe.getInputs().subList(0, Math.max(9, recipe.getInputs().size()) - blankedSlots), RecipeIngredientRole.INPUT);
				}
			} else {
				addIngredients(builder, slotWidgets, recipe.getInputs(), RecipeIngredientRole.INPUT);
			}
			if (recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING) {
				for (int i = recipe.getInputs().size() + blankedSlots; i < 9; i++) {
					addIngredients(builder, slotWidgets, List.of(EmiStack.EMPTY), RecipeIngredientRole.INPUT);
				}
			}
			// CRAFTING_STATION is JEI 29's name for the old CATALYST role; RENDER_ONLY is
			// decorative and would misrepresent these as scenery to the transfer handler.
			addIngredients(builder, slotWidgets, recipe.getCatalysts(), RecipeIngredientRole.CRAFTING_STATION);
		}

		return new JemiRecipeSlotsView(builder.slots.stream().map(JemiRecipeSlot::new).toList());
	}

	/**
	 * Whether {@code rawRecipe} really belongs to {@code type}.
	 * <p>
	 * {@code getRecipeClass()} alone is not enough for a vanilla recipe type: every
	 * {@link IRecipeHolderType} erases to {@code RecipeHolder.class}, so the {@code isInstance}
	 * check passes for a holder of any type at all and the category is handed a recipe it cannot
	 * use. {@code IRecipeHolderType}'s uid is {@code BuiltInRegistries.RECIPE_TYPE.getKey(...)} of
	 * the type it was created from, so the holder's own registered type is compared against it.
	 */
	private static boolean isOfHolderType(IRecipeType<?> type, Object rawRecipe) {
		try {
			if (type instanceof IRecipeHolderType<?> && rawRecipe instanceof RecipeHolder<?> holder) {
				return type.getUid().equals(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()));
			}
		} catch (Exception e) {
			// A type that cannot even say what it is does not get to drive a category.
			return false;
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private R getRawRecipe(EmiRecipe recipe) {
		try {
			if (type != null && type.getRecipeClass() != null) {
				if (recipe instanceof JemiRecipe jr && jr.recipe != null) {
					if (type.getRecipeClass().isAssignableFrom(jr.recipe.getClass())) {
						return type.getRecipeClass().cast(jr.recipe);
					}
				}
				RecipeHolder<?> holder = ProxyRecipeManager.getRecipeEntry(recipe.getId());
				if (holder != null && type.getRecipeClass().isAssignableFrom(holder.getClass())) {
					return type.getRecipeClass().cast(holder);
				}
			}
			// Unconditional fallback, as upstream. Many JEI transfer handlers declare a
			// recipe class that is the Recipe itself rather than the RecipeHolder, so the
			// check above never matches; handing them the vanilla entry anyway is what
			// makes transfer work at all. transferRecipe is called inside a try/catch,
			// so a handler that cannot use the entry just reports an internal error.
			return (R) ProxyRecipeManager.getRecipeEntry(recipe.getId());
		} catch (Exception e) {
		}
		return null;
	}

	private static void addBlankIngredients(JemiRecipeLayoutBuilder builder, List<SlotWidget> widgets, int amount, RecipeIngredientRole role) {
		for (int i = 0; i < amount; i++) {
			addIngredients(builder, widgets, List.of(EmiStack.EMPTY), RecipeIngredientRole.INPUT);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void addIngredients(JemiRecipeLayoutBuilder builder, List<SlotWidget> widgets, List<? extends EmiIngredient> stacks, RecipeIngredientRole role) {
		for (EmiIngredient ing : stacks) {
			int x = 0, y = 0;
			for (SlotWidget w : widgets) {
				if (w.getStack() == ing) {
					x = w.getBounds().x();
					y = w.getBounds().y();
				}
			}
			IIngredientAcceptor acceptor = builder.addSlot(role, x, y);
			for (EmiStack stack : ing.getEmiStacks()) {
				Optional<ITypedIngredient<?>> opt = JemiUtil.getTyped(stack);
				if (opt.isPresent()) {
					ITypedIngredient<?> typed = opt.get();
					acceptor.add((IIngredientType) typed.getType(), typed.getIngredient());
				}
			}
		}
	}
}
