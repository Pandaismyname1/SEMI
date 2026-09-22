package dev.emi.emi.platform.fabric;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.text.WordUtils;

import com.google.common.collect.Lists;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.FabricEmiStack;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.registry.EmiPluginContainer;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.screen.FakeScreen;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;

import net.minecraft.world.item.crafting.RecipeMap;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.material.Fluid;

public class EmiAgnosFabric extends EmiAgnos {
	// Written on the network thread, read on the reload and render threads
	private static volatile RecipeMap receivedRecipeMap;

	static {
		EmiAgnos.delegate = new EmiAgnosFabric();
	}

	// Set by Fabric's ClientRecipeSynchronizedEvent and consumed by the vanilla recipe packet that
	// always follows it, so a map is only ever used for the sync it belongs to
	private static volatile RecipeMap pendingRecipeMap;

	public static void setReceivedRecipeMap(RecipeMap recipeMap) {
		receivedRecipeMap = recipeMap;
	}

	/**
	 * Whether a recipe map has been recorded for the current connection, used to tell "the server
	 * synchronized recipes" apart from "nothing arrived" before EMI falls back to an empty map.
	 */
	public static boolean hasReceivedRecipeMap() {
		return receivedRecipeMap != null;
	}

	/** Records the map Fabric just synchronized, to be picked up by the vanilla packet after it. */
	public static void setPendingRecipeMap(RecipeMap recipeMap) {
		pendingRecipeMap = recipeMap;
	}

	/**
	 * Takes the map synchronized for the sync currently being reported, or {@code null} when this
	 * sync produced none. One-shot, so a {@code /reload} whose sync sends no payload does not
	 * silently reuse the map from before the reload.
	 */
	public static @Nullable RecipeMap consumePendingRecipeMap() {
		RecipeMap map = pendingRecipeMap;
		pendingRecipeMap = null;
		return map;
	}

	@Override
	protected boolean isForgeAgnos() {
		return false;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected String getModNameAgnos(String namespace) {
		if (namespace.equals("c")) {
			return "Common";
		}
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(namespace);
		if (container.isPresent()) {
			return container.get().getMetadata().getName();
		}
		container = FabricLoader.getInstance().getModContainer(namespace.replace('_', '-'));
		if (container.isPresent()) {
			return container.get().getMetadata().getName();
		}
		return WordUtils.capitalizeFully(namespace.replace('_', ' '));
	}

	@Override
	protected Path getConfigDirectoryAgnos() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	protected boolean isDevelopmentEnvironmentAgnos() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	protected boolean isModLoadedAgnos(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}

	@Override
	protected List<String> getAllModNamesAgnos() {
		return FabricLoader.getInstance().getAllMods().stream().map(c -> c.getMetadata().getName()).toList();
	}

	@Override
	protected List<String> getAllModAuthorsAgnos() {
		return FabricLoader.getInstance().getAllMods().stream().flatMap(c -> c.getMetadata().getAuthors().stream())
			.map(p -> p.getName()).distinct().toList();
	}

	@Override
	protected List<String> getModsWithPluginsAgnos() {
		List<String> list = Lists.newArrayList();
		for (EntrypointContainer<EmiPlugin> container : FabricLoader.getInstance().getEntrypointContainers("emi", EmiPlugin.class)) {
			try {
				list.add(container.getProvider().getMetadata().getId());
			} catch (Throwable t) {
				EmiLog.error("Critical exception thrown when reading EMI Plugin from mod " + container.getProvider().getMetadata().getId(), t);
			}
		}
		return list;
	}


	@Override
	protected List<EmiPluginContainer> getPluginsAgnos() {
		List<EmiPluginContainer> list = Lists.newArrayList();
		for (EntrypointContainer<EmiPlugin> container : FabricLoader.getInstance().getEntrypointContainers("emi", EmiPlugin.class)) {
			try {
				list.add(new EmiPluginContainer(container.getEntrypoint(), container.getProvider().getMetadata().getId()));
			} catch (Throwable t) {
				EmiLog.error("Critical exception thrown when constructing EMI Plugin from mod " + container.getProvider().getMetadata().getId(), t);
			}
		}
		return list;
	}

	@Override
	protected List<ClientTooltipComponent> getItemTooltipAgnos(ItemStack stack) {
		return FakeScreen.INSTANCE.getTooltipComponentListFromItem(stack);
	}

	@Override
	protected Component getFluidNameAgnos(Fluid fluid, DataComponentPatch componentChanges) {
		return FluidVariantAttributes.getName(FluidVariant.of(fluid, componentChanges));
	}

	@Override
	protected List<Component> getFluidTooltipAgnos(Fluid fluid, DataComponentPatch componentChanges) {
		return FluidVariantRendering.getTooltip(FluidVariant.of(fluid, componentChanges));
	}

	@Override
	protected boolean isFloatyFluidAgnos(FluidEmiStack stack) {
		FluidVariant fluid = FluidVariant.of(stack.getKeyOfType(Fluid.class), stack.getComponentChanges());
		return FluidVariantAttributes.isLighterThanAir(fluid);
	}

	/**
	 * 1.21 read the sprite from {@code FluidVariantRendering.getSprites}, which no longer exists:
	 * fabric-rendering-fluids-v1 6.x registers fluid models into vanilla's {@code FluidStateModelSet}
	 * (see its {@code FluidStateModelSetMixin}), so reading that set is the loader supported path
	 * and picks up mod fluids. The tint still comes from the Fabric API.
	 */
	@Override
	protected void renderFluidAgnos(FluidEmiStack stack, GuiGraphicsExtractor draw, int x, int y, float delta, int xOff, int yOff, int width, int height) {
		Fluid fluid = stack.getKeyOfType(Fluid.class);
		net.minecraft.client.renderer.block.FluidModel fluidModel = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
		TextureAtlasSprite sprite = fluidModel.stillMaterial().sprite();
		if (sprite == null) {
			return;
		}
		FluidVariant fluidVariant = FluidVariant.of(fluid, stack.getComponentChanges());
		int color = FluidVariantRendering.getColor(fluidVariant);
		EmiRenderHelper.drawTintedSprite(draw, sprite, color, x, y, xOff, yOff, width, height);
	}

	@Override
	protected EmiStack createFluidStackAgnos(Object object) {
		if (object instanceof IJeiFluidIngredient fluid) {
			return FabricEmiStack.of(fluid.getFluidVariant(), fluid.getAmount());
		}
		return EmiStack.EMPTY;
	}

	@Override
	protected boolean isEnchantableAgnos(ItemStack stack, Enchantment enchantment) {
		return true;
	}

	@Override
	protected @Nullable RecipeMap getRecipeMapAgnos() {
		return receivedRecipeMap;
	}
}
