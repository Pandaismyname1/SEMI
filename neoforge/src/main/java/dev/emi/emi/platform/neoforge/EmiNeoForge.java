package dev.emi.emi.platform.neoforge;

import dev.emi.emi.data.ContextIntValues;
import dev.emi.emi.network.ContextIntValuesS2CPacket;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.PingS2CPacket;
import dev.emi.emi.platform.EmiMain;
import dev.emi.emi.registry.EmiCommands;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

@Mod("emi")
public class EmiNeoForge {

	public EmiNeoForge(IEventBus modEventBus) {
		EmiMain.init();
		modEventBus.addListener(EmiPacketHandler::init);
		EmiNetwork.initServer((player, packet) -> {
			if (player.connection.hasChannel(packet)) {
				PacketDistributor.sendToPlayer(player, EmiPacketHandler.wrap(packet));
			} else {
				EmiLog.warn("Can't send EMI packet to " + player + " as they're missing the channel");
			}
		});
		modEventBus.addListener(EmiNeoForge::registerConfigurationTasks);
		// Computed once per datapack state rather than once per joining client, so the evaluation
		// and everything it logs happen once.
		NeoForge.EVENT_BUS.addListener((ServerStartedEvent started) -> ContextIntValues.refresh(started.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerStoppedEvent stopped) -> ContextIntValues.forgetServerValues());
		NeoForge.EVENT_BUS.addListener(this::registerCommands);
		NeoForge.EVENT_BUS.addListener(this::playerConnect);
		NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
	}

	/**
	 * Sends the resolved number provider values while the client is still configuring, so they are
	 * in place before tags and recipes arrive and the single reload per join still covers
	 * everything. A vanilla client never announces the channel and is skipped.
	 */
	public static void registerConfigurationTasks(RegisterConfigurationTasksEvent event) {
		if (event.getListener().hasChannel(EmiNetwork.CONTEXT_INT_VALUES)) {
			event.register(new EmiContextValuesTask(event.getListener()));
		}
	}

	public void registerCommands(RegisterCommandsEvent event) {
		EmiCommands.registerCommands(event.getDispatcher());
	}

	public void playerConnect(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer spe) {
			EmiNetwork.sendToClient(spe, new PingS2CPacket());
		}
	}

	/**
	 * The requested recipe types apply to every player this event syncs to (on /reload that is the
	 * whole player list), so the full recipe payload can only be skipped when none of them can
	 * receive EMI's packets at all.
	 */
	public void onDatapackSync(OnDatapackSyncEvent event) {
		if (event.getRelevantPlayers().anyMatch(player -> player.connection.hasChannel(EmiNetwork.PING))) {
			event.sendRecipes(BuiltInRegistries.RECIPE_TYPE);
		}
		// A null player means /reload rather than a join, whose values the configuration task
		// already sent.
		if (event.getPlayer() == null) {
			ContextIntValuesS2CPacket packet = new ContextIntValuesS2CPacket(
				ContextIntValues.refresh(event.getPlayerList().getServer()));
			event.getRelevantPlayers()
				.filter(player -> player.connection.hasChannel(EmiNetwork.CONTEXT_INT_VALUES))
				.forEach(player -> PacketDistributor.sendToPlayer(player, packet));
		}
	}
}
