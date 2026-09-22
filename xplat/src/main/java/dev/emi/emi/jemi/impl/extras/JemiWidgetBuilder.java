package dev.emi.emi.jemi.impl.extras;

import java.util.Collection;

import dev.emi.emi.api.widget.WidgetHolder;
import mezz.jei.api.gui.widgets.IDrawableWidget;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public class JemiWidgetBuilder extends JemiPlaceable<IDrawableWidget> implements IDrawableWidget {
	public WidgetConstructor constructor;

	public JemiWidgetBuilder(int width, int height, WidgetConstructor constructor) {
		super(width, height);
		this.constructor = constructor;
	}

	public void addWidgets(WidgetHolder holder) {
		constructor.accept(this, holder);
	}

	@Override
	public IDrawableWidget setTooltip(FormattedText tooltip) {
		// Unimplemented
		return this;
	}

	@Override
	public IDrawableWidget setTooltip(Collection<? extends FormattedText> tooltip) {
		// Unimplemented
		return this;
	}

	@Override
	public IDrawableWidget setTooltip(TooltipComponent tooltip) {
		// Unimplemented
		return this;
	}

	@Override
	public IDrawableWidget setTooltip(IRecipeWidgetTooltipCallback tooltipCallback) {
		// Unimplemented
		return this;
	}
	
	public static interface WidgetConstructor {

		void accept(JemiWidgetBuilder builder, WidgetHolder holder);
	}
}
