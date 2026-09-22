package dev.emi.emi.platform.fabric;

import java.util.function.Consumer;

import dev.emi.emi.data.ContextIntValues;
import dev.emi.emi.network.ContextIntValuesS2CPacket;
import dev.emi.emi.network.EmiNetwork;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/**
 * Sends the resolved number provider values while the client is still configuring, so they are in
 * place before tags and recipes arrive and the single reload per join still covers everything.
 */
public record EmiContextValuesTask(ServerConfigurationPacketListenerImpl handler, MinecraftServer server)
		implements ConfigurationTask {
	public static final Type TYPE = new Type(EmiNetwork.CONTEXT_INT_VALUES.id().toString());

	@Override
	public void start(Consumer<Packet<?>> sender) {
		sender.accept(ServerConfigurationNetworking.createClientboundPacket(
			new ContextIntValuesS2CPacket(ContextIntValues.computeFor(server))));
		((FabricServerConfigurationPacketListenerImpl) handler).completeTask(TYPE);
	}

	@Override
	public Type type() {
		return TYPE;
	}
}
