package dev.emi.emi.platform.neoforge;

import java.util.Arrays;

import dev.emi.emi.EmiPort;
import dev.emi.emi.data.EmiData;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.platform.EmiClient;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.StackBatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.crafting.RecipeMap;

@EventBusSubscriber(modid = "emi", value = Dist.CLIENT)
public class EmiClientNeoForge {
	/** Warn once per connection that the server sent no recipes, not once per datapack reload. */
	private static boolean warnedAboutEmptyRecipes = false;
	/** Warn once per connection that the server never sent a recipe payload at all. */
	private static boolean warnedAboutMissingRecipes = false;
	/**
	 * How long the vanilla recipe packet waits for NeoForge's {@code RecipeContentPayload} before
	 * giving up. The payload is written to the same connection immediately after the vanilla packet,
	 * so a second is a very generous bound; it exists only so a server that never sends one cannot
	 * leave EMI waiting forever.
	 */
	private static final int RECIPE_FALLBACK_TICKS = 20;
	/**
	 * Ticks remaining on the pending fallback, or -1 when none is armed. Client thread only: the
	 * vanilla packet handler, NeoForge's payload handler and {@code ClientTickEvent.Post} all run
	 * there.
	 */
	private static int recipeFallbackTicks = -1;

	@SubscribeEvent
	public static void clientInit(FMLClientSetupEvent event) {
		StackBatcher.EXTRA_RENDER_LAYERS.addAll(Arrays.stream(NeoForgeRenderTypes.values()).map(f -> f.get()).toList());
		EmiClient.init();
		EmiNetwork.initClient(packet -> ClientPacketDistributor.sendToServer(EmiPacketHandler.wrap(packet)));
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::tagsReloaded);
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::recipesReceived);
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::playerLoggedOut);
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::clientTick);
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::renderScreenForeground);
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::postRenderScreen);
		ModList.get().getModContainerById("emi").orElseThrow().registerExtensionPoint(IConfigScreenFactory.class,
				(container, last) -> new ConfigScreen(last));
	}

	@SubscribeEvent
	public static void registerResourceReloaders(AddClientReloadListenersEvent event) {
		EmiData.init(reloader -> event.addListener(reloader.getEmiId(), reloader));
	}

	/**
	 * EMI reloads once both halves of the server's data have arrived, so each event must only
	 * report its own half. Tags arrive during the configuration phase, before a level exists, so
	 * this deliberately does not check {@code client.level}; the reload worker checks it instead.
	 * Only the client-packet variant is listened to, since the integrated server also fires
	 * {@link TagsUpdatedEvent.ServerDataLoad} on the same bus.
	 */
	public static void tagsReloaded(TagsUpdatedEvent.ClientPacketReceived event) {
		EmiReloadManager.reloadTags();
	}

	/**
	 * NeoForge fires this unconditionally once the client has received the server's recipe data,
	 * with an empty map when no mod on the server asked for any recipe type. EMI still records the
	 * map and reloads in that case, so the item index, tags and everything EMI derives from the
	 * client keep working, but it says once why there are no crafting recipes.
	 */
	public static void recipesReceived(RecipesReceivedEvent event) {
		// Authoritative: the server really did synchronize, so drop the pending fallback.
		recipeFallbackTicks = -1;
		if (event.getRecipeTypes().isEmpty() && !warnedAboutEmptyRecipes) {
			warnedAboutEmptyRecipes = true;
			EmiLog.warn("The server did not synchronize any recipes with EMI. Crafting recipes will"
				+ " be unavailable; everything EMI derives from the client (the item index, tags,"
				+ " world interactions, fuels, brewing, ...) still works. This happens on a server"
				+ " that does not have EMI installed.");
		}
		EmiAgnosNeoForge.setReceivedRecipeMap(event.getRecipeMap());
		EmiReloadManager.reloadRecipes();
	}

	/**
	 * Arms the bounded fallback for the recipes half, called from {@code ClientPacketListenerMixin}
	 * once the vanilla {@code ClientboundUpdateRecipesPacket} has been handled.
	 * <p>
	 * NeoForge's patched {@code PlayerList} calls {@code CommonHooks.sendRecipes} <em>after</em>
	 * sending the vanilla packet, in both {@code placeNewPlayer} and {@code reloadResources}, so
	 * unlike Fabric this cannot report the half directly - the real map is still in flight. It only
	 * starts a countdown, which {@link #recipesReceived} cancels as soon as the payload lands. See
	 * D-F4b.1 in the decision log.
	 */
	public static void onVanillaRecipesReceived() {
		recipeFallbackTicks = RECIPE_FALLBACK_TICKS;
	}

	/**
	 * Expires the pending fallback. If nothing was ever recorded for this connection - a vanilla,
	 * Paper or Fabric server, where {@code CommonHooks.sendRecipes} sends no payload at all - EMI
	 * installs an empty map so the item index, tags and everything else derived from the client
	 * still load. If a map is already recorded (a {@code /reload} on such a server re-sends the
	 * vanilla packet while the empty map from the join is still in place) the reload is reported
	 * with it unchanged.
	 */
	public static void clientTick(ClientTickEvent.Post event) {
		if (recipeFallbackTicks < 0) {
			return;
		}
		if (--recipeFallbackTicks >= 0) {
			return;
		}
		recipeFallbackTicks = -1;
		if (!EmiAgnosNeoForge.hasReceivedRecipeMap()) {
			if (!warnedAboutMissingRecipes) {
				warnedAboutMissingRecipes = true;
				EmiLog.warn("The server is not synchronizing recipes with EMI. Crafting recipes will"
					+ " be unavailable; everything EMI derives from the client (the item index, tags,"
					+ " world interactions, fuels, brewing, ...) still works. This happens on any"
					+ " server that does not run NeoForge, since NeoForge only sends its recipe"
					+ " payload to a NeoForge connection.");
			}
			EmiAgnosNeoForge.setReceivedRecipeMap(RecipeMap.EMPTY);
		}
		EmiReloadManager.reloadRecipes();
	}

	public static void playerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
		EmiAgnosNeoForge.setReceivedRecipeMap(null);
		recipeFallbackTicks = -1;
		warnedAboutEmptyRecipes = false;
		warnedAboutMissingRecipes = false;
	}

	public static void renderScreenForeground(ContainerScreenEvent.Render.Foreground event) {
		EmiDrawContext context = EmiDrawContext.wrap(event.getGuiGraphics());
		AbstractContainerScreen<?> screen = event.getContainerScreen();
		EmiScreenBase base = EmiScreenBase.of(screen);
		if (base != null) {
			Minecraft client = Minecraft.getInstance();
			context.push();
			context.translate(-screen.getLeftPos(), -screen.getTopPos());
			EmiScreenManager.render(context, event.getMouseX(), event.getMouseY(), client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
			context.pop();
		}
	}

	public static void postRenderScreen(ScreenEvent.Render.Post event) {
		EmiDrawContext context = EmiDrawContext.wrap(event.getGuiGraphics());
		Screen screen = event.getScreen();
		if (!(screen instanceof AbstractContainerScreen<?>)) {
			return;
		}
		EmiScreenBase base = EmiScreenBase.of(screen);
		if (base != null) {
			Minecraft client = Minecraft.getInstance();
			context.push();
			EmiScreenManager.drawForeground(context, event.getMouseX(), event.getMouseY(), client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
			context.pop();
		}
		context.flushDeferredTooltips();
	}
}
