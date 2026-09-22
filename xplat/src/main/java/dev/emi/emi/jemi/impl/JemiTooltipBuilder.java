package dev.emi.emi.jemi.impl;

import java.util.ArrayList;
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
	public final List<ClientTooltipComponent> tooltip = Lists.newArrayList();
	// The single ordered record of everything added, text and components alike, so
	// getLines() can hand JEI back the lines in the order the plugin wrote them.
	private final List<Either<FormattedText, TooltipComponent>> lines = Lists.newArrayList();

	@Override
	public void add(FormattedText component) {
		if (component == null) {
			return;
		}
		Component text = asComponent(component);
		tooltip.add(ClientTooltipComponent.create(text.getVisualOrderText()));
		lines.add(Either.left(text));
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
		try {
			tooltip.add(ClientTooltipComponent.create(data));
			lines.add(Either.right(data));
		} catch (Exception e) {
			EmiLog.error("Error converting TooltipComponent", e);
		}
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
		// Mutable, and a copy: JEI hands this list to plugins, which may edit it.
		return new ArrayList<>(lines);
	}
}
