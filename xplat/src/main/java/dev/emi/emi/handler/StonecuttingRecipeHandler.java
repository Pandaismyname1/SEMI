package dev.emi.emi.handler;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.ProxyRecipeManager;

public class StonecuttingRecipeHandler implements StandardRecipeHandler<StonecutterMenu> {

	@Override
	public List<Slot> getInputSources(StonecutterMenu handler) {
		List<Slot> list = Lists.newArrayList();
		list.add(handler.getSlot(0));
		int invStart = 2;
		for (int i = invStart; i < invStart + 36; i++) {
			list.add(handler.getSlot(i));
		}
		return list;
	}

	@Override
	public List<Slot> getCraftingSlots(StonecutterMenu handler) {
		return List.of(handler.slots.get(0));
	}

	@Override
	public boolean supportsRecipe(EmiRecipe recipe) {
		return recipe.getCategory() == VanillaEmiRecipeCategories.STONECUTTING;
	}

	@Override
	public @Nullable Slot getOutputSlot(StonecutterMenu handler) {
		return handler.getSlot(1);
	}

	@Override
	public boolean craft(EmiRecipe recipe, EmiCraftContext<StonecutterMenu> context) {
		boolean action = StandardRecipeHandler.super.craft(recipe, context);
		Minecraft client = Minecraft.getInstance();
		StonecutterMenu sh = context.getScreenHandler();
		int index = findVisibleRecipeIndex(recipe, sh, client);
		if (index < 0) {
			index = findSyncedRecipeIndex(recipe);
		}
		if (index >= 0) {
			client.gameMode.handleInventoryButtonClick(sh.containerId, index);
			if (context.getDestination() == EmiCraftContext.Destination.CURSOR) {
				client.gameMode.handleContainerInput(sh.containerId, 1, 0, ContainerInput.PICKUP, client.player);
			} else if (context.getDestination() == EmiCraftContext.Destination.INVENTORY) {
				client.gameMode.handleContainerInput(sh.containerId, 1, 0, ContainerInput.QUICK_MOVE, client.player);
			}
		}
		return action;
	}

	/**
	 * The button index the server expects is an index into the menu's own visible recipe list, which
	 * is the only stonecutter recipe ordering the client is actually told about. The entries only
	 * carry a display, not an id, so they are matched by their resolved output stack.
	 */
	private static int findVisibleRecipeIndex(EmiRecipe recipe, StonecutterMenu handler, Minecraft client) {
		if (client.level == null || recipe.getOutputs().isEmpty()) {
			return -1;
		}
		ItemStack wanted = recipe.getOutputs().get(0).getItemStack();
		if (wanted.isEmpty()) {
			return -1;
		}
		ContextMap context = SlotDisplayContext.fromLevel(client.level);
		List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries = handler.getVisibleRecipes().entries();
		int found = -1;
		for (int i = 0; i < entries.size(); i++) {
			ItemStack option = entries.get(i).recipe().optionDisplay().resolveForFirstStack(context);
			if (ItemStack.isSameItemSameComponents(option, wanted)) {
				if (found != -1) {
					// Ambiguous, fall back to the synced recipe map ordering
					return -1;
				}
				found = i;
			}
		}
		return found;
	}

	private static int findSyncedRecipeIndex(EmiRecipe recipe) {
		List<EmiStack> inputs = recipe.getInputs().isEmpty() ? List.of() : recipe.getInputs().get(0).getEmiStacks();
		if (inputs.isEmpty()) {
			return -1;
		}
		SingleRecipeInput inv = new SingleRecipeInput(inputs.get(0).getItemStack());
		List<StonecutterRecipe> recipes = ProxyRecipeManager.getMatches(RecipeType.STONECUTTING, inv);
		for (int i = 0; i < recipes.size(); i++) {
			Identifier id = ProxyRecipeManager.getId(recipes.get(i));
			if (id != null && id.equals(recipe.getId())) {
				return i;
			}
		}
		return -1;
	}
}
