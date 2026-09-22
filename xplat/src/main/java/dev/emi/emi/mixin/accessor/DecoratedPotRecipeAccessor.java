package dev.emi.emi.mixin.accessor;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.DecoratedPotRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DecoratedPotRecipe.class)
public interface DecoratedPotRecipeAccessor {
	@Accessor("backPattern")
	Ingredient getBackPattern();

	@Accessor("leftPattern")
	Ingredient getLeftPattern();

	@Accessor("rightPattern")
	Ingredient getRightPattern();

	@Accessor("frontPattern")
	Ingredient getFrontPattern();

	@Accessor("result")
	ItemStackTemplate getResult();
}
