package dev.emi.emi;

import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.ANVIL_REPAIRING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.BLASTING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.BREWING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.CAMPFIRE_COOKING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.COMPOSTING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.CRAFTING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.FUEL;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.GRINDING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.INFO;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.SMELTING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.SMITHING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.SMOKING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.STONECUTTING;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.WORLD_INTERACTION;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.BannerDuplicateRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.BookCloningRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.DecoratedPotRecipe;
import net.minecraft.world.item.crafting.FireworkRocketRecipe;
import net.minecraft.world.item.crafting.FireworkStarFadeRecipe;
import net.minecraft.world.item.crafting.FireworkStarRecipe;
import net.minecraft.world.item.crafting.ImbueRecipe;
import net.minecraft.world.item.crafting.MapExtendingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.DyeRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.ShieldDecorationRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.CombiningPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.MatchingBlockTagPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.MatchingBlocksPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.CopyPropertiesProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatLinkedOpenHashMap;
import com.google.common.collect.Sets;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeSorting;
import dev.emi.emi.api.recipe.EmiWorldInteractionRecipe;
import dev.emi.emi.api.render.EmiRenderable;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import dev.emi.emi.config.EffectLocation;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.FluidUnit;
import dev.emi.emi.data.ContextIntValues;
import dev.emi.emi.handler.CookingRecipeHandler;
import dev.emi.emi.handler.CraftingRecipeHandler;
import dev.emi.emi.handler.InventoryRecipeHandler;
import dev.emi.emi.handler.StonecuttingRecipeHandler;
import dev.emi.emi.mixin.accessor.CombiningPredicateAccessor;
import dev.emi.emi.mixin.accessor.DecoratedPotRecipeAccessor;
import dev.emi.emi.mixin.accessor.HandledScreenAccessor;
import dev.emi.emi.mixin.accessor.MatchingBlockTagPredicateAccessor;
import dev.emi.emi.mixin.accessor.MatchingBlocksPredicateAccessor;
import dev.emi.emi.mixin.accessor.StateTestingPredicateAccessor;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.platform.EmiClient;
import dev.emi.emi.recipe.EmiAnvilRecipe;
import dev.emi.emi.recipe.EmiCompostingRecipe;
import dev.emi.emi.recipe.EmiCookingRecipe;
import dev.emi.emi.recipe.EmiFuelRecipe;
import dev.emi.emi.recipe.EmiGrindstoneRecipe;
import dev.emi.emi.recipe.EmiShapedRecipe;
import dev.emi.emi.recipe.EmiShapelessRecipe;
import dev.emi.emi.recipe.EmiSmithingRecipe;
import dev.emi.emi.recipe.EmiStonecuttingRecipe;
import dev.emi.emi.recipe.EmiTagRecipe;
import dev.emi.emi.recipe.special.EmiAnvilEnchantRecipe;
import dev.emi.emi.recipe.special.EmiAnvilRepairItemRecipe;
import dev.emi.emi.recipe.special.EmiArmorDyeRecipe;
import dev.emi.emi.recipe.special.EmiBannerDuplicateRecipe;
import dev.emi.emi.recipe.special.EmiBannerShieldRecipe;
import dev.emi.emi.recipe.special.EmiBookCloningRecipe;
import dev.emi.emi.recipe.special.EmiFireworkRocketRecipe;
import dev.emi.emi.recipe.special.EmiFireworkStarFadeRecipe;
import dev.emi.emi.recipe.special.EmiFireworkStarRecipe;
import dev.emi.emi.recipe.special.EmiGrindstoneDisenchantingBookRecipe;
import dev.emi.emi.recipe.special.EmiGrindstoneDisenchantingRecipe;
import dev.emi.emi.recipe.special.EmiRepairItemRecipe;
import dev.emi.emi.recipe.special.EmiSmithingTrimRecipe;
import dev.emi.emi.recipe.special.EmiTippedArrowRecipe;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadLog;
import dev.emi.emi.runtime.EmiTagKey;
import dev.emi.emi.runtime.ProxyRecipeManager;
import dev.emi.emi.stack.serializer.FluidEmiStackSerializer;
import dev.emi.emi.stack.serializer.ItemEmiStackSerializer;
import dev.emi.emi.stack.serializer.ListEmiIngredientSerializer;
import dev.emi.emi.stack.serializer.TagEmiIngredientSerializer;

@EmiEntrypoint
public class VanillaPlugin implements EmiPlugin {
	public static EmiRecipeCategory TAG = new EmiRecipeCategory(EmiPort.id("emi:tag"),
		EmiStack.of(Items.NAME_TAG), simplifiedRenderer(240, 208), EmiRecipeSorting.none());
	
	public static EmiRecipeCategory INGREDIENT = new EmiRecipeCategory(EmiPort.id("emi:ingredient"),
		EmiStack.of(Items.COMPASS), simplifiedRenderer(240, 208));
	public static EmiRecipeCategory RESOLUTION = new EmiRecipeCategory(EmiPort.id("emi:resolution"),
		EmiStack.of(Items.COMPASS), simplifiedRenderer(240, 208));

	static {
		CRAFTING = new EmiRecipeCategory(EmiPort.id("minecraft:crafting"),
			EmiStack.of(Items.CRAFTING_TABLE), simplifiedRenderer(240, 240), EmiRecipeSorting.compareOutputThenInput());
		SMELTING = new EmiRecipeCategory(EmiPort.id("minecraft:smelting"),
			EmiStack.of(Items.FURNACE), simplifiedRenderer(224, 240), EmiRecipeSorting.compareOutputThenInput());
		BLASTING = new EmiRecipeCategory(EmiPort.id("minecraft:blasting"),
			EmiStack.of(Items.BLAST_FURNACE), simplifiedRenderer(208, 240), EmiRecipeSorting.compareOutputThenInput());
		SMOKING = new EmiRecipeCategory(EmiPort.id("minecraft:smoking"),
			EmiStack.of(Items.SMOKER), simplifiedRenderer(192, 240), EmiRecipeSorting.compareOutputThenInput());
		CAMPFIRE_COOKING = new EmiRecipeCategory(EmiPort.id("minecraft:campfire_cooking"),
			EmiStack.of(Items.CAMPFIRE), simplifiedRenderer(176, 240), EmiRecipeSorting.compareOutputThenInput());
		STONECUTTING = new EmiRecipeCategory(EmiPort.id("minecraft:stonecutting"),
			EmiStack.of(Items.STONECUTTER), simplifiedRenderer(160, 240), EmiRecipeSorting.compareInputThenOutput());
		SMITHING = new EmiRecipeCategory(EmiPort.id("minecraft:smithing"),
			EmiStack.of(Items.SMITHING_TABLE), simplifiedRenderer(240, 224), EmiRecipeSorting.compareInputThenOutput());
		ANVIL_REPAIRING = new EmiRecipeCategory(EmiPort.id("emi:anvil_repairing"),
			EmiStack.of(Items.ANVIL), simplifiedRenderer(240, 224), EmiRecipeSorting.none());
		GRINDING = new EmiRecipeCategory(EmiPort.id("emi:grinding"),
			EmiStack.of(Items.GRINDSTONE), simplifiedRenderer(192, 224), EmiRecipeSorting.none());
		BREWING = new EmiRecipeCategory(EmiPort.id("minecraft:brewing"),
			EmiStack.of(Items.BREWING_STAND), simplifiedRenderer(224, 224), EmiRecipeSorting.none());
		WORLD_INTERACTION = new EmiRecipeCategory(EmiPort.id("emi:world_interaction"),
			EmiStack.of(Items.GRASS_BLOCK), simplifiedRenderer(208, 224), EmiRecipeSorting.none());
		EmiRenderable flame = (matrices, x, y, delta) -> {
			EmiTexture.FULL_FLAME.render(matrices, x + 1, y + 1, delta);
		};
		FUEL = new EmiRecipeCategory(EmiPort.id("emi:fuel"), flame, flame, EmiRecipeSorting.compareInputThenOutput());
		COMPOSTING = new EmiRecipeCategory(EmiPort.id("emi:composting"), EmiStack.of(Items.COMPOSTER),
			EmiStack.of(Items.COMPOSTER), EmiRecipeSorting.compareInputThenOutput());
		INFO = new EmiRecipeCategory(EmiPort.id("emi:info"),
			EmiStack.of(Items.WRITABLE_BOOK), simplifiedRenderer(208, 224), EmiRecipeSorting.none());
	}


	@Override
	public void initialize(EmiInitRegistry registry) {
		registry.addIngredientSerializer(ItemEmiStack.class, new ItemEmiStackSerializer());
		registry.addIngredientSerializer(FluidEmiStack.class, new FluidEmiStackSerializer());
		registry.addIngredientSerializer(TagEmiIngredient.class, new TagEmiIngredientSerializer());
		registry.addIngredientSerializer(ListEmiIngredient.class, new ListEmiIngredientSerializer());

		registry.addRegistryAdapter(EmiRegistryAdapter.simple(Item.class, EmiPort.getItemRegistry(), EmiStack::of));
		registry.addRegistryAdapter(EmiRegistryAdapter.simple(Fluid.class, EmiPort.getFluidRegistry(), EmiStack::of));
	}

	@Override
	public void register(EmiRegistry registry) {
		RecipeMap recipeMap = registry.getRecipeMap();
		// Both loaders now always install a map, falling back to RecipeMap.EMPTY on a server that
		// does not synchronize, so "no recipes" means an empty map rather than a null one.
		if (recipeMap == null || recipeMap.values().isEmpty()) {
			EmiReloadLog.warn("No recipes were synchronized from the server, skipping every vanilla recipe type");
		}
		registry.addCategory(CRAFTING);
		registry.addCategory(SMELTING);
		registry.addCategory(BLASTING);
		registry.addCategory(SMOKING);
		registry.addCategory(CAMPFIRE_COOKING);
		registry.addCategory(STONECUTTING);
		registry.addCategory(SMITHING);
		registry.addCategory(ANVIL_REPAIRING);
		registry.addCategory(GRINDING);
		registry.addCategory(BREWING);
		registry.addCategory(WORLD_INTERACTION);
		registry.addCategory(FUEL);
		registry.addCategory(COMPOSTING);
		registry.addCategory(INFO);
		registry.addCategory(TAG);
		registry.addCategory(INGREDIENT);
		registry.addCategory(RESOLUTION);

		registry.addWorkstation(CRAFTING, EmiStack.of(Items.CRAFTING_TABLE));
		registry.addWorkstation(SMELTING, EmiStack.of(Items.FURNACE));
		registry.addWorkstation(BLASTING, EmiStack.of(Items.BLAST_FURNACE));
		registry.addWorkstation(SMOKING, EmiStack.of(Items.SMOKER));
		registry.addWorkstation(CAMPFIRE_COOKING, EmiStack.of(Items.CAMPFIRE));
		registry.addWorkstation(CAMPFIRE_COOKING, EmiStack.of(Items.SOUL_CAMPFIRE));
		registry.addWorkstation(STONECUTTING, EmiStack.of(Items.STONECUTTER));
		registry.addWorkstation(SMITHING, EmiStack.of(Items.SMITHING_TABLE));
		registry.addWorkstation(ANVIL_REPAIRING, EmiStack.of(Items.ANVIL));
		registry.addWorkstation(ANVIL_REPAIRING, EmiStack.of(Items.CHIPPED_ANVIL));
		registry.addWorkstation(ANVIL_REPAIRING, EmiStack.of(Items.DAMAGED_ANVIL));
		registry.addWorkstation(BREWING, EmiStack.of(Items.BREWING_STAND));
		registry.addWorkstation(GRINDING, EmiStack.of(Items.GRINDSTONE));
		registry.addWorkstation(COMPOSTING, EmiStack.of(Items.COMPOSTER));

		registry.addRecipeHandler(null, new InventoryRecipeHandler());
		registry.addRecipeHandler(MenuType.CRAFTING, new CraftingRecipeHandler());
		registry.addRecipeHandler(MenuType.FURNACE, new CookingRecipeHandler<FurnaceMenu>(SMELTING));
		registry.addRecipeHandler(MenuType.BLAST_FURNACE, new CookingRecipeHandler<BlastFurnaceMenu>(BLASTING));
		registry.addRecipeHandler(MenuType.SMOKER, new CookingRecipeHandler<SmokerMenu>(SMOKING));
		registry.addRecipeHandler(MenuType.STONECUTTER, new StonecuttingRecipeHandler());

		registry.addExclusionArea(CreativeModeInventoryScreen.class, (screen, consumer) -> {
			int left = ((HandledScreenAccessor) screen).getX();
			int top = ((HandledScreenAccessor) screen).getY();
			int width = ((HandledScreenAccessor) screen).getBackgroundWidth();
			int bottom = top + ((HandledScreenAccessor) screen).getBackgroundHeight();
			consumer.accept(new Bounds(left, top - 28, width, 28));
			consumer.accept(new Bounds(left, bottom, width, 28));
		});

		registry.addGenericExclusionArea((screen, consumer) -> {
			// 26.1 has no EffectRenderingInventoryScreen base class; screens compose an
			// EffectsInInventory and expose it through Screen#showsActiveEffects, which is the
			// widest check that still covers modded screens (and folds in vanilla's own
			// canSeeEffects room check)
			if (EmiConfig.effectLocation != EffectLocation.HIDDEN && screen instanceof AbstractContainerScreen<?> && screen.showsActiveEffects()) {
			Minecraft client = Minecraft.getInstance();
			Collection<MobEffectInstance> collection = client.player.getActiveEffects();
			if (!collection.isEmpty()) {
				AbstractContainerScreen<?> inv = (AbstractContainerScreen<?>) screen;
					int k = 33;
					if (collection.size() > 5) {
						k = 132 / (collection.size() - 1);
					}
					int right = ((HandledScreenAccessor) inv).getX() + ((HandledScreenAccessor) inv).getBackgroundWidth() + 2;
					int rightWidth = inv.width - right;
					if (rightWidth >= 32) {
						int top = ((HandledScreenAccessor) inv).getY();
						int height = (collection.size() - 1) * k + 32;
						int left, width;
						if (EmiConfig.effectLocation == EffectLocation.TOP) {
							int size = collection.size();
							top = ((HandledScreenAccessor) inv).getY() - 34;
							if (((Object) screen) instanceof CreativeModeInventoryScreen) {
								top -= 28;
								if (EmiAgnos.isForge()) {
									top -= 22;
								}
							}
							int xOff = 34;
							if (size == 1) {
								xOff = 122;
							} else if (size > 5) {
								xOff = (((HandledScreenAccessor) inv).getBackgroundWidth() - 32) / (size - 1);
							}
							width = Math.max(122, (size - 1) * xOff + 32);
							left = ((HandledScreenAccessor) inv).getX() + (((HandledScreenAccessor) inv).getBackgroundWidth() - width) / 2;
							height = 32;
						} else {
							left = switch (EmiConfig.effectLocation) {
								case LEFT_COMPRESSED -> ((HandledScreenAccessor) inv).getX() - 2 - 32;
								case LEFT -> ((HandledScreenAccessor) inv).getX() - 2 - 120;
								default -> right;
							};
							width = switch (EmiConfig.effectLocation) {
								case LEFT, RIGHT -> 120;
								case LEFT_COMPRESSED, RIGHT_COMPRESSED -> 32;
								default -> 32;
							};
						}
						consumer.accept(new Bounds(left, top, width, height));
					}
				}
			}
		});

		Comparison potionComparison = Comparison.compareData(stack -> stack.get(DataComponents.POTION_CONTENTS));

		registry.setDefaultComparison(Items.POTION, potionComparison);
		registry.setDefaultComparison(Items.SPLASH_POTION, potionComparison);
		registry.setDefaultComparison(Items.LINGERING_POTION, potionComparison);
		registry.setDefaultComparison(Items.TIPPED_ARROW, potionComparison);
		registry.setDefaultComparison(Items.ENCHANTED_BOOK, EmiPort.compareStrict());

		Set<Item> hiddenItems = Stream.concat(
			EmiTagKey.of(EmiPort.getItemRegistry(), EmiTags.HIDDEN_FROM_RECIPE_VIEWERS).stream(),
			EmiPort.getDisabledItems()
		).collect(Collectors.toSet());

		// 26.1 replaced ItemTags.DYEABLE with ItemTags.CAULDRON_CAN_REMOVE_DYE, which holds the
		// same set of dyeable items (leather armor, leather horse armor and wolf armor).
		List<Item> dyeableItems = EmiTagKey.of(ItemTags.CAULDRON_CAN_REMOVE_DYE).getList();

		Set<Class<?>> processedCustomRecipes = Sets.newHashSet();
		for (CraftingRecipe recipe : getRecipes(registry, RecipeType.CRAFTING)) {
			Identifier id = ProxyRecipeManager.getId(recipe);
			if (recipe instanceof MapExtendingRecipe map) {
				EmiStack paper = EmiStack.of(Items.PAPER);
				addRecipeSafe(registry, () -> new EmiCraftingRecipe(List.of(
						paper, paper, paper, paper,
						EmiStack.of(Items.FILLED_MAP),
						paper, paper, paper, paper
				), 
						EmiStack.of(Items.FILLED_MAP),
						id, false), recipe);
			} else if (recipe instanceof ShapedRecipe shaped && shaped.getWidth() <= 3 && shaped.getHeight() <= 3) {
				addRecipeSafe(registry, () -> new EmiShapedRecipe(shaped), recipe);
			} else if (recipe instanceof ShapelessRecipe shapeless) {
				addRecipeSafe(registry, () -> new EmiShapelessRecipe(shapeless), recipe);
			} else if (recipe instanceof DyeRecipe dye) {
				if (processedCustomRecipes.add(DyeRecipe.class)) {
					for (Item i : dyeableItems) {
						if (!hiddenItems.contains(i)) {
							addRecipeSafe(registry, () -> new EmiArmorDyeRecipe(i, synthetic("crafting/dying", EmiUtil.subId(i))), recipe);
						}
					}
				}
			} else if (recipe instanceof ShieldDecorationRecipe shield) {
				addRecipeSafe(registry, () -> new EmiBannerShieldRecipe(id), recipe);
			} else if (recipe instanceof BookCloningRecipe book) {
				addRecipeSafe(registry, () -> new EmiBookCloningRecipe(id), recipe);
			} else if (recipe instanceof FireworkStarRecipe star) {
				addRecipeSafe(registry, () -> new EmiFireworkStarRecipe(id), recipe);
			} else if (recipe instanceof FireworkStarFadeRecipe star) {
				addRecipeSafe(registry, () -> new EmiFireworkStarFadeRecipe(id), recipe);
			} else if (recipe instanceof FireworkRocketRecipe rocket) {
				addRecipeSafe(registry, () -> new EmiFireworkRocketRecipe(id), recipe);
			} else if (recipe instanceof BannerDuplicateRecipe banner) {
				if (processedCustomRecipes.add(BannerDuplicateRecipe.class)) {
					for (Item i : EmiBannerDuplicateRecipe.BANNERS) {
						if (!hiddenItems.contains(i)) {
							addRecipeSafe(registry, () -> new EmiBannerDuplicateRecipe(i, synthetic("crafting/banner_copying", EmiUtil.subId(i))), recipe);
						}
					}
				}
			} else if (recipe instanceof RepairItemRecipe tool) {
				if (processedCustomRecipes.add(RepairItemRecipe.class)) {
					for (Item i : EmiRepairItemRecipe.TOOLS) {
						if (!hiddenItems.contains(i)) {
							addRecipeSafe(registry, () -> new EmiRepairItemRecipe(i, synthetic("crafting/repairing", EmiUtil.subId(i))), recipe);
						}
					}
				}
			} else if (recipe instanceof DecoratedPotRecipe pot) {
				DecoratedPotRecipeAccessor acc = (DecoratedPotRecipeAccessor) pot;
				addRecipeSafe(registry, () -> new EmiCraftingRecipe(List.of(
					EmiStack.EMPTY, EmiIngredient.of(acc.getBackPattern()), EmiStack.EMPTY,
					EmiIngredient.of(acc.getLeftPattern()), EmiStack.EMPTY, EmiIngredient.of(acc.getRightPattern()),
					EmiStack.EMPTY, EmiIngredient.of(acc.getFrontPattern()), EmiStack.EMPTY
				), EmiStack.of(acc.getResult().create()), id, false), recipe);
			} else if (recipe instanceof ImbueRecipe imbue) {
				if (id.equals(EmiPort.id("minecraft", "tipped_arrow"))) {
					addRecipeSafe(registry, () -> new EmiTippedArrowRecipe(id), recipe);
				} else {
					List<Ingredient> ingredients = imbue.placementInfo().ingredients();
					List<EmiIngredient> input = ingredients.stream().map(EmiIngredient::of).toList();
					EmiShapedRecipe.setRemainders(input, imbue);
					addRecipeSafe(registry, () -> new EmiCraftingRecipe(input, EmiStack.of(EmiPort.getOutput(imbue)), id, false), recipe);
				}
			} else if (recipe instanceof TransmuteRecipe transmute) {
				// 26.1 turned the old MapCloningRecipe into a crafting_transmute recipe, and the old
				// SuspiciousStewRecipe into one crafting_shapeless recipe per flower, so both are
				// covered by this branch and the ShapelessRecipe branch above.
				List<Ingredient> ingredients = transmute.placementInfo().ingredients();
				List<EmiIngredient> input = ingredients.stream().map(EmiIngredient::of).toList();
				EmiShapedRecipe.setRemainders(input, transmute);
				addRecipeSafe(registry, () -> new EmiCraftingRecipe(input, EmiStack.of(EmiPort.getOutput(transmute)), id, true), recipe);
			} else if (!(recipe instanceof CustomRecipe)) {
				try {
					// Upstream used Recipe#fits here, which 26.1 removed. The only shape information
					// left on an arbitrary crafting recipe is PlacementInfo, a flat, position-less
					// ingredient list, so anything that isn't a ShapedRecipe is laid out (and marked)
					// the way a shapeless recipe is. Shaped recipes small enough to display were
					// already handled above, so any that reach here do not fit a 3x3 grid.
					List<Ingredient> ingredients = recipe.placementInfo().ingredients();
					if (recipe instanceof ShapedRecipe || ingredients.size() > 9) {
						continue;
					}
					if (!ingredients.isEmpty() && !EmiPort.getOutput(recipe).isEmpty()) {
						List<EmiIngredient> input = ingredients.stream().map(EmiIngredient::of).toList();
						EmiShapedRecipe.setRemainders(input, recipe);
						addRecipeSafe(registry, () -> new EmiCraftingRecipe(input, EmiStack.of(EmiPort.getOutput(recipe)), id, true));
					}
				} catch (Exception e) {
					EmiReloadLog.warn("Exception when parsing vanilla crafting recipe " + id, e);
				}
			}
		}

		for (SmeltingRecipe recipe : getRecipes(registry, RecipeType.SMELTING)) {
			addRecipeSafe(registry, () -> new EmiCookingRecipe(recipe, SMELTING, 1, false), recipe);
		}
		for (BlastingRecipe recipe : getRecipes(registry, RecipeType.BLASTING)) {
			addRecipeSafe(registry, () -> new EmiCookingRecipe(recipe, BLASTING, 2, false), recipe);
		}
		for (SmokingRecipe recipe : getRecipes(registry, RecipeType.SMOKING)) {
			addRecipeSafe(registry, () -> new EmiCookingRecipe(recipe, SMOKING, 2, false), recipe);
		}
		for (CampfireCookingRecipe recipe : getRecipes(registry, RecipeType.CAMPFIRE_COOKING)) {
			addRecipeSafe(registry, () -> new EmiCookingRecipe(recipe, CAMPFIRE_COOKING, 1, true), recipe);
		}
		for (SmithingRecipe recipe : getRecipes(registry, RecipeType.SMITHING)) {
			if (recipe instanceof SmithingTransformRecipe str) {
				addRecipeSafe(registry, () -> new EmiSmithingRecipe(
					str.templateIngredient().map(EmiIngredient::of).orElse(EmiStack.EMPTY),
					EmiIngredient.of(str.baseIngredient()),
					str.additionIngredient().map(EmiIngredient::of).orElse(EmiStack.EMPTY),
					EmiStack.of(EmiPort.getOutput(recipe)), ProxyRecipeManager.getId(recipe)), recipe);
			} else if (recipe instanceof SmithingTrimRecipe str) {
				addRecipeSafe(registry, () -> new EmiSmithingTrimRecipe(
					str.templateIngredient().map(EmiIngredient::of).orElse(EmiStack.EMPTY),
					EmiIngredient.of(str.baseIngredient()),
					str.additionIngredient().map(EmiIngredient::of).orElse(EmiStack.EMPTY),
					EmiStack.of(EmiPort.getOutput(recipe)), recipe), recipe);
			} else {
				addRecipeSafe(registry, () -> new EmiSmithingRecipe(
					recipe.templateIngredient().map(EmiIngredient::of).orElse(EmiStack.EMPTY),
					EmiIngredient.of(recipe.baseIngredient()),
					recipe.additionIngredient().map(EmiIngredient::of).orElse(EmiStack.EMPTY),
					EmiStack.of(EmiPort.getOutput(recipe)), ProxyRecipeManager.getId(recipe)), recipe);
			}
		}
		for (StonecutterRecipe recipe : getRecipes(registry, RecipeType.STONECUTTING)) {
			addRecipeSafe(registry, () -> new EmiStonecuttingRecipe(recipe), recipe);
		}

		safely("repair", () -> addRepair(registry, hiddenItems));
		safely("brewing", () -> EmiAgnos.addBrewingRecipes(registry));
		safely("world interaction", () -> addWorldInteraction(registry, hiddenItems, dyeableItems));
		safely("fuel", () -> addFuel(registry, hiddenItems));
		safely("composting", () -> addComposting(registry, hiddenItems));

		for (EmiTagKey<?> key : EmiTags.TAGS) {
			if (new TagEmiIngredient(key.raw(), 1).getEmiStacks().size() > 1) {
				addRecipeSafe(registry, () -> new EmiTagRecipe(key.raw()));
			}
		}
	}

	private static void addRepair(EmiRegistry registry, Set<Item> hiddenItems) {
		List<Enchantment> targetedEnchantments = Lists.newArrayList();
		List<Enchantment> universalEnchantments = Lists.newArrayList();
		for (Enchantment enchantment : EmiPort.getEnchantmentRegistry().stream().toList()) {
			try {
				if (enchantment.canEnchant(ItemStack.EMPTY)) {
					universalEnchantments.add(enchantment);
					continue;
				}
			} catch (Throwable t) {
			}
			targetedEnchantments.add(enchantment);
		}
		for (Item i : EmiPort.getItemRegistry()) {
			if (hiddenItems.contains(i)) {
				continue;
			}
			try {
				if (i.components().getOrDefault(DataComponents.MAX_DAMAGE, 0) > 0) {
					ItemStack defaultStack = i.getDefaultInstance();
					Repairable repairable = defaultStack.get(DataComponents.REPAIRABLE);
					if (repairable != null && i != Items.ELYTRA && i != Items.SHIELD) {
						EmiIngredient repairIngredient = EmiIngredient.of(repairable.items().stream()
							.map(h -> EmiStack.of(h.value())).toList());
						if (!repairIngredient.isEmpty()) {
							Item firstItem = repairable.items().iterator().next().value();
							Identifier id = synthetic("anvil/repairing/material", EmiUtil.subId(i) + "/" + EmiUtil.subId(firstItem));
							addRecipeSafe(registry, () -> new EmiAnvilRecipe(EmiStack.of(i), repairIngredient, id));
						}
					}
				}
				// TODO used to be isDamageable, the 20.1 impl of that method appears to just be maxDamage > 0 though
				if (i.components().getOrDefault(DataComponents.MAX_DAMAGE, 0) > 0) {
					addRecipeSafe(registry, () -> new EmiAnvilRepairItemRecipe(i, synthetic("anvil/repairing/tool", EmiUtil.subId(i))));
					addRecipeSafe(registry, () -> new EmiGrindstoneRecipe(i, synthetic("grindstone/repairing", EmiUtil.subId(i))));
				}
			} catch (Throwable t) {
				EmiLog.error("Exception thrown registering repair recipes", t);
			}
			try {
				ItemStack defaultStack = i.getDefaultInstance();
				int acceptableEnchantments = 0;
				Consumer<Enchantment> consumer = e -> {
					int max = e.getMaxLevel();
					addRecipeSafe(registry, () -> new EmiAnvilEnchantRecipe(i, e, max,
						synthetic("anvil/enchanting", EmiUtil.subId(i) + "/" + EmiUtil.subId(EmiPort.getEnchantmentRegistry().getKey(e)) + "/" + max)));
				};
				for (Enchantment e : targetedEnchantments) {
					if (e.canEnchant(defaultStack) && defaultStack.isEnchantable()
						&& EmiAgnos.isEnchantable(defaultStack, e)) {
						consumer.accept(e);
						acceptableEnchantments++;
					}
				}
				if (acceptableEnchantments > 0) {
					for (Enchantment e : universalEnchantments) {
						if (e.canEnchant(defaultStack)) {
							consumer.accept(e);
							acceptableEnchantments++;
						}
					}
					addRecipeSafe(registry, () -> new EmiGrindstoneDisenchantingRecipe(i, synthetic("grindstone/disenchanting/tool", EmiUtil.subId(i))));
				}
			} catch (Throwable t) {
				EmiReloadLog.warn("Exception thrown registering enchantment recipes", t);
			}
			if (i instanceof BlockItem bi && bi.getBlock() instanceof TallFlowerBlock tf && EmiPort.canTallFlowerDuplicate(tf)) {
				addRecipeSafe(registry, () -> basicWorld(EmiStack.of(bi).setRemainder(EmiStack.of(bi)), EmiStack.of(Items.BONE_MEAL), EmiStack.of(i),
						synthetic("world/flower_duping", EmiUtil.subId(i)), false));
			}
		}
		addRecipeSafe(registry, () -> new EmiAnvilRecipe(EmiStack.of(Items.ELYTRA), EmiStack.of(Items.PHANTOM_MEMBRANE),
			synthetic("anvil/repairing/material", EmiUtil.subId(Items.ELYTRA) + "/" + EmiUtil.subId(Items.PHANTOM_MEMBRANE))));
		addRecipeSafe(registry, () -> new EmiAnvilRecipe(EmiStack.of(Items.SHIELD), EmiIngredient.of(ItemTags.PLANKS),
			synthetic("anvil/repairing/material", EmiUtil.subId(Items.SHIELD) + "/" + EmiUtil.subId(Items.OAK_PLANKS))));

		for (Enchantment e : EmiPort.getEnchantmentRegistry().stream().toList()) {
			if (!EmiPort.getEnchantmentRegistry().wrapAsHolder(e).is(EnchantmentTags.CURSE)) {
				int max = Math.min(10, e.getMaxLevel());
				int min = e.getMinLevel();
				while (min <= max) {
					int level = min;
					addRecipeSafe(registry, () -> new EmiGrindstoneDisenchantingBookRecipe(e, level,
						synthetic("grindstone/disenchanting/book", EmiUtil.subId(EmiPort.getEnchantmentRegistry().getKey(e)) + "/" + level)));
					min++;
				}
			}
		}
	}

	private static void addWorldInteraction(EmiRegistry registry, Set<Item> hiddenItems, List<Item> dyeableItems) {
		EmiStack concreteWater = EmiStack.of(Fluids.WATER);
		concreteWater.setRemainder(concreteWater);
		List<Block> concretes = Blocks.CONCRETE.asList();
		List<Block> concretePowders = Blocks.CONCRETE_POWDER.asList();
		for (int i = 0; i < concretes.size(); i++) {
			addConcreteRecipe(registry, concretePowders.get(i), concreteWater, concretes.get(i));
		}

		EmiIngredient axes = damagedTool(getPreferredTag(List.of(
				"minecraft:axes", "c:axes", "c:tools/axes", "fabric:axes", "forge:tools/axes"
			), EmiStack.of(Items.IRON_AXE)), 1);
		// Stripping, de-waxing and de-oxidising all live in the axe block transformer as of 26.3.
		addBlockTransforms(registry, axes, BlockTransformers.AXE, "world/stripping");
		
		EmiIngredient shears = damagedTool(EmiStack.of(Items.SHEARS), 1);
		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/shearing", "minecraft/pumpkin"))
			.leftInput(EmiStack.of(Items.PUMPKIN))
			.rightInput(shears, true)
			.output(EmiStack.of(Items.PUMPKIN_SEEDS, 4))
			.output(EmiStack.of(Items.CARVED_PUMPKIN))
			.build());
		EmiIngredient hoes = damagedTool(getPreferredTag(List.of(
				"minecraft:hoes", "c:hoes", "c:tools/hoes", "fabric:hoes", "forge:tools/hoes"
			), EmiStack.of(Items.IRON_HOE)), 1);
		addBlockTransforms(registry, hoes, BlockTransformers.HOE, "world/tilling");

		EmiIngredient shovels = damagedTool(getPreferredTag(List.of(
				"minecraft:shovels", "c:shovels", "c:tools/shovels", "fabric:shovels", "forge:tools/shovels"
			), EmiStack.of(Items.IRON_SHOVEL)), 1);
		addBlockTransforms(registry, shovels, BlockTransformers.SHOVEL, "world/flattening");

		EmiIngredient honeycomb = EmiStack.of(Items.HONEYCOMB);
		for (Map.Entry<Block, Block> entry : HoneycombItem.WAXABLES.get().entrySet()) {
			Identifier id = synthetic("world/waxing", EmiUtil.subId(entry.getKey()));
			addRecipeSafe(registry, () -> basicWorld(EmiStack.of(entry.getKey()), honeycomb, EmiStack.of(entry.getValue()), id, false));
		}

		for (Item i : dyeableItems) {
			if (hiddenItems.contains(i)) {
				continue;
			}
			EmiStack cauldron = EmiStack.of(Items.CAULDRON);
			EmiStack waterThird = EmiStack.of(Fluids.WATER, FluidUnit.BOTTLE);
			int uniq = EmiUtil.RANDOM.nextInt();
			addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
				.id(synthetic("world/cauldron_washing", EmiUtil.subId(i)))
				.leftInput(EmiStack.EMPTY, s -> new GeneratedSlotWidget(r -> {
					ItemStack stack = i.getDefaultInstance();
					stack.set(DataComponents.DYED_COLOR, new DyedItemColor(r.nextInt(0xFFFFFF + 1)));
					return EmiStack.of(stack);
				}, uniq, s.getBounds().x(), s.getBounds().y()))
				.rightInput(cauldron, true)
				.rightInput(waterThird, false)
				.output(EmiStack.of(i))
				.supportsRecipeTree(false)
				.build());
		}

		EmiStack water = EmiStack.of(Fluids.WATER, FluidUnit.BUCKET);
		EmiStack lava = EmiStack.of(Fluids.LAVA, FluidUnit.BUCKET);
		EmiStack waterCatalyst = water.copy().setRemainder(water);
		EmiStack lavaCatalyst = lava.copy().setRemainder(lava);

		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/fluid_spring", "minecraft/water"))
			.leftInput(waterCatalyst)
			.rightInput(waterCatalyst, false)
			.output(EmiStack.of(Fluids.WATER, FluidUnit.BUCKET))
			.build());
		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/fluid_interaction", "minecraft/cobblestone"))
			.leftInput(waterCatalyst)
			.rightInput(lavaCatalyst, false)
			.output(EmiStack.of(Items.COBBLESTONE))
			.build());
		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/fluid_interaction", "minecraft/stone"))
			.leftInput(waterCatalyst)
			.rightInput(lavaCatalyst, false)
			.output(EmiStack.of(Items.STONE))
			.build());
		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/fluid_interaction", "minecraft/obsidian"))
			.leftInput(lava)
			.rightInput(waterCatalyst, false)
			.output(EmiStack.of(Items.OBSIDIAN))
			.build());
	
		EmiStack soulSoil = EmiStack.of(Items.SOUL_SOIL);
		soulSoil.setRemainder(soulSoil);
		EmiStack blueIce = EmiStack.of(Items.BLUE_ICE);
		blueIce.setRemainder(blueIce);

		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/fluid_interaction", "minecraft/basalt"))
			.leftInput(lavaCatalyst)
			.rightInput(soulSoil, false, s -> s.appendTooltip(EmiPort
				.translatable("tooltip.emi.fluid_interaction.basalt.soul_soil", ChatFormatting.GREEN)))
			.rightInput(blueIce, false, s -> s.appendTooltip(EmiPort
				.translatable("tooltip.emi.fluid_interaction.basalt.blue_ice", ChatFormatting.GREEN)))
			.output(EmiStack.of(Items.BASALT))
			.build());

		EmiPort.getFluidRegistry().listElements().forEach(entry -> {
			Fluid fluid = entry.value();
			Item bucket = fluid.getBucket();
			if (fluid.isSource(fluid.defaultFluidState()) && !fluid.defaultFluidState().createLegacyBlock().isAir() && bucket != Items.AIR && fluid instanceof FlowingFluid) {
				addRecipeSafe(registry, () -> basicWorld(EmiStack.of(Items.BUCKET), EmiStack.of(fluid, FluidUnit.BUCKET), EmiStack.of(bucket),
					synthetic("emi", "bucket_filling/" + EmiUtil.subId(fluid)), false));
			}
		});

		addRecipeSafe(registry, () -> basicWorld(EmiStack.of(Items.GLASS_BOTTLE), water,
			EmiStack.of(EmiPort.setPotion(new ItemStack(Items.POTION), Potions.WATER.value())),
			synthetic("world/unique", "minecraft/water_bottle")));

		EmiStack waterBottle = EmiStack.of(EmiPort.setPotion(new ItemStack(Items.POTION), Potions.WATER.value()))
			.setRemainder(EmiStack.of(Items.GLASS_BOTTLE));
		EmiStack mud = EmiStack.of(Items.MUD);
		addRecipeSafe(registry, () -> basicWorld(EmiStack.of(Items.DIRT), waterBottle, mud, synthetic("world/unique", "minecraft/mud"), false));
	}

	private static EmiIngredient damagedTool(EmiIngredient tool, int damage) {
		for (EmiStack stack : tool.getEmiStacks()) {
			ItemStack is = stack.getItemStack().copy();
			is.setDamageValue(1);
			stack.setRemainder(EmiStack.of(is));
		}
		return tool;
	}

	private static EmiIngredient getPreferredTag(List<String> candidates, EmiIngredient fallback) {
		for (String id : candidates) {
			EmiIngredient potential = EmiIngredient.of(TagKey.create(EmiPort.getItemRegistry().key(), EmiPort.id(id)));
			if (!potential.isEmpty()) {
				return potential;
			}
		}
		return fallback;
	}

	private static void addFuel(EmiRegistry registry, Set<Item> hiddenItems) {
		Map<Item, Integer> fuelMap = EmiAgnos.getFuelMap();
		compressRecipesToTags(fuelMap.keySet().stream().collect(Collectors.toSet()), (a, b) -> {
				return Integer.compare(fuelMap.get(a), fuelMap.get(b));
			}, tag -> {
				EmiIngredient stack = EmiIngredient.of(tag.raw());
				Item item = stack.getEmiStacks().get(0).getItemStack().getItem();
				int time = fuelMap.get(item);
				registry.addRecipe(new EmiFuelRecipe(stack, time, synthetic("fuel/tag", EmiUtil.subId(tag.id()))));
			}, item -> {
				if (!hiddenItems.contains(item)) {
					int time = fuelMap.get(item);
					registry.addRecipe(new EmiFuelRecipe(EmiStack.of(item), time, synthetic("fuel/item", EmiUtil.subId(item))));
				}
			});
	}

	private static void addComposting(EmiRegistry registry, Set<Item> hiddenItems) {
		// Resolved once, not once per item, and certainly not once per comparison
		EmiPortClient.ContextIntSource source = EmiPortClient.contextIntSource();
		Set<String> unhandledTypes = Sets.newLinkedHashSet();
		// Linked, so the item set keeps registry order and the index looks like it did in 26.2
		Object2FloatMap<Item> chances = new Object2FloatLinkedOpenHashMap<>();
		int unresolved = 0;
		for (Item item : EmiPort.getItemRegistry()) {
			Compostable compostable = item.components().get(DataComponents.COMPOSTABLE);
			if (compostable == null) {
				continue;
			}
			float chance = EmiPortClient.getExpectedValue(compostable.layers(), source, unhandledTypes::add);
			if (chance > 0) {
				chances.put(item, chance);
			} else {
				unresolved++;
			}
		}
		if (unresolved > 0) {
			EmiReloadLog.warn("The compost chance of " + unresolved + " items is unknown, so they are not"
				+ " listed as compostable." + (source.isEmpty() ? " " + ContextIntValues.MISSING_NUMBER_PROVIDERS : ""));
		}
		ContextIntValues.warnUnhandled(unhandledTypes, "composting chances");
		compressRecipesToTags(chances.keySet(), (a, b) -> {
				return Float.compare(chances.getFloat(a), chances.getFloat(b));
			}, tag -> {
				EmiIngredient stack = EmiIngredient.of(tag.raw());
				Item item = stack.getEmiStacks().get(0).getItemStack().getItem();
				registry.addRecipe(new EmiCompostingRecipe(stack, chances.getFloat(item), synthetic("composting/tag", EmiUtil.subId(tag.id()))));
			}, item -> {
				if (!hiddenItems.contains(item)) {
					registry.addRecipe(new EmiCompostingRecipe(EmiStack.of(item), chances.getFloat(item), synthetic("composting/item", EmiUtil.subId(item))));
				}
			});
	}

	private static void compressRecipesToTags(Set<Item> stacks, Comparator<Item> comparator, Consumer<EmiTagKey<Item>> tagConsumer, Consumer<Item> itemConsumer) {
		Set<Item> handled = Sets.newHashSet();
		outer:
		for (EmiTagKey<Item> key : EmiTags.getTags(EmiPort.getItemRegistry())) {
			List<Item> items = key.getList();
			if (items.size() < 2) {
				continue;
			}
			Item base = items.get(0);
			if (!stacks.contains(base)) {
				continue;
			}
			for (int i = 1; i < items.size(); i++) {
				Item item = items.get(i);
				if (!stacks.contains(item) || comparator.compare(base, item) != 0) {
					continue outer;
				}
			}
			if (handled.containsAll(items)) {
				continue;
			}
			handled.addAll(items);
			tagConsumer.accept(key);
		}
		for (Item item : stacks) {
			if (handled.contains(item)) {
				continue;
			}
			itemConsumer.accept(item);
		}
	}

	private static Identifier synthetic(String type, String name) {
		return EmiPort.id("emi", "/" + type + "/" + name);
	}

	/**
	 * Registers the block transformations a tool performs (stripping, tilling, flattening, ...).
	 * Since 26.3 these are defined by the data driven {@link BlockTransformer} registry rather than
	 * by hardcoded maps on the tool items. The registry is synchronized to the client, but the loot
	 * tables a transform may additionally drop are not, so the extra drop the old hoe actions
	 * showed (hanging roots from rooted dirt) is no longer part of the displayed recipe.
	 */
	private static void addBlockTransforms(EmiRegistry registry, EmiIngredient tool, ResourceKey<BlockTransformer> key, String syntheticPath) {
		for (Map.Entry<Block, Block> entry : getBlockTransforms(key).entrySet()) {
			Identifier id = synthetic(syntheticPath, EmiUtil.subId(entry.getKey()));
			addRecipeSafe(registry, () -> basicWorld(EmiStack.of(entry.getKey()), tool, EmiStack.of(entry.getValue()), id));
		}
	}

	private static Map<Block, Block> getBlockTransforms(ResourceKey<BlockTransformer> key) {
		Minecraft client = Minecraft.getInstance();
		BlockTransformer transformer = null;
		if (client.level != null) {
			transformer = client.level.registryAccess().lookup(Registries.BLOCK_TRANSFORMER)
				.flatMap(registry -> registry.get(key)).map(Holder::value).orElse(null);
		}
		if (transformer == null) {
			return Map.of();
		}
		Map<Block, Block> map = Maps.newLinkedHashMap();
		for (BlockTransformer.BlockTransformData data : transformer.transforms()) {
			collectBlockTransforms(data.blockStateProvider().value(), map);
		}
		return map;
	}

	private static void collectBlockTransforms(BlockStateProvider provider, Map<Block, Block> map) {
		if (provider instanceof RuleBasedStateProvider rules) {
			for (RuleBasedStateProvider.Rule rule : rules.rules()) {
				Block result = getTransformedBlock(rule.then().value());
				if (result != null) {
					for (Block block : getPredicateBlocks(rule.ifTrue())) {
						map.put(block, result);
					}
				}
			}
		}
	}

	private static @Nullable Block getTransformedBlock(BlockStateProvider provider) {
		if (provider instanceof SimpleStateProvider simple) {
			return simple.state().getBlock();
		} else if (provider instanceof CopyPropertiesProvider copy) {
			return getTransformedBlock(copy.source().value());
		}
		return null;
	}

	private static List<Block> getPredicateBlocks(BlockPredicate predicate) {
		if (predicate instanceof MatchingBlocksPredicate matching) {
			return ((MatchingBlocksPredicateAccessor) matching).emi$getBlocks().stream().map(Holder::value).toList();
		} else if (predicate instanceof MatchingBlockTagPredicate matching) {
			return EmiTagKey.of(((MatchingBlockTagPredicateAccessor) matching).emi$getTag()).getList();
		} else if (predicate instanceof CombiningPredicate combining) {
			// Compound checks describe the surroundings as well: tilling wants air above, so only
			// the child that looks at the block itself, offset zero, names the blocks the
			// transformation applies to. Falling back to any other child would publish the
			// neighbour's blocks, which is the bug this avoids.
			for (BlockPredicate child : ((CombiningPredicateAccessor) combining).emi$getPredicates()) {
				if (testsOwnPosition(child)) {
					List<Block> blocks = getPredicateBlocks(child);
					if (!blocks.isEmpty()) {
						return blocks;
					}
				}
			}
		}
		return List.of();
	}

	/** Whether a predicate looks at the block being transformed rather than at one of its neighbours. */
	private static boolean testsOwnPosition(BlockPredicate predicate) {
		if (predicate instanceof StateTestingPredicate state) {
			return ((StateTestingPredicateAccessor) state).emi$getOffset().equals(Vec3i.ZERO);
		}
		// A nested combining predicate has no offset of its own; its children carry them.
		return predicate instanceof CombiningPredicate;
	}

	@SuppressWarnings("unchecked")
	private static <C extends RecipeInput, T extends Recipe<C>> Iterable<T> getRecipes(EmiRegistry registry, RecipeType<T> type) {
		RecipeMap map = registry.getRecipeMap();
		if (map == null) {
			// Warned once at the top of register, not once per recipe type.
			return List.of();
		}
		return map.byType(type).stream()
			.map(e -> (T) e.value())::iterator;
	}

	private static void safely(String name, Runnable runnable) {
		try {
			runnable.run();
		} catch (Throwable t) {
			EmiReloadLog.warn("Exception thrown when reloading " + name  + " step in vanilla EMI plugin", t);
		}
	}

	private static void addRecipeSafe(EmiRegistry registry, Supplier<EmiRecipe> supplier) {
		try {
			registry.addRecipe(supplier.get());
		} catch (Throwable e) {
			EmiReloadLog.warn("Exception thrown when parsing EMI recipe (no ID available)", e);
		}
	}

	private static void addRecipeSafe(EmiRegistry registry, Supplier<EmiRecipe> supplier, Recipe<?> recipe) {
		try {
			registry.addRecipe(supplier.get());
		} catch (Throwable e) {
			EmiReloadLog.warn("Exception thrown when parsing vanilla recipe " + ProxyRecipeManager.getId(recipe), e);
		}
	}

	private static EmiRenderable simplifiedRenderer(int u, int v) {
		return (raw, x, y, delta) -> {
			EmiDrawContext context = EmiDrawContext.wrap(raw);
			context.drawTexture(EmiRenderHelper.WIDGETS, x, y, u, v, 16, 16);
		};
	}

	private static void addConcreteRecipe(EmiRegistry registry, Block powder, EmiStack water, Block result) {
		addRecipeSafe(registry, () -> basicWorld(EmiStack.of(powder), water, EmiStack.of(result),
			synthetic("world/concrete", EmiUtil.subId(result))));
	}

	private static EmiRecipe basicWorld(EmiIngredient left, EmiIngredient right, EmiStack output, Identifier id) {
		return basicWorld(left, right, output, id, true);
	}

	private static EmiRecipe basicWorld(EmiIngredient left, EmiIngredient right, EmiStack output, Identifier id, boolean catalyst) {
		return EmiWorldInteractionRecipe.builder()
			.id(id)
			.leftInput(left)
			.rightInput(right, catalyst)
			.output(output)
			.build();
	}
}