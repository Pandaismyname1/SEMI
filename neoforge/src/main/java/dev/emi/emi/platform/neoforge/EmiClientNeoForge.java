package dev.emi.emi.platform.neoforge;

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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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

	@SubscribeEvent
	public static void clientInit(FMLClientSetupEvent event) {
		EmiClient.init();
		EmiNetwork.initClient(packet -> ClientPacketDistributor.sendToServer(EmiPacketHandler.wrap(packet)));
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::tagsReloaded);
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::recipesReceived);
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::playerLoggedOut);
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
	 * NeoForge fires this once per vanilla recipe packet on every kind of connection: on a NeoForge
	 * server from its {@code RecipeContentPayload} handler, and on any other server (vanilla, Paper,
	 * Fabric) inline from {@code ClientHooks.handleUpdateRecipes} with an empty map, since
	 * {@code CommonHooks.sendRecipes} only sends the payload to NeoForge connections. So it is the
	 * single "recipes half" trigger and no vanilla-packet fallback is needed (see D-F4c in the
	 * decision log). EMI still records an empty map and reloads, so the item index, tags and
	 * everything EMI derives from the client keep working, but it says once why there are no
	 * crafting recipes.
	 */
	public static void recipesReceived(RecipesReceivedEvent event) {
		if (event.getRecipeTypes().isEmpty() && !warnedAboutEmptyRecipes) {
			warnedAboutEmptyRecipes = true;
			EmiLog.warn("The server did not synchronize any recipes with EMI. Crafting recipes and"
				+ " brewing, which is a recipe type as of 26.3, will be unavailable; everything EMI"
				+ " derives from the client (the item index, tags, world interactions, ...) still"
				+ " works. Fuel burn times and composting chances need the server to run SEMI as well,"
				+ " or a singleplayer or LAN host world. This happens on any server that does not run"
				+ " NeoForge with EMI installed.");
		}
		EmiAgnosNeoForge.setReceivedRecipeMap(event.getRecipeMap());
		EmiReloadManager.reloadRecipes();
	}

	public static void playerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
		EmiAgnosNeoForge.setReceivedRecipeMap(null);
		warnedAboutEmptyRecipes = false;
	}

	/**
	 * 26.2 removed {@code ContainerScreenEvent} and folded its foreground event into
	 * {@link ScreenEvent.Render.Foreground} (neoforged/NeoForge#3342), which now fires for every
	 * screen, so container screens have to be picked out by hand. It is also posted from
	 * {@code AbstractContainerScreen.extractRenderState} <i>after</i> {@code extractContents}
	 * rather than from inside it, so the pose is no longer translated by the container's
	 * {@code leftPos}/{@code topPos} and EMI must not translate it back.
	 */
	public static void renderScreenForeground(ScreenEvent.Render.Foreground event) {
		if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
			return;
		}
		EmiDrawContext context = EmiDrawContext.wrap(event.getGuiGraphics());
		EmiScreenBase base = EmiScreenBase.of(event.getScreen());
		if (base != null) {
			context.push();
			EmiScreenManager.render(context, event.getMouseX(), event.getMouseY(), event.getPartialTick());
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
			context.push();
			EmiScreenManager.drawForeground(context, event.getMouseX(), event.getMouseY(), event.getPartialTick());
			context.pop();
		}
		context.flushDeferredTooltips();
	}
}
