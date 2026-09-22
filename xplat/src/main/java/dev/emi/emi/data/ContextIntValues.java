package dev.emi.emi.data;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Maps;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;

import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadLog;

/**
 * The expected value of every entry of the server's {@code minecraft:context_int_provider} registry.
 * <p>
 * That registry is reloadable, which means it is never synchronized to a client: without it a client
 * cannot tell how long a fuel burns or how likely an item is to raise a composter, because 26.3
 * defines both as references into it. A server running SEMI therefore evaluates the whole registry
 * once and sends the results, and a client that received them prefers them over anything it can work
 * out locally.
 * <p>
 * Common code: the map is filled on the server, sent over EMI's own channel and stored on the client.
 */
public class ContextIntValues {
	/** A datapack with more providers than this is not worth a packet; vanilla has 26. */
	public static final int MAX_ENTRIES = 8192;
	public static final String MISSING_NUMBER_PROVIDERS = "Burn times and composting chances are data"
		+ " driven numbers that live in a registry the server never synchronizes: a server that runs SEMI"
		+ " sends the values it resolved, and without one only a singleplayer or LAN host world can work"
		+ " them out.";

	private static volatile Map<Identifier, Float> received = Map.of();
	private static volatile @Nullable Runnable changeListener;

	/**
	 * The expected value the server computed for a provider, or null when the server sent nothing
	 * (no SEMI on the other side, or a vanilla server).
	 */
	public static @Nullable Float get(Identifier id) {
		return received.get(id);
	}

	public static boolean isEmpty() {
		return received.isEmpty();
	}

	public static void clear() {
		received = Map.of();
	}

	/**
	 * Installed by the client so that a value change arriving in the play phase, that is after a
	 * datapack reload, can trigger one EMI reload. Values arriving in the configuration phase are
	 * stored with {@code notify} false, before EMI has loaded anything.
	 */
	public static void setChangeListener(@Nullable Runnable listener) {
		changeListener = listener;
	}

	/**
	 * @return whether the values differ from the ones already stored
	 */
	public static boolean set(Map<Identifier, Float> values, boolean notify) {
		boolean changed = !received.equals(values);
		received = Map.copyOf(values);
		if (changed && notify) {
			Runnable listener = changeListener;
			if (listener != null) {
				listener.run();
			}
		}
		return changed;
	}

	/** One line per provider type EMI cannot estimate, per reload, rather than a silent zero. */
	public static void warnUnhandled(Set<String> types, String what) {
		if (!types.isEmpty()) {
			EmiReloadLog.warn("EMI cannot estimate " + what + " that use the number provider type(s) "
				+ String.join(", ", types) + "; those values are treated as zero.");
		}
	}

	/**
	 * Evaluates every number provider the server has loaded. Runs on the server, on the thread the
	 * caller is on; the registry is frozen by the time any of this can be reached.
	 */
	public static Map<Identifier, Float> computeFor(MinecraftServer server) {
		Map<Identifier, Float> values = Maps.newLinkedHashMap();
		try {
			HolderLookup.RegistryLookup<ContextIntProvider> providers = server.reloadableRegistries().lookup()
				.lookup(Registries.CONTEXT_INT_PROVIDER).orElse(null);
			if (providers == null) {
				return Map.of();
			}
			Consumer<String> unhandled = type -> {
			};
			providers.listElements().forEach(reference -> {
				if (values.size() < MAX_ENTRIES) {
					values.put(reference.key().identifier(),
						ContextNumbers.expectedValue(reference.value(), unhandled));
				}
			});
		} catch (Exception e) {
			EmiLog.error("Could not evaluate the number provider registry", e);
			return Map.of();
		}
		return values;
	}
}
