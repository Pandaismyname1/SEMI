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
import dev.emi.emi.runtime.EmiReloadManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;

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

		registerVanillaRecipeSerializers();

		ClientRecipeSynchronizedEvent.EVENT.register((client, recipes) -> {
			EmiAgnosFabric.setReceivedRecipeMap(RecipeMap.create(recipes.recipes()));
			EmiReloadManager.reloadRecipes();
			EmiReloadManager.reloadTags();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			EmiAgnosFabric.setReceivedRecipeMap(null);
		});
	}

	private void registerVanillaRecipeSerializers() {
		for (RecipeSerializer<?> serializer : BuiltInRegistries.RECIPE_SERIALIZER) {
			RecipeSynchronization.synchronizeRecipeSerializer(serializer);
		}
	}

	private <T extends EmiPacket> void registerPacketReader(CustomPacketPayload.Type<T> id, StreamDecoder<RegistryFriendlyByteBuf, T> decode) {
		ClientPlayNetworking.registerGlobalReceiver(id, (payload, context) -> {
			context.client().execute(() -> {
				payload.apply(context.client().player);
			});
		});
	}
}
