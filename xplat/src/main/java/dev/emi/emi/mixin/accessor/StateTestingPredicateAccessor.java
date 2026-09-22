package dev.emi.emi.mixin.accessor;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The position a state testing predicate looks at, relative to the block being tested. A block
 * transformation combines a predicate on the block itself with predicates on its surroundings, so
 * EMI uses the offset to tell which one names the block the transformation applies to.
 */
@Mixin(StateTestingPredicate.class)
public interface StateTestingPredicateAccessor {

	@Accessor("offset")
	Vec3i emi$getOffset();
}
