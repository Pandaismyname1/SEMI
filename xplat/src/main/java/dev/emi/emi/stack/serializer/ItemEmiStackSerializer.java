package dev.emi.emi.stack.serializer;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.serializer.EmiStackSerializer;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class ItemEmiStackSerializer implements EmiStackSerializer<ItemEmiStack> {

	@Override
	public String getType() {
		return "item";
	}

	@Override
	public EmiStack create(Identifier id, DataComponentPatch componentChanges, long amount) {
		return EmiPort.getItemRegistry().get(id)
			.map(holder -> EmiStack.of(new ItemStack(holder, 1, componentChanges), amount))
			.orElse(EmiStack.EMPTY);
	}
}
