package dev.emi.emi.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.platform.EmiAgnos;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Abstraction over the source of recipes on the client.
 * <p>
 * Since 26.1 the client no longer owns a {@code RecipeManager}, the server instead sends the
 * client a {@link RecipeMap} which is captured per loader and exposed through
 * {@link EmiAgnos#getRecipeMap()}.
 */
public class ProxyRecipeManager {
	private static volatile Map<Recipe<?>, Identifier> recipeIds = Map.of();
	private static volatile Set<Identifier> bakedIds = Set.of();

	public static boolean isAvailable() {
		return getRaw() != null;
	}

	/**
	 * The map the server synchronized, or null if none has been received yet.
	 * <p>
	 * This is deliberately unconditional. It backs the public {@code EmiRegistry#getRecipeMap},
	 * which plugins call many times over a reload, and a map that flipped to null part-way through
	 * (because the level went away for a moment) would make a reload produce half a recipe set. The
	 * level is only needed where the map is actually queried against the world, see
	 * {@link #streamMatches}.
	 */
	public static @Nullable RecipeMap getRaw() {
		return EmiAgnos.getRecipeMap();
	}

	public static Identifier getId(Recipe<?> recipe) {
		return recipeIds.get(recipe);
	}

	public static boolean hasId(Identifier id) {
		return bakedIds.contains(id);
	}

	public static @Nullable RecipeHolder<?> getRecipeEntry(Identifier id) {
		RecipeMap raw = getRaw();
		if (raw == null || id == null) {
			return null;
		}
		return raw.byKey(ResourceKey.create(Registries.RECIPE, id));
	}

	public static <I extends RecipeInput, T extends Recipe<I>> Stream<T> streamMatches(RecipeType<T> type, I inventory) {
		RecipeMap raw = getRaw();
		// Matching runs Recipe#matches against the level, so it is only meaningful while a world is
		// loaded. This is the only place the level is needed; getRaw itself must stay valid.
		Level level = Minecraft.getInstance().level;
		if (raw == null || level == null) {
			return Stream.empty();
		}
		return raw.getRecipesFor(type, inventory, level).map(RecipeHolder::value);
	}

	public static <I extends RecipeInput, T extends Recipe<I>> List<T> getMatches(RecipeType<T> type, I inventory) {
		return streamMatches(type, inventory).toList();
	}

	public static <I extends RecipeInput, T extends Recipe<I>> @Nullable T getFirst(RecipeType<T> type, I inventory) {
		return streamMatches(type, inventory).findFirst().orElse(null);
	}

	public static void bakeIds() {
		RecipeMap raw = getRaw();
		if (raw == null) {
			recipeIds = Map.of();
			bakedIds = Set.of();
			return;
		}
		Map<Recipe<?>, Identifier> ids = new Reference2ObjectOpenHashMap<>();
		Set<Identifier> present = new ObjectOpenHashSet<>();
		for (RecipeHolder<?> entry : raw.values()) {
			Identifier id = entry.id().identifier();
			ids.put(entry.value(), id);
			present.add(id);
		}
		recipeIds = ids;
		bakedIds = present;
	}
}
