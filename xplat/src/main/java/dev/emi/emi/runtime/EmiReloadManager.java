package dev.emi.emi.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import dev.emi.emi.jemi.JemiPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.jemi.JemiPlugin;
import dev.emi.emi.mixinsupport.EmiMixinTransformation;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.registry.EmiComparisonDefaults;
import dev.emi.emi.registry.EmiDragDropHandlers;
import dev.emi.emi.registry.EmiExclusionAreas;
import dev.emi.emi.registry.EmiIngredientSerializers;
import dev.emi.emi.registry.EmiInitRegistryImpl;
import dev.emi.emi.registry.EmiPluginContainer;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.registry.EmiRegistryImpl;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.registry.EmiStackProviders;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.search.EmiSearch;

public class EmiReloadManager {
	private static int loadedResourcesMask = 0;
	private static volatile boolean clear = false, restart = false;
	// 0 - empty, 1 - reloading, 2 - loaded, -1 - error
	private static volatile int status = 0;
	private static volatile Thread thread;
	/** Server data outside the tags/recipes handshake changed and no reload has used it yet. */
	private static volatile boolean pendingDataChange = false;
	/**
	 * How long a pending data change waits for a handshake to pick it up before reloading on its
	 * own. NeoForge sends the values before the tags and recipes, Fabric after them, so the wait
	 * costs nothing on NeoForge and two seconds on Fabric, where a reload is due anyway.
	 */
	private static final int DATA_CHANGE_GRACE_TICKS = 40;
	/**
	 * However often the grace is renewed, a change waits no longer than this for a handshake. A
	 * half finished handshake, or a server that changes values faster than the grace runs out,
	 * would otherwise defer the reload for the whole session.
	 */
	private static final long DATA_CHANGE_DEADLINE_MS = 30_000;
	private static volatile int dataChangeGrace = 0;
	private static volatile long dataChangeDeadline = 0;
	public static volatile Component reloadStep = EmiPort.literal("");
	public static volatile long reloadWorry = Long.MAX_VALUE;

	static {
		EmiMixinTransformation.preach();
	}

	public static synchronized void reloadTags() {
		loadedResourcesMask |= 1;
		if (loadedResourcesMask == 3) {
			EmiLog.info("Tags synchronized, reloading EMI");
			loadedResourcesMask = 0;
			reload();
		} else {
			EmiLog.info("Tags synchronized, waiting for recipes to reload EMI...");
		}
	}

	public static synchronized void reloadRecipes() {
		loadedResourcesMask |= 2;
		if (loadedResourcesMask == 3) {
			EmiLog.info("Recipes synchronized, reloading EMI");
			loadedResourcesMask = 0;
			reload();
		} else {
			EmiLog.info("Recipes synchronized, waiting for tags to reload EMI...");
		}
	}

	/**
	 * A change to server data that is not one of the two halves of the tags/recipes handshake, such
	 * as new number provider values after a datapack reload.
	 * <p>
	 * This never reloads by itself, and never touches {@link #loadedResourcesMask}: that mask is
	 * the handshake's own state, and adding a bit to it out of band would leave it stuck and throw
	 * every later reload off by one half. It only records that a reload is owed. The data is stored
	 * before this is called, so a handshake that follows — which is the normal case on NeoForge,
	 * where the values are sent before the tags and recipes — reloads with the new data anyway and
	 * clears the debt; only when no handshake turns up does {@link #clientTick()} pay it.
	 */
	public static synchronized void reloadForDataChange() {
		if (!pendingDataChange) {
			dataChangeDeadline = System.currentTimeMillis() + DATA_CHANGE_DEADLINE_MS;
		}
		pendingDataChange = true;
		dataChangeGrace = DATA_CHANGE_GRACE_TICKS;
		EmiLog.info("Server data changed, waiting to see whether a reload is already coming");
	}

	/**
	 * Pays off a pending data change once it is clear that no handshake is going to. Called once per
	 * client tick by both loaders.
	 */
	public static synchronized void clientTick() {
		if (!pendingDataChange) {
			return;
		}
		boolean overdue = System.currentTimeMillis() >= dataChangeDeadline;
		if (!overdue) {
			if (loadedResourcesMask != 0) {
				// A handshake is under way; the reload it ends with consumes the change.
				dataChangeGrace = DATA_CHANGE_GRACE_TICKS;
				return;
			}
			if (dataChangeGrace > 0) {
				dataChangeGrace--;
				return;
			}
		}
		pendingDataChange = false;
		// status is not per connection and the worker may still be finishing after a disconnect,
		// so the level is what says whether a reload can do anything at all. It is null on the
		// title screen, between a disconnect and the next join, and while the server has sent the
		// client back to the configuration phase.
		if (Minecraft.getInstance().level == null || status == 0) {
			return;
		}
		EmiLog.info("Server data changed and no reload followed, reloading EMI");
		reload();
	}

	public static void clear() {
		synchronized (EmiReloadManager.class) {
			loadedResourcesMask = 0;
			pendingDataChange = false;
			dataChangeGrace = 0;
			dataChangeDeadline = 0;
			clear = true;
			status = 0;
			reloadWorry = Long.MAX_VALUE;
			if (thread != null && thread.isAlive()) {
				restart = true;
			} else {
				thread = new Thread(new ReloadWorker());
				thread.setDaemon(true);
				thread.start();
			}
		}
	}
	
	public static void reload() {
		synchronized (EmiReloadManager.class) {
			// Whatever this reload was asked for, it reads the current data, so any change waiting
			// for a reload is paid off by it.
			pendingDataChange = false;
			dataChangeGrace = 0;
			dataChangeDeadline = 0;
			step(EmiPort.literal("Starting Reload"));
			status = 1;
			if (thread != null && thread.isAlive()) {
				restart = true;
			} else {
				clear = false;
				thread = new Thread(new ReloadWorker());
				thread.setDaemon(false);
				thread.start();
			}
		}
	}

	public static void step(Component text) {
		step(text, 5_000);
	}

	public static void step(Component text, long worry) {
		EmiLog.info(text.getString());
		reloadStep = text;
		reloadWorry = System.currentTimeMillis() + worry;
	}

	public static boolean isLoaded() {
		return status == 2 && (thread == null || !thread.isAlive());
	}

	public static int getStatus() {
		return status;
	}
	
	/**
	 * Whether the worker should go round again. Clearing {@code thread} has to happen under the
	 * same lock that reads it, or a {@link #reload()} landing in the gap would set {@code restart}
	 * on a thread that is about to die and be lost.
	 */
	/**
	 * A pass that cannot run at all (no world, no recipe manager) must not leave the status at
	 * "reloading": nothing else would reset it and EMI would stay disabled. Publish the error
	 * status instead, unless a clear or restart is already pending; those decide the status
	 * themselves.
	 */
	private static synchronized void giveUp() {
		if (!clear && !restart) {
			status = -1;
		}
	}

	private static synchronized boolean restartOrFinish() {
		if (restart) {
			return true;
		}
		thread = null;
		return false;
	}

	private static class ReloadWorker implements Runnable {

		@Override
		public void run() {
			int retries = 3;
			outer:
			do {
				try {
					if (!clear) {
						EmiLog.info("Starting EMI reload...");
					}
					long reloadStart = System.currentTimeMillis();
					restart = false;
					step(EmiPort.literal("Clearing data"));
					EmiRecipes.clear();
					EmiStackList.clear();
					EmiIngredientSerializers.clear();
					EmiExclusionAreas.clear();
					EmiDragDropHandlers.clear();
					EmiStackProviders.clear();
					EmiRecipeFiller.clear();
					EmiHidden.clear();
					EmiTags.ADAPTERS_BY_CLASS.map().clear();
					EmiTags.ADAPTERS_BY_REGISTRY.clear();
					EmiScreenBase.clearScreenBoundsProviders();
					if (clear) {
						clear = false;
						continue;
					}
					Minecraft client = Minecraft.getInstance();
					// These give up on this pass, but they must still go through the loop
					// condition: breaking out of the loop skips restartOrFinish(), which would
					// lose a reload() that asked for a restart while this pass was running and
					// leave `thread` pointing at a dead thread with the status stuck at 1.
					if (client.level == null) {
						EmiReloadLog.warn("World is null");
						giveUp();
						continue;
					} else if (!ProxyRecipeManager.isAvailable()) {
						EmiReloadLog.warn("Recipe Manager is null");
						giveUp();
						continue;
					}
					List<EmiPluginContainer> plugins = Lists.newArrayList();
					plugins.addAll(EmiAgnos.getPlugins().stream()
						.sorted((a, b) -> Integer.compare(entrypointPriority(a), entrypointPriority(b))).toList());
					
					if (EmiAgnos.isModLoaded("jei")) {
						plugins.add(new EmiPluginContainer(new JemiPlugin(), "jemi"));
					}
					EmiInitRegistry initRegistry = new EmiInitRegistryImpl();
					for (EmiPluginContainer container : plugins) {
						step(EmiPort.literal("Initializing plugin from " + container.id()), 5_000);
						long start = System.currentTimeMillis();
						try {
							container.plugin().initialize(initRegistry);
						} catch (Throwable e) {
							EmiReloadLog.warn("Exception initializing plugin provided by " + container.id(), e);
							if (restart) {
								continue outer;
							}
							continue;
						}
						EmiLog.info("Initialized plugin from " + container.id() + " in " + (System.currentTimeMillis() - start) + "ms");
					}
					EmiHidden.reload();

					step(EmiPort.literal("Processing tags"));
					EmiTags.reload();

					step(EmiPort.literal("Constructing index"));
					EmiComparisonDefaults.comparisons = new HashMap<>();
					EmiStackList.reload();
					if (restart) {
						continue;
					}
					EmiRegistry registry = new EmiRegistryImpl();
					
					for (EmiPluginContainer container : plugins) {
						step(EmiPort.literal("Loading plugin from " + container.id()), 10_000);
						long start = System.currentTimeMillis();
						try {
							container.plugin().register(registry);
						} catch (Throwable e) {
							EmiReloadLog.warn("Exception loading plugin provided by " + container.id(), e);
							if (restart) {
								continue outer;
							}
							continue;
						}
						EmiLog.info("Reloaded plugin from " + container.id() + " in " + (System.currentTimeMillis() - start) + "ms");
						if (restart) {
							continue outer;
						}
					}
					if (restart) {
						continue;
					}
					step(EmiPort.literal("Baking index"));
					EmiStackList.bake();
					step(EmiPort.literal("Registering late recipes"), 10_000);
					Consumer<EmiRecipe> registerLateRecipe = registry::addRecipe;
					for (Consumer<Consumer<EmiRecipe>> consumer : EmiRecipes.lateRecipes) {
						try {
							consumer.accept(registerLateRecipe);
						} catch (Exception e) {
							EmiReloadLog.warn("Exception loading late recipes for plugins:", e);
							if (restart) {
								continue outer;
							}
						}
					}
					step(EmiPort.literal("Baking recipes"), 15_000);
					EmiRecipes.bake();
					BoM.reload();
					EmiPersistentData.load();
					step(EmiPort.literal("Baking search"), 15_000);
					EmiSearch.bake();
					step(EmiPort.literal("Finishing up"));
					EmiScreenManager.search.update();
					EmiScreenManager.forceRecalculate();
					EmiReloadLog.bake();
					EmiLog.info("Reloaded EMI in " + (System.currentTimeMillis() - reloadStart) + "ms");
					// Under the lock and only when nothing has asked for a restart or a clear since:
					// clear() resets the status to 0 while this loop is in its unchecked tail, and
					// publishing 2 over that would leak "loaded" onto the title screen.
					synchronized (EmiReloadManager.class) {
						if (!restart && !clear) {
							status = 2;
						}
					}
				} catch (Throwable e) {
					EmiReloadLog.warn("Critical error occured during reload:", e);
					// Same reasoning as the status = 2 above: a clear() that landed while this pass
					// was failing has already reset the status, and publishing over it would leak
					// a stale status onto the title screen.
					synchronized (EmiReloadManager.class) {
						if (!clear) {
							status = -1;
						}
					}
					if (retries-- > 0) {
						restart = true;
					}
				}
			} while (restartOrFinish());
		}

		private final static int entrypointPriority(EmiPluginContainer container) {
			return container.id().equals("emi") ? 0 : 1;
		}
	}
}
