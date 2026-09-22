package dev.emi.emi.mixin.accessor;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A transmute recipe resolves its output from the stack that matched its input ingredient and
 * from the number of stacks that matched its material ingredient, neither of which is reachable
 * through the public API, so EMI has to build a grid out of them to assemble the displayed output.
 */
@Mixin(TransmuteRecipe.class)
public interface TransmuteRecipeAccessor {

	@Accessor("input")
	Ingredient emi$getInput();

	@Accessor("material")
	Ingredient emi$getMaterial();
}
