package dev.emi.emi.mixin.accessor;

import java.util.List;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.CombiningPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The children of an {@code all_of}/{@code any_of} block predicate. Block transformations use
 * compound predicates to describe the surroundings a tool needs, so EMI has to look inside one to
 * find the block the transformation applies to.
 */
@Mixin(CombiningPredicate.class)
public interface CombiningPredicateAccessor {

	@Accessor("predicates")
	List<BlockPredicate> emi$getPredicates();
}
