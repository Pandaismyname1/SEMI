package dev.emi.emi.data;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.storage.loot.providers.number.ConditionalProvider;
import net.minecraft.world.level.storage.loot.providers.number.DispatcherProvider;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.Absolute;
import net.minecraft.world.level.storage.loot.providers.number.ints.Average;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.Difference;
import net.minecraft.world.level.storage.loot.providers.number.ints.FloorModulus;
import net.minecraft.world.level.storage.loot.providers.number.ints.FloorQuotient;
import net.minecraft.world.level.storage.loot.providers.number.ints.FromFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.Maximum;
import net.minecraft.world.level.storage.loot.providers.number.ints.Minimum;
import net.minecraft.world.level.storage.loot.providers.number.ints.Modulus;
import net.minecraft.world.level.storage.loot.providers.number.ints.Negate;
import net.minecraft.world.level.storage.loot.providers.number.ints.Power;
import net.minecraft.world.level.storage.loot.providers.number.ints.Product;
import net.minecraft.world.level.storage.loot.providers.number.ints.Quotient;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.level.storage.loot.providers.number.ints.Sum;
import net.minecraft.world.level.storage.loot.providers.number.ints.UniformGenerator;
import net.minecraft.world.level.storage.loot.providers.number.ints.WeightedListValue;

/**
 * Estimates the value a data driven number provider produces in normal play.
 * <p>
 * Since 26.3 values such as a fuel's burn time and an item's composting layers are
 * {@link ResolvableInt}s pointing into the {@code minecraft:context_int_provider} registry, and a
 * provider can branch on the state of the block it is used in or roll a random number. EMI shows one
 * number per item, so a dispatcher follows its default branch, a condition its false branch and a
 * distribution or a range its mean: that is what a player sees when nothing special is going on.
 * <p>
 * This is common code on purpose: the server evaluates the whole registry to send the results to
 * clients that have no copy of it (see {@link ContextIntValues}), and the client evaluates whatever
 * it can reach locally.
 */
public final class ContextNumbers {
	/**
	 * Providers reference each other through the registry, and a datapack can make that cycle. The
	 * evaluation would then recurse until it threw a {@link StackOverflowError}, which is an
	 * {@link Error} and would escape past the reload worker and the join. Vanilla's deepest chain
	 * is three, so anything this deep is a loop or not worth a number.
	 */
	private static final int MAX_DEPTH = 64;
	/**
	 * A depth limit alone does not bound the work: providers form a graph, not a tree, so a
	 * datapack could build a diamond forty levels deep and ask for a trillion visits. One
	 * evaluation is allowed this many provider visits, which is four orders of magnitude more than
	 * vanilla's deepest chain needs.
	 */
	private static final int MAX_VISITS = 10_000;

	private ContextNumbers() {
	}

	/**
	 * @param providers the number provider registry, or null when none is available, in which case
	 *	a reference resolves to zero
	 * @param unhandled receives the registry id of every provider type this does not understand
	 */
	public static float expectedValue(ContextIntProvider provider, Consumer<String> unhandled) {
		return expectedValue(provider, unhandled, 0, new int[] {MAX_VISITS});
	}

	private static float expectedValue(ContextIntProvider provider, Consumer<String> unhandled, int depth, int[] budget) {
		if (depth > MAX_DEPTH || budget[0]-- <= 0) {
			unhandled.accept(cycleId(BuiltInRegistries.CONTEXT_INT_PROVIDER_TYPE.getKey(provider.codec()), provider));
			return 0;
		}
		if (provider instanceof ConstantValue constant) {
			return constant.value();
		} else if (provider instanceof Sum sum) {
			float total = 0;
			for (Holder<ContextIntProvider> input : sum.inputs()) {
				total += expectedValue(input.value(), unhandled, depth + 1, budget);
			}
			return total;
		} else if (provider instanceof Product product) {
			float total = 1;
			for (Holder<ContextIntProvider> input : product.inputs()) {
				total *= expectedValue(input.value(), unhandled, depth + 1, budget);
			}
			return total;
		} else if (provider instanceof Average average) {
			float total = 0;
			int count = 0;
			for (Holder<ContextIntProvider> input : average.inputs()) {
				total += expectedValue(input.value(), unhandled, depth + 1, budget);
				count++;
			}
			return count == 0 ? 0 : total / count;
		} else if (provider instanceof Minimum minimum) {
			return extremum(minimum.inputs(), unhandled, depth + 1, budget, true);
		} else if (provider instanceof Maximum maximum) {
			return extremum(maximum.inputs(), unhandled, depth + 1, budget, false);
		} else if (provider instanceof Absolute absolute) {
			return Math.abs(expectedValue(absolute.input().value(), unhandled, depth + 1, budget));
		} else if (provider instanceof Negate negate) {
			return -expectedValue(negate.input().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof FromFloat fromFloat) {
			return expectedValue(fromFloat.input().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof UniformGenerator uniform) {
			return (expectedValue(uniform.min().value(), unhandled, depth + 1, budget)
				+ expectedValue(uniform.max().value(), unhandled, depth + 1, budget)) / 2;
		} else if (provider instanceof Difference difference) {
			return expectedValue(difference.left().value(), unhandled, depth + 1, budget)
				- expectedValue(difference.right().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof Quotient quotient) {
			float divisor = expectedValue(quotient.right().value(), unhandled, depth + 1, budget);
			return divisor == 0 ? 0 : expectedValue(quotient.left().value(), unhandled, depth + 1, budget) / divisor;
		} else if (provider instanceof FloorQuotient quotient) {
			float divisor = expectedValue(quotient.right().value(), unhandled, depth + 1, budget);
			return divisor == 0 ? 0 : (float) Math.floor(expectedValue(quotient.left().value(), unhandled, depth + 1, budget) / divisor);
		} else if (provider instanceof Modulus modulus) {
			float divisor = expectedValue(modulus.right().value(), unhandled, depth + 1, budget);
			return divisor == 0 ? 0 : expectedValue(modulus.left().value(), unhandled, depth + 1, budget) % divisor;
		} else if (provider instanceof FloorModulus modulus) {
			float divisor = expectedValue(modulus.right().value(), unhandled, depth + 1, budget);
			if (divisor == 0) {
				return 0;
			}
			float dividend = expectedValue(modulus.left().value(), unhandled, depth + 1, budget);
			return (float) (dividend - divisor * Math.floor(dividend / divisor));
		} else if (provider instanceof Power power) {
			return (float) Math.pow(expectedValue(power.base().value(), unhandled, depth + 1, budget),
				expectedValue(power.exponent().value(), unhandled, depth + 1, budget));
		} else if (provider instanceof DispatcherProvider<?> dispatcher) {
			// The cases describe special situations (an empty composter always accepting one
			// layer, for instance); the default is what the player sees in normal use.
			return expectedValue((ContextIntProvider) dispatcher.defaultValue().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof ConditionalProvider<?> conditional) {
			return expectedValue((ContextIntProvider) conditional.onFalse().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof WeightedListValue weighted) {
			float total = 0, sum = 0;
			for (Weighted<Holder<ContextIntProvider>> entry : weighted.distribution().unwrap()) {
				total += entry.weight();
				sum += entry.weight() * expectedValue(entry.value().value(), unhandled, depth + 1, budget);
			}
			return total == 0 ? 0 : sum / total;
		}
		unhandled.accept(typeId(BuiltInRegistries.CONTEXT_INT_PROVIDER_TYPE.getKey(provider.codec()), provider));
		return 0;
	}

	private static float expectedValue(ContextFloatProvider provider, Consumer<String> unhandled, int depth, int[] budget) {
		if (depth > MAX_DEPTH || budget[0]-- <= 0) {
			unhandled.accept(cycleId(BuiltInRegistries.CONTEXT_FLOAT_PROVIDER_TYPE.getKey(provider.codec()), provider));
			return 0;
		}
		if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.ConstantValue constant) {
			return constant.value();
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.FromInt fromInt) {
			return expectedValue(fromInt.input().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Sum sum) {
			float total = 0;
			for (Holder<ContextFloatProvider> input : sum.inputs()) {
				total += expectedValue(input.value(), unhandled, depth + 1, budget);
			}
			return total;
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Product product) {
			float total = 1;
			for (Holder<ContextFloatProvider> input : product.inputs()) {
				total *= expectedValue(input.value(), unhandled, depth + 1, budget);
			}
			return total;
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Average average) {
			float total = 0;
			int count = 0;
			for (Holder<ContextFloatProvider> input : average.inputs()) {
				total += expectedValue(input.value(), unhandled, depth + 1, budget);
				count++;
			}
			return count == 0 ? 0 : total / count;
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Minimum minimum) {
			return floatExtremum(minimum.inputs(), unhandled, depth + 1, budget, true);
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Maximum maximum) {
			return floatExtremum(maximum.inputs(), unhandled, depth + 1, budget, false);
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Length length) {
			float total = 0;
			for (Holder<ContextFloatProvider> input : length.inputs()) {
				float component = expectedValue(input.value(), unhandled, depth + 1, budget);
				total += component * component;
			}
			return (float) Math.sqrt(total);
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Absolute absolute) {
			return Math.abs(expectedValue(absolute.input().value(), unhandled, depth + 1, budget));
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Negate negate) {
			return -expectedValue(negate.input().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Ceiling ceiling) {
			return (float) Math.ceil(expectedValue(ceiling.input().value(), unhandled, depth + 1, budget));
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Floor floor) {
			return (float) Math.floor(expectedValue(floor.input().value(), unhandled, depth + 1, budget));
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Round round) {
			return Math.round(expectedValue(round.input().value(), unhandled, depth + 1, budget));
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Truncate truncate) {
			return (float) (long) expectedValue(truncate.input().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.SquareRoot root) {
			float value = expectedValue(root.input().value(), unhandled, depth + 1, budget);
			return value < 0 ? 0 : (float) Math.sqrt(value);
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Sine sine) {
			return (float) Math.sin(expectedValue(sine.input().value(), unhandled, depth + 1, budget));
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Cosine cosine) {
			return (float) Math.cos(expectedValue(cosine.input().value(), unhandled, depth + 1, budget));
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.UniformGenerator uniform) {
			return (expectedValue(uniform.min().value(), unhandled, depth + 1, budget)
				+ expectedValue(uniform.max().value(), unhandled, depth + 1, budget)) / 2;
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Difference difference) {
			return expectedValue(difference.left().value(), unhandled, depth + 1, budget)
				- expectedValue(difference.right().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Quotient quotient) {
			float divisor = expectedValue(quotient.right().value(), unhandled, depth + 1, budget);
			return divisor == 0 ? 0 : expectedValue(quotient.left().value(), unhandled, depth + 1, budget) / divisor;
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Modulus modulus) {
			float divisor = expectedValue(modulus.right().value(), unhandled, depth + 1, budget);
			return divisor == 0 ? 0 : expectedValue(modulus.left().value(), unhandled, depth + 1, budget) % divisor;
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.Power power) {
			return (float) Math.pow(expectedValue(power.base().value(), unhandled, depth + 1, budget),
				expectedValue(power.exponent().value(), unhandled, depth + 1, budget));
		} else if (provider instanceof DispatcherProvider<?> dispatcher) {
			return expectedValue((ContextFloatProvider) dispatcher.defaultValue().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof ConditionalProvider<?> conditional) {
			return expectedValue((ContextFloatProvider) conditional.onFalse().value(), unhandled, depth + 1, budget);
		} else if (provider instanceof net.minecraft.world.level.storage.loot.providers.number.floats.WeightedListValue weighted) {
			float total = 0, sum = 0;
			for (Weighted<Holder<ContextFloatProvider>> entry : weighted.distribution().unwrap()) {
				total += entry.weight();
				sum += entry.weight() * expectedValue(entry.value().value(), unhandled, depth + 1, budget);
			}
			return total == 0 ? 0 : sum / total;
		}
		unhandled.accept(typeId(BuiltInRegistries.CONTEXT_FLOAT_PROVIDER_TYPE.getKey(provider.codec()), provider));
		return 0;
	}

	private static float extremum(HolderSet<ContextIntProvider> inputs, Consumer<String> unhandled, int depth, int[] budget, boolean min) {
		Float best = null;
		for (Holder<ContextIntProvider> input : inputs) {
			float value = expectedValue(input.value(), unhandled, depth, budget);
			if (best == null || (min ? value < best : value > best)) {
				best = value;
			}
		}
		return best == null ? 0 : best;
	}

	private static float floatExtremum(HolderSet<ContextFloatProvider> inputs, Consumer<String> unhandled, int depth, int[] budget, boolean min) {
		Float best = null;
		for (Holder<ContextFloatProvider> input : inputs) {
			float value = expectedValue(input.value(), unhandled, depth, budget);
			if (best == null || (min ? value < best : value > best)) {
				best = value;
			}
		}
		return best == null ? 0 : best;
	}

	private static String typeId(@Nullable Identifier id, Object provider) {
		return id != null ? id.toString() : provider.getClass().getName();
	}

	private static String cycleId(@Nullable Identifier id, Object provider) {
		return typeId(id, provider) + " (more than " + MAX_DEPTH + " deep or " + MAX_VISITS
			+ " providers wide, probably a cycle)";
	}
}
