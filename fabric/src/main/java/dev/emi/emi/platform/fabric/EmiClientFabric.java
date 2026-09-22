package dev.emi.emi.platform.fabric;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import dev.emi.emi.data.EmiData;
import dev.emi.emi.network.CommandS2CPacket;
import dev.emi.emi.network.EmiChessPacket;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.EmiPacket;
import dev.emi.emi.network.PingS2CPacket;
import dev.emi.emi.platform.EmiClient;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.crafting.RecipeMap;

public class EmiClientFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EmiClient.init();
		EmiData.init(reloader -> {
			ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {

				@Override
				public CompletableFuture<Void> reload(PreparableReloadListener.SharedState var1,
						Executor var5, PreparableReloadListener.PreparationBarrier var7, Executor var6) {
					return reloader.reload(var1, var5, var7, var6);
				}

				@Override
				public String getName() {
					return reloader.getName();
				}

				@Override
				public Identifier getFabricId() {
					return reloader.getEmiId();
				}
			});
		});

		EmiNetwork.initClient(packet -> {
			if (ClientPlayNetworking.canSend(packet.type())) {
				ClientPlayNetworking.send(packet);
			}
		});

		registerPacketReader(EmiNetwork.PING, PingS2CPacket::new);
		registerPacketReader(EmiNetwork.COMMAND, CommandS2CPacket::new);
		registerPacketReader(EmiNetwork.CHESS, EmiChessPacket.S2C::new);

		// EMI reloads once both halves of the server's data have arrived, so each event must only
		// report its own half. This event only records the map; the "recipes" half is reported by
		// the vanilla recipe packet instead, see onVanillaRecipesReceived.
		ClientRecipeSynchronizedEvent.EVENT.register((client, recipes) -> {
			EmiAgnosFabric.setReceivedRecipeMap(RecipeMap.create(recipes.recipes()));
		});

		// Tags arrive during the configuration phase on join and in the play phase on /reload;
		// TAGS_LOADED with client == true is fired for both, so EMI does not hook either packet.
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
			if (client) {
				EmiReloadManager.reloadTags();
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			EmiAgnosFabric.setReceivedRecipeMap(null);
		});
	}

	/**
	 * Reports the "recipes" half of a reload, called from {@code ClientPlayNetworkHandlerMixin}
	 * once the vanilla {@code ClientboundUpdateRecipesPacket} has been handled.
	 * <p>
	 * Fabric's own {@code ClientRecipeSynchronizedEvent} cannot be the trigger, because Fabric
	 * silently sends no recipe payload at all when the client cannot receive it (vanilla server) or
	 * when the negotiated serializer set produced nothing. Waiting for it would leave EMI without
	 * any data on such a server. The vanilla packet, on the other hand, always arrives, and Fabric
	 * queues its payload strictly before it on both send paths, so anything the server was going to
	 * synchronize is already recorded by the time this runs. See D-F2b in the decision log.
	 */
	public static void onVanillaRecipesReceived() {
		if (!EmiAgnosFabric.hasReceivedRecipeMap()) {
			EmiLog.warn("The server did not synchronize any recipes with EMI. Crafting recipes will"
				+ " be unavailable; everything EMI derives from the client (the item index, tags,"
				+ " world interactions, fuels, brewing, ...) still works. This happens on a vanilla"
				+ " server, or on a server whose Fabric API does not synchronize recipe serializers.");
			EmiAgnosFabric.setReceivedRecipeMap(RecipeMap.EMPTY);
		}
		EmiReloadManager.reloadRecipes();
	}

	private <T extends EmiPacket> void registerPacketReader(CustomPacketPayload.Type<T> id, StreamDecoder<RegistryFriendlyByteBuf, T> decode) {
		ClientPlayNetworking.registerGlobalReceiver(id, (payload, context) -> {
			context.client().execute(() -> {
				payload.apply(context.client().player);
			});
		});
	}
}
