package dev.emi.emi.api.widget;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

public abstract class Widget implements Renderable {

	public abstract Bounds getBounds();
	
	public abstract void extractRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta);

	public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
		return List.of();
	}
	
	/**
	 * @param button The mouse button, as Minecraft numbers them. Since 26.3 these are SDL's
	 *	values, so left is {@code 1}, middle {@code 2} and right {@code 3}: compare against
	 *	{@link com.mojang.blaze3d.platform.InputConstants#MOUSE_BUTTON_LEFT} and friends, never
	 *	against a literal or a GLFW constant.
	 */
	public boolean mouseClicked(int mouseX, int mouseY, int button) {
		return false;
	}

	/**
	 * @param keyCode The SDL scancode of the key, that is {@code KeyEvent.key()}, which is what
	 *	key binds are stored with. Compare against
	 *	{@link com.mojang.blaze3d.platform.InputConstants}'s {@code KEY_*} constants, never
	 *	against a GLFW constant: 26.3 moved off GLFW and the numbers are different.
	 * @param scanCode The SDL keycode of the key, that is {@code KeyEvent.keycode()}: the
	 *	character the key produces under the current layout, for which
	 *	{@link com.mojang.blaze3d.platform.InputConstants}'s {@code KEYCODE_*} constants are the
	 *	ones to compare against. The parameter keeps its name for source compatibility; 26.3 has
	 *	no separate scancode key type any more.
	 * @param modifiers The SDL keymod mask, for which
	 *	{@link com.mojang.blaze3d.platform.InputConstants}'s {@code MOD_*} constants apply. EMI's
	 *	own {@link dev.emi.emi.input.EmiInput} helpers are usually the better choice.
	 */
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return false;
	}
}
