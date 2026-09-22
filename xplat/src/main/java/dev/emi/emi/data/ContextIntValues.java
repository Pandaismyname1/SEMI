package dev.emi.emi.data;

import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

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
	private static volatile boolean anyReceived = false;
	private static volatile @Nullable Runnable changeListener;
	/**
	 * Server side: the values for the datapacks currently loaded, computed once rather than once
	 * per joining client. Recomputed when the server starts and after every datapack reload.
	 */
	private static volatile @Nullable Map<Identifier, Float> cached;

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

	/**
	 * Whether a server sent values at all this connection, which is not the same as having any: a
	 * server running SEMI whose providers EMI cannot estimate sends an empty map, and blaming that
	 * on "a server without SEMI" would be wrong.
	 */
	public static boolean wasReceived() {
		return anyReceived;
	}

	public static void clear() {
		received = Map.of();
		anyReceived = false;
	}

	/**
	 * Installed by the client, and only by the client, so that a change to the values can trigger a
	 * reload. The listener decides for itself whether a reload is due: values arriving before EMI
	 * has loaded anything, or while it is waiting for the rest of the server's data, need none.
	 */
	public static void setChangeListener(@Nullable Runnable listener) {
		changeListener = listener;
	}

	/**
	 * Stores the values a server sent and tells the listener if they changed. Safe to call from any
	 * thread and in either connection phase.
	 *
	 * @return whether the values differ from the ones already stored
	 */
	public static boolean set(Map<Identifier, Float> values) {
		boolean changed = !received.equals(values) || !anyReceived;
		received = Map.copyOf(values);
		anyReceived = true;
		if (changed) {
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
	 * The values for the datapacks the server has loaded, computing them if this is the first time
	 * they are asked for. Every joining client gets the same map, so the work and the logging
	 * happen once per datapack state rather than once per player.
	 */
	public static Map<Identifier, Float> get(MinecraftServer server) {
		Map<Identifier, Float> values = cached;
		if (values == null) {
			values = computeFor(server);
			cached = values;
		}
		return values;
	}

	/** Recomputes and caches, for server start and datapack reloads. */
	public static Map<Identifier, Float> refresh(MinecraftServer server) {
		Map<Identifier, Float> values = computeFor(server);
		cached = values;
		return values;
	}

	/** Called when a server stops, so the next one does not inherit its values. */
	public static void forgetServerValues() {
		cached = null;
	}

	/**
	 * Evaluates every number provider the server has loaded. Runs on the server, on the thread the
	 * caller is on; the registry is frozen by the time any of this can be reached.
	 */
	private static Map<Identifier, Float> computeFor(MinecraftServer server) {
		Map<Identifier, Float> values = Maps.newLinkedHashMap();
		Set<String> unhandledTypes = Sets.newLinkedHashSet();
		Set<Identifier> skipped = Sets.newLinkedHashSet();
		boolean[] truncated = new boolean[1];
		try {
			HolderLookup.RegistryLookup<ContextIntProvider> providers = server.reloadableRegistries().lookup()
				.lookup(Registries.CONTEXT_INT_PROVIDER).orElse(null);
			if (providers == null) {
				EmiLog.warn("This server has no " + Registries.CONTEXT_INT_PROVIDER.identifier() + " registry,"
					+ " so clients cannot be told any fuel burn times or composting chances.");
				return Map.of();
			}
			providers.listElements().forEach(reference -> {
				if (values.size() >= MAX_ENTRIES) {
					truncated[0] = true;
					return;
				}
				// Per entry, so that one provider EMI cannot estimate does not make every other
				// value look unreliable
				Set<String> entryUnhandled = Sets.newLinkedHashSet();
				float value = ContextNumbers.expectedValue(reference.value(), entryUnhandled::add);
				if (entryUnhandled.isEmpty()) {
					values.put(reference.key().identifier(), value);
				} else {
					// Sending a zero would look like a resolved value and silence the client's own
					// fallbacks and warnings, so the entry is left out entirely
					unhandledTypes.addAll(entryUnhandled);
					skipped.add(reference.key().identifier());
				}
			});
		} catch (Throwable t) {
			// Deliberately Throwable: a datapack that makes the providers reference each other in a
			// loop would otherwise take the join or the reload worker down with a StackOverflowError
			EmiLog.error("Could not evaluate the number provider registry", t);
			return Map.of();
		}
		if (truncated[0]) {
			EmiLog.warn("This server has more than " + MAX_ENTRIES + " number providers; only the first "
				+ MAX_ENTRIES + " are sent to clients.");
		}
		if (!unhandledTypes.isEmpty()) {
			EmiLog.warn("EMI cannot estimate the value of the number provider type(s) "
				+ String.join(", ", unhandledTypes) + ", so " + skipped.size() + " provider(s) are not sent"
				+ " to clients: " + String.join(", ", skipped.stream().map(Identifier::toString).toList()));
		}
		if (values.isEmpty()) {
			EmiLog.info("No number provider resolved to a value, so clients get no fuel burn times or"
				+ " composting chances from this server.");
		}
		return values;
	}
}
