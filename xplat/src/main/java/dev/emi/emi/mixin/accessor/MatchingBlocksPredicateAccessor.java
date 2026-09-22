package dev.emi.emi.mixin.accessor;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blockpredicates.MatchingBlocksPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MatchingBlocksPredicate.class)
public interface MatchingBlocksPredicateAccessor {

	@Accessor("blocks")
	HolderSet<Block> emi$getBlocks();
}
