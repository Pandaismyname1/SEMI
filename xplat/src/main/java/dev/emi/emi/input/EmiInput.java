package dev.emi.emi.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;

public class EmiInput {
	public static final int CONTROL_MASK = 1;
	public static final int ALT_MASK = 2;
	public static final int SHIFT_MASK = 4;

	public static boolean isControlDown() {
		return Minecraft.getInstance().hasControlDown();
	}

	public static boolean isAltDown() {
		return InputConstants.isKeyDown(InputConstants.KEY_LALT)
			|| InputConstants.isKeyDown(InputConstants.KEY_RALT);
	}

	public static boolean isShiftDown() {
		return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
			|| InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
	}

	public static int maskFromCode(int keyCode) {
		if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
			if (keyCode == InputConstants.KEY_LGUI || keyCode == InputConstants.KEY_RGUI) {
				return CONTROL_MASK;
			}
		}
		if (keyCode == InputConstants.KEY_LCONTROL || keyCode == InputConstants.KEY_RCONTROL) {
			return CONTROL_MASK;
		} else if (keyCode == InputConstants.KEY_LALT || keyCode == InputConstants.KEY_RALT) {
			return ALT_MASK;
		} else if (keyCode == InputConstants.KEY_LSHIFT || keyCode == InputConstants.KEY_RSHIFT) {
			return SHIFT_MASK;
		}
		return 0;
	}

	public static int getCurrentModifiers() {
		int ret = 0;
		if (isControlDown()) {
			ret |= CONTROL_MASK;
		}
		if (isAltDown()) {
			ret |= ALT_MASK;
		}
		if (isShiftDown()) {
			ret |= SHIFT_MASK;
		}
		return ret;
	}
}
