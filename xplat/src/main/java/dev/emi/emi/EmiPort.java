package dev.emi.emi;

import com.mojang.serialization.DynamicOps;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.mixin.accessor.SmithingTransformRecipeAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.material.Fluid;

import java.io.InputStream;
import java.util.Collection;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EmiPort {
	private static final net.minecraft.util.RandomSource RANDOM = net.minecraft.util.RandomSource.create();

	public static MutableComponent literal(String s) {
		return Component.literal(s);
	}

	public static MutableComponent literal(String s, ChatFormatting formatting) {
		return Component.literal(s).withStyle(formatting);
	}

	public static MutableComponent literal(String s, ChatFormatting... formatting) {
		return Component.literal(s).withStyle(formatting);
	}

	public static MutableComponent literal(String s, Style style) {
		return Component.literal(s).setStyle(style);
	}
	
	public static MutableComponent translatable(String s) {
		return Component.translatable(s);
	}
	
	public static MutableComponent translatable(String s, ChatFormatting formatting) {
		return Component.translatable(s).withStyle(formatting);
	}
	
	public static MutableComponent translatable(String s, Object... objects) {
		return Component.translatable(s, objects);
	}

	public static MutableComponent append(MutableComponent text, Component appended) {
		return text.append(appended);
	}

	public static FormattedCharSequence ordered(Component text) {
		return text.getVisualOrderText();
	}

	public static Collection<Identifier> findResources(ResourceManager manager, String prefix, Predicate<String> pred) {
		return manager.listResources(prefix, i -> pred.test(i.toString())).keySet();
	}

	public static InputStream getInputStream(Resource resource) {
		try {
			return resource.open();
		} catch (Exception e) {
			return null;
		}
	}

	public static BannerPatternLayers addRandomBanner(BannerPatternLayers patterns, Random random) {
		return EmiPortClient.addRandomBanner(patterns, random);
	}

	public static boolean canTallFlowerDuplicate(TallFlowerBlock tallFlowerBlock) {
		try {
			return tallFlowerBlock.isValidBonemealTarget(null, null, null, BonemealSource.INTERACTION)
				&& tallFlowerBlock.isBonemealSuccess(null, null, null, null, BonemealSource.INTERACTION);
		} catch(Exception e) {
			return false;
		}
	}

	public static Registry<Item> getItemRegistry() {
		return BuiltInRegistries.ITEM;
	}

	public static Registry<Block> getBlockRegistry() {
		return BuiltInRegistries.BLOCK;
	}

	public static Registry<Fluid> getFluidRegistry() {
		return BuiltInRegistries.FLUID;
	}

	public static Registry<Potion> getPotionRegistry() {
		return BuiltInRegistries.POTION;
	}

	public static Registry<Enchantment> getEnchantmentRegistry() {
		return EmiPortClient.getEnchantmentRegistry();
	}

	public static ItemStack getOutput(Recipe<?> recipe) {
		return EmiPortClient.getOutput(recipe);
	}

	public static Stream<Item> getDisabledItems() {
		return EmiPortClient.getDisabledItems();
	}

	public static void playClickSound() {
		EmiPortClient.playClickSound();
	}

	public static Comparison compareStrict() {
		return Comparison.compareComponents();
	}

	public static ItemStack setPotion(ItemStack stack, Potion potion) {
		stack.update(DataComponents.POTION_CONTENTS, PotionContents.EMPTY, getPotionRegistry().wrapAsHolder(potion), PotionContents::withPotion);
		return stack;
	}

	public static DataComponentPatch emptyExtraData() {
		return DataComponentPatch.EMPTY;
	}

	public static Identifier id(String id) {
		return Identifier.parse(id);
	}

	public static Identifier id(String namespace, String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	/**
	 * Serializes a stack the way vanilla's item argument parses it, i.e.
	 * {@code minecraft:stone[minecraft:custom_name={...}]}, for use in commands such as
	 * {@code /give}.
	 * <p>
	 * 26.1 removed {@code ItemInput#serialize}, which upstream used for this, so the component
	 * syntax is rebuilt here from the same pieces {@link net.minecraft.commands.arguments.item.ItemParser}
	 * reads back.
	 */
	public static String serializeItemArgument(ItemStack stack, HolderLookup.Provider registries) {
		StringBuilder builder = new StringBuilder(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		String components = serializeComponents(stack.getComponentsPatch(), registries);
		if (!components.isEmpty()) {
			builder.append(ItemParser.SYNTAX_START_COMPONENTS).append(components).append(ItemParser.SYNTAX_END_COMPONENTS);
		}
		return builder.toString();
	}

	private static String serializeComponents(DataComponentPatch patch, HolderLookup.Provider registries) {
		DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		// 26.3 removed DataComponentPatch#entrySet; split() hands out the same information as a
		// map of added components plus a set of removed types.
		DataComponentPatch.SplitResult split = patch.split();
		Stream<String> added = split.added().stream().flatMap(component -> {
			Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
			if (id == null || component.type().isTransient()) {
				return Stream.<String>empty();
			}
			return component.encodeValue(ops).result().stream()
				.map(tag -> id.toString() + ItemParser.SYNTAX_COMPONENT_ASSIGNMENT + tag);
		});
		Stream<String> removed = split.removed().stream().flatMap(type -> {
			Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
			if (id == null || type.isTransient()) {
				return Stream.<String>empty();
			}
			return Stream.of(ItemParser.SYNTAX_REMOVED_COMPONENT + id.toString());
		});
		return Stream.concat(added, removed)
			.collect(Collectors.joining(String.valueOf(ItemParser.SYNTAX_COMPONENT_SEPARATOR)));
	}

}
