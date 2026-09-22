package dev.emi.emi.platform.fabric;

import java.util.function.BiConsumer;
import java.util.function.Function;

import dev.emi.emi.network.CommandS2CPacket;
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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class EmiMainFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		EmiMain.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> EmiCommands.registerCommands(dispatcher));

		EmiNetwork.initServer(ServerPlayNetworking::send);

		registerPacketReader(EmiNetwork.FILL_RECIPE, FillRecipeC2SPacket::new);
		registerPacketReader(EmiNetwork.CREATE_ITEM, CreateItemC2SPacket::new);
		registerPacketReader(EmiNetwork.CHESS, EmiChessPacket.C2S::new);

		PayloadTypeRegistry.clientboundPlay().register(EmiNetwork.PING, StreamCodec.<RegistryFriendlyByteBuf, PingS2CPacket>of((buf, v) -> v.write(buf), PingS2CPacket::new));
		PayloadTypeRegistry.clientboundPlay().register(EmiNetwork.COMMAND, StreamCodec.<RegistryFriendlyByteBuf, CommandS2CPacket>of((buf, v) -> v.write(buf), CommandS2CPacket::new));
		PayloadTypeRegistry.clientboundPlay().register(EmiNetwork.CHESS, StreamCodec.<RegistryFriendlyByteBuf, EmiChessPacket>of((buf, v) -> v.write(buf), buf -> new EmiChessPacket.S2C(buf)));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			EmiNetwork.sendToClient(handler.player, new PingS2CPacket());
		});

		registerVanillaRecipeSerializers();
	}

	private void registerVanillaRecipeSerializers() {
		for (RecipeSerializer<?> serializer : BuiltInRegistries.RECIPE_SERIALIZER) {
			RecipeSynchronization.synchronizeRecipeSerializer(serializer);
		}
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