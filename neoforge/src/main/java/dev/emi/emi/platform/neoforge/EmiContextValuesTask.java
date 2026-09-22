package dev.emi.emi.platform.neoforge;

import java.util.function.Consumer;

import dev.emi.emi.data.ContextIntValues;
import dev.emi.emi.network.ContextIntValuesS2CPacket;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.runtime.EmiLog;
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
		// Completed whatever happens: a task that fails to send and never finishes leaves the
		// client stuck in the configuration phase forever.
		try {
			// The configuration listener does not expose its server, and there is only ever one.
			sender.accept(new ContextIntValuesS2CPacket(ContextIntValues.get(ServerLifecycleHooks.getCurrentServer())));
		} catch (Throwable t) {
			EmiLog.error("Could not send the number provider values", t);
		} finally {
			listener.finishCurrentTask(type());
		}
	}

	@Override
	public Type type() {
		return TYPE;
	}
}
