package dev.emi.emi.jemi.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import com.google.common.collect.Lists;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.jemi.JemiUtil;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;

@SuppressWarnings({"unchecked", "removal"})
public class JemiRecipeSlot implements IRecipeSlotDrawable {
	public final RecipeIngredientRole role;
	public final boolean large, defaultBackground;
	public int x, y;
	public final Optional<String> name;
	public final IRecipeSlotRichTooltipCallback richTooltipCallback;
	public final OffsetDrawable background, overlay;
	public final Map<IIngredientType<?>, IngredientRenderer<?>> renderers;
	public final TankInfo tankInfo;
	public final EmiIngredient stack;
	public final List<JemiIngredientAcceptor> displayOverrides = Lists.newArrayList();
	private JemiIngredientAcceptor displayOverride;
	public SlotWidget widget;
	public int highlight = 0;

	public JemiRecipeSlot(JemiRecipeSlotBuilder builder) {
		this.role = builder.acceptor.role;
		this.large = builder.large;
		this.defaultBackground = builder.defaultBackground;
		this.x = builder.x;
		this.y = builder.y;
		this.name = builder.name;
		this.richTooltipCallback = builder.richTooltipCallback;
		this.background = builder.background;
		this.overlay = builder.overlay;
		this.renderers = builder.renderers;
		this.tankInfo = builder.tankInfo;
		this.stack = builder.acceptor.build();
	}

	public JemiRecipeSlot(RecipeIngredientRole role, EmiStack stack) {
		this.role = role;
		this.large = false;
		this.defaultBackground = false;
		this.x = 0;
		this.y = 0;
		this.name = Optional.empty();
		this.richTooltipCallback = null;
		this.background = null;
		this.overlay = null;
		this.renderers = null;
		this.tankInfo = null;
		this.stack = stack;
	}

	@Override
	public <T> Stream<T> getIngredients(IIngredientType<T> ingredientType) {
		return (Stream<T>) getAllIngredients().filter(t -> t.getType() == ingredientType).map(t -> t.getIngredient());
	}

	@Override
	public @Unmodifiable List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
		return getAllIngredients().toList();
	}

	@Override
	public Stream<ITypedIngredient<?>> getAllIngredients() {
		return stack.getEmiStacks().stream().map(JemiUtil::getTyped).filter(Optional::isPresent).map(Optional::get);
	}

	@Override
	public boolean isEmpty() {
		return stack.isEmpty();
	}

	@Override
	public <T> Optional<T> getDisplayedIngredient(IIngredientType<T> ingredientType) {
		Optional<ITypedIngredient<?>> ing = getDisplayedIngredient();
		if (ing.isPresent() && ing.get().getType() == ingredientType) {
			return (Optional<T>) Optional.of(ing.get().getIngredient());
		}
		return Optional.empty();
	}

	@Override
	public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
		List<EmiStack> stacks = stack.getEmiStacks();
		if (stacks.isEmpty()) {
			return Optional.empty();
		}
		return JemiUtil.getTyped(stacks.get(0));
	}

	/**
	 * Aliases {@link #getAllIngredients()}: EMI cycles through every stack of an
	 * ingredient rather than displaying a single one, and this does <em>not</em>
	 * honour EMI's stack visibility filter, so a JEI plugin may see ingredients
	 * here that EMI itself would hide.
	 */
	@Override
	public Stream<ITypedIngredient<?>> getDisplayedIngredients() {
		return getAllIngredients();
	}

	@Override
	public Optional<TagKey<?>> getTagKey() {
		if (stack instanceof TagEmiIngredient tag) {
			return Optional.of(tag.key);
		}
		return Optional.empty();
	}

	@Override
	public Optional<String> getSlotName() {
		return name;
	}

	@Override
	public RecipeIngredientRole getRole() {
		return role;
	}

	@Override
	public void drawHighlight(GuiGraphicsExtractor raw, int color) {
		this.highlight = color;
	}

	/**
	 * Unlike upstream, which ignored this, the position is honoured: JEI widgets such as
	 * the scroll grid lay their slots out by calling this, and {@link JemiRecipe} builds
	 * the {@link dev.emi.emi.jemi.widget.JemiSlotWidget}s only after extras have run so
	 * they pick up the final coordinates.
	 */
	@Override
	public void setPosition(int xPos, int yPos) {
		this.x = xPos;
		this.y = yPos;
	}

	@Override
	public Rect2i getAreaIncludingBackground() {
		if (widget != null) {
			Bounds b = widget.getBounds();
			return new Rect2i(b.x(), b.y(), b.width(), b.height());
		}
		int size = large ? 26 : 18;
		return new Rect2i(x - 1, y - 1, size, size);
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return widget != null && widget.getBounds().contains((int) mouseX, (int) mouseY);
	}

	// draw/drawHoverOverlays/drawTooltip/getTooltip were all unimplemented upstream too
	// ("I don't think I will"). EMI draws slot contents and tooltips through
	// JemiSlotWidget, so JEI must not draw them a second time.
	@Override
	public void draw(GuiGraphicsExtractor raw) {
	}

	@Override
	public void draw(GuiGraphicsExtractor raw, boolean highlight) {
	}

	@Override
	public void drawHoverOverlays(GuiGraphicsExtractor raw) {
	}

	@Override
	public List<Component> getTooltip() {
		// Unimplemented
		// Mutable because who knows
		return Lists.newArrayList();
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltipBuilder) {
		// Unimplemented
	}

	@Override
	public void drawTooltip(GuiGraphicsExtractor raw, int mouseX, int mouseY) {
		// Unimplemented
	}

	/**
	 * Unlike upstream, which recorded the acceptor and then ignored it, the overrides
	 * are honoured: {@link dev.emi.emi.jemi.widget.JemiSlotWidget#displayedStack} renders
	 * them in place of {@link #stack}. Only overrides registered before the slot widgets
	 * are built (i.e. during {@code createRecipeExtras}) take effect; the slot's logical
	 * ingredients, which the transfer handlers see, are deliberately left alone.
	 */
	@Override
	public IIngredientAcceptor<?> createDisplayOverrides() {
		// Idempotent, like JEI's own slot: the acceptor is created on the first call and the same
		// one is handed back afterwards, so a plugin that calls this per ingredient adds them all
		// to one override instead of creating a fresh, separate override each time.
		if (displayOverride == null) {
			displayOverride = new JemiIngredientAcceptor(role);
			displayOverrides.add(displayOverride);
		}
		return displayOverride;
	}

	@Override
	public void clearDisplayOverrides() {
		displayOverride = null;
		displayOverrides.clear();
	}

	public static record OffsetDrawable(IDrawable drawable, int xOff, int yOff){
	}

	public static record IngredientRenderer<T>(IIngredientType<T> type, IIngredientRenderer<T> renderer){
	}

	public static record TankInfo(int width, int height, long capacity, boolean showCapacity) {
	}
}
