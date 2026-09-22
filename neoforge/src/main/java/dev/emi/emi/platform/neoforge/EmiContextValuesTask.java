package dev.emi.emi.platform.neoforge;

import java.util.function.Consumer;

import dev.emi.emi.data.ContextIntValues;
import dev.emi.emi.network.ContextIntValuesS2CPacket;
import dev.emi.emi.network.EmiNetwork;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Sends the resolved number provider values while the client is still configuring, so they are in
 * place before tags and recipes arrive and the single reload per join still covers everything.
 */
public record EmiContextValuesTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {
	public static final Type TYPE = new Type(EmiNetwork.CONTEXT_INT_VALUES.id());

	@Override
	public void run(Consumer<CustomPacketPayload> sender) {
		// The configuration listener does not expose its server, and there is only ever one.
		sender.accept(new ContextIntValuesS2CPacket(ContextIntValues.computeFor(ServerLifecycleHooks.getCurrentServer())));
		listener.finishCurrentTask(type());
	}

	@Override
	public Type type() {
		return TYPE;
	}
}
