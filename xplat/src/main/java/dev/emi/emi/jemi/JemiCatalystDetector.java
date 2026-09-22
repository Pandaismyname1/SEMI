package dev.emi.emi.jemi;

import java.util.Collections;
import java.util.Set;

import net.minecraft.world.item.crafting.RecipeHolder;

public class JemiCatalystDetector {
	private static boolean ae2Loaded;

	static {
		try {
			Class.forName("appeng.core.AppEng");
			ae2Loaded = true;
		} catch (ClassNotFoundException e) {
			ae2Loaded = false;
		}
	}

	public static Set<String> detectCatalystSlotNames(Object recipe) {
		if (!ae2Loaded) return Collections.emptySet();
		Object rawRecipe = recipe;
		if (rawRecipe instanceof RecipeHolder<?> holder) {
			rawRecipe = holder.value();
		}
		return detectAe2InscriberCatalystSlots(rawRecipe);
	}

	private static Set<String> detectAe2InscriberCatalystSlots(Object recipe) {
		if (!"appeng.recipes.handlers.InscriberRecipe".equals(recipe.getClass().getName())) {
			return Collections.emptySet();
		}
		try {
			Object processType = recipe.getClass().getMethod("getProcessType").invoke(recipe);
			if ("INSCRIBE".equals(processType.toString())) {
				return Set.of("top", "bottom");
			}
		} catch (Exception e) {}
		return Collections.emptySet();
	}
}
