package dev.emi.emi.jemi;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * An {@link EmiInfoRecipe} whose construction is deferred until the recipe is first
 * laid out or drawn.
 *
 * <p>{@code EmiInfoRecipe}'s constructor word-wraps its text with
 * {@code Minecraft.getInstance().font.split(...)}. On 26.1 measuring text is no longer
 * thread safe: {@code Font}'s width provider resolves each code point through
 * {@code GlyphSource.getGlyph}, which returns a <em>baked</em> glyph and therefore
 * stitches glyph textures on demand, throwing
 * {@code IllegalStateException: RenderSystem called from wrong thread} off the render
 * thread. JEMI's {@link EmiRecipe} registration runs on the reload worker, so the
 * recipe must not touch the font there.
 *
 * <p>An earlier fix wrapped the registration in {@code Minecraft.getInstance().execute(...)},
 * which registered the recipes after the reload had already baked, so they were dropped
 * (or mutated the recipe lists concurrently). Instead, this class registers synchronously
 * and only builds the real {@code EmiInfoRecipe} from {@link #getDisplayWidth()},
 * {@link #getDisplayHeight()} and {@link #addWidgets(WidgetHolder)}, all of which EMI
 * calls from the render thread.
 *
 * <p>The proper fix belongs in {@code EmiInfoRecipe} itself: keep the raw
 * {@code List<Component>} and wrap it lazily behind an accessor used by
 * {@code getDisplayHeight}/{@code addWidgets}. {@code EmiRecipes.bake()} constructs the
 * data-driven {@code emi:info} recipes on the same worker thread and hits the same trap.
 */
public class JemiInfoRecipe implements EmiRecipe {
	private final List<EmiIngredient> stacks;
	private final List<Component> text;
	private final @Nullable Identifier id;
	private final List<EmiStack> outputs;
	private EmiInfoRecipe delegate;

	public JemiInfoRecipe(List<EmiIngredient> stacks, List<Component> text, @Nullable Identifier id) {
		this.stacks = stacks;
		this.text = text;
		this.id = id;
		this.outputs = stacks.stream().flatMap(ing -> ing.getEmiStacks().stream()).toList();
	}

	/**
	 * Must only be called from the render thread, see the class javadoc.
	 */
	private EmiInfoRecipe delegate() {
		if (delegate == null) {
			delegate = new EmiInfoRecipe(stacks, text, id);
		}
		return delegate;
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return VanillaEmiRecipeCategories.INFO;
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return stacks;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return outputs;
	}

	@Override
	public boolean supportsRecipeTree() {
		return false;
	}

	@Override
	public int getDisplayWidth() {
		return delegate().getDisplayWidth();
	}

	@Override
	public int getDisplayHeight() {
		return delegate().getDisplayHeight();
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		delegate().addWidgets(widgets);
	}
}
