package dev.emi.emi.handler;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiLog;

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

	/** Warn once, not once per click, when the button index cannot be resolved. */
	private static boolean warnedAboutUnresolvedIndex = false;

	@Override
	public boolean craft(EmiRecipe recipe, EmiCraftContext<StonecutterMenu> context) {
		// The index the server expects describes the stack that is actually placed in the input
		// slot, which for a tag input is whichever matching item the player happens to have, not
		// the first stack of the displayed ingredient. Ask the filler for it before filling.
		List<ItemStack> stacks = EmiRecipeFiller.getStacks(this, recipe, context.getScreen(), context.getAmount());
		if (stacks == null || stacks.isEmpty() || stacks.get(0).isEmpty()) {
			return false;
		}
		ItemStack input = stacks.get(0);
		boolean action = StandardRecipeHandler.super.craft(recipe, context);
		Minecraft client = Minecraft.getInstance();
		StonecutterMenu sh = context.getScreenHandler();
		int index = findRecipeIndex(recipe, input, client);
		if (index >= 0) {
			client.gameMode.handleInventoryButtonClick(sh.containerId, index);
			if (context.getDestination() == EmiCraftContext.Destination.CURSOR) {
				client.gameMode.handleContainerInput(sh.containerId, 1, 0, ContainerInput.PICKUP, client.player);
			} else if (context.getDestination() == EmiCraftContext.Destination.INVENTORY) {
				client.gameMode.handleContainerInput(sh.containerId, 1, 0, ContainerInput.QUICK_MOVE, client.player);
			}
		} else if (action && !warnedAboutUnresolvedIndex) {
			warnedAboutUnresolvedIndex = true;
			EmiLog.warn("Could not resolve a stonecutter button index for the crafted recipe, so no"
				+ " recipe was selected. Further occurrences will not be logged.");
		}
		// The item was moved into the slot, but without a selection nothing is crafted, so this is
		// not a successful craft.
		return action && index >= 0;
	}

	/**
	 * The button index the server expects is an index into the list the menu rebuilds for the input
	 * it is holding: {@code StonecutterMenu#setupRecipeList} assigns
	 * {@code level.recipeAccess().stonecutterRecipes().selectByInput(input)} and
	 * {@code isValidRecipeIndex} bounds the click against that.
	 * <p>
	 * This recomputes that list the same way instead of reading {@code getVisibleRecipes()}: the
	 * client menu only refreshes its copy when {@code slotsChanged} runs, and the stack EMI just
	 * placed goes in through the server-side fill path, so the menu's list still describes the
	 * previous input at this point.
	 * <p>
	 * The entries carry a display rather than an id, so they are matched by their resolved output
	 * stack, and an ambiguous match clicks nothing rather than guessing.
	 * <p>
	 * {@code wantedInput} is the stack EMI is putting in the slot, taken from
	 * {@link dev.emi.emi.registry.EmiRecipeFiller#getStacks}, not the recipe's first displayed
	 * stack: for a tag input those differ and the list the server builds is per input item.
	 */
	private static int findRecipeIndex(EmiRecipe recipe, ItemStack wantedInput, Minecraft client) {
		Level level = client.level;
		if (level == null || recipe.getOutputs().isEmpty()) {
			return -1;
		}
		ItemStack wantedOutput = recipe.getOutputs().get(0).getItemStack();
		if (wantedInput.isEmpty() || wantedOutput.isEmpty()) {
			return -1;
		}
		List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries =
			level.recipeAccess().stonecutterRecipes().selectByInput(wantedInput).entries();
		ContextMap context = SlotDisplayContext.fromLevel(level);
		int found = -1;
		for (int i = 0; i < entries.size(); i++) {
			ItemStack option = entries.get(i).recipe().optionDisplay().resolveForFirstStack(context);
			if (ItemStack.isSameItemSameComponents(option, wantedOutput)) {
				if (found != -1) {
					// Two recipes produce the same stack from this input, so there is no way to
					// tell which one the displayed recipe is. Don't click.
					return -1;
				}
				found = i;
			}
		}
		return found;
	}
}
