package dev.emi.emi.recipe.special;

import java.util.List;
import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiPatternCraftingRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import dev.emi.emi.api.widget.SlotWidget;

/// Recipe handler for the vanilla {@code minecraft:tipped_arrow} ImbueRecipe.
///
/// In MC 26.1.2 the classic TippedArrowRecipe was replaced by ImbueRecipe, whose
/// output is wrapped in SlotDisplay.WithAnyPotion. EMI's static EmiCraftingRecipe
/// path loses that "any potion" semantic, resolving only the first variant.
///
/// This class mirrors EmiArmorDyeRecipe: a single recipe entry whose center input
/// (lingering potion) and output (8 tipped arrows) are GeneratedSlotWidgets that
/// share the same {@code unique} seed, so both slots cycle through potion variants
/// in lockstep.
///
/// Because LINGERING_POTION and TIPPED_ARROW use a potion-content-aware Comparison
/// (see VanillaPlugin), the static inputs/outputs must enumerate every potion
/// variant so that byInput/byOutput indices match stacks the player actually holds.
public class EmiTippedArrowRecipe extends EmiPatternCraftingRecipe {
	private static final int OUTPUT_COUNT = 8;

	private static final List<Potion> POTIONS =
		BuiltInRegistries.POTION.stream().toList();

	/// All lingering potion variants, used as the center input for indexing.
	private static final List<EmiStack> LINGERING_POTION_VARIANTS = POTIONS.stream()
		.map(potion -> {
			ItemStack stack = new ItemStack(Items.LINGERING_POTION);
			EmiPort.setPotion(stack, potion);
			return EmiStack.of(stack);
		})
		.toList();

	/// All tipped arrow variants (count 8), used as the output for indexing.
	private static final List<EmiStack> TIPPED_ARROW_VARIANTS = POTIONS.stream()
		.map(potion -> {
			ItemStack stack = new ItemStack(Items.TIPPED_ARROW, OUTPUT_COUNT);
			EmiPort.setPotion(stack, potion);
			return EmiStack.of(stack);
		})
		.toList();

	public EmiTippedArrowRecipe(Identifier id) {
		super(List.of(
			EmiStack.of(Items.ARROW), EmiStack.of(Items.ARROW), EmiStack.of(Items.ARROW),
			EmiStack.of(Items.ARROW), EmiIngredient.of(LINGERING_POTION_VARIANTS),
			EmiStack.of(Items.ARROW),
			EmiStack.of(Items.ARROW), EmiStack.of(Items.ARROW), EmiStack.of(Items.ARROW)
		), TIPPED_ARROW_VARIANTS.get(0), id, false);
	}

	@Override
	public List<EmiStack> getOutputs() {
		return TIPPED_ARROW_VARIANTS;
	}

	@Override
	public SlotWidget getInputWidget(int slot, int x, int y) {
		if (slot == 4) {
			return new GeneratedSlotWidget(r -> {
				ItemStack stack = new ItemStack(Items.LINGERING_POTION);
				EmiPort.setPotion(stack, getPotion(r));
				return EmiStack.of(stack);
			}, unique, x, y);
		}
		return new SlotWidget(EmiStack.of(Items.ARROW), x, y);
	}

	@Override
	public SlotWidget getOutputWidget(int x, int y) {
		return new GeneratedSlotWidget(r -> {
			ItemStack stack = new ItemStack(Items.TIPPED_ARROW, OUTPUT_COUNT);
			EmiPort.setPotion(stack, getPotion(r));
			return EmiStack.of(stack);
		}, unique, x, y);
	}

	private static Potion getPotion(Random random) {
		return POTIONS.get(random.nextInt(POTIONS.size()));
	}
}
