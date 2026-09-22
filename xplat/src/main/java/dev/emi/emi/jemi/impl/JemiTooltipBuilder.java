package dev.emi.emi.jemi.impl;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;

import dev.emi.emi.runtime.EmiLog;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public class JemiTooltipBuilder implements ITooltipBuilder {
	// The single ordered record of everything added, text and components alike. JEI's own
	// JeiTooltip keeps exactly this one list and derives everything else from it, so getLines()
	// can hand plugins the live list and buildTooltip() renders whatever it holds at the time.
	private final List<Either<FormattedText, TooltipComponent>> lines = Lists.newArrayList();

	@Override
	public void add(FormattedText component) {
		if (component == null) {
			return;
		}
		lines.add(Either.left(asComponent(component)));
	}

	/**
	 * JEI accepts any {@link FormattedText}, but Minecraft's tooltip rendering needs a
	 * {@link Component}, so anything else is flattened into one by visiting its styled
	 * parts rather than being dropped.
	 */
	private static Component asComponent(FormattedText text) {
		if (text instanceof Component component) {
			return component;
		}
		MutableComponent ret = Component.empty();
		text.visit((style, str) -> {
			ret.append(Component.literal(str).setStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return ret;
	}

	@Override
	public void addAll(Collection<? extends FormattedText> components) {
		for (FormattedText v : components) {
			add(v);
		}
	}

	@Override
	public void add(TooltipComponent data) {
		if (data == null) {
			return;
		}
		lines.add(Either.right(data));
	}

	/**
	 * Renders the lines added so far, in order. Built on demand rather than as the lines come in,
	 * so edits a plugin makes through {@link #getLines()} are picked up.
	 */
	public List<ClientTooltipComponent> buildTooltip() {
		List<ClientTooltipComponent> ret = Lists.newArrayList();
		for (Either<FormattedText, TooltipComponent> line : lines) {
			try {
				line.left().ifPresent(text -> ret.add(ClientTooltipComponent.create(asComponent(text).getVisualOrderText())));
				line.right().ifPresent(data -> ret.add(ClientTooltipComponent.create(data)));
			} catch (Exception e) {
				EmiLog.error("Error converting TooltipComponent", e);
			}
		}
		return ret;
	}

	@Override
	public void setIngredient(ITypedIngredient<?> typedIngredient) {
		// EMI's methods bypass the vanilla tooltip render which accepts a stack, so this will do nothing
	}

	@Override
	public void clear() {
		// EMI does not support tooltip removal, this will only clear the user's additions
	}

	@Override
	public void clearIngredient() {
		// EMI does not support tooltip removal
	}

	@Override
	public void addKeyUsageComponent(String translationKey, IJeiKeyMapping keyMapping) {
		// EMI does not use JEI key mappings
	}

	public List<Component> toLegacyToComponents() {
		List<Component> ret = Lists.newArrayList();
		for (Either<FormattedText, TooltipComponent> line : lines) {
			line.left().filter(t -> t instanceof Component).map(t -> (Component) t).ifPresent(ret::add);
		}
		return ret;
	}

	public void removeAll(List<Component> components) {
		// EMI does not support tooltip removal
	}

	@Override
	public List<Either<FormattedText, TooltipComponent>> getLines() {
		// The live list, as JEI's JeiTooltip.getLines() returns: plugins edit it in place and the
		// edits have to be visible to buildTooltip().
		return lines;
	}
}
