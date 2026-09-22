package dev.emi.emi.platform.fabric;

import java.util.function.BiConsumer;
import java.util.function.Function;

import dev.emi.emi.data.ContextIntValues;
import dev.emi.emi.network.CommandS2CPacket;
import dev.emi.emi.network.ContextIntValuesS2CPacket;
import dev.emi.emi.network.CreateItemC2SPacket;
import dev.emi.emi.network.EmiChessPacket;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.EmiPacket;
import dev.emi.emi.network.FillRecipeC2SPacket;
import dev.emi.emi.network.PingS2CPacket;
import dev.emi.emi.platform.EmiMain;
import dev.emi.emi.registry.EmiCommands;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public class EmiMainFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		EmiMain.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> EmiCommands.registerCommands(dispatcher));

		EmiNetwork.initServer(ServerPlayNetworking::send);

		registerPacketReader(EmiNetwork.FILL_RECIPE, FillRecipeC2SPacket::new);
		registerPacketReader(EmiNetwork.CREATE_ITEM, CreateItemC2SPacket::new);
		registerPacketReader(EmiNetwork.CHESS, EmiChessPacket.C2S::new);

		// The resolved number provider values go out in the configuration phase, and again in the
		// play phase after a datapack reload, so the type is registered for both.
		PayloadTypeRegistry.clientboundConfiguration().register(EmiNetwork.CONTEXT_INT_VALUES, ContextIntValuesS2CPacket.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EmiNetwork.CONTEXT_INT_VALUES, ContextIntValuesS2CPacket.STREAM_CODEC);
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			// A vanilla client never announces the channel and must not be sent anything
			if (ServerConfigurationNetworking.canSend(handler, EmiNetwork.CONTEXT_INT_VALUES)) {
				((FabricServerConfigurationPacketListenerImpl) handler).addTask(new EmiContextValuesTask(handler, server));
			}
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (!success) {
				return;
			}
			ContextIntValuesS2CPacket packet = new ContextIntValuesS2CPacket(ContextIntValues.computeFor(server));
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ServerPlayNetworking.canSend(player, EmiNetwork.CONTEXT_INT_VALUES)) {
					ServerPlayNetworking.send(player, packet);
				}
			}
		});

		PayloadTypeRegistry.clientboundPlay().register(EmiNetwork.PING, StreamCodec.<RegistryFriendlyByteBuf, PingS2CPacket>of((buf, v) -> v.write(buf), PingS2CPacket::new));
		PayloadTypeRegistry.clientboundPlay().register(EmiNetwork.COMMAND, StreamCodec.<RegistryFriendlyByteBuf, CommandS2CPacket>of((buf, v) -> v.write(buf), CommandS2CPacket::new));
		PayloadTypeRegistry.clientboundPlay().register(EmiNetwork.CHESS, StreamCodec.<RegistryFriendlyByteBuf, EmiChessPacket>of((buf, v) -> v.write(buf), buf -> new EmiChessPacket.S2C(buf)));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			EmiNetwork.sendToClient(handler.player, new PingS2CPacket());
		});

		registerVanillaRecipeSerializers();
	}

	/**
	 * EMI shows every recipe it can, so it asks Fabric to synchronize every recipe serializer.
	 * <p>
	 * This runs in the main entrypoint, which is loaded on both the client and the server, and the
	 * serializers are kept in a set that is only read once the configuration phase negotiates the
	 * synchronized serializers, so registering here covers both sides exactly once. Iterating the
	 * registry directly would miss serializers registered by mods that initialize after EMI, so
	 * {@link RegistryEntryAddedCallback#allEntries} is used to also catch later additions.
	 */
	private void registerVanillaRecipeSerializers() {
		RegistryEntryAddedCallback.allEntries(BuiltInRegistries.RECIPE_SERIALIZER,
			entry -> RecipeSynchronization.synchronizeRecipeSerializer(entry.value()));
	}

	private <T extends EmiPacket> void registerPacketReader(CustomPacketPayload.Type<T> id, StreamDecoder<RegistryFriendlyByteBuf, T> decode) {
		PayloadTypeRegistry.serverboundPlay().register(id, StreamCodec.of((buf, v) -> v.write(buf), decode));
		ServerPlayNetworking.registerGlobalReceiver(id, (payload, context) -> {
			context.player().level().getServer().execute(() -> {
				((EmiPacket)payload).apply(context.player());
			});
		});
	}
}