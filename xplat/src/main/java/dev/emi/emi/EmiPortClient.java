package dev.emi.emi;

import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.data.ContextIntValues;
import dev.emi.emi.data.ContextNumbers;
import dev.emi.emi.mixin.accessor.SmithingTransformRecipeAccessor;
import dev.emi.emi.mixin.accessor.TransmuteRecipeAccessor;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.Random;
import java.util.stream.Stream;

public class EmiPortClient {
    public static BannerPatternLayers addRandomBanner(BannerPatternLayers patterns, Random random) {
        Minecraft client = Minecraft.getInstance();
        var bannerRegistry = client.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        return new BannerPatternLayers.Builder().addAll(patterns).add(bannerRegistry.get(random.nextInt(bannerRegistry.size())).orElseThrow(),
                DyeColor.values()[random.nextInt(DyeColor.values().length)]).build();
    }

    public static Registry<Enchantment> getEnchantmentRegistry() {
        Minecraft client = Minecraft.getInstance();
        return client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }

    public static ItemStack getOutput(Recipe<?> recipe) {
        if (recipe instanceof TransmuteRecipe transmute) {
            ItemStack stack = getTransmuteOutput(transmute);
            if (!stack.isEmpty()) {
                return stack;
            }
        } else if (recipe instanceof CraftingRecipe crafting) {
            try {
                ItemStack stack = crafting.assemble(CraftingInput.EMPTY);
                if (!stack.isEmpty()) {
                    return stack;
                }
            } catch (Exception e) {
                logOutputFailure(recipe, e);
            }
        } else if (recipe instanceof SingleItemRecipe single) {
            return single.assemble(new SingleRecipeInput(ItemStack.EMPTY));
        } else if (recipe instanceof SmithingTransformRecipe smithing) {
            ItemStackTemplate result = ((SmithingTransformRecipeAccessor) smithing).getResult();
            return result.create();
        } else if (recipe instanceof SmithingRecipe smithing) {
            try {
                ItemStack templateStack = smithing.templateIngredient()
                        .flatMap(i -> i.items().findFirst().map(h -> new ItemStack(h.value())))
                        .orElse(ItemStack.EMPTY);
                ItemStack baseStack = smithing.baseIngredient()
                        .items().findFirst().map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
                ItemStack additionStack = smithing.additionIngredient()
                        .flatMap(i -> i.items().findFirst().map(h -> new ItemStack(h.value())))
                        .orElse(ItemStack.EMPTY);
                ItemStack result = smithing.assemble(new SmithingRecipeInput(templateStack, baseStack, additionStack));
                if (!result.isEmpty()) return result;
            } catch (Exception e) {
                logOutputFailure(recipe, e);
            }
        }
        Minecraft client = Minecraft.getInstance();
        for (var display : recipe.display()) {
            ItemStack stack = display.result().resolveForFirstStack(SlotDisplayContext.fromLevel(client.level));
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Assembling a recipe with an empty input is expected to fail for plenty of modded recipes, so
     * this stays quiet outside of a development environment, where it is worth seeing.
     */
    private static void logOutputFailure(Recipe<?> recipe, Exception e) {
        if (EmiConfig.devMode) {
            EmiLog.error("Exception assembling output of " + recipe.getClass().getName(), e);
        }
    }

    /**
     * A transmute recipe reads its result off the stack that matched its input ingredient, and
     * counts the stacks that matched its material ingredient, so it cannot be assembled from an
     * empty grid: a result of {@code TransmuteResult.KEEP_INPUT_ITEM} (map cloning) would resolve
     * to air and throw. Assemble it against one input stack plus one material stack instead, which
     * is also what makes the count and the copied components come out right.
     */
    private static ItemStack getTransmuteOutput(TransmuteRecipe transmute) {
        try {
            TransmuteRecipeAccessor accessor = (TransmuteRecipeAccessor) transmute;
            ItemStack input = firstStack(accessor.emi$getInput());
            if (input.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack material = firstStack(accessor.emi$getMaterial());
            return transmute.assemble(CraftingInput.of(2, 1, List.of(input, material)));
        } catch (Exception e) {
            logOutputFailure(transmute, e);
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack firstStack(Ingredient ingredient) {
        return ingredient.items().findFirst().map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
    }

    public static int getGuiScale(Minecraft client) {
        return (int) client.getWindow().getGuiScale();
    }

    public static Button newButton(int x, int y, int w, int h, Component name, OnPress action) {
        return Button.builder(name, action).pos(x, y).size(w, h).build();
    }

    /**
     * Everything a client can use to put a number on a {@link ResolvableInt}, resolved once per
     * reload because the lookup walks the integrated server's registries.
     *
     * @param serverValues the values a SEMI server sent, empty when there are none
     * @param providers the number provider registry, when this client can reach one at all
     */
    public record ContextIntSource(boolean serverValues,
            @Nullable HolderLookup.RegistryLookup<ContextIntProvider> providers) {

        public boolean isEmpty() {
            return !serverValues && providers == null;
        }
    }

    /** Resolves the sources of data driven numbers once, for a whole reload. */
    public static ContextIntSource contextIntSource() {
        return new ContextIntSource(!ContextIntValues.isEmpty(), getContextIntProviders().orElse(null));
    }

    /**
     * Resolves a data driven number, preferring the values a SEMI server sent over anything this
     * client can work out locally.
     *
     * @param unhandled receives the registry id of every provider type EMI cannot estimate
     */
    public static float getExpectedValue(ResolvableInt value, ContextIntSource source, Consumer<String> unhandled) {
        if (value instanceof ResolvableInt.Reference reference) {
            Float sent = ContextIntValues.get(reference.key().identifier());
            if (sent != null) {
                return sent;
            }
        }
        return ContextNumbers.expectedValue(value, source.providers(), unhandled);
    }

    /**
     * The registry the data driven number providers live in. It is a reloadable, server side
     * registry: it is never synchronized to a client, so only an integrated server has a copy.
     * See {@link #getExpectedValue(ResolvableInt, ContextIntSource, Consumer)}.
     */
    public static Optional<? extends HolderLookup.RegistryLookup<ContextIntProvider>> getContextIntProviders() {
        Minecraft client = Minecraft.getInstance();
        try {
            // Singleplayer and LAN hosts: the integrated server has loaded the datapack registries.
            MinecraftServer server = client.getSingleplayerServer();
            if (server != null) {
                Optional<? extends HolderLookup.RegistryLookup<ContextIntProvider>> lookup
                        = server.reloadableRegistries().lookup().lookup(Registries.CONTEXT_INT_PROVIDER);
                if (lookup.isPresent()) {
                    return lookup;
                }
            }
            // Anything that does synchronize the registry anyway (a loader or another mod).
            if (client.level != null) {
                return client.level.registryAccess().lookup(Registries.CONTEXT_INT_PROVIDER);
            }
        } catch (Exception e) {
            EmiLog.error("Could not look up the context int provider registry", e);
        }
        return Optional.empty();
    }

    public static void focus(EditBox widget, boolean focused) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.gui != null && client.gui.screen() != null) {
            var currentFocus = client.gui.screen().getFocused();
            if (!focused && currentFocus == widget || focused && currentFocus != widget) {
                client.gui.screen().setFocused(null);
            }
        }
        widget.setFocused(focused);
    }

    public static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    public static Stream<Item> getDisabledItems() {
        Minecraft client = Minecraft.getInstance();
        FeatureFlagSet fs = client.level.enabledFeatures();
        return EmiPort.getItemRegistry().stream().filter(i -> !i.isEnabled(fs));
    }
}
