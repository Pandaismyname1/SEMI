package dev.emi.emi.mixin.accessor;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blockpredicates.MatchingBlockTagPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MatchingBlockTagPredicate.class)
public interface MatchingBlockTagPredicateAccessor {

	@Accessor("tag")
	TagKey<Block> emi$getTag();
}
