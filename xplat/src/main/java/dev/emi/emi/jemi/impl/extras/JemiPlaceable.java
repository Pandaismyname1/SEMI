package dev.emi.emi.jemi.impl.extras;

import java.util.Collection;

import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.jemi.impl.JemiTooltipBuilder;
import dev.emi.emi.runtime.EmiLog;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public class JemiPlaceable<T extends IPlaceable<T>> implements IPlaceable<T> {
	public int x = 0, y = 0;
	public int width, height;
	/**
	 * Every {@code setTooltip} overload of {@code IRecipeWidgetBuilder} collapses onto a
	 * callback, which is what {@link #addTooltip(WidgetHolder)} turns into an EMI tooltip.
	 */
	public IRecipeWidgetTooltipCallback tooltipCallback;

	public JemiPlaceable(int width, int height) {
		this.width = width;
		this.height = height;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T setPosition(int xPos, int yPos) {
		this.x = xPos;
		this.y = yPos;
		return (T) this;
	}

	@Override
	public T setPosition(int areaX, int areaY, int areaWidth, int areaHeight,
			HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
		int xPos = areaX + horizontalAlignment.getXPos(areaWidth, getWidth());
		int yPos = areaY + verticalAlignment.getYPos(areaHeight, getHeight());
		return setPosition(xPos, yPos);
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	protected void setTooltipCallback(IRecipeWidgetTooltipCallback tooltipCallback) {
		this.tooltipCallback = tooltipCallback;
	}

	protected void setTooltipText(FormattedText tooltip) {
		setTooltipCallback(builder -> builder.add(tooltip));
	}

	protected void setTooltipText(Collection<? extends FormattedText> tooltip) {
		setTooltipCallback(builder -> builder.addAll(tooltip));
	}

	protected void setTooltipComponent(TooltipComponent tooltip) {
		setTooltipCallback(builder -> builder.add(tooltip));
	}

	/**
	 * Registers the plugin-supplied tooltip, if any, over this widget's area.
	 */
	protected void addTooltip(WidgetHolder holder) {
		IRecipeWidgetTooltipCallback callback = tooltipCallback;
		if (callback == null || getWidth() <= 0 || getHeight() <= 0) {
			return;
		}
		holder.addTooltip((mouseX, mouseY) -> {
			JemiTooltipBuilder builder = new JemiTooltipBuilder();
			try {
				callback.onTooltip(builder);
			} catch (Exception e) {
				EmiLog.error("Error building JEI widget tooltip", e);
			}
			return builder.buildTooltip();
		}, x, y, getWidth(), getHeight());
	}
}
