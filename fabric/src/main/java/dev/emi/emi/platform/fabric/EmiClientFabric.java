package dev.emi.emi.platform.fabric;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.mojang.serialization.Lifecycle;

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
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

public class EmiClientFabric implements ClientModInitializer {
	/** Warn once per connection that the server sent no recipes, not once per datapack reload. */
	private static boolean warnedAboutMissingRecipes = false;

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
			EmiAgnosFabric.setPendingRecipeMap(toRecipeMap(recipes.recipes()));
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
			EmiAgnosFabric.consumePendingRecipeMap();
			warnedAboutMissingRecipes = false;
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
	 * <p>
	 * The recorded map is consumed rather than read, so it only applies to the sync it arrived with:
	 * a {@code /reload} whose sync sends no payload falls back to an empty map instead of silently
	 * keeping the pre-reload one. See D-F4b.2.
	 */
	public static void onVanillaRecipesReceived() {
		RecipeMap map = EmiAgnosFabric.consumePendingRecipeMap();
		if (map == null) {
			// Nothing was synchronized for *this* packet. The previously recorded map, if any,
			// describes the state before this reload, so it must not be reused.
			if (!warnedAboutMissingRecipes) {
				warnedAboutMissingRecipes = true;
				EmiLog.warn("The server did not synchronize any recipes with EMI. Crafting recipes will"
					+ " be unavailable; everything EMI derives from the client (the item index, tags,"
					+ " world interactions, fuels, brewing, ...) still works. This happens on a vanilla"
					+ " server, or on a server whose Fabric API does not synchronize recipe serializers.");
			}
			map = RecipeMap.EMPTY;
		}
		EmiAgnosFabric.setReceivedRecipeMap(map);
		EmiReloadManager.reloadRecipes();
	}

	/**
	 * Turns the recipes Fabric synchronized into a {@link RecipeMap}.
	 * <p>
	 * 26.3 made recipes a datapack registry, and {@code RecipeMap.create} now takes a
	 * {@code HolderLookup} instead of the plain collection the sync event hands out, so the
	 * recipes are put into a throwaway registry first. NeoForge patches in its own
	 * {@code RecipeMap.createClient} for this; Fabric has no equivalent.
	 */
	private static RecipeMap toRecipeMap(Collection<RecipeHolder<?>> recipes) {
		MappedRegistry<Recipe<?>> registry = new MappedRegistry<>(Registries.RECIPE, Lifecycle.experimental());
		for (RecipeHolder<?> holder : recipes) {
			try {
				registry.register(holder.id(), holder.value(), RegistrationInfo.BUILT_IN);
			} catch (Exception e) {
				// A duplicate id or a recipe instance shared between two ids; skipping the one
				// entry is better than losing every recipe
				EmiLog.error("Could not record the synchronized recipe " + holder.id().identifier(), e);
			}
		}
		return RecipeMap.create(registry.freeze());
	}

	private <T extends EmiPacket> void registerPacketReader(CustomPacketPayload.Type<T> id, StreamDecoder<RegistryFriendlyByteBuf, T> decode) {
		ClientPlayNetworking.registerGlobalReceiver(id, (payload, context) -> {
			context.client().execute(() -> {
				payload.apply(context.client().player);
			});
		});
	}
}
